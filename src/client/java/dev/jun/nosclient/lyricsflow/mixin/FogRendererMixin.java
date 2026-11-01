package dev.jun.nosclient.lyricsflow.mixin;

import dev.jun.nosclient.lyricsflow.app.sky.WorldSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
   @Inject(method = "computeFogColor", at = @At("RETURN"), cancellable = true)
   private void lyricsflow$skyFog(Camera camera, float pt, ClientLevel level, int renderDistance, float darken, CallbackInfoReturnable<Vector4f> cir) {
      if (camera.getFluidInCamera() == FogType.NONE) {
         Vector4f v = cir.getReturnValue();
         if (v != null && WorldSky.tintFog(v)) {
            cir.setReturnValue(v);
         }
      }
   }
}
