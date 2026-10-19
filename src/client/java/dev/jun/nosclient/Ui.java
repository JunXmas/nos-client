package dev.jun.nosclient;

import net.minecraft.client.gui.GuiGraphics;

/** Vẽ dùng chung cho bảng cảnh nền và HUD. */
public final class Ui {
	private Ui() {}

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

	public static int lerpCol(int a, int b, float k) {
		int r = 0;
		for (int s = 0; s < 32; s += 8) r |= Math.round(((a >>> s) & 255) + (((b >>> s) & 255) - ((a >>> s) & 255)) * k) << s;
		return r;
	}
}
