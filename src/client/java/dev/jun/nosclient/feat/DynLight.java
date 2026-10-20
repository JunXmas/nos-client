package dev.jun.nosclient.feat;

import dev.jun.nosclient.scene.SceneConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Dynamic light: đồ phát sáng trên tay (đuốc, đèn lồng, glowstone…) hoặc người đang cháy toả sáng quanh người chơi.
 * Mixin vào LevelRenderer.getLightColor nâng mức sáng khối trong bán kính; chỉ đánh dấu dựng lại các section
 * quanh người khi nguồn sáng ĐỔI (đổi đồ hay sang khối khác) — không dựng lại mỗi khung.
 */
public final class DynLight {
	private DynLight() {}

	/** Nguồn sáng hiện tại: toạ độ khối và mức sáng (0 = tắt). Đọc trên luồng dựng chunk nên volatile. */
	public static volatile int sx, sy, sz, level;
	private static int lastX = Integer.MIN_VALUE, lastY, lastZ, lastLevel;

	/** Mức sáng của đồ trên tay: khối phát sáng lấy đúng mức của khối đó; vài món không phải khối có mức riêng. */
	static int emission(ItemStack it) {
		if (it.isEmpty()) return 0;
		if (it.getItem() instanceof BlockItem bi) return bi.getBlock().defaultBlockState().getLightEmission();
		if (it.is(Items.LAVA_BUCKET)) return 15;
		if (it.is(Items.GLOW_INK_SAC) || it.is(Items.GLOW_BERRIES)) return 6;
		if (it.is(Items.BLAZE_ROD) || it.is(Items.FIRE_CHARGE)) return 10;
		return 0;
	}

	public static void tick(Minecraft mc) {
		Player p = mc.player;
		int lv = 0;
		if (SceneConfig.INSTANCE.dynlight && p != null) {
			lv = Math.max(emission(p.getMainHandItem()), emission(p.getOffhandItem()));
			if (p.isOnFire()) lv = Math.max(lv, 15);
		}
		BlockPos b = p == null ? BlockPos.ZERO : BlockPos.containing(p.getEyePosition());
		if (lv == lastLevel && (lv == 0 || b.getX() == lastX && b.getY() == lastY && b.getZ() == lastZ)) return;
		// đánh dấu dựng lại vùng sáng cũ và mới
		if (lastLevel > 0) dirty(mc, lastX, lastY, lastZ, lastLevel);
		sx = b.getX();
		sy = b.getY();
		sz = b.getZ();
		level = lv;
		if (lv > 0) dirty(mc, sx, sy, sz, lv);
		lastX = sx;
		lastY = sy;
		lastZ = sz;
		lastLevel = lv;
	}

	private static void dirty(Minecraft mc, int x, int y, int z, int lv) {
		if (mc.levelRenderer == null) return;
		int r = lv; // ánh sáng giảm 1 mỗi khối
		for (int cx = SectionPos.blockToSectionCoord(x - r); cx <= SectionPos.blockToSectionCoord(x + r); cx++)
			for (int cy = SectionPos.blockToSectionCoord(y - r); cy <= SectionPos.blockToSectionCoord(y + r); cy++)
				for (int cz = SectionPos.blockToSectionCoord(z - r); cz <= SectionPos.blockToSectionCoord(z + r); cz++)
					mc.levelRenderer.setSectionDirty(cx, cy, cz);
	}

	/** Mức sáng khối tại pos sau khi cộng nguồn sáng tay (ánh sáng giảm 1 mỗi khối khoảng cách Manhattan, như game). */
	public static int boost(BlockPos pos, int block) {
		int lv = level;
		if (lv == 0) return block;
		int d = Math.abs(pos.getX() - sx) + Math.abs(pos.getY() - sy) + Math.abs(pos.getZ() - sz);
		return Math.max(block, lv - d);
	}
}
