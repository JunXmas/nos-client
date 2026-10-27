package dev.jun.nosclient;

import dev.jun.nosclient.gui.HubMotion;
import dev.jun.nosclient.gui.IconGeometry;
import dev.jun.nosclient.gui.UiTransform;
import dev.jun.nosclient.gui.RenderTween;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HubPresentationTest {
	@Test void allArtworkFitsInsidePaddedCellsWithoutStretching() {
		for (int index = 0; index < 32; index++) {
			var crop = IconGeometry.crop(index);
			assertTrue(crop.x() >= 0 && crop.y() >= 0);
			assertTrue(crop.x() + crop.width() <= IconGeometry.SOURCE_WIDTH);
			assertTrue(crop.y() + crop.height() <= IconGeometry.SOURCE_HEIGHT);
			for (int size : IconGeometry.DENSITIES) {
				var fit = IconGeometry.fit(crop, size);
				assertTrue(fit.x() > 0 && fit.y() > 0);
				assertTrue(fit.x() + fit.width() < size && fit.y() + fit.height() < size);
				assertTrue(Math.abs(fit.width() * (double) crop.height() / crop.width() - fit.height()) < 1.5);
				assertTrue(Math.abs((fit.x() + fit.width() / 2.0) - size / 2.0) <= .5);
			}
		}
		assertTrue(IconGeometry.crop(14).width() > IconGeometry.crop(14).height());
	}
	@Test void smallIconsUsePrefilteredAtlasAndHighDpiStaysBounded() {
		assertEquals(0, IconGeometry.tier(12)); assertEquals(2, IconGeometry.tier(36));
		assertEquals(3, IconGeometry.tier(114)); assertEquals(3, IconGeometry.tier(800));
	}
	@Test void pointerTracksEveryPositionDuringOpeningAndPaneTravel() {
		for (float progress = 0; progress <= 1; progress += .01f) {
			var transform = UiTransform.opening(progress, 360, 225).translated(16 * (1 - progress), 3 * (1 - progress));
			for (float x : new float[]{15, 228, 490}) for (float y : new float[]{25, 190, 390}) {
				assertEquals(x, transform.localX(x * transform.scale() + transform.x()), .0001);
				assertEquals(y, transform.localY(y * transform.scale() + transform.y()), .0001);
			}
		}
	}
	@Test void paneEntranceStartsAtFirstFrameAndRapidSwitchUsesLatestDirection() {
		var motion = new HubMotion(); motion.enter(1);
		var first = motion.frame(8_000_000_000L, true);
		assertEquals(0, first.opacity()); assertEquals(22, first.x());
		var middle = motion.frame(8_120_000_000L, true);
		assertTrue(middle.opacity() > 0 && middle.opacity() < 1);
		motion.enter(-1); assertEquals(-22, motion.frame(8_130_000_000L, true).x());
		var done = motion.frame(8_380_000_000L, true);
		for (int i = 1; i <= 20; i++) done = motion.frame(8_380_000_000L + i * 16_000_000L, true);
		assertEquals(1, done.opacity()); assertEquals(0, done.x(), .00001);
		motion.enter(1); assertEquals(1, motion.frame(9_000_000_000L, false).opacity());
		assertEquals(0, motion.frame(9_010_000_000L, true).x());
	}
	@Test void expensiveFirstFrameStillShowsEntranceAndDisablingMotionDoesNotReplayItLater() {
		var tween = new RenderTween(300_000_000L); tween.restart();
		assertEquals(0, tween.value(10_000_000_000L, true));
		float next = tween.value(11_000_000_000L, true);
		assertTrue(next > 0 && next < 1);
		assertEquals(1, tween.value(11_010_000_000L, false));
		assertEquals(1, tween.value(11_020_000_000L, true));
	}
}
