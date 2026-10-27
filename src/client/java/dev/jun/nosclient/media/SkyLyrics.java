package dev.jun.nosclient.media;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.feat.Feats;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.theme.Theme;
import dev.jun.nosclient.theme.ThemePalette;
import dev.jun.nosclient.typography.Typography;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.joml.Matrix4f;

/**
 * Lời nhạc trên trời. Mỗi dòng mới sinh ra đúng hướng người chơi đang nhìn (nâng lên khỏi chân trời),
 * từng chữ ngưng tụ từ bụi sao, chữ sáng dần theo nhịp hát (karaoke), dòng cũ tan thành bụi bay lên.
 * Chữ nằm trong thế giới ở bán kính theo tầm nhìn nên núi, cây, nhà che được như cảnh vật thật.
 * Toán chuyển động ở {@link LyricMotion}.
 */
@Environment(EnvType.CLIENT)
public final class SkyLyrics {
    public static LyricsService service;
    private static final int LIGHT = 15728880;
    private static final int ROW = 13;
    private static final int DUST = 5;
    private static final int MOTES = 7;
    private static final float[][] EDGE = new float[][]{{-0.22F, 0.0F}, {0.22F, 0.0F}, {0.0F, -0.22F}, {0.0F, 0.22F}};
    private static final float[][] AURA = new float[][]{
        {-0.55F, 0.0F}, {0.55F, 0.0F}, {0.0F, -0.55F}, {0.0F, 0.55F}, {-0.39F, -0.39F}, {0.39F, -0.39F}, {-0.39F, 0.39F}, {0.39F, 0.39F}
    };
    private static final ArrayList<Shown> shown = new ArrayList<>();
    private static final LinkedHashMap<Key, List<Line>> layouts = new LinkedHashMap<>(16, 0.75F, true);
    private static Object level;
    private static String key = "";
    private static String next = "";
    private static long position;
    private static long ambientAt;
    private static float glow;
    private static float presence;
    private static float speed = 1.0F;
    private static int mode;
    private static double radius = 120.0;
    private static CelestialTypography.Ink ink = CelestialTypography.ink(0, -1, -4927299, true);

    private SkyLyrics() {
    }

    public static void init() {
        service = new LyricsService(FabricLoader.getInstance().getConfigDir().resolve("nosclient-lyrics"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            service.configure(SceneConfig.INSTANCE.lyrics, SceneConfig.INSTANCE.lyricsAutoFetch);
            if (mc.getOverlay() != null) {
                layouts.clear();
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> service.close());
        WorldRenderEvents.END_EXTRACTION.register(context -> extract());
        AuroraSky.init();
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            PoseStack matrices = context.matrices();
            if (matrices != null && presence > 0.01F && !shown.isEmpty()) {
                render(matrices, context.consumers());
            }
        });
    }

    private static void extract() {
        Minecraft mc = Minecraft.getInstance();
        SceneConfig c = SceneConfig.INSTANCE;
        long now = System.nanoTime();
        if (level != mc.level) {
            level = mc.level;
            shown.clear();
            layouts.clear();
            key = "";
        }

        boolean active = c.lyrics && mc.level != null && !mc.options.hideGui && mc.level.dimensionType().skybox() == Skybox.OVERWORLD;
        String line = "";
        String upcoming = "";
        String id = "";
        long start = 0L;
        long length = 4000L;
        if (active) {
            LyricsService.Snapshot state = service.snapshot();
            List<LrcTimeline.Line> lines = state.timeline().lines();
            position = state.track().position(now) - c.lyricsDelay;
            int index = now - state.track().sampledAt() > 15000000000L ? -1 : state.timeline().indexAt(position);
            if (index >= 0) {
                line = lines.get(index).text();
                start = lines.get(index).atMs();
                if (index + 1 < lines.size()) {
                    length = lines.get(index + 1).atMs() - start;
                    upcoming = c.lyricsNext ? lines.get(index + 1).text() : "";
                }
            }

            id = state.track().key() + ":" + index;
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        float elapsed = ambientAt == 0L ? 0.0F : Math.min(0.1F, (now - ambientAt) / 1.0E9F);
        ambientAt = now;
        // Chui vào hang/nhà thì lời mờ đi chứ không tắt phụt; trời mở lại thì hiện lại.
        float target = active && mc.level.canSeeSky(camera.blockPosition()) ? 1.0F : 0.0F;
        presence = c.animations ? Ui.approach(presence, target, elapsed, 3.0F) : target;
        mode = c.animations ? c.lyricsMotion : LyricMotion.NONE;
        speed = 480.0F / c.lyricsDuration;
        if (!id.equals(key)) {
            key = id;
            Shown last = shown.isEmpty() ? null : shown.getLast();

            for (Shown s : shown) {
                if (s.died == 0L) {
                    s.died = now;
                }
            }

            if (line.isBlank()) {
                AuroraSky.calm();
            } else {
                AuroraSky.kick(line.length(), length);
            }

            if (!line.isBlank()) {
                float[] anchor = c.lyricsFollow
                    ? LyricMotion.anchor(camera.yRot(), camera.xRot(), last == null ? 0.0F : last.bearing, last == null ? 0.0F : last.elevation,
                        last != null && now - last.born < 20000000000L)
                    : new float[]{c.lyricsBearing, c.lyricsElevation};
                shown.add(new Shown(line, id.hashCode() * 31L + now, anchor[0], anchor[1], now, start, length));
            }
        }

        shown.removeIf(s -> s.died != 0L && (now - s.died) / 1.0E9F > LyricMotion.lifetime(s.text.length(), speed) * (mode == LyricMotion.NONE ? 0.0F : 1.0F));

        while (shown.size() > 4) {
            shown.removeFirst();
        }

        next = upcoming;
        // Đặt chữ sát mép tầm nhìn: mọi địa hình đã nạp (núi xa, rặng cây) đều ở trước chữ nên che được nó.
        // Cỡ chữ tỉ lệ với bán kính nên trên màn hình vẫn giữ nguyên; mặt phẳng xa của camera là 4× tầm nhìn nên không bị cắt.
        // Đặt sát mép tầm nhìn: mọi địa hình đã nạp (núi xa, rặng cây) đều ở trước chữ nên che được nó.
        radius = Math.max(40.0, mc.options.getEffectiveRenderDistance() * 16 * 0.92);
        ThemePalette palette = Theme.palette();
        int sky = camera.attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, 1.0F);
        ink = CelestialTypography.ink(sky, palette.text(), palette.accent(), c.lyricsBlendSky);
        float targetGlow = c.lyricsCinematic ? ink.haloOpacity() * (float)c.lyricsGlow : 0.0F;
        glow = c.animations ? Ui.approach(glow, targetGlow, elapsed, 4.0F) : targetGlow;
    }

    private static void render(PoseStack matrices, MultiBufferSource buffers) {
        SceneConfig c = SceneConfig.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        double fov = Math.max(5.0F, Math.min(170.0F, Feats.lastFov));
        double raster = SkyTextPlane.density(mc.getWindow().getHeight(), fov, c.lyricsSize, c.lyricsWidth * 1.04, c.lyricsHeight * 1.1);
        long now = System.nanoTime();
        Shown newest = null;

        for (Shown s : shown) {
            if (s.died == 0L) {
                newest = s;
            }
        }

        for (Shown s : shown) {
            double yaw = Math.toRadians(s.bearing);
            double pitch = Math.toRadians(s.elevation);
            matrices.pushPose();

            try {
                matrices.translate(-Math.sin(yaw) * Math.cos(pitch) * radius, Math.sin(pitch) * radius, Math.cos(yaw) * Math.cos(pitch) * radius);
                matrices.mulPose(SkyTextPlane.rotation(s.bearing, s.elevation));
                float scale = (float)(SkyTextPlane.WORLD_SCALE * c.lyricsSize * radius / 120.0);
                matrices.scale(scale * (float)c.lyricsWidth * 1.04F, -scale * (float)c.lyricsHeight * 1.1F, scale);
                float bottom = line(font, s, raster, now, s == newest, matrices.last().pose(), buffers);
                if (s == newest && !next.isBlank()) {
                    upcoming(font, raster, (now - s.born) / 1.0E9F, bottom, matrices.last().pose(), buffers);
                }
            } finally {
                matrices.popPose();
            }
        }
    }

    /** Vẽ một dòng; trả về đáy dòng (toạ độ chữ) để đặt dòng kế tiếp. */
    private static float line(Font font, Shown s, double raster, long now, boolean newest, Matrix4f base, MultiBufferSource buffers) {
        SceneConfig c = SceneConfig.INSTANCE;
        List<Line> rows = wrap(font, s.text, raster);
        int n = 0;

        for (Line row : rows) {
            for (Letter letter : row.letters) {
                n += letter.blank ? 0 : 1;
            }
        }

        float age = (now - s.born) / 1.0E9F;
        float exit = s.died == 0L ? -1.0F : (now - s.died) / 1.0E9F;
        float music = (position - s.startMs) / 1000.0F;
        float seconds = c.animations ? (float)(now / 1.0E9 % 3600.0) : 0.0F;
        float[] cx = new float[n];
        float[] cy = new float[n];
        LyricMotion.Pose[] poses = new LyricMotion.Pose[n];
        Letter[] glyphs = new Letter[n];
        // Dòng bị thay trôi lên nhường chỗ (như cuộn lời) trong lúc tan, không đè lên dòng mới.
        float lift = exit < 0.0F || mode == LyricMotion.NONE ? 0.0F : LyricMotion.smooth(exit * speed / 0.5F) * (rows.size() * ROW + 8);
        float top = -(rows.size() - 1) * ROW / 2.0F - lift;
        float widest = 0.0F;
        int i = 0;

        for (int r = 0; r < rows.size(); r++) {
            Line row = rows.get(r);
            widest = Math.max(widest, row.width);
            float x = -row.width / 2.0F;

            for (Letter letter : row.letters) {
                if (!letter.blank) {
                    float sinceLit = exit >= 0.0F ? 9.0F : music - LyricMotion.litAt(i, n, s.lengthMs / 1000.0F);
                    cx[i] = x + letter.advance / 2.0F;
                    cy[i] = top + r * ROW + 4.5F
                        + (c.lyricsCinematic ? (float)CelestialTypography.wave(cx[i], row.width / 2.0F, seconds) : 0.0F);
                    poses[i] = LyricMotion.letter(mode, age, exit, i, n, s.seed, sinceLit, speed);
                    glyphs[i] = letter;
                    i++;
                }

                x += letter.advance + (float)c.lyricsTracking;
            }
        }

        int body = ink.body();
        // Hạt sáng vẽ trước, chữ đè lên: quầng nằm sau nét chữ.
        if (mode == LyricMotion.CONSTELLATION && presence > 0.01F) {
            VertexConsumer stars = buffers.getBuffer(SkyTextMaterial.stars());
            int halo = ink.halo();
            int white = ThemePalette.mix(body, 0xFFFFFF, 0.5);

            for (int k = 0; k < n; k++) {
                LyricMotion.Pose p = poses[k];
                float x = cx[k] + p.x();
                float y = cy[k] + p.y();
                if (p.trail() > 0.02F) {
                    // Đuôi sao chổi: nối chỗ chữ đang ở với chỗ nó ở 0.09 s trước.
                    LyricMotion.Pose back = LyricMotion.letter(mode, age - 0.09F, exit, k, n, s.seed, -1.0F, speed);
                    streak(stars, base, cx[k] + back.x(), cy[k] + back.y(), x, y, 1.6F * p.scale(), halo, p.trail() * p.alpha() * presence * 0.8F);
                    star(stars, base, x, y, 2.6F, white, p.trail() * p.alpha() * presence);
                }

                if (p.flash() > 0.02F) {
                    star(stars, base, x, y, 7.5F * p.flash() + 2.0F, white, p.flash() * presence * 0.9F);
                }

                float[] spark = exit >= 0.0F ? null : LyricMotion.spark(music - LyricMotion.litAt(k, n, s.lengthMs / 1000.0F), k, s.seed);
                if (spark != null) {
                    star(stars, base, x + spark[0], y + spark[1], 2.2F * spark[2], halo, spark[3] * presence);
                }

                if (exit >= 0.0F) {
                    for (int d = 0; d < DUST; d++) {
                        float[] dust = LyricMotion.dust(exit, k, d, s.seed, speed);
                        if (dust != null) {
                            star(stars, base, cx[k] + dust[0], cy[k] + dust[1], 1.4F * dust[2], d == 0 ? white : halo, dust[3] * presence * 0.8F);
                        }
                    }
                }
            }

            if (newest) {
                // Vài đốm sao lơ lửng quanh dòng đang hát cho bầu trời có hơi thở.
                float appear = LyricMotion.smooth(age / 1.2F);
                float rx = widest / 2.0F + 16.0F;
                float ry = rows.size() * ROW / 2.0F + 10.0F;

                for (int k = 0; k < MOTES; k++) {
                    double angle = LyricMotion.hash(s.seed, k, 30) * Math.PI * 2.0 + seconds * (0.08 + 0.06 * LyricMotion.hash(s.seed, k, 31));
                    float twinkle = 0.5F + 0.5F * (float)Math.sin(seconds * (1.5F + LyricMotion.hash(s.seed, k, 32) * 2.0F) + k);
                    star(stars, base, (float)Math.cos(angle) * rx, 4.5F + (float)Math.sin(angle) * ry, 1.6F,
                        halo, appear * twinkle * presence * 0.45F);
                }
            }
        }

        if (poster()) {
            posterPass(font, n, poses, glyphs, cx, cy, base, buffers);
            return top + rows.size() * ROW;
        }

        int dim = ThemePalette.mix(body, ink.halo(), 0.5);
        // Lượt mềm (quầng + viền) cho mọi chữ rồi mới tới lượt lõi: gom theo loại vẽ để bộ đệm không xả liên tục.
        for (int k = 0; k < n; k++) {
            LyricMotion.Pose p = poses[k];
            float alpha = p.alpha() * presence * (0.5F + 0.5F * p.lit());
            if (!(alpha < 0.03F)) {
                Matrix4f m = letter(base, cx[k], cy[k], p, glyphs[k].advance).translate(0.0F, 0.0F, -0.01F);
                float aura = alpha * (glow * (0.3F + 0.7F * p.lit()) + 0.5F * p.flash());
                float edge = alpha * ink.edgeOpacity();
                if (aura > 0.005F) {
                    for (float[] d : AURA) {
                        font.drawInBatch(glyphs[k].glyph, d[0] * 1.9F, d[1] * 1.9F, Ui.alpha(ink.halo(), Math.min(1.0F, aura * 0.22F)), false, m, buffers,
                            DisplayMode.POLYGON_OFFSET, 0, LIGHT);
                        font.drawInBatch(glyphs[k].glyph, d[0], d[1], Ui.alpha(ink.halo(), Math.min(1.0F, aura)), false, m, buffers,
                            DisplayMode.POLYGON_OFFSET, 0, LIGHT);
                    }
                }

                if (edge > 0.02F) {
                    for (float[] d : EDGE) {
                        font.drawInBatch(glyphs[k].glyph, d[0], d[1], Ui.alpha(ink.edge(), edge), false, m, buffers, DisplayMode.POLYGON_OFFSET, 0, LIGHT);
                    }
                }
            }
        }

        for (int k = 0; k < n; k++) {
            LyricMotion.Pose p = poses[k];
            float alpha = p.alpha() * presence * (0.5F + 0.5F * p.lit());
            if (!(alpha < 0.03F)) {
                int color = Ui.lerpCol(dim, body, p.lit());
                font.drawInBatch(glyphs[k].glyph, 0.0F, 0.0F, Ui.alpha(color, Math.min(1.0F, alpha)), false, letter(base, cx[k], cy[k], p, glyphs[k].advance),
                    buffers, DisplayMode.NORMAL, 0, LIGHT);
            }
        }

        return top + rows.size() * ROW;
    }

    /**
     * Chữ poster "trong suốt": lòng chữ không có màu riêng mà là cảnh phía sau (bản chụp SceneCopy) bị đảo rồi ánh
     * xạ lên dải màu đo từ ảnh mẫu — trời sáng ra tím đậm, cây tối ra hồng rực, vân lá/mây/núi vẫn hiện trong chữ.
     * Không vẽ bóng: lớp bóng lùi sau mặt chữ qua polygon offset thò ra thành viền sáng-mờ lởm chởm khi nhìn gần;
     * ảnh mẫu gần như không có bóng, đường viền đã có sẵn nhờ cảnh tối ở mép do lòng chữ đảo màu.
     */
    private static void posterPass(Font font, int n, LyricMotion.Pose[] poses, Letter[] glyphs, float[] cx, float[] cy, Matrix4f base, MultiBufferSource buffers) {
        int inkColor = 0xFF000000 | SceneConfig.INSTANCE.lyricsInk;
        // Chụp cảnh trước khi có chữ: lòng chữ lấy mẫu bản chụp này (vẽ đè lên chính framebuffer đang đọc thì GL
        // cấm). Xả bộ đệm trước để cảnh đã đủ mọi thứ vẽ trước chữ.
        flush(buffers);
        SceneCopy.capture();

        for (int k = 0; k < n; k++) {
            LyricMotion.Pose p = poses[k];
            float alpha = Math.min(1.0F, p.alpha() * presence);
            if (!(alpha < 0.03F)) {
                // Mực sáng dần theo nhịp hát: chưa tới lượt thì tông trầm, tới lượt thì hồng rực.
                int lit = Ui.lerpCol(ThemePalette.mix(inkColor, 0x2A0B3D, 0.45) | 0xFF000000, inkColor, p.lit());
                Matrix4f m = letter(base, cx[k], cy[k], p, glyphs[k].advance);
                // Một bản duy nhất: in chồng nhiều bản cùng mặt phẳng thì chúng tranh depth với nhau, mép nét lởm
                // chởm và hở sáng (jun chụp được ở cự ly gần).
                font.drawInBatch(glyphs[k].glyph, 0.0F, 0.0F, Ui.alpha(lit, alpha), false, m, buffers, DisplayMode.NORMAL, 0, LIGHT);
            }
        }

        flush(buffers);
    }

    private static void flush(MultiBufferSource buffers) {
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
    }

    private static void upcoming(Font font, double raster, float age, float bottom, Matrix4f base, MultiBufferSource buffers) {
        float alpha = LyricMotion.smooth((age - 0.35F) / 0.6F) * 0.38F * presence;
        if (!(alpha < 0.03F)) {
            Matrix4f m = new Matrix4f(base).scale(0.72F, 0.72F, 0.72F);
            float y = bottom / 0.72F + 6.0F;

            for (Line row : wrap(font, next, raster * 0.72)) {
                float x = -row.width / 2.0F;

                for (Letter letter : row.letters) {
                    font.drawInBatch(letter.glyph, x, y, Ui.alpha(ink.body(), alpha), false, m, buffers, DisplayMode.NORMAL, 0, LIGHT);
                    x += letter.advance + (float)SceneConfig.INSTANCE.lyricsTracking;
                }

                y += ROW - 1;
            }
        }
    }

    private static Matrix4f letter(Matrix4f base, float cx, float cy, LyricMotion.Pose p, float advance) {
        return new Matrix4f(base).translate(cx + p.x(), cy + p.y(), 0.0F).rotateZ(p.rot()).scale(p.scale(), p.scale(), 1.0F).translate(-advance / 2.0F, -4.5F, 0.0F);
    }

    private static void star(VertexConsumer vc, Matrix4f m, float x, float y, float r, int rgb, float alpha) {
        if (!(alpha < 0.01F)) {
            int color = Ui.alpha(0xFF000000 | rgb, Math.min(1.0F, alpha));
            vc.addVertex(m, x - r, y - r, 0.0F).setUv(-1.0F, -1.0F).setColor(color);
            vc.addVertex(m, x - r, y + r, 0.0F).setUv(-1.0F, 1.0F).setColor(color);
            vc.addVertex(m, x + r, y + r, 0.0F).setUv(1.0F, 1.0F).setColor(color);
            vc.addVertex(m, x + r, y - r, 0.0F).setUv(1.0F, -1.0F).setColor(color);
        }
    }

    private static void streak(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float width, int rgb, float alpha) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float)Math.sqrt(dx * dx + dy * dy);
        if (!(alpha < 0.01F) && !(length < 0.3F)) {
            float nx = -dy / length * width;
            float ny = dx / length * width;
            int tail = rgb & 0xFFFFFF;
            int head = Ui.alpha(0xFF000000 | rgb, Math.min(1.0F, alpha));
            vc.addVertex(m, x0 + nx, y0 + ny, 0.0F).setUv(2.0F, -1.0F).setColor(tail);
            vc.addVertex(m, x0 - nx, y0 - ny, 0.0F).setUv(2.0F, 1.0F).setColor(tail);
            vc.addVertex(m, x1 - nx, y1 - ny, 0.0F).setUv(2.0F, 1.0F).setColor(head);
            vc.addVertex(m, x1 + nx, y1 + ny, 0.0F).setUv(2.0F, -1.0F).setColor(head);
        }
    }

    private static boolean poster() {
        return SceneConfig.INSTANCE.lyricsStyle == 0;
    }

    private static List<Line> wrap(Font font, String raw, double raster) {
        SceneConfig c = SceneConfig.INSTANCE;
        boolean poster = poster();
        // Chữ khối in hoa như poster; toLowerCase/UpperCase theo locale gốc để giữ dấu tiếng Việt.
        String text = poster ? raw.toUpperCase(java.util.Locale.ROOT) : raw;
        Key key = new Key(text, Typography.worldDensityIndex(raster) + (poster ? 100 : 0), c.lyricsWidth, c.lyricsTracking);
        List<Line> cached = layouts.get(key);
        if (cached != null) {
            return cached;
        }

        List<String> rows = CelestialTypography.wrap(
            text, part -> font.getSplitter().stringWidth(Ui.worldText(part, raster, poster)), c.lyricsTracking, 170.0 / c.lyricsWidth
        );
        List<Line> built = rows.stream().map(row -> {
            List<Letter> letters = CelestialTypography.clusters(row).stream().map(part -> {
                MutableComponent component = Ui.worldText(part, raster, poster);
                return new Letter(component.getVisualOrderText(), font.getSplitter().stringWidth(component), part.isBlank());
            }).toList();
            return new Line(letters, (float)(letters.stream().mapToDouble(Letter::advance).sum() + Math.max(0, letters.size() - 1) * c.lyricsTracking));
        }).toList();
        layouts.put(key, built);
        if (layouts.size() > 16) {
            layouts.pollFirstEntry();
        }

        return built;
    }

    @Environment(EnvType.CLIENT)
    private static final class Shown {
        final String text;
        final long seed;
        final float bearing;
        final float elevation;
        final long born;
        final long startMs;
        final long lengthMs;
        long died;

        Shown(String text, long seed, float bearing, float elevation, long born, long startMs, long lengthMs) {
            this.text = text;
            this.seed = seed;
            this.bearing = bearing;
            this.elevation = elevation;
            this.born = born;
            this.startMs = startMs;
            this.lengthMs = lengthMs;
        }
    }

    @Environment(EnvType.CLIENT)
    private record Key(String text, int density, double width, double tracking) {
    }

    @Environment(EnvType.CLIENT)
    private record Letter(FormattedCharSequence glyph, float advance, boolean blank) {
    }

    @Environment(EnvType.CLIENT)
    private record Line(List<Letter> letters, float width) {
    }
}
