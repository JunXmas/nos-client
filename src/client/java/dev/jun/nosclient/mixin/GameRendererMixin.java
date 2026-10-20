package dev.jun.nosclient.mixin;

import dev.jun.nosclient.feat.Feats;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: chia FOV cuối cùng, làm mượt về mức đích mỗi khung. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	private static long nosclient$last;

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void nosclient$zoom(Camera cam, float pt, boolean world, CallbackInfoReturnable<Float> cir) {
		long now = System.nanoTime();
		double dt = nosclient$last == 0 ? 0 : Math.min(.05, (now - nosclient$last) / 1e9);
		nosclient$last = now;
		Feats.zoomNow += (Feats.zoomTarget - Feats.zoomNow) * (1 - Math.exp(-dt * 14));
		float fov = cir.getReturnValue();
		if (Math.abs(Feats.zoomNow - 1) >= 1e-3) fov = (float) (fov / Feats.zoomNow);
		if (world) Feats.lastFov = fov; // để HUD chiếu waypoint đúng góc nhìn
		if (fov != cir.getReturnValue()) cir.setReturnValue(fov);
	}
}
