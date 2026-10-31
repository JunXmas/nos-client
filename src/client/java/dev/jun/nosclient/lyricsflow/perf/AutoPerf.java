package dev.jun.nosclient.lyricsflow.perf;

import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import net.minecraft.client.Minecraft;

public final class AutoPerf {
   public static final int MAX = 6;
   private static int level;
   private static int lastFps;
   private static int lowTicks;
   private static int okTicks;
   private static int relaxTicks;
   private static int badLevel = -1;
   private static long lastCheck;
   private static long badUntil;
   private static long raisedAt;
   private static long frameStart;
   private static double modMs;
   private static double frameAcc;

   private AutoPerf() {
   }

   public static int level() {
      return ConfigManager.get().autoPerf ? level : 0;
   }

   public static int fps() {
      return lastFps;
   }

   public static double modMs() {
      return modMs;
   }

   public static int quality() {
      int q = Math.max(0, Math.min(2, ConfigManager.get().quality));
      int lv = level();
      return lv >= 2 ? 0 : (lv >= 1 ? Math.min(q, 1) : q);
   }

   public static boolean allow(int cut) {
      return level() < cut;
   }

   public static int target() {
      LFConfig c = ConfigManager.get();
      int t = c.targetFps <= 0 ? 60 : c.targetFps;

      try {
         Minecraft mc = Minecraft.getInstance();
         if ((Boolean)mc.options.enableVsync().get()) {
            t = Math.min(t, 57);
         }

         int lim = (Integer)mc.options.framerateLimit().get();
         if (lim > 0 && lim < 260) {
            t = Math.min(t, lim - 3);
         }
      } catch (Throwable var4) {
      }

      return Math.max(20, t);
   }

   public static void begin() {
      frameStart = System.nanoTime();
   }

   public static void end() {
      if (frameStart != 0L) {
         frameAcc = frameAcc + (System.nanoTime() - frameStart) / 1000000.0;
      }

      frameStart = 0L;
   }

   public static void frame() {
      modMs = modMs * 0.95 + frameAcc * 0.05;
      frameAcc = 0.0;
   }

   public static void tick(Minecraft mc) {
      long now = System.currentTimeMillis();
      if (now - lastCheck >= 500L) {
         lastCheck = now;
         LFConfig c = ConfigManager.get();
         lastFps = mc.getFps();
         if (c.autoPerf && mc.level != null && mc.player != null && mc.screen == null) {
            int t = target();
            if (lastFps < t * 0.92) {
               lowTicks++;
               okTicks = 0;
            } else {
               okTicks++;
               lowTicks = 0;
            }

            double frameMs = 1000.0 / Math.max(1, lastFps);
            boolean modMatters = modMs > frameMs * 0.1 || modMs > 2.5;
            if (!modMatters) {
               lowTicks = 0;
               if (level > 0 && ++relaxTicks >= 6 && (level - 1 > badLevel || now >= badUntil)) {
                  level--;
                  raisedAt = now;
                  relaxTicks = 0;
               }
            } else {
               relaxTicks = 0;
               if (lowTicks >= 2 && level < 6) {
                  if (now - raisedAt < 6000L) {
                     badLevel = level;
                     badUntil = now + 120000L;
                  }

                  level = Math.min(6, level + (lastFps < t * 0.6 ? 2 : 1));
                  lowTicks = 0;
                  okTicks = 0;
               } else {
                  boolean plenty = lastFps >= t * 1.25;
                  boolean ok = lastFps >= t * 0.98;
                  if (level > 0 && (plenty && okTicks >= 8 || ok && okTicks >= 40)) {
                     int next = level - 1;
                     if (next <= badLevel && now < badUntil) {
                        return;
                     }

                     level = next;
                     raisedAt = now;
                     okTicks = 0;
                  }
               }
            }
         } else {
            okTicks = 0;
            lowTicks = 0;
         }
      }
   }

   public static void reset() {
      level = 0;
      okTicks = 0;
      lowTicks = 0;
      badLevel = -1;
   }
}
