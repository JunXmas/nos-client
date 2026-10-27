package dev.jun.nosclient;

import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.theme.Theme;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class Mods {
    private static final SceneConfig c = SceneConfig.INSTANCE;
    public static final List<Mods.Mod> ALL = List.of(
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
        m("armor", "inv", "", Items.DIAMOND_CHESTPLATE, () -> c.armor, v -> c.armor = v),
        m("waypoint", "world", "", Items.LODESTONE, () -> c.waypoint, v -> c.waypoint = v),
        m("minimap", "world", "", Items.MAP, () -> c.minimap, v -> c.minimap = v),
        m("blockinfo", "world", "", Items.SPYGLASS, () -> c.blockinfo, v -> c.blockinfo = v),
        m("zoom", "world", "", Items.SPYGLASS, () -> c.zoom, v -> c.zoom = v),
        m("light", "world", "", Items.TORCH, () -> c.dynlight, v -> c.dynlight = v),
        m("sky", "world", "", Items.CLOCK, () -> c.sky, v -> c.sky = v),
        m("weather", "world", "", Items.WATER_BUCKET, () -> c.weather, v -> c.weather = v),
        m("lyrics", "world", "", Items.NOTE_BLOCK, () -> c.lyrics, v -> c.lyrics = v),
        m("keys", "pvp", "", Items.OAK_BUTTON, () -> c.keys, v -> c.keys = v),
        m("sprint", "pvp", "", Items.SUGAR, () -> c.sprint, v -> c.sprint = v),
        m("freelook", "pvp", "", Items.ENDER_EYE, () -> c.freelook, v -> c.freelook = v),
        m("dfps", "sys", "", Items.REPEATER, () -> c.dfps, v -> c.dfps = v),
        m("mp", "sys", "", Items.ENDER_EYE, () -> c.mp, v -> c.mp = v)
    );
    private static final Map<String, String> CONFLICT = Map.of("zoom", "zoomify", "dfps", "dynamic_fps", "light", "lambdynlights");
    public static final boolean ZOOM_OWNED = conflict("zoom") != null;
    public static final boolean DFPS_OWNED = conflict("dfps") != null;
    public static final boolean LIGHT_OWNED = conflict("light") != null;
    public static final String[] CATS = new String[]{"hud", "inv", "world", "pvp", "sys"};
    private static final int[] CAT_COLOR = new int[]{-5583944, -5583944, -5583944, -5583944, -5583944};
    private static final Map<String, List<Mods.Mod>> BY_CATEGORY = Arrays.stream(CATS)
        .collect(Collectors.toUnmodifiableMap(x -> (String)x, cat -> ALL.stream().filter(m -> m.cat.equals(cat)).toList()));

    private Mods() {
    }

    private static Mods.Mod m(String id, String cat, String group, Item icon, Supplier<Boolean> get, Consumer<Boolean> set) {
        return new Mods.Mod(id, cat, group, new ItemStack(icon), get, set);
    }

    public static String conflict(String id) {
        String other = CONFLICT.get(id);
        return other != null && FabricLoader.getInstance().isModLoaded(other) ? other : null;
    }

    public static int catColor(String cat) {
        return Theme.palette().accent();
    }

    public static List<Mods.Mod> of(String cat) {
        return cat == null ? List.of() : BY_CATEGORY.getOrDefault(cat, List.of());
    }

    public static long on(String cat) {
        return of(cat).stream().filter(x -> x.get.get()).count();
    }

    @Environment(EnvType.CLIENT)
    public record Mod(String id, String cat, String group, ItemStack icon, Supplier<Boolean> get, Consumer<Boolean> set) {
    }
}
