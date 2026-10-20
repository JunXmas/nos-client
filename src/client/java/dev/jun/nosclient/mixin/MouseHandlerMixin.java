package dev.jun.nosclient.mixin;

import dev.jun.nosclient.feat.Feats;
import dev.jun.nosclient.hud.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	/** CPS: đếm cú bấm ngay từ callback chuột của GLFW, chỉ khi đang chơi (không mở màn nào). */
	@Inject(method = "onButton", at = @At("HEAD"))
	private void nosclient$cps(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action != 1 || Minecraft.getInstance().screen != null) return; // 1 = GLFW_PRESS
		long now = System.nanoTime();
		if (info.button() == 0) Hud.INSTANCE.left.click(now);
		else if (info.button() == 1) Hud.INSTANCE.right.click(now);
	}

	/** Zoom: đang zoom thì lăn chuột đổi mức zoom thay vì đổi ô hotbar. */
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void nosclient$zoomScroll(long window, double dx, double dy, CallbackInfo ci) {
		if (Minecraft.getInstance().screen == null && Feats.scroll(dy)) ci.cancel();
	}

	/** Freelook: chuột xoay camera riêng, người chơi giữ nguyên hướng chạy. */
	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void nosclient$freelook(net.minecraft.client.player.LocalPlayer p, double yaw, double pitch) {
		if (!Feats.looking) {
			p.turn(yaw, pitch);
			return;
		}
		Feats.lookYaw += (float) yaw * .15f;
		Feats.lookPitch = Math.max(-90, Math.min(90, Feats.lookPitch + (float) pitch * .15f));
	}
}
