package dev.jun.nosclient.gui.music;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;

import dev.jun.nosclient.ClientEffects;
import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.gui.effects.SkyThumbs;
import dev.jun.nosclient.render.Gfx;
import dev.jun.nosclient.render.Themes;
import dev.jun.nosclient.sky.SkyPreset;
import dev.jun.nosclient.util.Anim;
import dev.jun.nosclient.util.Col;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class Ui {
    public static final int BG = -233697255;
    public static final int CARD = -15001562;
    public static final int CARD_HOVER = -14541264;
    public static final int LINE = 352321535;
    public static final int TEXT = -856070;
    public static final int TEXT2 = -6515020;
    public static final int TEXT3 = -9541498;
    public static final int OFF = -12962480;
    public static final String F = "ui_4";
    public static final String FB = "ui_bold_4";

    private Ui() {}

    public static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    public static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    static void save() {
        MusicSettings.save();
        ClientEffects.saveConfig();
    }

    public static List<String> wrap(String s, String font, float size, float maxW) {
        List<String> out = new ArrayList<>();

        for (String para : s.split("\n")) {
            StringBuilder line = new StringBuilder();

            for (String word : para.split(" ")) {
                String test = line.isEmpty() ? word : line + " " + word;
                if (Gfx.textWidth(test, font, size) > maxW && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }

            out.add(line.toString());
        }

        return out;
    }

    public static class Button extends Ui.Row {
        private final Supplier<String> button;
        private final Runnable action;
        private final String icon;
        private final boolean primary;

        public Button(
                String label,
                String desc,
                Supplier<String> buttonText,
                String icon,
                boolean primary,
                Runnable action) {
            super(label, desc);
            this.button = buttonText;
            this.icon = icon;
            this.primary = primary;
            this.action = action;
        }

        private float bw() {
            return Gfx.textWidth(this.button.get(), Ui.FB, 7.4F) + (this.icon != null ? 26 : 16);
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            this.drawHover(g, ox, oy, mx, my, a);
            float bw = this.bw();
            float bh = 15.0F;
            float bx = ox + this.x + this.w - bw - 2.0F;
            float by = oy + this.y + (this.h - bh) / 2.0F;
            this.drawLabel(g, ox, oy, a, this.w - bw - 12.0F);
            boolean over = Ui.inside(mx, my, bx, by, bw, bh);
            float sc = 1.0F - this.pressT * 0.06F + (over ? 0.02F : 0.0F);
            float cw = bw * sc;
            float ch = bh * sc;
            float cx = bx + (bw - cw) / 2.0F;
            float cy = by + (bh - ch) / 2.0F;
            int base = this.primary ? Themes.accent() : -13883584;
            int col = over ? Col.brighten(base, 0.12F) : base;
            if (this.primary && over) {
                Gfx.glow(g, cx, cy, cw, ch, ch / 2.0F, 3.0F, Col.alpha(base, a * 0.4F));
            }

            Gfx.round(g, cx, cy, cw, ch, ch / 2.0F, Col.alpha(col, a));
            float tx = cx + 8.0F;
            if (this.icon != null) {
                Icons.draw(g, this.icon, cx + 10.0F, cy + ch / 2.0F, 8.0F, Col.alpha(-1, a));
                tx += 10.0F;
            }

            Gfx.text(
                    g,
                    this.button.get(),
                    Ui.FB,
                    tx,
                    cy + (ch - 7.4F) / 2.0F - 0.3F,
                    7.4F,
                    Col.alpha(-1, a));
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            if (!this.hit(mx, my, ox, oy)) {
                return false;
            }

            UiSounds.play(UiSounds.CLICK, 1.1F);
            this.ripple(mx, my);
            this.action.run();
            return true;
        }
    }

    public static class Card extends Ui.Widget {
        @Override
        public boolean dragging() {
            return rows.stream().anyMatch(Ui.Widget::dragging);
        }

        @Override
        public String searchText() {
            return rows.stream()
                    .map(Ui.Widget::searchText)
                    .collect(java.util.stream.Collectors.joining(" "));
        }

        final List<Ui.Widget> rows = new ArrayList<>();

        public Ui.Card add(Ui.Widget w) {
            this.rows.add(w);
            return this;
        }

        @Override
        public float layout(float x, float y, float w) {
            this.x = x;
            this.y = y;
            this.w = w;
            float cy = y + 2.0F;

            for (Ui.Widget r : this.rows) {
                cy += r.layout(x + 10.0F, cy, w - 20.0F);
            }

            this.h = cy - y + 2.0F;
            return this.h + 6.0F;
        }

        @Override
        protected float measure(float w) {
            return this.h;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            dev.jun.nosclient.gui.Glass.panel(
                    g,
                    Math.round(ox + x),
                    Math.round(oy + y),
                    Math.round(w),
                    Math.round(h),
                    Col.alpha(dev.jun.nosclient.theme.Theme.palette().raised(), a),
                    Col.alpha(285212671, a));

            for (int i = 0; i < this.rows.size(); i++) {
                Ui.Widget r = this.rows.get(i);
                r.render(g, ox, oy, mx, my, a);
                if (i < this.rows.size() - 1) {
                    Gfx.rect(g, ox + r.x, oy + r.y + r.h, r.w, 0.5F, Col.alpha(352321535, a));
                }
            }
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            for (Ui.Widget r : this.rows) {
                if (r.click(mx, my, ox, oy)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void drag(double mx, double my, float ox, float oy) {
            for (Ui.Widget r : this.rows) {
                r.drag(mx, my, ox, oy);
            }
        }

        @Override
        public void release() {
            for (Ui.Widget r : this.rows) {
                r.release();
            }
        }

        @Override
        public boolean key(KeyEvent e) {
            for (Ui.Widget r : this.rows) {
                if (r.key(e)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean chr(CharacterEvent e) {
            for (Ui.Widget r : this.rows) {
                if (r.chr(e)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean capturesKeys() {
            for (Ui.Widget r : this.rows) {
                if (r.capturesKeys()) {
                    return true;
                }
            }

            return false;
        }
    }

    public static class Chips extends Ui.Row {
        private final String[] options;
        private final IntSupplier get;
        private final IntConsumer set;
        private float[] cx;
        private float[] cy;
        private float[] cw;
        private static final float CH = 15.0F;
        private static final float GAP = 4.0F;
        private String[] fonts;
        private int[] dots;
        private Runnable reset;
        private float spin;

        public Ui.Chips fonts(String[] f) {
            this.fonts = f;
            return this;
        }

        public Ui.Chips dots(int[] d) {
            this.dots = d;
            return this;
        }

        public Ui.Chips reset(Runnable r) {
            this.reset = r;
            return this;
        }

        private String fontOf(int i) {
            return this.fonts != null && i < this.fonts.length && this.fonts[i] != null
                    ? this.fonts[i]
                    : Ui.FB;
        }

        private float dotW(int i) {
            return this.dots != null && i < this.dots.length ? 9.0F : 0.0F;
        }

        public Chips(
                String label, String desc, String[] optionKeys, IntSupplier get, IntConsumer set) {
            super(label, desc);
            this.options = optionKeys;
            this.get = get;
            this.set = set;
        }

        private float head() {
            return this.desc != null ? 30.0F : 22.0F;
        }

        private int place(float w) {
            int n = this.options.length;
            this.cx = new float[n];
            this.cy = new float[n];
            this.cw = new float[n];
            float x = 2.0F;
            float y = 0.0F;
            int lines = 1;

            for (int i = 0; i < n; i++) {
                float ww =
                        Gfx.textWidth(Ui.tr(this.options[i]), this.fontOf(i), 7.2F)
                                + 16.0F
                                + this.dotW(i);
                if (x + ww > w - 2.0F && x > 2.0F) {
                    x = 2.0F;
                    y += 19.0F;
                    lines++;
                }

                this.cx[i] = x;
                this.cy[i] = y;
                this.cw[i] = ww;
                x += ww + 4.0F;
            }

            return lines;
        }

        @Override
        protected float measure(float w) {
            int lines = this.place(w);
            return this.head() + lines * 19.0F + 4.0F;
        }

        @Override
        protected void drawLabel(GuiGraphics g, float ox, float oy, float a, float maxW) {
            float ly = oy + this.y + 5.0F;
            Gfx.text(
                    g,
                    Gfx.ellipsize(Ui.tr(this.label), Ui.FB, 8.4F, this.w - 4.0F),
                    Ui.FB,
                    ox + this.x + 2.0F,
                    ly,
                    8.4F,
                    Col.alpha(dev.jun.nosclient.theme.Theme.palette().text(), a));
            if (this.desc != null) {
                Gfx.text(
                        g,
                        Gfx.ellipsize(Ui.tr(this.desc), Ui.F, 6.8F, this.w - 4.0F),
                        Ui.F,
                        ox + this.x + 2.0F,
                        ly + 11.5F,
                        6.8F,
                        Col.alpha(dev.jun.nosclient.theme.Theme.palette().muted(), a));
            }
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            this.drawHover(g, ox, oy, mx, my, a);
            this.drawLabel(g, ox, oy, a, this.w);
            if (this.cx == null) {
                this.place(this.w);
            }

            int sel = this.get.getAsInt();
            float by = oy + this.y + this.head();

            for (int i = 0; i < this.options.length; i++) {
                float px = ox + this.x + this.cx[i];
                float py = by + this.cy[i];
                boolean on = i == sel;
                boolean over = Ui.inside(mx, my, px, py, this.cw[i], 15.0F);
                int bg = on ? -1 : (over ? -14015176 : -15659240);
                Gfx.round(g, px, py, this.cw[i], 15.0F, 7.5F, Col.alpha(bg, a));
                int col = on ? -15330529 : (over ? -856070 : -6515020);
                float dw = this.dotW(i);
                if (dw > 0.0F) {
                    Gfx.circle(
                            g, px + 9.0F, py + 7.5F, 3.2F, Col.alpha(this.dots[i] | 0xFF000000, a));
                }

                Gfx.textCentered(
                        g,
                        Ui.tr(this.options[i]),
                        this.fontOf(i),
                        px + dw + (this.cw[i] - dw) / 2.0F,
                        py + 3.9F - 0.4F,
                        7.2F,
                        Col.alpha(col, a));
            }

            if (this.reset != null) {
                float rx = ox + this.x + this.w - 16.0F;
                float ry = oy + this.y + 3.0F;
                boolean ro = Ui.inside(mx, my, rx, ry, 16.0F, 16.0F);
                this.spin = Anim.approach(this.spin, 0.0F, 6.0F);
                Gfx.round(g, rx, ry, 16.0F, 16.0F, 8.0F, Col.alpha(ro ? -13883584 : -15659240, a));
                g.pose().pushMatrix();
                g.pose().translate(rx + 8.0F, ry + 8.0F);
                g.pose().rotate(-this.spin * 6.28F);
                Icons.draw(g, "refresh", 0.0F, 0.0F, 8.0F, Col.alpha(ro ? -856070 : -9541498, a));
                g.pose().popMatrix();
            }
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            if (this.hit(mx, my, ox, oy) && this.cx != null) {
                if (this.reset != null
                        && Ui.inside(
                                mx,
                                my,
                                ox + this.x + this.w - 16.0F,
                                oy + this.y + 3.0F,
                                16.0F,
                                16.0F)) {
                    this.reset.run();
                    this.spin = 1.0F;
                    Ui.save();
                    UiSounds.play(UiSounds.CLICK, 0.9F);
                    this.ripple(mx, my);
                    return true;
                }

                float by = oy + this.y + this.head();

                for (int i = 0; i < this.options.length; i++) {
                    if (Ui.inside(
                            mx, my, ox + this.x + this.cx[i], by + this.cy[i], this.cw[i], 15.0F)) {
                        this.set.accept(i);
                        Ui.save();
                        UiSounds.play(UiSounds.CLICK, 1.0F);
                        this.ripple(mx, my);
                        return true;
                    }
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public static class Choice extends Ui.Row {
        private final String[] options;
        private final IntSupplier get;
        private final IntConsumer set;
        private float pillX = -1.0F;
        private float pillW;
        private float[] ox0;
        private float[] ow;
        private boolean stacked;

        public Choice(
                String label, String desc, String[] optionKeys, IntSupplier get, IntConsumer set) {
            super(label, desc);
            this.options = optionKeys;
            this.get = get;
            this.set = set;
        }

        private float segH() {
            return 15.0F;
        }

        private float totalWidth() {
            float total = 6.0F;

            for (String o : this.options) {
                total += Gfx.textWidth(Ui.tr(o), Ui.FB, 7.2F) + 14.0F;
            }

            return total;
        }

        @Override
        protected float measure(float w) {
            this.stacked = this.totalWidth() > w * 0.56F;
            return !this.stacked ? super.measure(w) : (this.desc != null ? 30 : 22) + 18;
        }

        private float segY() {
            return this.stacked ? this.h - this.segH() - 5.0F : (this.h - this.segH()) / 2.0F;
        }

        @Override
        protected void drawLabel(GuiGraphics g, float ox, float oy, float a, float maxW) {
            if (!this.stacked) {
                super.drawLabel(g, ox, oy, a, maxW);
            } else {
                float ly = oy + this.y + 5.0F;
                Gfx.text(
                        g,
                        Gfx.ellipsize(Ui.tr(this.label), Ui.FB, 8.4F, this.w - 4.0F),
                        Ui.FB,
                        ox + this.x + 2.0F,
                        ly,
                        8.4F,
                        Col.alpha(dev.jun.nosclient.theme.Theme.palette().text(), a));
                if (this.desc != null) {
                    Gfx.text(
                            g,
                            Gfx.ellipsize(Ui.tr(this.desc), Ui.F, 6.8F, this.w - 4.0F),
                            Ui.F,
                            ox + this.x + 2.0F,
                            ly + 11.5F,
                            6.8F,
                            Col.alpha(dev.jun.nosclient.theme.Theme.palette().muted(), a));
                }
            }
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            this.drawHover(g, ox, oy, mx, my, a);
            int n = this.options.length;
            this.ox0 = new float[n];
            this.ow = new float[n];
            float total = 3.0F;

            for (int i = 0; i < n; i++) {
                this.ow[i] = Gfx.textWidth(Ui.tr(this.options[i]), Ui.FB, 7.2F) + 14.0F;
                total += this.ow[i];
            }

            total += 3.0F;
            float sh = this.segH();
            if (this.stacked && total > this.w - 4.0F) {
                float k = (this.w - 4.0F - 6.0F) / (total - 6.0F);

                for (int i = 0; i < n; i++) {
                    this.ow[i] = this.ow[i] * k;
                }

                total = this.w - 4.0F;
            }

            float bx = this.stacked ? ox + this.x + 2.0F : ox + this.x + this.w - total - 2.0F;
            float by = oy + this.y + this.segY();
            float cx = bx + 3.0F;

            for (int i = 0; i < n; i++) {
                this.ox0[i] = cx;
                cx += this.ow[i];
            }

            this.drawLabel(g, ox, oy, a, this.w - total - 12.0F);
            Gfx.round(g, bx, by, total, sh, sh / 2.0F, Col.alpha(-15659240, a));
            int sel = Math.max(0, Math.min(n - 1, this.get.getAsInt()));
            float tx = this.ox0[sel];
            float twd = this.ow[sel];
            if (this.pillX < 0.0F) {
                this.pillX = tx - bx;
                this.pillW = twd;
            }

            this.pillX = Anim.approach(this.pillX, tx - bx, 15.0F);
            this.pillW = Anim.approach(this.pillW, twd, 15.0F);
            Gfx.round(
                    g,
                    bx + this.pillX,
                    by + 2.0F,
                    this.pillW,
                    sh - 4.0F,
                    (sh - 4.0F) / 2.0F,
                    Col.alpha(-1, a));

            for (int i = 0; i < n; i++) {
                float center = this.ox0[i] + this.ow[i] / 2.0F;
                float dist =
                        Math.abs(bx + this.pillX + this.pillW / 2.0F - center)
                                / Math.max(1.0F, this.ow[i]);
                int col = Col.lerp(-15330529, -6515020, Math.min(1.0F, dist * 1.6F));
                boolean over = Ui.inside(mx, my, this.ox0[i], by, this.ow[i], sh);
                if (over && i != sel) {
                    col = Col.lerp(col, -856070, 0.6F);
                }

                String lbl = Gfx.ellipsize(Ui.tr(this.options[i]), Ui.FB, 7.2F, this.ow[i] - 6.0F);
                Gfx.textCentered(
                        g,
                        lbl,
                        Ui.FB,
                        center,
                        by + (sh - 7.2F) / 2.0F - 0.4F,
                        7.2F,
                        Col.alpha(col, a));
            }
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            if (this.ox0 != null && this.hit(mx, my, ox, oy)) {
                int n = this.options.length;
                float sh = this.segH();
                float by = oy + this.y + this.segY();

                for (int i = 0; i < n; i++) {
                    if (Ui.inside(mx, my, this.ox0[i], by - 3.0F, this.ow[i], sh + 6.0F)) {
                        if (i != this.get.getAsInt()) {
                            this.set.accept(i);
                            Ui.save();
                            UiSounds.play(UiSounds.CLICK, 1.05F + i * 0.04F);
                        }

                        this.ripple(mx, my);
                        return true;
                    }
                }

                this.set.accept((this.get.getAsInt() + 1) % n);
                Ui.save();
                UiSounds.play(UiSounds.CLICK, 1.0F);
                this.ripple(mx, my);
                return true;
            } else {
                return false;
            }
        }
    }

    public static class Colors extends Ui.Row {
        private final IntSupplier get;
        private final IntConsumer set;
        private final int[] pal;
        private final int first;
        private final float[] pop;
        private boolean none;

        public Colors(String label, String desc, IntSupplier get, IntConsumer set) {
            this(label, desc, get, set, Themes.SINGLE, true);
        }

        public Colors(
                String label,
                String desc,
                IntSupplier get,
                IntConsumer set,
                int[] palette,
                boolean theme) {
            super(label, desc);
            this.get = get;
            this.set = set;
            this.pal = palette;
            this.first = theme ? 1 : 0;
            this.pop = new float[palette.length + 1];
        }

        public Ui.Colors noneDot() {
            this.none = true;
            return this;
        }

        private float dotsW() {
            return (this.pal.length + this.first) * 12.5F;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            this.drawHover(g, ox, oy, mx, my, a);
            float dw = this.dotsW();
            this.drawLabel(g, ox, oy, a, this.w - dw - 10.0F);
            float sx = ox + this.x + this.w - dw;
            float cy = oy + this.y + this.h / 2.0F;
            int cur = this.get.getAsInt();

            for (int i = 1 - this.first; i <= this.pal.length; i++) {
                float cx = sx + (i - 1 + this.first) * 12.5F + 6.0F;
                boolean sel =
                        i == 0 ? cur == 0 : (cur | 0xFF000000) == (this.pal[i - 1] | 0xFF000000);
                boolean over = Ui.inside(mx, my, cx - 6.0F, cy - 6.0F, 12.0F, 12.0F);
                this.pop[i] = Anim.approach(this.pop[i], sel ? 1.0F : (over ? 0.5F : 0.0F), 16.0F);
                float r = 4.2F + this.pop[i] * 0.9F;
                if (sel) {
                    Gfx.circle(g, cx, cy, r + 2.2F, Col.alpha(-1, a));
                }

                if (sel) {
                    Gfx.circle(
                            g,
                            cx,
                            cy,
                            r + 1.1F,
                            Col.alpha(dev.jun.nosclient.theme.Theme.palette().raised(), a));
                }

                if (i == 0) {
                    Gfx.circle(g, cx, cy, r, Col.alpha(-12962480, a));
                    Gfx.textCentered(
                            g,
                            this.none ? "/" : "A",
                            Ui.FB,
                            cx,
                            cy - 3.2F,
                            6.4F,
                            Col.alpha(this.none ? -2076592 : -856070, a));
                } else {
                    Gfx.circle(g, cx, cy, r, Col.alpha(this.pal[i - 1] | 0xFF000000, a));
                    if ((this.pal[i - 1] & 16777215) < 2105376) {
                        Gfx.roundOutline(
                                g,
                                cx - r,
                                cy - r,
                                r * 2.0F,
                                r * 2.0F,
                                r,
                                0.6F,
                                Col.alpha(1627389951, a));
                    }
                }
            }
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            if (!this.hit(mx, my, ox, oy)) {
                return false;
            }

            float sx = ox + this.x + this.w - this.dotsW();
            float cy = oy + this.y + this.h / 2.0F;

            for (int i = 1 - this.first; i <= this.pal.length; i++) {
                float cx = sx + (i - 1 + this.first) * 12.5F + 6.0F;
                if (Ui.inside(mx, my, cx - 6.25F, cy - 8.0F, 12.5F, 16.0F)) {
                    this.set.accept(i == 0 ? 0 : this.pal[i - 1]);
                    Ui.save();
                    UiSounds.play(UiSounds.CLICK, 0.9F + i * 0.03F);
                    this.pop[i] = 1.6F;
                    return true;
                }
            }

            return true;
        }
    }

    public static class CosmeticGrid extends Ui.Widget {
        private final String[] ids;
        private final IntSupplier get;
        private final IntConsumer set;
        private static final float CW = 66.0F;
        private static final float CH = 80.0F;
        private static final float GAP = 6.0F;
        private int cols = 1;
        private final float[] hov;

        public CosmeticGrid(String[] ids, IntSupplier get, IntConsumer set) {
            this.ids = ids;
            this.get = get;
            this.set = set;
            this.hov = new float[ids.length];
        }

        @Override
        protected boolean interactive() {
            return true;
        }

        @Override
        protected float measure(float w) {
            this.cols = Math.max(1, (int) ((w + 6.0F) / 72.0F));
            int rows = (this.ids.length + this.cols - 1) / this.cols;
            return rows * 86.0F + 4.0F;
        }

        private float cellW() {
            return (this.w - 6.0F * (this.cols - 1)) / this.cols;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            float cw = this.cellW();
            int sel = this.get.getAsInt();

            for (int i = 0; i < this.ids.length; i++) {
                float x = ox + this.x + i % this.cols * (cw + 6.0F);
                float y = oy + this.y + i / this.cols * 86.0F;
                boolean over = Ui.inside(mx, my, x, y, cw, 80.0F);
                boolean on = i == sel;
                this.hov[i] = Anim.approach(this.hov[i], over ? 1.0F : 0.0F, 14.0F);
                int bg = Col.lerp(-14541264, -13883584, this.hov[i]);
                Gfx.round(g, x, y, cw, 80.0F, 8.0F, Col.alpha(bg, a));
                if (on) {
                    Gfx.roundOutline(g, x, y, cw, 80.0F, 8.0F, 1.4F, Col.alpha(Themes.accent(), a));
                }

                float im = Math.min(cw - 12.0F, 56.0F);
                float ix = x + (cw - im) / 2.0F;
                float iy = y + 5.0F - this.hov[i] * 1.5F;
                if ("none".equals(this.ids[i])) {
                    Icons.draw(
                            g,
                            "close",
                            ix + im / 2.0F,
                            iy + im / 2.0F,
                            im * 0.45F,
                            Col.alpha(-9541498, a));
                } else {
                    Gfx.image(
                            g,
                            ClientResources.id("textures/gui/cosmetics/" + this.ids[i] + ".png"),
                            128,
                            128,
                            ix,
                            iy,
                            im,
                            im,
                            Col.alpha(-1, a));
                }

                String name =
                        Gfx.ellipsize(
                                Ui.tr("nosclient.music.cosmetic." + this.ids[i]),
                                Ui.FB,
                                6.6F,
                                cw - 8.0F);
                Gfx.textCentered(
                        g,
                        name,
                        Ui.FB,
                        x + cw / 2.0F,
                        y + 80.0F - 14.0F,
                        6.6F,
                        Col.alpha(on ? -856070 : -6515020, a));
            }
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            float cw = this.cellW();

            for (int i = 0; i < this.ids.length; i++) {
                float x = ox + this.x + i % this.cols * (cw + 6.0F);
                float y = oy + this.y + i / this.cols * 86.0F;
                if (Ui.inside(mx, my, x, y, cw, 80.0F)) {
                    this.set.accept(i);
                    Ui.save();
                    UiSounds.play(UiSounds.CLICK, 1.0F + i % 5 * 0.02F);
                    return true;
                }
            }

            return false;
        }
    }

    public static class Custom extends Ui.Widget {
        private final Function<Float, Float> height;
        private final Ui.Custom.Painter painter;
        private final Ui.Custom.Clicker clicker;

        public Custom(
                Function<Float, Float> height,
                Ui.Custom.Painter painter,
                Ui.Custom.Clicker clicker) {
            this.height = height;
            this.painter = painter;
            this.clicker = clicker;
        }

        @Override
        protected float measure(float w) {
            return this.height.apply(w);
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            this.painter.paint(g, ox + this.x, oy + this.y, this.w, this.h, mx, my, a);
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            return this.clicker != null
                    && this.clicker.click(mx, my, ox + this.x, oy + this.y, this.w, this.h);
        }

        public interface Clicker {
            boolean click(double var1, double var3, float var5, float var6, float var7, float var8);
        }

        public interface Painter {
            void paint(
                    GuiGraphics var1,
                    float var2,
                    float var3,
                    float var4,
                    float var5,
                    double var6,
                    double var8,
                    float var10);
        }
    }

    public static class Info extends Ui.Widget {
        private final Supplier<String> text;
        private List<String> lines = List.of();

        public Info(Supplier<String> text) {
            this.text = text;
        }

        @Override
        protected float measure(float w) {
            this.lines = Ui.wrap(this.text.get(), Ui.F, 7.2F, w - 4.0F);
            return this.lines.size() * 10.5F + 6.0F;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            for (int i = 0; i < this.lines.size(); i++) {
                Gfx.text(
                        g,
                        this.lines.get(i),
                        Ui.F,
                        ox + this.x + 2.0F,
                        oy + this.y + 3.0F + i * 10.5F,
                        7.2F,
                        Col.alpha(dev.jun.nosclient.theme.Theme.palette().muted(), a));
            }
        }
    }

    public static class KeyBind extends Ui.Row {
        private final KeyMapping mapping;
        private boolean listening;

        public KeyBind(String label, String desc, KeyMapping mapping) {
            super(label, desc);
            this.mapping = mapping;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            this.drawHover(g, ox, oy, mx, my, a);
            String s =
                    this.listening
                            ? Ui.tr("nosclient.music.menu.press_key")
                            : this.mapping.getTranslatedKeyMessage().getString();
            if (this.mapping.isUnbound() && !this.listening) {
                s = Ui.tr("nosclient.music.menu.unbound");
            }

            float kw = Math.max(26.0F, Gfx.textWidth(s, Ui.FB, 7.6F) + 14.0F);
            float kh = 15.0F;
            float kx = ox + this.x + this.w - kw - 2.0F;
            float ky = oy + this.y + (this.h - kh) / 2.0F;
            boolean custom = !this.mapping.isDefault();
            float rx = kx - 18.0F;
            float ry = oy + this.y + this.h / 2.0F;
            this.drawLabel(g, ox, oy, a, this.w - kw - (custom ? 30 : 12));
            int bg =
                    this.listening
                            ? Col.lerp(
                                    -13883584,
                                    Themes.accent(),
                                    (float) (0.5 + 0.5 * Math.sin(Anim.seconds() * 6.0)))
                            : -13883584;
            Gfx.round(g, kx, ky, kw, kh, 5.0F, Col.alpha(bg, a));
            Gfx.roundOutline(g, kx, ky, kw, kh, 5.0F, 0.5F, Col.alpha(822083583, a));
            Gfx.textCentered(
                    g,
                    s,
                    Ui.FB,
                    kx + kw / 2.0F,
                    ky + (kh - 7.6F) / 2.0F - 0.3F,
                    7.6F,
                    Col.alpha(this.mapping.isUnbound() && !this.listening ? -6515020 : -856070, a));
            if (custom) {
                boolean over = Ui.inside(mx, my, rx - 7.0F, ry - 7.0F, 14.0F, 14.0F);
                Gfx.circle(g, rx, ry, 7.0F, Col.alpha(over ? 956301311 : 486539263, a));
                Icons.draw(g, "refresh", rx, ry, 6.5F, Col.alpha(over ? -856070 : -6515020, a));
            }

            if (this.listening) {
                Gfx.text(
                        g,
                        Ui.tr("nosclient.music.menu.esc_unbind"),
                        Ui.F,
                        ox + this.x + 10.0F,
                        oy + this.y + this.h - 7.0F,
                        5.6F,
                        Col.alpha(-9541498, a));
            }
        }

        private float keyBoxX(float ox) {
            String s =
                    this.listening
                            ? Ui.tr("nosclient.music.menu.press_key")
                            : this.mapping.getTranslatedKeyMessage().getString();
            if (this.mapping.isUnbound() && !this.listening) {
                s = Ui.tr("nosclient.music.menu.unbound");
            }

            float kw = Math.max(26.0F, Gfx.textWidth(s, Ui.FB, 7.6F) + 14.0F);
            return ox + this.x + this.w - kw - 2.0F;
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            if (!this.hit(mx, my, ox, oy)) {
                this.listening = false;
                return false;
            }

            if (!this.mapping.isDefault() && !this.listening) {
                float rx = this.keyBoxX(ox) - 18.0F;
                float ry = oy + this.y + this.h / 2.0F;
                if (Ui.inside(mx, my, rx - 7.0F, ry - 7.0F, 14.0F, 14.0F)) {
                    this.mapping.setKey(this.mapping.getDefaultKey());
                    KeyMapping.resetMapping();
                    Minecraft.getInstance().options.save();
                    UiSounds.play(UiSounds.TOGGLE_ON, 1.1F);
                    return true;
                }
            }

            this.listening = !this.listening;
            UiSounds.play(UiSounds.CLICK, this.listening ? 1.2F : 0.9F);
            this.ripple(mx, my);
            return true;
        }

        @Override
        public boolean capturesKeys() {
            return this.listening;
        }

        @Override
        public boolean key(KeyEvent e) {
            if (!this.listening) {
                return false;
            }

            Minecraft mc = Minecraft.getInstance();
            boolean clear = e.key() == 256 || e.key() == 259;
            Key k = clear ? InputConstants.UNKNOWN : InputConstants.getKey(e);
            this.mapping.setKey(k);
            KeyMapping.resetMapping();
            mc.options.save();
            this.listening = false;
            UiSounds.play(UiSounds.TOGGLE_ON, 1.1F);
            return true;
        }
    }

    public static class Picker extends Ui.Row {
        private final Supplier<String> value;
        private final Supplier<String> valueFont;
        private final IntSupplier dot;
        private final Runnable prev;
        private final Runnable next;
        private Runnable reset;
        private float bx;
        private float bw;
        private float flash;
        private float rx = -1.0F;
        private float spin;

        public Picker(
                String label,
                String desc,
                Supplier<String> value,
                Supplier<String> valueFont,
                IntSupplier dot,
                Runnable prev,
                Runnable next) {
            super(label, desc);
            this.value = value;
            this.valueFont = valueFont;
            this.dot = dot;
            this.prev = prev;
            this.next = next;
        }

        public Ui.Picker reset(Runnable r) {
            this.reset = r;
            return this;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            this.drawHover(g, ox, oy, mx, my, a);
            String v = this.value.get();
            String vf =
                    this.valueFont != null && this.valueFont.get() != null
                            ? this.valueFont.get()
                            : Ui.FB;
            float size = 8.6F;
            int dc = this.dot != null ? this.dot.getAsInt() : 0;
            float vw = Math.min(110.0F, Gfx.textWidth(v, vf, size)) + (dc != 0 ? 11 : 0);
            this.bw = vw + 34.0F;
            this.bx = ox + this.x + this.w - this.bw - 2.0F;
            float by = oy + this.y + (this.h - 16.0F) / 2.0F;
            float extra = 0.0F;
            if (this.reset != null) {
                this.rx = this.bx - 18.0F;
                extra = 20.0F;
                boolean ro = Ui.inside(mx, my, this.rx, by, 16.0F, 16.0F);
                this.spin = Anim.approach(this.spin, 0.0F, 6.0F);
                Gfx.round(
                        g,
                        this.rx,
                        by,
                        16.0F,
                        16.0F,
                        8.0F,
                        Col.alpha(ro ? -13883584 : -15659240, a));
                g.pose().pushMatrix();
                g.pose().translate(this.rx + 8.0F, by + 8.0F);
                g.pose().rotate(-this.spin * 6.28F);
                Icons.draw(g, "refresh", 0.0F, 0.0F, 8.0F, Col.alpha(ro ? -856070 : -9541498, a));
                g.pose().popMatrix();
            }

            this.drawLabel(g, ox, oy, a, this.w - this.bw - 12.0F - extra);
            this.flash = Anim.approach(this.flash, 0.0F, 8.0F);
            Gfx.round(
                    g,
                    this.bx,
                    by,
                    this.bw,
                    16.0F,
                    8.0F,
                    Col.alpha(Col.lerp(-15659240, -13883584, this.flash), a));
            boolean ol = Ui.inside(mx, my, this.bx, by, this.bw / 2.0F, 16.0F);
            boolean or = Ui.inside(mx, my, this.bx + this.bw / 2.0F, by, this.bw / 2.0F, 16.0F);
            Gfx.text(
                    g,
                    "‹",
                    Ui.FB,
                    this.bx + 6.0F,
                    by + 3.5F,
                    9.0F,
                    Col.alpha(ol ? -856070 : -9541498, a));
            Gfx.text(
                    g,
                    "›",
                    Ui.FB,
                    this.bx + this.bw - 10.0F,
                    by + 3.5F,
                    9.0F,
                    Col.alpha(or ? -856070 : -9541498, a));
            float tx = this.bx + 17.0F;
            if (dc != 0) {
                Gfx.circle(g, tx + 3.5F, by + 8.0F, 3.6F, Col.alpha(dc | 0xFF000000, a));
                tx += 11.0F;
            }

            Gfx.text(
                    g,
                    Gfx.ellipsize(v, vf, size, 110.0F),
                    vf,
                    tx,
                    by + 3.6F,
                    size,
                    Col.alpha(dev.jun.nosclient.theme.Theme.palette().text(), a));
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            if (!this.hit(mx, my, ox, oy)) {
                return false;
            }

            if (this.reset != null && mx >= this.rx && mx < this.rx + 16.0F) {
                this.reset.run();
                this.spin = 1.0F;
                Ui.save();
                UiSounds.play(UiSounds.CLICK, 0.9F);
                this.ripple(mx, my);
                return true;
            }

            if (mx < this.bx) {
                return false;
            }

            if (mx < this.bx + this.bw / 2.0F) {
                this.prev.run();
            } else {
                this.next.run();
            }

            this.flash = 1.0F;
            Ui.save();
            UiSounds.play(UiSounds.CLICK, mx < this.bx + this.bw / 2.0F ? 0.95F : 1.1F);
            this.ripple(mx, my);
            return true;
        }
    }

    public abstract static class Row extends Ui.Widget {
        @Override
        public String searchText() {
            return Ui.tr(label) + " " + (desc == null ? "" : Ui.tr(desc));
        }

        protected final String label;
        protected final String desc;
        protected float pressT;
        protected double rippleX;
        protected double rippleY;
        protected double rippleAt = -10.0;

        protected Row(String label, String desc) {
            this.label = label;
            this.desc = desc;
        }

        @Override
        protected float measure(float w) {
            return this.desc != null ? 30.0F : 24.0F;
        }

        @Override
        protected boolean interactive() {
            return true;
        }

        protected void drawLabel(GuiGraphics g, float ox, float oy, float a, float maxW) {
            float ly = oy + this.y + (this.desc != null ? 6.0F : 7.5F);
            Gfx.text(
                    g,
                    Gfx.ellipsize(Ui.tr(this.label), Ui.FB, 8.4F, maxW),
                    Ui.FB,
                    ox + this.x + 2.0F,
                    ly,
                    8.4F,
                    Col.alpha(dev.jun.nosclient.theme.Theme.palette().text(), a));
            if (this.desc != null) {
                Gfx.text(
                        g,
                        Gfx.ellipsize(Ui.tr(this.desc), Ui.F, 6.8F, maxW),
                        Ui.F,
                        ox + this.x + 2.0F,
                        ly + 11.5F,
                        6.8F,
                        Col.alpha(dev.jun.nosclient.theme.Theme.palette().muted(), a));
            }
        }

        protected void drawHover(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            boolean over =
                    Ui.inside(mx, my, ox + this.x - 6.0F, oy + this.y, this.w + 12.0F, this.h);
            this.updateHover(over);
            if (this.hover > 0.01F) {
                Gfx.round(
                        g,
                        ox + this.x - 6.0F,
                        oy + this.y + 1.0F,
                        this.w + 12.0F,
                        this.h - 2.0F,
                        6.0F,
                        Col.alpha(218103807, a * this.hover));
            }

            double age = Anim.seconds() - this.rippleAt;
            if (age < 0.45) {
                float p = (float) (age / 0.45);
                float r = 8.0F + p * 60.0F;
                g.enableScissor(
                        (int) (ox + this.x - 6.0F),
                        (int) (oy + this.y),
                        (int) Math.ceil(ox + this.x + this.w + 6.0F),
                        (int) Math.ceil(oy + this.y + this.h));
                Gfx.circle(
                        g,
                        (float) this.rippleX,
                        (float) this.rippleY,
                        r,
                        Col.alpha(Themes.accent(), a * 0.16F * (1.0F - p)));
                g.disableScissor();
            }

            this.pressT = Anim.approach(this.pressT, 0.0F, 10.0F);
        }

        protected void ripple(double mx, double my) {
            this.rippleX = mx;
            this.rippleY = my;
            this.rippleAt = Anim.seconds();
            this.pressT = 1.0F;
        }

        protected boolean hit(double mx, double my, float ox, float oy) {
            return Ui.inside(mx, my, ox + this.x - 6.0F, oy + this.y, this.w + 12.0F, this.h);
        }
    }

    public static class Section extends Ui.Widget {
        @Override
        public String searchText() {
            return Ui.tr(key);
        }

        private final String key;

        public Section(String key) {
            this.key = key;
        }

        @Override
        protected float measure(float w) {
            return 18.0F;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            String s = Ui.tr(this.key).toUpperCase(Locale.ROOT);
            Gfx.text(
                    g,
                    s,
                    Ui.FB,
                    ox + this.x + 2.0F,
                    oy + this.y + 8.0F,
                    6.4F,
                    Col.alpha(dev.jun.nosclient.theme.Theme.palette().muted(), a));
        }
    }

    public static class SkyGrid extends Ui.Widget {
        private final SkyPreset[] skies;
        private final Supplier<SkyPreset> get;
        private final Consumer<SkyPreset> set;
        private static final float MINW = 92.0F;
        private static final float GAP = 6.0F;
        private int cols = 1;
        private final float[] hov;

        public SkyGrid(SkyPreset[] skies, Supplier<SkyPreset> get, Consumer<SkyPreset> set) {
            this.skies = skies;
            this.get = get;
            this.set = set;
            this.hov = new float[skies.length];
        }

        @Override
        protected boolean interactive() {
            return true;
        }

        private float cellW() {
            return (this.w - 6.0F * (this.cols - 1)) / this.cols;
        }

        private float picH() {
            return this.cellW() * 0.5625F;
        }

        private float cellH() {
            return this.picH() + 14.0F;
        }

        @Override
        protected float measure(float w) {
            this.cols = Math.max(2, (int) ((w + 6.0F) / 98.0F));
            this.w = w;
            int rows = (this.skies.length + this.cols - 1) / this.cols;
            return rows * (this.cellH() + 6.0F) + 2.0F;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            float cw = this.cellW();
            float ph = this.picH();
            float ch = this.cellH();
            SkyPreset cur = this.get.get();

            for (int i = 0; i < this.skies.length; i++) {
                float x = ox + this.x + i % this.cols * (cw + 6.0F);
                float y = oy + this.y + i / this.cols * (ch + 6.0F);
                boolean over = Ui.inside(mx, my, x, y, cw, ch);
                boolean on = this.skies[i] == cur;
                this.hov[i] = Anim.approach(this.hov[i], over ? 1.0F : 0.0F, 14.0F);
                float lift = -this.hov[i] * 1.2F;
                if (on) {
                    Gfx.glow(
                            g,
                            x,
                            y + lift,
                            cw,
                            ph,
                            6.0F,
                            4.0F,
                            Col.alpha(Themes.accent(), a * 0.45F));
                }

                Gfx.round(g, x, y + lift, cw, ph, 6.0F, Col.alpha(-14541264, a));
                SkyPreset p = this.skies[i];
                float fx = x;
                float fy = y + lift;
                int ix = Math.round(fx);
                int iy = Math.round(fy);
                int iw = Math.round(cw);
                int ih = Math.round(ph);
                SkyThumbs.draw(g, p, ix, iy, iw, ih, a);
                Gfx.roundOutline(
                        g,
                        x,
                        y + lift,
                        cw,
                        ph,
                        6.0F,
                        on ? 1.3F : 0.5F,
                        Col.alpha(on ? Themes.accent() : 587202559, a));
                String name =
                        Gfx.ellipsize(
                                Ui.tr("nosclient.music.sky." + p.name().toLowerCase(Locale.ROOT)),
                                Ui.FB,
                                6.6F,
                                cw - 4.0F);
                Gfx.text(
                        g,
                        name,
                        Ui.FB,
                        x + 1.0F,
                        y + lift + ph + 3.5F,
                        6.6F,
                        Col.alpha(on ? -856070 : Col.lerp(-6515020, -856070, this.hov[i]), a));
            }
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            float cw = this.cellW();
            float ch = this.cellH();

            for (int i = 0; i < this.skies.length; i++) {
                float x = ox + this.x + i % this.cols * (cw + 6.0F);
                float y = oy + this.y + i / this.cols * (ch + 6.0F);
                if (Ui.inside(mx, my, x, y, cw, ch)) {
                    this.set.accept(this.skies[i]);
                    Ui.save();
                    UiSounds.play(UiSounds.CLICK, 1.0F + i % 5 * 0.02F);
                    return true;
                }
            }

            return false;
        }
    }

    public static class Slider extends Ui.Row {
        @Override
        public boolean dragging() {
            return dragging;
        }

        private final double min;
        private final double max;
        private final double step;
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private final DoubleFunction<String> fmt;
        private boolean dragging;
        private float thumb;
        private float shown = Float.NaN;
        private double lastStepValue = Double.NaN;

        public Slider(
                String label,
                double min,
                double max,
                double step,
                DoubleSupplier get,
                DoubleConsumer set,
                DoubleFunction<String> fmt) {
            super(label, null);
            this.min = min;
            this.max = max;
            this.step = step;
            this.get = get;
            this.set = set;
            this.fmt = fmt;
        }

        @Override
        protected float measure(float w) {
            return 32.0F;
        }

        private float trackX(float ox) {
            return ox + this.x + 2.0F;
        }

        private float trackW() {
            return this.w - 4.0F;
        }

        private float trackY(float oy) {
            return oy + this.y + 22.0F;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            this.drawHover(g, ox, oy, mx, my, a);
            double v = this.get.getAsDouble();
            float p = (float) ((v - this.min) / (this.max - this.min));
            if (Float.isNaN(this.shown)) {
                this.shown = p;
            }

            this.shown = Anim.approach(this.shown, p, this.dragging ? 30.0F : 14.0F);
            String val = this.fmt.apply(v);
            Gfx.text(
                    g,
                    Ui.tr(this.label),
                    Ui.FB,
                    ox + this.x + 2.0F,
                    oy + this.y + 5.0F,
                    8.4F,
                    Col.alpha(dev.jun.nosclient.theme.Theme.palette().text(), a));
            float vw = Gfx.textWidth(val, Ui.F, 7.6F);
            Gfx.text(
                    g,
                    val,
                    Ui.F,
                    ox + this.x + this.w - vw - 2.0F,
                    oy + this.y + 5.6F,
                    7.6F,
                    Col.alpha(dev.jun.nosclient.theme.Theme.palette().muted(), a));
            float tx = this.trackX(ox);
            float tw = this.trackW();
            float ty = this.trackY(oy);
            Gfx.round(g, tx, ty - 1.5F, tw, 3.0F, 1.5F, Col.alpha(-12962480, a));
            Gfx.round(
                    g,
                    tx,
                    ty - 1.5F,
                    Math.max(3.0F, tw * this.shown),
                    3.0F,
                    1.5F,
                    Col.alpha(Themes.accent(), a));
            boolean over = Ui.inside(mx, my, tx - 4.0F, ty - 7.0F, tw + 8.0F, 14.0F);
            this.thumb =
                    Anim.approach(this.thumb, this.dragging ? 1.4F : (over ? 1.18F : 1.0F), 18.0F);
            float r = 4.6F * this.thumb;
            float kx = tx + tw * this.shown;
            if (this.dragging) {
                Gfx.circle(g, kx, ty, r + 4.0F, Col.alpha(Themes.accent(), a * 0.22F));
            }

            Gfx.circle(g, kx, ty + 0.6F, r + 0.6F, Col.alpha(1426063360, a));
            Gfx.circle(g, kx, ty, r, Col.alpha(-1, a));
        }

        private void apply(double mx, float ox) {
            double p = (mx - this.trackX(ox)) / this.trackW();
            p = Math.max(0.0, Math.min(1.0, p));
            double v = this.min + (this.max - this.min) * p;
            if (this.step > 0.0) {
                v = Math.round(v / this.step) * this.step;
            }

            v = Math.max(this.min, Math.min(this.max, v));
            if (Double.isNaN(this.lastStepValue) || Math.abs(v - this.lastStepValue) > 1.0E-9) {
                if (!Double.isNaN(this.lastStepValue)) {
                    UiSounds.play(UiSounds.SLIDER, 0.8F + (float) p * 0.6F);
                }

                this.lastStepValue = v;
                this.set.accept(v);
            }
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            if (!Ui.inside(mx, my, ox + this.x - 4.0F, oy + this.y + 12.0F, this.w + 8.0F, 20.0F)) {
                return false;
            }

            this.dragging = true;
            this.lastStepValue = this.get.getAsDouble();
            this.apply(mx, ox);
            return true;
        }

        @Override
        public void drag(double mx, double my, float ox, float oy) {
            if (this.dragging) {
                this.apply(mx, ox);
            }
        }

        @Override
        public void release() {
            if (this.dragging) {
                this.dragging = false;
                Ui.save();
            }
        }
    }

    public static class Spacer extends Ui.Widget {
        private final float size;

        public Spacer(float size) {
            this.size = size;
        }

        @Override
        protected float measure(float w) {
            return this.size;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {}
    }

    public static class Status extends Ui.Row {
        private final Supplier<String> text;
        private final Supplier<String> sub;
        private final IntSupplier color;

        public Status(Supplier<String> text, Supplier<String> sub, IntSupplier color) {
            super("", null);
            this.text = text;
            this.sub = sub;
            this.color = color;
        }

        @Override
        protected boolean interactive() {
            return false;
        }

        @Override
        protected float measure(float w) {
            return 32.0F;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            Gfx.text(
                    g,
                    Gfx.ellipsize(this.text.get(), Ui.FB, 8.6F, this.w - 30.0F),
                    Ui.FB,
                    ox + this.x + 2.0F,
                    oy + this.y + 6.0F,
                    8.6F,
                    Col.alpha(dev.jun.nosclient.theme.Theme.palette().text(), a));
            String s = this.sub.get();
            if (s != null) {
                Gfx.text(
                        g,
                        Gfx.ellipsize(s, Ui.F, 7.0F, this.w - 30.0F),
                        Ui.F,
                        ox + this.x + 2.0F,
                        oy + this.y + 18.0F,
                        7.0F,
                        Col.alpha(dev.jun.nosclient.theme.Theme.palette().muted(), a));
            }

            float cx = ox + this.x + this.w - 8.0F;
            float cy = oy + this.y + this.h / 2.0F;
            double pulse = 0.5 + 0.5 * Math.sin(Anim.seconds() * 3.0);
            Gfx.circle(
                    g,
                    cx,
                    cy,
                    6.0F + (float) pulse * 2.0F,
                    Col.alpha(this.color.getAsInt(), a * 0.18F));
            Gfx.circle(g, cx, cy, 3.6F, Col.alpha(this.color.getAsInt(), a));
        }
    }

    public static class TextField extends Ui.Row {
        private final Supplier<String> get;
        private final Consumer<String> set;
        private final String placeholder;
        private boolean focused;

        public TextField(
                String label,
                String desc,
                String placeholderKey,
                Supplier<String> get,
                Consumer<String> set) {
            super(label, desc);
            this.get = get;
            this.set = set;
            this.placeholder = placeholderKey;
        }

        @Override
        protected float measure(float w) {
            return 44.0F;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            this.drawHover(g, ox, oy, mx, my, a);
            Gfx.text(
                    g,
                    Ui.tr(this.label),
                    Ui.FB,
                    ox + this.x + 2.0F,
                    oy + this.y + 4.0F,
                    8.4F,
                    Col.alpha(dev.jun.nosclient.theme.Theme.palette().text(), a));
            if (this.desc != null) {
                Gfx.text(
                        g,
                        Gfx.ellipsize(Ui.tr(this.desc), Ui.F, 6.8F, this.w),
                        Ui.F,
                        ox + this.x + 2.0F,
                        oy + this.y + 14.5F,
                        6.8F,
                        Col.alpha(dev.jun.nosclient.theme.Theme.palette().muted(), a));
            }

            float fx = ox + this.x + 2.0F;
            float fy = oy + this.y + 25.0F;
            float fw = this.w - 4.0F;
            float fh = 15.0F;
            Gfx.round(g, fx, fy, fw, fh, 5.0F, Col.alpha(-15659240, a));
            Gfx.roundOutline(
                    g,
                    fx,
                    fy,
                    fw,
                    fh,
                    5.0F,
                    this.focused ? 1.0F : 0.5F,
                    Col.alpha(this.focused ? Themes.accent() : 822083583, a));
            String v = this.get.get();
            boolean empty = v.isEmpty();
            String shown = empty ? Ui.tr(this.placeholder) : v;
            float tw = Gfx.textWidth(shown, Ui.F, 7.4F);
            float off = Math.max(0.0F, tw - (fw - 12.0F));
            g.enableScissor((int) fx + 1, (int) fy, (int) (fx + fw - 1.0F), (int) (fy + fh));
            Gfx.text(
                    g,
                    shown,
                    Ui.F,
                    fx + 6.0F - off,
                    fy + 4.0F,
                    7.4F,
                    Col.alpha(empty ? -9541498 : -856070, a));
            if (this.focused && System.currentTimeMillis() / 500L % 2L == 0L) {
                float cx = fx + 6.0F - off + (empty ? 0.0F : tw) + 0.5F;
                Gfx.rect(
                        g,
                        cx,
                        fy + 3.5F,
                        0.8F,
                        8.0F,
                        Col.alpha(dev.jun.nosclient.theme.Theme.palette().text(), a));
            }

            g.disableScissor();
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            boolean over = Ui.inside(mx, my, ox + this.x, oy + this.y + 24.0F, this.w, 17.0F);
            if (over && !this.focused) {
                UiSounds.play(UiSounds.CLICK, 1.2F);
            }

            this.focused = over;
            return over;
        }

        @Override
        public boolean capturesKeys() {
            return this.focused;
        }

        @Override
        public boolean key(KeyEvent e) {
            if (!this.focused) {
                return false;
            }

            String v = this.get.get();
            if (e.key() == 259) {
                if (!v.isEmpty()) {
                    this.set.accept(v.substring(0, v.length() - 1));
                }
            } else if (e.key() == 86 && (e.modifiers() & 2) != 0) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                this.set.accept((v + clip.trim()).replaceAll("[\\r\\n]", ""));
            } else {
                if (e.key() != 257 && e.key() != 256 && e.key() != 258) {
                    return e.key() != 256;
                }

                this.focused = false;
            }

            Ui.save();
            return true;
        }

        @Override
        public boolean chr(CharacterEvent e) {
            if (!this.focused) {
                return false;
            }

            String s = e.codepointAsString();
            if (!s.isEmpty() && !Character.isISOControl(s.charAt(0))) {
                if (this.get.get().length() < 120) {
                    this.set.accept(this.get.get() + s);
                }

                Ui.save();
                return true;
            } else {
                return true;
            }
        }
    }

    public static class Toggle extends Ui.Row {
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private float knob = -1.0F;

        public Toggle(String label, String desc, BooleanSupplier get, Consumer<Boolean> set) {
            super(label, desc);
            this.get = get;
            this.set = set;
        }

        @Override
        public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            this.drawHover(g, ox, oy, mx, my, a);
            boolean on = this.get.getAsBoolean();
            if (this.knob < 0.0F) {
                this.knob = on ? 1.0F : 0.0F;
            }

            this.knob = Anim.approach(this.knob, on ? 1.0F : 0.0F, 16.0F);
            float tw = 24.0F;
            float th = 13.0F;
            float tx = ox + this.x + this.w - tw - 2.0F;
            float ty = oy + this.y + (this.h - th) / 2.0F;
            this.drawLabel(g, ox, oy, a, this.w - tw - 14.0F);
            int bg = Col.lerp(-12962480, Themes.accent(), this.knob);
            if (this.knob > 0.5F) {
                Gfx.glow(
                        g,
                        tx,
                        ty,
                        tw,
                        th,
                        th / 2.0F,
                        3.0F,
                        Col.alpha(Themes.accent(), a * 0.35F * this.knob));
            }

            Gfx.round(g, tx, ty, tw, th, th / 2.0F, Col.alpha(bg, a));
            float squash = 1.0F + this.pressT * 0.25F;
            float kd = th - 3.4F;
            float kw = kd * squash;
            float kx = tx + 1.7F + (tw - 3.4F - kw) * Anim.easeInOut(this.knob);
            Gfx.shadow(g, kx, ty + 1.7F, kw, kd, kd / 2.0F, 1.5F, Col.alpha(-16777216, a * 0.4F));
            Gfx.round(g, kx, ty + 1.7F, kw, kd, kd / 2.0F, Col.alpha(-1, a));
        }

        @Override
        public boolean click(double mx, double my, float ox, float oy) {
            if (!this.hit(mx, my, ox, oy)) {
                return false;
            }

            boolean v = !this.get.getAsBoolean();
            this.set.accept(v);
            Ui.save();
            UiSounds.play(v ? UiSounds.TOGGLE_ON : UiSounds.TOGGLE_OFF, 1.0F);
            this.ripple(mx, my);
            return true;
        }
    }

    public abstract static class Widget {
        public boolean dragging() {
            return false;
        }

        public String searchText() {
            return "";
        }

        public float x;
        public float y;
        public float w;
        public float h;
        protected float hover;
        protected boolean wasHovered;

        public float layout(float x, float y, float w) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = this.measure(w);
            return this.h;
        }

        protected abstract float measure(float var1);

        public abstract void render(
                GuiGraphics var1, float var2, float var3, double var4, double var6, float var8);

        protected boolean interactive() {
            return false;
        }

        protected void updateHover(boolean over) {
            if (this.interactive() && over && !this.wasHovered) {
                UiSounds.play(UiSounds.HOVER, 1.2F);
            }

            this.wasHovered = over;
            this.hover = Anim.approach(this.hover, over ? 1.0F : 0.0F, 14.0F);
        }

        public boolean click(double mx, double my, float ox, float oy) {
            return false;
        }

        public void drag(double mx, double my, float ox, float oy) {}

        public void release() {}

        public boolean key(KeyEvent e) {
            return false;
        }

        public boolean chr(CharacterEvent e) {
            return false;
        }

        public boolean capturesKeys() {
            return false;
        }
    }
}
