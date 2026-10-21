package dev.jun.nosclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.jun.nosclient.Ui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix3x2f;

/**
 * Intro mở game, dựng lại đúng kịch bản của preview (ms): đảo cỏ hiện 120 → mầm sồi 520 → bột xương bay vào 880,
 * hạt xanh 1340 → thân mọc 1700 → lá bung 2120 → một khối lá rung 2950, rụng, nảy → cây tan 3900 → khối lá bay lên
 * giữa, xoay một vòng thành logo 3950–4650 → tên trượt ra 5050 → mờ dần sang màn chính 6500–7100.
 * Khối vẽ isometric bằng GuiGraphics: mỗi mặt là một blit kéo thành hình bình hành bằng ma trận affine của pose,
 * nên không cần renderer 3D riêng; texture lấy thẳng từ game (đổi resource pack là intro đổi theo).
 * Toạ độ thiết kế 1920×1080 như preview, co theo màn hình bằng hệ số U.
 */
public final class IntroScreen extends Screen {
	/** Mỗi lần mở game chỉ chạy một lần. */
	public static boolean played;

	private static final float SZ = 48, COS30 = (float) Math.cos(Math.PI / 6), C45 = (float) Math.cos(Math.PI / 4);
	private static final int TRUNK = Anim.TRUNK, GRASS = 0xFF91BD59, FOLIAGE = 0xFF77AB2F;
	private static final int[] HERO = Anim.HERO;
	private static final Identifier T_GRASS_TOP = mc("block/grass_block_top"), T_GRASS_SIDE = mc("block/grass_block_side"), T_DIRT = mc("block/dirt"),
		T_LOG = mc("block/oak_log"), T_LOG_TOP = mc("block/oak_log_top"), T_LEAVES = mc("block/oak_leaves"), T_SAPLING = mc("block/oak_sapling"),
		T_BONE = mc("item/bone_meal"), T_GLINT = mc("particle/glint"), LOGO = Identifier.fromNamespaceAndPath("nosclient", "textures/gui/logo.png");
	private static final Identifier BG = Identifier.fromNamespaceAndPath("nosclient", "intro/bg"), GLOW = Identifier.fromNamespaceAndPath("nosclient", "intro/glow"),
		RING = Identifier.fromNamespaceAndPath("nosclient", "intro/ring");
	private static boolean texMade;
	/** Đóng băng ở một mốc (ms) để chụp từng khung: -Dnosclient.introAt=3000. */
	private static final float FREEZE = Float.parseFloat(System.getProperty("nosclient.introAt", "-1"));

	private static Identifier mc(String p) {
		return Identifier.withDefaultNamespace("textures/" + p + ".png");
	}

	/** Toán thuần của intro (easing kiểu CSS, sinh lá), tách khỏi Screen để tự kiểm không cần mở game. */
	static final class Anim {
		static final int TRUNK = 5;
		static final int[] HERO = {0, 3, 2};

		record Bez(float x1, float y1, float x2, float y2) {
			float at(float x) {
				if (x <= 0 || x >= 1) return x <= 0 ? 0 : 1;
				float t = x;
				for (int i = 0; i < 8; i++) { // Newton trên x(t)
					float cx = bx(t) - x, d = 3 * (1 - t) * (1 - t) * x1 + 6 * (1 - t) * t * (x2 - x1) + 3 * t * t * (1 - x2);
					if (Math.abs(cx) < 1e-4f || d == 0) break;
					t = Math.min(1, Math.max(0, t - cx / d));
				}
				return 3 * (1 - t) * (1 - t) * t * y1 + 3 * (1 - t) * t * t * y2 + t * t * t;
			}

			private float bx(float t) {
				return 3 * (1 - t) * (1 - t) * t * x1 + 3 * (1 - t) * t * t * x2 + t * t * t;
			}
		}

		static final Bez OUT = new Bez(.215f, .61f, .355f, 1), SPR = new Bez(.34f, 1.56f, .64f, 1), IN = new Bez(.42f, 0, 1, 1),
			IN_OUT = new Bez(.42f, 0, .58f, 1), LIN = new Bez(0, 0, 1, 1), FALL = new Bez(.55f, 0, 1, .45f), SLIDE = new Bez(.65f, 0, .35f, 1),
			REVEAL = new Bez(.16f, 1, .3f, 1), SPIN = new Bez(.3f, .7f, .2f, 1);

		/**
		 * Giá trị kênh ch của hoạt ảnh tại thời điểm ms. kf: mỗi hàng [offset, v0, v1…], nội suy tuyến tính giữa các mốc;
		 * easing áp lên tiến độ chung như Web Animations; trước/sau hoạt ảnh giữ mốc đầu/cuối (fill both).
		 */
		static float anim(float ms, float delay, float dur, Bez e, float[][] kf, int ch) {
			float p = e.at(Math.min(1, Math.max(0, (ms - delay) / dur)));
			int i = 0;
			while (i < kf.length - 2 && p > kf[i + 1][0]) i++;
			float[] a = kf[i], b = kf[i + 1];
			float k = (p - a[0]) / (b[0] - a[0]); // SPR vượt 1 thì ngoại suy đoạn cuối, đúng kiểu "nảy"
			return a[ch] + (b[ch] - a[ch]) * k;
		}

		private static float lerp(float ms, float delay, float dur, Bez e, float a, float b) {
			return a + (b - a) * e.at(Math.min(1, Math.max(0, (ms - delay) / dur)));
		}

		/** Lá theo blob_foliage_placer của game: bán kính 2 hai tầng dưới, 1 hai tầng trên, góc thủng ngẫu nhiên (seed 11). */
		static List<int[]> leafCells() {
			List<int[]> out = new ArrayList<>();
			int seed = 11;
			for (int dy = 0; dy >= -3; dy--) {
				int r = Math.max(1 - dy / 2, 0), y = TRUNK + 1 + dy; // chia nguyên Java cắt về 0, đúng Math.trunc của preview
				for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
					if (Math.abs(x) == r && Math.abs(z) == r && r > 0) {
						if (dy == 0) continue;
						seed = (int) ((seed * 16807L) % 2147483647L);
						if ((seed & 1) == 0) continue;
					}
					if (x == 0 && z == 0 && y <= TRUNK) continue;
					out.add(new int[]{x, y, z});
				}
			}
			return out;
		}

		/** Tự kiểm (không cần game): java -cp build/classes/java/client 'dev.jun.nosclient.gui.IntroScreen$Anim' */
		public static void main(String[] a) {
			List<int[]> lv = leafCells();
			if (lv.size() != 55) throw new AssertionError("lá " + lv.size());
			if (lv.stream().noneMatch(l -> l[0] == HERO[0] && l[1] == HERO[1] && l[2] == HERO[2])) throw new AssertionError("thiếu khối lá chính");
			for (Bez e : new Bez[]{OUT, SPR, IN, IN_OUT, SLIDE, REVEAL, SPIN}) if (Math.abs(e.at(0)) > 1e-4 || Math.abs(e.at(1) - 1) > 1e-4) throw new AssertionError(e);
			if (SPR.at(.5f) <= 1) throw new AssertionError("SPR phải vượt 1 (nảy)");
			if (Math.abs(anim(2950 + 80, 2950, 1000, LIN, new float[][]{{0, 0}, {.08f, -3}, {1, 0}}, 1) + 3) > 1e-3) throw new AssertionError("anim");
			System.out.println("INTRO OK");
		}
	}

	// ---------- cảnh ----------
	private static final Anim.Bez OUT = Anim.OUT, SPR = Anim.SPR, IN = Anim.IN, IN_OUT = Anim.IN_OUT, LIN = Anim.LIN, FALL = Anim.FALL, SLIDE = Anim.SLIDE,
		REVEAL = Anim.REVEAL, SPIN = Anim.SPIN;

	private static float anim(float ms, float delay, float dur, Anim.Bez e, float[][] kf, int ch) {
		return Anim.anim(ms, delay, dur, e, kf, ch);
	}

	private static float lerp(float ms, float delay, float dur, Anim.Bez e, float a, float b) {
		return Anim.lerp(ms, delay, dur, e, a, b);
	}
	private record Cube(int x, int y, int z, int kind, float delay) {}

	private static final int GRASS_K = 0, DIRT_K = 1, LOG_K = 2, LEAF_K = 3;
	private final List<Cube> cubes = new ArrayList<>();
	private float heroDelay;

	private record Part(Identifier tex, int tint, float size, float x0, float y0, float[][] kf, float delay, float dur, Anim.Bez e) {}

	private final List<Part> parts = new ArrayList<>();
	private int rng = 7;

	private float rnd() {
		rng = (int) ((rng * 16807L) % 2147483647L);
		return rng / 2147483647f;
	}

	private final Screen next;
	private Screen shown;
	private long start = -1;
	private final boolean[] sounded = new boolean[4];

	public IntroScreen(Screen next) {
		super(Component.empty());
		this.next = next;
		played = true;
		build();
	}

	/** Chiếu điểm thế giới (khối) ra mặt phẳng thiết kế, đúng rotateX(-30°) rotateY(45°) của preview. */
	private static float[] proj(float x, float y, float z) {
		return new float[]{(x + z) * SZ * C45, -y * SZ * COS30 + (z - x) * SZ * C45 * .5f};
	}

	private void build() {
		// đảo cỏ 5×5 bỏ bốn góc + đất bên dưới
		for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (!(Math.abs(x) == 2 && Math.abs(z) == 2)) cubes.add(new Cube(x, 0, z, GRASS_K, 120 + (Math.abs(x) + Math.abs(z)) * 55));
		for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) cubes.add(new Cube(x, -1, z, DIRT_K, 120 + (Math.abs(x) + Math.abs(z)) * 55 + 40));
		cubes.add(new Cube(0, -2, 0, DIRT_K, 200));
		for (int i = 0; i < TRUNK; i++) cubes.add(new Cube(0, i + 1, 0, LOG_K, 1700 + i * 90));
		float[] sap = proj(0, 1, 0);
		float tx = sap[0], ty = sap[1];
		// hạt xanh kiểu bột xương, hai đợt (cùng thứ tự gọi rnd như preview)
		for (int w = 0; w < 2; w++) for (int i = 0; i < 14; i++) {
			float ang = rnd() * (float) Math.PI * 2, rad = 20 + rnd() * 70, x0 = tx + (float) Math.cos(ang) * rad - 10, y0 = ty - 20 + (float) Math.sin(ang) * rad * .6f;
			float size = 18 + rnd() * 12, up = -60 - rnd() * 40;
			parts.add(new Part(T_GLINT, 0xFFFFFFFF, size, x0, y0, new float[][]{{0, 0, 0, 0, .4f, 0}, {.3f, 1, 0, -14, 1.1f, 0}, {1, 0, 0, up, .6f, 0}},
				1340 + w * 260 + rnd() * 240, 700 + rnd() * 300, new Anim.Bez(0, 0, .58f, 1)));
		}
		for (int[] l : Anim.leafCells()) if (!(l[0] == HERO[0] && l[1] == HERO[1] && l[2] == HERO[2])) cubes.add(new Cube(l[0], l[1], l[2], LEAF_K, 0));
		// trễ bung lá: lan từ thân ra ngoài, tầng trên trước
		for (int i = 0; i < cubes.size(); i++) {
			Cube c = cubes.get(i);
			if (c.kind == LEAF_K) cubes.set(i, new Cube(c.x, c.y, c.z, LEAF_K, leafDelay(c.x, c.y, c.z)));
		}
		heroDelay = leafDelay(HERO[0], HERO[1], HERO[2]);
		// xa trước gần sau: trục hướng về người xem là +y, +z, -x
		cubes.sort(Comparator.comparingInt(c -> c.y + c.z - c.x));
		// lá rơi lả tả
		for (int i = 0; i < 22; i++) {
			float[] p = proj(rnd() * 4 - 2, 3 + rnd() * 4, rnd() * 4 - 2);
			float dx = (rnd() - .5f) * 160, size = 16 + rnd() * 8, end = 380 + rnd() * 120;
			parts.add(new Part(leaf(i), FOLIAGE, size, p[0], p[1], new float[][]{{0, 0, 0, 0, 1, 0}, {.15f, 1, dx * .3f, 60, 1, 90}, {.6f, 1, -dx * .4f, 220, 1, 250}, {1, 0, dx, end, 1, 420}},
				2500 + rnd() * 1900, 2200 + rnd() * 900, LIN));
		}
		// chùm lá bung quanh logo (toạ độ so với tâm cây: logo ở (960,470), cây ở (960,680))
		for (int i = 0; i < 16; i++) {
			float ang = i / 16f * (float) Math.PI * 2 + rnd() * .3f, d = 150 + rnd() * 110, c = (float) Math.cos(ang), s = (float) Math.sin(ang);
			parts.add(new Part(leaf(i), FOLIAGE, 18, -12, -222, new float[][]{{0, 0, 0, 0, .6f, 0}, {.3f, 1, c * d * .5f, s * d * .5f, 1.2f, 0}, {1, 0, c * d, s * d + 60, .8f, 300}},
				4560 + rnd() * 80, 900 + rnd() * 300, new Anim.Bez(0, 0, .58f, 1)));
		}
	}

	private float leafDelay(int x, int y, int z) {
		return 2120 + (Math.abs(x) + Math.abs(z)) * 70 + (TRUNK + 1 - y) * -45 + rnd() * 60;
	}

	private static Identifier leaf(int i) {
		return mc("particle/leaf_" + new int[]{0, 3, 6, 9}[i % 4]);
	}

	// ---------- texture sinh sẵn: nền, quầng sáng, vòng sóng ----------
	private static void makeTextures(Minecraft mc) {
		if (texMade) return;
		texMade = true;
		// nền: #1d2534 ở giữa → #0a0d13 ở 62% → #05070a ở mép (alpha phủ lên nền #05070a)
		radial(mc, BG, 128, r -> r < .62 ? 1 - .79 * r / .62 : .21 * (1 - (r - .62) / .38));
		radial(mc, GLOW, 128, r -> r < .6 ? 1 - r / .6 : 0);
		radial(mc, RING, 256, r -> Math.max(0, 1 - Math.abs(r - .98) / .018));
	}

	private static void radial(Minecraft mc, Identifier id, int n, DoubleUnaryOperator alpha) {
		NativeImage img = new NativeImage(n, n, false);
		for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
			double r = Math.hypot(x + .5 - n / 2.0, y + .5 - n / 2.0) / (n / 2.0), a = Math.min(1, Math.max(0, r > 1 ? 0 : alpha.applyAsDouble(r)));
			img.setPixel(x, y, (int) Math.round(a * 255) << 24 | 0xFFFFFF);
		}
		// lọc tuyến tính để gradient phóng to không thành bậc
		DynamicTexture t = new DynamicTexture(() -> "nosclient " + id.getPath(), img) {
			{
				sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			}
		};
		t.upload();
		mc.getTextureManager().register(id, t);
	}

	// ---------- vẽ ----------
	private float A = 1; // độ đục chung (mờ dần cuối intro)

	private int col(int argb, float a) {
		return Ui.alpha(argb, a * A);
	}

	private static int shade(int rgb, float k) {
		int r = Math.round(((rgb >> 16) & 255) * k), g = Math.round(((rgb >> 8) & 255) * k), b = Math.round((rgb & 255) * k);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** 6 mặt khối trong toạ độ khối [0,1]³: góc đầu, cạnh ngang, cạnh dọc (xuống), pháp tuyến, độ sáng như renderer của game. */
	private static final float[][] FACES = {
		{0, 1, 0, 1, 0, 0, 0, 0, 1, 0, 1, 0, 1f},
		{0, 1, 1, 1, 0, 0, 0, -1, 0, 0, 0, 1, .8f},
		{1, 1, 0, -1, 0, 0, 0, -1, 0, 0, 0, -1, .8f},
		{1, 1, 1, 0, 0, -1, 0, -1, 0, 1, 0, 0, .6f},
		{0, 1, 0, 0, 0, 1, 0, -1, 0, -1, 0, 0, .6f},
	};

	/**
	 * Vẽ một khối tâm (cx,cy,cz), co giãn quanh tâm đáy (sxz, sy) như transform-origin 50% 100% của preview,
	 * xoay quanh trục đứng một góc rot (rad). Chỉ vẽ mặt quay về người xem; khối lồi nên các mặt đó không đè nhau.
	 */
	private void cube(GuiGraphics g, float cx, float cy, float cz, float sxz, float sy, float rot, int kind, float a) {
		if (sxz <= .001f || sy <= .001f || a <= .003f) return;
		float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
		for (float[] f : FACES) {
			// pháp tuyến sau khi xoay; z' = .5·ny + .61·(nz − nx) > 0 là mặt hướng về người xem
			float nx = f[9] * cr + f[11] * sr, nz = -f[9] * sr + f[11] * cr;
			if (.5f * f[10] + .612f * (nz - nx) <= .01f) continue;
			float[] o = pt(f[0], f[1], f[2], cx, cy, cz, sxz, sy, cr, sr), u = pt(f[0] + f[3], f[1] + f[4], f[2] + f[5], cx, cy, cz, sxz, sy, cr, sr),
				v = pt(f[0] + f[6], f[1] + f[7], f[2] + f[8], cx, cy, cz, sxz, sy, cr, sr);
			g.pose().pushMatrix();
			g.pose().mul(new Matrix3x2f((u[0] - o[0]) / 16, (u[1] - o[1]) / 16, (v[0] - o[0]) / 16, (v[1] - o[1]) / 16, o[0], o[1]));
			float k = f[12];
			boolean top = f[10] > 0;
			switch (kind) {
				case GRASS_K -> blit(g, top ? T_GRASS_TOP : T_GRASS_SIDE, top ? shade(GRASS, k) : shade(0xFFFFFF, k), a);
				case DIRT_K -> blit(g, T_DIRT, shade(0xFFFFFF, k), a);
				case LOG_K -> blit(g, top ? T_LOG_TOP : T_LOG, shade(0xFFFFFF, k), a);
				default -> { // lá: nền xanh thẫm như lá "Nhanh" của game rồi mới phủ texture, nên khối đặc như logo
					g.fill(0, 0, 16, 16, col(shade(FOLIAGE, k * .42f), a));
					blit(g, T_LEAVES, shade(FOLIAGE, k), a);
				}
			}
			g.pose().popMatrix();
		}
	}

	private void blit(GuiGraphics g, Identifier tex, int tint, float a) {
		g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0, 0, 16, 16, 16, 16, col(tint, a));
	}

	/** Điểm (lx,ly,lz) ∈ [0,1]³ của khối → mặt phẳng thiết kế. */
	private static float[] pt(float lx, float ly, float lz, float cx, float cy, float cz, float sxz, float sy, float cr, float sr) {
		float x = (lx - .5f) * sxz, y = ly * sy - .5f, z = (lz - .5f) * sxz;
		float rx = x * cr + z * sr, rz = -x * sr + z * cr;
		return proj(cx + rx, cy + y, cz + rz);
	}

	private void sprite(GuiGraphics g, Identifier tex, int tint, float cx, float cy, float size, float scale, float rotDeg, float a) {
		if (a <= .003f || scale <= .001f) return;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate((float) Math.toRadians(rotDeg));
		g.pose().scale(size * scale / 16f, size * scale / 16f);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, -8, -8, 0, 0, 16, 16, 16, 16, col(tint, a));
		g.pose().popMatrix();
	}

	private void soft(GuiGraphics g, Identifier tex, int rgb, float cx, float cy, float w, float h, float a) {
		if (a <= .003f) return;
		g.pose().pushMatrix();
		g.pose().translate(cx - w / 2, cy - h / 2);
		g.pose().scale(w / 64f, h / 64f);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, 0, 0, 0, 0, 64, 64, 64, 64, col(0xFF000000 | rgb, a));
		g.pose().popMatrix();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
		Minecraft mc = Minecraft.getInstance();
		makeTextures(mc);
		long now = System.nanoTime();
		// màn tải của game còn phủ thì chưa tính giờ, kẻo intro chạy mất sau màn tải
		if (start < 0 || mc.getOverlay() != null) start = now;
		float ms = FREEZE >= 0 ? FREEZE : (now - start) / 1e6f;
		if (ms >= 7100) {
			finish();
			return;
		}
		sounds(mc, ms);
		A = 1 - lerp(ms, 6500, 600, IN, 0, 1);
		if (ms >= 6500) { // mờ dần sang màn chính đang vẽ bên dưới
			if (shown == null) {
				shown = next;
				next.init(width, height);
			}
			next.render(g, mouseX, mouseY, pt);
		}
		float U = Math.min(width / 1920f, height / 1080f);
		g.pose().pushMatrix();
		g.pose().translate(width / 2f, height / 2f);
		g.pose().scale(U, U);
		// nền + quầng xanh (toạ độ thiết kế, gốc ở tâm màn hình)
		float W = width / U, H = height / U;
		g.fill((int) (-W / 2) - 1, (int) (-H / 2) - 1, (int) (W / 2) + 1, (int) (H / 2) + 1, col(0xFF05070A, 1));
		soft(g, BG, 0x1D2534, 0, H * .02f, W * 1.2f, H * 1.1f, 1);
		soft(g, GLOW, 0x5AC54F, 0, 0, 900, 900, .2f * lerp(ms, 200, 1400, OUT, 0, 1));
		// cây: gốc thiết kế (960,680) = tâm + (0,140)
		g.pose().pushMatrix();
		g.pose().translate(0, 140);
		float fade = lerp(ms, 3900, 520, IN, 0, 1), treeA = 1 - fade;
		g.pose().translate(0, 40 * fade);
		g.pose().scale(1 - .06f * fade, 1 - .06f * fade);
		float[][] pop = {{0, 0}, {.7f, 1.08f}, {1, 1}}, leafPop = {{0, 0}, {.65f, 1.14f}, {1, 1}};
		float[] hp = proj(HERO[0], HERO[1], HERO[2]);
		boolean heroDrawn = false;
		for (Cube c : cubes) {
			// khối lá chính chen vào đúng thứ tự xa–gần khi còn nằm trên cây
			if (!heroDrawn && ms < 3950 && c.y + c.z - c.x > HERO[1] + HERO[2] - HERO[0]) {
				hero(g, ms, hp, treeA);
				heroDrawn = true;
			}
			if (c.kind == LOG_K) {
				float d = c.delay;
				float[][] grow = {{0, .7f, 0}, {.7f, 1.05f, 1.08f}, {1, 1, 1}};
				cube(g, c.x, c.y, c.z, anim(ms, d, 280, SPR, grow, 1), anim(ms, d, 280, SPR, grow, 2), 0, LOG_K, treeA);
			} else {
				float s = c.kind == LEAF_K ? anim(ms, c.delay, 380, SPR, leafPop, 1) : anim(ms, c.delay, 480, SPR, pop, 1);
				cube(g, c.x, c.y, c.z, s, s, 0, c.kind, treeA);
			}
			if (c.kind == GRASS_K && c.x == 0 && c.z == 0) sapling(g, ms, treeA);
		}
		if (!heroDrawn && ms < 3950) hero(g, ms, hp, treeA);
		g.pose().popMatrix();
		// khối lá bay lên giữa, xoay một vòng, to dần thành logo (vẽ trên cùng khi cây đang tan)
		if (ms >= 3950) {
			g.pose().pushMatrix();
			g.pose().translate(0, 140);
			hero(g, ms, hp, 1);
			g.pose().popMatrix();
		}
		// bột xương bay theo đường cong tới mầm rồi vỡ
		float[] sp = proj(0, 1, 0);
		float sx = sp[0], sy = sp[1] + 140;
		float[][] bone = {{0, 460, 340, 40, 1, 0}, {.35f, 340, 100, 0, 1.1f, 1}, {.8f, sx + 40, sy - 60, -30, 1, 1}, {1, sx, sy, -50, .2f, 0}};
		if (ms > 880 && ms < 1400) sprite(g, T_BONE, 0xFFFFFFFF, anim(ms, 880, 520, IN, bone, 1), anim(ms, 880, 520, IN, bone, 2), 56, anim(ms, 880, 520, IN, bone, 4),
			anim(ms, 880, 520, IN, bone, 3), anim(ms, 880, 520, IN, bone, 5));
		// hạt
		for (Part p : parts) {
			if (ms < p.delay || ms > p.delay + p.dur) continue;
			float op = anim(ms, p.delay, p.dur, p.e, p.kf, 1), dx = anim(ms, p.delay, p.dur, p.e, p.kf, 2), dy = anim(ms, p.delay, p.dur, p.e, p.kf, 3);
			sprite(g, p.tex, p.tint, p.x0 + dx + p.size / 2, p.y0 + 140 + dy + p.size / 2, p.size, anim(ms, p.delay, p.dur, p.e, p.kf, 4), anim(ms, p.delay, p.dur, p.e, p.kf, 5), op);
		}
		brand(g, ms);
		// gợi ý bỏ qua
		Component skip = Ui.small(Component.translatable("nosclient.intro.skip"));
		g.pose().pushMatrix();
		g.pose().translate(0, H / 2 - 60);
		g.pose().scale(2.2f, 2.2f);
		Ui.text(g, skip, -Ui.width(skip) / 2, 0, col(0xFFAEB7C8, .55f * lerp(ms, 900, 600, OUT, 0, 1)));
		g.pose().popMatrix();
		g.pose().popMatrix();
	}

	private void sapling(GuiGraphics g, float ms, float a) {
		float[][] kf = {{0, 0, 0}, {.25f, 1, 1}, {.62f, 1, 1}, {.7f, 1.12f, 1.25f}, {.78f, .92f, .86f}, {.86f, 1.06f, 1.14f}, {.93f, 1, 1}, {1, 0, 0}};
		float sx = anim(ms, 520, 1380, IN_OUT, kf, 1), sy = anim(ms, 520, 1380, IN_OUT, kf, 2);
		if (ms < 520 || ms > 1900 || sx <= .001f) return;
		// cành mầm là hai mặt chéo; ở góc nhìn này một mặt quay thẳng về người xem → vẽ một tấm đứng
		float[] b = proj(0, .5f, 0);
		g.pose().pushMatrix();
		g.pose().translate(b[0], b[1]);
		g.pose().scale(sx * SZ / 16f, sy * SZ * COS30 / 16f);
		g.blit(RenderPipelines.GUI_TEXTURED, T_SAPLING, -8, -16, 0, 0, 16, 16, 16, 16, col(0xFFFFFFFF, a));
		g.pose().popMatrix();
	}

	private void hero(GuiGraphics g, float ms, float[] hp, float treeA) {
		float[][] leafPop = {{0, 0}, {.65f, 1.14f}, {1, 1}};
		float s = anim(ms, heroDelay, 380, SPR, leafPop, 1), fall = 2 * SZ * COS30;
		float dx, dy, rot2d, scale = 1, a = treeA;
		if (ms < 3950) { // rung → rụng → nảy (mỗi đoạn một easing như keyframe của preview)
			float p = Math.min(1, Math.max(0, (ms - 2950) / 1000));
			float[][] shake = {{0, 0, 0}, {.08f, -3, -3}, {.16f, 3, 3}, {.24f, -2, -2}, {.3f, 0, 0}};
			dx = p < .3f ? anim(p, 0, 1, LIN, shake, 1) : 0;
			rot2d = p < .3f ? anim(p, 0, 1, LIN, shake, 2) : 0;
			dy = p < .3f ? 0 : p < .68f ? fall * FALL.at((p - .3f) / .38f) : p < .84f ? fall - 26 * OUT.at((p - .68f) / .16f) : fall - 26 + 26 * IN.at((p - .84f) / .16f);
		} else {
			float k = SPR.at(Math.min(1, (ms - 3950) / 700));
			dx = -hp[0] * k;
			dy = fall + (-210 - hp[1] - fall) * k;
			scale = 1 + 1.9f * k;
			rot2d = 0;
			a = 1 - lerp(ms, 4560, 160, OUT, 0, 1);
		}
		float spin = (float) (Math.PI * 2 * SPIN.at(Math.min(1, Math.max(0, (ms - 3950) / 760))));
		g.pose().pushMatrix();
		g.pose().translate(hp[0] + dx, hp[1] + dy);
		g.pose().rotate((float) Math.toRadians(rot2d));
		g.pose().scale(scale, scale);
		g.pose().translate(-hp[0], -hp[1]);
		// tâm xoay ở giữa khối: đặt gốc cube tại HERO rồi xoay quanh trục đứng qua tâm
		cube(g, HERO[0], HERO[1], HERO[2], s, s, spin, LEAF_K, a);
		g.pose().popMatrix();
	}

	/** Logo + vòng sóng, trượt sang trái, chữ Nostalgia LAUNCHER chạy ra từ sau logo. */
	private void brand(GuiGraphics g, float ms) {
		if (ms < 4500) return;
		float bx = -300 * SLIDE.at(Math.min(1, Math.max(0, (ms - 4950) / 700))), by = -70;
		float[][] lk = {{0, 0, .92f}, {.6f, 1, 1.06f}, {1, 1, 1}};
		float la = anim(ms, 4520, 420, SPR, lk, 1), ls = anim(ms, 4520, 420, SPR, lk, 2);
		soft(g, GLOW, 0x5AC54F, bx, by, 380 * ls, 380 * ls, .45f * Math.min(1, la));
		float rs = lerp(ms, 4560, 700, OUT, .5f, 1.5f), ra = lerp(ms, 4560, 700, OUT, .9f, 0);
		soft(g, RING, 0x7FD873, bx, by, 300 * rs, 300 * rs, .7f * ra);
		g.pose().pushMatrix();
		g.pose().translate(bx, by);
		g.pose().scale(ls * 220 / 256f, ls * 220 / 256f);
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, -128, -128, 0, 0, 256, 256, 256, 256, col(0xFFFFFFFF, Math.min(1, la)));
		g.pose().popMatrix();
		if (ms < 5050) return;
		// chữ: font logo 30 px phóng lên 112 px thiết kế; lộ dần sau một khung cắt ngay mép phải logo
		float fs = 112 / 30f;
		Component nos = Ui.logo("Nostal"), gia = Ui.logo("gia"), sub = Ui.small(Component.literal("L A U N C H E R"));
		float tw = Math.max(Ui.width(nos) + Ui.width(gia), Ui.width(sub) * 26 / 6f / fs) * fs;
		float reveal = REVEAL.at(Math.min(1, (ms - 5050) / 720)), wx = bx + 150, wy = by - 74;
		g.enableScissor((int) wx, (int) wy - 10, (int) (wx + tw + 60), (int) (wy + 190));
		g.pose().pushMatrix();
		g.pose().translate(wx - 1.05f * tw * (1 - reveal), wy);
		g.pose().pushMatrix();
		g.pose().scale(fs, fs);
		Ui.text(g, nos, 0, 0, col(0xFFFFFFFF, 1));
		Ui.text(g, gia, Ui.width(nos), 0, col(0xFF5AC54F, 1));
		g.pose().popMatrix();
		g.pose().translate(6, 120);
		g.pose().scale(26 / 6f, 26 / 6f);
		Ui.text(g, sub, 0, 0, col(0xFFAEB7C8, 1));
		g.pose().popMatrix();
		g.disableScissor();
	}

	private void sounds(Minecraft mc, float ms) {
		float[] at = {150, 1400, 2150, 4560};
		SoundEvent[] ev = {SoundEvents.GRASS_PLACE, SoundEvents.BONE_MEAL_USE, SoundEvents.AZALEA_LEAVES_PLACE, SoundEvents.AMETHYST_BLOCK_CHIME};
		for (int i = 0; i < at.length; i++) if (!sounded[i] && ms >= at[i]) {
			sounded[i] = true;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(ev[i], 1, .6f));
		}
	}

	private void finish() {
		Minecraft.getInstance().setScreen(next);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		finish();
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		finish();
		return true;
	}

	@Override
	public void onClose() {
		finish();
	}
}
