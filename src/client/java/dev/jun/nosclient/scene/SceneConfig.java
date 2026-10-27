package dev.jun.nosclient.scene;

import dev.jun.nosclient.storage.ClientFiles;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class SceneConfig {
    private static final Logger LOG = LoggerFactory.getLogger("nosclient");
    public static final SceneConfig INSTANCE = load(file());
    public boolean blossom = true;
    public boolean cane = true;
    public boolean fx = true;
    public boolean menuAurora = false;
    public int uiRevision = 2;
    public double tod = 47.0;
    public boolean coords = true;
    public boolean direction = true;
    public boolean day = false;
    public boolean fps = true;
    public boolean ping = false;
    public boolean cps = false;
    public boolean invhud = false;
    public boolean effects = true;
    public boolean recipe = true;
    public boolean shulker = true;
    public boolean armor = true;
    public boolean animations = true;
    public int themeAccent = 10931891;
    public int themeSurface = 1187104;
    public int themeText = 15856621;
    public int hudStyle = 0;
    public boolean sky = false;
    public boolean weather = false;
    public boolean lyrics = false;
    public double skyHour = 18.0;
    public double skyStars = 0.7;
    public int weatherMode = 0;
    public boolean lyricsAutoFetch = true;
    public boolean lyricsNext = true;
    public int lyricsDelay = 0;
    public int lyricsBearing = 0;
    public int lyricsElevation = 40;
    public double lyricsSize = 1.0;
    public int lyricsMotion = 0;
    public int lyricsDuration = 480;
    public boolean waypoint = false;
    public boolean minimap = false;
    public boolean blockinfo = true;
    public boolean zoom = true;
    public boolean dynlight = true;
    public int minimapZoom = 1;
    public int minimapSize = 96;
    public double gamma = -1.0;
    public boolean keys = true;
    public boolean sprint = true;
    public boolean freelook = false;
    public boolean sprintSneak = false;
    public int zoomLevel = 4;
    public boolean dfps = true;
    public boolean mp = true;
    public int dfpsBg = 15;
    public boolean mpOffline = true;
    public boolean mpPvp = true;
    public boolean coordsBiome = true;
    public boolean coordsDecimal = false;
    public boolean directionDeg = true;
    public boolean dayTime = true;
    public boolean fpsLow = true;
    public boolean cpsRight = true;
    public boolean invhudBg = true;
    public boolean fxTime = true;
    public boolean shulkerColor = true;
    public boolean minimapCoords = true;
    public boolean minimapBiome = true;
    public boolean minimapWaypoints = true;
    public boolean mapGrid = false;
    public int armorFmt = 0;
    public boolean armorHand = true;
    public boolean armorWarn = true;
    public boolean blockId = true;
    public boolean blockTool = true;
    public boolean blockHp = true;
    public boolean minimapRotate = false;
    public boolean zoomScroll = true;
    public boolean keysMouse = true;
    public boolean keysCps = true;
    public boolean keysSpace = true;
    public boolean sprintShow = true;
    public boolean freelookToggle = false;
    public boolean dfpsMin = true;
    public boolean dfpsAfk = false;
    public boolean intro = true;
    public double hudScale = 0.8;
    public double hudBg = 0.45;
    public boolean hudIdleFade = true;
    public boolean hudReadable = true;
    public boolean hudShadow = false;
    public double lyricsWidth = 1.18;
    public double lyricsTracking = 0.6;
    public double lyricsHeight = 1.2;
    public double lyricsGlow = 1.4;
    public boolean lyricsCinematic = true;
    public boolean lyricsBlendSky = true;
    public boolean lyricsFollow = true;
    /** 0 = Poster (chữ khối in hoa, lòng đảo màu cảnh), 1 = Thanh mảnh (bản cũ). */
    public int lyricsStyle = 0;
    /** Cực quang nhịp theo lời nhạc trên trời đêm. */
    public boolean lyricsAurora = true;
    public int lyricsInk = 0xF826BE;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("nosclient.properties");
    }

    static String key(Field f) {
        return switch (f.getName()) {
            case "fx" -> "shader";
            case "coords", "direction", "day", "fps", "ping", "cps", "keys" -> "hud." + f.getName();
            case "hudScale" -> "hud.scale";
            case "hudBg" -> "hud.bg";
            case "hudIdleFade" -> "hud.idleFade";
            default -> f.getName();
        };
    }

    static boolean option(Field f) {
        int m = f.getModifiers();
        return Modifier.isPublic(m) && !Modifier.isStatic(m) && (f.getType() == boolean.class || f.getType() == int.class || f.getType() == double.class);
    }

    static SceneConfig load(Path p) {
        SceneConfig c = new SceneConfig();
        if (!Files.exists(p)) {
            return c;
        }

        Properties props = new Properties();

        try (StringReader r = new StringReader(ClientFiles.read(p))) {
            props.load(r);
        } catch (IOException | IllegalArgumentException e) {
            LOG.warn("Không đọc được {}, dùng mặc định", p, e);
            return c;
        }

        for (Field f : SceneConfig.class.getFields()) {
            if (option(f)) {
                String v = props.getProperty(key(f));
                if (v != null) {
                    try {
                        if (f.getType() == boolean.class) {
                            f.setBoolean(c, ConfigValues.bool(v));
                        } else if (f.getType() == int.class) {
                            f.setInt(c, Integer.parseInt(v.trim()));
                        } else {
                            f.setDouble(c, ConfigValues.number(v));
                        }
                    } catch (ReflectiveOperationException | IllegalArgumentException e) {
                        LOG.debug("Bỏ qua giá trị hỏng {}={}", key(f), v);
                    }
                }
            }
        }

        if (!props.containsKey("hudShadow")) {
            // Trước khi có tuỳ chọn này, bóng chữ đi theo kiểu HUD "Tối giản": giữ nguyên cho người đang dùng.
            c.hudShadow = c.hudStyle == 1;
        }

        if (!props.containsKey("uiRevision")) {
            c.menuAurora = false;
            c.uiRevision = 2;
        }

        c.clamp();
        return c;
    }

    void clamp() {
        this.tod = ConfigValues.bounded(this.tod, 40.0, 100.0, 47.0);
        this.hudScale = ConfigValues.bounded(this.hudScale, 0.5, 1.5, 0.8);
        this.hudBg = ConfigValues.bounded(this.hudBg, 0.0, 1.0, 0.45);
        this.minimapZoom = Math.min(3, Math.max(0, this.minimapZoom));
        this.minimapSize = Math.min(160, Math.max(48, this.minimapSize));
        this.zoomLevel = this.zoomLevel <= 2 ? 2 : (this.zoomLevel <= 4 ? 4 : 8);
        this.dfpsBg = Math.min(60, Math.max(1, this.dfpsBg));
        if (this.gamma != -1.0) {
            this.gamma = ConfigValues.bounded(this.gamma, 0.0, 16.0, -1.0);
        }

        this.skyHour = ConfigValues.bounded(this.skyHour, 0.0, 24.0, 18.0);
        this.skyStars = ConfigValues.bounded(this.skyStars, 0.0, 1.0, 0.7);
        this.lyricsSize = ConfigValues.bounded(this.lyricsSize, 0.5, 2.0, 1.0);
        this.weatherMode = Math.min(3, Math.max(0, this.weatherMode));
        this.lyricsDelay = Math.min(5000, Math.max(-5000, this.lyricsDelay));
        this.lyricsBearing = Math.min(360, Math.max(0, this.lyricsBearing));
        this.lyricsElevation = Math.min(80, Math.max(15, this.lyricsElevation));
        this.armorFmt = Math.min(2, Math.max(0, this.armorFmt));
        this.themeAccent &= 16777215;
        this.themeSurface &= 16777215;
        this.themeText &= 16777215;
        this.hudStyle = Math.min(2, Math.max(0, this.hudStyle));
        this.lyricsMotion = Math.min(2, Math.max(0, this.lyricsMotion));
        this.lyricsStyle = Math.min(1, Math.max(0, this.lyricsStyle));
        this.lyricsInk &= 16777215;
        if (this.lyricsInk == 0xFF4DB4) {
            // Mặc định của bản thử v9 — đưa về tông đo từ ảnh mẫu của jun.
            this.lyricsInk = 0xF826BE;
        }
        this.lyricsDuration = Math.min(1200, Math.max(180, this.lyricsDuration));
        this.lyricsWidth = ConfigValues.bounded(this.lyricsWidth, 1.0, 1.8, 1.18);
        this.lyricsHeight = ConfigValues.bounded(this.lyricsHeight, 1.0, 2.0, 1.2);
        this.lyricsGlow = ConfigValues.bounded(this.lyricsGlow, 0.0, 2.5, 1.4);
        this.lyricsTracking = ConfigValues.bounded(this.lyricsTracking, 0.0, 2.0, 0.6);
    }

    public void save() {
        this.save(file());
    }

    void save(Path p) {
        this.clamp();
        Properties props = new Properties();

        for (Field f : SceneConfig.class.getFields()) {
            if (option(f)) {
                try {
                    props.setProperty(key(f), String.valueOf(f.get(this)));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        try (StringWriter w = new StringWriter()) {
            props.store(w, "Nos client");
            ClientFiles.write(p, w.toString());
        } catch (IOException e) {
            LOG.warn("Không lưu được {}", p, e);
        }
    }
}
