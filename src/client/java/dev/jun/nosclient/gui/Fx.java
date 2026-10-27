package dev.jun.nosclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.jun.nosclient.Ui;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription.Resource;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class Fx {
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("nosclient", "fx/glow");
    private static final Identifier HIT = Identifier.withDefaultNamespace("textures/particle/enchanted_hit.png");
    private static final Style RUNE = Style.EMPTY.withFont(new Resource(Identifier.withDefaultNamespace("alt")));
    private static boolean made;

    private Fx() {
    }

    public static float time() {
        return (float)(System.nanoTime() % 1000000000000L) / 1.0E9F;
    }

    private static void make() {
        if (!made) {
            made = true;
            Minecraft mc = Minecraft.getInstance();
            int n = 128;
            NativeImage g = new NativeImage(n, n, false);

            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    double r = Math.hypot(x + 0.5 - n / 2.0, y + 0.5 - n / 2.0) / (n / 2.0);
                    double a = r >= 1.0 ? 0.0 : Math.pow(1.0 - r, 2.2);
                    g.setPixel(x, y, (int)Math.round(a * 255.0) << 24 | 16777215);
                }
            }

            register(mc, GLOW, g, false);
        }
    }

    private static void register(Minecraft mc, Identifier id, NativeImage img, boolean repeat) {
        DynamicTexture t = new DynamicTexture(() -> "nosclient " + id.getPath(), img) {
            {
                this.sampler = repeat
                    ? RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR)
                    : RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            }
        };
        t.upload();
        mc.getTextureManager().register(id, t);
    }

    public static void glow(GuiGraphics g, float cx, float cy, float w, float h, int argb) {
        make();
        if (argb >>> 24 != 0 && !(w < 1.0F) && !(h < 1.0F)) {
            g.pose().pushMatrix();
            g.pose().translate(cx - w / 2.0F, cy - h / 2.0F);
            g.pose().scale(w / 64.0F, h / 64.0F);
            g.blit(RenderPipelines.GUI_TEXTURED, GLOW, 0, 0, 0.0F, 0.0F, 64, 64, 64, 64, argb);
            g.pose().popMatrix();
        }
    }

    public static void hairline(GuiGraphics g, int x0, int x1, int y, int argb) {
        int mid = (x0 + x1) / 2;
        int a = argb & 16777215;
        hgrad(g, x0, mid, y, y + 1, a, argb);
        hgrad(g, mid, x1, y, y + 1, argb, a);
    }

    public static void hgrad(GuiGraphics g, int x0, int x1, int y0, int y1, int left, int right) {
        g.pose().pushMatrix();
        g.pose().translate(x0, y1);
        g.pose().rotate((float) (-Math.PI / 2));
        g.fillGradient(0, 0, y1 - y0, x1 - x0, left, right);
        g.pose().popMatrix();
    }

    @Environment(EnvType.CLIENT)
    public static final class Runes {
        private static final int N = 40;
        private final float[] sx = new float[40];
        private final float[] sy = new float[40];
        private final float[] tx = new float[40];
        private final float[] ty = new float[40];
        private final float[] bend = new float[40];
        private final float[] t0 = new float[40];
        private final float[] dur = new float[40];
        private final char[] ch = new char[40];
        private final int[] col = new int[40];
        private int next;
        private float acc;
        private int rng = 12345;
        public float size = 1.0F;

        private float rnd() {
            this.rng = this.rng * 1103515245 + 12345;
            return (this.rng >>> 8 & 65535) / 65535.0F;
        }

        public void emit(float x, float y, float r, float rate, float dt, int argb) {
            this.acc += rate * dt;

            while (this.acc >= 1.0F) {
                this.acc--;
                int i = this.next = (this.next + 1) % 40;
                double a = this.rnd() * Math.PI * 2.0;
                float rr = r * (0.7F + 0.5F * this.rnd());
                this.sx[i] = x + (float)Math.cos(a) * rr;
                this.sy[i] = y + (float)Math.sin(a) * rr * 0.8F - r * 0.25F;
                this.tx[i] = x;
                this.ty[i] = y;
                this.bend[i] = (this.rnd() - 0.5F) * r * 1.2F;
                this.t0[i] = Fx.time();
                this.dur[i] = 0.9F + this.rnd() * 0.7F;
                this.ch[i] = (char)(97 + (int)(this.rnd() * 26.0F));
                this.col[i] = argb;
            }
        }

        public void render(GuiGraphics g) {
            float now = Fx.time();
            Font f = Minecraft.getInstance().font;

            for (int i = 0; i < 40; i++) {
                if (!(this.dur[i] <= 0.0F)) {
                    float p = (now - this.t0[i]) / this.dur[i];
                    if (!(p >= 1.0F) && !(p < 0.0F)) {
                        float e = p * p * (3.0F - 2.0F * p);
                        float mx = (this.sx[i] + this.tx[i]) / 2.0F + this.bend[i];
                        float my = (this.sy[i] + this.ty[i]) / 2.0F - Math.abs(this.bend[i]) * 0.5F;
                        float x = (1.0F - e) * (1.0F - e) * this.sx[i] + 2.0F * (1.0F - e) * e * mx + e * e * this.tx[i];
                        float y = (1.0F - e) * (1.0F - e) * this.sy[i] + 2.0F * (1.0F - e) * e * my + e * e * this.ty[i];
                        float a = (float)Math.sin(Math.PI * Math.min(1.0F, p * 1.15F));
                        float s = this.size * (1.0F - 0.45F * e);
                        g.pose().pushMatrix();
                        g.pose().translate(x, y);
                        g.pose().scale(s, s);
                        g.drawString(f, Component.literal(String.valueOf(this.ch[i])).withStyle(Fx.RUNE), -3, -4, Ui.alpha(this.col[i], a), false);
                        g.pose().popMatrix();
                    } else {
                        this.dur[i] = 0.0F;
                    }
                }
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public static final class Sparks {
        private static final int N = 48;
        private final float[] x = new float[48];
        private final float[] y = new float[48];
        private final float[] vx = new float[48];
        private final float[] vy = new float[48];
        private final float[] life = new float[48];
        private final float[] size = new float[48];
        private final int[] col = new int[48];
        private int next;
        private int rng = 777;
        private long last = System.nanoTime();

        private float rnd() {
            this.rng = this.rng * 1103515245 + 12345;
            return (this.rng >>> 8 & 65535) / 65535.0F;
        }

        public void burst(float cx, float cy, int count, int argb) {
            for (int k = 0; k < count; k++) {
                int i = this.next = (this.next + 1) % 48;
                double a = this.rnd() * Math.PI * 2.0;
                float sp = 30.0F + this.rnd() * 60.0F;
                this.x[i] = cx;
                this.y[i] = cy;
                this.vx[i] = (float)Math.cos(a) * sp;
                this.vy[i] = (float)Math.sin(a) * sp - 20.0F;
                this.life[i] = 0.45F + this.rnd() * 0.35F;
                this.size[i] = 4.0F + this.rnd() * 4.0F;
                this.col[i] = argb;
            }
        }

        public void render(GuiGraphics g) {
            long now = System.nanoTime();
            float dt = Math.min(0.05F, (float)(now - this.last) / 1.0E9F);
            this.last = now;

            for (int i = 0; i < 48; i++) {
                if (!(this.life[i] <= 0.0F)) {
                    this.life[i] = this.life[i] - dt;
                    this.x[i] = this.x[i] + this.vx[i] * dt;
                    this.y[i] = this.y[i] + this.vy[i] * dt;
                    this.vx[i] = this.vx[i] * (1.0F - 3.0F * dt);
                    this.vy[i] = this.vy[i] * (1.0F - 3.0F * dt) + 40.0F * dt;
                    float a = Math.min(1.0F, this.life[i] * 3.0F);
                    float s = this.size[i] * (0.5F + a * 0.5F);
                    g.pose().pushMatrix();
                    g.pose().translate(this.x[i], this.y[i]);
                    g.pose().scale(s / 8.0F, s / 8.0F);
                    g.blit(RenderPipelines.GUI_TEXTURED, Fx.HIT, -4, -4, 0.0F, 0.0F, 8, 8, 8, 8, Ui.alpha(this.col[i], a));
                    g.pose().popMatrix();
                }
            }
        }
    }
}
