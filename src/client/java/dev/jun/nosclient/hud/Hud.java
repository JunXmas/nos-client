package dev.jun.nosclient.hud;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * HUD phẳng kiểu Lunar: mỗi mục là MỘT ô nền tối trong suốt + chữ font game, không bóng, không viền, không bóng chữ.
 * Kích thước, độ trong suốt nền và "mờ khi đứng yên" chỉnh trong cài đặt.
 * Rẻ nhất có thể: ~1 quad nền mỗi mục, chữ không vẽ bóng (bóng chữ nhân đôi số quad), biome dịch lại chỉ khi đổi.
 */
public final class Hud {
	public static final Hud INSTANCE = new Hud();
	public final Cps left = new Cps(), right = new Cps();
	private final SceneConfig c = SceneConfig.INSTANCE;

	private static final int TEXT = 0xFFFFFFFF, DIM = 0xFFAAB1C0, ACC = 0xFFD3A8F5;
	private static final int PAD = 4, H = 13, GAP = 2;

	/** Chỉ để đo: -Dnosclient.countHud=true in số phần tử GUI HUD đẩy vào mỗi 120 khung. */
	private static final boolean COUNT = Boolean.getBoolean("nosclient.countHud");
	private int counted;

	private final long[] ft = new long[240];
	private int fi, fn;
	private long lastFrame, lastLow, lastSpark, lastDay, lastBusy;
	private int low;
	private final int[] spark = new int[24];
	private int sp;
	private String dayText = "";
	private final float[] keyGlow = new float[7];
	private float fade = 1;
	/** Độ đục chữ (mờ khi đứng yên) và độ đục nền (người chơi chỉnh × mờ khi đứng yên) của khung này. */
	private static float TA = 1, BA = .45f;
	private String biomeKey = "", biomeName = "";

	private Hud() {}

	public void render(GuiGraphics g, DeltaTracker dt) {
		if (!COUNT) {
			draw(g);
			return;
		}
		int[] n = {0};
		var st = g.guiRenderState;
		st.forEachElement(e -> n[0]++, net.minecraft.client.gui.render.state.GuiRenderState.TraverseRange.ALL);
		int before = n[0];
		draw(g);
		n[0] = 0;
		st.forEachElement(e -> n[0]++, net.minecraft.client.gui.render.state.GuiRenderState.TraverseRange.ALL);
		if (++counted % 120 == 0) org.slf4j.LoggerFactory.getLogger("nosclient").info("HUD: {} phần tử GUI/khung", n[0] - before);
	}

	private void draw(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		float d = lastFrame == 0 ? 0 : Math.min(.05f, (now - lastFrame) / 1e9f);
		if (lastFrame != 0) {
			ft[fi] = now - lastFrame;
			fi = (fi + 1) % ft.length;
			if (fn < ft.length) fn++;
		}
		lastFrame = now;
		if (now - lastSpark > 125_000_000L) {
			spark[sp] = mc.getFps();
			sp = (sp + 1) % spark.length;
			lastSpark = now;
		}
		LocalPlayer p = mc.player;
		if (p == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
		if (mc.gui.getChat().isChatFocused() || mc.options.keyPlayerList.isDown()) return; // nhường chỗ cho chat / Tab
		boolean busy = p.getDeltaMovement().horizontalDistanceSqr() > 1e-4 || left.count(now) + right.count(now) > 0 || p.hurtTime > 0;
		if (busy) lastBusy = now;
		fade = Ui.approach(fade, !c.hudIdleFade || now - lastBusy < 3_000_000_000L ? 1 : .5f, d, 3);
		TA = fade;
		BA = (float) c.hudBg * fade;
		float S = (float) c.hudScale;
		g.pose().pushMatrix();
		g.pose().scale(S, S);
		try {
			drawScaled(g, mc, p, mc.font, now, d, Math.round(g.guiWidth() / S));
		} finally {
			g.pose().popMatrix();
		}
	}

	private static int T(int col) {
		return TA >= .999f ? col : Ui.alpha(col, TA);
	}

	/** Một ô nền phẳng. */
	private static void bg(GuiGraphics g, int x, int y, int w, int h) {
		if (BA > .01f) g.fill(x, y, x + w, y + h, Math.round(BA * 255) << 24);
	}

	private void drawScaled(GuiGraphics g, Minecraft mc, LocalPlayer p, Font f, long now, float d, int W) {
		int x = 4, y = 4;
		if (c.fps) y = fps(g, f, x, y, mc.getFps(), low(now)) + GAP;
		if (c.coords) {
			BlockPos b = p.blockPosition();
			String k = mc.level.getBiome(b).unwrapKey().map(r -> r.identifier().getPath()).orElse("");
			if (!k.equals(biomeKey)) {
				biomeKey = k;
				biomeName = Component.translatable("biome.minecraft." + k).getString();
			}
			y = row(g, f, x, y, "XYZ", b.getX() + " " + b.getY() + " " + b.getZ(), null) + GAP;
			y = row(g, f, x, y, null, biomeName, null) + GAP;
		}
		if (c.day) {
			if (now - lastDay > 2_000_000_000L || dayText.isEmpty()) {
				long t = mc.level.getDayTime();
				int h = (int) ((t / 1000 + 6) % 24), m = (int) (t % 1000 * 60 / 1000);
				dayText = Component.translatable("nosclient.hud.day.label").getString() + " " + (t / 24000 + 1) + "  " + (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
				lastDay = now;
			}
			y = row(g, f, x, y, null, dayText, null) + GAP;
		}
		if (c.cps) y = row(g, f, x, y, null, left.count(now) + " | " + right.count(now), "CPS") + GAP;
		if (c.ping && mc.getConnection() != null) {
			PlayerInfo pi = mc.getConnection().getPlayerInfo(p.getUUID());
			y = row(g, f, x, y, null, String.valueOf(pi == null ? 0 : pi.getLatency()), "ms") + GAP;
		}
		if (c.keys) keys(g, f, mc, x, y + 2, now, d);
		if (c.direction) {
			int boss = ((dev.jun.nosclient.mixin.BossHealthOverlayAccessor) mc.gui.getBossOverlay()).nosclient$events().size();
			compass(g, f, p, W / 2, boss > 0 ? 4 + boss * 19 : 4);
		}
	}

	private int low(long now) {
		if (now - lastLow > 500_000_000L && fn > 20) {
			long[] s = java.util.Arrays.copyOf(ft, fn);
			java.util.Arrays.sort(s);
			low = (int) Math.round(1e9 / Math.max(1, s[Math.max(0, fn - 1 - fn / 100)]));
			lastLow = now;
		}
		return low;
	}

	/** Một dòng: [nhãn mờ] giá trị trắng [đuôi mờ]. Chữ font game cao 8, ô cao 13 → chữ nằm giữa ô. */
	private static int row(GuiGraphics g, Font f, int x, int y, String label, String val, String suffix) {
		int w = PAD + (label != null ? f.width(label) + 4 : 0) + f.width(val) + (suffix != null ? 4 + f.width(suffix) : 0) + PAD;
		bg(g, x, y, w, H);
		int tx = x + PAD, ty = y + 3;
		if (label != null) {
			g.drawString(f, label, tx, ty, T(DIM), false);
			tx += f.width(label) + 4;
		}
		g.drawString(f, val, tx, ty, T(TEXT), false);
		if (suffix != null) g.drawString(f, suffix, tx + f.width(val) + 4, ty, T(DIM), false);
		return y + H;
	}

	private int fps(GuiGraphics g, Font f, int x, int y, int fps, int low) {
		String v = String.valueOf(fps), lo = "1% " + low;
		int sw = 24, w = PAD + f.width(v) + 4 + f.width("FPS") + 6 + sw + 6 + f.width(lo) + PAD;
		bg(g, x, y, w, H);
		int tx = x + PAD, ty = y + 3;
		g.drawString(f, v, tx, ty, T(TEXT), false);
		tx += f.width(v) + 4;
		g.drawString(f, "FPS", tx, ty, T(DIM), false);
		tx += f.width("FPS") + 6;
		int mn = Integer.MAX_VALUE, mx = 0; // sparkline: 24 mẫu FPS gần nhất
		for (int s : spark) {
			mn = Math.min(mn, s);
			mx = Math.max(mx, s);
		}
		int prev = -1;
		for (int i = 0; i < spark.length; i++) {
			int s = spark[(sp + i) % spark.length], yy = y + 9 - Math.round((s - mn) / (float) Math.max(1, mx - mn) * 5);
			int xx = tx + Math.round(i * (sw - 1) / (float) (spark.length - 1));
			if (prev >= 0) g.fill(xx - 1, Math.min(prev, yy), xx, Math.max(prev, yy) + 1, T(0xFF7FD873));
			prev = yy;
		}
		g.drawString(f, lo, tx + sw + 6, ty, T(DIM), false);
		return y + H;
	}

	private static final String[] DIRS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	/** La bàn dải: vạch mỗi 15°, chữ mỗi 45°, kim giữa. Không scissor (cắt bằng điều kiện) để không bẻ lô vẽ. */
	private static void compass(GuiGraphics g, Font f, LocalPlayer p, int cx, int y) {
		float yaw = ((p.getYRot() % 360) + 360 + 180) % 360;
		int w = 160, h = 14, x0 = cx - w / 2;
		bg(g, x0, y, w, h);
		for (int d = -180; d <= 540; d += 15) {
			float dx = (d - yaw) * 1.6f;
			if (Math.abs(dx) > w / 2f - 6) continue;
			int xx = Math.round(cx + dx);
			float edge = 1 - Math.abs(dx) / (w / 2f);
			if (d % 45 == 0) {
				int di = ((d / 45) % 8 + 8) % 8;
				String s = Component.translatable("nosclient.dir." + DIRS[di]).getString();
				g.drawString(f, s, xx - f.width(s) / 2 + 1, y + 3, T(Ui.alpha(di == 0 ? ACC : TEXT, Math.max(.2f, edge))), false);
			} else g.fill(xx, y + h - 4, xx + 1, y + h - 2, T(Ui.alpha(0x80FFFFFF, edge)));
		}
		g.fill(cx, y, cx + 1, y + h, T(0xFFFFFFFF));
		String deg = Math.round(yaw) % 360 + "°";
		int bw = f.width(deg) + 2 * PAD;
		bg(g, cx - bw / 2, y + h + 2, bw, 12);
		g.drawString(f, deg, cx - f.width(deg) / 2 + 1, y + h + 4, T(TEXT), false);
	}

	private void keys(GuiGraphics g, Font f, Minecraft mc, int x, int y, long now, float dt) {
		var o = mc.options;
		int s = 18, gap = 2;
		key(g, f, 0, x + s + gap, y, s, s, "W", o.keyUp, dt);
		key(g, f, 1, x, y + s + gap, s, s, "A", o.keyLeft, dt);
		key(g, f, 2, x + s + gap, y + s + gap, s, s, "S", o.keyDown, dt);
		key(g, f, 3, x + 2 * (s + gap), y + s + gap, s, s, "D", o.keyRight, dt);
		int full = 3 * s + 2 * gap, mw = (full - gap) / 2, my = y + 2 * (s + gap);
		key(g, f, 4, x, my, mw, s, "LMB " + left.count(now), o.keyAttack, dt);
		key(g, f, 5, x + mw + gap, my, mw, s, "RMB " + right.count(now), o.keyUse, dt);
		key(g, f, 6, x, my + s + gap, full, 8, "", o.keyJump, dt);
	}

	/** Phím kiểu Lunar: ô tối trong suốt, bấm thì nền trắng chữ đen. */
	private void key(GuiGraphics g, Font f, int i, int x, int y, int w, int h, String label, KeyMapping k, float dt) {
		keyGlow[i] = Ui.approach(keyGlow[i], k.isDown() ? 1 : 0, dt, 24);
		float e = keyGlow[i];
		int a = Math.round((BA + (Math.max(BA, .85f) - BA) * e) * 255);
		g.fill(x, y, x + w, y + h, a << 24 | (Ui.lerpCol(0x000000, 0xFFFFFF, e) & 0xFFFFFF));
		int col = T(Ui.lerpCol(TEXT, 0xFF000000, e));
		if (label.isEmpty()) g.fill(x + w / 2 - 8, y + h / 2, x + w / 2 + 8, y + h / 2 + 1, col);
		else g.drawString(f, label, x + (w - f.width(label)) / 2 + 1, y + (h - 7) / 2, col, false);
	}
}
