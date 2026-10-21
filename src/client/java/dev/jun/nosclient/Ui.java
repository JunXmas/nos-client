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

	/*
	 * Chữ TTF nét: game raster glyph một lần ở (cỡ × oversample) px. Oversample lệch tỉ lệ vẽ thật là glyph bị co/giãn
	 * → vỡ nét. Nên mỗi font có 12 bản oversample 0,5…6 (font/<tên>_<2×oversample>.json), luôn chọn bản gần nhất với
	 * (cỡ giao diện × hint); phần lệch còn lại do lấy mẫu tuyến tính (GlyphRenderStateMixin) làm mượt.
	 */
	private static final String[] NAMES = {"ui", "ui_bold", "small", "big", "logo", "num", "hud"};
	private static final FontDescription[][] FONTS = new FontDescription[NAMES.length][12];

	static {
		for (int i = 0; i < NAMES.length; i++) for (int k = 0; k < 12; k++) FONTS[i][k] = new FontDescription.Resource(Identifier.fromNamespaceAndPath("nosclient", NAMES[i] + "_" + (k + 1)));
	}

	/** Tỉ lệ pose mà chữ sắp tạo sẽ được vẽ (HUD co 0,68, intro phóng 3×…); bên gọi đặt rồi trả về 1. */
	public static float hint = 1;

	private static final Style[][] STYLES = new Style[NAMES.length][12];

	/** Style cache theo (font, oversample) — mỗi khung HUD tạo vài chục chuỗi, không cấp phát Style mới. */
	private static Style style(int i) {
		int k2 = Math.max(1, Math.min(12, Math.round(Minecraft.getInstance().getWindow().getGuiScale() * hint * 2))) - 1;
		Style st = STYLES[i][k2];
		if (st == null) st = STYLES[i][k2] = Style.EMPTY.withFont(FONTS[i][k2]);
		return st;
	}

	private static final int UI = 0, UI_BOLD = 1, SMALL = 2, BIG = 3, LOGO = 4, NUM = 5, HUD = 6;

	public static MutableComponent ui(Component c) {
		return c.copy().withStyle(style(UI));
	}

	public static MutableComponent ui(String s) {
		return Component.literal(s).withStyle(style(UI));
	}

	/** Chữ HUD: Inter nét ở mọi cỡ (font pixel của game co lẻ 0,68 là vỡ nét). */
	public static MutableComponent hud(String s) {
		return Component.literal(s).withStyle(style(HUD));
	}

	public static MutableComponent bold(Component c) {
		return c.copy().withStyle(style(UI_BOLD));
	}

	public static MutableComponent small(Component c) {
		return c.copy().withStyle(style(SMALL));
	}

	public static MutableComponent big(Component c) {
		return c.copy().withStyle(style(BIG));
	}

	public static MutableComponent logo(String s) {
		return Component.literal(s).withStyle(style(LOGO));
	}

	public static MutableComponent num(String s) {
		return Component.literal(s).withStyle(style(NUM));
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
	 * Tấm mica phẳng kiểu Lunar: nền tối trong suốt + viền sáng 1 px. Không bóng đổ, không vệt sáng, không gờ — 5 quad.
	 * tint = nền ARGB riêng (vd. tím cho nút đang chọn), 0 = nền mặc định.
	 */
	public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int tint) {
		g.fill(x0, y0, x1, y1, tint != 0 ? tint : 0x99101218);
		frame(g, x0, y0, x1, y1, 0x14FFFFFF);
		// gờ sáng mép trên như ánh trăng trên kính: sáng ở giữa, tan về hai đầu (2 quad)
		dev.jun.nosclient.gui.Fx.hairline(g, x0 + 1, x1 - 1, y0, 0x40E9D5FF);
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
		// bật = được phù phép: vệt sáng của game trôi qua rãnh, quầng tím quanh núm
		if (k > .02f) {
			dev.jun.nosclient.gui.Fx.glint(g, x + 1, y + 1, x + w - 1, y + h - 1, alpha(0xCCFFF0FF, k));
			dev.jun.nosclient.gui.Fx.glow(g, kx + 4, y + h / 2f, 30, 24, alpha(0x99D9B3FF, k));
		}
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
