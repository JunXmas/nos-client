package dev.jun.nosclient.typography;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Typography {
    public static final int[] DENSITIES = new int[]{2, 3, 4, 6, 8, 12};
    public static final int[] WORLD_DENSITIES = new int[]{2, 3, 4, 6, 8, 12, 16, 24, 32};

    private Typography() {
    }

    public static int densityIndex(double pixelsPerUnit) {
        return densityIndex(pixelsPerUnit, DENSITIES);
    }

    public static int worldDensityIndex(double pixelsPerUnit) {
        return densityIndex(pixelsPerUnit, WORLD_DENSITIES);
    }

    private static int densityIndex(double pixelsPerUnit, int[] tiers) {
        if (Double.isFinite(pixelsPerUnit) && !(pixelsPerUnit <= 0.0)) {
            for (int i = 0; i < tiers.length; i++) {
                if (tiers[i] / 2.0 >= pixelsPerUnit) {
                    return i;
                }
            }

            return tiers.length - 1;
        } else {
            return 0;
        }
    }

    public static double snap(double coordinate, double guiScale) {
        return Double.isFinite(coordinate) && Double.isFinite(guiScale) && guiScale > 0.0 ? Math.rint(coordinate * guiScale) / guiScale : 0.0;
    }

    public static double hudScale(double requested, double guiScale, boolean readable) {
        if (!Double.isFinite(requested) || requested <= 0.0) {
            requested = 0.8;
        }

        if (!Double.isFinite(guiScale) || guiScale <= 0.0) {
            guiScale = 1.0;
        }

        return readable ? Math.max(requested, 10.0 / (6.5 * guiScale)) : requested;
    }
}
