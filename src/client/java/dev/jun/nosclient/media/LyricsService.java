package dev.jun.nosclient.media;

import dev.jun.nosclient.storage.ClientFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class LyricsService implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("nosclient");
    private final Path directory;
    private final Supplier<SystemMedia> provider;
    private final LyricsService.Lookup lookup;
    private final LongSupplier clock;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "nos-system-media"));
    private final ExecutorService downloads = Executors.newSingleThreadExecutor(r -> daemon(r, "nos-lyrics-lookup"));
    private volatile LyricsService.Snapshot snapshot = new LyricsService.Snapshot(MediaTrack.NONE, LrcTimeline.EMPTY, "disabled");
    private volatile boolean enabled;
    private volatile boolean autoFetch;
    private volatile boolean closed;
    private SystemMedia media;
    private String loadedKey = "";
    private String resultStatus = "waiting";
    private LrcTimeline timeline = LrcTimeline.EMPTY;
    private long localStamp;
    private long retryAt;
    private long revision;
    private int failures;
    private int mediaFailures;
    private boolean lastAuto;
    private Future<?> pending;

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    public LyricsService(Path directory) {
        this(directory, SystemMedia::create, LyricsLookup::fetch, System::nanoTime, true);
    }

    LyricsService(Path directory, Supplier<SystemMedia> provider, LyricsService.Lookup lookup, LongSupplier clock, boolean schedule) {
        this.directory = directory;
        this.provider = provider;
        this.lookup = lookup;
        this.clock = clock;
        if (schedule) {
            this.worker.scheduleWithFixedDelay(this::poll, 0L, 2L, TimeUnit.SECONDS);
        }
    }

    public void configure(boolean enabled, boolean autoFetch) {
        this.enabled = enabled;
        this.autoFetch = autoFetch;
    }

    public LyricsService.Snapshot snapshot() {
        return this.snapshot;
    }

    public Path directory() {
        return this.directory;
    }

    public void openDirectory() {
        Thread.ofVirtual().name("nos-lyrics-folder").start(() -> {
            try {
                Files.createDirectories(this.directory);
                Util.getPlatform().openPath(this.directory);
            } catch (IOException var2) {
            }
        });
    }

    public void importLrc(Path source) {
        MediaTrack track = this.snapshot.track();
        if (!this.closed && !track.title().isBlank()) {
            this.worker.execute(() -> {
                try {
                    String text = ClientFiles.read(source);
                    LrcTimeline parsed = LrcTimeline.parse(text);
                    if (parsed.lines().isEmpty()) {
                        this.importFailed(track);
                        return;
                    }

                    synchronized (this) {
                        if (this.closed) {
                            return;
                        }

                        boolean active = this.enabled && this.loadedKey.equals(track.key());
                        if (active) {
                            this.cancelLookup();
                        }

                        Path file = this.directory.resolve(fileName(track));
                        ClientFiles.write(file, text);
                        if (active) {
                            this.timeline = parsed;
                            this.resultStatus = "ready";
                            this.localStamp = Files.getLastModifiedTime(file).toMillis();
                            if (this.snapshot.track().key().equals(track.key())) {
                                this.snapshot = new LyricsService.Snapshot(this.snapshot.track(), parsed, this.resultStatus);
                            }
                        }
                    }
                } catch (Exception error) {
                    this.importFailed(track);
                }
            });
        }
    }

    private synchronized void importFailed(MediaTrack track) {
        if (!this.closed && this.enabled && this.snapshot.track().key().equals(track.key())) {
            this.snapshot = new LyricsService.Snapshot(this.snapshot.track(), this.timeline, "invalid_lrc");
        }
    }

    private void cancelLookup() {
        this.revision++;
        if (this.pending != null) {
            this.pending.cancel(true);
        }

        this.pending = null;
    }

    private void reset() {
        this.cancelLookup();
        this.loadedKey = "";
        this.timeline = LrcTimeline.EMPTY;
        this.localStamp = this.retryAt = 0L;
        this.failures = 0;
    }

    private void closeMedia() {
        if (this.media != null) {
            try {
                this.media.close();
            } catch (Exception var2) {
            }

            this.media = null;
        }
    }

    synchronized void poll() {
        if (!this.closed) {
            try {
                if (!this.enabled) {
                    this.closeMedia();
                    this.reset();
                    this.snapshot = new LyricsService.Snapshot(MediaTrack.NONE, this.timeline, "disabled");
                    return;
                }

                if (this.media == null) {
                    this.media = this.provider.get();
                }

                MediaTrack track = this.media.poll();
                this.mediaFailures = 0;
                if (track.title().isBlank()) {
                    this.snapshot = new LyricsService.Snapshot(track, LrcTimeline.EMPTY, "waiting");
                    return;
                }

                Path file = this.directory.resolve(fileName(track));
                long stamp = Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0L;
                boolean changed = !this.loadedKey.equals(track.key()) || this.lastAuto != this.autoFetch;
                this.lastAuto = this.autoFetch;
                if (changed || stamp != this.localStamp) {
                    this.reset();
                    this.loadedKey = track.key();
                    this.localStamp = stamp;

                    try {
                        ClientFiles.write(
                            this.directory.resolve("current-track.txt"), track.title() + "\n" + track.artist() + "\n\nLRC: " + file.getFileName() + "\n"
                        );
                    } catch (IOException var9) {
                    }

                    if (stamp != 0L) {
                        try {
                            this.timeline = LrcTimeline.parse(ClientFiles.read(file));
                        } catch (IOException var8) {
                        }
                    }

                    this.resultStatus = this.timeline.lines().isEmpty() ? "no_lyrics" : "ready";
                }

                if (this.timeline.lines().isEmpty()
                    && this.autoFetch
                    && this.pending == null
                    && (this.retryAt == 0L || this.clock.getAsLong() - this.retryAt >= 0L)) {
                    this.resultStatus = "loading";
                    long token = this.revision;
                    this.pending = this.downloads.submit(() -> {
                        String lrc = "";
                        Exception failure = null;

                        try {
                            lrc = this.lookup.fetch(track);
                        } catch (Exception errorx) {
                            failure = errorx;
                        }

                        this.complete(token, track, lrc, failure);
                    });
                }

                this.snapshot = new LyricsService.Snapshot(track, this.timeline, this.resultStatus);
            } catch (Exception error) {
                // Cả bước đọc nhạc chết thì phải nghe được: báo một lần mỗi khi chuyển từ chạy sang hỏng.
                if (this.mediaFailures == 0 && !"unavailable".equals(this.snapshot.status())) {
                    LOG.warn("Không đọc được bài đang phát, lời nhạc tạm tắt: {}", error.toString());
                }

                if (++this.mediaFailures >= 2) {
                    this.closeMedia();
                    this.mediaFailures = 0;
                }

                this.snapshot = new LyricsService.Snapshot(MediaTrack.NONE, LrcTimeline.EMPTY, "unavailable");
            }
        }
    }

    private synchronized void complete(long token, MediaTrack track, String lrc, Exception failure) {
        if (!this.closed && this.enabled && this.autoFetch && this.revision == token && this.loadedKey.equals(track.key())) {
            this.pending = null;
            this.timeline = failure == null ? LrcTimeline.parse(lrc) : LrcTimeline.EMPTY;
            this.resultStatus = failure != null ? "unavailable" : (this.timeline.lines().isEmpty() ? "no_lyrics" : "ready");
            this.retryAt = this.clock.getAsLong() + (failure == null ? 300L : Math.min(120L, 10L << Math.min(this.failures++, 4))) * 1000000000L;
            if (!this.timeline.lines().isEmpty()) {
                this.failures = 0;

                try {
                    Path file = this.directory.resolve(fileName(track));
                    ClientFiles.write(file, lrc);
                    this.localStamp = Files.getLastModifiedTime(file).toMillis();
                } catch (Exception var7) {
                }
            }

            if (this.snapshot.track().key().equals(track.key())) {
                this.snapshot = new LyricsService.Snapshot(this.snapshot.track(), this.timeline, this.resultStatus);
            }
        }
    }

    public static String fileName(MediaTrack track) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(track.key().getBytes(StandardCharsets.UTF_8))) + ".lrc";
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        this.enabled = false;
        this.cancelLookup();
        this.worker.shutdownNow();
        this.downloads.shutdownNow();
        this.closeMedia();
        this.snapshot = new LyricsService.Snapshot(MediaTrack.NONE, LrcTimeline.EMPTY, "disabled");
    }

    @FunctionalInterface
    @Environment(EnvType.CLIENT)
    interface Lookup {
        String fetch(MediaTrack var1) throws Exception;
    }

    @Environment(EnvType.CLIENT)
    public record Snapshot(MediaTrack track, LrcTimeline timeline, String status) {
    }
}
