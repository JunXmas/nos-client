package dev.jun.nosclient.mixin;

import dev.jun.nosclient.cosmetic.effects.DeathFx;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class DeathHideMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void nosclient$hideForDeathFx(
            Entity entity,
            Frustum frustum,
            double x,
            double y,
            double z,
            CallbackInfoReturnable<Boolean> cir) {
        if (DeathFx.hides(entity)) {
            cir.setReturnValue(false);
        }
    }
}
