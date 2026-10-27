package dev.jun.nosclient.hud;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.feat.Waypoints;
import dev.jun.nosclient.gui.Glass;
import dev.jun.nosclient.gui.Icons;
import dev.jun.nosclient.gui.UiOpacity;
import dev.jun.nosclient.map.MapCache;
import dev.jun.nosclient.scene.SceneConfig;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public final class Minimap {
    public static final double[] ZOOMS = new double[]{0.5, 1.0, 2.0, 4.0};
    private static long last;
    private static double shownZoom = 1.0;
    private static double rotation;
    private static String biomeKey = "";
    private static String language = "";
    private static String biomeName = "";

    private Minimap() {
    }

    public static void tick(Minecraft mc) {
        MapCache.tick(mc);
    }

    public static double zoom() {
        return ZOOMS[Math.max(0, Math.min(3, SceneConfig.INSTANCE.minimapZoom))];
    }

    public static int render(GuiGraphics g, Minecraft mc, int right, int y, float alpha, float contentAlpha) {
        SceneConfig c = SceneConfig.INSTANCE;
        LocalPlayer player = mc.player;
        long now = System.nanoTime();
        double dt = last == 0L ? 0.0 : Math.min(0.05, (now - last) / 1.0E9);
        last = now;
        double k = c.animations ? 1.0 - Math.exp(-dt * 14.0) : 1.0;
        shownZoom = shownZoom + (zoom() - shownZoom) * k;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double yaw = player.getViewYRot(partial);
        double target = c.minimapRotate ? 180.0 - yaw : 0.0;
        rotation = rotation + (((target - rotation) % 360.0 + 540.0) % 360.0 - 180.0) * k;
        int box = c.minimapSize;
        int x = right - box;
        int mapY = y + 17;
        int footer = (c.minimapCoords ? 12 : 0) + (c.minimapBiome ? 11 : 0);
        Hud.mark(x - 3, y, right + 3, mapY + box + footer + 4);
        HudSurface.draw(g, x - 3, y, box + 6, box + footer + 21, alpha, contentAlpha);
        float previousOpacity = UiOpacity.value;
        UiOpacity.value *= contentAlpha;

        try {
            Icons.draw(g, "map", x + 1, y, 16.0F);
            Component zoom = Ui.small(Component.literal(String.format(Locale.ROOT, "%.1f×", shownZoom)));
            Ui.text(g, zoom, right - Ui.width(zoom) - 3, y + 5, Ui.MUTED);
            Component heading = Ui.small(Component.literal(c.minimapRotate ? "↑" : "N"));
            Ui.text(g, heading, x + (box - Ui.width(heading)) / 2, y + 5, Ui.TEXT);
            double px = player.xOld + (player.getX() - player.xOld) * partial;
            double pz = player.zOld + (player.getZ() - player.zOld) * partial;
            double reach = box / 2.0 / shownZoom * 1.42;
            int x0 = Math.floorDiv((int)Math.floor(px - reach), 512);
            int x1 = Math.floorDiv((int)Math.ceil(px + reach), 512);
            int z0 = Math.floorDiv((int)Math.floor(pz - reach), 512);
            int z1 = Math.floorDiv((int)Math.ceil(pz + reach), 512);
            int cx = x + box / 2;
            int cy = mapY + box / 2;
            g.enableScissor(x, mapY, right, mapY + box);
            g.pose().pushMatrix();
            g.pose().translate(cx, cy);
            g.pose().rotate((float)Math.toRadians(rotation));
            g.pose().scale((float)shownZoom, (float)shownZoom);
            g.pose().translate((float)(-px), (float)(-pz));

            for (int rx = x0; rx <= x1; rx++) {
                for (int rz = z0; rz <= z1; rz++) {
                    MapCache.Region region = MapCache.region(rx, rz, false);
                    if (region != null) {
                        g.blit(RenderPipelines.GUI_TEXTURED, region.id(), rx * 512, rz * 512, 0.0F, 0.0F, 512, 512, 512, 512, Ui.alpha(-1, contentAlpha));
                    }
                }
            }

            g.pose().popMatrix();
            arrow(g, cx, cy, (float)Math.toRadians(yaw + 180.0 + rotation), contentAlpha);
            if (c.minimapWaypoints) {
                for (Waypoints.Wp point : Waypoints.here(mc)) {
                    double dx = (point.x() + 0.5 - px) * shownZoom;
                    double dz = (point.z() + 0.5 - pz) * shownZoom;
                    double a = Math.toRadians(rotation);
                    double nx = dx * Math.cos(a) - dz * Math.sin(a);
                    double nz = dx * Math.sin(a) + dz * Math.cos(a);
                    double size = Math.max(Math.abs(nx), Math.abs(nz));
                    double limit = box / 2.0 - 6.0;
                    if (size > limit) {
                        nx *= limit / size;
                        nz *= limit / size;
                    }

                    int wx = cx + (int)Math.round(nx);
                    int wz = cy + (int)Math.round(nz);
                    Glass.fill(g, wx - 3, wz - 3, 6, 6, -535815658);
                    g.fill(wx - 1, wz - 1, wx + 2, wz + 2, Ui.alpha(point.color(), contentAlpha));
                }
            }

            String[] directions = new String[]{"N", "E", "S", "W"};

            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(i * 90 + rotation);
                int lx = cx + (int)Math.round(Math.sin(a) * (box / 2.0 - 6.0));
                int ly = cy - (int)Math.round(Math.cos(a) * (box / 2.0 - 6.0));
                Component label = Ui.small(Component.literal(directions[i]));
                Ui.text(g, label, lx - Ui.width(label) / 2, ly - 3, i == 0 ? -1791589 : Ui.TEXT);
            }

            g.disableScissor();
            int row = mapY + box + 4;
            if (c.minimapCoords) {
                Component label = Ui.fit(
                    Ui.small(Component.literal(player.blockPosition().getX() + "  " + player.blockPosition().getY() + "  " + player.blockPosition().getZ())),
                    box - 6
                );
                Ui.text(g, label, x + (box - Ui.width(label)) / 2, row, Ui.TEXT);
                row += 12;
            }

            if (c.minimapBiome) {
                String key = mc.level.getBiome(player.blockPosition()).unwrapKey().map(b -> b.identifier().toString()).orElse("");
                if (!key.equals(biomeKey) || !language.equals(mc.options.languageCode)) {
                    biomeKey = key;
                    language = mc.options.languageCode;
                    String[] parts = key.split(":", 2);
                    biomeName = parts.length == 2 ? Component.translatable("biome." + parts[0] + "." + parts[1].replace('/', '.')).getString() : "";
                }

                Component label = Ui.fit(Ui.small(Component.literal(biomeName)), box - 6);
                Ui.text(g, label, x + (box - Ui.width(label)) / 2, row, Ui.MUTED);
            }

            return mapY + box + footer + 8;
        } finally {
            UiOpacity.value = previousOpacity;
        }
    }

    private static void arrow(GuiGraphics g, int cx, int cy, float a, float alpha) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate(a);

        for (int i = 0; i < 6; i++) {
            int half = i / 2;
            g.fill(-half - 1, -4 + i, half + 2, -3 + i, Ui.alpha(-16777216, alpha));
        }

        for (int i = 0; i < 5; i++) {
            int half = i / 2;
            g.fill(-half, -3 + i, half + 1, -2 + i, Ui.alpha(-1, alpha));
        }

        g.pose().popMatrix();
    }
}
