package dev.jun.nosclient.lyricsflow.perf;

import dev.jun.nosclient.lyricsflow.LyricsFlow;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

public final class FpsBoost {
   private FpsBoost() {
   }

   public static void apply(boolean on) {
      Minecraft mc = Minecraft.getInstance();
      LFConfig c = ConfigManager.get();

      try {
         if (!on) {
            if (c.boostBefore != null && !c.boostBefore.isEmpty()) {
               String[] v = c.boostBefore.split(";", -1);
               if (v.length >= 3) {
                  set(mc.options.entityShadows(), v[0]);
                  set(mc.options.particles(), v[1]);
                  set(mc.options.biomeBlendRadius(), v[2]);
               }

               if (v.length >= 5) {
                  set(mc.options.cloudStatus(), v[3]);
                  set(mc.options.enableVsync(), v[4]);
               }

               c.boostBefore = "";
               if (c.quality == 0) {
                  c.quality = 1;
               }
            }
         } else {
            if (!c.fpsBoost || c.boostBefore == null || c.boostBefore.isEmpty()) {
               c.boostBefore = save(mc.options.entityShadows())
                  + ";"
                  + save(mc.options.particles())
                  + ";"
                  + save(mc.options.biomeBlendRadius())
                  + ";"
                  + save(mc.options.cloudStatus())
                  + ";"
                  + save(mc.options.enableVsync());
            }

            set(mc.options.entityShadows(), "false");
            set(mc.options.particles(), "MINIMAL");
            set(mc.options.biomeBlendRadius(), "0");
            set(mc.options.cloudStatus(), "OFF");
            set(mc.options.enableVsync(), "false");
            c.quality = 0;
         }

         mc.options.save();
      } catch (Exception e) {
         LyricsFlow.LOG.warn("[LyricsFlow] FPS boost could not change every setting", e);
      }

      c.fpsBoost = on;
      ConfigManager.save();
   }

   private static String save(OptionInstance<?> o) {
      Object v = o.get();
      return v instanceof Enum<?> e ? e.name() : String.valueOf(v);
   }

   private static void set(OptionInstance o, String value) {
      Object cur = o.get();
      Object nv;
      if (cur instanceof Boolean) {
         nv = Boolean.parseBoolean(value);
      } else if (cur instanceof Integer) {
         nv = Integer.parseInt(value);
      } else {
         if (!(cur instanceof Enum<?> e)) {
            return;
         }

         nv = Enum.valueOf(e.getDeclaringClass(), value);
      }

      o.set(nv);
   }
}
