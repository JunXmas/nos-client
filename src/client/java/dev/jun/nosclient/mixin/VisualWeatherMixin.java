package dev.jun.nosclient.mixin;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Level.class)
public abstract class VisualWeatherMixin {
    @Unique
    private long nosclient$lastRain;
    @Unique
    private long nosclient$lastThunder;
    @Unique
    private float nosclient$rainShown;
    @Unique
    private float nosclient$thunderShown;

    private boolean nosclient$enabled() {
        Level level = (Level)(Object)this;
        return level.isClientSide() && SceneConfig.INSTANCE.weather && level.dimensionType().skybox() == Skybox.OVERWORLD;
    }

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void nosclient$rain(float tick, CallbackInfoReturnable<Float> result) {
        if (!this.nosclient$enabled()) {
            this.nosclient$lastRain = 0L;
        } else {
            long now = System.nanoTime();
            float target = SceneConfig.INSTANCE.weatherMode == 0 ? 0.0F : 1.0F;
            if (this.nosclient$lastRain == 0L) {
                this.nosclient$rainShown = (Float)result.getReturnValue();
            }

            this.nosclient$rainShown = SceneConfig.INSTANCE.animations
                ? Ui.approach(this.nosclient$rainShown, target, Math.min(0.1F, (float)(now - this.nosclient$lastRain) / 1.0E9F), 4.0F)
                : target;
            this.nosclient$lastRain = now;
            result.setReturnValue(this.nosclient$rainShown);
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void nosclient$thunder(float tick, CallbackInfoReturnable<Float> result) {
        if (!this.nosclient$enabled()) {
            this.nosclient$lastThunder = 0L;
        } else {
            long now = System.nanoTime();
            float target = SceneConfig.INSTANCE.weatherMode == 2 ? 1.0F : 0.0F;
            if (this.nosclient$lastThunder == 0L) {
                this.nosclient$thunderShown = (Float)result.getReturnValue();
            }

            this.nosclient$thunderShown = SceneConfig.INSTANCE.animations
                ? Ui.approach(this.nosclient$thunderShown, target, Math.min(0.1F, (float)(now - this.nosclient$lastThunder) / 1.0E9F), 4.0F)
                : target;
            this.nosclient$lastThunder = now;
            result.setReturnValue(this.nosclient$thunderShown);
        }
    }
}
