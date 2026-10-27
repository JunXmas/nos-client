package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

@Environment(EnvType.CLIENT)
public final class GlassConfirmScreen extends Screen {
    private final Consumer<Boolean> reply;
    private final Component message;
    private int x;
    private int y;
    private int w;
    private int h;
    private List<FormattedCharSequence> lines = List.of();
    private int scroll;
    private int maxScroll;
    private boolean resolved;
    private final MenuMotion motion = new MenuMotion();

    public GlassConfirmScreen(Consumer<Boolean> reply, Component title, Component message) {
        super(title);
        this.reply = reply;
        this.message = message;
    }

    @Override
    protected void init() {
        this.motion.enter();
        this.w = Math.min(300, this.width - 32);
        this.lines = this.font.split(Ui.ui(this.message), this.w - 28);
        this.h = Math.min(this.height - 24, Math.max(110, 76 + this.lines.size() * 11));
        this.x = (this.width - this.w) / 2;
        this.y = (this.height - this.h) / 2;
        this.maxScroll = Math.max(0, this.lines.size() * 11 - (this.h - 80));
        this.scroll = Math.min(this.scroll, this.maxScroll);
        int bw = (this.w - 34) / 2;
        this.addRenderableWidget(new GlassButton(this.x + 12, this.y + this.h - 32, bw, 20, Component.translatable("gui.cancel"), () -> this.answer(false)));
        this.addRenderableWidget(new GlassButton(this.x + 22 + bw, this.y + this.h - 32, bw, 20, Component.translatable("gui.yes"), () -> this.answer(true)));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (Minecraft.getInstance().level == null) {
            if (SceneConfig.INSTANCE.menuAurora) {
                AuroraBackground.render(g, this.width, this.height);
            } else {
                this.renderPanorama(g, pt);
            }
        } else {
            super.renderBackground(g, mx, my, pt);
        }

        Glass.panel(g, this.x, this.y, this.w, this.h, -183233250, -2130706433);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        UiOpacity.value = this.motion.opacity();

        try {
            this.draw(g, mx, my, pt);
        } finally {
            UiOpacity.value = 1.0F;
        }
    }

    private void draw(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Component heading = Ui.fit(Ui.big(this.title), this.w - 24);
        Ui.text(g, heading, this.x + (this.w - Ui.width(heading)) / 2, this.y + 16, Ui.TEXT);
        g.enableScissor(this.x + 12, this.y + 39, this.x + this.w - 12, this.y + this.h - 40);
        int first = this.scroll / 11;
        int end = Math.min(this.lines.size(), first + Math.max(1, (this.h - 80) / 11) + 2);

        for (int i = first; i < end && UiOpacity.value > 0.02F; i++) {
            g.drawString(this.font, this.lines.get(i), this.x + 14, this.y + 41 + i * 11 - this.scroll, UiOpacity.tint(Ui.MUTED), false);
        }

        g.disableScissor();
        if (this.maxScroll > 0) {
            int track = this.h - 80;
            int thumb = Math.max(10, track * track / (track + this.maxScroll));
            int top = this.y + 39 + (track - thumb) * this.scroll / this.maxScroll;
            Glass.fill(g, this.x + this.w - 9, top, 2, thumb, -1962934273);
        }
    }

    private void answer(boolean value) {
        if (!this.resolved) {
            this.resolved = true;
            this.motion.exit(() -> this.reply.accept(value));
        }
    }

    @Override
    public void onClose() {
        this.answer(false);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= this.x && mx < this.x + this.w && my >= this.y + 39 && my < this.y + this.h - 40) {
            this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll - (int)(sy * 22.0)));
            return true;
        } else {
            return super.mouseScrolled(mx, my, sx, sy);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() != 266 && event.key() != 267) {
            return super.keyPressed(event);
        }

        this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll + (event.key() == 266 ? -1 : 1) * Math.max(22, this.h - 80)));
        return true;
    }

    @Override
    public void tick() {
        this.motion.tick();
    }
}
