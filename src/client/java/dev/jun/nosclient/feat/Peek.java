package dev.jun.nosclient.feat;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.gui.Glass;
import dev.jun.nosclient.gui.TooltipPlacement;
import dev.jun.nosclient.mixin.AbstractContainerScreenAccessor;
import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

@Environment(EnvType.CLIENT)
public final class Peek {
    private static ItemContainerContents lastContents;
    private static NonNullList<ItemStack> slots = NonNullList.withSize(27, ItemStack.EMPTY);
    private static long appeared;

    private Peek() {
    }

    public static boolean render(AbstractContainerScreen<?> screen, GuiGraphics g, int mouseX, int mouseY) {
        SceneConfig c = SceneConfig.INSTANCE;
        Slot slot = ((AbstractContainerScreenAccessor)screen).nosclient$hovered();
        if (c.shulker && slot != null && slot.hasItem() && screen.getMenu().getCarried().isEmpty()) {
            ItemStack stack = slot.getItem();
            if (stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock box) {
                ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
                if (contents != null && !contents.nonEmptyStream().findAny().isEmpty()) {
                    if (contents != lastContents) {
                        lastContents = contents;
                        slots = NonNullList.withSize(27, ItemStack.EMPTY);
                        contents.copyInto(slots);
                        appeared = System.nanoTime();
                    }

                    int w = 174;
                    int h = 82;
                    TooltipPlacement at = TooltipPlacement.at(screen.width, screen.height, mouseX, mouseY, w, h);
                    float enter = c.animations ? Ui.ease(Math.min(1.0F, (float)(System.nanoTime() - appeared) / 1.4E8F)) : 1.0F;
                    float scale = 0.96F + 0.04F * enter;
                    g.nextStratum();
                    g.pose().pushMatrix();
                    g.pose().translate(at.x() + w / 2.0F, at.y() + h / 2.0F);
                    g.pose().scale(scale, scale);
                    g.pose().translate(-w / 2.0F, -h / 2.0F);

                    try {
                        Glass.panel(g, 0, 0, w, h, -233497565, 1789904827);
                        if (c.shulkerColor) {
                            Glass.fill(g, 6, 6, 3, 10, box.getColor() == null ? -4153639 : 0xFF000000 | box.getColor().getTextureDiffuseColor());
                        }

                        Ui.text(g, Ui.fit(Ui.small(stack.getHoverName()), w - 25), 13, 8, Ui.TEXT);

                        for (int i = 0; i < 27; i++) {
                            int x = 6 + i % 9 * 18;
                            int y = 22 + i / 9 * 18;
                            g.fill(x, y, x + 16, y + 16, 469762047);
                            if (!slots.get(i).isEmpty()) {
                                g.renderItem(slots.get(i), x, y);
                                g.renderItemDecorations(Minecraft.getInstance().font, slots.get(i), x, y);
                            }
                        }
                    } finally {
                        g.pose().popMatrix();
                    }

                    return true;
                } else {
                    lastContents = null;
                    return false;
                }
            } else {
                lastContents = null;
                return false;
            }
        } else {
            lastContents = null;
            return false;
        }
    }
}
