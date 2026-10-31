package dev.jun.nosclient.lyricsflow.app.util;

import net.minecraft.client.Minecraft;

public final class AutoPerf {
   public static final int MAX = 6;

   private AutoPerf() {
   }

   public static int level() {
      return dev.jun.nosclient.lyricsflow.perf.AutoPerf.level();
   }

   public static int fps() {
      return dev.jun.nosclient.lyricsflow.perf.AutoPerf.fps();
   }

   public static double modMs() {
      return dev.jun.nosclient.lyricsflow.perf.AutoPerf.modMs();
   }

   public static int quality() {
      return dev.jun.nosclient.lyricsflow.perf.AutoPerf.quality();
   }

   public static boolean allow(int cut) {
      return dev.jun.nosclient.lyricsflow.perf.AutoPerf.allow(cut);
   }

   public static int target() {
      return dev.jun.nosclient.lyricsflow.perf.AutoPerf.target();
   }

   public static void begin() {
      dev.jun.nosclient.lyricsflow.perf.AutoPerf.begin();
   }

   public static void end() {
      dev.jun.nosclient.lyricsflow.perf.AutoPerf.end();
   }

   public static void frame() {
      dev.jun.nosclient.lyricsflow.perf.AutoPerf.frame();
   }

   public static void tick(Minecraft mc) {
   }

   public static void reset() {
      dev.jun.nosclient.lyricsflow.perf.AutoPerf.reset();
   }
}
