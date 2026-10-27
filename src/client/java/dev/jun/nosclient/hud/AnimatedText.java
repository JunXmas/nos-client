package dev.jun.nosclient.hud;

import java.util.LinkedHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class AnimatedText {
    private static final long DURATION = 150000000L;
    private final LinkedHashMap<String, AnimatedText.State> values = new LinkedHashMap<>(32, 0.75F, true);

    public AnimatedText.Frame update(String key, String value, long now, boolean animated) {
        AnimatedText.State state = this.values.get(key);
        if (state == null) {
            state = new AnimatedText.State();
            state.current = state.previous = value;
            state.changed = now - 150000000L;
            this.values.put(key, state);
            if (this.values.size() > 96) {
                this.values.pollFirstEntry();
            }
        } else if (!state.current.equals(value)) {
            if (now - state.changed >= 150000000L) {
                state.previous = state.current;
                state.changed = now;
            }

            state.current = value;
        }

        if (!animated) {
            state.previous = value;
            state.changed = now - 150000000L;
        }

        float t = Math.max(0.0F, Math.min(1.0F, (float)(now - state.changed) / 1.5E8F));
        return new AnimatedText.Frame(state.previous, state.current, 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t));
    }

    public int size() {
        return this.values.size();
    }

    @Environment(EnvType.CLIENT)
    public record Frame(String previous, String current, float progress) {
    }

    @Environment(EnvType.CLIENT)
    private static final class State {
        String previous;
        String current;
        long changed;
    }
}
