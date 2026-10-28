package dev.jun.nosclient.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.function.LongSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class WindowsMedia implements SystemMedia {
    private final WindowsMedia.Starter starter;
    private final LongSupplier clock;
    private Process process;
    private MediaTrack latest = MediaTrack.NONE;
    private long received;
    private long started;
    private long attempted;

    WindowsMedia() {
        this(WindowsMedia::startProcess, System::nanoTime);
    }

    WindowsMedia(WindowsMedia.Starter starter, LongSupplier clock) {
        this.starter = starter;
        this.clock = clock;
    }

    private static Process startProcess() throws Exception {
        try (InputStream resource = WindowsMedia.class.getResourceAsStream("/assets/nosclient/media/windows.ps1")) {
            if (resource == null) {
                throw new IOException("Missing media helper");
            }

            String script = "$GamePid = " + ProcessHandle.current().pid() + "\n" + new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            return new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script)
                .redirectError(Redirect.DISCARD)
                .start();
        }
    }

    @Override
    public synchronized MediaTrack poll() throws Exception {
        long now = this.clock.getAsLong();
        if (this.process != null && (!this.process.isAlive() || now - Math.max(this.started, this.received) > 10000000000L)) {
            this.stop();
        }

        if (this.process == null) {
            if (this.attempted != 0L && now - this.attempted < 5000000000L) {
                return MediaTrack.NONE;
            }

            this.attempted = now;
            this.started = now;
            this.process = this.starter.start();
            Process child = this.process;
            Thread.ofVirtual().name("nos-media-windows").start(() -> this.read(child));
        }

        return this.received != 0L && now - this.received <= 8000000000L ? this.latest : MediaTrack.NONE;
    }

    private void read(Process child) {
        try {
            try (InputStream input = child.getInputStream()) {
                ByteArrayOutputStream line = new ByteArrayOutputStream();

                int next;
                while ((next = input.read()) >= 0) {
                    if (next == 10) {
                        MediaTrack value;
                        try {
                            value = parse(line.toString(StandardCharsets.UTF_8).strip(), this.clock.getAsLong());
                        } catch (RuntimeException error) {
                            value = MediaTrack.NONE;
                        }

                        synchronized (this) {
                            if (this.process != child) {
                                return;
                            }

                            this.latest = value;
                            this.received = this.clock.getAsLong();
                        }

                        line.reset();
                    } else {
                        if (line.size() >= 16384) {
                            return;
                        }

                        line.write(next);
                    }
                }
            } catch (IOException var30) {
            }
        } finally {
            synchronized (this) {
                if (this.process == child) {
                    this.process = null;
                    this.latest = MediaTrack.NONE;
                    this.received = 0L;
                }
            }

            if (child.isAlive()) {
                child.destroyForcibly();
            }
        }
    }

    static MediaTrack parse(String json, long now) {
        JsonObject data = JsonParser.parseString(json).getAsJsonObject();
        return !data.has("title")
            ? MediaTrack.NONE
            : new MediaTrack(
                data.get("title").getAsString(),
                data.has("artist") && !data.get("artist").isJsonNull() ? data.get("artist").getAsString() : "",
                data.get("duration_ms").getAsLong(),
                data.get("position_ms").getAsLong(),
                data.get("playing").getAsBoolean(),
                data.get("rate").getAsDouble(),
                now,
                MediaSource.detect(data.has("source") && !data.get("source").isJsonNull() ? data.get("source").getAsString() : "")
            );
    }

    private void stop() {
        Process child = this.process;
        this.process = null;
        this.latest = MediaTrack.NONE;
        this.received = 0L;
        if (child != null) {
            child.destroyForcibly();
        }
    }

    @Override
    public synchronized void close() {
        this.stop();
    }

    @FunctionalInterface
    @Environment(EnvType.CLIENT)
    interface Starter {
        Process start() throws Exception;
    }
}
