package dev.jun.nosclient.mixin;

import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome.Precipitation;
import net.minecraft.world.level.dimension.DimensionType.Skybox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(WeatherEffectRenderer.class)
public abstract class PrecipitationMixin {
    @Inject(method = "getPrecipitationAt", at = @At("RETURN"), cancellable = true)
    private void nosclient$precipitation(Level level, BlockPos pos, CallbackInfoReturnable<Precipitation> result) {
        SceneConfig c = SceneConfig.INSTANCE;
        if (c.weather && level.isClientSide() && level.dimensionType().skybox() == Skybox.OVERWORLD) {
            result.setReturnValue(c.weatherMode == 0 ? Precipitation.NONE : (c.weatherMode == 3 ? Precipitation.SNOW : Precipitation.RAIN));
        }
    }
}
