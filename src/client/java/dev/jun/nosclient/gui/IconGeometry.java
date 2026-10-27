package dev.jun.nosclient.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class IconGeometry {
    public static final int SOURCE_WIDTH = 1774;
    public static final int SOURCE_HEIGHT = 887;
    public static final int[] DENSITIES = new int[]{16, 32, 64, 128};
    private static final int[][] BOUNDS = new int[][]{
        {28, 68, 226, 232},
        {270, 67, 408, 255},
        {457, 66, 654, 249},
        {669, 63, 862, 248},
        {898, 79, 1091, 243},
        {1140, 92, 1315, 243},
        {1357, 62, 1518, 251},
        {1566, 76, 1748, 246},
        {38, 280, 213, 458},
        {249, 277, 427, 462},
        {449, 299, 658, 458},
        {677, 281, 878, 458},
        {898, 280, 1088, 462},
        {1137, 290, 1308, 460},
        {1330, 303, 1549, 443},
        {1571, 281, 1756, 465},
        {31, 518, 222, 646},
        {247, 490, 433, 677},
        {485, 486, 620, 670},
        {702, 503, 867, 653},
        {906, 508, 1087, 670},
        {1139, 504, 1296, 663},
        {1334, 511, 1536, 661},
        {1567, 497, 1757, 663},
        {26, 688, 247, 858},
        {250, 678, 422, 859},
        {466, 686, 649, 864},
        {677, 680, 867, 864},
        {912, 699, 1081, 858},
        {1135, 688, 1317, 861},
        {1344, 682, 1525, 863},
        {1569, 707, 1756, 857}
    };

    private IconGeometry() {
    }

    public static IconGeometry.Crop crop(int index) {
        int[] b = BOUNDS[index];
        return new IconGeometry.Crop(b[0], b[1], b[2] - b[0], b[3] - b[1]);
    }

    public static IconGeometry.Fit fit(IconGeometry.Crop source, int cell) {
        double scale = cell * 0.84 / Math.max(source.width, source.height);
        int w = Math.max(1, (int)Math.round(source.width * scale));
        int h = Math.max(1, (int)Math.round(source.height * scale));
        return new IconGeometry.Fit((cell - w) / 2, (cell - h) / 2, w, h);
    }

    public static int tier(double pixels) {
        for (int i = 0; i < DENSITIES.length; i++) {
            if (DENSITIES[i] >= pixels) {
                return i;
            }
        }

        return DENSITIES.length - 1;
    }

    @Environment(EnvType.CLIENT)
    public record Crop(int x, int y, int width, int height) {
    }

    @Environment(EnvType.CLIENT)
    public record Fit(int x, int y, int width, int height) {
    }
}
