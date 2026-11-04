package dev.jun.nosclient.sky;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import dev.jun.nosclient.ClientEffects;
import dev.jun.nosclient.config.ClientSettings;
import dev.jun.nosclient.config.EffectsConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.nio.file.Files;
import java.time.LocalDateTime;

public final class LiveSkyShader {
    private static final Identifier TEX =
            Identifier.fromNamespaceAndPath("nosclient", "live_sky_params");
    private static final RenderPipeline PIPE =
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath("nosclient", "pipeline/live_sky"))
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withVertexShader(Identifier.fromNamespaceAndPath("nosclient", "core/live_sky"))
                    .withFragmentShader(
                            Identifier.fromNamespaceAndPath("nosclient", "core/live_sky"))
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, Mode.QUADS)
                    .build();
    private static RenderType type;
    private static DynamicTexture tex;
    private static int state = 0;
    private static final int[] last = new int[16];
    private static final int[] next = new int[16];

    private LiveSkyShader() {}

    public static boolean ready() {
        if (state != 0) {
            return state > 0;
        }

        try {
            if (!RenderSystem.getDevice().precompilePipeline(PIPE).isValid()) {
                ClientEffects.LOG.warn(
                        "Himmel-Shader laesst sich nicht kompilieren - nehme den alten Himmel");
                state = -1;
                return false;
            }

            NativeImage img = new NativeImage(16, 1, false);
            tex = new DynamicTexture(() -> "nosclient_live_sky", img);
            Minecraft.getInstance().getTextureManager().register(TEX, tex);
            type =
                    RenderType.create(
                            "nosclient_live_sky",
                            RenderSetup.builder(PIPE)
                                    .withTexture("Sampler0", TEX)
                                    .createRenderSetup());
            state = 1;
            ClientEffects.LOG.info("Himmel-Shader aktiv");
        } catch (Throwable var1) {
            ClientEffects.LOG.warn("Himmel-Shader nicht verfuegbar - nehme den alten Himmel", var1);
            state = -1;
        }

        status(state > 0 ? "aktiv" : "aus (alter Himmel)");
        return state > 0;
    }

    private static void status(String s) {
        try {
            String gpu = "";

            try {
                gpu =
                        RenderSystem.getDevice().getRenderer()
                                + " / "
                                + RenderSystem.getDevice().getVersion();
            } catch (Throwable var3) {
            }

            Files.writeString(
                    ClientSettings.directory().resolve("sky-status.txt"),
                    LocalDateTime.now() + "\nHimmel-Shader: " + s + "\nGrafik: " + gpu + "\n");
        } catch (Throwable var4) {
        }
    }

    public static void disable(Throwable t) {
        ClientEffects.LOG.warn("Himmel-Shader abgeschaltet", t);
        state = -1;
        status("Fehler beim Zeichnen: " + t);
    }

    public static RenderType type() {
        return type;
    }

    public static void params(
            SkyPreset p, int a1, int a2, double t, EffectsConfig c, Matrix4f pose) {
        float pattern = 1.0F;
        float lively = 0.6F;
        float breath = 1.0F;
        float aurora = 1.0F;
        if (c != null) {
            pattern = Math.max(0.2F, Math.min(1.5F, c.skyPattern));
            lively = Math.max(0.0F, Math.min(1.0F, c.skyLively));
            breath =
                    c.skyBreath <= 0.01F
                            ? 1.0F
                            : 0.85F
                                    + 0.15F
                                            * (float)
                                                    Math.sin(
                                                            t * 0.45 * Math.max(0.2F, c.skyBreath));
            aurora = c.fxAurora ? 1.0F : 0.0F;
        }

        next[0] = rgba(p.top, 255);
        next[1] = rgba(p.horizon, 255);
        next[2] = rgba(a1, 255);
        next[3] = rgba(a2, 255);
        LiveSky.Body b = LiveSky.body(p);
        next[4] = b == null ? 0 : rgba(b.color(), b.kind() + 1);
        next[5] = bytes(b8(pattern / 1.5F), b8(lively), b8(breath), b8(aurora));
        next[6] = b == null ? 0 : pair(b.az() * 100.0, (b.el() + 90.0) * 100.0);
        next[7] = (b == null ? 0 : pair(b.radius() * 1000.0, 0.0) & -65536) | p.ordinal() << 8;
        next[13] = bytes(c == null || c.fxBeams ? 255 : 0, 0, 0, 0);
        Matrix3f inv = new Matrix3f().set(pose).invert();
        float[] m =
                new float[] {
                    inv.m00, inv.m01, inv.m02, inv.m10, inv.m11, inv.m12, inv.m20, inv.m21, inv.m22,
                    0.0F
                };

        for (int i = 0; i < 5; i++) {
            next[8 + i] = pair(enc(m[i * 2]), enc(m[i * 2 + 1]));
        }

        boolean changed = false;

        for (int i = 0; i < 16; i++) {
            if (next[i] != last[i]) {
                last[i] = next[i];
                changed = true;
                int v = next[i];
                tex.getPixels().setPixel(i, 0, (v & 0xFF) << 24 | v >>> 8);
            }
        }

        if (changed) {
            tex.upload();
        }
    }

    private static int pair(double v1, double v2) {
        int a = (int) Math.max(0L, Math.min(65535L, Math.round(v1)));
        int b = (int) Math.max(0L, Math.min(65535L, Math.round(v2)));
        return a << 16 | b;
    }

    private static double enc(float v) {
        return (Math.max(-2.0F, Math.min(2.0F, v)) + 2.0) / 4.0 * 65535.0;
    }

    private static int b8(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255.0F)));
    }

    private static int rgba(int rgb, int a) {
        return (rgb & 16777215) << 8 | a & 0xFF;
    }

    private static int bytes(int r, int g, int b, int a) {
        return r << 24 | g << 16 | b << 8 | a;
    }
}
