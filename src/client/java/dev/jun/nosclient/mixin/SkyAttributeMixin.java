package dev.jun.nosclient.mixin;

import dev.jun.nosclient.media.AuroraSky;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.scene.SkyPalette;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(EnvironmentAttributeProbe.class)
public abstract class SkyAttributeMixin {
    @Unique
    private long nosclient$lastSky;
    @Unique
    private double nosclient$hour;

    /** Trời đêm và sương mù ngả theo màu nhịp của cực quang (đổi theo câu hát). Chỉ khi cực quang đang hiện. */
    @Unique
    private void nosclient$beat(EnvironmentAttribute<?> attribute, CallbackInfoReturnable<Object> result) {
        if ((attribute == EnvironmentAttributes.SKY_COLOR || attribute == EnvironmentAttributes.FOG_COLOR) && result.getReturnValue() instanceof Integer base) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameRenderer != null && mc.gameRenderer.getMainCamera().attributeProbe() == (Object)this) {
                float k = AuroraSky.skyTint() * (attribute == EnvironmentAttributes.FOG_COLOR ? 0.6F : 1.0F);
                if (k > 0.002F) {
                    result.setReturnValue(base & 0xFF000000 | SkyPalette.blend(base & 0xFFFFFF, AuroraSky.beatColor() & 0xFFFFFF, k));
                }
            }
        }
    }

    @Inject(method = "getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;", at = @At("RETURN"), cancellable = true)
    private void nosclient$sky(EnvironmentAttribute<?> attribute, float partialTick, CallbackInfoReturnable<Object> result) {
        SceneConfig c = SceneConfig.INSTANCE;
        this.nosclient$beat(attribute, result);
        if (!c.sky) {
            this.nosclient$lastSky = 0L;
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null
                && mc.level != null
                && mc.gameRenderer != null
                && mc.gameRenderer.getMainCamera().attributeProbe() == (Object)this
                && mc.level.dimensionType().skybox() == Skybox.OVERWORLD) {
                long now = System.nanoTime();
                if (this.nosclient$lastSky != 0L && c.animations) {
                    double difference = (c.skyHour - this.nosclient$hour + 36.0) % 24.0 - 12.0;
                    this.nosclient$hour = this.nosclient$hour + difference * (1.0 - Math.exp(-Math.min(0.05, (now - this.nosclient$lastSky) / 1.0E9) * 7.0));
                    this.nosclient$hour = (this.nosclient$hour + 24.0) % 24.0;
                } else {
                    this.nosclient$hour = c.skyHour;
                }

                this.nosclient$lastSky = now;
                if (attribute == EnvironmentAttributes.SUN_ANGLE || attribute == EnvironmentAttributes.STAR_ANGLE) {
                    result.setReturnValue(SkyPalette.angle(this.nosclient$hour));
                } else if (attribute == EnvironmentAttributes.MOON_ANGLE) {
                    result.setReturnValue(SkyPalette.angle(this.nosclient$hour));
                } else if (attribute == EnvironmentAttributes.STAR_BRIGHTNESS) {
                    result.setReturnValue((1.0F - SkyPalette.daylight(this.nosclient$hour)) * (float)c.skyStars);
                } else if (attribute == EnvironmentAttributes.SKY_COLOR) {
                    result.setReturnValue(SkyPalette.color(this.nosclient$hour));
                } else if (attribute == EnvironmentAttributes.FOG_COLOR) {
                    result.setReturnValue(SkyPalette.blend(SkyPalette.color(this.nosclient$hour), 9608877, 0.15F));
                } else if (attribute == EnvironmentAttributes.CLOUD_COLOR && result.getReturnValue() instanceof Integer color) {
                    result.setReturnValue(color & 0xFF000000 | SkyPalette.blend(2963276, 15134447, SkyPalette.daylight(this.nosclient$hour)));
                } else if (attribute == EnvironmentAttributes.SUNRISE_SUNSET_COLOR) {
                    result.setReturnValue(0);
                }
            }
        }
    }
}
