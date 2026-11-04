package dev.jun.nosclient.config;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MusicConfig {
    public int configVersion = 19;
    public volatile boolean enabled = true;
    public boolean uiSounds = true;
    public boolean songChime = false;
    public volatile MusicConfig.Source source = MusicConfig.Source.ANY;
    public int syncOffsetMs = 0;
    public Map<String, Integer> songOffsets = new HashMap<>();
    public boolean showLyrics = false;
    public MusicConfig.LyricsStyle lyricsStyle = MusicConfig.LyricsStyle.NOS_SKY;
    public boolean skyWords = false;
    public boolean followView = false;
    public boolean viewLock = false;
    public boolean showUnderground = false;
    public boolean musicNotes = true;
    public boolean randomPosition = true;
    public boolean allCaps = true;
    public float floatHeight = 0.0F;
    public float lyricsScale = 1.0F;
    public float lyricsDistance = 1.0F;
    public float skyDistance = 150.0F;
    public int textShadow = 1;
    public int shadowColor = 0;
    public boolean varySizes = true;
    public boolean arcLayout = true;
    public int maxLines = 2;
    public String font = "nos-celestial";
    public MusicConfig.TextStyle textStyle = MusicConfig.TextStyle.NEON;
    public String theme = "lavender";
    public boolean customTheme = false;
    public int singleColor = 0;
    public int customMain = -7178;
    public int customA = -42296;
    public int customB = -9447169;
    public boolean twoColor = true;
    public float twoColorStrength = 0.0F;
    public int twoColorColor = 0;
    public boolean glow = true;
    public boolean autoPerf = true;
    public int targetFps = 60;
    public int quality = 1;
    public float textOpacity = 1.0F;
    public MusicConfig.EnterAnim enterAnim = MusicConfig.EnterAnim.NEON;
    public MusicConfig.ExitAnim exitAnim = MusicConfig.ExitAnim.FLOAT_UP;
    public float animSpeed = 1.0F;
    public boolean beatGlow = false;
    public int accentColor = 0;
    public int barColor = 0;
    public boolean showTopBar = true;
    public boolean chroma = false;
    public boolean skyAlways = true;
    public int chromaColor = 0;
    public boolean customHotbar = false;
    public MusicConfig.TopBarStyle topBarStyle = MusicConfig.TopBarStyle.KARAOKE;
    public String topBarFont = "outfit";
    public float topBarScale = 1.0F;
    public float topBarX = 0.5F;
    public float topBarY = 0.02F;
    public boolean showOnlyWhenPlaying = true;
    public boolean hideWhenStopped = false;
    public boolean dimWhenIdle = true;
    public boolean mediaKeys = true;
    public Map<String, String> profiles = new LinkedHashMap<>();

    public void clamp() {
        configVersion = Math.max(19, configVersion);
        if (source == null) source = Source.ANY;
        if (lyricsStyle == null) lyricsStyle = LyricsStyle.NOS_SKY;
        if (textStyle == null) textStyle = TextStyle.NEON;
        if (enterAnim == null) enterAnim = EnterAnim.NEON;
        if (exitAnim == null) exitAnim = ExitAnim.FLOAT_UP;
        if (topBarStyle == null) topBarStyle = TopBarStyle.KARAOKE;
        if (font == null || !font.matches("[a-z0-9_-]{1,96}")) font = "nos-celestial";
        if (topBarFont == null || !topBarFont.matches("[a-z0-9_-]{1,96}")) topBarFont = "outfit";
        if (theme == null) theme = "lavender";
        if (profiles == null) profiles = new LinkedHashMap<>();
        if (songOffsets == null) songOffsets = new HashMap<>();
        songOffsets
                .entrySet()
                .removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        songOffsets.replaceAll((key, value) -> Math.max(-5000, Math.min(5000, value)));
        profiles.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        quality = Math.max(0, Math.min(2, quality));
        if (targetFps != 0) targetFps = Math.max(30, Math.min(240, targetFps));
        twoColorStrength = bounded(twoColorStrength, 0, 1, 0);
        syncOffsetMs = Math.max(-5000, Math.min(5000, syncOffsetMs));
        maxLines = Math.max(1, Math.min(3, maxLines));
        lyricsScale = bounded(lyricsScale, .4F, 2.5F, 1);
        lyricsDistance = bounded(lyricsDistance, .4F, 2, 1);
        skyDistance = bounded(skyDistance, 30, 300, 150);
        textOpacity = bounded(textOpacity, .2F, 1, 1);
        topBarScale = bounded(topBarScale, .6F, 1.8F, 1);
        topBarX = bounded(topBarX, 0, 1, .5F);
        topBarY = bounded(topBarY, 0, 1, .02F);
        animSpeed = bounded(animSpeed, .3F, 2, 1);
        floatHeight = bounded(floatHeight, -1, 4, 0);
    }

    private static float bounded(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    public enum EnterAnim {
        WORD,
        FADE,
        RISE,
        NEON;
    }

    public enum ExitAnim {
        FLOAT_UP,
        DRIFT,
        FADE;
    }

    public enum LyricsStyle {
        SKY,
        FLOATING,
        NOS_SKY;
    }

    public enum Source {
        SPOTIFY,
        YOUTUBE,
        ANY;
    }

    public enum TextStyle {
        NEON,
        CLEAN;
    }

    public enum TopBarStyle {
        KARAOKE,
        SIMPLE;
    }
}
