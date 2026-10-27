package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.cosmetic.Cosmetics;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.theme.Theme;
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
    private long opened = System.nanoTime();

    public NosTitleScreen() {
        super(Component.literal("Nos Client"));
    }

    private void navigate(Screen screen) {
        this.motion.exit(() -> this.minecraft.setScreen(screen));
    }

    private void button(int x, int y, int w, String label, Runnable action) {
        float row = (y - this.y) / (float)(this.h + this.gap);
        this.addRenderableWidget(new GlassButton(x, y, w, this.h, Component.translatable(label), action).delayed(0.08F + 0.055F * row + (x > this.x ? 0.03F : 0.0F)));
    }

    @Override
    protected void init() {
        this.motion.enter();
        this.opened = System.nanoTime();
        this.minecraft
            .getSkinManager()
            .get(this.minecraft.getGameProfile())
            .thenAccept(found -> found.ifPresent(value -> this.minecraft.execute(() -> this.skin = value)));
        this.w = Math.min(196, this.width - 40);
        this.x = (this.width - this.w) / 2;
        this.h = this.height >= 280 ? 24 : 21;
        this.gap = 5;
        int content = 5 * this.h + 4 * this.gap + 50;
        this.logoY = Math.max(12, (this.height - content) / 2);
        this.y = this.logoY + 50;
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
                .delayed(0.4F)
        );
        if (Cosmetics.ENABLED) {
            this.addRenderableWidget(
                new GlassButton(12, this.height - 30, 86, 18, Component.translatable("nosclient.cosmetic.title"), () -> this.navigate(new CosmeticScreen(this)))
                    .delayed(0.45F)
            );
        }
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

        // Tối nhẹ phía dưới cho chữ phiên bản/nút Phong cảnh đọc rõ, phía trên để logo nổi.
        g.fillGradient(0, 0, this.width, this.height / 4, 0x50000000, 0);
        g.fillGradient(0, this.height * 3 / 4, this.width, this.height, 0, 0x60000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        UiOpacity.value = this.motion.opacity();

        try {
            super.render(g, mx, my, pt);
            Component title = Ui.logo("Nos Client");
            int tx = (this.width - Ui.width(title)) / 2 + 13;
            float age = (System.nanoTime() - this.opened) / 1.0E9F;
            boolean animated = SceneConfig.INSTANCE.animations;
            float enter = animated ? Ui.ease(Math.clamp(age / 0.6F, 0.0F, 1.0F)) : 1.0F;
            // Logo rơi nhẹ xuống chỗ rồi bồng bềnh; biểu tượng xoay vào trước chữ một nhịp.
            float bob = animated ? (float)Math.sin(age * 1.1F) * 1.2F : 0.0F;
            float opacity = UiOpacity.value;
            UiOpacity.value = opacity * enter;
            g.pose().pushMatrix();
            g.pose().translate(0.0F, -10.0F * (1.0F - enter) + bob);
            float spin = animated ? (1.0F - Ui.ease(Math.clamp(age / 0.5F, 0.0F, 1.0F))) * -1.2F : 0.0F;
            g.pose().pushMatrix();
            g.pose().translate(tx - 20, this.logoY + 16);
            g.pose().rotate(spin);
            g.blit(RenderPipelines.GUI_TEXTURED, LOGO, -12, -12, 0.0F, 0.0F, 24, 24, 24, 24, UiOpacity.tint(-1));
            g.pose().popMatrix();
            UiOpacity.value = opacity * (animated ? Ui.ease(Math.clamp((age - 0.12F) / 0.5F, 0.0F, 1.0F)) : 1.0F);
            Ui.text(g, title, tx, this.logoY, Ui.TEXT);
            // Gạch accent dưới logo, giãn vào cùng lúc chữ hiện.
            int line = Math.round(46.0F * enter);
            int lx = (this.width - line) / 2 + 13;
            Glass.glow(g, lx, this.logoY + 31, line, 1, Ui.alpha(Theme.palette().accent() | 0xFF000000, 0.5F * enter));
            Glass.fill(g, lx, this.logoY + 31, line, 1, Ui.alpha(Theme.palette().accent() | 0xFF000000, 0.9F * enter));
            g.pose().popMatrix();
            UiOpacity.value = opacity;
            if (this.width >= 440) {
                PlayerFaceRenderer.draw(g, this.skin, this.width - 132, 14, 17);
                Ui.text(g, Ui.fit(Ui.ui(this.minecraft.getUser().getName()), 96), this.width - 109, 19, Ui.TEXT);
            }

            String version = FabricLoader.getInstance().getModContainer("nosclient").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
            Ui.text(g, Ui.small(Component.literal("Nos " + version + " · " + SharedConstants.getCurrentVersion().name())), 12, this.height - 10, Ui.MUTED);
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
