package dev.jun.nosclient.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.util.Anim;
import dev.jun.nosclient.util.Http;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public final class Cosmetics {
    public static final String[] IDS =
            new String[] {
                "none",
                "kuz3y_wqq",
                "astronaut_helmet",
                "axolotl",
                "snorkel",
                "cat_ears",
                "pixel_glasses",
                "halo",
                "hair_bow",
                "cap_back",
                "devil_horns",
                "viking",
                "cat_headphones",
                "straw_hat",
                "pirate_hat",
                "spartan",
                "dragon_wings",
                "red_dragon_wings",
                "bat_wings",
                "captain_hat",
                "knight_helmet",
                "top_hat",
                "witch_hat",
                "jack_o_lantern",
                "cowboy_hat",
                "snapback",
                "ninja_band",
                "traffic_cone"
            };
    private static final float SCALE = 0.3F;
    private static final Identifier SKIN = ClientResources.id("cosmetic/buddy_skin");
    static final Identifier KUZ3Y = ClientResources.id("textures/cosmetic/kuz3y_wqq.png");
    private static final Map<Integer, PlayerModel> BUDDIES = new HashMap<>();
    private static PlayerModel wide;
    private static PlayerModel slim;
    private static String loadedName;
    private static String loadingName;
    private static boolean slimSkin;
    private static boolean ready;
    private static long nextTry;
    private static int tries;
    private static String lastWanted;

    static PlayerModel buddyFor(int entityId, double t, float headYaw, float headPitch) {
        PlayerModel model;
        try {
            if (BUDDIES.size() > 32) {
                BUDDIES.clear();
            }

            model =
                    BUDDIES.computeIfAbsent(
                            entityId,
                            k ->
                                    new PlayerModel(
                                            Minecraft.getInstance()
                                                    .getEntityModels()
                                                    .bakeLayer(ModelLayers.PLAYER_SLIM),
                                            true));
        } catch (Throwable e) {
            return null;
        }

        pose(model, t);
        model.head.yRot = 0.0F;
        model.head.xRot = (float) Math.toRadians(Mth.clamp(headPitch, -60.0F, 60.0F));
        model.head.zRot = -0.04F * (float) Math.sin(t * 7.0);
        return model;
    }

    static PlayerModel buddy(double t) {
        PlayerModel model = model(true);
        if (model == null) {
            return null;
        }

        pose(model, t);
        return model;
    }

    private static void pose(PlayerModel model, double t) {
        model.resetPose();
        model.setAllVisible(true);
        model.rightLeg.xRot = -1.35F;
        model.rightLeg.yRot = 0.18F;
        model.leftLeg.xRot = -1.35F;
        model.leftLeg.yRot = -0.18F;
        model.rightArm.xRot = -0.15F;
        model.rightArm.zRot = 2.55F + 0.38F * (float) Math.sin(t * 7.0);
        model.leftArm.xRot = -0.55F;
        model.leftArm.zRot = -0.12F;
        model.head.yRot = 0.25F * (float) Math.sin(t * 0.9);
        model.head.xRot = 0.08F + 0.05F * (float) Math.sin(t * 1.3);
        model.head.zRot = -0.06F * (float) Math.sin(t * 7.0);
    }

    private Cosmetics() {}

    public static void render(PoseStack ps, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (dev.jun.nosclient.ClientEffects.cfg().cosmeticsEnabled
                && mc.level != null
                && buffers != null) {
            for (Player pl : mc.level.players()) {
                if ("kuz3y_wqq".equals(forPlayer(pl))
                        && (pl != mc.player || !mc.options.getCameraType().isFirstPerson())
                        && !(pl.distanceToSqr(mc.gameRenderer.getMainCamera().position())
                                > 4096.0)) {
                    renderOne(ps, buffers, pl, "kuz3y_wqq");
                }
            }
        }
    }

    public static String forPlayer(Player p) {
        Minecraft mc = Minecraft.getInstance();
        MusicConfig c = MusicSettings.get();
        if (!dev.jun.nosclient.ClientEffects.cfg().cosmeticsEnabled) {
            return "none";
        } else {
            // Chỉ người chơi này thấy cosmetic của chính mình: không gửi, không nhận gì qua mạng.
            return p == mc.player && c.cosmetic != null ? c.cosmetic : "none";
        }
    }

    public static String forEntity(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return "none";
        } else if (mc.player != null && entityId == mc.player.getId()) {
            return forPlayer(mc.player);
        } else {
            return mc.level.getEntity(entityId) instanceof Player p ? forPlayer(p) : "none";
        }
    }

    public static Player playerOf(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        } else {
            return mc.level.getEntity(entityId) instanceof Player p ? p : null;
        }
    }

    private static void renderOne(PoseStack ps, MultiBufferSource buffers, Player pl, String name) {
        Minecraft mc = Minecraft.getInstance();
        Identifier skinTex;
        boolean isSlim;
        if ("kuz3y_wqq".equals(name)) {
            skinTex = KUZ3Y;
            isSlim = true;
        } else {
            ensureSkin(name);
            if (!ready || !name.equals(loadedName)) {
                return;
            }

            skinTex = SKIN;
            isSlim = slimSkin;
        }

        if (!pl.isInvisible()) {
            Pose pose = pl.getPose();
            if (pose == Pose.STANDING || pose == Pose.CROUCHING) {
                PlayerModel model = model(isSlim);
                if (model != null) {
                    float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                    Vec3 cam = mc.gameRenderer.getMainCamera().position();
                    Vec3 p = pl.getPosition(pt);
                    float bodyDeg = Mth.rotLerp(pt, pl.yBodyRotO, pl.yBodyRot);
                    double body = Math.toRadians(bodyDeg);
                    double t = Anim.seconds();
                    double side = 0.38;
                    double top = pl.isCrouching() ? 1.12 : 1.4;
                    if (!pl.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
                        side += 0.06;
                        top += 0.09;
                    }

                    double x = p.x - cam.x - Math.cos(body) * side;
                    double y = p.y - cam.y + top;
                    double z = p.z - cam.z - Math.sin(body) * side;
                    model.resetPose();
                    model.setAllVisible(true);
                    model.rightLeg.xRot = -1.35F;
                    model.rightLeg.yRot = 0.18F;
                    model.leftLeg.xRot = -1.35F;
                    model.leftLeg.yRot = -0.18F;
                    model.rightArm.xRot = -0.15F;
                    model.rightArm.zRot = 2.55F + 0.38F * (float) Math.sin(t * 7.0);
                    model.leftArm.xRot = -0.55F;
                    model.leftArm.zRot = -0.12F;
                    float headYaw =
                            Mth.wrapDegrees(Mth.rotLerp(pt, pl.yHeadRotO, pl.yHeadRot) - bodyDeg);
                    float headPitch = Mth.lerp(pt, pl.xRotO, pl.getXRot());
                    model.head.yRot = (float) Math.toRadians(Mth.clamp(headYaw, -75.0F, 75.0F));
                    model.head.xRot = (float) Math.toRadians(Mth.clamp(headPitch, -60.0F, 60.0F));
                    model.head.zRot = -0.04F * (float) Math.sin(t * 7.0);
                    int light = mc.getEntityRenderDispatcher().getPackedLightCoords(pl, pt);
                    ps.pushPose();
                    ps.translate(x, y, z);
                    ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyDeg));
                    ps.scale(-0.3F, -0.3F, 0.3F);
                    ps.translate(0.0F, -0.75F, 0.0F);
                    RenderType type = RenderTypes.entityTranslucent(skinTex);
                    model.renderToBuffer(
                            ps, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY);
                    ps.popPose();
                    if (buffers instanceof BufferSource bs) {
                        bs.endBatch(type);
                    }
                }
            }
        }
    }

    private static PlayerModel model(boolean isSlim) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (isSlim) {
                if (slim == null) {
                    slim =
                            new PlayerModel(
                                    mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
                }

                return slim;
            } else {
                if (wide == null) {
                    wide =
                            new PlayerModel(
                                    mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
                }

                return wide;
            }
        } catch (Throwable e) {
            return null;
        }
    }

    private static void ensureSkin(String name) {
        if (!name.equals(loadedName) && !name.equals(loadingName)) {
            if (!name.equals(lastWanted)) {
                lastWanted = name;
                tries = 0;
                nextTry = 0L;
                ready = false;
            }

            long now = System.currentTimeMillis();
            if (now >= nextTry && tries < 5) {
                tries++;
                loadingName = name;
                Thread th =
                        new Thread(
                                () -> {
                                    try {
                                        Http.Response r =
                                                Http.get(
                                                        "https://api.mojang.com/users/profiles/minecraft/"
                                                                + Http.enc(name),
                                                        null);
                                        if (r.code() != 200) {
                                            throw new IllegalStateException("profile " + r.code());
                                        }

                                        String id =
                                                JsonParser.parseString(r.body())
                                                        .getAsJsonObject()
                                                        .get("id")
                                                        .getAsString();
                                        Http.Response s =
                                                Http.get(
                                                        "https://sessionserver.mojang.com/session/minecraft/profile/"
                                                                + id,
                                                        null);
                                        if (s.code() != 200) {
                                            throw new IllegalStateException("session " + s.code());
                                        }

                                        String b64 =
                                                JsonParser.parseString(s.body())
                                                        .getAsJsonObject()
                                                        .getAsJsonArray("properties")
                                                        .get(0)
                                                        .getAsJsonObject()
                                                        .get("value")
                                                        .getAsString();
                                        JsonObject skin =
                                                JsonParser.parseString(
                                                                new String(
                                                                        Base64.getDecoder()
                                                                                .decode(b64),
                                                                        StandardCharsets.UTF_8))
                                                        .getAsJsonObject()
                                                        .getAsJsonObject("textures")
                                                        .getAsJsonObject("SKIN");
                                        String url =
                                                skin.get("url")
                                                        .getAsString()
                                                        .replace("http://", "https://");
                                        boolean isSlim =
                                                skin.has("metadata")
                                                        && "slim"
                                                                .equals(
                                                                        skin.getAsJsonObject(
                                                                                        "metadata")
                                                                                .get("model")
                                                                                .getAsString());
                                        byte[] png = Http.getBytes(url);
                                        if (png == null) {
                                            throw new IllegalStateException("skin download");
                                        }

                                        NativeImage img = legacy(NativeImage.read(png));
                                        Minecraft.getInstance()
                                                .execute(
                                                        () -> {
                                                            Minecraft.getInstance()
                                                                    .getTextureManager()
                                                                    .register(
                                                                            SKIN,
                                                                            new DynamicTexture(
                                                                                    () ->
                                                                                            "nosclient"
                                                                                                + " buddy"
                                                                                                + " skin",
                                                                                    img));
                                                            slimSkin = isSlim;
                                                            loadedName = name;
                                                            ready = true;
                                                            if (name.equals(loadingName)) {
                                                                loadingName = null;
                                                            }
                                                        });
                                    } catch (Throwable e) {
                                        ClientResources.LOG.warn(
                                                "[Nos Client] Could not load the skin of {}: {}",
                                                name,
                                                e.toString());
                                        nextTry = System.currentTimeMillis() + 20000L;
                                        if (name.equals(loadingName)) {
                                            loadingName = null;
                                        }
                                    }
                                },
                                "Nos Client skin");
                th.setDaemon(true);
                th.start();
            }
        }
    }

    private static NativeImage legacy(NativeImage in) {
        if (in.getHeight() != 32) {
            return in;
        }

        NativeImage out = new NativeImage(64, 64, true);

        for (int yy = 0; yy < 32; yy++) {
            for (int xx = 0; xx < 64; xx++) {
                out.setPixel(xx, yy, in.getPixel(xx, yy));
            }
        }

        for (int yy = 0; yy < 16; yy++) {
            for (int xx = 0; xx < 16; xx++) {
                out.setPixel(16 + xx, 48 + yy, in.getPixel(xx, 16 + yy));
                out.setPixel(32 + xx, 48 + yy, in.getPixel(40 + xx, 16 + yy));
            }
        }

        in.close();
        return out;
    }
}
