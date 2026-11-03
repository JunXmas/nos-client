package dev.jun.nosclient.checks;

import dev.jun.nosclient.ClientEffects;
import dev.jun.nosclient.MediaKeys;
import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.gui.HudEditScreen;
import dev.jun.nosclient.gui.NosSettingsScreen;
import dev.jun.nosclient.gui.music.PlayerScreen;
import dev.jun.nosclient.render.Fonts;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.sky.SkyPreset;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** Chạy riêng trong world đã chép, chụp từng preset và từng trang hub từ framebuffer thật. */
public final class GameVerification implements ClientModInitializer {
    private Path output;
    private CloudStatus clouds;
    private long since;
    private int phase = -1;
    private long loadedAt;
    private boolean finished;
    private boolean photographed;
    private boolean photographedTop;
    private HubVerification hub;
    private HudVerification hud;
    private int originalWidth;
    private int originalHeight;
    private java.util.List<Fonts.LyricFont> fonts;
    private final int presetCount =
            Boolean.getBoolean("nos.verify.quick") ? 0 : SkyPreset.values().length;
    private final String[] panels = {
        "music",
        "lyrics",
        "textstyle",
        "sky",
        "accessories",
        "karaoke",
        "mediaKeys",
        "mediaProfiles",
        "visualPerformance"
    };

    public void onInitializeClient() {
        output = Path.of(System.getProperty("nos.verify.output"));
        ClientLifecycleEvents.CLIENT_STARTED.register(
                mc -> {
                    for (KeyMapping own : MediaKeys.all()) {
                        var binding = KeyBindingHelper.getBoundKeyOf(own);
                        for (KeyMapping other : mc.options.keyMappings) {
                            if (own != other
                                    && binding.equals(KeyBindingHelper.getBoundKeyOf(other)))
                                throw new AssertionError(
                                        "Media key conflict: "
                                                + own.getName()
                                                + " and "
                                                + other.getName());
                        }
                    }
                    clouds = mc.options.cloudStatus().get();
                    originalWidth = mc.getWindow().getWidth();
                    originalHeight = mc.getWindow().getHeight();
                    try {
                        Files.createDirectories(output);
                        mc.options.save();
                        Files.copy(
                                mc.gameDirectory.toPath().resolve("options.txt"),
                                output.resolve("options-loaded.txt"));
                    } catch (Exception error) {
                        throw new IllegalStateException(error);
                    }
                });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        try {
            if (finished) return;
            if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
            long now = System.nanoTime();
            mc.getFramerateLimitTracker().onInputReceived();
            if (mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying) {
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
                mc.getConnection().sendCommand("tp @s 64 200 64 -90 -15");
            }
            if (loadedAt == 0) loadedAt = now;
            if (now - loadedAt < 15_000_000_000L) return;
            if (phase == -1) {
                mc.setScreen(null);
                mc.getConnection().sendCommand("gamemode creative");
                mc.getConnection().sendCommand("tp @s 64 200 64 -90 -15");
                mc.getConnection().sendCommand("gamerule advance_time false");
                mc.getConnection().sendCommand("weather clear");
                MusicSettings.get().showLyrics = true;
                MusicSettings.get().enabled = true;
                MusicSettings.get().source = MusicConfig.Source.ANY;
                MusicSettings.get().lyricsStyle = MusicConfig.LyricsStyle.SKY;
                fonts =
                        Boolean.getBoolean("nos.verify.uiOnly")
                                ? java.util.List.of()
                                : Fonts.lyricsAll();
                phase = 0;
                apply(mc);
            }
            double elapsed = (now - since) / 1e9;
            boolean inHub = phase >= presetCount * 2 && phase < presetCount * 2 + panels.length;
            if (inHub && !(mc.screen instanceof NosSettingsScreen))
                throw new AssertionError("Expected Nos hub, got " + mc.screen);
            if (inHub && mc.screen instanceof NosSettingsScreen screen) {
                if (elapsed > .6 && !photographedTop) {
                    Screenshot.grab(
                            output.toFile(),
                            label() + "-top.png",
                            mc.getMainRenderTarget(),
                            1,
                            ignored -> {});
                    photographedTop = true;
                }
                hub.tick(screen, elapsed);
            }
            if (hud != null && mc.screen instanceof HudEditScreen) hud.tick(mc, mc.screen, elapsed);
            if (elapsed >= (inHub ? 5 : 3) && !photographed) {
                String name = label();
                Files.createDirectories(output);
                Screenshot.grab(
                        output.toFile(), name + ".png", mc.getMainRenderTarget(), 1, ignored -> {});
                LoggerFactory.getLogger("nos-verification")
                        .info(
                                "CAPTURE {} fps={} cloud={}",
                                name,
                                mc.getFps(),
                                mc.options.cloudStatus().get());
                photographed = true;
            }
            if (elapsed >= (inHub ? 6 : 4)) {
                phase++;
                apply(mc);
            }
        } catch (Throwable error) {
            LoggerFactory.getLogger("nos-verification").error("VERIFY FAILED", error);
            try {
                Files.writeString(output.resolve("failed.txt"), error.toString());
            } catch (Exception ignored) {
            }
            finished = true;
            mc.stop();
        }
    }

    private String label() {
        int count = presetCount * 2;
        if (phase < count)
            return "sky-"
                    + SkyPreset.values()[phase / 2].name().toLowerCase()
                    + (phase % 2 == 0 ? "-night" : "-day");
        int part = phase - count;
        if (part < panels.length) return "hub-" + panels[part];
        part -= panels.length;
        if (part == 0) return "player";
        if (part <= fonts.size()) return "font-" + fonts.get(part - 1).id();
        return switch (part - fonts.size()) {
            case 1 -> "floating";
            case 2 -> "sky-music-disabled";
            case 3 -> "sky-disabled";
            case 4 -> "cosmetic-preview";
            case 5 -> "cosmetic-disabled";
            case 6 -> "hub-resize-small";
            case 7 -> "hub-resize-large";
            case 8 -> "hud-karaoke-before";
            case 9 -> "hud-karaoke-after";
            default -> "done";
        };
    }

    private void apply(Minecraft mc) throws Exception {
        since = System.nanoTime();
        photographed = false;
        photographedTop = false;
        int count = presetCount * 2;
        if (phase < count) {
            mc.options.hideGui = true;
            ClientEffects.cfg().sky = phase / 2;
            SceneConfig.INSTANCE.sky = true;
            mc.getConnection().sendCommand("time set " + (phase % 2 == 0 ? 14000 : 6000));
        } else if (phase < count + panels.length) {
            mc.options.hideGui = false;
            mc.setScreen(NosSettingsScreen.forFeature(null, panels[phase - count]));
            hub = new HubVerification();
            boolean enabled = MusicSettings.get().enabled;
            int offset = MusicSettings.get().syncOffsetMs;
            var songOffsets = new java.util.HashMap<>(MusicSettings.get().songOffsets);
            KeyMapping.click(MediaKeys.toggle.getDefaultKey());
            KeyMapping.click(MediaKeys.lyricsEarlier.getDefaultKey());
            MediaKeys.tick(mc);
            if (enabled != MusicSettings.get().enabled
                    || offset != MusicSettings.get().syncOffsetMs
                    || !songOffsets.equals(MusicSettings.get().songOffsets))
                throw new AssertionError("media hotkeys changed settings while typing");
        } else if (phase == count + panels.length) {
            mc.setScreen(new PlayerScreen());
        } else if (phase < count + panels.length + 1 + fonts.size()) {
            mc.options.hideGui = true;
            mc.setScreen(null);
            ClientEffects.cfg().sky = SkyPreset.AURORA.ordinal();
            mc.getConnection().sendCommand("time set 14000");
            MusicSettings.get().font = fonts.get(phase - count - panels.length - 1).id();
            SceneConfig.INSTANCE.lyricsFollow = false;
            SceneConfig.INSTANCE.lyricsBearing = 270;
            SceneConfig.INSTANCE.lyricsElevation = 15;
        } else if (phase == count + panels.length + 1 + fonts.size()) {
            mc.setScreen(null);
            mc.options.hideGui = true;
            MusicSettings.get().lyricsStyle = MusicConfig.LyricsStyle.FLOATING;
        } else if (phase == count + panels.length + fonts.size() + 2) {
            mc.setScreen(null);
            MusicSettings.get().enabled = false;
            SceneConfig.INSTANCE.sky = true;
            ClientEffects.cfg().sky = SkyPreset.AURORA.ordinal();
        } else if (phase == count + panels.length + fonts.size() + 3) {
            SceneConfig.INSTANCE.sky = false;
        } else if (phase == count + panels.length + fonts.size() + 4) {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            ClientEffects.cfg().cosmeticsEnabled = true;
            ClientEffects.cfg().aura = true;
            ClientEffects.cfg().trail = 1;
            MusicSettings.get().cosmetic = "cap_back";
            dev.jun.nosclient.cosmetic.effects.DeathFx.test();
        } else if (phase == count + panels.length + fonts.size() + 5) {
            ClientEffects.cfg().cosmeticsEnabled = false;
        } else if (phase == count + panels.length + fonts.size() + 6) {
            mc.options.hideGui = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.getWindow().setWindowed(960, 540);
            mc.setScreen(NosSettingsScreen.forFeature(null, "lyrics"));
        } else if (phase == count + panels.length + fonts.size() + 7) {
            mc.getWindow().setWindowed(originalWidth, originalHeight);
            MusicSettings.get().enabled = true;
            SceneConfig.INSTANCE.sky = true;
            mc.setScreen(NosSettingsScreen.forFeature(null, "sky"));
        } else if (phase == count + panels.length + fonts.size() + 8) {
            MusicSettings.get().enabled = false;
            mc.setScreen(new HudEditScreen(null));
        } else if (phase == count + panels.length + fonts.size() + 9) {
            hud = new HudVerification();
        } else {
            SceneConfig.INSTANCE.sky = false;
            dev.jun.nosclient.sky.SkyRenderer.restoreClouds(mc);
            if (mc.options.cloudStatus().get() != clouds)
                throw new AssertionError("cloud preference changed");
            mc.level.setRainLevel(.35F);
            mc.level.setThunderLevel(.2F);
            float expectedRain = mc.level.getRainLevel(1);
            float expectedThunder = mc.level.getThunderLevel(1);
            ClientEffects.cfg().sky = SkyPreset.CUSTOM.ordinal();
            ClientEffects.cfg().customWeather = SkyPreset.Weather.THUNDER.ordinal();
            ClientEffects.cfg().realWeather = true;
            SceneConfig.INSTANCE.sky = true;
            dev.jun.nosclient.sky.SkyRenderer.tick(mc);
            if (mc.level.getRainLevel(1) != 1)
                throw new AssertionError("client rain did not start");
            SceneConfig.INSTANCE.sky = false;
            dev.jun.nosclient.sky.SkyRenderer.tick(mc);
            if (Math.abs(mc.level.getRainLevel(1) - expectedRain) > .001F
                    || Math.abs(mc.level.getThunderLevel(1) - expectedThunder) > .001F)
                throw new AssertionError("client weather was not restored");
            Files.writeString(
                    output.resolve("complete.txt"),
                    "preset captures="
                            + count
                            + ", hub captures="
                            + panels.length
                            + ", fonts="
                            + fonts.size()
                            + ", cloud/weather restoration=passed, input guard=passed, HUD"
                            + " drag/scale/opacity=passed\n");
            finished = true;
            mc.stop();
        }
    }
}
