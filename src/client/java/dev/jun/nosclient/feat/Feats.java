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
 * Tính năng chạy theo tick, không vẽ gì: Toggle Sprint, Zoom, Freelook, Dynamic FPS. Gamma nằm ở LightTextureMixin.
 * Trạng thái để mixin đọc nằm ở các field static — đọc một biến, không tra cứu gì mỗi khung.
 */
public final class Feats {
	private Feats() {}

	private static final SceneConfig c = SceneConfig.INSTANCE;
	public static KeyMapping ZOOM, FREELOOK, WAYPOINT;
	private static final boolean PROBE = Boolean.getBoolean("nosclient.probe");
	private static int probeTicks;

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
	private static boolean lookToggled;

	/** FOV dọc của khung vừa vẽ (độ), đã tính zoom. */
	public static volatile float lastFov = 70;

	public static void init() {
		KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nosclient", "feat"));
		// Zoomify cũng mặc định phím C: có nó thì phím zoom của ta để trống, khỏi một phím hai việc
		ZOOM = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.zoom", dev.jun.nosclient.Mods.ZOOM_OWNED ? GLFW.GLFW_KEY_UNKNOWN : GLFW.GLFW_KEY_C, cat));
		FREELOOK = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.freelook", GLFW.GLFW_KEY_LEFT_ALT, cat));
		WAYPOINT = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.waypoint", GLFW.GLFW_KEY_B, cat));
		zoomMul = c.zoomLevel;
		ClientTickEvents.END_CLIENT_TICK.register(Feats::tick);
	}

	private static void tick(Minecraft mc) {
		if (PROBE && ++probeTicks % 100 == 0) // chỉ để đo: -Dnosclient.probe=true in giới hạn FPS, gamma đang áp, màn hiện tại mỗi 5 giây
			org.slf4j.LoggerFactory.getLogger("nosclient").info("probe fps-limit={} reason={} gamma-opt={} gamma-nos={} screen={}",
				mc.getFramerateLimitTracker().getFramerateLimit(), mc.getFramerateLimitTracker().getThrottleReason(), mc.options.gamma().get(), c.gamma,
				mc.screen == null ? "-" : mc.screen.getClass().getSimpleName());
		DynLight.tick(mc);
		dev.jun.nosclient.hud.Minimap.tick(mc);
		if (mc.player == null) {
			looking = false;
			return;
		}
		sprint(mc);
		while (WAYPOINT.consumeClick()) if (c.waypoint) {
			// đứng ngay trên một waypoint thì bấm B để xoá, không thì đánh dấu chỗ đang đứng
			if (Waypoints.removeNear(mc)) mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("nosclient.wp.removed"), false);
			else mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("nosclient.wp.added", Waypoints.addHere(mc).name()), false);
		}
		boolean z = c.zoom && !dev.jun.nosclient.Mods.ZOOM_OWNED && ZOOM.isDown() && mc.screen == null;
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
		// kiểu "bấm bật/tắt": mỗi cú bấm đổi trạng thái; kiểu "giữ": giữ phím mới nhìn quanh
		if (c.freelookToggle) while (FREELOOK.consumeClick()) lookToggled = !lookToggled;
		else lookToggled = false;
		boolean want = c.freelook && (c.freelookToggle ? lookToggled : FREELOOK.isDown()) && mc.screen == null;
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

	/** Lần cuối có phím/chuột (cho Dynamic FPS AFK). */
	public static volatile long lastInput = System.currentTimeMillis();

	/** Lăn chuột khi đang zoom: đổi mức 1.5× – 50×. @return true nếu đã nuốt cú lăn. */
	public static boolean scroll(double dy) {
		if (zoomTarget <= 1 || dy == 0 || !c.zoomScroll) return false;
		zoomMul = Math.min(50, Math.max(1.5, zoomMul * (dy > 0 ? 1.25 : 0.8)));
		zoomTarget = zoomMul;
		return true;
	}

	/** Màn do Nos client vẽ cảnh động (cần đủ FPS, không phải menu tĩnh). */
	public static boolean nosScreen(net.minecraft.client.gui.screens.Screen s) {
		return s instanceof dev.jun.nosclient.gui.NosTitleScreen || s instanceof dev.jun.nosclient.gui.IntroScreen || s instanceof dev.jun.nosclient.gui.NosSettingsScreen;
	}

	/** Dynamic FPS: game ở nền thì hạ FPS, bị thu nhỏ thì gần như dừng vẽ. -1 = để game tự quyết. */
	public static int fpsCap(Minecraft mc) {
		if (!c.dfps || dev.jun.nosclient.Mods.DFPS_OWNED) return -1;
		var w = mc.getWindow();
		if (c.dfpsMin && (w.isMinimized() || w.isIconified())) return 1;
		if (!mc.isWindowActive()) return c.dfpsBg;
		// AFK 5 phút (không chạm phím/chuột) thì hạ còn 10 FPS
		if (c.dfpsAfk && mc.screen == null && System.currentTimeMillis() - lastInput > 300_000) return 10;
		return -1;
	}
}
