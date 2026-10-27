package dev.jun.nosclient.hud;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.gui.Glass;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.theme.Theme;
import dev.jun.nosclient.theme.ThemePalette;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;

@Environment(EnvType.CLIENT)
public final class HudSurface {
    private HudSurface() {
    }

    public static void draw(GuiGraphics g, int x, int y, int w, int h, float background, float foreground) {
        ThemePalette p = Theme.palette();
        int style = SceneConfig.INSTANCE.hudStyle;
        if (style != 1) {
            if (background > 0.01F) {
                Glass.fill(g, x, y, w, h, Ui.alpha(p.surface(), background));
            }

            Glass.outline(g, x, y, w, h, Ui.alpha(p.accent(), foreground * (style == 2 ? 0.5F : 0.12F) * Math.min(1.0F, background * 2.0F)));
        }
    }
}
