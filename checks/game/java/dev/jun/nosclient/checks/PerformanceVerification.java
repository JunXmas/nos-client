package dev.jun.nosclient.checks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Cùng camera, tầm nhìn, thời gian chờ và nguồn nhạc cho jar mới lẫn jar đối chứng. */
public final class PerformanceVerification implements ClientModInitializer {
    private final Path output = Path.of(System.getProperty("nos.verify.output"));
    private final String scene = System.getProperty("nos.verify.scene", "off");
    private final List<Integer> samples = new ArrayList<>();
    private long started;
    private long sampledAt;
    private boolean finished;
    private Field lastInput;

    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(
                mc -> {
                    try {
                        Files.createDirectories(output);
                        mc.options.save();
                        Files.copy(
                                mc.gameDirectory.toPath().resolve("options.txt"),
                                output.resolve("options-loaded.txt"));
                        lastInput =
                                Class.forName("dev.jun.nosclient.feat.Feats").getField("lastInput");
                    } catch (Exception error) {
                        throw new IllegalStateException(error);
                    }
                });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        if (finished || mc.level == null || mc.player == null || mc.getConnection() == null) return;
        try {
            long now = System.nanoTime();
            mc.getFramerateLimitTracker().onInputReceived();
            lastInput.setLong(null, System.currentTimeMillis());
            if (mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying) {
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
                mc.getConnection().sendCommand("tp @s 64 200 64 -90 -15");
            }
            if (started == 0) {
                mc.setScreen(null);
                mc.options.hideGui = true;
                mc.getConnection().sendCommand("gamemode creative");
                mc.getConnection().sendCommand("tp @s 64 200 64 -90 -15");
                mc.getConnection().sendCommand("gamerule advance_time false");
                mc.getConnection().sendCommand("time set 14000");
                mc.getConnection().sendCommand("weather clear");
                configure();
                started = now;
            }
            if (now - started < 30_000_000_000L || now - sampledAt < 1_000_000_000L) return;
            sampledAt = now;
            samples.add(mc.getFps());
            if (samples.size() >= 30) {
                double average =
                        samples.stream().mapToInt(Integer::intValue).average().orElseThrow();
                List<Integer> sorted = samples.stream().sorted().toList();
                String text =
                        "scene="
                                + scene
                                + "\nsamples="
                                + samples
                                + "\nmean="
                                + average
                                + "\nmedian="
                                + sorted.get(15)
                                + "\nlimit="
                                + mc.getFramerateLimitTracker().getFramerateLimit()
                                + "\nthrottle="
                                + mc.getFramerateLimitTracker().getThrottleReason()
                                + "\n";
                Files.writeString(output.resolve("performance.txt"), text);
                Screenshot.grab(
                        output.toFile(),
                        "performance.png",
                        mc.getMainRenderTarget(),
                        1,
                        ignored -> {});
                Files.writeString(output.resolve("complete.txt"), text);
                finished = true;
                mc.stop();
            }
        } catch (Throwable error) {
            LoggerFactory.getLogger("nos-verification").error("PERFORMANCE FAILED", error);
            try {
                Files.writeString(output.resolve("failed.txt"), error.toString());
            } catch (Exception ignored) {
            }
            finished = true;
            mc.stop();
        }
    }

    private void configure() throws Exception {
        Object client =
                Class.forName("dev.jun.nosclient.scene.SceneConfig").getField("INSTANCE").get(null);
        boolean lyrics = !scene.equals("off") && !scene.equals("idle") && !scene.equals("sky");
        set(client, "lyrics", lyrics);
        set(client, "lyricsAurora", scene.equals("aurora"));
        set(client, "lyricsAutoFetch", false);
        set(client, "dfps", false);
        set(client, "sky", false);
        try {
            Object music =
                    Class.forName("dev.jun.nosclient.config.MusicSettings")
                            .getMethod("get")
                            .invoke(null);
            set(music, "enabled", !scene.equals("off"));
            set(music, "showTopBar", false);
            set(music, "showLyrics", lyrics);
            set(music, "autoPerf", false);
            set(music, "font", "nos-celestial");
            Object effects =
                    Class.forName("dev.jun.nosclient.ClientEffects").getMethod("cfg").invoke(null);
            set(effects, "sky", scene.equals("default") || scene.equals("sky") ? 1 : -1);
            set(effects, "hideClouds", false);
            set(
                    client,
                    "sky",
                    scene.equals("default") || scene.equals("sky") || scene.equals("aurora"));
        } catch (ClassNotFoundException baseline) {
            // Jar 0.3.1 dùng SceneConfig và nguồn nhạc cũ.
        }
    }

    private static void set(Object target, String field, Object value) throws Exception {
        try {
            target.getClass().getField(field).set(target, value);
        } catch (NoSuchFieldException obsolete) {
            /* Trường cũ đã được gộp trong bản 0.4.0. */
        }
    }
}
