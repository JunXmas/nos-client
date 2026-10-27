package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class SceneSettingsScreen extends Screen {
    private final Screen parent;
    private final MenuMotion motion = new MenuMotion();

    public SceneSettingsScreen(Screen parent) {
        super(Component.translatable("nosclient.scene.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.motion.enter();
        float scale = Math.min(1.0F, (this.height - 100) / 178.0F);
        int x = (this.width - Math.round(220.0F * scale)) / 2;
        if (!SceneConfig.INSTANCE.menuAurora) {
            this.addRenderableWidget(new ScenePanel(x, 54, scale));
        }

        this.addRenderableWidget(
            new GlassButton(
                this.width / 2 - 110,
                30,
                220,
                18,
                Component.translatable(SceneConfig.INSTANCE.menuAurora ? "nosclient.scene.preset.aurora" : "nosclient.scene.preset.lake"),
                () -> {
                    SceneConfig.INSTANCE.menuAurora = !SceneConfig.INSTANCE.menuAurora;
                    SceneConfig.INSTANCE.save();
                    this.rebuildWidgets();
                }
            )
        );
        this.addRenderableWidget(new GlassButton(this.width / 2 - 50, this.height - 30, 100, 20, Component.translatable("gui.back"), this::onClose));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (SceneConfig.INSTANCE.menuAurora) {
            AuroraBackground.render(g, this.width, this.height);
        } else {
            this.renderPanorama(g, pt);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        UiOpacity.value = this.motion.opacity();

        try {
            super.render(g, mx, my, pt);
            Ui.text(g, Ui.bold(this.title), (this.width - Ui.width(Ui.bold(this.title))) / 2, 12, Ui.TEXT);
        } finally {
            UiOpacity.value = 1.0F;
        }
    }

    @Override
    public void tick() {
        this.motion.tick();
    }

    @Override
    public void onClose() {
        this.motion.exit(() -> this.minecraft.setScreen(this.parent));
    }
}
