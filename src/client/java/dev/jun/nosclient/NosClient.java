package dev.jun.nosclient;

import dev.jun.nosclient.hud.Hud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.TitleScreen;

public final class NosClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// HUD vẽ ngay sau tâm ngắm: dưới chat/menu tạm dừng, trên thế giới
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Identifier.fromNamespaceAndPath("nosclient", "hud"), Hud.INSTANCE::render);
		// bảng "Cảnh nền" kiểu mica ở góc phải màn chính
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof TitleScreen)) return;
			// nút giữa màn chính rộng 200 (+ nút trợ năng 24): bảng chỉ dùng phần còn trống bên phải
			float free = w - (w / 2f + 100 + 24 + 6) - 8, scale = Math.min(1, Math.max(.5f, free / ScenePanel.W));
			// màn hẹp thì chữ splash vàng tràn sang góc phải trên: hạ bảng xuống ngay trên dòng bản quyền
			int y = scale < 1 ? Math.round(h - ScenePanel.H * scale - 14) : 8;
			Screens.getButtons(screen).add(new ScenePanel(Math.round(w - ScenePanel.W * scale - 8), y, scale));
		});
	}
}
