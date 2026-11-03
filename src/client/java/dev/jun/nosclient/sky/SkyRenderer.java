package dev.jun.nosclient.sky;

import dev.jun.nosclient.ClientEffects;
import dev.jun.nosclient.config.EffectsConfig;
import dev.jun.nosclient.gui.effects.Gfx;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

public final class SkyRenderer {
    private static float sw;
    private static float sh;
    private static final Random RND = new Random();
    private static long flashAt = 0L;
    private static long nextFlash = 0L;
    private static net.minecraft.client.multiplayer.ClientLevel rainWorld;
    private static float rainBefore;
    private static float thunderBefore;

    private SkyRenderer() {}

    public static void tick(Minecraft mc) {
        EffectsConfig c = ClientEffects.cfg();
        updateClouds(mc, c);
        if (mc.level == null) {
            restoreRain();
        } else {
            SkyPreset p = SkyPreset.get(c.sky);
            boolean wantRain =
                    dev.jun.nosclient.scene.SceneConfig.INSTANCE.sky
                            && p != null
                            && c.realWeather
                            && (p.weather == SkyPreset.Weather.RAIN
                                    || p.weather == SkyPreset.Weather.THUNDER)
                            && ClientEffects.skyWorld(mc.level);
            if (wantRain) {
                if (rainWorld != mc.level) {
                    restoreRain();
                    rainWorld = mc.level;
                    rainBefore = mc.level.getRainLevel(1);
                    thunderBefore =
                            ((dev.jun.nosclient.mixin.LevelWeatherAccessor) mc.level)
                                    .nosclient$thunderLevel();
                }
                mc.level.setRainLevel(1.0F);
                mc.level.setThunderLevel(p.weather == SkyPreset.Weather.THUNDER ? 1.0F : 0.0F);
            } else {
                restoreRain();
            }
        }
    }

    private static void updateClouds(Minecraft mc, EffectsConfig c) {
        boolean hide =
                dev.jun.nosclient.scene.SceneConfig.INSTANCE.sky
                        && c.hideClouds
                        && c.sky >= 0
                        && mc.level != null
                        && ClientEffects.skyWorld(mc.level);
        OptionInstance<CloudStatus> opt = mc.options.cloudStatus();
        if (hide && opt.get() != CloudStatus.OFF) {
            if (c.cloudsBefore.isEmpty()) c.cloudsBefore = ((CloudStatus) opt.get()).name();
            opt.set(CloudStatus.OFF);
            ClientEffects.saveConfig();
        } else if (!hide && !c.cloudsBefore.isEmpty()) {
            try {
                opt.set(CloudStatus.valueOf(c.cloudsBefore));
            } catch (IllegalArgumentException error) {
                ClientEffects.LOG.warn(
                        "Không khôi phục được tuỳ chọn mây {}", c.cloudsBefore, error);
            }

            c.cloudsBefore = "";
            ClientEffects.saveConfig();
        }
    }

    private static void restoreRain() {
        if (rainWorld != null) {
            rainWorld.setRainLevel(rainBefore);
            rainWorld.setThunderLevel(thunderBefore);
            rainWorld = null;
        }
    }

    public static void restoreClouds(Minecraft mc) {
        restoreRain();
        EffectsConfig c = ClientEffects.cfg();
        if (!c.cloudsBefore.isEmpty()) {
            try {
                mc.options.cloudStatus().set(CloudStatus.valueOf(c.cloudsBefore));
            } catch (IllegalArgumentException error) {
                ClientEffects.LOG.warn("Không khôi phục được mây", error);
            }
            c.cloudsBefore = "";
            ClientEffects.saveConfig();
            mc.options.save();
        }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.hideGui && mc.player != null && mc.level != null) {
            EffectsConfig c = ClientEffects.cfg();
            sw = g.guiWidth();
            sh = g.guiHeight();
            SkyPreset p = SkyPreset.get(c.sky);
            float old = Gfx.alpha;
            Gfx.alpha = 1.0F;
            if (dev.jun.nosclient.scene.SceneConfig.INSTANCE.sky
                    && c.fxWeather
                    && p != null
                    && p.weather == SkyPreset.Weather.THUNDER
                    && ClientEffects.skyWorld(mc.level)) {
                drawLightning(g);
            }

            Gfx.alpha = old;
        }
    }

    private static void drawLightning(GuiGraphics g) {
        long now = System.currentTimeMillis();
        if (now > nextFlash) {
            flashAt = now;
            nextFlash = now + 5000L + RND.nextInt(9000);
        }

        long age = now - flashAt;
        if (age < 600L) {
            float a =
                    age < 90L
                            ? 0.32F
                            : (age < 180L
                                    ? 0.05F
                                    : (age < 260L
                                            ? 0.22F
                                            : 0.22F * (1.0F - (float) (age - 260L) / 340.0F)));
            Gfx.rect(g, 0, 0, (int) sw, (int) sh, 15266047, Math.max(0.0F, a));
        }
    }
}
