package dev.jun.nosclient.lyricsflow.gui;

import dev.jun.nosclient.lyricsflow.LyricsFlow;
import dev.jun.nosclient.lyricsflow.render.Gfx;
import dev.jun.nosclient.lyricsflow.util.Col;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class Icons {
   private static final Map<String, Identifier> CACHE = new HashMap<>();

   private Icons() {
   }

   private static Identifier tex(String name) {
      return CACHE.computeIfAbsent(name, n -> LyricsFlow.id("textures/gui/icons/" + n + ".png"));
   }

   public static void draw(GuiGraphics g, String name, float cx, float cy, float size, int color) {
      if (Col.a(color) >= 4) {
         float s = Gfx.scale();
         int px = Math.max(1, Math.round(size * s));
         g.pose().pushMatrix();
         g.pose().translate(cx, cy);
         g.pose().scale(1.0F / s, 1.0F / s);
         g.blit(RenderPipelines.GUI_TEXTURED, tex(name), -px / 2, -px / 2, 0.0F, 0.0F, px, px, 64, 64, 64, 64, color);
         g.pose().popMatrix();
      }
   }
}
