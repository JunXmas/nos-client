package dev.jun.nosclient.cosmetic;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** tools/bbmodel_to_nos.py output for tools/samples/test-hat.bbmodel: an 8x2x8 px slab 8 px above a pivot at 24 px. */
final class BlockbenchImportTest {
	private CosmeticMesh hat() throws Exception { return load("test-hat"); }
	private CosmeticMesh load(String name) throws Exception {
		try (var in = getClass().getResourceAsStream("/cosmetic/" + name + ".json")) {
			return new CosmeticMesh(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
		}
	}
	private static float[] bounds(float[] v) {
		float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		for (int p = 0; p < v.length; p += 8) for (int a = 0; a < 3; a++) { b[a] = Math.min(b[a], v[p + a]); b[a + 3] = Math.max(b[a + 3], v[p + a]); }
		return b;
	}
	@Test void pixelsBecomeBlocksAtTheRightHeight() throws Exception {
		float[] b = bounds(hat().bake(0, false).vertices());
		assertArrayEquals(new float[]{-.25f, 2f, -.25f, .25f, 2.125f, .25f}, b, 1e-5f);
	}
	@Test void groupRotationKeysTurnTheSlabAroundItsPivot() throws Exception {
		CosmeticMesh mesh = hat();
		float[] start = mesh.bake(0, false).vertices().clone();
		float[] quarter = mesh.bake(.5, false).vertices();
		// 90° about +Y maps (x, z) to (z, -x); height is unchanged.
		for (int p = 0; p < start.length; p += 8) {
			assertEquals(start[p + 2], quarter[p], 1e-4f);
			assertEquals(-start[p], quarter[p + 2], 1e-4f);
			assertEquals(start[p + 1], quarter[p + 1], 1e-4f);
		}
		assertArrayEquals(start, mesh.bake(2, false).vertices(), 1e-4f);
	}
	@Test void everyFaceKeepsOutwardNormalsAndInRangeUv() throws Exception {
		float[] v = hat().bake(0, false).vertices();
		assertEquals(6 * 2 * 4 * 8, v.length);
		for (int p = 0; p < v.length; p += 8) {
			float cx = v[p], cy = v[p + 1] - 2.0625f, cz = v[p + 2];
			assertTrue(cx * v[p + 5] + cy * v[p + 6] + cz * v[p + 7] > 0, "normal points inward at vertex " + p / 8);
			assertTrue(v[p + 3] >= 0 && v[p + 3] <= 1 && v[p + 4] >= 0 && v[p + 4] <= 1);
		}
	}
	@Test void restRotationFollowsBlockbenchZyxOrder() throws Exception {
		// Group at rest rotation [30, 60, 45]; Blockbench applies X, then Y, then Z (three.js order "ZYX").
		float[] v = load("test-tilt").bake(0, false).vertices();
		org.joml.Matrix4f expected = new org.joml.Matrix4f().translate(0, 1.5f, 0)
			.rotateZYX((float) Math.toRadians(45), (float) Math.toRadians(60), (float) Math.toRadians(30));
		org.joml.Vector3f corner = new org.joml.Vector3f();
		float[][] box = {{-.25f, .5f, -.25f}, {.25f, .625f, .25f}};
		for (int p = 0, i = 0; p < v.length; p += 8, i++) {
			boolean seen = false;
			for (int c = 0; c < 8 && !seen; c++) {
				expected.transformPosition(box[c & 1][0], box[c >> 1 & 1][1], box[c >> 2 & 1][2], corner);
				seen = corner.distance(v[p], v[p + 1], v[p + 2]) < 1e-4f;
			}
			assertTrue(seen, "vertex " + i + " is not a rotated slab corner");
		}
	}
}
