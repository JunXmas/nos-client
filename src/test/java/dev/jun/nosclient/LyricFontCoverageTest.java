package dev.jun.nosclient;

import com.google.gson.JsonParser;
import java.awt.Font;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricFontCoverageTest {
    @Test void bundledVietnameseFontCoversEveryVietnameseLetterAndCombiningMarks() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/nosclient/font/be-vietnam-semibold.ttf")) {
            assertNotNull(input);
            var font = Font.createFont(Font.TRUETYPE_FONT, input);
            for (int code = 0x1EA0; code <= 0x1EF9; code++)
                assertTrue(font.canDisplay(code), "Missing Vietnamese glyph U+" + Integer.toHexString(code));
            String sample = "ĂăÂâĐđÊêÔôƠơƯư ÁÀẢÃẠ ẮẰẲẴẶ ẤẦẨẪẬ ẾỀỂỄỆ ỐỒỔỖỘ ỚỜỞỠỢ ỨỪỬỮỰ";
            assertEquals(-1, font.canDisplayUpTo(sample));
            assertEquals(-1, font.canDisplayUpTo(Normalizer.normalize(sample, Normalizer.Form.NFD)));
        }
    }

    @Test void vietnameseProfilesShareOneLicensedFontAndEnoughNativePixelsForLargeLyrics() throws Exception {
        for (String suffix : new String[]{"", "_lyrics", "_sky"}) {
            try (var input = getClass().getResourceAsStream("/assets/nosclient/font/be-vietnam" + suffix + ".json")) {
                assertNotNull(input);
                var providers = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("providers");
                var ttf = providers.get(0).getAsJsonObject();
                assertEquals("nosclient:be-vietnam-semibold.ttf", ttf.get("file").getAsString());
                int pixels = ttf.get("size").getAsInt() * ttf.get("oversample").getAsInt();
                assertTrue(pixels >= (suffix.isEmpty() ? 64 : 128));
                assertEquals("minecraft:include/unifont", providers.get(2).getAsJsonObject().get("id").getAsString());
            }
        }
    }
}
