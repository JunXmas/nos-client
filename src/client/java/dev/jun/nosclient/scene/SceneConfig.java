package dev.jun.nosclient.scene;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mọi lựa chọn của Nos client, lưu ở config/nosclient.properties.
 * Mỗi field public (boolean/int/double) là một khoá — thêm tuỳ chọn chỉ cần khai báo field kèm mặc định.
 * Mặc định bật/tắt theo spec của jun (ghi chú cạnh từng nhóm).
 */
public final class SceneConfig {
	private static final Logger LOG = LoggerFactory.getLogger("nosclient");
	public static final SceneConfig INSTANCE = load(file());

	// ---- cảnh nền màn chính ----
	public boolean blossom = true, cane = true, fx = true;
	/** 40 = hoàng hôn … 100 = đêm (ban ngày đã bỏ theo yêu cầu). */
	public double tod = 47;

	// ---- HUD cơ bản: toạ độ, hướng bật; ngày tắt ----
	public boolean coords = true, direction = true, day = false;
	// ---- HUD nâng cao: FPS bật; ping, CPS, Inventory HUD tắt; hiệu ứng thuốc bật ----
	public boolean fps = true, ping = false, cps = false, invhud = false, effects = true;
	// ---- túi đồ: công thức, shulker, bản đồ, giáp đều bật ----
	public boolean recipe = true, shulker = true, mappv = true, armor = true;
	// ---- thế giới: waypoint, minimap tắt; thông tin khối, zoom, ánh sáng bật ----
	public boolean waypoint = false, minimap = false, blockinfo = true, zoom = true, dynlight = true;
	/** Minimap: bán kính tối đa tính theo chunk (spec: cắt theo bề rộng tối đa, không vẽ hang). */
	public int minimapChunks = 4;
	/** Gamma ghi vào tuỳ chọn độ sáng của game; -1 = giữ độ sáng gốc của game. */
	public double gamma = -1;
	// ---- PvP & phím: keystrokes, toggle sprint bật; freelook tắt ----
	public boolean keys = true, sprint = true, freelook = false;
	public boolean sprintSneak = false;
	public int zoomLevel = 4;
	// ---- hệ thống: Dynamic FPS bật; chơi chung bật ----
	public boolean dfps = true, mp = true;
	public int dfpsBg = 15;
	public boolean mpOffline = true, mpPvp = true;

	// ---- giao diện HUD ----
	/** Cỡ HUD (0,5–1,5 × cỡ giao diện), độ đục nền ô (0–1), mờ khi đứng yên. */
	public double hudScale = .8, hudBg = .45;
	public boolean hudIdleFade = true;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("nosclient.properties");
	}

	/** Khoá cũ của bản 0.1 → giữ nguyên để file cũ vẫn đọc được. */
	static String key(Field f) {
		return switch (f.getName()) {
			case "fx" -> "shader";
			case "coords", "direction", "day", "fps", "ping", "cps", "keys" -> "hud." + f.getName();
			case "hudScale" -> "hud.scale";
			case "hudBg" -> "hud.bg";
			case "hudIdleFade" -> "hud.idleFade";
			default -> f.getName();
		};
	}

	static boolean option(Field f) {
		int m = f.getModifiers();
		return Modifier.isPublic(m) && !Modifier.isStatic(m) && (f.getType() == boolean.class || f.getType() == int.class || f.getType() == double.class);
	}

	static SceneConfig load(Path p) {
		SceneConfig c = new SceneConfig();
		if (!Files.exists(p)) return c;
		Properties props = new Properties();
		try (Reader r = Files.newBufferedReader(p)) {
			props.load(r);
		} catch (IOException e) {
			LOG.warn("Không đọc được {}, dùng mặc định", p, e);
			return c;
		}
		for (Field f : SceneConfig.class.getFields()) {
			if (!option(f)) continue;
			String v = props.getProperty(key(f));
			if (v == null) continue;
			try {
				if (f.getType() == boolean.class) f.setBoolean(c, Boolean.parseBoolean(v.trim()));
				else if (f.getType() == int.class) f.setInt(c, Integer.parseInt(v.trim()));
				else f.setDouble(c, Double.parseDouble(v.trim()));
			} catch (ReflectiveOperationException | NumberFormatException e) {
				LOG.debug("Bỏ qua giá trị hỏng {}={}", key(f), v);
			}
		}
		c.clamp();
		return c;
	}

	/** Giữ giá trị trong khoảng hợp lệ, kể cả khi người dùng sửa tay file. */
	void clamp() {
		tod = Math.min(100, Math.max(40, tod));
		hudScale = Math.min(1.5, Math.max(.5, hudScale));
		hudBg = Math.min(1, Math.max(0, hudBg));
		minimapChunks = Math.min(32, Math.max(1, minimapChunks));
		zoomLevel = zoomLevel <= 2 ? 2 : zoomLevel <= 4 ? 4 : 8;
		dfpsBg = Math.min(60, Math.max(1, dfpsBg));
		if (gamma != -1) gamma = Math.min(16, Math.max(0, gamma));
	}

	public void save() {
		save(file());
	}

	void save(Path p) {
		Properties props = new Properties();
		for (Field f : SceneConfig.class.getFields()) {
			if (!option(f)) continue;
			try {
				props.setProperty(key(f), String.valueOf(f.get(this)));
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		try (Writer w = Files.newBufferedWriter(p)) {
			props.store(w, "Nos client");
		} catch (IOException e) {
			LOG.warn("Không lưu được {}", p, e);
		}
	}
}
