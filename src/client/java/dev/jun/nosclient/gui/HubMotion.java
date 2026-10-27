package dev.jun.nosclient.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class HubMotion {
    private final RenderTween tween = new RenderTween(240000000L);
    private boolean pending;
    private int direction = 1;

    public void enter(int direction) {
        this.direction = direction < 0 ? -1 : 1;
        this.pending = true;
    }

    public HubMotion.Frame frame(long now, boolean animated) {
        if (this.pending) {
            this.tween.restart();
            this.pending = false;
        }

        float p = this.tween.value(now, animated);
        return new HubMotion.Frame(p, this.direction * 16 * (1.0F - p), 3.0F * (1.0F - p));
    }

    @Environment(EnvType.CLIENT)
    public record Frame(float opacity, float x, float y) {
    }
}
