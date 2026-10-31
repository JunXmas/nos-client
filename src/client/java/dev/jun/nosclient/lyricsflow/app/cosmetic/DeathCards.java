package dev.jun.nosclient.lyricsflow.app.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jun.nosclient.lyricsflow.app.Core;
import dev.jun.nosclient.lyricsflow.app.sky.SkyTextures;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class DeathCards {
   static final float T_END = 4.2F;
   static final float[] TT = new float[9001];
   private static final int N = 8;
   private static final float DASH0 = 1.15F;
   private static final float GAP = 0.1F;
   private static final float DUR = 0.14F;
   private static final float TS = 2.05F;
   private static final float FIN0 = 2.25F;
   private static final float FIN1 = 2.75F;
   private static final float DY = 2.45F;
   private static final int[] SLASH_PX;
   private static final int[][] SLICES;
   private static final float[][] PARTS;
   private static final float[] A;
   private static final float[] B;
   private static final float[] S0;
   private static final float[][] LAND;
   private static final float[] C;
   private static final float[] C2;
   private static final float CW = 0.21F;
   private static final float CH = 0.29F;
   private static final float CT = 0.006F;
   static final Identifier CARDS;
   private static final int AW = 512;
   private static final int AH = 256;
   private static final int TW = 80;
   private static final int TH = 112;
   private static boolean cardsReady;
   private static boolean cardsFailed;

   private DeathCards() {
   }

   static Object create(DeathFx.Fx f, Random r) {
      DeathCards.State s = new DeathCards.State();
      s.face[0] = 1;
      s.face[1] = 2;

      for (int i = 2; i < 8; i++) {
         s.face[i] = 3 + r.nextInt(4);
      }

      for (int j = 0; j < SLICES.length; j++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         float sp = j == 0 ? 0.3F + r.nextFloat() * 0.3F : 0.9F + r.nextFloat() * 1.3F;
         float ax = r.nextFloat() - 0.5F;
         float ay = r.nextFloat() - 0.5F;
         float az = r.nextFloat() - 0.5F;
         float al = (float)Math.sqrt(ax * ax + ay * ay + az * az) + 1.0E-4F;
         s.pieces[j] = new float[]{
            (float)Math.cos(a) * sp,
            j == 0 ? 0.4F : 1.4F + j * 0.45F + r.nextFloat() * 0.8F,
            (float)Math.sin(a) * sp,
            ax / al,
            ay / al,
            az / al,
            j == 0 ? 2.5F : 5.0F + r.nextFloat() * 5.0F
         };
      }

      for (int i = 0; i < s.hits.length; i++) {
         s.hits[i] = new float[]{
            (float)r.nextGaussian() * 0.5F,
            0.4F + r.nextFloat() * 1.2F,
            (float)r.nextGaussian() * 0.5F,
            1.8F + r.nextFloat() * 2.4F,
            0.04F + r.nextFloat() * 0.06F,
            r.nextFloat() < 0.4F ? 1.0F : 0.0F
         };
      }

      for (int i = 0; i < s.burst.length; i++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         float sp = 1.5F + r.nextFloat() * 3.0F;
         s.burst[i] = new float[]{
            (float)Math.cos(a) * sp,
            1.0F + r.nextFloat() * 3.5F,
            (float)Math.sin(a) * sp,
            0.04F + r.nextFloat() * 0.08F,
            r.nextFloat() * 14.0F,
            r.nextFloat() < 0.35F ? 1.0F : 0.0F
         };
      }

      int[] chipCols = new int[]{13111342, 1381653, 15921906, 1986504, 2067002};

      for (int i = 0; i < s.chips.length; i++) {
         double a = r.nextDouble() * Math.PI * 2.0;
         float sp = 1.6F + r.nextFloat() * 2.4F;
         s.chips[i] = new float[]{
            (float)Math.cos(a) * sp,
            2.5F + r.nextFloat() * 3.0F,
            (float)Math.sin(a) * sp,
            6.0F + r.nextFloat() * 14.0F,
            Float.intBitsToFloat(chipCols[r.nextInt(chipCols.length)])
         };
      }

      for (int i = 0; i < s.sparks.length; i++) {
         s.sparks[i] = new float[]{
            i < 12 ? 0.1F + r.nextFloat() * 0.15F : 2.75F + r.nextFloat() * 0.1F,
            r.nextFloat() * 6.283F,
            0.6F + r.nextFloat() * 1.8F,
            0.3F + r.nextFloat() * 1.5F,
            0.05F + r.nextFloat() * 0.08F
         };
      }

      for (int i = 0; i < s.embers.length; i++) {
         s.embers[i] = new float[]{2.95F + r.nextFloat() * 0.5F, (r.nextFloat() - 0.5F) * 0.4F, (r.nextFloat() - 0.5F) * 0.4F, 0.6F + r.nextFloat() * 1.2F};
      }

      return s;
   }

   private static float omega(float t) {
      float k = Math.max(0.0F, t - 0.6F);
      return 1.3F * t + 6.0F * k * k;
   }

   private static void orbit(int i, float t, boolean flipped, float[] o) {
      double ang = i * Math.PI * 2.0 / 8.0 + omega(t) + (flipped ? Math.PI : 0.0);
      float r = 1.6F - 0.15F * DeathFx.sm(0.9F, 1.2F, t);
      o[0] = (float)Math.cos(ang) * r;
      o[1] = 1.05F + 0.2F * (float)Math.sin(ang * 2.0 + t * 3.0F);
      o[2] = (float)Math.sin(ang) * r;
   }

   private static float appear(int i) {
      return 0.2F + i * 0.04F;
   }

   private static int flyPos(int i, float t, float[] o) {
      float ts = 1.15F + i * 0.1F;
      if (t < appear(i)) {
         o[0] = 0.0F;
         o[1] = 2.45F + i * 0.012F;
         o[2] = 0.0F;
         return 0;
      } else if (t < ts) {
         orbit(i, t, false, o);
         float k = DeathFx.eo((t - appear(i)) / 0.45F);
         o[0] = DeathFx.lerp(0.0F, o[0], k);
         o[1] = DeathFx.lerp(2.45F + i * 0.012F, o[1], k);
         o[2] = DeathFx.lerp(0.0F, o[2], k);
         return 1;
      } else {
         orbit(i, ts, false, A);
         float cy = SLASH_PX[i] * 0.05859375F;
         if (t < ts + 0.14F) {
            float k = (t - ts) / 0.14F * 2.0F;
            o[0] = A[0] + (0.0F - A[0]) * k;
            o[1] = A[1] + (cy - A[1]) * k;
            o[2] = A[2] + (0.0F - A[2]) * k;
            return 2;
         } else {
            float px = -A[0];
            float py = 2.0F * cy - A[1];
            float pz = -A[2];
            orbit(i, t, true, B);
            float k = DeathFx.eo((t - ts - 0.14F) / 0.3F);
            o[0] = DeathFx.lerp(px, B[0], k);
            o[1] = DeathFx.lerp(py, B[1], k);
            o[2] = DeathFx.lerp(pz, B[2], k);
            return 1;
         }
      }
   }

   private static boolean card(int i, float t, float[] o) {
      if (t < 2.25F) {
         int st = flyPos(i, t, o);
         o[3] = st == 0 ? 0.3F * i : t * (st == 2 ? 45.0F : 28.0F) + i * 1.3F;
         o[4] = st == 0 ? 0.0F : 0.25F * (float)Math.sin(i * 1.7F + t * 2.0F);
         o[5] = st == 0 ? DeathFx.sm(0.08F, 0.22F, t) : 1.0F;
         return true;
      } else {
         flyPos(i, 2.249F, S0);
         float a = t - 2.25F;
         if (i < 2) {
            float[] L = LAND[i];
            float k = DeathFx.cl(a / 0.5F);
            float kx = DeathFx.sm(0.0F, 1.0F, k);
            o[0] = DeathFx.lerp(S0[0], L[0], kx);
            o[2] = DeathFx.lerp(S0[2], L[2], kx);
            o[1] = DeathFx.lerp(S0[1], L[1], k * k) + (float)Math.sin(Math.PI * k) * 0.7F * (1.0F - k * 0.3F);
            o[3] = L[3] + (1.0F - k) * (1.0F - k) * 14.0F;
            o[4] = 0.0F;
            o[5] = 1.0F - DeathFx.sm(3.7F, 4.1F, t);
            return o[5] > 0.01F;
         } else {
            float l = (float)Math.sqrt(S0[0] * S0[0] + S0[2] * S0[2]) + 1.0E-4F;
            float vx = S0[0] / l * 3.2F;
            float vz = S0[2] / l * 3.2F;
            float vy = 2.2F + i * 0.15F;
            float land = (vy + (float)Math.sqrt(vy * vy + 18.0F * Math.max(0.0F, S0[1] - 0.012F))) / 9.0F;
            float aa = Math.min(a, land);
            o[0] = S0[0] + vx * aa;
            o[2] = S0[2] + vz * aa;
            o[1] = Math.max(0.012F + i * 0.002F, S0[1] + vy * aa - 4.5F * aa * aa);
            o[3] = 63.0F + i * 1.3F + aa * 20.0F;
            o[4] = a < land ? (float)Math.sin(a * 9.0F + i) * 0.6F : 0.0F;
            o[5] = 1.0F - DeathFx.sm(3.2F, 3.7F, t);
            return o[5] > 0.01F;
         }
      }
   }

   static void draw(Minecraft mc, MultiBufferSource buffers, Matrix4f base, Vec3 cam, DeathFx.Fx f, float t) {
      if (f.extra instanceof DeathCards.State s && Accessories.whiteTex() && cardsTex()) {
         Matrix4f m = new Matrix4f(base)
            .translate((float)(f.ox - cam.x), (float)(f.oy - cam.y), (float)(f.oz - cam.z))
            .rotateY(-f.yaw);
         int light = DeathFx.light(mc, f.ox, f.oy + 1.0, f.oz);
         boolean firstPerson = f.self && mc.options.getCameraType().isFirstPerson();
         VertexConsumer g = buffers.getBuffer(RenderTypes.debugQuads());

         for (int i = 0; i < 8; i++) {
            if (!(t < appear(i) + 0.05F) && !(t > 2.75F)) {
               boolean dash = t >= 1.15F + i * 0.1F && t < 1.15F + i * 0.1F + 0.14F;
               int col = dash ? 16734780 : 16765802;
               card(i, t, C);

               for (int k = 1; k <= 6; k++) {
                  card(i, t - k * 0.014F, C2);
                  float dx = C2[0] - C[0];
                  float dz = C2[2] - C[2];
                  float speed = (float)Math.sqrt(dx * dx + dz * dz) / 0.014F;
                  float a = (dash ? 0.75F : 0.4F) * (1.0F - k / 7.0F) * DeathFx.sm(2.0F, 7.0F, speed);
                  float w0 = 0.2F * (1.0F - (k - 1) / 7.0F);
                  float w1 = 0.2F * (1.0F - k / 7.0F);
                  ribbon(g, m, C[0], C[1], C[2], C2[0], C2[1], C2[2], w0, w1, col, a, a * 0.6F);
                  System.arraycopy(C2, 0, C, 0, 3);
               }
            }
         }

         if (t < 2.2F && !firstPerson) {
            for (int ix = 0; ix < 8; ix++) {
               float tc = 1.15F + ix * 0.1F + 0.07F;
               float age = t - tc;
               if (!(age < 0.0F) && !(age > 0.6F)) {
                  orbit(ix, 1.15F + ix * 0.1F, false, A);
                  float l = (float)Math.sqrt(A[0] * A[0] + A[2] * A[2]) + 1.0E-4F;
                  float dx = -A[0] / l;
                  float dz = -A[2] / l;
                  float cy = SLASH_PX[ix] * 0.05859375F;
                  float a = (1.0F - age / 0.6F) * (t > 2.05F ? 1.0F - DeathFx.sm(2.05F, 2.2F, t) : 1.0F);
                  float len = 0.42F;
                  float w = 0.045F * (1.0F - age);
                  cross(g, m, -dx * len, cy, -dz * len, dx * len, cy, dz * len, w * 3.5F, 16719936, a * 0.35F);
                  cross(g, m, -dx * len, cy, -dz * len, dx * len, cy, dz * len, w, 16773320, a);
               }
            }
         }

         float ar = (t - 2.75F) / 0.6F;
         if (ar > 0.0F && ar < 1.0F) {
            float rr = 0.2F + DeathFx.eo(ar) * 1.6F;
            ringFlat(g, m, 0.0F, 0.03F, 0.0F, rr * 0.85F, rr, 16765802, 0.9F * (1.0F - ar));
         }

         if (t > 2.7F) {
            float a = DeathFx.sm(2.7F, 2.85F, t) * (1.0F - DeathFx.sm(3.7F, 4.1F, t)) * (0.32F + 0.06F * (float)Math.sin(t * 6.0F));
            DeathFx.disc(g, m, 0.0F, 0.015F, 0.0F, 0.0F, 0.75F, 16762960, a, 16762960, 0.0F, 28);
         }

         float as = (t - 2.05F) / 0.35F;
         if (as > 0.0F && as < 1.0F) {
            ringFlat(g, m, 0.0F, 1.1F, 0.0F, 0.2F + DeathFx.eo(as) * 1.6F, 0.3F + DeathFx.eo(as) * 1.75F, 16724048, 0.85F * (1.0F - as));
         }

         VertexConsumer w = buffers.getBuffer(RenderTypes.entityCutoutNoCull(Accessories.WHITE));
         if (t >= 2.05F && !firstPerson) {
            for (int j = 0; j < SLICES.length; j++) {
               Matrix4f pm = pieceMatrix(m, s, j, t);
               if (pm != null) {
                  float glow = 0.75F + 0.25F * (float)Math.sin(t * 14.0F + j);
                  int capCol = DeathFx.mix(7997206, 16723274, glow);

                  for (float[] p : PARTS) {
                     caps(w, pm, p, SLICES[j][0], SLICES[j][1], capCol);
                  }
               }
            }
         }

         for (int ixx = 0; ixx < 8; ixx++) {
            float tc = 1.15F + ixx * 0.1F + 0.07F;
            float a0 = t - tc;
            if (!(a0 <= 0.0F) && !(a0 > 0.8F) && !firstPerson) {
               orbit(ixx, 1.15F + ixx * 0.1F, false, A);
               float l = (float)Math.sqrt(A[0] * A[0] + A[2] * A[2]) + 1.0E-4F;
               float dx = -A[0] / l;
               float dz = -A[2] / l;
               float cy = SLASH_PX[ixx] * 0.05859375F;

               for (int k = 0; k < 7; k++) {
                  float[] h = s.hits[ixx * 7 + k];
                  float vx = dx * h[3] + h[0];
                  float vy = h[1];
                  float vz = dz * h[3] + h[2];
                  float x = vx * a0;
                  float y = cy + vy * a0 - 6.0F * a0 * a0;
                  float z = vz * a0;
                  if (!(y < 0.0F)) {
                     float sz = h[4] * (1.0F - a0 / 0.8F * 0.5F);
                     boolean hot = h[5] > 0.5F;
                     DeathFx.cube(
                        w,
                        new Matrix4f(m).translate(x, y, z).rotateXYZ(a0 * 9.0F, a0 * 7.0F, 0.0F),
                        -sz / 2.0F,
                        -sz / 2.0F,
                        -sz / 2.0F,
                        sz / 2.0F,
                        sz / 2.0F,
                        sz / 2.0F,
                        hot ? -54715 : -12974576,
                        hot ? 15728880 : light
                     );
                  }
               }
            }
         }

         float ab = t - 2.05F;
         if (ab > 0.0F && ab < 1.6F) {
            for (float[] b : s.burst) {
               float x = b[0] * ab;
               float y = 1.1F + b[1] * ab - 6.0F * ab * ab;
               float z = b[2] * ab;
               if (!(y < 0.0F)) {
                  float sz = b[3] * (1.0F - ab / 1.6F * 0.5F);
                  boolean hot = b[5] > 0.5F;
                  DeathFx.cube(
                     w,
                     new Matrix4f(m).translate(x, y, z).rotateXYZ(b[4] * ab, b[4] * ab * 0.6F, 0.0F),
                     -sz / 2.0F,
                     -sz / 2.0F,
                     -sz / 2.0F,
                     sz / 2.0F,
                     sz / 2.0F,
                     sz / 2.0F,
                     hot ? -54715 : -12974576,
                     hot ? 15728880 : light
                  );
               }
            }
         }

         if (ab > 0.0F && t < 3.8F) {
            for (float[] c : s.chips) {
               float vy = c[1];
               float land = (vy + (float)Math.sqrt(vy * vy + 19.44F)) / 9.0F;
               float aa = Math.min(ab, land);
               float x = c[0] * aa;
               float z = c[2] * aa;
               float y = Math.max(0.02F, 1.1F + vy * aa - 4.5F * aa * aa);
               float sc = 1.0F - DeathFx.sm(3.3F, 3.8F, t);
               Matrix4f cm = new Matrix4f(m)
                  .translate(x, y, z)
                  .rotateXYZ(ab < land ? c[3] * aa : 0.0F, c[3] * aa * 0.5F, ab < land ? c[3] * aa * 0.3F : 0.0F)
                  .scale(sc);
               chip(w, cm, Float.floatToRawIntBits(c[4]) | 0xFF000000, light);
            }
         }

         VertexConsumer cv = buffers.getBuffer(RenderTypes.entityCutoutNoCull(CARDS));

         for (int ixxx = 0; ixxx < 8; ixxx++) {
            if (card(ixxx, t, C) && !(C[5] <= 0.01F)) {
               Matrix4f cm = new Matrix4f(m).translate(C[0], C[1], C[2]).rotateY(C[3]).rotateX(C[4]).scale(C[5]);
               cardQuad(cv, cm, s.face[ixxx]);
            }
         }

         if (!firstPerson && f.skin != null) {
            VertexConsumer sk = buffers.getBuffer(RenderTypes.entityCutoutNoCull(f.skin));
            int bl = DeathFx.light(mc, f.ox, f.oy + 1.2, f.oz);
            if (t < 2.05F) {
               float e = (float)(0.8 * Math.exp(-t * 10.0F));
               float ry = 0.0F;
               float rx = -0.12F * (float)Math.sin(DeathFx.cl(t / 0.35F) * Math.PI);

               for (int ixxxx = 0; ixxxx < 8; ixxxx++) {
                  float tc = 1.15F + ixxxx * 0.1F + 0.07F;
                  if (!(t < tc)) {
                     float jx = (float)Math.exp(-(t - tc) * 12.0F);
                     e = Math.max(e, jx);
                     ry += ((ixxxx & 1) == 0 ? 0.22F : -0.22F) * jx;
                     rx += 0.07F * jx;
                  }
               }

               int tint = tint(Math.min(1.0F, e));
               float cy = 1.0546875F;
               Matrix4f bm = new Matrix4f(m).translate(0.0F, cy, 0.0F).rotateY(ry).rotateX(rx).translate(0.0F, -cy, 0.0F);

               for (int jx = 0; jx < SLICES.length; jx++) {
                  for (float[] p : PARTS) {
                     slice(sk, bm, p, SLICES[jx][0], SLICES[jx][1], tint, bl);
                  }
               }
            } else {
               for (int jx = 0; jx < SLICES.length; jx++) {
                  Matrix4f pm = pieceMatrix(m, s, jx, t);
                  if (pm != null) {
                     float burn = DeathFx.sm(2.95F, 3.45F, t);
                     int tint = tint(Math.min(1.0F, 0.35F * (float)Math.exp(-(t - 2.05F) * 5.0F) + burn));

                     for (float[] p : PARTS) {
                        slice(sk, pm, p, SLICES[jx][0], SLICES[jx][1], tint, bl);
                     }
                  }
               }
            }
         }

         if (SkyTextures.spritesReady()) {
            VertexConsumer sp = buffers.getBuffer(RenderTypes.text(SkyTextures.SPRITES));
            float rx = DeathFx.AX[0];
            float ry = DeathFx.AX[1];
            float rz = DeathFx.AX[2];
            float ux = DeathFx.AX[3];
            float uy = DeathFx.AX[4];
            float uz = DeathFx.AX[5];
            float ad = (t - 0.05F) / 0.5F;
            if (ad > 0.0F && ad < 1.0F) {
               DeathFx.sprite(sp, base, f, cam, 0.0F, 2.45F, 0.0F, 1.6F * DeathFx.eo(ad), 2, 16765802, (1.0F - ad) * 0.9F, rx, ry, rz, ux, uy, uz);
            }

            for (int ixxxxx = 0; ixxxxx < 8; ixxxxx++) {
               if (card(ixxxxx, t, C) && !(C[5] <= 0.01F)) {
                  DeathFx.sprite(
                     sp, base, f, cam, C[0], C[1], C[2], 0.75F * C[5], 2, ixxxxx < 2 && t > 2.75F ? 16769162 : 16762960, 0.3F * C[5], rx, ry, rz, ux, uy, uz
                  );
               }
            }

            for (int ixxxxxx = 0; ixxxxxx < 8; ixxxxxx++) {
               float age = t - (1.15F + ixxxxxx * 0.1F + 0.07F);
               if (!(age < 0.0F) && !(age > 0.25F) && !firstPerson) {
                  DeathFx.sprite(
                     sp, base, f, cam, 0.0F, SLASH_PX[ixxxxxx] * 0.05859375F, 0.0F, 0.6F + age * 4.0F, 2, 16773320, 1.0F - age / 0.25F, rx, ry, rz, ux, uy, uz
                  );
               }
            }

            if (t >= 2.05F && t < 2.45F) {
               float a = (t - 2.05F) / 0.4F;
               DeathFx.sprite(sp, base, f, cam, 0.0F, 1.1F, 0.0F, 1.2F + a * 3.0F, 2, 16726613, 0.9F * (1.0F - a), rx, ry, rz, ux, uy, uz);
            }

            for (float[] sk : s.sparks) {
               float a = (t - sk[0]) / 0.9F;
               if (!(a <= 0.0F) && !(a >= 1.0F)) {
                  boolean deck = sk[0] < 1.0F;
                  float cx = 0.0F;
                  float cy = deck ? 2.45F : 0.05F;
                  float cz = 0.0F;
                  float rad = sk[2] * DeathFx.eo(a) * (deck ? 0.8F : 1.0F);
                  DeathFx.sprite(
                     sp,
                     base,
                     f,
                     cam,
                     cx + (float)Math.cos(sk[1]) * rad,
                     cy + sk[3] * a * (deck ? 0.6F : 1.0F),
                     cz + (float)Math.sin(sk[1]) * rad,
                     sk[4] * 2.4F,
                     2,
                     16765802,
                     (float)Math.sin(a * Math.PI),
                     rx,
                     ry,
                     rz,
                     ux,
                     uy,
                     uz
                  );
               }
            }

            if (!firstPerson) {
               for (int kx = 0; kx < s.embers.length; kx++) {
                  float[] e = s.embers[kx];
                  float a = (t - e[0]) / 0.9F;
                  if (!(a <= 0.0F) && !(a >= 1.0F)) {
                     piecePos(s, kx % SLICES.length, Math.min(t, 3.45F), A);
                     DeathFx.sprite(
                        sp,
                        base,
                        f,
                        cam,
                        A[0] + e[1],
                        A[1] + e[3] * a,
                        A[2] + e[2],
                        0.14F * (1.0F - a * 0.5F),
                        2,
                        (kx & 1) == 0 ? 16720960 : 16756800,
                        (float)Math.sin(a * Math.PI),
                        rx,
                        ry,
                        rz,
                        ux,
                        uy,
                        uz
                     );
                  }
               }
            }
         }
      }
   }

   private static int tint(float e) {
      int gb = Math.round(255.0F * (1.0F - 0.65F * e));
      return -65536 | gb << 8 | gb;
   }

   private static float restY(int j) {
      return (SLICES[j][0] + SLICES[j][1]) / 2.0F * 0.05859375F;
   }

   private static void piecePos(DeathCards.State s, int j, float t, float[] o) {
      float[] p = s.pieces[j];
      float a = Math.max(0.0F, t - 2.05F);
      float y0 = restY(j);
      float yl = 0.14F;
      float land = (p[1] + (float)Math.sqrt(p[1] * p[1] + 18.0F * Math.max(0.0F, y0 - yl))) / 9.0F;
      float aa = Math.min(a, land);
      float slide = a > land ? (1.0F - (float)Math.exp(-(a - land) * 4.0F)) / 4.0F * 0.35F : 0.0F;
      o[0] = p[0] * (aa + slide);
      o[2] = p[2] * (aa + slide);
      o[1] = a < land ? y0 + p[1] * aa - 4.5F * aa * aa : yl;
   }

   private static Matrix4f pieceMatrix(Matrix4f m, DeathCards.State s, int j, float t) {
      float sc = 1.0F - DeathFx.sm(3.0F, 3.5F, t);
      if (sc <= 0.01F) {
         return null;
      }

      float[] p = s.pieces[j];
      piecePos(s, j, t, B);
      float a = t - 2.05F;
      float y0 = restY(j);
      float land = (p[1] + (float)Math.sqrt(p[1] * p[1] + 18.0F * Math.max(0.0F, y0 - 0.14F))) / 9.0F;
      float ang = p[6] * Math.min(a, land) + p[6] * 0.15F * (a > land ? 1.0F - (float)Math.exp(-(a - land) * 5.0F) : 0.0F);
      return new Matrix4f(m).translate(B[0], B[1], B[2]).rotate(ang, p[3], p[4], p[5]).scale(sc).translate(0.0F, -y0, 0.0F);
   }

   private static void slice(VertexConsumer vc, Matrix4f m, float[] p, int lo, int hi, int col, int light) {
      float p0 = p[1];
      float p1 = p[1] + p[3];
      float a = Math.max(lo, p0);
      float b = Math.min(hi, p1);
      if (!(a >= b)) {
         Matrix4f pm = new Matrix4f(m).translate(p[0] * 0.05859375F, (p0 + p[3] / 2.0F) * 0.05859375F, 0.0F);
         box(vc, pm, p[2], p[3], p[4], 0.0F, (int)p[5], (int)p[6], a - p0, b - p0, col, light);
         box(vc, pm, p[2], p[3], p[4], p[9], (int)p[7], (int)p[8], a - p0, b - p0, col, light);
      }
   }

   private static void caps(VertexConsumer w, Matrix4f m, float[] p, int lo, int hi, int col) {
      float p0 = p[1];
      float p1 = p[1] + p[3];
      float a = Math.max(lo, p0);
      float b = Math.min(hi, p1);
      if (!(a >= b)) {
         float hx = p[2] / 2.0F * 0.05859375F * 0.97F;
         float hz = p[4] / 2.0F * 0.05859375F * 0.97F;
         float x = p[0] * 0.05859375F;
         if (b < p1) {
            float y = b * 0.05859375F;
            DeathFx.face(w, m, x - hx, y, -hz, x + hx, y, -hz, x + hx, y, hz, x - hx, y, hz, 0.5F, 0.5F, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F, col, 15728880);
         }

         if (a > p0) {
            float y = a * 0.05859375F;
            DeathFx.face(w, m, x - hx, y, hz, x + hx, y, hz, x + hx, y, -hz, x - hx, y, -hz, 0.5F, 0.5F, 0.5F, 0.5F, 0.0F, -1.0F, 0.0F, col, 15728880);
         }
      }
   }

   private static void box(VertexConsumer vc, Matrix4f m, float w, float h, float d, float infl, int u, int v, float y0, float y1, int col, int light) {
      float hx = (w / 2.0F + infl) * 0.05859375F;
      float hz = (d / 2.0F + infl) * 0.05859375F;
      float yb = (y0 - h / 2.0F) * 0.05859375F - (y0 <= 0.0F ? infl * 0.05859375F : 0.0F);
      float yt = (y1 - h / 2.0F) * 0.05859375F + (y1 >= h ? infl * 0.05859375F : 0.0F);
      float k = 0.015625F;
      float vt = (v + d + (h - y1)) * k;
      float vb = (v + d + (h - y0)) * k;
      DeathFx.face(vc, m, -hx, yt, hz, hx, yt, hz, hx, yb, hz, -hx, yb, hz, (u + d) * k, vt, (u + d + w) * k, vb, 0.0F, 0.0F, 1.0F, col, light);
      DeathFx.face(
         vc,
         m,
         hx,
         yt,
         -hz,
         -hx,
         yt,
         -hz,
         -hx,
         yb,
         -hz,
         hx,
         yb,
         -hz,
         (u + 2.0F * d + w) * k,
         vt,
         (u + 2.0F * d + 2.0F * w) * k,
         vb,
         0.0F,
         0.0F,
         -1.0F,
         col,
         light
      );
      DeathFx.face(vc, m, -hx, yt, -hz, -hx, yt, hz, -hx, yb, hz, -hx, yb, -hz, u * k, vt, (u + d) * k, vb, -1.0F, 0.0F, 0.0F, col, light);
      DeathFx.face(vc, m, hx, yt, hz, hx, yt, -hz, hx, yb, -hz, hx, yb, hz, (u + d + w) * k, vt, (u + 2.0F * d + w) * k, vb, 1.0F, 0.0F, 0.0F, col, light);
      if (y1 >= h) {
         DeathFx.face(vc, m, -hx, yt, -hz, hx, yt, -hz, hx, yt, hz, -hx, yt, hz, (u + d) * k, v * k, (u + d + w) * k, (v + d) * k, 0.0F, 1.0F, 0.0F, col, light);
      }

      if (y0 <= 0.0F) {
         DeathFx.face(
            vc,
            m,
            -hx,
            yb,
            hz,
            hx,
            yb,
            hz,
            hx,
            yb,
            -hz,
            -hx,
            yb,
            -hz,
            (u + d + w) * k,
            v * k,
            (u + d + 2.0F * w) * k,
            (v + d) * k,
            0.0F,
            -1.0F,
            0.0F,
            col,
            light
         );
      }
   }

   private static void cardQuad(VertexConsumer vc, Matrix4f m, int tile) {
      float[] f = uv(tile);
      float[] b = uv(0);
      DeathFx.face(
         vc,
         m,
         -0.21F,
         0.006F,
         -0.29F,
         0.21F,
         0.006F,
         -0.29F,
         0.21F,
         0.006F,
         0.29F,
         -0.21F,
         0.006F,
         0.29F,
         f[0],
         f[1],
         f[2],
         f[3],
         0.0F,
         1.0F,
         0.0F,
         -1,
         15728880
      );
      DeathFx.face(
         vc,
         m,
         0.21F,
         -0.006F,
         -0.29F,
         -0.21F,
         -0.006F,
         -0.29F,
         -0.21F,
         -0.006F,
         0.29F,
         0.21F,
         -0.006F,
         0.29F,
         b[0],
         b[1],
         b[2],
         b[3],
         0.0F,
         -1.0F,
         0.0F,
         -1,
         15728880
      );
   }

   private static void chip(VertexConsumer w, Matrix4f m, int col, int light) {
      float r = 0.075F;
      float h = 0.014F;
      int rim = DeathFx.mix(col & 16777215, 16777215, 0.55F);

      for (int i = 0; i < 8; i++) {
         double a0 = i * Math.PI / 4.0;
         double a1 = (i + 1) * Math.PI / 4.0;
         float x0 = (float)Math.cos(a0) * r;
         float z0 = (float)Math.sin(a0) * r;
         float x1 = (float)Math.cos(a1) * r;
         float z1 = (float)Math.sin(a1) * r;
         int sc = (i & 1) == 0 ? rim : col;
         float nx = (float)Math.cos((a0 + a1) / 2.0);
         float nz = (float)Math.sin((a0 + a1) / 2.0);
         DeathFx.face(w, m, x0, h, z0, x1, h, z1, x1, -h, z1, x0, -h, z0, 0.5F, 0.5F, 0.5F, 0.5F, nx, 0.0F, nz, sc, light);
         DeathFx.tri(w, m, 0.0F, h, 0.0F, x1, h, z1, x0, h, z0, i % 2 == 0 ? col : DeathFx.mix(col & 16777215, 16777215, 0.15F), light);
         DeathFx.tri(w, m, 0.0F, -h, 0.0F, x0, -h, z0, x1, -h, z1, col, light);
      }
   }

   private static void ribbon(
      VertexConsumer g, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float wa, float wb, int rgb, float aa, float ab
   ) {
      float dx = bx - ax;
      float dz = bz - az;
      float l = (float)Math.sqrt(dx * dx + dz * dz);
      if (!(l < 1.0E-5F) && (!(aa <= 0.003F) || !(ab <= 0.003F))) {
         float nx = -dz / l;
         float nz = dx / l;
         int ca = DeathFx.argb(rgb, aa);
         int cb = DeathFx.argb(rgb, ab);
         DeathFx.GP[0] = ax + nx * wa / 2.0F;
         DeathFx.GP[1] = ay;
         DeathFx.GP[2] = az + nz * wa / 2.0F;
         DeathFx.GP[3] = bx + nx * wb / 2.0F;
         DeathFx.GP[4] = by;
         DeathFx.GP[5] = bz + nz * wb / 2.0F;
         DeathFx.GP[6] = bx - nx * wb / 2.0F;
         DeathFx.GP[7] = by;
         DeathFx.GP[8] = bz - nz * wb / 2.0F;
         DeathFx.GP[9] = ax - nx * wa / 2.0F;
         DeathFx.GP[10] = ay;
         DeathFx.GP[11] = az - nz * wa / 2.0F;
         DeathFx.gquad(g, m, DeathFx.GP, ca, cb, cb, ca);
         DeathFx.GP[0] = ax;
         DeathFx.GP[1] = ay + wa * 0.25F;
         DeathFx.GP[2] = az;
         DeathFx.GP[3] = bx;
         DeathFx.GP[4] = by + wb * 0.25F;
         DeathFx.GP[5] = bz;
         DeathFx.GP[6] = bx;
         DeathFx.GP[7] = by - wb * 0.25F;
         DeathFx.GP[8] = bz;
         DeathFx.GP[9] = ax;
         DeathFx.GP[10] = ay - wa * 0.25F;
         DeathFx.GP[11] = az;
         DeathFx.gquad(g, m, DeathFx.GP, ca, cb, cb, ca);
      }
   }

   private static void cross(VertexConsumer g, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float w, int rgb, float a) {
      ribbon(g, m, ax, ay, az, bx, by, bz, w, w, rgb, a, a);
      float dx = bx - ax;
      float dz = bz - az;
      float l = (float)Math.sqrt(dx * dx + dz * dz);
      if (!(l < 1.0E-5F)) {
         int c = DeathFx.argb(rgb, a);
         DeathFx.GP[0] = ax;
         DeathFx.GP[1] = ay + w / 2.0F;
         DeathFx.GP[2] = az;
         DeathFx.GP[3] = bx;
         DeathFx.GP[4] = by + w / 2.0F;
         DeathFx.GP[5] = bz;
         DeathFx.GP[6] = bx;
         DeathFx.GP[7] = by - w / 2.0F;
         DeathFx.GP[8] = bz;
         DeathFx.GP[9] = ax;
         DeathFx.GP[10] = ay - w / 2.0F;
         DeathFx.GP[11] = az;
         DeathFx.gquad(g, m, DeathFx.GP, c, c, c, c);
      }
   }

   private static void ringFlat(VertexConsumer g, Matrix4f m, float cx, float y, float cz, float r0, float r1, int rgb, float a) {
      DeathFx.disc(g, m, cx, y, cz, r0, r1, rgb, 0.0F, rgb, a, 40);
      DeathFx.disc(g, m, cx, y, cz, r1, r1 * 1.05F, rgb, a, rgb, 0.0F, 40);
   }

   private static float[] uv(int tile) {
      int col = tile % 6;
      int row = tile / 6;
      float x = 2 + col * 84;
      float y = 4 + row * 124;
      return new float[]{x / 512.0F, y / 256.0F, (x + 80.0F) / 512.0F, (y + 112.0F) / 256.0F};
   }

   private static boolean cardsTex() {
      if (cardsReady) {
         return true;
      }

      if (cardsFailed) {
         return false;
      }

      try {
         BufferedImage img = new BufferedImage(512, 256, 2);
         Graphics2D g = img.createGraphics();
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
         String[] rank = new String[]{"", "A", "K", "Q", "J", "10", "7"};
         int[] suit = new int[]{0, 0, 1, 2, 3, 1, 0};

         for (int tile = 0; tile < 7; tile++) {
            int col = tile % 6;
            int row = tile / 6;
            int x = 2 + col * 84;
            int y = 4 + row * 124;
            if (tile == 0) {
               back(g, x, y);
            } else {
               front(g, x, y, rank[tile], suit[tile]);
            }
         }

         g.dispose();
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         ImageIO.write(img, "png", out);
         Minecraft.getInstance().getTextureManager().register(CARDS, new DynamicTexture(() -> "lyricsflow_cards", NativeImage.read(out.toByteArray())));
         cardsReady = true;
      } catch (Throwable var9) {
         Core.LOG.warn("Karten-Bild konnte nicht gemalt werden", var9);
         cardsFailed = true;
      }

      return cardsReady;
   }

   private static void back(Graphics2D g, int x, int y) {
      Shape card = new java.awt.geom.RoundRectangle2D.Float(x, y, 80.0F, 112.0F, 12.0F, 12.0F);
      g.setColor(new Color(9309986));
      g.fill(card);
      Shape inner = new java.awt.geom.RoundRectangle2D.Float(x + 6, y + 6, 68.0F, 100.0F, 8.0F, 8.0F);
      Shape old = g.getClip();
      g.setClip(inner);
      g.setColor(new Color(217, 180, 74, 70));
      g.setStroke(new BasicStroke(1.4F));

      for (int k = -112; k < 192; k += 9) {
         g.drawLine(x + k, y, x + k + 112, y + 112);
         g.drawLine(x + k, y + 112, x + k + 112, y);
      }

      g.setClip(old);
      g.setColor(new Color(14267466));
      g.setStroke(new BasicStroke(2.6F));
      g.draw(inner);
      float cx = x + 40.0F;
      float cy = y + 56.0F;
      g.fill(new java.awt.geom.Ellipse2D.Float(cx - 17.0F, cy - 17.0F, 34.0F, 34.0F));
      g.setColor(new Color(1380376));
      g.fill(suitShape(0, cx, cy, 11.0F));
   }

   private static void front(Graphics2D g, int x, int y, String rank, int suit) {
      Shape card = new java.awt.geom.RoundRectangle2D.Float(x, y, 80.0F, 112.0F, 12.0F, 12.0F);
      g.setColor(new Color(16183783));
      g.fill(card);
      g.setColor(new Color(13616820));
      g.setStroke(new BasicStroke(2.0F));
      g.draw(new java.awt.geom.RoundRectangle2D.Float(x + 1, y + 1, 78.0F, 110.0F, 11.0F, 11.0F));
      Color c = suit != 1 && suit != 2 ? new Color(1447452) : new Color(13111342);
      g.setColor(c);
      g.setFont(new Font("SansSerif", 1, rank.length() > 1 ? 17 : 20));
      g.drawString(rank, x + 7, y + 22);
      g.fill(suitShape(suit, x + 14, y + 34, 6.0F));
      AffineTransform at = g.getTransform();
      g.rotate(Math.PI, x + 40.0, y + 56.0);
      g.drawString(rank, x + 7, y + 22);
      g.fill(suitShape(suit, x + 14, y + 34, 6.0F));
      g.setTransform(at);
      g.fill(suitShape(suit, x + 40.0F, y + 56.0F, suit == 0 && rank.equals("A") ? 22.0F : 17.0F));
   }

   private static Shape suitShape(int suit, float cx, float cy, float s) {
      java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
      switch (suit) {
         case 1:
            p.moveTo(cx, cy + s * 0.9F);
            p.curveTo(cx - s * 1.25F, cy + s * 0.05F, cx - s * 0.65F, cy - s * 1.0F, cx, cy - s * 0.35F);
            p.curveTo(cx + s * 0.65F, cy - s * 1.0F, cx + s * 1.25F, cy + s * 0.05F, cx, cy + s * 0.9F);
            break;
         case 2:
            p.moveTo(cx, cy - s);
            p.lineTo(cx + s * 0.72F, cy);
            p.lineTo(cx, cy + s);
            p.lineTo(cx - s * 0.72F, cy);
            break;
         case 3:
            p.append(new java.awt.geom.Ellipse2D.Float(cx - s * 0.42F, cy - s * 0.95F, s * 0.84F, s * 0.84F), false);
            p.append(new java.awt.geom.Ellipse2D.Float(cx - s * 0.92F, cy - s * 0.3F, s * 0.84F, s * 0.84F), false);
            p.append(new java.awt.geom.Ellipse2D.Float(cx + s * 0.08F, cy - s * 0.3F, s * 0.84F, s * 0.84F), false);
            p.moveTo(cx, cy);
            p.lineTo(cx - s * 0.32F, cy + s * 0.95F);
            p.lineTo(cx + s * 0.32F, cy + s * 0.95F);
            break;
         default:
            p.moveTo(cx, cy - s * 0.95F);
            p.curveTo(cx + s * 1.25F, cy - s * 0.1F, cx + s * 0.65F, cy + s * 0.8F, cx, cy + s * 0.25F);
            p.curveTo(cx - s * 0.65F, cy + s * 0.8F, cx - s * 1.25F, cy - s * 0.1F, cx, cy - s * 0.95F);
            p.moveTo(cx, cy + s * 0.1F);
            p.lineTo(cx - s * 0.36F, cy + s * 0.95F);
            p.lineTo(cx + s * 0.36F, cy + s * 0.95F);
      }

      p.closePath();
      return p;
   }

   static {
      float t = 0.0F;

      for (int i = 0; i < TT.length; i++) {
         TT[i] = t;
         float r = i / 1000.0F;
         t += (1.0F - 0.72F * DeathFx.sm(1.97F, 2.07F, r) * (1.0F - DeathFx.sm(2.55F, 2.8F, r))) / 1000.0F;
      }

      SLASH_PX = new int[]{24, 12, 18, 6, 18, 24, 6, 12};
      SLICES = new int[][]{{0, 6}, {6, 12}, {12, 18}, {18, 24}, {24, 32}};
      PARTS = new float[][]{
         {-2.0F, 0.0F, 4.0F, 12.0F, 4.0F, 0.0F, 16.0F, 0.0F, 32.0F, 0.25F},
         {2.0F, 0.0F, 4.0F, 12.0F, 4.0F, 16.0F, 48.0F, 0.0F, 48.0F, 0.25F},
         {0.0F, 12.0F, 8.0F, 12.0F, 4.0F, 16.0F, 16.0F, 16.0F, 32.0F, 0.25F},
         {-6.0F, 12.0F, 4.0F, 12.0F, 4.0F, 40.0F, 16.0F, 40.0F, 32.0F, 0.25F},
         {6.0F, 12.0F, 4.0F, 12.0F, 4.0F, 32.0F, 48.0F, 48.0F, 48.0F, 0.25F},
         {0.0F, 24.0F, 8.0F, 8.0F, 8.0F, 0.0F, 0.0F, 32.0F, 0.0F, 0.5F}
      };
      A = new float[3];
      B = new float[3];
      S0 = new float[3];
      LAND = new float[][]{{-0.17F, 0.02F, 0.05F, 0.35F}, {0.16F, 0.028F, -0.05F, -0.28F}};
      C = new float[6];
      C2 = new float[6];
      CARDS = Identifier.fromNamespaceAndPath("lyricsflow", "cosmetic/cards");
   }

   private static final class State {
      int[] face = new int[8];
      float[][] pieces = new float[DeathCards.SLICES.length][];
      float[][] hits = new float[56][];
      float[][] burst = new float[30][];
      float[][] chips = new float[16][];
      float[][] sparks = new float[40][];
      float[][] embers = new float[DeathCards.SLICES.length * 8][];
   }
}
