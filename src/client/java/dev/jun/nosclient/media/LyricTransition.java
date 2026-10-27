package dev.jun.nosclient.media;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class LyricTransition {
    private String key = "";
    private String current = "";
    private String previous = "";
    private long started;

    public void clear() {
        this.key = "";
        this.current = this.previous = "";
        this.started = 0L;
    }

    public LyricTransition.Frame update(String key, String line, long now, int milliseconds, boolean animated) {
        if (!key.equals(this.key) || !line.equals(this.current)) {
            this.previous = this.current;
            this.current = line;
            this.key = key;
            this.started = now;
        }

        float t = !animated ? 1.0F : (float)Math.max(0.0, Math.min(1.0, (now - this.started) / (Math.max(100, Math.min(1600, milliseconds)) * 1000000.0)));
        float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        if (t >= 1.0F) {
            this.previous = "";
        }

        return new LyricTransition.Frame(this.current, this.previous, eased);
    }

    @Environment(EnvType.CLIENT)
    public record Frame(String current, String previous, float progress) {
    }
}
