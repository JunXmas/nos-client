package dev.jun.nosclient;

import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.scene.SceneRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public final class NosClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// bảng "Cảnh nền" ở góc phải màn chính: anh đào, mía, shader, hoàng hôn – đêm
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof TitleScreen)) return;
			SceneConfig c = SceneConfig.INSTANCE;
			int bw = 150, x = w - bw - 8, y = 8;
			var list = Screens.getButtons(screen);
			list.add(CycleButton.onOffBuilder(c.blossom).create(x, y, bw, 20, Component.translatable("nosclient.scene.blossom"),
				(b, v) -> SceneRenderer.INSTANCE.toggle(1, v)));
			list.add(CycleButton.onOffBuilder(c.cane).create(x, y + 22, bw, 20, Component.translatable("nosclient.scene.cane"),
				(b, v) -> SceneRenderer.INSTANCE.toggle(2, v)));
			list.add(CycleButton.onOffBuilder(c.fx).create(x, y + 44, bw, 20, Component.translatable("nosclient.scene.shader"), (b, v) -> {
				c.fx = v;
				c.save();
			}));
			list.add(new TodSlider(x, y + 66, bw, c));
		});
	}

	/** 0 = hoàng hôn (tod 40) … 1 = đêm (tod 100). */
	private static final class TodSlider extends AbstractSliderButton {
		private final SceneConfig c;

		TodSlider(int x, int y, int w, SceneConfig c) {
			super(x, y, w, 20, Component.empty(), (c.tod - 40) / 60);
			this.c = c;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			double t = 40 + value * 60;
			setMessage(Component.translatable(t < 62 ? "nosclient.scene.sunset" : t < 80 ? "nosclient.scene.dusk" : "nosclient.scene.night"));
		}

		@Override
		protected void applyValue() {
			c.tod = 40 + value * 60;
		}

		@Override
		public void onRelease(net.minecraft.client.input.MouseButtonEvent e) {
			super.onRelease(e);
			c.save();
		}
	}
}
