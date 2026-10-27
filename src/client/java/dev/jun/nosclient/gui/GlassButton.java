package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
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
    private float hover;
    private long last = System.nanoTime();

    public GlassButton(int x, int y, int w, int h, Component label, Runnable action) {
        super(x, y, w, h, label);
        this.action = action;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.action.run();
    }

    @Override
    protected void renderContents(GuiGraphics g, int mx, int my, float pt) {
        long now = System.nanoTime();
        float target = this.isHoveredOrFocused() && this.active ? 1.0F : 0.0F;
        this.hover = SceneConfig.INSTANCE.animations ? Ui.approach(this.hover, target, Math.min(0.05F, (float)(now - this.last) / 1.0E9F), 14.0F) : target;
        this.last = now;
        Glass.panel(
            g,
            this.getX(),
            this.getY(),
            this.width,
            this.height,
            Ui.lerpCol(-1173218016, -534037448, this.hover),
            Ui.lerpCol(1084142507, -1143608866, this.hover)
        );
        Component label = Ui.fit(Ui.ui(this.getMessage()), this.width - 18);
        Ui.text(g, label, this.getX() + (this.width - Ui.width(label)) / 2, this.getY() + (this.height - 8) / 2, this.active ? Ui.TEXT : Ui.FAINT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
