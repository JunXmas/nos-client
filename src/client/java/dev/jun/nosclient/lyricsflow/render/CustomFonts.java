package dev.jun.nosclient.lyricsflow.render;

import dev.jun.nosclient.lyricsflow.LyricsFlow;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources.PathResourcesSupplier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.Pack.Position;

public final class CustomFonts {
   private static final List<Fonts.LyricFont> FONTS = new ArrayList<>();
   private static final Map<String, Float> SHIFT = new HashMap<>();

   private CustomFonts() {
   }

   public static Path folder() {
      Path p = ConfigManager.dir().resolve("fonts");

      try {
         Files.createDirectories(p);
         Path readme = p.resolve("README.txt");
         if (!Files.exists(readme)) {
            Files.writeString(
               readme,
               "Put .ttf font files in this folder, then press \"Reload fonts\" in the LyricsFlow menu (Style tab).\nFree fonts: https://fonts.google.com  -  https://www.dafont.com\nOnly TrueType (.ttf) works. Some .otf fonts are TrueType inside: rename them to .ttf to try.\n",
               StandardCharsets.UTF_8
            );
         }
      } catch (IOException var2) {
      }

      return p;
   }

   private static Path packDir() {
      return ConfigManager.dir().resolve("fontpack");
   }

   public static synchronized List<Fonts.LyricFont> list() {
      return new ArrayList<>(FONTS);
   }

   public static synchronized Float shift(String id) {
      return SHIFT.get(id);
   }

   private static String slug(String name) {
      String s = name.toLowerCase(Locale.ROOT).replaceAll("\\.ttf$", "").replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
      return s.isEmpty() ? "font" : s;
   }

   public static synchronized void rebuild() {
      FONTS.clear();
      SHIFT.clear();
      Path fonts = folder();
      Path pack = packDir();

      try {
         deleteTree(pack);
         Path fontDir = pack.resolve("assets/lyricsflow/font");
         Path ttfDir = fontDir.resolve("custom");
         Files.createDirectories(ttfDir);
         Files.writeString(
            pack.resolve("pack.mcmeta"),
            "{\"pack\": {\"description\": \"LyricsFlow custom fonts\", \"min_format\": 75, \"max_format\": 999}}\n",
            StandardCharsets.UTF_8
         );

         List<Path> files;
         try (Stream<Path> s = Files.list(fonts)) {
            files = s.filter(fx -> fx.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ttf"))
               .sorted(Comparator.comparing(fx -> fx.getFileName().toString().toLowerCase(Locale.ROOT)))
               .toList();
         }

         Iterator var26 = files.iterator();

         label66:
         while (true) {
            Path f;
            String id;
            String display;
            float shift;
            while (true) {
               if (!var26.hasNext()) {
                  break label66;
               }

               f = (Path)var26.next();
               String file = f.getFileName().toString();
               id = "custom_" + slug(file);
               if (!SHIFT.containsKey(id)) {
                  shift = 0.35F;

                  try {
                     Font awt = Font.createFont(0, f.toFile()).deriveFont(16.0F);
                     display = awt.getFamily(Locale.ROOT);
                     String style = awt.getFontName(Locale.ROOT);
                     if (style != null && !style.equals(display) && style.length() < 40) {
                        display = style;
                     }

                     FontRenderContext frc = new FontRenderContext(null, true, true);
                     Rectangle2D cap = awt.createGlyphVector(frc, "H").getVisualBounds();
                     Rectangle2D xh = awt.createGlyphVector(frc, "x").getVisualBounds();
                     double capH = -cap.getY();
                     double xH = -xh.getY();
                     if (capH > 1.0 && xH > 1.0) {
                        double center = 7.0 - (capH + xH) / 4.0;
                        shift = (float)((8.0 - center) / 16.0);
                     }
                     break;
                  } catch (Throwable t) {
                     LyricsFlow.LOG.warn("Skipping font {} (not a TrueType font?)", file);
                  }
               }
            }

            Files.copy(f, ttfDir.resolve(id + ".ttf"));
            Files.writeString(
               fontDir.resolve(id + ".json"),
               "{\"providers\": [\n  {\"type\": \"ttf\", \"file\": \"lyricsflow:custom/%s.ttf\", \"size\": 16.0, \"oversample\": 8.0, \"shift\": [0.0, 0.0]},\n  {\"type\": \"reference\", \"id\": \"minecraft:include/default\"},\n  {\"type\": \"reference\", \"id\": \"minecraft:include/unifont\"}\n]}\n"
                  .formatted(id),
               StandardCharsets.UTF_8
            );
            FONTS.add(new Fonts.LyricFont(id, display));
            SHIFT.put(id, Math.max(0.1F, Math.min(0.6F, shift)));
         }
      } catch (Throwable t) {
         LyricsFlow.LOG.warn("Could not build the custom font pack", t);
      }

      Fonts.clearCaches();
   }

   private static void deleteTree(Path p) throws IOException {
      if (Files.exists(p)) {
         try (Stream<Path> s = Files.walk(p)) {
            for (Path q : s.sorted(Comparator.reverseOrder()).toList()) {
               Files.deleteIfExists(q);
            }
         }
      }
   }

   public static RepositorySource source() {
      return consumer -> {
         rebuild();
         PackLocationInfo info = new PackLocationInfo("lyricsflow_fonts", Component.literal("LyricsFlow fonts"), PackSource.BUILT_IN, Optional.empty());
         Pack p = Pack.readMetaAndCreate(
            info, new PathResourcesSupplier(packDir()), PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Position.TOP, false)
         );
         if (p != null) {
            consumer.accept(p);
         }
      };
   }
}
