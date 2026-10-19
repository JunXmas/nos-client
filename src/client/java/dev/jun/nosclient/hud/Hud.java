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
 * HUD cơ bản theo preview: FPS (+1% low), toạ độ (+biome), ngày, la bàn, CPS, ping, keystrokes.
 * Viên thuốc mica bo góc, chấm màu và số sáng. Mỗi khung chỉ đọc số và vẽ — không cấp phát trong vòng vẽ ngoài chuỗi chữ.
 */
public final class Hud {
	public static final Hud INSTANCE = new Hud();
	public final Cps left = new Cps(), right = new Cps();
	private final SceneConfig c = SceneConfig.INSTANCE;

	private static final int BG = 0x8C141821, EDGE = 0x1FFFFFFF, TEXT = 0xFFEEF1F7, DIM = 0x99EEF1F7;
	private static final int GREEN = 0xFF5AC54F, CYAN = 0xFF3ECFD5, GOLD = 0xFFF5C451, RED = 0xFFF2555A, ACC = 0xFFD3A8F5;

	// 1% low: 240 khung gần nhất, sắp xếp mỗi 0,5 giây — không mỗi khung
	private final long[] ft = new long[240];
	private int fi, fn;
	private long lastFrame, lastLow;
	private int low;

	// "Ngày": đọc mỗi 2 giây theo spec
	private long lastDay;
	private String dayText = "";

	private Hud() {}

	public void render(GuiGraphics g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		if (lastFrame != 0) {
			ft[fi] = now - lastFrame;
			fi = (fi + 1) % ft.length;
			if (fn < ft.length) fn++;
		}
		lastFrame = now;
		LocalPlayer p = mc.player;
		if (p == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
		Font f = mc.font;
		int x = 6, y = 6;
		if (c.fps) y = pill(g, f, x, y, GREEN, mc.getFps() + "", "FPS", "1% " + low(now)) + 3;
		if (c.coords) {
			BlockPos b = p.blockPosition();
			String biome = mc.level.getBiome(b).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
			y = pill2(g, f, x, y, CYAN, b.getX() + "  " + b.getY() + "  " + b.getZ(),
				Component.translatable("biome.minecraft." + biome).getString()) + 3;
		}
		if (c.day) {
			if (now - lastDay > 2_000_000_000L || dayText.isEmpty()) {
				long t = mc.level.getDayTime();
				int h = (int) ((t / 1000 + 6) % 24), m = (int) (t % 1000 * 60 / 1000);
				dayText = (t / 24000 + 1) + "|" + String.format("%02d:%02d", h, m);
				lastDay = now;
			}
			String[] d = dayText.split("\\|");
			y = pill(g, f, x, y, GOLD, Component.translatable("nosclient.hud.day", d[0]).getString(), d[1], null) + 3; // "Ngày 8  22:53" như preview
		}
		if (c.cps) y = pill(g, f, x, y, RED, left.count(now) + " | " + right.count(now), "CPS", null) + 3;
		if (c.ping && mc.getConnection() != null) {
			PlayerInfo pi = mc.getConnection().getPlayerInfo(p.getUUID());
			int ms = pi == null ? 0 : pi.getLatency();
			y = pill(g, f, x, y, ms < 80 ? GREEN : ms < 160 ? GOLD : RED, ms + "", "ms", null) + 3;
		}
		if (c.keys) keys(g, f, mc, x, g.guiHeight() - 92, now);
		if (c.direction) compass(g, f, p, g.guiWidth() / 2);
	}

	private int low(long now) {
		if (now - lastLow > 500_000_000L && fn > 20) {
			long[] s = java.util.Arrays.copyOf(ft, fn);
			java.util.Arrays.sort(s);
			long worst = s[Math.max(0, fn - 1 - fn / 100)];
			low = (int) Math.round(1e9 / Math.max(1, worst));
			lastLow = now;
		}
		return low;
	}

	/** Viên thuốc một dòng: ● số NHÃN phụ. @return đáy viên. */
	private static int pill(GuiGraphics g, Font f, int x, int y, int col, String val, String label, String extra) {
		int w = 18 + f.width(val) + 4 + f.width(label) + (extra != null ? 6 + f.width(extra) : 0) + 7, h = 16;
		box(g, x, y, w, h);
		g.fill(x + 7, y + 6, x + 10, y + 9, col);
		int tx = x + 15;
		g.drawString(f, val, tx, y + 4, TEXT, true);
		tx += f.width(val) + 4;
		g.drawString(f, label, tx, y + 4, DIM, false);
		if (extra != null) g.drawString(f, extra, tx + f.width(label) + 6, y + 4, DIM, false);
		return y + h;
	}

	/** Viên thuốc hai dòng (toạ độ + biome). */
	private static int pill2(GuiGraphics g, Font f, int x, int y, int col, String top, String sub) {
		int w = Math.max(18 + f.width("XYZ") + 4 + f.width(top), 15 + f.width(sub)) + 8, h = 26;
		box(g, x, y, w, h);
		g.fill(x + 7, y + 6, x + 10, y + 9, col);
		g.drawString(f, "XYZ", x + 15, y + 4, DIM, false);
		g.drawString(f, top, x + 15 + f.width("XYZ") + 4, y + 4, TEXT, true);
		g.drawString(f, sub, x + 15, y + 15, DIM, false);
		return y + h;
	}

	private static void box(GuiGraphics g, int x, int y, int w, int h) {
		Ui.round(g, x, y, x + w, y + h, 5, BG);
		g.fill(x + 5, y, x + w - 5, y + 1, EDGE);
	}

	private static final String[] DIRS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	/** La bàn dạng dải: vạch mỗi 15°, chữ mỗi 45°, kim giữa; số độ bên dưới. */
	private static void compass(GuiGraphics g, Font f, LocalPlayer p, int cx) {
		float yaw = ((p.getYRot() % 360) + 360 + 180) % 360; // 0 = Bắc như F3
		int w = 180, x0 = cx - w / 2, y = 4, h = 22;
		box(g, x0, y, w, h);
		g.enableScissor(x0 + 2, y, x0 + w - 2, y + h);
		float pxPerDeg = 1.6f;
		for (int d = -180; d <= 540; d += 15) {
			float dx = (d - yaw) * pxPerDeg;
			if (Math.abs(dx) > w / 2f) continue;
			int x = Math.round(cx + dx);
			boolean major = d % 45 == 0;
			g.fill(x, y + h - (major ? 7 : 4), x + 1, y + h - 2, major ? 0x99FFFFFF : 0x55FFFFFF);
			if (major) {
				int di = ((d / 45) % 8 + 8) % 8;
				String s = Component.translatable("nosclient.dir." + DIRS[di]).getString();
				g.drawString(f, s, x - f.width(s) / 2 + 1, y + 4, di == 0 ? ACC : TEXT, true);
			}
		}
		g.disableScissor();
		g.fill(cx, y + 1, cx + 1, y + h - 1, 0xFFFFFFFF);
		String deg = Math.round(yaw) % 360 + "°";
		int dw = f.width(deg) + 10;
		box(g, cx - dw / 2, y + h + 3, dw, 13);
		g.drawString(f, deg, cx - f.width(deg) / 2, y + h + 6, TEXT, true);
	}

	private void keys(GuiGraphics g, Font f, Minecraft mc, int x, int y, long now) {
		var o = mc.options;
		int s = 20, gap = 2;
		key(g, f, x + s + gap, y, s, s, "W", o.keyUp);
		key(g, f, x, y + s + gap, s, s, "A", o.keyLeft);
		key(g, f, x + s + gap, y + s + gap, s, s, "S", o.keyDown);
		key(g, f, x + 2 * (s + gap), y + s + gap, s, s, "D", o.keyRight);
		int mw = (3 * s + 2 * gap - gap) / 2, my = y + 2 * (s + gap);
		key(g, f, x, my, mw, s, "LMB " + left.count(now), o.keyAttack);
		key(g, f, x + mw + gap, my, mw, s, "RMB " + right.count(now), o.keyUse);
		key(g, f, x, my + s + gap, 3 * s + 2 * gap, 10, "", o.keyJump);
	}

	private static void key(GuiGraphics g, Font f, int x, int y, int w, int h, String label, KeyMapping k) {
		boolean down = k.isDown();
		Ui.round(g, x, y, x + w, y + h, 4, down ? 0xD9B682E6 : BG);
		if (label.isEmpty()) g.fill(x + w / 2 - 8, y + h / 2, x + w / 2 + 8, y + h / 2 + 1, down ? 0xFFFFFFFF : DIM);
		else g.drawString(f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2, down ? 0xFFFFFFFF : TEXT, !down);
	}
}
