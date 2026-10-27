package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.theme.Theme;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class GlassButton extends AbstractButton {
    private final Runnable action;
    private final Spring hover = new Spring(0.0F);
    private final Spring press = new Spring(0.0F);
    private long last = System.nanoTime();
    /** Giây trễ lúc màn mở: các nút vào so le từ trên xuống thay vì hiện cùng lúc. */
    private float delay;
    private long born = System.nanoTime();

    public GlassButton(int x, int y, int w, int h, Component label, Runnable action) {
        super(x, y, w, h, label);
        this.action = action;
    }

    public GlassButton delayed(float seconds) {
        this.delay = seconds;
        this.born = System.nanoTime();
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.press.velocity = -9.0F;
        this.action.run();
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float pt) {
        long now = System.nanoTime();
        boolean animated = SceneConfig.INSTANCE.animations;
        float dt = Math.min(0.05F, (now - this.last) / 1.0E9F);
        this.last = now;
        float target = this.isHoveredOrFocused() && this.active ? 1.0F : 0.0F;
        if (animated) {
            this.hover.step(target, dt, 20.0F, 0.62F);
            this.press.step(0.0F, dt, 30.0F, 0.45F);
        } else {
            this.hover.snap(target);
            this.press.snap(0.0F);
        }

        float h = Math.clamp(this.hover.value, 0.0F, 1.0F);
        float enter = animated ? Math.clamp(((now - this.born) / 1.0E9F - this.delay) / 0.42F, 0.0F, 1.0F) : 1.0F;
        float rise = 1.0F - Ui.ease(enter);
        // Hover nở ra theo lò xo (vượt nhẹ rồi lắng), bấm thì lún xuống rồi bật lại.
        float scale = 1.0F + 0.035F * this.hover.value + 0.06F * this.press.value - 0.04F * rise;
        float opacity = UiOpacity.value;
        UiOpacity.value = opacity * Ui.ease(enter);
        g.pose().pushMatrix();

        try {
            float cx = this.getX() + this.width / 2.0F;
            float cy = this.getY() + this.height / 2.0F + 10.0F * rise;
            g.pose().translate(cx, cy);
            g.pose().scale(scale, scale);
            g.pose().translate(-cx, -this.getY() - this.height / 2.0F);
            Glass.panel(
                g, this.getX(), this.getY(), this.width, this.height, Ui.lerpCol(-1173218016, -534037448, h), Ui.lerpCol(1084142507, -1143608866, h)
            );
            if (h > 0.01F) {
                // Vệt sáng quét ngang mép trên khi trỏ chuột vào.
                float sweep = Math.clamp(this.hover.value, 0.0F, 1.1F) / 1.1F;
                int lw = Math.round((this.width - 8) * sweep);
                Glass.fill(g, this.getX() + this.width / 2 - lw / 2, this.getY() + 1, lw, 1, Ui.alpha(Theme.palette().accent() | 0xFF000000, 0.55F * h));
            }

            Component label = Ui.fit(Ui.ui(this.getMessage()), this.width - 18);
            Ui.text(g, label, this.getX() + (this.width - Ui.width(label)) / 2, this.getY() + (this.height - 8) / 2, this.active ? Ui.TEXT : Ui.FAINT);
        } finally {
            g.pose().popMatrix();
            UiOpacity.value = opacity;
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
