package dev.jun.nosclient.lyricsflow.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.jun.nosclient.lyricsflow.render.ScreenshotHelper;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
   @WrapOperation(
      method = "keyPress",
      require = 0,
      at = @At(value = "INVOKE", target = "Lnet/minecraft/Screenshot;grab(Ljava/io/File;Lnet/minecraft/RenderTarget;Ljava/util/function/Consumer;)V")
   )
   private void lyricsflow$delayScreenshot(File dir, RenderTarget target, Consumer<Component> done, Operation<Void> original) {
      if (!ScreenshotHelper.defer(() -> original.call(dir, target, done))) {
         original.call(dir, target, done);
      }
   }
}
