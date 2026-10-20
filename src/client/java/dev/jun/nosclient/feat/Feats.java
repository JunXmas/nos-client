package dev.jun.nosclient.feat;

import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Tính năng chạy theo tick, không vẽ gì: Toggle Sprint, Zoom, Freelook, Dynamic FPS, Gamma.
 * Trạng thái để mixin đọc nằm ở các field static — đọc một biến, không tra cứu gì mỗi khung.
 */
public final class Feats {
	private Feats() {}

	private static final SceneConfig c = SceneConfig.INSTANCE;
	public static KeyMapping ZOOM, FREELOOK;

	// ---- Toggle Sprint ----
	public static boolean sprintOn, sneakOn;

	// ---- Zoom ----
	/** Hệ số chia FOV đang áp (1 = không zoom), làm mượt mỗi khung. */
	public static double zoomNow = 1, zoomTarget = 1;
	/** Bội zoom người chơi đang chọn, chỉnh bằng cuộn chuột khi giữ phím. */
	public static double zoomMul;

	// ---- Freelook ----
	public static boolean looking;
	public static float lookYaw, lookPitch;
	private static CameraType lookPrev;

	// ---- Gamma ----
	private static double gammaApplied = Double.NaN;

	public static void init() {
		KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nosclient", "feat"));
		ZOOM = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.zoom", GLFW.GLFW_KEY_C, cat));
		FREELOOK = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.freelook", GLFW.GLFW_KEY_LEFT_ALT, cat));
		zoomMul = c.zoomLevel;
		ClientTickEvents.END_CLIENT_TICK.register(Feats::tick);
	}

	private static void tick(Minecraft mc) {
		gamma(mc);
		if (mc.player == null) {
			looking = false;
			return;
		}
		sprint(mc);
		boolean z = c.zoom && ZOOM.isDown() && mc.screen == null;
		if (!z) zoomMul = c.zoomLevel; // thả phím: lần sau zoom lại từ mức đã chọn
		zoomTarget = z ? zoomMul : 1;
		freelook(mc);
	}

	/** Bấm phím chạy (Ctrl) một lần để bật, bấm lần nữa để tắt; giữ trạng thái bằng cách giữ phím hộ. */
	private static void sprint(Minecraft mc) {
		var o = mc.options;
		if (!c.sprint) {
			if (sprintOn || sneakOn) {
				sprintOn = sneakOn = false;
				o.keySprint.setDown(false);
			}
			return;
		}
		// đếm cú bấm thật (consumeClick): cú bấm nhanh giữa hai tick vẫn được tính, và setDown của ta không tạo cú bấm
		while (o.keySprint.consumeClick()) sprintOn = !sprintOn;
		if (sprintOn) o.keySprint.setDown(true);
		if (c.sprintSneak) {
			while (o.keyShift.consumeClick()) sneakOn = !sneakOn;
			if (sneakOn) o.keyShift.setDown(true);
		} else sneakOn = false;
	}

	/** Giữ phím: camera xoay quanh người chơi, hướng chạy giữ nguyên; thả ra thì về góc nhìn cũ. */
	private static void freelook(Minecraft mc) {
		boolean want = c.freelook && FREELOOK.isDown() && mc.screen == null;
		if (want && !looking) {
			looking = true;
			lookYaw = mc.player.getYRot();
			lookPitch = mc.player.getXRot();
			lookPrev = mc.options.getCameraType();
			if (lookPrev.isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		} else if (!want && looking) {
			looking = false;
			if (lookPrev != null) mc.options.setCameraType(lookPrev);
		}
	}

	/** Gamma: ghi thẳng vào tuỳ chọn độ sáng của game (như full bright); -1 = không đụng. */
	private static void gamma(Minecraft mc) {
		if (c.gamma < 0 || c.gamma == gammaApplied) return;
		((dev.jun.nosclient.mixin.OptionInstanceAccessor) (Object) mc.options.gamma()).nosclient$setRaw(c.gamma);
		gammaApplied = c.gamma;
	}

	/** Lăn chuột khi đang zoom: đổi mức 1.5× – 50×. @return true nếu đã nuốt cú lăn. */
	public static boolean scroll(double dy) {
		if (zoomTarget <= 1 || dy == 0) return false;
		zoomMul = Math.min(50, Math.max(1.5, zoomMul * (dy > 0 ? 1.25 : 0.8)));
		zoomTarget = zoomMul;
		return true;
	}

	/** Dynamic FPS: game ở nền thì hạ FPS, bị thu nhỏ thì gần như dừng vẽ. -1 = để game tự quyết. */
	public static int fpsCap(Minecraft mc) {
		if (!c.dfps) return -1;
		var w = mc.getWindow();
		if (w.isMinimized() || w.isIconified()) return 1;
		if (!mc.isWindowActive()) return c.dfpsBg;
		return -1;
	}
}
