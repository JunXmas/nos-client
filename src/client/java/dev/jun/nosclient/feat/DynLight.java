package dev.jun.nosclient.feat;

import dev.jun.nosclient.Mods;
import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class DynLight {
    public static volatile int sx;
    public static volatile int sy;
    public static volatile int sz;
    public static volatile int level;
    private static int lastX = Integer.MIN_VALUE;
    private static int lastY;
    private static int lastZ;
    private static int lastLevel;

    private DynLight() {
    }

    static int emission(ItemStack it) {
        if (it.isEmpty()) {
            return 0;
        } else if (it.getItem() instanceof BlockItem bi) {
            return bi.getBlock().defaultBlockState().getLightEmission();
        } else if (it.is(Items.LAVA_BUCKET)) {
            return 15;
        } else if (it.is(Items.GLOW_INK_SAC) || it.is(Items.GLOW_BERRIES)) {
            return 6;
        } else {
            return !it.is(Items.BLAZE_ROD) && !it.is(Items.FIRE_CHARGE) ? 0 : 10;
        }
    }

    public static void tick(Minecraft mc) {
        Player p = mc.player;
        int lv = 0;
        if (SceneConfig.INSTANCE.dynlight && !Mods.LIGHT_OWNED && p != null) {
            lv = Math.max(emission(p.getMainHandItem()), emission(p.getOffhandItem()));
            if (p.isOnFire()) {
                lv = Math.max(lv, 15);
            }
        }

        BlockPos b = p == null ? BlockPos.ZERO : BlockPos.containing(p.getEyePosition());
        if (lv != lastLevel || lv != 0 && (b.getX() != lastX || b.getY() != lastY || b.getZ() != lastZ)) {
            if (lastLevel > 0) {
                dirty(mc, lastX, lastY, lastZ, lastLevel);
            }

            sx = b.getX();
            sy = b.getY();
            sz = b.getZ();
            level = lv;
            if (lv > 0) {
                dirty(mc, sx, sy, sz, lv);
            }

            lastX = sx;
            lastY = sy;
            lastZ = sz;
            lastLevel = lv;
        }
    }

    private static void dirty(Minecraft mc, int x, int y, int z, int lv) {
        if (mc.levelRenderer != null) {
            int r = lv;

            for (int cx = SectionPos.blockToSectionCoord(x - r); cx <= SectionPos.blockToSectionCoord(x + r); cx++) {
                for (int cy = SectionPos.blockToSectionCoord(y - r); cy <= SectionPos.blockToSectionCoord(y + r); cy++) {
                    for (int cz = SectionPos.blockToSectionCoord(z - r); cz <= SectionPos.blockToSectionCoord(z + r); cz++) {
                        mc.levelRenderer.setSectionDirty(cx, cy, cz);
                    }
                }
            }
        }
    }

    public static int boost(BlockPos pos, int block) {
        int lv = level;
        if (lv == 0) {
            return block;
        }

        int d = Math.abs(pos.getX() - sx) + Math.abs(pos.getY() - sy) + Math.abs(pos.getZ() - sz);
        return Math.max(block, lv - d);
    }
}
