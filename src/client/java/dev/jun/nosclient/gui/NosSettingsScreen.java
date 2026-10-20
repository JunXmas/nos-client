package dev.jun.nosclient.gui;

import dev.jun.nosclient.Mods;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Cài đặt Nos client (R-Shift) theo preview: tấm kính lớn, cột trái có logo + danh mục + bộ đếm "đang bật",
 * bên phải là lưới thẻ mod nhóm "Cơ bản"/"Nâng cao", mỗi thẻ có icon, tên, mô tả và công tắc trượt.
 * Bản này mới có nhóm HUD đã làm thật; danh mục khác hiện mờ "sắp có" thay vì giả vờ chạy được.
 */
public final class NosSettingsScreen extends Screen {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("nosclient", "textures/gui/logo.png");
	private final Screen parent;
	private final SceneConfig c = SceneConfig.INSTANCE;
	private final long opened = System.nanoTime();
	private long last = opened;

	/** Danh mục đang chọn ở cột trái. */
	private int cat;
	/** Hoạt ảnh công tắc/rê chuột của từng thẻ, theo id mod. */
	private final java.util.Map<String, float[]> anim = new java.util.HashMap<>();

	private List<Mods.Mod> mods() {
		return Mods.of(Mods.CATS[cat]);
	}

	private float[] k(Mods.Mod m) {
		return anim.computeIfAbsent(m.id(), id -> new float[]{m.get().get() ? 1 : 0, 0});
	}

	/** Nhóm của thẻ: HUD chia Cơ bản/Nâng cao như preview, danh mục khác một nhóm. */
	private static boolean basic(Mods.Mod m) {
		return !m.group().equals("advanced");
	}

	private static final String[] CATS = Mods.CATS;
	private static final ItemStack[] CAT_ICON = {new ItemStack(Items.COMPASS), new ItemStack(Items.PURPLE_SHULKER_BOX), new ItemStack(Items.GRASS_BLOCK),
		new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.ENDER_EYE)};

	public NosSettingsScreen(Screen parent) {
		super(Component.translatable("nosclient.settings.title"));
		this.parent = parent;
	}

	// ---------- bố cục ----------
	private int pw() {
		return Math.min(width - 24, 600);
	}

	private int ph() {
		return Math.min(height - 24, 360);
	}

	private int px() {
		return (width - pw()) / 2;
	}

	private int py() {
		return (height - ph()) / 2;
	}

	private static final int GAP = 8;

	/** Cột trái hẹp lại khi màn hẹp để lưới thẻ còn đủ 2 cột. */
	private int side() {
		return pw() >= 560 ? 140 : 112;
	}
	/** 0 = thẻ mod, 1 = giao diện HUD. Tách tab để màn thấp không phải nhồi hai khu chồng lên nhau. */
	private int tab;

	/** Thẻ cao 44 (có mô tả); không đủ chỗ trong tấm kính thì 28 (chỉ tên + công tắc) để lưới không tràn. */
	private int cardH() {
		int basic = (int) mods().stream().filter(NosSettingsScreen::basic).count(), adv = mods().size() - basic;
		int rows = (basic + cols() - 1) / cols() + (adv + cols() - 1) / cols();
		return 76 + rows * (50 + GAP) + 22 + 14 <= ph() ? 50 : 36;
	}

	private int cols() {
		return Math.max(1, (pw() - side() - 44 + GAP) / (118 + GAP));
	}

	private int cardW() {
		return (pw() - side() - 44 - (cols() - 1) * GAP) / cols();
	}

	/** Vị trí thẻ thứ i trong nhóm của nó; nhóm Nâng cao nằm dưới Cơ bản. */
	private int[] cardPos(int i) {
		Mods.Mod m = mods().get(i);
		int basic = (int) mods().stream().filter(NosSettingsScreen::basic).count();
		int idx = basic(m) ? i : i - basic;
		int rowsBasic = (basic + cols() - 1) / cols();
		int top = py() + 76 - (int) scroll + (basic(m) ? 0 : rowsBasic * (cardH() + GAP) + 22);
		int x = px() + side() + 22 + (idx % cols()) * (cardW() + GAP), y = top + (idx / cols()) * (cardH() + GAP);
		return new int[]{x, y};
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		super.render(g, mx, my, pt);
		long now = System.nanoTime();
		float dt = Math.min(.05f, (now - last) / 1e9f), t = (now - opened) / 1e9f;
		last = now;
		float in = Ui.ease(Math.min(1, t / .35f));
		int x = px(), y = py() + Math.round((1 - in) * 10), w = pw(), h = ph();
		Ui.panel(g, x, y, x + w, y + h, Ui.alpha(0xB8141821, in));
		// cột trái
		g.fill(x + side(), y + 1, x + side() + 1, y + h - 1, 0x14FFFFFF);
		g.fill(x + 1, y + 1, x + side(), y + h - 1, 0x1A000000);
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, x + 16, y + 16, 0, 0, 22, 22, 22, 22);
		Ui.text(g, Ui.bold(Component.literal("Nos client")), x + 46, y + 18, Ui.TEXT);
		Ui.text(g, Ui.small(Component.literal("NOSTALGIA · R-SHIFT")), x + 46, y + 29, Ui.FAINT);
		Ui.text(g, Ui.small(Component.translatable("nosclient.settings.cats")), x + 16, y + 56, Ui.FAINT);
		for (int i = 0; i < CATS.length; i++) {
			int cy = y + 70 + i * 26;
			boolean sel = i == cat, live = true;
			if (sel) {
				g.fill(x + 10, cy, x + side() - 10, cy + 22, 0x405E3A8A);
				g.fill(x + 3, cy + 5, x + 5, cy + 17, Ui.ACCENT);
			}
			g.pose().pushMatrix();
			g.pose().translate(x + 18, cy + 4);
			g.pose().scale(.8f, .8f);
			g.renderFakeItem(CAT_ICON[i], 0, 0);
			g.pose().popMatrix();
			Ui.text(g, Ui.ui(Component.translatable("nosclient.cat." + CATS[i])), x + 38, cy + 7, live ? Ui.TEXT : Ui.FAINT);
			Component badge = Ui.small(Component.literal(Mods.on(CATS[i]) + "/" + Mods.of(CATS[i]).size()));
			int bw = Ui.width(badge) + 10;
			g.fill(x + side() - 16 - bw, cy + 5, x + side() - 16, cy + 17, sel ? 0x40B06EE6 : 0x14FFFFFF);
			Ui.text(g, badge, x + side() - 16 - bw + 5, cy + 8, sel ? 0xFFEAD4FB : Ui.FAINT);
		}
		int on = (int) Mods.ALL.stream().filter(m -> m.get().get()).count(), total = Mods.ALL.size();
		Ui.text(g, Ui.small(Component.translatable("nosclient.settings.on")), x + 16, y + h - 40, Ui.MUTED);
		Component n = Ui.bold(Component.literal(on + "/" + total));
		Ui.text(g, n, x + side() - 16 - Ui.width(n), y + h - 41, Ui.TEXT);
		g.fill(x + 16, y + h - 26, x + side() - 16, y + h - 23, 0x1FFFFFFF);
		int fw = Math.round((side() - 32) * on / (float) total);
		if (fw > 2) g.fill(x + 16, y + h - 26, x + 16 + fw, y + h - 23, Ui.ACC1);
		// phần phải
		int rx = x + side() + 22;
		Ui.text(g, Ui.big(Component.translatable("nosclient.cat." + CATS[cat])), rx, y + 18, Ui.TEXT);
		Ui.text(g, Ui.small(Component.translatable("nosclient.cat." + CATS[cat] + ".sub")), rx, y + 40, Ui.MUTED);
		int dy = Math.round((1 - in) * 10);
		if (cat == 0) tabs(g, dy);
		else tab = 0;
		if (tab == 0) {
			scroll = Ui.approach(scroll, (float) scrollTarget, dt, 16);
			g.enableScissor(x + side() + 1, py() + 56, x + w - 1, py() + ph() - 6);
			int nb = (int) mods().stream().filter(NosSettingsScreen::basic).count();
			if (nb < mods().size()) {
				Ui.text(g, Ui.small(Component.translatable("nosclient.group.basic")), rx, py() + 62 - (int) scroll + dy, Ui.FAINT);
				Ui.text(g, Ui.small(Component.translatable("nosclient.group.advanced")), rx, cardPos(nb)[1] - 14 + dy, Ui.FAINT);
			}
			for (int i = 0; i < mods().size(); i++) card(g, i, mx, my, dt, t, dy);
			g.disableScissor();
			int over = maxScroll();
			if (over > 0) { // thanh cuộn mảnh bên phải
				int top = py() + 58, bot = py() + ph() - 8, len = Math.max(16, (bot - top) * (bot - top) / (bot - top + over));
				int pos = top + Math.round((bot - top - len) * scroll / over);
				g.fill(x + w - 5, pos, x + w - 3, pos + len, 0x55FFFFFF);
			}
		} else look(g, dt, dy);
	}

	private static final String[] TABS = {"nosclient.tab.mods", "nosclient.tab.look"};

	/** [x0, x1] của tab i, canh phải ngang hàng tiêu đề. */
	private int[] tabBox(int i) {
		int x1 = px() + pw() - 22;
		for (int k = TABS.length - 1; k >= 0; k--) {
			int w = Ui.width(Ui.bold(Component.translatable(TABS[k]))) + 22, x0 = x1 - w;
			if (k == i) return new int[]{x0, x1};
			x1 = x0 - 4;
		}
		return new int[]{0, 0};
	}

	private void tabs(GuiGraphics g, int dy) {
		int y = py() + 20 + dy;
		for (int i = 0; i < TABS.length; i++) {
			int[] b = tabBox(i);
			boolean sel = i == tab;
			g.fill(b[0], y, b[1], y + 18, sel ? 0x665E3A8A : 0x14FFFFFF);
			if (sel) g.fill(b[0], y + 17, b[1], y + 18, Ui.ACCENT);
			Component c = Ui.bold(Component.translatable(TABS[i]));
			Ui.text(g, c, b[0] + 11, y + 5, sel ? 0xFFEAD4FB : Ui.MUTED);
		}
	}

	// ---------- khu "Giao diện HUD": kích thước, độ trong suốt nền, mờ khi đứng yên ----------
	private int lookY() {
		return py() + 70;
	}

	private int lookX0() {
		return px() + side() + 22;
	}

	private int lookX1() {
		return px() + pw() - 22;
	}

	private int dragging = -1;
	private float fadeK = c.hudIdleFade ? 1 : 0;

	private void look(GuiGraphics g, float dt, int dy) {
		int y = lookY() + dy, x0 = lookX0(), x1 = lookX1();
		double[] val = {c.hudScale - .5, c.hudBg};
		String[] lab = {"nosclient.look.size", "nosclient.look.bg"};
		String[] shown = {Math.round(c.hudScale * 100) + "%", Math.round(c.hudBg * 100) + "%"};
		for (int i = 0; i < 2; i++) {
			int ry = y + i * 34, tx0 = x0, tx1 = trackEnd();
			Ui.text(g, Ui.ui(Component.translatable(lab[i])), tx0, ry, Ui.TEXT);
			Component v = Ui.bold(Component.literal(shown[i]));
			Ui.text(g, v, tx1 - Ui.width(v), ry, Ui.ACCENT_HI);
			int ty = ry + 15, kx = (int) Math.round(tx0 + val[i] * (tx1 - tx0));
			g.fill(tx0, ty, tx1, ty + 2, 0x24FFFFFF);
			g.fill(tx0, ty, kx, ty + 2, Ui.ACC1);
			g.fill(kx - 2, ty - 4, kx + 2, ty + 6, 0xFFFFFFFF);
		}
		fadeK = Ui.approach(fadeK, c.hudIdleFade ? 1 : 0, dt, 14);
		int fy = y + 72;
		Ui.toggle(g, x0, fy, Ui.ease(fadeK));
		Ui.text(g, Ui.ui(Component.translatable("nosclient.look.fade")), x0 + 30, fy + 2, Ui.TEXT);
		Ui.text(g, Ui.fit(Ui.small(Component.translatable("nosclient.look.fade.desc")), x1 - x0 - 30), x0 + 30, fy + 14, Ui.MUTED);
		// xem trước: một viên HUD thật ở đúng cỡ và độ đậm nền đang chọn
		int pvx = trackEnd() + 24, pvy = y;
		if (pvx + 60 < x1) {
			Ui.text(g, Ui.small(Component.translatable("nosclient.look.preview")), pvx, pvy, Ui.FAINT);
			var f = Minecraft.getInstance().font;
			// cỡ thật so với giao diện, nhưng không tràn khỏi khung xem trước
			float k = Math.min((float) c.hudScale, (x1 - pvx) / (float) (f.width("XYZ 120 64 -35") + 8));
			g.pose().pushMatrix();
			g.pose().translate(pvx, pvy + 14);
			g.pose().scale(k, k);
			String[] rows = {"123 FPS", "XYZ 120 64 -35", "0 | 0 CPS"};
			for (int r = 0; r < rows.length; r++) {
				int w = f.width(rows[r]) + 8;
				g.fill(0, r * 15, w, r * 15 + 13, (int) Math.round(c.hudBg * 255) << 24);
				g.drawString(f, rows[r], 4, r * 15 + 3, 0xFFFFFFFF, false);
			}
			g.pose().popMatrix();
		}
	}

	/** Thanh kéo chiếm nửa trái, nửa phải để xem trước. */
	private int trackEnd() {
		return lookX0() + Math.min(220, (lookX1() - lookX0()) * 55 / 100);
	}

	private void setTrack(int i, double mx) {
		double k = Math.min(1, Math.max(0, (mx - lookX0()) / (double) (trackEnd() - lookX0())));
		if (i == 0) c.hudScale = Math.round((.5 + k) * 20) / 20.0; // bước 5%
		else c.hudBg = Math.round(k * 20) / 20.0;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging >= 0) {
			setTrack(dragging, e.x());
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragging >= 0) {
			dragging = -1;
			c.save();
			return true;
		}
		return super.mouseReleased(e);
	}

	private float scroll;
	private double scrollTarget;

	/** Phần nội dung thẻ cao hơn khung bao nhiêu. */
	private int maxScroll() {
		int last = cardPos(mods().size() - 1)[1] + (int) scroll + cardH();
		return Math.max(0, last - (py() + ph() - 10));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (tab == 0 && maxScroll() > 0) {
			scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - sy * 24));
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}


	private void card(GuiGraphics g, int i, int mx, int my, float dt, float t, int dy) {
		Mods.Mod m = mods().get(i);
		int[] p = cardPos(i);
		int x = p[0], y = p[1] + dy, w = cardW(), h = cardH();
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h, on = m.get().get();
		float[] mk = k(m);
		mk[0] = Ui.approach(mk[0], on ? 1 : 0, dt, 14);
		mk[1] = Ui.approach(mk[1], hov ? 1 : 0, dt, 12);
		float k = Ui.ease(mk[0]), in = Ui.ease(Math.min(1, Math.max(0, (t - .03f * i) / .4f)));
		int base = Ui.lerpCol(0x0FFFFFFF, 0x2E8A5BC4, k), top = Ui.lerpCol(base, Ui.lerpCol(0x1AFFFFFF, 0x408A5BC4, k), mk[1]);
		g.fill(x, y, x + w, y + h, Ui.alpha(top, in));
		Ui.frame(g, x, y, x + w, y + h, Ui.alpha(Ui.lerpCol(0x14FFFFFF, 0x59D3A8F5, k), in));
		int iy = h >= 50 ? 10 : (h - 18) / 2;
		g.fill(x + 12, y + iy, x + 30, y + iy + 18, Ui.alpha(0x1FFFFFFF, in));
		g.pose().pushMatrix();
		g.pose().translate(x + 21, y + iy + 9);
		g.pose().scale(.9f, .9f);
		g.renderFakeItem(m.icon(), -8, -8);
		g.pose().popMatrix();
		Ui.toggle(g, x + w - 12 - 24, y + iy + 3, k);
		int tx = x + 40, tw = w - 40 - 12 - 24 - 8;
		Ui.text(g, Ui.fit(Ui.bold(Component.translatable("nosclient.mod." + m.id())), tw), tx, y + iy + 5, Ui.alpha(Ui.TEXT, in));
		if (h >= 50) Ui.text(g, Ui.fit(Ui.small(Component.translatable("nosclient.mod." + m.id() + ".desc")), w - 24), x + 12, y + 35, Ui.alpha(Ui.MUTED, in));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		for (int i = 0; i < CATS.length; i++) {
			int cy = py() + 70 + i * 26;
			if (e.x() >= px() + 10 && e.x() < px() + side() - 10 && e.y() >= cy && e.y() < cy + 22) {
				if (cat != i) {
					cat = i;
					scroll = 0;
					scrollTarget = 0;
					Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
				}
				return true;
			}
		}
		for (int i = 0; cat == 0 && i < TABS.length; i++) {
			int[] b = tabBox(i);
			if (e.x() >= b[0] && e.x() < b[1] && e.y() >= py() + 20 && e.y() < py() + 38) {
				tab = i;
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
				return true;
			}
		}
		if (tab == 1) {
			int ly = lookY();
			for (int i = 0; i < 2; i++) {
				int ry = ly + i * 34;
				if (e.x() >= lookX0() - 4 && e.x() <= trackEnd() + 4 && e.y() >= ry + 8 && e.y() <= ry + 24) {
					dragging = i;
					setTrack(i, e.x());
					return true;
				}
			}
			if (e.x() >= lookX0() && e.x() < lookX1() && e.y() >= ly + 70 && e.y() < ly + 92) {
				c.hudIdleFade = !c.hudIdleFade;
				c.save();
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
				return true;
			}
			return super.mouseClicked(e, dbl);
		}
		for (int i = 0; i < mods().size(); i++) {
			int[] p = cardPos(i);
			if (tab == 0 && e.y() >= py() + 56 && e.y() < py() + ph() - 6 && e.x() >= p[0] && e.x() < p[0] + cardW() && e.y() >= p[1] && e.y() < p[1] + cardH()) {
				Mods.Mod m = mods().get(i);
				m.set().accept(!m.get().get());
				c.save();
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
				return true;
			}
		}
		return super.mouseClicked(e, dbl);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			onClose();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
