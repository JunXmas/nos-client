package dev.jun.nosclient.cosmetic;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Immutable rig/keys, baked once per shared time slice and submitted as one material batch. */
public final class CosmeticMesh {
	private record Node(int parent, float[] translation, float[] rest, float[] keys, float[] vertices) {}
	public record Baked(float[] vertices) {}
	public record RigPose(Matrix4f[] matrices) {}
	public final Identifier texture;
	private final List<Node> nodes;
	private final double duration;
	private final int fps;
	private long lastSlice = Long.MIN_VALUE;
	private int lastRate;
	private RigPose lastPose;

	CosmeticMesh(JsonObject json) {
		if (json.get("format").getAsInt() != 1) throw new IllegalArgumentException("Unsupported cosmetic format");
		texture = Identifier.parse(json.get("texture").getAsString());
		fps = json.get("fps").getAsInt(); duration = json.get("duration").getAsDouble();
		if (fps != 60 || duration <= 0 || duration > 20) throw new IllegalArgumentException("Invalid animation timing");
		nodes = new ArrayList<>();
		for (var entry : json.getAsJsonArray("nodes")) {
			var n = entry.getAsJsonObject();
			Node node = new Node(n.get("parent").getAsInt(), floats(n, "translation"), floats(n, "rotation"),
				n.has("keys") ? floats(n, "keys") : new float[0], floats(n, "vertices"));
			if (node.vertices.length % 32 != 0 || node.translation.length != 3 || node.rest.length != 4
				|| node.parent < -1 || node.parent >= json.getAsJsonArray("nodes").size()
				|| node.keys.length != 0 && node.keys.length != (Math.round(duration * fps) + 1) * 4)
				throw new IllegalArgumentException("Invalid cosmetic rig data");
			nodes.add(node);
		}
		if (nodes.isEmpty() || nodes.size() > 128) throw new IllegalArgumentException("Cosmetic rig exceeds limits");
		// Resolve once during loading to reject cycles before entering a render callback.
		Matrix4f[] matrices = new Matrix4f[nodes.size()];
		for (int i = 0; i < nodes.size(); i++) matrix(i, 0, matrices, new boolean[nodes.size()]);
	}
	private static float[] floats(JsonObject object, String name) {
		var array = object.getAsJsonArray(name); float[] out = new float[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).getAsFloat();
			if (!Float.isFinite(out[i])) throw new IllegalArgumentException("Non-finite cosmetic vertex");
		}
		return out;
	}
	private Matrix4f matrix(int index, double time, Matrix4f[] matrices, boolean[] visiting) {
		if (matrices[index] != null) return matrices[index];
		if (visiting[index]) throw new IllegalArgumentException("Cyclic cosmetic rig");
		visiting[index] = true;
		Node n = nodes.get(index);
		Quaternionf q;
		if (n.keys.length == 0) q = new Quaternionf(n.rest[0], n.rest[1], n.rest[2], n.rest[3]);
		else {
			double frame = (time % duration + duration) % duration * fps;
			int a = (int) frame, b = Math.min(a + 1, n.keys.length / 4 - 1);
			int p = a * 4, r = b * 4;
			q = new Quaternionf(n.keys[p], n.keys[p + 1], n.keys[p + 2], n.keys[p + 3]);
			q.slerp(new Quaternionf(n.keys[r], n.keys[r + 1], n.keys[r + 2], n.keys[r + 3]), (float) (frame - a)).normalize();
		}
		Matrix4f m = n.parent < 0 ? new Matrix4f() : new Matrix4f(matrix(n.parent, time, matrices, visiting));
		m.translate(n.translation[0], n.translation[1], n.translation[2]).rotate(q);
		visiting[index] = false; matrices[index] = m; return m;
	}
	public RigPose pose(double seconds, boolean distant) {
		int rate = distant ? 15 : 120;
		long slice = (long) Math.floor(seconds * rate + 1e-7);
		if (lastPose != null && slice == lastSlice && rate == lastRate) return lastPose;
		Matrix4f[] matrices = new Matrix4f[nodes.size()]; boolean[] visiting = new boolean[nodes.size()];
		for (int i = 0; i < nodes.size(); i++) matrix(i, slice / (double) rate, matrices, visiting);
		lastSlice = slice; lastRate = rate; lastPose = new RigPose(matrices); return lastPose;
	}
	/** Small immutable matrix snapshot; vertex buffers stay static even at high frame rates. */
	public void emit(RigPose snapshot, PoseStack.Pose captured, VertexConsumer out, int light) {
		Vector3f normal = new Vector3f();
		for (int i = 0; i < nodes.size(); i++) {
			float[] v = nodes.get(i).vertices;
			if (v.length == 0) continue;
			Matrix4f position = new Matrix4f(captured.pose()).mul(snapshot.matrices[i]);
			Matrix3f directions = new Matrix3f(captured.normal()).mul(new Matrix3f(snapshot.matrices[i]));
			for (int p = 0; p < v.length; p += 8) {
				directions.transform(v[p + 5], v[p + 6], v[p + 7], normal).normalize();
				out.addVertex(position, v[p], v[p + 1], v[p + 2]).setColor(0xFFFFFFFF)
					.setUv(v[p + 3], v[p + 4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
					.setNormal(normal.x, normal.y, normal.z);
			}
		}
	}
	/** CPU export for geometry verification/adapters; the game renders with emit(). */
	public Baked bake(double seconds, boolean distant) {
		RigPose snapshot = pose(seconds, distant);
		int size = nodes.stream().mapToInt(n -> n.vertices.length).sum();
		float[] output = new float[size]; int cursor = 0;
		Vector3f position = new Vector3f(), normal = new Vector3f();
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i); Matrix4f m = snapshot.matrices[i];
			for (int p = 0; p < n.vertices.length; p += 8) {
				m.transformPosition(n.vertices[p], n.vertices[p + 1], n.vertices[p + 2], position);
				m.transformDirection(n.vertices[p + 5], n.vertices[p + 6], n.vertices[p + 7], normal).normalize();
				output[cursor++] = position.x; output[cursor++] = position.y; output[cursor++] = position.z;
				output[cursor++] = n.vertices[p + 3]; output[cursor++] = n.vertices[p + 4];
				output[cursor++] = normal.x; output[cursor++] = normal.y; output[cursor++] = normal.z;
			}
		}
		return new Baked(output);
	}
}
