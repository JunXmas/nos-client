package dev.jun.nosclient.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.jun.nosclient.feat.Feats;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic FPS: hạ giới hạn khung khi game ở nền hoặc bị thu nhỏ, đỡ nóng máy. */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
	@Inject(method = "onInputReceived", at = @At("HEAD"))
	private void nosclient$input(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		Feats.lastInput = System.currentTimeMillis();
	}

	@Shadow
	private int framerateLimit;

	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void nosclient$dfps(CallbackInfoReturnable<Integer> cir) {
		var mc = Minecraft.getInstance();
		int fps = cir.getReturnValue();
		// màn của Nos (màn chính có cảnh 3D, intro, cài đặt) không phải menu tĩnh: bỏ trần 60 của game, dùng đúng giới hạn người chơi đặt
		if (((FramerateLimitTracker) (Object) this).getThrottleReason() == FramerateLimitTracker.FramerateThrottleReason.OUT_OF_LEVEL_MENU && Feats.nosScreen(mc.screen))
			fps = framerateLimit;
		int cap = Feats.fpsCap(mc);
		cir.setReturnValue(cap > 0 ? Math.min(fps, cap) : fps);
	}
}
