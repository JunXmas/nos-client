package dev.jun.nosclient.feat;

import com.mojang.blaze3d.platform.Window;
import dev.jun.nosclient.Mods;
import dev.jun.nosclient.gui.HudEditScreen;
import dev.jun.nosclient.gui.IntroScreen;
import dev.jun.nosclient.gui.NosSettingsScreen;
import dev.jun.nosclient.gui.NosTitleScreen;
import dev.jun.nosclient.gui.WaypointScreen;
import dev.jun.nosclient.gui.WorldMapScreen;
import dev.jun.nosclient.hud.Minimap;
import dev.jun.nosclient.map.MapCache;
import dev.jun.nosclient.media.AuroraSky;
import dev.jun.nosclient.media.SkyLyrics;
import dev.jun.nosclient.scene.SceneConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class Feats {
    private static final SceneConfig c = SceneConfig.INSTANCE;
    public static KeyMapping ZOOM;
    public static KeyMapping FREELOOK;
    public static KeyMapping WAYPOINT;
    public static KeyMapping WAYPOINTS;
    public static KeyMapping HUD_EDIT;
    public static KeyMapping WORLD_MAP;
    public static KeyMapping MAP_IN;
    public static KeyMapping MAP_OUT;
    private static final boolean PROBE = Boolean.getBoolean("nosclient.probe");
    private static int probeTicks;
    public static boolean sprintOn;
    public static boolean sneakOn;
    public static double zoomNow = 1.0;
    public static double zoomTarget = 1.0;
    public static double zoomMul;
    public static boolean looking;
    public static float lookYaw;
    public static float lookPitch;
    private static CameraType lookPrev;
    private static boolean lookToggled;
    public static volatile float lastFov = 70.0F;
    public static volatile long lastInput = System.currentTimeMillis();

    private Feats() {
    }

    public static void init() {
        Category cat = Category.register(Identifier.fromNamespaceAndPath("nosclient", "feat"));
        ZOOM = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.zoom", Mods.ZOOM_OWNED ? -1 : 67, cat));
        FREELOOK = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.freelook", 342, cat));
        WAYPOINT = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.waypoint", 66, cat));
        WAYPOINTS = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.waypoints", 74, cat));
        HUD_EDIT = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.hudedit", 72, cat));
        WORLD_MAP = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.worldmap", 77, cat));
        MAP_IN = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.mapin", 61, cat));
        MAP_OUT = KeyBindingHelper.registerKeyBinding(new KeyMapping("nosclient.key.mapout", 45, cat));
        ClientChunkEvents.CHUNK_LOAD.register(MapCache::onChunkLoad);
        ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)mc -> MapCache.flush(true));
        zoomMul = c.zoomLevel;
        ClientTickEvents.END_CLIENT_TICK.register(Feats::tick);
    }

    private static void tick(Minecraft mc) {
        if (PROBE && ++probeTicks % 100 == 0) {
            LoggerFactory.getLogger("nosclient")
                .info(
                    "probe fps={} lyrics-cpu-us={} aurora-cpu-us={} lyric-frames={} fps-limit={} reason={} gamma-opt={} gamma-nos={} screen={}",
                    new Object[]{
                        mc.getFps(),
                        SkyLyrics.renderFrames == 0 ? 0 : SkyLyrics.renderNanos / SkyLyrics.renderFrames / 1000,
                        SkyLyrics.renderFrames == 0 ? 0 : AuroraSky.renderNanos / SkyLyrics.renderFrames / 1000,
                        SkyLyrics.renderFrames,
                        mc.getFramerateLimitTracker().getFramerateLimit(),
                        mc.getFramerateLimitTracker().getThrottleReason(),
                        mc.options.gamma().get(),
                        c.gamma,
                        mc.screen == null ? "-" : mc.screen.getClass().getSimpleName()
                    }
                );
        }

        DynLight.tick(mc);
        Minimap.tick(mc);
        if (mc.player == null) {
            looking = false;
        } else {
            sprint(mc);

            while (WAYPOINTS.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new WaypointScreen(null));
                }
            }

            while (WORLD_MAP.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new WorldMapScreen(null));
                }
            }

            while (MAP_IN.consumeClick()) {
                if (c.minimap) {
                    c.minimapZoom = Math.min(3, c.minimapZoom + 1);
                    c.save();
                }
            }

            while (MAP_OUT.consumeClick()) {
                if (c.minimap) {
                    c.minimapZoom = Math.max(0, c.minimapZoom - 1);
                    c.save();
                }
            }

            while (HUD_EDIT.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new HudEditScreen(null));
                }
            }

            while (WAYPOINT.consumeClick()) {
                if (c.waypoint) {
                    if (Waypoints.near(mc, 2) != null) {
                        mc.gui.setOverlayMessage(Component.translatable("nosclient.wp.exists", WAYPOINTS.getTranslatedKeyMessage()), false);
                    } else {
                        Waypoints.Wp wp = Waypoints.addHere(mc);
                        mc.gui.setOverlayMessage(Component.translatable("nosclient.wp.added", wp.name(), wp.x(), wp.y(), wp.z()), false);
                    }
                }
            }

            boolean z = c.zoom && !Mods.ZOOM_OWNED && ZOOM.isDown() && mc.screen == null;
            if (!z) {
                zoomMul = c.zoomLevel;
            }

            zoomTarget = z ? zoomMul : 1.0;
            freelook(mc);
        }
    }

    private static void sprint(Minecraft mc) {
        Options o = mc.options;
        if (!c.sprint) {
            if (sprintOn || sneakOn) {
                sneakOn = false;
                sprintOn = false;
                o.keySprint.setDown(false);
            }
        } else {
            while (o.keySprint.consumeClick()) {
                sprintOn = !sprintOn;
            }

            if (sprintOn) {
                o.keySprint.setDown(true);
            }

            if (!c.sprintSneak) {
                sneakOn = false;
            } else {
                while (o.keyShift.consumeClick()) {
                    sneakOn = !sneakOn;
                }

                if (sneakOn) {
                    o.keyShift.setDown(true);
                }
            }
        }
    }

    private static void freelook(Minecraft mc) {
        if (c.freelookToggle) {
            while (FREELOOK.consumeClick()) {
                lookToggled = !lookToggled;
            }
        } else {
            lookToggled = false;
        }

        boolean want = c.freelook && (c.freelookToggle ? lookToggled : FREELOOK.isDown()) && mc.screen == null;
        if (want && !looking) {
            looking = true;
            lookYaw = mc.player.getYRot();
            lookPitch = mc.player.getXRot();
            lookPrev = mc.options.getCameraType();
            if (lookPrev.isFirstPerson()) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        } else if (!want && looking) {
            looking = false;
            if (lookPrev != null) {
                mc.options.setCameraType(lookPrev);
            }
        }
    }

    public static boolean scroll(double dy) {
        if (!(zoomTarget <= 1.0) && dy != 0.0 && c.zoomScroll) {
            zoomMul = Math.min(50.0, Math.max(1.5, zoomMul * (dy > 0.0 ? 1.25 : 0.8)));
            zoomTarget = zoomMul;
            return true;
        } else {
            return false;
        }
    }

    public static boolean nosScreen(Screen s) {
        return s instanceof NosTitleScreen || s instanceof IntroScreen || s instanceof NosSettingsScreen;
    }

    public static int fpsCap(Minecraft mc) {
        if (c.dfps && !Mods.DFPS_OWNED) {
            Window w = mc.getWindow();
            if (!c.dfpsMin || !w.isMinimized() && !w.isIconified()) {
                if (!mc.isWindowActive()) {
                    return c.dfpsBg;
                } else {
                    return c.dfpsAfk && mc.screen == null && System.currentTimeMillis() - lastInput > 300000L ? 10 : -1;
                }
            } else {
                return 1;
            }
        } else {
            return -1;
        }
    }
}
