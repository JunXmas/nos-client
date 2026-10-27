package dev.jun.nosclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.Random;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class AuroraBackground {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("nosclient", "ui/night_backdrop");
    private static boolean ready;
    private static final int W = 1024;
    private static final int H = 576;

    private AuroraBackground() {
    }

    private static void prepare() {
        if (!ready) {
            NativeImage image = new NativeImage(1024, 576, false);

            for (int y = 0; y < 576; y++) {
                for (int x = 0; x < 1024; x++) {
                    double u = x / 1024.0;
                    double v = y / 576.0;
                    double band = Math.exp(-Math.pow((v - 0.34 - 0.13 * Math.sin(u * 5.5)) / 0.19, 2.0));
                    double ribbons = 0.35 + 0.65 * Math.pow(0.5 + 0.5 * Math.sin(u * 37.0 + Math.sin(u * 11.0) * 2.5), 3.0);
                    double light = band * ribbons * (0.35 + 0.65 * Math.sin(Math.PI * u));
                    int r = 7 + (int)(light * 14.0);
                    int g = 9 + (int)(light * 22.0);
                    int b = 16 + (int)(light * 35.0);
                    if (v > 0.77 + 0.035 * Math.sin(u * 14.0) + 0.025 * Math.cos(u * 23.0)) {
                        r = 13;
                        g = 16;
                        b = 25;
                    }

                    if (v > 0.84 + 0.035 * Math.sin(u * 18.0 + 1.0)) {
                        r = 9;
                        g = 11;
                        b = 18;
                    }

                    if (v > 0.91 + 0.035 * Math.cos(u * 15.0 + 0.8)) {
                        r = 5;
                        g = 7;
                        b = 12;
                    }

                    image.setPixel(x, y, 0xFF000000 | r << 16 | g << 8 | b);
                }
            }

            Random random = new Random(1313821524L);

            for (int i = 0; i < 185; i++) {
                int x = random.nextInt(1024);
                int y = random.nextInt(384);
                int a = 48 + random.nextInt(85);
                image.setPixel(x, y, 0xFF000000 | a << 16 | a + 5 << 8 | a + 12);
            }

            DynamicTexture texture = new DynamicTexture(() -> "Nos night backdrop", image) {
                {
                    this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                }
            };
            texture.upload();
            Minecraft.getInstance().getTextureManager().register(ID, texture);
            ready = true;
        }
    }

    public static void render(GuiGraphics g, int width, int height) {
        prepare();
        g.pose().pushMatrix();
        g.pose().scale(width / 1024.0F, height / 576.0F);
        g.blit(RenderPipelines.GUI_TEXTURED, ID, 0, 0, 0.0F, 0.0F, 1024, 576, 1024, 576);
        g.pose().popMatrix();
    }
}
