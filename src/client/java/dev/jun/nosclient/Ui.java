package dev.jun.nosclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Bộ vẽ dùng chung theo theme của launcher (Theme.qml): nền #141821, chữ #eef1f7/#8d97ab, nhấn tím THƯ VIỆN #b06ee6.
 * Chữ dùng Inter/Manrope đóng gói trong mod (OFL), đúng font của preview.
 */
public final class Ui {
	private Ui() {}

	public static final int TEXT = 0xFFEEF1F7, MUTED = 0xFF8D97AB, FAINT = 0x998D97AB, ACCENT = 0xFFB06EE6, ACCENT_HI = 0xFFD3A8F5, BRAND = 0xFF5AC54F;
	public static final int ACC0 = 0xFF9A5BD6, ACC1 = 0xFFBF86EF;

	private static final FontDescription UI = font("ui"), UI_BOLD = font("ui_bold"), SMALL = font("small"), BIG = font("big"), LOGO = font("logo"), NUM = font("num");

	private static FontDescription font(String n) {
		return new FontDescription.Resource(Identifier.fromNamespaceAndPath("nosclient", n));
	}

	public static MutableComponent ui(Component c) {
		return c.copy().withStyle(Style.EMPTY.withFont(UI));
	}

	public static MutableComponent ui(String s) {
		return Component.literal(s).withStyle(Style.EMPTY.withFont(UI));
	}

	public static MutableComponent bold(Component c) {
		return c.copy().withStyle(Style.EMPTY.withFont(UI_BOLD));
	}

	public static MutableComponent small(Component c) {
		return c.copy().withStyle(Style.EMPTY.withFont(SMALL));
	}

	public static MutableComponent big(Component c) {
		return c.copy().withStyle(Style.EMPTY.withFont(BIG));
	}

	public static MutableComponent logo(String s) {
		return Component.literal(s).withStyle(Style.EMPTY.withFont(LOGO));
	}

	public static MutableComponent num(String s) {
		return Component.literal(s).withStyle(Style.EMPTY.withFont(NUM));
	}

	public static MutableComponent tr(String key, Object... args) {
		return Component.translatable(key, args);
	}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	public static void text(GuiGraphics g, Component c, int x, int y, int col) {
		g.drawString(font(), c, x, y, col, false);
	}

	public static int width(Component c) {
		return font().width(c);
	}

	// ---------- hình ----------
	/** Hình chữ nhật bo góc bằng các dải fill: GuiGraphics không có rounded rect. */
	public static void round(GuiGraphics g, int x0, int y0, int x1, int y1, int r, int col) {
		r = Math.min(r, Math.min(x1 - x0, y1 - y0) / 2);
		for (int k = 0; k < r; k++) {
			double dy = r - k - .5;
			int ins = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(x0 + ins, y0 + k, x1 - ins, y0 + k + 1, col);
			g.fill(x0 + ins, y1 - k - 1, x1 - ins, y1 - k, col);
		}
		g.fill(x0, y0 + r, x1, y1 - r, col);
	}

	/** Viền bo góc dày 1 px. */
	public static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int r, int col) {
		r = Math.min(r, Math.min(x1 - x0, y1 - y0) / 2);
		int prev = r;
		for (int k = 0; k < r; k++) {
			double dy = r - k - .5;
			int ins = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			int a = Math.min(prev, ins), b = Math.max(prev, ins);
			g.fill(x0 + a, y0 + k, x0 + b + 1, y0 + k + 1, col);
			g.fill(x1 - b - 1, y0 + k, x1 - a, y0 + k + 1, col);
			g.fill(x0 + a, y1 - k - 1, x0 + b + 1, y1 - k, col);
			g.fill(x1 - b - 1, y1 - k - 1, x1 - a, y1 - k, col);
			prev = ins;
		}
		g.fill(x0 + r, y0, x1 - r, y0 + 1, col);
		g.fill(x0 + r, y1 - 1, x1 - r, y1, col);
		g.fill(x0, y0 + r, x0 + 1, y1 - r, col);
		g.fill(x1 - 1, y0 + r, x1, y1 - r, col);
	}

	/** Bóng đổ mềm dưới tấm kính: vài lớp bo góc mờ dần. */
	public static void shadow(GuiGraphics g, int x0, int y0, int x1, int y1, int r, int spread) {
		for (int i = spread; i > 0; i -= 2) {
			int a = (int) (26 * (1 - (double) i / spread) + 4);
			round(g, x0 - i, y0 - i + spread / 2, x1 + i, y1 + i + spread / 2, r + i, a << 24);
		}
	}

	/**
	 * Tấm kính mica như preview: bóng đổ, nền tối trong, vệt sáng dọc nửa trên, viền sáng mảnh, gờ sáng ở mép trên.
	 * tint = màu ARGB pha thêm (vd. tím cho nút chính), 0 nếu không.
	 */
	public static void mica(GuiGraphics g, int x0, int y0, int x1, int y1, int r, int tint) {
		shadow(g, x0, y0, x1, y1, r, 10);
		round(g, x0, y0, x1, y1, r, tint != 0 ? tint : 0xA6141821);
		int h = y1 - y0;
		for (int i = 0; i < Math.min(h / 2, 24); i++) // vệt sáng mờ dần từ trên xuống
			g.fill(x0 + (i < r ? r - (int) Math.sqrt(Math.max(0, r * r - (r - i) * (r - i))) : 0), y0 + i,
				x1 - (i < r ? r - (int) Math.sqrt(Math.max(0, r * r - (r - i) * (r - i))) : 0), y0 + i + 1, ((int) (18 * (1 - i / 24.0))) << 24 | 0xFFFFFF);
		outline(g, x0, y0, x1, y1, r, 0x1FFFFFFF);
		g.fill(x0 + r, y0, x1 - r, y0 + 1, 0x38FFFFFF);
	}

	public static int lerpCol(int a, int b, float k) {
		int r = 0;
		for (int s = 0; s < 32; s += 8) r |= Math.round(((a >>> s) & 255) + (((b >>> s) & 255) - ((a >>> s) & 255)) * k) << s;
		return r;
	}

	public static int alpha(int col, float a) {
		return Math.round(((col >>> 24) & 255) * a) << 24 | col & 0xFFFFFF;
	}

	/** Dải chuyển màu ngang (fillGradient của game chỉ dọc). */
	public static void hgrad(GuiGraphics g, int x0, int y0, int x1, int y1, int c0, int c1) {
		for (int x = x0; x < x1; x++) g.fill(x, y0, x + 1, y1, lerpCol(c0, c1, (x - x0) / (float) Math.max(1, x1 - x0 - 1)));
	}

	/** Công tắc trượt kiểu iOS như preview; k = 0 tắt … 1 bật (đã làm mượt bên gọi). */
	public static void toggle(GuiGraphics g, int x, int y, float k) {
		int w = 26, h = 14;
		round(g, x, y, x + w, y + h, 7, lerpCol(0x24FFFFFF, ACC0, k));
		if (k > .02f) { // ánh tím phát sáng như box-shadow
			round(g, x - 2, y - 1, x + w + 2, y + h + 1, 8, alpha(0x40B06EE6, k));
			round(g, x, y, x + w, y + h, 7, lerpCol(0x24FFFFFF, ACC0, k));
			hgrad(g, x + 7, y, x + w - 7, y + h, alpha(ACC0, k), alpha(ACC1, k));
			round(g, x + w - 14, y, x + w, y + h, 7, alpha(ACC1, k));
		}
		int kx = Math.round(x + 2 + k * 12);
		round(g, kx, y + 2, kx + 10, y + h - 2, 5, lerpCol(0xFFC9CFDB, 0xFFFFFFFF, k));
	}

	public static float ease(float x) {
		return 1 - (1 - x) * (1 - x) * (1 - x);
	}

	/** Làm mượt về đích theo thời gian như InertialMotion của launcher. */
	public static float approach(float v, float target, float dt, float speed) {
		return v + (target - v) * (1 - (float) Math.exp(-dt * speed));
	}
}
