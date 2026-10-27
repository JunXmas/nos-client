package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

@Environment(EnvType.CLIENT)
public final class NosTitleScreen extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("nosclient", "textures/gui/logo.png");
    private final MenuMotion motion = new MenuMotion();
    private PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();
    private int x;
    private int y;
    private int w;
    private int h;
    private int gap;
    private int logoY;

    public NosTitleScreen() {
        super(Component.literal("Nos Client"));
    }

    private void navigate(Screen screen) {
        this.motion.exit(() -> this.minecraft.setScreen(screen));
    }

    private void button(int x, int y, int w, String label, Runnable action) {
        this.addRenderableWidget(new GlassButton(x, y, w, this.h, Component.translatable(label), action));
    }

    @Override
    protected void init() {
        this.motion.enter();
        this.minecraft
            .getSkinManager()
            .get(this.minecraft.getGameProfile())
            .thenAccept(found -> found.ifPresent(value -> this.minecraft.execute(() -> this.skin = value)));
        this.w = Math.min(196, this.width - 40);
        this.x = (this.width - this.w) / 2;
        this.h = this.height >= 280 ? 24 : 21;
        this.gap = 5;
        int content = 5 * this.h + 4 * this.gap + 46;
        this.logoY = Math.max(12, (this.height - content) / 2);
        this.y = this.logoY + 46;
        this.button(this.x, this.y, this.w, "nosclient.menu.single", () -> this.navigate(new SelectWorldScreen(this)));
        this.button(this.x, this.y + this.h + this.gap, this.w, "nosclient.menu.multi", () -> this.navigate(new JoinMultiplayerScreen(this)));
        this.button(this.x, this.y + 2 * (this.h + this.gap), this.w, "nosclient.menu.nos", () -> this.navigate(new NosSettingsScreen(this)));
        this.button(this.x, this.y + 3 * (this.h + this.gap), this.w, "nosclient.menu.hud", () -> this.navigate(new NosSettingsScreen(this, true)));
        int half = (this.w - this.gap) / 2;
        int bottom = this.y + 4 * (this.h + this.gap);
        this.button(this.x, bottom, half, "nosclient.menu.options", () -> this.navigate(new OptionsScreen(this, this.minecraft.options)));
        this.button(this.x + half + this.gap, bottom, this.w - half - this.gap, "nosclient.menu.quit", () -> this.navigate(new GlassConfirmScreen(ok -> {
            if (ok) {
                this.minecraft.stop();
            } else {
                this.minecraft.setScreen(this);
            }
        }, Component.translatable("nosclient.menu.quit"), Component.translatable("nosclient.menu.quit.confirm"))));
        this.addRenderableWidget(
            new GlassButton(
                this.width - 98, this.height - 30, 86, 18, Component.translatable("nosclient.scene.title"), () -> this.navigate(new SceneSettingsScreen(this))
            )
        );
    }

    @Override
    public void tick() {
        this.motion.tick();
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
            Component title = Ui.logo("Nos Client");
            int tx = (this.width - Ui.width(title)) / 2 + 13;
            g.blit(RenderPipelines.GUI_TEXTURED, LOGO, tx - 32, this.logoY + 4, 0.0F, 0.0F, 24, 24, 24, 24, UiOpacity.tint(-1));
            Ui.text(g, title, tx, this.logoY, Ui.TEXT);
            if (this.width >= 440) {
                PlayerFaceRenderer.draw(g, this.skin, this.width - 132, 14, 17);
                Ui.text(g, Ui.fit(Ui.ui(this.minecraft.getUser().getName()), 96), this.width - 109, 19, Ui.TEXT);
            }

            String version = FabricLoader.getInstance().getModContainer("nosclient").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
            Ui.text(g, Ui.small(Component.literal("Nos " + version + " · " + SharedConstants.getCurrentVersion().name())), 12, this.height - 14, Ui.MUTED);
        } finally {
            UiOpacity.value = 1.0F;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (this.motion.closing()) {
            return true;
        } else if (e.key() == 344) {
            this.navigate(new NosSettingsScreen(this));
            return true;
        } else {
            return super.keyPressed(e);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
