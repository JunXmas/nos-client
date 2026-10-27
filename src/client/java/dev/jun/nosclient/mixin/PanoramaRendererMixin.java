package dev.jun.nosclient.mixin;

import dev.jun.nosclient.gui.AuroraBackground;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.scene.SceneRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PanoramaRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(PanoramaRenderer.class)
public abstract class PanoramaRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void nosclient$lakeScene(GuiGraphics g, int w, int h, boolean spin, CallbackInfo ci) {
        if (SceneConfig.INSTANCE.menuAurora) {
            AuroraBackground.render(g, w, h);
            ci.cancel();
        } else if (SceneRenderer.INSTANCE.render()) {
            ci.cancel();
        }
    }
}
