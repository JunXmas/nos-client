package dev.jun.nosclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.jun.nosclient.ClientResources;

import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class MusicSettings {
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile MusicConfig config = new MusicConfig();
    private static final Set<String> DROP =
            Set.of("connection", "spotifyClientId", "spotifyRefreshToken");

    private MusicSettings() {}

    public static MusicConfig get() {
        return config;
    }

    public static Path dir() {
        return ClientSettings.directory();
    }

    private static Path legacyDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("lyricsflow");
    }

    private static Path file() {
        return legacyDir().resolve("config.json");
    }

    private static boolean isColor(String k) {
        return k.endsWith("Color") && !k.equals("twoColor")
                || k.equals("customMain")
                || k.equals("customA")
                || k.equals("customB");
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
            if (isColor(e.getKey())
                    && e.getValue().isJsonPrimitive()
                    && e.getValue().getAsJsonPrimitive().isString()) {
                String s = e.getValue().getAsString().trim();
                int col = 0;
                if (s.startsWith("#")) {
                    try {
                        long l = Long.parseLong(s.substring(1), 16);
                        col = s.length() <= 7 ? (int) (4278190080L | l) : (int) l;
                    } catch (NumberFormatException var8) {
                    }
                }

                flat.addProperty(e.getKey(), col);
            }
        }

        return flat;
    }

    public static void load() {
        config =
                ClientSettings.current()
                        .section("music", MusicConfig.class, MusicSettings::loadLegacy);
        if (config.source == null) config.source = MusicConfig.Source.ANY;
        if (config.profiles == null) config.profiles = new LinkedHashMap<>();
        if (config.songOffsets == null) config.songOffsets = new LinkedHashMap<>();
        if (!ClientSettings.current().contains("music") && config.syncOffsetMs == 0)
            config.syncOffsetMs = LegacyConfig.lyricsOffset();
        if (!ClientSettings.current().contains("music")) {
            var scene = LegacyConfig.scene(FabricLoader.getInstance().getConfigDir());
            if (scene.containsKey("lyrics"))
                config.showLyrics = Boolean.parseBoolean(scene.getProperty("lyrics"));
        }
        config.clamp();
    }

    private static MusicConfig loadLegacy() {
        Path path = file();
        if (!Files.isRegularFile(path)) return new MusicConfig();
        try {
            JsonObject flat =
                    flatten(
                            JsonParser.parseString(dev.jun.nosclient.storage.ClientFiles.read(path))
                                    .getAsJsonObject());
            MusicConfig c = GSON.fromJson(flat, MusicConfig.class);
            if (c == null) return new MusicConfig();
            int version = flat.has("configVersion") ? flat.get("configVersion").getAsInt() : 1;
            if (version < 4 || c.lyricsStyle == null)
                c.lyricsStyle = MusicConfig.LyricsStyle.FLOATING;
            if (version < 6) c.songChime = false;
            if (version < 11) c.twoColorStrength = c.twoColor ? .6F : 0;
            if (version < 10) c.customHotbar = false;
            if (version < 12) c.followView = false;
            if (version < 14) c.syncOffsetMs = 0;
            if (version < 15) c.font = "poppins";
            if (version < 16) {
                c.theme = "lavender";
                c.customTheme = false;
                c.accentColor = 0;
                c.barColor = 0;
            }
            if (version < 17) {
                c.allCaps = true;
                c.twoColorStrength = 0;
            }
            if (version < 18) {
                c.font = "poppins";
                c.enterAnim = MusicConfig.EnterAnim.NEON;
            }
            c.font = LegacyConfig.fontId(c.font);
            c.configVersion = 18;
            return c;
        } catch (Exception error) {
            ClientResources.LOG.warn("Could not migrate legacy music settings", error);
            return new MusicConfig();
        }
    }

    public static void save() {
        config.clamp();
        ClientSettings.current().replace("music", config);
        ClientSettings.current().save();
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
        return "NS1:"
                + Base64.getEncoder()
                        .encodeToString(deflate(snapshotJson.getBytes(StandardCharsets.UTF_8)));
    }

    public static String importCode(String code) {
        if (code == null || code.length() > 524288) {
            return null;
        }

        code = code.trim();

        try {
            String json;
            if (code.startsWith("NS1:") || code.startsWith("LF1:")) {
                json =
                        new String(
                                inflate(
                                        Base64.getDecoder()
                                                .decode(code.substring(4).replaceAll("\\s", ""))),
                                StandardCharsets.UTF_8);
            } else {
                if (!code.startsWith("{")) {
                    return null;
                }

                json = code;
            }

            JsonObject flat = flatten(JsonParser.parseString(json).getAsJsonObject());
            flat.remove("profiles");
            GSON.fromJson(flat, MusicConfig.class);
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
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (out.size() + count > 262144 || count == 0 && !inflater.finished())
                    throw new DataFormatException("Config is truncated or exceeds size limit");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }

    public static void applySnapshot(String json) {
        try {
            MusicConfig c =
                    GSON.fromJson(
                            flatten(JsonParser.parseString(json).getAsJsonObject()),
                            MusicConfig.class);
            c.font = LegacyConfig.fontId(c.font);
            c.profiles = config.profiles;
            c.configVersion = 18;
            config = c;
            save();
        } catch (Exception e) {
            ClientResources.LOG.warn("Could not apply profile", e);
        }
    }

    public static void reset() {
        config = new MusicConfig();
        save();
    }

    public static Gson gson() {
        return GSON;
    }
}
