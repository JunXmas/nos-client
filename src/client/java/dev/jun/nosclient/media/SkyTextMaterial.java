package dev.jun.nosclient.media;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
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
     * Lòng chữ poster: một lượt, trong suốt thường, lấy mẫu bản chụp cảnh (SceneCopy) ở Sampler1 rồi đảo + ánh xạ
     * sang tông mực trong shader. Ghi depth như chữ thường để địa hình phía trước vẫn che.
     */
    private static final RenderPipeline POSTER = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.TEXT_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("nosclient", "pipeline/sky_poster"))
            .withVertexShader(Identifier.fromNamespaceAndPath("nosclient", "core/sky_poster"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("nosclient", "core/sky_poster"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withUniform("Globals", UniformType.UNIFORM_BUFFER)
            .withDepthWrite(true)
            .build()
    );
    private static final Map<Identifier, RenderType> POSTERS = new HashMap<>();

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

    public static RenderType type(Identifier atlas, boolean soft) {
        if (!soft && poster(atlas)) {
            return POSTERS.computeIfAbsent(
                atlas,
                id -> RenderType.create(
                    "nos_sky_poster",
                    RenderSetup.builder(POSTER).withTexture("Sampler0", id).withTexture("Sampler1", SceneCopy.ID).bufferSize(32768).createRenderSetup()
                )
            );
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
