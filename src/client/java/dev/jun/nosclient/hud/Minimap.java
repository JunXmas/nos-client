package dev.jun.nosclient.hud;

import dev.jun.nosclient.scene.SceneConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Bản đồ nhỏ nhìn từ trên xuống, theo spec:
 * - chỉ bề mặt (heightmap WORLD_SURFACE) nên không bao giờ lộ hang động;
 * - bề rộng tối đa chỉnh theo chunk; nếu khoảng nhìn của game nhỏ hơn thì chỉ vẽ tới khoảng nhìn;
 * - màu khối dùng MapColor của game (giống bản đồ giấy), đổ bóng theo chênh độ cao như game.
 * Rẻ: texture cố định 1 px/khối, mỗi tick quét lại 1/8 số hàng — đủ tươi mà không tốn khung nào.
 */
public final class Minimap {
	private Minimap() {}

	private static final int MAX = 32 * 16 + 1; // 32 chunk bán kính → texture tối đa 513
	private static final Identifier TEX = Identifier.fromNamespaceAndPath("nosclient", "minimap");
	private static DynamicTexture tex;
	private static int size, cx = Integer.MIN_VALUE, cz, row;

	/** Bán kính khối thật đang vẽ: min(tuỳ chọn, khoảng nhìn) × 16. */
	private static int radius(Minecraft mc) {
		return Math.min(SceneConfig.INSTANCE.minimapChunks, mc.options.getEffectiveRenderDistance()) * 16;
	}

	public static void tick(Minecraft mc) {
		if (!SceneConfig.INSTANCE.minimap || mc.player == null || mc.level == null) return;
		int r = radius(mc), s = r * 2 + 1;
		if (tex == null || size != s) {
			if (tex != null) tex.close();
			tex = new DynamicTexture(() -> "nosclient minimap", s, s, true);
			mc.getTextureManager().register(TEX, tex);
			size = s;
			cx = Integer.MIN_VALUE;
		}
		BlockPos p = mc.player.blockPosition();
		// đi xa khỏi tâm ảnh hơn 1/8 bán kính thì vẽ lại cả ảnh quanh vị trí mới
		boolean full = cx == Integer.MIN_VALUE || Math.abs(p.getX() - cx) > r / 8 || Math.abs(p.getZ() - cz) > r / 8;
		if (full) {
			cx = p.getX();
			cz = p.getZ();
			row = 0;
		}
		int rows = full ? s : Math.max(8, s / 8);
		var img = tex.getPixels();
		var lvl = mc.level;
		var mp = new BlockPos.MutableBlockPos();
		for (int k = 0; k < rows; k++, row = (row + 1) % s) {
			int z = cz - r + row, prevH = Integer.MIN_VALUE;
			for (int i = 0; i < s; i++) {
				int x = cx - r + i;
				if (!lvl.hasChunk(x >> 4, z >> 4)) {
					img.setPixel(i, row, 0);
					continue;
				}
				int h = lvl.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				var st = lvl.getBlockState(mp.set(x, h, z));
				MapColor mc0 = st.getMapColor(lvl, mp);
				int hn = prevH == Integer.MIN_VALUE ? lvl.getHeight(Heightmap.Types.WORLD_SURFACE, x - 1, z) - 1 : prevH;
				var br = h > hn ? MapColor.Brightness.HIGH : h < hn ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
				img.setPixel(i, row, mc0 == MapColor.NONE ? 0 : mc0.calculateARGBColor(br) | 0xFF000000);
				prevH = h;
			}
		}
		tex.upload();
	}

	/** Vẽ ô bản đồ vuông canh phải trên, kích thước cố định trên màn hình; mũi tên người chơi ở giữa. */
	public static int render(GuiGraphics g, Minecraft mc, int xr, int y, float alpha) {
		if (tex == null || cx == Integer.MIN_VALUE) return y;
		int box = 72, x = xr - box;
		g.fill(x - 2, y - 2, x + box + 2, y + box + 2, Math.round(alpha * 160) << 24);
		BlockPos p = mc.player.blockPosition();
		int r = (size - 1) / 2;
		// lệch ảnh theo phần người chơi đã đi khỏi tâm ảnh
		float scale = box / (float) size, ox = (p.getX() - cx) * scale, oz = (p.getZ() - cz) * scale;
		g.enableScissor(x, y, x + box, y + box);
		g.blit(RenderPipelines.GUI_TEXTURED, TEX, Math.round(x - ox), Math.round(y - oz), 0, 0, box, box, size, size, size, size);
		g.disableScissor();
		int mx = x + box / 2, my = y + box / 2;
		g.fill(mx - 1, my - 1, mx + 2, my + 2, 0xFFFFFFFF);
		// vạch hướng nhìn
		double yaw = Math.toRadians(mc.player.getYRot());
		int dx = (int) Math.round(-Math.sin(yaw) * 5), dz = (int) Math.round(Math.cos(yaw) * 5);
		g.fill(Math.min(mx, mx + dx), Math.min(my, my + dz), Math.max(mx, mx + dx) + 1, Math.max(my, my + dz) + 1, 0xFFD3A8F5);
		return y + box + 6;
	}
}
