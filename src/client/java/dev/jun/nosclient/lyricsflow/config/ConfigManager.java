package dev.jun.nosclient.lyricsflow.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.jun.nosclient.lyricsflow.LyricsFlow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
   public static boolean moveKeys;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private static LFConfig config = new LFConfig();
   private static final Map<String, String[]> GROUPS = new LinkedHashMap<>();
   private static final Set<String> DROP = Set.of("connection", "spotifyClientId", "spotifyRefreshToken");

   private ConfigManager() {
   }

   public static LFConfig get() {
      return config;
   }

   public static Path dir() {
      Path p = FabricLoader.getInstance().getConfigDir().resolve("lyricsflow");

      try {
         Files.createDirectories(p);
      } catch (IOException var2) {
      }

      return p;
   }

   private static Path file() {
      return dir().resolve("config.json");
   }

   private static boolean isColor(String k) {
      return k.endsWith("Color") && !k.equals("twoColor") || k.equals("customMain") || k.equals("customA") || k.equals("customB");
   }

   static JsonObject toPretty(LFConfig c) {
      JsonObject flat = GSON.toJsonTree(c).getAsJsonObject();
      JsonObject out = new JsonObject();
      out.addProperty("_info", "LyricsFlow v2.1-beta - change these in game with the menu key. Colors: #RRGGBB or \"auto\".");
      out.add("configVersion", flat.remove("configVersion"));
      Set<String> used = new HashSet<>();

      for (Entry<String, String[]> g : GROUPS.entrySet()) {
         JsonObject o = new JsonObject();

         for (String k : g.getValue()) {
            JsonElement v = flat.get(k);
            if (v != null) {
               used.add(k);
               if (isColor(k) && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                  int col = v.getAsInt();
                  v = new JsonPrimitive(col == 0 ? "auto" : (col >>> 24 == 255 ? String.format("#%06X", col & 16777215) : String.format("#%08X", col)));
               }

               o.add(k, v);
            }
         }

         out.add(g.getKey(), o);
      }

      JsonObject other = new JsonObject();

      for (Entry<String, JsonElement> e : flat.entrySet()) {
         if (!used.contains(e.getKey()) && !DROP.contains(e.getKey()) && !e.getKey().equals("profiles")) {
            other.add(e.getKey(), e.getValue());
         }
      }

      if (other.size() > 0) {
         out.add("other", other);
      }

      out.add("profiles", flat.get("profiles"));
      return out;
   }

   static JsonObject flatten(JsonObject in) {
      JsonObject flat = new JsonObject();

      for (Entry<String, JsonElement> e : in.entrySet()) {
         String k = e.getKey();
         JsonElement v = e.getValue();
         if (v.isJsonObject() && !k.equals("profiles")) {
            for (Entry<String, JsonElement> e2 : v.getAsJsonObject().entrySet()) {
               flat.add(e2.getKey(), e2.getValue());
            }
         } else if (!k.startsWith("_")) {
            flat.add(k, v);
         }
      }

      for (Entry<String, JsonElement> e : new ArrayList<>(flat.entrySet())) {
         if (isColor(e.getKey()) && e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
            String s = e.getValue().getAsString().trim();
            int col = 0;
            if (s.startsWith("#")) {
               try {
                  long l = Long.parseLong(s.substring(1), 16);
                  col = s.length() <= 7 ? (int)(4278190080L | l) : (int)l;
               } catch (NumberFormatException var8) {
               }
            }

            flat.addProperty(e.getKey(), col);
         }
      }

      return flat;
   }

   public static void load() {
      Path f = file();
      if (Files.exists(f)) {
         try {
            JsonObject flat = flatten(JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
            LFConfig c = GSON.fromJson(flat, LFConfig.class);
            if (c != null) {
               if (c.profiles == null) {
                  c.profiles = new LinkedHashMap<>();
               }

               if (c.source == null || c.source == LFConfig.Source.ANY) {
                  c.source = LFConfig.Source.SPOTIFY;
               }

               int ver = flat.has("configVersion") ? flat.get("configVersion").getAsInt() : 1;
               if (ver < 4) {
                  c.lyricsStyle = LFConfig.LyricsStyle.FLOATING;
               }

               if (ver < 5) {
                  c.soundStyle = 0;
               }

               if (!flat.has("skyPreset")) {
                  c.skyPreset = "watercolor";
               }

               if ("candy".equals(c.skyPreset)) {
                  c.skyPreset = "lilac";
               }

               if (!flat.has("stars")) {
                  c.stars = true;
               }

               if (!flat.has("motes")) {
                  c.motes = true;
               }

               if (!flat.has("streaks")) {
                  c.streaks = true;
               }

               if (ver < 6) {
                  c.songChime = false;
               }

               if (ver < 7) {
                  moveKeys = true;
               }

               if (ver < 8) {
                  c.stars = true;
                  c.streaks = true;
               }

               if (ver < 9) {
                  c.beatGlow = false;
               }

               if (ver < 11) {
                  c.twoColorStrength = c.twoColor ? 0.6F : 0.0F;
               }

               if (ver < 10) {
                  c.customHotbar = false;
               }

               c.viewLock = false;
               if (ver < 13) {
                  c.customWeather = 0;
               }

               if (ver < 12) {
                  c.followView = false;
               }

               c.randomPosition = true;
               if (ver < 14) {
                  c.syncOffsetMs = 0;
               }

               if (c.lyricsStyle == null) {
                  c.lyricsStyle = LFConfig.LyricsStyle.FLOATING;
               }

               if (ver < 15) {
                  c.font = "poppins";
               }

               if (ver < 16) {
                  c.theme = "lavender";
                  c.customTheme = false;
                  c.accentColor = 0;
                  c.barColor = 0;
               }

               if (ver < 17) {
                  c.allCaps = true;
                  c.twoColorStrength = 0.0F;
               }

               if (ver < 18) {
                  c.font = "poppins";
                  c.enterAnim = LFConfig.EnterAnim.NEON;
               }

               c.configVersion = 18;
               config = c;
            }
         } catch (Exception e) {
            LyricsFlow.LOG.warn("Could not read config, using defaults", e);
         }
      }

      save();
   }

   public static void save() {
      String json = GSON.toJson(toPretty(config));

      try {
         Path tmp = file().resolveSibling("config.json.tmp");
         Files.writeString(tmp, json, StandardCharsets.UTF_8);
         Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (Exception e) {
         try {
            Files.writeString(file(), json, StandardCharsets.UTF_8);
         } catch (IOException var3) {
         }
      }
   }

   public static String snapshot() {
      JsonObject o = GSON.toJsonTree(config).getAsJsonObject();
      o.remove("profiles");

      for (String k : DROP) {
         o.remove(k);
      }

      return new Gson().toJson(o);
   }

   public static String exportCode(String snapshotJson) {
      return "LF1:" + Base64.getEncoder().encodeToString(deflate(snapshotJson.getBytes(StandardCharsets.UTF_8)));
   }

   public static String importCode(String code) {
      if (code == null) {
         return null;
      }

      code = code.trim();

      try {
         String json;
         if (code.startsWith("LF1:")) {
            json = new String(inflate(Base64.getDecoder().decode(code.substring(4).replaceAll("\\s", ""))), StandardCharsets.UTF_8);
         } else {
            if (!code.startsWith("{")) {
               return null;
            }

            json = code;
         }

         JsonObject flat = flatten(JsonParser.parseString(json).getAsJsonObject());
         flat.remove("profiles");
         GSON.fromJson(flat, LFConfig.class);
         return new Gson().toJson(flat);
      } catch (Exception e) {
         return null;
      }
   }

   private static byte[] deflate(byte[] in) {
      Deflater d = new Deflater(9);
      d.setInput(in);
      d.finish();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[1024];

      while (!d.finished()) {
         out.write(buf, 0, d.deflate(buf));
      }

      d.end();
      return out.toByteArray();
   }

   private static byte[] inflate(byte[] in) throws DataFormatException {
      Inflater i = new Inflater();
      i.setInput(in);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[1024];

      while (!i.finished()) {
         int n = i.inflate(buf);
         if (n == 0 && (i.needsInput() || i.needsDictionary())) {
            break;
         }

         out.write(buf, 0, n);
      }

      i.end();
      return out.toByteArray();
   }

   public static void applySnapshot(String json) {
      try {
         LFConfig c = GSON.fromJson(flatten(JsonParser.parseString(json).getAsJsonObject()), LFConfig.class);
         c.profiles = config.profiles;
         c.configVersion = 18;
         config = c;
         save();
      } catch (Exception e) {
         LyricsFlow.LOG.warn("Could not apply profile", e);
      }
   }

   public static void reset() {
      config = new LFConfig();
      save();
   }

   public static Gson gson() {
      return GSON;
   }

   static {
      GROUPS.put("general", new String[]{"enabled", "uiSounds", "uiVolume", "soundStyle", "songChime", "mediaKeys"});
      GROUPS.put("music", new String[]{"source", "syncOffsetMs"});
      GROUPS.put(
         "lyrics",
         new String[]{
            "showLyrics",
            "lyricsStyle",
            "sentenceMode",
            "allCaps",
            "floatHeight",
            "skyWords",
            "randomPosition",
            "followView",
            "viewLock",
            "showUnderground",
            "musicNotes",
            "lyricsScale",
            "lyricsDistance",
            "skyDistance",
            "textShadow",
            "shadowColor",
            "textOpacity",
            "maxLines",
            "varySizes",
            "arcLayout",
            "enterAnim",
            "exitAnim",
            "animSpeed"
         }
      );
      GROUPS.put(
         "style",
         new String[]{
            "font",
            "textStyle",
            "theme",
            "customTheme",
            "singleColor",
            "customMain",
            "customA",
            "customB",
            "twoColor",
            "twoColorStrength",
            "twoColorColor",
            "glow",
            "beatGlow",
            "accentColor"
         }
      );
      GROUPS.put(
         "sky",
         new String[]{
            "skyPreset",
            "skyColor",
            "plainSkyColor",
            "skyAlways",
            "chroma",
            "chromaColor",
            "stars",
            "starDensity",
            "starColor",
            "motes",
            "moteDensity",
            "moteColor",
            "streaks",
            "streakDensity",
            "streakColor",
            "customTop",
            "customHorizon",
            "customAurora1",
            "customAurora2",
            "customAurora",
            "customStars",
            "customMoon",
            "customShooting",
            "customWeather",
            "weatherRain",
            "weatherSnow",
            "weatherEmbers"
         }
      );
      GROUPS.put(
         "topBar",
         new String[]{
            "showTopBar",
            "customHotbar",
            "hotbarColor",
            "hotbarBarColor",
            "topBarStyle",
            "topBarFont",
            "topBarScale",
            "barColor",
            "clock",
            "ping",
            "fps",
            "showOnlyWhenPlaying",
            "hideWhenStopped",
            "dimWhenIdle"
         }
      );
      GROUPS.put(
         "performance",
         new String[]{"autoPerf", "targetFps", "quality", "fxSkyColor", "fxWeather", "fxAurora", "fxStars", "lastSky"}
      );
      GROUPS.put("positions", new String[]{"topBarX", "topBarY"});
   }
}
