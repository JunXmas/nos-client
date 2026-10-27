package dev.jun.nosclient.theme;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record HsvColor(double hue, double saturation, double value) {
    public HsvColor {
        hue = Double.isFinite(hue) ? (hue % 360.0 + 360.0) % 360.0 : 0.0;
        saturation = Double.isFinite(saturation) ? Math.max(0.0, Math.min(1.0, saturation)) : 0.0;
        value = Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }

    public int rgb() {
        double c = this.value * this.saturation;
        double h = this.hue / 60.0;
        double x = c * (1.0 - Math.abs(h % 2.0 - 1.0));
        double m = this.value - c;
        double r;
        double g;
        double b;
        if (h < 1.0) {
            r = c;
            g = x;
            b = 0.0;
        } else if (h < 2.0) {
            r = x;
            g = c;
            b = 0.0;
        } else if (h < 3.0) {
            r = 0.0;
            g = c;
            b = x;
        } else if (h < 4.0) {
            r = 0.0;
            g = x;
            b = c;
        } else if (h < 5.0) {
            r = x;
            g = 0.0;
            b = c;
        } else {
            r = c;
            g = 0.0;
            b = x;
        }

        return (int)Math.round((r + m) * 255.0) << 16 | (int)Math.round((g + m) * 255.0) << 8 | (int)Math.round((b + m) * 255.0);
    }

    public static HsvColor fromRgb(int rgb) {
        double r = (rgb >> 16 & 0xFF) / 255.0;
        double g = (rgb >> 8 & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        double high = Math.max(r, Math.max(g, b));
        double low = Math.min(r, Math.min(g, b));
        double delta = high - low;
        double hue = 0.0;
        if (delta > 0.0) {
            hue = high == r ? 60.0 * ((g - b) / delta % 6.0) : (high == g ? 60.0 * ((b - r) / delta + 2.0) : 60.0 * ((r - g) / delta + 4.0));
        }

        return new HsvColor(hue, high == 0.0 ? 0.0 : delta / high, high);
    }
}
