package dev.jun.nosclient.lyricsflow.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.jun.nosclient.lyricsflow.render.HotbarStyle;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
public abstract class GuiHotbarMixin {
   @WrapOperation(
      method = "renderItemHotbar",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/Identifier;IIII)V")
   )
   private void lyricsflow$hotbar(GuiGraphics g, RenderPipeline pipeline, Identifier id, int x, int y, int w, int h, Operation<Void> original) {
      if (!HotbarStyle.draw(g, id, x, y, w, h)) {
         original.call(g, pipeline, id, x, y, w, h);
      }
   }
}
