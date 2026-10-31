package dev.jun.nosclient.lyricsflow;

import dev.jun.nosclient.lyricsflow.app.Core;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.media.CosmeticSync;
import dev.jun.nosclient.lyricsflow.media.MediaManager;
import dev.jun.nosclient.lyricsflow.perf.AutoPerf;
import dev.jun.nosclient.lyricsflow.render.Chroma;
import dev.jun.nosclient.lyricsflow.render.CosmeticLayer;
import dev.jun.nosclient.lyricsflow.render.HelmetLayer;
import dev.jun.nosclient.lyricsflow.render.Hud;
import dev.jun.nosclient.lyricsflow.render.LivePing;
import dev.jun.nosclient.lyricsflow.render.WorldLyrics;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.InteractionResult;

public class LyricsFlowClient implements ClientModInitializer {
   @Override
   public void onInitializeClient() {
      ConfigManager.load();
      Keys.register();
      Core.init();
      HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, LyricsFlow.id("world_lyrics"), Hud::renderWorldLayer);
      HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LyricsFlow.id("top_bar"), Hud::renderTopLayer);
      ClientTickEvents.END_CLIENT_TICK.register(Keys::tick);
      ClientTickEvents.END_CLIENT_TICK.register(LivePing::tick);
      ClientTickEvents.END_CLIENT_TICK.register(CosmeticSync::tick);
      ClientTickEvents.END_CLIENT_TICK.register(AutoPerf::tick);
      WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
         if (!Chroma.drawsLate() && !WorldLyrics.lateForShaders()) {
            Hud.renderWorldSky(ctx.matrices(), ctx.consumers());
            WorldLyrics.renderWorld(ctx.matrices(), ctx.consumers());
         } else {
            Chroma.deferred = true;
         }
      });
      LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
         if (renderer instanceof AvatarRenderer<?> r) {
            registerLayers(helper, r);
         }
      });
      AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> InteractionResult.PASS);
      ClientLifecycleEvents.CLIENT_STARTED.register(client -> MediaManager.get().start());
      ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MediaManager.get().stop());
      LyricsFlow.LOG.info("LyricsFlow {} ready", "v2.1-beta");
   }

   private static void registerLayers(RegistrationHelper helper, AvatarRenderer<?> r) {
      helper.register(new HelmetLayer(r));
      helper.register(new CosmeticLayer(r));
   }
}
