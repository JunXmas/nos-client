package dev.jun.nosclient.media;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.feat.Feats;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.theme.Theme;
import dev.jun.nosclient.theme.ThemePalette;
import dev.jun.nosclient.typography.Typography;
import java.util.LinkedHashMap;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.AfterEntities;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.EndExtraction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class SkyLyrics {
    public static LyricsService service;
    private static final SkyLyrics.Frame HIDDEN = new SkyLyrics.Frame(
        "", "", "", 0.0, 40.0, 1.0F, 1.0F, false, 120.0, CelestialTypography.ink(0, -1, -4927299, true), 1.0, 1.2, 0.0, false, 0.0F
    );
    private static volatile SkyLyrics.Frame frame = HIDDEN;
    private static final LyricTransition transition = new LyricTransition();
    private static Object level;
    private static float glow;
    private static long ambientAt;
    private static final LinkedHashMap<SkyLyrics.Key, List<SkyLyrics.Line>> layouts = new LinkedHashMap<>(16, 0.75F, true);
    private static final float[][] EDGE = new float[][]{{-0.22F, 0.0F}, {0.22F, 0.0F}, {0.0F, -0.22F}, {0.0F, 0.22F}};
    private static final float[][] AURA = new float[][]{
        {-0.55F, 0.0F}, {0.55F, 0.0F}, {0.0F, -0.55F}, {0.0F, 0.55F}, {-0.39F, -0.39F}, {0.39F, -0.39F}, {-0.39F, 0.39F}, {0.39F, 0.39F}
    };

    private SkyLyrics() {
    }

    private static void line(Font font, SkyLyrics.Line line, float y, int color, float alpha, Matrix4f pose, MultiBufferSource buffers, SkyLyrics.Frame frame) {
        if (!(alpha < 0.03F)) {
            Matrix4f softPose = new Matrix4f(pose).translate(0.0F, 0.0F, -0.01F);
            double seconds = SceneConfig.INSTANCE.animations ? System.nanoTime() / 1.0E9 : 0.0;
            float aura = alpha * frame.glow;
            float edge = alpha * frame.ink.edgeOpacity();

            for (int pass = 0; pass < 2; pass++) {
                float x = -line.width / 2.0F;

                for (SkyLyrics.Letter part : line.letters) {
                    float offset = frame.cinematic ? (float)CelestialTypography.wave(x + part.advance / 2.0F, line.width / 2.0F, seconds) : 0.0F;
                    if (pass != 0) {
                        font.drawInBatch(part.glyph, x, y + offset, Ui.alpha(color, alpha), false, pose, buffers, DisplayMode.NORMAL, 0, 15728880);
                    } else {
                        if (aura > 0.005F) {
                            for (float[] d : AURA) {
                                font.drawInBatch(
                                    part.glyph,
                                    x + d[0] * 1.9F,
                                    y + offset + d[1] * 1.9F,
                                    Ui.alpha(frame.ink.halo(), aura * 0.22F),
                                    false,
                                    softPose,
                                    buffers,
                                    DisplayMode.POLYGON_OFFSET,
                                    0,
                                    15728880
                                );
                                font.drawInBatch(
                                    part.glyph,
                                    x + d[0],
                                    y + offset + d[1],
                                    Ui.alpha(frame.ink.halo(), aura),
                                    false,
                                    softPose,
                                    buffers,
                                    DisplayMode.POLYGON_OFFSET,
                                    0,
                                    15728880
                                );
                            }
                        }

                        if (edge > 0.02F) {
                            for (float[] d : EDGE) {
                                font.drawInBatch(
                                    part.glyph,
                                    x + d[0],
                                    y + offset + d[1],
                                    Ui.alpha(frame.ink.edge(), edge),
                                    false,
                                    softPose,
                                    buffers,
                                    DisplayMode.POLYGON_OFFSET,
                                    0,
                                    15728880
                                );
                            }
                        }
                    }

                    x = (float)(x + (part.advance + frame.tracking));
                }
            }
        }
    }

    private static List<SkyLyrics.Line> wrap(Font font, String text, double raster, SkyLyrics.Frame frame) {
        SkyLyrics.Key key = new SkyLyrics.Key(text, Typography.worldDensityIndex(raster), frame.width, frame.tracking);
        List<SkyLyrics.Line> cached = layouts.get(key);
        if (cached != null) {
            return cached;
        }

        List<String> rows = CelestialTypography.wrap(
            text, part -> font.getSplitter().stringWidth(Ui.worldText(part, raster)), frame.tracking, 170.0 / frame.width
        );
        List<SkyLyrics.Line> built = rows.stream()
            .map(
                row -> {
                    List<SkyLyrics.Letter> letters = CelestialTypography.clusters(row).stream().map(part -> {
                        MutableComponent component = Ui.worldText(part, raster);
                        return new SkyLyrics.Letter(component.getVisualOrderText(), font.getSplitter().stringWidth(component));
                    }).toList();
                    return new SkyLyrics.Line(
                        letters, (float)(letters.stream().mapToDouble(SkyLyrics.Letter::advance).sum() + Math.max(0, letters.size() - 1) * frame.tracking)
                    );
                }
            )
            .toList();
        layouts.put(key, built);
        if (layouts.size() > 12) {
            layouts.pollFirstEntry();
        }

        return built;
    }

    public static void init() {
        service = new LyricsService(FabricLoader.getInstance().getConfigDir().resolve("nosclient-lyrics"));
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
            service.configure(SceneConfig.INSTANCE.lyrics, SceneConfig.INSTANCE.lyricsAutoFetch);
            if (mc.getOverlay() != null) {
                layouts.clear();
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)mc -> service.close());
        WorldRenderEvents.END_EXTRACTION
            .register(
                (EndExtraction)context -> {
                    Minecraft mc = Minecraft.getInstance();
                    SceneConfig c = SceneConfig.INSTANCE;
                    if (level != mc.level) {
                        level = mc.level;
                        transition.clear();
                        layouts.clear();
                    }

                    if (c.lyrics
                        && mc.level != null
                        && !mc.options.hideGui
                        && mc.level.dimensionType().skybox() == Skybox.OVERWORLD
                        && mc.level.canSeeSky(mc.gameRenderer.getMainCamera().blockPosition())) {
                        LyricsService.Snapshot state = service.snapshot();
                        long now = System.nanoTime();
                        int index = state.timeline().indexAt(state.track().position(now) - c.lyricsDelay);
                        String line = index < 0 ? "" : state.timeline().lines().get(index).text();
                        String next = index >= 0 && c.lyricsNext && index + 1 < state.timeline().lines().size()
                            ? state.timeline().lines().get(index + 1).text()
                            : "";
                        if (now - state.track().sampledAt() > 15000000000L) {
                            line = "";
                            next = "";
                        }

                        LyricTransition.Frame motion = transition.update(
                            state.track().key() + ":" + index, line, now, c.lyricsDuration, c.animations && c.lyricsMotion != 2
                        );
                        double radius = Math.min(120.0, Math.max(24.0, mc.options.getEffectiveRenderDistance() * 16 * 0.35));
                        ThemePalette palette = Theme.palette();
                        int sky = mc.gameRenderer.getMainCamera().attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, 1.0F);
                        CelestialTypography.Ink ink = CelestialTypography.ink(sky, palette.text(), palette.accent(), c.lyricsBlendSky);
                        float targetGlow = c.lyricsCinematic ? ink.haloOpacity() * (float)c.lyricsGlow : 0.0F;
                        float elapsed = ambientAt == 0L ? 0.0F : Math.min(0.1F, (float)(now - ambientAt) / 1.0E9F);
                        ambientAt = now;
                        glow = c.animations ? Ui.approach(glow, targetGlow, elapsed, 4.0F) : targetGlow;
                        frame = new SkyLyrics.Frame(
                            motion.current(),
                            motion.previous(),
                            next,
                            c.lyricsBearing,
                            c.lyricsElevation,
                            (float)c.lyricsSize,
                            motion.progress(),
                            c.lyricsMotion == 0,
                            radius,
                            ink,
                            c.lyricsWidth,
                            c.lyricsHeight,
                            c.lyricsTracking,
                            c.lyricsCinematic,
                            glow
                        );
                    } else {
                        frame = HIDDEN;
                        transition.clear();
                    }
                }
            );
        WorldRenderEvents.AFTER_ENTITIES
            .register(
                (AfterEntities)context -> {
                    SkyLyrics.Frame f = frame;
                    if (!f.line.isBlank() || !f.previous.isBlank()) {
                        PoseStack matrices = context.matrices();
                        if (matrices != null) {
                            Font font = Minecraft.getInstance().font;
                            double fov = Math.max(5.0F, Math.min(170.0F, Feats.lastFov));
                            double raster = SkyTextPlane.density(Minecraft.getInstance().getWindow().getHeight(), fov, f.size, f.width * 1.04, f.height * 1.1);
                            double yaw = Math.toRadians(f.yaw);
                            double pitch = Math.toRadians(f.elevation);
                            double radius = f.radius;
                            matrices.pushPose();

                            try {
                                matrices.translate(
                                    -Math.sin(yaw) * Math.cos(pitch) * radius, Math.sin(pitch) * radius, Math.cos(yaw) * Math.cos(pitch) * radius
                                );
                                matrices.mulPose(SkyTextPlane.rotation(f.yaw, f.elevation));
                                float scale = (float)(0.9F * f.size * radius / 120.0);
                                matrices.scale(scale * (float)f.width * 1.04F, -scale * (float)f.height * 1.1F, scale);
                                float t = f.progress;
                                if (!f.previous.isBlank() && t < 0.98F) {
                                    matrices.pushPose();
                                    float shrink = f.slide ? 1.0F - 0.07F * t : 1.0F;
                                    matrices.translate(0.0F, 0.0F, -0.03F);
                                    matrices.scale(shrink, shrink, shrink);
                                    List<SkyLyrics.Line> old = wrap(font, f.previous, raster, f);
                                    float y = -(old.size() - 1) * (9 + 4) / 2.0F - (f.slide ? 17.0F * t : 0.0F);

                                    for (SkyLyrics.Line text : old) {
                                        line(font, text, y, f.ink.body(), 1.0F - t, matrices.last().pose(), context.consumers(), f);
                                        y += 9 + 4;
                                    }

                                    matrices.popPose();
                                }

                                if (f.line.isBlank() || t < 0.02F) {
                                    return;
                                }

                                matrices.pushPose();
                                float enter = f.slide ? 0.88F + 0.12F * t : 1.0F;
                                matrices.scale(enter, enter, enter);
                                List<SkyLyrics.Line> lines = wrap(font, f.line, raster, f);
                                float y = -(lines.size() - 1) * (9 + 4) / 2.0F + (f.slide ? 17.0F * (1.0F - t) : 0.0F);

                                for (SkyLyrics.Line text : lines) {
                                    line(font, text, y, f.ink.body(), t, matrices.last().pose(), context.consumers(), f);
                                    y += 9 + 4;
                                }

                                matrices.popPose();
                                if (!f.next.isBlank()) {
                                    matrices.scale(0.72F, 0.72F, 0.72F);
                                    float alpha = Math.max(0.0F, (t - 0.2F) / 0.8F) * 0.4F;

                                    for (SkyLyrics.Line text : wrap(font, f.next, raster * 0.72, f)) {
                                        line(
                                            font,
                                            text,
                                            y / 0.72F + 6.0F + (f.slide ? (1.0F - t) * 5.0F : 0.0F),
                                            f.ink.body(),
                                            alpha,
                                            matrices.last().pose(),
                                            context.consumers(),
                                            f
                                        );
                                        y += (9 + 3) * 0.72F;
                                    }
                                }
                            } finally {
                                matrices.popPose();
                            }
                        }
                    }
                }
            );
    }

    @Environment(EnvType.CLIENT)
    private record Frame(
        String line,
        String previous,
        String next,
        double yaw,
        double elevation,
        float size,
        float progress,
        boolean slide,
        double radius,
        CelestialTypography.Ink ink,
        double width,
        double height,
        double tracking,
        boolean cinematic,
        float glow
    ) {
    }

    @Environment(EnvType.CLIENT)
    private record Key(String text, int density, double width, double tracking) {
    }

    @Environment(EnvType.CLIENT)
    private record Letter(FormattedCharSequence glyph, float advance) {
    }

    @Environment(EnvType.CLIENT)
    private record Line(List<SkyLyrics.Letter> letters, float width) {
    }
}
