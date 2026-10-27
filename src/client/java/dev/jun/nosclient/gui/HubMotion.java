package dev.jun.nosclient.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class HubMotion {
    private final RenderTween tween = new RenderTween(340000000L);
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
        // Nội dung trượt vào theo hướng chuyển, mờ nhanh hơn trượt để không thấy "bóng ma" ở giữa đường.
        return new HubMotion.Frame(Math.min(1.0F, p * 1.35F), this.direction * 22 * (1.0F - p), 4.0F * (1.0F - p));
    }

    public int direction() {
        return this.direction;
    }

    @Environment(EnvType.CLIENT)
    public record Frame(float opacity, float x, float y) {
    }
}
