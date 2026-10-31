package dev.jun.nosclient.lyricsflow.render;

import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import dev.jun.nosclient.lyricsflow.util.Col;
import java.util.List;

public final class Themes {
   public static final List<Themes.Theme> ALL = List.of(
      new Themes.Theme("neon_pink", -7178, -42296, -9447169, -14017980, -2520139),
      new Themes.Theme("ice_blue", -1508353, -12601089, -3608321, -15784896, -8406816),
      new Themes.Theme("sunset", -3872, -42358, -19641, -12969928, -26006),
      new Themes.Theme("matrix", -1638423, -14836107, -10813558, -16509942, -14848443),
      new Themes.Theme("lavender", -528129, -6595329, -1, -14016448, -4611352),
      new Themes.Theme("ocean", -1638405, -13703466, -12890934, -15979979, -12808536),
      new Themes.Theme("fire", -2858, -50116, -20434, -12972016, -2072534),
      new Themes.Theme("sakura", -3851, -28744, -11807, -12771280, -874044),
      new Themes.Theme("galaxy", -988929, -8635667, -14494738, -16054240, -11851126),
      new Themes.Theme("gold", -1050, -18432, -3168, -13753336, -2513855),
      new Themes.Theme("vaporwave", -1, -36402, -16658946, -14017984, -4626433),
      new Themes.Theme("toxic", -720928, -6029550, -5232897, -15064056, -10581474),
      new Themes.Theme("blood_moon", -6426, -5046245, -38037, -14416117, -7725526),
      new Themes.Theme("aurora", -1048584, -16718432, -7643914, -16311772, -13989254),
      new Themes.Theme("candy", -1, -25899, -6625025, -12965312, -1595176),
      new Themes.Theme("midnight", -1643265, -12890934, -5917444, -16052186, -12957046),
      new Themes.Theme("minecraft", -1, -12632257, -12632257, -8869889, -3744769),
      new Themes.Theme("minecraft_gold", -171, -12632299, -5605632, -14411206, -7705912),
      new Themes.Theme("enchant", -2045697, -5635926, -11184641, -15594972, -10863974),
      new Themes.Theme("nether", -12128, -5636096, -43776, -14022648, -8772592),
      new Themes.Theme("black", -16053489, -11908523, -6645078, -16448250, -12961214),
      new Themes.Theme("black_white", -1, -16053489, -14013904, -16448250, -14013904)
   );
   public static final int[] SINGLE = new int[]{
      -46518, -26052, -7862, -11870592, -13703466, -11891201, -12890934, -6595329, -37177, -14006, -1, -3618604, -10855834, -16119282
   };

   private Themes() {
   }

   public static Themes.Theme byId(String id) {
      for (Themes.Theme t : ALL) {
         if (t.id.equals(id)) {
            return t;
         }
      }

      return ALL.get(0);
   }

   public static Themes.Theme current() {
      LFConfig c = ConfigManager.get();
      Themes.Theme base = byId(c.theme);
      if (c.customTheme) {
         return new Themes.Theme("custom", c.customMain, c.customA, c.customB, Col.darken(c.customA, 0.82F), Col.lerp(c.customA, c.customB, 0.5F));
      } else {
         return c.singleColor != 0 ? new Themes.Theme(base.id, c.singleColor, base.a, base.b, base.skyTop, base.skyBottom) : base;
      }
   }

   public static int accent() {
      return ConfigManager.get().accentColor != 0 ? ConfigManager.get().accentColor : Col.lerp(-6595329, -7643914, 0.45F);
   }

   public record Theme(String id, int main, int a, int b, int skyTop, int skyBottom) {
      public String key() {
         return "lyricsflow.theme." + this.id;
      }
   }
}
