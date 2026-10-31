package dev.jun.nosclient.lyricsflow.app.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.jun.nosclient.lyricsflow.app.Core;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class Shapes {
   public static final int MAX_R = 512;
   private static final Map<Integer, Identifier> CIRCLES = new HashMap<>();
   private static boolean failed = false;

   private Shapes() {
   }

   public static boolean usable() {
      return !failed;
   }

   public static void fail() {
      failed = true;
   }

   public static Identifier circle(int r) {
      if (!failed && r >= 1) {
         if (r > 96) {
            r = Math.min(512, (r + 4) / 8 * 8);
         }

         Identifier id = CIRCLES.get(r);
         if (id != null) {
            return id;
         }

         try {
            int n = r * 2;
            BufferedImage img = new BufferedImage(n, n, 2);

            for (int y = 0; y < n; y++) {
               for (int x = 0; x < n; x++) {
                  int hit = 0;

                  for (int sy = 0; sy < 4; sy++) {
                     for (int sx = 0; sx < 4; sx++) {
                        float dx = x + (sx + 0.5F) / 4.0F - r;
                        float dy = y + (sy + 0.5F) / 4.0F - r;
                        if (dx * dx + dy * dy <= (float)r * r) {
                           hit++;
                        }
                     }
                  }

                  int a = Math.round(hit / 16.0F * 255.0F);
                  img.setRGB(x, y, a << 24 | 16777215);
               }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            NativeImage ni = NativeImage.read(out.toByteArray());
            id = Identifier.fromNamespaceAndPath("lyricsflow", "shape/c" + r);
            DynamicTexture tex = new DynamicTexture(() -> "lyricsflow_circle", ni);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            CIRCLES.put(r, id);
            return id;
         } catch (Throwable var11) {
            Core.LOG.warn("Formen als Textur gehen nicht, nutze einfache Variante", var11);
            failed = true;
            return null;
         }
      } else {
         return null;
      }
   }
}
