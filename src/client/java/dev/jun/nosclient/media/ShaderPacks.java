package dev.jun.nosclient.media;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/**
 * Iris có đang chạy shaderpack không. Chữ khối lấy mẫu khung hình trước khi Iris dựng xong cảnh (Iris vẽ qua bộ
 * đệm riêng rồi ghép sau), nên khi có shaderpack lòng chữ sẽ đọc sai — lúc đó lời nhạc tự dùng kiểu Thanh mảnh.
 * Gọi API công khai của Iris qua reflection: build không phụ thuộc Iris, không có Iris thì luôn false.
 */
final class ShaderPacks {
    private static Method inUse;
    private static Object api;
    private static boolean resolved;

    private ShaderPacks() {
    }

    /**
     * Báo Iris vẽ các pipeline của mod bằng chương trình nào. Không báo thì Iris rơi về chương trình mặc định (log
     * "Missing program … in override list") và chữ/cực quang bị tô tối + sương theo cảnh đến mức chìm hẳn.
     * BEACON_BEAM = vật tự phát sáng, không nhận ánh sáng cảnh — đúng bản chất chữ và cực quang trên trời.
     */
    static void assign(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, String program) {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return;
        }

        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<?> programs = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object value = programs.getField(program).get(null);
            Object instance = type.getMethod("getInstance").invoke(null);
            type.getMethod("assignPipeline", com.mojang.blaze3d.pipeline.RenderPipeline.class, programs).invoke(instance, pipeline, value);
        } catch (ReflectiveOperationException | LinkageError error) {
            LoggerFactory.getLogger("nosclient").warn("Không gán được {} cho Iris; với shaderpack có thể vẽ sai", pipeline.getLocation(), error);
        }
    }

    static boolean active() {
        if (!resolved) {
            resolved = true;
            if (FabricLoader.getInstance().isModLoaded("iris")) {
                try {
                    Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = type.getMethod("getInstance").invoke(null);
                    inUse = type.getMethod("isShaderPackInUse");
                } catch (ReflectiveOperationException | LinkageError error) {
                    LoggerFactory.getLogger("nosclient").warn("Không hỏi được Iris có bật shaderpack không; lời nhạc giữ kiểu đã chọn", error);
                }
            }
        }

        if (inUse == null) {
            return false;
        }

        try {
            return (Boolean)inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException error) {
            inUse = null;
            return false;
        }
    }
}
