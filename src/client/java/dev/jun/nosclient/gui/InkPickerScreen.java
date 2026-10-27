package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.theme.ThemePalette;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Chọn màu mực lời nhạc tự do (vuông độ đậm/sáng + dải sắc, dùng lại ColorPickerWidget của màn chủ đề). Đổi màu là
 * thấy ngay trên trời nếu đang trong thế giới; Huỷ trả màu cũ.
 */
@Environment(EnvType.CLIENT)
public final class InkPickerScreen extends Screen {
    private final Screen parent;
    private final SceneConfig c = SceneConfig.INSTANCE;
    private final int original;
    private final MenuMotion motion = new MenuMotion();
    private boolean saved;
    private int left;
    private int top;
    private int w;
    private int h;

    public InkPickerScreen(Screen parent) {
        super(Component.translatable("nosclient.opt.lyrics.ink"));
        this.parent = parent;
        this.original = this.c.lyricsInk;
    }

    @Override
    protected void init() {
        this.motion.enter();
        int square = Math.max(48, Math.min(120, this.height - 110));
        this.w = square + 20 + 28 + 150;
        this.h = square + 64;
        this.left = (this.width - this.w) / 2;
        this.top = (this.height - this.h) / 2;
        this.addRenderableWidget(new ColorPickerWidget(this.left + 14, this.top + 30, square, this.c.lyricsInk, rgb -> this.c.lyricsInk = rgb & 0xFFFFFF));
        this.addRenderableWidget(new GlassButton(this.left + this.w - 84, this.top + this.h - 27, 72, 19, Component.translatable("gui.done"), () -> {
            this.saved = true;
            this.c.save();
            this.close();
        }));
        this.addRenderableWidget(
            new GlassButton(this.left + this.w - 162, this.top + this.h - 27, 72, 19, Component.translatable("gui.cancel"), this::onClose)
        );
    }

    @Override
    public void removed() {
        if (!this.saved) {
            this.c.lyricsInk = this.original;
        }
    }

    @Override
    public void tick() {
        this.motion.tick();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        return this.motion.closing() || super.mouseClicked(event, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.motion.closing() || super.mouseDragged(event, dx, dy);
    }

    @Override
    public void onClose() {
        if (!this.motion.closing()) {
            this.c.lyricsInk = this.original;
            this.close();
        }
    }

    private void close() {
        this.motion.exit(() -> this.minecraft.setScreen(this.parent));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (this.minecraft.level == null) {
            this.renderPanorama(g, pt);
        }

        Glass.panel(g, this.left, this.top, this.w, this.h, -267117022, 1593835519);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        UiOpacity.value = this.motion.opacity();

        try {
            super.render(g, mx, my, pt);
            Ui.text(g, Ui.ui(this.title), this.left + 14, this.top + 12, Ui.TEXT);
            int px = this.left + this.w - 150;
            int rgb = 0xFF000000 | this.c.lyricsInk;
            // Xem trước: mẫu màu + chữ in hoa đậm cùng màu, cạnh mã hex.
            Glass.glow(g, px, this.top + 32, 136, 40, Ui.alpha(rgb, 0.35F));
            Glass.fill(g, px, this.top + 32, 136, 40, rgb);
            Component sample = Ui.bold(Component.literal("LYRICS"));
            int ink = ThemePalette.luminance(rgb) > 0.45 ? 0xFF14061A : -1;
            Ui.text(g, sample, px + (136 - Ui.width(sample)) / 2, this.top + 48, ink);
            Ui.text(g, Ui.small(Component.literal(ThemePalette.hex(this.c.lyricsInk))), px, this.top + 80, Ui.MUTED);
            Ui.text(g, Ui.small(Component.translatable("nosclient.lyrics.ink.hint")), px, this.top + 94, Ui.FAINT);
        } finally {
            UiOpacity.value = 1.0F;
        }
    }
}
