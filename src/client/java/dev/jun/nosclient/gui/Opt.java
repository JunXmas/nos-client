package dev.jun.nosclient.gui;

import dev.jun.nosclient.Mods;
import dev.jun.nosclient.scene.SceneConfig;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
record Opt(String type, String field, String label, String[] choices, double min, double max, String unit) {
    private static final Map<String, Field> FIELD_CACHE = new HashMap<>();
    static final Map<String, List<Opt>> BY_MOD = Map.ofEntries(
        Map.entry("coords", List.of(chk("coordsBiome", "opt.coords.biome"), seg("coordsDecimal", "opt.coords.fmt", "opt.coords.int", "opt.coords.dec"))),
        Map.entry("direction", List.of(chk("directionDeg", "opt.direction.deg"))),
        Map.entry("day", List.of(chk("dayTime", "opt.day.time"))),
        Map.entry("invhud", List.of(chk("invhudBg", "opt.invhud.bg"))),
        Map.entry("fps", List.of(chk("fpsLow", "opt.fps.low"))),
        Map.entry("effects", List.of(chk("fxTime", "opt.fx.time"))),
        Map.entry("cps", List.of(chk("cpsRight", "opt.cps.right"))),
        Map.entry("shulker", List.of(chk("shulkerColor", "opt.shulker.color"))),
        Map.entry(
            "armor",
            List.of(
                seg("armorFmt", "opt.armor.fmt", "opt.armor.num", "opt.armor.pct", "opt.armor.bar"),
                chk("armorHand", "opt.armor.hand"),
                chk("armorWarn", "opt.armor.warn")
            )
        ),
        Map.entry("waypoint", List.of(key("nosclient.key.waypoint", "opt.key"))),
        Map.entry(
            "minimap",
            List.of(
                seg("minimapZoom", "opt.minimap.zoom", "½×", "1×", "2×", "4×"),
                rng("minimapSize", "opt.minimap.size", 48.0, 160.0, " px"),
                chk("minimapRotate", "opt.minimap.rot"),
                chk("minimapCoords", "opt.minimap.coords"),
                chk("minimapBiome", "opt.minimap.biome"),
                chk("minimapWaypoints", "opt.minimap.waypoints"),
                key("nosclient.key.worldmap", "opt.minimap.mapkey")
            )
        ),
        Map.entry("sky", List.of(rng("skyHour", "opt.sky.hour", 0.0, 24.0, " h"), rng("skyStars", "opt.sky.stars", 0.0, 1.0, ""))),
        Map.entry("weather", List.of(seg("weatherMode", "opt.weather.mode", "opt.weather.clear", "opt.weather.rain", "opt.weather.storm", "opt.weather.snow"))),
        Map.entry(
            "lyrics",
            List.of(
                seg("lyricsStyle", "opt.lyrics.style", "opt.lyrics.style.poster", "opt.lyrics.style.thin"),
                chk("lyricsFollow", "opt.lyrics.follow"),
                chk("lyricsAurora", "opt.lyrics.aurora"),
                chk("lyricsCinematic", "opt.lyrics.cinematic"),
                chk("lyricsBlendSky", "opt.lyrics.blend"),
                rng("lyricsHeight", "opt.lyrics.height", 1.0, 2.0, "×"),
                rng("lyricsGlow", "opt.lyrics.glow", 0.0, 2.5, "×"),
                rng("lyricsWidth", "opt.lyrics.width", 1.0, 1.8, "%"),
                rng("lyricsTracking", "opt.lyrics.tracking", 0.0, 2.0, ""),
                chk("lyricsAutoFetch", "opt.lyrics.auto"),
                chk("lyricsNext", "opt.lyrics.next"),
                seg("lyricsMotion", "opt.lyrics.motion", "opt.lyrics.motion.slide", "opt.lyrics.motion.fade", "opt.lyrics.motion.none"),
                rng("lyricsDuration", "opt.lyrics.duration", 180.0, 1200.0, " ms"),
                rng("lyricsBearing", "opt.lyrics.bearing", 0.0, 360.0, "°"),
                rng("lyricsElevation", "opt.lyrics.elevation", 15.0, 80.0, "°"),
                rng("lyricsSize", "opt.lyrics.size", 0.5, 2.0, "×"),
                rng("lyricsDelay", "opt.lyrics.delay", -5000.0, 5000.0, " ms")
            )
        ),
        Map.entry("blockinfo", List.of(chk("blockId", "opt.block.id"), chk("blockTool", "opt.block.tool"), chk("blockHp", "opt.block.hp"))),
        Map.entry(
            "zoom", List.of(key("nosclient.key.zoom", "opt.key"), seg("zoomLevel", "opt.zoom.lv", "2×", "4×", "8×"), chk("zoomScroll", "opt.zoom.scroll"))
        ),
        Map.entry("light", List.of(rng("gamma", "opt.light.gamma", 0.0, 10.0, "%"))),
        Map.entry("keys", List.of(chk("keysMouse", "opt.keys.mouse"), chk("keysCps", "opt.keys.cps"), chk("keysSpace", "opt.keys.space"))),
        Map.entry("sprint", List.of(chk("sprintSneak", "opt.sprint.sneak"), chk("sprintShow", "opt.sprint.show"))),
        Map.entry("freelook", List.of(key("nosclient.key.freelook", "opt.key"), seg("freelookToggle", "opt.freelook.mode", "opt.hold", "opt.toggle"))),
        Map.entry("dfps", List.of(rng("dfpsBg", "opt.dfps.bg", 1.0, 60.0, " FPS"), chk("dfpsMin", "opt.dfps.min"), chk("dfpsAfk", "opt.dfps.afk"))),
        Map.entry("mp", List.of(chk("mpOffline", "opt.mp.offline"), chk("mpPvp", "opt.mp.pvp")))
    );
    private static final SceneConfig DEF = new SceneConfig();

    static Opt chk(String field, String label) {
        return new Opt("chk", field, label, null, 0.0, 0.0, null);
    }

    static Opt seg(String field, String label, String... choices) {
        return new Opt("seg", field, label, choices, 0.0, 0.0, null);
    }

    static Opt rng(String field, String label, double min, double max, String unit) {
        return new Opt("rng", field, label, null, min, max, unit);
    }

    static Opt key(String keyName, String label) {
        return new Opt("key", keyName, label, null, 0.0, 0.0, null);
    }

    private Field f() {
        return FIELD_CACHE.computeIfAbsent(this.field, name -> {
            try {
                return SceneConfig.class.getField(name);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("thiếu field " + name, e);
            }
        });
    }

    boolean bool() {
        try {
            return this.f().getBoolean(SceneConfig.INSTANCE);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    double num() {
        try {
            Field ff = this.f();
            return ff.getType() == int.class
                ? ff.getInt(SceneConfig.INSTANCE)
                : (ff.getType() == boolean.class ? (ff.getBoolean(SceneConfig.INSTANCE) ? 1 : 0) : ff.getDouble(SceneConfig.INSTANCE));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    void set(double v) {
        try {
            Field ff = this.f();
            if (ff.getType() == int.class) {
                ff.setInt(SceneConfig.INSTANCE, (int)Math.round(v));
            } else if (ff.getType() == boolean.class) {
                ff.setBoolean(SceneConfig.INSTANCE, v != 0.0);
            } else {
                ff.setDouble(SceneConfig.INSTANCE, v);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static String field(Mods.Mod m) {
        return m.id().equals("light") ? "dynlight" : m.id();
    }

    static boolean defOn(String field) {
        try {
            return SceneConfig.class.getField(field).getBoolean(DEF);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static void reset(String field) {
        try {
            Field ff = SceneConfig.class.getField(field);
            ff.set(SceneConfig.INSTANCE, ff.get(DEF));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    int segIndex() {
        double v = this.num();
        if (this.field.equals("zoomLevel")) {
            return v <= 2.0 ? 0 : (v <= 4.0 ? 1 : 2);
        } else {
            return (int)v;
        }
    }

    void setSeg(int i) {
        if (this.field.equals("zoomLevel")) {
            this.set(new int[]{2, 4, 8}[i]);
        } else {
            this.set(i);
        }
    }
}
