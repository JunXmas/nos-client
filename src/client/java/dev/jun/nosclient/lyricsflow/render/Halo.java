package dev.jun.nosclient.lyricsflow.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jun.nosclient.lyricsflow.LyricsFlow;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public final class Halo {
   private static final Identifier GLOW = LyricsFlow.id("textures/gui/shape/glow.png");
   private static final int LIGHT = 15728880;

   private Halo() {
   }

   public static void draw(Matrix4f m, float x0, float y0, float x1, float y1, int argb, MultiBufferSource buf) {
      if (argb >>> 24 >= 3 && buf != null) {
         VertexConsumer vc = buf.getBuffer(RenderTypes.eyes(GLOW));
         int ov = OverlayTexture.NO_OVERLAY;
         v(vc, m, x0, y0, 0.0F, 0.0F, argb, ov);
         v(vc, m, x0, y1, 0.0F, 1.0F, argb, ov);
         v(vc, m, x1, y1, 1.0F, 1.0F, argb, ov);
         v(vc, m, x1, y0, 1.0F, 0.0F, argb, ov);
         v(vc, m, x1, y0, 1.0F, 0.0F, argb, ov);
         v(vc, m, x1, y1, 1.0F, 1.0F, argb, ov);
         v(vc, m, x0, y1, 0.0F, 1.0F, argb, ov);
         v(vc, m, x0, y0, 0.0F, 0.0F, argb, ov);
      }
   }

   private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float u, float vv, int argb, int ov) {
      vc.addVertex(m, x, y, 0.0F).setColor(argb).setUv(u, vv).setOverlay(ov).setLight(15728880).setNormal(0.0F, 1.0F, 0.0F);
   }
}
