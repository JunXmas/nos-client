package dev.jun.nosclient.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record TooltipPlacement(int x, int y) {
    public static TooltipPlacement at(int screenWidth, int screenHeight, int mouseX, int mouseY, int width, int height) {
        int x = mouseX + 12;
        int y = mouseY + 14;
        if (x + width > screenWidth - 4) {
            x = mouseX - width - 12;
        }

        if (y + height > screenHeight - 4) {
            y = mouseY - height - 14;
        }

        return new TooltipPlacement(Math.max(4, Math.min(screenWidth - width - 4, x)), Math.max(4, Math.min(screenHeight - height - 4, y)));
    }
}
