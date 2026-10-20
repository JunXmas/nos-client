package dev.jun.nosclient;

import dev.jun.nosclient.gui.NosSettingsScreen;
import dev.jun.nosclient.gui.NosTitleScreen;
import dev.jun.nosclient.hud.Hud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class NosClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		dev.jun.nosclient.feat.Feats.init();
		// HUD vẽ ngay sau tâm ngắm: dưới chat/menu tạm dừng, trên thế giới
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Identifier.fromNamespaceAndPath("nosclient", "hud"), Hud.INSTANCE::render);
		// bật Hiệu ứng thuốc của Nos client thì ẩn ô hiệu ứng gốc ở góc phải, khỏi hiện hai lần
		HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, old -> (g, dt) -> {
			if (!dev.jun.nosclient.scene.SceneConfig.INSTANCE.effects) old.render(g, dt);
		});

		// màn chính vanilla → màn chính Nos client (giữ đủ chức năng: chơi đơn, mạng, tuỳ chọn, thoát)
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof TitleScreen) mc.setScreen(new NosTitleScreen());
		});

		// R-Shift trong game mở cài đặt Nos client
		KeyMapping open = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.settings", GLFW.GLFW_KEY_RIGHT_SHIFT,
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nosclient", "main"))));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (open.consumeClick()) if (mc.screen == null) mc.setScreen(new NosSettingsScreen(null));
		});
	}
}
