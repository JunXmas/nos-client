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

	/** Cắt chữ cho vừa bề rộng, thêm "…" — chữ không bao giờ tràn sang ô bên cạnh. */
	public static Component fit(Component c, int maxW) {
		Font f = font();
		if (f.width(c) <= maxW) return c;
		var cut = f.substrByWidth(c, Math.max(0, maxW - f.width("…")));
		return Component.literal(cut.getString() + "…").withStyle(c.getStyle());
	}

	// ---------- hình ----------
	/*
	 * Góc vuông, mỗi hình chỉ vài quad: bản bo góc vẽ mỗi góc bằng hàng chục dải fill (cả trăm quad cho một viên HUD)
	 * và làm tụt FPS trong game. Vẻ đẹp giữ bằng lớp: nền trong, vệt sáng chuyển màu dọc, viền mảnh, gờ sáng mép trên.
	 */
	/** Viền 1 px: 4 quad. */
	public static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
		g.fill(x0, y0, x1, y0 + 1, col);
		g.fill(x0, y1 - 1, x1, y1, col);
		g.fill(x0, y0 + 1, x0 + 1, y1 - 1, col);
		g.fill(x1 - 1, y0 + 1, x1, y1 - 1, col);
	}

	/**
	 * Tấm kính phẳng: bóng 1 lớp lệch xuống, nền tối trong, vệt sáng chuyển màu dọc nửa trên, viền mảnh, gờ sáng mép trên.
	 * Tổng 8 quad. tint = nền ARGB riêng (vd. tím cho nút chính), 0 = nền mặc định.
	 */
	public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int tint) {
		g.fill(x0 + 1, y1, x1 + 1, y1 + 2, 0x30000000);
		g.fill(x0, y0, x1, y1, tint != 0 ? tint : 0xA6141821);
		g.fillGradient(x0, y0, x1, y0 + Math.min((y1 - y0) / 2, 24), 0x14FFFFFF, 0x00FFFFFF);
		frame(g, x0, y0, x1, y1, 0x1FFFFFFF);
		g.fill(x0 + 1, y0, x1 - 1, y0 + 1, 0x40FFFFFF);
	}

	public static int lerpCol(int a, int b, float k) {
		int r = 0;
		for (int s = 0; s < 32; s += 8) r |= Math.round(((a >>> s) & 255) + (((b >>> s) & 255) - ((a >>> s) & 255)) * k) << s;
		return r;
	}

	public static int alpha(int col, float a) {
		return Math.round(((col >>> 24) & 255) * a) << 24 | col & 0xFFFFFF;
	}

	/** Công tắc phẳng: rãnh + núm vuông, 4 quad; k = 0 tắt … 1 bật (đã làm mượt bên gọi). */
	public static void toggle(GuiGraphics g, int x, int y, float k) {
		int w = 24, h = 12;
		g.fill(x, y, x + w, y + h, lerpCol(0x24FFFFFF, ACC0, k));
		frame(g, x, y, x + w, y + h, lerpCol(0x14FFFFFF, 0x66D3A8F5, k));
		int kx = Math.round(x + 2 + k * (w - 12));
		g.fill(kx, y + 2, kx + 8, y + h - 2, lerpCol(0xFFC9CFDB, 0xFFFFFFFF, k));
	}

	public static float ease(float x) {
		return 1 - (1 - x) * (1 - x) * (1 - x);
	}

	/** Làm mượt về đích theo thời gian như InertialMotion của launcher. */
	public static float approach(float v, float target, float dt, float speed) {
		return v + (target - v) * (1 - (float) Math.exp(-dt * speed));
	}
}
