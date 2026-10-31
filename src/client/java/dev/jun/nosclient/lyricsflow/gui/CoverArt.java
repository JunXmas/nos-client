package dev.jun.nosclient.lyricsflow.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.jun.nosclient.lyricsflow.LyricsFlow;
import dev.jun.nosclient.lyricsflow.media.MediaManager;
import dev.jun.nosclient.lyricsflow.render.Gfx;
import dev.jun.nosclient.lyricsflow.util.Col;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class CoverArt {
   private static final Identifier ID = LyricsFlow.id("dynamic/cover");
   private static final Identifier ID_ROUND = LyricsFlow.id("dynamic/cover_round");
   private static long loadedSeq = -2L;
   private static boolean has;
   private static int w;
   private static int h;
   private static volatile int[] decoded;
   private static volatile long decodedSeq = -3L;
   private static int rw = 1;

   private CoverArt() {
   }

   public static Identifier id() {
      return ID;
   }

   public static boolean ready() {
      MediaManager mm = MediaManager.get();
      long seq = mm.coverSeq();
      if (seq != mm.songSeq()) {
         if (has && loadedSeq != seq) {
            release();
         }

         return false;
      } else if (seq == loadedSeq) {
         int[] px = decoded;
         if (px != null && decodedSeq == seq && !has) {
            upload(px);
         }

         return has;
      } else {
         loadedSeq = seq;
         release();
         decoded = null;
         byte[] bytes = mm.cover();
         if (bytes != null && bytes.length != 0) {
            Thread th = new Thread(() -> {
               try {
                  BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                  if (img == null) {
                     return;
                  }

                  int s = Math.min(img.getWidth(), img.getHeight());
                  int ox = (img.getWidth() - s) / 2;
                  int oy = (img.getHeight() - s) / 2;
                  int size = Math.min(256, s);
                  int[] out = new int[size * size + 1];
                  out[0] = size;

                  for (int y = 0; y < size; y++) {
                     for (int x = 0; x < size; x++) {
                        out[1 + y * size + x] = 0xFF000000 | img.getRGB(ox + x * s / size, oy + y * s / size) & 16777215;
                     }
                  }

                  decodedSeq = seq;
                  decoded = out;
               } catch (Throwable var11) {
               }
            }, "LyricsFlow cover");
            th.setDaemon(true);
            th.start();
            return false;
         } else {
            return false;
         }
      }
   }

   private static void upload(int[] px) {
      try {
         int size = px[0];
         NativeImage ni = new NativeImage(size, size, false);

         for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
               ni.setPixel(x, y, px[1 + y * size + x]);
            }
         }

         Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(() -> "lyricsflow cover", ni));
         int rs = Math.min(size, 96);
         NativeImage rn = new NativeImage(rs, rs, false);
         float c = rs / 2.0F;

         for (int y = 0; y < rs; y++) {
            for (int x = 0; x < rs; x++) {
               float d = (float)Math.hypot(x + 0.5F - c, y + 0.5F - c);
               float al = Math.max(0.0F, Math.min(1.0F, c - d));
               int src = px[1 + y * size / rs * size + x * size / rs];
               rn.setPixel(x, y, (int)(al * 255.0F) << 24 | src & 16777215);
            }
         }

         Minecraft.getInstance().getTextureManager().register(ID_ROUND, new DynamicTexture(() -> "lyricsflow cover round", rn));
         rw = rs;
         int s = size;
         h = s;
         w = s;
         has = true;
      } catch (Throwable t) {
         LyricsFlow.LOG.debug("Cover art failed", t);
         has = false;
      }
   }

   public static void drawRound(GuiGraphics g, float cx, float cy, float r, float a) {
      if (has) {
         float sc = Gfx.scale();
         int px = Math.max(2, Math.round(r * 2.0F * sc));
         g.pose().pushMatrix();
         g.pose().translate(cx, cy);
         g.pose().scale(1.0F / sc, 1.0F / sc);
         g.blit(RenderPipelines.GUI_TEXTURED, ID_ROUND, -px / 2, -px / 2, 0.0F, 0.0F, px, px, rw, rw, rw, rw, Col.alpha(-1, a));
         g.pose().popMatrix();
      }
   }

   private static void release() {
      if (has) {
         try {
            Minecraft.getInstance().getTextureManager().release(ID);
         } catch (Throwable var2) {
         }

         try {
            Minecraft.getInstance().getTextureManager().release(ID_ROUND);
         } catch (Throwable var1) {
         }
      }

      has = false;
   }

   public static void draw(GuiGraphics g, float x, float y, float size, float a) {
      if (has) {
         float sc = Gfx.scale();
         int px = Math.round(size * sc);
         g.pose().pushMatrix();
         g.pose().translate(x, y);
         g.pose().scale(1.0F / sc, 1.0F / sc);
         g.blit(RenderPipelines.GUI_TEXTURED, ID, 0, 0, 0.0F, 0.0F, px, px, w, h, w, h, Col.alpha(-1, a));
         g.pose().popMatrix();
      }
   }
}
