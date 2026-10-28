$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]
$asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1
function Await($operation, $type) {
    $task = $asTask.MakeGenericMethod($type).Invoke($null, @($operation))
    if (-not $task.Wait(2500)) {
        try { $operation.Cancel() } catch {}
        throw 'System media request timed out'
    }
    $task.GetAwaiter().GetResult()
}
$manager = $null
$refreshAt = [DateTimeOffset]::MinValue
while (Get-Process -Id $GamePid -ErrorAction SilentlyContinue) {
    try {
        if ($null -eq $manager -or [DateTimeOffset]::UtcNow -ge $refreshAt) {
            $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            $refreshAt = [DateTimeOffset]::UtcNow.AddSeconds(30)
        }
        $session = $manager.GetCurrentSession()
        # Keep Windows' current playing session (e.g. the active YouTube tab).
        # Enumerating Spotify first must not replace it with another playing app.
        if ($null -eq $session -or $session.GetPlaybackInfo().PlaybackStatus.ToString() -ne 'Playing') {
            foreach ($candidate in $manager.GetSessions()) {
                try {
                    if ($candidate.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing') { $session = $candidate; break }
                } catch { } # A tab may close while sessions are being enumerated.
            }
        }
        if ($null -eq $session) { [Console]::WriteLine('{}') }
        else {
            $media = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $playback = $session.GetPlaybackInfo()
            $timeline = $session.GetTimelineProperties()
            $playing = $playback.PlaybackStatus.ToString() -eq 'Playing'
            $rate = 1.0
            if ($null -ne $playback.PlaybackRate -and $playback.PlaybackRate -gt 0) { $rate = [double]$playback.PlaybackRate }
            $position = $timeline.Position.TotalMilliseconds
            if ($playing) { $position += [Math]::Max(0, ([DateTimeOffset]::UtcNow - $timeline.LastUpdatedTime).TotalMilliseconds) * $rate }
            $document = @{
                title = $media.Title; artist = $media.Artist; playing = $playing; rate = $rate
                source = $session.SourceAppUserModelId
                duration_ms = [long]$timeline.EndTime.TotalMilliseconds; position_ms = [long]$position
            }
            [Console]::WriteLine(($document | ConvertTo-Json -Compress))
        }
    } catch { $manager = $null; [Console]::WriteLine('{}') }
    [Console]::Out.Flush()
    Start-Sleep -Milliseconds 1500
}
