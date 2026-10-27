package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.hud.HudSurface;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.theme.Theme;
import dev.jun.nosclient.theme.ThemePalette;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class ThemeScreen extends Screen {
    private static final ThemeScreen.Preset[] PRESETS = new ThemeScreen.Preset[]{
        new ThemeScreen.Preset("forest", 10931891, 1187104, 15856621),
        new ThemeScreen.Preset("aurora", 9561052, 858925, 15464438),
        new ThemeScreen.Preset("violet", 12101119, 1578020, 16118015),
        new ThemeScreen.Preset("ocean", 9423871, 1121580, 15529471),
        new ThemeScreen.Preset("rose", 15903178, 2431010, 16773365),
        new ThemeScreen.Preset("ember", 15973510, 2432023, 16774633)
    };
    private final Screen parent;
    private final SceneConfig c = SceneConfig.INSTANCE;
    private final int originalAccent;
    private final int originalSurface;
    private final int originalText;
    private final int originalStyle;
    private final MenuMotion motion = new MenuMotion();
    private int target;
    private int left;
    private int top;
    private int w;
    private int h;
    private int contentY;
    private int sideX;
    private int sideW;
    private int squareSize;
    private boolean saved;
    private boolean updatingHex;
    private ColorPickerWidget picker;
    private EditBox hex;
    private GlassButton apply;
    private GlassButton style;

    public ThemeScreen(Screen parent) {
        super(Component.translatable("nosclient.theme.title"));
        this.parent = parent;
        this.originalAccent = this.c.themeAccent;
        this.originalSurface = this.c.themeSurface;
        this.originalText = this.c.themeText;
        this.originalStyle = this.c.hudStyle;
    }

    private Component tr(String key) {
        return Component.translatable("nosclient.theme." + key);
    }

    @Override
    protected void init() {
        this.motion.enter();
        this.w = Math.min(440, this.width - 20);
        this.h = Math.min(292, this.height - 20);
        this.left = (this.width - this.w) / 2;
        this.top = (this.height - this.h) / 2;
        this.contentY = this.top + 56;
        this.squareSize = Math.max(36, Math.min(138, Math.min(this.h - 104, (this.w - 70) / 2)));
        this.sideX = this.left + this.squareSize + 46;
        this.sideW = this.left + this.w - 12 - this.sideX;
        int bw = (this.w - 32) / 3;
        String[] labels = new String[]{"accent", "surface", "text"};

        for (int i = 0; i < 3; i++) {
            int index = i;
            this.addRenderableWidget(new GlassButton(this.left + 12 + i * (bw + 4), this.top + 28, bw, 18, this.tr(labels[i]), () -> this.select(index)));
        }

        this.picker = this.addRenderableWidget(new ColorPickerWidget(this.left + 14, this.contentY, this.squareSize, this.selected(), rgb -> {
            this.setSelected(rgb);
            this.updateHex();
        }));
        this.hex = this.addRenderableWidget(
            new StyledEditBox(this.font, this.left + 21, this.contentY + this.squareSize + 11, this.squareSize + 5, 12, this.tr("hex"))
        );
        this.hex.setBordered(false);
        this.hex.setMaxLength(7);
        this.hex.setFilter(value -> value.matches("#?[0-9a-fA-F]{0,6}"));
        this.hex.setResponder(value -> {
            if (!this.updatingHex && this.validHex()) {
                this.setSelected(ThemePalette.parseHex(value));
                this.picker.setRgb(this.selected());
            }

            if (this.apply != null) {
                this.apply.active = this.validHex();
            }
        });
        this.updateHex();
        int sw = Math.max(18, (this.sideW - 8) / 3);

        for (int i = 0; i < PRESETS.length; i++) {
            ThemeScreen.Preset preset = PRESETS[i];
            GlassButton button = new GlassButton(this.sideX + i % 3 * (sw + 4), this.contentY + i / 3 * 23, sw, 19, this.tr("preset." + preset.name), () -> {
                this.c.themeAccent = preset.accent;
                this.c.themeSurface = preset.surface;
                this.c.themeText = preset.text;
                Theme.palette();
                this.picker.setRgb(this.selected());
                this.updateHex();
            });
            button.setTooltip(Tooltip.create(this.tr("preset." + preset.name)));
            this.addRenderableWidget(button);
        }

        int styleY = this.h >= 230 ? this.top + this.h - 65 : this.contentY + 50;
        this.style = this.addRenderableWidget(new GlassButton(this.sideX, styleY, this.sideW, 18, this.styleName(), () -> {
            this.c.hudStyle = (this.c.hudStyle + 1) % 3;
            this.style.setMessage(this.styleName());
        }));
        this.addRenderableWidget(new GlassButton(this.left + this.w - 180, this.top + this.h - 27, 78, 19, Component.translatable("gui.cancel"), this::onClose));
        this.apply = this.addRenderableWidget(new GlassButton(this.left + this.w - 96, this.top + this.h - 27, 84, 19, this.tr("save"), () -> {
            if (this.validHex() && !this.motion.closing()) {
                this.saved = true;
                this.c.save();
                this.motion.exit(() -> this.minecraft.setScreen(this.parent));
            }
        }));
        this.apply.active = this.validHex();
    }

    private Component styleName() {
        return this.tr("style." + this.c.hudStyle);
    }

    private int selected() {
        return this.target == 0 ? this.c.themeAccent : (this.target == 1 ? this.c.themeSurface : this.c.themeText);
    }

    private void setSelected(int rgb) {
        if (this.target == 0) {
            this.c.themeAccent = rgb;
        } else if (this.target == 1) {
            this.c.themeSurface = rgb;
        } else {
            this.c.themeText = rgb;
        }

        Theme.palette();
    }

    private void select(int target) {
        if (!this.motion.closing()) {
            this.target = target;
            this.picker.setRgb(this.selected());
            this.updateHex();
        }
    }

    private boolean validHex() {
        return this.hex != null && this.hex.getValue().matches("#?[0-9a-fA-F]{6}");
    }

    private void updateHex() {
        if (this.hex != null) {
            this.updatingHex = true;
            this.hex.setValue(ThemePalette.hex(this.selected()));
            this.updatingHex = false;
        }
    }

    private void restore() {
        this.c.themeAccent = this.originalAccent;
        this.c.themeSurface = this.originalSurface;
        this.c.themeText = this.originalText;
        this.c.hudStyle = this.originalStyle;
        Theme.palette();
    }

    @Override
    public void removed() {
        if (!this.saved) {
            this.restore();
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
    public boolean keyPressed(KeyEvent event) {
        return this.motion.closing() || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return this.motion.closing() || super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (!this.motion.closing()) {
            this.restore();
            this.motion.exit(() -> this.minecraft.setScreen(this.parent));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (this.minecraft.level == null) {
            if (this.c.menuAurora) {
                AuroraBackground.render(g, this.width, this.height);
            } else {
                this.renderPanorama(g, pt);
            }
        } else {
            g.fill(0, 0, this.width, this.height, 1426063360);
        }

        Glass.panel(g, this.left, this.top, this.w, this.h, -267117022, 1593835519);
        Glass.panel(g, this.left + 14, this.contentY + this.squareSize + 6, this.squareSize + 20, 22, -586214117, this.validHex() ? 1442840575 : -1191864168);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Theme.palette();
        UiOpacity.value = this.motion.opacity();

        try {
            this.hex.setTextColor(Ui.TEXT);
            super.render(g, mx, my, pt);
            Ui.text(g, Ui.bold(this.title), this.left + 13, this.top + 11, Ui.TEXT);
            int bw = (this.w - 32) / 3;
            int[] colors = new int[]{this.c.themeAccent, this.c.themeSurface, this.c.themeText};

            for (int i = 0; i < 3; i++) {
                int x = this.left + 12 + i * (bw + 4);
                Glass.fill(g, x + bw - 12, this.top + 34, 6, 6, ThemePalette.opaque(colors[i]));
                if (i == this.target) {
                    Glass.fill(g, x + 8, this.top + 45, bw - 16, 1, Ui.ACCENT);
                }
            }

            int sw = Math.max(18, (this.sideW - 8) / 3);

            for (int i = 0; i < PRESETS.length; i++) {
                Glass.fill(g, this.sideX + i % 3 * (sw + 4) + 5, this.contentY + i / 3 * 23, sw - 10, 2, ThemePalette.opaque(PRESETS[i].accent));
            }

            if (this.h >= 230) {
                Ui.text(g, Ui.small(this.tr("preview")), this.sideX, this.contentY + 57, Ui.MUTED);
                this.preview(g, this.sideX, this.contentY + 72, this.sideW);
                Ui.text(g, Ui.fit(Ui.small(this.tr("contrast")), this.w - 200), this.left + 14, this.top + this.h - 19, Ui.MUTED);
            }
        } finally {
            UiOpacity.value = 1.0F;
        }
    }

    private void preview(GuiGraphics g, int x, int y, int width) {
        ThemePalette p = Theme.palette();
        HudSurface.draw(g, x, y, width, 51, (float)this.c.hudBg, 1.0F);
        Ui.text(g, Ui.bold(Component.literal("144")), x + 10, y + 8, p.text());
        Ui.text(g, Ui.small(Component.literal("FPS")), x + 37, y + 10, p.muted());
        Ui.text(g, Ui.ui(Component.literal("X 128   Y 64   Z −35")), x + 10, y + 25, p.text());
        Ui.text(g, Ui.small(Component.translatable("biome.minecraft.cherry_grove")), x + 10, y + 38, p.muted());
        Glass.fill(g, x + width - 31, y + 7, 22, 15, p.accent());
        Ui.text(g, Ui.small(Component.literal("W")), x + width - 23, y + 11, p.ink());
    }

    @Environment(EnvType.CLIENT)
    private record Preset(String name, int accent, int surface, int text) {
    }
}
