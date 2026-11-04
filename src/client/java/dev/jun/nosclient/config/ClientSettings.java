package dev.jun.nosclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.jun.nosclient.storage.ClientFiles;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Một file cấu hình; các nhóm có kiểu riêng để tránh đụng tên hoặc kiểu dữ liệu. */
public final class ClientSettings {
    private static final Logger LOG = LoggerFactory.getLogger("nosclient");
    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path file;
    private final JsonObject stored;
    private final Map<String, Object> sections = new LinkedHashMap<>();
    private boolean damaged;

    private static final class Holder {
        static final ClientSettings INSTANCE =
                new ClientSettings(
                        FabricLoader.getInstance().getConfigDir().resolve("nosclient.json"));
    }

    public static ClientSettings current() {
        return Holder.INSTANCE;
    }

    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve("nosclient");
    }

    public ClientSettings(Path file) {
        this.file = file;
        JsonObject document = new JsonObject();
        if (Files.exists(file)) {
            try {
                document = JsonParser.parseString(ClientFiles.read(file)).getAsJsonObject();
            } catch (IOException | RuntimeException error) {
                damaged = true;
                LOG.warn("Không đọc được cấu hình {}, sẽ giữ bản hỏng khi lưu", file, error);
            }
        }
        stored = document;
    }

    public boolean contains(String key) {
        return stored.has(key);
    }

    public synchronized <T> T section(String key, Class<T> type, Supplier<T> legacy) {
        T value = null;
        if (stored.has(key)) {
            try {
                value = GSON.fromJson(stored.get(key), type);
            } catch (RuntimeException error) {
                LOG.warn("Nhóm cấu hình {} bị hỏng, dùng mặc định", key, error);
            }
        }
        if (value == null) value = legacy.get();
        sections.put(key, value);
        return value;
    }

    public synchronized void replace(String key, Object value) {
        sections.put(key, value);
    }

    public synchronized void save() {
        JsonObject document = stored.deepCopy();
        document.addProperty("schema", 1);
        sections.forEach((key, value) -> document.add(key, GSON.toJsonTree(value)));
        try {
            if (damaged && Files.exists(file)) {
                Files.copy(
                        file,
                        file.resolveSibling(
                                file.getFileName() + ".invalid-" + System.currentTimeMillis()),
                        StandardCopyOption.COPY_ATTRIBUTES);
                damaged = false;
            }
            ClientFiles.write(file, GSON.toJson(document));
        } catch (IOException error) {
            LOG.warn("Không lưu được cấu hình {}", file, error);
        }
    }
}
