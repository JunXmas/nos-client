package dev.jun.nosclient.gui.music;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

/** Các trang nội dung được đặt ngay trong hub Nos, không có cửa sổ hay thanh điều hướng riêng. */
public final class MusicPanels {
    private List<Ui.Widget> widgets = List.of();
    private Tab tab;
    private boolean rebuild = true;
    private float x;
    private float y;
    private float width;
    private float height;

    public enum Tab {
        NOW,
        LYRICS,
        STYLE,
        SKY,
        COSMETICS,
        DISPLAY,
        PERF,
        KEYS,
        SAVED
    }

    public static Tab tabFor(String feature) {
        return switch (feature) {
            case "music" -> Tab.NOW;
            case "lyrics" -> Tab.LYRICS;
            case "textstyle" -> Tab.STYLE;
            case "sky" -> Tab.SKY;
            case "accessories" -> Tab.COSMETICS;
            case "karaoke" -> Tab.DISPLAY;
            case "mediaKeys" -> Tab.KEYS;
            case "mediaProfiles" -> Tab.SAVED;
            case "visualPerformance" -> Tab.PERF;
            default -> null;
        };
    }

    public static String searchText(String feature) {
        Tab chosen = tabFor(feature);
        if (chosen == null) return "";
        return Pages.build(chosen, new MusicPanels(), true).stream()
                .map(Ui.Widget::searchText)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    public static boolean resetFeature(String id) {
        if (!dev.jun.nosclient.config.ConfigDefaults.reset(
                id,
                dev.jun.nosclient.config.MusicSettings.get(),
                dev.jun.nosclient.ClientEffects.cfg())) return false;
        if (id.equals("sky")) {
            dev.jun.nosclient.scene.SceneConfig.INSTANCE.sky = true;
            dev.jun.nosclient.scene.SceneConfig.INSTANCE.lyricsAurora = false;
        }
        if (id.equals("karaoke")) {
            dev.jun.nosclient.hud.HudLayout.reset("karaoke");
            dev.jun.nosclient.hud.HudLayout.save();
        }
        if (id.equals("mediaKeys")) {
            for (var key : dev.jun.nosclient.MediaKeys.all()) key.setKey(key.getDefaultKey());
            net.minecraft.client.KeyMapping.resetMapping();
            net.minecraft.client.Minecraft.getInstance().options.save();
        }
        dev.jun.nosclient.feat.AutoPerf.reset();
        dev.jun.nosclient.config.MusicSettings.save();
        dev.jun.nosclient.ClientEffects.saveConfig();
        return true;
    }

    public void clear() {
        widgets.forEach(Ui.Widget::release);
        widgets = List.of();
        tab = null;
        rebuild = true;
    }

    public void rebuildSoon() {
        rebuild = true;
    }

    public int render(
            GuiGraphics graphics,
            Tab chosen,
            int left,
            int top,
            int availableWidth,
            int clipTop,
            int clipBottom,
            double mouseX,
            double mouseY) {
        if (rebuild || tab != chosen) {
            tab = chosen;
            widgets.forEach(Ui.Widget::release);
            widgets = Pages.build(tab, this, false);
            rebuild = false;
        }
        x = left;
        y = top;
        width = availableWidth;
        float at = 0;
        for (Ui.Widget widget : widgets) {
            float size = widget.layout(0, at, width);
            if (y + at + size >= clipTop && y + at <= clipBottom) {
                widget.render(graphics, x, y, mouseX, mouseY, 1);
            }
            at += size + 6;
        }
        height = at;
        return Math.round(y + height);
    }

    public boolean click(double mouseX, double mouseY) {
        if (!Ui.inside(mouseX, mouseY, x, y, width, height)) return false;
        for (Ui.Widget widget : widgets) if (widget.click(mouseX, mouseY, x, y)) return true;
        return false;
    }

    public void drag(double mouseX, double mouseY) {
        widgets.forEach(widget -> widget.drag(mouseX, mouseY, x, y));
    }

    public boolean dragging() {
        return widgets.stream().anyMatch(Ui.Widget::dragging);
    }

    public void release() {
        widgets.forEach(Ui.Widget::release);
    }

    public boolean key(KeyEvent event) {
        return widgets.stream().anyMatch(widget -> widget.key(event));
    }

    public boolean character(CharacterEvent event) {
        return widgets.stream().anyMatch(widget -> widget.chr(event));
    }
}
