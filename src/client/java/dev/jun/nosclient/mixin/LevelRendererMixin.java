package dev.jun.nosclient.mixin;

import dev.jun.nosclient.feat.DynLight;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Dynamic light: nâng mức sáng khối trong ô sáng gói (packed light) mà renderer khối và nước dùng. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "getLightColor(Lnet/minecraft/client/renderer/LevelRenderer$BrightnessGetter;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
		at = @At("RETURN"), cancellable = true)
	private static void nosclient$dynlight(LevelRenderer.BrightnessGetter getter, BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (DynLight.level == 0) return;
		int packed = cir.getReturnValue(), block = LightTexture.block(packed), boosted = DynLight.boost(pos, block);
		if (boosted != block) cir.setReturnValue(LightTexture.pack(boosted, LightTexture.sky(packed)));
	}
}
