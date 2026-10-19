package dev.jun.nosclient.scene;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cảnh hồ của màn chính: địa hình, cây, mía, hoa, núi xa — dựng thành một lưới tĩnh MỘT lần.
 * Port thẳng từ preview HTML (~/nostalgia-hud-preview/preview.html) để mod ra đúng ảnh đã duyệt.
 *
 * Mỗi đỉnh 14 float: vị trí 3 · uv 2 · bóng 1 · tâm 3 · hoạt ảnh 4 (nhóm, trễ mọc, kiểu, trễ tàn) · lắc 1.
 * Cây/mía mọc và tàn hoàn toàn trong shader đỉnh theo uniform thời gian, nên bật/tắt không dựng lại lưới.
 */
final class World {
	static final int STRIDE = 14;
	static final int X0 = -80, X1 = 80, Z0 = -72, Z1 = 24, MS = 2, MX = 208, MZ0 = -176;
	static final double LX = 1, LZ = -4;
	static final float WY = -.125f; // mặt nước thấp hơn mặt khối 2/16 như game

	/** Thứ tự ô trong atlas 256×256 (16 ô một hàng); renderer đọc đúng thứ tự này khi ghép atlas. */
	static final String[] TILES = {"gtop", "gside", "dirt", "sand", "stone", "snow", "gsnow", "clog", "clogt", "cleaf", "cane", "poppy", "dand", "azure",
		"grassp", "sap", "ppet", "lily", "oakl", "oakt", "oakf", "birl", "birt", "birf", "tulr", "tulo", "tulp", "tulw", "corn", "alli", "daisy", "lotv",
		"tgb", "tgt", "fern", "lilb", "lilt", "roseb", "roset", "peob", "peot", "glint", "bone", "fly",
		"p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9", "p10", "p11"};

	static int tileSize(String n) {
		return n.matches("p\\d+") ? 3 : n.equals("glint") || n.equals("fly") ? 8 : 16;
	}

	private static final Map<String, float[]> UV = new HashMap<>();
	static {
		UV.put("full", new float[]{0, 0, 1, 1});
		for (int i = 0; i < TILES.length; i++) {
			int x = (i % 16) * 16, y = (i / 16) * 16, s = tileSize(TILES[i]);
			float e = .01f;
			UV.put(TILES[i], new float[]{(x + e) / 256, (y + e) / 256, (x + s - e) / 256, (y + s - e) / 256});
		}
	}

	static float[] uv(String tile) {
		return UV.get(tile);
	}

	// ---------- địa hình ----------
	static double lerp(double a, double b, double k) {
		return a + (b - a) * k;
	}

	static double sstep(double a, double b, double v) {
		double k = Math.min(1, Math.max(0, (v - a) / (b - a)));
		return k * k * (3 - 2 * k);
	}

	static double hash(double x, double z) {
		double h = Math.sin(x * 127.1 + z * 311.7) * 43758.5453;
		return h - Math.floor(h);
	}

	static double vnoise(double x, double z) {
		double xi = Math.floor(x), zi = Math.floor(z), fx = s3(x - xi), fz = s3(z - zi);
		return lerp(lerp(hash(xi, zi), hash(xi + 1, zi), fx), lerp(hash(xi, zi + 1), hash(xi + 1, zi + 1), fx), fz);
	}

	private static double s3(double t) {
		return t * t * (3 - 2 * t);
	}

	static double lakeE(double x, double z) {
		double a = (x + .5 - LX) / 9.5, b = (z + .5 - LZ) / 5.8;
		return a * a + b * b;
	}

	static boolean isW(int x, int z) {
		return lakeE(x, z) < 1;
	}

	private static final int[][] N8 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	static boolean isShore(int x, int z) {
		if (isW(x, z)) return false;
		for (int[] d : N8) if (isW(x + d[0], z + d[1])) return true;
		return false;
	}

	private final Map<Long, Integer> hm = new HashMap<>();

	int hgt(int x, int z) {
		long k = ((long) x << 32) ^ (z & 0xffffffffL);
		Integer c = hm.get(k);
		if (c != null) return c;
		double e = lakeE(x, z);
		int h = e < 1 ? (e < .3 ? -3 : e < .65 ? -2 : -1)
			: (int) Math.floor(Math.min(1, Math.max(0, (-z - 12) / 12.0)) * (1 + 5 * vnoise(x * .09, z * .09)));
		// dãy núi: nhiễu dạng sống núi, mọc dần từ z=-60; giữa hạ thấp thành đèo cho mặt trời lặn vào
		double ridge = 1 - Math.abs(2 * vnoise(x * .028 + 7, z * .028) - 1);
		double m = sstep(-78, -150, z) * (1 - .55 * (1 - sstep(20, 60, Math.abs(x - 6)))) + sstep(95, 150, Math.abs(x)) * sstep(-20, -60, z) * .6;
		if (m > 0 && e >= 1) h += (int) Math.floor(m * (5 + 34 * ridge * ridge + 6 * vnoise(x * .09, z * .09)));
		hm.put(k, h);
		return h;
	}

	private long srng = 5;

	private double sr() {
		srng = srng * 16807 % 2147483647;
		return srng / 2147483647.0;
	}

	// ---------- lưới ----------
	static final class FloatList {
		float[] a = new float[1 << 16];
		int n;

		void add(float v) {
			if (n == a.length) a = java.util.Arrays.copyOf(a, n * 2);
			a[n++] = v;
		}

		float[] toArray() {
			return java.util.Arrays.copyOf(a, n);
		}
	}

	/** Tâm xoay/co (pv), hoạt ảnh (an), độ lắc theo gió cho 4 đỉnh (sw). */
	record Opt(double[] pv, double[] an, double[] sw) {
		static final Opt NONE = new Opt(new double[3], new double[4], new double[4]);

		static Opt pv(double x, double y, double z) {
			return new Opt(new double[]{x, y, z}, new double[4], new double[4]);
		}

		Opt an(double a, double b, double c, double d) {
			return new Opt(pv, new double[]{a, b, c, d}, sw);
		}

		Opt sw(double a, double b, double c, double d) {
			return new Opt(pv, an, new double[]{a, b, c, d});
		}
	}

	private static final int[] ORD_A = {0, 1, 2, 0, 2, 3}, ORD_B = {1, 2, 3, 1, 3, 0};

	static void quad(FloatList m, double[][] P, String tile, float[] shade4, float shade, Opt o, int rot) {
		float[] r = uv(tile);
		float[][] u = {{r[0], r[1]}, {r[2], r[1]}, {r[2], r[3]}, {r[0], r[3]}};
		// lật đường chéo theo AO như game, để vệt tối không bị gãy
		int[] ord = shade4 != null && shade4[0] + shade4[2] < shade4[1] + shade4[3] ? ORD_B : ORD_A;
		for (int i : ord) {
			double[] p = P[i];
			float[] t = u[(i + rot) % 4];
			m.add((float) p[0]); m.add((float) p[1]); m.add((float) p[2]);
			m.add(t[0]); m.add(t[1]);
			m.add(shade4 != null ? shade4[i] : shade);
			m.add((float) o.pv[0]); m.add((float) o.pv[1]); m.add((float) o.pv[2]);
			m.add((float) o.an[0]); m.add((float) o.an[1]); m.add((float) o.an[2]); m.add((float) o.an[3]);
			m.add((float) o.sw[i]);
		}
	}

	static void quad(FloatList m, double[][] P, String tile, float shade, Opt o) {
		quad(m, P, tile, null, shade, o, 0);
	}

	static final String[] FACES = {"px", "nx", "pz", "nz", "py", "ny"};
	private static final Map<String, int[][]> FC = Map.of(
		"px", new int[][]{{1, 1, 1}, {1, 1, 0}, {1, 0, 0}, {1, 0, 1}}, "nx", new int[][]{{0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {0, 0, 0}},
		"pz", new int[][]{{0, 1, 1}, {1, 1, 1}, {1, 0, 1}, {0, 0, 1}}, "nz", new int[][]{{1, 1, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}},
		"py", new int[][]{{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}}, "ny", new int[][]{{0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {0, 0, 0}});
	/** Bóng mặt như renderer khối của game: trên 1, Bắc/Nam .8, Đông/Tây .6, dưới .5. */
	private static final Map<String, Float> FS = Map.of("px", .6f, "nx", .6f, "pz", .8f, "nz", .8f, "py", 1f, "ny", .5f);
	private static final Map<String, int[]> NRM = Map.of("px", new int[]{1, 0, 0}, "nx", new int[]{-1, 0, 0}, "py", new int[]{0, 1, 0},
		"ny", new int[]{0, -1, 0}, "pz", new int[]{0, 0, 1}, "nz", new int[]{0, 0, -1});
	private static final float[] AOL = {.42f, .6f, .78f, 1};

	interface Solid {
		boolean at(int x, int y, int z);
	}

	/** AO như Smooth Lighting của game: mỗi đỉnh xét 2 khối cạnh + 1 khối góc ở lớp trước mặt. */
	private static float[] aoOf(int x, int y, int z, String f, Solid S) {
		int[] n = NRM.get(f);
		int ax = n[0] != 0 ? 0 : n[1] != 0 ? 1 : 2, t0 = ax == 0 ? 1 : 0, t1 = ax == 2 ? 1 : 2;
		int[] q = {x + n[0], y + n[1], z + n[2]};
		int[][] c4 = FC.get(f);
		float[] out = new float[4];
		for (int i = 0; i < 4; i++) {
			int[] c = c4[i], a = q.clone(), b = q.clone(), k = q.clone();
			int d0 = c[t0] != 0 ? 1 : -1, d1 = c[t1] != 0 ? 1 : -1;
			a[t0] += d0; b[t1] += d1; k[t0] += d0; k[t1] += d1;
			boolean s1 = S.at(a[0], a[1], a[2]), s2 = S.at(b[0], b[1], b[2]);
			out[i] = AOL[s1 && s2 ? 0 : 3 - (s1 ? 1 : 0) - (s2 ? 1 : 0) - (S.at(k[0], k[1], k[2]) ? 1 : 0)];
		}
		return out;
	}

	static double[][] corners(String f, double x, double y, double z) {
		int[][] c = FC.get(f);
		double[][] P = new double[4][];
		for (int i = 0; i < 4; i++) P[i] = new double[]{x + c[i][0], y + c[i][1], z + c[i][2]};
		return P;
	}

	void face(FloatList m, int x, int y, int z, String f, String tile, Opt o, int rot, Solid S) {
		float fs = FS.get(f);
		float[] sh = null;
		if (S != null) {
			sh = aoOf(x, y, z, f, S);
			for (int i = 0; i < 4; i++) sh[i] *= fs;
		}
		quad(m, corners(f, x, y, z), tile, sh, fs, o, rot);
	}

	/** Hai mặt chéo như model "cross" của game; s0/s1 = độ lắc chân/ngọn. */
	static void plant(FloatList m, double x, double y, double z, String tile, Opt o, double s0, double s1) {
		Opt q = o.sw(s1, s1, s0, s0);
		quad(m, new double[][]{{x + .1, y + 1, z + .1}, {x + .9, y + 1, z + .9}, {x + .9, y, z + .9}, {x + .1, y, z + .1}}, tile, null, .9f, q, 0);
		quad(m, new double[][]{{x + .9, y + 1, z + .1}, {x + .1, y + 1, z + .9}, {x + .1, y, z + .9}, {x + .9, y, z + .1}}, tile, null, .9f, q, 0);
	}

	static void flat(FloatList m, double x, double y, double z, String tile, Opt o, int rot) {
		quad(m, new double[][]{{x, y, z}, {x + 1, y, z}, {x + 1, y, z + 1}, {x, y, z + 1}}, tile, null, 1, o, rot);
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0xfffff)) | ((long) (y & 0xfffff) << 20) | ((long) (z & 0xfffff) << 40);
	}

	private final Set<Long> occ = new HashSet<>(), trunks = new HashSet<>();
	private final Solid TS = (x, y, z) -> y < hgt(x, z);
	private final Solid TRS = (x, y, z) -> occ.contains(key(x, y, z)) || y < hgt(x, z);

	record Tree(int x, int y, int z, double t0, List<int[]> leaves) {}

	final List<Tree> trees = new ArrayList<>();
	/** Mỗi bụi mía: x, y, z, trễ mọc. */
	final List<double[]> canes = new ArrayList<>();

	private record Log(int x, int y, int z, char ax, double d) {}

	private record Leaf(int x, int y, int z, double d) {}

	/**
	 * Cây anh đào theo cherry_trunk_placer + cherry_foliage_placer của game: thân 7–8, 1–2 cành chéo;
	 * mỗi đầu cành một tán 5 tầng (bán kính 1,2,3,3,2), 25% thủng góc, 1/6 lá rủ, 1/3 rủ dài thêm.
	 */
	private void cherry(FloatList m, int ox, int oz, long seed, double t0) {
		long[] s = {seed};
		java.util.function.DoubleSupplier r = () -> (s[0] = s[0] * 16807 % 2147483647) / 2147483647.0;
		int by = hgt(ox, oz), H = 7 + (r.getAsDouble() < .5 ? 1 : 0);
		Map<Long, Log> logs = new LinkedHashMap<>();
		Map<Long, Leaf> leaves = new LinkedHashMap<>();
		for (int y = 0; y < H; y++) logs.put(key(ox, by + y, oz), new Log(ox, by + y, oz, 'y', 1 + y * .09));
		List<double[]> att = new ArrayList<>();
		att.add(new double[]{ox, by + H, oz, 1 + H * .09});
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int i = 3; i > 0; i--) {
			int j = (int) (r.getAsDouble() * (i + 1));
			int[] tmp = dirs[i]; dirs[i] = dirs[j]; dirs[j] = tmp;
		}
		for (int b = 0, nb = r.getAsDouble() < .3 ? 1 : 2; b < nb; b++) {
			int dx = dirs[b][0], dz = dirs[b][1], sy = by + H - 4 - (int) (r.getAsDouble() * 2), L = 2 + (int) (r.getAsDouble() * 3),
				ey = by + H - 1 - (int) (r.getAsDouble() * 2), py = sy;
			double t = 1 + (sy - by) * .09;
			for (int i = 1; i <= L; i++) {
				int y = (int) Math.round(sy + (ey - sy) * (double) i / L);
				t += .1;
				for (int yy = py + 1; yy < y; yy++) logs.put(key(ox + dx * i, yy, oz + dz * i), new Log(ox + dx * i, yy, oz + dz * i, 'y', t));
				logs.put(key(ox + dx * i, y, oz + dz * i), new Log(ox + dx * i, y, oz + dz * i, dx != 0 ? 'x' : 'z', t));
				py = y;
			}
			att.add(new double[]{ox + dx * L, ey + 1, oz + dz * L, t});
		}
		java.util.function.Consumer<Leaf> leaf = l -> {
			long k = key(l.x, l.y, l.z);
			if (logs.containsKey(k)) return;
			Leaf o = leaves.get(k);
			if (o == null || o.d > l.d) leaves.put(k, l);
		};
		int[][] layers = {{2, 1, 0}, {1, 2, 0}, {0, 3, 0}, {-1, 3, 1}, {-2, 2, 1}};
		for (double[] at : att) {
			int ax = (int) at[0], ay = (int) at[1], az = (int) at[2];
			for (int[] ly : layers) {
				int dy = ly[0], R = ly[1];
				boolean hang = ly[2] == 1;
				for (int dx = -R; dx <= R; dx++)
					for (int dz = -R; dz <= R; dz++) {
						int a = Math.abs(dx), c = Math.abs(dz);
						boolean edge = a == R || c == R;
						if (dy == -1 && edge && r.getAsDouble() < .25) continue;
						if (R > 2 ? (a == R && c == R) || (a + c > R * 2 - 2 && r.getAsDouble() < .25) : (a == R && c == R && r.getAsDouble() < .25)) continue;
						double d = at[3] + .12 + (a + c + Math.abs(dy)) * .05 + r.getAsDouble() * .05;
						leaf.accept(new Leaf(ax + dx, ay + dy, az + dz, d));
						if (hang && edge && r.getAsDouble() < 1 / 6.0) {
							leaf.accept(new Leaf(ax + dx, ay + dy - 1, az + dz, d + .08));
							if (r.getAsDouble() < 1 / 3.0) leaf.accept(new Leaf(ax + dx, ay + dy - 2, az + dz, d + .16));
						}
					}
			}
		}
		occ.addAll(logs.keySet());
		occ.addAll(leaves.keySet());
		int top = by + H;
		List<int[]> lv = new ArrayList<>();
		for (Leaf l : leaves.values()) lv.add(new int[]{l.x, l.y, l.z});
		trees.add(new Tree(ox, by, oz, t0, lv));

		plant(m, ox, by, oz, "sap", Opt.pv(ox + .5, by, oz + .5).an(1, t0, 2, 0), 0, .03);
		for (Log g : logs.values()) {
			boolean vert = g.ax == 'y' && g.x == ox && g.z == oz;
			Opt o = (vert ? Opt.pv(g.x + .5, g.y, g.z + .5) : Opt.pv(g.x + .5, g.y + .5, g.z + .5)).an(1, t0 + g.d, vert ? 1 : 0, .35 + (top - g.y) * .04);
			for (String f : FACES) {
				boolean end = f.charAt(1) == g.ax;
				int rot = (g.ax == 'x' && f.charAt(1) != 'x') || (g.ax == 'z' && (f.equals("px") || f.equals("nx"))) ? 1 : 0;
				face(m, g.x, g.y, g.z, f, end ? "clogt" : "clog", o, rot, TRS);
			}
		}
		for (Leaf l : leaves.values()) {
			double w = .025;
			Opt o = Opt.pv(l.x + .5, l.y + .5, l.z + .5).an(1, t0 + l.d, 0, r.getAsDouble() * .3).sw(w, w, w, w);
			for (String f : FACES) face(m, l.x, l.y, l.z, f, "cleaf", o, 0, TRS);
		}
		for (int dx = -4; dx <= 4; dx++)
			for (int dz = -4; dz <= 4; dz++) { // thảm pink_petals dưới gốc
				int x = ox + dx, z = oz + dz;
				if (dx * dx + dz * dz > 14 || isW(x, z) || isShore(x, z) || hgt(x, z) != by || (dx == 0 && dz == 0) || r.getAsDouble() > .45) continue;
				Opt o = Opt.pv(x + .5, by, z + .5).an(1, t0 + 2 + r.getAsDouble() * .6, 0, r.getAsDouble() * .2);
				flat(m, x, by + .015, z, "ppet", o, (int) (r.getAsDouble() * 4));
			}
	}

	/** Sồi / bạch dương: thân thẳng + blob_foliage_placer của game (bán kính 2, cao 3; hai tầng trên bán kính 1). */
	private void blobTree(FloatList m, int ox, int oz, int H, String lg, String lt, String lf, long seed) {
		long[] s = {seed};
		java.util.function.DoubleSupplier r = () -> (s[0] = s[0] * 16807 % 2147483647) / 2147483647.0;
		int by = hgt(ox, oz);
		List<int[]> L = new ArrayList<>();
		trunks.add(key(ox, 0, oz));
		for (int y = 0; y < H; y++) occ.add(key(ox, by + y, oz));
		for (int i = 0; i >= -3; i--) {
			int j = 1 - (int) (i / 2.0), y = by + H + i; // trunc như JS
			for (int dx = -j; dx <= j; dx++)
				for (int dz = -j; dz <= j; dz++) {
					if (Math.abs(dx) == j && Math.abs(dz) == j && (r.getAsDouble() < .5 || i == 0)) continue;
					long k = key(ox + dx, y, oz + dz);
					if (occ.contains(k)) continue;
					occ.add(k);
					L.add(new int[]{ox + dx, y, oz + dz});
				}
		}
		for (int y = 0; y < H; y++) for (String f : FACES) face(m, ox, by + y, oz, f, f.charAt(1) == 'y' ? lt : lg, Opt.pv(ox + .5, by + y, oz + .5), 0, TRS);
		for (int[] p : L) {
			double w = .02;
			Opt o = Opt.pv(p[0] + .5, p[1] + .5, p[2] + .5).sw(w, w, w, w);
			for (String f : FACES) face(m, p[0], p[1], p[2], f, lf, o, 0, TRS);
		}
	}

	/** Mía: ô bờ sát nước (đúng luật đặt mía của game), mọc từng khối từ dưới lên. */
	private void buildCanes(FloatList m) {
		srng = 77;
		List<int[]> cand = new ArrayList<>();
		for (int x = X0; x < X1; x++)
			for (int z = Z0; z < Z1; z++)
				if (isShore(x, z) && (isW(x + 1, z) || isW(x - 1, z) || isW(x, z + 1) || isW(x, z - 1))) cand.add(new int[]{x, z});
		cand.sort((a, b) -> Double.compare(Math.atan2(a[1] - LZ, a[0] - LX), Math.atan2(b[1] - LZ, b[0] - LX)));
		for (int i = 0; i < cand.size(); i++) {
			int x = cand.get(i)[0], z = cand.get(i)[1];
			if (z > -1 && Math.abs(x - 1) < 8) continue; // bờ gần nằm trên đường máy quay trượt: mía ở đây che kín khung
			if (i % 3 != 0 || trees.stream().anyMatch(t -> Math.abs(t.x - x) + Math.abs(t.z - z) < 2)) continue;
			int n = canes.size(), h = 1 + (int) (sr() * 3), y = hgt(x, z);
			canes.add(new double[]{x, y, z, n * .11});
			for (int k = 0; k < h; k++)
				plant(m, x, y + k, z, "cane", Opt.pv(x + .5, y + k, z + .5).an(2, n * .11 + k * .32, 1, (h - 1 - k) * .1 + sr() * .08), k * .035, (k + 1) * .035);
		}
	}

	/** Núi xa: ô S×S khối (như LOD), đỉnh tuyết từ y=26, sườn dốc lộ đá. */
	private void mtnCell(FloatList m, int x, int z, int S) {
		int h = hgt(x, z);
		int[][] nb = {{S, 0}, {-S, 0}, {0, S}, {0, -S}};
		String[] nf = {"px", "nx", "pz", "nz"};
		int lo = Integer.MAX_VALUE;
		for (int[] d : nb) lo = Math.min(lo, hgt(x + d[0], z + d[1]));
		boolean snow = h >= 26, rock = !snow && (h >= 15 || h - lo >= 3);
		quad(m, new double[][]{{x, h, z}, {x + S, h, z}, {x + S, h, z + S}, {x, h, z + S}}, snow ? "snow" : rock ? "stone" : "gtop", 1, Opt.NONE);
		for (int i = 0; i < 4; i++) {
			int n = hgt(x + nb[i][0], z + nb[i][1]);
			if (n >= h) continue;
			int[][] c = FC.get(nf[i]);
			for (int y = Math.max(n, h - 12); y < h; y += S) {
				int y1 = Math.min(h, y + S);
				String t = y1 == h ? (snow ? "gsnow" : rock ? "stone" : "gside") : y >= h - 3 && !rock && !snow ? "dirt" : "stone";
				double[][] P = new double[4][];
				for (int k = 0; k < 4; k++) P[k] = new double[]{x + c[k][0] * S, c[k][1] != 0 ? y1 : y, z + c[k][2] * S};
				quad(m, P, t, FS.get(nf[i]), Opt.NONE);
			}
		}
	}

	/** Kết quả dựng: lưới đất (phần gần trước, núi xa sau — lượt bóng đổ chỉ vẽ phần gần) và lưới nước. */
	record Mesh(float[] land, int nearVerts, float[] water) {}

	Mesh build() {
		FloatList m = new FloatList(), w = new FloatList(), far = new FloatList();
		srng = 5;
		for (int x = -MX; x < MX; x += MS) for (int z = MZ0; z < Z1; z += MS) if (x < X0 || x >= X1 || z < Z0) mtnCell(far, x, z, MS);
		String[] sf = {"px", "nx", "pz", "nz"};
		int[][] sd = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int x = X0; x < X1; x++)
			for (int z = Z0; z < Z1; z++) {
				int h = hgt(x, z);
				boolean sand = isW(x, z) || isShore(x, z);
				if (h > 3) {
					mtnCell(m, x, z, 1);
					continue;
				}
				face(m, x, h - 1, z, "py", sand ? "sand" : "gtop", Opt.NONE, 0, TS);
				for (int i = 0; i < 4; i++)
					for (int y = Math.max(hgt(x + sd[i][0], z + sd[i][1]), h - 6); y < h; y++)
						face(m, x, y, z, sf[i], sand ? "sand" : y == h - 1 ? "gside" : "dirt", Opt.NONE, 0, TS);
				if (isW(x, z)) {
					quad(w, new double[][]{{x, WY, z}, {x + 1, WY, z}, {x + 1, WY, z + 1}, {x, WY, z + 1}}, "full", 1, Opt.NONE);
					if (sr() < .06) flat(m, x, WY + .02, z, "lily", Opt.NONE, (int) (sr() * 4));
				}
			}
		int[][] ch = {{-11, -9, 11}, {13, -10, 23}, {28, -5, 37}, {-19, -15, 51}, {22, -18, 63}, {-30, -24, 71}, {11, -27, 83}, {-21, 4, 97}};
		for (int i = 0; i < ch.length; i++) cherry(m, ch[i][0], ch[i][1], ch[i][2], i * .3);
		int[][] bt = {{-4, -23, 5, 0}, {34, -13, 6, 0}, {-35, -5, 5, 0}, {5, -35, 6, 0}, {41, 6, 5, 0}, {-27, -12, 6, 1}, {18, -31, 7, 1}, {-13, -31, 6, 1}, {33, -25, 6, 1}, {-41, 9, 7, 1}};
		for (int i = 0; i < bt.length; i++) {
			boolean b = bt[i][3] == 1;
			blobTree(m, bt[i][0], bt[i][1], bt[i][2], b ? "birl" : "oakl", b ? "birt" : "oakt", b ? "birf" : "oakf", 131 + i * 17L);
		}
		for (Tree t : trees) trunks.add(key(t.x, 0, t.z));
		buildCanes(m);
		for (double[] c : canes) trunks.add(key((int) c[0], 0, (int) c[2]));
		srng = 19;
		// cỏ và hoa mọc thành vạt theo nhiễu, mỗi vạt hoa một loại như biome đồng hoa
		String[] FL = {"tulr", "tulo", "tulp", "tulw", "corn", "alli", "daisy", "lotv", "poppy", "dand", "azure"};
		String[][] DBL = {{"lilb", "lilt"}, {"roseb", "roset"}, {"peob", "peot"}};
		for (int x = -36; x < 40; x++)
			for (int z = -30; z < 19; z++) {
				int h = hgt(x, z);
				if (Math.abs(x - 1) < 7 && z > 0 && z < 8) continue; // chừa tiền cảnh trước máy quay
				if (isW(x, z) || isShore(x, z) || trunks.contains(key(x, 0, z)) || occ.contains(key(x, h, z))) continue;
				double r = sr(), g = vnoise(x * .16, z * .16), fp = vnoise(x * .21 + 40, z * .21 - 7);
				Opt pv = Opt.pv(x + .5, h, z + .5);
				if (fp > .64 && r < .5) {
					plant(m, x, h, z, FL[(int) (vnoise(x * .06 + 9, z * .06 + 3) * FL.length * 1.6) % FL.length], pv, 0, .1);
					continue;
				}
				if (r < .012) {
					String[] d = DBL[(int) (sr() * 3)];
					plant(m, x, h, z, d[0], pv, 0, .04);
					plant(m, x, h + 1, z, d[1], Opt.pv(x + .5, h + 1, z + .5), .04, .09);
					continue;
				}
				if (g > .5 && r < .42) {
					if (r < .1) {
						plant(m, x, h, z, "tgb", pv, 0, .05);
						plant(m, x, h + 1, z, "tgt", Opt.pv(x + .5, h + 1, z + .5), .05, .11);
					} else plant(m, x, h, z, vnoise(x * .3, z * .3 + 5) > .62 ? "fern" : "grassp", pv, 0, .1);
					continue;
				}
				if (r < .07) plant(m, x, h, z, "grassp", pv, 0, .1);
			}
		float[] land = new float[m.n + far.n];
		System.arraycopy(m.a, 0, land, 0, m.n);
		System.arraycopy(far.a, 0, land, m.n, far.n);
		return new Mesh(land, m.n / STRIDE, w.toArray());
	}

	// ---------- cừu: đúng hộp của SheepModel và lớp lông của SheepWoolModel ----------
	/** tâm x, z · bán kính vòng đi · chiều · lệch pha · cỡ (cừu non .62). */
	private static final double[][] SHEEP = {{-15, 1, 2.4, 1, 0, 1}, {17, -1, 2.2, -1, 3, 1}, {10, -15, 2.4, 1, 7, 1}, {-8, -17, 2, -1, 11, 1}, {-12.5, 3.2, 1.3, 1, 5, .62}};
	static final int SHEEP_FLOATS = SHEEP.length * 12 * 36 * STRIDE;

	private record Pose(double x, double y, double z, double yaw, double leg, double g, double sc) {}

	private Pose sheepPose(double[] sd, double t) {
		double T = t + sd[4], k = T % 16, W = 7, sp = .5, dist = (Math.floor(T / 16) * W + Math.min(k, W)) * sp, a = dist / sd[2] * sd[3];
		double x = sd[0] + Math.cos(a) * sd[2], z = sd[1] + Math.sin(a) * sd[2], hx = -Math.sin(a) * sd[3], hz = Math.cos(a) * sd[3];
		double mv = sstep(0, .5, k) * (1 - sstep(W - .5, W, k));
		return new Pose(x, hgt((int) Math.floor(x), (int) Math.floor(z)), z, Math.atan2(-hx, -hz), Math.sin(dist * 6) * .75 * mv,
			sstep(W + .3, W + 1, k) * (1 - sstep(12.2, 13, k)), sd[5]);
	}

	private int nSB;

	private void sbox(float[] SB, Pose P, int ox, int u, int v, double[] pv, double rx, double x0, double y0, double z0, double w, double h, double d, double g) {
		double X0 = pv[0] + x0 - g, X1 = pv[0] + x0 + w + g, Y0 = pv[1] + y0 - g, Y1 = pv[1] + y0 + h + g, Z0 = pv[2] + z0 - g, Z1 = pv[2] + z0 + d + g;
		double c = Math.cos(rx), s = Math.sin(rx), cy = Math.cos(P.yaw), sy = Math.sin(P.yaw), S = P.sc / 16;
		java.util.function.Function<double[], double[]> tf = e -> {
			double y = e[1] - pv[1], z = e[2] - pv[2], Yr = y * c - z * s + pv[1], Zr = y * s + z * c + pv[2], wx = e[0] * S, wy = (24 - Yr) * S, wz = Zr * S;
			return new double[]{wx * cy + wz * sy + P.x, wy + P.y, -wx * sy + wz * cy + P.z};
		};
		double[] C = tf.apply(new double[]{(X0 + X1) / 2, (Y0 + Y1) / 2, (Z0 + Z1) / 2});
		double[][][] faces = {
			{{X0, Y0, Z0}, {X1, Y0, Z0}, {X1, Y0, Z1}, {X0, Y0, Z1}}, {{X0, Y1, Z1}, {X1, Y1, Z1}, {X1, Y1, Z0}, {X0, Y1, Z0}},
			{{X1, Y0, Z0}, {X0, Y0, Z0}, {X0, Y1, Z0}, {X1, Y1, Z0}}, {{X0, Y0, Z1}, {X1, Y0, Z1}, {X1, Y1, Z1}, {X0, Y1, Z1}},
			{{X0, Y0, Z0}, {X0, Y0, Z1}, {X0, Y1, Z1}, {X0, Y1, Z0}}, {{X1, Y0, Z1}, {X1, Y0, Z0}, {X1, Y1, Z0}, {X1, Y1, Z1}}};
		double[][] uvs = {{u + d, v, u + d + w, v + d}, {u + d + w, v, u + d + 2 * w, v + d}, {u + d, v + d, u + d + w, v + d + h},
			{u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h}, {u, v + d, u + d, v + d + h}, {u + d + w, v + d, u + 2 * d + w, v + d + h}};
		for (int fi = 0; fi < 6; fi++) {
			double[][] p = new double[4][];
			for (int i = 0; i < 4; i++) p[i] = tf.apply(faces[fi][i]);
			double nx = (p[0][0] + p[2][0]) / 2 - C[0], ny = (p[0][1] + p[2][1]) / 2 - C[1], nz = (p[0][2] + p[2][2]) / 2 - C[2], l = Math.sqrt(nx * nx + ny * ny + nz * nz);
			float sh = Math.abs(ny) > .7 * l ? (ny > 0 ? 1 : .5f) : Math.abs(nz) > Math.abs(nx) ? .8f : .6f;
			double[] q = uvs[fi];
			double[][] U = {{q[0], q[1]}, {q[2], q[1]}, {q[2], q[3]}, {q[0], q[3]}};
			for (int i : ORD_A) {
				int o = nSB++ * STRIDE;
				SB[o] = (float) p[i][0]; SB[o + 1] = (float) p[i][1]; SB[o + 2] = (float) p[i][2];
				SB[o + 3] = (float) ((ox + U[i][0]) / 256); SB[o + 4] = (float) ((192 + U[i][1]) / 256); SB[o + 5] = sh;
				SB[o + 6] = (float) P.x; SB[o + 7] = (float) P.y; SB[o + 8] = (float) P.z;
				for (int j = 9; j < 14; j++) SB[o + j] = 0;
			}
		}
	}

	/** Dựng lại đỉnh cừu cho khung này vào SB; trả về số đỉnh. */
	int buildSheep(float[] SB, double t) {
		nSB = 0;
		for (double[] sd : SHEEP) {
			Pose P = sheepPose(sd, t);
			double[] hp = {0, 6 + P.g * 8, -8};
			double hr = P.g * 1.05;
			double[][] L = {{-3, 7, P.leg}, {3, 7, -P.leg}, {-3, -5, -P.leg}, {3, -5, P.leg}};
			for (double[] ly : new double[][]{{0, 0, 0, 0, 12}, {64, .6, 1.75, .5, 6}}) {
				int ox = (int) ly[0];
				if (ox != 0) sbox(SB, P, ox, 0, 0, hp, hr, -3, -4, -4, 6, 6, 6, ly[1]);
				else sbox(SB, P, ox, 0, 0, hp, hr, -3, -4, -6, 6, 6, 8, 0);
				sbox(SB, P, ox, 28, 8, new double[]{0, 5, 2}, Math.PI / 2, -4, -10, -7, 8, 16, 6, ly[2]);
				for (double[] lg : L) sbox(SB, P, ox, 0, 16, new double[]{lg[0], 12, lg[1]}, lg[2], -2, 0, -2, 4, ly[4], 4, ly[3]);
			}
		}
		return nSB;
	}

	// ---------- mây khối: mỗi px của environment/clouds.png = ô 12×12 khối, dày 4 ----------
	static final int CY = 36, CH = 4, CS = 12, CI0 = -50, CI1 = 50, CJ0 = -66, CJ1 = 13;

	static float[] buildClouds(boolean[] cm, int shift) {
		FloatList m = new FloatList();
		for (int i = CI0; i < CI1; i++)
			for (int j = CJ0; j < CJ1; j++) {
				if (!on(cm, i, j, shift)) continue;
				int x0 = i * CS, x1 = x0 + CS, z0 = j * CS, z1 = z0 + CS, y0 = CY, y1 = CY + CH;
				quad(m, new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}, "full", 1, Opt.NONE);
				quad(m, new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}}, "full", .5f, Opt.NONE);
				if (!on(cm, i + 1, j, shift)) quad(m, new double[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}}, "full", .6f, Opt.NONE);
				if (!on(cm, i - 1, j, shift)) quad(m, new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}}, "full", .6f, Opt.NONE);
				if (!on(cm, i, j + 1, shift)) quad(m, new double[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}}, "full", .8f, Opt.NONE);
				if (!on(cm, i, j - 1, shift)) quad(m, new double[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}}, "full", .8f, Opt.NONE);
			}
		return m.toArray();
	}

	private static boolean on(boolean[] cm, int i, int j, int shift) {
		return cm[Math.floorMod(j, 256) * 256 + Math.floorMod(i - shift, 256)];
	}
}
