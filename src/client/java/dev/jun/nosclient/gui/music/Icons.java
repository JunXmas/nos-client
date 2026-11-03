package dev.jun.nosclient.gui.music;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.render.Gfx;
import dev.jun.nosclient.util.Col;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class Icons {
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private Icons() {}

    private static Identifier tex(String name) {
        return CACHE.computeIfAbsent(
                name, n -> ClientResources.id("textures/gui/icons/" + n + ".png"));
    }

    public static void draw(GuiGraphics g, String name, float cx, float cy, float size, int color) {
        if (Col.a(color) >= 4) {
            float s = Gfx.scale();
            int px = Math.max(1, Math.round(size * s));
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().scale(1.0F / s, 1.0F / s);
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    tex(name),
                    -px / 2,
                    -px / 2,
                    0.0F,
                    0.0F,
                    px,
                    px,
                    64,
                    64,
                    64,
                    64,
                    color);
            g.pose().popMatrix();
        }
    }
}
