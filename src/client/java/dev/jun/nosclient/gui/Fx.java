package dev.jun.nosclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.jun.nosclient.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

/**
 * Ngôn ngữ "bàn phù phép" của Nos client — mọi hiệu ứng menu dùng chung ở đây, mỗi cái 1 đến vài quad:
 * - glow: quầng sáng tròn mềm (texture sinh sẵn, lọc tuyến tính) cho nền và mục đang chọn;
 * - Runes: chữ Standard Galactic bay vòng vào đích như chữ bay vào sách ở bàn phù phép;
 * - Sparks: tia enchanted_hit bung ra khi bật một mod.
 * Chỉ dùng ở màn menu; HUD trong game không gọi gì ở đây.
 */
public final class Fx {
	private Fx() {}

	private static final Identifier GLOW = Identifier.fromNamespaceAndPath("nosclient", "fx/glow"), HIT = Identifier.withDefaultNamespace("textures/particle/enchanted_hit.png");
	private static final Style RUNE = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.withDefaultNamespace("alt")));
	private static boolean made;

	public static float time() {
		return (System.nanoTime() % 1_000_000_000_000L) / 1e9f;
	}

	private static void make() {
		if (made) return;
		made = true;
		Minecraft mc = Minecraft.getInstance();
		int n = 128;
		NativeImage g = new NativeImage(n, n, false);
		for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) {
			double r = Math.hypot(x + .5 - n / 2.0, y + .5 - n / 2.0) / (n / 2.0), a = r >= 1 ? 0 : Math.pow(1 - r, 2.2);
			g.setPixel(x, y, (int) Math.round(a * 255) << 24 | 0xFFFFFF);
		}
		register(mc, GLOW, g, false);
	}

	private static void register(Minecraft mc, Identifier id, NativeImage img, boolean repeat) {
		DynamicTexture t = new DynamicTexture(() -> "nosclient " + id.getPath(), img) {
			{
				sampler = repeat ? RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR) : RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			}
		};
		t.upload();
		mc.getTextureManager().register(id, t);
	}

	/** Quầng sáng tròn mềm tâm (cx,cy). */
	public static void glow(GuiGraphics g, float cx, float cy, float w, float h, int argb) {
		make();
		if ((argb >>> 24) == 0 || w < 1 || h < 1) return;
		g.pose().pushMatrix();
		g.pose().translate(cx - w / 2, cy - h / 2);
		g.pose().scale(w / 64f, h / 64f);
		g.blit(RenderPipelines.GUI_TEXTURED, GLOW, 0, 0, 0, 0, 64, 64, 64, 64, argb);
		g.pose().popMatrix();
	}

	/** Đường kẻ ngang chuyển màu hai đầu mờ (fillGradient chỉ dọc nên xoay pose), như dải sáng mép trên tấm kính. */
	public static void hairline(GuiGraphics g, int x0, int x1, int y, int argb) {
		int mid = (x0 + x1) / 2, a = argb & 0xFFFFFF;
		hgrad(g, x0, mid, y, y + 1, a, argb);
		hgrad(g, mid, x1, y, y + 1, argb, a);
	}

	/** Dải chuyển màu ngang (trái → phải). */
	public static void hgrad(GuiGraphics g, int x0, int x1, int y0, int y1, int left, int right) {
		g.pose().pushMatrix();
		g.pose().translate(x0, y1);
		g.pose().rotate((float) (-Math.PI / 2));
		g.fillGradient(0, 0, y1 - y0, x1 - x0, left, right);
		g.pose().popMatrix();
	}

	// ---------- chữ rune bay ----------
	/** Bể cố định 40 chữ rune (không cấp phát mỗi khung). Mỗi chữ bay theo đường cong vào đích rồi tan. */
	public static final class Runes {
		private static final int N = 40;
		private final float[] sx = new float[N], sy = new float[N], tx = new float[N], ty = new float[N], bend = new float[N], t0 = new float[N], dur = new float[N];
		private final char[] ch = new char[N];
		private final int[] col = new int[N];
		private int next;
		private float acc;
		private int rng = 12345;
		/** Cỡ chữ rune (1 = 8 px giao diện). */
		public float size = 1;

		private float rnd() {
			rng = rng * 1103515245 + 12345;
			return ((rng >>> 8) & 0xFFFF) / 65535f;
		}

		/** Phát rune về (x,y) với tốc độ rate chữ/giây, xuất phát trên vòng bán kính r quanh đích. */
		public void emit(float x, float y, float r, float rate, float dt, int argb) {
			acc += rate * dt;
			while (acc >= 1) {
				acc -= 1;
				int i = next = (next + 1) % N;
				double a = rnd() * Math.PI * 2;
				float rr = r * (.7f + .5f * rnd());
				sx[i] = x + (float) Math.cos(a) * rr;
				sy[i] = y + (float) Math.sin(a) * rr * .8f - r * .25f;
				tx[i] = x;
				ty[i] = y;
				bend[i] = (rnd() - .5f) * r * 1.2f;
				t0[i] = time();
				dur[i] = .9f + rnd() * .7f;
				ch[i] = (char) ('a' + (int) (rnd() * 26));
				col[i] = argb;
			}
		}

		public void render(GuiGraphics g) {
			float now = time();
			var f = Minecraft.getInstance().font;
			for (int i = 0; i < N; i++) {
				if (dur[i] <= 0) continue;
				float p = (now - t0[i]) / dur[i];
				if (p >= 1 || p < 0) {
					dur[i] = 0;
					continue;
				}
				float e = p * p * (3 - 2 * p), mx = (sx[i] + tx[i]) / 2 + bend[i], my = (sy[i] + ty[i]) / 2 - Math.abs(bend[i]) * .5f;
				float x = (1 - e) * (1 - e) * sx[i] + 2 * (1 - e) * e * mx + e * e * tx[i], y = (1 - e) * (1 - e) * sy[i] + 2 * (1 - e) * e * my + e * e * ty[i];
				float a = (float) Math.sin(Math.PI * Math.min(1, p * 1.15f)), s = size * (1 - .45f * e);
				g.pose().pushMatrix();
				g.pose().translate(x, y);
				g.pose().scale(s, s);
				g.drawString(f, Component.literal(String.valueOf(ch[i])).withStyle(RUNE), -3, -4, Ui.alpha(col[i], a), false);
				g.pose().popMatrix();
			}
		}
	}

	// ---------- tia lấp lánh ----------
	/** Tia enchanted_hit bung ra rồi rơi chậm — khi bật một mod. Bể cố định 48 tia. */
	public static final class Sparks {
		private static final int N = 48;
		private final float[] x = new float[N], y = new float[N], vx = new float[N], vy = new float[N], life = new float[N], size = new float[N];
		private final int[] col = new int[N];
		private int next;
		private int rng = 777;
		private long last = System.nanoTime();

		private float rnd() {
			rng = rng * 1103515245 + 12345;
			return ((rng >>> 8) & 0xFFFF) / 65535f;
		}

		public void burst(float cx, float cy, int count, int argb) {
			for (int k = 0; k < count; k++) {
				int i = next = (next + 1) % N;
				double a = rnd() * Math.PI * 2;
				float sp = 30 + rnd() * 60;
				x[i] = cx;
				y[i] = cy;
				vx[i] = (float) Math.cos(a) * sp;
				vy[i] = (float) Math.sin(a) * sp - 20;
				life[i] = .45f + rnd() * .35f;
				size[i] = 4 + rnd() * 4;
				col[i] = argb;
			}
		}

		public void render(GuiGraphics g) {
			long now = System.nanoTime();
			float dt = Math.min(.05f, (now - last) / 1e9f);
			last = now;
			for (int i = 0; i < N; i++) {
				if (life[i] <= 0) continue;
				life[i] -= dt;
				x[i] += vx[i] * dt;
				y[i] += vy[i] * dt;
				vx[i] *= 1 - 3 * dt;
				vy[i] = vy[i] * (1 - 3 * dt) + 40 * dt;
				float a = Math.min(1, life[i] * 3), s = size[i] * (.5f + a * .5f);
				g.pose().pushMatrix();
				g.pose().translate(x[i], y[i]);
				g.pose().scale(s / 8f, s / 8f);
				g.blit(RenderPipelines.GUI_TEXTURED, HIT, -4, -4, 0, 0, 8, 8, 8, 8, Ui.alpha(col[i], a));
				g.pose().popMatrix();
			}
		}
	}
}
