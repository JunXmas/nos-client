package dev.jun.nosclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.jun.nosclient.typography.FontSampling;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(FontTexture.class)
public abstract class FontTextureMixin extends AbstractTexture {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void nosclient$smoothWorldFont(Supplier<String> name, GlyphRenderTypes types, boolean colored, CallbackInfo ci) {
        String identifier = name == null ? null : name.get();
        if (identifier != null && identifier.startsWith("nosclient:")) {
            FontSampling.register(this.texture);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }
}
