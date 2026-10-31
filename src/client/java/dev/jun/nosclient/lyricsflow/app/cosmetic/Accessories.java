package dev.jun.nosclient.lyricsflow.app.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import dev.jun.nosclient.lyricsflow.app.Core;
import dev.jun.nosclient.lyricsflow.render.Cosmetics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class Accessories {
   public static final String[] HATS = Cosmetics.IDS;
   public static final Identifier WHITE = Identifier.fromNamespaceAndPath("lyricsflow", "cosmetic/white");
   private static boolean whiteReady;
   private static boolean whiteFailed;
   private static final float[][] MESH = new float[HATS.length][];
   private static final boolean[] TRIED = new boolean[HATS.length];
   private static final int STRIDE = 14;

   private Accessories() {
   }

   private static float[] mesh(int i) {
      if (TRIED[i]) {
         return MESH[i];
      }

      TRIED[i] = true;

      try (InputStream in = Accessories.class.getResourceAsStream("/assets/lyricsflow/models/" + HATS[i] + ".bin")) {
         if (in != null) {
            ByteBuffer b = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            if (b.getInt() != 827149390) {
               return null;
            }

            int n = b.getInt();
            float[] m = new float[n * 14];

            for (int t = 0; t < n; t++) {
               for (int k = 0; k < 12; k++) {
                  m[t * 14 + k] = b.getFloat();
               }

               m[t * 14 + 12] = Float.intBitsToFloat(b.getInt());
               m[t * 14 + 13] = b.getInt();
            }

            MESH[i] = m;
            return MESH[i];
         } else {
            Object var10 = null;
            return (float[])var10;
         }
      } catch (Throwable var9) {
         Core.LOG.warn("Hut-Modell fehlt: " + HATS[i], var9);
         return MESH[i];
      }
   }

   public static boolean whiteTex() {
      return white();
   }

   private static boolean white() {
      if (whiteReady) {
         return true;
      }

      if (whiteFailed) {
         return false;
      }

      try {
         BufferedImage img = new BufferedImage(4, 4, 2);

         for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
               img.setRGB(x, y, -1);
            }
         }

         ByteArrayOutputStream out = new ByteArrayOutputStream();
         ImageIO.write(img, "png", out);
         Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(() -> "lyricsflow_white", NativeImage.read(out.toByteArray())));
         whiteReady = true;
      } catch (Throwable var3) {
         whiteFailed = true;
      }

      return whiteReady;
   }

   public static void render(Minecraft mc, MultiBufferSource buffers, Matrix4f base, Vec3 cam, float pt) {
   }
}
