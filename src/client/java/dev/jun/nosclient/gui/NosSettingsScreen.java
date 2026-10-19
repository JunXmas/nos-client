package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

	private record Mod(String id, ItemStack icon, String group, Supplier<Boolean> get, Consumer<Boolean> set, float[] k) {}

	private final List<Mod> mods = List.of(
		mod("coords", Items.COMPASS, "basic", () -> c.coords, v -> c.coords = v),
		mod("direction", Items.RECOVERY_COMPASS, "basic", () -> c.direction, v -> c.direction = v),
		mod("day", Items.CLOCK, "basic", () -> c.day, v -> c.day = v),
		mod("fps", Items.REDSTONE, "advanced", () -> c.fps, v -> c.fps = v),
		mod("ping", Items.ENDER_PEARL, "advanced", () -> c.ping, v -> c.ping = v),
		mod("cps", Items.IRON_SWORD, "advanced", () -> c.cps, v -> c.cps = v),
		mod("keys", Items.OAK_BUTTON, "advanced", () -> c.keys, v -> c.keys = v));

	private static Mod mod(String id, net.minecraft.world.item.Item icon, String group, Supplier<Boolean> get, Consumer<Boolean> set) {
		return new Mod(id, new ItemStack(icon), group, get, set, new float[]{get.get() ? 1 : 0, 0});
	}

	private static final String[] CATS = {"hud", "inv", "world", "pvp", "sys"};
	private static final ItemStack[] CAT_ICON = {new ItemStack(Items.COMPASS), new ItemStack(Items.PURPLE_SHULKER_BOX), new ItemStack(Items.GRASS_BLOCK),
		new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.ENDER_EYE)};

	public NosSettingsScreen(Screen parent) {
		super(Component.translatable("nosclient.settings.title"));
		this.parent = parent;
	}

	// ---------- bố cục ----------
	private int pw() {
		return Math.min(width - 24, 560);
	}

	private int ph() {
		return Math.min(height - 24, 330);
	}

	private int px() {
		return (width - pw()) / 2;
	}

	private int py() {
		return (height - ph()) / 2;
	}

	private static final int SIDE = 128, CARD_H = 70, GAP = 8;

	private int cols() {
		return Math.max(1, (pw() - SIDE - 32 + GAP) / (120 + GAP));
	}

	private int cardW() {
		return (pw() - SIDE - 32 - (cols() - 1) * GAP) / cols();
	}

	/** Vị trí thẻ thứ i trong nhóm của nó; nhóm Nâng cao nằm dưới Cơ bản. */
	private int[] cardPos(int i) {
		Mod m = mods.get(i);
		int basic = (int) mods.stream().filter(x -> x.group.equals("basic")).count();
		int idx = m.group.equals("basic") ? i : i - basic;
		int rowsBasic = (basic + cols() - 1) / cols();
		int top = py() + 52 + 12 + (m.group.equals("basic") ? 0 : rowsBasic * (CARD_H + GAP) + 18);
		int x = px() + SIDE + 16 + (idx % cols()) * (cardW() + GAP), y = top + (idx / cols()) * (CARD_H + GAP);
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
		Ui.mica(g, x, y, x + w, y + h, 14, Ui.alpha(0xB8141821, in));
		// cột trái
		g.fill(x + SIDE, y + 1, x + SIDE + 1, y + h - 1, 0x14FFFFFF);
		Ui.round(g, x + 1, y + 1, x + SIDE, y + h - 1, 13, 0x1A000000);
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, x + 12, y + 12, 0, 0, 22, 22, 22, 22);
		Ui.text(g, Ui.bold(Component.literal("Nos client")), x + 40, y + 13, Ui.TEXT);
		Ui.text(g, Ui.small(Component.literal("NOSTALGIA · R-SHIFT")), x + 40, y + 24, Ui.FAINT);
		Ui.text(g, Ui.small(Component.translatable("nosclient.settings.cats")), x + 12, y + 46, Ui.FAINT);
		for (int i = 0; i < CATS.length; i++) {
			int cy = y + 58 + i * 22;
			boolean sel = i == 0, live = i == 0;
			if (sel) {
				Ui.round(g, x + 8, cy, x + SIDE - 8, cy + 19, 6, 0x405E3A8A);
				g.fill(x + 2, cy + 4, x + 4, cy + 15, Ui.ACCENT);
			}
			g.pose().pushMatrix();
			g.pose().translate(x + 14, cy + 3);
			g.pose().scale(.8f, .8f);
			g.renderFakeItem(CAT_ICON[i], 0, 0);
			g.pose().popMatrix();
			Ui.text(g, Ui.ui(Component.translatable("nosclient.cat." + CATS[i])), x + 32, cy + 5, live ? Ui.TEXT : Ui.FAINT);
			Component badge = Ui.small(Component.literal(live ? count() + "/" + mods.size() : "—"));
			int bw = Ui.width(badge) + 8;
			Ui.round(g, x + SIDE - 12 - bw, cy + 4, x + SIDE - 12, cy + 15, 4, sel ? 0x40B06EE6 : 0x14FFFFFF);
			Ui.text(g, badge, x + SIDE - 12 - bw + 4, cy + 6, sel ? 0xFFEAD4FB : Ui.FAINT);
		}
		int on = count();
		Ui.text(g, Ui.small(Component.translatable("nosclient.settings.on")), x + 12, y + h - 36, Ui.MUTED);
		Component n = Ui.bold(Component.literal(on + "/" + mods.size()));
		Ui.text(g, n, x + SIDE - 12 - Ui.width(n), y + h - 37, Ui.TEXT);
		Ui.round(g, x + 12, y + h - 24, x + SIDE - 12, y + h - 21, 2, 0x1FFFFFFF);
		int fw = Math.round((SIDE - 24) * on / (float) mods.size());
		if (fw > 2) Ui.hgrad(g, x + 12, y + h - 24, x + 12 + fw, y + h - 21, Ui.ACC0, Ui.ACC1);
		// phần phải
		int rx = x + SIDE + 16;
		Ui.text(g, Ui.big(Component.translatable("nosclient.cat.hud")), rx, y + 14, Ui.TEXT);
		Ui.text(g, Ui.small(Component.translatable("nosclient.cat.hud.sub")), rx, y + 34, Ui.MUTED);
		Ui.text(g, Ui.small(Component.translatable("nosclient.group.basic")), rx, py() + 52 + 2 + Math.round((1 - in) * 10), Ui.FAINT);
		int adv = cardPos((int) mods.stream().filter(m -> m.group.equals("basic")).count())[1];
		Ui.text(g, Ui.small(Component.translatable("nosclient.group.advanced")), rx, adv - 12 + Math.round((1 - in) * 10), Ui.FAINT);
		for (int i = 0; i < mods.size(); i++) card(g, i, mx, my, dt, t, Math.round((1 - in) * 10));
	}

	private int count() {
		return (int) mods.stream().filter(m -> m.get.get()).count();
	}

	private void card(GuiGraphics g, int i, int mx, int my, float dt, float t, int dy) {
		Mod m = mods.get(i);
		int[] p = cardPos(i);
		int x = p[0], y = p[1] + dy, w = cardW(), h = CARD_H;
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h, on = m.get.get();
		m.k[0] = Ui.approach(m.k[0], on ? 1 : 0, dt, 14);
		m.k[1] = Ui.approach(m.k[1], hov ? 1 : 0, dt, 12);
		float k = Ui.ease(m.k[0]), in = Ui.ease(Math.min(1, Math.max(0, (t - .03f * i) / .4f)));
		int base = Ui.lerpCol(0x0FFFFFFF, 0x2E8A5BC4, k), top = Ui.lerpCol(base, Ui.lerpCol(0x1AFFFFFF, 0x408A5BC4, k), m.k[1]);
		Ui.round(g, x, y, x + w, y + h, 9, Ui.alpha(top, in));
		Ui.outline(g, x, y, x + w, y + h, 9, Ui.alpha(Ui.lerpCol(0x14FFFFFF, 0x59D3A8F5, k), in));
		Ui.round(g, x + 10, y + 10, x + 30, y + 30, 6, Ui.alpha(0x1FFFFFFF, in));
		g.renderFakeItem(m.icon, x + 12, y + 12);
		Ui.toggle(g, x + w - 10 - 26, y + 10, k);
		Ui.text(g, Ui.bold(Component.translatable("nosclient.mod." + m.id)), x + 10, y + 36, Ui.alpha(Ui.TEXT, in));
		var lines = font.split(Ui.small(Component.translatable("nosclient.mod." + m.id + ".desc")), w - 20);
		for (int l = 0; l < Math.min(2, lines.size()); l++) g.drawString(font, lines.get(l), x + 10, y + 47 + l * 8, Ui.alpha(Ui.MUTED, in), false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		for (int i = 0; i < mods.size(); i++) {
			int[] p = cardPos(i);
			if (e.x() >= p[0] && e.x() < p[0] + cardW() && e.y() >= p[1] && e.y() < p[1] + CARD_H) {
				Mod m = mods.get(i);
				m.set.accept(!m.get.get());
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
