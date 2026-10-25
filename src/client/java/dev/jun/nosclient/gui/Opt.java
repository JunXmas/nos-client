package dev.jun.nosclient.gui;

import dev.jun.nosclient.scene.SceneConfig;
import java.util.List;
import java.util.Map;

/**
 * Tuỳ chọn con của từng mod theo preview: công tắc (chk), lựa chọn (seg), thanh trượt (rng), phím (key).
 * Đọc/ghi thẳng field của SceneConfig qua reflection — thêm tuỳ chọn chỉ cần một dòng ở đây và một field ở config.
 */
record Opt(String type, String field, String label, String[] choices, double min, double max, String unit) {
	static Opt chk(String field, String label) {
		return new Opt("chk", field, label, null, 0, 0, null);
	}

	static Opt seg(String field, String label, String... choices) {
		return new Opt("seg", field, label, choices, 0, 0, null);
	}

	static Opt rng(String field, String label, double min, double max, String unit) {
		return new Opt("rng", field, label, null, min, max, unit);
	}

	/** Phím do game giữ (Tuỳ chọn → Điều khiển); field = tên KeyMapping. */
	static Opt key(String keyName, String label) {
		return new Opt("key", keyName, label, null, 0, 0, null);
	}

	private java.lang.reflect.Field f() {
		try {
			return SceneConfig.class.getField(field);
		} catch (NoSuchFieldException e) {
			throw new IllegalStateException("thiếu field " + field, e);
		}
	}

	boolean bool() {
		try {
			return f().getBoolean(SceneConfig.INSTANCE);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	double num() {
		try {
			var ff = f();
			return ff.getType() == int.class ? ff.getInt(SceneConfig.INSTANCE) : ff.getType() == boolean.class ? (ff.getBoolean(SceneConfig.INSTANCE) ? 1 : 0) : ff.getDouble(SceneConfig.INSTANCE);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Ghi vào config trong bộ nhớ; bên gọi tự save (kéo thanh trượt chỉ lưu lúc thả chuột). */
	void set(double v) {
		try {
			var ff = f();
			if (ff.getType() == int.class) ff.setInt(SceneConfig.INSTANCE, (int) Math.round(v));
			else if (ff.getType() == boolean.class) ff.setBoolean(SceneConfig.INSTANCE, v != 0);
			else ff.setDouble(SceneConfig.INSTANCE, v);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	static final Map<String, List<Opt>> BY_MOD = Map.ofEntries(
		Map.entry("coords", List.of(chk("coordsBiome", "opt.coords.biome"), seg("coordsDecimal", "opt.coords.fmt", "opt.coords.int", "opt.coords.dec"))),
		Map.entry("direction", List.of(chk("directionDeg", "opt.direction.deg"))),
		Map.entry("day", List.of(chk("dayTime", "opt.day.time"))),
		Map.entry("invhud", List.of(chk("invhudBg", "opt.invhud.bg"))),
		Map.entry("fps", List.of(chk("fpsLow", "opt.fps.low"))),
		Map.entry("effects", List.of(chk("fxTime", "opt.fx.time"))),
		Map.entry("cps", List.of(chk("cpsRight", "opt.cps.right"))),
		Map.entry("shulker", List.of(chk("shulkerColor", "opt.shulker.color"))),
		Map.entry("mappv", List.of(seg("mapSize", "opt.map.size", "opt.small", "opt.medium", "opt.large"))),
		Map.entry("armor", List.of(seg("armorFmt", "opt.armor.fmt", "opt.armor.num", "opt.armor.pct", "opt.armor.bar"), chk("armorHand", "opt.armor.hand"), chk("armorWarn", "opt.armor.warn"))),
		Map.entry("waypoint", List.of(key("nosclient.key.waypoint", "opt.key"))),
		Map.entry("minimap", List.of(seg("minimapZoom", "opt.minimap.zoom", "½×", "1×", "2×", "4×"), rng("minimapSize", "opt.minimap.size", 48, 160, " px"), chk("minimapRotate", "opt.minimap.rot"), key("nosclient.key.worldmap", "opt.minimap.mapkey"))),
		Map.entry("blockinfo", List.of(chk("blockId", "opt.block.id"), chk("blockTool", "opt.block.tool"), chk("blockHp", "opt.block.hp"))),
		Map.entry("zoom", List.of(key("nosclient.key.zoom", "opt.key"), seg("zoomLevel", "opt.zoom.lv", "2×", "4×", "8×"), chk("zoomScroll", "opt.zoom.scroll"))),
		Map.entry("light", List.of(rng("gamma", "opt.light.gamma", 0, 10, "%"))),
		Map.entry("keys", List.of(chk("keysMouse", "opt.keys.mouse"), chk("keysCps", "opt.keys.cps"), chk("keysSpace", "opt.keys.space"))),
		Map.entry("sprint", List.of(chk("sprintSneak", "opt.sprint.sneak"), chk("sprintShow", "opt.sprint.show"))),
		Map.entry("freelook", List.of(key("nosclient.key.freelook", "opt.key"), seg("freelookToggle", "opt.freelook.mode", "opt.hold", "opt.toggle"))),
		Map.entry("dfps", List.of(rng("dfpsBg", "opt.dfps.bg", 1, 60, " FPS"), chk("dfpsMin", "opt.dfps.min"), chk("dfpsAfk", "opt.dfps.afk"))),
		Map.entry("mp", List.of(chk("mpOffline", "opt.mp.offline"), chk("mpPvp", "opt.mp.pvp"))));

	private static final SceneConfig DEF = new SceneConfig();

	/** Field công tắc chính của mod (Ánh sáng bật/tắt dynamic light). */
	static String field(dev.jun.nosclient.Mods.Mod m) {
		return m.id().equals("light") ? "dynlight" : m.id();
	}

	static boolean defOn(String field) {
		try {
			return SceneConfig.class.getField(field).getBoolean(DEF);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Trả field về mặc định của bản phát hành. */
	static void reset(String field) {
		try {
			var ff = SceneConfig.class.getField(field);
			ff.set(SceneConfig.INSTANCE, ff.get(DEF));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Giá trị đang chọn của seg: zoomLevel lưu 2/4/8 nên quy về chỉ số 0/1/2. */
	int segIndex() {
		double v = num();
		if (field.equals("zoomLevel")) return v <= 2 ? 0 : v <= 4 ? 1 : 2;
		return (int) v;
	}

	void setSeg(int i) {
		if (field.equals("zoomLevel")) set(new int[]{2, 4, 8}[i]);
		else set(i);
	}
}
