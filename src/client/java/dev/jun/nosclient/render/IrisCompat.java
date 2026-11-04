package dev.jun.nosclient.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import dev.jun.nosclient.ClientResources;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/** API Iris tuỳ chọn, dùng chung trạng thái cho trời, lời và các lớp vẽ sau shaderpack. */
public final class IrisCompat {
    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("iris");
    private static Object api;
    private static Method inUse;
    private static boolean broken;

    private IrisCompat() {}

    public static boolean shadersOn() {
        if (!PRESENT || broken) return false;
        try {
            if (inUse == null) {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = type.getMethod("getInstance").invoke(null);
                inUse = type.getMethod("isShaderPackInUse");
            }
            return (Boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            broken = true;
            ClientResources.LOG.warn("Could not query Iris shader state", error);
            return false;
        }
    }

    /** Gán vật tự sáng cho shaderpack qua API công khai, không thêm phụ thuộc Iris khi build. */
    public static void assign(RenderPipeline pipeline, String program) {
        if (!PRESENT) return;
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> programs = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object value = programs.getField(program).get(null);
            Object instance = type.getMethod("getInstance").invoke(null);
            type.getMethod("assignPipeline", RenderPipeline.class, programs)
                    .invoke(instance, pipeline, value);
        } catch (ReflectiveOperationException | LinkageError error) {
            ClientResources.LOG.warn(
                    "Could not assign Iris program for {}", pipeline.getLocation(), error);
        }
    }
}
