package dev.jun.nosclient.theme;

import java.util.Locale;
import java.util.function.IntPredicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record ThemePalette(int accent, int surface, int raised, int text, int muted, int faint, int ink) {
    public static final int DEFAULT_ACCENT = 10931891;
    public static final int DEFAULT_SURFACE = 1187104;
    public static final int DEFAULT_TEXT = 15856621;

    public static ThemePalette of(int accent, int surface, int text) {
        surface = opaque(surface);
        if (luminance(surface) > 0.075) {
            surface = toward(surface, -16777216, color -> luminance(color) <= 0.075);
        }

        accent = readable(opaque(accent), surface, 4.5);
        int raised = mix(surface, accent, 0.11);
        text = readable(opaque(text), raised, 7.0);
        int muted = readable(mix(text, raised, 0.35), raised, 4.5);
        int faint = readable(mix(text, raised, 0.52), raised, 3.0);
        int ink = contrast(-16777216, accent) >= contrast(-1, accent) ? -16777216 : -1;
        return new ThemePalette(accent, surface, raised, text, muted, faint, ink);
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | rgb & 16777215;
    }

    public static int mix(int a, int b, double amount) {
        amount = Math.max(0.0, Math.min(1.0, amount));
        int result = -16777216;

        for (int shift = 0; shift <= 16; shift += 8) {
            result |= (int)Math.round((a >>> shift & 0xFF) * (1.0 - amount) + (b >>> shift & 0xFF) * amount) << shift;
        }

        return result;
    }

    private static double linear(int value) {
        double c = value / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static double luminance(int rgb) {
        return 0.2126 * linear(rgb >> 16 & 0xFF) + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF);
    }

    public static double contrast(int a, int b) {
        double x = luminance(a);
        double y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static int toward(int from, int to, IntPredicate satisfied) {
        double low = 0.0;
        double high = 1.0;

        for (int i = 0; i < 16; i++) {
            double mid = (low + high) / 2.0;
            if (satisfied.test(mix(from, to, mid))) {
                high = mid;
            } else {
                low = mid;
            }
        }

        return mix(from, to, high);
    }

    public static int readable(int foreground, int background, double minimum) {
        if (contrast(foreground, background) >= minimum) {
            return foreground;
        }

        int endpoint = contrast(-1, background) >= contrast(-16777216, background) ? -1 : -16777216;
        return toward(foreground, endpoint, color -> contrast(color, background) >= minimum);
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 16777215);
    }

    public static int parseHex(String value) {
        if (!value.matches("#?[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Expected six hexadecimal digits");
        } else {
            return Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16);
        }
    }
}
