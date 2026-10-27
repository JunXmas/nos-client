package dev.jun.nosclient.gui;

import dev.jun.nosclient.Mods;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.scene.SceneRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
final class ScenePanel extends AbstractWidget {
    static final int W = 220;
    static final int H = 178;
    private static final String[] ICON = new String[]{"world", "inv", "light"};
    private static final String[] LABEL = new String[]{"nosclient.scene.blossom", "nosclient.scene.cane", "nosclient.scene.shader"};
    private final SceneConfig c = SceneConfig.INSTANCE;
    private static final int ACC = Mods.catColor("world");
    private final float s;
    private final float[] knob = new float[3];
    private float todShown;
    private boolean dragging;
    private long last = System.nanoTime();

    ScenePanel(int x, int y, float scale) {
        super(x, y, Math.round(220.0F * scale), Math.round(178.0F * scale), Component.translatable("nosclient.scene.title"));
        this.s = scale;

        for (int i = 0; i < 3; i++) {
            this.knob[i] = this.on(i) ? 1.0F : 0.0F;
        }

        this.todShown = (float)this.c.tod;
    }

    private boolean on(int i) {
        return i == 0 ? this.c.blossom : (i == 1 ? this.c.cane : this.c.fx);
    }

    private int rowY(int i) {
        return this.getY() + 30 + i * 26;
    }

    private int tx0() {
        return this.getX() + 16;
    }

    private int tx1() {
        return this.getX() + 220 - 16;
    }

    private int trackY() {
        return this.getY() + 128;
    }

    private int segY() {
        return this.getY() + 146;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        long now = System.nanoTime();
        float dt = Math.min(0.05F, (float)(now - this.last) / 1.0E9F);
        this.last = now;
        int x = this.getX();
        int y = this.getY();
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(this.s, this.s);
        g.pose().translate(-x, -y);
        Ui.hint = this.s;

        try {
            Ui.panel(g, x, y, x + 220, y + 178, 0);
            Ui.text(g, Ui.small(Component.translatable("nosclient.scene.title")), x + 16, y + 13, Ui.MUTED);

            for (int i = 0; i < 3; i++) {
                int ry = this.rowY(i);
                if (i > 0) {
                    g.fill(x + 16, ry - 6, x + 220 - 16, ry - 5, 352321535);
                }

                Icons.draw(g, ICON[i], x + 10, ry - 5, 24.0F);
                Ui.text(g, Ui.ui(Component.translatable(LABEL[i])), x + 36, ry + 3, Ui.TEXT);
                this.knob[i] = Ui.approach(this.knob[i], this.on(i) ? 1.0F : 0.0F, dt, 14.0F);
                Ui.toggle(g, x + 220 - 16 - 24, ry + 1, Ui.ease(this.knob[i]), ACC);
            }

            this.todShown = Ui.approach(this.todShown, (float)this.c.tod, dt, 12.0F);
            int ty = this.trackY();
            int a = this.tx0();
            int b = this.tx1();
            float p = (this.todShown - 40.0F) / 60.0F;
            Ui.text(g, Ui.ui(Component.translatable("nosclient.scene.tod")), a, ty - 16, Ui.TEXT);
            Component val = Ui.bold(
                Component.translatable(this.c.tod < 62.0 ? "nosclient.scene.sunset" : (this.c.tod < 80.0 ? "nosclient.scene.dusk" : "nosclient.scene.night"))
            );
            Ui.text(g, val, b - Ui.width(val), ty - 16, Ui.ACCENT_HI);
            g.fill(a, ty, b, ty + 4, 536870911);
            int px = Math.round(a + p * (b - a));
            if (px > a + 2) {
                g.fill(a, ty, px + 2, ty + 4, ACC);
                g.fill(a + 2, ty, px, ty + 4, Ui.lerpCol(ACC, -1, 0.4F));
            }

            g.fill(px - 7, ty - 5, px + 7, ty + 9, Ui.alpha(ACC, 0.4F));
            g.fill(px - 5, ty - 3, px + 5, ty + 7, -1);
            int sy = this.segY();
            int half = 94;
            g.fill(x + 16, sy, x + 220 - 16, sy + 20, 939524096);
            Ui.frame(g, x + 16, sy, x + 220 - 16, sy + 20, 318767103);

            for (int i = 0; i < 2; i++) {
                int bx = x + 18 + i * half;
                boolean sel = i == 0 ? this.c.tod < 62.0 : this.c.tod >= 80.0;
                if (sel) {
                    g.fill(bx, sy + 2, bx + half - 4, sy + 18, Ui.alpha(ACC, 0.65F));
                }

                Component t = Ui.bold(Component.translatable(i == 0 ? "nosclient.scene.sunset" : "nosclient.scene.night"));
                Ui.text(g, t, bx + (half - 4 - Ui.width(t)) / 2, sy + 6, sel ? -1387269 : Ui.MUTED);
            }
        } finally {
            Ui.hint = 1.0F;
            g.pose().popMatrix();
        }
    }

    private double lx(double mx) {
        return this.getX() + (mx - this.getX()) / this.s;
    }

    private double ly(double my) {
        return this.getY() + (my - this.getY()) / this.s;
    }

    @Override
    public void onClick(MouseButtonEvent e, boolean dbl) {
        double mx = this.lx(e.x());
        double my = this.ly(e.y());

        for (int i = 0; i < 3; i++) {
            int ry = this.rowY(i);
            if (my >= ry - 6 && my < ry + 20) {
                boolean v = !this.on(i);
                if (i < 2) {
                    SceneRenderer.INSTANCE.toggle(i + 1, v);
                } else {
                    this.c.fx = v;
                    this.c.save();
                }

                return;
            }
        }

        if (my >= this.trackY() - 8 && my <= this.trackY() + 12) {
            this.dragging = true;
            this.setTod(mx);
        } else if (my >= this.segY() && my < this.segY() + 20) {
            this.c.tod = mx < this.getX() + 110.0 ? 47.0 : 88.0;
            this.c.save();
        }
    }

    private void setTod(double mx) {
        this.c.tod = 40.0 + 60.0 * Math.min(1.0, Math.max(0.0, (mx - this.tx0()) / (this.tx1() - this.tx0())));
    }

    @Override
    protected void onDrag(MouseButtonEvent e, double dx, double dy) {
        if (this.dragging) {
            this.setTod(this.lx(e.x()));
        }
    }

    @Override
    public void onRelease(MouseButtonEvent e) {
        if (this.dragging) {
            this.dragging = false;
            this.c.save();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        this.defaultButtonNarrationText(out);
    }
}
