package dev.jun.nosclient.lyricsflow.gui;

import dev.jun.nosclient.lyricsflow.Keys;
import dev.jun.nosclient.lyricsflow.app.Core;
import dev.jun.nosclient.lyricsflow.app.config.Config;
import dev.jun.nosclient.lyricsflow.app.cosmetic.DeathFx;
import dev.jun.nosclient.lyricsflow.app.sky.SkyImage;
import dev.jun.nosclient.lyricsflow.app.sky.SkyPreset;
import dev.jun.nosclient.lyricsflow.app.sky.WorldSky;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import dev.jun.nosclient.lyricsflow.media.AppVolume;
import dev.jun.nosclient.lyricsflow.media.MediaManager;
import dev.jun.nosclient.lyricsflow.media.SpotifyWeb;
import dev.jun.nosclient.lyricsflow.media.Track;
import dev.jun.nosclient.lyricsflow.perf.AutoPerf;
import dev.jun.nosclient.lyricsflow.perf.FpsBoost;
import dev.jun.nosclient.lyricsflow.perf.Profiles;
import dev.jun.nosclient.lyricsflow.render.Chroma;
import dev.jun.nosclient.lyricsflow.render.Cosmetics;
import dev.jun.nosclient.lyricsflow.render.Fonts;
import dev.jun.nosclient.lyricsflow.render.Gfx;
import dev.jun.nosclient.lyricsflow.render.Themes;
import dev.jun.nosclient.lyricsflow.render.WorldLyrics;
import dev.jun.nosclient.lyricsflow.util.Anim;
import dev.jun.nosclient.lyricsflow.util.Col;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.function.DoubleFunction;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;

final class Pages {
   private static int skyPage;
   private static final int[] DARK = argb(new int[]{131848, 460058, 656920, 1378328, 201240, 1053982, 1839672, 2758216, 4068404, 668480, 1784412, 4852242});
   private static final int[] BRIGHT = argb(
      new int[]{10719743, 13127912, 15757496, 15756906, 16296026, 16310384, 5957792, 3856584, 8046840, 5929200, 15263984, 16777215}
   );
   private static final int[] PLAIN_PALETTE = new int[]{-2084816, -1013212, -863174, -12793766, -12793640, -12948240, -7451928, -1021264, -855310, -16119286};
   private static final int[] SKY_PALETTE = new int[]{
      -16777216, -16053473, -15592914, -14020560, -16045520, -15459284, -14017974, -11916694, -10870704, -15050134, -14005622, -9826280
   };
   private static final int[] AURORA_PALETTE = new int[]{
      -5726977, -3649304, -1019720, -1021334, -481190, -466832, -10819424, -12920632, -8730376, -10848016, -1513232, -1
   };
   private static String pasteStatus;
   private static final int[] TARGETS = new int[]{0, 60, 90, 120, 144, 240};

   private Pages() {
   }

   private static LFConfig c() {
      return ConfigManager.get();
   }

   private static String pct(double v) {
      return Math.round(v * 100.0) + "%";
   }

   private static String ms(double v) {
      long m = Math.round(v);
      return m == 0L ? "0 ms" : (m > 0L ? "+" : "") + String.format(Locale.ROOT, "%.2f s", m / 1000.0);
   }

   private static String mult(double v) {
      return String.format(Locale.ROOT, "%.2fx", v);
   }

   static List<Ui.Widget> build(LyricsFlowScreen.Tab tab, LyricsFlowScreen screen, boolean heroVisible) {
      List<Ui.Widget> l = new ArrayList<>();
      switch (tab) {
         case NOW:
            now(l, heroVisible);
            break;
         case LYRICS:
            lyrics(l, screen);
            break;
         case STYLE:
            style(l, screen);
            break;
         case SKY:
            sky(l, screen);
            break;
         case COSMETICS:
            cosmetics(l, screen);
            break;
         case DISPLAY:
            display(l);
            break;
         case KEYS:
            keys(l);
            break;
         case SAVED:
            saved(l, screen);
            break;
         case PERF:
            perf(l, screen);
      }

      l.add(new Ui.Spacer(8.0F));
      return l;
   }

   private static void now(List<Ui.Widget> l, boolean heroVisible) {
      if (!heroVisible) {
         l.add(
            new Ui.Custom(w -> NowPlaying.height(w), (g, x, y, w, h, mx, my, a) -> NowPlaying.renderHero(g, x, y, w, h, mx, my, a), (mx, my, x, y, w, h) -> {
               if (!Ui.inside(mx, my, x, y, w, h)) {
                  return false;
               }

               NowPlaying.click(mx, my);
               return true;
            })
         );
      }

      l.add(
         new Ui.Card()
            .add(
               new Ui.Button(
                  "lyricsflow.opt.open_player",
                  "lyricsflow.opt.open_player.desc",
                  () -> Ui.tr("lyricsflow.button.open"),
                  "music",
                  true,
                  () -> Minecraft.getInstance().setScreen(new PlayerScreen())
               )
            )
      );
      l.add(new Ui.Section("lyricsflow.section.quick"));
      l.add(
         new Ui.Card()
            .add(new Ui.Toggle("lyricsflow.opt.enabled", "lyricsflow.opt.enabled.desc", () -> c().enabled, v -> c().enabled = v))
            .add(new Ui.Toggle("lyricsflow.opt.show_lyrics", "lyricsflow.opt.show_lyrics.desc", () -> c().showLyrics, v -> c().showLyrics = v))
            .add(
               new Ui.Choice(
                  "lyricsflow.opt.lyrics_style",
                  null,
                  new String[]{"lyricsflow.style.sky", "lyricsflow.style.floating"},
                  () -> Math.min(1, c().lyricsStyle.ordinal()),
                  i -> {
                     c().lyricsStyle = LFConfig.LyricsStyle.values()[i];
                     WorldLyrics.clear();
                  }
               )
            )
            .add(new Ui.Choice("lyricsflow.perf.profile", "lyricsflow.perf.profile.desc", new String[]{"Chill", "PvP"}, () -> c().profile, i -> {
               Profiles.set(i);
               note(i == 0 ? "lyricsflow.perf.profile.chill" : "lyricsflow.perf.profile.pvp");
            }))
            .add(new Ui.Toggle("lyricsflow.opt.sky_follow", "lyricsflow.opt.sky_follow.desc", () -> c().skyFollow, v -> c().skyFollow = v))
            .add(new Ui.Slider("lyricsflow.opt.scale", 0.4, 2.5, 0.05, () -> c().lyricsScale, v -> c().lyricsScale = (float)v, Pages::mult))
            .add(new Ui.Toggle("lyricsflow.opt.top_bar", "lyricsflow.opt.top_bar.desc", () -> c().showTopBar, v -> c().showTopBar = v))
            .add(new Ui.Toggle("lyricsflow.opt.chroma", "lyricsflow.opt.chroma.desc", () -> c().chroma, v -> c().chroma = v))
            .add(
               new Ui.Choice(
                  "lyricsflow.opt.chroma_color",
                  null,
                  Arrays.stream(Chroma.COLORS).map(n -> "lyricsflow.chroma." + n).toArray(String[]::new),
                  () -> c().chromaColor,
                  i -> c().chromaColor = i
               )
            )
            .add(
               new Ui.Slider(
                  "lyricsflow.opt.music_volume",
                  0.0,
                  1.0,
                  0.01,
                  () -> Math.max(0.0F, AppVolume.get()),
                  v -> AppVolume.set((float)v),
                  v -> AppVolume.get() < 0.0F ? "—" : Math.round(v * 100.0) + "%"
               )
            )
      );
      source(l);
   }

   private static void lyrics(List<Ui.Widget> l, LyricsFlowScreen screen) {
      l.add(new Ui.Section("lyricsflow.section.general"));
      l.add(
         new Ui.Card()
            .add(new Ui.Toggle("lyricsflow.opt.show_lyrics", "lyricsflow.opt.show_lyrics.desc", () -> c().showLyrics, v -> c().showLyrics = v))
            .add(
               new Ui.Choice(
                  "lyricsflow.opt.lyrics_style",
                  "lyricsflow.opt.lyrics_style.desc",
                  new String[]{"lyricsflow.style.sky", "lyricsflow.style.floating"},
                  () -> Math.min(1, c().lyricsStyle.ordinal()),
                  i -> {
                     c().lyricsStyle = LFConfig.LyricsStyle.values()[i];
                     WorldLyrics.clear();
                     screen.rebuildSoon();
                  }
               )
            )
            .add(new Ui.Choice("lyricsflow.perf.profile", "lyricsflow.perf.profile.desc", new String[]{"Chill", "PvP"}, () -> c().profile, i -> {
               Profiles.set(i);
               note(i == 0 ? "lyricsflow.perf.profile.chill" : "lyricsflow.perf.profile.pvp");
               screen.rebuildSoon();
            }))
      );
      l.add(new Ui.Section("lyricsflow.section.size"));
      Ui.Card size = new Ui.Card()
         .add(new Ui.Slider("lyricsflow.opt.scale", 0.4, 2.5, 0.05, () -> c().lyricsScale, v -> c().lyricsScale = (float)v, Pages::mult));
      if (c().lyricsStyle == LFConfig.LyricsStyle.FLOATING) {
         size.add(new Ui.Slider("lyricsflow.opt.distance", 0.4, 2.0, 0.05, () -> c().lyricsDistance, v -> c().lyricsDistance = (float)v, Pages::mult))
            .add(new Ui.Toggle("lyricsflow.opt.follow_view", "lyricsflow.opt.follow_view.desc", () -> c().followView, v -> c().followView = v))
            .add(
               new Ui.Slider(
                  "lyricsflow.opt.float_height",
                  -1.0,
                  4.0,
                  0.25,
                  () -> c().floatHeight,
                  v -> c().floatHeight = (float)v,
                  v -> (v > 0.0 ? "+" : "") + String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "")
               )
            );
      }

      if (c().lyricsStyle == LFConfig.LyricsStyle.SKY) {
         size.add(new Ui.Slider("lyricsflow.opt.distance", 30.0, 300.0, 10.0, () -> c().skyDistance, v -> c().skyDistance = (float)v, v -> Math.round(v) + "m"))
            .add(new Ui.Toggle("lyricsflow.opt.sky_words", "lyricsflow.opt.sky_words.desc", () -> c().skyWords, v -> c().skyWords = v));
      }

      l.add(
         size.add(new Ui.Slider("lyricsflow.opt.opacity", 0.2, 1.0, 0.05, () -> c().textOpacity, v -> c().textOpacity = (float)v, Pages::pct))
            .add(
               new Ui.Slider(
                  "lyricsflow.opt.max_lines",
                  1.0,
                  3.0,
                  1.0,
                  () -> c().maxLines,
                  v -> c().maxLines = (int)Math.round(v),
                  v -> String.valueOf((int)Math.round(v))
               )
            )
      );
      l.add(new Ui.Section("lyricsflow.section.animation"));
      l.add(
         new Ui.Card()
            .add(
               new Ui.Choice(
                  "lyricsflow.opt.enter",
                  null,
                  new String[]{"lyricsflow.enter.word", "lyricsflow.enter.fade", "lyricsflow.enter.rise", "lyricsflow.enter.neon"},
                  () -> c().enterAnim.ordinal(),
                  i -> c().enterAnim = LFConfig.EnterAnim.values()[i]
               )
            )
            .add(
               new Ui.Choice(
                  "lyricsflow.opt.exit",
                  null,
                  new String[]{"lyricsflow.exit.float_up", "lyricsflow.exit.drift", "lyricsflow.exit.fade"},
                  () -> c().exitAnim.ordinal(),
                  i -> c().exitAnim = LFConfig.ExitAnim.values()[i]
               )
            )
            .add(new Ui.Slider("lyricsflow.opt.anim_speed", 0.5, 2.0, 0.05, () -> c().animSpeed, v -> c().animSpeed = (float)v, Pages::mult))
      );
      l.add(new Ui.Section("lyricsflow.section.advanced"));
      l.add(
         new Ui.Card()
            .add(new Ui.Toggle("lyricsflow.opt.underground", "lyricsflow.opt.underground.desc", () -> c().showUnderground, v -> c().showUnderground = v))
            .add(new Ui.Toggle("lyricsflow.opt.music_notes", "lyricsflow.opt.music_notes.desc", () -> c().musicNotes, v -> c().musicNotes = v))
      );
   }

   private static void style(List<Ui.Widget> l, LyricsFlowScreen screen) {
      l.add(new Ui.Section("lyricsflow.section.font"));
      List<Fonts.LyricFont> fonts = Fonts.lyricsAll();
      l.add(
         new Ui.Card()
            .add(
               new Ui.Chips(
                     "lyricsflow.section.font",
                     null,
                     fonts.stream().map(Fonts.LyricFont::name).toArray(String[]::new),
                     () -> Math.max(0, indexOf(fonts, c().font)),
                     i -> {
                        c().font = fonts.get(i).id();
                        WorldLyrics.clear();
                     }
                  )
                  .fonts(fonts.stream().map(Fonts.LyricFont::id).toArray(String[]::new))
                  .reset(() -> {
                     c().font = (new LFConfig()).font;
                     WorldLyrics.clear();
                  })
            )
            .add(
               new Ui.Choice(
                  "lyricsflow.opt.text_shadow",
                  null,
                  new String[]{"lyricsflow.shadow.off", "lyricsflow.shadow.soft", "lyricsflow.shadow.strong"},
                  () -> c().textShadow,
                  i -> c().textShadow = i
               )
            )
            .add(new Ui.Toggle("lyricsflow.opt.all_caps", null, () -> c().allCaps, v -> {
               c().allCaps = v;
               WorldLyrics.clear();
            }))
      );
      l.add(new Ui.Section("lyricsflow.section.look"));
      l.add(
         new Ui.Card()
            .add(new Ui.Colors("lyricsflow.opt.colour", "lyricsflow.opt.colour.desc", () -> c().singleColor, v -> c().singleColor = v))
            .add(new Ui.Toggle("lyricsflow.opt.beat_glow", "lyricsflow.opt.beat_glow.desc", () -> c().beatGlow, v -> c().beatGlow = v))
      );
      themeGrid(l);
   }

   private static void themeGrid(List<Ui.Widget> l) {
      l.add(new Ui.Section("lyricsflow.section.text_color"));
      List<Themes.Theme> all = Themes.ALL;
      IntSupplier cur = () -> {
         for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(c().theme)) {
               return i;
            }
         }

         return 0;
      };
      IntConsumer pick = i -> {
         c().theme = all.get(Math.floorMod(i, all.size())).id();
         c().customTheme = false;
         c().singleColor = 0;
      };
      l.add(
         new Ui.Card()
            .add(
               new Ui.Chips(
                     "lyricsflow.section.text_color",
                     null,
                     all.stream().map(Themes.Theme::key).toArray(String[]::new),
                     () -> !c().customTheme && c().singleColor == 0 ? cur.getAsInt() : -1,
                     pick
                  )
                  .dots(all.stream().mapToInt(Themes.Theme::a).toArray())
                  .reset(() -> {
                     LFConfig d = new LFConfig();
                     c().theme = d.theme;
                     c().customTheme = false;
                     c().singleColor = 0;
                  })
            )
      );
      Ui.Card custom = new Ui.Card();
      custom.add(new Ui.Toggle("lyricsflow.opt.custom_theme", "lyricsflow.opt.custom_theme.desc", () -> c().customTheme, v -> {
         c().customTheme = v;
         if (v) {
            Themes.Theme t = Themes.byId(c().theme);
            c().customMain = t.main();
            c().customA = t.a();
            c().customB = t.b();
         }
      }));
      custom.add(hueSlider("lyricsflow.opt.custom_main", () -> c().customMain, v -> c().customMain = v, 0.12F, 1.0F));
      custom.add(hueSlider("lyricsflow.opt.custom_a", () -> c().customA, v -> c().customA = v, 0.7F, 1.0F));
      custom.add(hueSlider("lyricsflow.opt.custom_b", () -> c().customB, v -> c().customB = v, 0.6F, 1.0F));
      l.add(custom);
   }

   private static Ui.Widget hueSlider(String label, IntSupplier get, IntConsumer set, float sat, float val) {
      return new Ui.Slider(label, 0.0, 358.0, 2.0, () -> Math.min(358, Math.round(Col.toHsv(get.getAsInt())[0] * 360.0F)), v -> {
         set.accept(Col.hsv((float)(v / 360.0), sat, val));
         c().customTheme = true;
      }, v -> Math.round(v) + "°") {
         @Override
         public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
            super.render(g, ox, oy, mx, my, a);
            Gfx.circle(g, ox + this.x + Gfx.textWidth(Ui.tr(this.label), Ui.FB, 8.4F) + 10.0F, oy + this.y + 9.5F, 3.6F, Col.alpha(get.getAsInt(), a));
         }
      };
   }

   private static Ui.Widget fontGrid() {
      List<Fonts.LyricFont> fonts = Fonts.lyricsAll();
      float[] hover = new float[fonts.size()];
      float cellH = 34.0F;
      return new Ui.Custom(
         w -> (float)Math.ceil(fonts.size() / 2.0) * (cellH + 6.0F) + 4.0F,
         (g, x, y, w, h, mx, my, a) -> {
            float cw = (w - 6.0F) / 2.0F;
            Themes.Theme th = Themes.current();

            for (int i = 0; i < fonts.size(); i++) {
               Fonts.LyricFont f = fonts.get(i);
               float fx = x + i % 2 * (cw + 6.0F);
               float fy = y + i / 2 * (cellH + 6.0F);
               boolean sel = c().font.equals(f.id());
               boolean over = Ui.inside(mx, my, fx, fy, cw, cellH);
               if (over && hover[i] < 0.05F) {
                  UiSounds.play(UiSounds.HOVER, 1.2F);
               }

               hover[i] = Anim.approach(hover[i], over ? 1.0F : 0.0F, 14.0F);
               float lift = -hover[i] * 1.2F;
               if (sel) {
                  Gfx.glow(g, fx, fy + lift, cw, cellH, 8.0F, 4.0F, Col.alpha(Themes.accent(), a * 0.35F));
               }

               Gfx.round(g, fx, fy + lift, cw, cellH, 8.0F, Col.alpha(Col.lerp(-15001562, -14541264, hover[i]), a));
               Gfx.roundOutline(g, fx, fy + lift, cw, cellH, 8.0F, sel ? 1.2F : 0.5F, Col.alpha(sel ? Themes.accent() : 352321535, a));
               float size = 14.5F;
               String name = Gfx.ellipsize(f.name(), f.id(), size, cw - 24.0F);
               if (sel) {
                  Gfx.neon(g, name, f.id(), fx + cw / 2.0F - 4.0F, fy + lift + (cellH - size) / 2.0F - 1.0F, size, th.main(), th.a(), th.b(), a, true, 0.4F);
                  Gfx.circle(g, fx + cw - 10.0F, fy + lift + 10.0F, 5.0F, Col.alpha(Themes.accent(), a));
                  Icons.draw(g, "check", fx + cw - 10.0F, fy + lift + 10.0F, 6.5F, Col.alpha(-1, a));
               } else {
                  Gfx.textCentered(
                     g,
                     name,
                     f.id(),
                     fx + cw / 2.0F - 4.0F,
                     fy + lift + (cellH - size) / 2.0F - 1.0F,
                     size,
                     Col.alpha(Col.lerp(-6515020, -856070, hover[i]), a)
                  );
               }
            }
         },
         (mx, my, x, y, w, h) -> {
            float cw = (w - 6.0F) / 2.0F;

            for (int i = 0; i < fonts.size(); i++) {
               float fx = x + i % 2 * (cw + 6.0F);
               float fy = y + i / 2 * (cellH + 6.0F);
               if (Ui.inside(mx, my, fx, fy, cw, cellH)) {
                  c().font = fonts.get(i).id();
                  ConfigManager.save();
                  WorldLyrics.clear();
                  UiSounds.play(UiSounds.TOGGLE_ON, 0.9F + i * 0.02F);
                  return true;
               }
            }

            return false;
         }
      );
   }

   private static Config sc() {
      return Core.cfg();
   }

   private static int[] argb(int[] rgb) {
      int[] o = new int[rgb.length];

      for (int i = 0; i < rgb.length; i++) {
         o[i] = 0xFF000000 | rgb[i];
      }

      return o;
   }

   private static void sky(List<Ui.Widget> l, LyricsFlowScreen screen) {
      Config c = sc();
      l.add(new Ui.Section("lyricsflow.ui.group.general"));
      l.add(new Ui.Card().add(new Ui.Toggle("lyricsflow.ui.sky.enabled", "lyricsflow.ui.sky.enabled.sub", () -> c.sky >= 0, v -> {
         c.sky = v ? Math.max(0, c.lastSky) : -1;
         screen.rebuildSoon();
      })).add(new Ui.Toggle("lyricsflow.ui.sky.random", "lyricsflow.ui.sky.shuffle.sub", () -> c.skyShuffle, v -> {
         c.skyShuffle = v;
         if (v) {
            SkyPreset.shuffle();
            screen.rebuildSoon();
         }
      })));
      l.add(new Ui.Section("lyricsflow.ui.sky.picture"));
      Ui.Card pic = new Ui.Card().add(new Ui.Toggle("lyricsflow.ui.sky.picture.on", "lyricsflow.ui.sky.picture.on.sub", () -> c.skyImage, v -> {
         c.skyImage = v;
         screen.rebuildSoon();
      }));
      List<String> pics = SkyImage.files();
      pic.add(new Ui.Button("lyricsflow.ui.sky.picture.folder", null, () -> Ui.tr("lyricsflow.ui.sky.picture.folder"), "download", false, SkyImage::openFolder));
      pic.add(new Ui.Button("lyricsflow.ui.sky.picture.reload", null, () -> Ui.tr("lyricsflow.ui.sky.picture.reload"), "refresh", false, () -> {
         SkyImage.reload();
         screen.rebuildSoon();
      }));
      l.add(pic);
      if (!pics.isEmpty()) {
         float cellH = 58.0F;
         float[] hov = new float[pics.size()];
         l.add(new Ui.Custom(w -> {
            int cols = w > 330.0F ? 3 : 2;
            return (float)Math.ceil((double)pics.size() / cols) * (cellH + 6.0F) + 2.0F;
         }, (g, x, y, w, h, mx, my, a) -> {
            int cols = w > 330.0F ? 3 : 2;
            float cw = (w - 6 * (cols - 1)) / cols;
            String curx = pics.contains(c.skyImageFile) ? c.skyImageFile : pics.get(0);

            for (int ix = 0; ix < pics.size(); ix++) {
               String name = pics.get(ix);
               float fx = x + ix % cols * (cw + 6.0F);
               float fy = y + ix / cols * (cellH + 6.0F);
               boolean on = c.skyImage && name.equals(curx);
               boolean over = Ui.inside(mx, my, fx, fy, cw, cellH);
               hov[ix] = Anim.approach(hov[ix], over ? 1.0F : 0.0F, 14.0F);
               float ih = cellH - 14.0F;
               Gfx.round(g, fx - 1.0F, fy - 1.0F, cw + 2.0F, cellH + 2.0F, 7.0F, Col.alpha(on ? Themes.accent() : Col.lerp(-14541264, -13883584, hov[ix]), a));
               Identifier id = SkyImage.thumb(name);
               if (id != null) {
                  Gfx.image(g, id, 160, 80, fx + 2.0F, fy + 2.0F, cw - 4.0F, ih - 2.0F, Col.alpha(-1, a));
               } else {
                  Gfx.round(g, fx + 2.0F, fy + 2.0F, cw - 4.0F, ih - 2.0F, 5.0F, Col.alpha(-15396065, a));
               }

               Gfx.text(g, Gfx.ellipsize(name, Ui.F, 6.4F, cw - 8.0F), Ui.F, fx + 4.0F, fy + ih + 2.5F, 6.4F, Col.alpha(on ? -856070 : -6515020, a));
            }
         }, (mx, my, x, y, w, h) -> {
            int cols = w > 330.0F ? 3 : 2;
            float cw = (w - 6 * (cols - 1)) / cols;

            for (int ix = 0; ix < pics.size(); ix++) {
               float fx = x + ix % cols * (cw + 6.0F);
               float fy = y + ix / cols * (cellH + 6.0F);
               if (Ui.inside(mx, my, fx, fy, cw, cellH)) {
                  c.skyImageFile = pics.get(ix);
                  c.skyImage = true;
                  Core.saveConfig();
                  UiSounds.play(UiSounds.TOGGLE_ON, 0.9F + ix * 0.03F);
                  return true;
               }
            }

            return false;
         }));
      }

      l.add(new Ui.Info(() -> {
         String info = Ui.tr("lyricsflow.ui.sky.picture.info", "4096 x 2048");
         if (c.skyImage && SkyImage.files().isEmpty()) {
            info = info + "\n" + Ui.tr("lyricsflow.ui.sky.picture.none");
         } else if (!SkyImage.size().isEmpty()) {
            info = info + "\n" + Ui.tr("lyricsflow.ui.sky.picture.loaded", SkyImage.size());
         }

         return info;
      }));
      l.add(new Ui.Section("lyricsflow.ui.sky.style"));
      l.add(new Ui.SkyGrid(SkyPreset.order(), () -> c.sky >= 0 ? SkyPreset.get(c.sky) : null, p -> {
         c.sky = p.ordinal();
         c.lastSky = p.ordinal();
         screen.rebuildSoon();
      }));
      SkyPreset cur = SkyPreset.get(c.sky >= 0 ? c.sky : c.lastSky);
      if (cur == SkyPreset.CUSTOM && c.sky >= 0) {
         Class<SkyPreset> A = SkyPreset.class;
         SkyPreset.Weather[] ws = SkyPreset.Weather.values();
         String[] wn = Arrays.stream(ws)
            .map(w -> w == SkyPreset.Weather.NONE ? "lyricsflow.skyed.noweather" : "lyricsflow.feat." + w.name().toLowerCase(Locale.ROOT))
            .toArray(String[]::new);
         l.add(new Ui.Section("lyricsflow.skyed.title"));
         l.add(new Ui.Card().add(new Ui.Colors("lyricsflow.skyed.top", null, () -> 0xFF000000 | c.customTop, v -> {
            c.customTop = v & 16777215;
            SkyPreset.applyCustom();
         }, DARK, false)).add(new Ui.Colors("lyricsflow.skyed.horizon", null, () -> 0xFF000000 | c.customHorizon, v -> {
            c.customHorizon = v & 16777215;
            SkyPreset.applyCustom();
         }, DARK, false)).add(new Ui.Colors("lyricsflow.skyed.aurora1", null, () -> 0xFF000000 | c.customAurora1, v -> {
            c.customAurora1 = v & 16777215;
            SkyPreset.applyCustom();
         }, BRIGHT, false)).add(new Ui.Colors("lyricsflow.skyed.aurora2", null, () -> 0xFF000000 | c.customAurora2, v -> {
            c.customAurora2 = v & 16777215;
            SkyPreset.applyCustom();
         }, BRIGHT, false)).add(new Ui.Toggle("lyricsflow.feat.aurora", null, () -> c.customAurora, v -> {
            c.customAurora = v;
            SkyPreset.applyCustom();
         })).add(new Ui.Toggle("lyricsflow.feat.stars", null, () -> c.customStars, v -> {
            c.customStars = v;
            SkyPreset.applyCustom();
         })).add(new Ui.Toggle("lyricsflow.feat.moon", null, () -> c.customMoon, v -> {
            c.customMoon = v;
            SkyPreset.applyCustom();
         })).add(new Ui.Toggle("lyricsflow.feat.shooting", null, () -> c.customShooting, v -> {
            c.customShooting = v;
            SkyPreset.applyCustom();
         })).add(new Ui.Chips("lyricsflow.skyed.weather", null, wn, () -> Math.floorMod(c.customWeather, ws.length), ix -> {
            c.customWeather = ix;
            SkyPreset.applyCustom();
         })));
      }

      l.add(new Ui.Section("lyricsflow.ui.sky.tune"));
      String[] pals = new String[WorldSky.PALETTE_KEYS.length + 1];
      pals[0] = "lyricsflow.ui.palette.original";

      for (int i = 0; i < WorldSky.PALETTE_KEYS.length; i++) {
         pals[i + 1] = "lyricsflow.ui.palette." + WorldSky.PALETTE_KEYS[i];
      }

      l.add(
         new Ui.Card()
            .add(new Ui.Chips("lyricsflow.ui.sky.palette", null, pals, () -> c.skyPalette + 1, ix -> c.skyPalette = ix - 1))
            .add(new Ui.Slider("lyricsflow.ui.sky.speed", 0.25, 2.0, 0.05, () -> c.skySpeed, v -> c.skySpeed = (float)v, Pages::mult))
            .add(new Ui.Slider("lyricsflow.ui.sky.lively", 0.0, 1.0, 0.05, () -> c.skyLively, v -> c.skyLively = (float)v, Pages::pct))
            .add(new Ui.Slider("lyricsflow.ui.sky.pattern", 0.2, 1.5, 0.05, () -> c.skyPattern, v -> c.skyPattern = (float)v, Pages::pct))
            .add(new Ui.Slider("lyricsflow.opt.skystrength", 0.1, 1.0, 0.05, () -> c.skyStrength, v -> c.skyStrength = (float)v, Pages::pct))
            .add(new Ui.Slider("lyricsflow.opt.weatheramount", 0.0, 2.0, 0.05, () -> c.weatherAmount, v -> c.weatherAmount = (float)v, Pages::pct))
      );
      l.add(new Ui.Section("lyricsflow.ui.sky.behaviour"));
      l.add(
         new Ui.Card()
            .add(new Ui.Toggle("lyricsflow.opt.realweather", "lyricsflow.ui.sky.realweather.sub", () -> c.realWeather, v -> c.realWeather = v))
            .add(new Ui.Toggle("lyricsflow.opt.hideclouds", "lyricsflow.ui.sky.hideclouds.sub", () -> c.hideClouds, v -> c.hideClouds = v))
            .add(new Ui.Toggle("lyricsflow.extras.skyday", "lyricsflow.ui.sky.skyday.sub", () -> c.skyDay, v -> {
               c.skyDay = v;
               if (v) {
                  c.fxDayNight = false;
               }
            }))
            .add(new Ui.Toggle("lyricsflow.extras.const", "lyricsflow.ui.sky.const.sub", () -> c.fxConstellations, v -> c.fxConstellations = v))
            .add(new Ui.Toggle("lyricsflow.extras.skymusic", "lyricsflow.ui.sky.music.sub", () -> c.fxSkyMusic, v -> c.fxSkyMusic = v))
      );
   }

   private static void cosmetics(List<Ui.Widget> l, LyricsFlowScreen screen) {
      l.add(new Ui.Section("lyricsflow.section.cosmetics"));
      l.add(new Ui.Info(() -> Ui.tr("lyricsflow.opt.cosmetic.desc")));
      String[] ids = Cosmetics.IDS;
      l.add(new Ui.CosmeticGrid(ids, () -> Math.max(0, Arrays.asList(ids).indexOf(c().cosmetic)), i -> c().cosmetic = ids[i]));
      l.add(new Ui.Info(() -> Ui.tr("lyricsflow.info.cosmetic_share")));
      Config sc = sc();
      l.add(new Ui.Section("lyricsflow.ui.style.death"));
      String[] de = new String[4];

      for (int i = 0; i < de.length; i++) {
         de[i] = "lyricsflow.ui.death." + i;
      }

      Ui.Card death = new Ui.Card()
         .add(new Ui.Chips("lyricsflow.ui.style.death", "lyricsflow.ui.style.death.sub", de, () -> Math.floorMod(sc.deathFx, de.length), i -> {
            sc.deathFx = i;
            screen.rebuildSoon();
         }));
      if (sc.deathFx > 0) {
         death.add(new Ui.Toggle("lyricsflow.ui.style.deathothers", "lyricsflow.ui.style.deathothers.sub", () -> sc.deathOthers, v -> sc.deathOthers = v))
            .add(
               new Ui.Button(
                  "lyricsflow.ui.style.deathtest", "lyricsflow.ui.style.deathtest.sub", () -> Ui.tr("lyricsflow.ui.style.deathtest"), "play", true, () -> {
                     Minecraft.getInstance().setScreen(null);
                     DeathFx.test();
                  }
               )
            );
      }

      l.add(death);
   }

   private static void display(List<Ui.Widget> l) {
      Config sc = sc();
      l.add(new Ui.Section("lyricsflow.ui.display.menu"));
      l.add(
         new Ui.Card()
            .add(
               new Ui.Choice(
                  "lyricsflow.ui.menu.mode",
                  "lyricsflow.ui.menu.world.sub",
                  new String[]{"lyricsflow.ui.menu.normal", "lyricsflow.ui.menu.world"},
                  () -> sc.menuMode,
                  i -> sc.menuMode = i
               )
            )
      );
      l.add(new Ui.Section("lyricsflow.section.top_bar"));
      String[] uiFonts = Fonts.UI.stream().map(f -> "lyricsflow.uifont." + f.id()).toArray(String[]::new);
      l.add(
         new Ui.Card()
            .add(new Ui.Toggle("lyricsflow.opt.top_bar", "lyricsflow.opt.top_bar.desc", () -> c().showTopBar, v -> c().showTopBar = v))
            .add(new Ui.Toggle("lyricsflow.opt.custom_hotbar", "lyricsflow.opt.custom_hotbar.desc", () -> c().customHotbar, v -> c().customHotbar = v))
            .add(
               new Ui.Choice(
                  "lyricsflow.opt.top_bar_style",
                  "lyricsflow.opt.top_bar_style.desc",
                  new String[]{"lyricsflow.topbar.karaoke", "lyricsflow.topbar.simple"},
                  () -> c().topBarStyle.ordinal(),
                  i -> c().topBarStyle = LFConfig.TopBarStyle.values()[i]
               )
            )
            .add(new Ui.Slider("lyricsflow.opt.top_bar_scale", 0.6, 1.8, 0.05, () -> c().topBarScale, v -> c().topBarScale = (float)v, Pages::mult))
            .add(new Ui.Toggle("lyricsflow.opt.clock", null, () -> c().clock, v -> c().clock = v))
            .add(new Ui.Toggle("lyricsflow.opt.ping", null, () -> c().ping, v -> c().ping = v))
            .add(new Ui.Toggle("lyricsflow.opt.fps", null, () -> c().fps, v -> c().fps = v))
      );
      l.add(new Ui.Section("lyricsflow.section.behaviour"));
      l.add(
         new Ui.Card()
            .add(
               new Ui.Toggle("lyricsflow.opt.only_playing", "lyricsflow.opt.only_playing.desc", () -> c().showOnlyWhenPlaying, v -> c().showOnlyWhenPlaying = v)
            )
            .add(new Ui.Toggle("lyricsflow.opt.hide_paused", "lyricsflow.opt.hide_paused.desc", () -> c().hideWhenStopped, v -> c().hideWhenStopped = v))
            .add(new Ui.Toggle("lyricsflow.opt.dim_idle", "lyricsflow.opt.dim_idle.desc", () -> c().dimWhenIdle, v -> c().dimWhenIdle = v))
            .add(new Ui.Toggle("lyricsflow.opt.song_chime", "lyricsflow.opt.song_chime.desc", () -> c().songChime, v -> c().songChime = v))
      );
   }

   private static int indexOf(List<Fonts.LyricFont> list, String id) {
      for (int i = 0; i < list.size(); i++) {
         if (list.get(i).id().equals(id)) {
            return i;
         }
      }

      return -1;
   }

   private static void source(List<Ui.Widget> l) {
      MediaManager mm = MediaManager.get();
      l.add(new Ui.Section("lyricsflow.section.status"));
      l.add(new Ui.Card().add(new Ui.Status(() -> Ui.tr(mm.statusKey()), () -> {
         Track t = mm.track();
         return t == null ? Ui.tr("lyricsflow.status.hint") : t.title + "  ·  " + t.artist;
      }, () -> {
         Track t = mm.track();
         return t == null ? -9541498 : (t.playing ? -11870592 : -11414);
      })));
      l.add(new Ui.Section("lyricsflow.section.connection"));
      l.add(
         new Ui.Card()
            .add(
               new Ui.Choice(
                  "lyricsflow.opt.source",
                  "lyricsflow.opt.source.desc",
                  new String[]{"lyricsflow.source.spotify", "lyricsflow.source.youtube"},
                  () -> Math.min(1, c().source.ordinal()),
                  i -> {
                     c().source = LFConfig.Source.values()[i];
                     mm.reconnect();
                  }
               )
            )
            .add(new Ui.Button("lyricsflow.opt.reconnect", null, () -> Ui.tr("lyricsflow.button.reconnect"), "refresh", false, mm::reconnect))
      );
      l.add(new Ui.Info(() -> Ui.tr("lyricsflow.info.automatic")));
      l.add(new Ui.Section("lyricsflow.section.spotify_exact"));
      l.add(
         new Ui.Card()
            .add(
               new Ui.Status(
                  () -> Ui.tr("lyricsflow.spotify.state." + SpotifyWeb.state()),
                  () -> {
                     String e = SpotifyWeb.error();
                     return "error".equals(SpotifyWeb.state()) && !e.isEmpty()
                        ? Ui.tr("lyricsflow.spotify.err." + e)
                        : Ui.tr("lyricsflow.spotify.redirect", "http://127.0.0.1:8789/callback");
                  },
                  () -> {
                     return switch (SpotifyWeb.state()) {
                        case "on" -> -11870592;
                        case "waiting" -> -11414;
                        case "error" -> -495247;
                        default -> -9541498;
                     };
                  }
               )
            )
            .add(
               new Ui.TextField(
                  "lyricsflow.opt.spotify_client",
                  "lyricsflow.opt.spotify_client.desc",
                  "lyricsflow.opt.spotify_client.hint",
                  SpotifyWeb::clientId,
                  SpotifyWeb::setClientId
               )
            )
            .add(
               new Ui.Button(
                  "lyricsflow.opt.spotify_login",
                  null,
                  () -> Ui.tr(SpotifyWeb.connected() ? "lyricsflow.button.disconnect" : "lyricsflow.button.connect"),
                  "refresh",
                  !SpotifyWeb.connected(),
                  () -> {
                     if (SpotifyWeb.connected()) {
                        SpotifyWeb.disconnect();
                     } else {
                        SpotifyWeb.login();
                     }
                  }
               )
            )
      );
      l.add(new Ui.Info(() -> Ui.tr("lyricsflow.info.spotify_exact")));
      l.add(new Ui.Section("lyricsflow.section.playback"));
      l.add(
         new Ui.Card()
            .add(
               new Ui.Slider("lyricsflow.opt.sync_offset", -2000.0, 2000.0, 50.0, () -> c().syncOffsetMs, v -> c().syncOffsetMs = (int)Math.round(v), Pages::ms)
            )
            .add(new Ui.Button("lyricsflow.opt.refetch", "lyricsflow.opt.refetch.desc", () -> Ui.tr("lyricsflow.button.retry"), "refresh", false, mm::refetch))
      );
      l.add(new Ui.Info(() -> Ui.tr("lyricsflow.info.sync_offset")));
   }

   private static void keys(List<Ui.Widget> l) {
      l.add(new Ui.Section("lyricsflow.section.keys"));
      l.add(
         new Ui.Card()
            .add(new Ui.KeyBind("key.lyricsflow.menu", null, Keys.openMenu))
            .add(new Ui.KeyBind("key.lyricsflow.player", null, Keys.openPlayer))
            .add(new Ui.KeyBind("key.lyricsflow.onoff", "lyricsflow.opt.toggle_key.desc", Keys.toggle))
            .add(new Ui.Toggle("lyricsflow.opt.media_keys", "lyricsflow.opt.media_keys.desc", () -> c().mediaKeys, v -> c().mediaKeys = v))
            .add(new Ui.KeyBind("key.lyricsflow.play_pause", null, Keys.playPause))
            .add(new Ui.KeyBind("key.lyricsflow.next", null, Keys.next))
            .add(new Ui.KeyBind("key.lyricsflow.previous", null, Keys.previous))
            .add(new Ui.KeyBind("key.lyricsflow.earlier", null, Keys.lyricsEarlier))
            .add(new Ui.KeyBind("key.lyricsflow.later", null, Keys.lyricsLater))
      );
      l.add(new Ui.Info(() -> Ui.tr("lyricsflow.info.keys")));
      l.add(new Ui.Section("lyricsflow.section.sounds"));
      l.add(new Ui.Card().add(new Ui.Toggle("lyricsflow.opt.ui_sounds", "lyricsflow.opt.ui_sounds.desc", () -> c().uiSounds, v -> c().uiSounds = v)));
   }

   private static void saved(List<Ui.Widget> l, LyricsFlowScreen screen) {
      MediaManager mm = MediaManager.get();
      l.add(new Ui.Section("lyricsflow.section.timing"));
      l.add(
         new Ui.Card()
            .add(
               new Ui.Slider("lyricsflow.opt.sync_offset", -2000.0, 2000.0, 50.0, () -> c().syncOffsetMs, v -> c().syncOffsetMs = (int)Math.round(v), Pages::ms)
            )
            .add(
               new Ui.Button(
                  "lyricsflow.opt.clear_song_offsets",
                  "lyricsflow.opt.clear_song_offsets.desc",
                  () -> Ui.tr("lyricsflow.button.clear_n", c().songOffsets.size()),
                  "trash",
                  false,
                  () -> {
                     c().songOffsets.clear();
                     ConfigManager.save();
                     screen.rebuildSoon();
                  }
               )
            )
            .add(new Ui.Button("lyricsflow.opt.refetch", "lyricsflow.opt.refetch.desc", () -> Ui.tr("lyricsflow.button.retry"), "refresh", false, mm::refetch))
            .add(new Ui.Button("lyricsflow.opt.reconnect", null, () -> Ui.tr("lyricsflow.button.reconnect"), "refresh", false, mm::reconnect))
      );
      l.add(new Ui.Section("lyricsflow.section.profiles"));
      l.add(
         new Ui.Card()
            .add(
               new Ui.Button(
                  "lyricsflow.opt.save_profile", "lyricsflow.opt.save_profile.desc", () -> Ui.tr("lyricsflow.button.save"), "download", true, () -> {
                     c().profiles.put(freeName("lyricsflow.profile.name"), ConfigManager.snapshot());
                     ConfigManager.save();
                     screen.rebuildSoon();
                  }
               )
            )
            .add(
               new Ui.Button(
                  "lyricsflow.opt.paste_profile", "lyricsflow.opt.paste_profile.desc", () -> Ui.tr("lyricsflow.button.paste"), "paste", false, () -> {
                     String json = ConfigManager.importCode(Minecraft.getInstance().keyboardHandler.getClipboard());
                     if (json == null) {
                        pasteStatus = Ui.tr("lyricsflow.profile.paste_bad");
                        UiSounds.play(UiSounds.TOGGLE_OFF, 0.8F);
                     } else {
                        String namex = freeName("lyricsflow.profile.pasted");
                        c().profiles.put(namex, json);
                        ConfigManager.save();
                        pasteStatus = Ui.tr("lyricsflow.profile.paste_ok", namex);
                        UiSounds.play(UiSounds.TOGGLE_ON, 1.1F);
                     }

                     screen.rebuildSoon();
                  }
               )
            )
      );
      if (pasteStatus != null) {
         String ps = pasteStatus;
         l.add(new Ui.Info(() -> ps));
      }

      if (c().profiles.isEmpty()) {
         l.add(new Ui.Info(() -> Ui.tr("lyricsflow.info.no_profiles")));
      } else {
         for (Entry<String, String> e : new ArrayList<>(c().profiles.entrySet())) {
            String[] name = new String[]{e.getKey()};
            String[] draft = new String[]{e.getKey()};
            Ui.Card card = new Ui.Card();
            card.add(new Ui.TextField("lyricsflow.opt.profile_name", null, "lyricsflow.profile.name_hint", () -> draft[0], v -> {
               draft[0] = v;
               String nn = v.trim();
               if (!nn.isEmpty() && !nn.equals(name[0]) && !c().profiles.containsKey(nn)) {
                  renameProfile(name[0], nn);
                  name[0] = nn;
               }
            }));
            card.add(new Ui.Button("lyricsflow.opt.load_profile", null, () -> Ui.tr("lyricsflow.button.load"), "check", true, () -> {
               ConfigManager.applySnapshot(c().profiles.get(name[0]));
               WorldLyrics.clear();
               screen.rebuildSoon();
            }));
            card.add(
               new Ui.Button("lyricsflow.opt.copy_profile", "lyricsflow.opt.copy_profile.desc", () -> Ui.tr("lyricsflow.button.copy"), "copy", false, () -> {
                  String json = c().profiles.get(name[0]);
                  if (json != null) {
                     Minecraft.getInstance().keyboardHandler.setClipboard(ConfigManager.exportCode(json));
                     pasteStatus = Ui.tr("lyricsflow.profile.copied", name[0]);
                     screen.rebuildSoon();
                  }
               })
            );
            card.add(new Ui.Button("lyricsflow.opt.delete_profile", null, () -> Ui.tr("lyricsflow.button.delete"), "trash", false, () -> {
               c().profiles.remove(name[0]);
               ConfigManager.save();
               screen.rebuildSoon();
            }));
            l.add(card);
            l.add(new Ui.Spacer(4.0F));
         }
      }

      l.add(new Ui.Section("lyricsflow.section.reset"));
      l.add(
         new Ui.Card()
            .add(new Ui.Button("lyricsflow.opt.reset_all", "lyricsflow.opt.reset_all.desc", () -> Ui.tr("lyricsflow.button.reset"), "refresh", false, () -> {
               if (c().fpsBoost) {
                  FpsBoost.apply(false);
               }

               ConfigManager.reset();
               Core.resetConfig();
               SkyImage.reload();
               AutoPerf.reset();
               WorldLyrics.clear();
               Fonts.clearCaches();
               dev.jun.nosclient.lyricsflow.app.hud.Gfx.clearWidths();
               Ui.syncFont();
               note("lyricsflow.opt.reset_all.done");
               screen.rebuildSoon();
            }))
      );
   }

   private static String freeName(String key) {
      int n = c().profiles.size() + 1;
      String name = Ui.tr(key, n);

      while (c().profiles.containsKey(name)) {
         name = Ui.tr(key, ++n);
      }

      return name;
   }

   private static void renameProfile(String from, String to) {
      LinkedHashMap<String, String> m = new LinkedHashMap<>();

      for (Entry<String, String> e : c().profiles.entrySet()) {
         m.put(e.getKey().equals(from) ? to : e.getKey(), e.getValue());
      }

      c().profiles.clear();
      c().profiles.putAll(m);
      ConfigManager.save();
   }

   private static void perf(List<Ui.Widget> l, LyricsFlowScreen screen) {
      l.add(new Ui.Section("lyricsflow.section.perf_now"));
      l.add(
         new Ui.Card()
            .add(
               new Ui.Custom(
                  w -> 50.0F,
                  (g, x, y, w, h, mx, my, a) -> {
                     String num = Integer.toString(AutoPerf.fps());
                     Gfx.text(g, num, Ui.FB, x + 4.0F, y + 9.0F, 22.0F, Col.alpha(-856070, a));
                     Gfx.text(g, "FPS", Ui.F, x + 4.0F + Gfx.textWidth(num, Ui.FB, 22.0F) + 6.0F, y + 24.0F, 8.0F, Col.alpha(-6515020, a));
                     int lv = AutoPerf.level();
                     String st = !c().autoPerf
                        ? Ui.tr("lyricsflow.perf.auto_off")
                        : (lv == 0 ? Ui.tr("lyricsflow.perf.auto_full") : Ui.tr("lyricsflow.perf.auto_level", lv, 6));
                     String cost = Ui.tr("lyricsflow.perf.cost", String.format(Locale.ROOT, "%.1f", AutoPerf.modMs()));
                     Gfx.text(g, cost, Ui.F, x + w - 4.0F - Gfx.textWidth(cost, Ui.F, 7.2F), y + 15.0F, 7.2F, Col.alpha(-6515020, a));
                     Gfx.text(g, st, Ui.F, x + w - 4.0F - Gfx.textWidth(st, Ui.F, 7.2F), y + 27.0F, 7.2F, Col.alpha(-6515020, a));
                  },
                  null
               )
            )
      );
      l.add(new Ui.Section("lyricsflow.section.perf_auto"));
      String[] targets = new String[TARGETS.length];

      for (int i = 0; i < TARGETS.length; i++) {
         targets[i] = TARGETS[i] == 0 ? "lyricsflow.perf.target_auto" : TARGETS[i] + " FPS";
      }

      l.add(
         new Ui.Card()
            .add(new Ui.Toggle("lyricsflow.perf.auto", "lyricsflow.perf.auto.desc", () -> c().autoPerf, v -> {
               c().autoPerf = v;
               AutoPerf.reset();
            }))
            .add(new Ui.Choice("lyricsflow.perf.target", "lyricsflow.perf.target.desc", targets, () -> {
               for (int i = 0; i < TARGETS.length; i++) {
                  if (TARGETS[i] == c().targetFps) {
                     return i;
                  }
               }

               return 1;
            }, i -> {
               c().targetFps = TARGETS[i];
               AutoPerf.reset();
            }))
            .add(
               new Ui.Choice(
                  "lyricsflow.perf.quality",
                  null,
                  new String[]{"lyricsflow.perf.quality.0", "lyricsflow.perf.quality.1", "lyricsflow.perf.quality.2"},
                  () -> c().quality,
                  i -> {
                     c().quality = i;
                     sc().quality = i;
                  }
               )
            )
            .add(new Ui.Choice("lyricsflow.perf.profile", null, new String[]{"Chill", "PvP"}, () -> c().profile, i -> {
               Profiles.set(i);
               note(i == 0 ? "lyricsflow.perf.profile.chill" : "lyricsflow.perf.profile.pvp");
               screen.rebuildSoon();
            }))
            .add(new Ui.Toggle("lyricsflow.perf.boost", "lyricsflow.perf.boost.desc", () -> c().fpsBoost, FpsBoost::apply))
      );
      Options o = Minecraft.getInstance().options;
      l.add(new Ui.Section("lyricsflow.section.perf_game"));
      l.add(
         new Ui.Card()
            .add(new Ui.Slider("lyricsflow.perf.render_distance", 2.0, 32.0, 1.0, () -> ((Integer)o.renderDistance().get()).intValue(), v -> {
               o.renderDistance().set((int)Math.round(v));
               o.save();
            }, v -> Math.round(v) + " " + Ui.tr("lyricsflow.perf.chunks")))
            .add(new Ui.Slider("lyricsflow.perf.max_fps", 30.0, 260.0, 10.0, () -> ((Integer)o.framerateLimit().get()).intValue(), v -> {
               o.framerateLimit().set((int)Math.round(v));
               o.save();
            }, v -> v >= 260.0 ? Ui.tr("lyricsflow.perf.unlimited") : Math.round(v) + " FPS"))
            .add(
               new Ui.Choice(
                  "lyricsflow.perf.clouds",
                  null,
                  new String[]{"lyricsflow.perf.off", "lyricsflow.perf.fast", "lyricsflow.perf.fancy"},
                  () -> ((CloudStatus)o.cloudStatus().get()).ordinal(),
                  i -> {
                     o.cloudStatus().set(CloudStatus.values()[i]);
                     o.save();
                  }
               )
            )
            .add(
               new Ui.Choice(
                  "lyricsflow.perf.particles",
                  null,
                  new String[]{"lyricsflow.perf.all", "lyricsflow.perf.decreased", "lyricsflow.perf.minimal"},
                  () -> ((ParticleStatus)o.particles().get()).ordinal(),
                  i -> {
                     o.particles().set(ParticleStatus.values()[i]);
                     o.save();
                  }
               )
            )
            .add(new Ui.Toggle("lyricsflow.perf.shadows", null, () -> (Boolean)o.entityShadows().get(), v -> {
               o.entityShadows().set(v);
               o.save();
            }))
            .add(new Ui.Toggle("lyricsflow.perf.vsync", "lyricsflow.perf.vsync.desc", () -> (Boolean)o.enableVsync().get(), v -> {
               o.enableVsync().set(v);
               o.save();
            }))
      );
      l.add(new Ui.Section("lyricsflow.section.perf_effects"));
      l.add(
         new Ui.Card()
            .add(new Ui.Toggle("lyricsflow.perf.sky_color", null, () -> sc().fxSkyColor, v -> {
               c().fxSkyColor = v;
               sc().fxSkyColor = v;
            }))
            .add(new Ui.Toggle("lyricsflow.perf.weather", null, () -> sc().fxWeather, v -> {
               c().fxWeather = v;
               sc().fxWeather = v;
            }))
            .add(new Ui.Toggle("lyricsflow.perf.aurora", null, () -> sc().fxAurora, v -> {
               c().fxAurora = v;
               sc().fxAurora = v;
            }))
            .add(new Ui.Toggle("lyricsflow.perf.stars", null, () -> sc().fxStars, v -> {
               c().fxStars = v;
               sc().fxStars = v;
            }))
            .add(new Ui.Toggle("lyricsflow.perf.glow", "lyricsflow.perf.glow.desc", () -> c().glow, v -> c().glow = v))
            .add(new Ui.Button("lyricsflow.perf.all_off", null, () -> Ui.tr("lyricsflow.button.apply"), null, false, () -> {
               Profiles.setAll(c(), false);
               ConfigManager.save();
               note("lyricsflow.perf.all_off.done");
               screen.rebuildSoon();
            }))
            .add(new Ui.Button("lyricsflow.perf.all_on", null, () -> Ui.tr("lyricsflow.button.apply"), null, true, () -> {
               Profiles.setAll(c(), true);
               ConfigManager.save();
               note("lyricsflow.perf.all_on.done");
               screen.rebuildSoon();
            }))
      );
   }

   private static void note(String key) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gui != null) {
         mc.gui.setOverlayMessage(Component.literal(Ui.tr(key)), false);
      }
   }
}
