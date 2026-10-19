package dev.jun.nosclient.scene;

import static org.lwjgl.opengl.GL33C.*;

import org.lwjgl.system.MemoryStack;

/**
 * Chụp lại rồi trả đúng trạng thái GL mà renderer của game (GlStateManager / GlCommandEncoder) đang giữ trong bộ nhớ đệm.
 * Game không hỏi lại GL — nó tin bộ đệm của nó; ta đổi mà không trả là khung sau vẽ sai.
 */
final class GlState {
	private int program, vao, arrayBuf, readFbo, drawFbo, activeTex;
	private final int[] tex = new int[4], samplers = new int[4], viewport = new int[4], scissorBox = new int[4];
	private boolean blend, depth, cull, scissor, polyOffset, depthMask;
	private int bSrcRgb, bDstRgb, bSrcA, bDstA, depthFunc;
	private float poFactor, poUnits;
	private final boolean[] colorMask = new boolean[4];

	void save() {
		try (MemoryStack s = MemoryStack.stackPush()) {
			program = glGetInteger(GL_CURRENT_PROGRAM);
			vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
			arrayBuf = glGetInteger(GL_ARRAY_BUFFER_BINDING);
			readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
			drawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
			activeTex = glGetInteger(GL_ACTIVE_TEXTURE);
			for (int i = 0; i < 4; i++) {
				glActiveTexture(GL_TEXTURE0 + i);
				tex[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
				samplers[i] = glGetInteger(GL_SAMPLER_BINDING);
			}
			glActiveTexture(activeTex);
			var v = s.mallocInt(4);
			glGetIntegerv(GL_VIEWPORT, v);
			v.get(viewport);
			glGetIntegerv(GL_SCISSOR_BOX, v.clear());
			v.get(scissorBox);
			var b = s.malloc(4);
			glGetBooleanv(GL_COLOR_WRITEMASK, b);
			for (int i = 0; i < 4; i++) colorMask[i] = b.get(i) != 0;
		}
		blend = glIsEnabled(GL_BLEND);
		depth = glIsEnabled(GL_DEPTH_TEST);
		cull = glIsEnabled(GL_CULL_FACE);
		scissor = glIsEnabled(GL_SCISSOR_TEST);
		polyOffset = glIsEnabled(GL_POLYGON_OFFSET_FILL);
		depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
		depthFunc = glGetInteger(GL_DEPTH_FUNC);
		bSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
		bDstRgb = glGetInteger(GL_BLEND_DST_RGB);
		bSrcA = glGetInteger(GL_BLEND_SRC_ALPHA);
		bDstA = glGetInteger(GL_BLEND_DST_ALPHA);
		poFactor = glGetFloat(GL_POLYGON_OFFSET_FACTOR);
		poUnits = glGetFloat(GL_POLYGON_OFFSET_UNITS);
	}

	/** Trạng thái sạch cho cảnh của ta: không cắt mặt, không scissor, ghi đủ màu. */
	static void clean() {
		glDisable(GL_SCISSOR_TEST);
		glDisable(GL_CULL_FACE);
		glDisable(GL_BLEND);
		glDisable(GL_POLYGON_OFFSET_FILL);
		glColorMask(true, true, true, true);
		glDepthMask(true);
		glDepthFunc(GL_LEQUAL);
		for (int i = 0; i < 4; i++) glBindSampler(i, 0);
	}

	void restore() {
		glUseProgram(program);
		glBindVertexArray(vao);
		glBindBuffer(GL_ARRAY_BUFFER, arrayBuf);
		glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
		glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
		for (int i = 0; i < 4; i++) {
			glActiveTexture(GL_TEXTURE0 + i);
			glBindTexture(GL_TEXTURE_2D, tex[i]);
			glBindSampler(i, samplers[i]);
		}
		glActiveTexture(activeTex);
		glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
		glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
		set(GL_BLEND, blend);
		set(GL_DEPTH_TEST, depth);
		set(GL_CULL_FACE, cull);
		set(GL_SCISSOR_TEST, scissor);
		set(GL_POLYGON_OFFSET_FILL, polyOffset);
		glDepthMask(depthMask);
		glDepthFunc(depthFunc);
		glBlendFuncSeparate(bSrcRgb, bDstRgb, bSrcA, bDstA);
		glPolygonOffset(poFactor, poUnits);
		glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
	}

	private static void set(int cap, boolean on) {
		if (on) glEnable(cap);
		else glDisable(cap);
	}
}
