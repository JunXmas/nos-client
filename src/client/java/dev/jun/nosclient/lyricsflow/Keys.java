package dev.jun.nosclient.lyricsflow;

import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.jun.nosclient.lyricsflow.gui.LyricsFlowScreen;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import dev.jun.nosclient.lyricsflow.gui.PlayerScreen;
import dev.jun.nosclient.lyricsflow.gui.UiSounds;
import dev.jun.nosclient.lyricsflow.media.MediaManager;
import dev.jun.nosclient.lyricsflow.media.Track;
import dev.jun.nosclient.lyricsflow.render.WorldLyrics;
import java.util.HashMap;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.network.chat.Component;

public final class Keys {
   public static final Category CATEGORY = Category.register(LyricsFlow.id("main"));
   public static KeyMapping openMenu;
   public static KeyMapping playPause;
   public static KeyMapping next;
   public static KeyMapping previous;
   public static KeyMapping openPlayer;
   public static KeyMapping toggle;
   public static KeyMapping lyricsEarlier;
   public static KeyMapping lyricsLater;
   private static boolean movedKeys;

   private Keys() {
   }

   public static void register() {
      openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricsflow.menu", Type.KEYSYM, 75, CATEGORY));
      playPause = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricsflow.play_pause", Type.KEYSYM, 264, CATEGORY));
      next = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricsflow.next", Type.KEYSYM, 262, CATEGORY));
      openPlayer = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricsflow.player", Type.KEYSYM, 74, CATEGORY));
      toggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricsflow.onoff", Type.KEYSYM, 79, CATEGORY));
      previous = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricsflow.previous", Type.KEYSYM, 263, CATEGORY));
      lyricsEarlier = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricsflow.earlier", Type.KEYSYM, 93, CATEGORY));
      lyricsLater = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lyricsflow.later", Type.KEYSYM, 91, CATEGORY));
   }

   private static void moveOldKeys(Minecraft mc) {
      movedKeys = true;
      if (ConfigManager.moveKeys && mc.options != null) {
         ConfigManager.moveKeys = false;
         boolean changed = false;
         changed |= move(openMenu, "key.keyboard.period");
         changed |= move(openPlayer, "key.keyboard.semicolon");
         changed |= move(toggle, "key.keyboard.right.bracket");
         if (changed) {
            KeyMapping.resetMapping();
            mc.options.save();
         }

         ConfigManager.save();
      }
   }

   private static boolean move(KeyMapping k, String old) {
      if (!old.equals(k.saveString())) {
         return false;
      }

      k.setKey(k.getDefaultKey());
      return true;
   }

   public static void tick(Minecraft mc) {
      if (!movedKeys) {
         moveOldKeys(mc);
      }

      while (openMenu.consumeClick()) {
         if (mc.screen == null) {
            mc.setScreen(new LyricsFlowScreen());
         }
      }

      while (openPlayer.consumeClick()) {
         if (mc.screen == null) {
            mc.setScreen(new PlayerScreen());
         }
      }

      while (toggle.consumeClick()) {
         LFConfig c = ConfigManager.get();
         c.enabled = !c.enabled;
         ConfigManager.save();
         if (!c.enabled) {
            WorldLyrics.clear();
         }

         mc.gui
            .setOverlayMessage(
               Component.literal("LyricsFlow: ")
                  .append(
                     Component.translatable(c.enabled ? "lyricsflow.toggle.on" : "lyricsflow.toggle.off")
                        .withStyle(c.enabled ? ChatFormatting.GREEN : ChatFormatting.RED)
                  ),
               false
            );
         UiSounds.play(c.enabled ? UiSounds.TOGGLE_ON : UiSounds.TOGGLE_OFF, 1.0F);
      }

      while (lyricsEarlier.consumeClick()) {
         nudge(mc, 100);
      }

      while (lyricsLater.consumeClick()) {
         nudge(mc, -100);
      }

      boolean media = ConfigManager.get().mediaKeys;

      while (playPause.consumeClick()) {
         if (media) {
            MediaManager.get().playPause();
         }
      }

      while (next.consumeClick()) {
         if (media) {
            MediaManager.get().next();
         }
      }

      while (previous.consumeClick()) {
         if (media) {
            MediaManager.get().previous();
         }
      }
   }

   public static KeyMapping[] all() {
      return new KeyMapping[]{openMenu, openPlayer, toggle, playPause, next, previous};
   }

   private static void nudge(Minecraft mc, int ms) {
      Track t = MediaManager.get().track();
      if (t != null) {
         LFConfig c = ConfigManager.get();
         if (c.songOffsets == null) {
            c.songOffsets = new HashMap<>();
         }

         int v = Math.max(-5000, Math.min(5000, c.songOffsets.getOrDefault(t.key(), 0) + ms));
         if (v == 0) {
            c.songOffsets.remove(t.key());
         } else {
            c.songOffsets.put(t.key(), v);
         }

         ConfigManager.save();
         String shown = v == 0 ? "0 s" : (v > 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f s", v / 1000.0);
         mc.gui
            .setOverlayMessage(
               Component.literal("LyricsFlow: ").append(Component.translatable("lyricsflow.toast.song_offset", new Object[]{shown})), false
            );
         UiSounds.play(UiSounds.CLICK, ms > 0 ? 1.15F : 0.9F);
      }
   }
}
