package dev.jun.nosclient.feat;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.mixin.AbstractContainerScreenAccessor;
import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.saveddata.maps.MapId;

/**
 * Rê chuột trong túi đồ/rương:
 * - hộp Shulker → lưới 9×3 đồ bên trong, đọc thẳng component CONTAINER của món đồ (spec: đọc NBT trực tiếp);
 * - bản đồ → hình bản đồ thật, dùng texture bản đồ game đã có.
 * Vẽ ở lớp trên cùng (nextStratum) nên đè lên tooltip gốc, đặt ngay bên trên con trỏ.
 */
public final class Peek {
	private Peek() {}

	private static final SceneConfig c = SceneConfig.INSTANCE;

	public static void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?>) ScreenEvents.afterRender(screen).register(Peek::render);
		});
	}

	private static void render(Screen screen, GuiGraphics g, int mx, int my, float pt) {
		var slot = ((AbstractContainerScreenAccessor) screen).nosclient$hovered();
		if (slot == null || !slot.hasItem()) return;
		ItemStack it = slot.getItem();
		if (c.shulker && it.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock box) shulker(g, it, box, mx, my, screen.width);
		else if (c.mappv && it.has(DataComponents.MAP_ID)) map(g, it, mx, my, screen.width);
	}

	private static void shulker(GuiGraphics g, ItemStack it, ShulkerBoxBlock box, int mx, int my, int sw) {
		ItemContainerContents cc = it.get(DataComponents.CONTAINER);
		if (cc == null) return;
		ItemStack[] items = cc.stream().limit(27).toArray(ItemStack[]::new);
		boolean any = false;
		for (ItemStack s : items) any |= !s.isEmpty();
		if (!any) return; // hộp trống: để tooltip gốc lo ("Trống")
		int cell = 18, w = 9 * cell + 8, h = 3 * cell + 8;
		int x = Math.min(mx + 12, sw - w - 4), y = Math.max(4, my - h - 14);
		g.nextStratum();
		int tint = box.getColor() == null ? 0xFF8E6E9E : 0xFF000000 | box.getColor().getTextureDiffuseColor();
		Ui.panel(g, x, y, x + w, y + h, 0xF2101218);
		if (c.shulkerColor) g.fill(x, y, x + w, y + 2, tint); // khung theo màu hộp
		for (int i = 0; i < items.length; i++) {
			int cx = x + 4 + (i % 9) * cell, cy = y + 5 + (i / 9) * cell;
			g.fill(cx, cy, cx + 16, cy + 16, 0x14FFFFFF);
			if (items[i].isEmpty()) continue;
			g.renderItem(items[i], cx, cy);
			g.renderItemDecorations(Minecraft.getInstance().font, items[i], cx, cy);
		}
	}

	private static void map(GuiGraphics g, ItemStack it, int mx, int my, int sw) {
		Minecraft mc = Minecraft.getInstance();
		MapId id = it.get(DataComponents.MAP_ID);
		var data = mc.level.getMapData(id);
		if (data == null) return;
		var tex = mc.getMapTextureManager().prepareMapTexture(id, data);
		int s = new int[]{64, 96, 128}[c.mapSize], x = Math.min(mx + 12, sw - s - 12), y = Math.max(4, my - s - 22);
		g.nextStratum();
		Ui.panel(g, x, y, x + s + 8, y + s + 8, 0xF2101218);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x + 4, y + 4, 0, 0, s, s, s, s);
	}
}
