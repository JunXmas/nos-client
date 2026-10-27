package dev.jun.nosclient;

import dev.jun.nosclient.feat.Feats;
import dev.jun.nosclient.feat.RecipeAuto;
import dev.jun.nosclient.gui.Glass;
import dev.jun.nosclient.gui.Icons;
import dev.jun.nosclient.gui.IntroScreen;
import dev.jun.nosclient.gui.NosSettingsScreen;
import dev.jun.nosclient.gui.NosTitleScreen;
import dev.jun.nosclient.hud.Hud;
import dev.jun.nosclient.media.SkyLyrics;
import dev.jun.nosclient.media.SkyTextMaterial;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.theme.Theme;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class NosClient implements ClientModInitializer {
    public void onInitializeClient() {
        Glass.init();
        SkyTextMaterial.init();
        Icons.init();
        Theme.palette();
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> Theme.palette());
        Feats.init();
        SkyLyrics.init();
        RecipeAuto.init();
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Identifier.fromNamespaceAndPath("nosclient", "hud"), Hud.INSTANCE::render);
        HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, old -> (g, dt) -> {
            if (!SceneConfig.INSTANCE.effects) {
                old.render(g, dt);
            }
        });
        ScreenEvents.AFTER_INIT
            .register(
                (AfterInit)(mc, screen, w, h) -> {
                    if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
                        List<AbstractWidget> buttons = Screens.getButtons(screen);
                        int bottom = buttons.stream().mapToInt(b -> b.getY() + b.getHeight()).max().orElse(h / 2);
                        int x = w / 2 - 102;
                        int y = bottom + 4;
                        int bw = 204;
                        if (y + 20 > h - 4) {
                            bw = Math.min(148, w - 16);
                            x = w - bw - 8;
                            y = 8;
                        }

                        buttons.add(
                            Button.builder(Component.translatable("nosclient.settings.title"), b -> mc.setScreen(new NosSettingsScreen(screen)))
                                .bounds(x, y, bw, 20)
                                .build()
                        );
                    }
                }
            );
        ScreenEvents.AFTER_INIT.register((AfterInit)(mc, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                IntroScreen intro = IntroScreen.active;
                if (intro != null) {
                    mc.setScreen(intro);
                } else if (SceneConfig.INSTANCE.intro && !IntroScreen.played) {
                    mc.setScreen(new IntroScreen(new NosTitleScreen()));
                } else {
                    mc.setScreen(new NosTitleScreen());
                }
            }
        });
        KeyMapping open = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("nosclient.key.settings", 344, Category.register(Identifier.fromNamespaceAndPath("nosclient", "main")))
        );
        ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
            while (open.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new NosSettingsScreen(null));
                }
            }
        });
    }
}
