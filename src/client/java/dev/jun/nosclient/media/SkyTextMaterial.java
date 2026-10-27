package dev.jun.nosclient.media;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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

    public static RenderType type(Identifier atlas, boolean soft) {
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
