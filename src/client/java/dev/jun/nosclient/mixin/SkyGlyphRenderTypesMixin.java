package dev.jun.nosclient.mixin;

import dev.jun.nosclient.media.SkyTextMaterial;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(GlyphRenderTypes.class)
public abstract class SkyGlyphRenderTypesMixin {
    @Inject(method = "createForIntensityTexture", at = @At("RETURN"), cancellable = true)
    private static void nosclient$skyMaterial(Identifier atlas, CallbackInfoReturnable<GlyphRenderTypes> result) {
        if (atlas.toString().startsWith("nosclient:sky_")) {
            GlyphRenderTypes previous = (GlyphRenderTypes)result.getReturnValue();
            result.setReturnValue(
                new GlyphRenderTypes(SkyTextMaterial.type(atlas, false), previous.seeThrough(), SkyTextMaterial.type(atlas, true), previous.guiPipeline())
            );
        }
    }
}
