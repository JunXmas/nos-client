package dev.jun.nosclient.cosmetic;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CosmeticMeshTest {
	private CosmeticMesh load(String theme, String slot) throws Exception {
		try (var in = getClass().getResourceAsStream("/assets/nosclient/cosmetics/" + theme + "/" + slot + ".json")) {
			assertNotNull(in);
			return new CosmeticMesh(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
		}
	}
	@Test void deliveredSetsLoadAndPreserveTriangleBudgets() throws Exception {
		for (String theme : new String[]{"angel", "crimson"}) {
			int triangles = 0;
			for (String slot : new String[]{"wings", "cape", "headband"}) {
				float[] vertices = load(theme, slot).bake(.8, false).vertices();
				assertEquals(0, vertices.length % 32);
				triangles += vertices.length / 32;
				for (int p = 0; p < vertices.length; p += 8) {
					for (int i = 0; i < 8; i++) assertTrue(Float.isFinite(vertices[p + i]));
					assertTrue(vertices[p + 3] >= 0 && vertices[p + 3] <= 1);
					assertTrue(vertices[p + 4] >= 0 && vertices[p + 4] <= 1);
				}
			}
			assertEquals(theme.equals("angel") ? 904 : 676, triangles);
		}
	}
	@Test void wingLoopsAreSeamlessAndContinueAfterHours() throws Exception {
		for (String theme : new String[]{"angel", "crimson"}) {
			CosmeticMesh mesh = load(theme, "wings");
			float[] a = mesh.bake(0, false).vertices().clone();
			float[] b = mesh.bake(3.2, false).vertices();
			assertArrayEquals(a, b, 1e-5f);
			assertArrayEquals(mesh.bake(.8, false).vertices(), mesh.bake(28_800.8, false).vertices(), 1e-5f);
			float[] c = mesh.bake(1.6, false).vertices();
			double max = 0;
			for (int i = 0; i < a.length; i += 8) for (int k = 0; k < 3; k++) max = Math.max(max, Math.abs(a[i + k] - c[i + k]));
			assertTrue(max > .1, "Wing must deform rather than remain at its bind pose");
		}
	}
	@Test void deferredRenderSnapshotsRemainUnchangedByLaterFrames() throws Exception {
		CosmeticMesh mesh = load("angel", "wings");
		var pose = mesh.pose(1, false);
		assertSame(pose, mesh.pose(1.001, false));
		var captured = new org.joml.Matrix4f(pose.matrices()[3]);
		mesh.bake(2, false); mesh.bake(2.1, true);
		assertEquals(captured, pose.matrices()[3]);
	}
	@Test void malformedRigFailsBeforeRendering() {
		var doc = JsonParser.parseString("""
			{"format":1,"fps":60,"duration":3.2,"texture":"nosclient:textures/test.png","nodes":[
			{"parent":0,"translation":[0,0,0],"rotation":[0,0,0,1],"vertices":[]}]}
			""").getAsJsonObject();
		assertThrows(IllegalArgumentException.class, () -> new CosmeticMesh(doc));
	}
	@Test void submittedVerticesUseMinecraftShoulderOriginAndReflectedUpAxis() throws Exception {
		for (String theme : new String[]{"angel", "crimson"}) for (String slot : new String[]{"wings", "cape", "headband"}) {
			CosmeticMesh mesh = load(theme, slot);
			float[] reference = mesh.bake(.8, false).vertices();
			var stack = new com.mojang.blaze3d.vertex.PoseStack();
			stack.translate(0, 1.5, 0); stack.scale(1, -1, 1);
			var vertices = new java.util.ArrayList<Float>();
			var writer = new com.mojang.blaze3d.vertex.VertexConsumer() {
				public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) { vertices.add(x); vertices.add(y); vertices.add(z); return this; }
				public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) { return this; }
				public com.mojang.blaze3d.vertex.VertexConsumer setColor(int color) { return this; }
				public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) { vertices.add(u); vertices.add(v); return this; }
				public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { return this; }
				public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { return this; }
				public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { vertices.add(x); vertices.add(y); vertices.add(z); return this; }
				public com.mojang.blaze3d.vertex.VertexConsumer setLineWidth(float width) { return this; }
			};
			mesh.emit(mesh.pose(.8, false), stack.last(), writer, 0xF000F0);
			assertEquals(reference.length, vertices.size());
			for (int p = 0; p < reference.length; p += 8) {
				float[] expected = {reference[p], 1.5f - reference[p + 1], reference[p + 2], reference[p + 3], reference[p + 4], reference[p + 5], -reference[p + 6], reference[p + 7]};
				for (int k = 0; k < 8; k++) assertEquals(expected[k], vertices.get(p + k), 1e-5f, theme + "/" + slot + " vertex " + p);
			}
		}
	}
}
