package dev.jun.nosclient.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class HubCarousel {
    public static final int COUNT = 5;
    private final RenderTween clock = new RenderTween(680000000L);
    private double cursor;
    private double from;
    private double target;
    private int selected;
    private int previous;
    private float open = 1.0F;
    private float collapse;
    private boolean moving;
    private boolean pending;

    public void snap(int selected) {
        this.selected = this.previous = Math.floorMod(selected, 5);
        this.cursor = this.from = this.target = this.selected;
        this.open = 1.0F;
        this.moving = this.pending = false;
    }

    public void select(int index) {
        index = Math.floorMod(index, 5);
        if (index != this.selected) {
            this.previous = this.selected;
            this.collapse = this.open;
            this.selected = index;
            this.from = this.cursor;
            this.target = this.from + relative(index - this.from);
            this.pending = this.moving = true;
            this.open = 0.0F;
        }
    }

    public HubCarousel.Frame frame(long now, boolean animated) {
        if (this.pending) {
            this.clock.restart();
            this.pending = false;
        }

        if (!this.moving) {
            return new HubCarousel.Frame(this.cursor, this.selected, this.previous, 1.0F, 0.0F, false);
        }

        float p = this.clock.linear(now, animated);
        double travel = smooth((p - 0.08) / 0.64);
        this.cursor = this.from + (this.target - this.from) * travel;
        this.open = (float)smooth((p - 0.72) / 0.28);
        float oldOpen = this.collapse * (1.0F - (float)smooth(p / 0.18));
        if (p >= 1.0F) {
            this.moving = false;
            this.cursor = this.from = this.target = this.selected;
        }

        return new HubCarousel.Frame(this.cursor, this.selected, this.previous, this.open, oldOpen, this.moving);
    }

    public static double relative(double value) {
        return value - 5.0 * Math.floor((value + 2.5) / 5.0);
    }

    private static double smooth(double t) {
        t = Math.clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static HubCarousel.Card card(HubCarousel.Frame f, int index, int viewportWidth, int x, int y, int w, int h) {
        double distance = relative(index - f.cursor);
        double absolute = Math.abs(distance);
        float expansion = index == f.selected ? f.open : (index == f.previous ? f.oldOpen : 0.0F);
        boolean wide = viewportWidth >= 600;
        float cw = wide ? 60.0F : Math.min(54.0F, (w - 12) / 5.0F);
        float ch = wide ? 70.0F : 18.0F;
        float centerX = x + w / 2.0F;
        float centerY = y + h / 2.0F;
        float dx;
        float dy;
        if (wide) {
            double radius = (w / 2.0 + 46.0) / Math.tanh(0.86);
            dx = (float)Math.clamp(radius * Math.tanh(distance * 0.86), -viewportWidth / 2.0 + 38.0, viewportWidth / 2.0 - 38.0);
            dy = (float)(-42.0 * (1.0 - Math.cos(distance * 0.7)));
        } else {
            dx = (float)(distance * ((w - cw - 8.0F) / 4.0F));
            dy = (float)((y - 15 - centerY) * smooth(absolute) - 2.0 * Math.max(0.0, absolute - 1.0));
        }

        float visibility = 1.0F - (float)smooth((absolute - 2.0) / 0.48);
        float width = mix(cw, w, expansion);
        float height = mix(ch, h, expansion);
        float cx = centerX + dx * (1.0F - expansion);
        float cy = centerY + dy * (1.0F - expansion);
        return new HubCarousel.Card(cx - width / 2.0F, cy - height / 2.0F, width, height, visibility, expansion);
    }

    @Environment(EnvType.CLIENT)
    public record Card(float x, float y, float width, float height, float opacity, float open) {
    }

    @Environment(EnvType.CLIENT)
    public record Frame(double cursor, int selected, int previous, float open, float oldOpen, boolean moving) {
    }
}
