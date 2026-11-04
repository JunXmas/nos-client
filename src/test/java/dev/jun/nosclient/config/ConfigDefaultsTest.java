package dev.jun.nosclient.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConfigDefaultsTest {
    @Test
    void resettingAppearanceDoesNotEraseProfilesOrChangeSourcesAndSky() {
        MusicConfig music = new MusicConfig();
        EffectsConfig effects = new EffectsConfig();
        music.profiles.put("Keep", "{}");
        music.font = "dancing";
        music.source = MusicConfig.Source.SPOTIFY;
        effects.sky = 20;
        effects.cloudsBefore = "FAST";
        assertTrue(ConfigDefaults.reset("textstyle", music, effects));
        assertEquals("nos-celestial", music.font);
        assertEquals(MusicConfig.Source.SPOTIFY, music.source);
        assertTrue(music.profiles.containsKey("Keep"));
        assertEquals(20, effects.sky);
        ConfigDefaults.reset("sky", music, effects);
        assertEquals(new EffectsConfig().sky, effects.sky);
        assertEquals("FAST", effects.cloudsBefore);
        assertTrue(music.profiles.containsKey("Keep"));
    }

    @Test
    void everyGroupReferencesRealFieldsAndUnknownGroupsAreIgnored() {
        for (String feature :
                new String[] {
                    "music",
                    "lyrics",
                    "textstyle",
                    "karaoke",
                    "mediaKeys",
                    "mediaProfiles",
                    "visualPerformance",
                    "sky"
                }) {
            assertTrue(
                    ConfigDefaults.reset(feature, new MusicConfig(), new EffectsConfig()), feature);
        }
        assertFalse(ConfigDefaults.reset("accessories", new MusicConfig(), new EffectsConfig()));
        assertFalse(ConfigDefaults.reset("unknown", new MusicConfig(), new EffectsConfig()));
    }
}
