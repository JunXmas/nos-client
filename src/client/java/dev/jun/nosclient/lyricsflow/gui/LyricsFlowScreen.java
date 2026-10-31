package dev.jun.nosclient.lyricsflow.gui;

import dev.jun.nosclient.lyricsflow.Keys;
import dev.jun.nosclient.lyricsflow.app.Core;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.render.Gfx;
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

public class LyricsFlowScreen extends Screen {
   private static LyricsFlowScreen.Tab lastTab = LyricsFlowScreen.Tab.LYRICS;
   private LyricsFlowScreen.Tab tab = lastTab;
   private final List<Ui.Widget> widgets = new ArrayList<>();
   private float openT;
   private boolean closing;
   private final long openedAt = System.currentTimeMillis();
   private long closedAt = -1L;
   private float tabT = 1.0F;
   private float railY = -1.0F;
   private final float[] railHover = new float[LyricsFlowScreen.Tab.values().length];
   private float scroll;
   private float scrollTarget;
   private float contentH;
   private float barX;
   private float barY;
   private float barH;
   private float barMax;
   private float barHover;
   private boolean barDrag;
   private float barGrab;
   private float closeHover;
   private Ui.Widget dragging;
   private boolean rebuild = true;
   private boolean heroVisible;
   private float px;
   private float py;
   private float pw;
   private float ph;
   private float hx;
   private float hy;
   private float hw;
   private float hh;
   private float railW;
   private float contentX;
   private float contentY;
   private float contentW;
   private float contentHgt;
   private final Screen parent;
   private float fit = 1.0F;
   private double lastMx;
   private double lastMy;
   private final float[] modeHover = new float[2];
   private float modePill = -1.0F;

   public LyricsFlowScreen() {
      this(null);
   }

   public LyricsFlowScreen(Screen parent) {
      super(Component.literal("LyricsFlow"));
      this.parent = parent;
   }

   public void rebuildSoon() {
      this.rebuild = true;
   }

   protected void init() {
      UiSounds.play(UiSounds.OPEN, 1.0F);
      this.rebuild = true;
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static float easeOutQuint(float x) {
      float u = 1.0F - x;
      return 1.0F - u * u * u * u * u;
   }

   private void computeFit() {
      float f = Math.min(this.width / 812.0F, this.height / 424.0F);
      this.fit = Math.max(0.75F, Math.min(1.5F, f * 0.98F));
   }

   private void layout() {
      float W = this.width / this.fit;
      float H = this.height / this.fit;
      this.heroVisible = W >= 640.0F;
      this.hw = this.heroVisible ? Math.min(158.0F, W * 0.22F) : 0.0F;
      float gap = this.heroVisible ? 10.0F : 0.0F;
      this.pw = Math.min(520.0F, W - 24.0F - this.hw - gap);
      this.ph = Math.min(350.0F, H - 24.0F);
      float total = this.hw + gap + this.pw;
      this.hx = (W - total) / 2.0F;
      this.px = this.hx + this.hw + gap;
      this.py = (H - this.ph) / 2.0F;
      this.hh = Math.min(this.ph, NowPlaying.height(this.hw));
      this.hy = this.py;
      this.railW = 66.0F;
      this.contentX = this.px + this.railW + 14.0F;
      this.contentY = this.py + 46.0F;
      this.contentW = this.pw - this.railW - 28.0F;
      this.contentHgt = this.ph - 46.0F - 10.0F;
   }

   private void build() {
      this.widgets.clear();
      this.widgets.addAll(Pages.build(this.tab, this, this.heroVisible));
      float y = 0.0F;

      for (Ui.Widget w : this.widgets) {
         y += w.layout(0.0F, y, this.contentW);
      }

      this.contentH = y;
      this.rebuild = false;
   }

   private void switchTab(LyricsFlowScreen.Tab t) {
      if (t != this.tab) {
         this.tab = t;
         lastTab = t;
         this.tabT = 0.0F;
         this.scroll = this.scrollTarget = 0.0F;
         this.rebuild = true;
         UiSounds.play(UiSounds.TAB, 0.95F + t.ordinal() * 0.03F);
      }
   }

   public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      if (this.parent != null || this.minecraft == null || this.minecraft.level == null) {
         super.renderBackground(g, mouseX, mouseY, partialTick);
      }
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      Anim.frame();
      String oldF = Ui.F;
      Ui.syncFont();
      if (!oldF.equals(Ui.F)) {
         this.rebuild = true;
      }

      float oldFit = this.fit;
      this.computeFit();
      if (Math.abs(oldFit - this.fit) > 0.001F) {
         this.rebuild = true;
      }

      this.layout();
      if (this.rebuild) {
         this.build();
      }

      long nowMs = System.currentTimeMillis();
      float t = (float)(nowMs - this.openedAt) / 1000.0F;
      if (this.closing && this.closedAt < 0L) {
         this.closedAt = nowMs;
      }

      float ck = this.closing ? 1.0F - Anim.easeOutCubic(Math.min(1.0F, (float)(nowMs - this.closedAt) / 160.0F)) : 1.0F;
      this.openT = ck;
      if (this.closing && ck < 0.02F) {
         if (this.parent != null && this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
         } else {
            super.onClose();
         }
      } else {
         this.tabT = Anim.approach(this.tabT, 1.0F, 10.0F);
         float bgA = Anim.easeOutCubic(Math.min(1.0F, t / 0.25F)) * ck;
         float eh = easeOutQuint(Math.max(0.0F, Math.min(1.0F, (t - 0.02F) / 0.55F)));
         float ep = easeOutQuint(Math.max(0.0F, Math.min(1.0F, (t - 0.1F) / 0.6F)));
         float out = 1.0F - ck;
         float a = Math.min(1.0F, Math.max(0.0F, t / 0.2F)) * ck;
         float aH = Math.min(1.0F, eh * 1.6F) * ck;
         float aP = Math.min(1.0F, ep * 1.6F) * ck;
         Gfx.gradientV(g, 0.0F, 0.0F, this.width, this.height, Col.alpha(-1341585646, bgA), Col.alpha(-804714734, bgA));
         float fl = Math.max(0.0F, 1.0F - Math.abs(t - 0.1F) / 0.1F);
         if (fl > 0.01F) {
            Gfx.rect(g, 0.0F, 0.0F, this.width, this.height, Col.alpha(-1, 0.06F * fl));
         }

         g.pose().pushMatrix();
         g.pose().scale(this.fit, this.fit);
         float cx = this.width / this.fit / 2.0F;
         float cy = this.height / this.fit / 2.0F;
         float sc = 1.0F - 0.03F * out;
         g.pose().translate(cx, cy + out * 8.0F);
         g.pose().scale(sc, sc);
         g.pose().translate(-cx, -cy);
         double vmx = mouseX / this.fit;
         double vmy = mouseY / this.fit;
         double mx = (vmx - cx) / sc + cx;
         double my = (vmy - cy - out * 8.0F) / sc + cy;
         this.lastMx = mx;
         this.lastMy = my;
         if (this.heroVisible) {
            float hs = 0.96F + 0.04F * eh;
            g.pose().pushMatrix();
            g.pose().translate(this.hx + this.hw / 2.0F - (1.0F - eh) * 46.0F, this.hy + this.hh / 2.0F);
            g.pose().scale(hs, hs);
            g.pose().translate(-(this.hx + this.hw / 2.0F), -(this.hy + this.hh / 2.0F));
            NowPlaying.renderHero(g, this.hx, this.hy, this.hw, this.hh, mx, my, aH);
            g.pose().popMatrix();
         }

         float ps = 0.96F + 0.04F * ep;
         g.pose().pushMatrix();
         g.pose().translate(this.px + this.pw / 2.0F + (1.0F - ep) * 46.0F, this.py + this.ph / 2.0F);
         g.pose().scale(ps, ps);
         g.pose().translate(-(this.px + this.pw / 2.0F), -(this.py + this.ph / 2.0F));
         a = aP;
         this.renderPanel(g, mx, my, a);
         g.pose().popMatrix();
         g.pose().popMatrix();
      }
   }

   private void renderPanel(GuiGraphics g, double mx, double my, float a) {
      int accent = Themes.accent();
      Gfx.shadow(g, this.px, this.py, this.pw, this.ph, 16.0F, 14.0F, Col.alpha(-16777216, a * 0.55F));
      Gfx.round(g, this.px, this.py, this.pw, this.ph, 16.0F, Col.alpha(-233697255, a));
      Gfx.roundOutline(g, this.px, this.py, this.pw, this.ph, 16.0F, 0.6F, Col.alpha(587202559, a));
      LyricsFlowScreen.Tab[] tabs = LyricsFlowScreen.Tab.values();
      float tabH = Math.min(36.0F, (this.ph - 16.0F) / tabs.length);
      boolean labels = tabH >= 30.0F;
      float ry0 = this.py + (this.ph - tabH * tabs.length) / 2.0F;
      float target = ry0 + this.tab.ordinal() * tabH;
      if (this.railY < 0.0F) {
         this.railY = target;
      }

      this.railY = Anim.approach(this.railY, target, 14.0F);
      Gfx.rect(g, this.px + this.railW + 2.0F, this.py + 12.0F, 0.5F, this.ph - 24.0F, Col.alpha(352321535, a));
      float pillX = this.px + 7.0F;
      float pillW = this.railW - 10.0F;
      Gfx.glow(g, pillX, this.railY + 2.0F, pillW, tabH - 4.0F, 9.0F, 4.0F, Col.alpha(accent, a * 0.25F));
      Gfx.round(g, pillX, this.railY + 2.0F, pillW, tabH - 4.0F, 9.0F, Col.alpha(-724481, a));

      for (int i = 0; i < tabs.length; i++) {
         LyricsFlowScreen.Tab t = tabs[i];
         float ty = ry0 + i * tabH;
         boolean over = Ui.inside(mx, my, pillX, ty + 2.0F, pillW, tabH - 4.0F);
         if (over && this.railHover[i] < 0.05F && t != this.tab) {
            UiSounds.play(UiSounds.HOVER, 1.3F);
         }

         this.railHover[i] = Anim.approach(this.railHover[i], over ? 1.0F : 0.0F, 14.0F);
         float sel = 1.0F - Math.min(1.0F, Math.abs(this.railY - ty) / tabH);
         if (this.railHover[i] > 0.01F && t != this.tab) {
            Gfx.round(g, pillX, ty + 2.0F, pillW, tabH - 4.0F, 9.0F, Col.alpha(285212671, a * this.railHover[i]));
         }

         int col = Col.lerp(Col.lerp(-6515020, -856070, this.railHover[i]), -15330529, sel);
         float lift = this.railHover[i] * -0.8F;
         if (labels) {
            Icons.draw(g, t.icon, pillX + pillW / 2.0F, ty + tabH * 0.38F + lift, 10.5F, Col.alpha(col, a));
            Gfx.textCentered(
               g, Gfx.ellipsize(t.title(), Ui.FB, 6.4F, pillW - 4.0F), Ui.FB, pillX + pillW / 2.0F, ty + tabH * 0.62F + lift, 6.4F, Col.alpha(col, a)
            );
         } else {
            Icons.draw(g, t.icon, pillX + pillW / 2.0F, ty + tabH / 2.0F + lift, 11.0F, Col.alpha(col, a));
         }
      }

      float ta = a * Anim.easeOutCubic(this.tabT);
      float tOff = (1.0F - Anim.easeOutCubic(this.tabT)) * 8.0F;
      Gfx.text(g, this.tab.title(), Ui.FB, this.contentX + 2.0F + tOff, this.py + 14.0F, 14.5F, Col.alpha(-856070, ta));
      Gfx.text(g, this.tab.subtitle(), Ui.F, this.contentX + 2.0F + tOff, this.py + 31.0F, 7.2F, Col.alpha(-6515020, ta));
      float cr = 8.5F;
      float ccx = this.px + this.pw - 18.0F;
      float ccy = this.py + 18.0F;
      boolean overClose = Ui.inside(mx, my, ccx - cr, ccy - cr, cr * 2.0F, cr * 2.0F);
      this.closeHover = Anim.approach(this.closeHover, overClose ? 1.0F : 0.0F, 14.0F);
      Gfx.circle(g, ccx, ccy, cr + this.closeHover * 1.2F, Col.alpha(Col.lerp(-14541264, -42374, this.closeHover * 0.85F), a));
      g.pose().pushMatrix();
      g.pose().translate(ccx, ccy);
      g.pose().rotate(this.closeHover * (float) Math.PI / 2.0F);
      Icons.draw(g, "close", 0.0F, 0.0F, 8.0F, Col.alpha(-1, a));
      g.pose().popMatrix();
      this.drawModeSwitch(g, mx, my, a);
      float maxScroll = Math.max(0.0F, this.contentH - this.contentHgt);
      this.scrollTarget = Math.max(0.0F, Math.min(maxScroll, this.scrollTarget));
      this.scroll = Anim.approach(this.scroll, this.scrollTarget, 16.0F);
      g.enableScissor(
         (int)this.contentX - 10, (int)this.contentY, (int)Math.ceil(this.contentX + this.contentW + 10.0F), (int)Math.ceil(this.contentY + this.contentHgt)
      );
      float ox = this.contentX;
      float oy = this.contentY - this.scroll + tOff * 1.4F;

      for (Ui.Widget w : this.widgets) {
         if (!(oy + w.y + w.h < this.contentY - 20.0F) && !(oy + w.y > this.contentY + this.contentHgt + 20.0F)) {
            boolean inView = my >= this.contentY && my <= this.contentY + this.contentHgt;
            w.render(g, ox, oy, inView ? mx : -9999.0, inView ? my : -9999.0, ta);
         }
      }

      g.disableScissor();
      if (this.scroll > 1.0F) {
         Gfx.gradientV(g, this.contentX - 10.0F, this.contentY, this.contentW + 20.0F, 10.0F, Col.alpha(-233697255, a), Col.alpha(-233697255, 0.0F));
      }

      if (this.scroll < maxScroll - 1.0F) {
         Gfx.gradientV(
            g,
            this.contentX - 10.0F,
            this.contentY + this.contentHgt - 10.0F,
            this.contentW + 20.0F,
            10.0F,
            Col.alpha(-233697255, 0.0F),
            Col.alpha(-233697255, a)
         );
      }

      this.barMax = maxScroll;
      if (maxScroll > 0.0F) {
         this.barH = Math.max(18.0F, this.contentHgt * this.contentHgt / this.contentH);
         this.barY = this.contentY + (this.contentHgt - this.barH) * (this.scroll / maxScroll);
         this.barX = this.px + this.pw - 6.0F;
         boolean over = this.barDrag || Ui.inside(mx, my, this.barX - 6.0F, this.barY, 12.0F, this.barH);
         this.barHover = Anim.approach(this.barHover, over ? 1.0F : 0.0F, 14.0F);
         float bw = 2.5F + this.barHover * 1.5F;
         Gfx.round(g, this.barX + 1.25F - bw / 2.0F, this.barY, bw, this.barH, bw / 2.0F, Col.alpha(Col.lerp(1090519039, -1862270977, this.barHover), a));
      }

      Gfx.text(g, "v2.1-beta", Ui.F, this.px + 10.0F, this.py + this.ph - 10.0F, 5.6F, Col.alpha(-9541498, a));
   }

   private float[] modeBox() {
      float h = 14.0F;
      float w = 0.0F;
      String[] l = modeLabels();
      float[] ws = new float[2];

      for (int i = 0; i < 2; i++) {
         ws[i] = Gfx.textWidth(l[i], Ui.FB, 6.6F) + 14.0F;
         w += ws[i];
      }

      float x = this.px + this.pw - 36.0F - w;
      float y = this.py + 11.0F;
      return new float[]{x, y, w, h, ws[0], ws[1]};
   }

   private static String[] modeLabels() {
      return new String[]{Ui.tr("lyricsflow.ui.menu.normal"), Ui.tr("lyricsflow.ui.menu.world")};
   }

   private void drawModeSwitch(GuiGraphics g, double mx, double my, float a) {
      float[] b = this.modeBox();
      int sel = Core.cfg().menuMode == 1 ? 1 : 0;
      Gfx.round(g, b[0], b[1], b[2], b[3], b[3] / 2.0F, Col.alpha(-14541264, a));
      float target = sel == 0 ? b[0] : b[0] + b[4];
      if (this.modePill < 0.0F) {
         this.modePill = target;
      }

      this.modePill = Anim.approach(this.modePill, target, 16.0F);
      float pwid = sel == 0 ? b[4] : b[5];
      Gfx.round(g, this.modePill + 1.0F, b[1] + 1.0F, pwid - 2.0F, b[3] - 2.0F, (b[3] - 2.0F) / 2.0F, Col.alpha(-724481, a));
      String[] l = modeLabels();
      float x = b[0];

      for (int i = 0; i < 2; i++) {
         boolean over = Ui.inside(mx, my, x, b[1], b[4 + i], b[3]);
         this.modeHover[i] = Anim.approach(this.modeHover[i], over ? 1.0F : 0.0F, 14.0F);
         int col = i == sel ? -15330529 : Col.lerp(-6515020, -856070, this.modeHover[i]);
         Gfx.textCentered(g, l[i], Ui.FB, x + b[4 + i] / 2.0F, b[1] + 3.6F, 6.6F, Col.alpha(col, a));
         x += b[4 + i];
      }
   }

   private boolean clickModeSwitch(double mx, double my) {
      float[] b = this.modeBox();
      if (!Ui.inside(mx, my, b[0], b[1], b[2], b[3])) {
         return false;
      }

      int i = mx < b[0] + b[4] ? 0 : 1;
      Core.cfg().menuMode = i;
      Core.saveConfig();
      UiSounds.play(UiSounds.CLICK);
      this.rebuild = true;
      return true;
   }

   private float ox() {
      return this.contentX;
   }

   private float oy() {
      return this.contentY - this.scroll;
   }

   public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
      if (this.closing) {
         return true;
      }

      double mx = this.lastMx;
      double my = this.lastMy;
      if (e.button() != 0) {
         return super.mouseClicked(e, doubleClick);
      }

      if (Ui.inside(mx, my, this.px + this.pw - 27.0F, this.py + 9.0F, 18.0F, 18.0F)) {
         this.onClose();
         return true;
      }

      if (this.clickModeSwitch(mx, my)) {
         return true;
      }

      if (this.heroVisible && Ui.inside(mx, my, this.hx, this.hy, this.hw, this.hh)) {
         if (!NowPlaying.pressBar(mx, my)) {
            NowPlaying.click(mx, my);
         }

         return true;
      } else {
         LyricsFlowScreen.Tab[] tabs = LyricsFlowScreen.Tab.values();
         float tabH = Math.min(36.0F, (this.ph - 16.0F) / tabs.length);
         float ry0 = this.py + (this.ph - tabH * tabs.length) / 2.0F;

         for (int i = 0; i < tabs.length; i++) {
            if (Ui.inside(mx, my, this.px + 7.0F, ry0 + i * tabH, this.railW - 10.0F, tabH)) {
               this.switchTab(tabs[i]);
               return true;
            }
         }

         if (this.barMax > 0.0F && Ui.inside(mx, my, this.barX - 6.0F, this.barY, 12.0F, this.barH)) {
            this.barGrab = (float)(my - this.barY);
            this.barDrag = true;
            return true;
         }

         if (Ui.inside(mx, my, this.contentX - 10.0F, this.contentY, this.contentW + 20.0F, this.contentHgt)) {
            for (Ui.Widget w : this.widgets) {
               if (w.click(mx, my, this.ox(), this.oy())) {
                  this.dragging = w;
                  return true;
               }
            }
         }

         return super.mouseClicked(e, doubleClick);
      }
   }

   public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
      if (this.barDrag) {
         this.dragBar(this.lastMy);
         return true;
      }

      if (NowPlaying.dragging()) {
         NowPlaying.dragBar(this.lastMx);
         return true;
      }

      if (this.dragging != null) {
         this.dragging.drag(this.lastMx, this.lastMy, this.ox(), this.oy());
      }

      return true;
   }

   public void mouseMoved(double x, double y) {
      if (this.barDrag) {
         this.dragBar(this.lastMy);
      } else {
         if (NowPlaying.dragging()) {
            NowPlaying.dragBar(this.lastMx);
         }

         if (this.dragging != null) {
            this.dragging.drag(this.lastMx, this.lastMy, this.ox(), this.oy());
         }
      }
   }

   public boolean mouseReleased(MouseButtonEvent e) {
      this.barDrag = false;
      NowPlaying.releaseBar();
      if (this.dragging != null) {
         this.dragging.release();
         this.dragging = null;
      }

      return true;
   }

   private void dragBar(double my) {
      float track = this.contentHgt - this.barH;
      if (!(track <= 0.0F)) {
         float k = (float)Math.max(0.0, Math.min(1.0, (my - this.barGrab - this.contentY) / track));
         this.scrollTarget = k * this.barMax;
         this.scroll = this.scrollTarget;
      }
   }

   public boolean mouseScrolled(double x, double y, double h, double v) {
      this.scrollTarget -= (float)v * 28.0F;
      return true;
   }

   private boolean anyCapturing() {
      for (Ui.Widget w : this.widgets) {
         if (w.capturesKeys()) {
            return true;
         }
      }

      return false;
   }

   public boolean keyPressed(KeyEvent e) {
      for (Ui.Widget w : this.widgets) {
         if (w.capturesKeys() && w.key(e)) {
            return true;
         }
      }

      if (!this.anyCapturing() && Keys.openMenu != null && Keys.openMenu.matches(e)) {
         this.onClose();
         return true;
      }

      if (e.key() == 256) {
         this.onClose();
         return true;
      }

      if (e.key() != 264 && e.key() != 265) {
         return super.keyPressed(e);
      }

      int d = e.key() == 264 ? 1 : -1;
      LyricsFlowScreen.Tab[] t = LyricsFlowScreen.Tab.values();
      this.switchTab(t[(this.tab.ordinal() + d + t.length) % t.length]);
      return true;
   }

   public boolean charTyped(CharacterEvent e) {
      for (Ui.Widget w : this.widgets) {
         if (w.chr(e)) {
            return true;
         }
      }

      return super.charTyped(e);
   }

   public void onClose() {
      if (!this.closing) {
         this.closing = true;
         ConfigManager.save();
         UiSounds.play(UiSounds.CLOSE, 1.0F);
      }
   }

   public boolean shouldCloseOnEsc() {
      return false;
   }

   public enum Tab {
      NOW("now", "music"),
      LYRICS("lyrics", "lyrics"),
      STYLE("style", "style"),
      SKY("sky", "sky"),
      COSMETICS("cosmetics", "cosmetic"),
      DISPLAY("display", "display"),
      PERF("perf", "perf"),
      KEYS("keys", "keys"),
      SAVED("saved", "saved");

      final String id;
      final String icon;

      Tab(String id, String icon) {
         this.id = id;
         this.icon = icon;
      }

      String title() {
         return Ui.tr("lyricsflow.tab." + this.id);
      }

      String subtitle() {
         return Ui.tr("lyricsflow.tab." + this.id + ".desc");
      }
   }
}
