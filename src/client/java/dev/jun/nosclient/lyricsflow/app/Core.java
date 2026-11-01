package dev.jun.nosclient.lyricsflow.app;

import dev.jun.nosclient.lyricsflow.app.config.Config;
import dev.jun.nosclient.lyricsflow.app.config.Storage;
import dev.jun.nosclient.lyricsflow.app.cosmetic.DeathFx;
import dev.jun.nosclient.lyricsflow.app.hud.Gfx;
import dev.jun.nosclient.lyricsflow.app.hud.GlassHotbar;
import dev.jun.nosclient.lyricsflow.app.lyrics.NowPlaying;
import dev.jun.nosclient.lyricsflow.app.sky.SkyRenderer;
import dev.jun.nosclient.lyricsflow.app.sky.WorldSky;
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

public final class Core {
   public static final Logger LOG = LoggerFactory.getLogger("LyricsFlow");
   private static Config config = new Config();

   private Core() {
   }

   public static Config cfg() {
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
      Config fresh = new Config();
      fresh.clamp();
      fresh.uiFont = Gfx.FONT_IDS.length;
      config = fresh;
      saveConfig();
   }

   public static void saveConfig() {
      config.clamp();
      Storage.save("settings.json", config);
   }

   public static void init() {
      config = Storage.load("settings.json", Config.class, Config::new);
      config.clamp();
      config.uiFont = Gfx.FONT_IDS.length;
      saveConfig();
      HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("lyricsflow", "sky_flash"), SkyRenderer::render);
      HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, GlassHotbar::wrap);
      WorldRenderEvents.AFTER_ENTITIES.register(WorldSky::render);
      AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
         DeathFx.onAttack(entity);
         return InteractionResult.PASS;
      });
      ClientTickEvents.END_CLIENT_TICK.register(Core::tick);
   }

   private static void tick(Minecraft mc) {
      NowPlaying.tick();
      DeathFx.tick(mc);
      SkyRenderer.tick(mc);
   }
}
