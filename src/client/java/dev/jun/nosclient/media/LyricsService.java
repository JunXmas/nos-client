package dev.jun.nosclient.media;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.media.lyrics.Lyrics;
import dev.jun.nosclient.media.lyrics.LyricsFetcher;
import dev.jun.nosclient.storage.ClientFiles;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;

/** Tra lời bất đồng bộ từ một nguồn nhạc; kết quả cũ không được ghi đè bài hoặc LRC vừa nhập. */
public final class LyricsService implements AutoCloseable {
    private final Path directory;
    private final Lookup lookup;
    private final LyricsCache cache;
    private final LongSupplier clock;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread thread = new Thread(r, "nos-lyrics");
                        thread.setDaemon(true);
                        return thread;
                    });
    private volatile Snapshot snapshot = new Snapshot(null, null, "disabled");
    private Future<?> pending;
    private String key = "";
    private long revision;
    private long stamp;
    private long retryAt;
    private int failures;
    private boolean enabled;
    private boolean closed;
    private boolean automatic;

    public LyricsService() {
        this(
                FabricLoader.getInstance().getConfigDir().resolve("nosclient-lyrics"),
                LyricsService::fetch,
                System::nanoTime);
    }

    public LyricsService(Path directory, Lookup lookup, LongSupplier clock) {
        this.directory = directory;
        this.cache = new LyricsCache(directory.resolve("cache"));
        this.lookup = lookup;
        this.clock = clock;
    }

    private static Lyrics fetch(Track track) throws Exception {
        if ("Browser".equals(track.sourceApp)) {
            try {
                LyricsLookup.Match match = LyricsLookup.find(asMediaTrack(track));
                if (match != null) {
                    Lyrics words =
                            LyricsFetcher.fetch(
                                    match.artist(), match.title(), track.album, track.durationMs);
                    return words != null && words.wordSynced
                            ? words
                            : parse(match.lrc(), track.durationMs);
                }
            } catch (InterruptedException interrupted) {
                throw interrupted;
            } catch (Exception error) {
                ClientResources.LOG.debug(
                        "Browser lyrics lookup unavailable; trying other sources", error);
            }
        }
        return LyricsFetcher.fetch(track.artist, track.title, track.album, track.durationMs);
    }

    static MediaTrack asMediaTrack(Track track) {
        return new MediaTrack(
                track.title,
                track.artist,
                track.durationMs,
                track.positionMs,
                track.playing,
                1.0,
                track.sampledAtNanos,
                "Browser".equals(track.sourceApp) ? MediaSource.BROWSER : MediaSource.UNKNOWN);
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public Path directory() {
        return directory;
    }

    public void openDirectory() {
        try {
            Files.createDirectories(directory);
            Util.getPlatform().openPath(directory);
        } catch (Exception error) {
            ClientResources.LOG.warn("Không mở được thư mục lời {}", directory, error);
        }
    }

    private void cancel() {
        revision++;
        if (pending != null) pending.cancel(true);
        pending = null;
    }

    public synchronized void poll(Track track, boolean active, boolean autoFetch) {
        if (closed) return;
        enabled = active;
        if (!active || track == null || track.isEmpty()) {
            if (!key.isEmpty()) cancel();
            key = "";
            snapshot = new Snapshot(track, null, active ? "waiting" : "disabled");
            return;
        }
        try {
            Path local = directory.resolve(fileName(track));
            long localStamp =
                    Files.isRegularFile(local) ? Files.getLastModifiedTime(local).toMillis() : 0;
            if (!key.equals(track.key()) || stamp != localStamp || automatic != autoFetch) {
                cancel();
                key = track.key();
                stamp = localStamp;
                automatic = autoFetch;
                retryAt = 0;
                failures = 0;
                snapshot = new Snapshot(track, null, "no_lyrics");
                Lyrics lyrics =
                        stamp > 0
                                ? parse(ClientFiles.read(local), track.durationMs)
                                : cache.readCache(track);
                snapshot = new Snapshot(track, lyrics, lyrics == null ? "no_lyrics" : "ready");
                ClientFiles.write(
                        directory.resolve("current-track.txt"),
                        track.title + "\n" + track.artist + "\nLRC: " + local.getFileName() + "\n");
            } else snapshot = new Snapshot(track, snapshot.lyrics(), snapshot.status());
            if (snapshot.lyrics() == null
                    && autoFetch
                    && pending == null
                    && clock.getAsLong() >= retryAt) {
                long token = revision;
                snapshot = new Snapshot(track, null, "loading");
                pending =
                        worker.submit(
                                () -> {
                                    Lyrics lyrics = null;
                                    Exception failure = null;
                                    try {
                                        lyrics = lookup.fetch(track);
                                    } catch (Exception error) {
                                        failure = error;
                                    }
                                    complete(token, track, lyrics, failure);
                                });
            }
        } catch (Exception error) {
            ClientResources.LOG.warn("Không đọc được lời cục bộ cho {}", track.title, error);
            snapshot =
                    new Snapshot(
                            track,
                            snapshot.track() != null && snapshot.track().key().equals(track.key())
                                    ? snapshot.lyrics()
                                    : null,
                            "unavailable");
        }
    }

    private synchronized void complete(long token, Track track, Lyrics lyrics, Exception failure) {
        if (closed || !enabled || !automatic || token != revision || !key.equals(track.key()))
            return;
        pending = null;
        if (failure != null)
            ClientResources.LOG.warn("Chưa lấy được lời cho {}, sẽ thử lại", track.title, failure);
        if (lyrics != null && (!lyrics.synced || lyrics.lines.isEmpty())) lyrics = null;
        retryAt =
                clock.getAsLong()
                        + (failure == null ? 300L : Math.min(120L, 10L << Math.min(failures++, 4)))
                                * 1_000_000_000L;
        if (lyrics != null) cache.writeCache(track, lyrics);
        snapshot =
                new Snapshot(
                        snapshot.track(),
                        lyrics,
                        failure != null ? "unavailable" : lyrics == null ? "no_lyrics" : "ready");
    }

    public void importLrc(Path source) {
        Track track = snapshot.track();
        if (track == null) return;
        Thread.ofVirtual()
                .name("nos-import-lyrics")
                .start(
                        () -> {
                            try {
                                String text = ClientFiles.read(source);
                                Lyrics lyrics = parse(text, track.durationMs);
                                if (lyrics == null)
                                    throw new IllegalArgumentException("LRC không có dòng hợp lệ");
                                synchronized (this) {
                                    if (closed) return;
                                    Path target = directory.resolve(fileName(track));
                                    ClientFiles.write(target, text);
                                    if (enabled && key.equals(track.key())) {
                                        cancel();
                                        stamp = Files.getLastModifiedTime(target).toMillis();
                                        snapshot = new Snapshot(snapshot.track(), lyrics, "ready");
                                    }
                                }
                            } catch (Exception error) {
                                ClientResources.LOG.warn(
                                        "Không nhập được lời từ {}", source, error);
                            }
                        });
    }

    public synchronized void refetch() {
        if (closed) return;
        cancel();
        Track track = snapshot.track();
        if (track != null) cache.remove(track);
        key = "";
    }

    public static Lyrics parse(String text, long durationMs) {
        LrcTimeline timeline = LrcTimeline.parse(text);
        if (timeline.lines().isEmpty()) return null;
        List<long[]> times = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        timeline.lines()
                .forEach(
                        line -> {
                            times.add(new long[] {line.atMs()});
                            texts.add(line.text());
                        });
        return new Lyrics(times, texts, true, "local LRC", durationMs);
    }

    public static String fileName(Track track) {
        try {
            return HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    asMediaTrack(track)
                                                            .key()
                                                            .getBytes(StandardCharsets.UTF_8)))
                    + ".lrc";
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public synchronized void close() {
        closed = true;
        cancel();
        worker.shutdownNow();
    }

    @FunctionalInterface
    public interface Lookup {
        Lyrics fetch(Track track) throws Exception;
    }

    public record Snapshot(Track track, Lyrics lyrics, String status) {}
}
