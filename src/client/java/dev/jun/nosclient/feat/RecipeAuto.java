package dev.jun.nosclient.feat;

import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.RecipeBookType;

/**
 * Xem công thức: mở sẵn sách công thức mỗi khi vào túi đồ. Sách của game đã nạp công thức vào bộ nhớ
 * từ lúc vào thế giới (spec: "load vào cache của game"), nên chỉ cần bật trạng thái mở.
 */
public final class RecipeAuto {
	private RecipeAuto() {}

	public static void init() {
		ScreenEvents.BEFORE_INIT.register((mc, screen, w, h) -> {
			if (!SceneConfig.INSTANCE.recipe || !(screen instanceof InventoryScreen) || mc.player == null) return;
			var book = mc.player.getRecipeBook();
			if (!book.isOpen(RecipeBookType.CRAFTING)) book.setOpen(RecipeBookType.CRAFTING, true);
		});
	}
}
