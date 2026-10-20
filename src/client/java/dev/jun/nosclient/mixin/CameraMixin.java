package dev.jun.nosclient.mixin;

import dev.jun.nosclient.feat.Feats;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Freelook: camera dùng góc riêng thay góc của người chơi. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
	private void nosclient$freelook(Args a) {
		if (!Feats.looking) return;
		a.set(0, Feats.lookYaw);
		a.set(1, Feats.lookPitch);
	}
}
