package dev.jun.nosclient.cosmetic;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** A neutral cutout preview, independent of the menu scene's lightmap and fog. */
public final class CosmeticMaterials {
	private static final RenderPipeline FLAT = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
		.withLocation(Identifier.fromNamespaceAndPath("nosclient", "pipeline/cosmetic_cutout"))
		.withVertexShader(Identifier.fromNamespaceAndPath("nosclient", "core/cosmetic_preview"))
		.withFragmentShader(Identifier.fromNamespaceAndPath("nosclient", "core/cosmetic_preview"))
		.withSampler("Sampler0").withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
		.withCull(false).withoutBlend().withDepthWrite(true).build());
	private static final Map<Identifier, RenderType> TYPES = new HashMap<>();
	private CosmeticMaterials() {}
	public static void init() {}
	public static RenderType flat(Identifier texture) {
		return TYPES.computeIfAbsent(texture, id -> RenderType.create("nos_cosmetic_cutout",
			RenderSetup.builder(FLAT).withTexture("Sampler0", id).bufferSize(65536).createRenderSetup()));
	}
}
