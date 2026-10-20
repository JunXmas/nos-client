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
		int x = 6, y = 6;
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
		if (c.keys) y = keys(g, f, mc, x, y + GAP, now, d) + GAP;
		if (c.sprint && dev.jun.nosclient.feat.Feats.sprintOn) y = row(g, f, x, y, null, Component.translatable("nosclient.hud.sprint").getString(), null) + GAP;
		// cột phải bắt đầu ở 30% chiều cao: góc phải trên là chỗ toast (thành tựu, hướng dẫn) của game
		int ry = Math.round(g.guiHeight() / (float) c.hudScale * .3f);
		if (c.effects) ry = effects(g, f, p, W - 6, ry) + GAP * 2;
		if (c.armor) ry = armor(g, f, p, W - 6, ry);
		if (c.invhud) invhud(g, mc, p, W);
		if (c.direction) {
			int boss = ((dev.jun.nosclient.mixin.BossHealthOverlayAccessor) mc.gui.getBossOverlay()).nosclient$events().size();
			compass(g, f, p, W / 2, boss > 0 ? 6 + boss * 19 : 6);
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

	/** Bề rộng chữ ở cỡ TS. */
	private static int sw(Font f, String s) {
		return Math.round(f.width(s) * TS);
	}

	private static void txt(GuiGraphics g, Font f, String s, float x, float y, int col) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(TS, TS);
		g.drawString(f, s, 0, 0, col, false);
		g.pose().popMatrix();
	}

	private int fps(GuiGraphics g, Font f, int x, int y, int fps, int low) {
		String v = String.valueOf(fps), lo = "1% " + low;
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
	private static void compass(GuiGraphics g, Font f, LocalPlayer p, int cx, int y) {
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
		g.fill(cx, y, cx + 1, y + h, T(0xFFFFFFFF));
		String deg = Math.round(yaw) % 360 + "°";
		int bw = sw(f, deg) + 2 * PAD;
		bg(g, cx - bw / 2, y + h + GAP, bw, 15);
		txt(g, f, deg, cx - sw(f, deg) / 2f + .5f, y + h + GAP + (15 - 8 * TS) / 2, T(TEXT));
	}

	private int keys(GuiGraphics g, Font f, Minecraft mc, int x, int y, long now, float dt) {
		var o = mc.options;
		int s = 22, gap = 4;
		key(g, f, 0, x + s + gap, y, s, s, "W", o.keyUp, dt);
		key(g, f, 1, x, y + s + gap, s, s, "A", o.keyLeft, dt);
		key(g, f, 2, x + s + gap, y + s + gap, s, s, "S", o.keyDown, dt);
		key(g, f, 3, x + 2 * (s + gap), y + s + gap, s, s, "D", o.keyRight, dt);
		int full = 3 * s + 2 * gap, mw = (full - gap) / 2, my = y + 2 * (s + gap);
		key(g, f, 4, x, my, mw, s, "LMB " + left.count(now), o.keyAttack, dt);
		key(g, f, 5, x + mw + gap, my, mw, s, "RMB " + right.count(now), o.keyUse, dt);
		key(g, f, 6, x, my + s + gap, full, 10, "", o.keyJump, dt);
		return my + s + gap + 10;
	}

	// ---------- hiệu ứng thuốc ----------
	/** Mỗi hiệu ứng một ô canh phải: icon, tên + cấp, thời gian còn. Sắp hết (dưới 10 giây) thì nhấp nháy. */
	private int effects(GuiGraphics g, Font f, LocalPlayer p, int xr, int y) {
		for (var e : p.getActiveEffects()) {
			if (!e.showIcon()) continue;
			String name = e.getEffect().value().getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "");
			String time = e.isInfiniteDuration() ? "∞" : mmss(e.getDuration() / 20);
			int w = PAD + 14 + 5 + sw(f, name) + 6 + sw(f, time) + PAD, h = H + 2, x = xr - w;
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
			if (it.isEmpty() || !it.isDamageableItem() && i < 4) continue;
			if (!it.isDamageableItem()) continue;
			int left = it.getMaxDamage() - it.getDamageValue(), k = System.identityHashCode(it.getItem()) * 31 + left;
			if (armorKey[i] != k || armorText[i] == null) {
				armorKey[i] = k;
				armorText[i] = String.valueOf(left);
			}
			float frac = left / (float) Math.max(1, it.getMaxDamage());
			int col = frac > .5f ? 0xFF7FD873 : frac > .2f ? 0xFFF5C451 : 0xFFF2555A;
			int w = PAD + 16 + 5 + sw(f, armorText[i]) + PAD, h = 18, x = xr - w;
			bg(g, x, y, w, h);
			g.renderItem(it, x + PAD - 1, y + 1);
			txt(g, f, armorText[i], x + PAD + 20, y + (h - 8 * TS) / 2, T(col));
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

	private void invhud(GuiGraphics g, Minecraft mc, LocalPlayer p, int W) {
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
		if (invEmpty) return; // túi trống: không vẽ ô tối vô nghĩa
		int cell = 17, cols = 9, w = cols * cell + 4, h = 3 * cell + 4, x = W / 2 - w / 2;
		int y = Math.round(g.guiHeight() / (float) c.hudScale) - 22 - 40 - h; // trên hotbar + thanh máu
		bg(g, x, y, w, h);
		for (int i = 0; i < 27; i++) {
			if (inv[i].isEmpty()) continue;
			int cx = x + 2 + (i % cols) * cell, cy = y + 2 + (i / cols) * cell;
			g.renderItem(inv[i], cx, cy);
			g.renderItemDecorations(mc.font, inv[i], cx, cy);
		}
	}

	/** Phím kiểu Lunar: ô tối trong suốt, bấm thì nền trắng chữ đen. */
	private void key(GuiGraphics g, Font f, int i, int x, int y, int w, int h, String label, KeyMapping k, float dt) {
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
