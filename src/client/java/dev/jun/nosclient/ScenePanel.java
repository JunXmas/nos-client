package dev.jun.nosclient;

import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.scene.SceneRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Bảng "Cảnh nền" kiểu mica ở góc phải màn chính, theo đúng bảng của preview:
 * ba công tắc (anh đào, mía, shader), thanh hoàng hôn ↔ đêm và hai nút chọn nhanh.
 * Một widget cho cả bảng: tự chia vùng bấm, tự vẽ công tắc/thanh trượt bằng fill của GuiGraphics.
 */
final class ScenePanel extends AbstractWidget {
	static final int W = 168, H = 128;
	private static final int TEXT = 0xFFEEF1F7, MUTED = 0xFF8D97AB, LINE = 0x17FFFFFF;
	private static final int ACC0 = 0xFF9A5BD6, ACC1 = 0xFFBF86EF, OFF = 0x21FFFFFF, KNOB_OFF = 0xFFC9CFDB;
	private static final Identifier[] ICON = {Identifier.withDefaultNamespace("textures/block/cherry_leaves.png"),
		Identifier.withDefaultNamespace("textures/block/sugar_cane.png"), Identifier.withDefaultNamespace("textures/block/torch.png")};
	private static final String[] LABEL = {"nosclient.scene.blossom", "nosclient.scene.cane", "nosclient.scene.shader"};

	private final SceneConfig c = SceneConfig.INSTANCE;
	/** Vị trí núm công tắc 0..1, trượt mượt về trạng thái thật. */
	private final float[] knob = new float[3];
	private boolean dragging;
	private long last = System.nanoTime();

	/** Co cả bảng khi màn hẹp (GUI scale lớn) để không đè lên nút giữa của màn chính. */
	private final float s;

	ScenePanel(int x, int y, float scale) {
		super(x, y, Math.round(W * scale), Math.round(H * scale), Component.translatable("nosclient.scene.title"));
		s = scale;
		for (int i = 0; i < 3; i++) knob[i] = on(i) ? 1 : 0;
	}

	private boolean on(int i) {
		return i == 0 ? c.blossom : i == 1 ? c.cane : c.fx;
	}

	// ---------- bố cục (toạ độ GUI) ----------
	private int rowY(int i) {
		return getY() + 22 + i * 19;
	}

	private int trackX0() {
		return getX() + 10;
	}

	private int trackX1() {
		return getX() + W - 10;
	}

	private int trackY() {
		return getY() + 92;
	}

	private int segY() {
		return getY() + 104;
	}

	// ---------- vẽ ----------
	/** Hình chữ nhật bo góc bằng các dải fill: GuiGraphics không có rounded rect. */
	private static void round(GuiGraphics g, int x0, int y0, int x1, int y1, int r, int col) {
		for (int k = 0; k < r; k++) {
			double dy = r - k - .5;
			int ins = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(x0 + ins, y0 + k, x1 - ins, y0 + k + 1, col);
			g.fill(x0 + ins, y1 - k - 1, x1 - ins, y1 - k, col);
		}
		g.fill(x0, y0 + r, x1, y1 - r, col);
	}

	private static int lerpCol(int a, int b, float k) {
		int r = 0;
		for (int s = 0; s < 32; s += 8) r |= Math.round(((a >>> s) & 255) + (((b >>> s) & 255) - ((a >>> s) & 255)) * k) << s;
		return r;
	}

	private static float ease(float x) {
		return 1 - (1 - x) * (1 - x) * (1 - x);
	}

	@Override
	protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
		long now = System.nanoTime();
		float dt = Math.min(.05f, (now - last) / 1e9f);
		last = now;
		Font f = Minecraft.getInstance().font;
		int x = getX(), y = getY();
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(s, s);
		g.pose().translate(-x, -y);
		try {
			draw(g, f, x, y, dt);
		} finally {
			g.pose().popMatrix();
		}
	}

	private void draw(GuiGraphics g, Font f, int x, int y, float dt) {
		// mica: nền tối trong suốt, viền sáng mảnh, vệt sáng đỉnh
		round(g, x, y, x + W, y + H, 8, 0x9E141821);
		round(g, x, y, x + W, y + 1 + 8, 8, 0x0FFFFFFF);
		g.fill(x + 8, y, x + W - 8, y + 1, 0x24FFFFFF);
		g.drawString(f, Component.translatable("nosclient.scene.title"), x + 10, y + 8, MUTED, false);

		for (int i = 0; i < 3; i++) {
			int ry = rowY(i);
			if (i > 0) g.fill(x + 10, ry - 3, x + W - 10, ry - 2, LINE);
			g.blit(RenderPipelines.GUI_TEXTURED, ICON[i], x + 10, ry + 1, 0, 0, 10, 10, 16, 16, 16, 16);
			g.drawString(f, Component.translatable(LABEL[i]), x + 26, ry + 2, TEXT, false);
			float tgt = on(i) ? 1 : 0;
			knob[i] += (tgt - knob[i]) * (1 - (float) Math.exp(-dt * 14));
			float k = ease(knob[i]);
			int sx1 = x + W - 10, sx0 = sx1 - 24, sy0 = ry, sy1 = ry + 13;
			round(g, sx0, sy0, sx1, sy1, 6, lerpCol(OFF, ACC0, k));
			if (k > .02f) round(g, sx0 + 12, sy0, sx1, sy1, 6, lerpCol(OFF, ACC1, k) & 0x00FFFFFF | Math.round(k * 255) << 24);
			int kx = Math.round(sx0 + 2 + k * 11);
			round(g, kx, sy0 + 2, kx + 9, sy1 - 2, 4, lerpCol(KNOB_OFF, 0xFFFFFFFF, k));
		}

		// hoàng hôn ↔ đêm
		int ty = trackY(), tx0 = trackX0(), tx1 = trackX1();
		double p = (c.tod - 40) / 60;
		String key = c.tod < 62 ? "nosclient.scene.sunset" : c.tod < 80 ? "nosclient.scene.dusk" : "nosclient.scene.night";
		g.drawString(f, Component.translatable("nosclient.scene.tod"), x + 10, ty - 12, TEXT, false);
		Component val = Component.translatable(key);
		g.drawString(f, val, tx1 - f.width(val), ty - 12, 0xFFD9B8F5, false);
		round(g, tx0, ty, tx1, ty + 4, 2, 0x26FFFFFF);
		int px = (int) Math.round(tx0 + p * (tx1 - tx0));
		g.fillGradient(tx0 + 1, ty, px, ty + 4, ACC0, ACC0); // dải đã kéo
		g.fill(tx0, ty + 1, tx0 + 1, ty + 3, ACC0);
		round(g, px - 5, ty - 3, px + 5, ty + 7, 5, 0xFFFFFFFF);

		// hai nút chọn nhanh
		int sy = segY(), half = (W - 20) / 2;
		round(g, x + 10, sy, x + W - 10, sy + 16, 5, 0x38000000);
		for (int i = 0; i < 2; i++) {
			int bx = x + 12 + i * half;
			boolean sel = i == 0 ? c.tod < 62 : c.tod >= 80;
			if (sel) round(g, bx, sy + 2, bx + half - 4, sy + 14, 4, 0x8C6A3B94);
			Component t = Component.translatable(i == 0 ? "nosclient.scene.sunset" : "nosclient.scene.night");
			g.drawString(f, t, bx + (half - 4 - f.width(t)) / 2, sy + 4, sel ? 0xFFEAD4FB : MUTED, false);
		}
	}

	// ---------- tương tác ----------
	@Override
	public void onClick(MouseButtonEvent e, boolean dbl) {
		double mx = lx(e.x()), my = getY() + (e.y() - getY()) / s;
		for (int i = 0; i < 3; i++) {
			int ry = rowY(i);
			if (my >= ry - 3 && my < ry + 16) {
				toggle(i);
				return;
			}
		}
		if (my >= trackY() - 6 && my <= trackY() + 10) {
			dragging = true;
			setTod(mx);
			return;
		}
		if (my >= segY() && my < segY() + 16) {
			c.tod = mx < getX() + W / 2.0 ? 47 : 88;
			c.save();
		}
	}

	/** Toạ độ chuột màn hình → toạ độ bảng chưa co. */
	private double lx(double mx) {
		return getX() + (mx - getX()) / s;
	}

	private void toggle(int i) {
		boolean v = !on(i);
		if (i < 2) SceneRenderer.INSTANCE.toggle(i + 1, v);
		else {
			c.fx = v;
			c.save();
		}
	}

	private void setTod(double mx) {
		double p = Math.min(1, Math.max(0, (mx - trackX0()) / (trackX1() - trackX0())));
		c.tod = 40 + p * 60;
	}

	@Override
	protected void onDrag(MouseButtonEvent e, double dx, double dy) {
		if (dragging) setTod(lx(e.x()));
	}

	@Override
	public void onRelease(MouseButtonEvent e) {
		if (dragging) {
			dragging = false;
			c.save();
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput out) {
		defaultButtonNarrationText(out);
	}
}
