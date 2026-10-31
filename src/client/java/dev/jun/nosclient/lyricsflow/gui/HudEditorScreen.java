package dev.jun.nosclient.lyricsflow.gui;

import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import dev.jun.nosclient.lyricsflow.render.Gfx;
import dev.jun.nosclient.lyricsflow.render.Themes;
import dev.jun.nosclient.lyricsflow.render.TopBar;
import dev.jun.nosclient.lyricsflow.util.Anim;
import dev.jun.nosclient.lyricsflow.util.Col;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class HudEditorScreen extends Screen {
   private final Screen parent;
   private int dragging;
   private double grabDx;
   private double grabDy;
   private float barHover;
   private float lyrHover;
   private float doneHover;
   private float resetHover;
   private float guideX;
   private float guideY;
   private float openT;

   public HudEditorScreen(Screen parent) {
      super(Component.translatable("lyricsflow.editor.title"));
      this.parent = parent;
   }

   protected void init() {
      UiSounds.play(UiSounds.OPEN, 1.15F);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
   }

   private float lyrW() {
      return 150.0F * ConfigManager.get().lyricsScale;
   }

   private float lyrH() {
      return 34.0F * ConfigManager.get().lyricsScale;
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      Anim.frame();
      this.openT = Anim.approach(this.openT, 1.0F, 8.0F);
      float a = Anim.easeOutCubic(this.openT);
      LFConfig c = ConfigManager.get();
      Gfx.rect(g, 0.0F, 0.0F, this.width, this.height, Col.alpha(1879048192, a));

      for (int x = 0; x < this.width; x += 20) {
         Gfx.rect(g, x, 0.0F, 0.5F, this.height, Col.alpha(218103807, a));
      }

      for (int y = 0; y < this.height; y += 20) {
         Gfx.rect(g, 0.0F, y, this.width, 0.5F, Col.alpha(218103807, a));
      }

      this.guideX = Anim.approach(
         this.guideX, this.dragging != 0 && Math.abs(this.centerOf(this.dragging)[0] - this.width / 2.0F) < 1.0F ? 1.0F : 0.0F, 12.0F
      );
      this.guideY = Anim.approach(
         this.guideY, this.dragging != 0 && Math.abs(this.centerOf(this.dragging)[1] - this.height / 2.0F) < 1.0F ? 1.0F : 0.0F, 12.0F
      );
      if (this.guideX > 0.01F) {
         Gfx.rect(g, this.width / 2.0F - 0.5F, 0.0F, 1.0F, this.height, Col.alpha(Themes.accent(), a * this.guideX));
      }

      if (this.guideY > 0.01F) {
         Gfx.rect(g, 0.0F, this.height / 2.0F - 0.5F, this.width, 1.0F, Col.alpha(Themes.accent(), a * this.guideY));
      }

      TopBar.render(g, true);
      boolean overBar = Ui.inside(mx, my, TopBar.lastX, TopBar.lastY, TopBar.lastW, TopBar.lastH);
      this.barHover = Anim.approach(this.barHover, !overBar && this.dragging != 1 ? 0.0F : 1.0F, 14.0F);
      Gfx.roundOutline(
         g,
         TopBar.lastX - 3.0F,
         TopBar.lastY - 3.0F,
         TopBar.lastW + 6.0F,
         TopBar.lastH + 6.0F,
         TopBar.lastH / 2.0F + 3.0F,
         1.0F,
         Col.alpha(Col.lerp(1627389951, Themes.accent(), this.barHover), a)
      );
      this.label(
         g, Ui.tr("lyricsflow.editor.top_bar"), TopBar.lastX + TopBar.lastW / 2.0F, TopBar.lastY + TopBar.lastH + 6.0F, a * (0.6F + 0.4F * this.barHover)
      );
      Themes.Theme th = Themes.current();
      float lx = c.screenLyricsX * this.width;
      float ly = c.screenLyricsY * this.height;
      float lw = this.lyrW();
      float lh = this.lyrH();
      boolean overLyr = Ui.inside(mx, my, lx - lw / 2.0F, ly - lh / 2.0F, lw, lh);
      this.lyrHover = Anim.approach(this.lyrHover, !overLyr && this.dragging != 2 ? 0.0F : 1.0F, 14.0F);
      Gfx.round(g, lx - lw / 2.0F, ly - lh / 2.0F, lw, lh, 10.0F, Col.alpha(805306368, a));
      Gfx.roundOutline(g, lx - lw / 2.0F, ly - lh / 2.0F, lw, lh, 10.0F, 1.0F, Col.alpha(Col.lerp(1627389951, Themes.accent(), this.lyrHover), a));
      float size = 18.0F * c.lyricsScale;
      Gfx.neon(g, Ui.tr("lyricsflow.editor.sample"), c.font, lx, ly - size / 2.0F, size, th.main(), th.a(), th.b(), a, c.twoColor, 0.6F);
      this.label(g, Ui.tr("lyricsflow.editor.screen_lyrics"), lx, ly + lh / 2.0F + 5.0F, a * (0.6F + 0.4F * this.lyrHover));
      Gfx.textCentered(g, Ui.tr("lyricsflow.editor.help"), Ui.F, this.width / 2.0F, this.height - 46, 7.2F, Col.alpha(-856070, a));
      float bw = 70.0F;
      float bh = 18.0F;
      float by = this.height - 30;
      this.doneHover = this.button(g, this.width / 2.0F + 4.0F, by, bw, bh, Ui.tr("lyricsflow.button.done"), true, mx, my, this.doneHover, a);
      this.resetHover = this.button(g, this.width / 2.0F - 4.0F - bw, by, bw, bh, Ui.tr("lyricsflow.button.reset"), false, mx, my, this.resetHover, a);
   }

   private float[] centerOf(int which) {
      LFConfig c = ConfigManager.get();
      return which == 1
         ? new float[]{TopBar.lastX + TopBar.lastW / 2.0F, TopBar.lastY + TopBar.lastH / 2.0F}
         : new float[]{c.screenLyricsX * this.width, c.screenLyricsY * this.height};
   }

   private void label(GuiGraphics g, String s, float cx, float y, float a) {
      float w = Gfx.textWidth(s, Ui.FB, 6.4F) + 10.0F;
      Gfx.round(g, cx - w / 2.0F, y, w, 11.0F, 5.5F, Col.alpha(-1072295135, a));
      Gfx.textCentered(g, s, Ui.FB, cx, y + 2.6F, 6.4F, Col.alpha(-856070, a));
   }

   private float button(GuiGraphics g, float x, float y, float w, float h, String text, boolean primary, double mx, double my, float hov, float a) {
      boolean over = Ui.inside(mx, my, x, y, w, h);
      hov = Anim.approach(hov, over ? 1.0F : 0.0F, 14.0F);
      int base = primary ? Themes.accent() : -13883584;
      Gfx.round(g, x, y, w, h, h / 2.0F, Col.alpha(Col.brighten(base, hov * 0.12F), a));
      Gfx.textCentered(g, text, Ui.FB, x + w / 2.0F, y + (h - 7.6F) / 2.0F - 0.3F, 7.6F, Col.alpha(-1, a));
      return hov;
   }

   public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
      double mx = e.x();
      double my = e.y();
      LFConfig c = ConfigManager.get();
      float bw = 70.0F;
      float bh = 18.0F;
      float by = this.height - 30;
      if (Ui.inside(mx, my, this.width / 2.0F + 4.0F, by, bw, bh)) {
         this.onClose();
         return true;
      } else if (Ui.inside(mx, my, this.width / 2.0F - 4.0F - bw, by, bw, bh)) {
         LFConfig d = new LFConfig();
         c.topBarX = d.topBarX;
         c.topBarY = d.topBarY;
         c.screenLyricsX = d.screenLyricsX;
         c.screenLyricsY = d.screenLyricsY;
         c.topBarScale = d.topBarScale;
         ConfigManager.save();
         UiSounds.play(UiSounds.CLICK, 0.9F);
         return true;
      } else if (Ui.inside(mx, my, TopBar.lastX, TopBar.lastY, TopBar.lastW, TopBar.lastH)) {
         this.dragging = 1;
         this.grabDx = mx - c.topBarX * this.width;
         this.grabDy = my - c.topBarY * this.height;
         UiSounds.play(UiSounds.CLICK, 1.2F);
         return true;
      } else {
         float lx = c.screenLyricsX * this.width;
         float ly = c.screenLyricsY * this.height;
         if (Ui.inside(mx, my, lx - this.lyrW() / 2.0F, ly - this.lyrH() / 2.0F, this.lyrW(), this.lyrH())) {
            this.dragging = 2;
            this.grabDx = mx - lx;
            this.grabDy = my - ly;
            UiSounds.play(UiSounds.CLICK, 1.2F);
            return true;
         } else {
            return super.mouseClicked(e, dbl);
         }
      }
   }

   public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
      LFConfig c = ConfigManager.get();
      double nx = e.x() - this.grabDx;
      double ny = e.y() - this.grabDy;
      if (this.dragging == 1) {
         if (Math.abs(nx - this.width / 2.0) < 6.0) {
            nx = this.width / 2.0;
         }

         c.topBarX = (float)Math.max(0.0, Math.min(1.0, nx / this.width));
         c.topBarY = (float)Math.max(0.0, Math.min(0.95, ny / this.height));
      } else if (this.dragging == 2) {
         if (Math.abs(nx - this.width / 2.0) < 6.0) {
            nx = this.width / 2.0;
         }

         if (Math.abs(ny - this.height / 2.0) < 6.0) {
            ny = this.height / 2.0;
         }

         c.screenLyricsX = (float)Math.max(0.05, Math.min(0.95, nx / this.width));
         c.screenLyricsY = (float)Math.max(0.05, Math.min(0.95, ny / this.height));
      }

      return true;
   }

   public boolean mouseReleased(MouseButtonEvent e) {
      if (this.dragging != 0) {
         this.dragging = 0;
         ConfigManager.save();
         UiSounds.play(UiSounds.TOGGLE_ON, 1.0F);
      }

      return true;
   }

   public boolean mouseScrolled(double mx, double my, double h, double v) {
      LFConfig c = ConfigManager.get();
      if (Ui.inside(mx, my, TopBar.lastX, TopBar.lastY, TopBar.lastW, TopBar.lastH)) {
         c.topBarScale = (float)Math.max(0.6, Math.min(1.8, c.topBarScale + v * 0.05));
      } else {
         c.lyricsScale = (float)Math.max(0.4, Math.min(2.5, c.lyricsScale + v * 0.05));
      }

      UiSounds.play(UiSounds.SLIDER, 1.0F);
      ConfigManager.save();
      return true;
   }

   public boolean keyPressed(KeyEvent e) {
      if (e.key() != 256 && e.key() != 257) {
         return super.keyPressed(e);
      }

      this.onClose();
      return true;
   }

   public void onClose() {
      ConfigManager.save();
      UiSounds.play(UiSounds.CLOSE, 1.1F);
      this.minecraft.setScreen(this.parent instanceof LyricsFlowScreen ? new LyricsFlowScreen() : this.parent);
   }
}
