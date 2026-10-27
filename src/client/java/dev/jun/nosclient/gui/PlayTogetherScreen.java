package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

@Environment(EnvType.CLIENT)
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
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        int w = 220;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 50;
        if (server != null) {
            this.mode = server.getDefaultGameType();
        }

        this.addRenderableWidget(CycleButton.onOffBuilder(this.c.mpOffline).create(x, y, w, 20, Component.translatable("nosclient.mp.offline"), (b, v) -> {
            this.c.mpOffline = v;
            this.c.save();
        }));
        this.addRenderableWidget(CycleButton.onOffBuilder(this.c.mpPvp).create(x, y + 24, w, 20, Component.translatable("nosclient.mp.pvp"), (b, v) -> {
            this.c.mpPvp = v;
            this.c.save();
        }));
        this.addRenderableWidget(
            CycleButton.builder(GameType::getShortDisplayName, this.mode)
                .withValues(GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR)
                .create(x, y + 48, w, 20, Component.translatable("selectWorld.gameMode"), (b, v) -> this.mode = v)
        );
        this.addRenderableWidget(
            CycleButton.onOffBuilder(this.cheats).create(x, y + 72, w, 20, Component.translatable("selectWorld.allowCommands"), (b, v) -> this.cheats = v)
        );
        boolean published = server != null && server.isPublished();
        Button open = Button.builder(Component.translatable(published ? "nosclient.mp.apply" : "nosclient.mp.open"), b -> this.open())
            .bounds(x, y + 104, w / 2 - 2, 20)
            .build();
        open.active = server != null;
        this.addRenderableWidget(open);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> this.onClose()).bounds(x + w / 2 + 2, y + 104, w / 2 - 2, 20).build());
        if (published) {
            this.status = Component.translatable("nosclient.mp.running", server.getPort());
        } else if (server == null) {
            this.status = Component.translatable("nosclient.mp.notsp");
        }
    }

    private void open() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.setUsesAuthentication(!this.c.mpOffline);
            server.getWorldData().getGameRules().set(GameRules.PVP, this.c.mpPvp, server);
            if (server.isPublished()) {
                this.status = Component.translatable("nosclient.mp.running", server.getPort());
            } else {
                int port = HttpUtil.getAvailablePort();
                if (server.publishServer(this.mode, this.cheats, port)) {
                    this.status = Component.translatable("nosclient.mp.running", port);
                    mc.gui
                        .getChat()
                        .addMessage(
                            Component.translatable(
                                "nosclient.mp.chat", port, this.c.mpOffline ? Component.translatable("nosclient.mp.chat.offline") : Component.empty()
                            )
                        );
                    this.rebuildWidgets();
                } else {
                    this.status = Component.translatable("commands.publish.failed");
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int w = 260;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 92;
        Ui.text(g, Ui.big(Component.translatable("nosclient.mp.title")), x, y, Ui.TEXT);
        Ui.text(g, Ui.fit(Ui.small(Component.translatable("nosclient.mp.sub")), w), x, y + 22, Ui.MUTED);
        Ui.text(g, Ui.fit(Ui.ui(this.status), w), this.width / 2 - Math.min(w, Ui.width(Ui.ui(this.status))) / 2, this.height / 2 + 80, Ui.ACCENT_HI);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
