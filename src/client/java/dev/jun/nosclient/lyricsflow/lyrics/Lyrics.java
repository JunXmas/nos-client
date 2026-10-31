package dev.jun.nosclient.lyricsflow.lyrics;

import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

public final class Lyrics {
   private static final ThreadLocal<Double> SONG_RATE = ThreadLocal.withInitial(() -> 4.5);
   public final List<Lyrics.Line> lines;
   public final boolean synced;
   public final String source;
   public final boolean wordSynced;

   public static String show(String s) {
      return s != null && ConfigManager.get().allCaps ? upper(s) : s;
   }

   public static String upper(String s) {
      if (s == null) {
         return null;
      }

      Locale loc = Locale.ROOT;

      try {
         String lang = Minecraft.getInstance().options.languageCode;
         if (lang != null && (lang.startsWith("tr") || lang.startsWith("az"))) {
            loc = Locale.forLanguageTag("tr");
         }
      } catch (Throwable var3) {
      }

      return s.toUpperCase(loc);
   }

   static int syllables(String w) {
      int n = 0;
      boolean prev = false;

      for (int i = 0; i < w.length(); i++) {
         char c = Character.toLowerCase(w.charAt(i));
         boolean v = "aeiouyıöüâêîôûàèìòùáéíóúäëïãõåæøœ".indexOf(c) >= 0;
         if (v && !prev) {
            n++;
         }

         prev = v;
      }

      if (n == 0) {
         int letters = 0;

         for (int i = 0; i < w.length(); i++) {
            if (Character.isLetterOrDigit(w.charAt(i))) {
               letters++;
            }
         }

         n = Math.max(1, (int)Math.round(letters / 2.5));
      }

      return n;
   }

   private static double songRate(List<long[]> times, List<String> texts, long durationMs) {
      List<Double> r = new ArrayList<>();

      for (int i = 0; i < texts.size(); i++) {
         long s = times.get(i)[0];
         long e = ((long[])times.get(i)).length > 1 ? times.get(i)[1] : (i + 1 < texts.size() ? times.get(i + 1)[0] : -1L);
         if (e > s) {
            long gap = e - s;
            if (gap >= 700L && gap <= 8000L) {
               String[] parts = texts.get(i).trim().split("\\s+");
               if (parts.length >= 2) {
                  int syl = 0;

                  for (String p : parts) {
                     syl += syllables(p);
                  }

                  r.add(syl / (gap / 1000.0));
               }
            }
         }
      }

      if (r.size() < 3) {
         return 4.5;
      }

      Collections.sort(r);
      double busy = r.get(Math.min(r.size() - 1, (int)(r.size() * 0.75)));
      return Math.max(2.5, Math.min(10.0, busy * 1.05));
   }

   public Lyrics(List<long[]> times, List<String> texts, boolean synced, String source, long durationMs) {
      this(times, texts, synced, source, durationMs, null);
   }

   public Lyrics(List<long[]> times, List<String> texts, boolean synced, String source, long durationMs, List<List<Lyrics.Word>> words) {
      List<Lyrics.Line> l = new ArrayList<>();
      SONG_RATE.set(songRate(times, texts, durationMs));

      for (int i = 0; i < texts.size(); i++) {
         long s = times.get(i)[0];
         long e = ((long[])times.get(i)).length > 1
            ? times.get(i)[1]
            : (i + 1 < texts.size() ? times.get(i + 1)[0] : (durationMs > s ? Math.min(durationMs, s + 6000L) : s + 5000L));
         if (e - s > 9000L) {
            e = s + 9000L;
         }

         List<Lyrics.Word> w = words != null && i < words.size() ? words.get(i) : null;
         if (w != null && !w.isEmpty()) {
            e = Math.max(e, w.get(w.size() - 1).end);
         }

         l.add(new Lyrics.Line(l.size(), texts.get(i), s, e, w));
      }

      this.lines = Collections.unmodifiableList(l);
      this.synced = synced;
      this.source = source;
      boolean ws = words != null;
      if (ws) {
         for (List<Lyrics.Word> w : words) {
            if (w == null || w.isEmpty()) {
               ws = false;
               break;
            }
         }
      }

      this.wordSynced = ws && !l.isEmpty();
   }

   public Lyrics.Line lineAt(long t) {
      Lyrics.Line cur = null;

      for (Lyrics.Line l : this.lines) {
         if (l.start > t) {
            break;
         }

         cur = l;
      }

      return cur != null && t > cur.end ? null : cur;
   }

   public Lyrics.Line lastStartedBefore(long t) {
      Lyrics.Line cur = null;

      for (Lyrics.Line l : this.lines) {
         if (l.start > t) {
            break;
         }

         cur = l;
      }

      return cur;
   }

   public static final class Line {
      public final int index;
      public final String text;
      public final long start;
      public final long end;
      public final List<Lyrics.Word> words;
      public final boolean estimated;

      Line(int index, String text, long start, long end) {
         this(index, text, start, end, null);
      }

      Line(int index, String text, long start, long end, List<Lyrics.Word> words) {
         this.index = index;
         this.text = text;
         this.start = start;
         this.end = end;
         this.estimated = words == null || words.isEmpty();
         this.words = !this.estimated ? Collections.unmodifiableList(new ArrayList<>(words)) : splitWords(text, start, end);
      }

      public void retime(double k) {
         if (this.estimated && !this.words.isEmpty()) {
            long limit = this.end - 40L;

            for (Lyrics.Word w : this.words) {
               w.start = Math.min(limit, this.start + Math.round((w.origStart - this.start) * k));
               w.end = Math.min(limit, this.start + Math.round((w.origEnd - this.start) * k));
            }
         }
      }

      public long guessedEnd() {
         return this.words.isEmpty() ? this.end : this.words.get(this.words.size() - 1).origEnd;
      }

      private static List<Lyrics.Word> splitWords(String text, long start, long end) {
         String[] parts = text.trim().split("\\s+");
         List<Lyrics.Word> out = new ArrayList<>();
         if (parts.length != 0 && !parts[0].isEmpty()) {
            long gap = Math.max(300L, end - start);
            int[] syl = new int[parts.length];
            int total = 0;

            for (int i = 0; i < parts.length; i++) {
               syl[i] = Lyrics.syllables(parts[i]);
               total += syl[i];
            }

            double rate = Lyrics.SONG_RATE.get();
            double lineRate = total / (gap / 1000.0);
            long lead = 0L;
            long sing;
            if (lineRate >= rate * 0.9) {
               sing = (long)(gap * 0.74);
            } else {
               sing = Math.max((long)(total / rate * 1000.0), (long)(gap * 0.6));
            }

            sing = Math.max(Math.min(350L, gap), Math.min(sing, (long)(gap * 0.82)));
            double weight = 0.0;

            for (int n : syl) {
               weight += n + 0.6;
            }

            double t = start + lead;
            long early = 60L;

            for (int i = 0; i < parts.length; i++) {
               double d = sing * ((syl[i] + 0.6) / weight);
               long prev = out.isEmpty() ? start - 1000L : out.get(out.size() - 1).start;
               long ws = i == 0 ? start : Math.max(prev + 130L, (long)t - 60L);
               out.add(new Lyrics.Word(parts[i], ws, Math.max(ws + 60L, (long)(t + d) - 60L)));
               t += d;
            }

            return Collections.unmodifiableList(out);
         } else {
            return out;
         }
      }

      public static Lyrics.Line notes(int index, String text, long start, long end) {
         return new Lyrics.Line(index, text, start, end);
      }

      public int wordAt(long t) {
         if (!this.words.isEmpty() && t >= this.start) {
            for (int i = this.words.size() - 1; i >= 0; i--) {
               if (t >= this.words.get(i).start) {
                  return i;
               }
            }

            return -1;
         } else {
            return -1;
         }
      }
   }

   public static final class Word {
      public final String text;
      public volatile long start;
      public volatile long end;
      final long origStart;
      final long origEnd;

      Word(String text, long start, long end) {
         this.text = text;
         this.start = start;
         this.end = end;
         this.origStart = start;
         this.origEnd = end;
      }

      public static Lyrics.Word of(String text, long start, long end) {
         return new Lyrics.Word(text, start, end);
      }
   }
}
