package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.feat.Feats;
import dev.jun.nosclient.feat.Waypoints;
import dev.jun.nosclient.map.MapCache;
import dev.jun.nosclient.map.MapViewport;
import dev.jun.nosclient.scene.SceneConfig;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;

@Environment(EnvType.CLIENT)
public final class WorldMapScreen extends Screen {
    private final Screen parent;
    private final Minecraft mc = Minecraft.getInstance();
    private final SceneConfig c = SceneConfig.INSTANCE;
    private final MapViewport view;
    private final MenuMotion motion = new MenuMotion();
    private boolean dragging;
    private long lastFrame = System.nanoTime();
    private String scope = "";

    public WorldMapScreen(Screen parent) {
        super(Component.translatable("nosclient.map.title"));
        this.parent = parent;
        this.view = new MapViewport(this.mc.player == null ? 0.0 : this.mc.player.getX(), this.mc.player == null ? 0.0 : this.mc.player.getZ(), 1.0);
    }

    private boolean inside(double x, double y) {
        return x >= 8.0 && x < this.width - 8 && y >= 34.0 && y < this.height - 52;
    }

    @Override
    protected void init() {
        this.motion.enter();
        this.dragging = false;
        this.view.pan(0.0, 0.0);
        if (this.scope.isEmpty()) {
            this.scope = this.mc.level == null ? "" : Waypoints.serverKey(this.mc) + "|" + Waypoints.dim(this.mc);
        }

        this.addRenderableWidget(new GlassButton(this.width - 58, 9, 50, 19, Component.translatable("gui.back"), this::onClose));
        int[] widths = new int[]{62, 24, 24, 48, 72};
        int total = 246;
        int x = (this.width - total) / 2;
        int y = this.height - 29;
        Component[] names = new Component[]{
            Component.translatable("nosclient.map.center"),
            Component.literal("−"),
            Component.literal("+"),
            Component.translatable("nosclient.map.grid"),
            Component.translatable("nosclient.wp.title")
        };
        Runnable[] actions = new Runnable[]{
            this::recenter,
            () -> this.view.zoomAt(this.width / 2.0, this.height / 2.0, -1.0, this.width, this.height),
            () -> this.view.zoomAt(this.width / 2.0, this.height / 2.0, 1.0, this.width, this.height),
            () -> {
                this.c.mapGrid = !this.c.mapGrid;
                this.c.save();
            },
            () -> this.mc.setScreen(new WaypointScreen(this))
        };

        for (int i = 0; i < widths.length; i++) {
            this.addRenderableWidget(new GlassButton(x, y, widths[i], 20, names[i], actions[i]));
            x += widths[i] + 4;
        }
    }

    @Override
    public void tick() {
        this.motion.tick();
        if (this.mc.level == null || !this.scope.equals(Waypoints.serverKey(this.mc) + "|" + Waypoints.dim(this.mc))) {
            this.onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, -234154984);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.nanoTime();
        this.view.step((now - this.lastFrame) / 1.0E9, this.width, this.height, this.c.animations);
        this.lastFrame = now;
        UiOpacity.value = this.motion.opacity();

        try {
            super.render(g, mx, my, pt);
            if (this.mc.level != null && this.mc.player != null) {
                Icons.draw(g, "map", 9.0F, 6.0F, 22.0F);
                Ui.text(g, Ui.bold(this.title), 34, 8, Ui.TEXT);
                String dim = this.mc.level.dimension().identifier().getPath().replace('_', ' ');
                Ui.text(g, Ui.small(Component.literal(dim)), 34, 21, Ui.MUTED);
                Glass.panel(g, 7, 33, this.width - 14, this.height - 84, -15392223, 1553905322);
                g.enableScissor(8, 34, this.width - 8, this.height - 52);
                double x0 = this.view.worldX(8.0, this.width);
                double z0 = this.view.worldZ(34.0, this.height);
                double x1 = this.view.worldX(this.width - 8, this.width);
                double z1 = this.view.worldZ(this.height - 52, this.height);
                int rx0 = Math.floorDiv((int)Math.floor(x0), 512);
                int rz0 = Math.floorDiv((int)Math.floor(z0), 512);
                int rx1 = Math.floorDiv((int)Math.ceil(x1), 512);
                int rz1 = Math.floorDiv((int)Math.ceil(z1), 512);
                g.pose().pushMatrix();
                g.pose().translate(this.width / 2.0F, this.height / 2.0F);
                g.pose().scale((float)this.view.zoom, (float)this.view.zoom);
                g.pose().translate((float)(-this.view.x), (float)(-this.view.z));
                int tiles = 0;

                for (int rx = rx0; rx <= rx1; rx++) {
                    for (int rz = rz0; rz <= rz1; rz++) {
                        MapCache.Region region = MapCache.region(rx, rz, false);
                        if (region != null) {
                            g.blit(RenderPipelines.GUI_TEXTURED, region.id(), rx * 512, rz * 512, 0.0F, 0.0F, 512, 512, 512, 512);
                            tiles++;
                        }
                    }
                }

                g.pose().popMatrix();
                if (this.c.mapGrid && this.view.zoom >= 1.0) {
                    for (int x = (int)Math.floor(x0 / 16.0) * 16; x <= x1; x += 16) {
                        int sx = this.screenX(x);
                        g.fill(sx, 34, sx + 1, this.height - 52, 583915978);
                    }

                    for (int z = (int)Math.floor(z0 / 16.0) * 16; z <= z1; z += 16) {
                        int sy = this.screenY(z);
                        g.fill(8, sy, this.width - 8, sy + 1, 583915978);
                    }
                }

                for (Waypoints.Wp point : Waypoints.here(this.mc)) {
                    int x = this.screenX(point.x() + 0.5);
                    int y = this.screenY(point.z() + 0.5);
                    if (this.inside(x, y)) {
                        Glass.fill(g, x - 4, y - 4, 8, 8, -535552485);
                        g.fill(x - 2, y - 2, x + 2, y + 2, point.color());
                        Component name = Ui.fit(Ui.small(Component.literal(point.name())), 90);
                        Ui.text(g, name, x - Ui.width(name) / 2, y + 7, Ui.TEXT);
                    }
                }

                int px = this.screenX(this.mc.player.getX());
                int pz = this.screenY(this.mc.player.getZ());
                g.pose().pushMatrix();
                g.pose().translate(px, pz);
                g.pose().rotate((float)Math.toRadians(this.mc.player.getYRot() + 180.0F));

                for (int i = 0; i < 7; i++) {
                    g.fill(-i / 2 - 1, -5 + i, i / 2 + 2, -4 + i, -15720424);
                }

                for (int i = 0; i < 6; i++) {
                    g.fill(-i / 2, -4 + i, i / 2 + 1, -3 + i, -789529);
                }

                g.pose().popMatrix();
                if (tiles == 0) {
                    Ui.text(g, Ui.small(Component.translatable("nosclient.map.explore")), 18, 44, Ui.MUTED);
                }

                g.disableScissor();
                Component position = Ui.small(
                    Component.literal("X " + (int)Math.floor(this.view.worldX(mx, this.width)) + "   Z " + (int)Math.floor(this.view.worldZ(my, this.height)))
                );
                Ui.text(g, position, 10, this.height - 45, Ui.MUTED);
                Component zoom = Ui.small(Component.literal(String.format(Locale.ROOT, "%.2f×", this.view.zoom)));
                Ui.text(g, zoom, this.width - Ui.width(zoom) - 10, this.height - 45, Ui.MUTED);
                Component hint = Ui.fit(Ui.small(Component.translatable("nosclient.map.controls")), this.width - 170);
                if (this.width >= 360) {
                    Ui.text(g, hint, (this.width - Ui.width(hint)) / 2, this.height - 45, Ui.FAINT);
                }

                return;
            }
        } finally {
            UiOpacity.value = 1.0F;
        }
    }

    private int screenX(double x) {
        return (int)Math.round((x - this.view.x) * this.view.zoom + this.width / 2.0);
    }

    private int screenY(double z) {
        return (int)Math.round((z - this.view.z) * this.view.zoom + this.height / 2.0);
    }

    private void recenter() {
        if (this.mc.player != null) {
            this.view.center(this.mc.player.getX(), this.mc.player.getZ());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        if (this.motion.closing()) {
            return true;
        }

        if (!this.inside(event.x(), event.y())) {
            return super.mouseClicked(event, dbl);
        }

        if (event.button() != 1) {
            if (event.button() == 0) {
                if (dbl) {
                    this.recenter();
                } else {
                    this.dragging = true;
                    this.setDragging(true);
                }

                return true;
            } else {
                return false;
            }
        } else {
            int x = (int)Math.floor(this.view.worldX(event.x(), this.width));
            int z = (int)Math.floor(this.view.worldZ(event.y(), this.height));
            Waypoints.Wp nearby = null;

            for (Waypoints.Wp point : Waypoints.here(this.mc)) {
                if (Math.abs(this.screenX(point.x()) - event.x()) < 7.0 && Math.abs(this.screenY(point.z()) - event.y()) < 7.0) {
                    nearby = point;
                    break;
                }
            }

            int y = this.mc.player.blockPosition().getY();
            LevelChunk chunk = this.mc.level.getChunkSource().getChunk(x >> 4, z >> 4, false);
            if (chunk != null) {
                y = chunk.getHeight(Types.WORLD_SURFACE, x & 15, z & 15) + 1;
            }

            this.mc.setScreen(new WaypointEditorScreen(this, nearby, x, y, z));
            return true;
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!this.dragging) {
            return false;
        }

        this.view.pan(dx, dy);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        this.setDragging(false);
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (!this.inside(x, y)) {
            return false;
        }

        this.view.zoomAt(x, y, sy, this.width, this.height);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 32) {
            this.recenter();
            return true;
        } else if (Feats.WORLD_MAP.matches(event)) {
            this.onClose();
            return true;
        } else {
            return super.keyPressed(event);
        }
    }

    @Override
    public void onClose() {
        this.motion.exit(() -> this.mc.setScreen(this.parent));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
