package dev.jun.nosclient.scene;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
final class GlState {
    private int program;
    private int vao;
    private int arrayBuf;
    private int readFbo;
    private int drawFbo;
    private int activeTex;
    private final int[] tex = new int[4];
    private final int[] samplers = new int[4];
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private boolean blend;
    private boolean depth;
    private boolean cull;
    private boolean scissor;
    private boolean polyOffset;
    private boolean depthMask;
    private int bSrcRgb;
    private int bDstRgb;
    private int bSrcA;
    private int bDstA;
    private int depthFunc;
    private float poFactor;
    private float poUnits;
    private final boolean[] colorMask = new boolean[4];

    void save() {
        MemoryStack s = MemoryStack.stackPush();

        try {
            this.program = GL33C.glGetInteger(35725);
            this.vao = GL33C.glGetInteger(34229);
            this.arrayBuf = GL33C.glGetInteger(34964);
            this.readFbo = GL33C.glGetInteger(36010);
            this.drawFbo = GL33C.glGetInteger(36006);
            this.activeTex = GL33C.glGetInteger(34016);

            for (int i = 0; i < 4; i++) {
                GL33C.glActiveTexture(33984 + i);
                this.tex[i] = GL33C.glGetInteger(32873);
                this.samplers[i] = GL33C.glGetInteger(35097);
            }

            GL33C.glActiveTexture(this.activeTex);
            IntBuffer v = s.mallocInt(4);
            GL33C.glGetIntegerv(2978, v);
            v.get(this.viewport);
            GL33C.glGetIntegerv(3088, v.clear());
            v.get(this.scissorBox);
            ByteBuffer b = s.malloc(4);
            GL33C.glGetBooleanv(3107, b);

            for (int i = 0; i < 4; i++) {
                this.colorMask[i] = b.get(i) != 0;
            }
        } catch (Throwable var6) {
            if (s != null) {
                try {
                    s.close();
                } catch (Throwable var5) {
                    var6.addSuppressed(var5);
                }
            }

            throw var6;
        }

        if (s != null) {
            s.close();
        }

        this.blend = GL33C.glIsEnabled(3042);
        this.depth = GL33C.glIsEnabled(2929);
        this.cull = GL33C.glIsEnabled(2884);
        this.scissor = GL33C.glIsEnabled(3089);
        this.polyOffset = GL33C.glIsEnabled(32823);
        this.depthMask = GL33C.glGetBoolean(2930);
        this.depthFunc = GL33C.glGetInteger(2932);
        this.bSrcRgb = GL33C.glGetInteger(32969);
        this.bDstRgb = GL33C.glGetInteger(32968);
        this.bSrcA = GL33C.glGetInteger(32971);
        this.bDstA = GL33C.glGetInteger(32970);
        this.poFactor = GL33C.glGetFloat(32824);
        this.poUnits = GL33C.glGetFloat(10752);
    }

    static void clean() {
        GL33C.glDisable(3089);
        GL33C.glDisable(2884);
        GL33C.glDisable(3042);
        GL33C.glDisable(32823);
        GL33C.glColorMask(true, true, true, true);
        GL33C.glDepthMask(true);
        GL33C.glDepthFunc(515);

        for (int i = 0; i < 4; i++) {
            GL33C.glBindSampler(i, 0);
        }
    }

    void restore() {
        GL33C.glUseProgram(this.program);
        GL33C.glBindVertexArray(this.vao);
        GL33C.glBindBuffer(34962, this.arrayBuf);
        GL33C.glBindFramebuffer(36008, this.readFbo);
        GL33C.glBindFramebuffer(36009, this.drawFbo);

        for (int i = 0; i < 4; i++) {
            GL33C.glActiveTexture(33984 + i);
            GL33C.glBindTexture(3553, this.tex[i]);
            GL33C.glBindSampler(i, this.samplers[i]);
        }

        GL33C.glActiveTexture(this.activeTex);
        GL33C.glViewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
        GL33C.glScissor(this.scissorBox[0], this.scissorBox[1], this.scissorBox[2], this.scissorBox[3]);
        set(3042, this.blend);
        set(2929, this.depth);
        set(2884, this.cull);
        set(3089, this.scissor);
        set(32823, this.polyOffset);
        GL33C.glDepthMask(this.depthMask);
        GL33C.glDepthFunc(this.depthFunc);
        GL33C.glBlendFuncSeparate(this.bSrcRgb, this.bDstRgb, this.bSrcA, this.bDstA);
        GL33C.glPolygonOffset(this.poFactor, this.poUnits);
        GL33C.glColorMask(this.colorMask[0], this.colorMask[1], this.colorMask[2], this.colorMask[3]);
    }

    private static void set(int cap, boolean on) {
        if (on) {
            GL33C.glEnable(cap);
        } else {
            GL33C.glDisable(cap);
        }
    }
}
