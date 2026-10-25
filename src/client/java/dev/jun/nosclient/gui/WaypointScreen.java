package dev.jun.nosclient.gui;

import dev.jun.nosclient.Mods;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.feat.Waypoints;
import dev.jun.nosclient.feat.Waypoints.Wp;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Quản lý waypoint (phím J): danh sách của máy chủ này ở mọi chiều, khoảng cách + hướng, đổi tên, đổi màu,
 * ẩn/hiện, xoá có hoàn tác, thêm tại chỗ đứng hoặc theo toạ độ gõ tay.
 * Trước đây chỉ xoá được bằng cách đứng cách waypoint ≤4 khối rồi bấm B.
 */
public final class WaypointScreen extends Screen {
	private final Screen parent;
	private final Minecraft mc = Minecraft.getInstance();
	private static final int ROW = 22, ACC = Mods.catColor("world");
	private EditBox name, coords;
	private int sel = -1, scroll;
	/** Hoàn tác xoá cuối: bản đã xoá + vị trí cũ. */
	private Wp undo;
	private int undoAt = -1;
	private Component note = Component.empty();
	private int x0, y0, w, listTop, listRows;

	public WaypointScreen(Screen parent) {
		super(Component.translatable("nosclient.wp.title"));
		this.parent = parent;
	}

	private List<Wp> list() {
		return Waypoints.mine(mc);
	}

	@Override
	protected void init() {
		w = Math.min(420, width - 40);
		x0 = (width - w) / 2;
		y0 = 30;
		listTop = y0 + 30;
		listRows = Math.max(3, (height - listTop - 96) / ROW);
		int by = listTop + listRows * ROW + 8;
		name = addRenderableWidget(new EditBox(font, x0, by, 150, 20, Component.translatable("nosclient.wpm.name")));
		name.setHint(Component.translatable("nosclient.wpm.name"));
		name.setMaxLength(32);
		coords = addRenderableWidget(new EditBox(font, x0 + 156, by, 120, 20, Component.translatable("nosclient.wpm.coords")));
		coords.setHint(Component.literal("X Y Z"));
		coords.setMaxLength(40);
		addRenderableWidget(Button.builder(Component.translatable("nosclient.wpm.add"), b -> addFromFields()).bounds(x0 + 282, by, w - 282, 20).build());
		int ay = by + 26, bw = (w - 3 * 6) / 4;
		addRenderableWidget(Button.builder(Component.translatable("nosclient.wpm.rename"), b -> rename()).bounds(x0, ay, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("nosclient.wpm.color"), b -> recolor()).bounds(x0 + (bw + 6), ay, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("nosclient.wpm.toggle"), b -> toggleHidden()).bounds(x0 + 2 * (bw + 6), ay, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("nosclient.wpm.delete"), b -> delete()).bounds(x0 + 3 * (bw + 6), ay, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 60, ay + 26, 120, 20).build());
	}

	private void addFromFields() {
		if (mc.player == null) return;
		var p = mc.player.blockPosition();
		int x = p.getX(), y = p.getY(), z = p.getZ();
		String t = coords.getValue().trim();
		if (!t.isEmpty()) {
			String[] parts = t.split("[\\s,;]+");
			try {
				if (parts.length == 2) { // X Z: giữ độ cao hiện tại
					x = Integer.parseInt(parts[0]);
					z = Integer.parseInt(parts[1]);
				} else if (parts.length == 3) {
					x = Integer.parseInt(parts[0]);
					y = Integer.parseInt(parts[1]);
					z = Integer.parseInt(parts[2]);
				} else throw new NumberFormatException();
			} catch (NumberFormatException e) {
				note = Component.translatable("nosclient.wpm.badcoords");
				return;
			}
		}
		Waypoints.add(mc, name.getValue().trim(), x, y, z);
		sel = list().size() - 1;
		name.setValue("");
		coords.setValue("");
		note = Component.translatable("nosclient.wp.added", list().get(sel).name(), list().get(sel).x(), list().get(sel).y(), list().get(sel).z());
	}

	private boolean hasSel() {
		return sel >= 0 && sel < list().size();
	}

	private void rename() {
		if (!hasSel() || name.getValue().isBlank()) {
			note = Component.translatable("nosclient.wpm.renamehint");
			return;
		}
		list().set(sel, list().get(sel).withName(name.getValue().trim()));
		name.setValue("");
		Waypoints.saveNow();
	}

	private void recolor() {
		if (!hasSel()) return;
		Wp wp = list().get(sel);
		int i = 0;
		for (int k = 0; k < Waypoints.COLORS.length; k++) if (Waypoints.COLORS[k] == wp.color()) i = k + 1;
		list().set(sel, wp.withColor(Waypoints.COLORS[i % Waypoints.COLORS.length]));
		Waypoints.saveNow();
	}

	private void toggleHidden() {
		if (!hasSel()) return;
		list().set(sel, list().get(sel).withHidden(!list().get(sel).hidden()));
		Waypoints.saveNow();
	}

	private void delete() {
		if (!hasSel()) return;
		undo = list().remove(sel);
		undoAt = sel;
		Waypoints.saveNow();
		note = Component.translatable("nosclient.wpm.deleted", undo.name());
		sel = Math.min(sel, list().size() - 1);
	}

	private void restore() {
		if (undo == null) return;
		list().add(Math.min(undoAt, list().size()), undo);
		sel = Math.min(undoAt, list().size() - 1);
		undo = null;
		Waypoints.saveNow();
		note = Component.empty();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		super.renderBackground(g, mx, my, pt);
		// panel nằm DƯỚI nút: vẽ trong render() thì nó phủ lên nút làm chúng mờ như bị tắt
		Ui.panel(g, x0 - 10, y0 - 10, x0 + w + 10, height - 8, 0xE0161A23);
		g.fill(x0 - 10, y0 - 10, x0 - 7, height - 8, ACC);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		super.render(g, mx, my, pt);
		Ui.text(g, Ui.big(title), x0, y0, Ui.TEXT);
		var all = list();
		Component count = Ui.small(Component.translatable("nosclient.wpm.count", all.size(), Waypoints.serverKey(mc)));
		Ui.text(g, count, x0 + w - Ui.width(count), y0 + 6, Ui.MUTED);
		String here = mc.level == null ? "" : Waypoints.dim(mc);
		scroll = Math.max(0, Math.min(scroll, all.size() - listRows));
		if (all.isEmpty()) Ui.text(g, Ui.small(Component.translatable("nosclient.wpm.empty")), x0 + 8, listTop + 8, Ui.MUTED);
		for (int r = 0; r < listRows && scroll + r < all.size(); r++) {
			int i = scroll + r, y = listTop + r * ROW;
			Wp wp = all.get(i);
			boolean hv = mx >= x0 && mx < x0 + w && my >= y && my < y + ROW - 2, on = i == sel;
			g.fill(x0, y, x0 + w, y + ROW - 2, on ? Ui.alpha(ACC, .28f) : hv ? 0x1AFFFFFF : 0x0FFFFFFF);
			if (on) Ui.brackets(g, x0, y, x0 + w, y + ROW - 2, 5, ACC);
			// viên kim cương màu như trên HUD
			int cx = x0 + 11, cy = y + 10, col = wp.hidden() ? Ui.alpha(wp.color(), .35f) : wp.color();
			for (int k = 0; k < 4; k++) g.fill(cx - k, cy - 3 + k, cx + k + 1, cy - 2 + k, col);
			for (int k = 0; k < 3; k++) g.fill(cx - 2 + k, cy + 1 + k, cx + 3 - k, cy + 2 + k, col);
			Ui.text(g, Ui.fit(Ui.bold(Component.literal(wp.name())), 130), x0 + 22, y + 3, wp.hidden() ? Ui.MUTED : Ui.TEXT);
			String dimName = wp.dim().substring(wp.dim().indexOf(':') + 1).replace('_', ' ');
			Ui.text(g, Ui.small(Component.literal(wp.x() + " " + wp.y() + " " + wp.z() + "  ·  " + dimName)), x0 + 22, y + 13, Ui.MUTED);
			String right;
			if (wp.dim().equals(here) && mc.player != null) {
				double dx = wp.x() + .5 - mc.player.getX(), dz = wp.z() + .5 - mc.player.getZ();
				// hướng tương đối so với hướng nhìn: mũi tên 8 hướng
				double ang = Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot();
				String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
				right = arrows[(int) Math.floorMod(Math.round(ang / 45), 8)] + " " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " m";
			} else right = "—";
			if (wp.hidden()) right = Component.translatable("nosclient.wpm.hidden").getString() + "  " + right;
			Component rc = Ui.small(Component.literal(right));
			Ui.text(g, rc, x0 + w - 8 - Ui.width(rc), y + 8, Ui.TEXT);
		}
		if (all.size() > listRows) { // thanh cuộn mảnh
			int track = listRows * ROW, th = Math.max(12, track * listRows / all.size()), ty = listTop + (track - th) * scroll / Math.max(1, all.size() - listRows);
			g.fill(x0 + w + 3, ty, x0 + w + 5, ty + th, 0x66FFFFFF);
		}
		int ny = height - 22;
		if (!note.getString().isEmpty()) Ui.text(g, Ui.small(note), x0, ny, Ui.ACCENT_HI);
		if (undo != null) {
			Component u = Ui.bold(Component.translatable("nosclient.wpm.undo"));
			int ux = x0 + w - Ui.width(u);
			Ui.text(g, u, ux, ny, mx >= ux && my >= ny - 2 && my < ny + 10 ? Ui.TEXT : Ui.ACCENT_HI);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (super.mouseClicked(e, dbl)) return true;
		int ny = height - 22;
		if (undo != null && e.y() >= ny - 2 && e.y() < ny + 10 && e.x() >= x0 + w - 80 && e.x() < x0 + w) {
			restore();
			return true;
		}
		if (e.x() >= x0 && e.x() < x0 + w && e.y() >= listTop && e.y() < listTop + listRows * ROW) {
			int i = scroll + (int) ((e.y() - listTop) / ROW);
			if (i < list().size()) {
				sel = i;
				name.setValue(list().get(i).name());
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scroll -= (int) Math.signum(sy);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		boolean typing = name.isFocused() || coords.isFocused();
		if (!typing && (e.key() == GLFW.GLFW_KEY_DELETE || e.key() == GLFW.GLFW_KEY_BACKSPACE)) {
			delete();
			return true;
		}
		if (e.key() == GLFW.GLFW_KEY_Z && e.hasControlDown()) {
			restore();
			return true;
		}
		if (!typing && (e.key() == GLFW.GLFW_KEY_UP || e.key() == GLFW.GLFW_KEY_DOWN) && !list().isEmpty()) {
			sel = Math.max(0, Math.min(list().size() - 1, sel + (e.key() == GLFW.GLFW_KEY_UP ? -1 : 1)));
			if (sel < scroll) scroll = sel;
			if (sel >= scroll + listRows) scroll = sel - listRows + 1;
			return true;
		}
		if (e.key() == GLFW.GLFW_KEY_ENTER && (name.isFocused() || coords.isFocused())) {
			if (hasSel() && name.isFocused() && coords.getValue().isBlank() && !name.getValue().equals(list().get(sel).name())) rename();
			else addFromFields();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void onClose() {
		mc.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
