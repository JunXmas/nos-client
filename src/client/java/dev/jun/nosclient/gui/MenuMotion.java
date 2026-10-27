package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class MenuMotion {
    private long closing;
    private final RenderTween opening = new RenderTween(300000000L);
    private Runnable destination;

    public void enter() {
        if (this.destination == null) {
            this.opening.restart();
            this.closing = 0L;
        }
    }

    public boolean closing() {
        return this.destination != null;
    }

    public void exit(Runnable destination) {
        if (!this.closing()) {
            if (!SceneConfig.INSTANCE.animations) {
                destination.run();
            } else {
                this.destination = destination;
                this.closing = System.nanoTime();
            }
        }
    }

    public float opacity() {
        return this.destination == null
            ? this.opening.value(System.nanoTime(), SceneConfig.INSTANCE.animations)
            : 1.0F - Ui.ease(Math.min(1.0F, (float)(System.nanoTime() - this.closing) / 1.2E8F));
    }

    public void tick() {
        if (this.destination != null && System.nanoTime() - this.closing >= 120000000L) {
            Runnable next = this.destination;
            this.destination = null;
            next.run();
        }
    }
}
