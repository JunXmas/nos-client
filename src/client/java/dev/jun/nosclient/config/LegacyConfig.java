package dev.jun.nosclient.config;

import com.google.gson.Gson;

import dev.jun.nosclient.storage.ClientFiles;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** Chỉ đọc cấu hình cũ một lần; không sửa hoặc xoá dữ liệu của bản trước. */
public final class LegacyConfig {
    private LegacyConfig() {}

    public static void migrateResources(Path configDir) {
        if (Files.exists(configDir.resolve("nosclient.json"))) return;
        Path source = configDir.resolve("lyricsflow"), destination = configDir.resolve("nosclient");
        for (String name : new String[] {"fonts", "sky", "spotify.json"}) {
            Path entry = source.resolve(name);
            if (!Files.exists(entry)) continue;
            try (var paths = Files.walk(entry)) {
                for (Path path : paths.toList()) {
                    if (Files.isSymbolicLink(path)) continue;
                    Path target = destination.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) Files.createDirectories(target);
                    else if (!Files.exists(target)) {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            } catch (Exception error) {
                LoggerFactory.getLogger("nosclient")
                        .warn("Không chuyển được dữ liệu cũ {}", entry, error);
            }
        }
    }

    public static String fontId(String old) {
        return "lyricsflow_script".equals(old) ? "nosclient_script" : old;
    }

    public static java.util.Properties scene(Path configDir) {
        java.util.Properties values = new java.util.Properties();
        Path path = configDir.resolve("nosclient.properties");
        if (Files.isRegularFile(path)) {
            try (var reader = new java.io.StringReader(ClientFiles.read(path))) {
                values.load(reader);
            } catch (Exception error) {
                LoggerFactory.getLogger("nosclient")
                        .warn("Không đọc được cấu hình cũ {}", path, error);
            }
        }
        return values;
    }

    public static int lyricsOffset() {
        try {
            return -Integer.parseInt(
                    scene(FabricLoader.getInstance().getConfigDir())
                            .getProperty("lyricsDelay", "0"));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    public static EffectsConfig effects() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("lyricsflow/settings.json");
        if (Files.isRegularFile(path)) {
            try {
                EffectsConfig config =
                        new Gson().fromJson(ClientFiles.read(path), EffectsConfig.class);
                if (config != null) return config;
            } catch (Exception error) {
                LoggerFactory.getLogger("nosclient")
                        .warn("Không chuyển được cấu hình cũ {}", path, error);
            }
        }
        EffectsConfig config = new EffectsConfig();
        var values = scene(FabricLoader.getInstance().getConfigDir());
        if ("true".equals(values.getProperty("sky"))) {
            try {
                config.visualHour = Float.parseFloat(values.getProperty("skyHour", "18"));
            } catch (NumberFormatException error) {
                config.visualHour = 18;
            }
        }
        if ("true".equals(values.getProperty("weather"))) {
            config.sky = dev.jun.nosclient.sky.SkyPreset.CUSTOM.ordinal();
            String mode = values.getProperty("weatherMode", "0");
            config.customWeather =
                    switch (mode) {
                        case "1" -> dev.jun.nosclient.sky.SkyPreset.Weather.RAIN.ordinal();
                        case "2" -> dev.jun.nosclient.sky.SkyPreset.Weather.THUNDER.ordinal();
                        case "3" -> dev.jun.nosclient.sky.SkyPreset.Weather.SNOW.ordinal();
                        default -> dev.jun.nosclient.sky.SkyPreset.Weather.NONE.ordinal();
                    };
        }
        return config;
    }
}
