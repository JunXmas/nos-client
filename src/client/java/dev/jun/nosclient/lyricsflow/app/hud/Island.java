package dev.jun.nosclient.lyricsflow.app.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class Island {
   private Island() {
   }

   public static void notify(String text, boolean ok) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gui != null) {
         mc.gui.setOverlayMessage(Component.literal(text), false);
      }
   }
}
