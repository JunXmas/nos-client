package dev.jun.nosclient.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.jun.nosclient.typography.FontSampling;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GlyphRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(GlyphRenderState.class)
public abstract class GlyphRenderStateMixin {
    @Shadow
    @Final
    private TextRenderable renderable;

    @Inject(method = "textureSetup", at = @At("RETURN"), cancellable = true)
    private void nosclient$linear(CallbackInfoReturnable<TextureSetup> cir) {
        GpuTextureView view = this.renderable.textureView();
        if (view != null && FontSampling.smooth(view.texture())) {
            cir.setReturnValue(TextureSetup.singleTextureWithLightmap(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
        }
    }
}
