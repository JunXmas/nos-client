package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Màn chính của Nos client theo preview: logo Nostalgia bên trái, menu kính mica có icon + dòng phụ,
 * chip tài khoản và bảng "Cảnh nền" bên phải, chân trang phiên bản. Nền là cảnh hồ 3D (panorama đã thay).
 * Mọi nút mở đúng màn của vanilla, nên không mất chức năng nào so với màn gốc.
 */
public final class NosTitleScreen extends Screen {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("nosclient", "textures/gui/logo.png");
	private final long opened = System.nanoTime();
	private final int splash = (int) (System.currentTimeMillis() / 4000 % 6);
	private float t;
	private PlayerSkin skin = DefaultPlayerSkin.getDefaultSkin();

	public NosTitleScreen() {
		super(Component.translatable("narrator.screen.title"));
	}

	/** Bố cục co theo màn: nút cao 32 → 24, khoảng cách 6 → 3 khi màn thấp; logo luôn nằm trên menu, menu luôn trên chân trang. */
	private int bx, bw, bh, gap, menuY, logoY, panelX;

	private void layout() {
		bx = Math.max(12, width / 18);
		bw = Math.min(260, Math.max(160, width / 3));
		int avail = height - 16 - 14; // trừ lề trên và dòng chân trang
		bh = avail >= 360 ? 38 : avail >= 300 ? 32 : 26;
		gap = avail >= 360 ? 8 : avail >= 300 ? 6 : 4;
		int menu = 5 * (bh + gap) + 22, logo = 62;
		int top = Math.max(10, (avail - logo - menu) / 2 + 4);
		logoY = top;
		menuY = top + logo;
	}

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		mc.getSkinManager().get(mc.getGameProfile()).thenAccept(o -> o.ifPresent(s -> skin = s));
		layout();
		int x = bx, w = bw, h = bh, y = menuY;
		List<Item> items = new ArrayList<>();
		items.add(new Item(new ItemStack(Items.GRASS_BLOCK), "nosclient.menu.single", "nosclient.menu.single.sub", "Enter", true,
			() -> mc.setScreen(new SelectWorldScreen(this))));
		items.add(new Item(new ItemStack(Items.COMPASS), "nosclient.menu.multi", "nosclient.menu.multi.sub", null, false,
			() -> mc.setScreen(new JoinMultiplayerScreen(this))));
		items.add(new Item(new ItemStack(Items.ENDER_EYE), "nosclient.menu.together", "nosclient.menu.together.sub", null, false,
			() -> mc.setScreen(new JoinMultiplayerScreen(this)))); // ponytail: "Chơi chung" của launcher chưa có cầu nối trong game; tạm mở danh sách máy chủ
		items.add(new Item(new ItemStack(Items.CHERRY_LEAVES), "nosclient.menu.nos", "nosclient.menu.nos.sub", "R-Shift", false,
			() -> mc.setScreen(new NosSettingsScreen(this))));
		items.add(new Item(new ItemStack(Items.REDSTONE), "nosclient.menu.options", "nosclient.menu.options.sub", null, false,
			() -> mc.setScreen(new OptionsScreen(this, mc.options))));
		for (int i = 0; i < items.size(); i++) addRenderableWidget(new MenuButton(x, y + i * (h + gap), w, h, items.get(i), i));
		addRenderableWidget(new QuitLink(x + 4, y + items.size() * (h + gap) + 2));
		// bảng cảnh nền: vừa phần trống bên phải, dưới chip tài khoản, không đè chân trang
		float free = width - (x + w) - 28, scale = Math.min(1, Math.min(free / ScenePanel.W, (height - 48 - 20) / (float) ScenePanel.H));
		panelX = scale >= .45f ? Math.round(width - ScenePanel.W * scale - 10) : width;
		if (panelX < width) addRenderableWidget(new ScenePanel(panelX, 48, scale));
	}

	private record Item(ItemStack icon, String title, String sub, String key, boolean primary, Runnable action) {}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		t = (System.nanoTime() - opened) / 1e9f;
		super.render(g, mx, my, pt);
		int x = bx;
		// logo: khối lá + "Nostalgia" (gia xanh) + LAUNCHER giãn chữ, trượt vào khi mở màn
		float k = Ui.ease(Math.min(1, t / .6f));
		int ly = logoY + Math.round((1 - k) * -12);
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, ly, 0, 0, 40, 40, 40, 40);
		Component nos = Ui.logo("Nostal"), gia = Ui.logo("gia");
		int tx = x + 48;
		Ui.text(g, nos, tx, ly + 4, Ui.alpha(Ui.TEXT, k));
		Ui.text(g, gia, tx + Ui.width(nos), ly + 4, Ui.alpha(Ui.BRAND, k));
		Component sub = Ui.small(Component.literal("L A U N C H E R"));
		Ui.text(g, sub, tx + 2, ly + 38, Ui.alpha(Ui.MUTED, k));
		// splash vàng xoay như vanilla
		Component sp = Component.translatable("nosclient.splash." + splash);
		g.pose().pushMatrix();
		g.pose().translate(tx + Ui.width(nos) + Ui.width(gia) + 8, ly + 34);
		g.pose().rotate((float) Math.toRadians(-12));
		float fit = Math.min(1, (panelX - 16 - (tx + Ui.width(nos) + Ui.width(gia) + 8)) / (float) Math.max(1, font.width(sp)));
		float s = fit * (1 + (float) Math.abs(Math.sin(t * 4.5)) * .06f);
		if (fit < .7f) s = 0; // không đủ chỗ thì bỏ splash, không để đè bảng
		g.pose().scale(s, s);
		g.drawString(font, sp, 0, 0, 0xFFFFFF00, true);
		g.pose().popMatrix();
		// chip tài khoản góc phải trên
		Minecraft mc = Minecraft.getInstance();
		Component name = Ui.bold(Component.literal(mc.getUser().getName())), kind = Ui.small(Component.translatable("nosclient.account.offline"));
		int cw = 8 + 18 + 10 + Math.max(Ui.width(name), Ui.width(kind)) + 22, cx = width - cw - 12, cy = 10;
		Ui.panel(g, cx, cy, cx + cw, cy + 30, 0);
		PlayerFaceRenderer.draw(g, skin, cx + 8, cy + 6, 18);
		Ui.text(g, name, cx + 36, cy + 6, Ui.TEXT);
		Ui.text(g, kind, cx + 36, cy + 17, Ui.MUTED);
		g.fill(cx + cw - 12, cy + 13, cx + cw - 8, cy + 17, Ui.BRAND);
		// chân trang
		Component ver = Ui.small(Component.literal("Minecraft " + SharedConstants.getCurrentVersion().name() + " · Fabric · Nos client 0.1"));
		Ui.text(g, ver, x, height - 12, Ui.FAINT);
		Component legal = Ui.small(Component.translatable("nosclient.legal"));
		if (x + Ui.width(ver) + 16 + Ui.width(legal) + 12 <= width) Ui.text(g, legal, width - Ui.width(legal) - 12, height - 12, Ui.FAINT);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		renderPanorama(g, pt);
		// một dải tối mềm sau cột menu cho chữ nổi trên cảnh (1 quad chuyển màu, xoay ngang bằng pose)
		g.pose().pushMatrix();
		g.pose().translate(width / 2f, 0);
		g.pose().rotate((float) Math.toRadians(-90));
		g.pose().translate(-height, -width / 2f);
		g.fillGradient(0, 0, height, width / 2, 0x00000000, 0x8C07090D);
		g.pose().popMatrix();
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == GLFW.GLFW_KEY_ENTER) {
			Minecraft.getInstance().setScreen(new SelectWorldScreen(this));
			return true;
		}
		if (e.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			Minecraft.getInstance().setScreen(new NosSettingsScreen(this));
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
	}

	/** Nút menu kính mica: icon khối trong ô bo góc, tiêu đề + dòng phụ, phím tắt bên phải; nút chính màu tím. */
	private final class MenuButton extends AbstractWidget {
		private final Item it;
		private final int idx;
		private float hover;

		MenuButton(int x, int y, int w, int h, Item it, int idx) {
			super(x, y, w, h, Component.translatable(it.title));
			this.it = it;
			this.idx = idx;
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
			hover = Ui.approach(hover, isHoveredOrFocused() ? 1 : 0, 1 / 60f, 12);
			float in = Ui.ease(Math.min(1, Math.max(0, (t - .08f * idx) / .5f)));
			int dx = Math.round((1 - in) * -24 + hover * 3), x = getX() + dx, y = getY(), w = width, h = height;
			int tint = it.primary ? Ui.lerpCol(0xB3734AA8, 0xCC8A5BC4, hover) : Ui.lerpCol(0x99101218, 0xB3262D3A, hover);
			Ui.panel(g, x, y, x + w, y + h, Ui.alpha(tint, in));
			if (hover > .02f) { // vạch tím bên trái như .mb:before của preview
				int half = Math.round((h / 2f - 8) * Ui.ease(hover));
				g.fill(x - 1, y + h / 2 - half - 1, x + 4, y + h / 2 + half + 1, Ui.alpha(0x59B06EE6, hover));
				g.fill(x, y + h / 2 - half, x + 2, y + h / 2 + half, Ui.ACCENT);
			}
			// mọi toạ độ theo chiều cao thật của nút (24–32): màn thấp thì nút co, chữ không chồng
			int ic = Math.min(22, h - 12), iy = y + (h - ic) / 2;
			g.fill(x + 10, iy, x + 10 + ic, iy + ic, it.primary ? 0x33FFFFFF : 0x1FFFFFFF);
			g.pose().pushMatrix();
			g.pose().translate(x + 10 + ic / 2f, iy + ic / 2f);
			g.pose().scale(ic / 22f, ic / 22f);
			g.renderFakeItem(it.icon, -8, -8);
			g.pose().popMatrix();
			int tx = x + 20 + ic, right = x + w - 12;
			Component k = it.key != null ? Ui.small(Component.literal(it.key)) : null;
			if (k != null) { // nhãn phím tắt ở góc phải, dòng chữ phải dừng trước nó
				int kw = Ui.width(k) + 12, kx = right - kw, ky = y + (h - 13) / 2;
				g.fill(kx, ky, kx + kw, ky + 13, 0x33000000);
				Ui.frame(g, kx, ky, kx + kw, ky + 13, 0x1AFFFFFF);
				Ui.text(g, k, kx + 6, ky + 3, Ui.MUTED);
				right = kx - 10;
			}
			Component title = Ui.bold(Component.translatable(it.title));
			if (h >= 32) {
				Ui.text(g, title, tx, y + h / 2 - 10, Ui.alpha(Ui.TEXT, in));
				Ui.text(g, Ui.fit(Ui.small(Component.translatable(it.sub)), right - tx), tx, y + h / 2 + 3, Ui.alpha(it.primary ? 0xD9EAD4FB : Ui.MUTED, in));
			} else Ui.text(g, title, tx, y + (h - 8) / 2, Ui.alpha(Ui.TEXT, in)); // nút thấp: bỏ dòng phụ thay vì nhồi
		}

		@Override
		public void onClick(MouseButtonEvent e, boolean dbl) {
			click();
			it.action.run();
		}

		@Override
		public void playDownSound(net.minecraft.client.sounds.SoundManager m) {}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			defaultButtonNarrationText(out);
		}
	}

	/** "Thoát game" dạng chữ mờ như preview, có hỏi lại trước khi thoát. */
	private final class QuitLink extends AbstractWidget {
		QuitLink(int x, int y) {
			super(x, y, 80, 12, Component.translatable("nosclient.menu.quit"));
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
			Ui.text(g, Ui.ui(Component.translatable("nosclient.menu.quit")), getX(), getY() + 2, isHoveredOrFocused() ? 0xFFF2555A : Ui.MUTED);
		}

		@Override
		public void onClick(MouseButtonEvent e, boolean dbl) {
			click();
			Minecraft mc = Minecraft.getInstance();
			mc.setScreen(new ConfirmScreen(ok -> {
				if (ok) mc.stop();
				else mc.setScreen(NosTitleScreen.this);
			}, Component.translatable("nosclient.menu.quit"), Component.translatable("nosclient.menu.quit.confirm")));
		}

		@Override
		public void playDownSound(net.minecraft.client.sounds.SoundManager m) {}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			defaultButtonNarrationText(out);
		}
	}
}
