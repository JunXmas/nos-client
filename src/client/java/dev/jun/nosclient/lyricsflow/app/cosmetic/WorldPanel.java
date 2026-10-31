package dev.jun.nosclient.lyricsflow.app.cosmetic;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jun.nosclient.lyricsflow.LyricsFlow;
import dev.jun.nosclient.lyricsflow.app.Core;
import dev.jun.nosclient.lyricsflow.app.config.Config;
import dev.jun.nosclient.lyricsflow.app.hud.Gfx;
import dev.jun.nosclient.lyricsflow.app.hud.SkyThumbs;
import dev.jun.nosclient.lyricsflow.app.lyrics.NowPlaying;
import dev.jun.nosclient.lyricsflow.app.lyrics.SpotifyClient;
import dev.jun.nosclient.lyricsflow.app.sky.SkyImage;
import dev.jun.nosclient.lyricsflow.app.sky.SkyPreset;
import dev.jun.nosclient.lyricsflow.app.util.Fx;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import dev.jun.nosclient.lyricsflow.gui.CoverArt;
import dev.jun.nosclient.lyricsflow.gui.LyricsFlowScreen;
import dev.jun.nosclient.lyricsflow.media.MediaManager;
import dev.jun.nosclient.lyricsflow.media.Track;
import dev.jun.nosclient.lyricsflow.render.Cosmetics;
import dev.jun.nosclient.lyricsflow.render.Fonts;
import dev.jun.nosclient.lyricsflow.render.Themes;
import dev.jun.nosclient.lyricsflow.render.WorldLyrics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class WorldPanel {
   static final int W = 380;
   static final int H = 222;
   static final float K = 0.0105F;
   private static final float RCX = 98.0F;
   private static final float RCY = 104.0F;
   private static final float RR = 78.0F;
   private static final float BX = 62.0F;
   private static final float CX0 = 206.0F;
   private static final float CX1 = 366.0F;
   private static final int ACCENT = 10321407;
   private static final int ACCENT2 = 14471167;
   private static final int DEEP = 5914576;
   private static final int TOP = 1709620;
   private static final int BOTTOM = 723479;
   private static final int LIGHT = 15728880;
   private static RenderType flat;
   private final Vec3 pos;
   private final float rx;
   private final float rz;
   private final float nx;
   private final float nz;
   private final List<WorldPanel.Hit> hits = new ArrayList<>();
   private final Map<String, Float> hov = new HashMap<>();
   private int page;
   private int skyPage;
   private int hatPage;
   private final long born = System.currentTimeMillis();
   private long closing = -1L;
   private long pageTime = 0L;
   private long lastNs = System.nanoTime();
   private float dt;
   private float spin;
   private boolean hovered;
   private boolean sndLand;
   private boolean sndClose;
   private float hx;
   private float hy;
   private static final String[] TABS = new String[]{"sky", "lyrics", "look", "cosm", "more"};
   private Matrix4f m;
   private float ox;
   private float oy;
   private VertexConsumer q;
   private final List<Runnable> late = new ArrayList<>();
   private int fontPage;

   private static RenderType flat() {
      if (flat == null) {
         flat = RenderType.create("lyricsflow_panel", RenderSetup.builder(RenderPipelines.DEBUG_QUADS).createRenderSetup());
      }

      return flat;
   }

   private static String tr(String key) {
      return Component.translatable("lyricsflow.wp." + key).getString();
   }

   WorldPanel(Minecraft mc) {
      Camera cam = mc.gameRenderer.getMainCamera();
      Vector3fc f = cam.forwardVector();
      double fl = Math.sqrt(f.x() * f.x() + f.z() * f.z());
      float fx = fl < 1.0E-4 ? 0.0F : (float)(f.x() / fl);
      float fz = fl < 1.0E-4 ? 1.0F : (float)(f.z() / fl);
      Vec3 c = cam.position();
      this.pos = new Vec3(c.x + fx * 3.2, c.y - 0.05, c.z + fz * 3.2);
      this.nx = -fx;
      this.nz = -fz;
      this.rx = -fz;
      this.rz = fx;
      play(mc, SoundEvents.AMETHYST_BLOCK_CHIME, 1.3F, 0.8F);
   }

   Vec3 pos() {
      return this.pos;
   }

   boolean hovered() {
      return this.hovered;
   }

   boolean closing() {
      return this.closing >= 0L;
   }

   void close() {
      if (this.closing < 0L) {
         this.closing = System.currentTimeMillis();
      }
   }

   boolean finished() {
      return this.closing >= 0L && System.currentTimeMillis() - this.closing > 900L;
   }

   private float age() {
      return (float)(System.currentTimeMillis() - this.born);
   }

   private float closeAge() {
      return this.closing < 0L ? -1.0F : (float)(System.currentTimeMillis() - this.closing);
   }

   void tick(Minecraft mc) {
      if (!this.sndLand && this.age() > 860.0F) {
         this.sndLand = true;
         play(mc, SoundEvents.BEACON_POWER_SELECT, 1.8F, 0.3F);
      }

      if (!this.sndClose && this.closing >= 0L && this.closeAge() > 360.0F) {
         this.sndClose = true;
         play(mc, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.7F);
      }
   }

   private static void play(Minecraft mc, SoundEvent ev, float pitch, float vol) {
      try {
         mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, vol));
      } catch (Throwable var5) {
      }
   }

   private void aim(Camera cam) {
      this.hovered = false;
      if (!(this.age() < 1200.0F) && this.closing < 0L) {
         Vec3 o = cam.position();
         Vector3fc d = cam.forwardVector();
         double denom = d.x() * this.nx + d.z() * this.nz;
         if (!(denom > -1.0E-4)) {
            double t = ((this.pos.x - o.x) * this.nx + (this.pos.z - o.z) * this.nz) / denom;
            if (!(t <= 0.0) && !(t > 14.0)) {
               double px = o.x + d.x() * t - this.pos.x;
               double py = o.y + d.y() * t - this.pos.y;
               double pz = o.z + d.z() * t - this.pos.z;
               float u = (float)((px * this.rx + pz * this.rz) / 0.0105F) + 190.0F;
               float v = 111.0F - (float)(py / 0.0105F);
               if (!(u < 14.0F) && !(v < 0.0F) && !(u > 380.0F) && !(v > 222.0F)) {
                  this.hovered = true;
                  this.hx = u;
                  this.hy = v;
               }
            }
         }
      }
   }

   private boolean over(float x, float y, float w, float h) {
      return this.hovered && this.hx >= x && this.hy >= y && this.hx < x + w && this.hy < y + h;
   }

   private float hv(String id, boolean on) {
      float v = this.hov.getOrDefault(id, 0.0F);
      v += ((on ? 1.0F : 0.0F) - v) * Math.min(1.0F, this.dt * 14.0F);
      this.hov.put(id, v);
      return v;
   }

   void click() {
      if (this.hovered && this.closing < 0L) {
         for (int i = this.hits.size() - 1; i >= 0; i--) {
            WorldPanel.Hit h = this.hits.get(i);
            if (this.hx >= h.x() && this.hy >= h.y() && this.hx < h.x() + h.w() && this.hy < h.y() + h.h()) {
               Fx.sound(Fx.Sfx.CLICK);
               h.run().run();
               Core.saveConfig();
               ConfigManager.save();
               return;
            }
         }
      }
   }

   private void go(int p) {
      if (p != this.page) {
         this.page = p;
         this.pageTime = System.currentTimeMillis();
      }
   }

   void render(Minecraft mc, MultiBufferSource buffers, Matrix4f base) {
      Camera cam = mc.gameRenderer.getMainCamera();
      this.aim(cam);
      long ns = System.nanoTime();
      this.dt = Math.min(0.1F, (float)(ns - this.lastNs) / 1.0E9F);
      this.lastNs = ns;
      float t = this.age();
      float c = this.closeAge();
      float fly;
      float slide;
      float content;
      float arm;
      if (c < 0.0F) {
         fly = inOut(clamp(t / 900.0F));
         slide = outCubic(clamp((t - 820.0F) / 460.0F));
         content = outCubic(clamp((t - 1100.0F) / 300.0F));
         arm = inOut(clamp((t - 1150.0F) / 400.0F));
      } else {
         arm = 1.0F - inOut(clamp(c / 220.0F));
         content = 1.0F - clamp(c / 160.0F);
         slide = 1.0F - inOut(clamp((c - 120.0F) / 300.0F));
         fly = 1.0F - inOut(clamp((c - 380.0F) / 480.0F));
      }

      float w = 3.5F + 16.5F * (float)Math.exp(-t / 420.0F) + (c >= 0.0F ? 10.0F * clamp(c / 600.0F) : 0.0F);
      this.spin = this.spin + w * this.dt;
      Vec3 cp = cam.position();
      float bob = (float)Math.sin(System.currentTimeMillis() / 1200.0) * 0.012F * slide;
      Matrix4f rot = new Matrix4f(this.rx, 0.0F, this.rz, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, this.nx, 0.0F, this.nz, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
      Matrix4f panel = new Matrix4f(base)
         .translate(
            (float)(this.pos.x - cp.x), (float)(this.pos.y - cp.y) + bob, (float)(this.pos.z - cp.z)
         )
         .mul(rot);
      this.q = buffers.getBuffer(flat());
      this.hits.clear();
      this.late.clear();
      Config cf = Core.cfg();
      NowPlaying np = SpotifyClient.now();
      if (slide > 0.01F) {
         float anchor = -0.96599996F;
         this.m = new Matrix4f(panel).translate(anchor, 0.0F, 0.0F).scale(slide, 1.0F, 1.0F).translate(-anchor, 0.0F, 0.0F);
         this.ox = 190.0F;
         this.oy = 111.0F;
         float a = Math.min(1.0F, slide * 1.5F);

         for (int i = 4; i >= 1; i--) {
            this.round(62.0F - i * 2.5F, -i * 2.5F, 318.0F + i * 5.0F, 222.0F + i * 5.0F, 19.0F + i * 2.5F, 10321407, 10321407, 0.05F * a, 0.0F);
         }

         this.round(61.2F, -0.8F, 319.6F, 223.6F, 19.8F, 12167664, 7231688, 0.35F * a, 0.0F);
         this.round(62.0F, 0.0F, 318.0F, 222.0F, 19.0F, 1709620, 723479, 0.96F * a, 0.0F);
         this.round(150.0F, 0.6F, 144.0F, 1.6F, 0.8F, 10321407, 14471167, 0.7F * a, 0.0F);
         if (content > 0.01F) {
            this.body(mc, cf, np, content);
         }
      }

      float recX = -0.96599996F;
      float recY = 0.0735F;
      Vector3f end = new Vector3f(this.rx * recX, recY + bob, this.rz * recX)
         .add((float)(this.pos.x - cp.x), (float)(this.pos.y - cp.y), (float)(this.pos.z - cp.z));
      Vector3fc f = cam.forwardVector();
      Vector3fc u = cam.upVector();
      Vector3f start = new Vector3f(f).mul(0.9F).add(new Vector3f(u).mul(-0.3F));
      Vector3f p = new Vector3f(start).lerp(end, fly).add(0.0F, (float)Math.sin(Math.PI * fly) * 0.45F, 0.0F);
      float scale = 0.12F + 0.88F * outCubic(fly);
      float tilt = (1.0F - fly) * 70.0F;
      float ra = Math.min(1.0F, (c < 0.0F ? t : 900.0F - c) / 150.0F);
      if (ra > 0.01F) {
         this.m = new Matrix4f(base).translate(p).mul(rot).rotateX((float)Math.toRadians(-tilt)).scale(scale).translate(0.0F, 0.0F, 0.006F);
         this.ox = 0.0F;
         this.oy = 0.0F;
         this.record(np, ra, arm);
      }

      if (this.hovered) {
         this.m = new Matrix4f(panel);
         this.ox = 190.0F;
         this.oy = 111.0F;
         this.ring(this.hx, this.hy, 0.0F, 2.2F, 0.0F, 360.0F, 16777215, 0.95F, 16777215, 0.95F, 0.02F, 12);
         this.ring(this.hx, this.hy, 2.2F, 5.5F, 0.0F, 360.0F, 10321407, 0.35F, 10321407, 0.0F, 0.02F, 12);
      }

      if (buffers instanceof BufferSource bs) {
         bs.endBatch(flat());
      }

      for (Runnable r : this.late) {
         r.run();
      }
   }

   private void record(NowPlaying np, float a, float arm) {
      this.round(-84.8F, -104.8F, 169.6F, 223.6F, 19.8F, 12167664, 7231688, 0.35F * a, 5.0E-4F);
      this.round(-84.0F, -104.0F, 168.0F, 222.0F, 19.0F, 1709620, 723479, 0.97F * a, 0.001F);
      float cs = 124.0F;
      float cx0 = -cs / 2.0F;
      float cy0 = -86.0F;
      if (np.hasTrack() && CoverArt.ready()) {
         this.round(cx0 - 1.5F, cy0 - 1.5F, cs + 3.0F, cs + 3.0F, 9.0F, 10321407, 7231688, 0.35F * a, 0.002F);
         this.image(CoverArt.id(), cx0, cy0, cs, cs, a);
      } else {
         this.round(cx0, cy0, cs, cs, 10.0F, 7231688, 2894902, a, 0.002F);
         this.textAt(this.m, "♪", 0.0F, cy0 + cs / 2.0F - 14.0F, 3.0F, 16777215, 0.8F * a, true);
      }

      String title = np.hasTrack() ? np.title : tr("nomusic");
      String artist = np.hasTrack() ? np.artist : "";
      this.textAt(this.m, ellip(title, 150.0F, 0.85F), 0.0F, 46.0F, 0.85F, 16777215, a, true);
      if (!artist.isEmpty()) {
         this.textAt(this.m, ellip(artist, 150.0F, 0.62F), 0.0F, 59.0F, 0.62F, 12961221, 0.85F * a, true);
      }

      float bw = 136.0F;
      float bx = -bw / 2.0F;
      float byy = 70.0F;
      float frac = 0.0F;
      long pos = 0L;
      if (np.hasTrack() && np.durationMs > 0L) {
         pos = np.progressNow(0);
         frac = clamp((float)pos / (float)np.durationMs);
      }

      this.round(bx, byy, bw, 3.0F, 1.5F, 16777215, 16777215, 0.18F * a, 0.003F);
      if (frac > 0.0F) {
         this.round(bx, byy, Math.max(3.0F, bw * frac), 3.0F, 1.5F, 2021216, 2021216, a, 0.004F);
      }

      if (np.hasTrack()) {
         this.textAt(this.m, fmt(pos), bx, 75.5F, 0.5F, 12961221, 0.8F * a, false);
         String dur = fmt(np.durationMs);
         this.textAt(this.m, dur, bx + bw - Minecraft.getInstance().font.width(dur) * 0.5F, 75.5F, 0.5F, 12961221, 0.8F * a, false);
      }

      Track tr = MediaManager.get().track();
      if (tr != null && !tr.isEmpty()) {
         boolean spotify = "Spotify".equals(tr.sourceApp);
         String src = spotify ? "Spotify" : ("Browser".equals(tr.sourceApp) ? "YouTube" : tr.sourceApp);
         float tw = Minecraft.getInstance().font.width(src) * 0.6F;
         float sx = -(tw + 15.0F) / 2.0F;
         this.image(LyricsFlow.id(spotify ? "textures/gui/logos/spotify.png" : "textures/gui/logos/youtube.png"), sx - 2.0F, 82.0F, 10.0F, 10.0F, a);
         this.textAt(this.m, src, sx + 13.0F, 84.0F, 0.6F, 16777215, a, false);
      }
   }

   private static String fmt(long ms) {
      long s = Math.max(0L, ms / 1000L);
      return s / 60L + ":" + String.format("%02d", s % 60L);
   }

   private void body(Minecraft mc, Config cf, NowPlaying np, float a) {
      float by = 207.0F;
      this.roundBtn("prev", 70.0F, by, 8.0F, "⏮", a, () -> SpotifyClient.control(SpotifyClient.Cmd.PREVIOUS));
      this.roundBtn("play", 98.0F, by, 10.0F, np.playing ? "⏸" : "▶", a, () -> SpotifyClient.control(SpotifyClient.Cmd.PLAYPAUSE));
      this.roundBtn("next", 126.0F, by, 8.0F, "⏭", a, () -> SpotifyClient.control(SpotifyClient.Cmd.NEXT));
      int rep = MediaManager.get().repeat();
      this.roundBtn("repeat", 150.0F, by, 6.5F, rep == 1 ? "↻1" : (rep == 2 ? "↻•" : "↻"), a, () -> MediaManager.get().cycleRepeat());
      this.hits.add(new WorldPanel.Hit(36.0F, 18.0F, 124.0F, 124.0F, () -> SpotifyClient.control(SpotifyClient.Cmd.PLAYPAUSE)));
      this.hits.add(new WorldPanel.Hit(30.0F, 169.0F, 136.0F, 10.0F, () -> {
         Track tk = MediaManager.get().track();
         if (tk != null && tk.durationMs > 0L) {
            float f = clamp((this.hx - 30.0F) / 136.0F);
            MediaManager.get().seek((long)(f * (float)tk.durationMs));
         }
      }));
      Font font = Minecraft.getInstance().font;
      this.text("LyricsFlow", 206.0F, 10.0F, 0.85F, 16777215, a);
      float xh = this.hv("x", this.over(351.0F, 6.0F, 15.0F, 15.0F));
      this.ring(
         358.5F,
         13.5F,
         0.0F,
         7.5F,
         0.0F,
         360.0F,
         Gfx.lerpColor(16777215, 16734842, xh),
         (0.08F + 0.55F * xh) * a,
         Gfx.lerpColor(16777215, 16734842, xh),
         (0.08F + 0.55F * xh) * a,
         0.002F,
         16
      );
      this.text("✕", 355.5F, 10.5F, 0.7F, 16777215, a);
      this.hits.add(new WorldPanel.Hit(351.0F, 6.0F, 15.0F, 15.0F, this::close));
      String nl = tr("normal");
      float nw = font.width(nl) * 0.62F + 12.0F;
      float nhv = this.hv("nrm", this.over(346.0F - nw, 6.0F, nw, 15.0F));
      this.round(346.0F - nw, 6.0F, nw, 15.0F, 7.5F, 16777215, 16777215, (0.07F + 0.12F * nhv) * a, 0.002F);
      this.text(nl, 352.0F - nw, 10.5F, 0.62F, 16777215, (0.8F + 0.2F * nhv) * a);
      this.hits.add(new WorldPanel.Hit(346.0F - nw, 6.0F, nw, 15.0F, () -> {
         cf.menuMode = 0;
         PhoneMenu.closeNow();
         mc.setScreen(new LyricsFlowScreen());
      }));
      float tabW = (160.0F - 3.0F * (TABS.length - 1)) / TABS.length;

      for (int i = 0; i < TABS.length; i++) {
         float x = 206.0F + i * (tabW + 3.0F);
         boolean on = this.page == i;
         float h = this.hv("tab" + i, this.over(x, 203.0F, tabW, 14.0F));
         int k = i;
         if (on) {
            this.round(x, 203.0F, tabW, 14.0F, 7.0F, 10321407, 8018664, 0.9F * a, 0.002F);
         } else {
            this.round(x, 203.0F, tabW, 14.0F, 7.0F, 16777215, 16777215, (0.05F + 0.08F * h) * a, 0.002F);
         }

         String lbl = ellip(tr(TABS[i]), tabW - 6.0F, 0.62F);
         this.text(lbl, x + (tabW - font.width(lbl) * 0.62F) / 2.0F, 207.0F, 0.62F, 16777215, (on ? 1.0F : 0.7F + 0.3F * h) * a);
         this.hits.add(new WorldPanel.Hit(x, 203.0F, tabW, 14.0F, () -> this.go(k)));
      }

      String song = np.hasTrack() ? "♪  " + np.title + "  ·  " + np.artist : "♪  " + tr("nomusic");
      this.text(ellip(song, 160.0F, 0.62F), 206.0F, 28.0F, 0.62F, 14471167, 0.75F * a);
      float pa = a * outCubic(clamp((float)(System.currentTimeMillis() - this.pageTime) / 220.0F));
      switch (this.page) {
         case 0:
            this.skies(cf, pa);
            break;
         case 1:
            this.lyrics(cf, pa);
            break;
         case 2:
            this.look(pa);
            break;
         case 3:
            this.style(cf, pa);
            break;
         default:
            this.more(mc, cf, pa);
      }
   }

   private void skies(Config cf, float a) {
      SkyPreset[] all = SkyPreset.order();
      this.toggle2("skon", 206.0F, 45.0F, 78.0F, tr("skyon"), cf.sky >= 0, () -> cf.sky = cf.sky >= 0 ? -1 : Math.max(0, cf.lastSky), a);
      this.toggle2("skpic", 288.0F, 45.0F, 78.0F, tr("picture"), cf.skyImage, () -> {
         cf.skyImage = !cf.skyImage;
         SkyImage.reload();
      }, a);
      int per = 6;
      int pages = (all.length + per - 1) / per;
      this.skyPage = Math.floorMod(this.skyPage, pages);
      float tw = 48.0F;
      float th = 27.0F;

      for (int i = 0; i < per; i++) {
         int idx = this.skyPage * per + i;
         if (idx >= all.length) {
            break;
         }

         SkyPreset p = all[idx];
         float x = 206.0F + i % 3 * (tw + 8.0F);
         float y = 66.0F + i / 3 * (th + 13.0F);
         boolean on = cf.sky == p.ordinal();
         float h = this.hv("sky" + idx, this.over(x - 1.0F, y - 1.0F, tw + 2.0F, th + 12.0F));
         float lift = h * 1.2F;
         if (on) {
            this.round(x - 2.0F, y - 2.0F - lift, tw + 4.0F, th + 4.0F, 6.0F, 10321407, 14471167, a, 0.002F);
         } else if (h > 0.01F) {
            this.round(x - 1.5F, y - 1.5F - lift, tw + 3.0F, th + 3.0F, 5.5F, 16777215, 16777215, 0.5F * h * a, 0.002F);
         }

         Identifier img = SkyThumbs.texture(p);
         if (img != null) {
            this.image(img, x, y - lift, tw, th, a);
         } else {
            this.round(x, y - lift, tw, th, 4.0F, p.top, p.horizon, a, 0.003F);
         }

         this.text(ellip(Component.translatable(p.key()).getString(), tw, 0.56F), x + 1.0F, y + th + 3.0F, 0.56F, 16777215, a * (on ? 1.0F : 0.75F + 0.25F * h));
         this.hits.add(new WorldPanel.Hit(x - 1.0F, y - 1.0F, tw + 2.0F, th + 12.0F, () -> {
            cf.sky = p.ordinal();
            cf.lastSky = cf.sky;
         }));
      }

      this.pager("sk", 146.0F, this.skyPage, pages, d -> this.skyPage += d, a);
      this.slider("str", tr("strength"), 162.0F, (cf.skyStrength - 0.1F) / 0.9F, v -> cf.skyStrength = 0.1F + 0.9F * v, a);
      this.slider("spd", tr("speed"), 178.0F, (cf.skySpeed - 0.25F) / 1.75F, v -> cf.skySpeed = 0.25F + 1.75F * v, a);
      this.slider("pat", tr("pattern"), 194.0F, (cf.skyPattern - 0.2F) / 1.3F, v -> cf.skyPattern = 0.2F + 1.3F * v, a);
   }

   private static LFConfig lf() {
      return ConfigManager.get();
   }

   private void lyrics(Config cf, float a) {
      LFConfig c = lf();
      this.toggle(0, tr("lyricssky"), c.showLyrics, () -> c.showLyrics = !c.showLyrics, a);
      this.toggle(1, tr("floating"), c.lyricsStyle == LFConfig.LyricsStyle.FLOATING, () -> {
         c.lyricsStyle = c.lyricsStyle == LFConfig.LyricsStyle.FLOATING ? LFConfig.LyricsStyle.SKY : LFConfig.LyricsStyle.FLOATING;
         WorldLyrics.clear();
      }, a);
      this.toggle2("beat", 206.0F, 87.0F, 78.0F, tr("beat"), c.beatGlow, () -> c.beatGlow = !c.beatGlow, a);
      this.toggle2("notes", 288.0F, 87.0F, 78.0F, tr("notes"), c.musicNotes, () -> c.musicNotes = !c.musicNotes, a);
      this.toggle2("smus", 206.0F, 107.0F, 78.0F, tr("skymusic"), cf.fxSkyMusic, () -> cf.fxSkyMusic = !cf.fxSkyMusic, a);
      this.toggle2("ugr", 288.0F, 107.0F, 78.0F, tr("underground"), c.showUnderground, () -> c.showUnderground = !c.showUnderground, a);
      this.slider("lop", tr("opacity"), 142.0F, (c.textOpacity - 0.2F) / 0.8F, v -> c.textOpacity = 0.2F + 0.8F * v, a);
      this.slider("lsz", tr("size"), 160.0F, (c.lyricsScale - 0.4F) / 2.1F, v -> c.lyricsScale = 0.4F + 2.1F * v, a);
      this.offset(178.0F, a);
   }

   private void offset(float y, float a) {
      LFConfig c = lf();
      Font font = Minecraft.getInstance().font;
      this.text(tr("offset"), 206.0F, y, 0.66F, 16777215, 0.85F * a);
      String v = (c.syncOffsetMs > 0 ? "+" : "") + c.syncOffsetMs + " ms";
      float vw = font.width(v) * 0.66F;
      this.text(v, 330.0F - vw / 2.0F, y, 0.66F, 16777215, a);
      float lh = this.hv("offl", this.over(294.0F, y - 4.0F, 14.0F, 14.0F));
      float rh = this.hv("offr", this.over(352.0F, y - 4.0F, 14.0F, 14.0F));
      this.round(294.0F, y - 4.0F, 14.0F, 14.0F, 7.0F, 16777215, 16777215, (0.06F + 0.12F * lh) * a, 0.002F);
      this.round(352.0F, y - 4.0F, 14.0F, 14.0F, 7.0F, 16777215, 16777215, (0.06F + 0.12F * rh) * a, 0.002F);
      this.text("‹", 299.0F, y, 0.66F, 16777215, a);
      this.text("›", 357.0F, y, 0.66F, 16777215, a);
      this.hits.add(new WorldPanel.Hit(294.0F, y - 4.0F, 14.0F, 14.0F, () -> c.syncOffsetMs = Math.max(-2000, c.syncOffsetMs - 100)));
      this.hits.add(new WorldPanel.Hit(352.0F, y - 4.0F, 14.0F, 14.0F, () -> c.syncOffsetMs = Math.min(2000, c.syncOffsetMs + 100)));
   }

   private void look(float a) {
      LFConfig c = lf();
      List<Themes.Theme> th = Themes.ALL;
      this.text(tr("color"), 206.0F, 45.0F, 0.62F, 16777215, 0.55F * a);
      int n = Math.min(th.size(), 20);

      for (int i = 0; i < n; i++) {
         Themes.Theme t = th.get(i);
         float cx = 212.0F + i % 10 * 16.0F;
         float cy = 60.0F + i / 10 * 15.0F;
         boolean on = !c.customTheme && c.singleColor == 0 && c.theme.equals(t.id());
         float h = this.hv("col" + i, this.over(cx - 6.0F, cy - 6.0F, 12.0F, 12.0F));
         if (on) {
            this.ring(cx, cy, 5.2F, 6.6F, 0.0F, 360.0F, 16777215, a, 16777215, a, 0.003F, 20);
         }

         this.ring(cx, cy, 0.0F, 4.6F + h * 0.6F, 0.0F, 360.0F, t.main() & 16777215, a, t.a() & 16777215, a, 0.004F, 18);
         this.hits.add(new WorldPanel.Hit(cx - 6.0F, cy - 6.0F, 12.0F, 12.0F, () -> {
            c.theme = t.id();
            c.customTheme = false;
            c.singleColor = 0;
         }));
      }

      List<Fonts.LyricFont> fonts = Fonts.lyricsAll();
      int per = 6;
      int pages = (fonts.size() + per - 1) / per;
      this.fontPage = Math.floorMod(this.fontPage, pages);
      this.text(tr("font"), 206.0F, 86.0F, 0.62F, 16777215, 0.55F * a);

      for (int i = 0; i < per; i++) {
         int idx = this.fontPage * per + i;
         if (idx >= fonts.size()) {
            break;
         }

         Fonts.LyricFont f = fonts.get(idx);
         this.option("fnt" + idx, 206.0F + i % 2 * 82, 95 + i / 2 * 17, 78.0F, 14.0F, f.name(), f.id().equals(c.font), () -> {
            c.font = f.id();
            WorldLyrics.clear();
         }, a);
      }

      this.pager("fp", 84.0F, this.fontPage, pages, d -> this.fontPage += d, a);
      this.toggle2("caps", 206.0F, 148.0F, 78.0F, tr("caps"), c.allCaps, () -> {
         c.allCaps = !c.allCaps;
         WorldLyrics.clear();
      }, a);
      this.toggle2("glow", 288.0F, 148.0F, 78.0F, tr("glow"), c.glow, () -> c.glow = !c.glow, a);
      boolean typed = c.enterAnim == LFConfig.EnterAnim.NEON;
      this.toggle2("typed", 206.0F, 167.0F, 160.0F, tr("typed"), typed, () -> c.enterAnim = typed ? LFConfig.EnterAnim.WORD : LFConfig.EnterAnim.NEON, a);
      String[] sh = new String[]{tr("shadow0"), tr("shadow1"), tr("shadow2")};

      for (int i = 0; i < 3; i++) {
         int k = i;
         this.option("sh" + i, 206.0F + i * 54, 186.0F, 50.0F, 13.0F, sh[i], c.textShadow == i, () -> c.textShadow = k, a);
      }
   }

   private void style(Config cf, float a) {
      String[] ids = Cosmetics.IDS;
      int per = 6;
      int pages = (ids.length + per - 1) / per;
      this.hatPage = Math.floorMod(this.hatPage, pages);
      this.text(tr("hat"), 206.0F, 45.0F, 0.62F, 16777215, 0.55F * a);

      for (int i = 0; i < per; i++) {
         int idx = this.hatPage * per + i;
         if (idx >= ids.length) {
            break;
         }

         this.option(
            "hat" + idx,
            206.0F + i % 2 * 82,
            54 + i / 2 * 18,
            78.0F,
            15.0F,
            Component.translatable("lyricsflow.cosmetic." + ids[idx]).getString(),
            ids[idx].equals(lf().cosmetic),
            () -> lf().cosmetic = ids[idx],
            a
         );
      }

      this.pager("ht", 108.0F, this.hatPage, pages, d -> this.hatPage += d, a);
      this.text(tr("death"), 206.0F, 125.0F, 0.62F, 16777215, 0.55F * a);

      for (int i = 0; i < 4; i++) {
         int k = i;
         this.option(
            "de" + i,
            206.0F + i % 2 * 82,
            134 + i / 2 * 18,
            78.0F,
            15.0F,
            Component.translatable("lyricsflow.ui.death." + i).getString(),
            cf.deathFx == i,
            () -> cf.deathFx = k,
            a
         );
      }

      this.toggle2(
         "dot",
         206.0F,
         174.0F,
         160.0F,
         Component.translatable("lyricsflow.ui.style.deathothers").getString(),
         cf.deathOthers,
         () -> cf.deathOthers = !cf.deathOthers,
         a
      );
   }

   private void more(Minecraft mc, Config cf, float a) {
      LFConfig c = lf();
      this.toggle(0, tr("island"), c.showTopBar, () -> c.showTopBar = !c.showTopBar, a);
      this.toggle(1, tr("clock"), c.clock, () -> c.clock = !c.clock, a);
      this.toggle2("fps", 206.0F, 87.0F, 78.0F, "FPS", c.fps, () -> c.fps = !c.fps, a);
      this.toggle2("png", 288.0F, 87.0F, 78.0F, "Ping", c.ping, () -> c.ping = !c.ping, a);
      this.toggle2("hb", 206.0F, 107.0F, 78.0F, tr("hotbar"), c.customHotbar, () -> c.customHotbar = !c.customHotbar, a);
      this.toggle2("snd", 288.0F, 107.0F, 78.0F, tr("sounds"), c.uiSounds, () -> c.uiSounds = !c.uiSounds, a);
      this.option("m0", 206.0F, 128.0F, 160.0F, 15.0F, "↗  " + tr("bigmenu"), false, () -> {
         PhoneMenu.closeNow();
         mc.setScreen(new LyricsFlowScreen());
      }, a);
      this.toggle2("m1", 206.0F, 145.0F, 160.0F, tr("random"), cf.skyShuffle, () -> {
         cf.skyShuffle = !cf.skyShuffle;
         if (cf.skyShuffle) {
            SkyPreset.shuffle();
         }
      }, a);
      this.option("m2", 206.0F, 162.0F, 160.0F, 15.0F, "↺  " + tr("backnormal"), false, () -> {
         cf.menuMode = 0;
         this.close();
      }, a);
      this.option("m3", 206.0F, 179.0F, 160.0F, 15.0F, "✕  " + tr("close"), false, this::close, a);
   }

   private void toggle(int row, String label, boolean on, Runnable flip, float a) {
      this.toggle2("tg" + row, 206.0F, 47 + row * 20, 160.0F, label, on, flip, a);
   }

   private void toggle2(String id, float x, float y, float w, String label, boolean on, Runnable flip, float a) {
      float h = 16.0F;
      float hh = this.hv(id, this.over(x, y, w, h));
      this.round(x, y, w, h, 6.0F, 16777215, 16777215, (0.035F + 0.05F * hh) * a, 0.002F);
      this.text(ellip(label, w - 34.0F, 0.68F), x + 7.0F, y + 5.0F, 0.68F, 16777215, 0.92F * a);
      float tx = x + w - 25.0F;
      float ty = y + 4.0F;
      this.round(tx, ty, 19.0F, 8.0F, 4.0F, on ? 10321407 : 16777215, on ? 8018664 : 16777215, (on ? 0.95F : 0.16F) * a, 0.003F);
      this.ring(on ? tx + 15.0F : tx + 4.0F, ty + 4.0F, 0.0F, 3.2F, 0.0F, 360.0F, 16777215, a, 15262966, a, 0.004F, 12);
      this.hits.add(new WorldPanel.Hit(x, y, w, h, flip));
   }

   private void option(String id, float x, float y, float w, float h, String label, boolean on, Runnable run, float a) {
      float hh = this.hv(id, this.over(x, y, w, h));
      if (on) {
         this.round(x, y, w, h, 6.0F, 10321407, 8018664, 0.88F * a, 0.002F);
      } else {
         this.round(x, y, w, h, 6.0F, 16777215, 16777215, (0.035F + 0.06F * hh) * a, 0.002F);
      }

      this.text(ellip(label, w - 12.0F, 0.66F), x + 7.0F, y + (h - 6.0F) / 2.0F, 0.66F, 16777215, (on ? 1.0F : 0.8F + 0.2F * hh) * a);
      this.hits.add(new WorldPanel.Hit(x, y, w, h, run));
   }

   private void slider(String id, String label, float y, float v, Consumer<Float> set, float a) {
      v = clamp(v);
      float tx = 258.0F;
      float tw = 366.0F - tx;
      float hh = this.hv(id, this.over(206.0F, y - 4.0F, 160.0F, 14.0F));
      this.text(label, 206.0F, y, 0.66F, 16777215, (0.7F + 0.3F * hh) * a);
      this.round(tx, y + 1.5F, tw, 4.0F, 2.0F, 16777215, 16777215, (0.12F + 0.06F * hh) * a, 0.002F);
      this.round(tx, y + 1.5F, Math.max(4.0F, tw * v), 4.0F, 2.0F, 14471167, 10321407, a, 0.003F);
      this.ring(tx + tw * v, y + 3.5F, 0.0F, 4.5F + hh, 0.0F, 360.0F, 16777215, a, 15262966, a, 0.004F, 14);
      this.hits.add(new WorldPanel.Hit(tx - 4.0F, y - 4.0F, tw + 8.0F, 14.0F, () -> set.accept(clamp((this.hx - tx) / tw))));
   }

   private void roundBtn(String id, float cx, float cy, float r, String icon, float a, Runnable run) {
      float hh = this.hv(id, this.over(cx - r, cy - r, 2.0F * r, 2.0F * r));
      this.ring(cx, cy, 0.0F, r, 0.0F, 360.0F, Gfx.lerpColor(2762309, 10321407, hh), a, Gfx.lerpColor(1841460, 8018664, hh), a, 0.002F, 20);
      float tw = Minecraft.getInstance().font.width(icon) * 0.75F;
      this.text(icon, cx - tw / 2.0F, cy - 3.0F, 0.75F, 16777215, a);
      this.hits.add(new WorldPanel.Hit(cx - r, cy - r, 2.0F * r, 2.0F * r, run));
   }

   private void pager(String id, float y, int cur, int pages, IntConsumer move, float a) {
      if (pages > 1) {
         float h1 = this.hv(id + "l", this.over(320.0F, y, 14.0F, 12.0F));
         float h2 = this.hv(id + "r", this.over(352.0F, y, 14.0F, 12.0F));
         this.round(320.0F, y, 14.0F, 12.0F, 6.0F, 16777215, 16777215, (0.08F + 0.2F * h1) * a, 0.002F);
         this.round(352.0F, y, 14.0F, 12.0F, 6.0F, 16777215, 16777215, (0.08F + 0.2F * h2) * a, 0.002F);
         this.text("‹", 325.0F, y + 2.5F, 0.75F, 16777215, a);
         this.text("›", 357.0F, y + 2.5F, 0.75F, 16777215, a);
         this.text(cur + 1 + "/" + pages, 336.0F, y + 3.0F, 0.6F, 16777215, 0.7F * a);
         this.hits.add(new WorldPanel.Hit(320.0F, y, 14.0F, 12.0F, () -> move.accept(-1)));
         this.hits.add(new WorldPanel.Hit(352.0F, y, 14.0F, 12.0F, () -> move.accept(1)));
      }
   }

   private float lx(float x) {
      return (x - this.ox) * 0.0105F;
   }

   private float ly(float y) {
      return (this.oy - y) * 0.0105F;
   }

   private void vert(float x, float y, float z, int col) {
      this.q.addVertex(this.m, this.lx(x), this.ly(y), z).setColor(col);
   }

   private void quad(float x, float y, float w, float h, int cTop, float aTop, int cBot, float aBot, float z) {
      int t = Gfx.argbRaw(cTop, aTop);
      int b = Gfx.argbRaw(cBot, aBot);
      this.vert(x, y, z, t);
      this.vert(x + w, y, z, t);
      this.vert(x + w, y + h, z, b);
      this.vert(x, y + h, z, b);
   }

   private void poly(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int rgb, float a, float z) {
      int col = Gfx.argbRaw(rgb, a);
      this.vert(x0, y0, z, col);
      this.vert(x1, y1, z, col);
      this.vert(x2, y2, z, col);
      this.vert(x3, y3, z, col);
   }

   private void ring(float cx, float cy, float r0, float r1, float a0, float a1, int cIn, float aIn, int cOut, float aOut, float z, int segs) {
      if (!(aIn <= 0.003F) || !(aOut <= 0.003F)) {
         int ci = Gfx.argbRaw(cIn, aIn);
         int co = Gfx.argbRaw(cOut, aOut);

         for (int i = 0; i < segs; i++) {
            double t0 = Math.toRadians(a0 + (a1 - a0) * i / segs);
            double t1 = Math.toRadians(a0 + (a1 - a0) * (i + 1) / segs);
            float c0 = (float)Math.cos(t0);
            float s0 = (float)Math.sin(t0);
            float c1 = (float)Math.cos(t1);
            float s1 = (float)Math.sin(t1);
            this.vert(cx + c0 * r0, cy + s0 * r0, z, ci);
            this.vert(cx + c0 * r1, cy + s0 * r1, z, co);
            this.vert(cx + c1 * r1, cy + s1 * r1, z, co);
            this.vert(cx + c1 * r0, cy + s1 * r0, z, ci);
         }
      }
   }

   private void round(float x, float y, float w, float h, float r, int top, int bot, float a, float z) {
      if (!(a <= 0.003F)) {
         r = Math.min(r, Math.min(w, h) / 2.0F);
         int n = r > 3.0F ? 5 : 2;
         float step = r / n;

         for (int j = 0; j < n; j++) {
            float yc = r - (j + 0.5F) * step;
            float in = r - (float)Math.sqrt(Math.max(0.0F, r * r - yc * yc));
            this.band(x + in, y + j * step, w - 2.0F * in, step, y, h, top, bot, a, z);
            this.band(x + in, y + h - (j + 1) * step, w - 2.0F * in, step, y, h, top, bot, a, z);
         }

         this.band(x, y + r, w, h - 2.0F * r, y, h, top, bot, a, z);
      }
   }

   private void band(float x, float y, float w, float h, float oy0, float oh, int top, int bot, float a, float z) {
      if (!(h <= 0.0F) && !(w <= 0.0F)) {
         this.quad(x, y, w, h, Gfx.lerpColor(top, bot, (y - oy0) / oh), a, Gfx.lerpColor(top, bot, (y + h - oy0) / oh), a, z);
      }
   }

   private void image(Identifier tex, float x, float y, float w, float h, float a) {
      Matrix4f mm = new Matrix4f(this.m);
      float x0 = this.lx(x);
      float x1 = this.lx(x + w);
      float y0 = this.ly(y);
      float y1 = this.ly(y + h);
      float z = 0.005F;
      int col = Gfx.argbRaw(16777215, a);
      this.late.add(() -> {
         VertexConsumer v = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderTypes.text(tex));
         v.addVertex(mm, x0, y0, z).setColor(col).setUv(0.0F, 0.0F).setLight(15728880);
         v.addVertex(mm, x0, y1, z).setColor(col).setUv(0.0F, 1.0F).setLight(15728880);
         v.addVertex(mm, x1, y1, z).setColor(col).setUv(1.0F, 1.0F).setLight(15728880);
         v.addVertex(mm, x1, y0, z).setColor(col).setUv(1.0F, 0.0F).setLight(15728880);
      });
   }

   private void text(String s, float x, float y, float scale, int rgb, float a) {
      this.textAt(this.m, s, this.lx(x) / 0.0105F, -this.ly(y) / 0.0105F, scale, rgb, a, false);
   }

   private void textAt(Matrix4f mat, String s, float px, float py, float scale, int rgb, float a, boolean centered) {
      if (!(a <= 0.01F) && !s.isEmpty()) {
         Font f = Minecraft.getInstance().font;
         float x = centered ? px - f.width(s) * scale / 2.0F : px;
         Matrix4f tm = new Matrix4f(mat).translate(x * 0.0105F, -py * 0.0105F, 0.012F).scale(0.0105F * scale, -0.0105F * scale, 0.0105F);
         int col = Gfx.argbRaw(rgb, a);
         this.late
            .add(
               () -> {
                  Minecraft mc = Minecraft.getInstance();
                  mc.font
                     .drawInBatch(
                        Component.literal(s), 0.0F, 0.0F, col, false, tm, mc.renderBuffers().bufferSource(), DisplayMode.NORMAL, 0, 15728880
                     );
               }
            );
      }
   }

   private static String ellip(String s, float maxPx, float scale) {
      Font f = Minecraft.getInstance().font;
      if (f.width(s) * scale <= maxPx) {
         return s;
      }

      String t = s;

      while (t.length() > 1 && f.width(t + "...") * scale > maxPx) {
         t = t.substring(0, t.length() - 1);
      }

      return t + "...";
   }

   private static float clamp(float x) {
      return Math.max(0.0F, Math.min(1.0F, x));
   }

   private static float outCubic(float t) {
      float u = 1.0F - t;
      return 1.0F - u * u * u;
   }

   private static float inOut(float t) {
      return t < 0.5F ? 4.0F * t * t * t : 1.0F - (float)Math.pow(-2.0F * t + 2.0F, 3.0) / 2.0F;
   }

   private record Hit(float x, float y, float w, float h, Runnable run) {
   }
}
