package dev.jun.nosclient;

import dev.jun.nosclient.config.ClientSettings;
import dev.jun.nosclient.config.EffectsConfig;
import dev.jun.nosclient.config.LegacyConfig;
import dev.jun.nosclient.cosmetic.effects.DeathFx;
import dev.jun.nosclient.gui.effects.Gfx;
import dev.jun.nosclient.gui.effects.GlassHotbar;
import dev.jun.nosclient.media.playback.NowPlaying;
import dev.jun.nosclient.sky.SkyRenderer;
import dev.jun.nosclient.sky.WorldSky;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClientEffects {
    public static final Logger LOG = LoggerFactory.getLogger("nosclient");
    private static EffectsConfig config = new EffectsConfig();

    private ClientEffects() {}

    public static EffectsConfig cfg() {
        return config;
    }

    public static boolean skyWorld(Level level) {
        if (level == null) {
            return false;
        }

        if (level.dimension() == Level.OVERWORLD) {
            return true;
        }

        if (level.dimension() != Level.NETHER && level.dimension() != Level.END) {
            try {
                DimensionType t = level.dimensionType();
                return t.hasSkyLight() && !t.hasCeiling();
            } catch (Throwable e) {
                return true;
            }
        } else {
            return false;
        }
    }

    public static void resetConfig() {
        EffectsConfig fresh = new EffectsConfig();
        fresh.clamp();
        fresh.uiFont = Gfx.FONT_IDS.length;
        config = fresh;
        saveConfig();
    }

    public static void saveConfig() {
        config.clamp();
        ClientSettings.current().replace("effects", config);
        ClientSettings.current().save();
    }

    public static void init() {
        config =
                ClientSettings.current()
                        .section("effects", EffectsConfig.class, LegacyConfig::effects);
        config.clamp();
        config.uiFont = Gfx.FONT_IDS.length;
        saveConfig();
        HudElementRegistry.addFirst(
                Identifier.fromNamespaceAndPath("nosclient", "sky_flash"), SkyRenderer::render);
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, GlassHotbar::wrap);
        WorldRenderEvents.AFTER_ENTITIES.register(WorldSky::render);
        AttackEntityCallback.EVENT.register(
                (player, level, hand, entity, hit) -> {
                    DeathFx.onAttack(entity);
                    return InteractionResult.PASS;
                });
        ClientTickEvents.END_CLIENT_TICK.register(ClientEffects::tick);
    }

    private static void tick(Minecraft mc) {
        NowPlaying.tick();
        DeathFx.tick(mc);
        SkyRenderer.tick(mc);
    }
}
