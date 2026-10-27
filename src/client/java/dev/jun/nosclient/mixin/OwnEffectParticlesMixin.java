package dev.jun.nosclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hạt hiệu ứng thuốc của chính mình bay sát camera ở góc nhìn thứ nhất thành vòng xanh to, vỡ pixel che màn hình
 * (Speed vô hạn). Bỏ hạt đó khi đang nhìn ngôi thứ nhất; người khác và ngôi thứ ba vẫn thấy bình thường.
 */
@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class OwnEffectParticlesMixin {
    @WrapOperation(
        method = "tickEffects",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V")
    )
    private void nosclient$hideOwnEffectParticles(
        Level level, ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz, Operation<Void> original
    ) {
        Minecraft mc = Minecraft.getInstance();
        boolean own = (Object)this == mc.player;
        if (!(own && SceneConfig.INSTANCE.hideOwnEffectParticles && mc.options.getCameraType().isFirstPerson())) {
            original.call(level, particle, x, y, z, dx, dy, dz);
        }
    }
}
