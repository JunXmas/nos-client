package dev.jun.nosclient.typography;

import com.mojang.blaze3d.textures.GpuTexture;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class FontSampling {
    private static final Set<GpuTexture> SMOOTH = Collections.newSetFromMap(new WeakHashMap<>());

    private FontSampling() {
    }

    public static void register(GpuTexture atlas) {
        SMOOTH.add(atlas);
    }

    public static boolean smooth(GpuTexture atlas) {
        return SMOOTH.contains(atlas);
    }
}
