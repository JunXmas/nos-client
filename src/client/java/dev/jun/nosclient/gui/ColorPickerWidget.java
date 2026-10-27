package dev.jun.nosclient.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.theme.HsvColor;
import dev.jun.nosclient.theme.ThemePalette;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class ColorPickerWidget extends AbstractWidget {
    private static final Identifier SV = Identifier.fromNamespaceAndPath("nosclient", "ui/theme_sv");
    private static final Identifier HUE = Identifier.fromNamespaceAndPath("nosclient", "ui/theme_hue");
    private static DynamicTexture square;
    private static int uploadedHue = -1;
    private final int size;
    private final IntConsumer changed;
    private HsvColor color;
    private int dragging;

    public ColorPickerWidget(int x, int y, int size, int rgb, IntConsumer changed) {
        super(x, y, size + 20, size, Component.translatable("nosclient.theme.picker"));
        this.size = size;
        this.changed = changed;
        this.color = HsvColor.fromRgb(rgb);
    }

    public void setRgb(int rgb) {
        this.color = HsvColor.fromRgb(rgb);
    }

    private void prepare() {
        if (square == null) {
            square = new DynamicTexture(() -> "Nos colour square", 64, 64, false) {
                {
                    this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                }
            };
            Minecraft.getInstance().getTextureManager().register(SV, square);
            var strip = new DynamicTexture(() -> "Nos hue strip", 1, 128, false) {
                {
                    this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                }
            };

            for (int y = 0; y < 128; y++) {
                strip.getPixels().setPixel(0, y, ThemePalette.opaque(new HsvColor(y / 127.0 * 359.9, 1.0, 1.0).rgb()));
            }

            strip.upload();
            Minecraft.getInstance().getTextureManager().register(HUE, strip);
        }

        int hue = (int)Math.round(this.color.hue());
        if (hue != uploadedHue) {
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    square.getPixels().setPixel(x, y, ThemePalette.opaque(new HsvColor(hue, x / 63.0, 1.0 - y / 63.0).rgb()));
                }
            }

            square.upload();
            uploadedHue = hue;
        }
    }

    private void texture(GuiGraphics g, Identifier id, int x, int y, int w, int h, int sourceW, int sourceH) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale((float)w / sourceW, (float)h / sourceH);
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0.0F, 0.0F, sourceW, sourceH, sourceW, sourceH, UiOpacity.tint(-1));
        g.pose().popMatrix();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        this.prepare();
        int x = this.getX();
        int y = this.getY();
        this.texture(g, SV, x, y, this.size, this.size, 64, 64);
        this.texture(g, HUE, x + this.size + 8, y, 10, this.size, 1, 128);
        Ui.frame(g, x - 1, y - 1, x + this.size + 1, y + this.size + 1, Ui.alpha(Ui.TEXT, 0.25F));
        int cx = x + (int)Math.round(this.color.saturation() * (this.size - 1));
        int cy = y + (int)Math.round((1.0 - this.color.value()) * (this.size - 1));
        Glass.outline(g, cx - 3, cy - 3, 7, 7, -15723240);
        Glass.outline(g, cx - 2, cy - 2, 5, 5, -1);
        int hy = y + (int)Math.round(this.color.hue() / 360.0 * (this.size - 1));
        g.fill(x + this.size + 6, hy - 1, x + this.size + 20, hy + 2, -15656928);
        g.fill(x + this.size + 7, hy, x + this.size + 19, hy + 1, -1);
        if (this.isFocused()) {
            Glass.outline(g, x - 3, y - 3, this.width + 4, this.height + 6, Ui.alpha(Ui.ACCENT, 0.65F));
        }
    }

    private void pick(double mx, double my) {
        if (this.dragging == 1) {
            this.color = new HsvColor(this.color.hue(), (mx - this.getX()) / (this.size - 1), 1.0 - (my - this.getY()) / (this.size - 1));
        } else if (this.dragging == 2) {
            this.color = new HsvColor(Math.max(0.0, Math.min(359.9, (my - this.getY()) / (this.size - 1) * 359.9)), this.color.saturation(), this.color.value());
        }

        this.changed.accept(this.color.rgb());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        if (event.button() == 0 && this.isMouseOver(event.x(), event.y())) {
            this.dragging = event.x() < this.getX() + this.size ? 1 : 2;
            this.pick(event.x(), event.y());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging == 0) {
            return false;
        }

        this.pick(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean was = this.dragging != 0;
        this.dragging = 0;
        return was;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!this.isFocused()) {
            return false;
        }

        double step = (event.modifiers() & 1) != 0 ? 0.1 : 0.01;

        HsvColor next = switch (event.key()) {
            case 262 -> new HsvColor(this.color.hue() + step * 100.0, this.color.saturation(), this.color.value());
            case 263 -> new HsvColor(this.color.hue() - step * 100.0, this.color.saturation(), this.color.value());
            case 264 -> new HsvColor(this.color.hue(), this.color.saturation(), this.color.value() - step);
            case 265 -> new HsvColor(this.color.hue(), this.color.saturation(), this.color.value() + step);
            default -> null;
        };
        if (next == null) {
            return false;
        }

        this.color = next;
        this.changed.accept(this.color.rgb());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
        output.add(NarratedElementType.HINT, Component.literal(ThemePalette.hex(this.color.rgb())));
    }
}
