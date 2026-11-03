package dev.jun.nosclient.render;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class IrisCompat {
    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("iris");
    private static Object api;
    private static Method inUse;
    private static boolean broken;
    private static long nextCheck;
    private static boolean cached;

    private IrisCompat() {}

    public static boolean shadersOn() {
        if (PRESENT && !broken) {
            long now = System.currentTimeMillis();
            if (now < nextCheck) {
                return cached;
            }

            nextCheck = now + 1000L;

            try {
                if (inUse == null) {
                    Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = c.getMethod("getInstance").invoke(null);
                    inUse = c.getMethod("isShaderPackInUse");
                }

                cached = (Boolean) inUse.invoke(api);
            } catch (Throwable t) {
                broken = true;
                cached = false;
            }

            return cached;
        } else {
            return false;
        }
    }
}
