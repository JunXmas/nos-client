package dev.jun.nosclient.theme;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Theme {
    private static int accent = -1;
    private static int surface = -1;
    private static int text = -1;
    private static ThemePalette palette = ThemePalette.of(10931891, 1187104, 15856621);

    private Theme() {
    }

    public static ThemePalette palette() {
        SceneConfig c = SceneConfig.INSTANCE;
        if (accent != c.themeAccent || surface != c.themeSurface || text != c.themeText) {
            accent = c.themeAccent;
            surface = c.themeSurface;
            text = c.themeText;
            palette = ThemePalette.of(accent, surface, text);
            Ui.TEXT = palette.text();
            Ui.MUTED = palette.muted();
            Ui.FAINT = palette.faint();
            Ui.ACCENT = palette.accent();
            Ui.ACCENT_HI = ThemePalette.mix(palette.accent(), palette.text(), 0.4);
            Ui.ACC0 = palette.accent();
            Ui.ACC1 = Ui.ACCENT_HI;
            Ui.BRAND = palette.accent();
        }

        return palette;
    }

    public static int surfaceTint(int color) {
        if (semantic(color)) {
            return color;
        }

        ThemePalette p = palette();
        double brightness = Math.max(color >> 16 & 0xFF, Math.max(color >> 8 & 0xFF, color & 0xFF)) / 255.0;
        int tinted = brightness < 0.12
            ? ThemePalette.mix(p.surface(), -16777216, 0.16)
            : ThemePalette.mix(p.surface(), p.accent(), Math.min(0.28, Math.max(0.0, (brightness - 0.12) * 0.5)));
        return color & 0xFF000000 | tinted & 16777215;
    }

    private static boolean semantic(int color) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        return r > 90 && r > g * 1.45 && r > b * 1.25;
    }

    public static int borderTint(int color) {
        return semantic(color) ? color : color & 0xFF000000 | ThemePalette.mix(palette().accent(), palette().text(), 0.36) & 16777215;
    }
}
