package dev.jun.nosclient.config;

import dev.jun.nosclient.gui.effects.Gfx;
import dev.jun.nosclient.sky.SkyPreset;
import dev.jun.nosclient.sky.WorldSky;

public class EffectsConfig {
    public int accent = 0;
    public boolean rgbAccent = false;
    public boolean blur = true;
    public boolean particles = false;
    public boolean sounds = true;
    public int uiFont = 0;
    public boolean fxMenuAnim = true;
    public int sky = SkyPreset.AURORA.ordinal();
    public int lastSky = SkyPreset.AURORA.ordinal();
    public float skyStrength = 0.8F;
    public float weatherAmount = 1.0F;
    public boolean realWeather = true;
    public boolean hideClouds = true;
    public String cloudsBefore = "";
    public boolean skyDay = true;
    public boolean skyImage = false;
    public String skyImageFile = "";
    public boolean fxDayNight = false;
    public boolean fxConstellations = true;
    public boolean fxSkyMusic = true;
    public float skySpeed = 1.0F;
    public int skyPalette = -1;
    public boolean cosmeticsEnabled = true;
    public int trail = 0;
    public boolean aura = false;
    public int deathFx = 1;
    public boolean deathOthers = false;
    public boolean beatGlow = false;
    public boolean skyShuffle = false;
    public float skyPattern = 1.0F;
    public float skyLively = 0.6F;
    public float skyBreath = 1.0F;
    public float starDensity = 1.0F;
    public float visualHour = -1.0F;
    public boolean skyAlways = true;
    public int customTop = 656920;
    public int customHorizon = 2758216;
    public int customAurora1 = 10719743;
    public int customAurora2 = 16777215;
    public int customWeather = 2;
    public boolean customAurora = true;
    public boolean customStars = true;
    public boolean customMoon = false;
    public boolean customShooting = true;
    public boolean fxBeatPulse = true;
    public boolean fxWeather = true;
    public boolean fxAurora = true;
    public boolean fxStars = true;
    public boolean fxBeams = true;
    public boolean fxSkyColor = true;
    public volatile String spotifyClientId = "";

    public void clamp() {
        EffectsConfig defaults = new EffectsConfig();
        for (java.lang.reflect.Field field : EffectsConfig.class.getFields()) {
            if (field.getType() == float.class) {
                try {
                    if (!Float.isFinite(field.getFloat(this)))
                        field.setFloat(this, field.getFloat(defaults));
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException(error);
                }
            }
        }
        visualHour = Math.max(-1, Math.min(24, visualHour));
        starDensity = Math.max(0, Math.min(2, starDensity));
        deathFx = Math.max(0, Math.min(3, deathFx));
        trail = Math.max(0, Math.min(3, trail));
        uiFont = Math.max(0, Math.min(Gfx.FONT_IDS.length, uiFont));
        sky = Math.max(-1, Math.min(SkyPreset.values().length - 1, sky));
        lastSky = Math.max(0, Math.min(SkyPreset.values().length - 1, lastSky));
        skyStrength = Math.max(.1F, Math.min(1, skyStrength));
        weatherAmount = Math.max(0, Math.min(2, weatherAmount));
        skySpeed = Math.max(.25F, Math.min(2, skySpeed));
        skyPattern = Math.max(.2F, Math.min(1.5F, skyPattern));
        skyLively = Math.max(0, Math.min(1, skyLively));
        skyBreath = Math.max(0, Math.min(2, skyBreath));
        skyPalette = Math.max(-1, Math.min(WorldSky.PALETTES.length - 1, skyPalette));
        customWeather = Math.max(0, Math.min(SkyPreset.Weather.values().length - 1, customWeather));
        if (spotifyClientId == null) spotifyClientId = "";
        if (cloudsBefore == null) cloudsBefore = "";
        if (skyImageFile == null) skyImageFile = "";
        if (sky >= 0) lastSky = sky;
    }
}
