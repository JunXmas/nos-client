package dev.jun.nosclient.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

class ClientSettingsTest {
    @TempDir Path directory;

    static final class Options {
        String name = "default";
        int count = 3;
    }

    @Test
    void obsoleteCosmeticSettingsAreIgnoredAndRemovedWithoutChangingMusicOrSky() throws Exception {
        Path file = directory.resolve("nosclient.json");
        Files.writeString(
                file,
                """
                {"music":{"showLyrics":true,"font":"poppins","cosmetic":"cap_back"},
                 "effects":{"sky":28,"cosmeticsEnabled":true,"deathFx":3,"deathOthers":true,
                            "trail":3,"aura":true}}
                """);
        ClientSettings store = new ClientSettings(file);
        MusicConfig music = store.section("music", MusicConfig.class, MusicConfig::new);
        EffectsConfig effects = store.section("effects", EffectsConfig.class, EffectsConfig::new);
        assertTrue(music.showLyrics);
        assertEquals("poppins", music.font);
        assertEquals(28, effects.sky);
        store.save();
        var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertFalse(saved.getAsJsonObject("music").has("cosmetic"));
        for (String field :
                new String[] {"cosmeticsEnabled", "deathFx", "deathOthers", "trail", "aura"})
            assertFalse(saved.getAsJsonObject("effects").has(field), field);
        assertTrue(saved.getAsJsonObject("music").get("showLyrics").getAsBoolean());
        assertEquals(28, saved.getAsJsonObject("effects").get("sky").getAsInt());
    }

    @Test
    void allSectionsPersistTogetherAndExistingValuesSkipLegacyMigration() throws Exception {
        Path file = directory.resolve("nosclient.json");
        Files.writeString(
                file, "{\"future\":{\"flag\":true},\"music\":{\"name\":\"local\",\"count\":7}}");
        ClientSettings store = new ClientSettings(file);
        AtomicInteger migrations = new AtomicInteger();
        Options music =
                store.section(
                        "music",
                        Options.class,
                        () -> {
                            migrations.incrementAndGet();
                            return new Options();
                        });
        Options effects =
                store.section(
                        "effects",
                        Options.class,
                        () -> {
                            migrations.incrementAndGet();
                            return new Options();
                        });
        assertEquals("local", music.name);
        assertEquals(7, music.count);
        assertEquals(1, migrations.get());
        music.count = 9;
        effects.name = "aurora";
        store.save();
        var saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals(9, saved.getAsJsonObject("music").get("count").getAsInt());
        assertEquals("aurora", saved.getAsJsonObject("effects").get("name").getAsString());
        assertTrue(saved.getAsJsonObject("future").get("flag").getAsBoolean());
        assertEquals(
                "aurora",
                new ClientSettings(file).section("effects", Options.class, Options::new).name);
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void damagedFileIsPreservedBeforeSavingNewPreferences() throws Exception {
        Path file = directory.resolve("nosclient.json");
        Files.writeString(file, "{broken-json");
        ClientSettings store = new ClientSettings(file);
        Options value = store.section("music", Options.class, Options::new);
        value.name = "recovered";
        store.save();
        assertEquals(
                "recovered",
                new ClientSettings(file).section("music", Options.class, Options::new).name);
        try (var files = Files.list(directory)) {
            Path backup =
                    files.filter(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .startsWith("nosclient.json.invalid-"))
                            .findFirst()
                            .orElseThrow();
            assertEquals("{broken-json", Files.readString(backup));
        }
    }

    @Test
    void musicDefaultsAndMalformedFieldsAlwaysProduceUsableControls() {
        MusicConfig music = new MusicConfig();
        music.clamp();
        assertEquals(MusicConfig.Source.ANY, music.source);
        assertEquals(MusicConfig.LyricsStyle.SKY, music.lyricsStyle);
        assertEquals("nos-celestial", music.font);
        music.font = "../../wrong";
        music.lyricsStyle = null;
        music.topBarStyle = null;
        music.lyricsScale = Float.NaN;
        music.textOpacity = Float.POSITIVE_INFINITY;
        music.maxLines = -99;
        music.clamp();
        assertEquals("poppins", music.font);
        assertNotNull(music.lyricsStyle);
        assertNotNull(music.topBarStyle);
        assertTrue(Float.isFinite(music.lyricsScale));
        assertTrue(Float.isFinite(music.textOpacity));
        assertEquals(1, music.maxLines);
    }
}
