package dev.jun.nosclient.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import dev.jun.nosclient.config.ClientSettings;
import dev.jun.nosclient.scene.ConfigValues;
import dev.jun.nosclient.storage.ClientFiles;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class HudLayout {
    private static final Logger LOG = LoggerFactory.getLogger("nosclient");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HudLayout.Slot DEFAULT = new HudLayout.Slot();
    private static Map<String, HudLayout.Slot> slots;

    private HudLayout() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("nosclient-hud.json");
    }

    private static Map<String, HudLayout.Slot> all() {
        if (slots != null) {
            return slots;
        }

        LayoutData data =
                ClientSettings.current().section("hud", LayoutData.class, HudLayout::legacy);
        slots = data.slots == null ? new HashMap<>() : data.slots;
        slots.replaceAll((id, slot) -> slot == null ? new Slot() : clamp(slot));
        return slots;
    }

    private static LayoutData legacy() {
        Map<String, Slot> loaded = new HashMap<>();
        Path p = file();
        if (Files.exists(p)) {
            try {
                Map<String, HudLayout.Slot> m =
                        (Map<String, HudLayout.Slot>)
                                GSON.fromJson(
                                        ClientFiles.read(p),
                                        (new TypeToken<Map<String, HudLayout.Slot>>() {})
                                                .getType());
                if (m != null) {
                    m.forEach(
                            (k, v) -> {
                                if (v != null) {
                                    loaded.put(k, clamp(v));
                                }
                            });
                }
            } catch (IOException | RuntimeException e) {
                LOG.warn("Không đọc được {}, dùng bố cục mặc định", p, e);
            }
        }

        LayoutData data = new LayoutData();
        data.slots = loaded;
        return data;
    }

    private static final class LayoutData {
        Map<String, Slot> slots = new HashMap<>();
    }

    private static HudLayout.Slot clamp(HudLayout.Slot s) {
        if (!Double.isFinite(s.x) || !Double.isFinite(s.y)) {
            s.x = -1.0;
            s.y = -1.0;
        }

        if (s.x >= 0.0) {
            s.x = Math.min(1.0, s.x);
            s.y = Math.min(1.0, Math.max(0.0, s.y));
        }

        s.ax = Math.min(2, Math.max(0, s.ax));
        s.ay = Math.min(1, Math.max(0, s.ay));
        s.scale = ConfigValues.bounded(s.scale, 0.5, 2.0, 1.0);
        s.alpha = ConfigValues.bounded(s.alpha, 0.2, 1.0, 1.0);
        return s;
    }

    public static HudLayout.Slot get(String id) {
        return all().getOrDefault(id, DEFAULT);
    }

    public static HudLayout.Slot edit(String id) {
        return all().computeIfAbsent(id, k -> new HudLayout.Slot());
    }

    public static void reset(String id) {
        all().remove(id);
    }

    public static void resetAll() {
        all().clear();
    }

    public static void save() {
        all().values().forEach(HudLayout::clamp);
        LayoutData data = new LayoutData();
        data.slots = all();
        ClientSettings.current().replace("hud", data);
        ClientSettings.current().save();
    }

    public static final class Slot {
        public double x = -1.0;
        public double y = -1.0;
        public int ax;
        public int ay;
        public double scale = 1.0;
        public double alpha = 1.0;

        public boolean auto() {
            return this.x < 0.0;
        }
    }
}
