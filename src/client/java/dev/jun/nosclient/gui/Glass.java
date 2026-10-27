package dev.jun.nosclient.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.jun.nosclient.theme.Theme;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

@Environment(EnvType.CLIENT)
public final class Glass {
    private static final RenderPipeline FILL = pipeline("glass_fill", null);
    private static final RenderPipeline LINE = pipeline("glass_line", "OUTLINE");
    private static final RenderPipeline SHADOW = pipeline("glass_shadow", "SHADOW");
    private static final RenderPipeline SHEEN = pipeline("glass_sheen", "SHEEN");
    /** Khớp hằng SPREAD trong rounded.fsh. */
    private static final int SPREAD = 10;

    private Glass() {
    }

    private static RenderPipeline pipeline(String name, String define) {
        Builder builder = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("nosclient", "pipeline/" + name))
            .withVertexShader(Identifier.fromNamespaceAndPath("nosclient", "core/rounded"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("nosclient", "core/rounded"))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, Mode.QUADS);
        if (define != null) {
            builder.withShaderDefine(define);
        }

        return RenderPipelines.register(builder.build());
    }

    public static void init() {
    }

    private static void draw(GuiGraphics g, RenderPipeline pipeline, int x, int y, int w, int h, int color) {
        color = UiOpacity.tint(color);
        if (w > 0 && h > 0 && color >>> 24 != 0) {
            g.guiRenderState
                .submitGuiElement(
                    new BlitRenderState(
                        pipeline,
                        TextureSetup.noTexture(),
                        new Matrix3x2f(g.pose()),
                        x,
                        y,
                        x + w,
                        y + h,
                        -w / 2.0F,
                        w / 2.0F,
                        -h / 2.0F,
                        h / 2.0F,
                        color,
                        g.scissorStack.peek()
                    )
                );
        }
    }

    public static void fill(GuiGraphics g, int x, int y, int w, int h, int color) {
        draw(g, FILL, x, y, w, h, color);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        draw(g, LINE, x, y, w, h, color);
    }

    /** Quầng mềm loang SPREAD px quanh khối (màu tối = bóng đổ, màu accent = phát sáng). */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int color) {
        draw(g, SHADOW, x - SPREAD, y - SPREAD, w + 2 * SPREAD, h + 2 * SPREAD, color);
    }

    public static void sheen(GuiGraphics g, int x, int y, int w, int h, int color) {
        draw(g, SHEEN, x, y, w, h, color);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        int surface = Theme.surfaceTint(fill);
        float a = (surface >>> 24) / 255.0F;
        // Bóng đổ lệch xuống một chút cho panel nổi khỏi nền; ánh kính và viền sáng mép trên như tấm kính thật.
        glow(g, x, y + 2, w, h, Math.round(110 * a) << 24);
        fill(g, x, y, w, h, surface);
        sheen(g, x, y, w, h, Math.round(26 * a) << 24 | 0xFFFFFF);
        outline(g, x, y, w, h, Theme.borderTint(border));
    }
}
