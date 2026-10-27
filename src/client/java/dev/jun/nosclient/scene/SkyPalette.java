package dev.jun.nosclient.scene;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SkyPalette {
    private SkyPalette() {
    }

    public static float daylight(double hour) {
        return (float)Math.max(0.0, Math.min(1.0, Math.sin((hour - 6.0) / 12.0 * Math.PI) * 1.3 + 0.15));
    }

    public static float angle(double hour) {
        return (float)((hour - 12.0) * 15.0);
    }

    public static int color(double hour) {
        float daylight = daylight(hour);
        int base = blend(1055281, 8432085, daylight);
        double distance = Math.min(Math.abs(hour - 6.0), Math.abs(hour - 18.0));
        return blend(base, 13144200, (float)(Math.exp(-distance * distance / 2.0) * 0.65));
    }

    public static int blend(int first, int second, float value) {
        value = Math.max(0.0F, Math.min(1.0F, value));
        int result = 0;

        for (int shift = 0; shift < 24; shift += 8) {
            result |= Math.round((first >>> shift & 0xFF) * (1.0F - value) + (second >>> shift & 0xFF) * value) << shift;
        }

        return result;
    }
}
