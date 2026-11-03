package dev.jun.nosclient.util;

import dev.jun.nosclient.ClientEffects;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class Fx {
    private Fx() {}

    public static String t(String key, Object... args) {
        return I18n.get(key, args);
    }

    public static void sound(Fx.Sfx s) {
        if (ClientEffects.cfg().sounds) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                switch (s) {
                    case CLICK:
                        mc.getSoundManager()
                                .play(
                                        SimpleSoundInstance.forUI(
                                                (SoundEvent) SoundEvents.UI_BUTTON_CLICK.value(),
                                                1.4F,
                                                0.35F));
                        break;
                    case WIN:
                        mc.getSoundManager()
                                .play(
                                        SimpleSoundInstance.forUI(
                                                SoundEvents.PLAYER_LEVELUP, 1.0F, 0.6F));
                        break;
                    case ERROR:
                        mc.getSoundManager()
                                .play(
                                        SimpleSoundInstance.forUI(
                                                (SoundEvent) SoundEvents.NOTE_BLOCK_BASS.value(),
                                                0.6F,
                                                0.7F));
                        break;
                    case TICK:
                        mc.getSoundManager()
                                .play(
                                        SimpleSoundInstance.forUI(
                                                (SoundEvent) SoundEvents.NOTE_BLOCK_HAT.value(),
                                                1.8F,
                                                0.4F));
                }
            }
        }
    }

    public static void info(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui
                    .getChat()
                    .addMessage(
                            Component.literal("[Nos Client] ")
                                    .withStyle(ChatFormatting.AQUA)
                                    .append(
                                            Component.literal(text)
                                                    .withStyle(ChatFormatting.WHITE)));
        }
    }

    public static void error(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui
                    .getChat()
                    .addMessage(
                            Component.literal("[Nos Client] ")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                                    .append(Component.literal(text).withStyle(ChatFormatting.RED)));
            sound(Fx.Sfx.ERROR);
        }
    }

    public static void clipboard(String s) {
        Minecraft.getInstance().keyboardHandler.setClipboard(s);
    }

    public static String myName() {
        return Minecraft.getInstance().getUser().getName();
    }

    public enum Sfx {
        CLICK,
        WIN,
        ERROR,
        TICK;
    }
}
