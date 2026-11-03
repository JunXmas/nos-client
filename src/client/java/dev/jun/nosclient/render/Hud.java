package dev.jun.nosclient.render;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.feat.AutoPerf;
import dev.jun.nosclient.gui.music.UiSounds;
import dev.jun.nosclient.media.MediaManager;
import dev.jun.nosclient.media.lyrics.Lyrics;
import dev.jun.nosclient.util.Anim;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

public final class Hud {
    public static float beat;
    public static float intensity;
    public static float skyIntensity;
    public static float focusA = 1.0F;
    private static boolean wasActive = true;
    private static long backAt = -1L;
    private static int lastWordKey = Integer.MIN_VALUE;
    private static long lastFrame = -1L;

    private Hud() {}

    public static void frame() {
        Anim.frame();
        Minecraft mcw = Minecraft.getInstance();
        Proj.update(mcw.getWindow().getGuiScaledWidth(), mcw.getWindow().getGuiScaledHeight());
        if (Anim.frameId() != lastFrame) {
            lastFrame = Anim.frameId();
            AutoPerf.frame();
            MediaManager mm = MediaManager.get();

            MediaManager.Event e;
            while ((e = mm.pollEvent()) != null) {
                TopBar.onEvent(e);
                WorldLyrics.onEvent(e);
                if (e.type() == MediaManager.EventType.SONG
                        && MusicSettings.get().enabled
                        && MusicSettings.get().songChime) {
                    UiSounds.play(UiSounds.SONG, 1.0F);
                }
            }

            boolean active = mcw.isWindowActive();
            if (active && !wasActive) {
                mm.resync();
                backAt = System.nanoTime();
            }

            wasActive = active;
            boolean waiting = backAt > 0L && System.nanoTime() - backAt < 450000000L;
            if (!waiting) {
                backAt = -1L;
            }

            focusA = 1.0F;
            MusicConfig c = MusicSettings.get();
            boolean on = c.enabled && mm.isPlaying();
            intensity = Anim.approach(intensity, on ? 1.0F : 0.0F, on ? 2.2F : 1.2F);
            boolean skyOn = c.enabled && (mm.isPlaying() || c.skyAlways);
            skyIntensity = Anim.approach(skyIntensity, skyOn ? 1.0F : 0.0F, skyOn ? 2.2F : 1.2F);
            Lyrics l = mm.lyrics();
            long t = mm.position();
            if (l != null && t >= 0L && mm.isPlaying()) {
                Lyrics.Line line = l.lineAt(t);
                if (line != null) {
                    int w = line.wordAt(t);
                    int key = line.index * 1000 + w;
                    if (w >= 0 && key != lastWordKey) {
                        lastWordKey = key;
                        beat = 1.0F;
                    }
                }
            }

            beat = Math.max(0.0F, beat - Anim.dt() * 3.2F);
        }
    }

    public static void renderWorldSky(PoseStack pose, MultiBufferSource buffers) {
        renderWorldFx(pose, buffers, false);
    }

    public static void renderFarSky() {
        renderWorldFx(new PoseStack(), null, true);
    }

    private static void renderWorldFx(PoseStack pose, MultiBufferSource buffers, boolean sky) {
        AutoPerf.begin();

        try {
            renderWorldFx0(pose, buffers, sky);
        } finally {
            AutoPerf.end();
        }
    }

    private static void renderWorldFx0(PoseStack pose, MultiBufferSource buffers, boolean sky) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            frame();
            MusicConfig c = MusicSettings.get();
            if (!sky && c.enabled && c.showLyrics) {
                WorldLyrics.tick();
            }
        }
    }

    public static void renderTopLayer(GuiGraphics g, DeltaTracker tick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            frame();
        }

        if (mc.level != null && mc.player != null) {
            MusicConfig c = MusicSettings.get();
            if (c.showTopBar && (c.enabled || dev.jun.nosclient.hud.Hud.INSTANCE.editing)) {
                AutoPerf.begin();

                try {
                    TopBar.render(g, dev.jun.nosclient.hud.Hud.INSTANCE.editing);
                } finally {
                    AutoPerf.end();
                }
            }
        }
    }
}
