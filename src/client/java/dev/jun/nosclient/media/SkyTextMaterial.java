package dev.jun.nosclient.media;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class SkyTextMaterial {
    private static final RenderPipeline CORE = pipeline("sky_text", true);
    private static final RenderPipeline SOFT = pipeline("sky_text_soft", false);
    /** Hạt sáng cộng dồn (bụi sao, vệt bay, chòm sao): không ghi depth nhưng vẫn bị địa hình che. */
    private static final RenderPipeline STAR = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("nosclient", "pipeline/sky_star"))
            .withVertexShader(Identifier.fromNamespaceAndPath("nosclient", "core/sky_star"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("nosclient", "core/sky_star"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, Mode.QUADS)
            .withBlend(BlendFunction.ADDITIVE)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );
    /**
     * Lòng chữ poster: ba lượt blend trên cùng hình chữ (xem sky_poster.fsh) — đảo màu cảnh rồi ánh xạ sang
     * hai tông mực. Không ghi depth ở hai lượt đầu để cả ba lượt đều qua kiểm depth với cùng giá trị; lượt cuối
     * ghi depth như chữ thường để địa hình phía trước vẫn che.
     */
    private static final RenderPipeline[] POSTER = {
        poster("invert", "PASS_INVERT", new BlendFunction(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ONE_MINUS_SRC_COLOR, SourceFactor.ZERO, DestFactor.ONE), false),
        poster("scale", "PASS_SCALE", new BlendFunction(SourceFactor.ZERO, DestFactor.SRC_COLOR, SourceFactor.ZERO, DestFactor.ONE), false),
        poster("add", null, new BlendFunction(SourceFactor.ONE, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE), true)
    };

    private static RenderPipeline poster(String name, String define, BlendFunction blend, boolean depthWrite) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("nosclient", "pipeline/sky_poster_" + name))
            .withVertexShader(Identifier.fromNamespaceAndPath("nosclient", "core/sky_poster"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("nosclient", "core/sky_poster"))
            .withSampler("Sampler0")
            .withBlend(blend)
            .withDepthWrite(depthWrite);
        if (define != null) {
            builder.withShaderDefine(define);
        }

        return RenderPipelines.register(builder.build());
    }

    @SuppressWarnings("unchecked")
    private static final Map<Identifier, RenderType>[] POSTERS = new Map[]{new HashMap<>(), new HashMap<>(), new HashMap<>()};

    private static RenderType stars;
    private static final Map<Identifier, RenderType> CORES = new HashMap<>();
    private static final Map<Identifier, RenderType> SOFTS = new HashMap<>();

    private SkyTextMaterial() {
    }

    private static RenderPipeline pipeline(String name, boolean depthWrite) {
        return RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("nosclient", "pipeline/" + name))
                .withVertexShader(Identifier.fromNamespaceAndPath("nosclient", "core/sky_text"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("nosclient", "core/sky_text"))
                .withSampler("Sampler0")
                .withDepthWrite(depthWrite)
                .build()
        );
    }

    public static void init() {
    }

    public static RenderType stars() {
        if (stars == null) {
            stars = RenderType.create("nos_sky_stars", RenderSetup.builder(STAR).bufferSize(65536).createRenderSetup());
        }

        return stars;
    }

    public static boolean poster(Identifier atlas) {
        return atlas.getPath().startsWith("sky_poster");
    }

    /** Render type của lượt thứ pass (0 đảo, 1 nhân, 2 cộng) cho atlas chữ poster. */
    public static RenderType posterPass(Identifier atlas, int pass) {
        return POSTERS[pass].computeIfAbsent(atlas, id -> {
            RenderType type = RenderType.create(
                "nos_sky_poster_" + pass, RenderSetup.builder(POSTER[pass]).withTexture("Sampler0", id).bufferSize(32768).createRenderSetup()
            );
            ATLAS_OF.put(type, id);
            return type;
        });
    }

    /**
     * Font chỉ biết một loại vẽ "thường" cho mỗi atlas — là lượt cuối (cộng). Đổi sang lượt khác của cùng atlas;
     * loại vẽ không phải của chữ poster thì giữ nguyên.
     */
    public static RenderType remap(RenderType type, int pass) {
        Identifier atlas = ATLAS_OF.get(type);
        return atlas == null || pass == 2 ? type : posterPass(atlas, pass);
    }

    private static final Map<RenderType, Identifier> ATLAS_OF = new HashMap<>();

    public static RenderType type(Identifier atlas, boolean soft) {
        if (!soft && poster(atlas)) {
            return posterPass(atlas, 2);
        }

        return (soft ? SOFTS : CORES)
            .computeIfAbsent(
                atlas,
                id -> RenderType.create(
                    soft ? "nos_sky_soft" : "nos_sky_core",
                    RenderSetup.builder(soft ? SOFT : CORE).withTexture("Sampler0", id).bufferSize(32768).createRenderSetup()
                )
            );
    }
}
