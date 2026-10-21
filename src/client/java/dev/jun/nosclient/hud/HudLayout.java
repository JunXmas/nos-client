package dev.jun.nosclient.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bố cục HUD người chơi tự kéo: mỗi ô một điểm neo (tỉ lệ màn hình, để đổi độ phân giải/cỡ giao diện không lệch),
 * cách canh (trái/giữa/phải, trên/dưới — ô lớn lên về phía trong màn hình), cỡ và độ đục riêng.
 * Lưu ở config/nosclient-hud.json; ô không có trong file thì tự xếp như mặc định.
 */
public final class HudLayout {
	private HudLayout() {}

	private static final Logger LOG = LoggerFactory.getLogger("nosclient");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final class Slot {
		/** Điểm neo theo tỉ lệ màn hình; x < 0 = tự xếp. */
		public double x = -1, y = -1;
		/** Canh ngang 0 trái, 1 giữa, 2 phải; canh dọc 0 trên, 1 dưới. */
		public int ax, ay;
		public double scale = 1, alpha = 1;

		public boolean auto() {
			return x < 0;
		}
	}

	private static final Slot DEFAULT = new Slot();
	private static Map<String, Slot> slots;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("nosclient-hud.json");
	}

	private static Map<String, Slot> all() {
		if (slots != null) return slots;
		slots = new HashMap<>();
		Path p = file();
		if (Files.exists(p)) try {
			Map<String, Slot> m = GSON.fromJson(Files.readString(p), new TypeToken<Map<String, Slot>>() {}.getType());
			if (m != null) m.forEach((k, v) -> {
				if (v != null) slots.put(k, clamp(v));
			});
		} catch (IOException | RuntimeException e) {
			LOG.warn("Không đọc được {}, dùng bố cục mặc định", p, e);
		}
		return slots;
	}

	private static Slot clamp(Slot s) {
		if (s.x >= 0) {
			s.x = Math.min(1, s.x);
			s.y = Math.min(1, Math.max(0, s.y));
		}
		s.ax = Math.min(2, Math.max(0, s.ax));
		s.ay = Math.min(1, Math.max(0, s.ay));
		s.scale = Math.min(2, Math.max(.5, s.scale));
		s.alpha = Math.min(1, Math.max(.2, s.alpha));
		return s;
	}

	/** Ô của id; chưa chỉnh thì trả mặc định dùng chung (không được sửa — sửa qua edit()). */
	public static Slot get(String id) {
		return all().getOrDefault(id, DEFAULT);
	}

	/** Ô riêng của id để sửa (tạo nếu chưa có). */
	public static Slot edit(String id) {
		return all().computeIfAbsent(id, k -> new Slot());
	}

	public static void reset(String id) {
		all().remove(id);
	}

	public static void resetAll() {
		all().clear();
	}

	public static void save() {
		Path p = file();
		try {
			Files.writeString(p, GSON.toJson(all()));
		} catch (IOException e) {
			LOG.warn("Không lưu được {}", p, e);
		}
	}
}
