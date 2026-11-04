package dev.jun.nosclient.media;

import com.google.gson.Gson;
import com.sun.jna.Platform;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.media.lyrics.Lyrics;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class MediaManager {
    private static final MediaManager INSTANCE = new MediaManager();
    private final SmtcBackend smtc = new SmtcBackend();
    private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<MediaManager.Event> events = new ConcurrentLinkedQueue<>();
    private final LyricsService lyricsService = new LyricsService();
    private volatile Track track;
    private volatile String statusKey = "nosclient.music.status.searching";
    private volatile boolean running;
    private boolean mediaFailed;
    private volatile long songSeq;
    private Thread thread;
    private String lastKey = "";
    private boolean lastPlaying;
    private long missingSince = -1L;
    private long pausedUpdatedFt = -2L;
    private long lastOffset = Long.MIN_VALUE;
    private long lastPollNs = -1L;
    private int stable;
    private volatile byte[] cover;
    private volatile long coverSeq = -1L;
    private volatile int repeat;
    private volatile long repeatHold;
    private volatile boolean pollSoon;
    private volatile boolean forceSync;
    private final MediaBackend desktop = Platform.isWindows() ? null : new DesktopBackend();
    private long lastApiAt;
    private final long[] apiOffs = new long[5];
    private int apiN;
    private static final Gson GSON = new Gson();

    public static MediaManager get() {
        return INSTANCE;
    }

    private MediaManager() {}

    public void start() {
        if (!this.running) {
            this.running = true;
            this.thread = new Thread(this::loop, "Nos Client-Media");
            this.thread.setDaemon(true);
            this.thread.start();
        }
    }

    public void stop() {
        this.running = false;
        if (this.thread != null) {
            this.thread.interrupt();
        }

        this.lyricsService.close();
    }

    public Track track() {
        return this.track;
    }

    public Lyrics lyrics() {
        return this.lyricsService.snapshot().lyrics();
    }

    public MediaManager.LyricsState lyricsState() {
        return switch (this.lyricsService.snapshot().status()) {
            case "ready" -> LyricsState.FOUND;
            case "loading" -> LyricsState.SEARCHING;
            case "no_lyrics", "unavailable" -> LyricsState.NOT_FOUND;
            default -> LyricsState.NONE;
        };
    }

    public String statusKey() {
        return this.statusKey;
    }

    public long songSeq() {
        return this.songSeq;
    }

    public byte[] cover() {
        return this.cover;
    }

    public long coverSeq() {
        return this.coverSeq;
    }

    public boolean isPlaying() {
        Track t = this.track;
        return t != null && t.playing;
    }

    public long positionRaw() {
        Track t = this.track;
        return t == null ? -1L : t.positionNow();
    }

    public long position() {
        Track t = this.track;
        if (t == null) {
            return -1L;
        }

        MusicConfig c = MusicSettings.get();
        Integer own = c.songOffsets == null ? null : c.songOffsets.get(t.key());
        return Math.max(0L, t.positionNow() + c.syncOffsetMs + (own == null ? 0 : own));
    }

    public MediaManager.Event pollEvent() {
        return this.events.poll();
    }

    public void playPause() {
        this.commands.add(
                () -> {
                    this.backend().playPause();
                    this.pollSoon = true;
                });
    }

    public void next() {
        this.commands.add(
                () -> {
                    this.backend().next();
                    this.pollSoon = true;
                });
    }

    public void previous() {
        this.commands.add(
                () -> {
                    this.backend().previous();
                    this.pollSoon = true;
                });
    }

    public int repeat() {
        return this.repeat;
    }

    public void cycleRepeat() {
        int next = this.repeat == 0 ? 2 : (this.repeat == 2 ? 1 : 0);
        this.repeat = next;
        this.repeatHold = System.currentTimeMillis() + 4000L;
        this.commands.add(
                () -> {
                    this.backend().setRepeat(next);
                    Track t = this.track;
                    if (t != null && "Spotify".equals(t.sourceApp)) {
                        Thread th =
                                new Thread(() -> SpotifyWeb.setRepeat(next), "Nos Client repeat");
                        th.setDaemon(true);
                        th.start();
                    }

                    this.pollSoon = true;
                });
    }

    public void seek(long ms) {
        this.commands.add(
                () -> {
                    this.backend().seek(ms);
                    Track t = this.track;
                    if (t != null) {
                        this.track =
                                new Track(
                                        t.title,
                                        t.artist,
                                        t.album,
                                        t.durationMs,
                                        ms,
                                        t.playing,
                                        t.sourceApp,
                                        ms);
                    }

                    this.pollSoon = true;
                });
    }

    public void reconnect() {
        this.commands.add(
                () -> {
                    this.smtc.close();
                    this.lastKey = "";
                    this.track = null;
                });
    }

    public LyricsService lyricsService() {
        return lyricsService;
    }

    public void refetch() {
        lyricsService.refetch();
    }

    public void resync() {
        this.forceSync = true;
        this.pollSoon = true;
    }

    private MediaBackend backend() {
        return this.desktop != null ? this.desktop : this.smtc;
    }

    private void loop() {
        while (this.running) {
            long started = System.currentTimeMillis();

            try {
                Runnable r;
                while ((r = this.commands.poll()) != null) {
                    try {
                        r.run();
                    } catch (Throwable t) {
                        ClientResources.LOG.warn("Không điều khiển được trình phát", t);
                    }
                }

                this.tick();
                mediaFailed = false;
                this.lyricsService.poll(
                        this.track,
                        MusicSettings.get().enabled,
                        dev.jun.nosclient.scene.SceneConfig.INSTANCE.lyricsAutoFetch);
            } catch (Throwable t) {
                if (!mediaFailed)
                    ClientResources.LOG.warn("Không đọc được trình phát, đang kết nối lại", t);
                mediaFailed = true;
                lastKey = "";
                this.backend().close();
                this.track = null;
                this.lyricsService.poll(null, false, false);
            }

            long interval = desktop == null ? 250L : 1000L;
            if (this.pollSoon) {
                this.pollSoon = false;
                interval = 120L;
            }

            long sleep = interval - (System.currentTimeMillis() - started);
            if (sleep > 0L) {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    if (!this.running) {
                        return;
                    }
                }
            }
        }

        this.smtc.close();
    }

    private void tick() throws Exception {
        MusicConfig c = MusicSettings.get();
        Track t = null;
        if (c.enabled) {
            MediaBackend b = this.backend();
            t = b.poll(c.source);
            int rp = b.repeatMode();
            if (rp >= 0 && rp <= 2 && System.currentTimeMillis() > this.repeatHold) {
                this.repeat = rp;
            }

            this.statusKey = b.statusKey();
        }

        if (t != null && !t.isEmpty()) {
            this.missingSince = -1L;
            String key = t.key();
            if (!key.equals(this.lastKey)) {
                this.lastKey = key;
                this.lastPlaying = t.playing;
                this.pausedUpdatedFt = t.playing ? -2L : t.updatedFt;
                this.stable = 0;
                this.lastOffset = Long.MIN_VALUE;
                this.apiN = 0;
                this.track = t;
                long seq = ++this.songSeq;
                this.events.add(
                        new MediaManager.Event(MediaManager.EventType.SONG, t, System.nanoTime()));
                this.cover = null;
                this.commands.add(
                        () -> {
                            byte[] b = null;

                            try {
                                b = this.backend().cover();
                            } catch (Throwable var5x) {
                            }

                            if (seq == this.songSeq) {
                                this.cover = b;
                                this.coverSeq = seq;
                            }
                        });

            } else {
                Track prev = this.track;
                boolean fresh = t.updatedFt > 0L && t.updatedFt != this.pausedUpdatedFt;
                long nowNs = System.nanoTime();
                if (prev != null && t.playing) {
                    if (prev.playing) {
                        long ours = prev.positionNow();
                        long offset = t.positionMs - ours;
                        long sinceMs =
                                this.lastPollNs > 0L ? (nowNs - this.lastPollNs) / 1000000L : 250L;
                        if (this.lastOffset != Long.MIN_VALUE
                                && Math.abs(offset - this.lastOffset) <= 60L + sinceMs / 5L) {
                            this.stable++;
                        } else {
                            this.stable = 0;
                        }

                        this.lastOffset = offset;
                        long diff = Math.abs(offset);
                        boolean seek =
                                prev.rawPositionMs >= 0L
                                        && t.rawPositionMs >= 0L
                                        && Math.abs(t.rawPositionMs - prev.rawPositionMs) > 400L
                                        && t.updatedFt != prev.updatedFt;
                        boolean apiLive =
                                c.source == MusicConfig.Source.SPOTIFY
                                        && SpotifyWeb.positionFor(t) >= 0L;
                        boolean trusted =
                                seek && this.stable >= 1 || fresh && this.stable >= 2 && !apiLive;
                        if (trusted && diff > (this.forceSync ? 120 : 250)) {
                            ClientResources.LOG.debug(
                                    "[Nos Client sync] re-sync {} ms (ours {} -> windows {}),"
                                            + " fresh={}, seek={}, stable={}",
                                    offset,
                                    ours,
                                    t.positionMs,
                                    fresh,
                                    seek,
                                    this.stable);
                            t = Track.at(t, t.positionMs);
                            this.lastOffset = 0L;
                        } else {
                            t = prev.keepClock(t);
                        }
                    } else {
                        this.stable = 0;
                        this.lastOffset = Long.MIN_VALUE;
                        if (!fresh && t.rawPositionMs >= 0L) {
                            long from =
                                    Math.abs(t.rawPositionMs - prev.positionMs) < 3000L
                                            ? prev.positionMs
                                            : t.rawPositionMs;
                            t = Track.at(t, from);
                        }
                    }
                }

                if (c.source == MusicConfig.Source.SPOTIFY && t.playing) {
                    SpotifyWeb.start();
                    long at = SpotifyWeb.sampleAt();
                    long api = SpotifyWeb.positionFor(t);
                    if (api >= 0L && at != this.lastApiAt) {
                        this.lastApiAt = at;
                        long off = api - t.positionNow();
                        this.apiOffs[this.apiN++ % this.apiOffs.length] = off;
                        int n = Math.min(this.apiN, this.apiOffs.length);
                        long[] s = Arrays.copyOf(this.apiOffs, n);
                        Arrays.sort(s);
                        long med = s[n / 2];
                        boolean big = Math.abs(off) > 1500L;
                        if (big || n >= 3 && Math.abs(med) > 90L) {
                            ClientResources.LOG.debug(
                                    "[Nos Client sync] spotify re-sync {} ms", big ? off : med);
                            t = Track.at(t, t.positionNow() + (big ? off : med));
                            this.apiN = 0;
                        }
                    }
                } else {
                    this.apiN = 0;
                }

                this.lastPollNs = nowNs;
                this.forceSync = false;
                if (!t.playing) {
                    this.pausedUpdatedFt = t.updatedFt;
                }

                this.track = t;
                if (prev != null && t.playing != this.lastPlaying) {
                    this.events.add(
                            new MediaManager.Event(
                                    t.playing
                                            ? MediaManager.EventType.RESUME
                                            : MediaManager.EventType.PAUSE,
                                    t,
                                    System.nanoTime()));
                }

                this.lastPlaying = t.playing;
            }
        } else {
            if (this.track != null) {
                if (this.missingSince < 0L) {
                    this.missingSince = System.currentTimeMillis();
                }

                if (System.currentTimeMillis() - this.missingSince > 2500L) {
                    this.events.add(
                            new MediaManager.Event(
                                    MediaManager.EventType.STOP, this.track, System.nanoTime()));
                    this.track = null;
                    this.lastKey = "";
                }
            }
        }
    }

    public record Event(MediaManager.EventType type, Track track, long atNanos) {}

    public enum EventType {
        SONG,
        PAUSE,
        RESUME,
        STOP;
    }

    public enum LyricsState {
        NONE,
        SEARCHING,
        FOUND,
        NOT_FOUND;
    }
}
