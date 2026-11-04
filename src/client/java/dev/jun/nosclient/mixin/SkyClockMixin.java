package dev.jun.nosclient.mixin;

import dev.jun.nosclient.ClientEffects;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.scene.SkyPalette;

import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chỉ đổi đồng hồ hiển thị; màu trời và sương thuộc hệ preset duy nhất. */
@Mixin(EnvironmentAttributeProbe.class)
public abstract class SkyClockMixin {
    @Inject(
            method =
                    "getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;",
            at = @At("RETURN"),
            cancellable = true)
    private void nosclient$clock(
            EnvironmentAttribute<?> attribute,
            float partialTick,
            CallbackInfoReturnable<Object> result) {
        Minecraft mc = Minecraft.getInstance();
        float hour = ClientEffects.cfg().visualHour;
        if (hour < 0
                || !SceneConfig.INSTANCE.sky
                || mc == null
                || mc.level == null
                || mc.gameRenderer == null
                || mc.gameRenderer.getMainCamera().attributeProbe() != (Object) this
                || !ClientEffects.skyWorld(mc.level)) return;
        if (attribute == EnvironmentAttributes.SUN_ANGLE
                || attribute == EnvironmentAttributes.STAR_ANGLE
                || attribute == EnvironmentAttributes.MOON_ANGLE) {
            result.setReturnValue(SkyPalette.angle(hour));
        }
    }
}
