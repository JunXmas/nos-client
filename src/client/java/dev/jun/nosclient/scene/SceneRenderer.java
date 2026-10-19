package dev.jun.nosclient.scene;

import static org.lwjgl.opengl.GL33C.*;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vẽ cảnh hồ 3D thay panorama của màn chính, bằng GL thô (core 3.3) vào khung chính của game.
 * Port từ preview HTML: cùng lưới, cùng shader, cùng các lượt (bóng đổ → phản chiếu → cảnh → nước/hạt → hậu kỳ).
 *
 * Renderer của 1.21.11 tự giữ bộ đệm trạng thái GL, nên mỗi khung ta chụp lại trạng thái, vẽ, rồi trả lại y nguyên
 * ({@link GlState}). Có lỗi thì ghi log một lần và trả quyền cho panorama gốc — màn chính không bao giờ chết theo mod.
 */
public final class SceneRenderer {
	private static final Logger LOG = LoggerFactory.getLogger("nosclient");
	public static final SceneRenderer INSTANCE = new SceneRenderer();
	private static final int ST = World.STRIDE, SMS = 2048;
	private static final double FOV = Math.toRadians(52);

	private final SceneConfig cfg = SceneConfig.INSTANCE;
	private final GlState gls = new GlState();
	private boolean ready, failed;
	private int glChecks;

	private World world;
	private int nW, nNear, nWater, nCl, nSB;
	private Prog pw, ps, pp, psh, ppo, pcopy;
	private int vaoW, vaoWater, vaoP, bufP, vaoCl, bufCl, vaoS, bufS, vaoEmpty;
	private int txa, txw, txs, txm, fb, fbt, fbDepth, sfb, stx, mfb, mtx, mdt, outFbo, outTex = -1;
	private int tw = -1, th = -1, rw, rh;
	private boolean[] cloudMask;
	private int cShift = Integer.MIN_VALUE;
	private float[] sheepBuf;
	private FloatBuffer pd, sheepFb;
	private static final int PD_VERTS = 600 * 6;

	/** [mọc, tàn] của nhóm 1 = anh đào, 2 = mía (ô 0 bỏ trống cho khớp uF[3] trong shader). */
	private final float[] fe = {0, 0, -1e4f, 1e9f, -1e4f, 1e9f};
	private final double t0 = System.nanoTime() / 1e9;
	private double todD = cfg.tod, lastT;

	private SceneRenderer() {
		if (!cfg.blossom) { fe[2] = 1e9f; fe[3] = -1e4f; }
		if (!cfg.cane) { fe[4] = 1e9f; fe[5] = -1e4f; }
	}

	private double now() {
		return System.nanoTime() / 1e9 - t0 + 30; // +30: mọi mốc "đã mọc từ lâu" (-1e4) đều đã xong
	}

	/** Bật/tắt nhóm cây: chỉ đổi mốc thời gian, shader đỉnh tự cho cây mọc/co. */
	public void toggle(int group, boolean on) {
		double n = now();
		int i = group * 2;
		if (on) {
			fe[i] = (float) n;
			fe[i + 1] = 1e9f;
			growFx(group, n);
		} else fe[i + 1] = (float) n;
		if (group == 1) cfg.blossom = on;
		else cfg.cane = on;
		cfg.save();
	}

	/** @return true nếu đã vẽ cảnh (bên gọi bỏ panorama gốc); false nếu mod hỏng và cần panorama gốc. */
	public boolean render() {
		if (failed) return false;
		gls.save();
		try {
			GlState.clean();
			if (!ready) init();
			frame(now());
			if (glChecks < 5 && ++glChecks == 5) { // tự kiểm sau vài khung chạy thật, như preview in "GL OK"
				int e = glGetError();
				if (e != 0) LOG.warn("Nos client cảnh nền: GL lỗi {}", e);
				else LOG.info("Nos client cảnh nền: GL OK");
			}
			return true;
		} catch (Throwable e) {
			failed = true;
			LOG.error("Nos client: cảnh nền hỏng, quay về panorama gốc", e);
			return false;
		} finally {
			gls.restore();
		}
	}

	// ---------------- khởi tạo ----------------
	private static final class Prog {
		final int id;
		final Map<String, Integer> loc = new HashMap<>();

		Prog(String vs, String fs) {
			id = glCreateProgram();
			int v = sh(GL_VERTEX_SHADER, vs), f = sh(GL_FRAGMENT_SHADER, fs);
			glAttachShader(id, v);
			glAttachShader(id, f);
			glLinkProgram(id);
			if (glGetProgrami(id, GL_LINK_STATUS) == 0) throw new IllegalStateException("link: " + glGetProgramInfoLog(id));
			glDeleteShader(v);
			glDeleteShader(f);
		}

		int u(String name) {
			return loc.computeIfAbsent(name, n -> glGetUniformLocation(id, n));
		}

		private static int sh(int type, String src) {
			int s = glCreateShader(type);
			glShaderSource(s, src);
			glCompileShader(s);
			if (glGetShaderi(s, GL_COMPILE_STATUS) == 0) throw new IllegalStateException("compile: " + glGetShaderInfoLog(s));
			return s;
		}
	}

	/** Ảnh của game (theo resource pack đang bật), đọc thành ARGB. */
	private static int[][] img(ResourceManager rm, String path) throws IOException {
		try (InputStream in = rm.open(Identifier.withDefaultNamespace("textures/" + path + ".png")); NativeImage im = NativeImage.read(in)) {
			int[][] p = new int[im.getHeight()][im.getWidth()];
			for (int y = 0; y < p.length; y++) for (int x = 0; x < p[0].length; x++) p[y][x] = im.getPixel(x, y);
			return p;
		}
	}

	private static int tint(int argb, int rgb) {
		int a = argb >>> 24, r = (argb >> 16 & 255) * (rgb >> 16 & 255) / 255, g = (argb >> 8 & 255) * (rgb >> 8 & 255) / 255, b = (argb & 255) * (rgb & 255) / 255;
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int[][] tinted(int[][] p, int rgb) {
		for (int[] row : p) for (int x = 0; x < row.length; x++) row[x] = tint(row[x], rgb);
		return p;
	}

	private static final int GRASS = 0x91BD59, FOLIAGE = 0x77AB2F, BIRCH = 0x80A755, LILY = 0x208030, WATER = 0x3F76E4;

	private static final Map<String, String> PATH = new HashMap<>();
	static {
		String[][] m = {{"gtop", "block/grass_block_top"}, {"dirt", "block/dirt"}, {"sand", "block/sand"}, {"stone", "block/stone"}, {"snow", "block/snow"},
			{"gsnow", "block/grass_block_snow"}, {"clog", "block/cherry_log"}, {"clogt", "block/cherry_log_top"}, {"cleaf", "block/cherry_leaves"},
			{"cane", "block/sugar_cane"}, {"poppy", "block/poppy"}, {"dand", "block/dandelion"}, {"azure", "block/azure_bluet"}, {"grassp", "block/short_grass"},
			{"sap", "block/cherry_sapling"}, {"ppet", "block/pink_petals"}, {"lily", "block/lily_pad"}, {"oakl", "block/oak_log"}, {"oakt", "block/oak_log_top"},
			{"oakf", "block/oak_leaves"}, {"birl", "block/birch_log"}, {"birt", "block/birch_log_top"}, {"birf", "block/birch_leaves"}, {"tulr", "block/red_tulip"},
			{"tulo", "block/orange_tulip"}, {"tulp", "block/pink_tulip"}, {"tulw", "block/white_tulip"}, {"corn", "block/cornflower"}, {"alli", "block/allium"},
			{"daisy", "block/oxeye_daisy"}, {"lotv", "block/lily_of_the_valley"}, {"tgb", "block/tall_grass_bottom"}, {"tgt", "block/tall_grass_top"},
			{"fern", "block/fern"}, {"lilb", "block/lilac_bottom"}, {"lilt", "block/lilac_top"}, {"roseb", "block/rose_bush_bottom"}, {"roset", "block/rose_bush_top"},
			{"peob", "block/peony_bottom"}, {"peot", "block/peony_top"}, {"glint", "particle/glint"}, {"bone", "item/bone_meal"}, {"fly", "particle/firefly"}};
		for (String[] e : m) PATH.put(e[0], e[1]);
		for (int i = 0; i < 12; i++) PATH.put("p" + i, "particle/cherry_" + i);
	}

	private static final Map<String, Integer> TINT = Map.of("gtop", GRASS, "cane", GRASS, "grassp", GRASS, "tgb", GRASS, "tgt", GRASS, "fern", GRASS,
		"oakf", FOLIAGE, "birf", BIRCH, "lily", LILY);

	private int[][] tile(ResourceManager rm, String n) throws IOException {
		if (n.equals("gside")) { // cạnh khối cỏ = đất + lớp phủ cỏ đã nhuộm, như game ghép hai lớp
			int[][] base = img(rm, "block/grass_block_side"), over = tinted(img(rm, "block/grass_block_side_overlay"), GRASS);
			for (int y = 0; y < 16; y++)
				for (int x = 0; x < 16; x++) {
					int o = over[y][x], b = base[y][x], a = o >>> 24;
					int r = ((o >> 16 & 255) * a + (b >> 16 & 255) * (255 - a)) / 255, g = ((o >> 8 & 255) * a + (b >> 8 & 255) * (255 - a)) / 255,
						bl = ((o & 255) * a + (b & 255) * (255 - a)) / 255;
					base[y][x] = 0xff000000 | r << 16 | g << 8 | bl;
				}
			return base;
		}
		int[][] p = img(rm, PATH.get(n));
		Integer t = TINT.get(n);
		return t != null ? tinted(p, t) : p;
	}

	private static int upload(int[][] p, int w, int h, boolean repeat, boolean mip) {
		ByteBuffer b = MemoryUtil.memAlloc(w * h * 4);
		try {
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) {
					int c = p[y][x];
					b.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24));
				}
			b.flip();
			int t = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, t);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, b);
			int wr = repeat ? GL_REPEAT : GL_CLAMP_TO_EDGE;
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wr);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wr);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			if (mip) {
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 4);
				glGenerateMipmap(GL_TEXTURE_2D);
			}
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, mip ? GL_NEAREST_MIPMAP_LINEAR : GL_NEAREST);
			return t;
		} finally {
			MemoryUtil.memFree(b);
		}
	}

	private static int vao(FloatBuffer data, int[] attrs, int stride, int usage, int[] bufOut) {
		int v = glGenVertexArrays();
		glBindVertexArray(v);
		int b = glGenBuffers();
		glBindBuffer(GL_ARRAY_BUFFER, b);
		glBufferData(GL_ARRAY_BUFFER, data, usage);
		int off = 0;
		for (int i = 0; i < attrs.length; i++) {
			glEnableVertexAttribArray(i);
			glVertexAttribPointer(i, attrs[i], GL_FLOAT, false, stride * 4, off * 4L);
			off += attrs[i];
		}
		if (bufOut != null) bufOut[0] = b;
		return v;
	}

	private static int vao(float[] data, int[] attrs, int stride, int usage, int[] bufOut) {
		FloatBuffer fb = MemoryUtil.memAllocFloat(Math.max(1, data.length));
		try {
			fb.put(data).flip();
			if (data.length == 0) fb.limit(1);
			return vao(fb, attrs, stride, usage, bufOut);
		} finally {
			MemoryUtil.memFree(fb);
		}
	}

	private static final int[] WL = {3, 2, 1, 3, 4, 1};

	private void init() throws IOException {
		long t = System.nanoTime();
		// ảnh tải lên với pixel-store mặc định; game có thể đang để khác cho việc tải texture của nó
		int rl = glGetInteger(GL_UNPACK_ROW_LENGTH), sp = glGetInteger(GL_UNPACK_SKIP_PIXELS), sr = glGetInteger(GL_UNPACK_SKIP_ROWS), al = glGetInteger(GL_UNPACK_ALIGNMENT);
		glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
		glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
		glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
		glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
		try {
			pw = new Prog(Shaders.WORLD_VS, Shaders.WORLD_FS);
			ps = new Prog(Shaders.FULL_VS, Shaders.SKY_FS);
			pp = new Prog(Shaders.SPRITE_VS, Shaders.SPRITE_FS);
			psh = new Prog(Shaders.WORLD_VS, Shaders.SHADOW_FS);
			ppo = new Prog(Shaders.FULL_VS, Shaders.POST_FS);
			pcopy = new Prog(Shaders.FULL_VS, Shaders.COPY_FS);

			ResourceManager rm = Minecraft.getInstance().getResourceManager();
			int[][] atlas = new int[256][256];
			for (int i = 0; i < World.TILES.length; i++) {
				int[][] p = tile(rm, World.TILES[i]);
				int x0 = (i % 16) * 16, y0 = (i / 16) * 16, s = World.tileSize(World.TILES[i]);
				for (int y = 0; y < s && y < p.length; y++) for (int x = 0; x < s && x < p[0].length; x++) atlas[y0 + y][x0 + x] = p[y][x];
			}
			blit(atlas, img(rm, "entity/sheep/sheep"), 0, 192); // cừu 64×32 ở dưới atlas
			blit(atlas, img(rm, "entity/sheep/sheep_wool"), 64, 192);
			txa = upload(atlas, 256, 256, false, true);
			if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) // mặt đất xa nét hơn
				glTexParameterf(GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, 8);
			int[][] water = tinted(img(rm, "block/water_still"), WATER);
			txw = upload(water, water[0].length, water.length, true, false);
			int[][] sun = img(rm, "environment/celestial/sun"), moon = img(rm, "environment/celestial/moon/full_moon");
			txs = upload(sun, sun[0].length, sun.length, false, false);
			txm = upload(moon, moon[0].length, moon.length, false, false);
			int[][] cl = img(rm, "environment/clouds");
			cloudMask = new boolean[65536];
			for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) cloudMask[y * 256 + x] = (cl[y % cl.length][x % cl[0].length] >>> 24) > 127;
		} finally {
			glPixelStorei(GL_UNPACK_ROW_LENGTH, rl);
			glPixelStorei(GL_UNPACK_SKIP_PIXELS, sp);
			glPixelStorei(GL_UNPACK_SKIP_ROWS, sr);
			glPixelStorei(GL_UNPACK_ALIGNMENT, al);
		}

		world = new World();
		World.Mesh mesh = world.build();
		nW = mesh.land().length / ST;
		nNear = mesh.nearVerts();
		nWater = mesh.water().length / ST;
		int[] b = new int[1];
		vaoW = vao(mesh.land(), WL, ST, GL_STATIC_DRAW, null);
		vaoWater = vao(mesh.water(), WL, ST, GL_STATIC_DRAW, null);
		pd = MemoryUtil.memAllocFloat(PD_VERTS * 11);
		vaoP = vao(pd, new int[]{3, 2, 2, 4}, 11, GL_DYNAMIC_DRAW, b);
		bufP = b[0];
		vaoCl = vao(new float[ST], WL, ST, GL_DYNAMIC_DRAW, b);
		bufCl = b[0];
		sheepBuf = new float[World.SHEEP_FLOATS];
		sheepFb = MemoryUtil.memAllocFloat(World.SHEEP_FLOATS);
		vaoS = vao(sheepFb, WL, ST, GL_DYNAMIC_DRAW, b);
		bufS = b[0];
		vaoEmpty = glGenVertexArrays();

		stx = depthTex(SMS, SMS, true);
		sfb = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, sfb);
		glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, stx, 0);
		glDrawBuffer(GL_NONE);
		glReadBuffer(GL_NONE);
		check("shadow");
		ready = true;
		LOG.info("Nos client cảnh nền: dựng xong {} đỉnh trong {} ms", nW, (System.nanoTime() - t) / 1_000_000);
	}

	private static void blit(int[][] dst, int[][] src, int x0, int y0) {
		for (int y = 0; y < src.length; y++) System.arraycopy(src[y], 0, dst[y0 + y], x0, src[y].length);
	}

	private static int depthTex(int w, int h, boolean cmp) {
		int t = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, t);
		glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, w, h, 0, GL_DEPTH_COMPONENT, GL_UNSIGNED_INT, (ByteBuffer) null);
		int f = cmp ? GL_LINEAR : GL_NEAREST;
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, f);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, f);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		if (cmp) {
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
		}
		return t;
	}

	private static void check(String what) {
		int s = glCheckFramebufferStatus(GL_FRAMEBUFFER);
		if (s != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("FBO " + what + " chưa đủ: 0x" + Integer.toHexString(s));
	}

	/** Ảnh phụ theo cỡ khung của game; dựng lại khi đổi cỡ cửa sổ. Khung ra gắn thẳng vào texture màu của khung chính. */
	private void targets(RenderTarget main) {
		int w = main.width, h = main.height, colorId = ((GlTexture) main.getColorTexture()).glId();
		if (w != tw || h != th) {
			if (tw > 0) {
				glDeleteFramebuffers(new int[]{fb, mfb});
				glDeleteTextures(new int[]{fbt, mtx, mdt});
				glDeleteRenderbuffers(fbDepth);
			}
			tw = w;
			th = h;
			rw = Math.max(1, w / 2);
			rh = Math.max(1, h / 2);
			fbt = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, fbt);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, rw, rh, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			fb = glGenFramebuffers();
			glBindFramebuffer(GL_FRAMEBUFFER, fb);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, fbt, 0);
			fbDepth = glGenRenderbuffers();
			glBindRenderbuffer(GL_RENDERBUFFER, fbDepth);
			glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, rw, rh);
			glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, fbDepth);
			check("reflection");
			mtx = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, mtx);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			glGenerateMipmap(GL_TEXTURE_2D);
			mdt = depthTex(w, h, false);
			mfb = glGenFramebuffers();
			glBindFramebuffer(GL_FRAMEBUFFER, mfb);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mtx, 0);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, mdt, 0);
			check("scene");
		}
		if (colorId != outTex) {
			if (outFbo == 0) outFbo = glGenFramebuffers();
			glBindFramebuffer(GL_FRAMEBUFFER, outFbo);
			glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorId, 0);
			check("out");
			outTex = colorId;
		}
	}

	// ---------------- toán ma trận (cột trước, như WebGL) ----------------
	private static float[] mul(float[] a, float[] b) {
		float[] r = new float[16];
		for (int c = 0; c < 4; c++) for (int i = 0; i < 4; i++) r[c * 4 + i] = a[i] * b[c * 4] + a[4 + i] * b[c * 4 + 1] + a[8 + i] * b[c * 4 + 2] + a[12 + i] * b[c * 4 + 3];
		return r;
	}

	private static double[] sub(double[] a, double[] b) {
		return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static double[] norm(double[] a) {
		double l = Math.sqrt(dot(a, a));
		return new double[]{a[0] / l, a[1] / l, a[2] / l};
	}

	private static double[] add(double[] a, double[] b, double k) {
		return new double[]{a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k};
	}

	private record Look(float[] view, double[] fw, double[] rt, double[] up) {}

	private static Look lookAt(double[] e, double[] t) {
		double[] fw = norm(sub(t, e)), rt = norm(cross(fw, new double[]{0, 1, 0})), up = cross(rt, fw);
		float[] v = {(float) rt[0], (float) up[0], (float) -fw[0], 0, (float) rt[1], (float) up[1], (float) -fw[1], 0, (float) rt[2], (float) up[2], (float) -fw[2], 0,
			(float) -dot(rt, e), (float) -dot(up, e), (float) dot(fw, e), 1};
		return new Look(v, fw, rt, up);
	}

	private static float[] ortho(double h, double n, double f) {
		return new float[]{(float) (1 / h), 0, 0, 0, 0, (float) (1 / h), 0, 0, 0, 0, (float) (-2 / (f - n)), 0, 0, 0, (float) (-(f + n) / (f - n)), 1};
	}

	private static final float[] I4 = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
	private static final float[] REFL = {1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, 2 * World.WY, 0, 1};

	private static double lerp(double a, double b, double k) {
		return a + (b - a) * k;
	}

	private static double clamp01(double v) {
		return Math.min(1, Math.max(0, v));
	}

	private static double[] mix3(double[] a, double[] b, double k) {
		return new double[]{lerp(a[0], b[0], k), lerp(a[1], b[1], k), lerp(a[2], b[2], k)};
	}

	private static double[] scale3(double[] a, double k) {
		return new double[]{a[0] * k, a[1] * k, a[2] * k};
	}

	private static final double[][] SKY = {{0, 110, 155, 255, 187, 211, 255, 255, 255, 255}, {50, 58, 74, 133, 244, 162, 89, 255, 212, 176},
		{75, 20, 26, 58, 58, 46, 90, 118, 120, 178}, {100, 7, 10, 24, 20, 32, 64, 72, 86, 146}};

	/** Màu trời theo giờ: [thiên đỉnh, chân trời, ánh sáng], làm tròn như preview. */
	private static double[][] skyAt(double v) {
		for (int i = 1; i < SKY.length; i++)
			if (v <= SKY[i][0]) {
				double[] a = SKY[i - 1], b = SKY[i];
				double k = (v - a[0]) / (b[0] - a[0]);
				double[][] r = new double[3][3];
				for (int c = 0; c < 3; c++) for (int j = 0; j < 3; j++) r[c][j] = Math.round(lerp(a[1 + c * 3 + j], b[1 + c * 3 + j], k)) / 255.0;
				return r;
			}
		double[] l = SKY[SKY.length - 1];
		return new double[][]{{l[1] / 255, l[2] / 255, l[3] / 255}, {l[4] / 255, l[5] / 255, l[6] / 255}, {l[7] / 255, l[8] / 255, l[9] / 255}};
	}

	private static double[] az(double a, double el) {
		double c = Math.cos(a), s = Math.sin(a), e = Math.toRadians(el);
		return new double[]{-s * Math.cos(e), Math.sin(e), -c * Math.cos(e)};
	}

	private static void u3(Prog p, String n, double[] v) {
		glUniform3f(p.u(n), (float) v[0], (float) v[1], (float) v[2]);
	}

	// ---------------- hạt: cánh hoa (12 particle/cherry_*), bột xương, hạt xanh, đom đóm ----------------
	private static final class P {
		double x, y, z, vx, vy, vz, ph, life, max, sz, land = -1;
		String tile;
		boolean pet, glow, fly;
		double[] b;
	}

	private record Ev(double at, Runnable fn) {}

	private final List<Ev> ev = new ArrayList<>();
	private final List<P> sp = new ArrayList<>();
	private double petAcc;
	private static final double[][] FLY = new double[26][];
	static {
		for (int i = 0; i < 26; i++) {
			double a = i / 26.0 * 6.283;
			FLY[i] = new double[]{World.LX + Math.cos(a) * (5 + World.hash(i, 1) * 6), .6 + World.hash(i, 2) * 2.4, World.LZ + Math.sin(a) * (4 + World.hash(i, 3) * 5),
				World.hash(i, 4) * 6.28, .4 + World.hash(i, 5) * .6};
		}
	}

	private void growFx(int group, double t) {
		if (world == null) return;
		if (group == 1) for (World.Tree tr : world.trees) {
			double[] b = {tr.x() + .5, tr.y() + .2, tr.z() + .5};
			double a = t + tr.t0();
			ev.add(new Ev(a + .4, () -> { // bột xương bay tới mầm như intro
				P p = new P();
				p.fly = true;
				p.b = b;
				p.max = .45;
				p.tile = "bone";
				sp.add(p);
			}));
			for (double k : new double[]{.85, 1.05}) ev.add(new Ev(a + k, () -> burst(b, 12)));
		}
		else for (double[] c : world.canes) ev.add(new Ev(t + c[3], () -> burst(new double[]{c[0] + .5, c[1] + .2, c[2] + .5}, 6)));
	}

	private void burst(double[] at, int n) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		for (int i = 0; i < n; i++) {
			double a = r.nextDouble() * 6.283, rr = .15 + r.nextDouble() * .5;
			P p = new P();
			p.x = at[0] + Math.cos(a) * rr;
			p.y = at[1] + r.nextDouble() * .6;
			p.z = at[2] + Math.sin(a) * rr;
			p.vy = .5 + r.nextDouble() * .5;
			p.max = .7 + r.nextDouble() * .4;
			p.tile = "glint";
			p.sz = .16;
			p.glow = true;
			sp.add(p);
		}
	}

	private void stepFx(double t, double dt) {
		List<Ev> due = new ArrayList<>();
		for (Iterator<Ev> it = ev.iterator(); it.hasNext(); ) {
			Ev e = it.next();
			if (e.at <= t) {
				it.remove();
				due.add(e);
			}
		}
		for (Ev e : due) e.fn.run(); // chạy sau vòng lặp: fn thêm hạt, không được đụng danh sách đang duyệt
		ThreadLocalRandom r = ThreadLocalRandom.current();
		if (cfg.blossom && t > fe[2] + 4) {
			petAcc += dt * 16;
			while (petAcc > 1 && sp.size() < 260) {
				petAcc--;
				World.Tree tr = world.trees.get(r.nextInt(world.trees.size()));
				int[] l = tr.leaves().get(r.nextInt(tr.leaves().size()));
				P p = new P();
				p.x = l[0] + r.nextDouble();
				p.y = l[1] + r.nextDouble() * .4;
				p.z = l[2] + r.nextDouble();
				p.vx = .25;
				p.vy = -(.5 + r.nextDouble() * .35);
				p.vz = .05;
				p.ph = r.nextDouble() * 6.28;
				p.max = 9;
				p.tile = "p" + r.nextInt(12);
				p.sz = .09;
				p.pet = true;
				sp.add(p);
			}
		}
		for (Iterator<P> it = sp.iterator(); it.hasNext(); ) {
			P p = it.next();
			p.life += dt;
			if (p.life > p.max) {
				it.remove();
				continue;
			}
			if (p.fly) continue;
			if (p.pet) {
				if (p.land < 0) {
					p.x += (p.vx + Math.sin(t * 1.7 + p.ph) * .35) * dt;
					p.z += (p.vz + Math.cos(t * 1.3 + p.ph) * .2) * dt;
					p.y += p.vy * dt;
					int cx = (int) Math.floor(p.x), cz = (int) Math.floor(p.z);
					double g = World.isW(cx, cz) ? World.WY + .01 : world.hgt(cx, cz) + .01;
					if (p.y <= g) {
						p.y = g;
						p.land = p.life;
						p.max = p.life + 4;
					}
				} else if (World.isW((int) Math.floor(p.x), (int) Math.floor(p.z))) p.x += .06 * dt;
			} else {
				p.x += p.vx * dt;
				p.y += p.vy * dt;
				p.z += p.vz * dt;
			}
		}
	}

	private int pn;

	private void put(double[] c, double s, String tile, double r, double g, double b, double a) {
		float[] u = World.uv(tile);
		float[][] q = {{-1, 1, u[0], u[1]}, {1, 1, u[2], u[1]}, {1, -1, u[2], u[3]}, {-1, 1, u[0], u[1]}, {1, -1, u[2], u[3]}, {-1, -1, u[0], u[3]}};
		for (float[] k : q) {
			if (pn >= PD_VERTS - 12) return;
			pd.put((float) c[0]).put((float) c[1]).put((float) c[2]).put((float) (k[0] * s)).put((float) (k[1] * s)).put(k[2]).put(k[3])
				.put((float) r).put((float) g).put((float) b).put((float) a);
			pn++;
		}
	}

	/** @return [số đỉnh hạt thường, số đỉnh hạt cộng màu]. */
	private int[] fillSprites(double t, float[] V, double[] cam, double[] lt, double night) {
		pd.clear();
		pn = 0;
		double[] R = {V[0], V[4], V[8]}, U = {V[1], V[5], V[9]}, F = {-V[2], -V[6], -V[10]};
		for (P p : sp) {
			if (p.fly) {
				double k = Math.min(1, p.life / p.max), e = k * k;
				double[] s = add(add(add(cam, R, 1.8), U, -1.2), F, 4), mid = {(s[0] + p.b[0]) / 2, Math.max(s[1], p.b[1]) + 1.4, (s[2] + p.b[2]) / 2}, c = new double[3];
				for (int i = 0; i < 3; i++) c[i] = (1 - e) * (1 - e) * s[i] + 2 * (1 - e) * e * mid[i] + e * e * p.b[i];
				put(c, lerp(.32, .12, k), "bone", lt[0], lt[1], lt[2], 1);
				continue;
			}
			double k = p.life / p.max, a = p.pet ? (p.land < 0 ? 1 : Math.max(0, 1 - (p.life - p.land) / 4)) : 1 - k * k;
			double[] c = {p.x, p.y, p.z};
			if (p.glow) put(c, p.sz * lerp(1.2, .5, k), p.tile, 1, 1, 1, a);
			else put(c, p.sz, p.tile, lt[0], lt[1], lt[2], a);
		}
		int nN = pn;
		if (night > .15) for (double[] f : FLY) {
			double b = Math.max(0, Math.sin(t * f[4] * 3 + f[3])) * night;
			if (b < .05) continue;
			put(new double[]{f[0] + Math.sin(t * f[4] + f[3]) * 1.2, f[1] + Math.cos(t * f[4] * .8 + f[3]) * .5, f[2] + Math.cos(t * f[4] * .6 + f[3]) * 1.2}, .09, "fly",
				.85 * b, b, .5 * b, 1);
		}
		return new int[]{nN, pn - nN};
	}

	// ---------------- một khung ----------------
	private void frame(double t) {
		RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
		if (main.width <= 0 || main.height <= 0) return; // cửa sổ thu nhỏ
		targets(main);
		boolean FX = cfg.fx;
		double dt = Math.min(.05, Math.max(0, t - lastT));
		lastT = t;
		todD += (cfg.tod - todD) * (1 - Math.exp(-dt * 2.2)); // mặt trời đuổi theo thanh trượt: kéo là thấy lặn/mọc
		double v = todD, night = Math.max(0, (v - 55) / 45);
		double[][] sk = skyAt(v);
		double[] zen0 = sk[0], hor0 = sk[1], lt = sk[2];
		double dayK = FX ? (1 - night) * (1 - Math.exp(-Math.pow((v - 47) / 16, 2))) : 0, gk = FX ? Math.exp(-Math.pow((v - 47) / 11, 2)) : 0;
		double[] zen1 = FX ? new double[]{zen0[0] * lerp(1, .5, dayK), zen0[1] * lerp(1, .68, dayK), zen0[2] * lerp(1, .97, dayK)} : zen0;
		double[] zen = mix3(zen1, new double[]{.56, .5, .74}, gk * .85);
		double[] hor1 = FX ? new double[]{hor0[0] * lerp(1, .74, dayK), hor0[1] * lerp(1, .86, dayK), hor0[2]} : hor0;
		double[] hor = mix3(hor1, new double[]{1, .7, .52}, gk * .7);

		// máy quay điện ảnh: ngang tầm mắt trên bờ hồ, trượt ngang rất chậm
		double dl = Math.sin(t * .035);
		double[] T0 = {1 + dl * 2, 6, -70}, cam = {1 + dl * 3.5, 2.6 + Math.sin(t * .05) * .1, 3.2 - Math.cos(t * .035) * .5};
		Look L = lookAt(cam, T0);
		double aspect = (double) tw / th, TY = Math.tan(FOV / 2), TX = TY * aspect, NEAR = .1, FAR = 600;
		float[] PROJ = {(float) (1 / TX), 0, 0, 0, 0, (float) (1 / TY), 0, 0, 0, 0, (float) ((FAR + NEAR) / (NEAR - FAR)), -1, 0, 0, (float) (2 * FAR * NEAR / (NEAR - FAR)), 0};
		float[] VIEW = L.view(), VP = mul(PROJ, VIEW);

		double sEl = 20 - v * .42, mEl = (v - 56) * .4;
		double[] sunD = az(-.06, sEl), moonD = az(.14, mEl);
		double glow = Math.exp(-Math.pow((v - 47) / 13, 2));
		double[] sunCol = scale3(new double[]{1, lerp(.95, .62, glow), lerp(.85, .4, glow)}, clamp01((sEl + 5) / 5));
		double[] moonCol = scale3(new double[]{.9, .92, 1}, clamp01((mEl + 3) / 5));
		double k47 = Math.min(1, v / 47), sunI = clamp01((sEl + 4.5) / 5.5), mI = clamp01((mEl + 2) / 6) * .3;
		boolean useSun = sunI >= mI;
		double[] Ld = useSun ? az(lerp(-1.45, -.06, k47 * k47), Math.max(7, sEl > 0 ? Math.min(58, sEl * 3.4) : sEl)) : az(.14, Math.max(14, mEl * 1.5));
		double[] warm = {1, lerp(.93, .62, glow), lerp(.84, .4, glow)};
		double[] sunC = useSun ? scale3(warm, 1.25 * sunI) : scale3(new double[]{.55, .65, 1}, mI);
		double[] amb = new double[3];
		for (int i = 0; i < 3; i++) amb[i] = lt[i] * (.3 + .12 * night) + lerp(lt[i], zen[i], .35) * .12;
		float[] LVP = I4;
		if (FX) {
			double[] C = {2, 4, -9};
			LVP = mul(ortho(52, 1, 300), lookAt(add(C, Ld, 140), C).view());
		}

		stepFx(t, dt);
		nSB = world.buildSheep(sheepBuf, t);
		sheepFb.clear();
		sheepFb.put(sheepBuf, 0, nSB * ST).flip();
		glBindBuffer(GL_ARRAY_BUFFER, bufS);
		glBufferSubData(GL_ARRAY_BUFFER, 0, sheepFb);
		int[] na = fillSprites(t, VIEW, cam, lt, night);
		int nN = na[0], nA = na[1];
		pd.flip();
		glBindBuffer(GL_ARRAY_BUFFER, bufP);
		glBufferSubData(GL_ARRAY_BUFFER, 0, pd);

		double drift = t * .5;
		int csh = (int) Math.floor(drift / World.CS);
		if (csh != cShift) { // mây trôi bằng ma trận dịch; qua hết một ô mới dựng lại lưới
			float[] cl = World.buildClouds(cloudMask, csh);
			nCl = cl.length / ST;
			FloatBuffer b = MemoryUtil.memAllocFloat(Math.max(1, cl.length));
			try {
				b.put(cl).flip();
				glBindBuffer(GL_ARRAY_BUFFER, bufCl);
				glBufferData(GL_ARRAY_BUFFER, b, GL_DYNAMIC_DRAW);
			} finally {
				MemoryUtil.memFree(b);
			}
			cShift = csh;
		}
		float[] TRc = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, (float) (drift - csh * World.CS), 0, 0, 1};
		double[] fogC = FX ? mix3(hor, zen, lerp(.3, .45, glow)) : hor;
		int fr = (int) (Math.floor(t * 10) % 32);

		// 0) bóng đổ: vẽ phần gần từ phía mặt trời, cùng shader đỉnh nên cây đang mọc/đung đưa cũng đổ bóng đúng
		glEnable(GL_DEPTH_TEST);
		if (FX) {
			glBindFramebuffer(GL_FRAMEBUFFER, sfb);
			glViewport(0, 0, SMS, SMS);
			glClear(GL_DEPTH_BUFFER_BIT);
			glEnable(GL_POLYGON_OFFSET_FILL);
			glPolygonOffset(2, 4);
			glUseProgram(psh.id);
			glUniformMatrix4fv(psh.u("uVP"), false, LVP);
			glUniformMatrix4fv(psh.u("uM"), false, I4);
			glUniform1f(psh.u("uT"), (float) t);
			glUniform2fv(psh.u("uF"), fe);
			glUniform1i(psh.u("uA"), 0);
			glActiveTexture(GL_TEXTURE0);
			glBindTexture(GL_TEXTURE_2D, txa);
			glBindVertexArray(vaoW);
			glDrawArrays(GL_TRIANGLES, 0, nNear);
			glBindVertexArray(vaoS);
			glDrawArrays(GL_TRIANGLES, 0, nSB);
			glDisable(GL_POLYGON_OFFSET_FILL);
		}
		glActiveTexture(GL_TEXTURE3);
		glBindTexture(GL_TEXTURE_2D, stx);
		// texture đang là đích vẽ không được nằm ở khe nào (phản chiếu ở khe 2, độ sâu hậu kỳ ở khe 1 từ khung trước)
		glActiveTexture(GL_TEXTURE2);
		glBindTexture(GL_TEXTURE_2D, 0);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D, txw);
		glActiveTexture(GL_TEXTURE0);

		final float[] LVPf = LVP;
		final int nAll = nN + nA;
		Runnable[] skyPass = new Runnable[2];
		for (int fi = 0; fi < 2; fi++) {
			final float flip = fi == 0 ? -1 : 1;
			skyPass[fi] = () -> {
				glDisable(GL_DEPTH_TEST);
				glUseProgram(ps.id);
				u3(ps, "uFw", L.fw());
				u3(ps, "uRt", L.rt());
				u3(ps, "uUp", L.up());
				glUniform2f(ps.u("uTan"), (float) TX, (float) TY);
				glUniform1f(ps.u("uFlip"), flip);
				u3(ps, "uZen", zen);
				u3(ps, "uHor", hor);
				u3(ps, "uSun", sunD);
				u3(ps, "uGlow", FX ? scale3(warm, (.05 + .75 * glow) * sunI) : new double[]{glow * .75, glow * .36, glow * .12});
				glUniform1f(ps.u("uNight"), (float) night);
				glUniform1f(ps.u("uT"), (float) t);
				glBindVertexArray(vaoEmpty);
				glDrawArrays(GL_TRIANGLES, 0, 3);
				// mặt trời/trăng: sun.png/full_moon.png không có alpha → cộng màu như game, đen tự biến mất
				glUseProgram(pp.id);
				glUniformMatrix4fv(pp.u("uV"), false, VIEW);
				glUniformMatrix4fv(pp.u("uP"), false, PROJ);
				glUniform1i(pp.u("uA"), 0);
				glEnable(GL_BLEND);
				glBlendFunc(GL_ONE, GL_ONE);
				glBindVertexArray(vaoP);
				glBindBuffer(GL_ARRAY_BUFFER, bufP);
				Object[][] bodies = {{sunD, sunCol, txs, lerp(18, 24, glow)}, {moonD, moonCol, txm, 15.0}};
				for (Object[] bd : bodies) {
					double[] d = (double[]) bd[0], c = (double[]) bd[1];
					if (c[0] <= 0) continue;
					double s = (double) bd[3];
					double[] p = add(cam, new double[]{d[0], d[1] * flip, d[2]}, 300);
					glBindTexture(GL_TEXTURE_2D, (int) bd[2]);
					float[] a = new float[66];
					float[][] q = {{-1, 1, 0, 0}, {1, 1, 1, 0}, {1, -1, 1, 1}, {-1, 1, 0, 0}, {1, -1, 1, 1}, {-1, -1, 0, 1}};
					for (int i = 0; i < 6; i++) {
						float[] k = q[i];
						System.arraycopy(new float[]{(float) p[0], (float) p[1], (float) p[2], (float) (k[0] * s), (float) (k[1] * s), k[2], k[3], (float) c[0], (float) c[1], (float) c[2], 1},
							0, a, i * 11, 11);
					}
					glBufferSubData(GL_ARRAY_BUFFER, nAll * 44L, a);
					glDrawArrays(GL_TRIANGLES, nAll, 6);
				}
				glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
				glDisable(GL_BLEND);
				glEnable(GL_DEPTH_TEST);
			};
		}
		java.util.function.BiConsumer<float[], Boolean> worldPass = (M, clip) -> {
			glUseProgram(pw.id);
			glUniformMatrix4fv(pw.u("uVP"), false, VP);
			glUniform1f(pw.u("uFx"), FX ? 1 : 0);
			glUniformMatrix4fv(pw.u("uLVP"), false, LVPf);
			u3(pw, "uLd", Ld);
			u3(pw, "uSunC", sunC);
			u3(pw, "uAmb", amb);
			glUniform3f(pw.u("uCamO"), (float) cam[0], (float) (clip ? 2 * World.WY - cam[1] : cam[1]), (float) cam[2]);
			glUniform1i(pw.u("uSM"), 3);
			glUniformMatrix4fv(pw.u("uM"), false, M);
			glUniform1f(pw.u("uT"), (float) t);
			glUniform2fv(pw.u("uF"), fe);
			u3(pw, "uL", lt);
			u3(pw, "uFog", fogC);
			u3(pw, "uCam", cam);
			glUniform2f(pw.u("uFr"), FX ? 70 : 40, FX ? 340 : 250);
			glUniform1f(pw.u("uClip"), clip ? 1 : 0);
			glUniform1f(pw.u("uMode"), 0);
			glUniform1i(pw.u("uA"), 0);
			glUniform1i(pw.u("uWt"), 1);
			glUniform1i(pw.u("uR"), 2);
			glActiveTexture(GL_TEXTURE0);
			glBindTexture(GL_TEXTURE_2D, txa);
			glBindVertexArray(vaoW);
			glDrawArrays(GL_TRIANGLES, 0, nW);
			glBindVertexArray(vaoS);
			glDrawArrays(GL_TRIANGLES, 0, nSB);
			glUniform1f(pw.u("uMode"), 2);
			glUniformMatrix4fv(pw.u("uM"), false, mul(M, TRc));
			glBindVertexArray(vaoCl);
			glDrawArrays(GL_TRIANGLES, 0, nCl);
			glUniform1f(pw.u("uMode"), 0);
			glUniformMatrix4fv(pw.u("uM"), false, M);
		};

		// 1) phản chiếu: cả cảnh lật qua mặt nước, bỏ phần dưới nước
		glBindFramebuffer(GL_FRAMEBUFFER, fb);
		glViewport(0, 0, rw, rh);
		glClear(GL_DEPTH_BUFFER_BIT);
		skyPass[0].run();
		worldPass.accept(REFL, true);
		// 2) cảnh chính vào ảnh phụ
		glBindFramebuffer(GL_FRAMEBUFFER, mfb);
		glViewport(0, 0, tw, th);
		glClear(GL_DEPTH_BUFFER_BIT);
		skyPass[1].run();
		worldPass.accept(I4, false);
		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glDepthMask(false);
		glUniform1f(pw.u("uMode"), 1);
		glUniform1f(pw.u("uFrame"), fr);
		glUniform2f(pw.u("uRes"), tw, th);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D, txw);
		glActiveTexture(GL_TEXTURE2);
		glBindTexture(GL_TEXTURE_2D, fbt);
		glActiveTexture(GL_TEXTURE0);
		glBindVertexArray(vaoWater);
		glDrawArrays(GL_TRIANGLES, 0, nWater);
		glUseProgram(pp.id);
		glBindTexture(GL_TEXTURE_2D, txa);
		glBindVertexArray(vaoP);
		glDrawArrays(GL_TRIANGLES, 0, nN);
		if (nA > 0) {
			glBlendFunc(GL_SRC_ALPHA, GL_ONE);
			glDrawArrays(GL_TRIANGLES, nN, nA);
			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		}
		glDepthMask(true);
		glDisable(GL_BLEND);

		// 3) ra khung chính của game: hậu kỳ nếu bật shader, không thì chép thẳng
		glBindFramebuffer(GL_FRAMEBUFFER, outFbo);
		glViewport(0, 0, tw, th);
		glDisable(GL_DEPTH_TEST);
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, mtx);
		glBindVertexArray(vaoEmpty);
		if (FX) {
			glGenerateMipmap(GL_TEXTURE_2D);
			glActiveTexture(GL_TEXTURE1);
			glBindTexture(GL_TEXTURE_2D, mdt);
			glUseProgram(ppo.id);
			glUniform1i(ppo.u("uC"), 0);
			glUniform1i(ppo.u("uD"), 1);
			double[] spos = add(cam, sunD, 300);
			double[] q = new double[4];
			for (int i = 0; i < 4; i++) q[i] = VP[i] * spos[0] + VP[4 + i] * spos[1] + VP[8 + i] * spos[2] + VP[12 + i];
			double sx = q[0] / q[3] * .5 + .5, sy = q[1] / q[3] * .5 + .5;
			double edge = clamp01(1.4 - Math.max(Math.abs(sx - .5), Math.abs(sy - .5)) * 2);
			glUniform2f(ppo.u("uSun"), (float) sx, (float) sy);
			glUniform1f(ppo.u("uSunV"), q[3] > 0 ? (float) (edge * sunI * (.45 + .55 * glow)) : 0);
			u3(ppo, "uRay", warm);
			glUniform1f(ppo.u("uExp"), (float) (lerp(1.55, 2.1, glow) * lerp(1, 1.5, night)));
			glDrawArrays(GL_TRIANGLES, 0, 3);
			glActiveTexture(GL_TEXTURE0);
		} else {
			glUseProgram(pcopy.id);
			glUniform1i(pcopy.u("uC"), 0);
			glDrawArrays(GL_TRIANGLES, 0, 3);
		}
	}
}
