package dev.jun.nosclient.lyricsflow.app.cosmetic;

import dev.jun.nosclient.lyricsflow.app.Core;
import dev.jun.nosclient.lyricsflow.gui.LyricsFlowScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

public final class PhoneMenu {
   private static WorldPanel panel;

   private PhoneMenu() {
   }

   public static boolean interactive() {
      return Core.cfg().menuMode == 1;
   }

   public static void open(Minecraft mc) {
      if (panel != null) {
         if (!panel.closing()) {
            panel.close();
         }
      } else if (interactive() && mc.level != null && mc.player != null) {
         panel = new WorldPanel(mc);
      } else {
         mc.setScreen(new LyricsFlowScreen());
      }
   }

   public static void closeNow() {
      panel = null;
   }

   public static void menuClosed() {
   }

   public static boolean pointerShown() {
      return panel != null && !panel.closing() && panel.hovered();
   }

   public static boolean hidesHand() {
      return false;
   }

   public static void tick(Minecraft mc) {
      if (panel != null) {
         if (mc.level != null && mc.player != null && !panel.finished() && !(mc.player.position().distanceTo(panel.pos()) > 32.0)) {
            panel.tick(mc);
         } else {
            panel = null;
         }
      }
   }

   public static void startTick(Minecraft mc) {
      if (panel != null && mc.screen == null && panel.hovered()) {
         while (mc.options.keyAttack.consumeClick()) {
            panel.click();
         }

         while (mc.options.keyUse.consumeClick()) {
            panel.click();
         }
      }
   }

   public static void render(Minecraft mc, MultiBufferSource buffers, Matrix4f base, float pt) {
      if (panel != null && mc.player != null) {
         panel.render(mc, buffers, base);
      }
   }
}
