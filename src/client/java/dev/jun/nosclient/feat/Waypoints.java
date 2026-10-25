package dev.jun.nosclient.feat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.jun.nosclient.scene.SceneConfig;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waypoint lưu theo máy chủ (spec: theo IP server) và theo chiều không gian.
 * Chơi đơn dùng tên thế giới làm khoá. File: config/nosclient-waypoints.json.
 */
public final class Waypoints {
	private Waypoints() {}

	private static final Logger LOG = LoggerFactory.getLogger("nosclient");
	/** hidden: ẩn khỏi HUD/la bàn nhưng vẫn giữ. Gson đọc file cũ (không có field) thành false. */
	public record Wp(String name, int x, int y, int z, String dim, int color, boolean hidden) {
		public Wp withName(String n) { return new Wp(n, x, y, z, dim, color, hidden); }
		public Wp withColor(int c) { return new Wp(name, x, y, z, dim, c, hidden); }
		public Wp withHidden(boolean h) { return new Wp(name, x, y, z, dim, color, h); }
	}

	private static final Gson GSON = new Gson();
	private static Map<String, List<Wp>> all;
	public static final int[] COLORS = {0xFFD3A8F5, 0xFF7FD873, 0xFF3ECFD5, 0xFFF5C451, 0xFFF2555A};

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("nosclient-waypoints.json");
	}

	private static Map<String, List<Wp>> all() {
		if (all != null) return all;
		all = new HashMap<>();
		Path f = file();
		if (Files.exists(f)) try (Reader r = Files.newBufferedReader(f)) {
			Map<String, List<Wp>> m = GSON.fromJson(r, new TypeToken<Map<String, List<Wp>>>() {}.getType());
			if (m != null) all.putAll(m);
		} catch (IOException | RuntimeException e) {
			LOG.warn("Không đọc được {}", f, e);
		}
		return all;
	}

	private static void save() {
		try (Writer w = Files.newBufferedWriter(file())) {
			GSON.toJson(all(), w);
		} catch (IOException e) {
			LOG.warn("Không lưu được {}", file(), e);
		}
	}

	/** Khoá máy chủ hiện tại: IP với chơi mạng, "sp:<tên thế giới>" với chơi đơn. */
	public static String serverKey(Minecraft mc) {
		if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
		if (mc.getSingleplayerServer() != null) return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		return "unknown";
	}

	public static String dim(Minecraft mc) {
		return mc.level.dimension().identifier().toString();
	}

	/** Waypoint của máy chủ và chiều không gian hiện tại. */
	public static List<Wp> here(Minecraft mc) {
		if (mc.level == null) return List.of();
		String d = dim(mc);
		return all().getOrDefault(serverKey(mc), List.of()).stream().filter(w -> w.dim.equals(d) && !w.hidden).toList();
	}

	/** Mọi waypoint của máy chủ hiện tại (mọi chiều, kể cả đã ẩn) — cho màn quản lý. Sửa list này rồi gọi save(). */
	public static List<Wp> mine(Minecraft mc) {
		return all().computeIfAbsent(serverKey(mc), k -> new ArrayList<>());
	}

	public static void saveNow() {
		save();
	}

	/** Thêm waypoint tại toạ độ cho trước (màn quản lý / bản đồ thế giới). */
	public static Wp add(Minecraft mc, String name, int x, int y, int z) {
		var list = mine(mc);
		Wp w = new Wp(name == null || name.isBlank() ? "#" + (list.size() + 1) : name, x, y, z, dim(mc), COLORS[list.size() % COLORS.length], false);
		list.add(w);
		save();
		return w;
	}

	/** Đánh dấu chỗ đang đứng (phím B). */
	public static Wp addHere(Minecraft mc) {
		var p = mc.player.blockPosition();
		return add(mc, null, p.getX(), p.getY(), p.getZ());
	}

	/** Waypoint của chiều hiện tại cách chỗ đứng ≤ r khối (Manhattan), hoặc null. */
	public static Wp near(Minecraft mc, int r) {
		var p = mc.player.blockPosition();
		String d = dim(mc);
		for (Wp w : mine(mc)) if (w.dim.equals(d) && Math.abs(w.x - p.getX()) + Math.abs(w.y - p.getY()) + Math.abs(w.z - p.getZ()) <= r) return w;
		return null;
	}

	public static boolean enabled() {
		return SceneConfig.INSTANCE.waypoint;
	}
}
