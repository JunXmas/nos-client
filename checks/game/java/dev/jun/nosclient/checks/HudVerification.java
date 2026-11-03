package dev.jun.nosclient.checks;

import com.google.gson.JsonParser;

import dev.jun.nosclient.hud.Hud;
import dev.jun.nosclient.hud.HudLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.lang.reflect.Field;
import java.nio.file.Files;

/** Kéo ô karaoke và hai thanh chỉnh qua sự kiện chuột của trình sửa HUD. */
final class HudVerification {
    private int step;
    private float initialHeight;

    void tick(Minecraft mc, Screen screen, double elapsed) throws Exception {
        if (elapsed >= .6 && step == 0) {
            float[] bounds = Hud.INSTANCE.rects.get("karaoke");
            if (bounds == null) throw new AssertionError("Karaoke preview is missing");
            initialHeight = bounds[3] - bounds[1];
            MouseButtonEvent start = event(bounds[0] + 6, bounds[1] + 6);
            screen.mouseClicked(start, false);
            if (!screen.isDragging()) throw new AssertionError("HUD did not capture the pointer");
            MouseButtonEvent target = event(screen.width * .3, screen.height * .5);
            screen.mouseDragged(target, target.x() - start.x(), target.y() - start.y());
            screen.mouseReleased(target);
            if (HudLayout.get("karaoke").auto()) throw new AssertionError("HUD drag was not saved");
            step++;
        }
        if (elapsed >= 1.2 && step == 1) {
            slide(screen, 0, .7);
            if (Math.abs(HudLayout.get("karaoke").scale - 1.55) > .01)
                throw new AssertionError("Karaoke scale slider failed");
            step++;
        }
        if (elapsed >= 1.8 && step == 2) {
            slide(screen, 1, .375);
            if (Math.abs(HudLayout.get("karaoke").alpha - .5) > .01)
                throw new AssertionError("Karaoke opacity slider failed");
            step++;
        }
        if (elapsed >= 2.5 && step == 3) {
            float[] bounds = Hud.INSTANCE.rects.get("karaoke");
            if (bounds[3] - bounds[1] < initialHeight * 1.4)
                throw new AssertionError("Karaoke scale did not change its rendered bounds");
            var root =
                    JsonParser.parseString(
                                    Files.readString(
                                            mc.gameDirectory
                                                    .toPath()
                                                    .resolve("config/nosclient.json")))
                            .getAsJsonObject();
            var saved =
                    root.getAsJsonObject("hud").getAsJsonObject("slots").getAsJsonObject("karaoke");
            if (Math.abs(saved.get("scale").getAsDouble() - 1.55) > .01
                    || Math.abs(saved.get("alpha").getAsDouble() - .5) > .01)
                throw new AssertionError("Karaoke layout was not persisted in the shared config");
            step++;
        }
    }

    private static void slide(Screen screen, int index, double fraction) throws Exception {
        int x = read(screen, "barX") + index * read(screen, "barStride");
        int y = read(screen, "barY");
        MouseButtonEvent click = event(x + read(screen, "barW") * fraction, y);
        screen.mouseClicked(click, false);
        screen.mouseDragged(click, 0, 0);
        screen.mouseReleased(click);
    }

    private static int read(Screen screen, String name) throws Exception {
        Field field = screen.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(screen);
    }

    private static MouseButtonEvent event(double x, double y) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
    }
}
