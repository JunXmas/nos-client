package dev.jun.nosclient.sky;

import com.mojang.blaze3d.platform.NativeImage;

import dev.jun.nosclient.ClientEffects;
import dev.jun.nosclient.gui.effects.Anim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

public final class SkyTextures {
    public static final Identifier SPRITES =
            Identifier.fromNamespaceAndPath("nosclient", "sky/sprites");
    public static final int W = 2048;
    public static final int H = 512;
    private static final ExecutorService EXEC =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "ClientEffects-SkyPaint");
                        t.setDaemon(true);
                        t.setPriority(4);
                        return t;
                    });
    private static volatile String wantKey = "";
    private static volatile SkyTextures.Pending pending;
    private static String doneKey = "";
    private static int[] base;
    private static int[] fx;
    private static int version = 0;
    private static boolean spritesReady;
    private static boolean spritesFailed;
    private static volatile NativeImage spritePending;
    private static boolean spritesRequested;
    private static long shownAt = 0L;

    private SkyTextures() {}

    public static SkyPainter.Spec spec(SkyPreset p) {
        if (p == SkyPreset.CUSTOM) {
            SkyPreset.applyCustom();
        }

        return new SkyPainter.Spec(
                p.name(),
                p.top,
                p.horizon,
                WorldSky.aurora1(p),
                WorldSky.aurora2(p),
                ClientEffects.cfg().fxStars ? p.stars * ClientEffects.cfg().starDensity : 0,
                p.aurora && ClientEffects.cfg().fxAurora,
                p.moon,
                p.weather.name());
    }

    public static String key(SkyPainter.Spec s) {
        return s.kind()
                + "_"
                + Integer.toHexString(s.top())
                + "_"
                + Integer.toHexString(s.horizon())
                + "_"
                + Integer.toHexString(s.a1())
                + "_"
                + Integer.toHexString(s.a2())
                + "_"
                + s.stars()
                + "_"
                + s.aurora()
                + "_"
                + Integer.toHexString(s.moon());
    }

    public static void request(SkyPreset p) {
        SkyPainter.Spec s = spec(p);
        String key = key(s);
        if (!key.equals(wantKey)) {
            wantKey = key;
            EXEC.execute(
                    () -> {
                        try {
                            if (!key.equals(wantKey)) {
                                return;
                            }

                            int[] b =
                                    SkyPainter.opaqueBase(
                                            SkyPainter.smooth(
                                                    SkyPainter.base(s, 2048, 512),
                                                    2048,
                                                    512,
                                                    false),
                                            s,
                                            2048,
                                            512);
                            if (!key.equals(wantKey)) {
                                return;
                            }

                            int[] f = SkyPainter.fx(s, 2048, 512);
                            if (f != null) {
                                f = SkyPainter.smooth(f, 2048, 512, true);
                            }

                            pending = new SkyTextures.Pending(key, b, f);
                        } catch (Throwable var4) {
                            ClientEffects.LOG.warn("Himmel konnte nicht gemalt werden", var4);
                        }
                    });
        }
    }

    public static void update() {
        if (!spritesRequested) {
            spritesRequested = true;
            EXEC.execute(
                    () -> {
                        try {
                            spritePending = image(SkyPainter.sprites(), 256, 64);
                        } catch (Throwable var1x) {
                            ClientEffects.LOG.warn("Partikel-Bilder fehlen", var1x);
                            spritesFailed = true;
                        }
                    });
        }

        NativeImage sp = spritePending;
        if (sp != null) {
            spritePending = null;

            try {
                Minecraft.getInstance()
                        .getTextureManager()
                        .register(SPRITES, new DynamicTexture(() -> "nosclient_sprites", sp));
                spritesReady = true;
            } catch (Throwable var2) {
                spritesFailed = true;
            }
        }

        SkyTextures.Pending pd = pending;
        if (pd != null) {
            pending = null;
            if (pd.key().equals(wantKey)) {
                base = pd.base();
                fx = pd.fx();
                doneKey = pd.key();
                version++;
                shownAt = System.currentTimeMillis();
            }
        }
    }

    public static boolean ready() {
        return base != null && doneKey.equals(wantKey);
    }

    public static boolean hasFx() {
        return fx != null;
    }

    public static int[] base() {
        return base;
    }

    public static int[] fx() {
        return fx;
    }

    public static int version() {
        return version;
    }

    public static boolean spritesReady() {
        return spritesReady && !spritesFailed;
    }

    public static float fadeIn() {
        return Anim.outCubic(Anim.since(shownAt, 1400L));
    }

    private static NativeImage image(int[] argb, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, 2);
        img.setRGB(0, 0, w, h, argb, 0, w);
        ByteArrayOutputStream out = new ByteArrayOutputStream(w * h);
        ImageIO.write(img, "png", out);
        return NativeImage.read(out.toByteArray());
    }

    private record Pending(String key, int[] base, int[] fx) {}
}
