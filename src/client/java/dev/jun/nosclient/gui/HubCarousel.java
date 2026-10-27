package dev.jun.nosclient.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Vòng thẻ danh mục. Con trỏ vòng và độ mở từng thẻ đều là lò xo: thẻ cũ thu lại ngay, vòng quay tới,
 * thẻ mới bung ra khi vòng gần tới nơi — ba pha gối lên nhau thay vì chạy tuần tự, bấm liên tiếp thì
 * vòng đổi hướng mượt chứ không chạy lại từ đầu.
 */
@Environment(EnvType.CLIENT)
public final class HubCarousel {
    public static final int COUNT = 5;
    private final Spring cursor = new Spring(0.0F);
    private final Spring[] open = new Spring[COUNT];
    private float target;
    private int selected;
    private int previous;
    private long last = -1L;

    public HubCarousel() {
        for (int i = 0; i < COUNT; i++) {
            this.open[i] = new Spring(0.0F);
        }
    }

    public void snap(int selected) {
        this.selected = this.previous = Math.floorMod(selected, COUNT);
        this.target = this.selected;
        this.cursor.snap(this.target);

        for (int i = 0; i < COUNT; i++) {
            this.open[i].snap(i == this.selected ? 1.0F : 0.0F);
        }

        this.last = -1L;
    }

    public void select(int index) {
        index = Math.floorMod(index, COUNT);
        if (index != this.selected) {
            this.previous = this.selected;
            this.selected = index;
            this.target = this.cursor.value + (float)relative(index - this.cursor.value);
        }
    }

    public HubCarousel.Frame frame(long now, boolean animated) {
        float dt = this.last < 0L ? 0.0F : Math.min(0.05F, (now - this.last) / 1.0E9F);
        this.last = now;
        float[] expansion = new float[COUNT];
        if (!animated) {
            this.snap(this.selected);
        } else {
            this.cursor.step(this.target, dt, 15.0F, 0.86F);
        }

        float gap = Math.abs(this.target - this.cursor.value);
        boolean moving = gap > 0.002F || Math.abs(this.cursor.velocity) > 0.01F;

        for (int i = 0; i < COUNT; i++) {
            // Thẻ được chọn chỉ bung khi vòng còn cách đích dưới 0.3 bước: thấy rõ thẻ bay vào giữa rồi mới nở.
            float goal = i == this.selected && gap < 0.3F ? 1.0F : 0.0F;
            if (animated) {
                this.open[i].step(goal, dt, goal > 0.5F ? 17.0F : 24.0F, goal > 0.5F ? 0.74F : 1.0F);
            }

            expansion[i] = Math.max(0.0F, this.open[i].value);
            moving |= !this.open[i].resting(goal);
        }

        if (!moving) {
            this.target = this.selected;
            this.cursor.snap(this.target);
        }

        return new HubCarousel.Frame(this.cursor.value, this.selected, this.previous, expansion, Math.min(1.0F, expansion[this.selected]), moving);
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
        float expansion = f.expansion[index];
        float settle = Math.min(1.0F, expansion);
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
        // Thẻ xa thì nhỏ lại một chút: vòng có chiều sâu thay vì dàn phẳng.
        float depth = 1.0F - 0.1F * (float)Math.min(2.0, absolute) * (1.0F - settle);
        float width = mix(cw * depth, w, expansion);
        float height = mix(ch * depth, h, expansion);
        float cx = centerX + dx * (1.0F - settle);
        float cy = centerY + dy * (1.0F - settle);
        return new HubCarousel.Card(cx - width / 2.0F, cy - height / 2.0F, width, height, visibility, settle);
    }

    @Environment(EnvType.CLIENT)
    public record Card(float x, float y, float width, float height, float opacity, float open) {
    }

    @Environment(EnvType.CLIENT)
    public record Frame(double cursor, int selected, int previous, float[] expansion, float open, boolean moving) {
    }
}
