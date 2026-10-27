package dev.jun.nosclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.jun.nosclient.Ui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3x2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class IntroScreen extends Screen {
    public static boolean played;
    public static IntroScreen active;
    private static final Logger LOG = LoggerFactory.getLogger("nosclient");
    private static final float SZ = 48.0F;
    private static final float COS30 = (float)Math.cos(Math.PI / 6);
    private static final float C45 = (float)Math.cos(Math.PI / 4);
    private static final int TRUNK = 5;
    private static final int GRASS = -7226023;
    private static final int FOLIAGE = -8934609;
    private static final int[] HERO = IntroScreen.Anim.HERO;
    private static final Identifier T_GRASS_TOP = mc("block/grass_block_top");
    private static final Identifier T_GRASS_SIDE = mc("block/grass_block_side");
    private static final Identifier T_DIRT = mc("block/dirt");
    private static final Identifier T_LOG = mc("block/oak_log");
    private static final Identifier T_LOG_TOP = mc("block/oak_log_top");
    private static final Identifier T_LEAVES = mc("block/oak_leaves");
    private static final Identifier T_SAPLING = mc("block/oak_sapling");
    private static final Identifier T_BONE = mc("item/bone_meal");
    private static final Identifier T_GLINT = mc("particle/glint");
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("nosclient", "textures/gui/logo.png");
    private static final Identifier BG = Identifier.fromNamespaceAndPath("nosclient", "intro/bg");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("nosclient", "intro/glow");
    private static final Identifier RING = Identifier.fromNamespaceAndPath("nosclient", "intro/ring");
    private static boolean texMade;
    private static final float FREEZE = Float.parseFloat(System.getProperty("nosclient.introAt", "-1"));
    private static final IntroScreen.Anim.Bez OUT = IntroScreen.Anim.OUT;
    private static final IntroScreen.Anim.Bez SPR = IntroScreen.Anim.SPR;
    private static final IntroScreen.Anim.Bez IN = IntroScreen.Anim.IN;
    private static final IntroScreen.Anim.Bez IN_OUT = IntroScreen.Anim.IN_OUT;
    private static final IntroScreen.Anim.Bez LIN = IntroScreen.Anim.LIN;
    private static final IntroScreen.Anim.Bez FALL = IntroScreen.Anim.FALL;
    private static final IntroScreen.Anim.Bez SLIDE = IntroScreen.Anim.SLIDE;
    private static final IntroScreen.Anim.Bez REVEAL = IntroScreen.Anim.REVEAL;
    private static final IntroScreen.Anim.Bez SPIN = IntroScreen.Anim.SPIN;
    private static final int GRASS_K = 0;
    private static final int DIRT_K = 1;
    private static final int LOG_K = 2;
    private static final int LEAF_K = 3;
    private final List<IntroScreen.Cube> cubes = new ArrayList<>();
    private float heroDelay;
    private final List<IntroScreen.Part> parts = new ArrayList<>();
    private int rng = 7;
    private final Screen next;
    private Screen shown;
    private long start = -1L;
    private final boolean[] sounded = new boolean[4];
    private float A = 1.0F;
    private float U = 1.0F;
    private final Fx.Runes runes = new Fx.Runes();
    private long lastFrame = System.nanoTime();
    private static final float[][] FACES = new float[][]{
        {0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 1.0F},
        {0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.8F},
        {1.0F, 1.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.8F},
        {1.0F, 1.0F, 1.0F, 0.0F, 0.0F, -1.0F, 0.0F, -1.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.6F},
        {0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, -1.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.6F}
    };

    private static Identifier mc(String p) {
        return Identifier.withDefaultNamespace("textures/" + p + ".png");
    }

    private static float anim(float ms, float delay, float dur, IntroScreen.Anim.Bez e, float[][] kf, int ch) {
        return IntroScreen.Anim.anim(ms, delay, dur, e, kf, ch);
    }

    private static float lerp(float ms, float delay, float dur, IntroScreen.Anim.Bez e, float a, float b) {
        return IntroScreen.Anim.lerp(ms, delay, dur, e, a, b);
    }

    private float rnd() {
        this.rng = (int)(this.rng * 16807L % 2147483647L);
        return this.rng / 2.1474836E9F;
    }

    public IntroScreen(Screen next) {
        super(Component.empty());
        this.next = next;
        active = this;
        this.build();
    }

    private static float[] proj(float x, float y, float z) {
        return new float[]{(x + z) * 48.0F * C45, -y * 48.0F * COS30 + (z - x) * 48.0F * C45 * 0.5F};
    }

    private void build() {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.abs(x) != 2 || Math.abs(z) != 2) {
                    this.cubes.add(new IntroScreen.Cube(x, 0, z, 0, 120 + (Math.abs(x) + Math.abs(z)) * 55));
                }
            }
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                this.cubes.add(new IntroScreen.Cube(x, -1, z, 1, 120 + (Math.abs(x) + Math.abs(z)) * 55 + 40));
            }
        }

        this.cubes.add(new IntroScreen.Cube(0, -2, 0, 1, 200.0F));

        for (int i = 0; i < 5; i++) {
            this.cubes.add(new IntroScreen.Cube(0, i + 1, 0, 2, 1700 + i * 90));
        }

        float[] sap = proj(0.0F, 1.0F, 0.0F);
        float tx = sap[0];
        float ty = sap[1];

        for (int w = 0; w < 2; w++) {
            for (int i = 0; i < 14; i++) {
                float ang = this.rnd() * (float) Math.PI * 2.0F;
                float rad = 20.0F + this.rnd() * 70.0F;
                float x0 = tx + (float)Math.cos(ang) * rad - 10.0F;
                float y0 = ty - 20.0F + (float)Math.sin(ang) * rad * 0.6F;
                float size = 18.0F + this.rnd() * 12.0F;
                float up = -60.0F - this.rnd() * 40.0F;
                this.parts
                    .add(
                        new IntroScreen.Part(
                            T_GLINT,
                            -1,
                            size,
                            x0,
                            y0,
                            new float[][]{{0.0F, 0.0F, 0.0F, 0.0F, 0.4F, 0.0F}, {0.3F, 1.0F, 0.0F, -14.0F, 1.1F, 0.0F}, {1.0F, 0.0F, 0.0F, up, 0.6F, 0.0F}},
                            1340 + w * 260 + this.rnd() * 240.0F,
                            700.0F + this.rnd() * 300.0F,
                            new IntroScreen.Anim.Bez(0.0F, 0.0F, 0.58F, 1.0F)
                        )
                    );
            }
        }

        for (int[] l : IntroScreen.Anim.leafCells()) {
            if (l[0] != HERO[0] || l[1] != HERO[1] || l[2] != HERO[2]) {
                this.cubes.add(new IntroScreen.Cube(l[0], l[1], l[2], 3, 0.0F));
            }
        }

        for (int i = 0; i < this.cubes.size(); i++) {
            IntroScreen.Cube c = this.cubes.get(i);
            if (c.kind == 3) {
                this.cubes.set(i, new IntroScreen.Cube(c.x, c.y, c.z, 3, this.leafDelay(c.x, c.y, c.z)));
            }
        }

        this.heroDelay = this.leafDelay(HERO[0], HERO[1], HERO[2]);
        this.cubes.sort(Comparator.comparingInt(cx -> cx.y + cx.z - cx.x));

        for (int i = 0; i < 22; i++) {
            float[] p = proj(this.rnd() * 4.0F - 2.0F, 3.0F + this.rnd() * 4.0F, this.rnd() * 4.0F - 2.0F);
            float dx = (this.rnd() - 0.5F) * 160.0F;
            float size = 16.0F + this.rnd() * 8.0F;
            float end = 380.0F + this.rnd() * 120.0F;
            this.parts
                .add(
                    new IntroScreen.Part(
                        leaf(i),
                        -8934609,
                        size,
                        p[0],
                        p[1],
                        new float[][]{
                            {0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F},
                            {0.15F, 1.0F, dx * 0.3F, 60.0F, 1.0F, 90.0F},
                            {0.6F, 1.0F, -dx * 0.4F, 220.0F, 1.0F, 250.0F},
                            {1.0F, 0.0F, dx, end, 1.0F, 420.0F}
                        },
                        2500.0F + this.rnd() * 1900.0F,
                        2200.0F + this.rnd() * 900.0F,
                        LIN
                    )
                );
        }

        for (int i = 0; i < 16; i++) {
            float ang = i / 16.0F * (float) Math.PI * 2.0F + this.rnd() * 0.3F;
            float d = 150.0F + this.rnd() * 110.0F;
            float c = (float)Math.cos(ang);
            float s = (float)Math.sin(ang);
            this.parts
                .add(
                    new IntroScreen.Part(
                        leaf(i),
                        -8934609,
                        18.0F,
                        -12.0F,
                        -222.0F,
                        new float[][]{
                            {0.0F, 0.0F, 0.0F, 0.0F, 0.6F, 0.0F},
                            {0.3F, 1.0F, c * d * 0.5F, s * d * 0.5F, 1.2F, 0.0F},
                            {1.0F, 0.0F, c * d, s * d + 60.0F, 0.8F, 300.0F}
                        },
                        4560.0F + this.rnd() * 80.0F,
                        900.0F + this.rnd() * 300.0F,
                        new IntroScreen.Anim.Bez(0.0F, 0.0F, 0.58F, 1.0F)
                    )
                );
        }
    }

    private float leafDelay(int x, int y, int z) {
        return 2120 + (Math.abs(x) + Math.abs(z)) * 70 + (6 - y) * -45 + this.rnd() * 60.0F;
    }

    private static Identifier leaf(int i) {
        return mc("particle/leaf_" + new int[]{0, 3, 6, 9}[i % 4]);
    }

    private static void makeTextures(Minecraft mc) {
        if (!texMade) {
            texMade = true;
            radial(mc, BG, 128, r -> r < 0.62 ? 1.0 - 0.79 * r / 0.62 : 0.21 * (1.0 - (r - 0.62) / 0.38));
            radial(mc, GLOW, 128, r -> r < 0.6 ? 1.0 - r / 0.6 : 0.0);
            radial(mc, RING, 256, r -> Math.max(0.0, 1.0 - Math.abs(r - 0.98) / 0.018));
        }
    }

    private static void radial(Minecraft mc, Identifier id, int n, DoubleUnaryOperator alpha) {
        NativeImage img = new NativeImage(n, n, false);

        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double r = Math.hypot(x + 0.5 - n / 2.0, y + 0.5 - n / 2.0) / (n / 2.0);
                double a = Math.min(1.0, Math.max(0.0, r > 1.0 ? 0.0 : alpha.applyAsDouble(r)));
                img.setPixel(x, y, (int)Math.round(a * 255.0) << 24 | 16777215);
            }
        }

        DynamicTexture t = new DynamicTexture(() -> "nosclient " + id.getPath(), img) {
            {
                this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            }
        };
        t.upload();
        mc.getTextureManager().register(id, t);
    }

    private int col(int argb, float a) {
        return Ui.alpha(argb, a * this.A);
    }

    private static int shade(int rgb, float k) {
        int r = Math.round((rgb >> 16 & 0xFF) * k);
        int g = Math.round((rgb >> 8 & 0xFF) * k);
        int b = Math.round((rgb & 0xFF) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private void cube(GuiGraphics g, float cx, float cy, float cz, float sxz, float sy, float rot, int kind, float a) {
        if (!(sxz <= 0.001F) && !(sy <= 0.001F) && !(a <= 0.003F)) {
            float cr = (float)Math.cos(rot);
            float sr = (float)Math.sin(rot);

            for (float[] f : FACES) {
                float nx = f[9] * cr + f[11] * sr;
                float nz = -f[9] * sr + f[11] * cr;
                if (!(0.5F * f[10] + 0.612F * (nz - nx) <= 0.01F)) {
                    float[] o = pt(f[0], f[1], f[2], cx, cy, cz, sxz, sy, cr, sr);
                    float[] u = pt(f[0] + f[3], f[1] + f[4], f[2] + f[5], cx, cy, cz, sxz, sy, cr, sr);
                    float[] v = pt(f[0] + f[6], f[1] + f[7], f[2] + f[8], cx, cy, cz, sxz, sy, cr, sr);
                    g.pose().pushMatrix();
                    g.pose().mul(new Matrix3x2f((u[0] - o[0]) / 16.0F, (u[1] - o[1]) / 16.0F, (v[0] - o[0]) / 16.0F, (v[1] - o[1]) / 16.0F, o[0], o[1]));
                    float k = f[12];
                    boolean top = f[10] > 0.0F;
                    switch (kind) {
                        case 0:
                            this.blit(g, top ? T_GRASS_TOP : T_GRASS_SIDE, top ? shade(-7226023, k) : shade(16777215, k), a);
                            break;
                        case 1:
                            this.blit(g, T_DIRT, shade(16777215, k), a);
                            break;
                        case 2:
                            this.blit(g, top ? T_LOG_TOP : T_LOG, shade(16777215, k), a);
                            break;
                        default:
                            g.fill(0, 0, 16, 16, this.col(shade(-8934609, k * 0.42F), a));
                            this.blit(g, T_LEAVES, shade(-8934609, k), a);
                    }

                    g.pose().popMatrix();
                }
            }
        }
    }

    private void blit(GuiGraphics g, Identifier tex, int tint, float a) {
        g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0.0F, 0.0F, 16, 16, 16, 16, this.col(tint, a));
    }

    private static float[] pt(float lx, float ly, float lz, float cx, float cy, float cz, float sxz, float sy, float cr, float sr) {
        float x = (lx - 0.5F) * sxz;
        float y = ly * sy - 0.5F;
        float z = (lz - 0.5F) * sxz;
        float rx = x * cr + z * sr;
        float rz = -x * sr + z * cr;
        return proj(cx + rx, cy + y, cz + rz);
    }

    private void sprite(GuiGraphics g, Identifier tex, int tint, float cx, float cy, float size, float scale, float rotDeg, float a) {
        if (!(a <= 0.003F) && !(scale <= 0.001F)) {
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().rotate((float)Math.toRadians(rotDeg));
            g.pose().scale(size * scale / 16.0F, size * scale / 16.0F);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, -8, -8, 0.0F, 0.0F, 16, 16, 16, 16, this.col(tint, a));
            g.pose().popMatrix();
        }
    }

    private void soft(GuiGraphics g, Identifier tex, int rgb, float cx, float cy, float w, float h, float a) {
        if (!(a <= 0.003F)) {
            g.pose().pushMatrix();
            g.pose().translate(cx - w / 2.0F, cy - h / 2.0F);
            g.pose().scale(w / 64.0F, h / 64.0F);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0.0F, 0.0F, 64, 64, 64, 64, this.col(0xFF000000 | rgb, a));
            g.pose().popMatrix();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        makeTextures(mc);
        long now = System.nanoTime();
        boolean ready = mc.getOverlay() == null && mc.isGameLoadFinished();
        if (!ready) {
            this.start = -1L;
        } else if (this.start < 0L) {
            this.start = now;
            LOG.info("Nos client intro: bắt đầu");
        }

        if (this.start < 0L) {
            g.fill(0, 0, this.width, this.height, -16447734);
        } else {
            float ms = FREEZE >= 0.0F ? FREEZE : (float)(now - this.start) / 1000000.0F;
            if (ms >= 7100.0F) {
                this.finish();
            } else {
                this.sounds(mc, ms);
                this.A = 1.0F - lerp(ms, 6500.0F, 600.0F, IN, 0.0F, 1.0F);
                if (ms >= 6500.0F) {
                    if (this.shown == null) {
                        this.shown = this.next;
                        this.next.init(this.width, this.height);
                    }

                    this.next.render(g, mouseX, mouseY, pt);
                }

                this.U = Math.min(this.width / 1920.0F, this.height / 1080.0F);
                g.pose().pushMatrix();
                g.pose().translate(this.width / 2.0F, this.height / 2.0F);
                g.pose().scale(this.U, this.U);
                float W = this.width / this.U;
                float H = this.height / this.U;
                g.fill((int)(-W / 2.0F) - 1, (int)(-H / 2.0F) - 1, (int)(W / 2.0F) + 1, (int)(H / 2.0F) + 1, this.col(-16447734, 1.0F));
                this.soft(g, BG, 1910068, 0.0F, H * 0.02F, W * 1.2F, H * 1.1F, 1.0F);
                this.soft(g, GLOW, 5948751, 0.0F, 0.0F, 900.0F, 900.0F, 0.2F * lerp(ms, 200.0F, 1400.0F, OUT, 0.0F, 1.0F));
                g.pose().pushMatrix();
                g.pose().translate(0.0F, 140.0F);
                float fade = lerp(ms, 3900.0F, 520.0F, IN, 0.0F, 1.0F);
                float treeA = 1.0F - fade;
                g.pose().translate(0.0F, 40.0F * fade);
                g.pose().scale(1.0F - 0.06F * fade, 1.0F - 0.06F * fade);
                float[][] pop = new float[][]{{0.0F, 0.0F}, {0.7F, 1.08F}, {1.0F, 1.0F}};
                float[][] leafPop = new float[][]{{0.0F, 0.0F}, {0.65F, 1.14F}, {1.0F, 1.0F}};
                float[] hp = proj(HERO[0], HERO[1], HERO[2]);
                boolean heroDrawn = false;

                for (IntroScreen.Cube c : this.cubes) {
                    if (!heroDrawn && ms < 3950.0F && c.y + c.z - c.x > HERO[1] + HERO[2] - HERO[0]) {
                        this.hero(g, ms, hp, treeA);
                        heroDrawn = true;
                    }

                    if (c.kind == 2) {
                        float d = c.delay;
                        float[][] grow = new float[][]{{0.0F, 0.7F, 0.0F}, {0.7F, 1.05F, 1.08F}, {1.0F, 1.0F, 1.0F}};
                        this.cube(g, c.x, c.y, c.z, anim(ms, d, 280.0F, SPR, grow, 1), anim(ms, d, 280.0F, SPR, grow, 2), 0.0F, 2, treeA);
                    } else {
                        float s = c.kind == 3 ? anim(ms, c.delay, 380.0F, SPR, leafPop, 1) : anim(ms, c.delay, 480.0F, SPR, pop, 1);
                        this.cube(g, c.x, c.y, c.z, s, s, 0.0F, c.kind, treeA);
                    }

                    if (c.kind == 0 && c.x == 0 && c.z == 0) {
                        this.sapling(g, ms, treeA);
                    }
                }

                if (!heroDrawn && ms < 3950.0F) {
                    this.hero(g, ms, hp, treeA);
                }

                g.pose().popMatrix();
                if (ms >= 3950.0F) {
                    g.pose().pushMatrix();
                    g.pose().translate(0.0F, 140.0F);
                    this.hero(g, ms, hp, 1.0F);
                    g.pose().popMatrix();
                }

                float[] sp = proj(0.0F, 1.0F, 0.0F);
                float sx = sp[0];
                float sy = sp[1] + 140.0F;
                float[][] bone = new float[][]{
                    {0.0F, 460.0F, 340.0F, 40.0F, 1.0F, 0.0F},
                    {0.35F, 340.0F, 100.0F, 0.0F, 1.1F, 1.0F},
                    {0.8F, sx + 40.0F, sy - 60.0F, -30.0F, 1.0F, 1.0F},
                    {1.0F, sx, sy, -50.0F, 0.2F, 0.0F}
                };
                if (ms > 880.0F && ms < 1400.0F) {
                    this.sprite(
                        g,
                        T_BONE,
                        -1,
                        anim(ms, 880.0F, 520.0F, IN, bone, 1),
                        anim(ms, 880.0F, 520.0F, IN, bone, 2),
                        56.0F,
                        anim(ms, 880.0F, 520.0F, IN, bone, 4),
                        anim(ms, 880.0F, 520.0F, IN, bone, 3),
                        anim(ms, 880.0F, 520.0F, IN, bone, 5)
                    );
                }

                for (IntroScreen.Part p : this.parts) {
                    if (!(ms < p.delay) && !(ms > p.delay + p.dur)) {
                        float op = anim(ms, p.delay, p.dur, p.e, p.kf, 1);
                        float dx = anim(ms, p.delay, p.dur, p.e, p.kf, 2);
                        float dy = anim(ms, p.delay, p.dur, p.e, p.kf, 3);
                        this.sprite(
                            g,
                            p.tex,
                            p.tint,
                            p.x0 + dx + p.size / 2.0F,
                            p.y0 + 140.0F + dy + p.size / 2.0F,
                            p.size,
                            anim(ms, p.delay, p.dur, p.e, p.kf, 4),
                            anim(ms, p.delay, p.dur, p.e, p.kf, 5),
                            op
                        );
                    }
                }

                this.brand(g, ms);
                Ui.hint = this.U * 2.2F;
                Component skip = Ui.small(Component.translatable("nosclient.intro.skip"));
                Ui.hint = 1.0F;
                g.pose().pushMatrix();
                g.pose().translate(0.0F, H / 2.0F - 60.0F);
                g.pose().scale(2.2F, 2.2F);
                Ui.text(g, skip, -Ui.width(skip) / 2, 0, this.col(-5326904, 0.55F * lerp(ms, 900.0F, 600.0F, OUT, 0.0F, 1.0F)));
                g.pose().popMatrix();
                g.pose().popMatrix();
            }
        }
    }

    private void sapling(GuiGraphics g, float ms, float a) {
        float[][] kf = new float[][]{
            {0.0F, 0.0F, 0.0F},
            {0.25F, 1.0F, 1.0F},
            {0.62F, 1.0F, 1.0F},
            {0.7F, 1.12F, 1.25F},
            {0.78F, 0.92F, 0.86F},
            {0.86F, 1.06F, 1.14F},
            {0.93F, 1.0F, 1.0F},
            {1.0F, 0.0F, 0.0F}
        };
        float sx = anim(ms, 520.0F, 1380.0F, IN_OUT, kf, 1);
        float sy = anim(ms, 520.0F, 1380.0F, IN_OUT, kf, 2);
        if (!(ms < 520.0F) && !(ms > 1900.0F) && !(sx <= 0.001F)) {
            float[] b = proj(0.0F, 0.5F, 0.0F);
            g.pose().pushMatrix();
            g.pose().translate(b[0], b[1]);
            g.pose().scale(sx * 48.0F / 16.0F, sy * 48.0F * COS30 / 16.0F);
            g.blit(RenderPipelines.GUI_TEXTURED, T_SAPLING, -8, -16, 0.0F, 0.0F, 16, 16, 16, 16, this.col(-1, a));
            g.pose().popMatrix();
        }
    }

    private void hero(GuiGraphics g, float ms, float[] hp, float treeA) {
        float[][] leafPop = new float[][]{{0.0F, 0.0F}, {0.65F, 1.14F}, {1.0F, 1.0F}};
        float s = anim(ms, this.heroDelay, 380.0F, SPR, leafPop, 1);
        float fall = 96.0F * COS30;
        float scale = 1.0F;
        float a = treeA;
        float dx;
        float dy;
        float rot2d;
        if (ms < 3950.0F) {
            float p = Math.min(1.0F, Math.max(0.0F, (ms - 2950.0F) / 1000.0F));
            float[][] shake = new float[][]{{0.0F, 0.0F, 0.0F}, {0.08F, -3.0F, -3.0F}, {0.16F, 3.0F, 3.0F}, {0.24F, -2.0F, -2.0F}, {0.3F, 0.0F, 0.0F}};
            dx = p < 0.3F ? anim(p, 0.0F, 1.0F, LIN, shake, 1) : 0.0F;
            rot2d = p < 0.3F ? anim(p, 0.0F, 1.0F, LIN, shake, 2) : 0.0F;
            dy = p < 0.3F
                ? 0.0F
                : (
                    p < 0.68F
                        ? fall * FALL.at((p - 0.3F) / 0.38F)
                        : (p < 0.84F ? fall - 26.0F * OUT.at((p - 0.68F) / 0.16F) : fall - 26.0F + 26.0F * IN.at((p - 0.84F) / 0.16F))
                );
        } else {
            float k = SPR.at(Math.min(1.0F, (ms - 3950.0F) / 700.0F));
            dx = -hp[0] * k;
            dy = fall + (-210.0F - hp[1] - fall) * k;
            scale = 1.0F + 1.9F * k;
            rot2d = 0.0F;
            a = 1.0F - lerp(ms, 4560.0F, 160.0F, OUT, 0.0F, 1.0F);
        }

        float spin = (float)((Math.PI * 2) * SPIN.at(Math.min(1.0F, Math.max(0.0F, (ms - 3950.0F) / 760.0F))));
        g.pose().pushMatrix();
        g.pose().translate(hp[0] + dx, hp[1] + dy);
        g.pose().rotate((float)Math.toRadians(rot2d));
        g.pose().scale(scale, scale);
        g.pose().translate(-hp[0], -hp[1]);
        this.cube(g, HERO[0], HERO[1], HERO[2], s, s, spin, 3, a);
        g.pose().popMatrix();
    }

    private void brand(GuiGraphics g, float ms) {
        if (!(ms < 4500.0F)) {
            float bx = -300.0F * SLIDE.at(Math.min(1.0F, Math.max(0.0F, (ms - 4950.0F) / 700.0F)));
            float by = -70.0F;
            float[][] lk = new float[][]{{0.0F, 0.0F, 0.92F}, {0.6F, 1.0F, 1.06F}, {1.0F, 1.0F, 1.0F}};
            float la = anim(ms, 4520.0F, 420.0F, SPR, lk, 1);
            float ls = anim(ms, 4520.0F, 420.0F, SPR, lk, 2);
            this.soft(g, GLOW, 5948751, bx, by, 380.0F * ls, 380.0F * ls, 0.45F * Math.min(1.0F, la));
            float rs = lerp(ms, 4560.0F, 700.0F, OUT, 0.5F, 1.5F);
            float ra = lerp(ms, 4560.0F, 700.0F, OUT, 0.9F, 0.0F);
            this.soft(g, RING, 8378483, bx, by, 300.0F * rs, 300.0F * rs, 0.7F * ra);
            long now = System.nanoTime();
            float dt = Math.min(0.05F, (float)(now - this.lastFrame) / 1.0E9F);
            this.lastFrame = now;
            if (ms < 6200.0F) {
                this.runes.emit(bx, by, 260.0F, ms < 5000.0F ? 60.0F : 14.0F, dt, this.col(-3607107, 1.0F));
            }

            this.runes.size = 3.2F;
            this.runes.render(g);
            g.pose().pushMatrix();
            g.pose().translate(bx, by);
            g.pose().scale(ls * 220.0F / 256.0F, ls * 220.0F / 256.0F);
            g.blit(RenderPipelines.GUI_TEXTURED, LOGO, -128, -128, 0.0F, 0.0F, 256, 256, 256, 256, this.col(-1, Math.min(1.0F, la)));
            g.pose().popMatrix();
            if (!(ms < 5050.0F)) {
                float fs = 3.7333333F;
                Ui.hint = this.U * fs;
                Component nos = Ui.logo("Nostal");
                Component gia = Ui.logo("gia");
                Ui.hint = this.U * 26.0F / 6.0F;
                Component sub = Ui.small(Component.literal("L A U N C H E R"));
                Ui.hint = 1.0F;
                float tw = Math.max(Ui.width(nos) + Ui.width(gia), Ui.width(sub) * 26 / 6.0F / fs) * fs;
                float reveal = REVEAL.at(Math.min(1.0F, (ms - 5050.0F) / 720.0F));
                float wx = bx + 150.0F;
                float wy = by - 74.0F;
                g.enableScissor((int)wx, (int)wy - 10, (int)(wx + tw + 60.0F), (int)(wy + 190.0F));
                g.pose().pushMatrix();
                g.pose().translate(wx - 1.05F * tw * (1.0F - reveal), wy);
                g.pose().pushMatrix();
                g.pose().scale(fs, fs);
                Ui.text(g, nos, 0, 0, this.col(-1, 1.0F));
                Ui.text(g, gia, Ui.width(nos), 0, this.col(-10828465, 1.0F));
                g.pose().popMatrix();
                g.pose().translate(6.0F, 120.0F);
                g.pose().scale(4.3333335F, 4.3333335F);
                Ui.text(g, sub, 0, 0, this.col(-5326904, 1.0F));
                g.pose().popMatrix();
                g.disableScissor();
            }
        }
    }

    private void sounds(Minecraft mc, float ms) {
        float[] at = new float[]{150.0F, 1400.0F, 2150.0F, 4560.0F};
        SoundEvent[] ev = new SoundEvent[]{
            SoundEvents.GRASS_PLACE, SoundEvents.BONE_MEAL_USE, SoundEvents.AZALEA_LEAVES_PLACE, SoundEvents.AMETHYST_BLOCK_CHIME
        };

        for (int i = 0; i < at.length; i++) {
            if (!this.sounded[i] && ms >= at[i]) {
                this.sounded[i] = true;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ev[i], 1.0F, 0.6F));
            }
        }
    }

    private void finish() {
        played = true;
        active = null;
        Minecraft.getInstance().setScreen(this.next);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        this.finish();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        this.finish();
        return true;
    }

    @Override
    public void onClose() {
        this.finish();
    }

    @Environment(EnvType.CLIENT)
    static final class Anim {
        static final int TRUNK = 5;
        static final int[] HERO = new int[]{0, 3, 2};
        static final IntroScreen.Anim.Bez OUT = new IntroScreen.Anim.Bez(0.215F, 0.61F, 0.355F, 1.0F);
        static final IntroScreen.Anim.Bez SPR = new IntroScreen.Anim.Bez(0.34F, 1.56F, 0.64F, 1.0F);
        static final IntroScreen.Anim.Bez IN = new IntroScreen.Anim.Bez(0.42F, 0.0F, 1.0F, 1.0F);
        static final IntroScreen.Anim.Bez IN_OUT = new IntroScreen.Anim.Bez(0.42F, 0.0F, 0.58F, 1.0F);
        static final IntroScreen.Anim.Bez LIN = new IntroScreen.Anim.Bez(0.0F, 0.0F, 1.0F, 1.0F);
        static final IntroScreen.Anim.Bez FALL = new IntroScreen.Anim.Bez(0.55F, 0.0F, 1.0F, 0.45F);
        static final IntroScreen.Anim.Bez SLIDE = new IntroScreen.Anim.Bez(0.65F, 0.0F, 0.35F, 1.0F);
        static final IntroScreen.Anim.Bez REVEAL = new IntroScreen.Anim.Bez(0.16F, 1.0F, 0.3F, 1.0F);
        static final IntroScreen.Anim.Bez SPIN = new IntroScreen.Anim.Bez(0.3F, 0.7F, 0.2F, 1.0F);

        static float anim(float ms, float delay, float dur, IntroScreen.Anim.Bez e, float[][] kf, int ch) {
            float p = e.at(Math.min(1.0F, Math.max(0.0F, (ms - delay) / dur)));
            int i = 0;

            while (i < kf.length - 2 && p > kf[i + 1][0]) {
                i++;
            }

            float[] a = kf[i];
            float[] b = kf[i + 1];
            float k = (p - a[0]) / (b[0] - a[0]);
            return a[ch] + (b[ch] - a[ch]) * k;
        }

        private static float lerp(float ms, float delay, float dur, IntroScreen.Anim.Bez e, float a, float b) {
            return a + (b - a) * e.at(Math.min(1.0F, Math.max(0.0F, (ms - delay) / dur)));
        }

        static List<int[]> leafCells() {
            List<int[]> out = new ArrayList<>();
            int seed = 11;

            for (int dy = 0; dy >= -3; dy--) {
                int r = Math.max(1 - dy / 2, 0);
                int y = 6 + dy;

                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (Math.abs(x) == r && Math.abs(z) == r && r > 0) {
                            if (dy == 0) {
                                continue;
                            }

                            seed = (int)(seed * 16807L % 2147483647L);
                            if ((seed & 1) == 0) {
                                continue;
                            }
                        }

                        if (x != 0 || z != 0 || y > 5) {
                            out.add(new int[]{x, y, z});
                        }
                    }
                }
            }

            return out;
        }

        public static void main(String[] a) {
            List<int[]> lv = leafCells();
            if (lv.size() != 55) {
                throw new AssertionError("lá " + lv.size());
            }

            if (lv.stream().noneMatch(l -> l[0] == HERO[0] && l[1] == HERO[1] && l[2] == HERO[2])) {
                throw new AssertionError("thiếu khối lá chính");
            }

            for (IntroScreen.Anim.Bez e : new IntroScreen.Anim.Bez[]{OUT, SPR, IN, IN_OUT, SLIDE, REVEAL, SPIN}) {
                if (Math.abs(e.at(0.0F)) > 1.0E-4 || Math.abs(e.at(1.0F) - 1.0F) > 1.0E-4) {
                    throw new AssertionError(e);
                }
            }

            if (SPR.at(0.5F) <= 1.0F) {
                throw new AssertionError("SPR phải vượt 1 (nảy)");
            }

            if (Math.abs(anim(3030.0F, 2950.0F, 1000.0F, LIN, new float[][]{{0.0F, 0.0F}, {0.08F, -3.0F}, {1.0F, 0.0F}}, 1) + 3.0F) > 0.001) {
                throw new AssertionError("anim");
            }

            System.out.println("INTRO OK");
        }

        @Environment(EnvType.CLIENT)
        record Bez(float x1, float y1, float x2, float y2) {
            float at(float x) {
                if (!(x <= 0.0F) && !(x >= 1.0F)) {
                    float t = x;

                    for (int i = 0; i < 8; i++) {
                        float cx = this.bx(t) - x;
                        float d = 3.0F * (1.0F - t) * (1.0F - t) * this.x1 + 6.0F * (1.0F - t) * t * (this.x2 - this.x1) + 3.0F * t * t * (1.0F - this.x2);
                        if (Math.abs(cx) < 1.0E-4F || d == 0.0F) {
                            break;
                        }

                        t = Math.min(1.0F, Math.max(0.0F, t - cx / d));
                    }

                    return 3.0F * (1.0F - t) * (1.0F - t) * t * this.y1 + 3.0F * (1.0F - t) * t * t * this.y2 + t * t * t;
                } else {
                    return x <= 0.0F ? 0.0F : 1.0F;
                }
            }

            private float bx(float t) {
                return 3.0F * (1.0F - t) * (1.0F - t) * t * this.x1 + 3.0F * (1.0F - t) * t * t * this.x2 + t * t * t;
            }
        }
    }

    @Environment(EnvType.CLIENT)
    private record Cube(int x, int y, int z, int kind, float delay) {
    }

    @Environment(EnvType.CLIENT)
    private record Part(Identifier tex, int tint, float size, float x0, float y0, float[][] kf, float delay, float dur, IntroScreen.Anim.Bez e) {
    }
}
