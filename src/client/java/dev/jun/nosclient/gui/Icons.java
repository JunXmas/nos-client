package dev.jun.nosclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.jun.nosclient.theme.Theme;
import dev.jun.nosclient.theme.ThemePalette;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStarted;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

@Environment(EnvType.CLIENT)
public final class Icons {
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath("nosclient", "textures/gui/function-icons.png");
    private static final Identifier[] ATLASES = Arrays.stream(IconGeometry.DENSITIES)
        .mapToObj(size -> Identifier.fromNamespaceAndPath("nosclient", "ui/function_icons_" + size))
        .toArray(Identifier[]::new);
    private static boolean ready;

    private Icons() {
    }

    public static void init() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            public Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath("nosclient", "function_icons");
            }

            public void onResourceManagerReload(ResourceManager resources) {
                Icons.release();
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register((ClientStarted)mc -> prepare());
        ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)mc -> release());
    }

    private static void release() {
        if (ready) {
            for (Identifier id : ATLASES) {
                Minecraft.getInstance().getTextureManager().release(id);
            }

            ready = false;
        }
    }

    private static void prepare() {
        if (!ready) {
            Minecraft mc = Minecraft.getInstance();

            try (
                InputStream stream = mc.getResourceManager().getResource(SOURCE).orElseThrow().open();
                NativeImage source = NativeImage.read(stream);
            ) {
                if (source.getWidth() != 1774 || source.getHeight() != 887) {
                    throw new IOException("Unexpected Nos icon source dimensions");
                }

                for (int tier = 0; tier < ATLASES.length; tier++) {
                    int cell = IconGeometry.DENSITIES[tier];
                    NativeImage image = new NativeImage(cell * 8, cell * 4, true);

                    for (int i = 0; i < 32; i++) {
                        IconGeometry.Crop crop = IconGeometry.crop(i);
                        IconGeometry.Fit fit = IconGeometry.fit(crop, cell);

                        try (NativeImage resized = new NativeImage(fit.width(), fit.height(), false)) {
                            source.resizeSubRectTo(crop.x(), crop.y(), crop.width(), crop.height(), resized);

                            for (int y = 0; y < fit.height(); y++) {
                                for (int x = 0; x < fit.width(); x++) {
                                    image.setPixel(i % 8 * cell + fit.x() + x, i / 8 * cell + fit.y() + y, resized.getPixel(x, y));
                                }
                            }
                        }
                    }

                    var texture = new DynamicTexture(() -> "Nos padded icon atlas", image) {
                        {
                            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                        }
                    };
                    texture.upload();
                    mc.getTextureManager().register(ATLASES[tier], texture);
                }

                ready = true;
            } catch (IOException error) {
                throw new IllegalStateException("Unable to prepare Nos UI icons", error);
            }
        }
    }

    public static int index(String id) {
        return switch (id) {
            case "fps" -> 0;
            case "coords", "waypoint" -> 1;
            case "direction" -> 2;
            case "day" -> 3;
            case "cps" -> 4;
            case "ping" -> 5;
            case "effects" -> 6;
            case "invhud" -> 7;
            case "armor" -> 8;
            case "shulker" -> 9;
            case "recipe" -> 10;
            case "minimap", "map" -> 11;
            case "world" -> 12;
            case "zoom" -> 13;
            case "keys" -> 14;
            case "sprint" -> 15;
            case "freelook" -> 16;
            case "gamma" -> 17;
            case "light", "blockinfo" -> 18;
            case "lyrics" -> 19;
            case "weather" -> 20;
            case "sky" -> 21;
            case "mp" -> 22;
            case "dfps" -> 23;
            case "hud" -> 24;
            case "inv" -> 25;
            case "pvp" -> 26;
            case "sys" -> 27;
            case "search" -> 28;
            case "look" -> 29;
            case "edit" -> 30;
            default -> 31;
        };
    }

    public static void draw(GuiGraphics g, String name, float x, float y, float size) {
        if (!(size <= 0.0F) && !(UiOpacity.value <= 0.01F)) {
            prepare();
            double scale = Math.hypot(g.pose().m00(), g.pose().m01()) * Minecraft.getInstance().getWindow().getGuiScale();
            int tier = IconGeometry.tier(size * scale);
            int cell = IconGeometry.DENSITIES[tier];
            int index = index(name);
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(size / cell, size / cell);
            int tint = ThemePalette.mix(Theme.palette().text(), Theme.palette().accent(), 0.12);
            g.blit(RenderPipelines.GUI_TEXTURED, ATLASES[tier], 0, 0, index % 8 * cell, index / 8 * cell, cell, cell, cell * 8, cell * 4, UiOpacity.tint(tint));
            g.pose().popMatrix();
        }
    }
}
