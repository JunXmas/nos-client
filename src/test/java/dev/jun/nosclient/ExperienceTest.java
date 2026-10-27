package dev.jun.nosclient;

import dev.jun.nosclient.gui.TooltipPlacement;
import dev.jun.nosclient.hud.AnimatedText;
import dev.jun.nosclient.map.MapViewport;
import dev.jun.nosclient.scene.SkyPalette;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperienceTest {
	@Test void shulkerTooltipStaysInsideEveryScreenEdge() {
		for (int x : new int[]{0, 10, 160, 319}) for (int y : new int[]{0, 10, 90, 179}) {
			var placement = TooltipPlacement.at(320, 180, x, y, 174, 82);
			assertTrue(placement.x() >= 4 && placement.y() >= 4);
			assertTrue(placement.x() + 174 <= 316 && placement.y() + 82 <= 176);
		}
	}
	@Test void zoomKeepsTheSameTerrainUnderCursorDuringEveryAnimationStep() {
		var view = new MapViewport(-250, 120, 1);
		double x = view.worldX(130, 640), z = view.worldZ(90, 360);
		view.zoomAt(130, 90, 1, 640, 360);
		for (int i = 0; i < 120; i++) {
			view.step(1 / 60.0, 640, 360, true);
			assertEquals(x, view.worldX(130, 640), 1e-8);
			assertEquals(z, view.worldZ(90, 360), 1e-8);
		}
		assertTrue(view.zoom > 1 && view.zoom < 2);
	}
	@Test void mapPanCancelsZoomAnchorAndCenteringAndZeroWheelDoesNothing() {
		var view = new MapViewport(20, -10, 1);
		view.zoomAt(50, 60, 0, 640, 360); view.step(.1, 640, 360, false);
		assertEquals(1, view.zoom);
		view.center(100, 300); view.pan(10, 20); view.step(.1, 640, 360, false);
		assertEquals(10, view.x); assertEquals(-30, view.z);
	}
	@Test void gadgetTextAlwaysContainsLatestValueEvenWhenUpdatesOutrunAnimation() {
		var labels = new AnimatedText(); long now = 1_000_000_000L;
		labels.update("fps", "120", now, true);
		for (int i = 1; i < 80; i++) {
			var frame = labels.update("fps", String.valueOf(i), now + i * 10_000_000L, true);
			assertEquals(String.valueOf(i), frame.current());
			assertTrue(frame.progress() >= 0 && frame.progress() <= 1);
		}
		assertEquals(1, labels.update("fps", "42", now + 900_000_000L, false).progress());
		for (int i = 0; i < 10000; i++) labels.update("id" + i, "x", now, true);
		assertTrue(labels.size() <= 96);
	}
	@Test void skyPaletteHandlesTheClockWithoutChangingWorldState() {
		assertEquals(0, SkyPalette.angle(12));
		assertEquals(1, SkyPalette.daylight(12));
		assertEquals(0, SkyPalette.daylight(0));
		assertNotEquals(SkyPalette.color(0), SkyPalette.color(12));
		assertEquals(0xFF0000, SkyPalette.blend(0xFF0000, 0x0000FF, -10));
		assertEquals(0x0000FF, SkyPalette.blend(0xFF0000, 0x0000FF, 20));
	}
}
