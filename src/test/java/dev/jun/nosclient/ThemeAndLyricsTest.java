package dev.jun.nosclient;

import dev.jun.nosclient.theme.HsvColor;
import dev.jun.nosclient.theme.ThemePalette;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThemeAndLyricsTest {
	@Test void customColoursKeepReadableLabelsEvenForBlackWhiteAndRandomInputs() {
		var random = new Random(44);
		for (int i = 0; i < 2500; i++) {
			int accent = i < 2 ? (i == 0 ? 0 : 0xFFFFFF) : random.nextInt(0x1000000);
			int background = i < 2 ? (i == 0 ? 0xFFFFFF : 0) : random.nextInt(0x1000000);
			var p = ThemePalette.of(accent, background, i < 2 ? background : random.nextInt(0x1000000));
			assertTrue(ThemePalette.contrast(p.text(), p.raised()) >= 4.5);
			assertTrue(ThemePalette.contrast(p.muted(), p.raised()) >= 4.5);
			assertTrue(ThemePalette.contrast(p.accent(), p.surface()) >= 4.5);
			assertTrue(ThemePalette.contrast(p.ink(), p.accent()) >= 4.5);
			assertTrue(ThemePalette.luminance(p.surface()) <= .07501);
		}
	}
	@Test void pickerRoundTripsRgbIncludingGreysWithoutNativeDependencies() {
		var random = new Random(9);
		for (int i = 0; i < 2000; i++) { int rgb = random.nextInt(0x1000000); assertEquals(rgb, HsvColor.fromRgb(rgb).rgb()); }
		assertEquals(0x808080, HsvColor.fromRgb(0x808080).rgb());
		assertEquals(0, new HsvColor(Double.NaN, Double.NaN, Double.NaN).rgb());
		assertEquals(new HsvColor(120, 1, 1).rgb(), new HsvColor(480, 2, 2).rgb());
	}
	@Test void hexIsStrictAndDoesNotAcceptPartialOrInjectedValues() {
		assertEquals(0xA1B2C3, ThemePalette.parseHex("#a1b2c3"));
		assertEquals("#00000A", ThemePalette.hex(10));
		for (String input : new String[]{"#FFF", "#FFFFFFFF", "#xyzxyz", "12345;", "1234567", "", "−12345"}) assertThrows(IllegalArgumentException.class, () -> ThemePalette.parseHex(input));
	}
}
