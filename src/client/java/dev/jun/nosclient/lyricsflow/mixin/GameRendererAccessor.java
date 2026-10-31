package dev.jun.nosclient.lyricsflow.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
   @Accessor("fogRenderer")
   FogRenderer lyricsflow$fogRenderer();
}
