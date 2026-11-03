package dev.jun.nosclient.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.Base64;

class MusicProfileTest {
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
