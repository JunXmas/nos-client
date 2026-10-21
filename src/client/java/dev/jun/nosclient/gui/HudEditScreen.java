package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.hud.Hud;
import dev.jun.nosclient.hud.HudLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Sửa bố cục HUD (preview 10): HUD thật vẽ ngay bên dưới; kéo ô để dời, lăn chuột trên ô để đổi cỡ,
 * thanh dưới đáy chỉnh cỡ + độ đục của ô đang chọn. Kéo hít vào mép màn hình, đường giữa và lưới 4 px; giữ Shift để thả tự do.
 */
public final class HudEditScreen extends Screen {
	private final Screen parent;
	private final Hud hud = Hud.INSTANCE;
	private final Minecraft mc = Minecraft.getInstance();
	private String sel, drag;
	private double offX, offY;
	/** 0 = thanh cỡ, 1 = thanh độ đục đang kéo; -1 = không. */
	private int slider = -1;
	private int barX, barY, barW;

	public HudEditScreen(Screen parent) {
		super(Component.translatable("nosclient.edit.title"));
		this.parent = parent;
		hud.editing = true;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		// không làm mờ: phải thấy đúng HUD đang sửa. Chỉ một lớp tối nhẹ + lưới thưa để căn.
		g.fill(0, 0, width, height, 0x33000000);
		for (int x = width / 12; x < width; x += Math.max(1, width / 12)) g.fill(x, 0, x + 1, height, 0x0FFFFFFF);
		for (int y = height / 8; y < height; y += Math.max(1, height / 8)) g.fill(0, y, width, y + 1, 0x0FFFFFFF);
		g.fill(width / 2, 0, width / 2 + 1, height, 0x1FD3A8F5);
	}

	private String at(double mx, double my) {
		String hit = null;
		for (var e : hud.rects.entrySet()) {
			float[] r = e.getValue();
			if (mx >= r[0] - 2 && mx < r[2] + 2 && my >= r[1] - 2 && my < r[3] + 2) hit = e.getKey(); // ô vẽ sau nằm trên
		}
		return hit;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		super.render(g, mx, my, pt);
		if (mc.player == null) {
			Component n = Ui.ui(Component.translatable("nosclient.edit.noworld"));
			Ui.text(g, n, (width - Ui.width(n)) / 2, height / 2, Ui.TEXT);
			return;
		}
		String hov = drag != null ? drag : at(mx, my);
		for (var e : hud.rects.entrySet()) {
			float[] r = e.getValue();
			boolean on = e.getKey().equals(sel), hv = e.getKey().equals(hov);
			int x0 = (int) Math.floor(r[0]) - 2, y0 = (int) Math.floor(r[1]) - 2, x1 = (int) Math.ceil(r[2]) + 2, y1 = (int) Math.ceil(r[3]) + 2;
			if (on || hv) g.fill(x0, y0, x1, y1, on ? 0x265E3A8A : 0x14FFFFFF);
			dashed(g, x0, y0, x1, y1, on ? 0xFFD3A8F5 : hv ? 0xCCFFFFFF : 0x66FFFFFF);
			if (hv && drag == null) {
				Component n = Ui.small(Component.translatable("nosclient.mod." + e.getKey()));
				int ty = y0 - 10 < 2 ? y1 + 3 : y0 - 10;
				Ui.text(g, n, x0, ty, 0xFFEAD4FB);
			}
		}
		toolbar(g, mx, my);
	}

	/** Viền nét đứt 3/2 px như preview. */
	private static void dashed(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
		for (int x = x0; x < x1; x += 5) {
			g.fill(x, y0, Math.min(x + 3, x1), y0 + 1, col);
			g.fill(x, y1 - 1, Math.min(x + 3, x1), y1, col);
		}
		for (int y = y0; y < y1; y += 5) {
			g.fill(x0, y, x0 + 1, Math.min(y + 3, y1), col);
			g.fill(x1 - 1, y, x1, Math.min(y + 3, y1), col);
		}
	}

	// ---------- thanh công cụ dưới đáy ----------
	private record Btn(int x0, int x1, Runnable run) {}

	private final java.util.List<Btn> btns = new java.util.ArrayList<>();

	private void toolbar(GuiGraphics g, int mx, int my) {
		btns.clear();
		var s = sel == null ? null : HudLayout.get(sel);
		Component hint = Ui.small(Component.translatable(sel == null ? "nosclient.edit.hint" : "nosclient.edit.hint.sel"));
		Component reset = Ui.bold(Component.translatable(sel == null ? "nosclient.edit.resetAll" : "nosclient.edit.reset")), done = Ui.bold(Component.translatable("nosclient.edit.done"));
		int sliders = sel == null ? 0 : 2 * 110 + 16;
		int w = 16 + Ui.width(hint) + 12 + sliders + Ui.width(reset) + 16 + 8 + Ui.width(done) + 16 + 12, h = 26;
		int x = (width - w) / 2, y = height - 78;
		Ui.panel(g, x, y, x + w, y + h, 0xE0161A23);
		int cx = x + 12;
		Ui.text(g, hint, cx, y + 10, Ui.MUTED);
		cx += Ui.width(hint) + 12;
		if (s != null) {
			barY = y + 13;
			barW = 70;
			for (int i = 0; i < 2; i++) {
				double v = i == 0 ? (s.scale - .5) / 1.5 : (s.alpha - .2) / .8;
				Component l = Ui.small(Component.literal(i == 0 ? Math.round(s.scale * 100) + "%" : I18n(i) + " " + Math.round(s.alpha * 100) + "%"));
				Ui.text(g, l, cx, y + 4, Ui.TEXT);
				int tx = cx, kx = (int) Math.round(tx + v * barW);
				g.fill(tx, barY + 3, tx + barW, barY + 5, 0x33FFFFFF);
				g.fill(tx, barY + 3, kx, barY + 5, Ui.ACC1);
				g.fill(kx - 2, barY, kx + 2, barY + 8, 0xFFFFFFFF);
				if (i == 0) barX = tx;
				int si = i;
				btns.add(new Btn(tx - 4, tx + barW + 4, () -> {
					slider = si;
					slide(mx);
				}));
				cx += 110 + (i == 0 ? 8 : 0);
			}
		}
		int rw = Ui.width(reset) + 16;
		btn(g, reset, cx, y + 4, rw, mx, my, false, () -> {
			if (sel == null) HudLayout.resetAll();
			else HudLayout.reset(sel);
			HudLayout.save();
		});
		cx += rw + 8;
		btn(g, done, cx, y + 4, Ui.width(done) + 16, mx, my, true, this::onClose);
	}

	private static String I18n(int i) {
		return net.minecraft.client.resources.language.I18n.get(i == 0 ? "nosclient.edit.size" : "nosclient.edit.alpha");
	}

	private void btn(GuiGraphics g, Component l, int x, int y, int w, int mx, int my, boolean primary, Runnable r) {
		boolean hv = mx >= x && mx < x + w && my >= y && my < y + 18;
		g.fill(x, y, x + w, y + 18, primary ? (hv ? 0xFF9A5BD6 : 0xFF8A4BC6) : hv ? 0x33FFFFFF : 0x1AFFFFFF);
		Ui.text(g, l, x + 8, y + 6, Ui.TEXT);
		btns.add(new Btn(x, x + w, r));
	}

	private void slide(double mx) {
		if (sel == null || slider < 0) return;
		int x0 = barX + slider * (110 + 8);
		double k = Math.min(1, Math.max(0, (mx - x0) / barW));
		var s = HudLayout.edit(sel);
		if (slider == 0) s.scale = Math.round((.5 + k * 1.5) * 20) / 20.0;
		else s.alpha = Math.round((.2 + k * .8) * 20) / 20.0;
	}

	// ---------- nhập ----------
	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (e.y() >= height - 78 && e.y() < height - 52) {
			for (Btn b : btns) if (e.x() >= b.x0 && e.x() < b.x1) {
				b.run.run();
				setDragging(slider >= 0); // Screen chỉ chuyển mouseDragged về khi cờ kéo bật
				return true;
			}
		}
		String id = at(e.x(), e.y());
		sel = id;
		if (id != null) {
			float[] r = hud.rects.get(id);
			drag = id;
			offX = e.x() - r[0];
			offY = e.y() - r[1];
			setDragging(true);
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (slider >= 0) {
			slide(e.x());
			return true;
		}
		if (drag == null) return true;
		float[] r = hud.rects.get(drag);
		if (r == null) return true;
		double w = r[2] - r[0], h = r[3] - r[1], nx = e.x() - offX, ny = e.y() - offY;
		boolean free = (e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
		if (!free) { // hít mép (lề 4), đường giữa, rồi lưới 4 px
			double m = 4, t = 6;
			if (Math.abs(nx - m) < t) nx = m;
			else if (Math.abs(nx + w - (width - m)) < t) nx = width - m - w;
			else if (Math.abs(nx + w / 2 - width / 2.0) < t) nx = width / 2.0 - w / 2;
			else nx = Math.round(nx / 4) * 4;
			if (Math.abs(ny - m) < t) ny = m;
			else if (Math.abs(ny + h - (height - m)) < t) ny = height - m - h;
			else ny = Math.round(ny / 4) * 4;
		}
		nx = Math.max(0, Math.min(width - w, nx));
		ny = Math.max(0, Math.min(height - h, ny));
		// neo theo vùng: ô ở phần ba bên phải neo mép phải (lớn lên sang trái), giữa neo tâm; nửa dưới neo đáy
		double cx = nx + w / 2;
		var s = HudLayout.edit(drag);
		s.ax = cx < width / 3.0 ? 0 : cx > width * 2 / 3.0 ? 2 : 1;
		s.ay = ny + h / 2 < height / 2.0 ? 0 : 1;
		s.x = (s.ax == 0 ? nx : s.ax == 1 ? cx : nx + w) / width;
		s.y = (s.ay == 0 ? ny : ny + h) / height;
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (drag != null || slider >= 0) HudLayout.save();
		drag = null;
		slider = -1;
		setDragging(false);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		String id = at(mx, my);
		if (id == null) return false;
		sel = id;
		var s = HudLayout.edit(id);
		s.scale = Math.min(2, Math.max(.5, Math.round((s.scale + Math.signum(sy) * .05) * 20) / 20.0));
		HudLayout.save();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == GLFW.GLFW_KEY_ESCAPE && sel != null) {
			sel = null;
			return true;
		}
		if ((e.key() == GLFW.GLFW_KEY_DELETE || e.key() == GLFW.GLFW_KEY_BACKSPACE) && sel != null) {
			HudLayout.reset(sel);
			HudLayout.save();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void onClose() {
		hud.editing = false;
		HudLayout.save();
		mc.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
