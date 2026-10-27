package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.hud.Hud;
import dev.jun.nosclient.hud.HudLayout;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.typography.Typography;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class HudEditScreen extends Screen {
    private final Screen parent;
    private final Hud hud = Hud.INSTANCE;
    private final Minecraft mc = Minecraft.getInstance();
    private String sel;
    private String drag;
    private double offX;
    private double offY;
    private int slider = -1;
    private int barX;
    private int barY;
    private int barW;
    private int barStride;
    private int toolbarTop;
    private int toolbarX;
    private int toolbarW;
    /** 1 = bảng chỉnh hiện, 0 = ẩn (đang kéo gadget) — lò xo nhẹ cho khỏi giật. */
    private float toolbarShow = 1.0F;
    private long toolbarAt = System.nanoTime();
    static final int TOOLBAR_H = 64;
    static final int TOOLBAR_MARGIN = 10;
    private final MenuMotion motion = new MenuMotion();
    private final List<HudEditScreen.Btn> btns = new ArrayList<>();

    public HudEditScreen(Screen parent) {
        super(Component.translatable("nosclient.edit.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.hud.editing = true;
        this.motion.enter();
        this.drag = null;
        this.slider = -1;
        this.btns.clear();
        this.setDragging(false);
    }

    @Override
    public void tick() {
        this.motion.tick();
    }

    @Override
    public void removed() {
        this.hud.editing = false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 855638016);

        for (int x = this.width / 12; x < this.width; x += Math.max(1, this.width / 12)) {
            g.fill(x, 0, x + 1, this.height, 150994943);
        }

        for (int y = this.height / 8; y < this.height; y += Math.max(1, this.height / 8)) {
            g.fill(0, y, this.width, y + 1, 150994943);
        }

        g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, Ui.alpha(Ui.ACCENT, 0.15F));
    }

    private String at(double mx, double my) {
        String hit = null;

        for (Entry<String, float[]> e : this.hud.rects.entrySet()) {
            float[] r = e.getValue();
            if (mx >= r[0] - 2.0F && mx < r[2] + 2.0F && my >= r[1] - 2.0F && my < r[3] + 2.0F) {
                hit = e.getKey();
            }
        }

        return hit;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        UiOpacity.value = this.motion.opacity();

        try {
            this.renderEditor(g, mx, my, pt);
        } finally {
            UiOpacity.value = 1.0F;
        }
    }

    private void renderEditor(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (this.mc.player == null) {
            Component n = Ui.ui(Component.translatable("nosclient.edit.noworld"));
            Ui.text(g, n, (this.width - Ui.width(n)) / 2, this.height / 2, Ui.TEXT);
        } else {
            String hov = this.drag != null ? this.drag : this.at(mx, my);

            for (Entry<String, float[]> e : this.hud.rects.entrySet()) {
                float[] r = e.getValue();
                boolean on = e.getKey().equals(this.sel);
                boolean hv = e.getKey().equals(hov);
                int x0 = (int)Math.floor(r[0]) - 2;
                int y0 = (int)Math.floor(r[1]) - 2;
                int x1 = (int)Math.ceil(r[2]) + 2;
                int y1 = (int)Math.ceil(r[3]) + 2;
                if (on || hv) {
                    Glass.fill(g, x0, y0, x1 - x0, y1 - y0, Ui.alpha(Ui.ACCENT, on ? 0.13F : 0.06F));
                }

                Glass.outline(g, x0, y0, x1 - x0, y1 - y0, Ui.alpha(Ui.ACCENT, on ? 0.8F : (hv ? 0.45F : 0.1F)));
            }

            this.toolbar(g, mx, my);
            // Gadget nằm dưới bảng (đáy lẫn đỉnh đều kín): vẽ lại viền của nó đè lên bảng để thấy chỗ mà bấm.
            for (Entry<String, float[]> e : this.hud.rects.entrySet()) {
                float[] r = e.getValue();
                if (this.covered(r)) {
                    int x0 = (int)Math.floor(r[0]) - 2;
                    int y0 = (int)Math.floor(r[1]) - 2;
                    Glass.outline(g, x0, y0, (int)Math.ceil(r[2]) + 2 - x0, (int)Math.ceil(r[3]) + 2 - y0, Ui.alpha(Ui.ACCENT, 0.9F));
                }
            }
        }
    }

    private boolean covered(float[] r) {
        return this.toolbarShow > 0.02F
            && r[2] > this.toolbarX && r[0] < this.toolbarX + this.toolbarW && r[3] > this.toolbarTop && r[1] < this.toolbarTop + TOOLBAR_H;
    }

    /**
     * Bảng chỉnh nằm đáy, trừ khi đè lên gadget nào thì lên đỉnh; cả hai đều đè thì giữ đáy (gadget vẫn
     * bấm được vì gadget được ưu tiên hơn nền bảng). Trả về y của bảng.
     */
    static int dock(int screenH, int x0, int x1, Iterable<float[]> gadgets) {
        int bottom = screenH - TOOLBAR_H - TOOLBAR_MARGIN;
        int top = TOOLBAR_MARGIN;
        boolean underBottom = false;
        boolean underTop = false;

        for (float[] r : gadgets) {
            boolean across = r[2] > x0 && r[0] < x1;
            underBottom |= across && r[3] > bottom && r[1] < bottom + TOOLBAR_H;
            underTop |= across && r[3] > top && r[1] < top + TOOLBAR_H;
        }

        return underBottom && !underTop ? top : bottom;
    }

    private boolean onToolbar(double mx, double my) {
        return this.toolbarShow > 0.5F
            && mx >= this.toolbarX && mx < this.toolbarX + this.toolbarW && my >= this.toolbarTop && my < this.toolbarTop + TOOLBAR_H;
    }

    private void toolbar(GuiGraphics g, int mx, int my) {
        this.btns.clear();
        long now = System.nanoTime();
        float dt = Math.min(0.05F, (now - this.toolbarAt) / 1.0E9F);
        this.toolbarAt = now;
        float target = this.drag == null ? 1.0F : 0.0F;
        this.toolbarShow = SceneConfig.INSTANCE.animations ? Ui.approach(this.toolbarShow, target, dt, 14.0F) : target;
        if (this.toolbarShow < 0.02F) {
            return;
        }

        float opacity = UiOpacity.value;
        boolean hidesGadget = this.hud.rects.values().stream().anyMatch(this::covered);
        UiOpacity.value = opacity * this.toolbarShow * (hidesGadget ? 0.45F : 1.0F);
        try {
            this.toolbarBody(g, mx, my);
        } finally {
            UiOpacity.value = opacity;
        }

        if (this.toolbarShow < 0.5F) {
            this.btns.clear();
        }
    }

    private void toolbarBody(GuiGraphics g, int mx, int my) {
        HudLayout.Slot slot = this.sel == null ? null : HudLayout.get(this.sel);
        int w = this.toolbarW = Math.min(392, this.width - 24);
        int x = this.toolbarX = (this.width - w) / 2;
        int y = this.toolbarTop = dock(this.height, x, x + w, this.hud.rects.values());
        Glass.panel(g, x, y, w, TOOLBAR_H, -266852567, 1705362355);
        Component reset = Ui.small(Component.translatable(this.sel == null ? "nosclient.edit.resetAll" : "nosclient.edit.reset"));
        Component done = Ui.small(Component.translatable("nosclient.edit.done"));
        Component theme = Ui.small(Component.translatable("nosclient.theme.short"));
        int doneW = Ui.width(done) + 18;
        int resetW = Ui.width(reset) + 18;
        int themeW = Ui.width(theme) + 18;
        Component heading = Ui.fit(
            Ui.small(Component.translatable(this.sel == null ? "nosclient.edit.hint.short" : "nosclient.mod." + this.sel)), w - doneW - resetW - themeW - 47
        );
        Ui.text(g, heading, x + 12, y + 12, Ui.TEXT);
        int right = x + w - 10;
        this.btn(g, done, right - doneW, y + 6, doneW, mx, my, true, this::onClose);
        this.btn(g, reset, right - doneW - 5 - resetW, y + 6, resetW, mx, my, false, () -> {
            if (this.sel == null) {
                HudLayout.resetAll();
            } else {
                HudLayout.reset(this.sel);
            }

            HudLayout.save();
        });
        this.btn(g, theme, right - doneW - resetW - themeW - 10, y + 6, themeW, mx, my, false, () -> this.mc.setScreen(new ThemeScreen(this)));
        if (slot == null) {
            Ui.text(g, Ui.fit(Ui.small(Component.translatable("nosclient.edit.hint")), w - 24), x + 12, y + 40, Ui.MUTED);
        } else {
            this.barStride = (w - 24) / 2;
            this.barX = x + 12;
            this.barW = this.barStride - 12;
            this.barY = y + 49;

            for (int i = 0; i < 2; i++) {
                int left = this.barX + i * this.barStride;
                double value = i == 0 ? (slot.scale - 0.5) / 1.5 : (slot.alpha - 0.2) / 0.8;
                boolean floor = i == 0
                    && Typography.hudScale(SceneConfig.INSTANCE.hudScale * slot.scale, this.mc.getWindow().getGuiScale(), SceneConfig.INSTANCE.hudReadable)
                        > SceneConfig.INSTANCE.hudScale * slot.scale + 0.001;
                Component label = Ui.small(
                    Component.translatable(i == 0 ? "nosclient.edit.scale" : "nosclient.edit.opacity")
                        .append(" " + Math.round((i == 0 ? slot.scale : slot.alpha) * 100.0) + "%")
                        .append(floor ? Component.translatable("nosclient.edit.readable") : Component.empty())
                );
                Ui.text(g, label, left, y + 31, Ui.MUTED);
                int knob = left + (int)Math.round(value * this.barW);
                g.fill(left, this.barY, left + this.barW, this.barY + 2, Ui.alpha(Ui.ACCENT, 0.2F));
                g.fill(left, this.barY, knob, this.barY + 2, Ui.ACCENT);
                Glass.fill(g, knob - 2, this.barY - 3, 4, 8, Ui.TEXT);
                int index = i;
                this.btns.add(new HudEditScreen.Btn(left - 4, this.barY - 8, left + this.barW + 4, this.barY + 9, () -> {
                    this.slider = index;
                    this.slide(mx);
                }));
            }
        }
    }

    private void btn(GuiGraphics g, Component l, int x, int y, int w, int mx, int my, boolean primary, Runnable r) {
        boolean hv = mx >= x && mx < x + w && my >= y && my < y + 18;
        Glass.panel(g, x, y, w, 18, hv ? -12955320 : (primary ? -14072261 : -736481749), hv ? -1513365296 : 1001243575);
        Ui.text(g, l, x + 8, y + 6, Ui.TEXT);
        this.btns.add(new HudEditScreen.Btn(x, y, x + w, y + 18, r));
    }

    private void slide(double mx) {
        if (this.sel != null && this.slider >= 0) {
            int x0 = this.barX + this.slider * this.barStride;
            double k = Math.min(1.0, Math.max(0.0, (mx - x0) / this.barW));
            HudLayout.Slot s = HudLayout.edit(this.sel);
            if (this.slider == 0) {
                s.scale = Math.round((0.5 + k * 1.5) * 20.0) / 20.0;
            } else {
                s.alpha = Math.round((0.2 + k * 0.8) * 20.0) / 20.0;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (!this.motion.closing() && e.button() == 0) {
            // Ưu tiên: nút trên bảng > gadget > nền bảng. Trước đây cả dải ngang dưới đáy nuốt mọi cú
            // bấm nên gadget lọt xuống đó là kẹt luôn.
            for (HudEditScreen.Btn button : this.btns) {
                if (e.x() >= button.x0 && e.x() < button.x1 && e.y() >= button.y0 && e.y() < button.y1) {
                    button.run.run();
                    this.setDragging(this.slider >= 0);
                    return true;
                }
            }

            String id = this.at(e.x(), e.y());
            if (id == null && this.onToolbar(e.x(), e.y())) {
                return true;
            }

            this.sel = id;
            if (id != null) {
                float[] r = this.hud.rects.get(id);
                this.drag = id;
                this.offX = e.x() - r[0];
                this.offY = e.y() - r[1];
                this.setDragging(true);
            }

            return true;
        } else {
            return true;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (this.slider >= 0) {
            this.slide(e.x());
            return true;
        }

        if (this.drag == null) {
            return true;
        }

        float[] r = this.hud.rects.get(this.drag);
        if (r == null) {
            return true;
        }

        double w = r[2] - r[0];
        double h = r[3] - r[1];
        double nx = e.x() - this.offX;
        double ny = e.y() - this.offY;
        boolean free = (e.modifiers() & 1) != 0;
        if (!free) {
            double m = 4.0;
            double t = 6.0;
            if (Math.abs(nx - m) < t) {
                nx = m;
            } else if (Math.abs(nx + w - (this.width - m)) < t) {
                nx = this.width - m - w;
            } else if (Math.abs(nx + w / 2.0 - this.width / 2.0) < t) {
                nx = this.width / 2.0 - w / 2.0;
            } else {
                nx = Math.round(nx / 4.0) * 4L;
            }

            if (Math.abs(ny - m) < t) {
                ny = m;
            } else if (Math.abs(ny + h - (this.height - m)) < t) {
                ny = this.height - m - h;
            } else {
                ny = Math.round(ny / 4.0) * 4L;
            }
        }

        nx = Math.max(0.0, Math.min(this.width - w, nx));
        ny = Math.max(0.0, Math.min(this.height - h, ny));
        double cx = nx + w / 2.0;
        HudLayout.Slot s = HudLayout.edit(this.drag);
        s.ax = cx < this.width / 3.0 ? 0 : (cx > this.width * 2 / 3.0 ? 2 : 1);
        s.ay = ny + h / 2.0 < this.height / 2.0 ? 0 : 1;
        s.x = (s.ax == 0 ? nx : (s.ax == 1 ? cx : nx + w)) / this.width;
        s.y = (s.ay == 0 ? ny : ny + h) / this.height;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (this.drag != null || this.slider >= 0) {
            HudLayout.save();
        }

        this.drag = null;
        this.slider = -1;
        this.setDragging(false);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        String id = this.at(mx, my);
        if (id == null) {
            return false;
        }

        this.sel = id;
        HudLayout.Slot s = HudLayout.edit(id);
        s.scale = Math.min(2.0, Math.max(0.5, Math.round((s.scale + Math.signum(sy) * 0.05) * 20.0) / 20.0));
        HudLayout.save();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == 256 && this.sel != null) {
            this.sel = null;
            return true;
        } else if ((e.key() == 261 || e.key() == 259) && this.sel != null) {
            HudLayout.reset(this.sel);
            HudLayout.save();
            return true;
        } else {
            return super.keyPressed(e);
        }
    }

    @Override
    public void onClose() {
        HudLayout.save();
        this.motion.exit(() -> this.mc.setScreen(this.parent));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Environment(EnvType.CLIENT)
    private record Btn(int x0, int y0, int x1, int y1, Runnable run) {
    }
}
