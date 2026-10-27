package dev.jun.nosclient.feat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.storage.ClientFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class Waypoints {
    private static final Logger LOG = LoggerFactory.getLogger("nosclient");
    private static final Gson GSON = new Gson();
    private static Map<String, List<Waypoints.Wp>> all;
    public static final int[] COLORS = new int[]{-2905867, -8398733, -12660779, -670639, -895654};

    private Waypoints() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("nosclient-waypoints.json");
    }

    private static Map<String, List<Waypoints.Wp>> all() {
        if (all != null) {
            return all;
        }

        all = new HashMap<>();
        Path f = file();
        if (Files.exists(f)) {
            try {
                Map<String, List<Waypoints.Wp>> m = (Map<String, List<Waypoints.Wp>>)GSON.fromJson(
                    ClientFiles.read(f), (new TypeToken<Map<String, List<Waypoints.Wp>>>() {}).getType()
                );
                if (m != null) {
                    m.forEach((key, values) -> {
                        if (key != null && values != null) {
                            all.put(key, new ArrayList<>(values.stream().filter(w -> w != null && w.name != null && w.dim != null).toList()));
                        }
                    });
                }
            } catch (IOException | RuntimeException e) {
                LOG.warn("Không đọc được {}", f, e);
            }
        }

        return all;
    }

    private static void save() {
        try {
            ClientFiles.write(file(), GSON.toJson(all()));
        } catch (IOException e) {
            LOG.warn("Không lưu được {}", file(), e);
        }
    }

    public static String serverKey(Minecraft mc) {
        if (mc.getCurrentServer() != null) {
            return mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        }

        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return "unknown";
        }

        Path root = server.getWorldPath(LevelResource.ROOT);
        String key = singleplayerKey(root, server.getWorldData().worldGenOptions().seed());
        if (claimed.add(key) && migrate(all(), FabricLoader.getInstance().getConfigDir().resolve("nosclient-map"), "sp:" + folder(root), key)) {
            save();
        }

        return key;
    }

    private static final Set<String> claimed = new HashSet<>();

    private static String folder(Path worldRoot) {
        Path name = worldRoot.toAbsolutePath().normalize().getFileName();
        return name == null ? "unknown" : name.toString();
    }

    /**
     * Khoá chơi đơn = thư mục save + seed. Trước đây là tên hiển thị nên "New World" và "New World (1)"
     * (cùng tên) ghi đè bản đồ/waypoint của nhau; seed giữ cho world xoá đi tạo lại cùng tên vẫn mới tinh.
     */
    public static String singleplayerKey(Path worldRoot, long seed) {
        return "sp:" + folder(worldRoot) + "@" + Long.toHexString(seed);
    }

    public static String dirName(String key) {
        return key.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * Dữ liệu khoá cũ (theo tên thư mục, trùng tên hiển thị với world mặc định) về tay world có thư mục đó —
     * một lần, không chép đôi. Trả true nếu waypoint đổi (cần lưu).
     */
    static boolean migrate(Map<String, List<Waypoints.Wp>> waypoints, Path mapRoot, String legacy, String key) {
        List<Waypoints.Wp> old = waypoints.remove(legacy);
        if (old != null) {
            waypoints.computeIfAbsent(key, k -> new ArrayList<>()).addAll(old);
        }

        Path from = mapRoot.resolve(dirName(legacy));
        Path to = mapRoot.resolve(dirName(key));
        if (Files.isDirectory(from) && !Files.exists(to)) {
            try {
                Files.move(from, to);
                LOG.info("Bản đồ cũ {} chuyển về {}", from.getFileName(), to.getFileName());
            } catch (IOException e) {
                LOG.warn("Không chuyển được bản đồ cũ {} sang {}", from, to, e);
            }
        }

        return old != null;
    }

    public static String dim(Minecraft mc) {
        return mc.level.dimension().identifier().toString();
    }

    public static List<Waypoints.Wp> here(Minecraft mc) {
        if (mc.level == null) {
            return List.of();
        }

        String d = dim(mc);
        return all().getOrDefault(serverKey(mc), List.of()).stream().filter(w -> w.dim.equals(d) && !w.hidden).toList();
    }

    public static List<Waypoints.Wp> mine(Minecraft mc) {
        return all().computeIfAbsent(serverKey(mc), k -> new ArrayList<>());
    }

    public static void saveNow() {
        save();
    }

    public static Waypoints.Wp add(Minecraft mc, String name, int x, int y, int z) {
        List<Waypoints.Wp> list = mine(mc);
        Waypoints.Wp w = new Waypoints.Wp(
            name != null && !name.isBlank() ? name : "#" + (list.size() + 1), x, y, z, dim(mc), COLORS[list.size() % COLORS.length], false
        );
        list.add(w);
        save();
        return w;
    }

    public static Waypoints.Wp addHere(Minecraft mc) {
        BlockPos p = mc.player.blockPosition();
        return add(mc, null, p.getX(), p.getY(), p.getZ());
    }

    public static Waypoints.Wp near(Minecraft mc, int r) {
        BlockPos p = mc.player.blockPosition();
        String d = dim(mc);

        for (Waypoints.Wp w : mine(mc)) {
            if (w.dim.equals(d) && Math.abs(w.x - p.getX()) + Math.abs(w.y - p.getY()) + Math.abs(w.z - p.getZ()) <= r) {
                return w;
            }
        }

        return null;
    }

    public static boolean enabled() {
        return SceneConfig.INSTANCE.waypoint;
    }

    @Environment(EnvType.CLIENT)
    public record Wp(String name, int x, int y, int z, String dim, int color, boolean hidden) {
        public Waypoints.Wp withName(String n) {
            return new Waypoints.Wp(n, this.x, this.y, this.z, this.dim, this.color, this.hidden);
        }

        public Waypoints.Wp withColor(int c) {
            return new Waypoints.Wp(this.name, this.x, this.y, this.z, this.dim, c, this.hidden);
        }

        public Waypoints.Wp withHidden(boolean h) {
            return new Waypoints.Wp(this.name, this.x, this.y, this.z, this.dim, this.color, h);
        }
    }
}
