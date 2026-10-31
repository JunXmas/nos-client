package dev.jun.nosclient.lyricsflow.gui;

import dev.jun.nosclient.lyricsflow.LyricsFlow;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class UiSounds {
   public static final SoundEvent CLICK = event("ui.click");
   public static final SoundEvent HOVER = event("ui.hover");
   public static final SoundEvent OPEN = event("ui.open");
   public static final SoundEvent CLOSE = event("ui.close");
   public static final SoundEvent TAB = event("ui.tab");
   public static final SoundEvent TOGGLE_ON = event("ui.toggle_on");
   public static final SoundEvent TOGGLE_OFF = event("ui.toggle_off");
   public static final SoundEvent SLIDER = event("ui.slider");
   public static final SoundEvent SONG = event("ui.song");
   private static final SoundEvent S_CLICK = event("ui.serious.click");
   private static final SoundEvent S_TAB = event("ui.serious.tab");
   private static final SoundEvent S_ON = event("ui.serious.toggle_on");
   private static final SoundEvent S_OFF = event("ui.serious.toggle_off");
   private static final SoundEvent S_SONG = event("ui.serious.song");
   private static final RandomSource RANDOM = RandomSource.create();
   private static long lastHover;
   private static long lastSlider;

   private UiSounds() {
   }

   private static SoundEvent event(String name) {
      return SoundEvent.createVariableRangeEvent(LyricsFlow.id(name));
   }

   public static void play(SoundEvent e, float pitch) {
      if (ConfigManager.get().uiSounds || e == SONG) {
         float vol = 1.0F;
         Minecraft mc = Minecraft.getInstance();
         if (mc.getSoundManager() != null) {
            if (e == SONG) {
               mc.getSoundManager()
                  .play(
                     new SimpleSoundInstance(e.location(), SoundSource.MASTER, vol, pitch, RANDOM, false, 0, Attenuation.NONE, 0.0, 0.0, 0.0, true)
                  );
            } else {
               boolean clicky = e == CLICK || e == TAB || e == TOGGLE_ON || e == TOGGLE_OFF;
               if (clicky) {
                  mc.getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.35F * vol));
               }
            }
         }
      }
   }

   public static void play(SoundEvent e) {
      play(e, 1.0F);
   }
}
