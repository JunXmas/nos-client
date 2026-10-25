package dev.jun.nosclient.gui;

import dev.jun.nosclient.Mods;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.feat.Waypoints;
import dev.jun.nosclient.map.MapCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Bản đồ thế giới toàn màn (phím M): mọi nơi đã đi qua ở chiều hiện tại, đọc từ MapCache.
 * Kéo để dịch, cuộn để zoom (0,25–8 px/khối), bấm đúp hoặc Space để về chỗ mình, chuột phải để đặt waypoint
 * (hoặc xoá waypoint đang trỏ). Zoom gần thì hiện lưới chunk.
 */
public final class WorldMapScreen extends Screen {
	private final Screen parent;
	private final Minecraft mc = Minecraft.getInstance();
	/** Tâm màn hình đang nhìn (toạ độ khối) và zoom (px giao diện mỗi khối). */
	private double cx, cz, zoom = 1;
	private boolean dragging;
	private long lastClick;
	private Component note = Component.empty();

	public WorldMapScreen(Screen parent) {
		super(Component.translatable("nosclient.map.title"));
		this.parent = parent;
		if (mc.player != null) {
			cx = mc.player.getX();
			cz = mc.player.getZ();
		}
	}

	private double toWorldX(double sx) {
		return cx + (sx - width / 2.0) / zoom;
	}

	private double toWorldZ(double sy) {
		return cz + (sy - height / 2.0) / zoom;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		g.fill(0, 0, width, height, 0xFF0D1016);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		super.render(g, mx, my, pt);
		if (mc.level == null || mc.player == null) return;
		double x0 = toWorldX(0), z0 = toWorldZ(0), x1 = toWorldX(width), z1 = toWorldZ(height);
		int r0x = Math.floorDiv((int) Math.floor(x0), MapCache.R), r1x = Math.floorDiv((int) Math.ceil(x1), MapCache.R);
		int r0z = Math.floorDiv((int) Math.floor(z0), MapCache.R), r1z = Math.floorDiv((int) Math.ceil(z1), MapCache.R);
		g.pose().pushMatrix();
		g.pose().translate(width / 2f, height / 2f);
		g.pose().scale((float) zoom, (float) zoom);
		g.pose().translate((float) -cx, (float) -cz);
		for (int rx = r0x; rx <= r1x; rx++)
			for (int rz = r0z; rz <= r1z; rz++) {
				var reg = MapCache.region(rx, rz, false);
				if (reg == null) continue;
				g.blit(RenderPipelines.GUI_TEXTURED, reg.id(), rx * MapCache.R, rz * MapCache.R, 0, 0, MapCache.R, MapCache.R, MapCache.R, MapCache.R);
			}
		g.pose().popMatrix();
		if (zoom >= 2) { // lưới chunk
			for (int x = (int) Math.floor(x0 / 16) * 16; x <= x1; x += 16) {
				int sx = (int) Math.round((x - cx) * zoom + width / 2.0);
				g.fill(sx, 0, sx + 1, height, 0x18FFFFFF);
			}
			for (int z = (int) Math.floor(z0 / 16) * 16; z <= z1; z += 16) {
				int sy = (int) Math.round((z - cz) * zoom + height / 2.0);
				g.fill(0, sy, width, sy + 1, 0x18FFFFFF);
			}
		}
		// waypoint
		for (var w : Waypoints.here(mc)) {
			int sx = (int) Math.round((w.x() + .5 - cx) * zoom + width / 2.0), sy = (int) Math.round((w.z() + .5 - cz) * zoom + height / 2.0);
			if (sx < -20 || sy < -20 || sx > width + 20 || sy > height + 20) continue;
			for (int k = 0; k < 4; k++) g.fill(sx - k, sy - 3 + k, sx + k + 1, sy - 2 + k, w.color());
			for (int k = 0; k < 3; k++) g.fill(sx - 2 + k, sy + 1 + k, sx + 3 - k, sy + 2 + k, w.color());
			Component n = Ui.small(Component.literal(w.name()));
			g.fill(sx - Ui.width(n) / 2 - 3, sy + 6, sx + Ui.width(n) / 2 + 3, sy + 16, 0xA0000000);
			Ui.text(g, n, sx - Ui.width(n) / 2, sy + 8, Ui.TEXT);
		}
		// người chơi
		var p = mc.player;
		int px = (int) Math.round((p.getX() - cx) * zoom + width / 2.0), py = (int) Math.round((p.getZ() - cz) * zoom + height / 2.0);
		g.pose().pushMatrix();
		g.pose().translate(px, py);
		g.pose().rotate((float) Math.toRadians(p.getYRot() + 180));
		for (int i = 0; i < 7; i++) g.fill(-i / 2 - 1, -5 + i, i / 2 + 2, -4 + i, 0xFF000000);
		for (int i = 0; i < 6; i++) g.fill(-i / 2, -4 + i, i / 2 + 1, -3 + i, 0xFFFFFFFF);
		g.pose().popMatrix();
		// thanh trên: tiêu đề, toạ độ con trỏ, tỉ lệ, gợi ý
		g.fill(0, 0, width, 22, 0xD0101218);
		g.fill(0, 22, width, 23, Ui.alpha(Mods.catColor("world"), .6f));
		Ui.text(g, Ui.bold(title), 8, 7, Ui.TEXT);
		String dim = mc.level.dimension().identifier().getPath().replace('_', ' ');
		Component info = Ui.small(Component.literal((int) Math.floor(toWorldX(mx)) + ", " + (int) Math.floor(toWorldZ(my)) + "   ·   " + dim + "   ·   "
			+ (zoom >= 1 ? "1 khối = " + (zoom == Math.rint(zoom) ? String.valueOf((int) zoom) : String.valueOf(zoom)) + " px" : "1 px = " + (int) Math.round(1 / zoom) + " khối")));
		Ui.text(g, info, 8 + Ui.width(Ui.bold(title)) + 14, 9, Ui.MUTED);
		Component hint = Ui.small(Component.translatable("nosclient.map.hint"));
		Ui.text(g, hint, width - 8 - Ui.width(hint), 9, Ui.MUTED);
		if (!note.getString().isEmpty()) {
			int nw = Ui.width(Ui.small(note)) + 16;
			g.fill(width / 2 - nw / 2, height - 30, width / 2 + nw / 2, height - 14, 0xD0101218);
			Ui.text(g, Ui.small(note), width / 2 - nw / 2 + 8, height - 26, Ui.ACCENT_HI);
		}
	}

	private void recenter() {
		if (mc.player == null) return;
		cx = mc.player.getX();
		cz = mc.player.getZ();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (e.button() == 1) { // chuột phải: xoá waypoint đang trỏ, không thì đặt mới ở đây
			int wx = (int) Math.floor(toWorldX(e.x())), wz = (int) Math.floor(toWorldZ(e.y()));
			double pick = 6 / zoom;
			for (var w : Waypoints.mine(mc))
				if (w.dim().equals(Waypoints.dim(mc)) && Math.abs(w.x() + .5 - toWorldX(e.x())) <= pick && Math.abs(w.z() + .5 - toWorldZ(e.y())) <= pick) {
					Waypoints.mine(mc).remove(w);
					Waypoints.saveNow();
					note = Component.translatable("nosclient.wpm.deleted", w.name());
					return true;
				}
			int y = mc.player == null ? 64 : mc.player.blockPosition().getY();
			var col = mc.level.getChunkSource().getChunk(wx >> 4, wz >> 4, false);
			if (col != null) y = col.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15);
			var w = Waypoints.add(mc, null, wx, y, wz);
			note = Component.translatable("nosclient.wp.added", w.name(), w.x(), w.y(), w.z());
			return true;
		}
		long now = System.currentTimeMillis();
		if (now - lastClick < 300) recenter();
		lastClick = now;
		dragging = true;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (!dragging) return false;
		cx -= dx / zoom;
		cz -= dy / zoom;
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		dragging = false;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		// zoom quanh con trỏ: điểm dưới chuột đứng yên
		double wx = toWorldX(mx), wz = toWorldZ(my);
		zoom = Math.max(.25, Math.min(8, zoom * (sy > 0 ? 2 : .5)));
		cx = wx - (mx - width / 2.0) / zoom;
		cz = wz - (my - height / 2.0) / zoom;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == GLFW.GLFW_KEY_SPACE) {
			recenter();
			return true;
		}
		if (dev.jun.nosclient.feat.Feats.WORLD_MAP.matches(e)) {
			onClose();
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
