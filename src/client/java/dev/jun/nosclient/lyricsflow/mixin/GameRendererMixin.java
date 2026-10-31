package dev.jun.nosclient.lyricsflow.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import dev.jun.nosclient.lyricsflow.render.Chroma;
import dev.jun.nosclient.lyricsflow.render.Proj;
import dev.jun.nosclient.lyricsflow.render.ScreenshotHelper;
import dev.jun.nosclient.lyricsflow.util.Anim;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
   @Shadow
   @Final
   private CrossFrameResourcePool resourcePool;

   @Inject(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/LevelRenderer;renderLevel(Lnet/minecraft/GraphicsResourceAllocator;Lnet/minecraft/DeltaTracker;ZLnet/minecraft/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
         shift = Shift.AFTER
      ),
      require = 0
   )
   private void lyricsflow$chroma(DeltaTracker deltaTracker, CallbackInfo ci) {
      Chroma.apply(this.resourcePool);
      if (Chroma.deferred) {
         Chroma.drawLate();
      }

      Chroma.drawGhost();
   }

   @Inject(method = "getFov", at = @At("RETURN"), require = 0)
   private void lyricsflow$captureFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
      if (useFovSetting) {
         Proj.fovFromRenderer = cir.getReturnValueF();
      }
   }

   @Inject(method = "render", at = @At("HEAD"), require = 0)
   private void lyricsflow$frameStart(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
      Anim.beginFrame();
   }

   @Inject(method = "render", at = @At("TAIL"), require = 0)
   private void lyricsflow$afterRender(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
      ScreenshotHelper.afterFrame();
   }
}
