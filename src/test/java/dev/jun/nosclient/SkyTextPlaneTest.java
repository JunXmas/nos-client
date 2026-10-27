package dev.jun.nosclient;

import dev.jun.nosclient.media.CelestialTypography;
import dev.jun.nosclient.media.SkyTextPlane;
import dev.jun.nosclient.typography.Typography;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkyTextPlaneTest {
	@Test void planeFacesItsSkyOriginAtEveryBearingAndElevation() {
		for (int bearing = 0; bearing < 360; bearing += 15) for (int elevation : new int[]{15, 40, 65, 80}) {
			var rotation = SkyTextPlane.rotation(bearing, elevation);
			var normal = new Vector3f(0, 0, 1).rotate(rotation);
			assertEquals(-1, normal.dot(SkyTextPlane.direction(bearing, elevation)), .00001);
			assertEquals(1, new Vector3f(0, 1, 0).rotate(rotation).length(), .00001);
		}
	}
	@Test void lookingAwayChangesTheViewOfThePlaneWithoutTurningThePlaneItself() {
		var sky = SkyTextPlane.rotation(0, 40);
		var worldRight = new Vector3f(1, 0, 0).rotate(sky);
		for (int cameraYaw : new int[]{-70, -25, 0, 25, 70}) {
			var camera = new Quaternionf().rotationYXZ((float) Math.toRadians(180 - cameraYaw), (float) Math.toRadians(40), 0);
			var rightInView = new Vector3f(worldRight).rotate(new Quaternionf(camera).conjugate());
			if (cameraYaw == 0) assertEquals(1, rightInView.x(), .00001);
			else assertTrue(Math.abs(rightInView.z()) > .1);
			assertEquals(worldRight, new Vector3f(1, 0, 0).rotate(SkyTextPlane.rotation(0, 40)));
		}
	}
	@Test void tallWorldTextGetsItsOwnBoundedHighDensityAtlases() {
		for (int screen : new int[]{720, 900, 1080, 1440, 2160}) {
			double normal = SkyTextPlane.density(screen, 70, 1, 1.18, 1);
			double tall = SkyTextPlane.density(screen, 70, 1, 1.18, 1.45);
			assertTrue(tall > normal);
			int tier = Typography.worldDensityIndex(tall);
			assertTrue(Typography.WORLD_DENSITIES[tier] / 2.0 >= Math.min(16, tall));
		}
		assertEquals(8, Typography.worldDensityIndex(100));
		assertEquals(5, Typography.densityIndex(100));
	}
	@Test void whiteCoreAndContrastingEdgeStayClearInDayNightAndUnusualThemes() {
		for (int sky : new int[]{0, 0xFF101B2B, 0xFF80AADD, 0xFFFFFFFF}) for (int text : new int[]{0, 0xFFFFFFFF, 0xFFFF0000}) {
			var ink = CelestialTypography.ink(sky, text, 0xFFA6CEB3, true);
			assertTrue(CelestialTypography.ink(0xFFFFFFFF, text, 0xFFA6CEB3, true).edgeOpacity() >= ink.edgeOpacity());
			assertTrue(dev.jun.nosclient.theme.ThemePalette.luminance(ink.body()) > .85);
			assertTrue(dev.jun.nosclient.theme.ThemePalette.contrast(ink.body(), ink.edge()) >= 7);
		}
		assertTrue(CelestialTypography.ink(0xFF101B2B, 0xFFFFFFFF, 0xFFA6CEB3, true).haloOpacity() > .03f);
		assertTrue(CelestialTypography.ink(0xFFFFFFFF, 0xFFFFFFFF, 0xFFA6CEB3, true).haloOpacity() < .1f);
	}
}
