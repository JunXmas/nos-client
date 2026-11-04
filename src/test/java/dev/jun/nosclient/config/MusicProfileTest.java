package dev.jun.nosclient.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.Base64;

class MusicProfileTest {
    @Test
    void lyricRenderersAndTuningSurviveProfileImportWithoutReplacingTheChosenStyle() {
        for (MusicConfig.LyricsStyle style : MusicConfig.LyricsStyle.values()) {
            String json =
                    "{\"lyricsStyle\":\""
                            + style.name()
                            + "\",\"skyWords\":true,\"font\":\"nos-celestial\",\"syncOffsetMs\":750,\"enterAnim\":\"RISE\",\"viewLock\":true}";
            String encoded = MusicSettings.exportCode(json);
            for (String code : new String[] {encoded, encoded.replace("NS1:", "LF1:")}) {
                var imported =
                        JsonParser.parseString(MusicSettings.importCode(code)).getAsJsonObject();
                assertEquals(style.name(), imported.get("lyricsStyle").getAsString());
                assertTrue(imported.get("skyWords").getAsBoolean());
                assertTrue(imported.get("viewLock").getAsBoolean());
                assertEquals("nos-celestial", imported.get("font").getAsString());
                assertEquals(750, imported.get("syncOffsetMs").getAsInt());
                assertEquals("RISE", imported.get("enterAnim").getAsString());
            }
        }
    }

    @Test
    void newAndLegacyCodesRoundTripWithoutImportingNestedProfiles() {
        String encoded =
                MusicSettings.exportCode(
                        "{\"font\":\"poppins\",\"profiles\":{\"nested\":\"ignored\"}}");
        for (String code : new String[] {encoded, encoded.replace("NS1:", "LF1:")}) {
            var profile = JsonParser.parseString(MusicSettings.importCode(code)).getAsJsonObject();
            assertEquals("poppins", profile.get("font").getAsString());
            assertFalse(profile.has("profiles"));
        }
    }

    @Test
    void truncatedCompressedAndOversizedProfilesAreRejected() {
        String encoded = MusicSettings.exportCode("{\"font\":\"poppins\"}");
        byte[] compressed = Base64.getDecoder().decode(encoded.substring(4));
        byte[] truncated = java.util.Arrays.copyOf(compressed, compressed.length - 2);
        assertNull(
                MusicSettings.importCode("NS1:" + Base64.getEncoder().encodeToString(truncated)));
        assertNull(
                MusicSettings.importCode(
                        MusicSettings.exportCode("{\"font\":\"" + "a".repeat(262144) + "\"}")));
        assertNull(MusicSettings.importCode(" ".repeat(524289)));
        assertNull(MusicSettings.importCode("NS1:not-base64"));
    }
}
