package dev.jun.nosclient.lyricsflow.render;

public final class ScreenshotHelper {
   private static Runnable pending;
   private static int framesLeft;

   private ScreenshotHelper() {
   }

   public static boolean defer(Runnable takeScreenshot) {
      return false;
   }

   public static void afterFrame() {
      if (pending != null) {
         if (framesLeft-- <= 0) {
            Runnable r = pending;
            pending = null;

            try {
               r.run();
            } finally {
               Hud.hideForScreenshot = false;
            }
         }
      }
   }
}
