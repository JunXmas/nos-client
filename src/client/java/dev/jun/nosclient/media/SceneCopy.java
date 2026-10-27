package dev.jun.nosclient.media;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Bản chụp khung hình đang vẽ (trước khi có chữ) để chữ poster lấy mẫu cảnh phía sau theo toạ độ màn hình. Blend
 * cố định chỉ đảo được từng kênh; có bản chụp thì shader tự đảo, chỉnh độ tương phản và ánh xạ sang tông mực —
 * chữ "trong suốt" thấy rõ vân lá, mây, núi như ảnh mẫu.
 */
final class SceneCopy extends AbstractTexture {
    static final Identifier ID = Identifier.fromNamespaceAndPath("nosclient", "lyrics_scene");
    private static SceneCopy instance;
    private int width;
    private int height;

    private SceneCopy() {
        this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    /** Chép màu khung hình chính vào texture (tạo lại khi đổi cỡ cửa sổ). Gọi ngay trước khi vẽ lòng chữ. */
    static void capture() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GpuTexture source = main.getColorTexture();
        if (source == null) {
            return;
        }

        if (instance == null) {
            instance = new SceneCopy();
            Minecraft.getInstance().getTextureManager().register(ID, instance);
        }

        instance.ensure(main.width, main.height);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, instance.texture, 0, 0, 0, 0, 0, main.width, main.height);
    }

    private void ensure(int w, int h) {
        if (this.texture == null || this.width != w || this.height != h) {
            if (this.texture != null) {
                this.textureView.close();
                this.texture.close();
            }

            this.texture = RenderSystem.getDevice().createTexture(() -> "Nos lyrics scene", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, w, h, 1, 1);
            this.textureView = RenderSystem.getDevice().createTextureView(this.texture);
            this.width = w;
            this.height = h;
        }
    }
}
