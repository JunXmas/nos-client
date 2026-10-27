package dev.jun.nosclient;

import dev.jun.nosclient.media.CelestialTypography;
import dev.jun.nosclient.theme.ThemePalette;
import dev.jun.nosclient.typography.Typography;
import dev.jun.nosclient.typography.FontCoverage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TypographyTest {
	@Test void bundledCoverageIncludesVietnameseAndRoutesMissingSymbolsToFallback() {
		for (int cp = 32; cp <= 126; cp++) assertTrue(FontCoverage.contains(cp));
		for (int cp = 0x1EA0; cp <= 0x1EF9; cp++) assertTrue(FontCoverage.contains(cp));
		for (int cp : "↺◆♥∞↑→".codePoints().toArray()) assertFalse(FontCoverage.contains(cp));
		assertFalse(FontCoverage.contains(-1)); assertFalse(FontCoverage.contains(0x110000));
	}
	@Test void fractionalScalesNeverChooseAnUndersizedAtlasWithinSupportedRange() {
		for (double scale = .25; scale <= 6; scale += .013) {
			int tier = Typography.densityIndex(scale);
			assertTrue(Typography.DENSITIES[tier] / 2.0 >= scale);
			if (tier > 0) assertTrue(Typography.DENSITIES[tier - 1] / 2.0 < scale);
		}
		assertEquals(0, Typography.densityIndex(Double.NaN));
		assertEquals(5, Typography.densityIndex(20));
	}
	@Test void tinyHudPreservesTenPhysicalPixelsAndCanOptOut() {
		for (double gui : new double[]{1, 1.5, 2, 3, 4}) for (double requested : new double[]{.25, .5, .68, 1, 1.75, 2}) {
			double effective = Typography.hudScale(requested, gui, true);
			assertTrue(effective * gui * 6.5 >= 10 - .00001);
			assertTrue(effective >= requested);
			assertEquals(requested, Typography.hudScale(requested, gui, false));
		}
	}
	@Test void snappingUsesPhysicalPixelsWithAtMostHalfAPixelMovement() {
		for (double gui : new double[]{1, 1.5, 2, 3}) for (double position = -13.1; position < 20; position += .073) {
			double snapped = Typography.snap(position, gui);
			assertEquals(Math.rint(snapped * gui), snapped * gui, .00001);
			assertTrue(Math.abs(snapped - position) * gui <= .50001);
		}
	}
	@Test void stretchingAndWrappingKeepVietnameseCombiningMarksAttached() {
		assertEquals(CelestialTypography.clusters("Ánh trời"), CelestialTypography.clusters("A\u0301nh tro\u031b\u0300i"));
		var measure = (java.util.function.ToDoubleFunction<String>) s -> 6;
		String original = "Ánh trời dịu êm trên mặt hồ đêm nay";
		var rows = CelestialTypography.wrap(original, measure, .6, 84);
		assertEquals(original, String.join(" ", rows));
		for (var row : rows) assertTrue(CelestialTypography.width(row, measure, .6) <= 84);
		var longWord = CelestialTypography.wrap("á".repeat(50), measure, 1, 40);
		assertEquals("á".repeat(50), String.join("", longWord));
		assertTrue(longWord.stream().allMatch(row -> CelestialTypography.width(row, measure, 1) <= 40));
	}
	@Test void skyInkRemainsReadableAcrossDayNightAndCustomThemes() {
		for (int sky : new int[]{0xFF050812, 0xFF20304D, 0xFF80ADFF, 0xFFCACACA, 0xFFFADEBE})
			for (int accent : new int[]{0xFF91E3DC, 0xFFB8A5FF, 0xFF000000, 0xFFFFFFFF})
				{
					var ink = CelestialTypography.ink(sky, 0xFFF1F3ED, accent, true);
					assertTrue(ThemePalette.luminance(ink.body()) > .9);
					assertTrue(ThemePalette.contrast(ink.body(), ink.edge()) >= 7);
				}
		for (double seconds : new double[]{0, 1, 10, 100}) for (double x = -100; x <= 100; x++)
			assertTrue(Math.abs(CelestialTypography.wave(x, 100, seconds)) <= 2.251);
	}
	@Test void wideVersesDoNotLeaveASingleWordOnTheLastRow() {
		var measure = (java.util.function.ToDoubleFunction<String>) s -> 5;
		var rows = CelestialTypography.wrap("Giữ chút bình yên giữa ngân hà", measure, 1, 155);
		assertEquals(2, rows.size());
		assertEquals("Giữ chút bình yên giữa ngân hà", String.join(" ", rows));
		assertTrue(rows.getLast().contains(" "));
		assertTrue(Math.abs(CelestialTypography.width(rows.getFirst(), measure, 1) - CelestialTypography.width(rows.getLast(), measure, 1)) < 40);
	}
}
