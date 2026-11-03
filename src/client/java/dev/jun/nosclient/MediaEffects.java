package dev.jun.nosclient;

import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.feat.AutoPerf;
import dev.jun.nosclient.media.MediaManager;
import dev.jun.nosclient.render.Chroma;
import dev.jun.nosclient.render.CosmeticLayer;
import dev.jun.nosclient.render.HelmetLayer;
import dev.jun.nosclient.render.Hud;
import dev.jun.nosclient.render.LivePing;
import dev.jun.nosclient.render.WorldLyrics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;

public final class MediaEffects {
    public static void init() {
        MusicSettings.load();
        MediaKeys.register();
        ClientEffects.init();
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT, ClientResources.id("top_bar"), Hud::renderTopLayer);
        ClientTickEvents.END_CLIENT_TICK.register(MediaKeys::tick);
        ClientTickEvents.END_CLIENT_TICK.register(LivePing::tick);
        ClientTickEvents.END_CLIENT_TICK.register(AutoPerf::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(
                ctx -> {
                    if (!Chroma.drawsLate() && !WorldLyrics.lateForShaders()) {
                        Hud.renderWorldSky(ctx.matrices(), ctx.consumers());
                        WorldLyrics.renderWorld(ctx.matrices(), ctx.consumers());
                    } else {
                        Chroma.deferred = true;
                    }
                });
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (type, renderer, helper, ctx) -> {
                    if (renderer instanceof AvatarRenderer<?> r) {
                        registerLayers(helper, r);
                    }
                });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> MediaManager.get().start());
        ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> {
                    dev.jun.nosclient.sky.SkyRenderer.restoreClouds(client);
                    MediaManager.get().stop();
                });
        ClientResources.LOG.info("Nos Client {} ready", "0.4.0");
    }

    private static void registerLayers(RegistrationHelper helper, AvatarRenderer<?> r) {
        helper.register(new HelmetLayer(r));
        helper.register(new CosmeticLayer(r));
    }
}
