package dev.jun.nosclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.util.Col;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

public final class Gfx {
    private static PoseStack wPose;
    private static MultiBufferSource wBuf;
    public static float worldDepth = 64.0F;
    private static final float[] W0 = new float[3];
    private static final float[] W1 = new float[3];
    private static final float[] W2 = new float[3];
    private static final float[] W3 = new float[3];
    private static final Set<RenderType> wUsed = new HashSet<>();
    private static int wSeq;
    private static final Gfx.Seg[] SEGS =
            new Gfx.Seg[] {
                new Gfx.Seg(),
                new Gfx.Seg(),
                new Gfx.Seg(),
                new Gfx.Seg(),
                new Gfx.Seg(),
                new Gfx.Seg()
            };
    private static int segNext;
    private static final Identifier ROUND = ClientResources.id("textures/gui/shape/round.png");
    private static final Identifier RING = ClientResources.id("textures/gui/shape/ring.png");
    private static final Identifier SOFT = ClientResources.id("textures/gui/shape/soft.png");
    private static final Identifier DOT = ClientResources.id("textures/gui/shape/dot.png");
    private static final Identifier GLOW = ClientResources.id("textures/gui/shape/glow.png");
    private static final Identifier BAND = ClientResources.id("textures/gui/shape/band.png");

    private Gfx() {}

    public static boolean inWorld() {
        return wPose != null;
    }

    public static void beginWorld(PoseStack pose, MultiBufferSource buffers) {
        wPose = pose;
        wBuf = buffers;
        worldDepth = skyDepth();
        wSeq = 0;
    }

    public static float skyDepth() {
        Minecraft mc = Minecraft.getInstance();
        return Math.max(
                20.0F, Math.min(96.0F, mc.options.getEffectiveRenderDistance() * 16.0F * 0.5F));
    }

    public static void flushWorld() {
        flushBands();
        if (wBuf instanceof BufferSource bs) {
            for (RenderType t : wUsed) {
                bs.endBatch(t);
            }
        }
    }

    public static void endWorld() {
        flushBands();
        if (wBuf instanceof BufferSource bs) {
            for (RenderType t : wUsed) {
                bs.endBatch(t);
            }
        }

        wUsed.clear();
        wPose = null;
        wBuf = null;
    }

    private static void wQuad(
            Identifier tex,
            float x0,
            float y0,
            float x1,
            float y1,
            float x2,
            float y2,
            float x3,
            float y3,
            float u0,
            float v0,
            float u1,
            float v1,
            int color) {
        wQuadC(tex, x0, y0, x1, y1, x2, y2, x3, y3, u0, v0, u1, v1, color, color, color, color);
    }

    private static void wQuadC(
            Identifier tex,
            float x0,
            float y0,
            float x1,
            float y1,
            float x2,
            float y2,
            float x3,
            float y3,
            float u0,
            float v0,
            float u1,
            float v1,
            int c0,
            int c1,
            int c2,
            int c3) {
        if (Col.a(c0) >= 2 || Col.a(c1) >= 2 || Col.a(c2) >= 2 || Col.a(c3) >= 2) {
            float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
            float d = worldDepth * (1.0F - Math.min(0.03F, wSeq++ * 3.0E-6F));
            Proj.unproject(x0, y0, d, W0);
            Proj.unproject(x1, y1, d, W1);
            Proj.unproject(x2, y2, d, W2);
            Proj.unproject(x3, y3, d, W3);
            RenderType type = RenderTypes.eyes(tex);
            wUsed.add(type);
            VertexConsumer vc = wBuf.getBuffer(type);
            Pose pose = wPose.last();
            int ov = OverlayTexture.NO_OVERLAY;
            int light = 15728880;
            if (area < 0.0F) {
                vert(vc, pose, W0, u0, v0, c0, ov, light);
                vert(vc, pose, W1, u1, v0, c1, ov, light);
                vert(vc, pose, W2, u1, v1, c2, ov, light);
                vert(vc, pose, W3, u0, v1, c3, ov, light);
            } else {
                vert(vc, pose, W3, u0, v1, c3, ov, light);
                vert(vc, pose, W2, u1, v1, c2, ov, light);
                vert(vc, pose, W1, u1, v0, c1, ov, light);
                vert(vc, pose, W0, u0, v0, c0, ov, light);
            }
        }
    }

    private static void vert(
            VertexConsumer vc,
            Pose pose,
            float[] p,
            float u,
            float v,
            int color,
            int ov,
            int light) {
        vc.addVertex(pose, p[0], p[1], p[2])
                .setColor(color)
                .setUv(u, v)
                .setOverlay(ov)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void wStrip(
            Identifier tex,
            float x0,
            float y0,
            float x1,
            float y1,
            float thickness,
            float u0,
            float v0,
            float u1,
            float v1,
            int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (!(len < 0.001F)) {
            float nx = -dy / len * thickness / 2.0F;
            float ny = dx / len * thickness / 2.0F;
            wQuad(
                    tex, x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, x0 + nx, y0 + ny, u0,
                    v0, u1, v1, color);
        }
    }

    public static void bandPath(
            GuiGraphics g, float[] xs, float[] ys, int n, float thickness, int color) {
        if (n >= 2 && Col.a(color) != 0) {
            if (!inWorld()) {
                for (int i = 1; i < n; i++) {
                    band(g, xs[i - 1], ys[i - 1], xs[i], ys[i], thickness, color);
                }
            } else {
                float h = thickness / 2.0F;
                float[] ox = new float[n];
                float[] oy = new float[n];

                for (int i = 0; i < n; i++) {
                    int a = Math.max(0, i - 1);
                    int b = Math.min(n - 1, i + 1);
                    float dx = xs[b] - xs[a];
                    float dy = ys[b] - ys[a];
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len < 1.0E-4F) {
                        dx = 1.0F;
                        dy = 0.0F;
                        len = 1.0F;
                    }

                    ox[i] = -dy / len * h;
                    oy[i] = dx / len * h;
                }

                for (int i = 1; i < n; i++) {
                    float u0 = (float) (i - 1) / (n - 1);
                    float u1 = (float) i / (n - 1);
                    wQuad(
                            BAND,
                            xs[i - 1] - ox[i - 1],
                            ys[i - 1] - oy[i - 1],
                            xs[i] - ox[i],
                            ys[i] - oy[i],
                            xs[i] + ox[i],
                            ys[i] + oy[i],
                            xs[i - 1] + ox[i - 1],
                            ys[i - 1] + oy[i - 1],
                            0.5F,
                            0.0F,
                            0.5F,
                            1.0F,
                            color);
                }
            }
        }
    }

    private static void joinBand(
            float x0, float y0, float x1, float y1, float thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (!(len < 0.001F)) {
            float nx = -dy / len;
            float ny = dx / len;
            float h = thickness / 2.0F;
            float sx = nx * h;
            float sy = ny * h;

            for (Gfx.Seg p : SEGS) {
                if (p.on && !(Math.abs(p.x1 - x0) > 0.001F) && !(Math.abs(p.y1 - y0) > 0.001F)) {
                    float r = h / Math.max(1.0E-4F, p.h);
                    if (!(r < 0.7F) && !(r > 1.43F)) {
                        float ax = p.nx + nx;
                        float ay = p.ny + ny;
                        float al = (float) Math.sqrt(ax * ax + ay * ay);
                        if (!(al < 0.3F)) {
                            ax /= al;
                            ay /= al;
                            float miter = Math.min(2.0F, 1.0F / Math.max(0.5F, ax * nx + ay * ny));
                            float hj = (p.h + h) / 2.0F * miter;
                            int jc = Col.lerp(p.color, color, 0.5F);
                            emitSeg(p, ax * hj, ay * hj, jc);
                            store(p, x0, y0, x1, y1, h, nx, ny, ax * hj, ay * hj, color);
                            p.startColor = jc;
                            return;
                        }
                        break;
                    }
                }
            }

            Gfx.Seg slot = null;

            for (Gfx.Seg p : SEGS) {
                if (!p.on) {
                    slot = p;
                    break;
                }
            }

            if (slot == null) {
                slot = SEGS[segNext];
                segNext = (segNext + 1) % SEGS.length;
                emitSeg(slot, slot.nx * slot.h, slot.ny * slot.h, slot.color);
            }

            store(slot, x0, y0, x1, y1, h, nx, ny, sx, sy, color);
            slot.startColor = color;
        }
    }

    private static void store(
            Gfx.Seg p,
            float x0,
            float y0,
            float x1,
            float y1,
            float h,
            float nx,
            float ny,
            float sx,
            float sy,
            int color) {
        p.on = true;
        p.x0 = x0;
        p.y0 = y0;
        p.x1 = x1;
        p.y1 = y1;
        p.h = h;
        p.nx = nx;
        p.ny = ny;
        p.sx = sx;
        p.sy = sy;
        p.color = color;
    }

    private static void emitSeg(Gfx.Seg p, float ex, float ey, int endColor) {
        if (p.on) {
            p.on = false;
            wQuadC(
                    BAND,
                    p.x0 - p.sx,
                    p.y0 - p.sy,
                    p.x1 - ex,
                    p.y1 - ey,
                    p.x1 + ex,
                    p.y1 + ey,
                    p.x0 + p.sx,
                    p.y0 + p.sy,
                    0.0F,
                    0.0F,
                    1.0F,
                    1.0F,
                    p.startColor,
                    endColor,
                    endColor,
                    p.startColor);
        }
    }

    private static void flushBands() {
        for (Gfx.Seg p : SEGS) {
            emitSeg(p, p.nx * p.h, p.ny * p.h, p.color);
        }
    }

    public static void bandPathC(
            GuiGraphics g, float[] xs, float[] ys, float[] ws, int[] cs, int n) {
        if (n >= 2) {
            if (inWorld()) {
                flushWorld();
            }

            if (!inWorld()) {
                for (int i = 1; i < n; i++) {
                    band(g, xs[i - 1], ys[i - 1], xs[i], ys[i], (ws[i - 1] + ws[i]) / 2.0F, cs[i]);
                }
            } else {
                float[] ox = new float[n];
                float[] oy = new float[n];

                for (int i = 0; i < n; i++) {
                    int a = Math.max(0, i - 1);
                    int b = Math.min(n - 1, i + 1);
                    float dx = xs[b] - xs[a];
                    float dy = ys[b] - ys[a];
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len < 1.0E-4F) {
                        if (i > 0) {
                            ox[i] = ox[i - 1];
                            oy[i] = oy[i - 1];
                        }
                    } else {
                        float h = ws[i] / 2.0F;
                        ox[i] = -dy / len * h;
                        oy[i] = dx / len * h;
                    }
                }

                for (int i = 1; i < n; i++) {
                    if (Col.a(cs[i - 1]) >= 2 || Col.a(cs[i]) >= 2) {
                        wQuadC(
                                BAND,
                                xs[i - 1] - ox[i - 1],
                                ys[i - 1] - oy[i - 1],
                                xs[i] - ox[i],
                                ys[i] - oy[i],
                                xs[i] + ox[i],
                                ys[i] + oy[i],
                                xs[i - 1] + ox[i - 1],
                                ys[i - 1] + oy[i - 1],
                                0.0F,
                                0.0F,
                                1.0F,
                                1.0F,
                                cs[i - 1],
                                cs[i],
                                cs[i],
                                cs[i - 1]);
                    }
                }
            }
        }
    }

    public static float scale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static void rect(GuiGraphics g, float x, float y, float w, float h, int color) {
        if (Col.a(color) != 0 && !(w <= 0.0F) && !(h <= 0.0F)) {
            if (inWorld()) {
                wQuad(
                        DOT, x, y, x + w, y, x + w, y + h, x, y + h, 0.48F, 0.48F, 0.52F, 0.52F,
                        color);
            } else {
                float s = scale();
                g.pose().pushMatrix();
                g.pose().scale(1.0F / s, 1.0F / s);
                int x0 = Math.round(x * s);
                int y0 = Math.round(y * s);
                int x1 = Math.round((x + w) * s);
                int y1 = Math.round((y + h) * s);
                if (x1 > x0 && y1 > y0) {
                    g.fill(x0, y0, x1, y1, color);
                }

                g.pose().popMatrix();
            }
        }
    }

    public static void gradientV(
            GuiGraphics g, float x, float y, float w, float h, int top, int bottom) {
        float s = scale();
        g.pose().pushMatrix();
        g.pose().scale(1.0F / s, 1.0F / s);
        g.fillGradient(
                Math.round(x * s),
                Math.round(y * s),
                Math.round((x + w) * s),
                Math.round((y + h) * s),
                top,
                bottom);
        g.pose().popMatrix();
    }

    public static void gradientH(
            GuiGraphics g, float x, float y, float w, float h, int left, int right) {
        float s = scale();
        int px0 = Math.round(x * s);
        int px1 = Math.round((x + w) * s);
        int py0 = Math.round(y * s);
        int py1 = Math.round((y + h) * s);
        int n = Math.max(1, Math.min(48, (px1 - px0) / 4));
        g.pose().pushMatrix();
        g.pose().scale(1.0F / s, 1.0F / s);

        for (int i = 0; i < n; i++) {
            int a = px0 + (px1 - px0) * i / n;
            int b = px0 + (px1 - px0) * (i + 1) / n;
            g.fill(a, py0, b, py1, Col.lerp(left, right, (i + 0.5F) / n));
        }

        g.pose().popMatrix();
    }

    public static void band(
            GuiGraphics g, float x0, float y0, float x1, float y1, float thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (!(len < 0.5F) && Col.a(color) != 0) {
            if (inWorld()) {
                joinBand(x0, y0, x1, y1, thickness, color);
            } else {
                float s = scale();
                g.pose().pushMatrix();
                g.pose().translate(x0, y0);
                g.pose().rotate((float) Math.atan2(dy, dx));
                g.pose().scale(1.0F / s, 1.0F / s);
                int L = Math.round(len * s) + 2;
                int T = Math.max(2, Math.round(thickness * s));
                g.blit(
                        RenderPipelines.GUI_TEXTURED,
                        BAND,
                        -1,
                        -T / 2,
                        0.0F,
                        0.0F,
                        L,
                        T,
                        64,
                        64,
                        64,
                        64,
                        color);
                g.pose().popMatrix();
            }
        }
    }

    public static void worldPicture(
            Identifier tex, double[] tl, double[] tr, double[] br, double[] bl, int color) {
        worldPicture(tex, tl, tr, br, bl, 0.0F, 0.0F, 1.0F, 1.0F, color);
    }

    public static void worldQuad(Identifier tex, double[][] p, float[][] uv, int[] col) {
        if (inWorld()) {
            if (Col.a(col[0]) >= 2
                    || Col.a(col[1]) >= 2
                    || Col.a(col[2]) >= 2
                    || Col.a(col[3]) >= 2) {
                RenderType type = RenderTypes.eyes(tex);
                wUsed.add(type);
                VertexConsumer vc = wBuf.getBuffer(type);
                Pose pose = wPose.last();
                int ov = OverlayTexture.NO_OVERLAY;
                int light = 15728880;
                int[] order = new int[] {0, 1, 2, 3, 3, 2, 1, 0};

                for (int k : order) {
                    vert(vc, pose, f(p[k]), uv[k][0], uv[k][1], col[k], ov, light);
                }
            }
        }
    }

    public static void worldPictureV(
            Identifier tex,
            double[] tl,
            double[] tr,
            double[] br,
            double[] bl,
            int top,
            int bottom) {
        if (inWorld() && (Col.a(top) >= 2 || Col.a(bottom) >= 2)) {
            RenderType type = RenderTypes.eyes(tex);
            wUsed.add(type);
            VertexConsumer vc = wBuf.getBuffer(type);
            Pose pose = wPose.last();
            int ov = OverlayTexture.NO_OVERLAY;
            int light = 15728880;
            float[] a = f(tl);
            float[] b = f(tr);
            float[] c = f(br);
            float[] d = f(bl);
            vert(vc, pose, a, 0.0F, 0.0F, top, ov, light);
            vert(vc, pose, b, 1.0F, 0.0F, top, ov, light);
            vert(vc, pose, c, 1.0F, 1.0F, bottom, ov, light);
            vert(vc, pose, d, 0.0F, 1.0F, bottom, ov, light);
            vert(vc, pose, d, 0.0F, 1.0F, bottom, ov, light);
            vert(vc, pose, c, 1.0F, 1.0F, bottom, ov, light);
            vert(vc, pose, b, 1.0F, 0.0F, top, ov, light);
            vert(vc, pose, a, 0.0F, 0.0F, top, ov, light);
        }
    }

    public static void worldPicture(
            Identifier tex,
            double[] tl,
            double[] tr,
            double[] br,
            double[] bl,
            float u0,
            float v0,
            float u1,
            float v1,
            int color) {
        if (inWorld() && Col.a(color) >= 2) {
            RenderType type = RenderTypes.eyes(tex);
            wUsed.add(type);
            VertexConsumer vc = wBuf.getBuffer(type);
            Pose pose = wPose.last();
            int ov = OverlayTexture.NO_OVERLAY;
            int light = 15728880;
            float[] a = f(tl);
            float[] b = f(tr);
            float[] c = f(br);
            float[] d = f(bl);
            vert(vc, pose, a, u0, v0, color, ov, light);
            vert(vc, pose, b, u1, v0, color, ov, light);
            vert(vc, pose, c, u1, v1, color, ov, light);
            vert(vc, pose, d, u0, v1, color, ov, light);
            vert(vc, pose, d, u0, v1, color, ov, light);
            vert(vc, pose, c, u1, v1, color, ov, light);
            vert(vc, pose, b, u1, v0, color, ov, light);
            vert(vc, pose, a, u0, v0, color, ov, light);
        }
    }

    private static float[] f(double[] p) {
        return new float[] {(float) p[0], (float) p[1], (float) p[2]};
    }

    public static void image(
            GuiGraphics g,
            Identifier tex,
            int texW,
            int texH,
            float x,
            float y,
            float w,
            float h,
            int color) {
        if (Col.a(color) != 0 && !(w <= 0.0F) && !(h <= 0.0F)) {
            if (inWorld()) {
                wQuad(tex, x, y, x + w, y, x + w, y + h, x, y + h, 0.0F, 0.0F, 1.0F, 1.0F, color);
            } else {
                float s = scale();
                int x0 = Math.round(x * s);
                int y0 = Math.round(y * s);
                int W = Math.max(1, Math.round(w * s));
                int H = Math.max(1, Math.round(h * s));
                g.pose().pushMatrix();
                g.pose().scale(1.0F / s, 1.0F / s);
                g.blit(
                        RenderPipelines.GUI_TEXTURED,
                        tex,
                        x0,
                        y0,
                        0.0F,
                        0.0F,
                        W,
                        H,
                        texW,
                        texH,
                        texW,
                        texH,
                        color);
                g.pose().popMatrix();
            }
        }
    }

    private static void single(
            GuiGraphics g, Identifier tex, float cx, float cy, float radius, int color) {
        if (inWorld()) {
            wQuad(
                    tex,
                    cx - radius,
                    cy - radius,
                    cx + radius,
                    cy - radius,
                    cx + radius,
                    cy + radius,
                    cx - radius,
                    cy + radius,
                    0.0F,
                    0.0F,
                    1.0F,
                    1.0F,
                    color);
        } else {
            float s = scale();
            int d = Math.max(1, Math.round(radius * 2.0F * s));
            int x0 = Math.round(cx * s) - d / 2;
            int y0 = Math.round(cy * s) - d / 2;
            g.pose().pushMatrix();
            g.pose().scale(1.0F / s, 1.0F / s);
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    tex,
                    x0,
                    y0,
                    0.0F,
                    0.0F,
                    d,
                    d,
                    128,
                    128,
                    128,
                    128,
                    color);
            g.pose().popMatrix();
        }
    }

    private static void nine(
            GuiGraphics g,
            Identifier tex,
            int texSize,
            int slice,
            float x,
            float y,
            float w,
            float h,
            float corner,
            int top,
            int bottom) {
        float s = scale();
        int x0 = Math.round(x * s);
        int y0 = Math.round(y * s);
        int x1 = Math.round((x + w) * s);
        int y1 = Math.round((y + h) * s);
        int W = x1 - x0;
        int H = y1 - y0;
        if (W > 0 && H > 0) {
            int c = Math.max(1, Math.min(Math.round(corner * s), Math.min(W, H) / 2));
            int mid = texSize - 2 * slice;
            g.pose().pushMatrix();
            g.pose().scale(1.0F / s, 1.0F / s);
            int cw = W - 2 * c;
            int ch = H - 2 * c;
            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    tex,
                    x0,
                    y0,
                    0.0F,
                    0.0F,
                    c,
                    c,
                    slice,
                    slice,
                    texSize,
                    texSize,
                    top);
            if (cw > 0) {
                g.blit(
                        RenderPipelines.GUI_TEXTURED,
                        tex,
                        x0 + c,
                        y0,
                        slice,
                        0.0F,
                        cw,
                        c,
                        mid,
                        slice,
                        texSize,
                        texSize,
                        top);
            }

            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    tex,
                    x1 - c,
                    y0,
                    slice + mid,
                    0.0F,
                    c,
                    c,
                    slice,
                    slice,
                    texSize,
                    texSize,
                    top);
            if (ch > 0) {
                if (top != bottom && tex == ROUND) {
                    g.fillGradient(x0, y0 + c, x1, y1 - c, top, bottom);
                } else {
                    int mc = top == bottom ? top : Col.lerp(top, bottom, 0.5F);
                    g.blit(
                            RenderPipelines.GUI_TEXTURED,
                            tex,
                            x0,
                            y0 + c,
                            0.0F,
                            slice,
                            c,
                            ch,
                            slice,
                            mid,
                            texSize,
                            texSize,
                            mc);
                    if (cw > 0) {
                        g.blit(
                                RenderPipelines.GUI_TEXTURED,
                                tex,
                                x0 + c,
                                y0 + c,
                                slice,
                                slice,
                                cw,
                                ch,
                                mid,
                                mid,
                                texSize,
                                texSize,
                                mc);
                    }

                    g.blit(
                            RenderPipelines.GUI_TEXTURED,
                            tex,
                            x1 - c,
                            y0 + c,
                            slice + mid,
                            slice,
                            c,
                            ch,
                            slice,
                            mid,
                            texSize,
                            texSize,
                            mc);
                }
            }

            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    tex,
                    x0,
                    y1 - c,
                    0.0F,
                    slice + mid,
                    c,
                    c,
                    slice,
                    slice,
                    texSize,
                    texSize,
                    bottom);
            if (cw > 0) {
                g.blit(
                        RenderPipelines.GUI_TEXTURED,
                        tex,
                        x0 + c,
                        y1 - c,
                        slice,
                        slice + mid,
                        cw,
                        c,
                        mid,
                        slice,
                        texSize,
                        texSize,
                        bottom);
            }

            g.blit(
                    RenderPipelines.GUI_TEXTURED,
                    tex,
                    x1 - c,
                    y1 - c,
                    slice + mid,
                    slice + mid,
                    c,
                    c,
                    slice,
                    slice,
                    texSize,
                    texSize,
                    bottom);
            g.pose().popMatrix();
        }
    }

    public static void round(
            GuiGraphics g, float x, float y, float w, float h, float radius, int color) {
        if (Col.a(color) != 0 && !(w <= 0.0F) && !(h <= 0.0F)) {
            if (radius < 0.4F) {
                rect(g, x, y, w, h, color);
            } else {
                nine(g, ROUND, 136, 64, x, y, w, h, radius, color, color);
            }
        }
    }

    public static void roundGradient(
            GuiGraphics g, float x, float y, float w, float h, float radius, int top, int bottom) {
        if (!(w <= 0.0F) && !(h <= 0.0F)) {
            if (radius < 0.4F) {
                gradientV(g, x, y, w, h, top, bottom);
            } else {
                nine(g, ROUND, 136, 64, x, y, w, h, radius, top, bottom);
            }
        }
    }

    public static void roundOutline(
            GuiGraphics g,
            float x,
            float y,
            float w,
            float h,
            float radius,
            float thickness,
            int color) {
        if (Col.a(color) != 0 && !(w <= 0.0F) && !(h <= 0.0F)) {
            if (!inWorld()) {
                float r = Math.max(radius, thickness * 3.0F);
                float corner = Math.max(r, thickness * 16.0F);
                corner = Math.min(corner, Math.min(w, h) / 2.0F);
                nine(g, RING, 136, 64, x, y, w, h, corner, color, color);
            } else {
                float cx = x + w / 2.0F;
                float cy = y + h / 2.0F;
                float rx = w / 2.0F;
                float ry = h / 2.0F;
                int n = 64;
                float ht = Math.max(1.2F, thickness * 2.4F) / 2.0F;

                for (int i = 0; i < n; i++) {
                    double a0 = i * Math.PI * 2.0 / n;
                    double a1 = (i + 1) * Math.PI * 2.0 / n;
                    float c0 = (float) Math.cos(a0);
                    float s0 = (float) Math.sin(a0);
                    float c1 = (float) Math.cos(a1);
                    float s1 = (float) Math.sin(a1);
                    wQuad(
                            BAND,
                            cx + c0 * (rx - ht),
                            cy + s0 * (ry - ht),
                            cx + c1 * (rx - ht),
                            cy + s1 * (ry - ht),
                            cx + c1 * (rx + ht),
                            cy + s1 * (ry + ht),
                            cx + c0 * (rx + ht),
                            cy + s0 * (ry + ht),
                            0.0F,
                            0.0F,
                            1.0F,
                            1.0F,
                            color);
                }
            }
        }
    }

    public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) {
        if (!(radius <= 0.0F) && Col.a(color) != 0) {
            single(g, DOT, cx, cy, radius, color);
        }
    }

    public static void shadow(
            GuiGraphics g,
            float x,
            float y,
            float w,
            float h,
            float radius,
            float size,
            int color) {
        if (Col.a(color) != 0) {
            float e = size * 1.6F;
            nine(
                    g,
                    SOFT,
                    128,
                    48,
                    x - e,
                    y - e + size * 0.4F,
                    w + 2.0F * e,
                    h + 2.0F * e,
                    Math.max(radius, size) + e,
                    color,
                    color);
        }
    }

    public static void blob(GuiGraphics g, float cx, float cy, float radius, int color) {
        if (Col.a(color) != 0 && !(radius <= 0.5F)) {
            single(g, GLOW, cx, cy, radius, color);
        }
    }

    public static void glow(
            GuiGraphics g,
            float x,
            float y,
            float w,
            float h,
            float radius,
            float size,
            int color) {
        if (Col.a(color) != 0) {
            float e = size * 1.6F;
            nine(
                    g,
                    SOFT,
                    128,
                    48,
                    x - e,
                    y - e,
                    w + 2.0F * e,
                    h + 2.0F * e,
                    Math.max(radius, size) + e,
                    color,
                    color);
        }
    }

    public static void line(
            GuiGraphics g, float x0, float y0, float x1, float y1, float thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (!(len < 0.01F) && Col.a(color) != 0) {
            if (inWorld()) {
                wStrip(
                        DOT,
                        x0,
                        y0,
                        x1,
                        y1,
                        Math.max(0.5F, thickness),
                        0.48F,
                        0.48F,
                        0.52F,
                        0.52F,
                        color);
            } else {
                float s = scale();
                g.pose().pushMatrix();
                g.pose().translate(x0, y0);
                g.pose().rotate((float) Math.atan2(dy, dx));
                g.pose().scale(1.0F / s, 1.0F / s);
                int L = Math.round(len * s);
                int T = Math.max(1, Math.round(thickness * s));
                g.fill(0, -T / 2, L, -T / 2 + T, color);
                g.pose().popMatrix();
            }
        }
    }

    public static void streak(
            GuiGraphics g, float x0, float y0, float x1, float y1, float thickness, int color) {
        int n = 6;

        for (int i = 0; i < n; i++) {
            float a = (float) i / n;
            float b = (float) (i + 1) / n;
            line(
                    g,
                    x0 + (x1 - x0) * a,
                    y0 + (y1 - y0) * a,
                    x0 + (x1 - x0) * b,
                    y0 + (y1 - y0) * b,
                    thickness,
                    Col.alpha(color, (float) (i + 1) / n));
        }
    }

    public static float textWidth(String s, String fontId, float size) {
        return Fonts.unitWidth(s, fontId, font()) * size;
    }

    public static float text(
            GuiGraphics g, String s, String fontId, float x, float y, float size, int color) {
        if (!s.isEmpty() && Col.a(color) >= 4) {
            Font f = font();
            float k = size / Fonts.height(fontId);
            double raster =
                    Math.hypot(g.pose().m00(), g.pose().m01())
                            * Minecraft.getInstance().getWindow().getGuiScale()
                            * k;
            Component c =
                    fontId != null && fontId.startsWith("ui_")
                            ? dev.jun.nosclient.Ui.uiText(s, fontId.startsWith("ui_bold"), raster)
                            : Fonts.rasterText(s, fontId, raster);
            g.pose().pushMatrix();
            g.pose().translate(x, y + size * Fonts.yShift(fontId));
            g.pose().scale(k, k);
            dev.jun.nosclient.Ui.snapText(g);
            g.drawString(f, c, 0, 0, color, false);
            g.pose().popMatrix();
            return Fonts.unitWidth(s, fontId, f) * size;
        } else {
            return 0.0F;
        }
    }

    public static float textCentered(
            GuiGraphics g, String s, String fontId, float cx, float y, float size, int color) {
        float w = textWidth(s, fontId, size);
        text(g, s, fontId, cx - w / 2.0F, y, size, color);
        return w;
    }

    public static String ellipsize(String s, String fontId, float size, float maxWidth) {
        if (textWidth(s, fontId, size) <= maxWidth) {
            return s;
        }

        String e = "…";
        int lo = 0;
        int hi = s.length();

        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (textWidth(s.substring(0, mid) + e, fontId, size) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }

        return s.substring(0, lo).trim() + e;
    }

    private static void textShadow(
            GuiGraphics g,
            String s,
            String fontId,
            float x,
            float y,
            float size,
            float o,
            int soft,
            float alpha) {
        int mode = MusicSettings.get().textShadow;
        int custom = MusicSettings.get().shadowColor;
        if (custom != 0) {
            soft = custom | 0xFF000000;
        }

        int strong = custom != 0 ? custom | 0xFF000000 : -16120302;
        if (mode == 1) {
            text(g, s, fontId, x + o * 0.6F, y + o * 1.0F, size, Col.alpha(soft, alpha));
        } else if (mode == 2) {
            text(g, s, fontId, x + o * 1.3F, y + o * 2.0F, size, Col.alpha(strong, alpha));
            text(g, s, fontId, x + o * 0.7F, y + o * 1.1F, size, Col.alpha(strong, alpha));
        }
    }

    public static void neon(
            GuiGraphics g,
            String s,
            String fontId,
            float cx,
            float y,
            float size,
            int main,
            int a,
            int b,
            float alpha,
            boolean twoColor,
            float glow) {
        if (!(alpha <= 0.01F)) {
            float w = textWidth(s, fontId, size);
            float x = cx - w / 2.0F;
            float o = Math.max(0.6F, size * 0.055F);
            boolean clean = MusicSettings.get().textStyle == MusicConfig.TextStyle.CLEAN;
            textShadow(
                    g, s, fontId, x, y, size, o, clean ? -15396054 : Col.darken(a, 0.75F), alpha);
            if (clean) {
                text(g, s, fontId, x, y, size, Col.alpha(Col.lerp(main, -1, 0.2F), alpha));
            } else {
                float tcs = MusicSettings.get().twoColorStrength;
                if (twoColor && tcs > 0.01F) {
                    int tc = MusicSettings.get().twoColorColor;
                    int ca = tc != 0 ? tc | 0xFF000000 : a;
                    int cb = tc != 0 ? tc | 0xFF000000 : b;
                    float oo = o * (0.35F + 0.65F * tcs);
                    float ta = alpha * Math.min(1.0F, 0.3F + 0.7F * tcs);
                    text(g, s, fontId, x - oo, y + oo * 0.5F, size, Col.alpha(ca, ta));
                    text(g, s, fontId, x + oo, y - oo * 0.5F, size, Col.alpha(cb, ta));
                }

                text(g, s, fontId, x, y, size, Col.alpha(main, alpha));
            }
        }
    }

    private static final class Seg {
        boolean on;
        float x0;
        float y0;
        float x1;
        float y1;
        float h;
        float nx;
        float ny;
        float sx;
        float sy;
        int color;
        int startColor;
    }
}
