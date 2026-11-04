package dev.jun.nosclient.gui.effects;

import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.render.Themes;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class GlassHotbar {
    private static final Anim sel = new Anim(0.0, 22.0);

    private GlassHotbar() {}

    public static HudElement wrap(HudElement original) {
        return (g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (MusicSettings.get().customHotbar && mc.player != null && !mc.player.isSpectator()) {
                render(g, mc);
            } else {
                original.render(g, delta);
            }
        };
    }

    private static void render(GuiGraphics g, Minecraft mc) {
        float old = Gfx.alpha;
        Gfx.alpha = 1.0F;
        int acc = Themes.accent() & 16777215;
        int w = 182;
        int h = 22;
        int x = g.guiWidth() / 2 - w / 2;
        int y = g.guiHeight() - h - 1;
        Inventory inv = mc.player.getInventory();
        Gfx.round(g, x, y, w, h, 6, 1183260, 0.72F);
        Gfx.round(g, x, y, w, 1, 1, 16777215, 0.07F);
        int selected = inv.getSelectedSlot();
        float sx = sel.target(selected).get();
        int slotX = x + 1 + Math.round(sx * 20.0F);
        Gfx.glow(g, slotX, y + 1, 20, 20, 5, acc, 0.9F, 4);
        Gfx.round(g, slotX, y + 1, 20, 20, 5, acc, 0.22F);
        outline(g, slotX, y + 1, 20, 20, acc);

        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty()) {
                int ix = x + 3 + i * 20;
                int iy = y + 3;
                g.renderItem(st, ix, iy);
                g.renderItemDecorations(mc.font, st, ix, iy);
            }
        }

        ItemStack off = mc.player.getOffhandItem();
        if (!off.isEmpty()) {
            int ox = x - 26;
            Gfx.round(g, ox, y, 22, 22, 6, 1183260, 0.72F);
            g.renderItem(off, ox + 3, y + 3);
            g.renderItemDecorations(mc.font, off, ox + 3, y + 3);
        }

        Gfx.alpha = old;
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int col) {
        Gfx.rect(g, x + 3, y, w - 6, 1, col, 0.95F);
        Gfx.rect(g, x + 3, y + h - 1, w - 6, 1, col, 0.95F);
        Gfx.rect(g, x, y + 3, 1, h - 6, col, 0.95F);
        Gfx.rect(g, x + w - 1, y + 3, 1, h - 6, col, 0.95F);
        Gfx.rect(g, x + 1, y + 1, 2, 1, col, 0.95F);
        Gfx.rect(g, x + 1, y + 2, 1, 1, col, 0.95F);
        Gfx.rect(g, x + w - 3, y + 1, 2, 1, col, 0.95F);
        Gfx.rect(g, x + w - 2, y + 2, 1, 1, col, 0.95F);
        Gfx.rect(g, x + 1, y + h - 2, 2, 1, col, 0.95F);
        Gfx.rect(g, x + 1, y + h - 3, 1, 1, col, 0.95F);
        Gfx.rect(g, x + w - 3, y + h - 2, 2, 1, col, 0.95F);
        Gfx.rect(g, x + w - 2, y + h - 3, 1, 1, col, 0.95F);
    }
}
