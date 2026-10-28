package dev.jun.nosclient.media;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.theme.HsvColor;
import dev.jun.nosclient.theme.ThemePalette;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Cực quang theo nhạc: ba màn sáng vắt ngang trời, phía sau mọi địa hình (bị núi, cây che như trời thật). Không đọc
 * được âm thanh nên "beat" lấy từ lời nhạc đồng bộ: mỗi câu mới là một nhịp bừng sáng chạy dọc màn, câu hát dồn dập
 * (nhiều chữ/giây) thì màn sáng hơn, lượn nhanh hơn. Màu đỉnh màn theo màu chữ lyrics.
 */
@Environment(EnvType.CLIENT)
public final class AuroraSky {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("nosclient", "pipeline/aurora"))
            .withVertexShader(Identifier.fromNamespaceAndPath("nosclient", "core/sky_star"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("nosclient", "core/aurora"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, Mode.QUADS)
            .withBlend(BlendFunction.ADDITIVE)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );
    private static final int RIBBONS = 3;
    private static final int SEGMENTS = 72;
    private static RenderType type;
    /** Thời gian CPU vẽ cực quang (cộng dồn) — chỉ để probe. */
    public static long renderNanos;
    private static float pulse;
    private static float energy;
    private static float targetEnergy;
    private static float ripple = 2.0F;
    private static float night;
    private static float shown;
    private static long last;
    /** Thời gian riêng của cực quang: chạy nhanh hơn khi sôi động nhưng có trần, không nhảy theo từng câu. */
    private static double clock;
    /** Lệch sắc độ của màu nhịp so với màu chữ (độ), đổi mỗi câu; hue hiện tại trôi mượt về đích. */
    private static float hueTarget;
    private static float hue;
    private static int beats;

    private AuroraSky() {
    }

    static void init() {
        ShaderPacks.assign(PIPELINE, "BEACON_BEAM");
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (context.matrices() != null) {
                long t0 = System.nanoTime();
                render(context.matrices(), context.consumers());
                renderNanos += System.nanoTime() - t0;
            }
        });
    }

    /** Một câu lời mới: nhịp bừng sáng + đặt mức năng lượng theo tốc độ hát (chữ/giây). */
    static void kick(int letters, long lengthMs) {
        float perSecond = letters / Math.max(0.6F, lengthMs / 1000.0F);
        targetEnergy = Math.clamp((perSecond - 3.0F) / 9.0F, 0.15F, 1.0F);
        // Nhịp dồn dập thì chỉ đẩy thêm một phần, không bừng lại từ đầu mỗi câu (drop không còn chớp loạn).
        pulse = Math.min(1.0F, pulse * 0.4F + 0.7F);
        ripple = -0.1F;
        // Màu nhịp: lần lượt bốn tông quanh màu chữ (0°, +40°, −35°, +80°), như đèn sân khấu đổi theo câu.
        float[] steps = {0.0F, 40.0F, -35.0F, 80.0F};
        hueTarget = steps[beats++ % steps.length];
    }

    /** Màu nhịp hiện tại (đã trôi mượt), cho cả trời và màn cực quang. */
    public static int beatColor() {
        int ink = 0xFF000000 | SceneConfig.INSTANCE.lyricsInk;
        HsvColor base = HsvColor.fromRgb(ink);
        return 0xFF000000 | new HsvColor(base.hue() + hue, Math.max(0.55, base.saturation()), Math.max(0.85, base.value())).rgb();
    }

    /** Độ đậm phủ màu nhịp lên trời đêm (0..1), cho mixin màu trời. */
    public static float skyTint() {
        return shown * night * (0.07F + 0.05F * energy + 0.09F * pulse);
    }

    /** Hết lời (nhạc dạo, dừng phát): lắng dần. */
    static void calm() {
        targetEnergy = 0.0F;
    }

    private static void render(PoseStack matrices, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        SceneConfig c = SceneConfig.INSTANCE;
        long now = System.nanoTime();
        float dt = last == 0L ? 0.0F : Math.min(0.1F, (now - last) / 1.0E9F);
        last = now;
        boolean on = c.lyricsAurora && mc.level != null && mc.level.dimensionType().skybox() == Skybox.OVERWORLD;
        shown = c.animations ? Ui.approach(shown, on ? 1.0F : 0.0F, dt, 1.5F) : (on ? 1.0F : 0.0F);
        if (shown < 0.01F || mc.level == null) {
            return;
        }

        int sky = mc.gameRenderer.getMainCamera().attributeProbe().getValue(EnvironmentAttributes.SKY_COLOR, 1.0F);
        // Ban ngày cộng sáng lên trời sáng thì gần như không thấy; vẫn để một chút cho biết đang bật.
        float targetNight = 1.0F - (float)Math.clamp((ThemePalette.luminance(sky) - 0.06) / 0.35, 0.0, 0.9);
        night = Ui.approach(night, targetNight, dt, 2.0F);
        energy = Ui.approach(energy, targetEnergy, dt, 0.6F);
        pulse *= (float)Math.exp(-dt * 1.1F);
        ripple += dt / 2.2F;
        hue = Ui.approach(hue, hueTarget, dt, 1.4F);
        // Đồng hồ riêng: tối đa 1.6× tốc độ thường kể cả lúc drop.
        clock += c.animations ? dt * (1.0 + 0.6 * energy) : 0.0;
        clock %= 3600.0;
        float t = (float)clock;
        double radius = Math.max(48.0, mc.options.getEffectiveRenderDistance() * 16 * 0.97);
        int top = beatColor();
        if (type == null) {
            type = RenderType.create("nos_aurora", RenderSetup.builder(PIPELINE).bufferSize(65536).createRenderSetup());
        }

        VertexConsumer vc = buffers.getBuffer(type);
        Matrix4f m = matrices.last().pose();
        float sway = 1.0F + 0.6F * energy + 0.4F * pulse;

        for (int r = 0; r < RIBBONS; r++) {
            double center = r * 120.0 + 30.0;
            Vector3f[] bottom = new Vector3f[SEGMENTS + 1];
            Vector3f[] up = new Vector3f[SEGMENTS + 1];
            float[] glow = new float[SEGMENTS + 1];

            for (int i = 0; i <= SEGMENTS; i++) {
                float s = i / (float)SEGMENTS;
                double bearing = center + 110.0 * (s - 0.5) + 3.0 * Math.sin(s * 10.0 + t * 0.6) * sway;
                double base = 16.0 + 6.0 * Math.sin(s * Math.PI * 2.0 + t * 0.3 + r);
                double height = (22.0 + 10.0 * Math.sin(s * Math.PI * 3.0 - t * 0.2 + r * 2.0)) * (1.0 + 0.3 * energy + 0.2 * pulse);
                bottom[i] = SkyTextPlane.direction(bearing, base).mul((float)radius);
                up[i] = SkyTextPlane.direction(bearing, base + height).mul((float)radius);
                float edge = (float)Math.pow(Math.sin(Math.PI * s), 0.7);
                float wave = (float)Math.exp(-(s - ripple) * (s - ripple) / 0.006);
                glow[i] = shown * (0.15F + 0.85F * night) * edge * (0.5F + 0.25F * energy + 0.25F * pulse + 0.6F * pulse * wave);
            }

            float phase = t * 0.06F + r * 5.0F;

            for (int i = 0; i < SEGMENTS; i++) {
                float u0 = i / (float)SEGMENTS * 12.0F + phase;
                float u1 = (i + 1) / (float)SEGMENTS * 12.0F + phase;
                int a0 = Ui.alpha(top, Math.min(1.0F, glow[i]));
                int a1 = Ui.alpha(top, Math.min(1.0F, glow[i + 1]));
                vc.addVertex(m, bottom[i].x, bottom[i].y, bottom[i].z).setUv(u0, 0.0F).setColor(a0);
                vc.addVertex(m, up[i].x, up[i].y, up[i].z).setUv(u0, 1.0F).setColor(a0);
                vc.addVertex(m, up[i + 1].x, up[i + 1].y, up[i + 1].z).setUv(u1, 1.0F).setColor(a1);
                vc.addVertex(m, bottom[i + 1].x, bottom[i + 1].y, bottom[i + 1].z).setUv(u1, 0.0F).setColor(a1);
            }
        }
    }
}
