package dev.jun.nosclient.gui;

import dev.jun.nosclient.Mods;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.feat.Waypoints;
import dev.jun.nosclient.scene.SceneConfig;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Cài đặt Nos client (R-Shift) theo preview 07/08/17/18: cột trái (logo, tìm kiếm, danh mục, ngôn ngữ, bộ đếm),
 * "Tổng quan" gom mọi mod một chỗ, mỗi danh mục là lưới thẻ, bấm thẻ mở bảng chi tiết bên phải với tuỳ chọn riêng.
 * Vẽ kiểu immediate-mode: lúc vẽ ghi luôn vùng bấm (hits), nên bố cục và vùng bấm không bao giờ lệch nhau.
 */
public final class NosSettingsScreen extends Screen {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("nosclient", "textures/gui/logo.png");
	private static final String[] CATS = Mods.CATS;
	private static final ItemStack[] CAT_ICON = {new ItemStack(Items.COMPASS), new ItemStack(Items.PURPLE_SHULKER_BOX), new ItemStack(Items.GRASS_BLOCK),
		new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.ENDER_EYE)};
	private static final int GAP = 8;

	private final Screen parent;
	private final SceneConfig c = SceneConfig.INSTANCE;
	private final Minecraft mc = Minecraft.getInstance();
	private final long opened = System.nanoTime();
	private long last = opened;
	private float dt, t;
	private int mx, my;

	/** -1 = Tổng quan, 0..4 = Mods.CATS. */
	private int cat = -1;
	/** HUD: 0 = thẻ mod, 1 = giao diện HUD. */
	private int tab;
	/** Mod đang mở bảng chi tiết, null = đóng. */
	private String detail;
	private String query = "";
	/** Ô tìm tự vẽ bằng font của mod (EditBox của game chỉ đo con trỏ theo font mặc định). */
	private boolean searchFocus;
	private Component note;
	private float scroll, dScroll;
	private double scrollTarget, dTarget;
	private int maxScroll, dMax;
	private final Map<String, Float> anim = new HashMap<>();
	private final Fx.Runes runes = new Fx.Runes();
	private final Fx.Sparks sparks = new Fx.Sparks();

	private record Hit(int x0, int y0, int x1, int y1, Runnable run) {}

	private final List<Hit> hits = new ArrayList<>();
	/** Vùng cắt đang bật [x0,y0,x1,y1]; vùng bấm ngoài vùng cắt bị bỏ. */
	private int[] clip;
	/** Thanh trượt đang kéo: tuỳ chọn + hai mép rãnh; dragLook = thanh của khu giao diện HUD. */
	private Opt drag;
	private int dragX0, dragX1, dragLook = -1;

	public NosSettingsScreen(Screen parent) {
		super(Component.translatable("nosclient.settings.title"));
		this.parent = parent;
	}

	// ---------- bố cục ----------
	private int pw() {
		return Math.min(width - 24, 640);
	}

	private int ph() {
		return Math.min(height - 24, 380);
	}

	private int px() {
		return (width - pw()) / 2;
	}

	private int py() {
		return (height - ph()) / 2;
	}

	private int side() {
		return pw() >= 560 ? 140 : 116;
	}

	/** Bảng chi tiết: 184 px bên phải; màn hẹp thì phủ hết phần phải. */
	private int dw() {
		return pw() - side() >= 440 ? 184 : pw() - side();
	}

	private void setQuery(String q) {
		query = q.length() > 40 ? q.substring(0, 40) : q;
		scroll = 0;
		scrollTarget = 0;
	}

	// ---------- tiện ích vẽ ----------
	private boolean hov(int x0, int y0, int x1, int y1) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1 && (clip == null || mx >= clip[0] && mx < clip[2] && my >= clip[1] && my < clip[3]);
	}

	private void hit(int x0, int y0, int x1, int y1, Runnable r) {
		if (clip != null) {
			x0 = Math.max(x0, clip[0]);
			y0 = Math.max(y0, clip[1]);
			x1 = Math.min(x1, clip[2]);
			y1 = Math.min(y1, clip[3]);
		}
		if (x0 < x1 && y0 < y1) hits.add(new Hit(x0, y0, x1, y1, r));
	}

	private void clipOn(GuiGraphics g, int x0, int y0, int x1, int y1) {
		clip = new int[]{x0, y0, x1, y1};
		g.enableScissor(x0, y0, x1, y1);
	}

	private void clipOff(GuiGraphics g) {
		clip = null;
		g.disableScissor();
	}

	/** Công tắc mượt theo khoá (field config). */
	private float k(String key, boolean on) {
		float v = Ui.approach(anim.getOrDefault(key, on ? 1f : 0f), on ? 1 : 0, dt, 14);
		anim.put(key, v);
		return Ui.ease(v);
	}

	private void icon(GuiGraphics g, ItemStack s, int cx, int cy, float scale) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		g.renderFakeItem(s, -8, -8);
		g.pose().popMatrix();
	}

	private void click() {
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
	}

	private static Component tr(String key) {
		return Component.translatable(key);
	}

	private static Component name(Mods.Mod m) {
		return tr("nosclient.mod." + m.id());
	}

	private static boolean on(Mods.Mod m) {
		return m.get().get();
	}

	private void flip(Mods.Mod m) {
		boolean v = !on(m);
		m.set().accept(v);
		c.save();
		// bật = phù phép: tia lấp lánh bung ở chỗ bấm + tiếng ngân thạch anh
		if (v) {
			sparks.burst(mx, my, 12, 0xFFE2C6FF);
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, .9f));
		}
	}

	private int lines(GuiGraphics g, Component text, int x, int y, int w, int col) {
		List<FormattedCharSequence> ls = font.split(text, w);
		for (int i = 0; i < ls.size(); i++) g.drawString(font, ls.get(i), x, y + i * 10, col, false);
		return ls.size() * 10;
	}

	/** Nút phẳng có chữ; trả bề rộng. */
	private int button(GuiGraphics g, Component label, int x, int y, int h, boolean active, Runnable r) {
		int w = Ui.width(label) + 16;
		boolean hv = active && hov(x, y, x + w, y + h);
		g.fill(x, y, x + w, y + h, !active ? 0x0AFFFFFF : hv ? 0x335E3A8A : 0x14FFFFFF);
		Ui.frame(g, x, y, x + w, y + h, hv ? 0x59D3A8F5 : 0x1FFFFFFF);
		Ui.text(g, label, x + 8, y + (h - 7) / 2, active ? Ui.TEXT : Ui.FAINT);
		if (active) hit(x, y, x + w, y + h, r);
		return w;
	}

	// ---------- tìm kiếm: bỏ dấu để gõ "toa do" vẫn ra "Toạ độ" ----------
	static String fold(String s) {
		return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd');
	}

	private boolean matches(Mods.Mod m, String q) {
		StringBuilder b = new StringBuilder(I18n.get("nosclient.mod." + m.id())).append(' ').append(I18n.get("nosclient.mod." + m.id() + ".desc"));
		for (Opt o : Opt.BY_MOD.getOrDefault(m.id(), List.of())) b.append(' ').append(I18n.get("nosclient." + o.label()));
		return fold(b.toString()).contains(q);
	}

	private List<Mods.Mod> results() {
		String q = fold(query.trim());
		return Mods.ALL.stream().filter(m -> matches(m, q)).toList();
	}

	private boolean searching() {
		return !query.isBlank();
	}

	// ---------- vẽ ----------
	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
		super.render(g, mouseX, mouseY, pt);
		mx = mouseX;
		my = mouseY;
		long now = System.nanoTime();
		dt = Math.min(.05f, (now - last) / 1e9f);
		t = (now - opened) / 1e9f;
		last = now;
		hits.clear();
		float in = Ui.ease(Math.min(1, t / .35f));
		int x = px(), y = py() + Math.round((1 - in) * 10), w = pw(), h = ph();
		// hai quầng sáng sau kính: tím phù phép góc trên, xanh lá Nostalgia góc dưới — cả màn chỉ thêm 2 quad
		Ui.panel(g, x, y, x + w, y + h, Ui.alpha(0xB8141821, in));
		// hai quầng sáng trong kính: tím phù phép góc trên, xanh lá Nostalgia góc dưới — cả màn chỉ thêm 2 quad
		Fx.glow(g, x + side() + (w - side()) * .32f, y + 20, (w - side()) * 1.1f, h * .95f, Ui.alpha(0x3DA770F0, in));
		Fx.glow(g, x + w - 30, y + h - 10, w * .6f, h * .75f, Ui.alpha(0x245AC54F, in));
		Ui.brackets(g, x, y, x + w, y + h, 11, Ui.alpha(0xFFFFFFFF, .16f * in));
		left(g, x, y, h);

		boolean showDetail = detail != null;
		int rx = x + side() + 20, top = y + 58;
		int cx1 = x + w - 20 - (showDetail && dw() < w - side() ? dw() - 8 : 0);
		header(g, x, y, w, rx, showDetail ? cx1 : x + w - 20);
		scroll = Ui.approach(scroll, (float) scrollTarget, dt, 16);
		if (!(showDetail && dw() >= w - side())) {
			clipOn(g, x + side() + 1, y + 54, showDetail ? x + w - dw() : x + w - 1, y + h - 3);
			int bottom;
			if (searching()) bottom = cards(g, rx, cx1, top - (int) scroll, List.of(new Group(null, results())));
			else if (cat < 0) bottom = overview(g, rx, cx1, top - (int) scroll);
			else if (cat == 0 && tab == 1) bottom = look(g, rx, cx1, top - (int) scroll);
			else bottom = cards(g, rx, cx1, top - (int) scroll, groups());
			clipOff(g);
			maxScroll = Math.max(0, bottom + (int) scroll - (y + h - 10));
			scrollTarget = Math.min(scrollTarget, maxScroll);
			if (maxScroll > 0) {
				int st = y + 58, sb = y + h - 8, len = Math.max(16, (sb - st) * (sb - st) / (sb - st + maxScroll));
				int pos = st + Math.round((sb - st - len) * Math.min(1, scroll / maxScroll));
				int sx = (showDetail ? x + w - dw() : x + w) - 5;
				g.fill(sx, pos, sx + 2, pos + len, 0x55FFFFFF);
			}
		}
		if (showDetail) Mods.ALL.stream().filter(m -> m.id().equals(detail)).findFirst().ifPresent(m -> detailPanel(g, m, x + w - dw(), y + 54, x + w - 1, y + h - 1));
		runes.render(g);
		sparks.render(g);
	}

	private void left(GuiGraphics g, int x, int y, int h) {
		int s = side();
		g.fill(x + s, y + 1, x + s + 1, y + h - 1, 0x14FFFFFF);
		g.fill(x + 1, y + 1, x + s, y + h - 1, 0x1A000000);
		Fx.glow(g, x + 27, y + 27, 54, 46, 0x405AC54F);
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x + 16, y + 16, 0, 0, 22, 22, 22, 22);
		Ui.text(g, Ui.bold(Component.literal("Nos client")), x + 46, y + 18, Ui.TEXT);
		Ui.text(g, Ui.small(Component.literal("NOSTALGIA · R-SHIFT")), x + 46, y + 29, Ui.FAINT);
		// ô tìm kiếm (chữ do EditBox vẽ, ta vẽ nền)
		boolean sf = searchFocus;
		g.fill(x + 12, y + 45, x + s - 12, y + 63, sf ? 0x22FFFFFF : 0x12FFFFFF);
		Ui.frame(g, x + 12, y + 45, x + s - 12, y + 63, sf ? 0x66D3A8F5 : 0x1FFFFFFF);
		int qx = x + 28, qw = s - 46;
		if (query.isEmpty() && !sf) Ui.text(g, Ui.fit(Ui.small(tr("nosclient.settings.search")), qw), qx, y + 51, Ui.FAINT);
		else {
			// chữ dài hơn ô thì giữ phần cuối (chỗ đang gõ) trong tầm mắt
			String shown = query;
			while (shown.length() > 0 && Ui.width(Ui.small(Component.literal(shown))) > qw - 4) shown = shown.substring(1);
			Component q = Ui.small(Component.literal(shown));
			Ui.text(g, q, qx, y + 51, Ui.TEXT);
			if (sf && (t * 2 % 2) < 1.2f) g.fill(qx + Ui.width(q) + 1, y + 49, qx + Ui.width(q) + 2, y + 59, Ui.ACCENT_HI);
		}
		// kính lúp vẽ tay (font không có glyph ⌕): khung 5 px + cán chéo
		Ui.frame(g, x + 17, y + 50, x + 22, y + 55, Ui.FAINT);
		g.fill(x + 22, y + 55, x + 24, y + 57, Ui.FAINT);
		Ui.text(g, Ui.small(tr("nosclient.settings.cats")), x + 16, y + 72, Ui.FAINT);
		int step = h >= 320 ? 24 : 20;
		for (int i = -1; i < CATS.length; i++) {
			int cy = y + 84 + (i + 1) * step, ch = step - 4;
			int accent = i < 0 ? Ui.BRAND : Mods.catColor(CATS[i]);
			boolean sel = i == cat && !searching(), hv = hov(x + 8, cy, x + s - 8, cy + ch);
			if (sel || hv) g.fill(x + 8, cy, x + s - 8, cy + ch, sel ? Ui.alpha(accent, .22f) : 0x0FFFFFFF);
			if (sel) {
				g.fill(x + 3, cy + 4, x + 5, cy + ch - 4, accent);
				Fx.glint(g, x + 8, cy, x + s - 8, cy + ch, Ui.alpha(0xFFFFF0FF, .35f));
				Fx.glow(g, x + 4, cy + ch / 2f, 18, ch * 1.6f, Ui.alpha(accent, .5f));
			}
			if (i < 0) g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x + 18, cy + ch / 2 - 6, 0, 0, 12, 12, 12, 12);
			else icon(g, CAT_ICON[i], x + 24, cy + ch / 2, .75f);
			Ui.text(g, Ui.fit(Ui.ui(tr("nosclient.cat." + (i < 0 ? "all" : CATS[i]))), s - 80), x + 38, cy + ch / 2 - 3, Ui.TEXT);
			long n = i < 0 ? Mods.ALL.stream().filter(NosSettingsScreen::on).count() : Mods.on(CATS[i]);
			int total = i < 0 ? Mods.ALL.size() : Mods.of(CATS[i]).size();
			Component badge = Ui.small(Component.literal(n + "/" + total));
			int bw = Ui.width(badge) + 10;
			g.fill(x + s - 14 - bw, cy + ch / 2 - 6, x + s - 14, cy + ch / 2 + 6, sel ? Ui.alpha(accent, .4f) : 0x14FFFFFF);
			Ui.text(g, badge, x + s - 14 - bw + 5, cy + ch / 2 - 3, sel ? 0xFFF4F7FF : Ui.FAINT);
			int ci = i;
			hit(x + 8, cy, x + s - 8, cy + ch, () -> openCat(ci));
		}
		// đáy: ngôn ngữ, bộ đếm, ghi chú — màn thấp thì chỉ giữ bộ đếm
		if (h >= 320) {
			int ly = y + h - 82, half = (s - 32) / 2;
			g.fill(x + 16, ly, x + s - 16, ly + 16, 0x14FFFFFF);
			String[] codes = {"vi_vn", "en_us"};
			for (int i = 0; i < 2; i++) {
				boolean sel = mc.options.languageCode.equals(codes[i]);
				int bx = x + 16 + i * half;
				if (sel) g.fill(bx + 2, ly + 2, bx + half - 2, ly + 14, 0x665E3A8A);
				Component l = Ui.small(tr("nosclient.lang." + codes[i]));
				Ui.text(g, l, bx + (half - Ui.width(l)) / 2, ly + 5, sel ? 0xFFEAD4FB : Ui.MUTED);
				String code = codes[i];
				hit(bx, ly, bx + half, ly + 16, () -> setLang(code));
			}
			lines(g, Ui.small(tr("nosclient.settings.footer")), x + 16, y + h - 30, s - 32, Ui.FAINT);
		}
		int on = (int) Mods.ALL.stream().filter(NosSettingsScreen::on).count(), total = Mods.ALL.size();
		int cy = y + h - (h >= 320 ? 58 : 36);
		Ui.text(g, Ui.small(tr("nosclient.settings.on")), x + 16, cy, Ui.MUTED);
		Component n = Ui.bold(Component.literal(on + "/" + total));
		Ui.text(g, n, x + s - 16 - Ui.width(n), cy - 1, Ui.TEXT);
		g.fill(x + 16, cy + 13, x + s - 16, cy + 15, 0x1FFFFFFF);
		g.fill(x + 16, cy + 13, x + 16 + Math.round((s - 32) * on / (float) total), cy + 15, Ui.ACC1);
	}

	private void header(GuiGraphics g, int x, int y, int w, int rx, int right) {
		Component title, sub;
		if (searching()) {
			title = tr("nosclient.settings.results").copy().append(" “" + query.trim() + "”");
			sub = Component.translatable("nosclient.settings.count", results().size());
		} else {
			String id = cat < 0 ? "all" : CATS[cat];
			title = tr("nosclient.cat." + id);
			sub = tr("nosclient.cat." + id + ".sub");
		}
		// nút bên phải: Đặt lại; HUD có thêm tab Mod / Giao diện
		int bx = x + w - 20;
		if (!searching()) {
			Component rl = Ui.bold(tr("nosclient.settings.reset"));
			bx -= Ui.width(rl) + 16;
			button(g, rl, bx, y + 18, 18, true, this::askReset);
			// sửa bố cục cần HUD thật để kéo, nên chỉ bật khi đang ở trong thế giới
			Component el = Ui.bold(tr("nosclient.settings.edit"));
			bx -= Ui.width(el) + 16 + 6;
			button(g, el, bx, y + 18, 18, mc.player != null, () -> mc.setScreen(new HudEditScreen(this)));
			if (cat == 0) for (int i = 1; i >= 0; i--) {
				int hudAccentTab = Mods.catColor("hud");
				Component tl = Ui.bold(tr(i == 0 ? "nosclient.tab.mods" : "nosclient.tab.look"));
				int tw = Ui.width(tl) + 20, tx = bx - 6 - tw;
				boolean sel = tab == i;
				g.fill(tx, y + 18, tx + tw, y + 36, sel ? Ui.alpha(hudAccentTab, .4f) : 0x14FFFFFF);
				if (sel) g.fill(tx, y + 35, tx + tw, y + 36, hudAccentTab);
				Ui.text(g, tl, tx + 10, y + 24, sel ? 0xFFF4F7FF : Ui.MUTED);
				int ti = i;
				hit(tx, y + 18, tx + tw, y + 36, () -> {
					tab = ti;
					scroll = 0;
					scrollTarget = 0;
				});
				bx = tx + 2;
			}
		}
		int catAccent = searching() || cat < 0 ? Ui.BRAND : Mods.catColor(CATS[cat]);
		g.fill(rx - 10, y + 17, rx - 7, y + 29, catAccent);
		Ui.text(g, Ui.fit(Ui.big(title), bx - 8 - rx), rx, y + 18, Ui.TEXT);
		Ui.text(g, Ui.fit(Ui.small(sub), right - rx), rx, y + 40, Ui.MUTED);
	}

	// ---------- Tổng quan: thẻ tóm tắt từng danh mục + hàng bật/tắt nhanh ----------
	private int overview(GuiGraphics g, int x0, int x1, int y) {
		int n = CATS.length, gap = 6, W = x1 - x0, cw = (W - gap * (n - 1)) / n, ch = 46;
		for (int i = 0; i < n; i++) {
			int cx = x0 + i * (cw + gap), accent = Mods.catColor(CATS[i]);
			boolean hv = hov(cx, y, cx + cw, y + ch);
			g.fill(cx, y, cx + cw, y + ch, hv ? Ui.alpha(accent, .12f) : 0x0CFFFFFF);
			Ui.frame(g, cx, y, cx + cw, y + ch, Ui.alpha(accent, hv ? .65f : .3f));
			g.fill(cx, y, cx + 3, y + ch, Ui.alpha(accent, .9f)); // vạch nhận diện phe bên trái
			if (hv) Ui.brackets(g, cx, y, cx + cw, y + ch, 7, Ui.alpha(accent, .9f));
			icon(g, CAT_ICON[i], cx + 16, y + 13, .75f);
			Component lbl = Ui.fit(Ui.small(tr("nosclient.cat." + CATS[i])), cw - 32);
			Ui.text(g, lbl, cx + 28, y + 10, Ui.TEXT);
			g.fill(cx + 28, y + 19, cx + 28 + Ui.width(lbl), y + 20, Ui.alpha(accent, .8f));
			long on = Mods.on(CATS[i]);
			int total = Mods.of(CATS[i]).size();
			Component num = Ui.bold(Component.literal(on + "/" + total));
			Ui.text(g, num, cx + 8, y + 25, Ui.TEXT);
			Ui.text(g, Ui.fit(Ui.small(tr("nosclient.settings.enabled")), cw - 20 - Ui.width(num)), cx + 12 + Ui.width(num), y + 26, Ui.MUTED);
			g.fill(cx + 8, y + ch - 8, cx + cw - 8, y + ch - 6, 0x1FFFFFFF);
			g.fill(cx + 8, y + ch - 8, cx + 8 + Math.round((cw - 16) * on / (float) total), y + ch - 6, accent);
			int ci = i;
			hit(cx, y, cx + cw, y + ch, () -> openCat(ci));
		}
		y += ch + 14;
		int cols = W >= 520 ? 4 : W >= 330 ? 3 : 2, rw = (W - (cols - 1) * gap) / cols, rh = 18;
		for (int i = 0; i < n; i++) {
			int accent = Mods.catColor(CATS[i]);
			g.fill(x0 + 2, y + 2, x0 + 6, y + 6, accent);
			Ui.text(g, Ui.small(Component.literal(I18n.get("nosclient.cat." + CATS[i]).toUpperCase(Locale.ROOT))), x0 + 11, y, Ui.FAINT);
			Component open = Ui.small(tr("nosclient.settings.open"));
			int ow = Ui.width(open);
			Ui.text(g, open, x1 - ow, y, hov(x1 - ow - 4, y - 3, x1, y + 9) ? Ui.TEXT : Ui.lerpCol(accent, 0xFFFFFFFF, .3f));
			int ci = i;
			hit(x1 - ow - 4, y - 3, x1, y + 9, () -> openCat(ci));
			y += 12;
			List<Mods.Mod> ms = Mods.of(CATS[i]);
			for (int j = 0; j < ms.size(); j++) {
				Mods.Mod m = ms.get(j);
				int rx0 = x0 + (j % cols) * (rw + gap), ry0 = y + (j / cols) * (rh + 4);
				float k = k(Opt.field(m), on(m));
				boolean hv = hov(rx0, ry0, rx0 + rw, ry0 + rh);
				g.fill(rx0, ry0, rx0 + rw, ry0 + rh, Ui.lerpCol(hv ? 0x1AFFFFFF : 0x0FFFFFFF, Ui.alpha(accent, hv ? .3f : .2f), k));
				Ui.frame(g, rx0, ry0, rx0 + rw, ry0 + rh, Ui.lerpCol(0x14FFFFFF, Ui.alpha(accent, .4f), k));
				icon(g, m.icon(), rx0 + 10, ry0 + rh / 2, .65f);
				Ui.text(g, Ui.fit(Ui.ui(name(m)), rw - 20 - 32), rx0 + 20, ry0 + 6, Ui.TEXT);
				Ui.toggle(g, rx0 + rw - 28, ry0 + 3, k, accent);
				hit(rx0, ry0, rx0 + rw, ry0 + rh, () -> flip(m));
			}
			y += (ms.size() + cols - 1) / cols * (rh + 4) + 8;
		}
		return y;
	}

	// ---------- lưới thẻ của một danh mục (hoặc kết quả tìm) ----------
	private record Group(String label, List<Mods.Mod> mods) {}

	private List<Group> groups() {
		List<Mods.Mod> ms = Mods.of(CATS[cat]);
		if (cat != 0) return List.of(new Group(null, ms));
		return List.of(new Group("nosclient.group.basic", ms.stream().filter(m -> !m.group().equals("advanced")).toList()),
			new Group("nosclient.group.advanced", ms.stream().filter(m -> m.group().equals("advanced")).toList()));
	}

	private int cards(GuiGraphics g, int x0, int x1, int y, List<Group> gs) {
		int W = x1 - x0, cols = Math.max(1, (W + GAP) / (128 + GAP)), cw = (W - (cols - 1) * GAP) / cols, ch = 58;
		int idx = 0;
		for (Group gr : gs) {
			if (gr.mods.isEmpty()) continue;
			if (gr.label != null) {
				Ui.text(g, Ui.small(tr(gr.label)), x0 + 2, y + 2, Ui.FAINT);
				y += 14;
			}
			for (int i = 0; i < gr.mods.size(); i++, idx++) card(g, gr.mods.get(i), x0 + (i % cols) * (cw + GAP), y + (i / cols) * (ch + GAP), cw, ch, idx);
			y += (gr.mods.size() + cols - 1) / cols * (ch + GAP) + 6;
		}
		if (searching() && idx == 0) Ui.text(g, Ui.ui(tr("nosclient.settings.none")), x0, y + 4, Ui.MUTED);
		return y;
	}

	private void card(GuiGraphics g, Mods.Mod m, int x, int y, int w, int h, int i) {
		boolean on = on(m), sel = m.id().equals(detail), hv = hov(x, y, x + w, y + h);
		int accent = Mods.catColor(m.cat());
		float k = k(Opt.field(m), on), in = Ui.ease(Math.min(1, Math.max(0, (t - .03f * i) / .4f)));
		int bg = Ui.lerpCol(hv ? 0x1AFFFFFF : 0x0FFFFFFF, Ui.alpha(accent, hv ? .3f : .18f), k);
		g.fill(x, y, x + w, y + h, Ui.alpha(bg, in));
		if (k > .02f) Fx.glint(g, x + 1, y + 1, x + w - 1, y + h - 1, Ui.alpha(0x3DF0DBFF, k * in));
		Ui.frame(g, x, y, x + w, y + h, Ui.alpha(sel ? 0xCCD3A8F5 : Ui.lerpCol(0x14FFFFFF, Ui.alpha(accent, .4f), k), in));
		if (k > .02f) Fx.hairline(g, x + 1, x + w - 1, y, Ui.alpha(0x99E9D5FF, k * in));
		if (sel || hv) Ui.brackets(g, x, y, x + w, y + h, 6, Ui.alpha(sel ? 0xFFF4F7FF : accent, in * (sel ? 1 : .8f)));
		g.fill(x + 10, y + 9, x + 28, y + 27, Ui.alpha(Ui.lerpCol(0x1FFFFFFF, Ui.alpha(accent, .3f), k), in));
		icon(g, m.icon(), x + 19, y + 18, .9f);
		Ui.text(g, Ui.fit(Ui.bold(name(m)), w - 34 - 40), x + 34, y + 14, Ui.alpha(Ui.TEXT, in));
		Ui.toggle(g, x + w - 34, y + 12, k, accent);
		Ui.text(g, Ui.fit(Ui.small(tr("nosclient.mod." + m.id() + ".desc")), w - 20), x + 10, y + 33, Ui.alpha(Ui.MUTED, in));
		if (I18n.exists("nosclient.warn." + m.id())) {
			Component wl = Ui.small(tr("nosclient.warn.short"));
			g.fill(x + 10, y + 44, x + 18 + Ui.width(wl), y + 53, 0x33E6B23C);
			Ui.text(g, wl, x + 14, y + 46, 0xFFF2C46B);
		}
		Component more = Ui.small(tr("nosclient.settings.options"));
		Ui.text(g, more, x + w - 10 - Ui.width(more), y + 46, hv || sel ? Ui.TEXT : Ui.ACCENT_HI);
		hit(x, y, x + w, y + h, () -> {
			detail = sel ? null : m.id();
			dScroll = 0;
			dTarget = 0;
			note = null;
		});
		hit(x + w - 40, y + 6, x + w - 4, y + 30, () -> flip(m));
	}

	// ---------- bảng chi tiết ----------
	private void detailPanel(GuiGraphics g, Mods.Mod m, int x0, int y0, int x1, int y1) {
		int accent = Mods.catColor(m.cat());
		g.fill(x0, y0, x1, y1, 0xF0161A23);
		g.fill(x0, y0, x0 + 1, y1, 0x1FFFFFFF);
		g.fill(x0, y0, x0 + 3, y1, Ui.alpha(accent, .55f)); // vạch màu phe bên trái toàn bảng
		dScroll = Ui.approach(dScroll, (float) dTarget, dt, 16);
		clipOn(g, x0 + 1, y0, x1, y1);
		int px = x0 + 12, pw = x1 - x0 - 24, y = y0 + 12 - (int) dScroll;
		boolean lit = on(m);
		if (lit) {
			Fx.glow(g, px + 13, y + 13, 70, 70, Ui.alpha(accent, .55f));
			runes.emit(px + 13, y + 13, 46, 7, dt, Ui.lerpCol(0xFFFFFFFF, accent, .5f));
		}
		g.fill(px, y, px + 26, y + 26, 0x1FFFFFFF);
		if (lit) Fx.glint(g, px, y, px + 26, y + 26, Ui.alpha(accent, .5f));
		icon(g, m.icon(), px + 13, y + 13, 1.1f);
		Ui.text(g, Ui.fit(Ui.bold(name(m)), pw - 34 - 14), px + 34, y + 3, Ui.TEXT);
		boolean def = Opt.defOn(Opt.field(m));
		int by = y + 15, bx = px + 34;
		Component db = Ui.small(tr(def ? "nosclient.detail.defOn" : "nosclient.detail.defOff"));
		g.fill(bx, by, bx + Ui.width(db) + 8, by + 10, 0x14FFFFFF);
		Ui.text(g, db, bx + 4, by + 2, Ui.MUTED);
		bx += Ui.width(db) + 12;
		if (I18n.exists("nosclient.warn." + m.id())) {
			Component wb = Ui.small(tr("nosclient.warn." + m.id()));
			if (bx + Ui.width(wb) + 8 > px + pw) {
				bx = px + 34;
				by += 13;
				y += 13;
			}
			g.fill(bx, by, bx + Ui.width(wb) + 8, by + 10, 0x33E6B23C);
			Ui.text(g, wb, bx + 4, by + 2, 0xFFF2C46B);
		}
		y += 36;
		y += lines(g, Ui.small(tr("nosclient.mod." + m.id() + ".desc")), px, y, pw, Ui.MUTED) + 8;
		// trạng thái bật/tắt
		boolean on = on(m);
		g.fill(px, y, px + pw, y + 30, 0x0FFFFFFF);
		Ui.frame(g, px, y, px + pw, y + 30, 0x14FFFFFF);
		Ui.text(g, Ui.bold(tr(on ? "nosclient.detail.on" : "nosclient.detail.off")), px + 8, y + 6, Ui.TEXT);
		Ui.text(g, Ui.fit(Ui.small(tr(on ? "nosclient.detail.on.sub" : "nosclient.detail.off.sub")), pw - 46), px + 8, y + 18, Ui.MUTED);
		Ui.toggle(g, px + pw - 32, y + 9, k(Opt.field(m), on), accent);
		hit(px, y, px + pw, y + 30, () -> flip(m));
		y += 40;
		List<Opt> opts = Opt.BY_MOD.getOrDefault(m.id(), List.of());
		boolean extra = m.id().equals("waypoint") || m.id().equals("mp");
		if (!opts.isEmpty() || extra) {
			Ui.text(g, Ui.small(tr("nosclient.detail.opts")), px + 2, y, Ui.FAINT);
			y += 12;
			int top = y;
			for (int i = 0; i < opts.size(); i++) {
				if (i > 0) g.fill(px + 8, y, px + pw - 8, y + 1, 0x0FFFFFFF);
				y = option(g, opts.get(i), px, y, pw, accent);
			}
			if (!opts.isEmpty()) Ui.frame(g, px, top, px + pw, y, 0x14FFFFFF);
			y = extras(g, m, px, y + 6, pw);
		}
		// "Cách chạy": mod làm gì với máy, để người chơi yên tâm bật
		int ph = font.split(Ui.small(tr("nosclient.mod." + m.id() + ".perf")), pw - 16).size() * 10;
		y += 6;
		g.fill(px, y, px + pw, y + ph + 24, 0x0AFFFFFF);
		Ui.frame(g, px, y, px + pw, y + ph + 24, 0x14FFFFFF);
		Ui.text(g, Ui.bold(tr("nosclient.detail.perf")), px + 8, y + 7, Ui.ACCENT_HI);
		lines(g, Ui.small(tr("nosclient.mod." + m.id() + ".perf")), px + 8, y + 19, pw - 16, Ui.MUTED);
		y += ph + 34;
		clipOff(g);
		dMax = Math.max(0, y + (int) dScroll - y1);
		dTarget = Math.min(dTarget, dMax);
		// nút đóng nằm ngoài vùng cuộn
		boolean hv = hov(x1 - 18, y0 + 4, x1 - 4, y0 + 18);
		Ui.text(g, Ui.ui(Component.literal("×")), x1 - 14, y0 + 7, hv ? Ui.TEXT : Ui.MUTED);
		hit(x1 - 20, y0 + 2, x1 - 2, y0 + 20, () -> detail = null);
	}

	private Component optLabel(String key) {
		return key.startsWith("opt.") ? tr("nosclient." + key) : Component.literal(key);
	}

	/** Một hàng tuỳ chọn; trả y kế tiếp. */
	private int option(GuiGraphics g, Opt o, int x, int y, int w, int accent) {
		Component label = Ui.ui(optLabel(o.label()));
		switch (o.type()) {
			case "chk" -> {
				boolean v = o.bool();
				Ui.text(g, Ui.fit(label, w - 48), x + 8, y + 7, Ui.TEXT);
				Ui.toggle(g, x + w - 32, y + 5, k(o.field(), v), accent);
				hit(x, y, x + w, y + 22, () -> {
					o.set(v ? 0 : 1);
					c.save();
				});
				return y + 22;
			}
			case "seg" -> {
				int sel = o.segIndex(), segW = 0;
				Component[] ls = new Component[o.choices().length];
				for (int i = 0; i < ls.length; i++) segW += Ui.width(ls[i] = Ui.small(optLabel(o.choices()[i]))) + 12;
				boolean wrap = Ui.width(label) + 12 + segW > w - 16;
				Ui.text(g, Ui.fit(label, w - 16), x + 8, y + 7, Ui.TEXT);
				int sx = x + w - 8 - segW, sy = wrap ? y + 19 : y + 4;
				g.fill(sx, sy, sx + segW, sy + 14, 0x14FFFFFF);
				for (int i = 0; i < ls.length; i++) {
					int bw = Ui.width(ls[i]) + 12;
					if (i == sel) g.fill(sx + 1, sy + 1, sx + bw - 1, sy + 13, Ui.alpha(accent, .4f));
					Ui.text(g, ls[i], sx + 6, sy + 4, i == sel ? 0xFFF4F7FF : Ui.MUTED);
					int ci = i;
					hit(sx, sy, sx + bw, sy + 14, () -> {
						o.setSeg(ci);
						c.save();
					});
					sx += bw;
				}
				return y + (wrap ? 38 : 22);
			}
			case "rng" -> {
				double v = o.num();
				String shown = o.field().equals("gamma") ? (v < 0 ? I18n.get("nosclient.gamma.game") : Math.round(v * 100) + "%" + (v >= 10 ? " · Full bright" : ""))
					: (int) Math.round(v) + o.unit();
				Component val = Ui.bold(Component.literal(shown));
				Ui.text(g, Ui.fit(label, w - 24 - Ui.width(val)), x + 8, y + 7, Ui.TEXT);
				Ui.text(g, val, x + w - 8 - Ui.width(val), y + 7, Ui.lerpCol(accent, 0xFFFFFFFF, .35f));
				int tx0 = x + 10, tx1 = x + w - 10, ty = y + 24;
				double f = v < 0 ? (.5 - o.min()) / (o.max() - o.min()) : (v - o.min()) / (o.max() - o.min());
				int kx = (int) Math.round(tx0 + Math.min(1, Math.max(0, f)) * (tx1 - tx0));
				g.fill(tx0, ty, tx1, ty + 2, 0x24FFFFFF);
				g.fill(tx0, ty, kx, ty + 2, accent);
				g.fill(kx - 2, ty - 4, kx + 2, ty + 6, 0xFFFFFFFF);
				hit(tx0 - 4, ty - 7, tx1 + 4, ty + 9, () -> {
					drag = o;
					dragX0 = tx0;
					dragX1 = tx1;
					slide(mx);
				});
				return y + 34;
			}
			default -> { // phím: hiện phím đang gán, bấm mở màn Điều khiển của game
				KeyMapping km = keyOf(o.field());
				Component key = Ui.bold(km == null ? Component.literal("?") : km.getTranslatedKeyMessage());
				Ui.text(g, Ui.fit(label, w - 40 - Ui.width(key)), x + 8, y + 7, Ui.TEXT);
				int kw = Ui.width(key) + 12, kx = x + w - 8 - kw;
				boolean hv = hov(kx, y + 4, kx + kw, y + 18);
				g.fill(kx, y + 4, kx + kw, y + 18, hv ? 0x335E3A8A : 0x1FFFFFFF);
				Ui.frame(g, kx, y + 4, kx + kw, y + 18, hv ? 0x59D3A8F5 : 0x24FFFFFF);
				Ui.text(g, key, kx + 6, y + 8, Ui.TEXT);
				hit(x, y, x + w, y + 22, () -> mc.setScreen(new KeyBindsScreen(this, mc.options)));
				return y + 22;
			}
		}
	}

	private int extras(GuiGraphics g, Mods.Mod m, int x, int y, int w) {
		boolean inWorld = mc.player != null && mc.level != null;
		if (m.id().equals("waypoint")) {
			button(g, Ui.bold(tr("nosclient.wp.add")), x, y, 18, inWorld && c.waypoint, () -> {
				var wp = Waypoints.addHere(mc);
				note = Component.translatable("nosclient.wp.added", wp.name(), wp.x(), wp.y(), wp.z());
			});
			y += 22;
			Component sub = note != null ? note : inWorld ? Component.translatable("nosclient.wp.count", Waypoints.here(mc).size()) : tr("nosclient.wp.noworld");
			y += lines(g, Ui.small(sub), x + 2, y, w - 4, note != null ? Ui.ACCENT_HI : Ui.FAINT) + 4;
		} else if (m.id().equals("mp")) {
			boolean sp = mc.getSingleplayerServer() != null;
			button(g, Ui.bold(tr("nosclient.mp.openroom")), x, y, 18, sp && c.mp, () -> mc.setScreen(new PlayTogetherScreen(this)));
			y += 22;
			if (!sp) y += lines(g, Ui.small(tr("nosclient.mp.needsp")), x + 2, y, w - 4, Ui.FAINT) + 4;
		}
		return y;
	}

	private KeyMapping keyOf(String name) {
		for (KeyMapping km : mc.options.keyMappings) if (km.getName().equals(name)) return km;
		return null;
	}

	private void slide(double x) {
		if (drag == null) return;
		double f = Math.min(1, Math.max(0, (x - dragX0) / (double) (dragX1 - dragX0))), v = drag.min() + f * (drag.max() - drag.min());
		drag.set(drag.field().equals("gamma") ? Math.round(v * 10) / 10.0 : Math.round(v));
	}

	// ---------- khu "Giao diện HUD": kích thước, độ trong suốt nền, mờ khi đứng yên ----------
	private int lookTrackEnd(int x0, int x1) {
		return x0 + Math.min(220, (x1 - x0) * 55 / 100);
	}

	private int look(GuiGraphics g, int x0, int x1, int y) {
		int hudAccent = Mods.catColor("hud");
		y += 10;
		double[] val = {c.hudScale - .5, c.hudBg};
		String[] lab = {"nosclient.look.size", "nosclient.look.bg"};
		String[] shown = {Math.round(c.hudScale * 100) + "%", Math.round(c.hudBg * 100) + "%"};
		int te = lookTrackEnd(x0, x1);
		for (int i = 0; i < 2; i++) {
			int ry = y + i * 34;
			Ui.text(g, Ui.ui(tr(lab[i])), x0, ry, Ui.TEXT);
			Component v = Ui.bold(Component.literal(shown[i]));
			Ui.text(g, v, te - Ui.width(v), ry, Ui.lerpCol(hudAccent, 0xFFFFFFFF, .35f));
			int ty = ry + 15, kx = (int) Math.round(x0 + val[i] * (te - x0));
			g.fill(x0, ty, te, ty + 2, 0x24FFFFFF);
			g.fill(x0, ty, kx, ty + 2, hudAccent);
			g.fill(kx - 2, ty - 4, kx + 2, ty + 6, 0xFFFFFFFF);
			int li = i;
			hit(x0 - 4, ry + 8, te + 4, ry + 24, () -> {
				dragLook = li;
				dragX0 = x0;
				dragX1 = te;
				setLook(mx);
			});
		}
		int fy = y + 72;
		Ui.toggle(g, x0, fy, k("hudIdleFade", c.hudIdleFade), hudAccent);
		Ui.text(g, Ui.ui(tr("nosclient.look.fade")), x0 + 30, fy + 2, Ui.TEXT);
		Ui.text(g, Ui.fit(Ui.small(tr("nosclient.look.fade.desc")), x1 - x0 - 30), x0 + 30, fy + 14, Ui.MUTED);
		hit(x0, fy - 2, x1, fy + 22, () -> {
			c.hudIdleFade = !c.hudIdleFade;
			c.save();
		});
		int iy = fy + 32;
		Ui.toggle(g, x0, iy, k("intro", c.intro), hudAccent);
		Ui.text(g, Ui.ui(tr("nosclient.intro.opt")), x0 + 30, iy + 2, Ui.TEXT);
		Ui.text(g, Ui.fit(Ui.small(tr("nosclient.intro.opt.desc")), x1 - x0 - 30), x0 + 30, iy + 14, Ui.MUTED);
		hit(x0, iy - 2, x1, iy + 22, () -> {
			c.intro = !c.intro;
			c.save();
		});
		// xem trước: một viên HUD thật ở đúng cỡ và độ đậm nền đang chọn
		int pvx = te + 24;
		if (pvx + 60 < x1) {
			Ui.text(g, Ui.small(tr("nosclient.look.preview")), pvx, y, Ui.FAINT);
			float k = Math.min((float) c.hudScale, (x1 - pvx) / (float) (font.width("XYZ 120 64 -35") + 8));
			g.pose().pushMatrix();
			g.pose().translate(pvx, y + 14);
			g.pose().scale(k, k);
			String[] rows = {"123 FPS", "XYZ 120 64 -35", "0 | 0 CPS"};
			for (int r = 0; r < rows.length; r++) {
				g.fill(0, r * 15, font.width(rows[r]) + 8, r * 15 + 13, (int) Math.round(c.hudBg * 255) << 24);
				g.drawString(font, rows[r], 4, r * 15 + 3, 0xFFFFFFFF, false);
			}
			g.pose().popMatrix();
		}
		return iy + 30;
	}

	private void setLook(double x) {
		double k = Math.min(1, Math.max(0, (x - dragX0) / (double) (dragX1 - dragX0)));
		if (dragLook == 0) c.hudScale = Math.round((.5 + k) * 20) / 20.0; // bước 5%
		else c.hudBg = Math.round(k * 20) / 20.0;
	}

	// ---------- hành động ----------
	private void openCat(int i) {
		cat = i;
		tab = 0;
		detail = null;
		scroll = 0;
		scrollTarget = 0;
		if (searching()) setQuery("");
	}

	/** Đổi ngôn ngữ game như màn Ngôn ngữ, nhưng chỉ nạp lại bảng chữ (không nạp lại cả resource pack). */
	private void setLang(String code) {
		if (code.equals(mc.options.languageCode)) return;
		mc.options.languageCode = code;
		mc.getLanguageManager().setSelected(code);
		mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
		mc.options.save();
	}

	private List<Mods.Mod> scope() {
		return cat < 0 ? Mods.ALL : Mods.of(CATS[cat]);
	}

	private void askReset() {
		Component what = tr("nosclient.cat." + (cat < 0 ? "all" : CATS[cat]));
		mc.setScreen(new ConfirmScreen(ok -> {
			if (ok) reset();
			mc.setScreen(this);
		}, Component.translatable("nosclient.reset.title", what), tr("nosclient.reset.msg")));
	}

	/** Bật/tắt, tuỳ chọn và phím của các mod trong phạm vi về mặc định; Tổng quan và HUD trả cả giao diện HUD. */
	private void reset() {
		boolean keys = false;
		for (Mods.Mod m : scope()) {
			Opt.reset(Opt.field(m));
			for (Opt o : Opt.BY_MOD.getOrDefault(m.id(), List.of())) {
				if (!o.type().equals("key")) Opt.reset(o.field());
				else if (keyOf(o.field()) instanceof KeyMapping km) {
					km.setKey(km.getDefaultKey());
					keys = true;
				}
			}
		}
		if (cat <= 0) for (String f : new String[]{"hudScale", "hudBg", "hudIdleFade"}) Opt.reset(f);
		if (keys) {
			KeyMapping.resetMapping();
			mc.options.save();
		}
		c.save();
	}

	// ---------- nhập ----------
	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		searchFocus = e.x() >= px() + 12 && e.x() < px() + side() - 12 && e.y() >= py() + 45 && e.y() < py() + 63;
		if (searchFocus) return true;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (e.x() >= h.x0 && e.x() < h.x1 && e.y() >= h.y0 && e.y() < h.y1) {
				h.run.run();
				click();
				setDragging(drag != null || dragLook >= 0); // để thanh trượt nhận mouseDragged
				return true;
			}
		}
		return super.mouseClicked(e, dbl);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (drag != null) slide(e.x());
		else if (dragLook >= 0) setLook(e.x());
		else return super.mouseDragged(e, dx, dy);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (drag != null || dragLook >= 0) {
			drag = null;
			dragLook = -1;
			c.save();
			return true;
		}
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		if (detail != null && x >= px() + pw() - dw()) {
			dTarget = Math.max(0, Math.min(dMax, dTarget - sy * 24));
			return true;
		}
		if (maxScroll > 0) {
			scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - sy * 24));
			return true;
		}
		return super.mouseScrolled(x, y, sx, sy);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (searchFocus) {
			int k = e.key();
			if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_ENTER) searchFocus = false;
			// Ctrl+Backspace xoá hết, Backspace xoá một ký tự
			else if (k == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) setQuery((e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 ? "" : query.substring(0, query.offsetByCodePoints(query.length(), -1)));
			return true; // nuốt mọi phím khi đang gõ, kể cả R-Shift/E
		}
		if (e.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			onClose();
			return true;
		}
		if (e.key() == GLFW.GLFW_KEY_ESCAPE && detail != null) {
			detail = null;
			return true;
		}
		// Ctrl+F nhảy vào ô tìm
		if (e.key() == GLFW.GLFW_KEY_F && (e.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
			searchFocus = true;
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent e) {
		if (!searchFocus || !e.isAllowedChatCharacter()) return false;
		setQuery(query + e.codepointAsString());
		return true;
	}

	@Override
	public void onClose() {
		c.save();
		mc.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
