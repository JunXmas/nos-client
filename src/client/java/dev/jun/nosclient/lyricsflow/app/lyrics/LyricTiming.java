package dev.jun.nosclient.lyricsflow.app.lyrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class LyricTiming {
   public final long[][] wordStart;
   public final double beatMs;
   public final double beatPhase;
   public final float confidence;
   public final float sylPerSec;
   private static final Map<List<NowPlaying.Line>, LyricTiming> CACHE = Collections.synchronizedMap(new IdentityHashMap<>());

   private LyricTiming(long[][] ws, double beat, double phase, float conf, float sps) {
      this.wordStart = ws;
      this.beatMs = beat;
      this.beatPhase = phase;
      this.confidence = conf;
      this.sylPerSec = sps;
   }

   public static LyricTiming of(List<NowPlaying.Line> lines) {
      if (lines != null && !lines.isEmpty()) {
         LyricTiming t = CACHE.get(lines);
         if (t == null) {
            t = build(lines);
            if (CACHE.size() > 20) {
               CACHE.clear();
            }

            CACHE.put(lines, t);
         }

         return t;
      } else {
         return null;
      }
   }

   static float weight(String w) {
      float s = NowPlaying.syllables(w);
      char last = w.charAt(w.length() - 1);
      if (last == ',' || last == '.' || last == '!' || last == '?' || last == ')') {
         s += 0.6F;
      }

      return s;
   }

   private static long nextStart(List<NowPlaying.Line> lines, int i) {
      return i + 1 < lines.size() ? lines.get(i + 1).timeMs() : lines.get(i).timeMs() + 5000L;
   }

   private static LyricTiming build(List<NowPlaying.Line> lines) {
      int n = lines.size();
      List<Float> rates = new ArrayList<>();

      for (int i = 0; i < n; i++) {
         NowPlaying.Line l = lines.get(i);
         if (l.words().length >= 3) {
            long gap = nextStart(lines, i) - l.timeMs();
            if (gap >= 900L && gap <= 9000L) {
               float syl = 0.0F;

               for (String w : l.words()) {
                  syl += weight(w);
               }

               rates.add(syl / ((float)gap * 0.85F / 1000.0F));
            }
         }
      }

      float sps = 4.0F;
      if (!rates.isEmpty()) {
         Collections.sort(rates);
         sps = rates.get(rates.size() / 2);
      }

      sps = Math.max(2.2F, Math.min(8.0F, sps));
      List<Long> starts = new ArrayList<>();

      for (NowPlaying.Line l : lines) {
         if (!l.text().isBlank()) {
            starts.add(l.timeMs());
         }
      }

      double bestBeat = 0.0;
      double bestPhase = 0.0;
      float bestScore = 0.0F;
      if (starts.size() >= 6) {
         for (double bpm = 60.0; bpm <= 180.0; bpm += 0.25) {
            double p = 60000.0 / bpm;
            double sx = 0.0;
            double sy = 0.0;

            for (long t : starts) {
               double a = (Math.PI * 2) * (t % p) / p;
               sx += Math.cos(a);
               sy += Math.sin(a);
            }

            float r = (float)(Math.sqrt(sx * sx + sy * sy) / starts.size());
            float pref = bpm >= 80.0 && bpm <= 150.0 ? 1.0F : 0.93F;
            if (r * pref > bestScore) {
               bestScore = r * pref;
               bestBeat = p;
               double ang = Math.atan2(sy, sx);
               if (ang < 0.0) {
                  ang += Math.PI * 2;
               }

               bestPhase = ang / (Math.PI * 2) * p;
            }
         }
      }

      boolean useGrid = bestScore >= 0.45F && bestBeat > 0.0;
      long[][] ws = new long[n][];

      for (int ix = 0; ix < n; ix++) {
         NowPlaying.Line lx = lines.get(ix);
         String[] words = lx.words();
         ws[ix] = new long[words.length];
         if (words.length != 0) {
            if (lx.wordTimes() != null && lx.wordTimes().length == words.length) {
               ws[ix] = (long[])lx.wordTimes().clone();
            } else {
               long start = lx.timeMs();
               long gap = Math.min(nextStart(lines, ix) - start, 7000L);
               float total = 0.0F;

               for (String w : words) {
                  total += weight(w);
               }

               double dur = Math.min(gap * 0.92, Math.max(300.0, total / sps * 1000.0));
               double acc = 0.0;
               double grid = 0.0;
               if (useGrid) {
                  double perWord = dur / words.length;
                  grid = perWord < bestBeat / 2.0 * 0.75 ? bestBeat / 4.0 : bestBeat / 2.0;
               }

               long prev = start - 1L;

               for (int k = 0; k < words.length; k++) {
                  double t = start + acc / total * dur;
                  if (k == 0) {
                     t = start;
                  } else if (grid > 0.0) {
                     double rel = t - bestPhase;
                     double snapped = bestPhase + Math.round(rel / grid) * grid;
                     if (Math.abs(snapped - t) < grid * 0.6) {
                        t = snapped;
                     }
                  }

                  long ti = Math.max(prev + 40L, Math.round(t));
                  ti = Math.min(ti, start + (long)(gap * 0.97));
                  ws[ix][k] = ti;
                  prev = ti;
                  acc += weight(words[k]);
               }
            }
         }
      }

      return new LyricTiming(ws, useGrid ? bestBeat : 0.0, bestPhase, useGrid ? Math.min(1.0F, bestScore) : 0.0F, sps);
   }

   public float wordPosition(int line, long progress, long lineEnd) {
      if (line >= 0 && line < this.wordStart.length) {
         long[] s = this.wordStart[line];
         if (s.length == 0) {
            return -1.0F;
         }

         int k = Arrays.binarySearch(s, progress);
         if (k < 0) {
            k = -k - 2;
         }

         if (k < 0) {
            return 0.0F;
         } else if (k >= s.length - 1) {
            long end = Math.max(s[s.length - 1] + 200L, Math.min(lineEnd, s[s.length - 1] + 900L));
            return s.length - 1 + Math.min(0.999F, (float)(progress - s[s.length - 1]) / (float)(end - s[s.length - 1]));
         } else {
            return k + (float)(progress - s[k]) / (float)Math.max(1L, s[k + 1] - s[k]);
         }
      } else {
         return -1.0F;
      }
   }

   public float beatPulse(long progress) {
      if (this.beatMs <= 0.0) {
         return 0.0F;
      }

      double ph = ((progress - this.beatPhase) % this.beatMs + this.beatMs) % this.beatMs;
      return (float)Math.exp(-ph / (this.beatMs * 0.18)) * this.confidence;
   }
}
