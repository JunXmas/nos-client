package dev.jun.nosclient.lyricsflow.app.cosmetic;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jun.nosclient.lyricsflow.app.Core;
import dev.jun.nosclient.lyricsflow.app.config.Config;
import dev.jun.nosclient.lyricsflow.app.sky.SkyTextures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DeathFx {
   public static final int COUNT = 4;
   static final float TE = 1.62F;
   static final float TI = 1.9F;
   static final float TF = 2.06F;
   static final float TP = 2.32F;
   static final float T_END = 4.75F;
   static final float[] TT = new float[9001];
   static final float HX = 1.7F;
   static final float HZ = 0.85F;
   static final float BX = 0.0F;
   static final float BY = 3.3F;
   static final float BZ = 0.0F;
   static final float STAND = 1.055F;
   static final float[] PX;
   static final float[] PY;
   static final float[] PZ;
   static final float[] PS;
   static final float SB;
   static final float SH;
   static final float SEND;
   static final float DX;
   static final float DY;
   static final float DZ;
   static final float TC;
   static final float[] TA;
   static final float[] TB;
   static final List<DeathFx.Fx> ACTIVE;
   static final Set<Integer> TRIGGERED;
   static final Set<Integer> SEEN;
   static final Map<Integer, Long> HITS;
   static final long KILL_WINDOW_MS = 6000L;
   static final float PXS = 0.05859375F;
   static final DeathFx.Pose POSE;
   static final float[] Q;
   static final float[] Q2;
   static final float[] AX;
   static final float[] GP;

   private DeathFx() {
   }

   static void pathPoint(float s, float[] out) {
      int n = PS.length;
      if (s <= 0.0F) {
         out[0] = PX[0];
         out[1] = PY[0] + s;
         out[2] = PZ[0];
      } else if (s >= PS[n - 1]) {
         float e = s - PS[n - 1];
         out[0] = PX[n - 1] + DX * e;
         out[1] = PY[n - 1] + DY * e;
         out[2] = PZ[n - 1] + DZ * e;
      } else {
         int lo = 0;
         int hi = n - 1;

         while (hi - lo > 1) {
            int mid = lo + hi >>> 1;
            if (PS[mid] <= s) {
               lo = mid;
            } else {
               hi = mid;
            }
         }

         float k = (s - PS[lo]) / Math.max(1.0E-6F, PS[hi] - PS[lo]);
         out[0] = PX[lo] + (PX[hi] - PX[lo]) * k;
         out[1] = PY[lo] + (PY[hi] - PY[lo]) * k;
         out[2] = PZ[lo] + (PZ[hi] - PZ[lo]) * k;
      }
   }

   static float sTip(float t) {
      if (t < 1.62F) {
         return SH - 1.3F;
      } else if (t < 1.9F) {
         return lerp(SH - 1.3F, SB - 0.95F, (float)Math.pow((t - 1.62F) / 0.27999997F, 0.85));
      } else if (t < 2.06F) {
         return SB - 0.95F + 1.35F * eo((t - 1.9F) / 0.15999997F);
      } else {
         return t < 2.32F ? SEND + 0.025F * (float)Math.sin(t * 70.0F) * (1.0F - sm(2.1F, 2.32F, t)) : SEND - ei((t - 2.32F) / 0.75F) * (SEND - (SH - 7.0F));
      }
   }

   static float sBody(float t) {
      return sTip(t) - (SEND - SB);
   }

   static float wave(float s, float t) {
      float st = sTip(t);
      float a = 0.42F * (1.0F - sm(1.62F, 2.02F, t))
         + 0.04F * sm(1.9F, 2.0F, t) * (1.0F - sm(2.25F, 2.32F, t))
         + 0.13F * sm(2.32F, 2.5F, t) * (1.0F - sm(2.95F, 3.05F, t));
      a *= sm(SH - 0.2F, SH + 1.3F, s) * sm(0.0F, 1.6F, st - s + 0.6F);
      if (t > 1.9F) {
         a *= sm(0.0F, 1.1F, Math.abs(s - sBody(t)));
      }

      return a;
   }

   static void chainPoint(float s, float t, float[] out) {
      pathPoint(s, out);
      float a = wave(s, t);
      if (!(a < 1.0E-4F)) {
         pathPoint(s - 0.05F, TA);
         pathPoint(s + 0.05F, TB);
         float tx = TB[0] - TA[0];
         float ty = TB[1] - TA[1];
         float tz = TB[2] - TA[2];
         float tl = (float)Math.sqrt(tx * tx + ty * ty + tz * tz);
         if (!(tl < 1.0E-6F)) {
            tx /= tl;
            ty /= tl;
            tz /= tl;
            float n1x = -tz;
            float n1y = 0.0F;
            float nl = (float)Math.sqrt(n1x * n1x + tx * tx);
            float n1z;
            if (nl < 1.0E-4F) {
               n1x = 1.0F;
               n1z = 0.0F;
            } else {
               n1x /= nl;
               n1z = tx / nl;
            }

            float n2x = ty * n1z - tz * n1y;
            float n2y = tz * n1x - tx * n1z;
            float n2z = tx * n1y - ty * n1x;
            float w1 = a * (float)Math.sin(s * 2.3F - t * 34.0F);
            float w2 = a * 0.5F * (float)Math.cos(s * 1.7F - t * 27.0F);
            out[0] += n1x * w1 + n2x * w2;
            out[1] += n1y * w1 + n2y * w2;
            out[2] += n1z * w1 + n2z * w2;
         }
      }
   }

   static void pose(float t, DeathFx.Pose o) {
      if (t < 1.9F) {
         float k = 1.0F - (float)Math.pow(1.0F - sm(0.22F, 1.62F, t), 2.0);
         o.p[0] = lerp(0.0F, 0.0F, k);
         o.p[1] = lerp(1.055F, 3.3F, k) + (float)Math.sin(t * 2.6F) * 0.06F * sm(0.5F, 1.0F, t) * (1.0F - sm(1.45F, 1.62F, t));
         o.p[2] = lerp(0.0F, 0.0F, k) - 0.28F * (float)Math.sin(cl(t / 0.5F) * Math.PI) * (1.0F - k);
         o.rx = (float) (-Math.PI / 2) * sm(0.25F, 1.35F, t);
         o.ry = 0.55F * sm(0.2F, 1.6F, t);
         o.rz = 0.12F * (float)Math.sin(t * 1.8F) * sm(0.4F, 1.0F, t) * (1.0F - sm(1.5F, 1.62F, t));
         o.arm = -0.9F * (float)Math.sin(cl(t / 0.4F) * Math.PI) + 1.35F * sm(0.35F, 1.2F, t) + 0.12F * (float)Math.sin(t * 2.2F);
         o.leg = 0.55F * sm(0.3F, 1.2F, t);
         o.head = -0.75F * sm(0.4F, 1.3F, t);
      } else {
         chainPoint(sBody(t), t, o.p);
         float j = (float)Math.sin(cl((t - 1.9F) / 0.3F) * Math.PI);
         float pl = ei((t - 2.32F) / 0.6F);
         o.rx = (float) (-Math.PI / 2) - 0.38F * j + 0.25F * pl;
         o.ry = 0.55F;
         o.rz = 0.05F * (float)Math.sin(t * 40.0F) * sm(2.06F, 2.1F, t) * (1.0F - sm(2.25F, 2.32F, t));
         o.arm = lerp(1.35F - 1.0F * j, -1.3F, pl);
         o.leg = lerp(0.55F - 0.3F * j, -0.9F, pl);
         o.head = lerp(-0.75F + 0.5F * j, -1.1F, pl);
      }

      o.e = (float)(0.9 * Math.exp(-t * 12.0F) + (t >= 1.9F ? 0.8 * Math.exp(-(t - 1.9F) * 7.0F) : 0.0));
   }

   public static void tick(Minecraft mc) {
      Config c = Core.cfg();
      if (mc.level == null || mc.player == null) {
         ACTIVE.clear();
         TRIGGERED.clear();
      } else if (c.deathFx <= 0) {
         ACTIVE.clear();
         TRIGGERED.clear();
      } else {
         SEEN.clear();

         for (AbstractClientPlayer p : mc.level.players()) {
            int id = p.getId();
            SEEN.add(id);
            boolean dead = p.isDeadOrDying();
            if (!dead) {
               TRIGGERED.remove(id);
            } else if (!TRIGGERED.contains(id)) {
               TRIGGERED.add(id);
               if (p != mc.player && !(p.distanceToSqr(mc.player) > 6400.0) && (c.deathOthers || killedByMe(id))) {
                  spawn(mc, p, false, false);
               }
            }
         }

         TRIGGERED.retainAll(SEEN);
         long now = System.currentTimeMillis();
         HITS.values().removeIf(at -> now - at > 12000L);
      }
   }

   public static void onAttack(Entity target) {
      if (target instanceof AbstractClientPlayer) {
         HITS.put(target.getId(), System.currentTimeMillis());
      }
   }

   static boolean killedByMe(int id) {
      Long at = HITS.get(id);
      return at != null && System.currentTimeMillis() - at <= 6000L;
   }

   public static void test() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         spawn(mc, mc.player, false, true);
      }
   }

   public static boolean hides(Entity e) {
      if (Core.cfg().deathFx > 0 && e != null) {
         int id = e.getId();
         return TRIGGERED.contains(id) && e instanceof AbstractClientPlayer;
      } else {
         return false;
      }
   }

   static void spawn(Minecraft mc, AbstractClientPlayer p, boolean self, boolean test) {
      if (ACTIVE.size() >= 6) {
         ACTIVE.remove(0);
      }

      DeathFx.Fx f = new DeathFx.Fx();
      f.id = p.getId();
      f.self = self;
      f.test = test;
      f.kind = Math.max(1, Core.cfg().deathFx);
      Vec3 pos = p.position();
      f.yaw = (float)Math.toRadians(p.yBodyRot);
      if (test) {
         double yr = Math.toRadians(p.getYRot());
         pos = pos.add(-Math.sin(yr) * 3.2, 0.0, Math.cos(yr) * 3.2);
         f.yaw = (float)Math.toRadians(p.getYRot() + 180.0F);
         f.id = Integer.MIN_VALUE;
      }

      f.start = System.nanoTime();

      try {
         f.skin = p.getSkin().body().texturePath();
      } catch (Throwable var35) {
         f.skin = null;
      }

      double gy = pos.y;
      int top = 5999164;
      int under = 8017460;

      try {
         for (int dy = 0; dy <= 8; dy++) {
            BlockPos bp = BlockPos.containing(pos.x, pos.y - 0.01 - dy, pos.z);
            BlockState st = mc.level.getBlockState(bp);
            if (!st.getCollisionShape(mc.level, bp).isEmpty()) {
               gy = bp.getY() + 1.0;
               int c1 = st.getMapColor(mc.level, bp).col;
               BlockPos b2 = bp.below();
               int c2 = mc.level.getBlockState(b2).getMapColor(mc.level, b2).col;
               if (c1 != 0) {
                  top = c1;
               }

               under = c2 != 0 ? c2 : top;
               break;
            }
         }
      } catch (Throwable var36) {
      }

      f.ox = pos.x;
      f.oy = gy;
      f.oz = pos.z;
      f.groundCol = top;
      f.dirtCol = under;
      Random r = new Random(f.start ^ 31L * f.id);
      List<float[]> cr = new ArrayList<>();

      for (int k = 0; k < 13; k++) {
         double a = k / 13.0 * Math.PI * 2.0 + r.nextDouble() * 0.4;
         double rr = 0.15;
         double x = 1.7F + Math.cos(a) * rr;
         double z = 0.85F + Math.sin(a) * rr;
         double w = 0.085;

         for (double maxR = 2.4 + r.nextDouble() * 1.9; rr < maxR; w *= 0.94) {
            double len = 0.17 + r.nextDouble() * 0.24;
            a += (r.nextDouble() - 0.5) * 0.9;
            double nx = x + Math.cos(a) * len;
            double nz = z + Math.sin(a) * len;
            double mx = (x + nx) / 2.0;
            double mz = (z + nz) / 2.0;
            cr.add(new float[]{(float)x, (float)z, (float)nx, (float)nz, (float)w, (float)rr, (float)Math.hypot(mx - 1.7F, mz - 0.85F), r.nextFloat() * 9.0F});
            x = nx;
            z = nz;
            rr = Math.hypot(nx - 1.7F, nz - 0.85F);
         }
      }

      f.cracks = cr.toArray(new float[0][]);
      f.chunks = new float[42][];

      for (int i = 0; i < f.chunks.length; i++) {
         boolean big = i < 12;
         double a = r.nextDouble() * Math.PI * 2.0;
         double rr = r.nextDouble() * (big ? 0.7 : 0.4);
         double sp = big ? 1.2 + r.nextDouble() * 1.8 : 1.0 + r.nextDouble() * 3.0;
         f.chunks[i] = new float[]{
            (float)(1.7F + Math.cos(a) * rr),
            -0.1F,
            (float)(0.85F + Math.sin(a) * rr),
            (float)(Math.cos(a) * sp),
            (float)(big ? 4.5 + r.nextDouble() * 2.5 : 4.0 + r.nextDouble() * 5.0),
            (float)(Math.sin(a) * sp),
            (float)(big ? 0.24 + r.nextDouble() * 0.2 : 0.07 + r.nextDouble() * 0.12),
            r.nextFloat() * 10.0F - 5.0F,
            r.nextFloat() * 10.0F - 5.0F,
            r.nextFloat() * 10.0F - 5.0F,
            shade(r.nextBoolean() ? top : under, 0.8F + r.nextFloat() * 0.3F)
         };
      }

      f.ring = new float[18][];

      for (int i = 0; i < f.ring.length; i++) {
         f.ring[i] = new float[]{
            (float)(i / 18.0 * Math.PI * 2.0 + r.nextDouble() * 0.2),
            1.0F + r.nextFloat() * 0.7F,
            0.28F + r.nextFloat() * 0.16F,
            r.nextFloat() * 0.12F,
            shade(top, 0.75F + r.nextFloat() * 0.3F)
         };
      }

      f.burst = new float[70][];
      pathPoint(SB - 0.05F, TA);
      pathPoint(SB + 0.05F, TB);
      float tx = (TB[0] - TA[0]) * 10.0F;
      float ty = (TB[1] - TA[1]) * 10.0F;
      float tz = (TB[2] - TA[2]) * 10.0F;

      for (int i = 0; i < f.burst.length; i++) {
         double vx = r.nextGaussian();
         double vy = r.nextGaussian();
         double vz = r.nextGaussian();
         double vl = Math.max(0.01, Math.sqrt(vx * vx + vy * vy + vz * vz));
         double sp = 1.2 + r.nextDouble() * 4.0;
         double along = 1.5 + r.nextDouble() * 3.5;
         f.burst[i] = new float[]{
            (float)(vx / vl * sp + tx * along),
            (float)(vy / vl * sp + ty * along),
            (float)(vz / vl * sp + tz * along),
            0.035F + r.nextFloat() * 0.08F,
            r.nextFloat() * 12.0F,
            r.nextFloat() < 0.25F ? 1.0F : 0.0F,
            1.9F + r.nextFloat() * 0.04F
         };
      }

      f.embers = new float[56][];

      for (int i = 0; i < f.embers.length; i++) {
         f.embers[i] = new float[]{
            0.95F + r.nextFloat() * 3.4F,
            1.2F + r.nextFloat() * 0.9F,
            (float)Math.sqrt(r.nextDouble()) * 1.5F,
            r.nextFloat() * 6.283F,
            0.8F + r.nextFloat() * 1.6F,
            0.06F + r.nextFloat() * 0.08F,
            r.nextFloat() < 0.7F ? 1.672096E7F : 1.6747088E7F
         };
      }

      f.wisps = new float[24][];

      for (int i = 0; i < f.wisps.length; i++) {
         f.wisps[i] = new float[]{
            0.3F + r.nextFloat() * 1.1F,
            (r.nextFloat() - 0.5F) * 0.7F,
            (r.nextFloat() - 0.5F) * 0.7F,
            (r.nextFloat() - 0.5F) * 0.7F,
            r.nextFloat() - 0.5F,
            r.nextFloat() - 0.5F
         };
      }

      f.dust = new float[56][];

      for (int i = 0; i < f.dust.length; i++) {
         boolean first = i < 22;
         f.dust[i] = new float[]{
            (first ? 1.62F : TC) + r.nextFloat() * 0.08F,
            r.nextFloat() * 6.283F,
            (first ? 0.5F : 1.3F) + r.nextFloat() * 0.5F,
            (first ? 2.0F : 2.8F) * (0.6F + r.nextFloat() * 0.6F),
            0.2F + r.nextFloat() * 0.8F,
            1.5F + r.nextFloat() * 1.3F,
            0.8F + r.nextFloat() * 1.0F
         };
      }

      if (f.kind == 2) {
         f.extra = DeathCards.create(f, r);
      } else if (f.kind >= 3) {
         f.extra = DeathHard.create(f, r);
      }

      ACTIVE.add(f);
   }

   public static void render(Minecraft mc, MultiBufferSource buffers, Matrix4f base, Vec3 cam, float pt) {
      if (!ACTIVE.isEmpty() && mc.level != null) {
         long now = System.nanoTime();
         cameraAxes(mc, base, pt);
         Iterator<DeathFx.Fx> it = ACTIVE.iterator();

         while (it.hasNext()) {
            DeathFx.Fx f = it.next();
            int ms = (int)((now - f.start) / 1000000L);
            boolean hard = f.kind >= 3;
            float[] table = f.kind == 2 ? DeathCards.TT : (hard ? DeathHard.TT[Math.min(f.kind - 3, DeathHard.TT.length - 1)] : TT);
            float t = table[Math.min(table.length - 1, Math.max(0, ms))];
            float tEnd = f.kind == 2 ? 4.2F : (hard ? DeathHard.END[Math.min(f.kind - 3, DeathHard.END.length - 1)] : 4.75F);
            if (!(t > tEnd) && ms < table.length - 1) {
               try {
                  if (f.kind == 2) {
                     DeathCards.draw(mc, buffers, base, cam, f, t);
                  } else if (hard) {
                     DeathHard.draw(mc, buffers, base, cam, f, t);
                  } else {
                     draw(mc, buffers, base, cam, f, t);
                  }
               } catch (Throwable var15) {
                  Core.LOG.warn("Todes-Animation abgebrochen", var15);
                  it.remove();
               }
            } else {
               it.remove();
            }
         }
      }
   }

   static void draw(Minecraft mc, MultiBufferSource buffers, Matrix4f base, Vec3 cam, DeathFx.Fx f, float t) {
      if (Accessories.whiteTex()) {
         Matrix4f m = new Matrix4f(base)
            .translate((float)(f.ox - cam.x), (float)(f.oy - cam.y), (float)(f.oz - cam.z))
            .rotateY(-f.yaw);
         int light = light(mc, f.ox, f.oy + 0.5, f.oz);
         float fade = 1.0F - sm(3.7F, 4.7F, t);
         boolean firstPerson = f.self && mc.options.getCameraType().isFirstPerson();
         VertexConsumer g = buffers.getBuffer(RenderTypes.debugQuads());
         float glow = sm(0.95F, 1.6F, t) * (1.0F + 0.2F * (float)Math.sin(t * 19.0F));
         float y0 = 0.02F;
         float sheen = (t < 1.62F ? 0.35F * glow : 0.42F * (float)Math.exp(-(t - 1.62F) * 1.2) + 0.22F) * fade * sm(0.95F, 1.3F, t);
         disc(g, m, 1.7F, y0 - 0.005F, 0.85F, 0.0F, 2.6F, 16718390, sheen, 16718390, 0.0F, 28);
         float spread = 4.2F * eo((t - 0.92F) / 0.75F);
         float holeR = t < 1.62F ? 0.0F : (t < TC ? 0.95F : 1.75F);

         for (float[] c : f.cracks) {
            float op = sm(c[5], c[5] + 0.35F, spread) * (0.75F + 0.25F * (float)Math.sin(t * 23.0F + c[7])) * (1.0F - 0.7F * sm(3.4F, 4.6F, t));
            if (!(op < 0.01F) && !(c[6] < holeR)) {
               strip(g, m, c[0], c[1], c[2], c[3], y0 + 0.004F, c[4] * 3.2F, 16718904, op * 0.22F);
               strip(g, m, c[0], c[1], c[2], c[3], y0 + 0.008F, c[4], 16725328, op);
            }
         }

         if (t >= 1.62F) {
            float R = (t < TC ? 0.95F * eo((t - 1.62F) / 0.12F) : 0.95F + 0.8F * eo((t - TC) / 0.25F)) * (1.0F - 0.85F * sm(3.9F, 4.7F, t));
            float a = 1.0F - sm(4.2F, 4.75F, t);
            float core = 0.85F + 0.15F * (float)Math.sin(t * 9.0F);
            disc(g, m, 1.7F, y0 + 0.012F, 0.85F, 0.0F, R * 0.45F, 16726613, a * core, 6947864, a, 24);
            disc(g, m, 1.7F, y0 + 0.012F, 0.85F, R * 0.45F, R, 6947864, a, 393476, a, 24);
            disc(g, m, 1.7F, y0 + 0.01F, 0.85F, R, R * 1.18F, 16722504, 0.9F * a, 16722504, 0.0F, 24);
         }

         float ai = (t - 1.9F) / 0.32F;
         if (ai > 0.0F && ai < 1.0F) {
            pathPoint(SB - 0.05F, TA);
            pathPoint(SB + 0.05F, TB);
            ringAround(
               g,
               m,
               0.0F,
               3.3F,
               0.0F,
               TB[0] - TA[0],
               TB[1] - TA[1],
               TB[2] - TA[2],
               0.25F + eo(ai) * 1.7F,
               0.06F + 0.04F * (1.0F - ai),
               16730726,
               0.8F * (1.0F - ai)
            );
         }

         float ag = (t - TC) / 0.9F;
         if (ag > 0.0F && ag < 1.0F) {
            float rr = 0.5F + eo(ag) * 6.0F;
            disc(g, m, 1.7F, y0 + 0.02F, 0.85F, rr * 0.9F, rr, 16722500, 0.0F, 16722500, 0.9F * (1.0F - ag), 40);
            disc(g, m, 1.7F, y0 + 0.02F, 0.85F, rr, rr * 1.04F, 16751272, 0.6F * (1.0F - ag), 16751272, 0.0F, 40);
         }

         VertexConsumer w = buffers.getBuffer(RenderTypes.entityCutoutNoCull(Accessories.WHITE));
         boolean on = t >= 1.62F;
         float st = sTip(t);
         if (on) {
            for (int i = 0; i < 44; i++) {
               float s = st - 0.26F - i * 0.33F;
               if (s <= SH - 2.5F) {
                  break;
               }

               chainPoint(s, t, Q);
               chainPoint(s + 0.06F, t, Q2);
               float e = 0.0F;
               if (t < 2.1599998F) {
                  e += 0.9F * (float)Math.exp(-(st - s) / 0.45F) * sm(1.62F, 1.67F, t) * (1.0F - sm(2.0F, 2.15F, t));
               }

               if (t > 2.06F && t < 2.52F) {
                  float pp = SB - (t - 2.06F) * 30.0F;
                  e += 1.6F * (float)Math.exp(-(s - pp) * (s - pp) / 0.25F);
               }

               if (t > 2.32F) {
                  e += 0.05F + 0.3F * Math.max(0.0F, (float)Math.sin(s * 2.5F + t * 45.0F));
               }

               int col = mix(2763314, 16722504, Math.min(1.0F, e));
               int li = e > 0.35F ? 15728880 : light;
               Matrix4f lm = new Matrix4f(m).translate(Q[0], Q[1], Q[2]).rotate(toward(Q2[0] - Q[0], Q2[1] - Q[1], Q2[2] - Q[2]));
               if ((i & 1) == 1) {
                  lm.rotateY((float) (Math.PI / 2));
               }

               link(w, lm, col, li);
            }

            chainPoint(st, t, Q);
            chainPoint(st + 0.06F, t, Q2);
            Matrix4f sm4 = new Matrix4f(m).translate(Q[0], Q[1], Q[2]).rotate(toward(Q2[0] - Q[0], Q2[1] - Q[1], Q2[2] - Q[2]));
            float hot = t > 1.9F ? (float)Math.exp(-(t - 1.9F) * 4.0F) : 0.0F;
            spear(w, sm4, light, mix(11540526, 16734832, hot));
         }

         for (float[] cx : f.chunks) {
            float a = t - 1.62F;
            if (!(a <= 0.0F) && !(a > 2.5F)) {
               float x = cx[0] + cx[3] * a;
               float y = cx[1] + cx[4] * a - 8.0F * a * a;
               float z = cx[2] + cx[5] * a;
               if (!(y < -1.5F)) {
                  float sz = cx[6];
                  Matrix4f cm = new Matrix4f(m).translate(x, y, z).rotateXYZ(cx[7] * a, cx[8] * a, cx[9] * a);
                  cube(w, cm, -sz / 2.0F, -sz / 2.0F, -sz / 2.0F, sz / 2.0F, sz / 2.0F, sz / 2.0F, 0xFF000000 | Float.floatToRawIntBits(cx[10]), light);
               }
            }
         }

         for (float[] cxx : f.ring) {
            float a = t - TC - cxx[3];
            if (!(t < TC) && !(a > 1.4F)) {
               float aa = Math.max(0.0F, a);
               float rad = cxx[1] - aa * 0.8F;
               float x = 1.7F + (float)Math.cos(cxx[0]) * rad;
               float z = 0.85F + (float)Math.sin(cxx[0]) * rad;
               float y = -cxx[2] * 0.4F - 9.0F * aa * aa - aa * 0.5F;
               if (!(y < -2.5F)) {
                  float sz = cxx[2];
                  Matrix4f cm = new Matrix4f(m).translate(x, y, z).rotateY(-cxx[0]).rotateZ(aa * 3.0F);
                  cube(w, cm, -sz / 2.0F, -sz / 2.0F, -sz / 2.0F, sz / 2.0F, sz / 2.0F, sz / 2.0F, 0xFF000000 | Float.floatToRawIntBits(cxx[4]), light);
               }
            }
         }

         for (float[] b : f.burst) {
            float a = t - b[6];
            if (!(a <= 0.0F) && !(a > 1.6F)) {
               float x = 0.0F + b[0] * a;
               float y = 3.3F + b[1] * a - 6.0F * a * a;
               float z = 0.0F + b[2] * a;
               float sz = b[3] * (1.0F - a / 1.6F * 0.6F);
               Matrix4f cm = new Matrix4f(m).translate(x, y, z).rotateXYZ(b[4] * a, b[4] * a * 0.7F, 0.0F);
               boolean hotP = b[5] > 0.5F;
               cube(w, cm, -sz / 2.0F, -sz / 2.0F, -sz / 2.0F, sz / 2.0F, sz / 2.0F, sz / 2.0F, hotP ? -54715 : -12974576, hotP ? 15728880 : light);
            }
         }

         pose(t, POSE);
         boolean bodyVisible = POSE.p[1] > -1.4F && !firstPerson;
         if (bodyVisible && f.skin != null) {
            VertexConsumer sk = buffers.getBuffer(RenderTypes.entityCutoutNoCull(f.skin));
            int bl = light(mc, f.ox + POSE.p[0], f.oy + POSE.p[1], f.oz + POSE.p[2]);
            float ex = Math.min(1.0F, POSE.e);
            int tint = -65536 | Math.round(255.0F * (1.0F - 0.6F * ex)) << 8 | Math.round(255.0F * (1.0F - 0.6F * ex));
            body(sk, new Matrix4f(m).translate(POSE.p[0], POSE.p[1], POSE.p[2]).rotateY(POSE.ry).rotateX(POSE.rx).rotateZ(POSE.rz), POSE, tint, bl);
         }

         if (SkyTextures.spritesReady()) {
            VertexConsumer sp = buffers.getBuffer(RenderTypes.text(SkyTextures.SPRITES));
            float rx = AX[0];
            float ry = AX[1];
            float rz = AX[2];
            float ux = AX[3];
            float uy = AX[4];
            float uz = AX[5];
            if (!firstPerson) {
               for (float[] wp : f.wisps) {
                  float a = (t - wp[0]) / 1.0F;
                  if (!(a <= 0.0F) && !(a >= 1.0F)) {
                     pose(Math.min(wp[0], 1.8989999F), POSE);
                     float x = POSE.p[0] + wp[1] + wp[4] * a;
                     float y = POSE.p[1] + wp[2] + a * 1.2F;
                     float z = POSE.p[2] + wp[3] + wp[5] * a;
                     sprite(sp, base, f, cam, x, y, z, 0.5F + a * 1.3F, 1, 1707298, 0.55F * (float)Math.sin(a * Math.PI), rx, ry, rz, ux, uy, uz);
                  }
               }
            }

            for (float[] ex : f.embers) {
               float a = (t - ex[0]) / ex[1];
               if (!(a <= 0.0F) && !(a >= 1.0F)) {
                  float an = ex[3] + a * 1.5F;
                  float rad = ex[2] * (1.0F + a * 0.3F);
                  sprite(
                     sp,
                     base,
                     f,
                     cam,
                     1.7F + (float)Math.cos(an) * rad,
                     0.05F + a * ex[4] * 1.6F,
                     0.85F + (float)Math.sin(an) * rad,
                     ex[5] * 2.2F * (1.2F - a * 0.6F),
                     2,
                     (int)ex[6],
                     (float)Math.sin(a * Math.PI) * fade,
                     rx,
                     ry,
                     rz,
                     ux,
                     uy,
                     uz
                  );
               }
            }

            for (float[] d : f.dust) {
               float a = (t - d[0]) / d[5];
               if (!(a <= 0.0F) && !(a >= 1.0F)) {
                  float k = eo(a * 1.6F);
                  float x = 1.7F + (float)Math.cos(d[1]) * (d[2] + d[3] * k);
                  float z = 0.85F + (float)Math.sin(d[1]) * (d[2] + d[3] * k);
                  sprite(
                     sp,
                     base,
                     f,
                     cam,
                     x,
                     0.25F + d[4] * a * 1.5F,
                     z,
                     d[6] * (1.0F + a * 2.2F),
                     1,
                     mix(f.dirtCol, 2761254, 0.55F),
                     0.5F * (float)Math.pow(1.0F - a, 1.4) * cl(a * 8.0F),
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

   static void cameraAxes(Minecraft mc, Matrix4f base, float pt) {
      boolean identity = Math.abs(base.m00() - 1.0F) + Math.abs(base.m11() - 1.0F) + Math.abs(base.m22() - 1.0F) < 1.0E-4F;
      if (!identity) {
         AX[0] = base.m00();
         AX[1] = base.m10();
         AX[2] = base.m20();
         AX[3] = base.m01();
         AX[4] = base.m11();
         AX[5] = base.m21();
      } else {
         double y = Math.toRadians(mc.player.getViewYRot(pt));
         double p = Math.toRadians(mc.player.getViewXRot(pt));
         if (mc.options.getCameraType().isMirrored()) {
            y += Math.PI;
            p = -p;
         }

         double fx = -Math.sin(y) * Math.cos(p);
         double fy = -Math.sin(p);
         double fz = Math.cos(y) * Math.cos(p);
         double rx = -Math.cos(y);
         double ry = 0.0;
         double rz = -Math.sin(y);
         AX[0] = (float)rx;
         AX[1] = (float)ry;
         AX[2] = (float)rz;
         AX[3] = (float)(ry * fz - rz * fy);
         AX[4] = (float)(rz * fx - rx * fz);
         AX[5] = (float)(rx * fy - ry * fx);
      }
   }

   static Quaternionf toward(float x, float y, float z) {
      float l = (float)Math.sqrt(x * x + y * y + z * z);
      return l < 1.0E-6F ? new Quaternionf() : new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, x / l, y / l, z / l);
   }

   static void link(VertexConsumer w, Matrix4f m, int col, int light) {
      float L = 0.25F;
      float W = 0.13F;
      float T = 0.034F;
      cube(w, m, -W, -L, -T, -W + 2.0F * T, L, T, col, light);
      cube(w, m, W - 2.0F * T, -L, -T, W, L, T, col, light);
      cube(w, m, -W + 2.0F * T, L - 2.0F * T, -T, W - 2.0F * T, L, T, col, light);
      cube(w, m, -W + 2.0F * T, -L, -T, W - 2.0F * T, -L + 2.0F * T, T, col, light);
   }

   static void spear(VertexConsumer w, Matrix4f m, int light, int edge) {
      int dark = -14408660;
      cube(w, m, -0.09F, -0.12F, -0.09F, 0.09F, 0.1F, 0.09F, dark, light);
      float by = 0.12F;
      float ty = 1.0F;
      float bx = 0.17F;
      float bz = 0.07F;
      float[][] ring = new float[][]{{bx, by, 0.0F}, {0.0F, by, bz}, {-bx, by, 0.0F}, {0.0F, by, -bz}};

      for (int i = 0; i < 4; i++) {
         float[] a = ring[i];
         float[] b = ring[i + 1 & 3];
         tri(w, m, a[0], a[1], a[2], b[0], b[1], b[2], 0.0F, ty, 0.0F, dark, light);
         tri(w, m, a[0], a[1], a[2], b[0], b[1], b[2], 0.0F, 0.06F, 0.0F, dark, light);
      }

      for (int sgn = -1; sgn <= 1; sgn += 2) {
         float ex = sgn * (bx + 0.012F);
         quadW(w, m, ex, by, -0.012F, ex, by, 0.012F, 0.0F, ty + 0.02F, 0.006F, 0.0F, ty + 0.02F, -0.006F, edge, 15728880);
         tri(w, m, sgn * 0.1F, 0.3F, -0.035F, sgn * 0.1F, 0.3F, 0.035F, sgn * 0.33F, -0.1F, 0.0F, dark, light);
         tri(w, m, sgn * 0.1F, 0.3F, 0.035F, sgn * 0.13F, 0.18F, 0.0F, sgn * 0.33F, -0.1F, 0.0F, dark, light);
         tri(w, m, sgn * 0.1F, 0.3F, -0.035F, sgn * 0.13F, 0.18F, 0.0F, sgn * 0.33F, -0.1F, 0.0F, dark, light);
      }
   }

   static void body(VertexConsumer v, Matrix4f mb, DeathFx.Pose o, int tint, int light) {
      skinBox(v, mb, 8.0F, 12.0F, 4.0F, 0.0F, 16, 16, tint, light);
      skinBox(v, mb, 8.0F, 12.0F, 4.0F, 0.25F, 16, 32, tint, light);
      Matrix4f mh = new Matrix4f(mb).translate(0.0F, 0.3515625F, 0.0F).rotateX(o.head).translate(0.0F, 0.234375F, 0.0F);
      skinBox(v, mh, 8.0F, 8.0F, 8.0F, 0.0F, 0, 0, tint, light);
      skinBox(v, mh, 8.0F, 8.0F, 8.0F, 0.5F, 32, 0, tint, light);
      Matrix4f ar = new Matrix4f(mb).translate(-0.3515625F, 0.234375F, 0.0F).rotateX(o.arm * 0.9F + 0.1F).rotateZ(0.12F).translate(0.0F, -0.234375F, 0.0F);
      skinBox(v, ar, 4.0F, 12.0F, 4.0F, 0.0F, 40, 16, tint, light);
      skinBox(v, ar, 4.0F, 12.0F, 4.0F, 0.25F, 40, 32, tint, light);
      Matrix4f al = new Matrix4f(mb).translate(0.3515625F, 0.234375F, 0.0F).rotateX(o.arm).rotateZ(-0.12F).translate(0.0F, -0.234375F, 0.0F);
      skinBox(v, al, 4.0F, 12.0F, 4.0F, 0.0F, 32, 48, tint, light);
      skinBox(v, al, 4.0F, 12.0F, 4.0F, 0.25F, 48, 48, tint, light);
      Matrix4f lr = new Matrix4f(mb).translate(-0.1171875F, -0.3515625F, 0.0F).rotateX(o.leg * 0.75F).translate(0.0F, -0.3515625F, 0.0F);
      skinBox(v, lr, 4.0F, 12.0F, 4.0F, 0.0F, 0, 16, tint, light);
      skinBox(v, lr, 4.0F, 12.0F, 4.0F, 0.25F, 0, 32, tint, light);
      Matrix4f ll = new Matrix4f(mb).translate(0.1171875F, -0.3515625F, 0.0F).rotateX(o.leg).translate(0.0F, -0.3515625F, 0.0F);
      skinBox(v, ll, 4.0F, 12.0F, 4.0F, 0.0F, 16, 48, tint, light);
      skinBox(v, ll, 4.0F, 12.0F, 4.0F, 0.25F, 0, 48, tint, light);
   }

   static void skinBox(VertexConsumer vc, Matrix4f m, float w, float h, float d, float infl, int u, int v, int col, int light) {
      float hx = (w / 2.0F + infl) * 0.05859375F;
      float hy = (h / 2.0F + infl) * 0.05859375F;
      float hz = (d / 2.0F + infl) * 0.05859375F;
      float k = 0.015625F;
      face(vc, m, -hx, hy, hz, hx, hy, hz, hx, -hy, hz, -hx, -hy, hz, (u + d) * k, (v + d) * k, (u + d + w) * k, (v + d + h) * k, 0.0F, 0.0F, 1.0F, col, light);
      face(
         vc,
         m,
         hx,
         hy,
         -hz,
         -hx,
         hy,
         -hz,
         -hx,
         -hy,
         -hz,
         hx,
         -hy,
         -hz,
         (u + 2.0F * d + w) * k,
         (v + d) * k,
         (u + 2.0F * d + 2.0F * w) * k,
         (v + d + h) * k,
         0.0F,
         0.0F,
         -1.0F,
         col,
         light
      );
      face(vc, m, -hx, hy, -hz, -hx, hy, hz, -hx, -hy, hz, -hx, -hy, -hz, u * k, (v + d) * k, (u + d) * k, (v + d + h) * k, -1.0F, 0.0F, 0.0F, col, light);
      face(
         vc,
         m,
         hx,
         hy,
         hz,
         hx,
         hy,
         -hz,
         hx,
         -hy,
         -hz,
         hx,
         -hy,
         hz,
         (u + d + w) * k,
         (v + d) * k,
         (u + 2.0F * d + w) * k,
         (v + d + h) * k,
         1.0F,
         0.0F,
         0.0F,
         col,
         light
      );
      face(vc, m, -hx, hy, -hz, hx, hy, -hz, hx, hy, hz, -hx, hy, hz, (u + d) * k, v * k, (u + d + w) * k, (v + d) * k, 0.0F, 1.0F, 0.0F, col, light);
      face(
         vc,
         m,
         -hx,
         -hy,
         hz,
         hx,
         -hy,
         hz,
         hx,
         -hy,
         -hz,
         -hx,
         -hy,
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

   static void face(
      VertexConsumer vc,
      Matrix4f m,
      float x0,
      float y0,
      float z0,
      float x1,
      float y1,
      float z1,
      float x2,
      float y2,
      float z2,
      float x3,
      float y3,
      float z3,
      float u0,
      float v0,
      float u1,
      float v1,
      float nx,
      float ny,
      float nz,
      int col,
      int light
   ) {
      Vector3f n = m.transformDirection(new Vector3f(nx, ny, nz)).normalize();
      vert(vc, m, x0, y0, z0, u0, v0, col, light, n);
      vert(vc, m, x1, y1, z1, u1, v0, col, light, n);
      vert(vc, m, x2, y2, z2, u1, v1, col, light, n);
      vert(vc, m, x3, y3, z3, u0, v1, col, light, n);
   }

   static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int col, int light, Vector3f n) {
      vc.addVertex(m, x, y, z).setColor(col).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
   }

   static void cube(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int col, int light) {
      float u = 0.5F;
      face(vc, m, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, u, u, u, u, 0.0F, 0.0F, 1.0F, col, light);
      face(vc, m, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, u, u, u, u, 0.0F, 0.0F, -1.0F, col, light);
      face(vc, m, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, u, u, u, u, -1.0F, 0.0F, 0.0F, col, light);
      face(vc, m, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, u, u, u, u, 1.0F, 0.0F, 0.0F, col, light);
      face(vc, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, u, u, u, u, 0.0F, 1.0F, 0.0F, col, light);
      face(vc, m, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, u, u, u, u, 0.0F, -1.0F, 0.0F, col, light);
   }

   static void tri(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, int col, int light) {
      float e1x = bx - ax;
      float e1y = by - ay;
      float e1z = bz - az;
      float e2x = cx - ax;
      float e2y = cy - ay;
      float e2z = cz - az;
      Vector3f n = m.transformDirection(new Vector3f(e1y * e2z - e1z * e2y, e1z * e2x - e1x * e2z, e1x * e2y - e1y * e2x)).normalize();
      vert(vc, m, ax, ay, az, 0.5F, 0.5F, col, light, n);
      vert(vc, m, bx, by, bz, 0.5F, 0.5F, col, light, n);
      vert(vc, m, cx, cy, cz, 0.5F, 0.5F, col, light, n);
      vert(vc, m, cx, cy, cz, 0.5F, 0.5F, col, light, n);
   }

   static void quadW(
      VertexConsumer vc,
      Matrix4f m,
      float ax,
      float ay,
      float az,
      float bx,
      float by,
      float bz,
      float cx,
      float cy,
      float cz,
      float dx,
      float dy,
      float dz,
      int col,
      int light
   ) {
      Vector3f n = m.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
      vert(vc, m, ax, ay, az, 0.5F, 0.5F, col, light, n);
      vert(vc, m, bx, by, bz, 0.5F, 0.5F, col, light, n);
      vert(vc, m, cx, cy, cz, 0.5F, 0.5F, col, light, n);
      vert(vc, m, dx, dy, dz, 0.5F, 0.5F, col, light, n);
   }

   static void gv(VertexConsumer g, Matrix4f m, float x, float y, float z, int argb) {
      g.addVertex(m, x, y, z).setColor(argb);
   }

   static void gquad(VertexConsumer g, Matrix4f m, float[] p, int c0, int c1, int c2, int c3) {
      gv(g, m, p[0], p[1], p[2], c0);
      gv(g, m, p[3], p[4], p[5], c1);
      gv(g, m, p[6], p[7], p[8], c2);
      gv(g, m, p[9], p[10], p[11], c3);
      gv(g, m, p[9], p[10], p[11], c3);
      gv(g, m, p[6], p[7], p[8], c2);
      gv(g, m, p[3], p[4], p[5], c1);
      gv(g, m, p[0], p[1], p[2], c0);
   }

   static void disc(VertexConsumer g, Matrix4f m, float cx, float y, float cz, float r0, float r1, int cIn, float aIn, int cOut, float aOut, int seg) {
      if (!(aIn <= 0.003F) || !(aOut <= 0.003F)) {
         int ci = argb(cIn, aIn);
         int co = argb(cOut, aOut);

         for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2.0 / seg;
            double a1 = (i + 1) * Math.PI * 2.0 / seg;
            float c0 = (float)Math.cos(a0);
            float s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1);
            float s1 = (float)Math.sin(a1);
            GP[0] = cx + c0 * r0;
            GP[1] = y;
            GP[2] = cz + s0 * r0;
            GP[3] = cx + c1 * r0;
            GP[4] = y;
            GP[5] = cz + s1 * r0;
            GP[6] = cx + c1 * r1;
            GP[7] = y;
            GP[8] = cz + s1 * r1;
            GP[9] = cx + c0 * r1;
            GP[10] = y;
            GP[11] = cz + s0 * r1;
            gquad(g, m, GP, ci, ci, co, co);
         }
      }
   }

   static void strip(VertexConsumer g, Matrix4f m, float x0, float z0, float x1, float z1, float y, float w, int rgb, float a) {
      float dx = x1 - x0;
      float dz = z1 - z0;
      float l = (float)Math.sqrt(dx * dx + dz * dz);
      if (!(l < 1.0E-5F)) {
         float nx = -dz / l * w / 2.0F;
         float nz = dx / l * w / 2.0F;
         int c = argb(rgb, a);
         GP[0] = x0 + nx;
         GP[1] = y;
         GP[2] = z0 + nz;
         GP[3] = x1 + nx;
         GP[4] = y;
         GP[5] = z1 + nz;
         GP[6] = x1 - nx;
         GP[7] = y;
         GP[8] = z1 - nz;
         GP[9] = x0 - nx;
         GP[10] = y;
         GP[11] = z0 - nz;
         gquad(g, m, GP, c, c, c, c);
      }
   }

   static void ringAround(VertexConsumer g, Matrix4f m, float cx, float cy, float cz, float dx, float dy, float dz, float r, float width, int rgb, float a) {
      float l = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (!(l < 1.0E-6F)) {
         dx /= l;
         dy /= l;
         dz /= l;
         float e1x = -dz;
         float e1y = 0.0F;
         float el = (float)Math.sqrt(e1x * e1x + dx * dx);
         float e1z;
         if (el < 1.0E-4F) {
            e1x = 1.0F;
            e1z = 0.0F;
         } else {
            e1x /= el;
            e1z = dx / el;
         }

         float e2x = dy * e1z - dz * e1y;
         float e2y = dz * e1x - dx * e1z;
         float e2z = dx * e1y - dy * e1x;
         int ci = argb(rgb, 0.0F);
         int cm = argb(rgb, a);
         int seg = 40;
         float r0 = r - width;
         float r1 = r;

         for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2.0 / seg;
            double a1 = (i + 1) * Math.PI * 2.0 / seg;
            float c0 = (float)Math.cos(a0);
            float s0 = (float)Math.sin(a0);
            float c1 = (float)Math.cos(a1);
            float s1 = (float)Math.sin(a1);
            float ox0 = e1x * c0 + e2x * s0;
            float oy0 = e1y * c0 + e2y * s0;
            float oz0 = e1z * c0 + e2z * s0;
            float ox1 = e1x * c1 + e2x * s1;
            float oy1 = e1y * c1 + e2y * s1;
            float oz1 = e1z * c1 + e2z * s1;
            GP[0] = cx + ox0 * r0;
            GP[1] = cy + oy0 * r0;
            GP[2] = cz + oz0 * r0;
            GP[3] = cx + ox1 * r0;
            GP[4] = cy + oy1 * r0;
            GP[5] = cz + oz1 * r0;
            GP[6] = cx + ox1 * r1;
            GP[7] = cy + oy1 * r1;
            GP[8] = cz + oz1 * r1;
            GP[9] = cx + ox0 * r1;
            GP[10] = cy + oy0 * r1;
            GP[11] = cz + oz0 * r1;
            gquad(g, m, GP, ci, ci, cm, cm);
         }
      }
   }

   static void sprite(
      VertexConsumer vc,
      Matrix4f base,
      DeathFx.Fx f,
      Vec3 cam,
      float lx,
      float ly,
      float lz,
      float size,
      int k,
      int rgb,
      float a,
      float rx,
      float ry,
      float rz,
      float ux,
      float uy,
      float uz
   ) {
      if (!(a <= 0.02F)) {
         float c = (float)Math.cos(-f.yaw);
         float s = (float)Math.sin(-f.yaw);
         float wx = (float)(f.ox - cam.x) + lx * c + lz * s;
         float wy = (float)(f.oy - cam.y) + ly;
         float wz = (float)(f.oz - cam.z) - lx * s + lz * c;
         float h = size / 2.0F;
         int col = argb(rgb, a);
         float u0 = k * 0.25F;
         float u1 = u0 + 0.25F;
         float[] p = new float[]{
            wx - rx * h - ux * h,
            wy - ry * h - uy * h,
            wz - rz * h - uz * h,
            wx + rx * h - ux * h,
            wy + ry * h - uy * h,
            wz + rz * h - uz * h,
            wx + rx * h + ux * h,
            wy + ry * h + uy * h,
            wz + rz * h + uz * h,
            wx - rx * h + ux * h,
            wy - ry * h + uy * h,
            wz - rz * h + uz * h
         };
         float[][] uv = new float[][]{{u0, 1.0F}, {u1, 1.0F}, {u1, 0.0F}, {u0, 0.0F}};

         for (int i = 0; i < 4; i++) {
            sv(vc, base, p, i, uv, col);
         }

         for (int i = 3; i >= 0; i--) {
            sv(vc, base, p, i, uv, col);
         }
      }
   }

   static void sv(VertexConsumer vc, Matrix4f base, float[] p, int i, float[][] uv, int col) {
      vc.addVertex(base, p[i * 3], p[i * 3 + 1], p[i * 3 + 2])
         .setColor(col)
         .setUv(uv[i][0], uv[i][1])
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(15728880)
         .setNormal(0.0F, 1.0F, 0.0F);
   }

   static int light(Minecraft mc, double x, double y, double z) {
      try {
         BlockPos bp = BlockPos.containing(x, y, z);
         return LightTexture.pack(mc.level.getBrightness(LightLayer.BLOCK, bp), mc.level.getBrightness(LightLayer.SKY, bp));
      } catch (Throwable var8) {
         return 15728880;
      }
   }

   static float shade(int rgb, float k) {
      int r = Math.min(255, Math.round((rgb >> 16 & 0xFF) * k));
      int g = Math.min(255, Math.round((rgb >> 8 & 0xFF) * k));
      int b = Math.min(255, Math.round((rgb & 0xFF) * k));
      return Float.intBitsToFloat(r << 16 | g << 8 | b);
   }

   static int mix(int a, int b, float k) {
      k = cl(k);
      int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * k);
      int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * k);
      int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
      return 0xFF000000 | r << 16 | g << 8 | bl;
   }

   static int argb(int rgb, float a) {
      return Math.round(cl(a) * 255.0F) << 24 | rgb & 16777215;
   }

   static float cl(float x) {
      return Math.max(0.0F, Math.min(1.0F, x));
   }

   static float sm(float a, float b, float x) {
      float t = cl((x - a) / (b - a));
      return t * t * (3.0F - 2.0F * t);
   }

   static float eo(float x) {
      float t = 1.0F - cl(x);
      return 1.0F - t * t * t;
   }

   static float ei(float x) {
      float t = cl(x);
      return t * t * t;
   }

   static float lerp(float a, float b, float k) {
      return a + (b - a) * k;
   }

   static {
      float t = 0.0F;

      for (int i = 0; i < TT.length; i++) {
         TT[i] = t;
         float r = i / 1000.0F;
         t += (1.0F - 0.83F * sm(1.8F, 1.97F, r) * (1.0F - sm(2.85F, 3.1F, r))) / 1000.0F;
      }

      List<float[]> pts = new ArrayList<>();

      for (int i = 0; i <= 24; i++) {
         pts.add(new float[]{1.7F, -7.0F + 7.0F * i / 24.0F, 0.85F});
      }

      float cx = 1.5F;
      float cy = 1.45F;
      float cz = 0.75F;

      for (int i = 1; i <= 80; i++) {
         float u = i / 80.0F;
         float a = (1.0F - u) * (1.0F - u);
         float b = 2.0F * u * (1.0F - u);
         float c = u * u;
         pts.add(new float[]{a * 1.7F + b * cx + c * 0.0F, a * 0.0F + b * cy + c * 3.3F, a * 0.85F + b * cz + c * 0.0F});
      }

      float dx = 0.0F - cx;
      float dy = 3.3F - cy;
      float dz = 0.0F - cz;
      float dl = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      DX = dx / dl;
      DY = dy / dl;
      DZ = dz / dl;

      for (int i = 1; i <= 30; i++) {
         float k = 4.0F * i / 30.0F;
         pts.add(new float[]{0.0F + DX * k, 3.3F + DY * k, 0.0F + DZ * k});
      }

      int n = pts.size();
      PX = new float[n];
      PY = new float[n];
      PZ = new float[n];
      PS = new float[n];
      float s = 0.0F;
      float sb = 0.0F;
      float sh = 0.0F;

      for (int i = 0; i < n; i++) {
         float[] p = pts.get(i);
         if (i > 0) {
            float ex = p[0] - PX[i - 1];
            float ey = p[1] - PY[i - 1];
            float ez = p[2] - PZ[i - 1];
            s += (float)Math.sqrt(ex * ex + ey * ey + ez * ez);
         }

         PX[i] = p[0];
         PY[i] = p[1];
         PZ[i] = p[2];
         PS[i] = s;
         if (i == 24) {
            sh = s;
         }

         if (i == 104) {
            sb = s;
         }
      }

      SB = sb;
      SH = sh;
      SEND = SB + 0.4F;
      float tc = 3.0F;
      float[] o = new float[3];

      for (float tx = 2.32F; tx < 4.0F; tx += 0.002F) {
         pathPoint(sBody(tx), o);
         if (o[1] < 0.35F) {
            tc = tx;
            break;
         }
      }

      TC = tc;
      TA = new float[3];
      TB = new float[3];
      ACTIVE = new ArrayList<>();
      TRIGGERED = new HashSet<>();
      SEEN = new HashSet<>();
      HITS = new HashMap<>();
      POSE = new DeathFx.Pose();
      Q = new float[3];
      Q2 = new float[3];
      AX = new float[6];
      GP = new float[12];
   }

   static final class Fx {
      int id;
      boolean self;
      boolean test;
      double ox;
      double oy;
      double oz;
      float yaw;
      Identifier skin;
      int kind = 1;
      Object extra;
      long start;
      int groundCol;
      int dirtCol;
      float[][] cracks;
      float[][] chunks;
      float[][] ring;
      float[][] burst;
      float[][] embers;
      float[][] wisps;
      float[][] dust;
   }

   static final class Pose {
      final float[] p = new float[3];
      float rx;
      float ry;
      float rz;
      float arm;
      float leg;
      float head;
      float e;
   }
}
