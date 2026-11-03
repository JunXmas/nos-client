package dev.jun.nosclient;

import com.mojang.blaze3d.platform.InputConstants.Type;

import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.gui.NosSettingsScreen;
import dev.jun.nosclient.gui.music.PlayerScreen;
import dev.jun.nosclient.gui.music.UiSounds;
import dev.jun.nosclient.media.MediaManager;
import dev.jun.nosclient.media.Track;
import dev.jun.nosclient.render.WorldLyrics;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;

public final class MediaKeys {
    public static final Category CATEGORY = Category.register(ClientResources.id("main"));
    public static KeyMapping openMenu;
    public static KeyMapping playPause;
    public static KeyMapping next;
    public static KeyMapping previous;
    public static KeyMapping openPlayer;
    public static KeyMapping toggle;
    public static KeyMapping lyricsEarlier;
    public static KeyMapping lyricsLater;

    private MediaKeys() {}

    public static void register() {
        openMenu =
                KeyBindingHelper.registerKeyBinding(
                        new KeyMapping("key.nosclient.music.menu", Type.KEYSYM, 75, CATEGORY));
        playPause =
                KeyBindingHelper.registerKeyBinding(
                        new KeyMapping(
                                "key.nosclient.music.play_pause", Type.KEYSYM, 264, CATEGORY));
        next =
                KeyBindingHelper.registerKeyBinding(
                        new KeyMapping("key.nosclient.music.next", Type.KEYSYM, 262, CATEGORY));
        openPlayer =
                KeyBindingHelper.registerKeyBinding(
                        new KeyMapping("key.nosclient.music.player", Type.KEYSYM, 59, CATEGORY));
        toggle =
                KeyBindingHelper.registerKeyBinding(
                        new KeyMapping("key.nosclient.music.onoff", Type.KEYSYM, 79, CATEGORY));
        previous =
                KeyBindingHelper.registerKeyBinding(
                        new KeyMapping("key.nosclient.music.previous", Type.KEYSYM, 263, CATEGORY));
        lyricsEarlier =
                KeyBindingHelper.registerKeyBinding(
                        new KeyMapping("key.nosclient.music.earlier", Type.KEYSYM, 93, CATEGORY));
        lyricsLater =
                KeyBindingHelper.registerKeyBinding(
                        new KeyMapping("key.nosclient.music.later", Type.KEYSYM, 91, CATEGORY));
    }

    public static void tick(Minecraft mc) {
        if (mc.screen != null) {
            drain();
            return;
        }
        while (openMenu.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(NosSettingsScreen.forFeature(null, "lyrics"));
            }
        }

        while (openPlayer.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new PlayerScreen());
            }
        }

        if (mc.screen != null) {
            drain();
            return;
        }

        while (toggle.consumeClick()) {
            MusicConfig c = MusicSettings.get();
            c.enabled = !c.enabled;
            MusicSettings.save();
            if (!c.enabled) {
                WorldLyrics.clear();
            }

            mc.gui.setOverlayMessage(
                    Component.literal("Nos Client: ")
                            .append(
                                    Component.translatable(
                                                    c.enabled
                                                            ? "nosclient.music.toggle.on"
                                                            : "nosclient.music.toggle.off")
                                            .withStyle(
                                                    c.enabled
                                                            ? ChatFormatting.GREEN
                                                            : ChatFormatting.RED)),
                    false);
            UiSounds.play(c.enabled ? UiSounds.TOGGLE_ON : UiSounds.TOGGLE_OFF, 1.0F);
        }

        while (lyricsEarlier.consumeClick()) {
            nudge(mc, 100);
        }

        while (lyricsLater.consumeClick()) {
            nudge(mc, -100);
        }

        boolean media = MusicSettings.get().mediaKeys;

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
        return new KeyMapping[] {
            openMenu, openPlayer, toggle, playPause, next, previous, lyricsEarlier, lyricsLater
        };
    }

    private static void drain() {
        for (KeyMapping key : all()) {
            while (key.consumeClick()) {}
        }
    }

    private static void nudge(Minecraft mc, int ms) {
        Track t = MediaManager.get().track();
        if (t != null) {
            MusicConfig c = MusicSettings.get();
            if (c.songOffsets == null) {
                c.songOffsets = new HashMap<>();
            }

            int v = Math.max(-5000, Math.min(5000, c.songOffsets.getOrDefault(t.key(), 0) + ms));
            if (v == 0) {
                c.songOffsets.remove(t.key());
            } else {
                c.songOffsets.put(t.key(), v);
            }

            MusicSettings.save();
            String shown =
                    v == 0
                            ? "0 s"
                            : (v > 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f s", v / 1000.0);
            mc.gui.setOverlayMessage(
                    Component.literal("Nos Client: ")
                            .append(
                                    Component.translatable(
                                            "nosclient.music.toast.song_offset",
                                            new Object[] {shown})),
                    false);
            UiSounds.play(UiSounds.CLICK, ms > 0 ? 1.15F : 0.9F);
        }
    }
}
