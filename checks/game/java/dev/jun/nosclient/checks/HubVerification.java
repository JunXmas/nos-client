package dev.jun.nosclient.checks;

import dev.jun.nosclient.gui.UiTransform;
import dev.jun.nosclient.gui.music.MusicPanels;
import dev.jun.nosclient.gui.music.Ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Gửi sự kiện qua Screen thật; reflection chỉ để chọn điều khiển và đọc kết quả kiểm thử. */
final class HubVerification {
    private Ui.Choice lyricsChoice;
    private int choicesChecked;
    private boolean choiceScrolled;
    private static final java.util.Map<String, String> OWNERS = new java.util.HashMap<>();
    private Ui.Toggle toggle;
    private Ui.Slider slider;
    private boolean previousToggle;
    private double previousSlider;
    private int step;
    private int frame;
    private int actedFrame = -2;

    void rendered() {
        frame++;
    }

    boolean done() {
        return step >= 5 && (lyricsChoice == null || choicesChecked >= 3);
    }

    void tick(Screen screen, double elapsed) throws Exception {
        MusicPanels panel = (MusicPanels) read(screen, "musicPanels");
        if (elapsed < .8 || frame < actedFrame + 2) return;
        if (Math.abs((float) read(screen, "dScroll") - (double) read(screen, "dTarget")) > .75)
            return;
        if (step == 0) {
            List<Ui.Widget> rows = new ArrayList<>();
            for (Ui.Widget widget : (List<Ui.Widget>) read(panel, "widgets")) collect(widget, rows);
            for (Ui.Widget row : rows) {
                if (!(row instanceof Ui.Toggle
                        || row instanceof Ui.Slider
                        || row instanceof Ui.Choice)) continue;
                String key = (String) read(row, "label");
                if (java.util.Set.of(
                                "nosclient.music.opt.lyrics_style",
                                "nosclient.music.opt.scale",
                                "nosclient.music.opt.sync_offset",
                                "nosclient.music.opt.show_lyrics",
                                "nosclient.music.opt.enabled",
                                "nosclient.music.opt.top_bar",
                                "nosclient.music.opt.media_keys")
                        .contains(key)) {
                    String owner = String.valueOf(read(panel, "tab"));
                    if (OWNERS.putIfAbsent(key, owner) != null)
                        throw new AssertionError("Duplicate control: " + key);
                }
                if (key.equals("nosclient.music.opt.lyrics_style")) lyricsChoice = (Ui.Choice) row;
            }
            toggle =
                    rows.stream()
                            .filter(Ui.Toggle.class::isInstance)
                            .map(Ui.Toggle.class::cast)
                            .findFirst()
                            .orElse(null);
            slider =
                    rows.stream()
                            .filter(Ui.Slider.class::isInstance)
                            .map(Ui.Slider.class::cast)
                            .findFirst()
                            .orElse(null);
            if (toggle != null) {
                previousToggle = toggleValue();
                scroll(screen, panel, toggle);
            }
            step++;
            actedFrame = frame;
            return;
        }
        if (step >= 5 && lyricsChoice != null && choicesChecked < 3) {
            List<Ui.Widget> rows = new ArrayList<>();
            for (Ui.Widget widget : (List<Ui.Widget>) read(panel, "widgets")) collect(widget, rows);
            for (Ui.Widget row : rows)
                if (row instanceof Ui.Choice
                        && "nosclient.music.opt.lyrics_style".equals(read(row, "label")))
                    lyricsChoice = (Ui.Choice) row;
            if (!choiceScrolled) {
                scroll(screen, panel, lyricsChoice);
                choiceScrolled = true;
            } else {
                int before =
                        ((java.util.function.IntSupplier) read(lyricsChoice, "get")).getAsInt();
                click(screen, panel, lyricsChoice, .01, 5, false);
                int after = dev.jun.nosclient.config.MusicSettings.get().lyricsStyle.ordinal();
                if (after != (before + 1) % 3)
                    throw new AssertionError(
                            "Lyric style choice did not cycle through all three renderers");
                choicesChecked++;
                choiceScrolled = false;
            }
            actedFrame = frame;
            return;
        }
        if (elapsed >= 1.4 && step == 1) {
            if (toggle != null) {
                click(screen, panel, toggle, .5, toggle.h / 2, false);
                if (toggleValue() == previousToggle)
                    throw new AssertionError("Hub toggle did not change");
            }
            step++;
            actedFrame = frame;
            return;
        }
        if (elapsed >= 2 && step == 2) {
            if (toggle != null) {
                click(screen, panel, toggle, .5, toggle.h / 2, false);
                if (toggleValue() != previousToggle)
                    throw new AssertionError("Hub toggle did not restore");
            }
            if (slider != null) {
                previousSlider = sliderValue();
                scroll(screen, panel, slider);
            } else screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -3);
            step++;
            actedFrame = frame;
            return;
        }
        if (elapsed >= 2.8 && step == 3) {
            if (slider != null) {
                double min = (double) read(slider, "min"), max = (double) read(slider, "max");
                double fraction = (previousSlider - min) / (max - min);
                click(screen, panel, slider, fraction, 22, true);
                if (!screen.isDragging())
                    throw new AssertionError("Hub slider did not capture the pointer");
                MouseButtonEvent target = event(screen, panel, slider, fraction > .5 ? .2 : .8, 22);
                screen.mouseDragged(target, 10, 0);
                screen.mouseReleased(target);
                if (Math.abs(sliderValue() - previousSlider) < 1e-5)
                    throw new AssertionError("Hub slider drag did not change its value");
            }
            step++;
            actedFrame = frame;
            return;
        }
        if (elapsed >= 3.6 && step == 4) {
            if (slider != null) {
                double min = (double) read(slider, "min"), max = (double) read(slider, "max");
                click(screen, panel, slider, (previousSlider - min) / (max - min), 22, false);
                double tolerance =
                        Math.max((max - min) / slider.w * 2, (double) read(slider, "step"));
                if (Math.abs(sliderValue() - previousSlider) > tolerance + .0001)
                    throw new AssertionError("Hub slider cannot return to its original position");
                // Khôi phục đúng giá trị ban đầu sau phép thử có lượng tử hóa theo pixel.
                ((DoubleConsumer) read(slider, "set")).accept(previousSlider);
                dev.jun.nosclient.config.MusicSettings.save();
                dev.jun.nosclient.ClientEffects.saveConfig();
            }
            step++;
            actedFrame = frame;
            return;
        }
    }

    private boolean toggleValue() throws Exception {
        return ((BooleanSupplier) read(toggle, "get")).getAsBoolean();
    }

    private double sliderValue() throws Exception {
        return ((DoubleSupplier) read(slider, "get")).getAsDouble();
    }

    private static void collect(Ui.Widget widget, List<Ui.Widget> rows) throws Exception {
        if (widget instanceof Ui.Card)
            for (Ui.Widget child : (List<Ui.Widget>) read(widget, "rows")) collect(child, rows);
        else rows.add(widget);
    }

    private static void scroll(Screen screen, MusicPanels panel, Ui.Widget row) throws Exception {
        double y = (float) read(panel, "y") + row.y + row.h / 2;
        screen.mouseScrolled(
                screen.width / 2.0, screen.height / 2.0, 0, -(y - screen.height / 2.0) / 24);
    }

    private static MouseButtonEvent event(
            Screen screen, MusicPanels panel, Ui.Widget row, double fraction, double offsetY)
            throws Exception {
        UiTransform transform = (UiTransform) read(screen, "paneTransform");
        double x = (float) read(panel, "x") + row.x + 2 + (row.w - 4) * fraction;
        double y = (float) read(panel, "y") + row.y + offsetY;
        return new MouseButtonEvent(
                x * transform.scale() + transform.x(),
                y * transform.scale() + transform.y(),
                new MouseButtonInfo(0, 0));
    }

    private static void click(
            Screen screen,
            MusicPanels panel,
            Ui.Widget row,
            double fraction,
            double offsetY,
            boolean hold)
            throws Exception {
        MouseButtonEvent event = event(screen, panel, row, fraction, offsetY);
        screen.mouseClicked(event, false);
        if (!hold) screen.mouseReleased(event);
    }

    private static Object read(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException parent) {
            }
        }
        throw new NoSuchFieldException(name);
    }
}
