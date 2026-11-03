package dev.jun.nosclient.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.config.MusicSettings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class HelmetLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final String ID = "astronaut_helmet";
    private static final Identifier WHITE = ClientResources.id("textures/misc/white.png");
    private static final float PX = 0.0625F;
    private static final float V = 0.5F;
    private static List<HelmetLayer.Vox> solid;
    private static List<HelmetLayer.Vox> glow;
    private static final int WHITE_SHELL = 15922423;
    private static final int SHADE = 14278116;
    private static final int RIM = 12173259;
    private static final int COLLAR = 1843760;
    private static final int COLLAR_HI = 3029072;
    private static final int RED = 14879255;
    private static final int FLAG_W = 16777215;
    private static final int ANT = 10134192;
    private static final float VX0 = -3.5F;
    private static final float VX1 = 3.5F;
    private static final float VY0 = -6.5F;
    private static final float VY1 = -1.5F;
    private static final String[] FLAG =
            new String[] {
                "RRRRRRRRRR",
                "RRRWWWRRRR",
                "RRWWRRRRRR",
                "RRWRRRRWRR",
                "RRWRRRWWWR",
                "RRWWRRRWRR",
                "RRRWWWRRRR",
                "RRRRRRRRRR"
            };

    public HelmetLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    public static boolean on() {
        MusicConfig c = MusicSettings.get();
        return c.enabled && "astronaut_helmet".equals(c.cosmetic);
    }

    public void submit(
            PoseStack pose,
            SubmitNodeCollector queue,
            int light,
            AvatarRenderState state,
            float yRot,
            float xRot) {
        Minecraft mc = Minecraft.getInstance();
        if (MusicSettings.get().enabled
                && mc.player != null
                && !state.isInvisible
                && !state.isSpectator) {
            if ("astronaut_helmet".equals(Cosmetics.forEntity(state.id))) {
                Player owner = Cosmetics.playerOf(state.id);
                if (owner != null) {
                    if (((PlayerModel) this.getParentModel()).head.visible) {
                        if (solid == null) {
                            build();
                        }

                        double t = System.nanoTime() / 1.0E9;
                        boolean blink = t % 1.4 < 0.9;
                        pose.pushPose();
                        ((PlayerModel) this.getParentModel()).head.translateAndRotate(pose);
                        if (!owner.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                            pose.translate(0.0F, -0.25F, 0.0F);
                            pose.scale(1.17F, 1.17F, 1.17F);
                            pose.translate(0.0F, 0.25F, 0.0F);
                        }

                        queue.submitCustomGeometry(
                                pose,
                                RenderTypes.entityCutoutNoCull(WHITE),
                                (p, vc) -> {
                                    for (HelmetLayer.Vox v : solid) {
                                        cube(vc, p, v, light);
                                    }
                                });
                        queue.submitCustomGeometry(
                                pose,
                                RenderTypes.entityTranslucent(WHITE),
                                (p, vc) -> visor(vc, p, light));
                        queue.submitCustomGeometry(
                                pose,
                                RenderTypes.entityCutoutNoCull(WHITE),
                                (p, vc) -> {
                                    for (HelmetLayer.Vox v : glow) {
                                        if (v.rgb() != 16724016 || blink) {
                                            cube(vc, p, v, 15728880);
                                        }
                                    }
                                });
                        pose.popPose();
                    }
                }
            }
        }
    }

    private static void build() {
        Map<Long, int[]> m = new HashMap<>();
        List<HelmetLayer.Vox> g = new ArrayList<>();

        for (int ix = -11; ix < 11; ix++) {
            for (int iy = -19; iy < 1; iy++) {
                for (int iz = -11; iz < 11; iz++) {
                    float cx = (ix + 0.5F) * 0.5F;
                    float cy = (iy + 0.5F) * 0.5F;
                    float cz = (iz + 0.5F) * 0.5F;
                    boolean inner = Math.abs(cx) < 4.5F && Math.abs(cz) < 4.5F && cy > -8.5F;
                    if (!inner) {
                        int edges =
                                (Math.abs(cx) > 5.0F ? 1 : 0)
                                        + (Math.abs(cz) > 5.0F ? 1 : 0)
                                        + (cy < -9.0F ? 1 : 0);
                        if (edges < 2
                                && (!(cz < -4.5F)
                                        || !(cx > -3.5F)
                                        || !(cx < 3.5F)
                                        || !(cy > -6.5F)
                                        || !(cy < -1.5F))) {
                            int col = 15922423;
                            boolean front = cz < -4.5F;
                            if (front && cx > -4.5F && cx < 4.5F && cy > -7.5F && cy < -0.5F) {
                                col = 12173259;
                            }

                            if (cy > -0.5F) {
                                col = 14278116;
                            }

                            if (Math.abs(cy + 8.75F) < 0.3F && !front) {
                                col = 14278116;
                            }

                            if (cx > 5.0F && cz > -2.0F && cz < 3.0F && cy > -7.0F && cy < -3.0F) {
                                int pz = (int) Math.floor((cz + 2.0F) / 0.5F);
                                int py = (int) Math.floor((cy + 7.0F) / 0.5F);
                                col = FLAG[py].charAt(pz) == 'W' ? 16777215 : 14879255;
                            }

                            put(m, ix, iy, iz, col);
                        }
                    }
                }
            }
        }

        for (int ix = -12; ix < 12; ix++) {
            for (int iy = 0; iy < 3; iy++) {
                for (int iz = -12; iz < 12; iz++) {
                    float cx = (ix + 0.5F) * 0.5F;
                    float cz = (iz + 0.5F) * 0.5F;
                    float r = Math.max(Math.abs(cx), Math.abs(cz));
                    if (!(r < 4.5F)
                            && !(r > 6.0F)
                            && (!(Math.abs(cx) > 5.5F) || !(Math.abs(cz) > 5.5F))) {
                        put(m, ix, iy, iz, iy == 0 ? 3029072 : 1843760);
                    }
                }
            }
        }

        for (int iy = -23; iy < -19; iy++) {
            put(m, -7, iy, 6, 10134192);
        }

        g.add(new HelmetLayer.Vox(-7, -24, 6, 16724016, 63, true));

        for (int iy = -11; iy < -9; iy++) {
            for (int iz = -1; iz < 1; iz++) {
                g.add(new HelmetLayer.Vox(-12, iy, iz, 6285567, 63, true));
            }
        }

        List<HelmetLayer.Vox> s = new ArrayList<>();

        for (Entry<Long, int[]> e : m.entrySet()) {
            long k = e.getKey();
            int ix = (int) (k >> 40) - 1000;
            int iy = (int) (k >> 20 & 1048575L) - 1000;
            int iz = (int) (k & 1048575L) - 1000;
            int faces = 0;
            if (!m.containsKey(key(ix, iy - 1, iz))) {
                faces |= 1;
            }

            if (!m.containsKey(key(ix, iy + 1, iz))) {
                faces |= 2;
            }

            if (!m.containsKey(key(ix, iy, iz - 1))) {
                faces |= 4;
            }

            if (!m.containsKey(key(ix, iy, iz + 1))) {
                faces |= 8;
            }

            if (!m.containsKey(key(ix - 1, iy, iz))) {
                faces |= 16;
            }

            if (!m.containsKey(key(ix + 1, iy, iz))) {
                faces |= 32;
            }

            if (faces != 0) {
                s.add(new HelmetLayer.Vox(ix, iy, iz, e.getValue()[0], faces, false));
            }
        }

        solid = s;
        glow = g;
    }

    private static long key(int x, int y, int z) {
        return (long) (x + 1000) << 40 | (long) (y + 1000) << 20 | z + 1000;
    }

    private static void put(Map<Long, int[]> m, int x, int y, int z, int col) {
        m.put(key(x, y, z), new int[] {col});
    }

    private static void visor(VertexConsumer vc, Pose p, int light) {
        float z = -0.3125F;
        float x0 = -0.21875F;
        float x1 = 0.21875F;
        float y0 = -0.40625F;
        float y1 = -0.09375F;
        int top = -2131508128;
        int bot = 1892192302;
        v(vc, p, x0, y0, z, top, light, 0.0F, 0.0F, -1.0F);
        v(vc, p, x1, y0, z, top, light, 0.0F, 0.0F, -1.0F);
        v(vc, p, x1, y1, z, bot, light, 0.0F, 0.0F, -1.0F);
        v(vc, p, x0, y1, z, bot, light, 0.0F, 0.0F, -1.0F);
        float sx0 = -0.1625F;
        float sx1 = -0.10625F;
        float sy0 = -0.38125F;
        float sy1 = -0.225F;
        float zz = z - 6.25E-4F;
        int sh = 1895825407;
        v(vc, p, sx0, sy0, zz, sh, light, 0.0F, 0.0F, -1.0F);
        v(vc, p, sx1, sy0, zz, sh, light, 0.0F, 0.0F, -1.0F);
        v(vc, p, sx1 - 0.0375F, sy1, zz, sh, light, 0.0F, 0.0F, -1.0F);
        v(vc, p, sx0 - 0.0375F, sy1, zz, sh, light, 0.0F, 0.0F, -1.0F);
    }

    private static void cube(VertexConsumer vc, Pose p, HelmetLayer.Vox v, int light) {
        float x0 = v.x() * 0.5F * 0.0625F;
        float x1 = (v.x() + 1) * 0.5F * 0.0625F;
        float y0 = v.y() * 0.5F * 0.0625F;
        float y1 = (v.y() + 1) * 0.5F * 0.0625F;
        float z0 = v.z() * 0.5F * 0.0625F;
        float z1 = (v.z() + 1) * 0.5F * 0.0625F;
        int f = v.faces();
        int rgb = v.rgb();
        boolean both = v.glow();
        if ((f & 1) != 0) {
            quad(
                    vc, p, rgb, 1.0F, light, both, 0.0F, -1.0F, 0.0F, x0, y0, z0, x1, y0, z0, x1,
                    y0, z1, x0, y0, z1);
        }

        if ((f & 2) != 0) {
            quad(
                    vc, p, rgb, 0.55F, light, both, 0.0F, 1.0F, 0.0F, x0, y1, z1, x1, y1, z1, x1,
                    y1, z0, x0, y1, z0);
        }

        if ((f & 4) != 0) {
            quad(
                    vc, p, rgb, 0.9F, light, both, 0.0F, 0.0F, -1.0F, x0, y1, z0, x1, y1, z0, x1,
                    y0, z0, x0, y0, z0);
        }

        if ((f & 8) != 0) {
            quad(
                    vc, p, rgb, 0.8F, light, both, 0.0F, 0.0F, 1.0F, x1, y1, z1, x0, y1, z1, x0, y0,
                    z1, x1, y0, z1);
        }

        if ((f & 16) != 0) {
            quad(
                    vc, p, rgb, 0.72F, light, both, -1.0F, 0.0F, 0.0F, x0, y1, z1, x0, y1, z0, x0,
                    y0, z0, x0, y0, z1);
        }

        if ((f & 32) != 0) {
            quad(
                    vc, p, rgb, 0.72F, light, both, 1.0F, 0.0F, 0.0F, x1, y1, z0, x1, y1, z1, x1,
                    y0, z1, x1, y0, z0);
        }
    }

    private static void quad(
            VertexConsumer vc,
            Pose p,
            int rgb,
            float k,
            int light,
            boolean both,
            float nx,
            float ny,
            float nz,
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz,
            float cx,
            float cy,
            float cz,
            float dx,
            float dy,
            float dz) {
        if (both) {
            k = 1.0F;
        }

        int r = Math.min(255, (int) ((rgb >> 16 & 0xFF) * k));
        int g = Math.min(255, (int) ((rgb >> 8 & 0xFF) * k));
        int b = Math.min(255, (int) ((rgb & 0xFF) * k));
        int col = 0xFF000000 | r << 16 | g << 8 | b;
        v(vc, p, ax, ay, az, col, light, nx, ny, nz);
        v(vc, p, bx, by, bz, col, light, nx, ny, nz);
        v(vc, p, cx, cy, cz, col, light, nx, ny, nz);
        v(vc, p, dx, dy, dz, col, light, nx, ny, nz);
    }

    private static void v(
            VertexConsumer vc,
            Pose p,
            float x,
            float y,
            float z,
            int col,
            int light,
            float nx,
            float ny,
            float nz) {
        vc.addVertex(p, x, y, z)
                .setColor(col)
                .setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(p, nx, ny, nz);
    }

    private record Vox(int x, int y, int z, int rgb, int faces, boolean glow) {}
}
