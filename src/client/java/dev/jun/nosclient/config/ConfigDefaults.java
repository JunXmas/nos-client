package dev.jun.nosclient.config;

/** Đặt lại đúng nhóm đang chọn; không xóa profile hay cấu hình của nhóm khác. */
public final class ConfigDefaults {
    private ConfigDefaults() {}

    public static boolean reset(String feature, MusicConfig music, EffectsConfig effects) {
        MusicConfig m = new MusicConfig();
        EffectsConfig e = new EffectsConfig();
        switch (feature) {
            case "music" -> copy(music, m, "enabled source uiSounds songChime");
            case "lyrics" ->
                    copy(
                            music,
                            m,
                            "showLyrics lyricsStyle skyWords lyricsScale lyricsDistance skyDistance"
                                + " showUnderground musicNotes followView viewLock randomPosition"
                                + " maxLines enterAnim exitAnim animSpeed syncOffsetMs"
                                + " songOffsets");
            case "textstyle" ->
                    copy(
                            music,
                            m,
                            "allCaps varySizes arcLayout font textStyle theme customTheme"
                                    + " singleColor customMain customA customB twoColor"
                                    + " twoColorStrength twoColorColor glow textOpacity textShadow"
                                    + " shadowColor beatGlow accentColor barColor");
            case "karaoke" ->
                    copy(
                            music,
                            m,
                            "showTopBar topBarStyle topBarFont topBarScale topBarX topBarY"
                                + " showOnlyWhenPlaying hideWhenStopped dimWhenIdle customHotbar");
            case "mediaKeys" -> copy(music, m, "mediaKeys");
            case "mediaProfiles" -> copy(music, m, "profiles");
            case "visualPerformance" -> copy(music, m, "autoPerf targetFps quality");
            case "sky" ->
                    copy(
                            effects,
                            e,
                            "sky lastSky skyStrength weatherAmount realWeather hideClouds skyDay"
                                + " skyImage skyImageFile fxDayNight fxConstellations fxSkyMusic"
                                + " skySpeed skyPalette skyShuffle skyPattern skyLively skyBreath"
                                + " starDensity visualHour skyAlways customTop customHorizon"
                                + " customAurora1 customAurora2 customWeather customAurora"
                                + " customStars customMoon customShooting fxBeatPulse fxWeather"
                                + " fxAurora fxStars fxBeams fxSkyColor");
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void copy(Object target, Object defaults, String fields) {
        try {
            for (String name : fields.split(" ")) {
                var field = target.getClass().getField(name);
                field.set(target, field.get(defaults));
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Invalid settings reset group", error);
        }
    }
}
