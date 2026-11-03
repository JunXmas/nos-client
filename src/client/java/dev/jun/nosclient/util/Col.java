package dev.jun.nosclient.util;

import java.awt.Color;

public final class Col {
    private Col() {}

    public static int a(int c) {
        return c >>> 24;
    }

    public static int r(int c) {
        return c >> 16 & 0xFF;
    }

    public static int g(int c) {
        return c >> 8 & 0xFF;
    }

    public static int b(int c) {
        return c & 0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        return clamp(a) << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static int alpha(int c, float mul) {
        if (mul >= 1.0F) {
            return c;
        } else {
            return mul <= 0.0F ? c & 16777215 : (int) (a(c) * mul) << 24 | c & 16777215;
        }
    }

    public static int withAlpha(int c, int alpha) {
        return clamp(alpha) << 24 | c & 16777215;
    }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return argb(
                (int) (a(c1) + (a(c2) - a(c1)) * t),
                (int) (r(c1) + (r(c2) - r(c1)) * t),
                (int) (g(c1) + (g(c2) - g(c1)) * t),
                (int) (b(c1) + (b(c2) - b(c1)) * t));
    }

    public static int brighten(int c, float amount) {
        return lerp(c, c & 0xFF000000 | 16777215, amount);
    }

    public static int darken(int c, float amount) {
        return lerp(c, c & 0xFF000000, amount);
    }

    public static int hsv(float h, float s, float v) {
        return 0xFF000000 | Color.HSBtoRGB(h, s, v) & 16777215;
    }

    public static float[] toHsv(int c) {
        return Color.RGBtoHSB(r(c), g(c), b(c), null);
    }
}
