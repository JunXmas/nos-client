package dev.jun.nosclient.lyricsflow.render;

import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.util.Col;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public final class HotbarStyle {
   private static float selX = Float.NaN;

   private HotbarStyle() {
   }

   public static boolean draw(GuiGraphics g, Identifier id, int x, int y, int w, int h) {
      if (ConfigManager.get().enabled && ConfigManager.get().customHotbar && "minecraft".equals(id.getNamespace())) {
         String path = id.getPath();
         int custom = ConfigManager.get().hotbarColor;
         int accent = custom != 0 ? custom | 0xFF000000 : -6325530;
         int barCol = ConfigManager.get().hotbarBarColor;
         int bar = barCol != 0 ? -671088640 | barCol & 16777215 : -668851164;
         switch (path) {
            case "hud/hotbar":
               Gfx.round(g, x, y, w, h, 5.5F, bar);
               return true;
            case "hud/hotbar_selection":
               float sx = x + 3.0F;
               float sy = y + 3.0F;
               float s = 18.0F;
               Gfx.round(g, sx, sy, s, s, 3.0F, Col.alpha(accent, 0.22F));
               Gfx.roundOutline(g, sx, sy, s, s, 3.0F, 1.0F, Col.alpha(accent, 0.95F));
               return true;
            case "hud/hotbar_offhand_left":
            case "hud/hotbar_offhand_right":
               float bx = path.endsWith("left") ? x : x + w - 22;
               float by = y + 1;
               Gfx.round(g, bx, by - 1.0F, 22.0F, 22.0F, 5.5F, bar);
               return true;
            default:
               return false;
         }
      } else {
         return false;
      }
   }
}
