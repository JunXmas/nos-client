package dev.jun.nosclient.lyricsflow.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import dev.jun.nosclient.lyricsflow.lyrics.Lyrics;
import dev.jun.nosclient.lyricsflow.media.MediaManager;
import dev.jun.nosclient.lyricsflow.mixin.GameRendererAccessor;
import dev.jun.nosclient.lyricsflow.perf.AutoPerf;
import dev.jun.nosclient.lyricsflow.util.Col;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class AirLyrics {
   private static final float GLYPH = 9.25F;
   private static final int LIGHT = 15728880;
   private static final Random RND = new Random();
   private static long lastNs;
   private static boolean pit;
   private static long pitSince;
   private static long pitCheck;
   private static long lastRender;
   private static long seq = -1L;
   private static AirLyrics.SkyLine active;
   private static final List<AirLyrics.SkyLine> exiting = new ArrayList<>();
   private static Lyrics.Line activeLine;
   private static double lastSide;
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
   private static final float BLOCKS_PER_UNIT = 0.062F;
   private static final List<AirLyrics.Word> WORDS = new ArrayList<>();
   private static Lyrics.Line fLine;
   private static int fChunk = -1;
   private static int lastSlot = -1;
   private static int prevSlot = -1;
   private static String[] fChunks = new String[0];
   private static int[] fChunkOf = new int[0];

   private AirLyrics() {
   }

   static boolean handles(LFConfig c) {
      return c.lyricsStyle == LFConfig.LyricsStyle.SKY;
   }

   static boolean inPit() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return false;
      }

      long now = System.currentTimeMillis();
      if (now - pitCheck < 100L) {
         return pit;
      }

      pitCheck = now;
      boolean covered = !mc.level.canSeeSky(BlockPos.containing(Proj.cx, Proj.cy, Proj.cz).above());
      if (covered != pit) {
         if (pitSince == 0L) {
            pitSince = now;
         }

         if (now - pitSince > 1000L) {
            pit = covered;
            pitSince = 0L;
         }
      } else {
         pitSince = 0L;
      }

      return pit;
   }

   static void clear() {
      active = null;
      exiting.clear();
      activeLine = null;
      WORDS.clear();
      fLine = null;
      fChunk = -1;
   }

   static void render(PoseStack ps, MultiBufferSource buffers, DisplayMode mode) {
      Minecraft mc = Minecraft.getInstance();
      if (System.currentTimeMillis() - lastRender > 500L) {
         clear();
      }

      lastRender = System.currentTimeMillis();
      LFConfig c = ConfigManager.get();
      MediaManager mm = MediaManager.get();
      long ns = System.nanoTime();
      if (lastNs == 0L) {
         float dt = 0.016F;
      } else {
         Math.min(0.1F, (float)(ns - lastNs) / 1.0E9F);
      }

      lastNs = ns;
      if (mm.songSeq() != seq) {
         seq = mm.songSeq();
         clear();
      }

      Matrix4f base = ps.last().pose();
      float alpha = Math.max(0.0F, Math.min(1.0F, c.textOpacity)) * Hud.focusA;
      if (c.lyricsStyle == LFConfig.LyricsStyle.SKY) {
         WORDS.clear();
      } else {
         active = null;
         exiting.clear();
         activeLine = null;
         floating(mc, c, mm, buffers, base, mode, alpha);
      }
   }

   private static String font(LFConfig c) {
      return c.font != null && !c.font.isEmpty() ? c.font : "poppins";
   }

   private static float widthU(String s, String font) {
      return Fonts.unitWidth(s, font, Minecraft.getInstance().font) * 9.25F;
   }

   private static void text(Matrix4f m, String s, String font, float x, float y, float z, int rgb, float a, MultiBufferSource buf, DisplayMode mode) {
      if (!(a <= 0.03F)) {
         float h = Fonts.height(font);
         float u = 9.25F / h;
         Matrix4f mm = new Matrix4f(m).translate(x, y, z).scale(u, u, u);
         Component comp = Fonts.text(s, font);
         float gy = 4.5F / u - h / 2.0F + h * Fonts.yShift(font);
         int col = Col.alpha(0xFF000000 | rgb & 16777215, Math.min(1.0F, a));
         if (col >>> 24 >= 8) {
            Minecraft.getInstance().font.drawInBatch(comp, 0.0F, gy, col, false, mm, buf, mode, 0, 15728880);
         }
      }
   }

   private static boolean skyOpen(Minecraft mc) {
      return mc.level == null || mc.level.canSeeSky(BlockPos.containing(Proj.cx, Proj.cy, Proj.cz).above());
   }

   private static double skyEl(LFConfig c, boolean open) {
      double b = -Proj.pitch + 3.0F;
      return c.showUnderground && !open ? b : Math.max(-6.0, Math.min(60.0, b));
   }

   private static double degPerUnit(Minecraft mc, LFConfig c) {
      double guiH = Math.max(1, mc.getWindow().getGuiScaledHeight());
      double focalRef = guiH / 2.0 / Math.tan(Math.toRadians(35.0));
      return Math.toDegrees(1.6 * c.lyricsScale / focalRef);
   }

   private static double angDiff(double a, double b) {
      return ((a - b) % 360.0 + 540.0) % 360.0 - 180.0;
   }

   private static void sky(Minecraft mc, LFConfig c, MediaManager mm, MultiBufferSource buf, Matrix4f base, DisplayMode mode, float dt, float alpha) {
      Lyrics lyrics = mm.lyrics();
      long now = System.currentTimeMillis();
      long prog = mm.position() + 60L;
      Lyrics.Line cur = null;
      if (lyrics != null && mm.isPlaying() && mm.position() >= 0L) {
         cur = lyrics.lastStartedBefore(prog);
         if (cur != null && (prog > cur.end + 400L || cur.words.isEmpty() || cur.text.isBlank())) {
            cur = null;
         }
      }

      String font = font(c);
      double dpp = degPerUnit(mc, c);
      boolean open = skyOpen(mc);
      if (cur != activeLine) {
         if (active != null) {
            active.exitAt = now;
            exiting.add(active);
         }

         active = null;
         activeLine = cur;
         if (cur != null) {
            active = new AirLyrics.SkyLine(cur, Proj.yaw, skyEl(c, open), dpp, font);
         }
      }

      exiting.removeIf(sx -> now - sx.exitAt > 520L);

      while (exiting.size() > 1) {
         exiting.remove(0);
      }

      DisplayMode m = c.showUnderground && !open ? DisplayMode.SEE_THROUGH : mode;
      double d = WorldLyrics.skyDistance() * 0.9;
      GpuBufferSlice oldFog = null;

      try {
         oldFog = RenderSystem.getShaderFog();
         FogRenderer fr = ((GameRendererAccessor)mc.gameRenderer).lyricsflow$fogRenderer();
         RenderSystem.setShaderFog(fr.getBuffer(FogMode.NONE));
      } catch (Throwable ignored) {
         oldFog = null;
      }

      try {
         int color = Themes.current().main() & 16777215;

         for (AirLyrics.SkyLine s : exiting) {
            drawLine(c, buf, base, s, color, -1, now, dt, dpp, d, font, m, alpha, open);
         }

         if (active != null) {
            drawLine(c, buf, base, active, color, active.line.wordAt(prog), now, dt, dpp, d, font, m, alpha, open);
         }

         if (buf instanceof BufferSource bs) {
            bs.endBatch();
         }
      } finally {
         if (oldFog != null) {
            RenderSystem.setShaderFog(oldFog);
         }
      }
   }

   private static void drawLine(
      LFConfig c,
      MultiBufferSource buf,
      Matrix4f base,
      AirLyrics.SkyLine s,
      int color,
      int cur,
      long now,
      float dt,
      double dpp,
      double d,
      String font,
      DisplayMode mode,
      float alpha,
      boolean open
   ) {
      if (!c.viewLock && s.exitAt == 0L) {
         double dAz = angDiff(Proj.yaw, s.baseAz);
         double kh = 1.0 - Math.exp(-3.0 * dt);
         if (Math.abs(dAz) > 26.0) {
            s.baseAz = s.baseAz + (dAz - Math.signum(dAz) * 26.0) * kh;
         }

         double dEl = skyEl(c, open) - s.baseEl;
         double kv = 1.0 - Math.exp(-(Math.abs(dEl) > 6.0 ? 4.0 : 1.5) * dt);
         s.baseEl += dEl * kv;
      }

      float exit = s.exitAt == 0L ? 0.0F : clamp01((float)(now - s.exitAt) / 480.0F);
      float lineA = (1.0F - outCubic(exit)) * alpha;
      if (!(lineA <= 0.01F)) {
         int n = s.line.words.size();
         int upTo = s.exitAt != 0L ? n : cur;
         float k = (float)(Math.toRadians(dpp) * d);
         float space = widthU(" ", font);
         int shadow = Col.lerp(0xFF000000 | color, -16120300, 0.75F) & 16777215;
         long startDelay = !exiting.isEmpty() && s.exitAt == 0L ? 420L : 0L;
         float beat = c.beatGlow ? Hud.beat : 0.0F;

         for (AirLyrics.Chunk ch : s.chunks) {
            if (s.exitAt != 0L || ch.from <= upTo) {
               float kk = k * ch.size;
               double az = Math.toRadians(s.baseAz + ch.offAz);
               double el = Math.toRadians(s.baseEl + ch.offEl + outCubic(exit) * 3.0F);
               double dx = -Math.sin(az) * Math.cos(el);
               double dy = Math.sin(el);
               double dz = Math.cos(az) * Math.cos(el);
               double r0x = -Math.cos(az);
               double r0z = -Math.sin(az);
               double u0x = -r0z * dy;
               double u0y = r0z * dx - r0x * dz;
               double u0z = r0x * dy;
               double ct = Math.cos(ch.yawTilt);
               double st = Math.sin(ch.yawTilt);
               double rX = r0x * ct + dx * st;
               double rY = dy * st;
               double rZ = r0z * ct + dz * st;
               double cr = Math.cos(ch.roll);
               double sr = Math.sin(ch.roll);
               double Rx = rX * cr + u0x * sr;
               double Ry = rY * cr + u0y * sr;
               double Rz = rZ * cr + u0z * sr;
               double Ux = u0x * cr - rX * sr;
               double Uy = u0y * cr - rY * sr;
               double Uz = u0z * cr - rZ * sr;
               double Nx = Ry * Uz - Rz * Uy;
               double Ny = Rz * Ux - Rx * Uz;
               double Nz = Rx * Uy - Ry * Ux;
               if (Nx * dx + Ny * dy + Nz * dz > 0.0) {
                  Nx = -Nx;
                  Ny = -Ny;
                  Nz = -Nz;
               }

               Matrix4f m0 = new Matrix4f(base)
                  .mul(
                     new Matrix4f(
                        (float)Rx * kk,
                        (float)Ry * kk,
                        (float)Rz * kk,
                        0.0F,
                        (float)(-Ux) * kk,
                        (float)(-Uy) * kk,
                        (float)(-Uz) * kk,
                        0.0F,
                        (float)Nx * kk,
                        (float)Ny * kk,
                        (float)Nz * kk,
                        0.0F,
                        (float)(dx * d),
                        (float)(dy * d),
                        (float)(dz * d),
                        1.0F
                     )
                  );
               float lx = -ch.widthU / 2.0F;

               for (int i = ch.from; i < ch.to; i++) {
                  String word = Lyrics.show(s.line.words.get(i).text);
                  float ww = widthU(word, font);
                  boolean on = s.exitAt != 0L || i <= upTo;
                  if (on && s.shown[i] == 0L) {
                     s.shown[i] = Math.max(now, s.born + startDelay);
                  }

                  if (!on) {
                     lx += ww + space;
                  } else {
                     float raw = clamp01((float)(now - s.shown[i]) / 450.0F);
                     float e = outCubic(raw);
                     float a = e * lineA;
                     boolean current = s.exitAt == 0L && i == upTo;
                     Matrix4f m = new Matrix4f(m0);
                     if (current && beat > 0.01F) {
                        float bs = 1.0F + 0.07F * beat;
                        m.translate(lx + ww / 2.0F, 0.0F, 0.0F).scale(bs, bs, 1.0F).translate(-(lx + ww / 2.0F), 0.0F, 0.0F);
                     }

                     m.translate(lx, -4.5F + (1.0F - e) * 3.0F, 0.0F);
                     text(m, word, font, 0.0F, 0.9F, 0.0F, shadow, a * 0.55F, buf, mode);
                     if (c.glow && AutoPerf.allow(3)) {
                        float pad = 5.5F;
                        Halo.draw(m, -pad, -pad * 0.8F, ww + pad, 9.0F + pad * 0.8F, Col.alpha(0xFF000000 | color, a * (current ? 0.42F : 0.26F)), buf);
                     }

                     int col = Col.lerp(0xFF000000 | color, -1, current ? 0.6F : 0.2F) & 16777215;
                     text(m, word, font, 0.0F, 0.0F, 0.12F, col, a, buf, mode);
                     lx += ww + space;
                  }
               }
            }
         }
      }
   }

   private static float sz(LFConfig c) {
      return 1.6F * c.lyricsScale / 1.7F;
   }

   private static void floating(Minecraft mc, LFConfig c, MediaManager mm, MultiBufferSource buf, Matrix4f base, DisplayMode mode, float alpha) {
      long now = System.currentTimeMillis();
      Vec3 cam = new Vec3(Proj.cx, Proj.cy, Proj.cz);
      boolean live = false;
      Lyrics lyrics = mm.lyrics();
      if (lyrics != null && mm.isPlaying() && mm.position() >= 0L && !lyrics.lines.isEmpty()) {
         long progress = mm.position();
         Lyrics.Line line = lyrics.lastStartedBefore(progress);
         if (line != null && progress <= line.end + 450L) {
            live = update(c, line, progress, cam, now);
         }
      }

      if (!live) {
         for (AirLyrics.Word w : WORDS) {
            if (w.gone == 0L) {
               w.gone = now;
            }
         }
      }

      float fadeMs = 2200.0F;
      WORDS.removeIf(wx -> wx.gone != 0L && (float)(now - wx.gone) > fadeMs);
      draw(c, buf, base, cam, now, fadeMs, mode, alpha);
   }

   private static boolean update(LFConfig c, Lyrics.Line line, long progress, Vec3 cam, long now) {
      if (line.text != null && !line.text.isBlank() && !line.words.isEmpty()) {
         int n = line.words.size();
         if (line != fLine) {
            fLine = line;
            fChunk = -1;
            buildChunks(line);
         }

         if (fChunks.length == 0) {
            return false;
         }

         int aw = Math.max(0, Math.min(n - 1, line.wordAt(progress)));
         int ac = Math.max(0, Math.min(fChunks.length - 1, fChunkOf[Math.min(aw, fChunkOf.length - 1)]));
         if (ac > fChunk) {
            if (ac - fChunk > 2) {
               fChunk = ac - 1;
            }

            while (fChunk < ac) {
               spawn(fChunks[++fChunk], c, cam, now);
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static void buildChunks(Lyrics.Line line) {
      int n = line.words.size();
      List<String> list = new ArrayList<>();
      int[] map = new int[n];
      int i = 0;

      while (i < n) {
         String a = clean(line.words.get(i).text);
         if (a.isEmpty()) {
            map[i] = list.isEmpty() ? 0 : list.size() - 1;
            i++;
         } else {
            int idx = list.size();
            int used = 1;
            String chunk = a;
            if (a.length() <= 3 && i + 1 < n) {
               String b = clean(line.words.get(i + 1).text);
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

      fChunks = list.toArray(new String[0]);
      fChunkOf = map;
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

   private static void spawn(String text, LFConfig c, Vec3 cam, long now) {
      if (text != null && !text.isEmpty()) {
         String font = font(c);
         float yaw = Proj.yaw;
         float el0 = -Proj.pitch + 12.0F;
         String txt = Lyrics.show(text);
         float widthU = widthU(txt, font);
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
               slot = RND.nextInt(SLOTS.length);
               tries++;
            } while ((slot == lastSlot || slot == prevSlot) && tries < 12);

            double az = Math.toRadians(yaw + SLOTS[slot][0] + (RND.nextFloat() - 0.5F) * 7.0F);
            double el = Math.toRadians(el0 + SLOTS[slot][1] + (RND.nextFloat() - 0.5F) * 6.0F);
            double dist = 6.5 + RND.nextDouble() * 8.5;
            float size = 0.82F + RND.nextFloat() * 0.5F;
            double hw = widthU * 0.062F * sz(c) * size * 0.5 * 0.95;
            double hh = 0.682F * sz(c) * size * 0.5;
            double score = overlap(az, el, dist, hw, hh, cam);
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
         double dx = -Math.sin(bAz) * Math.cos(bEl);
         double dy = Math.sin(bEl);
         double dz = Math.cos(bAz) * Math.cos(bEl);
         double x = cam.x + dx * bDist;
         double y = cam.y + dy * bDist;
         double z = cam.z + dz * bDist;
         double h = Math.sqrt(dx * dx + dz * dz);
         double nz;
         double nx;
         if (h < 1.0E-4) {
            double yr = Math.toRadians(yaw);
            nx = Math.sin(yr);
            nz = -Math.cos(yr);
         } else {
            nx = -dx / h;
            nz = -dz / h;
         }

         float tilt = (float)Math.toRadians((RND.nextBoolean() ? 1 : -1) * (10.0F + RND.nextFloat() * 22.0F));
         float pitch = (float)Math.toRadians((RND.nextFloat() - 0.5F) * 14.0F);
         float roll = (float)Math.toRadians((RND.nextFloat() - 0.5F) * 8.0F);

         for (AirLyrics.Word o : WORDS) {
            if (o.gone == 0L) {
               o.gone = now;
            }
         }

         WORDS.add(new AirLyrics.Word(text, x, y, z, nx, nz, tilt, pitch, roll, (float)bSize, (float)bHw, (float)bHh, now));

         while (WORDS.size() > 7) {
            WORDS.remove(0);
         }
      }
   }

   private static double overlap(double az, double el, double dist, double hw, double hh, Vec3 cam) {
      double score = 0.0;
      double angW = hw / dist;
      double angH = hh / dist;

      for (AirLyrics.Word o : WORDS) {
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

   private static void draw(LFConfig c, MultiBufferSource buf, Matrix4f base, Vec3 cam, long now, float fadeMs, DisplayMode mode, float alpha) {
      if (!WORDS.isEmpty()) {
         String font = font(c);
         int baseCol = Themes.current().main() & 16777215;
         double yr = Math.toRadians(Proj.yaw);
         double pr = Math.toRadians(Proj.pitch);
         double vfx = -Math.sin(yr) * Math.cos(pr);
         double vfy = -Math.sin(pr);
         double vfz = Math.cos(yr) * Math.cos(pr);
         float beat = c.beatGlow ? Hud.beat : 0.0F;

         for (AirLyrics.Word w : WORDS) {
            long age = now - w.born;
            float a = clamp01((float)age / 140.0F) * alpha;
            float bright = 1.0F;
            boolean current = w.gone == 0L;
            if (!current) {
               float k = (float)(now - w.gone) / fadeMs;
               if (k >= 1.0F) {
                  continue;
               }

               a *= 1.0F - k * k;
               bright = 1.0F - 0.5F * easeOut(clamp01((float)(now - w.gone) / 240.0F));
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
                  Matrix4f m = new Matrix4f(base)
                     .mul(
                        new Matrix4f(
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
                  String txt = Lyrics.show(w.text);
                  float width = widthU(txt, font);
                  int shownLen = Math.max(1, (int)Math.ceil(txt.length() * clamp01((float)age / 230.0F)));
                  String shown = txt.substring(0, Math.min(txt.length(), shownLen));
                  if (current && beat > 0.01F) {
                     float bs = 1.0F + 0.06F * beat;
                     m.scale(bs, bs, 1.0F);
                  }

                  m.translate(-width / 2.0F, -4.5F, 0.0F);
                  int col = Col.lerp(-7566176, 0xFF000000 | baseCol, bright) & 16777215;
                  text(m, shown, font, 0.7F, 0.9F, -0.5F, 328716, a * 0.55F, buf, mode);
                  text(m, shown, font, 0.0F, 0.0F, 0.3F, col, a, buf, mode);
               }
            }
         }

         if (buf instanceof BufferSource bs) {
            bs.endBatch();
         }
      }
   }

   private static float clamp01(float v) {
      return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
   }

   private static float outCubic(float t) {
      float u = 1.0F - t;
      return 1.0F - u * u * u;
   }

   private static float easeOut(float t) {
      return outCubic(t);
   }

   private static final class Chunk {
      int from;
      int to;
      float widthU;
      double offAz;
      double offEl;
      float yawTilt;
      float roll;
      float size = 1.0F;
   }

   private static final class SkyLine {
      final Lyrics.Line line;
      final long[] shown;
      final List<AirLyrics.Chunk> chunks = new ArrayList<>();
      double baseAz;
      double baseEl;
      final long born = System.currentTimeMillis();
      long exitAt;

      SkyLine(Lyrics.Line l, double az, double el, double dpp, String font) {
         this.line = l;
         int n = l.words.size();
         this.shown = new long[n];
         this.baseAz = az;
         this.baseEl = el;
         String[] w = new String[n];

         for (int i = 0; i < n; i++) {
            w[i] = Lyrics.show(l.words.get(i).text);
         }

         float space = AirLyrics.widthU(" ", font);
         int i = 0;

         while (i < n) {
            int j = i + 1;

            for (int chars = w[i].length(); j < n && j - i < 3 && chars + w[j].length() <= (j - i == 1 ? 12 : 9); j++) {
               chars += w[j].length();
            }

            AirLyrics.Chunk ch = new AirLyrics.Chunk();
            ch.from = i;
            ch.to = j;

            for (int k = i; k < j; k++) {
               ch.widthU = ch.widthU + (AirLyrics.widthU(w[k], font) + (k > i ? space : 0.0F));
            }

            this.chunks.add(ch);
            i = j;
         }

         double gap = space * 2.4 * dpp;
         int cn = this.chunks.size();
         if (cn != 0) {
            int rowsN = cn <= 2 ? 1 : (cn >= 6 && AirLyrics.RND.nextFloat() < 0.35F ? 3 : 2);
            if (cn == 3 && AirLyrics.RND.nextFloat() < 0.35F) {
               rowsN = 1;
            }

            List<List<AirLyrics.Chunk>> rows = new ArrayList<>();
            int idx = 0;

            for (int r = 0; r < rowsN; r++) {
               int left = cn - idx;
               int rowsLeft = rowsN - r;
               int take = rowsLeft == 1
                  ? left
                  : Math.max(1, Math.min(left - (rowsLeft - 1), Math.round((float)left / rowsLeft + (AirLyrics.RND.nextFloat() - 0.5F) * 1.2F)));
               rows.add(new ArrayList<>(this.chunks.subList(idx, idx + take)));
               idx += take;
            }

            int big = AirLyrics.RND.nextInt(cn);

            for (int k = 0; k < cn; k++) {
               this.chunks.get(k).size = (k == big ? 1.22F : 0.86F) + AirLyrics.RND.nextFloat() * 0.2F;
            }

            double rowH = 17.0 * dpp;
            double side = (AirLyrics.RND.nextDouble() * 2.0 - 1.0) * 20.0;
            if (Math.signum(side) == AirLyrics.lastSide && AirLyrics.RND.nextFloat() < 0.65F) {
               side = -side;
            }

            AirLyrics.lastSide = Math.signum(side);
            double lift = 2.5 + AirLyrics.RND.nextDouble() * 6.0;
            double stair = (AirLyrics.RND.nextBoolean() ? 1 : -1) * (5.0 + AirLyrics.RND.nextDouble() * 8.0);

            for (int r = 0; r < rows.size(); r++) {
               List<AirLyrics.Chunk> rr = rows.get(r);
               double total = -gap;

               for (AirLyrics.Chunk ch : rr) {
                  total += gap + ch.widthU * dpp * ch.size;
               }

               if (total > 58.0) {
                  float f = (float)(58.0 / total);

                  for (AirLyrics.Chunk ch : rr) {
                     ch.size *= f;
                  }

                  total = 58.0;
               }

               double rowShift = side + r * stair + (AirLyrics.RND.nextDouble() - 0.5) * 4.0;
               double maxShift = Math.max(0.0, 46.0 - total / 2.0);
               rowShift = Math.max(-maxShift, Math.min(maxShift, rowShift));
               double y = lift + (rows.size() - 1 - r) * rowH + (AirLyrics.RND.nextDouble() - 0.5) * rowH * 0.15;
               double x = rowShift - total / 2.0;

               for (int k = 0; k < rr.size(); k++) {
                  AirLyrics.Chunk ch = rr.get(k);
                  double cw = ch.widthU * dpp * ch.size;
                  ch.offAz = x + cw / 2.0;
                  ch.offEl = y + (k % 2 == 0 ? 0.8 : -0.6) + (AirLyrics.RND.nextDouble() - 0.5) * 0.8;
                  x += cw + gap;
                  ch.yawTilt = (float)Math.toRadians((AirLyrics.RND.nextFloat() < 0.5F ? -1 : 1) * (5.0F + AirLyrics.RND.nextFloat() * 14.0F));
                  ch.roll = (float)Math.toRadians((AirLyrics.RND.nextFloat() - 0.5F) * 9.0F);
               }
            }
         }
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
      long gone;

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
