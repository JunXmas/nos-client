package dev.jun.nosclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.jun.nosclient.scene.SceneConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gamma/full bright: thay giá trị độ sáng ngay chỗ bảng sáng đọc nó, KHÔNG ghi vào tuỳ chọn của game.
 * Bản cũ ghi 10.0 thẳng vào OptionInstance: thanh Độ sáng của Video Settings kẹp về ≤1 và lúc vào thế giới
 * game nạp lại tuỳ chọn → full bright mất, còn options.txt bị ghi số ngoài khoảng.
 * Lần gọi OptionInstance.get() thứ ba trong updateLightTexture là gamma (1: hideLightningFlash, 2: darknessEffectScale).
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	@WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 2))
	private Object nosclient$gamma(OptionInstance<?> option, Operation<Object> original) {
		double g = SceneConfig.INSTANCE.gamma;
		return g >= 0 ? (Object) g : original.call(option);
	}
}
