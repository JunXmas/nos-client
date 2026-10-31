package dev.jun.nosclient.lyricsflow.app.sky;

import dev.jun.nosclient.lyricsflow.app.config.Config;
import dev.jun.nosclient.lyricsflow.app.hud.Gfx;
import dev.jun.nosclient.lyricsflow.app.lyrics.NowPlaying;
import dev.jun.nosclient.lyricsflow.app.lyrics.SpotifyClient;
import dev.jun.nosclient.lyricsflow.app.util.AutoPerf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class FloatWords {
   private static final float[][] SLOTS = new float[][]{
      {-22.0F, 7.0F},
      {20.0F, -4.0F},
      {-6.0F, -12.0F},
      {16.0F, 12.0F},
      {-26.0F, -8.0F},
      {8.0F, 4.0F},
      {-14.0F, 14.0F},
      {26.0F, 6.0F},
      {2.0F, -16.0F},
      {-18.0F, 0.0F},
      {12.0F, -10.0F},
      {-8.0F, 9.0F}
   };
   private static final float BLOCKS_PER_PX = 0.062F;
   private static final List<FloatWords.Word> WORDS = new ArrayList<>();
   private static final Random RNG = new Random();
   private static String lastTrack = "";
   private static int lastLine = -1;
   private static int lastChunk = -1;
   private static int lastSlot = -1;
   private static int prevSlot = -1;
   private static String[] chunks = new String[0];
   private static int[] chunkOf = new int[0];
   private static final Map<String, Component> COMP = new HashMap<>();
   private static final Matrix4f M0 = new Matrix4f();
   private static final Matrix4f TMP = new Matrix4f();
   private static final Matrix4f WM = new Matrix4f();
   private static final Matrix4f SCR = new Matrix4f();

   private FloatWords() {
   }

   public static void clear() {
      WORDS.clear();
      lastLine = -1;
      lastChunk = -1;
   }

   public static void renderWorld(Minecraft mc, Config c, MultiBufferSource buffers, Matrix4f base, Vec3 cam, float pt, boolean through) {
      if (mc != null && c != null && base != null && cam != null) {
         if (c.lyricsSky && c.lyricsStyle == 1 && !mc.options.hideGui && mc.player != null && mc.level != null) {
            long now = System.currentTimeMillis();
            boolean live = false;
            NowPlaying np = SpotifyClient.now();
            if (np != null && np.hasTrack() && np.playing && !np.lines.isEmpty()) {
               String tid = String.valueOf(np.trackId);
               if (!tid.equals(lastTrack)) {
                  lastTrack = tid;
                  WORDS.clear();
                  lastLine = -1;
                  lastChunk = -1;
               }

               long progress = np.progressNow(c.lyricsOffsetMs + 150);
               int li = np.lineIndex(progress);
               if (li >= 0 && li < np.lines.size() && progress <= np.lineEnd(li) + 450L) {
                  live = update(mc, c, np, li, progress, cam, pt, now);
               }
            }

            if (!live) {
               for (FloatWords.Word w : WORDS) {
                  if (w.gone == 0L) {
                     w.gone = now;
                  }
               }
            }

            float fadeMs = 2200.0F;
            WORDS.removeIf(wx -> wx.gone != 0L && (float)(now - wx.gone) > 2200.0F);
            draw(mc, c, buffers, base, cam, pt, now, 2200.0F, through, np);
         } else {
            clear();
         }
      }
   }

   private static boolean update(Minecraft mc, Config c, NowPlaying np, int li, long progress, Vec3 cam, float pt, long now) {
      NowPlaying.Line line = np.lines.get(li);
      String text = line.text();
      if (text != null && !text.isBlank()) {
         String[] words = line.words();
         if (words == null || words.length == 0) {
            words = text.trim().split("\\s+");
         }

         if (li != lastLine) {
            lastLine = li;
            lastChunk = -1;
            buildChunks(words);
         }

         if (chunks.length == 0) {
            return false;
         }

         float wp = np.wordPosition(li, progress);
         int aw;
         if (wp >= 0.0F) {
            aw = Math.min(words.length - 1, (int)Math.floor(wp));
         } else {
            long start = line.timeMs();
            long end = Math.max(start + 1L, np.lineEnd(li));
            aw = (int)Math.floor((float)(progress - start) / (float)(end - start) * words.length);
            aw = Math.max(0, Math.min(words.length - 1, aw));
         }

         int ac = Math.max(0, Math.min(chunks.length - 1, chunkOf[Math.min(aw, chunkOf.length - 1)]));
         if (ac > lastChunk) {
            if (ac - lastChunk > 2) {
               lastChunk = ac - 1;
            }

            while (lastChunk < ac) {
               lastChunk++;
               spawn(chunks[lastChunk], mc, c, cam, pt, now);
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static float sz(Config c) {
      return c.lyricsSize / 1.7F;
   }

   private static void spawn(String text, Minecraft mc, Config c, Vec3 cam, float pt, long now) {
      if (text != null && !text.isEmpty()) {
         float yaw = mc.player.getViewYRot(pt);
         float el0 = -mc.player.getViewXRot(pt) + 12.0F;
         String txt = text.toUpperCase(Locale.ROOT);
         int widthPx = Gfx.w(txt, Gfx.LYRICS);
         double bestScore = Double.MAX_VALUE;
         double bAz = 0.0;
         double bEl = 0.0;
         double bDist = 8.0;
         double bSize = 1.0;
         double bHw = 1.0;
         double bHh = 1.0;
         int bestSlot = 0;

         for (int attempt = 0; attempt < 28; attempt++) {
            int tries = 0;

            int slot;
            do {
               slot = RNG.nextInt(SLOTS.length);
               tries++;
            } while ((slot == lastSlot || slot == prevSlot) && tries < 12);

            double az = Math.toRadians(yaw + (SLOTS[slot][0] + (RNG.nextFloat() - 0.5F) * 7.0F));
            double el = Math.toRadians(el0 + (SLOTS[slot][1] + (RNG.nextFloat() - 0.5F) * 6.0F));
            double dist = 6.5 + RNG.nextDouble() * 8.5;
            float size = 0.82F + RNG.nextFloat() * 0.5F;
            double hw = widthPx * 0.062F * sz(c) * size * 0.5 * 0.95;
            double hh = 0.682F * sz(c) * size * 0.5;
            double score = overlapScore(az, el, dist, hw, hh, cam);
            if (score < bestScore) {
               bestScore = score;
               bAz = az;
               bEl = el;
               bDist = dist;
               bSize = size;
               bHw = hw;
               bHh = hh;
               bestSlot = slot;
            }

            if (score <= 0.0) {
               break;
            }
         }

         prevSlot = lastSlot;
         lastSlot = bestSlot;
         float sizex = (float)bSize;
         double dx = -Math.sin(bAz) * Math.cos(bEl);
         double dy = Math.sin(bEl);
         double dz = Math.cos(bAz) * Math.cos(bEl);
         double x = cam.x + dx * bDist;
         double y = cam.y + dy * bDist;
         double z = cam.z + dz * bDist;
         double h = Math.sqrt(dx * dx + dz * dz);
         double nx;
         double nz;
         if (h < 1.0E-4) {
            double yr = Math.toRadians(yaw);
            nx = Math.sin(yr);
            nz = -Math.cos(yr);
         } else {
            nx = -dx / h;
            nz = -dz / h;
         }

         float tilt = (float)Math.toRadians((RNG.nextBoolean() ? 1 : -1) * (10.0F + RNG.nextFloat() * 22.0F));
         float pitch = (float)Math.toRadians((RNG.nextFloat() - 0.5F) * 14.0F);
         float roll = (float)Math.toRadians((RNG.nextFloat() - 0.5F) * 8.0F);

         for (FloatWords.Word o : WORDS) {
            if (o.gone == 0L) {
               o.gone = now;
            }
         }

         WORDS.add(new FloatWords.Word(text, x, y, z, nx, nz, tilt, pitch, roll, sizex, (float)bHw, (float)bHh, now));
         int max = 7;

         while (WORDS.size() > max) {
            WORDS.remove(0);
         }
      }
   }

   private static double overlapScore(double az, double el, double dist, double hw, double hh, Vec3 cam) {
      double score = 0.0;
      double angW = hw / dist;
      double angH = hh / dist;

      for (FloatWords.Word o : WORDS) {
         double ex = o.x - cam.x;
         double ey = o.y - cam.y;
         double ez = o.z - cam.z;
         double d = Math.sqrt(ex * ex + ey * ey + ez * ez);
         if (!(d < 0.5)) {
            double oAz = Math.atan2(-ex, ez);
            double oEl = Math.asin(Math.max(-1.0, Math.min(1.0, ey / d)));
            double dAz = Math.abs(((az - oAz) % (Math.PI * 2) + (Math.PI * 3)) % (Math.PI * 2) - Math.PI) * Math.cos(el);
            double dEl = Math.abs(el - oEl);
            double needW = angW + o.halfW / d + 0.035;
            double needH = angH + o.halfH / d + 0.03;
            if (dAz < needW && dEl < needH) {
               score += 1.0 - dAz / needW + (1.0 - dEl / needH) + 0.5;
            }
         }
      }

      return score;
   }

   private static void buildChunks(String[] words) {
      List<String> list = new ArrayList<>();
      int[] map = new int[words.length];
      int i = 0;

      while (i < words.length) {
         String a = clean(words[i]);
         if (a.isEmpty()) {
            map[i] = list.isEmpty() ? 0 : list.size() - 1;
            i++;
         } else {
            int idx = list.size();
            String chunk = a;
            int used = 1;
            if (a.length() <= 3 && i + 1 < words.length) {
               String b = clean(words[i + 1]);
               if (!b.isEmpty()) {
                  chunk = a + " " + b;
                  used = 2;
                  map[i + 1] = idx;
               }
            }

            map[i] = idx;
            list.add(chunk);
            i += used;
         }
      }

      chunks = list.toArray(new String[0]);
      chunkOf = map;
   }

   private static String clean(String s) {
      if (s == null) {
         return "";
      }

      int a = 0;
      int b = s.length();

      while (a < b && !keep(s.charAt(a))) {
         a++;
      }

      while (b > a && !keep(s.charAt(b - 1))) {
         b--;
      }

      return s.substring(a, b);
   }

   private static boolean keep(char ch) {
      return Character.isLetterOrDigit(ch) || ch == '*' || ch == '\'';
   }

   private static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
   }

   private static float easeOut(float t) {
      float u = 1.0F - t;
      return 1.0F - u * u * u;
   }

   private static Component comp(String s, int fontIdx) {
      if (COMP.size() > 600) {
         COMP.clear();
      }

      return COMP.computeIfAbsent(fontIdx + "|" + s, k -> Gfx.comp(s, Gfx.LYRICS));
   }

   private static void draw(
      Minecraft mc, Config c, MultiBufferSource buffers, Matrix4f base, Vec3 cam, float pt, long now, float fadeMs, boolean through, NowPlaying np
   ) {
      if (!WORDS.isEmpty()) {
         Font font = mc.font;
         int fontIdx = c.lyricsFont;
         int baseCol = c.lyricsColor >= 0 ? WorldSky.LYRIC_COLORS[Math.floorMod(c.lyricsColor, WorldSky.LYRIC_COLORS.length)] : 16777215;
         boolean glow = c.lyricsGlow && AutoPerf.allow(3);
         int q = AutoPerf.quality();
         int glowN = q <= 0 ? 0 : (q == 1 ? 2 : 3);
         DisplayMode mode = through ? DisplayMode.SEE_THROUGH : DisplayMode.NORMAL;
         double yr = Math.toRadians(mc.player.getViewYRot(pt));
         double pr = Math.toRadians(mc.player.getViewXRot(pt));
         double vfx = -Math.sin(yr) * Math.cos(pr);
         double vfy = -Math.sin(pr);
         double vfz = Math.cos(yr) * Math.cos(pr);
         float beat = c.fxBeatPulse && np != null && np.hasTrack() ? np.beatPulse(np.progressNow(c.lyricsOffsetMs)) : 0.0F;
         Iterator var29 = WORDS.iterator();

         while (true) {
            FloatWords.Word w;
            long age;
            float a;
            float bright;
            boolean current;
            while (true) {
               if (!var29.hasNext()) {
                  return;
               }

               w = (FloatWords.Word)var29.next();
               age = now - w.born;
               a = clamp01((float)age / 140.0F) * c.lyricsOpacity;
               bright = 1.0F;
               current = w.gone == 0L;
               if (current) {
                  break;
               }

               float k = (float)(now - w.gone) / fadeMs;
               if (!(k >= 1.0F)) {
                  a *= 1.0F - k * k;
                  bright = 1.0F - 0.5F * easeOut(clamp01((float)(now - w.gone) / 240.0F));
                  break;
               }
            }

            if (!(a < 0.03F)) {
               double drift = Math.min(age / 1000.0 * 0.25, 1.0);
               double rx = w.x - cam.x;
               double ry = w.y + drift - cam.y;
               double rz = w.z - cam.z;
               if (!(rx * vfx + ry * vfy + rz * vfz < 0.3)) {
                  double ct = Math.cos(w.tilt);
                  double st = Math.sin(w.tilt);
                  double nx = w.nx * ct - w.nz * st;
                  double nz = w.nx * st + w.nz * ct;
                  double Ry = 0.0;
                  double Rz = -nx;
                  double Ux = 0.0;
                  double Uy = 1.0;
                  double Uz = 0.0;
                  double Ny = 0.0;
                  double cp = Math.cos(w.pitch);
                  double sp = Math.sin(w.pitch);
                  double U2x = Ux * cp + nx * sp;
                  double U2y = Uy * cp + Ny * sp;
                  double U2z = Uz * cp + nz * sp;
                  double N2x = nx * cp - Ux * sp;
                  double N2y = Ny * cp - Uy * sp;
                  double N2z = nz * cp - Uz * sp;
                  double cr = Math.cos(w.roll);
                  double sr = Math.sin(w.roll);
                  double R3x = nz * cr + U2x * sr;
                  double R3y = Ry * cr + U2y * sr;
                  double R3z = Rz * cr + U2z * sr;
                  double U3x = U2x * cr - nz * sr;
                  double U3y = U2y * cr - Ry * sr;
                  double U3z = U2z * cr - Rz * sr;
                  if (rx * N2x + ry * N2y + rz * N2z > 0.0) {
                     R3x = -R3x;
                     R3y = -R3y;
                     R3z = -R3z;
                     N2x = -N2x;
                     N2y = -N2y;
                     N2z = -N2z;
                  }

                  float pop = 1.0F + 0.22F * (1.0F - easeOut(clamp01((float)age / 240.0F)));
                  float kk = 0.062F * sz(c) * w.size * pop;
                  Matrix4f m0 = M0.set(base)
                     .mul(
                        TMP.set(
                           (float)R3x * kk,
                           (float)R3y * kk,
                           (float)R3z * kk,
                           0.0F,
                           (float)(-U3x) * kk,
                           (float)(-U3y) * kk,
                           (float)(-U3z) * kk,
                           0.0F,
                           (float)N2x * kk,
                           (float)N2y * kk,
                           (float)N2z * kk,
                           0.0F,
                           (float)rx,
                           (float)ry,
                           (float)rz,
                           1.0F
                        )
                     );
                  String txt = w.text.toUpperCase(Locale.ROOT);
                  int width = Gfx.w(txt, Gfx.LYRICS);
                  int shownLen = txt.length();
                  shownLen = Math.max(1, (int)Math.ceil(txt.length() * clamp01((float)age / 230.0F)));
                  Component shown = comp(txt.substring(0, shownLen), fontIdx);
                  Matrix4f m = WM.set(m0);
                  if (current && beat > 0.01F) {
                     float bs = 1.0F + 0.06F * beat;
                     m.scale(bs, bs, 1.0F);
                  }

                  m.translate(-width / 2.0F, -4.5F, 0.0F);
                  int col = Gfx.lerpColor(9211040, baseCol, bright);
                  int glowBase = c.lyricsGlowColor >= 0 ? WorldSky.LYRIC_COLORS[Math.floorMod(c.lyricsGlowColor, WorldSky.LYRIC_COLORS.length)] : baseCol;
                  int gcol = Gfx.lerpColor(9211040, glowBase, bright);
                  put(font, buffers, m, shown, 0.7F, 0.9F, -0.5F, 328460, a * 0.55F, mode);
                  if (glow) {
                     float ga = a * bright * 0.2F * c.lyricsGlowStrength * (current ? 1.3F : 0.8F);
                     if (glowN > 0) {
                        for (int i = 0; i < glowN; i++) {
                           double ang = i * Math.PI * 2.0 / glowN + (Math.PI / 8);
                           put(font, buffers, m, shown, (float)Math.cos(ang) * 1.1F, (float)Math.sin(ang) * 1.1F, 0.1F, gcol, ga * 6.0F / glowN, mode);
                        }
                     } else {
                        put(font, buffers, m, shown, 0.0F, 0.6F, 0.1F, gcol, ga * 1.6F, mode);
                     }
                  }

                  put(font, buffers, m, shown, 0.0F, 0.0F, 0.3F, col, a, mode);
               }
            }
         }
      }
   }

   private static void put(Font font, MultiBufferSource buffers, Matrix4f m, Component text, float x, float y, float z, int rgb, float a, DisplayMode mode) {
      if (!(a <= 0.03F)) {
         Matrix4f mm = SCR.set(m).translate(x, y, z);
         font.drawInBatch(text, 0.0F, 0.0F, Gfx.argbRaw(rgb, a), false, mm, buffers, mode, 0, 15728880);
      }
   }

   private static final class Word {
      final String text;
      final double x;
      final double y;
      final double z;
      final double nx;
      final double nz;
      final float tilt;
      final float pitch;
      final float roll;
      final float size;
      final float halfW;
      final float halfH;
      final long born;
      long gone = 0L;

      Word(
         String text, double x, double y, double z, double nx, double nz, float tilt, float pitch, float roll, float size, float halfW, float halfH, long born
      ) {
         this.text = text;
         this.x = x;
         this.y = y;
         this.z = z;
         this.nx = nx;
         this.nz = nz;
         this.tilt = tilt;
         this.pitch = pitch;
         this.roll = roll;
         this.size = size;
         this.halfW = halfW;
         this.halfH = halfH;
         this.born = born;
      }
   }
}
