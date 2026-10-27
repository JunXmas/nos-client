package dev.jun.nosclient.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class RenderTween {
    private final long duration;
    private long elapsed;
    private long last = -1L;

    public RenderTween(long duration) {
        this.duration = duration;
        this.elapsed = duration;
    }

    public void restart() {
        this.elapsed = 0L;
        this.last = -1L;
    }

    public float value(long now, boolean animated) {
        float t = this.linear(now, animated);
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    public float linear(long now, boolean animated) {
        if (!animated) {
            this.elapsed = this.duration;
            this.last = now;
            return 1.0F;
        }

        if (this.last >= 0L) {
            this.elapsed = Math.min(this.duration, this.elapsed + Math.clamp(now - this.last, 0L, 66000000L));
        }

        this.last = now;
        return (float)this.elapsed / (float)this.duration;
    }
}
