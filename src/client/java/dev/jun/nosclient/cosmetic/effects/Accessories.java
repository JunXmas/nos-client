package dev.jun.nosclient.cosmetic.effects;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

public final class Accessories {
    public static final Identifier WHITE =
            Identifier.fromNamespaceAndPath("nosclient", "cosmetic/white");
    private static boolean whiteReady;
    private static boolean whiteFailed;

    private Accessories() {}

    public static boolean whiteTex() {
        return white();
    }

    private static boolean white() {
        if (whiteReady) {
            return true;
        }

        if (whiteFailed) {
            return false;
        }

        try {
            BufferedImage img = new BufferedImage(4, 4, 2);

            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    img.setRGB(x, y, -1);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            Minecraft.getInstance()
                    .getTextureManager()
                    .register(
                            WHITE,
                            new DynamicTexture(
                                    () -> "nosclient_white", NativeImage.read(out.toByteArray())));
            whiteReady = true;
        } catch (Throwable var3) {
            whiteFailed = true;
        }

        return whiteReady;
    }
}
