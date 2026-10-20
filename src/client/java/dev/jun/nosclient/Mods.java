package dev.jun.nosclient;

import dev.jun.nosclient.scene.SceneConfig;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Danh sách mod của Nos client theo đúng preview: danh mục, nhóm, icon, công tắc. Tên/mô tả nằm trong lang. */
public final class Mods {
	private Mods() {}

	public record Mod(String id, String cat, String group, ItemStack icon, Supplier<Boolean> get, Consumer<Boolean> set) {}

	private static final SceneConfig c = SceneConfig.INSTANCE;

	private static Mod m(String id, String cat, String group, Item icon, Supplier<Boolean> get, Consumer<Boolean> set) {
		return new Mod(id, cat, group, new ItemStack(icon), get, set);
	}

	public static final List<Mod> ALL = List.of(
		m("coords", "hud", "basic", Items.COMPASS, () -> c.coords, v -> c.coords = v),
		m("direction", "hud", "basic", Items.RECOVERY_COMPASS, () -> c.direction, v -> c.direction = v),
		m("day", "hud", "basic", Items.CLOCK, () -> c.day, v -> c.day = v),
		m("fps", "hud", "advanced", Items.REDSTONE, () -> c.fps, v -> c.fps = v),
		m("ping", "hud", "advanced", Items.ENDER_PEARL, () -> c.ping, v -> c.ping = v),
		m("cps", "hud", "advanced", Items.IRON_SWORD, () -> c.cps, v -> c.cps = v),
		m("effects", "hud", "advanced", Items.POTION, () -> c.effects, v -> c.effects = v),
		m("invhud", "hud", "advanced", Items.BUNDLE, () -> c.invhud, v -> c.invhud = v),

		m("recipe", "inv", "", Items.CRAFTING_TABLE, () -> c.recipe, v -> c.recipe = v),
		m("shulker", "inv", "", Items.PURPLE_SHULKER_BOX, () -> c.shulker, v -> c.shulker = v),
		m("mappv", "inv", "", Items.FILLED_MAP, () -> c.mappv, v -> c.mappv = v),
		m("armor", "inv", "", Items.DIAMOND_CHESTPLATE, () -> c.armor, v -> c.armor = v),

		m("waypoint", "world", "", Items.LODESTONE, () -> c.waypoint, v -> c.waypoint = v),
		m("minimap", "world", "", Items.MAP, () -> c.minimap, v -> c.minimap = v),
		m("blockinfo", "world", "", Items.SPYGLASS, () -> c.blockinfo, v -> c.blockinfo = v),
		m("zoom", "world", "", Items.SPYGLASS, () -> c.zoom, v -> c.zoom = v),
		m("light", "world", "", Items.TORCH, () -> c.dynlight, v -> c.dynlight = v),

		m("keys", "pvp", "", Items.OAK_BUTTON, () -> c.keys, v -> c.keys = v),
		m("sprint", "pvp", "", Items.SUGAR, () -> c.sprint, v -> c.sprint = v),
		m("freelook", "pvp", "", Items.ENDER_EYE, () -> c.freelook, v -> c.freelook = v),

		m("dfps", "sys", "", Items.REPEATER, () -> c.dfps, v -> c.dfps = v),
		m("mp", "sys", "", Items.ENDER_EYE, () -> c.mp, v -> c.mp = v));

	public static final String[] CATS = {"hud", "inv", "world", "pvp", "sys"};

	public static List<Mod> of(String cat) {
		return ALL.stream().filter(x -> x.cat.equals(cat)).toList();
	}

	public static long on(String cat) {
		return of(cat).stream().filter(x -> x.get.get()).count();
	}
}
