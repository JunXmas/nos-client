package dev.jun.nosclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GlyphRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chữ TTF của Nos client lấy mẫu tuyến tính thay vì NEAREST: game vẽ glyph đúng 1:1 chỉ khi tỉ lệ vẽ khớp oversample;
 * HUD co nhỏ / intro phóng to lệch tỉ lệ thì NEAREST bỏ sót hoặc nhân đôi cột pixel → chữ răng cưa, vỡ nét.
 * Chỉ đụng atlas font của mod (nhãn "nosclient:"), font pixel của game giữ nguyên nét vuông.
 */
@Mixin(GlyphRenderState.class)
public abstract class GlyphRenderStateMixin {
	@Shadow @Final private TextRenderable renderable;

	@Inject(method = "textureSetup", at = @At("RETURN"), cancellable = true)
	private void nosclient$linear(CallbackInfoReturnable<TextureSetup> cir) {
		var view = renderable.textureView();
		if (view == null || !view.texture().getLabel().contains("nosclient:")) return;
		cir.setReturnValue(TextureSetup.singleTextureWithLightmap(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
	}
}
