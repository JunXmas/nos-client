package dev.jun.nosclient.mixin;

import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Mức sấm gốc; getThunderLevel đã nhân với mức mưa nên không dùng để khôi phục. */
@Mixin(Level.class)
public interface LevelWeatherAccessor {
    @Accessor("thunderLevel")
    float nosclient$thunderLevel();
}
