package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.feat.Waypoints;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class WaypointEditorScreen extends Screen {
    private final Screen parent;
    private final Waypoints.Wp existing;
    private final int x;
    private final int y;
    private final int z;
    private String scope;
    private int color;
    private EditBox name;
    private int left;
    private int top;
    private Component error = Component.empty();

    public WaypointEditorScreen(Screen parent, Waypoints.Wp existing, int x, int y, int z) {
        super(Component.translatable("nosclient.wp.title"));
        this.parent = parent;
        this.existing = existing;
        this.x = existing == null ? x : existing.x();
        this.y = existing == null ? y : existing.y();
        this.z = existing == null ? z : existing.z();
        this.color = existing == null ? Waypoints.COLORS[0] : existing.color();
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (this.scope == null) {
            this.scope = mc.level == null ? "" : Waypoints.serverKey(mc) + "|" + Waypoints.dim(mc);
        }

        this.left = this.width / 2 - 112;
        this.top = Math.max(12, this.height / 2 - 76);
        String value = this.name == null ? (this.existing == null ? "" : this.existing.name()) : this.name.getValue();
        this.name = this.addRenderableWidget(new StyledEditBox(this.font, this.left + 18, this.top + 49, 188, 14, Component.translatable("nosclient.wpm.name")));
        this.name.setBordered(false);
        this.name.setTextColor(Ui.TEXT);
        this.name.setTextColorUneditable(Ui.MUTED);
        this.name.setMaxLength(32);
        this.name.setValue(value);
        this.name.setHint(Component.translatable("nosclient.wpm.name"));
        this.addRenderableWidget(new GlassButton(this.left + 12, this.top + 70, 94, 20, Component.translatable("nosclient.wpm.color"), () -> {
            int index = 0;

            for (int i = 0; i < Waypoints.COLORS.length; i++) {
                if (Waypoints.COLORS[i] == this.color) {
                    index = i;
                }
            }

            this.color = Waypoints.COLORS[(index + 1) % Waypoints.COLORS.length];
        }));
        this.addRenderableWidget(
            new GlassButton(
                this.left + 118, this.top + 70, 94, 20, Component.translatable(this.existing == null ? "gui.back" : "nosclient.wpm.delete"), () -> {
                    if (this.existing == null) {
                        this.onClose();
                    } else {
                        mc.setScreen(new GlassConfirmScreen(ok -> {
                            if (ok && this.current()) {
                                Waypoints.mine(mc).remove(this.existing);
                                Waypoints.saveNow();
                                mc.setScreen(this.parent);
                            } else {
                                mc.setScreen(this);
                            }
                        }, Component.translatable("nosclient.wpm.delete"), Component.literal(this.existing.name())));
                    }
                }
            )
        );
        this.addRenderableWidget(new GlassButton(this.left + 12, this.top + 100, 200, 22, Component.translatable("nosclient.map.save"), this::save));
        this.setInitialFocus(this.name);
    }

    private boolean current() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && this.scope.equals(Waypoints.serverKey(mc) + "|" + Waypoints.dim(mc));
    }

    private void save() {
        if (!this.current()) {
            this.error = Component.translatable("nosclient.map.worldchanged");
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (this.existing == null) {
                Waypoints.Wp added = Waypoints.add(mc, this.name.getValue(), this.x, this.y, this.z);
                List<Waypoints.Wp> list = Waypoints.mine(mc);
                list.set(list.indexOf(added), added.withColor(this.color));
            } else {
                List<Waypoints.Wp> list = Waypoints.mine(mc);
                int index = list.indexOf(this.existing);
                if (index < 0) {
                    this.error = Component.translatable("nosclient.map.worldchanged");
                    return;
                }

                list.set(
                    index, this.existing.withName(this.name.getValue().isBlank() ? this.existing.name() : this.name.getValue().strip()).withColor(this.color)
                );
            }

            Waypoints.saveNow();
            mc.setScreen(this.parent);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, -720562149);
        Glass.panel(g, this.left, this.top, 224, 145, -233298392, 1720625821);
        Glass.panel(g, this.left + 12, this.top + 43, 200, 22, -1039263460, this.name != null && this.name.isFocused() ? -1464806981 : 1084142507);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Ui.text(g, Ui.bold(this.title), this.left + 12, this.top + 12, Ui.TEXT);
        Ui.text(g, Ui.small(Component.literal("X " + this.x + "   Y " + this.y + "   Z " + this.z)), this.left + 12, this.top + 28, Ui.MUTED);
        g.fill(this.left + 96, this.top + 76, this.left + 101, this.top + 84, this.color);
        if (!this.error.getString().isBlank()) {
            Ui.text(g, Ui.fit(Ui.small(this.error), 200), this.left + 12, this.top + 130, -1793117);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
