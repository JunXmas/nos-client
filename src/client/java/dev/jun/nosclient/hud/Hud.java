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
 * HUD theo preview: viên kính mica, chấm màu phát sáng, số sáng dùng font Minecraft (giữ chất game như preview .mc),
 * FPS có đường sparkline + 1% low, la bàn dạng dải có vạch và kim, keystrokes sáng tím khi bấm.
 * Mỗi khung chỉ đọc số và vẽ; ngày đọc mỗi 2 giây, 1% low sắp xếp mỗi 0,5 giây theo spec.
 */
public final class Hud {
	public static final Hud INSTANCE = new Hud();
	public final Cps left = new Cps(), right = new Cps();
	private final SceneConfig c = SceneConfig.INSTANCE;

	private static final int TEXT = 0xFFEEF1F7, DIM = 0x99EEF1F7;
	private static final int GREEN = 0xFF5AC54F, CYAN = 0xFF3ECFD5, GOLD = 0xFFF5C451, RED = 0xFFF2555A, ACC = 0xFFD3A8F5;

	private final long[] ft = new long[240];
	private int fi, fn;
	private long lastFrame, lastLow, lastSpark;
	private int low;
	/** FPS mỗi 1/8 giây cho sparkline. */
	private final int[] spark = new int[24];
	private int sp;
	private long lastDay;
	private String dayNum = "", dayTime = "";
	private final float[] keyGlow = new float[7];

	private Hud() {}

	public void render(GuiGraphics g, DeltaTracker dt) {
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
		Font f = mc.font;
		int x = 6, y = 6;
		if (c.fps) y = fps(g, f, x, y, mc.getFps(), low(now)) + 4;
		if (c.coords) {
			BlockPos b = p.blockPosition();
			String biome = mc.level.getBiome(b).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
			y = coords(g, f, x, y, b, Component.translatable("biome.minecraft." + biome)) + 4;
		}
		if (c.day) {
			if (now - lastDay > 2_000_000_000L || dayNum.isEmpty()) {
				long t = mc.level.getDayTime();
				int h = (int) ((t / 1000 + 6) % 24), m = (int) (t % 1000 * 60 / 1000);
				dayNum = String.valueOf(t / 24000 + 1);
				dayTime = String.format("%02d:%02d", h, m);
				lastDay = now;
			}
			y = pill(g, f, x, y, GOLD, Component.translatable("nosclient.hud.day.label").getString(), dayNum, dayTime) + 4;
		}
		if (c.cps) y = pill(g, f, x, y, RED, null, left.count(now) + " | " + right.count(now), "CPS") + 4;
		if (c.ping && mc.getConnection() != null) {
			PlayerInfo pi = mc.getConnection().getPlayerInfo(p.getUUID());
			y = ping(g, f, x, y, pi == null ? 0 : pi.getLatency()) + 4;
		}
		if (c.keys) keys(g, f, mc, x, g.guiHeight() - 96, now, d);
		if (c.direction) compass(g, f, p, g.guiWidth() / 2);
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

	// ---------- viên kính ----------
	private static void glass(GuiGraphics g, int x, int y, int w, int h) {
		Ui.round(g, x - 1, y, x + w + 1, y + h + 2, 7, 0x22000000); // bóng mềm
		Ui.round(g, x, y, x + w, y + h, 6, 0x9E141821);
		Ui.round(g, x, y, x + w, y + h / 2, 6, 0x0CFFFFFF);
		Ui.outline(g, x, y, x + w, y + h, 6, 0x1CFFFFFF);
		g.fill(x + 6, y, x + w - 6, y + 1, 0x33FFFFFF);
	}

	/** Chấm màu có quầng sáng như .dot của preview. */
	private static void dot(GuiGraphics g, int x, int y, int col) {
		Ui.round(g, x - 2, y - 2, x + 6, y + 6, 4, Ui.alpha(col, .25f));
		Ui.round(g, x, y, x + 4, y + 4, 2, col);
	}

	private static int pill(GuiGraphics g, Font f, int x, int y, int col, String label, String val, String extra) {
		int w = 18 + (label != null ? f.width(label) + 4 : 0) + f.width(val) + (extra != null ? 5 + f.width(extra) : 0) + 8, h = 17;
		glass(g, x, y, w, h);
		dot(g, x + 7, y + 7, col);
		int tx = x + 16;
		if (label != null) {
			g.drawString(f, label, tx, y + 5, DIM, false);
			tx += f.width(label) + 4;
		}
		g.drawString(f, val, tx, y + 5, TEXT, true);
		if (extra != null) g.drawString(f, extra, tx + f.width(val) + 5, y + 5, DIM, false);
		return y + h;
	}

	private int fps(GuiGraphics g, Font f, int x, int y, int fps, int low) {
		String v = String.valueOf(fps), lo = "1% " + low;
		int sw = 30, w = 16 + f.width(v) + 4 + f.width("FPS") + 6 + sw + 6 + f.width(lo) + 8, h = 17;
		glass(g, x, y, w, h);
		dot(g, x + 7, y + 7, GREEN);
		int tx = x + 16;
		g.drawString(f, v, tx, y + 5, TEXT, true);
		tx += f.width(v) + 4;
		g.drawString(f, "FPS", tx, y + 5, DIM, false);
		tx += f.width("FPS") + 6;
		// sparkline: 24 mẫu gần nhất, chuẩn hoá theo min/max
		int mn = Integer.MAX_VALUE, mxv = 0;
		for (int s : spark) {
			mn = Math.min(mn, s);
			mxv = Math.max(mxv, s);
		}
		int prev = -1;
		for (int i = 0; i < spark.length; i++) {
			int s = spark[(sp + i) % spark.length], yy = y + 12 - Math.round((s - mn) / (float) Math.max(1, mxv - mn) * 7);
			int xx = tx + Math.round(i * (sw - 1) / (float) (spark.length - 1));
			if (prev >= 0) g.fill(xx - 1, Math.min(prev, yy), xx, Math.max(prev, yy) + 1, 0xFF7FD873);
			prev = yy;
		}
		tx += sw + 6;
		g.drawString(f, lo, tx, y + 5, DIM, false);
		return y + h;
	}

	private static int coords(GuiGraphics g, Font f, int x, int y, BlockPos b, Component biome) {
		String xs = String.valueOf(b.getX()), ys = String.valueOf(b.getY()), zs = String.valueOf(b.getZ());
		int top = 16 + f.width("XYZ") + 5 + f.width(xs) + 5 + f.width(ys) + 5 + f.width(zs) + 8;
		int w = Math.max(top, 16 + f.width(biome) + 8), h = 27;
		glass(g, x, y, w, h);
		dot(g, x + 7, y + 7, CYAN);
		int tx = x + 16;
		g.drawString(f, "XYZ", tx, y + 5, DIM, false);
		tx += f.width("XYZ") + 5;
		for (String s : new String[]{xs, ys, zs}) {
			g.drawString(f, s, tx, y + 5, TEXT, true);
			tx += f.width(s) + 5;
		}
		g.drawString(f, biome, x + 16, y + 16, DIM, false);
		return y + h;
	}

	private static int ping(GuiGraphics g, Font f, int x, int y, int ms) {
		int col = ms < 80 ? GREEN : ms < 160 ? GOLD : RED, bars = ms < 80 ? 4 : ms < 160 ? 3 : ms < 300 ? 2 : 1;
		String v = String.valueOf(ms);
		int w = 8 + 13 + 5 + f.width(v) + 3 + f.width("ms") + 8, h = 17;
		glass(g, x, y, w, h);
		for (int i = 0; i < 4; i++) g.fill(x + 8 + i * 3, y + 12 - (i + 1) * 2, x + 10 + i * 3, y + 13, i < bars ? col : 0x40FFFFFF);
		int tx = x + 8 + 13 + 5;
		g.drawString(f, v, tx, y + 5, TEXT, true);
		g.drawString(f, "ms", tx + f.width(v) + 3, y + 5, DIM, false);
		return y + h;
	}

	private static final String[] DIRS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	/** La bàn dạng dải: vạch mỗi 15°, chữ mỗi 45°, kim sáng ở giữa mờ dần xuống; tên hướng + độ trong viên bên dưới. */
	private static void compass(GuiGraphics g, Font f, LocalPlayer p, int cx) {
		float yaw = ((p.getYRot() % 360) + 360 + 180) % 360;
		int w = 200, x0 = cx - w / 2, y = 5, h = 24;
		glass(g, x0, y, w, h);
		g.enableScissor(x0 + 3, y, x0 + w - 3, y + h);
		float pxPerDeg = 1.7f;
		for (int d = -180; d <= 540; d += 15) {
			float dx = (d - yaw) * pxPerDeg;
			if (Math.abs(dx) > w / 2f) continue;
			int xx = Math.round(cx + dx);
			float fade = 1 - Math.abs(dx) / (w / 2f); // hai đầu dải mờ như mask của preview
			boolean major = d % 45 == 0;
			g.fill(xx, y + h - (major ? 7 : 4), xx + 1, y + h - 3, Ui.alpha(major ? 0x99FFFFFF : 0x59FFFFFF, fade));
			if (major) {
				int di = ((d / 45) % 8 + 8) % 8;
				String s = Component.translatable("nosclient.dir." + DIRS[di]).getString();
				g.drawString(f, s, xx - f.width(s) / 2 + 1, y + 5, Ui.alpha(di == 0 ? ACC : TEXT, Math.max(.15f, fade)), di == 0);
			}
		}
		g.disableScissor();
		g.fillGradient(cx, y + 1, cx + 1, y + h - 1, 0xFFFFFFFF, 0x00FFFFFF);
		int di = Math.round(yaw / 45) % 8;
		String name = Component.translatable("nosclient.dir.full." + DIRS[di]).getString(), deg = Math.round(yaw) % 360 + "°";
		int bw = f.width(name) + 5 + f.width(deg) + 14, bx = cx - bw / 2, by = y + h + 4;
		glass(g, bx, by, bw, 15);
		g.drawString(f, name, bx + 7, by + 4, TEXT, true);
		g.drawString(f, deg, bx + 7 + f.width(name) + 5, by + 4, DIM, false);
	}

	private void keys(GuiGraphics g, Font f, Minecraft mc, int x, int y, long now, float dt) {
		var o = mc.options;
		int s = 22, gap = 3;
		key(g, f, 0, x + s + gap, y, s, s, "W", null, o.keyUp, dt);
		key(g, f, 1, x, y + s + gap, s, s, "A", null, o.keyLeft, dt);
		key(g, f, 2, x + s + gap, y + s + gap, s, s, "S", null, o.keyDown, dt);
		key(g, f, 3, x + 2 * (s + gap), y + s + gap, s, s, "D", null, o.keyRight, dt);
		int full = 3 * s + 2 * gap, mw = (full - gap) / 2, my = y + 2 * (s + gap);
		key(g, f, 4, x, my, mw, s, "LMB", left.count(now) + " CPS", o.keyAttack, dt);
		key(g, f, 5, x + mw + gap, my, mw, s, "RMB", right.count(now) + " CPS", o.keyUse, dt);
		key(g, f, 6, x, my + s + gap, full, 11, "", null, o.keyJump, dt);
	}

	private void key(GuiGraphics g, Font f, int i, int x, int y, int w, int h, String label, String sub, KeyMapping k, float dt) {
		keyGlow[i] = Ui.approach(keyGlow[i], k.isDown() ? 1 : 0, dt, 22);
		float e = keyGlow[i];
		if (e > .02f) Ui.round(g, x - 2, y - 2, x + w + 2, y + h + 2, 7, Ui.alpha(0x59B06EE6, e));
		Ui.round(g, x, y, x + w, y + h, 5, Ui.lerpCol(0x9E141821, 0xF2C79AF0, e));
		Ui.outline(g, x, y, x + w, y + h, 5, Ui.lerpCol(0x1CFFFFFF, 0x66FFFFFF, e));
		int col = Ui.lerpCol(TEXT, 0xFF2A1640, e);
		if (label.isEmpty()) g.fill(x + w / 2 - 10, y + h / 2, x + w / 2 + 10, y + h / 2 + 1, Ui.lerpCol(DIM, 0xFF2A1640, e));
		else if (sub == null) g.drawString(f, label, x + (w - f.width(label)) / 2 + 1, y + (h - 7) / 2, col, e < .5f);
		else {
			g.drawString(f, label, x + (w - f.width(label)) / 2 + 1, y + 3, col, e < .5f);
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2f, y + 13);
			g.pose().scale(.75f, .75f);
			g.drawString(f, sub, -f.width(sub) / 2, 0, Ui.lerpCol(DIM, 0xCC2A1640, e), false);
			g.pose().popMatrix();
		}
	}
}
