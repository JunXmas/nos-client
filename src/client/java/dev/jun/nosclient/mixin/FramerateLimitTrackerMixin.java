package dev.jun.nosclient.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.jun.nosclient.feat.Feats;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic FPS: hạ giới hạn khung khi game ở nền hoặc bị thu nhỏ, đỡ nóng máy. */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void nosclient$dfps(CallbackInfoReturnable<Integer> cir) {
		int cap = Feats.fpsCap(Minecraft.getInstance());
		if (cap > 0) cir.setReturnValue(Math.min(cir.getReturnValue(), cap));
	}
}
