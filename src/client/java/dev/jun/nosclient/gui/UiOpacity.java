package dev.jun.nosclient.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class UiOpacity {
    public static float value = 1.0F;

    private UiOpacity() {
    }

    public static int tint(int argb) {
        return Math.round((argb >>> 24) * value) << 24 | argb & 16777215;
    }
}
