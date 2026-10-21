package dev.jun.nosclient.gui;

import dev.jun.nosclient.Mods;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.scene.SceneRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Bảng "Cảnh nền" kiểu mica theo preview: ba hàng công tắc có icon khối, thanh hoàng hôn ↔ đêm có núm phát sáng,
 * hai nút chọn nhanh dạng segmented. Toạ độ thiết kế 200×150 rồi co theo chỗ trống.
 */
final class ScenePanel extends AbstractWidget {
	static final int W = 220, H = 178;
	private static final ItemStack[] ICON = {new ItemStack(Items.CHERRY_LEAVES), new ItemStack(Items.SUGAR_CANE), new ItemStack(Items.TORCH)};
	private static final String[] LABEL = {"nosclient.scene.blossom", "nosclient.scene.cane", "nosclient.scene.shader"};

	private final SceneConfig c = SceneConfig.INSTANCE;
	/** Bảng cảnh = "Thế giới" nên nhuốm xanh lá phe World thay vì tím mặc định — đổi màu 2 công tắc, thanh giờ, chọn nhanh. */
	private static final int ACC = Mods.catColor("world");
	private final float s;
	private final float[] knob = new float[3];
	private float todShown;
	private boolean dragging;
	private long last = System.nanoTime();

	ScenePanel(int x, int y, float scale) {
		super(x, y, Math.round(W * scale), Math.round(H * scale), Component.translatable("nosclient.scene.title"));
		s = scale;
		for (int i = 0; i < 3; i++) knob[i] = on(i) ? 1 : 0;
		todShown = (float) c.tod;
	}

	private boolean on(int i) {
		return i == 0 ? c.blossom : i == 1 ? c.cane : c.fx;
	}

	private int rowY(int i) {
		return getY() + 30 + i * 26;
	}

	private int tx0() {
		return getX() + 16;
	}

	private int tx1() {
		return getX() + W - 16;
	}

	private int trackY() {
		return getY() + 128;
	}

	private int segY() {
		return getY() + 146;
	}

	@Override
	protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
		long now = System.nanoTime();
		float dt = Math.min(.05f, (now - last) / 1e9f);
		last = now;
		int x = getX(), y = getY();
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(s, s);
		g.pose().translate(-x, -y);
		dev.jun.nosclient.Ui.hint = s;
		try {
			Ui.panel(g, x, y, x + W, y + H, 0);
			Ui.text(g, Ui.small(Component.translatable("nosclient.scene.title")), x + 16, y + 13, Ui.MUTED);
			for (int i = 0; i < 3; i++) {
				int ry = rowY(i);
				if (i > 0) g.fill(x + 16, ry - 6, x + W - 16, ry - 5, 0x14FFFFFF);
				g.pose().pushMatrix();
				g.pose().translate(x + 16, ry + 1);
				g.pose().scale(.75f, .75f);
				g.renderFakeItem(ICON[i], 0, 0);
				g.pose().popMatrix();
				Ui.text(g, Ui.ui(Component.translatable(LABEL[i])), x + 36, ry + 3, Ui.TEXT);
				knob[i] = Ui.approach(knob[i], on(i) ? 1 : 0, dt, 14);
				Ui.toggle(g, x + W - 16 - 24, ry + 1, Ui.ease(knob[i]), ACC);
			}
			todShown = Ui.approach(todShown, (float) c.tod, dt, 12);
			int ty = trackY(), a = tx0(), b = tx1();
			float p = (todShown - 40) / 60;
			Ui.text(g, Ui.ui(Component.translatable("nosclient.scene.tod")), a, ty - 16, Ui.TEXT);
			Component val = Ui.bold(Component.translatable(c.tod < 62 ? "nosclient.scene.sunset" : c.tod < 80 ? "nosclient.scene.dusk" : "nosclient.scene.night"));
			Ui.text(g, val, b - Ui.width(val), ty - 16, Ui.ACCENT_HI);
			g.fill(a, ty, b, ty + 4, 0x1FFFFFFF);
			int px = Math.round(a + p * (b - a));
			if (px > a + 2) {
				g.fill(a, ty, px + 2, ty + 4, ACC);
				g.fill(a + 2, ty, px, ty + 4, Ui.lerpCol(ACC, 0xFFFFFFFF, .4f));
			}
			g.fill(px - 7, ty - 5, px + 7, ty + 9, Ui.alpha(ACC, .4f));
			g.fill(px - 5, ty - 3, px + 5, ty + 7, 0xFFFFFFFF);
			int sy = segY(), half = (W - 32) / 2;
			g.fill(x + 16, sy, x + W - 16, sy + 20, 0x38000000);
			Ui.frame(g, x + 16, sy, x + W - 16, sy + 20, 0x12FFFFFF);
			for (int i = 0; i < 2; i++) {
				int bx = x + 18 + i * half;
				boolean sel = i == 0 ? c.tod < 62 : c.tod >= 80;
				if (sel) g.fill(bx, sy + 2, bx + half - 4, sy + 18, Ui.alpha(ACC, .65f));
				Component t = Ui.bold(Component.translatable(i == 0 ? "nosclient.scene.sunset" : "nosclient.scene.night"));
				Ui.text(g, t, bx + (half - 4 - Ui.width(t)) / 2, sy + 6, sel ? 0xFFEAD4FB : Ui.MUTED);
			}
		} finally {
			dev.jun.nosclient.Ui.hint = 1;
			g.pose().popMatrix();
		}
	}

	private double lx(double mx) {
		return getX() + (mx - getX()) / s;
	}

	private double ly(double my) {
		return getY() + (my - getY()) / s;
	}

	@Override
	public void onClick(MouseButtonEvent e, boolean dbl) {
		double mx = lx(e.x()), my = ly(e.y());
		for (int i = 0; i < 3; i++) {
			int ry = rowY(i);
			if (my >= ry - 6 && my < ry + 20) {
				boolean v = !on(i);
				if (i < 2) SceneRenderer.INSTANCE.toggle(i + 1, v);
				else {
					c.fx = v;
					c.save();
				}
				return;
			}
		}
		if (my >= trackY() - 8 && my <= trackY() + 12) {
			dragging = true;
			setTod(mx);
		} else if (my >= segY() && my < segY() + 20) {
			c.tod = mx < getX() + W / 2.0 ? 47 : 88;
			c.save();
		}
	}

	private void setTod(double mx) {
		c.tod = 40 + 60 * Math.min(1, Math.max(0, (mx - tx0()) / (tx1() - tx0())));
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
