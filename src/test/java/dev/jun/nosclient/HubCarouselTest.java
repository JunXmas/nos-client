package dev.jun.nosclient;

import dev.jun.nosclient.gui.HubCarousel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HubCarouselTest {
	@Test void chosenCardArrivesBeforePaneOpensAndRapidRetargetingKeepsItsVisiblePosition() {
		var c = new HubCarousel(); c.snap(3); c.select(2); long now = 1_000_000_000;
		var f = c.frame(now, true); assertEquals(3, f.cursor()); assertEquals(0, f.open()); assertEquals(1, f.expansion()[3]);
		for (int i = 0; i < 3; i++) f = c.frame(now += 16_000_000, true);
		assertTrue(f.cursor() > 2 && f.cursor() < 3); assertEquals(0, f.open(), .02);
		// Đổi đích giữa đường: lò xo giữ nguyên chỗ đang hiện, không nhảy về.
		double previous = f.cursor(); c.select(4); f = c.frame(now += 1_000_000, true); assertEquals(previous, f.cursor(), .02);
		for (int i = 0; i < 80; i++) {
			f = c.frame(now += 16_000_000, true);
			// Thẻ chỉ bắt đầu nở khi vòng đã gần tới nơi (gối pha, không tuần tự).
			if (f.open() > 0.05) assertEquals(4, HubCarousel.relative(f.cursor() - 4) + 4, .3);
		}
		assertFalse(f.moving()); assertEquals(1, f.open(), .001); assertEquals(4, f.cursor(), .001);
	}
	@Test void wrapUsesShortestArcAndReducedMotionDoesNotReplay() {
		var c = new HubCarousel(); c.snap(4); c.select(0); var f = c.frame(1, true);
		for (int i = 1; i <= 5; i++) f = c.frame(1 + i * 50_000_000L, true);
		assertTrue(f.cursor() > 4 && f.cursor() < 5);
		f = c.frame(500_000_000, false); assertEquals(0, f.cursor()); assertEquals(1, f.open());
		assertFalse(c.frame(501_000_000, true).moving());
	}
	@Test void allRestingCardsFitWideAndCompactViewportsAndExpansionMatchesPane() {
		for (int viewport : new int[]{360, 480, 600, 720, 960}) for (int selected = 0; selected < 5; selected++) {
			var c = new HubCarousel(); c.snap(selected); var f = c.frame(0, true);
			int w = 296, h = 196, x = (viewport - w) / 2, y = 52;
			for (int i = 0; i < 5; i++) {
				var card = HubCarousel.card(f, i, viewport, x, y, w, h);
				assertTrue(card.x() >= 0); assertTrue(card.x() + card.width() <= viewport);
				assertTrue(card.y() >= 0);
				if (viewport >= 600 && i != selected) assertTrue(card.x() + card.width() <= x - 8 || card.x() >= x + w + 8);
				if (i == selected) { assertEquals(x, card.x()); assertEquals(y, card.y()); assertEquals(w, card.width()); assertEquals(h, card.height()); }
			}
		}
	}
}
