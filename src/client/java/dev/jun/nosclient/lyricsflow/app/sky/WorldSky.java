package dev.jun.nosclient.lyricsflow.app.sky;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jun.nosclient.lyricsflow.app.Core;
import dev.jun.nosclient.lyricsflow.app.config.Config;
import dev.jun.nosclient.lyricsflow.app.cosmetic.DeathFx;
import dev.jun.nosclient.lyricsflow.app.hud.Anim;
import dev.jun.nosclient.lyricsflow.app.hud.Gfx;
import dev.jun.nosclient.lyricsflow.app.hud.Theme;
import dev.jun.nosclient.lyricsflow.app.lyrics.NowPlaying;
import dev.jun.nosclient.lyricsflow.app.lyrics.SpotifyClient;
import dev.jun.nosclient.lyricsflow.app.util.AutoPerf;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import dev.jun.nosclient.lyricsflow.mixin.GameRendererAccessor;
import dev.jun.nosclient.lyricsflow.render.Halo;
import dev.jun.nosclient.lyricsflow.render.Hud;
import dev.jun.nosclient.lyricsflow.render.Themes;
import dev.jun.nosclient.lyricsflow.util.Col;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class WorldSky {
   private static final Random RND = new Random();
   private static float[][] stars;
   private static final List<float[]> shooting = new ArrayList<>();
   private static long nextShooting = 0L;
   private static final long T0 = System.currentTimeMillis();
   private static double[][] parts = new double[0][];
   private static SkyPreset.Weather partType = null;
   private static long lastNanos = System.nanoTime();
   private static final Anim visible = new Anim(0.0, 3.0);
   private static final Anim picVis = new Anim(0.0, 2.5);
   private static final Anim outside = new Anim(0.0, 2.0);
   private static final Anim musicCalm = new Anim(1.0, 1.5);
   private static double fx;
   private static double fy;
   private static double fz;
   private static double rx;
   private static double ry;
   private static double rz;
   private static double ux;
   private static double uy;
   private static double uz;
   private static float camYaw;
   private static float camPitch;
   private static double dist;
   private static double texDist;
   private static double skyD;
   private static boolean skyOpen = true;
   private static Matrix4f base;
   private static double skyTime = 1000.0;
   private static double breathT = 0.0;
   private static final float[] Q_STARS = new float[]{0.4F, 0.65F, 1.0F};
   private static final double[] Q_AURORA_STEP = new double[]{7.0, 4.5, 3.0};
   private static final float[] Q_WEATHER = new float[]{0.3F, 0.55F, 0.85F};
   private static final int[] Q_DOME = new int[]{36, 30, 24};
   private static final int[] Q_MOON = new int[]{10, 14, 20};
   private static final int[] Q_GLOW = new int[]{0, 2, 4};
   private static VertexConsumer quads;
   private static final double[][] RING = new double[16][3];
   private static int ringPos = 0;
   private static final float[] DAY_AL = new float[]{0.0F, 0.55F, 0.9F, 0.97F, 0.985F, 0.99F, 0.99F};
   private static final double[] DOME_EL = new double[]{-6.0, 0.0, 8.0, 20.0, 38.0, 62.0, 90.0};
   private static final float[] DOME_AL = new float[]{0.0F, 0.3F, 0.42F, 0.52F, 0.6F, 0.64F, 0.64F};
   private static final float[] DOME_A = new float[DOME_EL.length];
   private static final int[] DOME_C = new int[DOME_EL.length];
   private static double[][][] domeGridCache;
   private static int domeGridStep = -1;
   private static double flashNext = 0.0;
   private static double flashT0 = -10.0;
   private static double[][] bolt = new double[0][0];
   public static final int[][] PALETTES = new int[][]{
      {10719743, 16184063}, {16740036, 16769266}, {3073750, 10155775}, {4652976, 8186111}, {16757596, 16739950}, {10475775, 16777215}, {13716479, 4970751}
   };
   public static final String[] PALETTE_KEYS = new String[]{"purple", "pink", "ocean", "mint", "sunset", "ice", "neon"};
   private static final double[][][] CONST = new double[][][]{
      {{30.0, 48.0}, {36.0, 50.0}, {42.0, 49.0}, {47.0, 46.0}, {50.0, 41.0}, {57.0, 42.0}, {56.0, 47.0}},
      {{160.0, 38.0}, {165.0, 44.0}, {171.0, 40.0}, {177.0, 46.0}, {183.0, 41.0}},
      {{250.0, 30.0}, {254.0, 36.0}, {258.0, 30.0}, {251.0, 25.0}, {257.0, 24.0}, {254.0, 36.0}, {254.0, 43.0}},
      {{320.0, 55.0}, {326.0, 58.0}, {331.0, 55.0}, {326.0, 52.0}, {320.0, 55.0}}
   };
   private static String introTrack = "";
   private static long introStart = 0L;
   private static double introAz = 0.0;
   private static double introEl = 0.0;
   private static final int PF = 11;
   private static final double[] TEX_EL = new double[]{-8.0, -3.0, 2.0, 8.0, 15.0, 24.0, 35.0, 48.0, 62.0, 76.0, 90.0};
   private static final float MESH_EL0 = -4.0F;
   private static final float MESH_EL1 = 90.0F;
   private static int meshVer = -1;
   private static int meshNA;
   private static int meshNE;
   private static double meshStep = 0.0;
   private static float[] mX;
   private static float[] mY;
   private static float[] mZ;
   private static float[] mAz;
   private static float[] mEl;
   private static int[] mBase;
   private static int[] mFrame;
   private static int[] mStamp;
   private static byte[] mDetail;
   private static int frameNo = 0;
   private static double liveStep = 0.0;
   private static int liveNA;
   private static int liveNE;
   private static float[] lX;
   private static float[] lY;
   private static float[] lZ;
   private static float[] lEl;
   private static int[] lCol;
   private static int[] lStamp;
   private static SkyPreset lastLive;
   private static long liveSince;
   private static float fogR;
   private static float fogG;
   private static float fogB;
   private static float fogMix;
   private static final int[] SUB_COL = new int[16];
   private static final float[][] SUB_P = new float[16][3];
   private static final ArrayDeque<double[]> TRAIL = new ArrayDeque<>();
   private static long lastTrail = 0L;
   private static double lastTx;
   private static double lastTz;
   private static double lastSide = 0.0;
   private static WorldSky.SkyLine active;
   private static final List<WorldSky.SkyLine> exiting = new ArrayList<>();
   private static int activeIdx = -2;
   private static String activeTrack = "";
   private static final Matrix4f M0 = new Matrix4f();
   private static final Matrix4f TMP = new Matrix4f();
   private static final Matrix4f WM = new Matrix4f();
   private static final Matrix4f SCR = new Matrix4f();
   private static final Map<String, Component> COMP = new HashMap<>();
   private static int compFont = -1;
   public static final int[] LYRIC_COLORS = new int[]{13219327, 16777215, 16747224, 9098495, 9240520, 16765562, 16743034, 11635967};
   private static boolean warned;
   private static String compFontId = "";

   private WorldSky() {
   }

   private static int q() {
      return AutoPerf.quality();
   }

   public static void render(WorldRenderContext ctx) {
      AutoPerf.begin();
      GpuBufferSlice oldFog = null;

      try {
         Minecraft mcf = Minecraft.getInstance();
         oldFog = RenderSystem.getShaderFog();
         FogRenderer fr = ((GameRendererAccessor)mcf.gameRenderer).lyricsflow$fogRenderer();
         RenderSystem.setShaderFog(fr.getBuffer(FogMode.NONE));
      } catch (Throwable ignored) {
         oldFog = null;
      }

      try {
         if (ConfigManager.get().enabled) {
            renderSky(ctx);
         }
      } catch (Throwable e) {
         if (!warned) {
            warned = true;
            Core.LOG.warn("[LyricsFlow] sky could not be drawn", e);
         }
      } finally {
         if (oldFog != null) {
            RenderSystem.setShaderFog(oldFog);
         }

         AutoPerf.end();
      }
   }

   private static void renderSky(WorldRenderContext ctx) {
      fogMix = 0.0F;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         Config c = Core.cfg();
         SkyPreset p = SkyPreset.get(c.sky);
         long nowNs = System.nanoTime();
         float dt = Math.min(0.1F, (float)(nowNs - lastNanos) / 1.0E9F);
         lastNanos = nowNs;
         float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
         Camera mainCam = mc.gameRenderer.getMainCamera();
         camYaw = mainCam.yRot();
         camPitch = mainCam.xRot();
         double y = Math.toRadians(camYaw);
         double pi = Math.toRadians(camPitch);
         fx = -Math.sin(y) * Math.cos(pi);
         fy = -Math.sin(pi);
         fz = Math.cos(y) * Math.cos(pi);
         rx = -Math.cos(y);
         ry = 0.0;
         rz = -Math.sin(y);
         ux = ry * fz - rz * fy;
         uy = rz * fx - rx * fz;
         uz = rx * fy - ry * fx;
         dist = Math.max(40.0, Math.min(180.0, mc.options.getEffectiveRenderDistance() * 16 * 0.7));
         texDist = Math.max(dist * 0.975, mc.options.getEffectiveRenderDistance() * 16 * 0.8);
         skyD = Math.max(dist, mc.options.getEffectiveRenderDistance() * 16 * 1.04 + 8.0);
         boolean overworld = Core.skyWorld(mc.level);
         float vis = visible.target(p != null && overworld ? 1.0 : 0.0).get();
         skyOpen = mc.level.canSeeSky(mc.player.blockPosition().above());
         float out = outside.target(skyOpen ? 1.0 : 0.0).get();
         skyTime = skyTime + dt * Math.max(0.1F, Math.min(3.0F, c.skySpeed));
         breathT = breathT + dt * c.skyBreath * 1.3;
         double t = skyTime;
         PoseStack ps = ctx.matrices();
         base = new Matrix4f(ps.last().pose());
         MultiBufferSource buffers = ctx.consumers();
         NowPlaying npm = SpotifyClient.now();
         float beat = 0.0F;
         float calm = 1.0F;
         if (c.fxSkyMusic && npm.hasTrack() && npm.playing) {
            long pr = npm.progressNow(c.lyricsOffsetMs);
            beat = npm.beatPulse(pr);
            int li = npm.lineIndex(pr);
            boolean singing = li >= 0 && pr <= npm.lineEnd(li) + 300L;
            calm = musicCalm.target(singing ? 1.0 : 0.7F).get();
         }

         long tod = Math.floorMod(mc.level.getDayTime(), 24000L);
         float nightF = tod >= 13000L && tod <= 23000L
            ? 1.0F
            : (tod > 12000L && tod < 13000L ? (float)(tod - 12000L) / 1000.0F : (tod > 23000L ? 1.0F - (float)(tod - 23000L) / 1000.0F : 0.0F));
         float cover = 0.0F;
         float night = 1.0F;
         if (c.skyDay) {
            cover = 1.0F;
         } else if (c.fxDayNight) {
            night = 0.35F + 0.65F * nightF;
         }

         boolean pic = overworld && SkyImage.active();
         float picA = picVis.target(pic ? 1.0 : 0.0).get();
         if (picA > 0.01F) {
            SkyImage.draw(buffers, base, skyD * 0.98, picA, c.fxDayNight ? 0.45F + 0.55F * (1.0F - nightF) : 1.0F);
         }

         if (!pic && p != null && vis > 0.01F) {
            SkyTextures.update();
            float strength = p.strength * c.skyStrength * vis * night;
            quads = buffers.getBuffer(RenderTypes.debugQuads());
            if (c.fxSkyColor || cover > 0.01F) {
               drawDome(p, strength, cover * vis, mc);
            }

            LiveSky.MAIN.prepare(p, aurora1(p), aurora2(p), t, c);
            float liveA = vis * liveFade(p);
            float liveB = night * (0.92F + 0.08F * calm) * (1.0F + 0.12F * beat);
            updateFog(liveA, liveB);
            if (!drawLiveShader(p, liveA, liveB, t, c, buffers, mc)) {
               drawLiveSky(liveA, liveB, mc);
            }

            quads = null;
            quads = buffers.getBuffer(RenderTypes.debugQuads());
            if (p.stars > 0.0F && c.fxStars && AutoPerf.allow(5)) {
               drawStars(p, vis * night * (0.85F + 0.3F * beat), t);
            }

            if (p.stars > 0.0F && c.fxStars && c.fxConstellations && AutoPerf.allow(2)) {
               drawConstellations(vis * night);
            }

            if (p == SkyPreset.STORM && AutoPerf.allow(2)) {
               drawStorm(vis, t);
            }

            if ((p.shootingStars || p.stars >= 0.8F) && AutoPerf.allow(3)) {
               drawShooting(vis, dt);
            }

            drawBody(p, vis, t);
            quads = null;
            if (c.fxWeather
               && SkyTextures.spritesReady()
               && AutoPerf.allow(4)
               && overworld
               && c.weatherAmount > 0.01F
               && p.weather != SkyPreset.Weather.NONE
               && out > 0.01F) {
               Vec3 cam = ctx.worldState().cameraRenderState.pos;
               VertexConsumer ts = buffers.getBuffer(RenderTypes.text(SkyTextures.SPRITES));
               drawWeather(ts, p.weather, c.weatherAmount * (AutoPerf.allow(3) ? 1.0F : 0.5F), out, dt, t, cam);
            }
         }

         if (c.lyricsColor >= 0) {
            int lyricCol = LYRIC_COLORS[Math.floorMod(c.lyricsColor, LYRIC_COLORS.length)];
         } else if (p == null) {
            Theme.accent();
         }

         LFConfig lf = ConfigManager.get();
         if (lf.enabled && lf.showLyrics && lf.lyricsStyle == LFConfig.LyricsStyle.SKY && !mc.options.hideGui && !Hud.hideForScreenshot) {
            c.lyricsOpacity = Math.max(0.2F, Math.min(1.0F, lf.textOpacity));
            c.lyricsUnderground = lf.showUnderground;
            c.lyricsGlow = lf.glow;
            c.lyricsGlowColor = -1;
            c.lyricsSize = Math.max(0.6F, Math.min(4.0F, 1.6F * lf.lyricsScale));
            c.fxBeatPulse = lf.beatGlow;
            c.lyricsFollow = lf.skyFollow;
            c.lyricsViewLock = false;
            c.lyricsOffsetMs = 0;
            Themes.Theme th = Themes.current();
            drawLyrics(mc, c, buffers, th.main() & 16777215, dt);
         } else {
            active = null;
            exiting.clear();
            activeIdx = -2;
         }

         Vec3 camV = ctx.worldState().cameraRenderState.pos;
         DeathFx.render(mc, buffers, base, camV, pt);
         base = null;
      }
   }

   private static double[] p(double x, double y, double z) {
      double[] r = RING[ringPos];
      ringPos = ringPos + 1 & 15;
      r[0] = x;
      r[1] = y;
      r[2] = z;
      return r;
   }

   private static double[] dir(double azDeg, double elDeg) {
      double a = Math.toRadians(azDeg);
      double e = Math.toRadians(elDeg);
      double ce = Math.cos(e);
      return p(-Math.sin(a) * ce, Math.sin(e), Math.cos(a) * ce);
   }

   private static void v(double x, double y, double z, int rgb, float a) {
      quads.addVertex(base, (float)x, (float)y, (float)z).setColor(Gfx.argbRaw(rgb, a));
   }

   private static void quad(double[] p0, double[] p1, double[] p2, double[] p3, int c0, float a0, int c1, float a1, int c2, float a2, int c3, float a3) {
      if (!(a0 <= 0.003F) || !(a1 <= 0.003F) || !(a2 <= 0.003F) || !(a3 <= 0.003F)) {
         v(p0[0], p0[1], p0[2], c0, a0);
         v(p1[0], p1[1], p1[2], c1, a1);
         v(p2[0], p2[1], p2[2], c2, a2);
         v(p3[0], p3[1], p3[2], c3, a3);
         v(p3[0], p3[1], p3[2], c3, a3);
         v(p2[0], p2[1], p2[2], c2, a2);
         v(p1[0], p1[1], p1[2], c1, a1);
         v(p0[0], p0[1], p0[2], c0, a0);
      }
   }

   private static double[] at(double azDeg, double elDeg, double d) {
      double[] q = dir(azDeg, elDeg);
      q[0] *= d;
      q[1] *= d;
      q[2] *= d;
      return q;
   }

   private static void softGlow(double cx, double cy, double cz, double radius, int rgb, float a) {
      int n = 28;

      for (int i = 0; i < n; i++) {
         double a0 = i * Math.PI * 2.0 / n;
         double a1 = (i + 1) * Math.PI * 2.0 / n;
         double c0 = Math.cos(a0) * radius;
         double s0 = Math.sin(a0) * radius;
         double c1 = Math.cos(a1) * radius;
         double s1 = Math.sin(a1) * radius;
         double[] o0 = p(cx + rx * c0 + ux * s0, cy + ry * c0 + uy * s0, cz + rz * c0 + uz * s0);
         double[] o1 = p(cx + rx * c1 + ux * s1, cy + ry * c1 + uy * s1, cz + rz * c1 + uz * s1);
         double[] m = p(cx, cy, cz);
         double[] m2 = p(cx, cy, cz);
         cquad(m, m2, o1, o0, rgb, a, rgb, a, rgb, 0.0F, rgb, 0.0F);
      }
   }

   private static void billboard(double cx, double cy, double cz, double size, int rgb, float a) {
      double s = size / 2.0;
      double[] p0 = p(cx - rx * s - ux * s, cy - ry * s - uy * s, cz - rz * s - uz * s);
      double[] p1 = p(cx + rx * s - ux * s, cy + ry * s - uy * s, cz + rz * s - uz * s);
      double[] p2 = p(cx + rx * s + ux * s, cy + ry * s + uy * s, cz + rz * s + uz * s);
      double[] p3 = p(cx - rx * s + ux * s, cy - ry * s + uy * s, cz - rz * s + uz * s);
      cquad(p0, p1, p2, p3, rgb, a, rgb, a, rgb, a, rgb, a);
   }

   private static void streak(double ax, double ay, double az, double bx, double by, double bz, double w, int rgb, float a0, float a1) {
      double dx = bx - ax;
      double dy = by - ay;
      double dz = bz - az;
      double vx = (ax + bx) / 2.0;
      double vy = (ay + by) / 2.0;
      double vz = (az + bz) / 2.0;
      double sx = dy * vz - dz * vy;
      double sy = dz * vx - dx * vz;
      double sz = dx * vy - dy * vx;
      double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
      if (!(l < 1.0E-6)) {
         double k = w / 2.0 / l;
         sx *= k;
         sy *= k;
         sz *= k;
         quad(
            p(ax - sx, ay - sy, az - sz),
            p(ax + sx, ay + sy, az + sz),
            p(bx + sx, by + sy, bz + sz),
            p(bx - sx, by - sy, bz - sz),
            rgb,
            a0,
            rgb,
            a0,
            rgb,
            a1,
            rgb,
            a1
         );
      }
   }

   private static double[][][] domeGrid(int step) {
      if (domeGridCache != null && domeGridStep == step) {
         return domeGridCache;
      }

      int cols = 360 / step;
      double[][][] g = new double[cols + 1][DOME_EL.length][3];

      for (int j = 0; j <= cols; j++) {
         double a = Math.toRadians(j * step);

         for (int i = 0; i < DOME_EL.length; i++) {
            double e = Math.toRadians(DOME_EL[i]);
            g[j][i][0] = -Math.sin(a) * Math.cos(e);
            g[j][i][1] = Math.sin(e);
            g[j][i][2] = Math.cos(a) * Math.cos(e);
         }
      }

      domeGridCache = g;
      domeGridStep = step;
      return g;
   }

   private static void drawDome(SkyPreset p, float s, float cover, Minecraft mc) {
      double d = skyD * 1.25;
      int step = Q_DOME[q()];
      double[][][] grid = domeGrid(step);
      float[] a = DOME_A;
      int[] col = DOME_C;

      for (int i = 0; i < DOME_EL.length; i++) {
         a[i] = DOME_AL[i] * s + (DAY_AL[i] - DOME_AL[i] * s) * cover;
         col[i] = Gfx.lerpColor(p.horizon, p.top, (float)Math.max(0.0, DOME_EL[i] / 45.0));
      }

      int n = grid.length - 1;

      for (int j = 0; j < n; j++) {
         double[] mid = grid[j][3];
         if (!(mid[0] * fx + mid[2] * fz < -0.55) || !(fy > -0.6) || !(fy < 0.6)) {
            for (int i = 0; i < DOME_EL.length - 1; i++) {
               double[] g00 = grid[j][i];
               double[] g10 = grid[j + 1][i];
               double[] g11 = grid[j + 1][i + 1];
               double[] g01 = grid[j][i + 1];
               quad(
                  p(g00[0] * d, g00[1] * d, g00[2] * d),
                  p(g10[0] * d, g10[1] * d, g10[2] * d),
                  p(g11[0] * d, g11[1] * d, g11[2] * d),
                  p(g01[0] * d, g01[1] * d, g01[2] * d),
                  col[i],
                  a[i],
                  col[i],
                  a[i],
                  col[i + 1],
                  a[i + 1],
                  col[i + 1],
                  a[i + 1]
               );
            }
         }
      }
   }

   private static void initStars() {
      Random r = new Random(1337L);
      stars = new float[1300][8];

      for (float[] s : stars) {
         s[0] = r.nextFloat() * 360.0F;
         s[1] = (float)Math.toDegrees(Math.asin(0.04 + r.nextFloat() * 0.96));
         s[2] = r.nextFloat() < 0.1F ? 1.8F : 1.0F;
         s[3] = 0.35F + r.nextFloat() * 0.65F;
         s[4] = r.nextFloat() * 6.28F;
         double a = Math.toRadians(s[0]);
         double e = Math.toRadians(s[1]);
         s[5] = (float)(-Math.sin(a) * Math.cos(e));
         s[6] = (float)Math.sin(e);
         s[7] = (float)(Math.cos(a) * Math.cos(e));
      }
   }

   private static void drawStars(SkyPreset p, float vis, double t) {
      if (stars == null) {
         initStars();
      }

      int count = (int)Math.min(stars.length, 520.0F * p.stars * Q_STARS[q()]);
      double d = skyD * 0.98;
      double px = d * 0.0016;

      for (int i = 0; i < count; i++) {
         float[] s = stars[i];
         if (!(s[5] * fx + s[6] * fy + s[7] * fz < 0.05)) {
            float tw = (float)(0.75 + 0.25 * Math.sin(t * (0.8 + s[4] * 0.2) + s[4]));
            float a = s[3] * tw * vis * Math.min(1.0F, s[1] / 10.0F);
            float live = Core.cfg().skyLively;
            float tw2 = live * (float)Math.pow(0.5 + 0.5 * Math.sin(t * (1.3 + s[4] * 0.4) + s[4] * 3.0F), 3.0) + (1.0F - live) * 0.5F;
            billboard(s[5] * d, s[6] * d, s[7] * d, px * 1.7 * s[2], 16052479, a * (0.25F + 0.75F * tw2));
         }
      }
   }

   private static float flashLevel(double t) {
      if (t >= flashNext) {
         flashT0 = t;
         flashNext = t + 0.5 + RND.nextDouble() * 2.0;
         bolt = makeBolt();
      }

      double age = t - flashT0;
      if (age < 0.05) {
         return 1.0F;
      } else if (age < 0.12) {
         return 0.25F;
      } else if (age < 0.18) {
         return 0.9F;
      } else {
         return age < 0.6 ? (float)(0.8 * Math.exp(-(age - 0.18) * 9.0)) : 0.0F;
      }
   }

   private static double[][] makeBolt() {
      int n = 10;
      double[][] b = new double[n][2];
      double az = RND.nextDouble() * 360.0;

      for (int i = 0; i < n; i++) {
         double f = (double)i / (n - 1);
         b[i][0] = az + (RND.nextDouble() - 0.5) * 9.0;
         b[i][1] = 58.0 - f * 48.0 + (RND.nextDouble() - 0.5) * 4.0;
         az += (RND.nextDouble() - 0.5) * 6.0;
      }

      return b;
   }

   private static void drawStorm(float vis, double t) {
      float fl = flashLevel(t);
      double d = skyD * 0.97;
      if (fl > 0.02F && bolt.length > 1) {
         double[] z = at(0.0, 70.0, d);
         softGlow(z[0], z[1], z[2], d * 0.6, 13161727, 0.35F * fl * vis);

         for (int i = 0; i + 1 < bolt.length; i++) {
            double[] p0 = at(bolt[i][0], bolt[i][1], d);
            double[] p1 = at(bolt[i + 1][0], bolt[i + 1][1], d);
            streak(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], d * 0.006, 12110079, fl * vis, fl * vis);
            streak(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], d * 0.0025, 16777215, fl * vis, fl * vis);
         }
      }
   }

   public static int aurora1(SkyPreset p) {
      int pal = Core.cfg().skyPalette;
      if (pal >= 0 && pal < PALETTES.length) {
         return PALETTES[pal][0];
      } else {
         return p.aurora1 != 0 ? p.aurora1 : p.lyricColor;
      }
   }

   public static int aurora2(SkyPreset p) {
      int pal = Core.cfg().skyPalette;
      if (pal >= 0 && pal < PALETTES.length) {
         return PALETTES[pal][1];
      } else {
         return p.aurora2 != 0 ? p.aurora2 : 16777215;
      }
   }

   private static void drawConstellations(float vis) {
      double d = skyD * 0.975;
      double px = d * 0.0016;

      for (double[][] cst : CONST) {
         for (int i = 0; i < cst.length; i++) {
            double[] a = at(cst[i][0], cst[i][1], d);
            double ax = a[0];
            double ay = a[1];
            double az = a[2];
            billboard(ax, ay, az, px * 2.6, 16777215, 0.9F * vis);
            if (i + 1 < cst.length) {
               double[] b = at(cst[i + 1][0], cst[i + 1][1], d);
               streak(ax, ay, az, b[0], b[1], b[2], px * 0.7, 14208255, 0.22F * vis, 0.22F * vis);
            }
         }
      }
   }

   private static void drawSongIntro(Minecraft mc, Config c, MultiBufferSource buffers, int color) {
      NowPlaying np = SpotifyClient.now();
      if (np.hasTrack()) {
         String id = String.valueOf(np.trackId);
         long now = System.currentTimeMillis();
         if (!id.equals(introTrack)) {
            introTrack = id;
            introStart = now;
            introAz = camYaw;
            introEl = Math.max(8.0F, Math.min(50.0F, -camPitch + 10.0F));
         }

         long age = now - introStart;
         if (age <= 3800L) {
            float a = Math.min(1.0F, (float)age / 500.0F) * (age > 3000L ? 1.0F - (float)(age - 3000L) / 800.0F : 1.0F) * c.lyricsOpacity;
            if (!(a <= 0.03F)) {
               double d = dist * 0.88;
               double dpp = degPerPx(mc, c);
               float k = (float)(Math.toRadians(dpp) * d);
               Font font = mc.font;
               String title = np.title.length() > 32 ? np.title.substring(0, 31) + "…" : np.title;
               drawFlat(font, buffers, title, introAz, introEl + 2.0 + (1.0F - Math.min(1.0F, (float)age / 700.0F)) * 2.0F, d, k * 1.6F, color, a);
               if (!np.artist.isBlank()) {
                  drawFlat(font, buffers, np.artist, introAz, introEl - 4.0, d, k * 0.9F, 16777215, a * 0.75F);
               }
            }
         }
      }
   }

   private static void drawFlat(Font font, MultiBufferSource buffers, String text, double azDeg, double elDeg, double d, float k, int col, float a) {
      double az = Math.toRadians(azDeg);
      double el = Math.toRadians(elDeg);
      double dx = -Math.sin(az) * Math.cos(el);
      double dy = Math.sin(el);
      double dz = Math.cos(az) * Math.cos(el);
      double r0x = -Math.cos(az);
      double r0z = -Math.sin(az);
      double ux0 = -r0z * dy;
      double uy0 = r0z * dx - r0x * dz;
      double uz0 = r0x * dy;
      Matrix4f m = new Matrix4f(base)
         .mul(
            new Matrix4f(
               (float)r0x * k,
               0.0F,
               (float)r0z * k,
               0.0F,
               (float)(-ux0) * k,
               (float)(-uy0) * k,
               (float)(-uz0) * k,
               0.0F,
               (float)(-dx) * k,
               (float)(-dy) * k,
               (float)(-dz) * k,
               0.0F,
               (float)(dx * d),
               (float)(dy * d),
               (float)(dz * d),
               1.0F
            )
         );
      int w = Gfx.w(text, Gfx.LYRICS);
      m.translate(-w / 2.0F, -4.5F, 0.0F);
      word(font, buffers, m, text, 0.0F, 1.0F, 0.0F, Gfx.lerpColor(col, 656916, 0.7F), a * 0.6F, false);
      word(font, buffers, m, text, 0.0F, 0.0F, 0.1F, col, a, false);
   }

   private static void drawShooting(float vis, float dt) {
      long now = System.currentTimeMillis();
      if (now > nextShooting) {
         nextShooting = now + 1200L + RND.nextInt(3500);
         shooting.add(
            new float[]{camYaw + (RND.nextFloat() - 0.5F) * 80.0F, 30.0F + RND.nextFloat() * 30.0F, (float)(now - T0), RND.nextBoolean() ? 1.0F : -1.0F}
         );
      }

      shooting.removeIf(sx -> now - (T0 + (long)sx[2]) > 900L || now < T0 + (long)sx[2] - 1000L);
      double d = skyD * 0.97;

      for (float[] s : shooting) {
         float life = Anim.clamp((float)(now - (T0 + (long)s[2])) / 900.0F);
         double az = s[0] + s[3] * life * 30.0F;
         double el = s[1] - life * 12.0F;
         double az2 = az - s[3] * 6.0F;
         double el2 = el + 2.4;
         double[] h = at(az, el, d);
         double hx = h[0];
         double hy = h[1];
         double hz = h[2];
         double[] tl = at(az2, el2, d);
         float a = vis * (1.0F - life);
         streak(tl[0], tl[1], tl[2], hx, hy, hz, d * 0.004, 16777215, 0.0F, a);
      }
   }

   private static void drawWeather(VertexConsumer vc, SkyPreset.Weather w, float amount, float out, float dt, double t, Vec3 eye) {
      int n = switch (w) {
         case BLIZZARD -> 240;
         case SNOW -> 200;
         case RAIN, THUNDER -> 220;
         case PETALS -> 150;
         case EMBERS -> 80;
         case FIREFLIES -> 45;
         default -> 0;
      };
      n = (int)(n * amount * Q_WEATHER[q()]);
      double box = 14.0;
      double hb = 9.0;
      if (partType != w || parts.length != n) {
         partType = w;
         parts = new double[n][11];

         for (double[] q : parts) {
            q[0] = eye.x + (RND.nextDouble() * 2.0 - 1.0) * box;
            q[1] = eye.y + (RND.nextDouble() * 2.0 - 1.0) * hb;
            q[2] = eye.z + (RND.nextDouble() * 2.0 - 1.0) * box;
            q[3] = RND.nextDouble() * 6.28;
            q[4] = 0.6 + RND.nextDouble() * 0.8;
            double ax = RND.nextGaussian();
            double ay = RND.nextGaussian() * 0.6;
            double az = RND.nextGaussian();
            double l = Math.sqrt(ax * ax + ay * ay + az * az) + 1.0E-9;
            q[5] = ax / l;
            q[6] = ay / l;
            q[7] = az / l;
            q[8] = (RND.nextBoolean() ? 1 : -1) * (1.2 + RND.nextDouble() * 2.6);
            q[9] = 0.75 + RND.nextDouble() * 0.6;
            q[10] = RND.nextDouble();
         }
      }

      for (double[] q : parts) {
         double ph = q[3];
         double sp = q[4];
         double vy;
         double vz;
         double vx;
         switch (w) {
            case BLIZZARD:
               vx = -8.5 * sp;
               vy = -4.0 * sp;
               vz = 2.8 * sp;
               break;
            case SNOW:
               vx = 0.4 + 0.5 * Math.sin(t * 0.8 + ph);
               vy = -1.3 * sp;
               vz = 0.3 * Math.cos(t * 0.7 + ph);
               break;
            case RAIN:
            case THUNDER:
               vx = 0.6;
               vy = -17.0 * sp;
               vz = 0.2;
               break;
            case PETALS:
               vx = 0.7 + 0.8 * Math.sin(t * 0.9 + ph);
               vy = -0.55 * sp - 0.25 * Math.sin(t * 2.1 + ph * 2.0);
               vz = 0.45 * Math.cos(t * 0.7 + ph);
               break;
            case EMBERS:
               vx = 0.3 * Math.sin(t * 1.3 + ph);
               vy = 0.9 * sp;
               vz = 0.3 * Math.cos(t + ph);
               break;
            default:
               vx = 0.4 * Math.sin(t * 0.5 + ph);
               vy = 0.25 * Math.sin(t * 0.7 + ph * 2.0);
               vz = 0.4 * Math.cos(t * 0.45 + ph);
         }

         q[0] += vx * dt;
         q[1] += vy * dt;
         q[2] += vz * dt;
         double dx = wrap(q[0] - eye.x, box);
         double dy = wrap(q[1] - eye.y, hb);
         double dz = wrap(q[2] - eye.z, box);
         q[0] = eye.x + dx;
         q[1] = eye.y + dy;
         q[2] = eye.z + dz;
         if (!(dx * fx + dy * fy + dz * fz < 0.3)) {
            double dd = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float near = (float)Math.min(1.0, 5.0 / Math.max(0.5, dd));
            float far = (float)(1.0 - Math.max(0.0, Math.min(1.0, (dd - 9.0) / 6.0)));
            switch (w) {
               case BLIZZARD:
                  spriteStreak(vc, dx - vx * 0.06, dy - vy * 0.06, dz - vz * 0.06, dx, dy, dz, 0.07, 16052991, out * far * (0.45F + 0.4F * near));
                  break;
               case SNOW:
                  sprite(vc, dx, dy, dz, 0.11 * q[9], 1, 16777215, out * far * (0.55F + 0.4F * near));
                  break;
               case RAIN:
               case THUNDER:
                  spriteStreak(vc, dx - vx * 0.035, dy - vy * 0.035, dz - vz * 0.035, dx, dy, dz, 0.05, 12767468, out * far * (0.3F + 0.35F * near));
                  break;
               case PETALS:
                  double ang = q[3] + t * q[8];
                  int col = Gfx.lerpColor(16777215, 16765670, (float)q[10]);
                  petal(vc, dx, dy, dz, 0.16 * q[9], q[5], q[6], q[7], ang, col, out * far * 0.95F);
                  break;
               case EMBERS:
                  float fl = (float)(0.5 + 0.5 * Math.sin(t * 9.0 + ph * 3.0));
                  sprite(vc, dx, dy, dz, 0.22 * q[9], 2, Gfx.lerpColor(16726815, 16760906, fl), out * far * (0.5F + 0.5F * fl));
                  break;
               default:
                  float pulse = (float)Math.pow(0.5 + 0.5 * Math.sin(t * 2.2 + ph * 4.0), 2.0);
                  sprite(vc, dx, dy, dz, 0.6, 2, 14090090, out * far * (0.15F + 0.85F * pulse));
            }
         }
      }
   }

   private static void tv(VertexConsumer vc, double x, double y, double z, float u, float v, int argb) {
      vc.addVertex(base, (float)x, (float)y, (float)z)
         .setColor(argb)
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(15728880)
         .setNormal(0.0F, 1.0F, 0.0F);
   }

   private static void tquad(VertexConsumer vc, double[] p0, double[] p1, double[] p2, double[] p3, float u0, float v0, float u1, float v1, int col) {
      double ax = p1[0] - p0[0];
      double ay = p1[1] - p0[1];
      double az = p1[2] - p0[2];
      double bx = p2[0] - p0[0];
      double by = p2[1] - p0[1];
      double bz = p2[2] - p0[2];
      double nx = ay * bz - az * by;
      double ny = az * bx - ax * bz;
      double nz = ax * by - ay * bx;
      if (nx * p0[0] + ny * p0[1] + nz * p0[2] < 0.0) {
         tv(vc, p0[0], p0[1], p0[2], u0, v1, col);
         tv(vc, p1[0], p1[1], p1[2], u1, v1, col);
         tv(vc, p2[0], p2[1], p2[2], u1, v0, col);
         tv(vc, p3[0], p3[1], p3[2], u0, v0, col);
      } else {
         tv(vc, p3[0], p3[1], p3[2], u0, v0, col);
         tv(vc, p2[0], p2[1], p2[2], u1, v0, col);
         tv(vc, p1[0], p1[1], p1[2], u1, v1, col);
         tv(vc, p0[0], p0[1], p0[2], u0, v1, col);
      }
   }

   private static void sprite(VertexConsumer vc, double cx, double cy, double cz, double size, int k, int rgb, float a) {
      if (!(a <= 0.02F)) {
         double s = size / 2.0;
         int col = Gfx.argbRaw(rgb, a);
         float u0 = k * 0.25F;
         float u1 = u0 + 0.25F;
         tquad(
            vc,
            p(cx - rx * s - ux * s, cy - ry * s - uy * s, cz - rz * s - uz * s),
            p(cx + rx * s - ux * s, cy + ry * s - uy * s, cz + rz * s - uz * s),
            p(cx + rx * s + ux * s, cy + ry * s + uy * s, cz + rz * s + uz * s),
            p(cx - rx * s + ux * s, cy - ry * s + uy * s, cz - rz * s + uz * s),
            u0,
            0.0F,
            u1,
            1.0F,
            col
         );
      }
   }

   private static void spriteStreak(VertexConsumer vc, double ax, double ay, double az, double bx, double by, double bz, double w, int rgb, float a) {
      if (!(a <= 0.02F)) {
         double dx = bx - ax;
         double dy = by - ay;
         double dz = bz - az;
         double vx = (ax + bx) / 2.0;
         double vy = (ay + by) / 2.0;
         double vz = (az + bz) / 2.0;
         double sx = dy * vz - dz * vy;
         double sy = dz * vx - dx * vz;
         double sz = dx * vy - dy * vx;
         double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
         if (!(l < 1.0E-6)) {
            double k = w / 2.0 / l;
            sx *= k;
            sy *= k;
            sz *= k;
            int col = Gfx.argbRaw(rgb, a);
            tquad(
               vc,
               p(bx - sx, by - sy, bz - sz),
               p(bx + sx, by + sy, bz + sz),
               p(ax + sx, ay + sy, az + sz),
               p(ax - sx, ay - sy, az - sz),
               0.75F,
               0.0F,
               1.0F,
               1.0F,
               col
            );
         }
      }
   }

   private static void petal(VertexConsumer vc, double cx, double cy, double cz, double size, double kx, double ky, double kz, double ang, int rgb, float a) {
      if (!(a <= 0.02F)) {
         double c = Math.cos(ang);
         double s = Math.sin(ang);
         double oc = 1.0 - c;
         double e1x = c + kx * kx * oc;
         double e1y = ky * kx * oc + kz * s;
         double e1z = kz * kx * oc - ky * s;
         double e2x = kx * kz * oc + ky * s;
         double e2y = ky * kz * oc - kx * s;
         double e2z = c + kz * kz * oc;
         double h = size / 2.0;
         int col = Gfx.argbRaw(rgb, a);
         tquad(
            vc,
            p(cx - e1x * h - e2x * h, cy - e1y * h - e2y * h, cz - e1z * h - e2z * h),
            p(cx + e1x * h - e2x * h, cy + e1y * h - e2y * h, cz + e1z * h - e2z * h),
            p(cx + e1x * h + e2x * h, cy + e1y * h + e2y * h, cz + e1z * h + e2z * h),
            p(cx - e1x * h + e2x * h, cy - e1y * h + e2y * h, cz - e1z * h + e2z * h),
            0.0F,
            0.0F,
            0.25F,
            1.0F,
            col
         );
      }
   }

   private static void texDome(VertexConsumer vc, float alpha, float uOff, float wave, double t) {
      texDome(vc, alpha, 1.0F, texDist, uOff, wave, t);
   }

   private static void texDome(VertexConsumer vc, float bright, float opacity, double d, float uOff, float wave, double t) {
      if (!(bright <= 0.01F) && !(opacity <= 0.01F)) {
         int gray = Math.round(Math.min(1.0F, bright) * 255.0F);
         int col = Math.round(Math.min(1.0F, opacity) * 255.0F) << 24 | gray << 16 | gray << 8 | gray;
         int step = q() == 0 ? 12 : 8;

         for (int az = 0; az < 360; az += step) {
            double am = Math.toRadians(az + step / 2.0);
            if (!(-Math.sin(am) * fx + Math.cos(am) * fz < -0.55) || !(fy > -0.6) || !(fy < 0.6)) {
               for (int i = 0; i < TEX_EL.length - 1; i++) {
                  texQuad(vc, az, az + step, TEX_EL[i], TEX_EL[i + 1], d, uOff, wave, t, col);
               }
            }
         }
      }
   }

   private static void texQuad(VertexConsumer vc, double az0, double az1, double el0, double el1, double d, float uOff, float wave, double t, int col) {
      double u0 = az0 / 360.0 + uOff;
      u0 -= Math.floor(u0);
      double u1 = u0 + (az1 - az0) / 360.0;
      if (u1 > 1.0000001) {
         double split = az0 + (1.0 - u0) * 360.0;
         texPart(vc, az0, split, u0, 1.0, el0, el1, d, wave, t, col);
         texPart(vc, split, az1, 0.0, u1 - 1.0, el0, el1, d, wave, t, col);
      } else {
         texPart(vc, az0, az1, u0, u1, el0, el1, d, wave, t, col);
      }
   }

   private static void texPart(VertexConsumer vc, double az0, double az1, double u0, double u1, double el0, double el1, double d, float wave, double t, int col) {
      float v0 = (float)((90.0 - el1) / 98.0);
      float v1 = (float)((90.0 - el0) / 98.0);
      double w00 = waveAt(az0, el0, t) * wave;
      double w10 = waveAt(az1, el0, t) * wave;
      double w11 = waveAt(az1, el1, t) * wave;
      double w01 = waveAt(az0, el1, t) * wave;
      double[] a0 = at(az0, el0 + w00, d);
      double ax = a0[0];
      double ay = a0[1];
      double az = a0[2];
      double[] b0 = at(az1, el0 + w10, d);
      double bx = b0[0];
      double by = b0[1];
      double bz = b0[2];
      double[] c1 = at(az1, el1 + w11, d);
      double cx = c1[0];
      double cy = c1[1];
      double cz = c1[2];
      double[] d1 = at(az0, el1 + w01, d);
      tquad(vc, p(ax, ay, az), p(bx, by, bz), p(cx, cy, cz), d1, (float)u0, v0, (float)u1, v1, col);
   }

   private static double waveAt(double azDeg, double el, double t) {
      double a = Math.toRadians(azDeg);
      double k = Math.max(0.0, Math.min(1.0, (el - 2.0) / 10.0)) * Math.max(0.0, Math.min(1.0, (80.0 - el) / 20.0));
      return (Math.sin(a * 5.0 + t * 0.55) * 0.6 + Math.sin(a * 11.0 - t * 0.8) * 0.4) * k;
   }

   private static double wrap(double v, double half) {
      double size = half * 2.0;
      v = (v + half) % size;
      if (v < 0.0) {
         v += size;
      }

      return v - half;
   }

   private static int mIdx(int ai, int ei) {
      return ei * meshNA + Math.floorMod(ai, meshNA);
   }

   private static int samplePano(int[] pix, double az, double el) {
      int w = 2048;
      int h = 512;
      double u = az / 360.0 * w - 0.5;
      double v = (90.0 - el) / 98.0 * h - 0.5;
      u -= Math.floor(u / w) * w;
      if (v < 0.0) {
         v = 0.0;
      }

      if (v > h - 1) {
         v = h - 1;
      }

      int x0 = (int)u;
      int y0 = (int)v;
      int x1 = (x0 + 1) % w;
      int y1 = Math.min(h - 1, y0 + 1);
      float tx = (float)(u - x0);
      float ty = (float)(v - y0);
      int a = pix[y0 * w + x0];
      int b = pix[y0 * w + x1];
      int c = pix[y1 * w + x0];
      int d = pix[y1 * w + x1];
      int r = Math.round(bil(a >> 16 & 0xFF, b >> 16 & 0xFF, c >> 16 & 0xFF, d >> 16 & 0xFF, tx, ty));
      int g = Math.round(bil(a >> 8 & 0xFF, b >> 8 & 0xFF, c >> 8 & 0xFF, d >> 8 & 0xFF, tx, ty));
      int bl = Math.round(bil(a & 0xFF, b & 0xFF, c & 0xFF, d & 0xFF, tx, ty));
      return r << 16 | g << 8 | bl;
   }

   private static float bil(int a, int b, int c, int d, float tx, float ty) {
      float top = a + (b - a) * tx;
      float bot = c + (d - c) * tx;
      return top + (bot - top) * ty;
   }

   private static float lum(int c) {
      return ((c >> 16 & 0xFF) * 0.3F + (c >> 8 & 0xFF) * 0.55F + (c & 0xFF) * 0.15F) / 255.0F;
   }

   private static void buildMesh(double step) {
      int[] base = SkyTextures.base();
      meshStep = step;
      meshVer = SkyTextures.version();
      meshNA = (int)Math.round(360.0 / step);
      meshNE = (int)Math.ceil(94.0 / step);
      int n = meshNA * (meshNE + 1);
      mX = new float[n];
      mY = new float[n];
      mZ = new float[n];
      mAz = new float[n];
      mEl = new float[n];
      mBase = new int[n];
      mFrame = new int[n];
      mStamp = new int[n];

      for (int ei = 0; ei <= meshNE; ei++) {
         float el = -4.0F + ei * 94.0F / meshNE;
         double e = Math.toRadians(el);
         double ce = Math.cos(e);

         for (int ai = 0; ai < meshNA; ai++) {
            float az = ai * 360.0F / meshNA;
            double a = Math.toRadians(az);
            int i = ei * meshNA + ai;
            mX[i] = (float)(-Math.sin(a) * ce);
            mY[i] = (float)Math.sin(e);
            mZ[i] = (float)(Math.cos(a) * ce);
            mAz[i] = az;
            mEl[i] = el;
            mBase[i] = samplePano(base, az, el);
         }
      }

      mDetail = new byte[meshNA * meshNE];

      for (int ei = 0; ei < meshNE; ei++) {
         for (int ai = 0; ai < meshNA; ai++) {
            int i00 = mIdx(ai, ei);
            float lo = 9.0F;
            float hi = -9.0F;
            float az0 = mAz[i00];
            float el0 = mEl[i00];

            for (int sy = 0; sy <= 1; sy++) {
               for (int sx = 0; sx <= 1; sx++) {
                  float l = lum(samplePano(base, az0 + (sx + 0.5) * step / 2.0, el0 + (sy + 0.5) * step / 2.0));
                  lo = Math.min(lo, l);
                  hi = Math.max(hi, l);
               }
            }

            mDetail[ei * meshNA + ai] = (byte)(hi - lo > 0.1F ? 1 : 0);
         }
      }
   }

   private static float horizonA(float el) {
      float t = Anim.clamp((el + 3.0F) / 6.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   private static int meshColor(int baseRgb, double az, double el, float night, int[] fxp, float fxA, double drift) {
      int r = baseRgb >> 16 & 0xFF;
      int g = baseRgb >> 8 & 0xFF;
      int b = baseRgb & 0xFF;
      if (night < 0.999F) {
         r = Math.round(r * night);
         g = Math.round(g * night);
         b = Math.round(b * night);
      }

      if (fxp != null && fxA > 0.01F) {
         int f = samplePano(fxp, az - drift, el);
         r = Math.min(255, r + Math.round((f >> 16 & 0xFF) * fxA));
         g = Math.min(255, g + Math.round((f >> 8 & 0xFF) * fxA));
         b = Math.min(255, b + Math.round((f & 0xFF) * fxA));
      }

      return r << 16 | g << 8 | b;
   }

   private static int vertColor(int i, float night, int[] fxp, float fxA, double drift) {
      if (mStamp[i] == frameNo) {
         return mFrame[i];
      }

      int c = meshColor(mBase[i], mAz[i], mEl[i], night, fxp, fxA, drift);
      mFrame[i] = c;
      mStamp[i] = frameNo;
      return c;
   }

   private static void cquad(double[] p0, double[] p1, double[] p2, double[] p3, int c0, float a0, int c1, float a1, int c2, float a2, int c3, float a3) {
      double ax = p1[0] - p0[0];
      double ay = p1[1] - p0[1];
      double az = p1[2] - p0[2];
      double bx = p2[0] - p0[0];
      double by = p2[1] - p0[1];
      double bz = p2[2] - p0[2];
      double nx = ay * bz - az * by;
      double ny = az * bx - ax * bz;
      double nz = ax * by - ay * bx;
      if (nx * p0[0] + ny * p0[1] + nz * p0[2] < 0.0) {
         v(p0[0], p0[1], p0[2], c0, a0);
         v(p1[0], p1[1], p1[2], c1, a1);
         v(p2[0], p2[1], p2[2], c2, a2);
         v(p3[0], p3[1], p3[2], c3, a3);
      } else {
         v(p3[0], p3[1], p3[2], c3, a3);
         v(p2[0], p2[1], p2[2], c2, a2);
         v(p1[0], p1[1], p1[2], c1, a1);
         v(p0[0], p0[1], p0[2], c0, a0);
      }
   }

   private static void drawSkyMesh(float alpha, float night, float fxA, double drift, double d, Minecraft mc) {
      if (!(alpha <= 0.01F)) {
         double step = q() != 0 && AutoPerf.allow(3) ? (q() == 1 ? 1.25 : 1.0) : 2.0;
         if (meshVer != SkyTextures.version() || step != meshStep || mX == null) {
            buildMesh(step);
         }

         int[] fxp = fxA > 0.01F ? SkyTextures.fx() : null;
         int[] basePix = SkyTextures.base();
         frameNo++;
         double fov = 70.0;

         try {
            fov = ((Integer)mc.options.fov().get()).intValue();
         } catch (Throwable var82) {
         }

         double aspect = (double)mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
         double halfDiag = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(Math.min(170.0, fov * 1.6) / 2.0)) * Math.sqrt(1.0 + aspect * aspect)));
         double lim = Math.toRadians(Math.min(135.0, halfDiag + 45.0 + step));
         double cosLim = Math.cos(lim);
         boolean fine = q() > 0 && AutoPerf.allow(2);
         float fd = (float)d;
         int budget = q() >= 2 ? 1800 : 900;

         for (int ei = 0; ei < meshNE; ei++) {
            for (int ai = 0; ai < meshNA; ai++) {
               int i00 = mIdx(ai, ei);
               int i10 = mIdx(ai + 1, ei);
               int i11 = mIdx(ai + 1, ei + 1);
               int i01 = mIdx(ai, ei + 1);
               double cx = mX[i00] + mX[i10] + mX[i11] + mX[i01];
               double cy = mY[i00] + mY[i10] + mY[i11] + mY[i01];
               double cz = mZ[i00] + mZ[i10] + mZ[i11] + mZ[i01];
               double cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
               if (!(cl < 1.0E-6) && !((cx * fx + cy * fy + cz * fz) / cl < cosLim)) {
                  float a0 = alpha * horizonA(mEl[i00]);
                  float a1 = alpha * horizonA(mEl[i01]);
                  if (!(a0 <= 0.003F) || !(a1 <= 0.003F)) {
                     if (fine && budget > 0 && mDetail[ei * meshNA + Math.floorMod(ai, meshNA)] != 0) {
                        budget--;
                        float az0 = mAz[i00];
                        float el0 = mEl[i00];
                        float st = (float)(meshStep / 2.0);

                        for (int sy = 0; sy < 2; sy++) {
                           for (int sx = 0; sx < 2; sx++) {
                              double azA = az0 + sx * st;
                              double azB = azA + st;
                              double elA = el0 + sy * st;
                              double elB = elA + st;
                              int q00 = meshColor(samplePano(basePix, azA, elA), azA, elA, night, fxp, fxA, drift);
                              int q10 = meshColor(samplePano(basePix, azB, elA), azB, elA, night, fxp, fxA, drift);
                              int q11 = meshColor(samplePano(basePix, azB, elB), azB, elB, night, fxp, fxA, drift);
                              int q01 = meshColor(samplePano(basePix, azA, elB), azA, elB, night, fxp, fxA, drift);
                              float hA = alpha * horizonA((float)elA);
                              float hB = alpha * horizonA((float)elB);
                              double[] p0 = at(azA, elA, fd);
                              double x0 = p0[0];
                              double y0 = p0[1];
                              double z0 = p0[2];
                              double[] p1 = at(azB, elA, fd);
                              double x1 = p1[0];
                              double y1 = p1[1];
                              double z1 = p1[2];
                              double[] p2 = at(azB, elB, fd);
                              double x2 = p2[0];
                              double y2 = p2[1];
                              double z2 = p2[2];
                              double[] p3 = at(azA, elB, fd);
                              cquad(p(x0, y0, z0), p(x1, y1, z1), p(x2, y2, z2), p3, q00, hA, q10, hA, q11, hB, q01, hB);
                           }
                        }
                     } else {
                        int c00 = vertColor(i00, night, fxp, fxA, drift);
                        int c10 = vertColor(i10, night, fxp, fxA, drift);
                        int c11 = vertColor(i11, night, fxp, fxA, drift);
                        int c01 = vertColor(i01, night, fxp, fxA, drift);
                        cquad(
                           p(mX[i00] * d, mY[i00] * d, mZ[i00] * d),
                           p(mX[i10] * d, mY[i10] * d, mZ[i10] * d),
                           p(mX[i11] * d, mY[i11] * d, mZ[i11] * d),
                           p(mX[i01] * d, mY[i01] * d, mZ[i01] * d),
                           c00,
                           a0,
                           c10,
                           a0,
                           c11,
                           a1,
                           c01,
                           a1
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private static float liveFade(SkyPreset p) {
      if (p != lastLive) {
         lastLive = p;
         liveSince = System.currentTimeMillis();
      }

      return Anim.outCubic(Anim.since(liveSince, 900L));
   }

   private static void buildLive(double step) {
      liveStep = step;
      liveNA = (int)Math.round(360.0 / step);
      liveNE = (int)Math.ceil(94.0 / step);
      int n = liveNA * (liveNE + 1);
      lX = new float[n];
      lY = new float[n];
      lZ = new float[n];
      lEl = new float[n];
      lCol = new int[n];
      lStamp = new int[n];

      for (int ei = 0; ei <= liveNE; ei++) {
         float el = -4.0F + ei * 94.0F / liveNE;
         double e = Math.toRadians(el);
         double ce = Math.cos(e);

         for (int ai = 0; ai < liveNA; ai++) {
            double a = Math.toRadians(ai * 360.0 / liveNA);
            int i = ei * liveNA + ai;
            lX[i] = (float)(-Math.sin(a) * ce);
            lY[i] = (float)Math.sin(e);
            lZ[i] = (float)(Math.cos(a) * ce);
            lEl[i] = el;
         }
      }
   }

   private static int liveCol(int i, float bright) {
      if (lStamp[i] == frameNo) {
         return lCol[i];
      }

      int c = LiveSky.MAIN.color(lX[i], lY[i], lZ[i], lEl[i]);
      if (bright < 0.999F || bright > 1.001F) {
         int r = Math.min(255, Math.round((c >> 16 & 0xFF) * bright));
         int g = Math.min(255, Math.round((c >> 8 & 0xFF) * bright));
         int b = Math.min(255, Math.round((c & 0xFF) * bright));
         c = r << 16 | g << 8 | b;
      }

      lCol[i] = c;
      lStamp[i] = frameNo;
      return c;
   }

   private static void drawLiveSky(float alpha, float bright, Minecraft mc) {
      if (!(alpha <= 0.01F)) {
         double step = q() != 0 && AutoPerf.allow(3) ? (q() == 1 ? 2.0 : 1.5) : 3.0;
         if (lX == null || step != liveStep) {
            buildLive(step);
         }

         frameNo++;
         double fov = 70.0;

         try {
            fov = ((Integer)mc.options.fov().get()).intValue();
         } catch (Throwable var39) {
         }

         double aspect = (double)mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
         double halfDiag = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(Math.min(170.0, fov * 1.6) / 2.0)) * Math.sqrt(1.0 + aspect * aspect)));
         double cosLim = Math.cos(Math.toRadians(Math.min(135.0, halfDiag + 45.0 + step)));
         double d = skyD;
         int budget = q() >= 2 ? 14000 : (q() == 1 ? 7000 : 3000);
         if (!AutoPerf.allow(5)) {
            budget = 0;
         }

         for (int ei = 0; ei < liveNE; ei++) {
            float a0 = alpha * horizonA(lEl[ei * liveNA]);
            float a1 = alpha * horizonA(lEl[(ei + 1) * liveNA]);
            if (!(a0 <= 0.003F) || !(a1 <= 0.003F)) {
               for (int ai = 0; ai < liveNA; ai++) {
                  int i00 = ei * liveNA + ai;
                  int i10 = ei * liveNA + (ai + 1) % liveNA;
                  int i01 = i00 + liveNA;
                  int i11 = i10 + liveNA;
                  double cx = lX[i00] + lX[i10] + lX[i11] + lX[i01];
                  double cy = lY[i00] + lY[i10] + lY[i11] + lY[i01];
                  double cz = lZ[i00] + lZ[i10] + lZ[i11] + lZ[i01];
                  double cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
                  if (!(cl < 1.0E-6) && !((cx * fx + cy * fy + cz * fz) / cl < cosLim)) {
                     int c00 = liveCol(i00, bright);
                     int c10 = liveCol(i10, bright);
                     int c11 = liveCol(i11, bright);
                     int c01 = liveCol(i01, bright);
                     if (budget > 0) {
                        float lo = Math.min(Math.min(lum(c00), lum(c10)), Math.min(lum(c01), lum(c11)));
                        float hi = Math.max(Math.max(lum(c00), lum(c10)), Math.max(lum(c01), lum(c11)));
                        int n = hi - lo > 0.3F ? 3 : (hi - lo > 0.1F ? 2 : 1);
                        if (n > 1) {
                           budget -= (n + 1) * (n + 1) - 4;
                           liveSub(ai, ei, n, c00, c10, c11, c01, alpha, bright, d);
                           continue;
                        }
                     }

                     cquad(
                        p(lX[i00] * d, lY[i00] * d, lZ[i00] * d),
                        p(lX[i10] * d, lY[i10] * d, lZ[i10] * d),
                        p(lX[i11] * d, lY[i11] * d, lZ[i11] * d),
                        p(lX[i01] * d, lY[i01] * d, lZ[i01] * d),
                        c00,
                        a0,
                        c10,
                        a0,
                        c11,
                        a1,
                        c01,
                        a1
                     );
                  }
               }
            }
         }
      }
   }

   public static boolean tintFog(Vector4f v) {
      if (fogMix <= 0.01F) {
         return false;
      }

      v.x = v.x + (fogR - v.x) * fogMix;
      v.y = v.y + (fogG - v.y) * fogMix;
      v.z = v.z + (fogB - v.z) * fogMix;
      return true;
   }

   private static void updateFog(float mix, float bright) {
      float r = 0.0F;
      float g = 0.0F;
      float b = 0.0F;

      for (int i = 0; i < 8; i++) {
         double[] q = dir(i * 45.0, 1.5);
         int c = LiveSky.MAIN.color(q[0], q[1], q[2], 1.5F);
         r += c >> 16 & 0xFF;
         g += c >> 8 & 0xFF;
         b += c & 0xFF;
      }

      fogR = Math.min(1.0F, r / 8.0F / 255.0F * bright);
      fogG = Math.min(1.0F, g / 8.0F / 255.0F * bright);
      fogB = Math.min(1.0F, b / 8.0F / 255.0F * bright);
      fogMix = Math.max(0.0F, Math.min(1.0F, mix));
   }

   private static boolean drawLiveShader(SkyPreset p, float alpha, float bright, double t, Config c, MultiBufferSource buffers, Minecraft mc) {
      if (!(buffers instanceof BufferSource bs && LiveSkyShader.ready())) {
         return false;
      } else {
         if (alpha <= 0.01F) {
            return true;
         }

         try {
            bs.endBatch(RenderTypes.debugQuads());
            LiveSkyShader.params(p, aurora1(p), aurora2(p), t, c, base);
            VertexConsumer vc = buffers.getBuffer(LiveSkyShader.type());
            float st = (float)(t * 2.2);
            int br = Math.max(0, Math.min(255, Math.round(bright / 2.0F * 255.0F)));
            int al = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
            double fov = 70.0;

            try {
               fov = ((Integer)mc.options.fov().get()).intValue();
            } catch (Throwable var52) {
            }

            double aspect = (double)mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
            double halfDiag = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(Math.min(170.0, fov * 1.6) / 2.0)) * Math.sqrt(1.0 + aspect * aspect)));
            double cosLim = Math.cos(Math.toRadians(Math.min(135.0, halfDiag + 50.0)));
            double d = skyD;
            int na = 36;
            int ne = 20;

            for (int ei = 0; ei < ne; ei++) {
               double el0 = -30.0 + ei * 120.0 / ne;
               double el1 = -30.0 + (ei + 1) * 120.0 / ne;

               for (int ai = 0; ai < na; ai++) {
                  double az0 = ai * 360.0 / na;
                  double az1 = (ai + 1) * 360.0 / na;
                  double[] m = dir((az0 + az1) / 2.0, (el0 + el1) / 2.0);
                  if (!(m[0] * fx + m[1] * fy + m[2] * fz < cosLim)) {
                     double[] q0 = dir(az0, el0);
                     float x0 = (float)(q0[0] * d);
                     float y0 = (float)(q0[1] * d);
                     float z0 = (float)(q0[2] * d);
                     double[] q1 = dir(az1, el0);
                     float x1 = (float)(q1[0] * d);
                     float y1 = (float)(q1[1] * d);
                     float z1 = (float)(q1[2] * d);
                     double[] q2 = dir(az1, el1);
                     float x2 = (float)(q2[0] * d);
                     float y2 = (float)(q2[1] * d);
                     float z2 = (float)(q2[2] * d);
                     double[] q3 = dir(az0, el1);
                     float x3 = (float)(q3[0] * d);
                     float y3 = (float)(q3[1] * d);
                     float z3 = (float)(q3[2] * d);
                     vc.addVertex(base, x0, y0, z0).setUv(st, 0.0F).setColor(br, 0, 0, al);
                     vc.addVertex(base, x1, y1, z1).setUv(st, 0.0F).setColor(br, 0, 0, al);
                     vc.addVertex(base, x2, y2, z2).setUv(st, 0.0F).setColor(br, 0, 0, al);
                     vc.addVertex(base, x3, y3, z3).setUv(st, 0.0F).setColor(br, 0, 0, al);
                  }
               }
            }

            bs.endBatch(LiveSkyShader.type());
            return true;
         } catch (Throwable var53) {
            LiveSkyShader.disable(var53);
            return false;
         }
      }
   }

   private static void liveSub(int ai, int ei, int n, int c00, int c10, int c11, int c01, float alpha, float bright, double d) {
      double az0 = ai * 360.0 / liveNA;
      double az1 = (ai + 1) * 360.0 / liveNA;
      float el0 = lEl[ei * liveNA];
      float el1 = lEl[(ei + 1) * liveNA];

      for (int j = 0; j <= n; j++) {
         float el = el0 + (el1 - el0) * j / n;
         double e = Math.toRadians(el);
         double ce = Math.cos(e);
         double se = Math.sin(e);

         for (int i = 0; i <= n; i++) {
            int k = j * (n + 1) + i;
            double a = Math.toRadians(az0 + (az1 - az0) * i / n);
            float x = (float)(-Math.sin(a) * ce);
            float y = (float)se;
            float z = (float)(Math.cos(a) * ce);
            SUB_P[k][0] = x;
            SUB_P[k][1] = y;
            SUB_P[k][2] = z;
            int c;
            if (i == 0 && j == 0) {
               c = c00;
            } else if (i == n && j == 0) {
               c = c10;
            } else if (i == n && j == n) {
               c = c11;
            } else if (i == 0 && j == n) {
               c = c01;
            } else {
               c = LiveSky.MAIN.color(x, y, z, el);
               if (bright < 0.999F || bright > 1.001F) {
                  int r = Math.min(255, Math.round((c >> 16 & 0xFF) * bright));
                  int g = Math.min(255, Math.round((c >> 8 & 0xFF) * bright));
                  int b = Math.min(255, Math.round((c & 0xFF) * bright));
                  c = r << 16 | g << 8 | b;
               }
            }

            SUB_COL[k] = c;
         }
      }

      for (int j = 0; j < n; j++) {
         float aA = alpha * horizonA(el0 + (el1 - el0) * j / n);
         float aB = alpha * horizonA(el0 + (el1 - el0) * (j + 1) / n);

         for (int i = 0; i < n; i++) {
            int k00 = j * (n + 1) + i;
            int k10 = k00 + 1;
            int k01 = k00 + n + 1;
            int k11 = k01 + 1;
            cquad(
               p(SUB_P[k00][0] * d, SUB_P[k00][1] * d, SUB_P[k00][2] * d),
               p(SUB_P[k10][0] * d, SUB_P[k10][1] * d, SUB_P[k10][2] * d),
               p(SUB_P[k11][0] * d, SUB_P[k11][1] * d, SUB_P[k11][2] * d),
               p(SUB_P[k01][0] * d, SUB_P[k01][1] * d, SUB_P[k01][2] * d),
               SUB_COL[k00],
               aA,
               SUB_COL[k10],
               aA,
               SUB_COL[k11],
               aB,
               SUB_COL[k01],
               aB
            );
         }
      }
   }

   private static void drawBody(SkyPreset p, float vis, double t) {
      LiveSky.Body b = LiveSky.body(p);
      if (b != null && !(vis <= 0.01F)) {
         double d = skyD * 0.995;
         double a = Math.toRadians(b.az());
         double e = Math.toRadians(b.el());
         double cx = -Math.sin(a) * Math.cos(e);
         double cy = Math.sin(e);
         double cz = Math.cos(a) * Math.cos(e);
         if (!(cx * fx + cy * fy + cz * fz < 0.2)) {
            double e1x = Math.cos(a);
            double e1y = 0.0;
            double e1z = Math.sin(a);
            double e2x = cy * e1z - cz * e1y;
            double e2y = cz * e1x - cx * e1z;
            double e2z = cx * e1y - cy * e1x;
            double[] B = new double[]{cx, cy, cz, e1x, e1y, e1z, e2x, e2y, e2z};
            double r = Math.toRadians(b.radius());
            int col = b.color();
            if (b.kind() == 0) {
               bodyRing(B, d, 0.0, r * 0.8, 16777215, vis, Gfx.lerpColor(col, 16777215, 0.6F), vis, 40, 0.0, t);
               bodyRing(B, d, r * 0.8, r, Gfx.lerpColor(col, 16777215, 0.6F), vis, col, vis, 40, 0.0, t);
               bodyRing(B, d, r, r * 1.25, col, vis, col, 0.0F, 40, 0.0, t);
               bodyRing(B, d, r * 1.25, r * 3.2, col, vis * 0.3F, col, 0.0F, 40, 0.0, t);
            } else if (b.kind() == 1) {
               int lit = Gfx.lerpColor(col, 16777215, 0.35F);
               int limb = Gfx.lerpColor(col, 0, 0.18F);
               bodyRing(B, d, 0.0, r * 0.7, lit, vis, col, vis, 40, 0.0, t);
               bodyRing(B, d, r * 0.7, r, col, vis, limb, vis, 40, 0.0, t);
               bodyRing(B, d, r, r * 1.08, limb, vis, limb, 0.0F, 40, 0.0, t);
               bodyRing(B, d, r * 1.08, r * 3.4, col, vis * 0.16F, col, 0.0F, 40, 0.0, t);
               int mare = Gfx.lerpColor(col, 0, 0.22F);
               double[][] spots = new double[][]{{-0.3, 0.25, 0.28}, {0.28, 0.05, 0.22}, {0.05, -0.35, 0.18}};

               for (double[] sp : spots) {
                  double ox = sp[0] * Math.sin(r);
                  double oy = sp[1] * Math.sin(r);
                  double[] C = new double[]{
                     cx * Math.cos(r * 0.3) + e1x * ox + e2x * oy,
                     cy * Math.cos(r * 0.3) + e1y * ox + e2y * oy,
                     cz * Math.cos(r * 0.3) + e1z * ox + e2z * oy,
                     e1x,
                     e1y,
                     e1z,
                     e2x,
                     e2y,
                     e2z
                  };
                  bodyRing(C, d * 0.999, 0.0, r * sp[2], mare, vis * 0.35F, mare, 0.0F, 20, 0.0, t);
               }
            } else if (b.kind() == 3) {
               int a1 = aurora1(p);
               bodyRing(B, d, r, r * 1.6, a1, vis * 0.45F, a1, 0.0F, 48, 0.0, t);
               int n = 64;

               for (int i = 0; i < n; i++) {
                  double v0 = -r + 2.0 * r * i / n;
                  double v1 = v0 + 2.0 * r / n;
                  double vm = (v0 + v1) / 2.0 / r;
                  if (!LiveSky.synthGap(vm, t)) {
                     double h0 = Math.sqrt(Math.max(0.0, r * r - v0 * v0));
                     double h1 = Math.sqrt(Math.max(0.0, r * r - v1 * v1));
                     int c0 = LiveSky.synthColor(v0 / r);
                     int c1 = LiveSky.synthColor(v1 / r);
                     quad(disc(B, -h0, v0, d), disc(B, h0, v0, d), disc(B, h1, v1, d), disc(B, -h1, v1, d), c0, vis, c0, vis, c1, vis, c1, vis);
                  }
               }
            } else {
               int a1 = aurora1(p);
               int a2 = aurora2(p);
               bodyRing(B, d, 0.0, r, 262664, vis, 262664, vis, 48, 0.0, t);
               bodyRing(B, d, r, r * 1.035, 16774364, vis, 16774364, vis, 64, 0.25, t);
               bodyRing(B, d, r * 1.035, r * 1.2, Gfx.lerpColor(a1, 16777215, 0.4F), vis * 0.95F, a1, 0.0F, 64, 0.5, t);
               bodyRing(B, d, r * 1.2, r * 2.4, a2, vis * 0.25F, a2, 0.0F, 64, 0.8, t);
            }
         }
      }
   }

   private static double[] disc(double[] B, double u, double v, double d) {
      double x = B[0] + B[3] * u + B[6] * v;
      double y = B[1] + B[4] * u + B[7] * v;
      double z = B[2] + B[5] * u + B[8] * v;
      double l = Math.sqrt(x * x + y * y + z * z);
      return p(x / l * d, y / l * d, z / l * d);
   }

   private static void bodyRing(double[] B, double d, double r0, double r1, int c0, float a0, int c1, float a1, int seg, double flick, double t) {
      double s0 = Math.sin(r0);
      double k0 = Math.cos(r0);
      double s1 = Math.sin(r1);
      double k1 = Math.cos(r1);

      for (int i = 0; i < seg; i++) {
         double th0 = i * Math.PI * 2.0 / seg;
         double th1 = (i + 1) * Math.PI * 2.0 / seg;
         float f0 = flick > 0.0 ? (float)(1.0 - flick + flick * (0.5 + 0.5 * Math.sin(th0 * 7.0 + t * 0.9) * Math.sin(th0 * 3.0 - t * 0.5))) : 1.0F;
         float f1 = flick > 0.0 ? (float)(1.0 - flick + flick * (0.5 + 0.5 * Math.sin(th1 * 7.0 + t * 0.9) * Math.sin(th1 * 3.0 - t * 0.5))) : 1.0F;
         double c0x = Math.cos(th0);
         double c0y = Math.sin(th0);
         double c1x = Math.cos(th1);
         double c1y = Math.sin(th1);
         quad(
            p(
               (B[0] * k0 + (B[3] * c0x + B[6] * c0y) * s0) * d,
               (B[1] * k0 + (B[4] * c0x + B[7] * c0y) * s0) * d,
               (B[2] * k0 + (B[5] * c0x + B[8] * c0y) * s0) * d
            ),
            p(
               (B[0] * k0 + (B[3] * c1x + B[6] * c1y) * s0) * d,
               (B[1] * k0 + (B[4] * c1x + B[7] * c1y) * s0) * d,
               (B[2] * k0 + (B[5] * c1x + B[8] * c1y) * s0) * d
            ),
            p(
               (B[0] * k1 + (B[3] * c1x + B[6] * c1y) * s1) * d,
               (B[1] * k1 + (B[4] * c1x + B[7] * c1y) * s1) * d,
               (B[2] * k1 + (B[5] * c1x + B[8] * c1y) * s1) * d
            ),
            p(
               (B[0] * k1 + (B[3] * c0x + B[6] * c0y) * s1) * d,
               (B[1] * k1 + (B[4] * c0x + B[7] * c0y) * s1) * d,
               (B[2] * k1 + (B[5] * c0x + B[8] * c0y) * s1) * d
            ),
            c0,
            a0 * f0,
            c0,
            a0 * f1,
            c1,
            a1 * f1,
            c1,
            a1 * f0
         );
      }
   }

   private static void drawPlayerFx(Minecraft mc, Config c, MultiBufferSource buffers, Vec3 cam, float pt, double t, SkyPreset p) {
      LocalPlayer pl = mc.player;
      if (pl != null && !pl.isInvisible()) {
         Vec3 pos = pl.getPosition(pt);
         long now = System.currentTimeMillis();
         NowPlaying np = SpotifyClient.now();
         float beat = np.playing ? np.beatPulse(np.progressNow(c.lyricsOffsetMs)) : 0.0F;
         int acc = Theme.accent();
         int skyCol = p != null ? aurora1(p) : acc;
         if (c.trail > 0 && SkyTextures.spritesReady()) {
            double mvx = pos.x - lastTx;
            double mvz = pos.z - lastTz;
            if (now - lastTrail > 35L && mvx * mvx + mvz * mvz > 9.0E-4) {
               lastTrail = now;
               lastTx = pos.x;
               lastTz = pos.z;
               double jx = (Math.random() - 0.5) * 0.35;
               double jz = (Math.random() - 0.5) * 0.35;
               TRAIL.addLast(new double[]{pos.x + jx, pos.y + 0.12 + Math.random() * 0.25, pos.z + jz, now, now / 1400.0 % 1.0});
            }

            while (!TRAIL.isEmpty() && now - ((double[])TRAIL.peekFirst())[3] > 1300.0) {
               TRAIL.pollFirst();
            }

            if (!TRAIL.isEmpty()) {
               VertexConsumer ts = buffers.getBuffer(RenderTypes.text(SkyTextures.SPRITES));

               for (double[] q : TRAIL) {
                  float age = (float)((now - q[3]) / 1300.0);
                  int col = c.trail == 2 ? Gfx.hsv((float)q[4], 0.65F, 1.0F) : (c.trail == 3 ? skyCol : acc);
                  double rise = age * 0.35;
                  sprite(
                     ts,
                     q[0] - cam.x,
                     q[1] + rise - cam.y,
                     q[2] - cam.z,
                     0.32 * (1.0 - age * 0.6) * (1.0 + 0.4 * beat),
                     2,
                     col,
                     (1.0F - age) * 0.9F
                  );
               }
            }
         } else if (!TRAIL.isEmpty()) {
            TRAIL.clear();
         }

         if (c.aura) {
            quads = buffers.getBuffer(RenderTypes.debugQuads());
            double fx0 = pos.x - cam.x;
            double fy0 = pos.y + 0.03 - cam.y;
            double fz0 = pos.z - cam.z;
            int seg = 40;
            float pulse = 0.35F + 0.65F * beat;
            double r0 = 0.55 + 0.08 * beat;
            double r1 = r0 + 0.16;

            for (int i = 0; i < seg; i++) {
               double a0 = i * Math.PI * 2.0 / seg + t * 0.6;
               double a1 = (i + 1) * Math.PI * 2.0 / seg + t * 0.6;
               float k = (float)(0.5 + 0.5 * Math.cos(a0 * 3.0 - t * 2.4));
               float al = (0.18F + 0.45F * k) * pulse;
               int col = c.trail == 2 ? Gfx.hsv((float)(((double)i / seg + t * 0.1) % 1.0), 0.6F, 1.0F) : acc;
               quad(
                  p(fx0 + Math.cos(a0) * r0, fy0, fz0 + Math.sin(a0) * r0),
                  p(fx0 + Math.cos(a1) * r0, fy0, fz0 + Math.sin(a1) * r0),
                  p(fx0 + Math.cos(a1) * r1, fy0, fz0 + Math.sin(a1) * r1),
                  p(fx0 + Math.cos(a0) * r1, fy0, fz0 + Math.sin(a0) * r1),
                  col,
                  al,
                  col,
                  al,
                  col,
                  0.0F,
                  col,
                  0.0F
               );
            }

            quads = null;
         }
      }
   }

   private static double skyEl() {
      double base = -camPitch + 3.0F;
      return Core.cfg().lyricsUnderground && !skyOpen ? base : Math.max(-6.0, Math.min(60.0, base));
   }

   private static double degPerPx(Minecraft mc, Config c) {
      double guiH = mc.getWindow().getGuiScaledHeight();
      double focalRef = Math.max(1.0, guiH) / 2.0 / Math.tan(Math.toRadians(35.0));
      return Math.toDegrees(c.lyricsSize / focalRef);
   }

   private static void drawLyrics(Minecraft mc, Config c, MultiBufferSource buffers, int color, float dt) {
      NowPlaying np = SpotifyClient.now();
      long now = System.currentTimeMillis();
      int li = -1;
      long prog = 0L;
      if (np.hasTrack() && np.lyricsState == NowPlaying.LyricsState.SYNCED && np.playing) {
         prog = np.progressNow(c.lyricsOffsetMs + 120);
         li = np.lineIndex(prog);
         if (li >= 0 && prog > np.lineEnd(li) + 400L) {
            li = -1;
         }
      }

      double dpp = degPerPx(mc, c);
      if (li != activeIdx || !String.valueOf(np.trackId).equals(activeTrack)) {
         if (active != null) {
            active.exitAt = now;
            exiting.add(active);
         }

         active = null;
         activeIdx = li;
         activeTrack = String.valueOf(np.trackId);
         if (li >= 0 && !np.lines.get(li).text().isBlank()) {
            active = new WorldSky.SkyLine(np.lines.get(li), camYaw, skyEl(), dpp);
         }
      }

      exiting.removeIf(sx -> now - sx.exitAt > 520L);

      while (exiting.size() > 1) {
         exiting.remove(0);
      }

      for (WorldSky.SkyLine s : exiting) {
         drawLine(mc, c, buffers, s, color, -1.0F, now, dt, dpp);
      }

      if (active != null) {
         drawLine(mc, c, buffers, active, color, np.wordPosition(li, prog), now, dt, dpp);
      }
   }

   private static double angDiff(double a, double b) {
      return ((a - b) % 360.0 + 540.0) % 360.0 - 180.0;
   }

   private static void drawLine(Minecraft mc, Config c, MultiBufferSource buffers, WorldSky.SkyLine s, int color, float wp, long now, float dt, double dpp) {
      if (c.lyricsFollow && !c.lyricsViewLock && s.exitAt == 0L) {
         double dAz = angDiff(camYaw, s.baseAz);
         double kh = 1.0 - Math.exp(-3.0 * dt);
         if (Math.abs(dAz) > 26.0) {
            s.baseAz = s.baseAz + (dAz - Math.signum(dAz) * 26.0) * kh;
         }

         double dEl = skyEl() - s.baseEl;
         double kv = 1.0 - Math.exp(-(Math.abs(dEl) > 6.0 ? 4.0 : 1.5) * dt);
         s.baseEl += dEl * kv;
      }

      float exit = s.exitAt == 0L ? 0.0F : Anim.clamp((float)(now - s.exitAt) / 480.0F);
      float lineA = (1.0F - Anim.outCubic(exit)) * c.lyricsOpacity;
      if (!(lineA <= 0.01F)) {
         String[] words = s.line.words();
         int cur = wp < 0.0F ? words.length : (int)Math.floor(wp);
         Font font = mc.font;
         double d = dist * 0.9;
         float k = (float)(Math.toRadians(dpp) * d);
         int space = Gfx.w(" ", Gfx.LYRICS);
         int shadow = Gfx.lerpColor(color, 656916, 0.75F);
         long startDelay = !exiting.isEmpty() && s.exitAt == 0L ? 420L : 0L;
         NowPlaying npB = SpotifyClient.now();
         float beat = c.fxBeatPulse ? npB.beatPulse(npB.progressNow(c.lyricsOffsetMs)) : 0.0F;
         boolean through = c.lyricsUnderground && !skyOpen;
         int glowCol = c.lyricsGlowColor < 0 ? color : LYRIC_COLORS[Math.floorMod(c.lyricsGlowColor, LYRIC_COLORS.length)];

         for (WorldSky.Chunk ch : s.chunks) {
            if (s.exitAt != 0L || ch.from <= cur) {
               float kk = k * ch.size;
               double az = Math.toRadians(s.baseAz + ch.offAz);
               double el = Math.toRadians(s.baseEl + ch.offEl + Anim.outCubic(exit) * 3.0F);
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

               Matrix4f m0 = M0.set(base)
                  .mul(
                     TMP.set(
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
               float lx = -ch.widthPx / 2.0F;

               for (int i = ch.from; i < ch.to; i++) {
                  String word = words[i];
                  int ww = Gfx.w(word, Gfx.LYRICS);
                  boolean on = s.exitAt != 0L || i <= cur;
                  if (on && s.shown[i] == 0L) {
                     s.shown[i] = Math.max(now, s.born + startDelay);
                  }

                  if (!on) {
                     lx += ww + space;
                  } else {
                     float raw = Anim.since(s.shown[i], 450L);
                     float e = Anim.outCubic(raw);
                     float a = e * lineA;
                     boolean current = s.exitAt == 0L && i == cur;
                     Matrix4f m = WM.set(m0);
                     if (current && beat > 0.01F) {
                        float bs = 1.0F + 0.07F * beat;
                        m.translate(lx + ww / 2.0F, 0.0F, 0.0F).scale(bs, bs, 1.0F).translate(-(lx + ww / 2.0F), 0.0F, 0.0F);
                     }

                     switch (c.lyricsAnim) {
                        case 1:
                           float ps = 1.3F - 0.3F * Anim.outBack(raw);
                           m.translate(lx + ww / 2.0F, 0.0F, 0.0F).scale(ps, ps, 1.0F).translate(-ww / 2.0F, -4.5F, 0.0F);
                           break;
                        case 2:
                           m.translate(lx - (1.0F - e) * 10.0F, -4.5F, 0.0F);
                           break;
                        default:
                           m.translate(lx, -4.5F + (1.0F - e) * 3.0F, 0.0F);
                     }

                     int glowN = Q_GLOW[q()];
                     boolean glowOn = c.lyricsGlow && AutoPerf.allow(3);
                     if (glowOn && glowN > 0) {
                        float gs = c.lyricsGlowStrength * (current ? 1.25F : 1.0F) * (8.0F / glowN) * 0.75F;
                        float pad = 5.5F;
                        Halo.draw(
                           new Matrix4f(m),
                           -pad,
                           -pad * 0.8F,
                           ww + pad,
                           9.0F + pad * 0.8F,
                           Gfx.argbRaw(glowCol, Math.min(0.35F, a * 0.18F * gs / (8.0F / glowN))),
                           buffers
                        );
                     }

                     LFConfig lfc = ConfigManager.get();
                     Themes.Theme lth = Themes.current();
                     boolean clean = lfc.textStyle == LFConfig.TextStyle.CLEAN;
                     if (lfc.textShadow == 1) {
                        int sc = lfc.shadowColor != 0 ? lfc.shadowColor : (clean ? 1381162 : Col.darken(lth.a(), 0.75F));
                        word(font, buffers, m, word, 0.3F, 0.5F, 0.02F, sc & 16777215, a, through);
                     } else if (lfc.textShadow == 2) {
                        int sc = lfc.shadowColor != 0 ? lfc.shadowColor : 657938;
                        word(font, buffers, m, word, 0.65F, 1.0F, 0.01F, sc & 16777215, a, through);
                        word(font, buffers, m, word, 0.35F, 0.55F, 0.02F, sc & 16777215, a, through);
                     }

                     int col = current ? Gfx.lerpColor(color, 16777215, 0.35F) : color;
                     word(font, buffers, m, word, 0.0F, 0.0F, 0.12F, col, a, through);
                     lx += ww + space;
                  }
               }
            }
         }
      }
   }

   private static Component lyricComp(String w) {
      String f = String.valueOf(ConfigManager.get().font);
      if (!f.equals(compFontId)) {
         COMP.clear();
         compFontId = f;
         activeIdx = -2;
      } else if (COMP.size() > 400) {
         COMP.clear();
      }

      return COMP.computeIfAbsent(w, k -> Gfx.comp(k, Gfx.LYRICS));
   }

   private static void word(Font font, MultiBufferSource buffers, Matrix4f m, String w, float x, float y, float z, int rgb, float a, boolean through) {
      if (!(a <= 0.03F)) {
         Matrix4f mm = SCR.set(m).translate(x, y, z);
         font.drawInBatch(
            lyricComp(w), 0.0F, 0.0F, Gfx.argbRaw(rgb, a), false, mm, buffers, through ? DisplayMode.SEE_THROUGH : DisplayMode.NORMAL, 0, 15728880
         );
      }
   }

   private static final class Chunk {
      int from;
      int to;
      int widthPx;
      double offAz;
      double offEl;
      float yawTilt;
      float roll;
      float size = 1.0F;
   }

   private static final class SkyLine {
      final NowPlaying.Line line;
      final long[] shown;
      final List<WorldSky.Chunk> chunks = new ArrayList<>();
      double baseAz;
      double baseEl;
      final long born = System.currentTimeMillis();
      long exitAt = 0L;

      SkyLine(NowPlaying.Line l, double az, double el, double degPerPx) {
         this.line = l;
         this.shown = new long[l.words().length];
         this.baseAz = az;
         this.baseEl = el;
         String[] w = l.words();
         int space = Gfx.w(" ", Gfx.LYRICS);
         int i = 0;

         while (i < w.length) {
            int j = i + 1;

            for (int chars = w[i].length(); j < w.length && j - i < 3 && chars + w[j].length() <= (j - i == 1 ? 12 : 9); j++) {
               chars += w[j].length();
            }

            WorldSky.Chunk c = new WorldSky.Chunk();
            c.from = i;
            c.to = j;

            for (int k = i; k < j; k++) {
               c.widthPx = c.widthPx + Gfx.w(w[k], Gfx.LYRICS) + (k > i ? space : 0);
            }

            this.chunks.add(c);
            i = j;
         }

         double gap = space * 2.4 * degPerPx;
         int n = this.chunks.size();
         int rowsN = n <= 2 ? 1 : (n >= 6 && WorldSky.RND.nextFloat() < 0.35F ? 3 : 2);
         if (n == 3 && WorldSky.RND.nextFloat() < 0.35F) {
            rowsN = 1;
         }

         List<List<WorldSky.Chunk>> rows = new ArrayList<>();
         int idx = 0;

         for (int r = 0; r < rowsN; r++) {
            int left = n - idx;
            int rowsLeft = rowsN - r;
            int take = rowsLeft == 1
               ? left
               : Math.max(1, Math.min(left - (rowsLeft - 1), Math.round((float)left / rowsLeft + (WorldSky.RND.nextFloat() - 0.5F) * 1.2F)));
            rows.add(new ArrayList<>(this.chunks.subList(idx, idx + take)));
            idx += take;
         }

         int big = WorldSky.RND.nextInt(n);

         for (int k = 0; k < n; k++) {
            this.chunks.get(k).size = (k == big ? 1.22F : 0.86F) + WorldSky.RND.nextFloat() * 0.2F;
         }

         double rowH = 17.0 * degPerPx;
         double side = (WorldSky.RND.nextDouble() * 2.0 - 1.0) * 20.0;
         if (Math.signum(side) == WorldSky.lastSide && WorldSky.RND.nextFloat() < 0.65F) {
            side = -side;
         }

         WorldSky.lastSide = Math.signum(side);
         double lift = 2.5 + WorldSky.RND.nextDouble() * 6.0;
         double stair = (WorldSky.RND.nextBoolean() ? 1 : -1) * (5.0 + WorldSky.RND.nextDouble() * 8.0);

         for (int r = 0; r < rows.size(); r++) {
            List<WorldSky.Chunk> rr = rows.get(r);
            double total = -gap;

            for (WorldSky.Chunk c : rr) {
               total += gap + c.widthPx * degPerPx * c.size;
            }

            if (total > 58.0) {
               float f = (float)(58.0 / total);

               for (WorldSky.Chunk c : rr) {
                  c.size *= f;
               }

               total = 58.0;
            }

            double rowShift = side + r * stair + (WorldSky.RND.nextDouble() - 0.5) * 4.0;
            double maxShift = Math.max(0.0, 46.0 - total / 2.0);
            rowShift = Math.max(-maxShift, Math.min(maxShift, rowShift));
            double y = lift + (rows.size() - 1 - r) * rowH + (WorldSky.RND.nextDouble() - 0.5) * rowH * 0.15;
            double x = rowShift - total / 2.0;

            for (int k = 0; k < rr.size(); k++) {
               WorldSky.Chunk c = rr.get(k);
               double cw = c.widthPx * degPerPx * c.size;
               c.offAz = x + cw / 2.0;
               c.offEl = y + (k % 2 == 0 ? 0.8 : -0.6) + (WorldSky.RND.nextDouble() - 0.5) * 0.8;
               x += cw + gap;
               c.yawTilt = (float)Math.toRadians((WorldSky.RND.nextFloat() < 0.5F ? -1 : 1) * (5.0F + WorldSky.RND.nextFloat() * 14.0F));
               c.roll = (float)Math.toRadians((WorldSky.RND.nextFloat() - 0.5F) * 9.0F);
            }
         }
      }
   }
}
