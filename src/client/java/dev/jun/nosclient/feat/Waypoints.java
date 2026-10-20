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
	public record Wp(String name, int x, int y, int z, String dim, int color) {}

	private static final Gson GSON = new Gson();
	private static Map<String, List<Wp>> all;
	private static final int[] COLORS = {0xFFD3A8F5, 0xFF7FD873, 0xFF3ECFD5, 0xFFF5C451, 0xFFF2555A};

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

	private static String dim(Minecraft mc) {
		return mc.level.dimension().identifier().toString();
	}

	/** Waypoint của máy chủ và chiều không gian hiện tại. */
	public static List<Wp> here(Minecraft mc) {
		if (mc.level == null) return List.of();
		String d = dim(mc);
		return all().getOrDefault(serverKey(mc), List.of()).stream().filter(w -> w.dim.equals(d)).toList();
	}

	/** Đánh dấu chỗ đang đứng (phím B). */
	public static Wp addHere(Minecraft mc) {
		var p = mc.player.blockPosition();
		var list = all().computeIfAbsent(serverKey(mc), k -> new ArrayList<>());
		Wp w = new Wp("#" + (list.size() + 1), p.getX(), p.getY(), p.getZ(), dim(mc), COLORS[list.size() % COLORS.length]);
		list.add(w);
		save();
		return w;
	}

	/** Xoá waypoint gần nhất trong 4 khối (bấm B khi đứng trên một waypoint). @return true nếu đã xoá. */
	public static boolean removeNear(Minecraft mc) {
		var p = mc.player.blockPosition();
		var list = all().get(serverKey(mc));
		if (list == null) return false;
		String d = dim(mc);
		boolean gone = list.removeIf(w -> w.dim.equals(d) && Math.abs(w.x - p.getX()) + Math.abs(w.y - p.getY()) + Math.abs(w.z - p.getZ()) <= 4);
		if (gone) save();
		return gone;
	}

	public static boolean enabled() {
		return SceneConfig.INSTANCE.waypoint;
	}
}
