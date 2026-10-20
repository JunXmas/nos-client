package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Chơi chung (LAN): mở thế giới đang chơi cho bạn bè cùng mạng (LAN, Radmin, ZeroTier…).
 * Khác "Mở cho LAN" của game: cho tài khoản offline vào (tắt kiểm tra đăng nhập — vanilla luôn bật nên offline
 * bị đá "Invalid session"), và bật/tắt PvP. Relay qua Internet là phần của launcher, chưa có ở đây.
 */
public final class PlayTogetherScreen extends Screen {
	private final Screen parent;
	private final SceneConfig c = SceneConfig.INSTANCE;
	private GameType mode = GameType.SURVIVAL;
	private boolean cheats;
	private Component status = Component.empty();

	public PlayTogetherScreen(Screen parent) {
		super(Component.translatable("nosclient.mp.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		var server = Minecraft.getInstance().getSingleplayerServer();
		int w = 220, x = width / 2 - w / 2, y = height / 2 - 50;
		if (server != null) mode = server.getDefaultGameType();
		addRenderableWidget(CycleButton.onOffBuilder(c.mpOffline).create(x, y, w, 20, Component.translatable("nosclient.mp.offline"), (b, v) -> {
			c.mpOffline = v;
			c.save();
		}));
		addRenderableWidget(CycleButton.onOffBuilder(c.mpPvp).create(x, y + 24, w, 20, Component.translatable("nosclient.mp.pvp"), (b, v) -> {
			c.mpPvp = v;
			c.save();
		}));
		addRenderableWidget(CycleButton.builder(GameType::getShortDisplayName, mode).withValues(GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR)
			.create(x, y + 48, w, 20, Component.translatable("selectWorld.gameMode"), (b, v) -> mode = v));
		addRenderableWidget(CycleButton.onOffBuilder(cheats).create(x, y + 72, w, 20, Component.translatable("selectWorld.allowCommands"), (b, v) -> cheats = v));
		boolean published = server != null && server.isPublished();
		Button open = Button.builder(Component.translatable(published ? "nosclient.mp.apply" : "nosclient.mp.open"), b -> open()).bounds(x, y + 104, w / 2 - 2, 20).build();
		open.active = server != null;
		addRenderableWidget(open);
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(x + w / 2 + 2, y + 104, w / 2 - 2, 20).build());
		if (published) status = Component.translatable("nosclient.mp.running", server.getPort());
		else if (server == null) status = Component.translatable("nosclient.mp.notsp");
	}

	private void open() {
		Minecraft mc = Minecraft.getInstance();
		var server = mc.getSingleplayerServer();
		if (server == null) return;
		// áp các luật trước khi mở cổng: online-mode tắt thì tài khoản offline (UUID offline) vào được
		server.setUsesAuthentication(!c.mpOffline);
		server.getWorldData().getGameRules().set(GameRules.PVP, c.mpPvp, server);
		if (server.isPublished()) {
			status = Component.translatable("nosclient.mp.running", server.getPort());
			return;
		}
		int port = HttpUtil.getAvailablePort();
		if (server.publishServer(mode, cheats, port)) {
			status = Component.translatable("nosclient.mp.running", port);
			mc.gui.getChat().addMessage(Component.translatable("nosclient.mp.chat", port, c.mpOffline ? Component.translatable("nosclient.mp.chat.offline") : Component.empty()));
			rebuildWidgets();
		} else status = Component.translatable("commands.publish.failed");
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		super.render(g, mx, my, pt);
		int w = 260, x = width / 2 - w / 2, y = height / 2 - 92;
		Ui.text(g, Ui.big(Component.translatable("nosclient.mp.title")), x, y, Ui.TEXT);
		Ui.text(g, Ui.fit(Ui.small(Component.translatable("nosclient.mp.sub")), w), x, y + 22, Ui.MUTED);
		Ui.text(g, Ui.fit(Ui.ui(status), w), width / 2 - Math.min(w, Ui.width(Ui.ui(status))) / 2, height / 2 + 80, Ui.ACCENT_HI);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}
}
