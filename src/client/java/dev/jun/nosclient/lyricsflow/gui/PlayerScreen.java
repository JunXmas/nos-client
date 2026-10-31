package dev.jun.nosclient.lyricsflow.gui;

import dev.jun.nosclient.lyricsflow.Keys;
import dev.jun.nosclient.lyricsflow.lyrics.Lyrics;
import dev.jun.nosclient.lyricsflow.media.AppVolume;
import dev.jun.nosclient.lyricsflow.media.MediaManager;
import dev.jun.nosclient.lyricsflow.media.Track;
import dev.jun.nosclient.lyricsflow.render.Gfx;
import dev.jun.nosclient.lyricsflow.render.Hud;
import dev.jun.nosclient.lyricsflow.render.Themes;
import dev.jun.nosclient.lyricsflow.util.Anim;
import dev.jun.nosclient.lyricsflow.util.Col;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class PlayerScreen extends Screen {
   private float openT;
   private boolean closing;
   private float scroll = -1.0F;
   private float userScroll;
   private double lastUserScroll = -10.0;
   private final float[] btnHover = new float[4];
   private final float[] btnPress = new float[4];
   private float[] lineY = new float[0];
   private float px;
   private float py;
   private float pw;
   private float ph;
   private float lx;
   private float lw;
   private float ly;
   private float lh;
   private final Screen parent;
   private final boolean blur;
   private float barX;
   private float barY;
   private float barW;
   private boolean seeking;
   private float seekFrac = -1.0F;
   private float barHover;
   private long seekSentAt;
   private boolean editing;
   private String timeText = "";
   private float tbX;
   private float tbY;
   private float tbW;
   private float tbH;
   private float mX;
   private float pX;
   private float jW;
   private float timeHover;
   private float vX;
   private float vY;
   private float vW;
   private float iconX;
   private boolean volDrag;
   private float volHover;
   private float volShown = -1.0F;
   private float ctlMid;
   private float ctlY;

   public PlayerScreen() {
      this(null, false);
   }

   public PlayerScreen(Screen parent, boolean blur) {
      super(Component.translatable("lyricsflow.player.title"));
      this.parent = parent;
      this.blur = blur;
   }

   protected void init() {
      UiSounds.play(UiSounds.OPEN, 0.9F);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
      if (this.blur) {
         this.renderBlurredBackground(g);
      }
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
      Anim.frame();
      this.openT = Anim.approach(this.openT, this.closing ? 0.0F : 1.0F, this.closing ? 16.0F : 9.0F);
      if (this.closing && this.openT < 0.02F) {
         this.finish();
      } else {
         float a = Anim.easeOutCubic(this.openT);
         Themes.Theme th = Themes.current();
         MediaManager mm = MediaManager.get();
         Track t = mm.track();
         Lyrics l = mm.lyrics();
         long pos = mm.positionRaw();
         Gfx.gradientV(
            g, 0.0F, 0.0F, this.width, this.height, Col.alpha(Col.withAlpha(Col.darken(th.skyTop(), 0.3F), 224), a), Col.alpha(-267778032, a)
         );
         this.pw = Math.min(680, this.width - 30);
         this.ph = Math.min(380, this.height - 30);
         this.px = (this.width - this.pw) / 2.0F;
         this.py = (this.height - this.ph) / 2.0F + (1.0F - a) * 12.0F;
         Gfx.shadow(g, this.px, this.py, this.pw, this.ph, 18.0F, 16.0F, Col.alpha(-16777216, a * 0.6F));
         Gfx.roundGradient(g, this.px, this.py, this.pw, this.ph, 18.0F, Col.alpha(Col.lerp(th.skyTop(), -15593447, 0.4F), a), Col.alpha(-15856620, a));
         float leftW = Math.min(230.0F, this.pw * 0.38F);
         float cs = Math.min(leftW - 40.0F, this.ph - 214.0F);
         float cx = this.px + (leftW - cs) / 2.0F + 6.0F;
         float cy = this.py + 22.0F;
         Gfx.glow(g, cx, cy, cs, cs, 14.0F, 8.0F, Col.alpha(th.a(), a * (0.25F + 0.25F * Hud.beat)));
         if (CoverArt.ready()) {
            CoverArt.draw(g, cx, cy, cs, a);
            Gfx.roundOutline(g, cx, cy, cs, cs, 6.0F, 0.6F, Col.alpha(822083583, a));
         } else {
            Gfx.roundGradient(g, cx, cy, cs, cs, 14.0F, Col.alpha(th.skyTop(), a), Col.alpha(th.skyBottom(), a));
            float dr = cs * 0.33F;
            double spin = Anim.seconds() * (t != null && t.playing ? 1.4 : 0.0);
            Gfx.circle(g, cx + cs / 2.0F, cy + cs / 2.0F, dr, Col.alpha(-15396322, a));
            Gfx.circle(g, cx + cs / 2.0F, cy + cs / 2.0F, dr * 0.72F, Col.alpha(-14541264, a));
            Gfx.circle(
               g, cx + cs / 2.0F + (float)Math.cos(spin) * dr * 0.5F, cy + cs / 2.0F + (float)Math.sin(spin) * dr * 0.5F, dr * 0.08F, Col.alpha(-2130706433, a)
            );
            Gfx.circle(g, cx + cs / 2.0F, cy + cs / 2.0F, dr * 0.25F, Col.alpha(th.a(), a));
         }

         float ty = cy + cs + 12.0F;
         String title = t == null ? Ui.tr("lyricsflow.hero.nothing_title") : t.title;
         String artist = t == null ? Ui.tr("lyricsflow.hero.nothing_sub") : t.artist;
         Gfx.textCentered(g, Gfx.ellipsize(title, Ui.FB, 12.0F, leftW - 20.0F), Ui.FB, this.px + leftW / 2.0F + 6.0F, ty, 12.0F, Col.alpha(-856070, a));
         Gfx.textCentered(g, Gfx.ellipsize(artist, Ui.F, 8.0F, leftW - 20.0F), Ui.F, this.px + leftW / 2.0F + 6.0F, ty + 15.0F, 8.0F, Col.alpha(-6515020, a));
         this.barW = leftW - 36.0F;
         this.barX = this.px + leftW / 2.0F + 6.0F - this.barW / 2.0F;
         this.barY = ty + 31.0F;
         long dur = t == null ? 0L : t.durationMs;
         boolean overBar = dur > 0L && Ui.inside(mouseX, mouseY, this.barX - 4.0F, this.barY - 6.0F, this.barW + 8.0F, 12.0F);
         this.barHover = Anim.approach(this.barHover, !overBar && !this.seeking ? 0.0F : 1.0F, 14.0F);
         float frac = dur > 0L ? Math.max(0.0F, Math.min(1.0F, (float)pos / (float)dur)) : 0.0F;
         if (this.seeking) {
            frac = this.seekFrac;
         } else if (this.seekFrac >= 0.0F && System.currentTimeMillis() - this.seekSentAt < 900L) {
            frac = this.seekFrac;
         } else {
            this.seekFrac = -1.0F;
         }

         float bh = 3.0F + this.barHover * 1.2F;
         Gfx.round(g, this.barX, this.barY - bh / 2.0F, this.barW, bh, bh / 2.0F, Col.alpha(822083583, a));
         if (frac > 0.0F) {
            Gfx.roundGradient(g, this.barX, this.barY - bh / 2.0F, Math.max(bh, this.barW * frac), bh, bh / 2.0F, Col.alpha(th.a(), a), Col.alpha(th.b(), a));
         }

         float knob = 3.2F + this.barHover * 1.6F;
         Gfx.glow(g, this.barX + this.barW * frac - knob, this.barY - knob, knob * 2.0F, knob * 2.0F, knob, 3.0F, Col.alpha(th.a(), a * 0.4F * this.barHover));
         Gfx.circle(g, this.barX + this.barW * frac, this.barY, knob, Col.alpha(-1, a * (0.6F + 0.4F * this.barHover)));
         String left = fmt(dur > 0L ? (long)(frac * (float)dur) : Math.max(0L, pos));
         String right = dur > 0L ? fmt(dur) : "--:--";
         Gfx.text(g, left, Ui.F, this.barX, this.barY + 5.0F, 6.2F, Col.alpha(this.seeking ? -856070 : -9541498, a));
         Gfx.text(g, right, Ui.F, this.barX + this.barW - Gfx.textWidth(right, Ui.F, 6.2F), this.barY + 5.0F, 6.2F, Col.alpha(-9541498, a));
         float mid = this.px + leftW / 2.0F + 6.0F;
         this.tbH = 13.0F;
         this.tbY = this.barY + 15.0F;
         String shown = this.editing ? this.timeText + (System.currentTimeMillis() / 450L % 2L == 0L ? "|" : " ") : left + " / " + right;
         this.tbW = Math.max(62.0F, Gfx.textWidth(shown, Ui.FB, 6.8F) + 16.0F);
         this.tbX = mid - this.tbW / 2.0F;
         this.jW = 24.0F;
         this.mX = this.tbX - this.jW - 5.0F;
         this.pX = this.tbX + this.tbW + 5.0F;
         boolean overTb = Ui.inside(mouseX, mouseY, this.tbX, this.tbY, this.tbW, this.tbH);
         this.timeHover = Anim.approach(this.timeHover, !overTb && !this.editing ? 0.0F : 1.0F, 14.0F);
         Gfx.round(g, this.tbX, this.tbY, this.tbW, this.tbH, 6.5F, Col.alpha(Col.lerp(620756991, 956301311, this.timeHover), a));
         Gfx.roundOutline(g, this.tbX, this.tbY, this.tbW, this.tbH, 6.5F, this.editing ? 1.0F : 0.5F, Col.alpha(this.editing ? Themes.accent() : 553648127, a));
         Gfx.textCentered(g, shown, Ui.FB, mid, this.tbY + (this.tbH - 6.8F) / 2.0F, 6.8F, Col.alpha(this.editing ? -1 : -6515020, a));

         for (int k = 0; k < 2; k++) {
            float jx = k == 0 ? this.mX : this.pX;
            boolean ov = Ui.inside(mouseX, mouseY, jx, this.tbY, this.jW, this.tbH);
            Gfx.round(g, jx, this.tbY, this.jW, this.tbH, 6.5F, Col.alpha(ov ? 956301311 : 553648127, a));
            Gfx.textCentered(g, k == 0 ? "-10" : "+10", Ui.FB, jx + this.jW / 2.0F, this.tbY + (this.tbH - 6.4F) / 2.0F, 6.4F, Col.alpha(-6515020, a));
         }

         float by = Math.min(this.py + this.ph - 52.0F, this.tbY + this.tbH + 24.0F);
         this.ctlMid = mid;
         this.ctlY = by;
         this.button(g, 0, mid - 40.0F, by, 12.0F, "prev", false, mouseX, mouseY, a);
         this.button(g, 1, mid, by, 17.0F, t != null && t.playing ? "pause" : "play", true, mouseX, mouseY, a);
         this.button(g, 2, mid + 40.0F, by, 12.0F, "next", false, mouseX, mouseY, a);
         int mode = MediaManager.get().repeat();
         int on = -14756000;
         boolean ov = Ui.inside(mouseX, mouseY, mid + 72.0F - 9.0F, by - 9.0F, 18.0F, 18.0F);
         if (ov && this.btnHover[3] < 0.05F) {
            UiSounds.play(UiSounds.HOVER, 1.2F);
         }

         this.btnHover[3] = Anim.approach(this.btnHover[3], ov ? 1.0F : 0.0F, 14.0F);
         this.btnPress[3] = Anim.approach(this.btnPress[3], 0.0F, 9.0F);
         float rr = 9.0F * (1.0F + this.btnHover[3] * 0.08F - this.btnPress[3] * 0.14F);
         int col = mode == 0 ? Col.lerp(-2434342, -1, this.btnHover[3]) : on;
         Icons.draw(g, mode == 1 ? "repeat_one" : "repeat", mid + 72.0F, by - 0.5F, rr * 1.3F, Col.alpha(col, a));
         if (mode != 0) {
            Gfx.circle(g, mid + 72.0F, by + rr + 1.5F, 1.2F, Col.alpha(on, a));
         }

         this.vY = by + 30.0F;
         this.vW = leftW - 70.0F;
         this.vX = mid - this.vW / 2.0F + 8.0F;
         this.iconX = this.vX - 14.0F;
         float vol = AppVolume.get();
         if (!this.volDrag) {
            this.volShown = vol;
         }

         boolean have = this.volShown >= 0.0F;
         ov = have && Ui.inside(mouseX, mouseY, this.vX - 4.0F, this.vY - 6.0F, this.vW + 8.0F, 12.0F);
         this.volHover = Anim.approach(this.volHover, !ov && !this.volDrag ? 0.0F : 1.0F, 14.0F);
         boolean mute = AppVolume.muted();
         Icons.draw(g, !mute && (!have || !(this.volShown < 0.01F)) ? "volume" : "mute", this.iconX, this.vY, 9.0F, Col.alpha(have ? -856070 : -9541498, a));
         if (have) {
            float vb = 3.0F + this.volHover * 1.2F;
            float f = mute ? 0.0F : this.volShown;
            Gfx.round(g, this.vX, this.vY - vb / 2.0F, this.vW, vb, vb / 2.0F, Col.alpha(822083583, a));
            if (f > 0.0F) {
               Gfx.round(g, this.vX, this.vY - vb / 2.0F, Math.max(vb, this.vW * f), vb, vb / 2.0F, Col.alpha(-1, a * 0.85F));
            }

            Gfx.circle(g, this.vX + this.vW * f, this.vY, 3.0F + this.volHover * 1.4F, Col.alpha(-1, a));
            Gfx.text(g, Math.round(f * 100.0F) + "%", Ui.F, this.vX + this.vW + 6.0F, this.vY - 3.1F, 6.2F, Col.alpha(-9541498, a));
         } else {
            Gfx.text(g, Ui.tr("lyricsflow.player.volume_none"), Ui.F, this.vX, this.vY - 3.1F, 6.2F, Col.alpha(-9541498, a));
         }

         this.lx = this.px + leftW + 16.0F;
         this.lw = this.pw - leftW - 32.0F;
         this.ly = this.py + 16.0F;
         this.lh = this.ph - 32.0F;
         Gfx.round(g, this.lx, this.ly, this.lw, this.lh, 12.0F, Col.alpha(1073741824, a));
         g.enableScissor((int)this.lx, (int)this.ly, (int)Math.ceil(this.lx + this.lw), (int)Math.ceil(this.ly + this.lh));
         if (l != null && !l.lines.isEmpty()) {
            this.drawLyrics(g, l, pos, mouseX, mouseY, a, th);
         } else {
            String s = t == null
               ? Ui.tr("lyricsflow.hero.preview_idle")
               : Ui.tr(mm.lyricsState() == MediaManager.LyricsState.SEARCHING ? "lyricsflow.lyrics.searching" : "lyricsflow.lyrics.not_found");
            Gfx.textCentered(g, s, Ui.FB, this.lx + this.lw / 2.0F, this.ly + this.lh / 2.0F - 5.0F, 10.0F, Col.alpha(-6515020, a));
         }

         g.disableScissor();
         Gfx.gradientV(g, this.lx, this.ly, this.lw, 14.0F, Col.alpha(1610612736, a), 0);
         Gfx.gradientV(g, this.lx, this.ly + this.lh - 14.0F, this.lw, 14.0F, 0, Col.alpha(1610612736, a));
         Gfx.text(g, Ui.tr("lyricsflow.player.hint"), Ui.F, this.px + 14.0F, this.py + this.ph - 11.0F, 5.6F, Col.alpha(-9541498, a));
      }
   }

   private void drawLyrics(GuiGraphics g, Lyrics l, long pos, double mx, double my, float a, Themes.Theme th) {
      float size = 11.0F;
      float gap = 7.0F;
      List<Lyrics.Line> lines = l.lines;
      if (this.lineY.length != lines.size() + 1) {
         this.lineY = new float[lines.size() + 1];
      }

      float y = 0.0F;
      List<List<String>> wrapped = new ArrayList<>();

      for (int i = 0; i < lines.size(); i++) {
         this.lineY[i] = y;
         List<String> w = Ui.wrap(lines.get(i).text, Ui.FB, size, this.lw - 28.0F);
         wrapped.add(w);
         y += w.size() * (size + 3.0F) + gap;
      }

      this.lineY[lines.size()] = y;
      Lyrics.Line cur = l.lastStartedBefore(pos);
      int ci = cur == null ? -1 : cur.index;
      float target = ci < 0 ? 0.0F : this.lineY[ci] - this.lh * 0.38F;
      if (Anim.seconds() - this.lastUserScroll < 3.0) {
         target = this.userScroll;
      }

      target = Math.max(0.0F, Math.min(Math.max(0.0F, y - this.lh + 20.0F), target));
      if (this.scroll < 0.0F) {
         this.scroll = target;
      }

      this.scroll = Anim.approach(this.scroll, target, 5.0F);

      for (int i = 0; i < lines.size(); i++) {
         float yy = this.ly + 16.0F + this.lineY[i] - this.scroll;
         if (!(yy > this.ly + this.lh) && !(yy + (this.lineY[i + 1] - this.lineY[i]) < this.ly)) {
            boolean over = Ui.inside(mx, my, this.lx, yy - 2.0F, this.lw, this.lineY[i + 1] - this.lineY[i] - gap + 4.0F);
            int col = i == ci ? -1 : (i < ci ? -7567706 : -11054484);
            if (over && i != ci) {
               col = Col.lerp(col, -1, 0.5F);
            }

            List<String> w = wrapped.get(i);

            for (int k = 0; k < w.size(); k++) {
               float lineYPos = yy + k * (size + 3.0F);
               if (i == ci) {
                  Gfx.glow(g, this.lx + 12.0F, lineYPos, Gfx.textWidth(w.get(k), Ui.FB, size), size, 4.0F, 4.0F, Col.alpha(th.a(), a * 0.18F));
               }

               Gfx.text(g, w.get(k), Ui.FB, this.lx + 14.0F, lineYPos, size, Col.alpha(col, a));
            }
         }
      }
   }

   private static String fmt(long ms) {
      long s = ms / 1000L;
      return String.format("%d:%02d", s / 60L, s % 60L);
   }

   private void button(GuiGraphics g, int i, float x, float y, float r, String icon, boolean primary, double mx, double my, float a) {
      boolean over = Ui.inside(mx, my, x - r, y - r, r * 2.0F, r * 2.0F);
      if (over && this.btnHover[i] < 0.05F) {
         UiSounds.play(UiSounds.HOVER, 1.2F);
      }

      this.btnHover[i] = Anim.approach(this.btnHover[i], over ? 1.0F : 0.0F, 14.0F);
      this.btnPress[i] = Anim.approach(this.btnPress[i], 0.0F, 9.0F);
      float rr = r * (1.0F + this.btnHover[i] * 0.08F - this.btnPress[i] * 0.14F);
      if (primary) {
         Gfx.glow(g, x - rr, y - rr, rr * 2.0F, rr * 2.0F, rr, 5.0F, Col.alpha(Themes.accent(), a * (0.3F + 0.3F * this.btnHover[i])));
         Gfx.circle(g, x, y, rr, Col.alpha(-1, a));
         Icons.draw(g, icon, x + (icon.equals("play") ? 1.0F : 0.0F), y, rr * 0.95F, Col.alpha(-15330529, a));
      } else {
         Gfx.circle(g, x, y, rr, Col.alpha(Col.lerp(553648127, 956301311, this.btnHover[i]), a));
         Icons.draw(g, icon, x, y, rr * 0.95F, Col.alpha(-1, a));
      }
   }

   public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
      double mx = e.x();
      double my = e.y();
      MediaManager mm = MediaManager.get();
      float by = this.ctlY;
      float mid = this.ctlMid;
      Track tr = mm.track();
      if (this.editing && !Ui.inside(mx, my, this.tbX, this.tbY, this.tbW, this.tbH)) {
         this.editing = false;
      }

      if (tr != null && tr.durationMs > 0L && Ui.inside(mx, my, this.barX - 4.0F, this.barY - 7.0F, this.barW + 8.0F, 14.0F)) {
         this.seeking = true;
         this.seekFrac = (float)Math.max(0.0, Math.min(1.0, (mx - this.barX) / this.barW));
         UiSounds.play(UiSounds.CLICK, 1.2F);
         return true;
      }

      if (tr != null && Ui.inside(mx, my, this.tbX, this.tbY, this.tbW, this.tbH)) {
         this.editing = true;
         this.timeText = "";
         UiSounds.play(UiSounds.CLICK, 1.2F);
         return true;
      }

      for (int k = 0; k < 2; k++) {
         if (tr != null && Ui.inside(mx, my, k == 0 ? this.mX : this.pX, this.tbY, this.jW, this.tbH)) {
            long target = Math.max(0L, mm.positionRaw() + (k == 0 ? -10000 : 10000));
            if (tr.durationMs > 0L) {
               target = Math.min(target, tr.durationMs - 500L);
            }

            this.jump(target);
            UiSounds.play(UiSounds.CLICK, k == 0 ? 1.05F : 1.2F);
            return true;
         }
      }

      if (Ui.inside(mx, my, this.iconX - 7.0F, this.vY - 7.0F, 14.0F, 14.0F)) {
         AppVolume.toggleMute();
         UiSounds.play(UiSounds.CLICK, 1.1F);
         return true;
      }

      if (this.volShown >= 0.0F && Ui.inside(mx, my, this.vX - 4.0F, this.vY - 7.0F, this.vW + 34.0F, 14.0F)) {
         this.volDrag = true;
         this.volShown = (float)Math.max(0.0, Math.min(1.0, (mx - this.vX) / this.vW));
         AppVolume.set(this.volShown);
         UiSounds.play(UiSounds.SLIDER, 1.0F);
         return true;
      }

      if (Ui.inside(mx, my, mid + 72.0F - 10.0F, by - 10.0F, 20.0F, 20.0F)) {
         this.btnPress[3] = 1.0F;
         UiSounds.play(UiSounds.CLICK, 1.15F);
         mm.cycleRepeat();
         return true;
      }

      float[][] b = new float[][]{{mid - 40.0F, 12.0F}, {mid, 17.0F}, {mid + 40.0F, 12.0F}};

      for (int i = 0; i < 3; i++) {
         double dx = mx - b[i][0];
         double dy = my - by;
         if (dx * dx + dy * dy <= (b[i][1] + 2.0F) * (b[i][1] + 2.0F)) {
            this.btnPress[i] = 1.0F;
            UiSounds.play(UiSounds.CLICK, i == 1 ? 1.0F : 1.15F);
            if (i == 0) {
               mm.previous();
            } else if (i == 1) {
               mm.playPause();
            } else {
               mm.next();
            }

            return true;
         }
      }

      Lyrics l = mm.lyrics();
      if (l != null && Ui.inside(mx, my, this.lx, this.ly, this.lw, this.lh) && this.lineY.length == l.lines.size() + 1) {
         for (int i = 0; i < l.lines.size(); i++) {
            float yy = this.ly + 16.0F + this.lineY[i] - this.scroll;
            if (my >= yy - 2.0F && my < yy + this.lineY[i + 1] - this.lineY[i]) {
               mm.seek(Math.max(0L, l.lines.get(i).start));
               this.lastUserScroll = -10.0;
               UiSounds.play(UiSounds.TOGGLE_ON, 1.1F);
               return true;
            }
         }
      }

      return super.mouseClicked(e, dbl);
   }

   private void jump(long ms) {
      Track tr = MediaManager.get().track();
      if (tr != null) {
         MediaManager.get().seek(ms);
         if (tr.durationMs > 0L) {
            this.seekFrac = Math.max(0.0F, Math.min(1.0F, (float)ms / (float)tr.durationMs));
            this.seekSentAt = System.currentTimeMillis();
         }

         this.lastUserScroll = -10.0;
      }
   }

   private static long parseTime(String s) {
      s = s.trim().replace('.', ':').replace(' ', ':');
      if (s.isEmpty()) {
         return -1L;
      }

      try {
         String[] p = s.split(":");
         long sec = 0L;

         for (String part : p) {
            sec = sec * 60L + (part.isEmpty() ? 0L : Long.parseLong(part));
         }

         return sec * 1000L;
      } catch (NumberFormatException e) {
         return -1L;
      }
   }

   public boolean charTyped(CharacterEvent e) {
      if (!this.editing) {
         return super.charTyped(e);
      }

      String c = e.codepointAsString();
      if (c.length() == 1 && (Character.isDigit(c.charAt(0)) || c.charAt(0) == ':' || c.charAt(0) == '.') && this.timeText.length() < 8) {
         this.timeText = this.timeText + c;
         UiSounds.play(UiSounds.SLIDER, 1.3F);
      }

      return true;
   }

   public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
      if (this.volDrag) {
         float nf = (float)Math.max(0.0, Math.min(1.0, (e.x() - this.vX) / this.vW));
         if (Math.abs(nf - this.volShown) > 0.01F) {
            this.volShown = nf;
            AppVolume.set(nf);
            UiSounds.play(UiSounds.SLIDER, 0.8F + nf * 0.5F);
         }

         return true;
      } else if (this.seeking) {
         float nf = (float)Math.max(0.0, Math.min(1.0, (e.x() - this.barX) / this.barW));
         if (Math.abs(nf - this.seekFrac) > 0.004F) {
            UiSounds.play(UiSounds.SLIDER, 0.9F + nf * 0.4F);
         }

         this.seekFrac = nf;
         return true;
      } else {
         return super.mouseDragged(e, dx, dy);
      }
   }

   public boolean mouseReleased(MouseButtonEvent e) {
      if (this.volDrag) {
         this.volDrag = false;
         return true;
      }

      if (this.seeking) {
         this.seeking = false;
         Track tr = MediaManager.get().track();
         if (tr != null && tr.durationMs > 0L) {
            MediaManager.get().seek((long)(this.seekFrac * (float)tr.durationMs));
            this.seekSentAt = System.currentTimeMillis();
            this.lastUserScroll = -10.0;
         }

         return true;
      } else {
         return super.mouseReleased(e);
      }
   }

   public boolean mouseScrolled(double x, double y, double h, double v) {
      if (Anim.seconds() - this.lastUserScroll >= 3.0) {
         this.userScroll = this.scroll;
      }

      this.userScroll -= (float)v * 30.0F;
      this.lastUserScroll = Anim.seconds();
      return true;
   }

   public boolean keyPressed(KeyEvent e) {
      if (this.editing) {
         if (e.key() == 257 || e.key() == 335) {
            long ms = parseTime(this.timeText);
            Track tr = MediaManager.get().track();
            if (ms >= 0L && tr != null) {
               if (tr.durationMs > 0L) {
                  ms = Math.min(ms, tr.durationMs - 500L);
               }

               this.jump(Math.max(0L, ms));
               UiSounds.play(UiSounds.TOGGLE_ON, 1.1F);
            }

            this.editing = false;
         } else if (e.key() == 259) {
            if (!this.timeText.isEmpty()) {
               this.timeText = this.timeText.substring(0, this.timeText.length() - 1);
            }
         } else if (e.key() == 256) {
            this.editing = false;
         }

         return true;
      } else if (e.key() == 265 || e.key() == 264) {
         float v = AppVolume.get();
         if (v >= 0.0F) {
            AppVolume.set(v + (e.key() == 265 ? 0.05F : -0.05F));
            UiSounds.play(UiSounds.SLIDER, 1.0F);
         }

         return true;
      } else if (e.key() != 256 && (Keys.openPlayer == null || !Keys.openPlayer.matches(e))) {
         if (e.key() == 32) {
            MediaManager.get().playPause();
            UiSounds.play(UiSounds.CLICK);
            return true;
         } else if (e.key() == 262) {
            MediaManager.get().next();
            return true;
         } else if (e.key() == 263) {
            MediaManager.get().previous();
            return true;
         } else {
            return super.keyPressed(e);
         }
      } else {
         this.onClose();
         return true;
      }
   }

   public void onClose() {
      if (!this.closing) {
         this.closing = true;
         UiSounds.play(UiSounds.CLOSE, 0.95F);
      }
   }

   public void removed() {
      super.removed();
   }

   private void finish() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }
}
