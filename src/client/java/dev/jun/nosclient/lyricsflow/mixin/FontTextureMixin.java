package dev.jun.nosclient.lyricsflow.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.function.Supplier;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FontTexture.class)
public abstract class FontTextureMixin extends AbstractTexture {
   @Inject(method = "<init>", at = @At("TAIL"))
   private void lyricsflow$smooth(Supplier<String> name, GlyphRenderTypes types, boolean colored, CallbackInfo ci) {
      try {
         String n = name.get();
         if (n != null && n.startsWith("lyricsflow:") && !n.contains("badge")) {
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);

            try (NativeImage blank = new NativeImage(colored ? Format.RGBA : Format.LUMINANCE, 256, 256, true)) {
               RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.getTexture(), blank, 0, 0, 0, 0, 256, 256, 0, 0);
            }
         }
      } catch (Throwable var11) {
      }
   }
}
