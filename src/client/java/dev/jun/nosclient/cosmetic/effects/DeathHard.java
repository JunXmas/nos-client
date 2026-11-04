package dev.jun.nosclient.cosmetic.effects;

import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.jun.nosclient.sky.SkyTextures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

final class DeathHard {
    static final int FIRST = 3;
    static final float[] END = new float[] {6.4F};
    private static final float[][][] SLOW =
            new float[][][] {{{1.6F, 2.0F, 0.22F}, {3.45F, 3.7F, 0.45F}}};
    static final float[][] TT = new float[END.length][];
    static final int[] BLOOD;
    static final int[] DIRT;
    static final int[][] UV;
    static final int[][] DIM;
    static final int[] BOTTOM;
    static final float[] INFL;

    private DeathHard() {}

    private static float[] table(float[][] slow, float end) {
        List<Float> out = new ArrayList<>();
        float t = 0.0F;

        while (t < end + 0.05F && out.size() < 30000) {
            out.add(t);
            float k = 1.0F;

            for (float[] w : slow) {
                float e =
                        DeathFx.sm(w[0] - 0.08F, w[0], t)
                                * (1.0F - DeathFx.sm(w[1], w[1] + 0.12F, t));
                k = Math.min(k, DeathFx.lerp(1.0F, w[2], e));
            }

            t += k / 1000.0F;
        }

        float[] a = new float[out.size()];

        for (int i = 0; i < a.length; i++) {
            a[i] = out.get(i);
        }

        return a;
    }

    static Object create(DeathFx.Fx f, Random r) {
        return new DeathHard.Tentacles(r);
    }

    static void draw(
            Minecraft mc,
            MultiBufferSource buffers,
            Matrix4f base,
            Vec3 cam,
            DeathFx.Fx f,
            float t) {
        if (f.extra instanceof DeathHard.Anim s && Accessories.whiteTex()) {
            DeathHard.Ctx c = new DeathHard.Ctx();
            c.mc = mc;
            c.base = base;
            c.cam = cam;
            c.f = f;
            c.m =
                    new Matrix4f(base)
                            .translate(
                                    (float) (f.ox - cam.x),
                                    (float) (f.oy - cam.y),
                                    (float) (f.oz - cam.z))
                            .rotateY(-f.yaw);
            c.light = DeathFx.light(mc, f.ox, f.oy + 1.0, f.oz);
            c.firstPerson = f.self && mc.options.getCameraType().isFirstPerson();
            float dt = t - s.lastT;
            if (dt < 0.0F || dt > 0.5F) {
                dt = 0.0F;
            }

            s.lastT = t;

            while (dt > 1.0E-5F) {
                float st = Math.min(dt, 0.016666668F);
                s.tick(s.simT + st, st);
                s.simT += st;
                dt -= st;
            }

            s.simT = t;
            s.pose(t);
            VertexConsumer g = buffers.getBuffer(RenderTypes.debugQuads());
            s.glow(c, g, t);
            s.drawPools(g, c.m, t);
            VertexConsumer w = buffers.getBuffer(RenderTypes.entityCutoutNoCull(Accessories.WHITE));
            s.solid(c, w, t);
            if (s.blood != null) {
                s.blood.draw(w, c.m, s.fade(t), c.light);
            }

            if (s.dirt != null) {
                s.dirt.draw(w, c.m, s.fade(t), c.light);
            }

            if (f.skin != null && !c.firstPerson) {
                VertexConsumer sk = buffers.getBuffer(RenderTypes.entityCutoutNoCull(f.skin));
                s.skin(c, sk, t);
            }

            if (SkyTextures.spritesReady()) {
                VertexConsumer sp = buffers.getBuffer(RenderTypes.text(SkyTextures.SPRITES));
                s.sprites(c, sp, t);
            }
        }
    }

    static void poolDisc(
            VertexConsumer g,
            Matrix4f m,
            float cx,
            float cz,
            float rad,
            float k,
            float a,
            int seed) {
        if (!(k <= 0.01F) && !(a <= 0.01F)) {
            int seg = 18;
            float[] p = new float[12];

            for (int i = 0; i < seg; i++) {
                float r0 = rad * k * (0.7F + 0.5F * hash(seed + i));
                float r1 = rad * k * (0.7F + 0.5F * hash(seed + (i + 1) % seg));
                double a0 = i * Math.PI * 2.0 / seg;
                double a1 = (i + 1) * Math.PI * 2.0 / seg;
                p[0] = cx;
                p[1] = 0.016F;
                p[2] = cz;
                p[3] = cx;
                p[4] = 0.016F;
                p[5] = cz;
                p[6] = cx + (float) Math.cos(a1) * r1;
                p[7] = 0.016F;
                p[8] = cz + (float) Math.sin(a1) * r1;
                p[9] = cx + (float) Math.cos(a0) * r0;
                p[10] = 0.016F;
                p[11] = cz + (float) Math.sin(a0) * r0;
                DeathFx.gquad(
                        g,
                        m,
                        p,
                        DeathFx.argb(7999004, a * 0.95F),
                        DeathFx.argb(7999004, a * 0.95F),
                        DeathFx.argb(4851730, a * 0.92F),
                        DeathFx.argb(4851730, a * 0.92F));
            }
        }
    }

    static float hash(int i) {
        int h = i * 374761393 + 668265263;
        h = (h ^ h >>> 13) * 1274126177;
        return ((h ^ h >>> 16) & 65535) / 65535.0F;
    }

    static Matrix4f part(DeathHard.Rig g, int i, Matrix4f out) {
        out.identity()
                .translate(g.px, g.py, g.pz)
                .rotateY(g.ry)
                .rotateX(g.rx)
                .rotateZ(g.rz)
                .translate(0.0F, -1.0F, 0.0F);
        switch (i) {
            case 0:
                out.translate(-0.1171875F, 0.703125F, 0.0F)
                        .rotateX(g.legR[0])
                        .rotateY(g.legR[1])
                        .rotateZ(g.legR[2])
                        .translate(0.0F, -0.3515625F, 0.0F);
                break;
            case 1:
            default:
                out.translate(0.1171875F, 0.703125F, 0.0F)
                        .rotateX(g.legL[0])
                        .rotateY(g.legL[1])
                        .rotateZ(g.legL[2])
                        .translate(0.0F, -0.3515625F, 0.0F);
                break;
            case 2:
                out.translate(0.0F, 1.0546875F, 0.0F).scale(1.0F, 1.0F, g.belly);
                break;
            case 3:
                out.translate(-0.3515625F, 1.2890625F, 0.0F)
                        .rotateX(g.armR[0])
                        .rotateY(g.armR[1])
                        .rotateZ(g.armR[2])
                        .translate(0.0F, -0.234375F, 0.0F);
                break;
            case 4:
                out.translate(0.3515625F, 1.2890625F, 0.0F)
                        .rotateX(g.armL[0])
                        .rotateY(g.armL[1])
                        .rotateZ(g.armL[2])
                        .translate(0.0F, -0.234375F, 0.0F);
                break;
            case 5:
                out.translate(0.0F, 1.40625F, 0.0F)
                        .rotateX(g.head[0])
                        .rotateY(g.head[1])
                        .rotateZ(g.head[2])
                        .translate(0.0F, 0.234375F, 0.0F);
        }

        return out;
    }

    static void rig(
            VertexConsumer sk, Matrix4f m, DeathHard.Rig g, int tint, int light, int lo, int hi) {
        Matrix4f tmp = new Matrix4f();

        for (int i = 0; i < 6; i++) {
            int b = BOTTOM[i];
            int h = DIM[i][1];
            float y0 = Math.max(0, lo - b);
            float y1 = Math.min(h, hi - b);
            if (!(y0 >= y1)) {
                Matrix4f pm = new Matrix4f(m).mul(part(g, i, tmp));
                sbox(
                        sk, pm, DIM[i][0], h, DIM[i][2], 0.0F, UV[i][0], UV[i][1], y0, y1, tint,
                        light);
                sbox(
                        sk, pm, DIM[i][0], h, DIM[i][2], INFL[i], UV[i][2], UV[i][3], y0, y1, tint,
                        light);
            }
        }
    }

    static void caps(VertexConsumer w, Matrix4f m, DeathHard.Rig g, int cut, int col) {
        Matrix4f tmp = new Matrix4f();

        for (int i = 0; i < 6; i++) {
            int b = BOTTOM[i];
            int h = DIM[i][1];
            if (cut > b && cut < b + h) {
                Matrix4f pm = new Matrix4f(m).mul(part(g, i, tmp));
                float y = (cut - b - h / 2.0F) * 0.05859375F;
                float hx = DIM[i][0] / 2.0F * 0.05859375F * 0.98F;
                float hz = DIM[i][2] / 2.0F * 0.05859375F * 0.98F;
                DeathFx.face(
                        w, pm, -hx, y, -hz, hx, y, -hz, hx, y, hz, -hx, y, hz, 0.5F, 0.5F, 0.5F,
                        0.5F, 0.0F, 1.0F, 0.0F, col, 15728880);
                DeathFx.face(
                        w, pm, -hx, y, hz, hx, y, hz, hx, y, -hz, -hx, y, -hz, 0.5F, 0.5F, 0.5F,
                        0.5F, 0.0F, -1.0F, 0.0F, col, 15728880);
            }
        }
    }

    static void sbox(
            VertexConsumer vc,
            Matrix4f m,
            float w,
            float h,
            float d,
            float infl,
            int u,
            int v,
            float y0,
            float y1,
            int col,
            int light) {
        float hx = (w / 2.0F + infl) * 0.05859375F;
        float hz = (d / 2.0F + infl) * 0.05859375F;
        float yb = (y0 - h / 2.0F) * 0.05859375F - (y0 <= 0.0F ? infl * 0.05859375F : 0.0F);
        float yt = (y1 - h / 2.0F) * 0.05859375F + (y1 >= h ? infl * 0.05859375F : 0.0F);
        float k = 0.015625F;
        float vt = (v + d + (h - y1)) * k;
        float vb = (v + d + (h - y0)) * k;
        DeathFx.face(
                vc,
                m,
                -hx,
                yt,
                hz,
                hx,
                yt,
                hz,
                hx,
                yb,
                hz,
                -hx,
                yb,
                hz,
                (u + d) * k,
                vt,
                (u + d + w) * k,
                vb,
                0.0F,
                0.0F,
                1.0F,
                col,
                light);
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
                light);
        DeathFx.face(
                vc,
                m,
                -hx,
                yt,
                -hz,
                -hx,
                yt,
                hz,
                -hx,
                yb,
                hz,
                -hx,
                yb,
                -hz,
                u * k,
                vt,
                (u + d) * k,
                vb,
                -1.0F,
                0.0F,
                0.0F,
                col,
                light);
        DeathFx.face(
                vc,
                m,
                hx,
                yt,
                hz,
                hx,
                yt,
                -hz,
                hx,
                yb,
                -hz,
                hx,
                yb,
                hz,
                (u + d + w) * k,
                vt,
                (u + 2.0F * d + w) * k,
                vb,
                1.0F,
                0.0F,
                0.0F,
                col,
                light);
        if (y1 >= h) {
            DeathFx.face(
                    vc,
                    m,
                    -hx,
                    yt,
                    -hz,
                    hx,
                    yt,
                    -hz,
                    hx,
                    yt,
                    hz,
                    -hx,
                    yt,
                    hz,
                    (u + d) * k,
                    v * k,
                    (u + d + w) * k,
                    (v + d) * k,
                    0.0F,
                    1.0F,
                    0.0F,
                    col,
                    light);
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
                    light);
        }
    }

    static void voxel(
            VertexConsumer vc,
            Matrix4f m,
            int part,
            float cx,
            float cy,
            float cz,
            float s,
            int tint,
            int light) {
        int w = DIM[part][0];
        int h = DIM[part][1];
        int d = DIM[part][2];
        int u = UV[part][0];
        int v = UV[part][1];
        float k = 0.015625F;
        float hs = s / 2.0F * 0.05859375F;
        float fu = u + d + (cx - s / 2.0F + w / 2.0F);
        float fv = v + d + (h / 2.0F - (cy + s / 2.0F));
        DeathFx.face(
                vc,
                m,
                -hs,
                hs,
                hs,
                hs,
                hs,
                hs,
                hs,
                -hs,
                hs,
                -hs,
                -hs,
                hs,
                fu * k,
                fv * k,
                (fu + s) * k,
                (fv + s) * k,
                0.0F,
                0.0F,
                1.0F,
                tint,
                light);
        float bu = u + 2 * d + w + (w / 2.0F - (cx + s / 2.0F));
        DeathFx.face(
                vc,
                m,
                hs,
                hs,
                -hs,
                -hs,
                hs,
                -hs,
                -hs,
                -hs,
                -hs,
                hs,
                -hs,
                -hs,
                bu * k,
                fv * k,
                (bu + s) * k,
                (fv + s) * k,
                0.0F,
                0.0F,
                -1.0F,
                tint,
                light);
        float ru = u + (cz - s / 2.0F + d / 2.0F);
        DeathFx.face(
                vc,
                m,
                -hs,
                hs,
                -hs,
                -hs,
                hs,
                hs,
                -hs,
                -hs,
                hs,
                -hs,
                -hs,
                -hs,
                ru * k,
                fv * k,
                (ru + s) * k,
                (fv + s) * k,
                -1.0F,
                0.0F,
                0.0F,
                tint,
                light);
        float lu = u + d + w + (d / 2.0F - (cz + s / 2.0F));
        DeathFx.face(
                vc,
                m,
                hs,
                hs,
                hs,
                hs,
                hs,
                -hs,
                hs,
                -hs,
                -hs,
                hs,
                -hs,
                hs,
                lu * k,
                fv * k,
                (lu + s) * k,
                (fv + s) * k,
                1.0F,
                0.0F,
                0.0F,
                tint,
                light);
        float tu = u + d + (cx - s / 2.0F + w / 2.0F);
        float tv = v + (cz - s / 2.0F + d / 2.0F);
        DeathFx.face(
                vc,
                m,
                -hs,
                hs,
                -hs,
                hs,
                hs,
                -hs,
                hs,
                hs,
                hs,
                -hs,
                hs,
                hs,
                tu * k,
                tv * k,
                (tu + s) * k,
                (tv + s) * k,
                0.0F,
                1.0F,
                0.0F,
                tint,
                light);
        float ou = u + d + w + (cx - s / 2.0F + w / 2.0F);
        float ov = v + (d / 2.0F - (cz + s / 2.0F));
        DeathFx.face(
                vc,
                m,
                -hs,
                -hs,
                hs,
                hs,
                -hs,
                hs,
                hs,
                -hs,
                -hs,
                -hs,
                -hs,
                -hs,
                ou * k,
                ov * k,
                (ou + s) * k,
                (ov + s) * k,
                0.0F,
                -1.0F,
                0.0F,
                tint,
                light);
    }

    static List<DeathHard.Vox> voxelize(DeathHard.Rig g, int step) {
        List<DeathHard.Vox> out = new ArrayList<>();
        Matrix4f tmp = new Matrix4f();

        for (int i = 0; i < 6; i++) {
            part(g, i, tmp);
            Quaternionf q = tmp.getNormalizedRotation(new Quaternionf());
            int x = 0;

            while (x < DIM[i][0]) {
                for (int y = 0; y < DIM[i][1]; y += step) {
                    for (int z = 0; z < DIM[i][2]; z += step) {
                        DeathHard.Vox v = new DeathHard.Vox();
                        v.part = i;
                        v.cx = x + step / 2.0F - DIM[i][0] / 2.0F;
                        v.cy = y + step / 2.0F - DIM[i][1] / 2.0F;
                        v.cz = z + step / 2.0F - DIM[i][2] / 2.0F;
                        tmp.transformPosition(
                                v.cx * 0.05859375F, v.cy * 0.05859375F, v.cz * 0.05859375F, v.p);
                        v.q.set(q);
                        out.add(v);
                    }
                }

                x += step;
            }
        }

        return out;
    }

    static void drawVox(
            VertexConsumer sk, Matrix4f m, DeathHard.Vox v, float s, float scale, int light) {
        if (!(scale <= 0.01F)) {
            Matrix4f vm =
                    new Matrix4f(m)
                            .translate(v.p)
                            .rotate(v.q)
                            .rotateXYZ(v.r.x, v.r.y, v.r.z)
                            .scale(scale);
            voxel(sk, vm, v.part, v.cx, v.cy, v.cz, s, v.tint, light);
        }
    }

    static boolean fly(
            Vector3f p, Vector3f v, Vector3f r, Vector3f w, float dt, float floor, float g) {
        v.y -= g * dt;
        p.add(v.x * dt, v.y * dt, v.z * dt);
        r.add(w.x * dt, w.y * dt, w.z * dt);
        if (p.y < floor) {
            p.y = floor;
            if (v.y < 0.0F) {
                v.y *= -0.3F;
            }

            v.x *= 0.55F;
            v.z *= 0.55F;
            w.mul(0.55F);
            return true;
        } else {
            return false;
        }
    }

    static void gline(
            VertexConsumer g,
            Matrix4f m,
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz,
            float w,
            int rgb,
            float a) {
        if (!(a <= 0.003F)) {
            float dx = bx - ax;
            float dy = by - ay;
            float dz = bz - az;
            float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!(l < 1.0E-5F)) {
                dx /= l;
                dy /= l;
                dz /= l;
                float ux = 0.0F;
                float uy = 1.0F;
                float uz = 0.0F;
                if (Math.abs(dy) > 0.9F) {
                    ux = 1.0F;
                    uy = 0.0F;
                }

                float n1x = dy * uz - dz * uy;
                float n1y = dz * ux - dx * uz;
                float n1z = dx * uy - dy * ux;
                float nl = (float) Math.sqrt(n1x * n1x + n1y * n1y + n1z * n1z);
                n1x /= nl;
                n1y /= nl;
                n1z /= nl;
                float n2x = dy * n1z - dz * n1y;
                float n2y = dz * n1x - dx * n1z;
                float n2z = dx * n1y - dy * n1x;
                int c = DeathFx.argb(rgb, a);
                float h = w / 2.0F;
                float[] p = new float[12];

                for (int k = 0; k < 2; k++) {
                    float ex = k == 0 ? n1x : n2x;
                    float ey = k == 0 ? n1y : n2y;
                    float ez = k == 0 ? n1z : n2z;
                    p[0] = ax + ex * h;
                    p[1] = ay + ey * h;
                    p[2] = az + ez * h;
                    p[3] = bx + ex * h;
                    p[4] = by + ey * h;
                    p[5] = bz + ez * h;
                    p[6] = bx - ex * h;
                    p[7] = by - ey * h;
                    p[8] = bz - ez * h;
                    p[9] = ax - ex * h;
                    p[10] = ay - ey * h;
                    p[11] = az - ez * h;
                    DeathFx.gquad(g, m, p, c, c, c, c);
                }
            }
        }
    }

    static void gbox(VertexConsumer g, Matrix4f m, float hx, float hy, float hz, int argbCol) {
        float[][] f =
                new float[][] {
                    {-hx, hy, hz, hx, hy, hz, hx, -hy, hz, -hx, -hy, hz},
                    {hx, hy, -hz, -hx, hy, -hz, -hx, -hy, -hz, hx, -hy, -hz},
                    {-hx, hy, -hz, -hx, hy, hz, -hx, -hy, hz, -hx, -hy, -hz},
                    {hx, hy, hz, hx, hy, -hz, hx, -hy, -hz, hx, -hy, hz},
                    {-hx, hy, -hz, hx, hy, -hz, hx, hy, hz, -hx, hy, hz},
                    {-hx, -hy, hz, hx, -hy, hz, hx, -hy, -hz, -hx, -hy, -hz}
                };

        for (float[] q : f) {
            DeathFx.gquad(g, m, q, argbCol, argbCol, argbCol, argbCol);
        }
    }

    static void block(
            VertexConsumer w,
            Matrix4f m,
            float cx,
            float cy,
            float cz,
            float sx,
            float sy,
            float sz,
            int rgb,
            int light) {
        DeathFx.cube(
                w,
                m,
                cx - sx / 2.0F,
                cy - sy / 2.0F,
                cz - sz / 2.0F,
                cx + sx / 2.0F,
                cy + sy / 2.0F,
                cz + sz / 2.0F,
                0xFF000000 | rgb,
                light);
    }

    static float rnd(Random r) {
        return r.nextFloat() - 0.5F;
    }

    static {
        for (int i = 0; i < END.length; i++) {
            TT[i] = table(SLOW[i], END[i]);
        }

        BLOOD = new int[] {3802640, 6949400, 11540526, 16722501};
        DIRT = new int[] {5214008, 6965804, 4160046, 8017460};
        UV =
                new int[][] {
                    {0, 16, 0, 32},
                    {16, 48, 0, 48},
                    {16, 16, 16, 32},
                    {40, 16, 40, 32},
                    {32, 48, 48, 48},
                    {0, 0, 32, 0}
                };
        DIM = new int[][] {{4, 12, 4}, {4, 12, 4}, {8, 12, 4}, {4, 12, 4}, {4, 12, 4}, {8, 8, 8}};
        BOTTOM = new int[] {0, 0, 12, 12, 12, 24};
        INFL = new float[] {0.25F, 0.25F, 0.25F, 0.25F, 0.25F, 0.5F};
    }

    abstract static class Anim {
        final Random r;
        float lastT = 0.0F;
        float simT = 0.0F;
        DeathHard.Blood blood;
        DeathHard.Blood dirt;
        final List<float[]> pools = new ArrayList<>();

        Anim(Random r) {
            this.r = new Random(r.nextLong());
        }

        void tick(float t, float dt) {
            if (this.blood != null) {
                this.blood.step(dt);
            }

            if (this.dirt != null) {
                this.dirt.step(dt);
            }
        }

        abstract void pose(float var1);

        void glow(DeathHard.Ctx c, VertexConsumer g, float t) {}

        void solid(DeathHard.Ctx c, VertexConsumer w, float t) {}

        void skin(DeathHard.Ctx c, VertexConsumer sk, float t) {}

        void sprites(DeathHard.Ctx c, VertexConsumer sp, float t) {}

        float end() {
            return 5.4F;
        }

        float fade(float t) {
            return 1.0F - DeathFx.sm(this.end() - 0.45F, this.end(), t);
        }

        void pool(float x, float z, float rad, float start, float dur) {
            this.pools.add(new float[] {x, z, rad, start, dur, this.r.nextInt(100000)});
        }

        void drawPools(VertexConsumer g, Matrix4f m, float t) {
            float a = this.fade(t);

            for (float[] p : this.pools) {
                DeathHard.poolDisc(
                        g, m, p[0], p[1], p[2], DeathFx.eo((t - p[3]) / p[4]), a, (int) p[5]);
            }
        }

        void spr(
                DeathHard.Ctx c,
                VertexConsumer sp,
                float x,
                float y,
                float z,
                float size,
                int k,
                int rgb,
                float a) {
            DeathFx.sprite(
                    sp,
                    c.base,
                    c.f,
                    c.cam,
                    x,
                    y,
                    z,
                    size,
                    k,
                    rgb,
                    a,
                    DeathFx.AX[0],
                    DeathFx.AX[1],
                    DeathFx.AX[2],
                    DeathFx.AX[3],
                    DeathFx.AX[4],
                    DeathFx.AX[5]);
        }
    }

    static final class Blood {
        final int max;
        final int[] cols;
        int n = 0;
        final float[] x;
        final float[] y;
        final float[] z;
        final float[] vx;
        final float[] vy;
        final float[] vz;
        final float[] s;
        final float[] splat;
        final float[] rot;
        final int[] col;
        final boolean[] down;
        final Random r;

        Blood(int max, Random r, int[] cols) {
            this.max = max;
            this.r = r;
            this.cols = cols;
            this.x = new float[max];
            this.y = new float[max];
            this.z = new float[max];
            this.vx = new float[max];
            this.vy = new float[max];
            this.vz = new float[max];
            this.s = new float[max];
            this.splat = new float[max];
            this.rot = new float[max];
            this.col = new int[max];
            this.down = new boolean[max];
        }

        void spurt(
                float px,
                float py,
                float pz,
                int count,
                float dx,
                float dy,
                float dz,
                float spread,
                float size) {
            int i = 0;

            while (i < count && this.n < this.max) {
                this.x[this.n] = px + (this.r.nextFloat() - 0.5F) * 0.15F;
                this.y[this.n] = py + (this.r.nextFloat() - 0.5F) * 0.15F;
                this.z[this.n] = pz + (this.r.nextFloat() - 0.5F) * 0.15F;
                this.vx[this.n] = dx + (this.r.nextFloat() - 0.5F) * spread;
                this.vy[this.n] = dy + (this.r.nextFloat() - 0.5F) * spread * 0.7F + spread * 0.25F;
                this.vz[this.n] = dz + (this.r.nextFloat() - 0.5F) * spread;
                this.s[this.n] = size * (0.5F + this.r.nextFloat());
                this.splat[this.n] = 1.0F + this.r.nextFloat() * 2.2F;
                this.rot[this.n] = this.r.nextFloat() * 6.28F;
                this.col[this.n] = this.cols[this.r.nextInt(this.cols.length)];
                this.down[this.n] = false;
                i++;
                this.n++;
            }
        }

        void step(float dt) {
            for (int i = 0; i < this.n; i++) {
                if (!this.down[i]) {
                    this.vy[i] = this.vy[i] - 14.0F * dt;
                    this.x[i] = this.x[i] + this.vx[i] * dt;
                    this.y[i] = this.y[i] + this.vy[i] * dt;
                    this.z[i] = this.z[i] + this.vz[i] * dt;
                    if (this.y[i] <= 0.015F) {
                        this.y[i] = 0.015F;
                        this.down[i] = true;
                        this.col[i] = DeathFx.mix(this.col[i], 4851730, 0.45F) & 16777215;
                    }
                }
            }
        }

        void draw(VertexConsumer w, Matrix4f m, float fade, int light) {
            if (!(fade <= 0.01F)) {
                for (int i = 0; i < this.n; i++) {
                    int c = 0xFF000000 | this.col[i];
                    boolean hot = this.col[i] == 16722501;
                    Matrix4f dm =
                            new Matrix4f(m)
                                    .translate(this.x[i], this.y[i], this.z[i])
                                    .rotateY(this.rot[i]);
                    if (!this.down[i]) {
                        float h = this.s[i] / 2.0F * fade;
                        dm.rotateX(this.rot[i] * this.y[i]);
                        DeathFx.cube(w, dm, -h, -h, -h, h, h, h, c, hot ? 15728880 : light);
                    } else {
                        float hx = this.s[i] * this.splat[i] / 2.0F * fade;
                        float hz = this.s[i] * this.splat[i] * 0.4F * fade;
                        DeathFx.cube(w, dm, -hx, -0.004F, -hz, hx, 0.004F, hz, c, light);
                    }
                }
            }
        }
    }

    static final class Ctx {
        Minecraft mc;
        Matrix4f base;
        Matrix4f m;
        Vec3 cam;
        DeathFx.Fx f;
        int light;
        boolean firstPerson;
    }

    static final class Rig {
        float px;
        float py = 1.0F;
        float pz;
        float rx;
        float ry;
        float rz;
        float belly = 1.0F;
        final float[] head = new float[3];
        final float[] armR = new float[3];
        final float[] armL = new float[3];
        final float[] legR = new float[3];
        final float[] legL = new float[3];

        void copy(DeathHard.Rig o) {
            this.px = o.px;
            this.py = o.py;
            this.pz = o.pz;
            this.rx = o.rx;
            this.ry = o.ry;
            this.rz = o.rz;
            this.belly = o.belly;
            System.arraycopy(o.head, 0, this.head, 0, 3);
            System.arraycopy(o.armR, 0, this.armR, 0, 3);
            System.arraycopy(o.armL, 0, this.armL, 0, 3);
            System.arraycopy(o.legR, 0, this.legR, 0, 3);
            System.arraycopy(o.legL, 0, this.legL, 0, 3);
        }

        void zero() {
            this.px = 0.0F;
            this.py = 1.0F;
            this.pz = 0.0F;
            this.rx = this.ry = this.rz = 0.0F;
            this.belly = 1.0F;

            for (float[] a :
                    new float[][] {this.head, this.armR, this.armL, this.legR, this.legL}) {
                Arrays.fill(a, 0.0F);
            }
        }
    }

    static final class Tentacles extends DeathHard.Anim {
        final DeathHard.Rig g = new DeathHard.Rig();
        static final int N = 5;
        static final int SEG = 30;
        final float[] ph = new float[5];
        final float[] sp = new float[5];
        final float[] gx = new float[5];
        final float[] gz = new float[5];
        final boolean[] hit = new boolean[5];
        final Vector3f belly = new Vector3f();
        final Vector3f nrm = new Vector3f();
        boolean burst;
        float inner;
        float throb;
        float tug;
        float pullK;
        float arch;

        Tentacles(Random r0) {
            super(r0);
            this.blood = new DeathHard.Blood(520, this.r, DeathHard.BLOOD);
            this.dirt = new DeathHard.Blood(200, this.r, DeathHard.DIRT);

            for (int i = 0; i < 5; i++) {
                double a = i / 5.0 * Math.PI * 2.0 + 0.4;
                this.gx[i] = (float) Math.cos(a) * 1.35F;
                this.gz[i] = (float) Math.sin(a) * 1.35F;
                this.ph[i] = this.r.nextFloat() * 6.0F;
                this.sp[i] = 0.8F + this.r.nextFloat() * 0.5F;
            }
        }

        @Override
        float end() {
            return 6.4F;
        }

        @Override
        void pose(float t) {
            this.arch = DeathFx.eo(DeathFx.sm(0.8F, 1.55F, t));
            this.pullK = DeathFx.ei(DeathFx.sm(3.45F, 4.9F, t));
            float twitch =
                    DeathFx.sm(0.1F, 0.4F, t)
                            * (1.0F - this.arch)
                            * (this.r.nextFloat() < 0.25F ? 1 : 0);
            this.g.rx = -1.2F * this.arch + DeathHard.rnd(this.r) * 0.15F * twitch;
            this.g.rz = DeathHard.rnd(this.r) * 0.12F * twitch;
            this.tug =
                    t > 3.45F
                            ? 0.12F * Math.max(0.0F, (float) Math.sin((t - 3.45F) * 11.0F))
                            : 0.0F;
            this.g.py = 1.0F + 0.55F * this.arch - this.pullK * 3.4F - this.tug;
            this.g.head[0] = -0.8F * this.arch + DeathHard.rnd(this.r) * 0.4F * twitch;
            float reach = DeathFx.sm(4.1F, 4.5F, t);
            this.g.armR[0] = 0.7F * this.arch * (1.0F - reach);
            this.g.armR[2] =
                    DeathFx.lerp(-1.35F * this.arch, -2.9F, reach)
                            + (float) Math.sin(t * 13.0F) * 0.15F * reach;
            this.g.armL[0] = 0.7F * this.arch * (1.0F - reach);
            this.g.armL[2] =
                    DeathFx.lerp(1.35F * this.arch, 2.9F, reach)
                            - (float) Math.sin(t * 15.0F) * 0.15F * reach;
            this.g.legR[0] = 0.35F * this.arch + DeathHard.rnd(this.r) * 0.3F * twitch;
            this.g.legL[0] = 0.2F * this.arch;
            this.throb =
                    0.5F + 0.5F * (float) Math.sin(t * (6.0F + 10.0F * DeathFx.sm(0.8F, 1.6F, t)));
            this.inner = DeathFx.sm(0.6F, 1.5F, t) * (1.0F - DeathFx.sm(1.6F, 1.8F, t));
            this.g.belly = t < 1.6F ? 1.0F + 0.25F * this.inner * this.throb : 1.0F;
            Matrix4f bm = DeathHard.part(this.g, 2, new Matrix4f());
            bm.transformPosition(0.0F, 0.02F, DeathHard.DIM[2][2] / 2.0F * 0.05859375F, this.belly);
            bm.transformDirection(0.0F, 0.0F, 1.0F, this.nrm).normalize();
        }

        @Override
        void tick(float t, float dt) {
            this.pose(t);
            if (t >= 1.6F && !this.burst) {
                this.burst = true;
                this.blood.spurt(
                        this.belly.x,
                        this.belly.y,
                        this.belly.z,
                        110,
                        this.nrm.x * 4.0F,
                        this.nrm.y * 4.0F,
                        this.nrm.z * 4.0F,
                        3.5F,
                        0.075F);
            }

            if (t > 1.6F && t < 3.4F && this.r.nextFloat() < 0.7F) {
                this.blood.spurt(
                        this.belly.x,
                        this.belly.y,
                        this.belly.z,
                        2,
                        this.nrm.x * 1.6F,
                        this.nrm.y * 1.6F,
                        this.nrm.z * 1.6F,
                        1.6F,
                        0.05F);
            }

            if (t > 3.4F && this.pullK < 0.85F && this.r.nextFloat() < 0.5F) {
                this.blood.spurt(
                        this.belly.x,
                        Math.max(0.05F, this.belly.y),
                        this.belly.z,
                        2,
                        0.0F,
                        1.2F,
                        0.0F,
                        2.0F,
                        0.05F);
            }

            float dive = DeathFx.sm(2.35F, 3.25F, t);

            for (int i = 0; i < 5; i++) {
                if (dive > 0.85F && !this.hit[i]) {
                    this.hit[i] = true;
                    this.dirt.spurt(
                            this.gx[i], 0.05F, this.gz[i], 22, 0.0F, 3.0F, 0.0F, 2.6F, 0.07F);
                }
            }

            if (t > 4.9F && this.pools.isEmpty()) {
                this.pool(0.0F, 0.0F, 1.5F, t, 1.0F);
            }

            super.tick(t, dt);
        }

        @Override
        void glow(DeathHard.Ctx c, VertexConsumer gv, float t) {
            float fade = this.fade(t);
            DeathFx.disc(
                    gv,
                    c.m,
                    0.0F,
                    0.012F,
                    0.0F,
                    0.0F,
                    1.6F,
                    9054936,
                    0.35F * DeathFx.sm(0.0F, 0.9F, t) * (0.7F + 0.3F * this.throb) * fade,
                    9054936,
                    0.0F,
                    32);

            for (int i = 0; i < 5; i++) {
                if (this.hit[i]) {
                    float s = 1.0F + 0.6F * DeathFx.sm(3.0F, 3.6F, t);
                    float a =
                            (0.7F + 0.3F * (float) Math.sin(t * 12.0F + i))
                                    * (1.0F - DeathFx.sm(5.2F, 6.0F, t));
                    DeathFx.disc(
                            gv,
                            c.m,
                            this.gx[i],
                            0.015F,
                            this.gz[i],
                            0.12F * s,
                            0.32F * s,
                            11553535,
                            a,
                            11553535,
                            0.0F,
                            16);
                }
            }

            float hole = DeathFx.sm(3.3F, 3.7F, t) * (1.0F - DeathFx.sm(5.1F, 5.9F, t));
            if (hole > 0.01F) {
                DeathFx.disc(
                        gv,
                        c.m,
                        0.0F,
                        0.014F,
                        0.0F,
                        0.0F,
                        0.75F * hole,
                        524812,
                        1.0F,
                        524812,
                        1.0F,
                        28);
                DeathFx.disc(
                        gv,
                        c.m,
                        0.0F,
                        0.016F,
                        0.0F,
                        0.75F * hole,
                        0.92F * hole,
                        11553535,
                        0.8F * hole,
                        11553535,
                        0.0F,
                        28);
            }
        }

        @Override
        void solid(DeathHard.Ctx c, VertexConsumer w, float t) {
            float grow = DeathFx.eo(DeathFx.sm(1.6F, 2.4F, t));
            float dive = DeathFx.sm(2.35F, 3.25F, t);
            if (grow > 0.01F) {
                for (int i = 0; i < 5; i++) {
                    float dx = (float) Math.cos(i / 5.0 * Math.PI * 2.0 + 0.4);
                    float dz = (float) Math.sin(i / 5.0 * Math.PI * 2.0 + 0.4);
                    float sx = -dz;
                    float sz = dx;
                    float L = 2.3F * grow;
                    float p0x = this.belly.x;
                    float p0y = this.belly.y;
                    float p0z = this.belly.z;
                    float p1x = this.belly.x + this.nrm.x * 1.2F + dx * 0.6F;
                    float p1y = this.belly.y + this.nrm.y * 1.2F;
                    float p1z = this.belly.z + this.nrm.z * 1.2F + dz * 0.6F;
                    float p2x = this.gx[i];
                    float p2y = 1.3F - 0.9F * this.pullK;
                    float p2z = this.gz[i];
                    float p3x = this.gx[i];
                    float p3y = -0.9F;
                    float p3z = this.gz[i];

                    for (int k = 0; k < 30; k++) {
                        float s = k / 29.0F;
                        float wig =
                                (float) Math.sin(s * 7.0F - t * 6.0F * this.sp[i] + this.ph[i])
                                        * 0.35F
                                        * s
                                        * grow;
                        float fx =
                                this.belly.x
                                        + this.nrm.x * s * L * 0.85F
                                        + dx * s * L * 0.55F
                                        + sx * wig;
                        float fy =
                                this.belly.y
                                        + this.nrm.y * s * L * 0.85F
                                        + (float) Math.cos(s * 5.0F - t * 5.0F + this.ph[i])
                                                * 0.2F
                                                * s
                                                * grow;
                        float fz =
                                this.belly.z
                                        + this.nrm.z * s * L * 0.85F
                                        + dz * s * L * 0.55F
                                        + sz * wig;
                        float u = 1.0F - s;
                        float b0 = u * u * u;
                        float b1 = 3.0F * u * u * s;
                        float b2 = 3.0F * u * s * s;
                        float b3 = s * s * s;
                        float w2 =
                                (float) Math.sin(s * 9.0F - t * 9.0F + this.ph[i])
                                        * 0.06F
                                        * (1.0F - this.pullK);
                        float bx = b0 * p0x + b1 * p1x + b2 * p2x + b3 * p3x + sx * w2;
                        float by = b0 * p0y + b1 * p1y + b2 * p2y + b3 * p3y;
                        float bz = b0 * p0z + b1 * p1z + b2 * p2z + b3 * p3z + sz * w2;
                        float e = DeathFx.ei(dive);
                        float x = DeathFx.lerp(fx, bx, e);
                        float y = DeathFx.lerp(fy, by, e);
                        float z = DeathFx.lerp(fz, bz, e);
                        if (!(y < -0.4F)) {
                            float rad =
                                    (0.13F * (float) Math.pow(1.0F - s, 0.7) + 0.025F)
                                            * Math.min(1.0F, grow * 1.5F);
                            int col = DeathFx.mix(2756154, 10506456, s);
                            if (k % 3 == 1) {
                                col = DeathFx.mix(col & 16777215, 14715592, 0.35F);
                            }

                            Matrix4f sm4 =
                                    new Matrix4f(c.m).translate(x, y, z).rotateY(s * 3.0F + i);
                            DeathFx.cube(w, sm4, -rad, -rad, -rad, rad, rad, rad, col, c.light);
                        }
                    }
                }
            }

            if (this.burst && !c.firstPerson) {
                Matrix4f bm = new Matrix4f(c.m).mul(DeathHard.part(this.g, 2, new Matrix4f()));
                float z0 = DeathHard.DIM[2][2] / 2.0F * 0.05859375F;
                DeathFx.cube(
                        w, bm, -0.15F, -0.16F, z0, 0.15F, 0.2F, z0 + 0.02F, -5236690, 15728880);
                DeathFx.cube(
                        w,
                        bm,
                        -0.09F,
                        -0.09F,
                        z0 + 0.005F,
                        0.09F,
                        0.13F,
                        z0 + 0.025F,
                        -12974576,
                        c.light);
            }
        }

        @Override
        void skin(DeathHard.Ctx c, VertexConsumer sk, float t) {
            int tint = DeathFx.mix(16777215, 13144319, 0.5F * this.inner * this.throb);
            DeathHard.rig(sk, c.m, this.g, tint, c.light, 0, 32);
        }

        @Override
        void sprites(DeathHard.Ctx c, VertexConsumer sp, float t) {
            float on = DeathFx.sm(0.3F, 1.0F, t) * (1.0F - DeathFx.sm(5.4F, 6.2F, t));

            for (int e = 0; e < 60; e++) {
                double ae = e * 2.39996;
                float rr = 0.3F + e % 9 * 0.15F;
                float life = (t * 0.6F + e % 13 / 13.0F) % 1.0F;
                this.spr(
                        c,
                        sp,
                        (float) Math.cos(ae) * rr,
                        life * 2.2F,
                        (float) Math.sin(ae) * rr,
                        0.12F,
                        2,
                        13144319,
                        on * (float) Math.sin(life * Math.PI) * 0.8F);
            }

            if (this.inner > 0.05F) {
                this.spr(
                        c,
                        sp,
                        this.belly.x + this.nrm.x * 0.1F,
                        this.belly.y + this.nrm.y * 0.1F,
                        this.belly.z + this.nrm.z * 0.1F,
                        0.9F,
                        2,
                        11553535,
                        this.inner * this.throb * 0.8F);
            }

            if (t > 1.6F && t < 2.2F) {
                this.spr(
                        c,
                        sp,
                        this.belly.x,
                        this.belly.y,
                        this.belly.z,
                        1.6F,
                        2,
                        16724048,
                        1.0F - (t - 1.6F) / 0.6F);
            }
        }
    }

    static final class Vox {
        int part;
        float cx;
        float cy;
        float cz;
        final Vector3f p = new Vector3f();
        final Vector3f v = new Vector3f();
        final Vector3f w = new Vector3f();
        final Vector3f r = new Vector3f();
        final Quaternionf q = new Quaternionf();
        int tint = -1;
        float floor;
        float delay;
    }
}
