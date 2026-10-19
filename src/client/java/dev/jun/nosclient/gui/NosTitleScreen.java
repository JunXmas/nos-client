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

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		mc.getSkinManager().get(mc.getGameProfile()).thenAccept(o -> o.ifPresent(s -> skin = s));
		int x = Math.max(16, width / 18), w = Math.min(240, width / 3), h = 32, gap = 6;
		int y = Math.max(height / 2 - 70, 96);
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
		addRenderableWidget(new QuitLink(x + 4, y + items.size() * (h + gap) + 6));
		float free = width - (x + w) - 24;
		float scale = Math.min(1, free / ScenePanel.W);
		addRenderableWidget(new ScenePanel(Math.round(width - ScenePanel.W * scale - 12), 40, scale));
	}

	private record Item(ItemStack icon, String title, String sub, String key, boolean primary, Runnable action) {}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		t = (System.nanoTime() - opened) / 1e9f;
		super.render(g, mx, my, pt);
		int x = Math.max(16, width / 18);
		// logo: khối lá + "Nostalgia" (gia xanh) + LAUNCHER giãn chữ, trượt vào khi mở màn
		float k = Ui.ease(Math.min(1, t / .6f));
		int ly = Math.max(14, height / 2 - 70 - 64) + Math.round((1 - k) * -12);
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, ly, 0, 0, 40, 40, 40, 40);
		Component nos = Ui.logo("Nostal"), gia = Ui.logo("gia");
		int tx = x + 48;
		Ui.text(g, nos, tx, ly + 4, Ui.alpha(Ui.TEXT, k));
		Ui.text(g, gia, tx + Ui.width(nos), ly + 4, Ui.alpha(Ui.BRAND, k));
		Component sub = Ui.small(Component.literal("L A U N C H E R"));
		Ui.text(g, sub, tx + 2, ly + 32, Ui.alpha(Ui.MUTED, k));
		// splash vàng xoay như vanilla
		Component sp = Component.translatable("nosclient.splash." + splash);
		g.pose().pushMatrix();
		g.pose().translate(tx + Ui.width(nos) + Ui.width(gia) + 10, ly + 30);
		g.pose().rotate((float) Math.toRadians(-12));
		float s = 1 + (float) Math.abs(Math.sin(t * 4.5)) * .06f;
		g.pose().scale(s, s);
		g.drawString(font, sp, 0, 0, 0xFFFFFF00, true);
		g.pose().popMatrix();
		// chip tài khoản góc phải trên
		Minecraft mc = Minecraft.getInstance();
		Component name = Ui.bold(Component.literal(mc.getUser().getName())), kind = Ui.small(Component.translatable("nosclient.account.offline"));
		int cw = 34 + Math.max(Ui.width(name), Ui.width(kind)) + 18, cx = width - cw - 12, cy = 10;
		Ui.mica(g, cx, cy, cx + cw, cy + 24, 7, 0);
		PlayerFaceRenderer.draw(g, skin, cx + 5, cy + 4, 16);
		Ui.text(g, name, cx + 26, cy + 4, Ui.TEXT);
		Ui.text(g, kind, cx + 26, cy + 14, Ui.MUTED);
		Ui.round(g, cx + cw - 9, cy + 10, cx + cw - 5, cy + 14, 2, Ui.BRAND);
		// chân trang
		Component ver = Ui.small(Component.literal("Minecraft " + SharedConstants.getCurrentVersion().name() + " · Fabric · Nos client 0.1"));
		Ui.text(g, ver, x, height - 12, Ui.FAINT);
		Component legal = Ui.small(Component.translatable("nosclient.legal"));
		Ui.text(g, legal, width - Ui.width(legal) - 12, height - 12, Ui.FAINT);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		renderPanorama(g, pt);
		// tối dần sang trái để chữ menu nổi trên cảnh như .shade của preview
		int w = width;
		for (int i = 0; i < 8; i++) {
			int x0 = i * w / 16, x1 = (i + 1) * w / 16;
			g.fill(x0, 0, x1, height, ((int) (150 * (1 - i / 8.0) * (1 - i / 8.0))) << 24 | 0x07090D);
		}
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
			int tint = it.primary ? Ui.lerpCol(0xD9734AA8, 0xE68A5BC4, hover) : Ui.lerpCol(0xA6141821, 0xBF262D3A, hover);
			Ui.mica(g, x, y, x + w, y + h, 8, Ui.alpha(tint, in));
			if (it.primary) Ui.outline(g, x, y, x + w, y + h, 8, Ui.alpha(0x66D3A8F5, in));
			if (hover > .02f) { // vạch tím bên trái như .mb:before của preview
				int half = Math.round((h / 2f - 8) * Ui.ease(hover));
				Ui.round(g, x - 1, y + h / 2 - half - 1, x + 4, y + h / 2 + half + 1, 2, Ui.alpha(0x59B06EE6, hover));
				g.fill(x, y + h / 2 - half, x + 2, y + h / 2 + half, Ui.ACCENT);
			}
			Ui.round(g, x + 6, y + 6, x + 26, y + 26, 6, it.primary ? 0x33FFFFFF : 0x1FFFFFFF);
			g.renderFakeItem(it.icon, x + 8, y + 8);
			Ui.text(g, Ui.bold(Component.translatable(it.title)), x + 34, y + 7, Ui.alpha(Ui.TEXT, in));
			Ui.text(g, Ui.small(Component.translatable(it.sub)), x + 34, y + 19, Ui.alpha(it.primary ? 0xD9EAD4FB : Ui.MUTED, in));
			if (it.key != null) {
				Component k = Ui.small(Component.literal(it.key));
				int kw = Ui.width(k) + 10, kx = x + w - kw - 8;
				Ui.round(g, kx, y + 10, kx + kw, y + 22, 4, 0x33000000);
				Ui.outline(g, kx, y + 10, kx + kw, y + 22, 4, 0x1AFFFFFF);
				Ui.text(g, k, kx + 5, y + 12, Ui.MUTED);
			}
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
