package dev.jun.nosclient.lyricsflow.mixin;

import dev.jun.nosclient.lyricsflow.app.cosmetic.PhoneMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com/wjbaker/ccm/crosshair/rendering/CrosshairRenderManager", remap = false)
public abstract class CustomCrosshairCompatMixin {
   @Inject(method = "draw", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
   private void lyricsflow$hideForWorldMenu(CallbackInfo ci) {
      if (PhoneMenu.pointerShown()) {
         ci.cancel();
      }
   }
}
