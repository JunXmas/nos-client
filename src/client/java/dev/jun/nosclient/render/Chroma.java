package dev.jun.nosclient.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.media.SkyLyrics;
import dev.jun.nosclient.mixin.PostChainAccessor;
import dev.jun.nosclient.mixin.PostPassAccessor;
import dev.jun.nosclient.sky.WorldSky;
import dev.jun.nosclient.util.Anim;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.Map;

public final class Chroma {
    public static final String[] COLORS =
            new String[] {"lilac", "black", "grey", "purple", "pink", "blue", "red", "gold"};
    private static final float[][] LOOK =
            new float[][] {
                {0.92F, 0.885F, 0.92F, 0.04F, 0.035F, 0.1F, 0.8F},
                {0.28F, 0.28F, 0.31F, 0.0F, 0.0F, 0.0F, 1.0F},
                {1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F},
                {0.85F, 0.62F, 1.15F, 0.0F, 0.0F, 0.0F, 1.0F},
                {1.15F, 0.66F, 0.95F, 0.0F, 0.0F, 0.0F, 1.0F},
                {0.62F, 0.82F, 1.18F, 0.0F, 0.0F, 0.0F, 1.0F},
                {1.15F, 0.55F, 0.55F, 0.0F, 0.0F, 0.0F, 1.0F},
                {1.15F, 0.95F, 0.55F, 0.0F, 0.0F, 0.0F, 1.0F}
            };
    private static final float RADIUS = 6.0F;
    private static final float START = 70.0F;
    private static boolean failed;
    private static float radius = 70.0F;
    private static float strength;
    private static boolean wasOn;
    private static GpuBuffer ubo;
    private static PostChain hooked;
    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static final Matrix4f INV = new Matrix4f();
    private static double camX;
    private static double camY;
    private static double camZ;
    private static boolean haveMatrices;
    private static GpuBufferSlice FOG;
    public static boolean deferred;

    private Chroma() {}

    public static boolean drawsLate() {
        return strength > 0.0F && !failed;
    }

    public static void drawGhost() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && haveMatrices) {
            Matrix4fStack mv = RenderSystem.getModelViewStack();
            GpuBufferSlice oldFog = RenderSystem.getShaderFog();
            mv.pushMatrix();
            mv.set(VIEW);

            try {
                if (FOG != null) {
                    RenderSystem.setShaderFog(FOG);
                }

                BufferSource buffers = mc.renderBuffers().bufferSource();
                WorldLyrics.renderGhost(new PoseStack(), buffers);
                buffers.endBatch();
            } catch (Throwable t) {
                ClientResources.LOG.debug("ghost lyrics draw failed", t);
            } finally {
                mv.popMatrix();
                if (oldFog != null) {
                    RenderSystem.setShaderFog(oldFog);
                }
            }
        }
    }

    public static void drawLate() {
        deferred = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && haveMatrices) {
            Matrix4fStack mv = RenderSystem.getModelViewStack();
            GpuBufferSlice oldFog = RenderSystem.getShaderFog();
            mv.pushMatrix();
            mv.set(VIEW);

            try {
                if (FOG != null) {
                    RenderSystem.setShaderFog(FOG);
                }

                BufferSource buffers = mc.renderBuffers().bufferSource();
                PoseStack pose = new PoseStack();
                // Trời và lời được vẽ sau khi shaderpack ghép cảnh, theo thứ tự nền rồi chữ.
                WorldSky.renderLate(pose, buffers, new Vec3(camX, camY, camZ));
                buffers.endBatch();
                SkyLyrics.renderLate(pose, buffers);
                Hud.renderWorldSky(pose, buffers);
                WorldLyrics.renderWorld(new PoseStack(), buffers);
                buffers.endBatch();
            } catch (Throwable t) {
                ClientResources.LOG.debug("late lyrics draw failed", t);
            } finally {
                mv.popMatrix();
                if (oldFog != null) {
                    RenderSystem.setShaderFog(oldFog);
                }
            }
        }
    }

    public static void capture(
            Camera camera, Matrix4f view, Matrix4f projection, GpuBufferSlice fog) {
        FOG = fog;
        if (camera != null && view != null && projection != null) {
            VIEW.set(view);
            PROJ.set(projection);
            Vec3 p = camera.position();
            camX = p.x;
            camY = p.y;
            camZ = p.z;
            haveMatrices = true;
        }
    }

    public static void apply(GraphicsResourceAllocator pool) {
        MusicConfig c = MusicSettings.get();
        Minecraft mc = Minecraft.getInstance();
        boolean on = c.enabled && c.chroma && !failed && mc.level != null && mc.player != null;
        if (on && !wasOn) {
            radius = 70.0F;
            strength = 1.0F;
        }

        wasOn = on;
        float dt = Anim.dt();
        if (on) {
            radius = radius + (6.0F - radius) * (1.0F - (float) Math.exp(-dt * 2.2F));
            strength = 1.0F;
        } else {
            radius = radius + (70.0F - radius) * (1.0F - (float) Math.exp(-dt * 2.5F));
            if (radius > 68.0F) {
                strength = 0.0F;
            }
        }

        if (!(strength <= 0.0F)
                && !failed
                && haveMatrices
                && mc.level != null
                && mc.player != null) {
            try {
                PostChain chain =
                        mc.getShaderManager()
                                .getPostChain(
                                        ClientResources.id("chroma"),
                                        LevelTargetBundle.MAIN_TARGETS);
                if (chain == null) {
                    return;
                }

                if (chain != hooked || ubo == null || ubo.isClosed()) {
                    hook(chain);
                }

                if (ubo == null) {
                    return;
                }

                writeUniforms(mc, c);
                chain.process(mc.getMainRenderTarget(), pool);
            } catch (Throwable t) {
                failed = true;
                ClientResources.LOG.warn(
                        "Chroma effect could not run, turning it off for this session", t);
            }
        }
    }

    private static void hook(PostChain chain) {
        hooked = chain;

        for (PostPass pass : ((PostChainAccessor) chain).nosclient$passes()) {
            Map<String, GpuBuffer> map = ((PostPassAccessor) pass).nosclient$uniforms();
            if (map.containsKey("ChromaConfig")) {
                ubo = RenderSystem.getDevice().createBuffer(() -> "Nos Client Chroma", 136, 256L);
                GpuBuffer old = map.put("ChromaConfig", ubo);
                if (old != null && old != ubo) {
                    old.close();
                }
            }
        }
    }

    private static void writeUniforms(Minecraft mc, MusicConfig c) {
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Entity e = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        Vec3 pp = e.getPosition(pt);
        double bx = Math.floor(camX);
        double by = Math.floor(camY);
        double bz = Math.floor(camZ);
        INV.set(PROJ).mul(VIEW).invert();
        float[] look = LOOK[Math.max(0, Math.min(LOOK.length - 1, c.chromaColor))];

        try (MemoryStack stack = MemoryStack.stackPush()) {
            Std140Builder b = Std140Builder.onStack(stack, 256);
            b.putMat4f(INV);
            b.putVec4((float) (camX - bx), (float) (camY - by), (float) (camZ - bz), 0.0F);
            b.putVec4((float) (pp.x - bx), (float) (pp.y - by), (float) (pp.z - bz), radius);
            b.putVec4(0.5F, look[6], strength, (float) Math.floorMod((long) bx, 256L));
            b.putVec4(look[0], look[1], look[2], (float) Math.floorMod((long) by, 256L));
            b.putVec4(look[3], look[4], look[5], (float) Math.floorMod((long) bz, 256L));
            ByteBuffer data = b.get();
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(ubo.slice(0L, data.remaining()), data);
        }
    }
}
