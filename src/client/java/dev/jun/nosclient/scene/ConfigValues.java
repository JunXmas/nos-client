package dev.jun.nosclient.scene;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ConfigValues {
    private ConfigValues() {
    }

    public static boolean bool(String text) {
        if ("true".equalsIgnoreCase(text.trim())) {
            return true;
        } else if ("false".equalsIgnoreCase(text.trim())) {
            return false;
        } else {
            throw new IllegalArgumentException("Invalid boolean setting");
        }
    }

    public static double number(String text) {
        double value = Double.parseDouble(text.trim());
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Invalid finite setting");
        } else {
            return value;
        }
    }

    public static double bounded(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
