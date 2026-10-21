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
	public final Cps left = new Cps(), right = new Cps();
	private final SceneConfig c = SceneConfig.INSTANCE;

	private static final int TEXT = 0xFFFFFFFF, DIM = 0xFFAAB1C0, ACC = 0xFFD3A8F5;
	/** Ô 17 cao, chữ font game vẽ 85% (≈7 px) → 5 px thở trên/dưới; đệm ngang 7; khoảng cách ô 4. */
	private static final int PAD = 7, H = 17, GAP = 4;
	private static final float TS = .85f;

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
		fade = Ui.approach(fade, editing || !c.hudIdleFade || now - lastBusy < 3_000_000_000L ? 1 : .5f, d, 3);
		TA = fade;
		BA = (float) c.hudBg * fade;
		S = (float) c.hudScale;
		W = Math.round(g.guiWidth() / S);
		Hs = Math.round(g.guiHeight() / S);
		g.pose().pushMatrix();
		g.pose().scale(S, S);
		Ui.hint = S * TS;
		if (!editing) rects.clear();
		try {
			drawScaled(g, mc, p, mc.font, now, d, W);
		} finally {
			Ui.hint = 1;
			g.pose().popMatrix();
		}
	}

	private static int T(int col) {
		return TA >= .999f ? col : Ui.alpha(col, TA);
	}

	/** Một ô nền phẳng. */
	private static void bg(GuiGraphics g, int x, int y, int w, int h) {
		mark(x, y, x + w, y + h);
		if (BA > .01f) g.fill(x, y, x + w, y + h, Math.round(BA * 255) << 24);
	}

	// ---------- bố cục kéo thả ----------
	/** Khung (toạ độ cục bộ) của ô đang vẽ, gom từ mọi nền nó vẽ; dùng để canh neo khung sau và để bấm trúng khi sửa. */
	private static float mx0, my0, mx1, my1;

	public static void mark(float x0, float y0, float x1, float y1) {
		mx0 = Math.min(mx0, x0);
		my0 = Math.min(my0, y0);
		mx1 = Math.max(mx1, x1);
		my1 = Math.max(my1, y1);
	}

	/** Đang mở màn sửa bố cục: hiện ô giữ chỗ cho mục bật mà chưa có gì để vẽ, không mờ khi đứng yên. */
	public boolean editing;
	/** Khung khung trước của từng ô: cục bộ (để neo) và toạ độ giao diện (để màn sửa bấm trúng). */
	private final java.util.Map<String, float[]> local = new java.util.HashMap<>();
	public final java.util.Map<String, float[]> rects = new java.util.LinkedHashMap<>();
	private float S = 1;
	private int W, Hs;

	/**
	 * Vẽ một ô: chưa kéo thì ở chỗ tự xếp (fx, fy), đã kéo thì neo theo HudLayout (cỡ khung trước, nên canh phải/giữa
	 * không giật). Cỡ và độ đục riêng của ô nhân vào cỡ/độ đục chung. @return đáy ô theo dòng tự xếp (ô đã kéo trả fy).
	 */
	private int place(GuiGraphics g, String id, int fx, int fy, java.util.function.IntBinaryOperator draw) {
		var s = HudLayout.get(id);
		float k = (float) s.scale, ox, oy;
		float[] b = local.getOrDefault(id, ZERO);
		if (s.auto()) {
			ox = fx;
			oy = fy;
		} else {
			ox = (float) (s.x * W) - (s.ax == 0 ? b[0] : s.ax == 1 ? (b[0] + b[2]) / 2 : b[2]) * k;
			oy = (float) (s.y * Hs) - (s.ay == 0 ? b[1] : b[3]) * k;
		}
		float ta = TA, ba = BA;
		TA *= (float) s.alpha;
		BA *= (float) s.alpha;
		mx0 = my0 = Float.MAX_VALUE;
		mx1 = my1 = -Float.MAX_VALUE;
		int bottom;
		g.pose().pushMatrix();
		g.pose().translate(ox, oy);
		g.pose().scale(k, k);
		Ui.hint = S * k * TS;
		try {
			bottom = draw.applyAsInt(0, 0);
			if (editing && mx1 < mx0) { // bật mà chưa có gì để hiện (vd. chưa có hiệu ứng): ô giữ chỗ để kéo được
				String n = Component.translatable("nosclient.mod." + id).getString();
				bg(g, 0, 0, sw(Minecraft.getInstance().font, n) + 2 * PAD, H);
				txt(g, Minecraft.getInstance().font, n, PAD, (H - 8 * TS) / 2, T(DIM));
				bottom = H;
			}
		} finally {
			g.pose().popMatrix();
			TA = ta;
			BA = ba;
			Ui.hint = S * TS;
		}
		if (mx1 >= mx0) {
			local.put(id, new float[]{mx0, my0, mx1, my1});
			rects.put(id, new float[]{(ox + mx0 * k) * S, (oy + my0 * k) * S, (ox + mx1 * k) * S, (oy + my1 * k) * S});
		} else rects.remove(id);
		return s.auto() ? Math.round(oy + bottom * k) : fy;
	}

	private static final float[] ZERO = new float[4];

	private void drawScaled(GuiGraphics g, Minecraft mc, LocalPlayer p, Font f, long now, float d, int W) {
		int x = 6, y = 6;
		if (editing) rects.clear();
		if (c.fps) y = place(g, "fps", x, y, (xx, yy) -> fps(g, f, xx, yy, mc.getFps(), low(now))) + GAP;
		if (c.coords) {
			BlockPos b = p.blockPosition();
			String k = mc.level.getBiome(b).unwrapKey().map(r -> r.identifier().getPath()).orElse("");
			if (!k.equals(biomeKey)) {
				biomeKey = k;
				biomeName = Component.translatable("biome.minecraft." + k).getString();
			}
			String xyz = c.coordsDecimal ? String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()) : b.getX() + " " + b.getY() + " " + b.getZ();
			y = place(g, "coords", x, y, (xx, yy) -> {
				int by = row(g, f, xx, yy, "XYZ", xyz, null);
				return c.coordsBiome ? row(g, f, xx, by + GAP, null, biomeName, null) : by;
			}) + GAP;
		}
		if (c.day) {
			if (now - lastDay > 2_000_000_000L || dayText.isEmpty()) {
				long t = mc.level.getDayTime();
				int h = (int) ((t / 1000 + 6) % 24), m = (int) (t % 1000 * 60 / 1000);
				dayText = Component.translatable("nosclient.hud.day.label").getString() + " " + (t / 24000 + 1) + (c.dayTime ? "  " + (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m : "");
				lastDay = now;
			}
			y = place(g, "day", x, y, (xx, yy) -> row(g, f, xx, yy, null, dayText, null)) + GAP;
		}
		if (c.cps) y = place(g, "cps", x, y, (xx, yy) -> row(g, f, xx, yy, null, c.cpsRight ? left.count(now) + " | " + right.count(now) : String.valueOf(left.count(now)), "CPS")) + GAP;
		if (c.ping && mc.getConnection() != null) {
			PlayerInfo pi = mc.getConnection().getPlayerInfo(p.getUUID());
			y = place(g, "ping", x, y, (xx, yy) -> row(g, f, xx, yy, null, String.valueOf(pi == null ? 0 : pi.getLatency()), "ms")) + GAP;
		}
		if (c.keys) y = place(g, "keys", x, y + GAP, (xx, yy) -> keys(g, f, mc, xx, yy, now, d)) + GAP;
		if (c.sprint && c.sprintShow && (dev.jun.nosclient.feat.Feats.sprintOn || editing))
			y = place(g, "sprint", x, y, (xx, yy) -> row(g, f, xx, yy, null, Component.translatable("nosclient.hud.sprint").getString(), null)) + GAP;
		// cột phải bắt đầu ở 30% chiều cao: góc phải trên là chỗ toast (thành tựu, hướng dẫn) của game
		int ry = Math.round(Hs * .3f);
		if (c.minimap) ry = place(g, "minimap", W - 6, ry, (xx, yy) -> Minimap.render(g, mc, xx, yy, Math.max(BA, .4f)));
		if (c.effects) ry = place(g, "effects", W - 6, ry, (xx, yy) -> effects(g, f, p, xx, yy)) + GAP * 2;
		if (c.armor) ry = place(g, "armor", W - 6, ry, (xx, yy) -> armor(g, f, p, xx, yy));
		if (c.invhud) place(g, "invhud", W / 2 - INV_W / 2, Hs - 22 - 40 - INV_H, (xx, yy) -> invhud(g, mc, p, xx, yy));
		if (c.waypoint) waypoints(g, f, mc, p, W, Hs);
		if (c.blockinfo) place(g, "blockinfo", W / 2, c.direction ? 44 : 6, (xx, yy) -> blockinfo(g, f, mc, xx, yy, now));
		if (c.direction) {
			int boss = ((dev.jun.nosclient.mixin.BossHealthOverlayAccessor) mc.gui.getBossOverlay()).nosclient$events().size();
			place(g, "direction", W / 2, boss > 0 ? 6 + boss * 19 : 6, (xx, yy) -> compass(g, f, p, xx, yy));
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
		int w = PAD + sw(f, label != null ? label + " " : "") + sw(f, val) + (suffix != null ? sw(f, " " + suffix) : 0) + PAD;
		bg(g, x, y, w, H);
		float tx = x + PAD, ty = y + (H - 8 * TS) / 2;
		if (label != null) {
			txt(g, f, label, tx, ty, T(DIM));
			tx += sw(f, label + " ");
		}
		txt(g, f, val, tx, ty, T(TEXT));
		if (suffix != null) txt(g, f, suffix, tx + sw(f, val + " "), ty, T(DIM));
		return y + H;
	}

	/** Bề rộng chữ ở cỡ TS (chữ Inter nét: Ui.hud chọn oversample theo đúng tỉ lệ vẽ, nên co giãn HUD không vỡ nét). */
	private static int sw(Font f, String s) {
		return Math.round(f.width(Ui.hud(s)) * TS);
	}

	private static void txt(GuiGraphics g, Font f, String s, float x, float y, int col) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(TS, TS);
		g.drawString(f, Ui.hud(s), 0, 0, col, false);
		g.pose().popMatrix();
	}

	private int fps(GuiGraphics g, Font f, int x, int y, int fps, int low) {
		String v = String.valueOf(fps), lo = "1% " + low;
		if (!c.fpsLow) return row(g, f, x, y, null, v, "FPS");
		int spw = 22, w = PAD + sw(f, v + " FPS") + 8 + spw + 8 + sw(f, lo) + PAD;
		bg(g, x, y, w, H);
		float ty = y + (H - 8 * TS) / 2;
		int tx = x + PAD;
		txt(g, f, v, tx, ty, T(TEXT));
		txt(g, f, "FPS", tx + sw(f, v + " "), ty, T(DIM));
		tx += sw(f, v + " FPS") + 8;
		int sw = spw;
		int mn = Integer.MAX_VALUE, mx = 0; // sparkline: 24 mẫu FPS gần nhất
		for (int s : spark) {
			mn = Math.min(mn, s);
			mx = Math.max(mx, s);
		}
		int prev = -1;
		for (int i = 0; i < spark.length; i++) {
			int s = spark[(sp + i) % spark.length], yy = y + H - 6 - Math.round((s - mn) / (float) Math.max(1, mx - mn) * 5);
			int xx = tx + Math.round(i * (sw - 1) / (float) (spark.length - 1));
			if (prev >= 0) g.fill(xx - 1, Math.min(prev, yy), xx, Math.max(prev, yy) + 1, T(0xFF7FD873));
			prev = yy;
		}
		txt(g, f, lo, tx + sw + 8, ty, T(DIM));
		return y + H;
	}

	private static final String[] DIRS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	/** La bàn dải: vạch mỗi 15°, chữ mỗi 45°, kim giữa. Không scissor (cắt bằng điều kiện) để không bẻ lô vẽ. */
	private static int compass(GuiGraphics g, Font f, LocalPlayer p, int cx, int y) {
		float yaw = ((p.getYRot() % 360) + 360 + 180) % 360;
		int w = 180, h = 17, x0 = cx - w / 2;
		bg(g, x0, y, w, h);
		for (int d = -180; d <= 540; d += 15) {
			float dx = (d - yaw) * 1.6f;
			if (Math.abs(dx) > w / 2f - 6) continue;
			int xx = Math.round(cx + dx);
			float edge = 1 - Math.abs(dx) / (w / 2f);
			if (d % 45 == 0) {
				int di = ((d / 45) % 8 + 8) % 8;
				String s = Component.translatable("nosclient.dir." + DIRS[di]).getString();
				txt(g, f, s, xx - sw(f, s) / 2f + .5f, y + 4, T(Ui.alpha(di == 0 ? ACC : TEXT, Math.max(.2f, edge))));
			} else g.fill(xx, y + h - 5, xx + 1, y + h - 3, T(Ui.alpha(0x80FFFFFF, edge)));
		}
		var mcw = Minecraft.getInstance();
		if (SceneConfig.INSTANCE.waypoint) for (var wp : dev.jun.nosclient.feat.Waypoints.here(mcw)) {
			double ang = (Math.toDegrees(Math.atan2(-(wp.x() + .5 - p.getX()), wp.z() + .5 - p.getZ())) + 180 + 360) % 360;
			float dxw = (float) (((ang - yaw + 540) % 360) - 180) * 1.6f;
			if (Math.abs(dxw) > w / 2f - 4) continue;
			int xx = Math.round(cx + dxw);
			g.fill(xx - 1, y + 1, xx + 2, y + 4, T(wp.color()));
		}
		g.fill(cx, y, cx + 1, y + h, T(0xFFFFFFFF));
		if (!SceneConfig.INSTANCE.directionDeg) return y + h;
		String deg = Math.round(yaw) % 360 + "°";
		int bw = sw(f, deg) + 2 * PAD;
		bg(g, cx - bw / 2, y + h + GAP, bw, 15);
		txt(g, f, deg, cx - sw(f, deg) / 2f + .5f, y + h + GAP + (15 - 8 * TS) / 2, T(TEXT));
		return y + h + GAP + 15;
	}

	private int keys(GuiGraphics g, Font f, Minecraft mc, int x, int y, long now, float dt) {
		var o = mc.options;
		int s = 22, gap = 4;
		key(g, f, 0, x + s + gap, y, s, s, "W", o.keyUp, dt);
		key(g, f, 1, x, y + s + gap, s, s, "A", o.keyLeft, dt);
		key(g, f, 2, x + s + gap, y + s + gap, s, s, "S", o.keyDown, dt);
		key(g, f, 3, x + 2 * (s + gap), y + s + gap, s, s, "D", o.keyRight, dt);
		int full = 3 * s + 2 * gap, mw = (full - gap) / 2, my = y + 2 * (s + gap);
		if (c.keysMouse) {
			key(g, f, 4, x, my, mw, s, c.keysCps ? "LMB " + left.count(now) : "LMB", o.keyAttack, dt);
			key(g, f, 5, x + mw + gap, my, mw, s, c.keysCps ? "RMB " + right.count(now) : "RMB", o.keyUse, dt);
			my += s + gap;
		}
		if (!c.keysSpace) return my - gap;
		key(g, f, 6, x, my, full, 10, "", o.keyJump, dt);
		return my + 10;
	}

	// ---------- waypoint ----------
	/**
	 * Chiếu waypoint lên màn hình bằng chính góc camera và FOV của khung vừa vẽ: viên kim cương màu + tên + khoảng cách.
	 * Waypoint sau lưng thì ghim vào mép màn hình theo hướng của nó. Trên la bàn có chấm màu tương ứng.
	 */
	private void waypoints(GuiGraphics g, Font f, Minecraft mc, LocalPlayer p, int W, int Hs) {
		var cam = mc.gameRenderer.getMainCamera();
		var cp = cam.position();
		double yaw = Math.toRadians(cam.yRot()), pitch = Math.toRadians(cam.xRot());
		double tanY = Math.tan(Math.toRadians(dev.jun.nosclient.feat.Feats.lastFov) / 2), aspect = W / (double) Hs;
		for (var w : dev.jun.nosclient.feat.Waypoints.here(mc)) {
			double dx = w.x() + .5 - cp.x, dy = w.y() + 1.2 - cp.y, dz = w.z() + .5 - cp.z, dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			// về hệ toạ độ camera: xoay ngược yaw quanh Y rồi ngược pitch quanh X (Minecraft: yaw 0 = nhìn +Z)
			double rx = dx * Math.cos(yaw) + dz * Math.sin(yaw), rz = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
			double ry = dy * Math.cos(pitch) + rz * Math.sin(pitch), fwd = -dy * Math.sin(pitch) + rz * Math.cos(pitch);
			double sx, sy;
			boolean on = fwd > .1;
			if (on) {
				sx = W / 2.0 - rx / fwd / (tanY * aspect) * W / 2.0;
				sy = Hs / 2.0 - ry / fwd / tanY * Hs / 2.0;
				on = sx > 8 && sx < W - 8 && sy > 8 && sy < Hs - 8;
			} else {
				sx = rx > 0 ? 10 : W - 10; // sau lưng: ghim mép theo phía của nó
				sy = Hs / 2.0;
			}
			if (!on) {
				sx = Math.max(10, Math.min(W - 10, sx));
				sy = Math.max(10, Math.min(Hs - 30, sy));
			}
			int x = (int) Math.round(sx), y = (int) Math.round(sy);
			// kim cương 7 px: 4 dải ngang
			for (int k = 0; k < 4; k++) g.fill(x - k, y - 3 + k, x + k + 1, y - 2 + k, T(w.color()));
			for (int k = 0; k < 3; k++) g.fill(x - 2 + k, y + 1 + k, x + 3 - k, y + 2 + k, T(w.color()));
			String label = w.name() + "  " + Math.round(dist) + " m";
			int lw = sw(f, label) + 2 * PAD;
			bg(g, x - lw / 2, y + 6, lw, 13);
			txt(g, f, label, x - sw(f, label) / 2f, y + 6 + (13 - 8 * TS) / 2, T(TEXT));
		}
	}

	// ---------- thông tin khối & mob ----------
	/** Theo spec: đọc thứ đang nhìn mỗi 10 tick (0,5 giây), giữa hai lần chỉ vẽ lại bản đã đọc. */
	private long lastLook;
	private String lookName = "", lookSub = "";
	private net.minecraft.world.item.ItemStack lookIcon = net.minecraft.world.item.ItemStack.EMPTY, lookTool = net.minecraft.world.item.ItemStack.EMPTY;
	private boolean lookHas;
	private float lookHp = -1;

	private int blockinfo(GuiGraphics g, Font f, Minecraft mc, int cx, int y, long now) {
		if (now - lastLook > 500_000_000L) {
			lastLook = now;
			readLook(mc);
		}
		if (!lookHas) return y;
		int iw = lookIcon.isEmpty() ? 0 : 20, w = Math.max(sw(f, lookName), sw(f, lookSub) + (lookTool.isEmpty() ? 0 : 14)) + iw + 2 * PAD;
		int h = lookSub.isEmpty() && lookTool.isEmpty() ? 18 : 28, x = cx - w / 2;
		bg(g, x, y, w, h);
		if (iw > 0) g.renderItem(lookIcon, x + PAD - 1, y + 6);
		int tx = x + PAD + iw;
		txt(g, f, lookName, tx, y + 5, T(TEXT));
		txt(g, f, lookSub, tx, y + 16, T(DIM));
		if (!lookTool.isEmpty()) {
			g.pose().pushMatrix();
			g.pose().translate(tx + sw(f, lookSub) + 3, y + 14);
			g.pose().scale(.6f, .6f);
			g.renderItem(lookTool, 0, 0);
			g.pose().popMatrix();
		}
		// thanh dưới đáy: tiến độ đập khối, hoặc máu của mob
		float bar = lookHp >= 0 ? lookHp : ((dev.jun.nosclient.mixin.MultiPlayerGameModeAccessor) mc.gameMode).nosclient$progress();
		if (bar > 0) g.fill(x, y + h - 2, x + Math.round(w * Math.min(1, bar)), y + h, T(lookHp >= 0 ? 0xFFF2555A : ACC));
		return y + h;
	}

	private static final net.minecraft.world.item.ItemStack[] TOOLS = {new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE),
		new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SHOVEL),
		new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HOE)};

	private void readLook(Minecraft mc) {
		lookHas = false;
		lookHp = -1;
		var hit = mc.hitResult;
		if (hit == null || mc.level == null) return;
		if (hit instanceof net.minecraft.world.phys.EntityHitResult eh) {
			var e = eh.getEntity();
			lookName = e.getDisplayName().getString();
			lookSub = c.blockId ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString() : "";
			lookIcon = net.minecraft.world.item.ItemStack.EMPTY;
			lookTool = net.minecraft.world.item.ItemStack.EMPTY;
			if (c.blockHp && e instanceof net.minecraft.world.entity.LivingEntity le) {
				lookHp = le.getHealth() / Math.max(1, le.getMaxHealth());
				lookSub = Math.round(le.getHealth()) + "/" + Math.round(le.getMaxHealth()) + " ♥  " + lookSub;
			}
			lookHas = true;
		} else if (hit instanceof net.minecraft.world.phys.BlockHitResult bh && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			var st = mc.level.getBlockState(bh.getBlockPos());
			if (st.isAir()) return;
			lookName = st.getBlock().getName().getString();
			lookSub = c.blockId ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString() : "";
			lookIcon = new net.minecraft.world.item.ItemStack(st.getBlock().asItem());
			// công cụ đúng: theo tag mineable của game
			lookTool = !c.blockTool ? net.minecraft.world.item.ItemStack.EMPTY : st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE) ? TOOLS[0] : st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE) ? TOOLS[1]
				: st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL) ? TOOLS[2] : st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE) ? TOOLS[3]
				: net.minecraft.world.item.ItemStack.EMPTY;
			lookHas = true;
		}
	}

	// ---------- hiệu ứng thuốc ----------
	/** Mỗi hiệu ứng một ô canh phải: icon, tên + cấp, thời gian còn. Sắp hết (dưới 10 giây) thì nhấp nháy. */
	private int effects(GuiGraphics g, Font f, LocalPlayer p, int xr, int y) {
		for (var e : p.getActiveEffects()) {
			if (!e.showIcon()) continue;
			String name = e.getEffect().value().getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "");
			String time = !c.fxTime ? "" : e.isInfiniteDuration() ? "∞" : mmss(e.getDuration() / 20);
			int w = PAD + 14 + 5 + sw(f, name) + (time.isEmpty() ? 0 : 6 + sw(f, time)) + PAD, h = H + 2, x = xr - w;
			bg(g, x, y, w, h);
			float blink = !e.isInfiniteDuration() && e.endsWithin(200) ? .45f + .55f * (float) Math.abs(Math.sin(System.nanoTime() / 2.5e8)) : 1;
			g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Gui.getMobEffectSprite(e.getEffect()), x + PAD, y + 1, 14, 14, T(Ui.alpha(0xFFFFFFFF, blink)));
			float ty = y + (h - 8 * TS) / 2;
			txt(g, f, name, x + PAD + 19, ty, T(e.getEffect().value().isBeneficial() ? TEXT : 0xFFFF9A9A));
			txt(g, f, time, x + w - PAD - sw(f, time), ty, T(Ui.alpha(DIM, blink)));
			y += h + GAP;
		}
		return y;
	}

	private static String mmss(int s) {
		return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
	}

	private static String roman(int n) {
		return n <= 10 ? new String[]{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"}[n] : String.valueOf(n);
	}

	// ---------- trạng thái giáp ----------
	private static final net.minecraft.world.entity.EquipmentSlot[] ARMOR = {net.minecraft.world.entity.EquipmentSlot.HEAD,
		net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET,
		net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.OFFHAND};
	/** Theo spec: chỉ dựng lại chuỗi khi độ bền hoặc món đồ thay đổi; mỗi khung chỉ so 6 số. */
	private final int[] armorKey = new int[ARMOR.length];
	private final String[] armorText = new String[ARMOR.length];

	private int armor(GuiGraphics g, Font f, LocalPlayer p, int xr, int y) {
		for (int i = 0; i < ARMOR.length; i++) {
			var it = p.getItemBySlot(ARMOR[i]);
			if (i >= 4 && !c.armorHand) continue;
			if (it.isEmpty() || !it.isDamageableItem()) continue;
			int left = it.getMaxDamage() - it.getDamageValue(), k = (System.identityHashCode(it.getItem()) * 31 + left) * 4 + c.armorFmt;
			float frac = left / (float) Math.max(1, it.getMaxDamage());
			if (armorKey[i] != k || armorText[i] == null) {
				armorKey[i] = k;
				armorText[i] = c.armorFmt == 0 ? String.valueOf(left) : c.armorFmt == 1 ? Math.round(frac * 100) + "%" : "";
			}
			int col = frac > .5f ? 0xFF7FD873 : frac > .2f ? 0xFFF5C451 : 0xFFF2555A;
			// cảnh báo dưới 10%: chữ và vạch nhấp nháy đỏ
			boolean warn = c.armorWarn && frac < .1f;
			float bl = warn ? .4f + .6f * (float) Math.abs(Math.sin(System.nanoTime() / 2e8)) : 1;
			int w = PAD + 16 + (armorText[i].isEmpty() ? 0 : 5 + sw(f, armorText[i])) + PAD, h = 18, x = xr - w;
			bg(g, x, y, w, h);
			if (warn) g.fill(x, y, x + w, y + h, T(Ui.alpha(0x66F2555A, bl)));
			g.renderItem(it, x + PAD - 1, y + 1);
			if (!armorText[i].isEmpty()) txt(g, f, armorText[i], x + PAD + 20, y + (h - 8 * TS) / 2, T(Ui.alpha(col, bl)));
			g.fill(x, y + h - 1, x + Math.round(w * frac), y + h, T(col)); // vạch độ bền dưới đáy ô
			y += h + GAP;
		}
		return y;
	}

	// ---------- Inventory HUD ----------
	/**
	 * 27 ô túi đồ (trừ hotbar) ngay trên hotbar. Theo spec: chỉ chụp lại danh sách khi túi đồ đổi (bộ đếm thay đổi
	 * của game) hoặc khi chết; mỗi khung chỉ vẽ lại từ bản chụp.
	 */
	private final net.minecraft.world.item.ItemStack[] inv = new net.minecraft.world.item.ItemStack[27];
	private int invStamp = -1;
	private boolean invEmpty = true;

	private static final int INV_W = 9 * 17 + 4, INV_H = 3 * 17 + 4;

	private int invhud(GuiGraphics g, Minecraft mc, LocalPlayer p, int x, int y) {
		var pi = p.getInventory();
		int stamp = pi.getTimesChanged() + (p.isDeadOrDying() ? 1 << 30 : 0);
		if (stamp != invStamp) {
			invEmpty = true;
			for (int i = 0; i < 27; i++) {
				inv[i] = pi.getItem(9 + i).copy();
				invEmpty &= inv[i].isEmpty();
			}
			invStamp = stamp;
		}
		if (invEmpty) return y; // túi trống: không vẽ ô tối vô nghĩa
		int cell = 17, cols = 9, w = INV_W, h = INV_H;
		mark(x, y, x + w, y + h);
		if (c.invhudBg) bg(g, x, y, w, h);
		for (int i = 0; i < 27; i++) {
			if (inv[i].isEmpty()) continue;
			int cx = x + 2 + (i % cols) * cell, cy = y + 2 + (i / cols) * cell;
			g.renderItem(inv[i], cx, cy);
			g.renderItemDecorations(mc.font, inv[i], cx, cy);
		}
		return y + h;
	}

	/** Phím kiểu Lunar: ô tối trong suốt, bấm thì nền trắng chữ đen. */
	private void key(GuiGraphics g, Font f, int i, int x, int y, int w, int h, String label, KeyMapping k, float dt) {
		mark(x, y, x + w, y + h);
		keyGlow[i] = Ui.approach(keyGlow[i], k.isDown() ? 1 : 0, dt, 24);
		float e = keyGlow[i];
		int a = Math.round((BA + (Math.max(BA, .85f) - BA) * e) * 255);
		g.fill(x, y, x + w, y + h, a << 24 | (Ui.lerpCol(0x000000, 0xFFFFFF, e) & 0xFFFFFF));
		int col = T(Ui.lerpCol(TEXT, 0xFF000000, e));
		if (label.isEmpty()) g.fill(x + w / 2 - 10, y + h / 2, x + w / 2 + 10, y + h / 2 + 1, col);
		else txt(g, f, label, x + (w - sw(f, label)) / 2f + .5f, y + (h - 8 * TS) / 2, col);
	}

	/** Khai báo SAU mọi field static (ARMOR…): Java khởi tạo static theo thứ tự viết, tạo sớm thì constructor thấy null. */
	public static final Hud INSTANCE = new Hud();
}
