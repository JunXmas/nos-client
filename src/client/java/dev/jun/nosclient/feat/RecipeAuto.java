package dev.jun.nosclient.feat;

import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.BeforeInit;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.RecipeBookType;

@Environment(EnvType.CLIENT)
public final class RecipeAuto {
    private RecipeAuto() {
    }

    public static void init() {
        ScreenEvents.BEFORE_INIT.register((BeforeInit)(mc, screen, w, h) -> {
            if (SceneConfig.INSTANCE.recipe && screen instanceof InventoryScreen && mc.player != null) {
                ClientRecipeBook book = mc.player.getRecipeBook();
                if (!book.isOpen(RecipeBookType.CRAFTING)) {
                    book.setOpen(RecipeBookType.CRAFTING, true);
                }
            }
        });
    }
}
