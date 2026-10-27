package dev.jun.nosclient.gui;

import dev.jun.nosclient.Mods;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.feat.Waypoints;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class WaypointScreen extends Screen {
    private final Screen parent;
    private final Minecraft mc = Minecraft.getInstance();
    private static final int ROW = 22;
    private static final int ACC = Mods.catColor("world");
    private EditBox name;
    private EditBox coords;
    private int sel = -1;
    private int scroll;
    private Waypoints.Wp undo;
    private int undoAt = -1;
    private Component note = Component.empty();
    private int x0;
    private int y0;
    private int w;
    private int listTop;
    private int listRows;

    public WaypointScreen(Screen parent) {
        super(Component.translatable("nosclient.wp.title"));
        this.parent = parent;
    }

    private List<Waypoints.Wp> list() {
        return Waypoints.mine(this.mc);
    }

    @Override
    protected void init() {
        this.w = Math.min(420, this.width - 40);
        this.x0 = (this.width - this.w) / 2;
        this.y0 = 30;
        this.listTop = this.y0 + 30;
        this.listRows = Math.max(3, (this.height - this.listTop - 96) / 22);
        int by = this.listTop + this.listRows * 22 + 8;
        this.name = this.addRenderableWidget(new StyledEditBox(this.font, this.x0, by, 150, 20, Component.translatable("nosclient.wpm.name")));
        this.name.setHint(Component.translatable("nosclient.wpm.name"));
        this.name.setMaxLength(32);
        this.coords = this.addRenderableWidget(new StyledEditBox(this.font, this.x0 + 156, by, 120, 20, Component.translatable("nosclient.wpm.coords")));
        this.coords.setHint(Component.literal("X Y Z"));
        this.coords.setMaxLength(40);
        this.addRenderableWidget(
            Button.builder(Component.translatable("nosclient.wpm.add"), b -> this.addFromFields()).bounds(this.x0 + 282, by, this.w - 282, 20).build()
        );
        int ay = by + 26;
        int bw = (this.w - 18) / 4;
        this.addRenderableWidget(Button.builder(Component.translatable("nosclient.wpm.rename"), b -> this.rename()).bounds(this.x0, ay, bw, 20).build());
        this.addRenderableWidget(
            Button.builder(Component.translatable("nosclient.wpm.color"), b -> this.recolor()).bounds(this.x0 + bw + 6, ay, bw, 20).build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("nosclient.wpm.toggle"), b -> this.toggleHidden()).bounds(this.x0 + 2 * (bw + 6), ay, bw, 20).build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("nosclient.wpm.delete"), b -> this.delete()).bounds(this.x0 + 3 * (bw + 6), ay, bw, 20).build()
        );
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose()).bounds(this.width / 2 - 60, ay + 26, 120, 20).build());
    }

    private void addFromFields() {
        if (this.mc.player != null) {
            BlockPos p = this.mc.player.blockPosition();
            int x = p.getX();
            int y = p.getY();
            int z = p.getZ();
            String t = this.coords.getValue().trim();
            if (!t.isEmpty()) {
                String[] parts = t.split("[\\s,;]+");

                try {
                    if (parts.length == 2) {
                        x = Integer.parseInt(parts[0]);
                        z = Integer.parseInt(parts[1]);
                    } else {
                        if (parts.length != 3) {
                            throw new NumberFormatException();
                        }

                        x = Integer.parseInt(parts[0]);
                        y = Integer.parseInt(parts[1]);
                        z = Integer.parseInt(parts[2]);
                    }
                } catch (NumberFormatException e) {
                    this.note = Component.translatable("nosclient.wpm.badcoords");
                    return;
                }
            }

            Waypoints.add(this.mc, this.name.getValue().trim(), x, y, z);
            this.sel = this.list().size() - 1;
            this.name.setValue("");
            this.coords.setValue("");
            this.note = Component.translatable(
                "nosclient.wp.added",
                this.list().get(this.sel).name(),
                this.list().get(this.sel).x(),
                this.list().get(this.sel).y(),
                this.list().get(this.sel).z()
            );
        }
    }

    private boolean hasSel() {
        return this.sel >= 0 && this.sel < this.list().size();
    }

    private void rename() {
        if (this.hasSel() && !this.name.getValue().isBlank()) {
            this.list().set(this.sel, this.list().get(this.sel).withName(this.name.getValue().trim()));
            this.name.setValue("");
            Waypoints.saveNow();
        } else {
            this.note = Component.translatable("nosclient.wpm.renamehint");
        }
    }

    private void recolor() {
        if (this.hasSel()) {
            Waypoints.Wp wp = this.list().get(this.sel);
            int i = 0;

            for (int k = 0; k < Waypoints.COLORS.length; k++) {
                if (Waypoints.COLORS[k] == wp.color()) {
                    i = k + 1;
                }
            }

            this.list().set(this.sel, wp.withColor(Waypoints.COLORS[i % Waypoints.COLORS.length]));
            Waypoints.saveNow();
        }
    }

    private void toggleHidden() {
        if (this.hasSel()) {
            this.list().set(this.sel, this.list().get(this.sel).withHidden(!this.list().get(this.sel).hidden()));
            Waypoints.saveNow();
        }
    }

    private void delete() {
        if (this.hasSel()) {
            this.undo = this.list().remove(this.sel);
            this.undoAt = this.sel;
            Waypoints.saveNow();
            this.note = Component.translatable("nosclient.wpm.deleted", this.undo.name());
            this.sel = Math.min(this.sel, this.list().size() - 1);
        }
    }

    private void restore() {
        if (this.undo != null) {
            this.list().add(Math.min(this.undoAt, this.list().size()), this.undo);
            this.sel = Math.min(this.undoAt, this.list().size() - 1);
            this.undo = null;
            Waypoints.saveNow();
            this.note = Component.empty();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        Ui.panel(g, this.x0 - 10, this.y0 - 10, this.x0 + this.w + 10, this.height - 8, -535422429);
        g.fill(this.x0 - 10, this.y0 - 10, this.x0 - 7, this.height - 8, ACC);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Ui.text(g, Ui.big(this.title), this.x0, this.y0, Ui.TEXT);
        List<Waypoints.Wp> all = this.list();
        Component count = Ui.small(Component.translatable("nosclient.wpm.count", all.size(), Waypoints.serverKey(this.mc)));
        Ui.text(g, count, this.x0 + this.w - Ui.width(count), this.y0 + 6, Ui.MUTED);
        String here = this.mc.level == null ? "" : Waypoints.dim(this.mc);
        this.scroll = Math.max(0, Math.min(this.scroll, all.size() - this.listRows));
        if (all.isEmpty()) {
            Ui.text(g, Ui.small(Component.translatable("nosclient.wpm.empty")), this.x0 + 8, this.listTop + 8, Ui.MUTED);
        }

        for (int r = 0; r < this.listRows && this.scroll + r < all.size(); r++) {
            int i = this.scroll + r;
            int y = this.listTop + r * 22;
            Waypoints.Wp wp = all.get(i);
            boolean hv = mx >= this.x0 && mx < this.x0 + this.w && my >= y && my < y + 22 - 2;
            boolean on = i == this.sel;
            g.fill(this.x0, y, this.x0 + this.w, y + 22 - 2, on ? Ui.alpha(ACC, 0.28F) : (hv ? 452984831 : 268435455));
            if (on) {
                Ui.brackets(g, this.x0, y, this.x0 + this.w, y + 22 - 2, 5, ACC);
            }

            int cx = this.x0 + 11;
            int cy = y + 10;
            int col = wp.hidden() ? Ui.alpha(wp.color(), 0.35F) : wp.color();

            for (int k = 0; k < 4; k++) {
                g.fill(cx - k, cy - 3 + k, cx + k + 1, cy - 2 + k, col);
            }

            for (int k = 0; k < 3; k++) {
                g.fill(cx - 2 + k, cy + 1 + k, cx + 3 - k, cy + 2 + k, col);
            }

            Ui.text(g, Ui.fit(Ui.bold(Component.literal(wp.name())), 130), this.x0 + 22, y + 3, wp.hidden() ? Ui.MUTED : Ui.TEXT);
            String dimName = wp.dim().substring(wp.dim().indexOf(58) + 1).replace('_', ' ');
            Ui.text(g, Ui.small(Component.literal(wp.x() + " " + wp.y() + " " + wp.z() + "  ·  " + dimName)), this.x0 + 22, y + 13, Ui.MUTED);
            String right;
            if (wp.dim().equals(here) && this.mc.player != null) {
                double dx = wp.x() + 0.5 - this.mc.player.getX();
                double dz = wp.z() + 0.5 - this.mc.player.getZ();
                double ang = Math.toDegrees(Math.atan2(-dx, dz)) - this.mc.player.getYRot();
                String[] arrows = new String[]{"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
                right = arrows[Math.floorMod(Math.round(ang / 45.0), 8)] + " " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " m";
            } else {
                right = "—";
            }

            if (wp.hidden()) {
                right = Component.translatable("nosclient.wpm.hidden").getString() + "  " + right;
            }

            Component rc = Ui.small(Component.literal(right));
            Ui.text(g, rc, this.x0 + this.w - 8 - Ui.width(rc), y + 8, Ui.TEXT);
        }

        if (all.size() > this.listRows) {
            int track = this.listRows * 22;
            int th = Math.max(12, track * this.listRows / all.size());
            int ty = this.listTop + (track - th) * this.scroll / Math.max(1, all.size() - this.listRows);
            g.fill(this.x0 + this.w + 3, ty, this.x0 + this.w + 5, ty + th, 1728053247);
        }

        int ny = this.height - 22;
        if (!this.note.getString().isEmpty()) {
            Ui.text(g, Ui.small(this.note), this.x0, ny, Ui.ACCENT_HI);
        }

        if (this.undo != null) {
            Component u = Ui.bold(Component.translatable("nosclient.wpm.undo"));
            int ux = this.x0 + this.w - Ui.width(u);
            Ui.text(g, u, ux, ny, mx >= ux && my >= ny - 2 && my < ny + 10 ? Ui.TEXT : Ui.ACCENT_HI);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (super.mouseClicked(e, dbl)) {
            return true;
        }

        int ny = this.height - 22;
        if (this.undo != null && e.y() >= ny - 2 && e.y() < ny + 10 && e.x() >= this.x0 + this.w - 80 && e.x() < this.x0 + this.w) {
            this.restore();
            return true;
        }

        if (e.x() >= this.x0 && e.x() < this.x0 + this.w && e.y() >= this.listTop && e.y() < this.listTop + this.listRows * 22) {
            int i = this.scroll + (int)((e.y() - this.listTop) / 22.0);
            if (i < this.list().size()) {
                this.sel = i;
                this.name.setValue(this.list().get(i).name());
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        this.scroll = this.scroll - (int)Math.signum(sy);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        boolean typing = this.name.isFocused() || this.coords.isFocused();
        if (typing || e.key() != 261 && e.key() != 259) {
            if (e.key() == 90 && e.hasControlDown()) {
                this.restore();
                return true;
            }

            if (!typing && (e.key() == 265 || e.key() == 264) && !this.list().isEmpty()) {
                this.sel = Math.max(0, Math.min(this.list().size() - 1, this.sel + (e.key() == 265 ? -1 : 1)));
                if (this.sel < this.scroll) {
                    this.scroll = this.sel;
                }

                if (this.sel >= this.scroll + this.listRows) {
                    this.scroll = this.sel - this.listRows + 1;
                }

                return true;
            } else if (e.key() == 257 && (this.name.isFocused() || this.coords.isFocused())) {
                if (this.hasSel()
                    && this.name.isFocused()
                    && this.coords.getValue().isBlank()
                    && !this.name.getValue().equals(this.list().get(this.sel).name())) {
                    this.rename();
                } else {
                    this.addFromFields();
                }

                return true;
            } else {
                return super.keyPressed(e);
            }
        } else {
            this.delete();
            return true;
        }
    }

    @Override
    public void onClose() {
        this.mc.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
