package dev.jun.nosclient.gui;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.cosmetic.CosmeticLook;
import dev.jun.nosclient.cosmetic.Cosmetics;
import dev.jun.nosclient.scene.SceneConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Native player try-on, explicit session-only apply, no premium/ownership claims. */
public final class CosmeticScreen extends Screen {
	private final Screen parent;
	private final MenuMotion motion = new MenuMotion();
	private CosmeticLook look = Cosmetics.applied().any() ? Cosmetics.applied() : new CosmeticLook("angel", true, true, true);
	private int left, top, w, h, divider, previewX, previewY, previewW, previewH;
	private float rotation = 24;
	private boolean dragging;
	private GlassButton[] pieces;
	public CosmeticScreen(Screen parent) { super(Component.translatable("nosclient.cosmetic.title")); this.parent = parent; }
	public CosmeticLook draft() { return look; }
	private Component tr(String key) { return Component.translatable("nosclient.cosmetic." + key); }
	private void updatePieces() {
		String[] slots = {"wings", "cape", "headband"};
		for (int i = 0; i < slots.length; i++) pieces[i].setMessage(Component.literal(look.visible(slots[i]) ? "✓ " : "").append(tr(slots[i])));
	}
	@Override protected void init() {
		motion.enter(); dragging = false;
		w = Math.min(460, width - 20); h = Math.min(278, height - 20); left = (width - w) / 2; top = (height - h) / 2;
		divider = Math.max(112, Math.min(142, w / 3));
		previewX = left + divider + 8; previewY = top + 31;
		previewW = w - divider - 20; previewH = h - 78;
		for (int i = 0; i < 2; i++) {
			String theme = i == 0 ? "angel" : "crimson";
			addRenderableWidget(new GlassButton(left + 12, top + 40 + i * 26, divider - 12, 21, tr(theme), () -> {
				look = new CosmeticLook(theme, look.wings(), look.cape(), look.headband());
			}));
		}
		pieces = new GlassButton[3];
		for (int i = 0; i < 3; i++) {
			int slot = i;
			pieces[i] = addRenderableWidget(new GlassButton(left + 12, top + 105 + i * 23, divider - 12, 19, Component.empty(), () -> {
				look = new CosmeticLook(look.theme(), slot == 0 ? !look.wings() : look.wings(),
					slot == 1 ? !look.cape() : look.cape(), slot == 2 ? !look.headband() : look.headband());
				updatePieces();
			}));
		}
		updatePieces();
		if (h >= 250) addRenderableWidget(new GlassButton(left + 12, top + 182, divider - 12, 18, tr("rotate"), () -> rotation = rotation < 90 ? 180 : 24));
		addRenderableWidget(new GlassButton(left + 12, top + h - 28, 65, 19, tr("clear"), () -> { Cosmetics.tryOn(CosmeticLook.NONE); onClose(); }));
		int bw = Math.min(126, (w - 112) / 2);
		var apply = addRenderableWidget(new GlassButton(left + w - bw - 12, top + h - 28, bw, 19, tr("apply"), () -> {
			Cosmetics.tryOn(look); onClose();
		}));
		apply.active = minecraft.player != null && Cosmetics.ready();
		addRenderableWidget(new GlassButton(left + w - bw - 90, top + h - 28, 72, 19, Component.translatable("gui.done"), this::onClose));
	}
	@Override public void tick() { motion.tick(); }
	@Override public void onClose() { if (!motion.closing()) { dragging = false; motion.exit(() -> minecraft.setScreen(parent)); } }
	@Override public boolean keyPressed(KeyEvent e) { return motion.closing() || super.keyPressed(e); }
	@Override public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
		if (motion.closing()) return true;
		if (event.button() == 0 && event.x() >= previewX && event.x() < previewX + previewW
			&& event.y() >= previewY && event.y() < previewY + previewH) { dragging = true; return true; }
		return super.mouseClicked(event, dbl);
	}
	@Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) { rotation = (rotation + (float) dx * 1.4f) % 360; return true; }
		return super.mouseDragged(event, dx, dy);
	}
	@Override public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) { dragging = false; return true; }
		return super.mouseReleased(event);
	}
	@Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
		if (minecraft.level == null) { if (SceneConfig.INSTANCE.menuAurora) AuroraBackground.render(g, width, height); else renderPanorama(g, pt); }
		else g.fill(0, 0, width, height, 0x77000000);
		Glass.panel(g, left, top, w, h, 0xF0141E22, 0x5EFFFFFF);
		Glass.panel(g, previewX, previewY, previewW, previewH, 0xA00C141D, 0x22FFFFFF);
	}
	@SuppressWarnings("unchecked")
	private void preview(GuiGraphics g, float pt) {
		AvatarRenderState state;
		if (minecraft.player == null) {
			state = new AvatarRenderState(); state.entityType = EntityType.PLAYER;
			state.id = Cosmetics.MENU_PREVIEW_ID; state.skin = DefaultPlayerSkin.getDefaultSkin();
			state.scale = 1; state.ageScale = 1; state.pose = Pose.STANDING;
			state.boundingBoxWidth = .6f; state.boundingBoxHeight = 1.8f;
			state.showHat = state.showJacket = state.showLeftPants = state.showRightPants = state.showLeftSleeve = state.showRightSleeve = true;
		} else {
			var renderer = (AvatarRenderer<AbstractClientPlayer>) minecraft.getEntityRenderDispatcher().getRenderer(minecraft.player);
			state = renderer.createRenderState(); renderer.extractRenderState(minecraft.player, state, pt);
		}
		state.bodyRot = rotation; state.yRot = 0; state.xRot = 0;
		state.nameTag = null; state.shadowPieces.clear(); state.distanceToCameraSq = 0;
		state.lightCoords = 0xF000F0;
		float scale = Math.min((previewW - 18) / 3.65f, (previewH - 10) / 2.9f);
		g.submitEntityRenderState(state, scale, new Vector3f(0, 1.2f, 0),
			new Quaternionf().rotationZ((float) Math.PI), new Quaternionf(), previewX, previewY, previewX + previewW, previewY + previewH);
	}
	@Override public void render(GuiGraphics g, int mx, int my, float pt) {
		UiOpacity.value = motion.opacity();
		try {
			super.render(g, mx, my, pt); preview(g, pt);
			Ui.text(g, Ui.bold(title), left + 13, top + 12, Ui.TEXT);
			Glass.fill(g, left + 12, top + (look.theme().equals("angel") ? 61 : 87), divider - 12, 1, Ui.ACCENT);
			Ui.text(g, Ui.fit(Ui.small(tr(minecraft.player == null ? "needworld" : "local")), w - 24), left + 12, top + h - 43, Ui.MUTED);
			if (minecraft.player != null) Ui.text(g, Ui.small(tr("drag")), previewX + 7, previewY + previewH - 14, Ui.MUTED);
		} finally { UiOpacity.value = 1; }
	}
}
