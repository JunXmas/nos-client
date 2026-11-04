package dev.jun.nosclient.sky;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.jun.nosclient.ClientEffects;
import dev.jun.nosclient.config.ClientSettings;
import dev.jun.nosclient.config.EffectsConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import org.joml.Matrix4f;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

public final class SkyImage {
    public static final String RECOMMENDED = "4096 x 2048";
    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("nosclient", "sky_picture");
    private static final int MAX_W = 4096;
    private static final int MAX_H = 2048;
    private static String loadedName;
    private static boolean ready;
    private static String error = "";
    private static int imgW;
    private static int imgH;
    private static final Map<String, Identifier> THUMBS = new ConcurrentHashMap<>();
    private static final Map<String, int[]> DECODED = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService THUMB_EXEC =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "Nos Client sky previews");
                        t.setDaemon(true);
                        return t;
                    });
    private static final int TW = 160;
    private static final int TH = 80;
    private static List<String> cached = List.of();
    private static long cachedAt;

    private SkyImage() {}

    public static Path dir() {
        return ClientSettings.directory().resolve("sky");
    }

    public static List<String> files() {
        try {
            Files.createDirectories(dir());

            try (Stream<Path> s = Files.list(dir())) {
                return s.filter(x$0 -> Files.isRegularFile(x$0))
                        .map(p -> p.getFileName().toString())
                        .filter(
                                n -> {
                                    String l = n.toLowerCase(Locale.ROOT);
                                    return l.endsWith(".png")
                                            || l.endsWith(".jpg")
                                            || l.endsWith(".jpeg");
                                })
                        .sorted()
                        .toList();
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String error() {
        return error;
    }

    public static String size() {
        return ready ? imgW + " x " + imgH : "";
    }

    public static Identifier thumb(String name) {
        Identifier id = THUMBS.get(name);
        if (id != null) {
            return id;
        }

        int[] px = DECODED.remove(name);
        if (px == null) {
            if (LOADING.add(name)) {
                THUMB_EXEC.execute(
                        () -> {
                            try {
                                BufferedImage src = ImageIO.read(dir().resolve(name).toFile());
                                if (src == null) {
                                    return;
                                }

                                BufferedImage img = new BufferedImage(160, 80, 2);
                                Graphics2D g = img.createGraphics();
                                g.setRenderingHint(
                                        RenderingHints.KEY_INTERPOLATION,
                                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                g.drawImage(src, 0, 0, 160, 80, null);
                                g.dispose();
                                int[] out = new int[12800];

                                for (int yx = 0; yx < 80; yx++) {
                                    for (int xx = 0; xx < 160; xx++) {
                                        out[yx * 160 + xx] = img.getRGB(xx, yx) | 0xFF000000;
                                    }
                                }

                                DECODED.put(name, out);
                            } catch (Throwable var7) {
                            }
                        });
            }

            return null;
        } else {
            NativeImage ni = new NativeImage(160, 80, false);

            for (int y = 0; y < 80; y++) {
                for (int x = 0; x < 160; x++) {
                    ni.setPixel(x, y, px[y * 160 + x]);
                }
            }

            id =
                    Identifier.fromNamespaceAndPath(
                            "nosclient", "sky_thumb/" + Integer.toHexString(name.hashCode()));
            Minecraft.getInstance()
                    .getTextureManager()
                    .register(id, new DynamicTexture(() -> "nosclient sky preview", ni));
            THUMBS.put(name, id);
            return id;
        }
    }

    public static void reload() {
        for (Identifier id : THUMBS.values()) {
            Minecraft.getInstance().getTextureManager().release(id);
        }

        THUMBS.clear();
        LOADING.clear();
        cachedAt = 0L;
        loadedName = null;
        ready = false;
    }

    public static void openFolder() {
        try {
            Files.createDirectories(dir());
            Util.getPlatform().openPath(dir());
        } catch (Exception var1) {
        }
    }

    private static String chosen() {
        EffectsConfig c = ClientEffects.cfg();
        long now = System.currentTimeMillis();
        if (now - cachedAt > 3000L) {
            cached = files();
            cachedAt = now;
        }

        List<String> all = cached;
        if (all.isEmpty()) {
            return null;
        } else {
            return c.skyImageFile != null && all.contains(c.skyImageFile)
                    ? c.skyImageFile
                    : all.get(0);
        }
    }

    public static boolean active() {
        EffectsConfig c = ClientEffects.cfg();
        if (!c.skyImage) {
            return false;
        }

        String name = chosen();
        if (name == null) {
            error = "none";
            return false;
        }

        if (!name.equals(loadedName)) {
            load(name);
        }

        return ready;
    }

    private static void load(String name) {
        loadedName = name;
        ready = false;
        error = "";

        try {
            BufferedImage src = ImageIO.read(dir().resolve(name).toFile());
            if (src == null) {
                error = "format";
                return;
            }

            int w = src.getWidth();
            int h = src.getHeight();
            int tw = Math.max(2048, Math.min(4096, w));
            int th = tw / 2;
            BufferedImage img = src;
            if (tw != w || th != h) {
                img = new BufferedImage(tw, th, 2);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, tw, th, null);
                g.dispose();
            }

            NativeImage ni = new NativeImage(tw, th, false);

            for (int y = 0; y < th; y++) {
                for (int x = 0; x < tw; x++) {
                    ni.setPixel(x, y, img.getRGB(x, y) | 0xFF000000);
                }
            }

            Minecraft.getInstance()
                    .getTextureManager()
                    .register(ID, new DynamicTexture(() -> "nosclient sky picture", ni));
            imgW = w;
            imgH = h;
            ready = true;
        } catch (Throwable e) {
            error = "read";
            ClientEffects.LOG.warn("[Nos Client] Could not load the sky picture {}", name, e);
        }
    }

    static void draw(
            MultiBufferSource buffers, Matrix4f base, double dist, float alpha, float brightness) {
        if (ready && !(alpha < 0.01F)) {
            VertexConsumer vc = buffers.getBuffer(RenderTypes.text(ID));
            int b = Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, brightness)));
            int col = Math.round(255.0F * Math.min(1.0F, alpha)) << 24 | b << 16 | b << 8 | b;
            int na = 64;
            int ne = 32;

            for (int j = 0; j < ne; j++) {
                double e0 = -90.0 + 180.0 * j / ne;
                double e1 = -90.0 + 180.0 * (j + 1) / ne;
                float v0 = (float) (0.5 - e0 / 180.0);
                float v1 = (float) (0.5 - e1 / 180.0);

                for (int i = 0; i < na; i++) {
                    double a0 = 360.0 * i / na;
                    double a1 = 360.0 * (i + 1) / na;
                    float u0 = (float) i / na;
                    float u1 = (float) (i + 1) / na;
                    float[] p00 = p(a0, e0, dist);
                    float[] p10 = p(a1, e0, dist);
                    float[] p11 = p(a1, e1, dist);
                    float[] p01 = p(a0, e1, dist);
                    v(vc, base, p00, u0, v0, col);
                    v(vc, base, p10, u1, v0, col);
                    v(vc, base, p11, u1, v1, col);
                    v(vc, base, p01, u0, v1, col);
                    v(vc, base, p01, u0, v1, col);
                    v(vc, base, p11, u1, v1, col);
                    v(vc, base, p10, u1, v0, col);
                    v(vc, base, p00, u0, v0, col);
                }
            }
        }
    }

    private static float[] p(double azDeg, double elDeg, double d) {
        double a = Math.toRadians(azDeg);
        double e = Math.toRadians(elDeg);
        double ce = Math.cos(e);
        return new float[] {
            (float) (-Math.sin(a) * ce * d),
            (float) (Math.sin(e) * d),
            (float) (Math.cos(a) * ce * d)
        };
    }

    private static void v(VertexConsumer vc, Matrix4f m, float[] p, float u, float v, int col) {
        vc.addVertex(m, p[0], p[1], p[2]).setColor(col).setUv(u, v).setLight(15728880);
    }
}
