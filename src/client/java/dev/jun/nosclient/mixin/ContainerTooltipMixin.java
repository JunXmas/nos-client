package dev.jun.nosclient.mixin;

import dev.jun.nosclient.feat.Peek;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerTooltipMixin {
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void nosclient$shulker(GuiGraphics graphics, int x, int y, CallbackInfo info) {
        if (Peek.render((AbstractContainerScreen<?>)(Object)this, graphics, x, y)) {
            info.cancel();
        }
    }
}
