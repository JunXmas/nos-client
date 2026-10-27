package dev.jun.nosclient.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record UiTransform(float scale, float x, float y) {
    public static final UiTransform IDENTITY = new UiTransform(1.0F, 0.0F, 0.0F);

    public double localX(double screen) {
        return (screen - this.x) / this.scale;
    }

    public double localY(double screen) {
        return (screen - this.y) / this.scale;
    }

    public UiTransform translated(float dx, float dy) {
        return new UiTransform(this.scale, this.x + dx * this.scale, this.y + dy * this.scale);
    }

    public static UiTransform opening(float progress, float cx, float cy) {
        float scale = 0.94F + 0.06F * progress;
        return new UiTransform(scale, cx * (1.0F - scale), cy * (1.0F - scale) + 12.0F * (1.0F - progress));
    }
}
