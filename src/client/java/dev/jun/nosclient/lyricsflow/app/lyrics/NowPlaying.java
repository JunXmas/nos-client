package dev.jun.nosclient.lyricsflow.app.lyrics;

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
   public static final NowPlaying EMPTY = new NowPlaying(null, "", "", 0L, false, 0L, 0L, Collections.emptyList(), NowPlaying.LyricsState.NONE);

   public NowPlaying(
      String trackId,
      String title,
      String artist,
      long durationMs,
      boolean playing,
      long progressMs,
      long fetchedAt,
      List<NowPlaying.Line> lines,
      NowPlaying.LyricsState lyricsState
   ) {
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

   public boolean hasTrack() {
      return this.trackId != null;
   }

   public NowPlaying withLyrics(List<NowPlaying.Line> l, NowPlaying.LyricsState st) {
      return new NowPlaying(this.trackId, this.title, this.artist, this.durationMs, this.playing, this.progressMs, this.fetchedAt, l, st);
   }

   public long progressNow(int offsetMs) {
      long p = this.progressMs + (this.playing ? System.currentTimeMillis() - this.fetchedAt : 0L) + offsetMs;
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
         long next = idx + 1 < this.lines.size() ? this.lines.get(idx + 1).timeMs() : start + 5000L;
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
