package dev.jun.nosclient.hud;

import dev.jun.nosclient.feat.Waypoints;
import dev.jun.nosclient.map.MapCache;
import dev.jun.nosclient.scene.SceneConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Bản đồ nhỏ đọc từ MapCache (1 pixel = 1 khối, đã nhớ nơi đi qua).
 *
 * Tỉ lệ cố định theo px màn hình mỗi khối (0,5/1/2/4, phím +/−), tách khỏi cỡ ô — bản cũ ép cả vùng quét vào ô 72 px
 * nên đổi "bán kính chunk" là đổi zoom và luôn sai tỉ lệ. Tâm theo toạ độ thật nội suy theo partialTick nên trượt mượt,
 * không giật từng khối. Xoay theo hướng nhìn tuỳ chọn; viền có N/E/S/W, waypoint ghim vào mép nếu ở ngoài ô.
 */
public final class Minimap {
	private Minimap() {}

	public static final double[] ZOOMS = {.5, 1, 2, 4};

	public static void tick(Minecraft mc) {
		MapCache.tick(mc);
	}

	public static double zoom() {
		int i = Math.max(0, Math.min(ZOOMS.length - 1, SceneConfig.INSTANCE.minimapZoom));
		return ZOOMS[i];
	}

	/** Ô vuông canh phải trên (xr = mép phải). @return đáy ô. */
	public static int render(GuiGraphics g, Minecraft mc, int xr, int y, float alpha) {
		var c = SceneConfig.INSTANCE;
		int box = c.minimapSize, x = xr - box;
		Hud.mark(x - 2, y - 2, x + box + 2, y + box + 2 + 11);
		g.fill(x - 2, y - 2, x + box + 2, y + box + 2, Math.round(Math.max(alpha, .55f) * 255) << 24);
		var p = mc.player;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		double px = p.xOld + (p.getX() - p.xOld) * pt, pz = p.zOld + (p.getZ() - p.zOld) * pt;
		double k = zoom(); // px trên ô (đơn vị giao diện) mỗi khối
		boolean rot = c.minimapRotate;
		float yaw = p.getViewYRot(pt);
		int cx = x + box / 2, cy = y + box / 2;
		// bán kính khối cần vẽ: nửa đường chéo khi xoay
		double reach = box / 2.0 / k * (rot ? 1.42 : 1);
		int r0x = Math.floorDiv((int) Math.floor(px - reach), MapCache.R), r1x = Math.floorDiv((int) Math.ceil(px + reach), MapCache.R);
		int r0z = Math.floorDiv((int) Math.floor(pz - reach), MapCache.R), r1z = Math.floorDiv((int) Math.ceil(pz + reach), MapCache.R);
		g.enableScissor(x, y, x + box, y + box);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		if (rot) g.pose().rotate((float) Math.toRadians(180 - yaw)); // hướng nhìn chỉ lên trên
		g.pose().scale((float) k, (float) k);
		g.pose().translate((float) -px, (float) -pz);
		for (int rx = r0x; rx <= r1x; rx++)
			for (int rz = r0z; rz <= r1z; rz++) {
				var reg = MapCache.region(rx, rz, false);
				if (reg == null) continue;
				int ox = rx * MapCache.R, oz = rz * MapCache.R;
				g.blit(RenderPipelines.GUI_TEXTURED, reg.id(), ox, oz, 0, 0, MapCache.R, MapCache.R, MapCache.R, MapCache.R);
			}
		g.pose().popMatrix();
		// người chơi: mũi tên ở tâm
		arrow(g, cx, cy, rot ? 0 : (float) Math.toRadians(yaw + 180));
		g.disableScissor();
		// waypoint: chấm màu, ra ngoài ô thì ghim vào mép
		if (c.waypoint) for (var w : Waypoints.here(mc)) {
			double dx = (w.x() + .5 - px) * k, dz = (w.z() + .5 - pz) * k;
			if (rot) {
				double a = Math.toRadians(180 - yaw), ca = Math.cos(a), sa = Math.sin(a);
				double nx = dx * ca - dz * sa, nz = dx * sa + dz * ca;
				dx = nx;
				dz = nz;
			}
			double lim = box / 2.0 - 3, m = Math.max(Math.abs(dx), Math.abs(dz));
			if (m > lim) {
				dx *= lim / m;
				dz *= lim / m;
			}
			int wx = (int) Math.round(cx + dx), wz = (int) Math.round(cy + dz);
			g.fill(wx - 2, wz - 2, wx + 2, wz + 2, 0xFF000000);
			g.fill(wx - 1, wz - 1, wx + 1, wz + 1, w.color());
		}
		// chữ hướng ở viền
		String[] dirs = {"N", "E", "S", "W"};
		for (int i = 0; i < 4; i++) {
			double a = Math.toRadians(i * 90 - (rot ? yaw + 180 : 0));
			int lx = (int) Math.round(cx + Math.sin(a) * (box / 2.0 - 5)), ly = (int) Math.round(cy - Math.cos(a) * (box / 2.0 - 5));
			if (!rot && i != 0) continue; // không xoay: chỉ cần N ở trên
			g.drawString(mc.font, dirs[i], lx - mc.font.width(dirs[i]) / 2, ly - 4, i == 0 ? 0xFFF2555A : 0xFFFFFFFF, true);
		}
		// toạ độ dưới đáy
		String xyz = (int) Math.floor(p.getX()) + " " + (int) Math.floor(p.getY()) + " " + (int) Math.floor(p.getZ());
		g.fill(x - 2, y + box + 2, x + box + 2, y + box + 13, Math.round(Math.max(alpha, .55f) * 255) << 24);
		g.drawString(mc.font, xyz, x + (box - mc.font.width(xyz)) / 2, y + box + 4, 0xFFEEF1F7, false);
		return y + box + 2 + 11 + 6;
	}

	/** Mũi tên tam giác 7 px chỉ theo góc a (0 = lên). */
	private static void arrow(GuiGraphics g, int cx, int cy, float a) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate(a);
		for (int i = 0; i < 6; i++) {
			int half = i / 2;
			g.fill(-half - 1, -4 + i, half + 2, -3 + i, 0xFF000000);
		}
		for (int i = 0; i < 5; i++) {
			int half = i / 2;
			g.fill(-half, -3 + i, half + 1, -2 + i, 0xFFFFFFFF);
		}
		g.pose().popMatrix();
	}
}
