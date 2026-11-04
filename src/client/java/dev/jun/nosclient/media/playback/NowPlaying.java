package dev.jun.nosclient.media.playback;

import dev.jun.nosclient.ClientEffects;
import dev.jun.nosclient.config.EffectsConfig;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.media.MediaManager;
import dev.jun.nosclient.media.Track;
import dev.jun.nosclient.media.lyrics.Lyrics;
import dev.jun.nosclient.sky.SkyPreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NowPlaying {
    public final String trackId;
    public final String title;
    public final String artist;
    public final long durationMs;
    public final boolean playing;
    public final long progressMs;
    public final long fetchedAt;
    public final List<NowPlaying.Line> lines;
    public final NowPlaying.LyricsState lyricsState;
    public static final NowPlaying EMPTY =
            new NowPlaying(
                    null,
                    "",
                    "",
                    0L,
                    false,
                    0L,
                    0L,
                    Collections.emptyList(),
                    NowPlaying.LyricsState.NONE);
    private static volatile NowPlaying now = EMPTY;
    private static Lyrics shownLyrics;
    private static boolean shownCaps;
    private static List<NowPlaying.Line> shownLines = List.of();
    private static String lastKey = "";

    public NowPlaying(
            String trackId,
            String title,
            String artist,
            long durationMs,
            boolean playing,
            long progressMs,
            long fetchedAt,
            List<NowPlaying.Line> lines,
            NowPlaying.LyricsState lyricsState) {
        this.trackId = trackId;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.durationMs = durationMs;
        this.playing = playing;
        this.progressMs = progressMs;
        this.fetchedAt = fetchedAt;
        this.lines = lines == null ? Collections.emptyList() : lines;
        this.lyricsState = lyricsState;
    }

    public static NowPlaying now() {
        return now;
    }

    /** Ảnh chụp bài đang phát cho bầu trời (mỗi tick client). */
    public static void tick() {
        MediaManager mm = MediaManager.get();
        Track t = mm.track();
        if (t == null || t.isEmpty()) {
            now = EMPTY;
            lastKey = "";
            return;
        }

        Lyrics l = mm.lyrics();
        boolean caps = MusicSettings.get().allCaps;
        if (l != shownLyrics || caps != shownCaps) {
            shownLyrics = l;
            shownCaps = caps;
            shownLines = lines(l);
        }

        NowPlaying.LyricsState st =
                switch (mm.lyricsState()) {
                    case SEARCHING -> NowPlaying.LyricsState.LOADING;
                    case FOUND ->
                            shownLines.isEmpty()
                                    ? NowPlaying.LyricsState.NOT_FOUND
                                    : NowPlaying.LyricsState.SYNCED;
                    case NOT_FOUND -> NowPlaying.LyricsState.NOT_FOUND;
                    default -> NowPlaying.LyricsState.NONE;
                };
        String key = t.key();
        now =
                new NowPlaying(
                        key,
                        t.title,
                        t.artist,
                        t.durationMs,
                        t.playing,
                        Math.max(0L, mm.position()),
                        System.currentTimeMillis(),
                        shownLines,
                        st);
        if (!key.equals(lastKey)) {
            lastKey = key;
            EffectsConfig cf = ClientEffects.cfg();
            if (cf.skyShuffle && cf.sky >= 0) {
                SkyPreset.shuffle();
            }
        }
    }

    static List<NowPlaying.Line> lines(Lyrics l) {
        if (l == null || l.lines.isEmpty()) {
            return List.of();
        }

        List<NowPlaying.Line> out = new ArrayList<>();
        for (Lyrics.Line line : l.lines) {
            int n = line.words.size();
            String[] words = new String[n];
            long[] times = new long[n];
            for (int i = 0; i < n; i++) {
                words[i] = Lyrics.show(line.words.get(i).text);
                times[i] = line.words.get(i).start;
            }

            out.add(
                    new NowPlaying.Line(
                            line.start, Lyrics.show(line.text), words, n > 0 ? times : null));
        }

        return Collections.unmodifiableList(out);
    }

    public boolean hasTrack() {
        return this.trackId != null;
    }

    public NowPlaying withLyrics(List<NowPlaying.Line> l, NowPlaying.LyricsState st) {
        return new NowPlaying(
                this.trackId,
                this.title,
                this.artist,
                this.durationMs,
                this.playing,
                this.progressMs,
                this.fetchedAt,
                l,
                st);
    }

    public long progressNow(int offsetMs) {
        long p =
                this.progressMs
                        + (this.playing ? System.currentTimeMillis() - this.fetchedAt : 0L)
                        + offsetMs;
        if (this.durationMs > 0L) {
            p = Math.min(p, this.durationMs);
        }

        return Math.max(0L, p);
    }

    public int lineIndex(long progress) {
        int idx = -1;
        int i = 0;

        while (i < this.lines.size() && this.lines.get(i).timeMs() <= progress) {
            idx = i++;
        }

        return idx;
    }

    public long lineEnd(int idx) {
        if (idx >= 0 && idx < this.lines.size()) {
            long start = this.lines.get(idx).timeMs();
            long next =
                    idx + 1 < this.lines.size() ? this.lines.get(idx + 1).timeMs() : start + 5000L;
            return Math.min(next, start + 7000L);
        } else {
            return 0L;
        }
    }

    static int syllables(String w) {
        int n = 0;
        boolean prev = false;

        for (int i = 0; i < w.length(); i++) {
            boolean v = "aeiouyäöüAEIOUYÄÖÜ".indexOf(w.charAt(i)) >= 0;
            if (v && !prev) {
                n++;
            }

            prev = v;
        }

        return Math.max(1, n);
    }

    public float wordPosition(int idx, long progress) {
        LyricTiming t = LyricTiming.of(this.lines);
        return t == null ? -1.0F : t.wordPosition(idx, progress, this.lineEnd(idx));
    }

    public float beatPulse(long progress) {
        LyricTiming t = LyricTiming.of(this.lines);
        return t != null && this.playing ? t.beatPulse(progress) : 0.0F;
    }

    public record Line(long timeMs, String text, String[] words, long[] wordTimes) {
        public Line(long timeMs, String text, String[] words) {
            this(timeMs, text, words, null);
        }
    }

    public enum LyricsState {
        NONE,
        LOADING,
        SYNCED,
        NOT_FOUND,
        ERROR;
    }
}
