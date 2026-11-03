package dev.jun.nosclient.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;

import dev.jun.nosclient.render.Chroma;
import dev.jun.nosclient.util.Anim;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class EffectsGameRendererMixin {
    @Shadow @Final private CrossFrameResourcePool resourcePool;

    @Inject(
            method = "renderLevel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
                            shift = Shift.AFTER))
    private void nosclient$chroma(DeltaTracker deltaTracker, CallbackInfo ci) {
        Chroma.apply(this.resourcePool);
        if (Chroma.deferred) {
            Chroma.drawLate();
        }

        Chroma.drawGhost();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void nosclient$frameStart(
            DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        Anim.beginFrame();
    }
}
