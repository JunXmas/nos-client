package dev.jun.nosclient.mixin;

import dev.jun.nosclient.scene.SceneRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Panorama của game vẽ thẳng vào khung chính trước khi giao diện (vẽ trễ) phủ lên. Ta chen vào đúng chỗ đó:
 * vẽ cảnh hồ thay cho khối lập phương panorama. Cảnh hỏng thì không huỷ — panorama gốc vẽ như thường.
 */
@Mixin(PanoramaRenderer.class)
public abstract class PanoramaRendererMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void nosclient$lakeScene(GuiGraphics g, int w, int h, boolean spin, CallbackInfo ci) {
		if (SceneRenderer.INSTANCE.render()) ci.cancel();
	}
}
