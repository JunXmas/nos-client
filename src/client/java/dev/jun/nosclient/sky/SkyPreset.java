package dev.jun.nosclient.sky;

import dev.jun.nosclient.ClientEffects;
import dev.jun.nosclient.config.EffectsConfig;
import dev.jun.nosclient.gui.effects.Gfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public enum SkyPreset {
    SYNTHWAVE(
            721956,
            5905006,
            0.9F,
            0.8F,
            false,
            16732120,
            4973567,
            SkyPreset.Weather.NONE,
            0,
            true,
            16759024),
    AURORA(
            133652,
            731699,
            0.85F,
            1.0F,
            true,
            4652976,
            10124543,
            SkyPreset.Weather.NONE,
            0,
            true,
            10289108),
    NEBULA(
            459546,
            1968698,
            0.85F,
            1.0F,
            false,
            16736184,
            7240959,
            SkyPreset.Weather.NONE,
            0,
            true,
            15780095),
    BLACK_HOLE(
            65541,
            722456,
            0.9F,
            1.4F,
            false,
            16757850,
            16734766,
            SkyPreset.Weather.NONE,
            0,
            false,
            16766632),
    COSMOS(
            197132,
            1182252,
            0.9F,
            1.7F,
            false,
            11824127,
            6015231,
            SkyPreset.Weather.NONE,
            0,
            true,
            14207231),
    NEON_CITY(
            329750,
            2756170,
            0.85F,
            0.3F,
            false,
            16727464,
            3073791,
            SkyPreset.Weather.RAIN,
            0,
            false,
            16751320),
    GOLDEN_HOUR(
            2761552,
            16751196,
            0.7F,
            0.15F,
            false,
            16761466,
            16743052,
            SkyPreset.Weather.FIREFLIES,
            0,
            false,
            16769208),
    COTTON_CANDY(
            5214453,
            16763108,
            0.85F,
            0.0F,
            false,
            16777215,
            16766700,
            SkyPreset.Weather.NONE,
            0,
            false,
            16777215),
    SAKURA(
            3807820,
            16757721,
            0.6F,
            0.3F,
            false,
            16762598,
            13144319,
            SkyPreset.Weather.PETALS,
            0,
            false,
            16762598),
    FROST(
            662064,
            6264008,
            0.75F,
            0.8F,
            true,
            12120319,
            15137023,
            SkyPreset.Weather.SNOW,
            15398655,
            false,
            14677247),
    MIDNIGHT(
            131850,
            1055290,
            0.85F,
            1.8F,
            false,
            0,
            0,
            SkyPreset.Weather.NONE,
            15920861,
            true,
            13161215),
    DEEP_SEA(
            136734,
            739930,
            0.8F,
            0.3F,
            false,
            6025958,
            12124150,
            SkyPreset.Weather.NONE,
            0,
            false,
            11075572),
    BLOOD_MOON(
            1377030,
            4852242,
            0.85F,
            0.6F,
            false,
            16734794,
            3803664,
            SkyPreset.Weather.EMBERS,
            16726843,
            false,
            16747146),
    STORM(921880, 3028034, 0.8F, 0.0F, false, 0, 0, SkyPreset.Weather.THUNDER, 0, false, 13161712),
    CUSTOM(
            656920,
            2758216,
            0.85F,
            1.2F,
            true,
            10719743,
            16777215,
            SkyPreset.Weather.BLIZZARD,
            0,
            true,
            13219327),
    INFERNO(
            1704962,
            8002566,
            0.85F,
            0.2F,
            false,
            16747038,
            16769658,
            SkyPreset.Weather.EMBERS,
            0,
            false,
            16757867),
    MATRIX(
            2052,
            138252,
            0.9F,
            0.0F,
            false,
            4652922,
            14221280,
            SkyPreset.Weather.NONE,
            0,
            false,
            8191898),
    PRISM(
            459796,
            1708086,
            0.9F,
            1.2F,
            false,
            11824127,
            4973567,
            SkyPreset.Weather.NONE,
            0,
            true,
            15126783),
    PULSAR(
            131852,
            660528,
            0.9F,
            1.6F,
            false,
            4973567,
            11824127,
            SkyPreset.Weather.NONE,
            0,
            true,
            12580095),
    AQUARELL(
            15124712,
            16770258,
            0.8F,
            0.0F,
            false,
            16752329,
            10475775,
            SkyPreset.Weather.PETALS,
            0,
            false,
            16777215),
    POLAR(
            1551,
            670280,
            0.9F,
            0.0F,
            false,
            3076288,
            4951295,
            SkyPreset.Weather.NONE,
            0,
            true,
            10289128),
    GALAXY(
            131088,
            1378360,
            0.9F,
            0.0F,
            false,
            11824127,
            6015231,
            SkyPreset.Weather.NONE,
            0,
            true,
            14207231),
    OCEAN(
            340843,
            2932966,
            0.9F,
            0.0F,
            false,
            6025958,
            12124150,
            SkyPreset.Weather.NONE,
            0,
            false,
            12124159),
    ICE(
            662096,
            6261990,
            0.9F,
            0.0F,
            false,
            12120319,
            15137023,
            SkyPreset.Weather.SNOW,
            0,
            false,
            14677247),
    PURPLE(
            656160,
            4199518,
            0.9F,
            0.0F,
            false,
            11824127,
            14725375,
            SkyPreset.Weather.NONE,
            0,
            true,
            14729471),
    SUNSET(
            3021401,
            16741158,
            0.9F,
            0.0F,
            false,
            16757867,
            16739950,
            SkyPreset.Weather.FIREFLIES,
            0,
            false,
            16765088),
    CHERRY(
            2034227,
            9188224,
            0.9F,
            0.0F,
            false,
            16762598,
            13144319,
            SkyPreset.Weather.PETALS,
            0,
            false,
            16762598),
    CRIMSON(
            655362,
            4195077,
            0.9F,
            0.0F,
            false,
            16726826,
            16747114,
            SkyPreset.Weather.EMBERS,
            0,
            false,
            16747146),
    THUNDER(
            198676,
            1320010,
            0.9F,
            0.0F,
            false,
            9416959,
            13161727,
            SkyPreset.Weather.RAIN,
            0,
            false,
            13161712);

    public static final int PAINTED = 20;
    public int top;
    public int horizon;
    public float strength;
    public float stars;
    public boolean aurora;
    public int aurora1;
    public int aurora2;
    public SkyPreset.Weather weather;
    public int moon;
    public boolean shootingStars;
    public int lyricColor;

    public boolean painted() {
        return this.ordinal() >= 20;
    }

    SkyPreset(
            int top,
            int horizon,
            float strength,
            float stars,
            boolean aurora,
            int aurora1,
            int aurora2,
            SkyPreset.Weather weather,
            int moon,
            boolean shootingStars,
            int lyricColor) {
        this.top = top;
        this.horizon = horizon;
        this.strength = strength;
        this.stars = stars;
        this.aurora = aurora;
        this.aurora1 = aurora1;
        this.aurora2 = aurora2;
        this.weather = weather;
        this.moon = moon;
        this.shootingStars = shootingStars;
        this.lyricColor = lyricColor;
    }

    public boolean clouds() {
        return this == COTTON_CANDY;
    }

    public String key() {
        return "nosclient.music.sky." + this.name().toLowerCase(Locale.ROOT);
    }

    public String[] features() {
        List<String> f = new ArrayList<>();
        switch (this) {
            case SYNTHWAVE:
                f.add("nosclient.music.feat.synth");
            case AURORA:
            case SAKURA:
            case FROST:
            case MIDNIGHT:
            case BLOOD_MOON:
            case STORM:
            case CUSTOM:
            default:
                break;
            case NEBULA:
                f.add("nosclient.music.feat.nebula");
                break;
            case BLACK_HOLE:
                f.add("nosclient.music.feat.blackhole");
                break;
            case COSMOS:
                f.add("nosclient.music.feat.galaxy");
                break;
            case NEON_CITY:
                f.add("nosclient.music.feat.beams");
                break;
            case GOLDEN_HOUR:
            case COTTON_CANDY:
                f.add("nosclient.music.feat.clouds");
                break;
            case DEEP_SEA:
                f.add("nosclient.music.feat.lagoon");
                break;
            case INFERNO:
                f.add("nosclient.music.feat.lava");
                break;
            case MATRIX:
                f.add("nosclient.music.feat.code");
                break;
            case PRISM:
            case PULSAR:
                f.add("nosclient.music.feat.beams");
                break;
            case AQUARELL:
                f.add("nosclient.music.feat.paint");
        }

        if (this.aurora) {
            f.add("nosclient.music.feat.aurora");
        }

        if (this.stars >= 1.0F) {
            f.add("nosclient.music.feat.stars");
        }

        if (this.shootingStars) {
            f.add("nosclient.music.feat.shooting");
        }

        if (this.moon != 0) {
            f.add("nosclient.music.feat.moon");
        }

        if (this.weather != SkyPreset.Weather.NONE) {
            f.add("nosclient.music.feat." + this.weather.name().toLowerCase(Locale.ROOT));
        }

        return f.toArray(new String[0]);
    }

    public static SkyPreset[] order() {
        return values();
    }

    public static SkyPreset get(int i) {
        SkyPreset[] v = values();
        SkyPreset p = i >= 0 && i < v.length ? v[i] : null;
        if (p == CUSTOM) {
            applyCustom();
        }

        return p;
    }

    public static void shuffle() {
        EffectsConfig c = ClientEffects.cfg();
        SkyPreset[] all = values();

        int n;
        do {
            n = ThreadLocalRandom.current().nextInt(all.length);
        } while ((all[n] == CUSTOM || n == c.sky) && all.length > 2);

        c.sky = n;
        c.lastSky = n;
        ClientEffects.saveConfig();
    }

    public static void applyCustom() {
        EffectsConfig c = ClientEffects.cfg();
        CUSTOM.top = c.customTop;
        CUSTOM.horizon = c.customHorizon;
        CUSTOM.aurora = c.customAurora;
        CUSTOM.aurora1 = c.customAurora1;
        CUSTOM.aurora2 = c.customAurora2;
        CUSTOM.stars = c.customStars ? 1.3F : 0.0F;
        CUSTOM.moon = c.customMoon ? 15920861 : 0;
        CUSTOM.shootingStars = c.customShooting;
        SkyPreset.Weather[] w = SkyPreset.Weather.values();
        CUSTOM.weather = w[Math.floorMod(c.customWeather, w.length)];
        CUSTOM.lyricColor = Gfx.lerpColor(c.customAurora1, 16777215, 0.35F);
    }

    public enum Weather {
        NONE,
        SNOW,
        BLIZZARD,
        RAIN,
        THUNDER,
        PETALS,
        EMBERS,
        FIREFLIES;
    }
}
