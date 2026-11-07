package dev.jun.nosclient.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The starter set built by tools/cosmetic_starter.py loads, stays within budget and sits where its slot says. */
final class StarterSetTest {
	static final String[][] ITEMS = {
		{"cap", "head"}, {"shades", "face"}, {"fox_ears", "head"}, {"crown", "head"},
		{"halo", "head"}, {"scarf", "neck"}, {"backpack", "back"}, {"pixel_wings", "back"}};
	static CosmeticMesh load(String key) throws Exception {
		try (var in = StarterSetTest.class.getResourceAsStream("/assets/nosclient/cosmetics/starter/" + key + ".json")) {
			assertNotNull(in, key);
			return new CosmeticMesh(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
		}
	}
	@Test void everyItemLoadsWithinBudgetAndAroundItsSlot() throws Exception {
		for (String[] item : ITEMS) {
			float[] v = load(item[0]).bake(.3, false).vertices();
			assertTrue(v.length > 0 && v.length / 32 <= 400, item[0] + " triangles " + v.length / 32);
			float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
			for (int p = 0; p < v.length; p += 8) {
				minY = Math.min(minY, v[p + 1]); maxY = Math.max(maxY, v[p + 1]);
				minZ = Math.min(minZ, v[p + 2]); maxZ = Math.max(maxZ, v[p + 2]);
			}
			switch (item[1]) {
				case "head", "face" -> assertTrue(minY >= 1.5f && maxY <= 2.4f, item[0] + " y " + minY + ".." + maxY);
				case "neck" -> assertTrue(minY >= .85f && maxY <= 1.6f, item[0] + " y " + minY + ".." + maxY);
				case "back" -> assertTrue(minZ >= -.2f && maxZ <= .45f && minY >= .8f, item[0] + " z " + minZ + ".." + maxZ);
				default -> fail(item[1]);
			}
			if (item[1].equals("face")) assertTrue(maxZ <= 0 + 1e-4f, item[0] + " must sit on the face (-Z)");
		}
	}
	/** Writes baked frames for tools/render_cosmetics.py; set NOS_COSMETIC_DUMP to a directory. */
	@Test void dumpForPreview() throws Exception {
		String target = System.getenv("NOS_COSMETIC_DUMP");
		if (target == null) return;
		JsonObject out = new JsonObject();
		for (String[] item : ITEMS) {
			CosmeticMesh mesh = load(item[0]);
			JsonArray frames = new JsonArray();
			for (double t : new double[]{0, .4, .8}) {
				JsonArray v = new JsonArray();
				for (float f : mesh.bake(t, false).vertices()) v.add(f);
				frames.add(v);
			}
			JsonObject entry = new JsonObject(); entry.addProperty("slot", item[1]); entry.add("frames", frames);
			out.add(item[0], entry);
		}
		Files.writeString(Path.of(target, "baked.json"), out.toString());
	}
}
