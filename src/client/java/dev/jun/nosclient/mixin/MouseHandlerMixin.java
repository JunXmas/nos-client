package dev.jun.nosclient.mixin;

import dev.jun.nosclient.hud.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Đếm cú bấm cho CPS ngay từ callback chuột của GLFW, chỉ khi đang chơi (không mở màn nào). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void nosclient$cps(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action != 1 || Minecraft.getInstance().screen != null) return; // 1 = GLFW_PRESS
		long now = System.nanoTime();
		if (info.button() == 0) Hud.INSTANCE.left.click(now);
		else if (info.button() == 1) Hud.INSTANCE.right.click(now);
	}
}
