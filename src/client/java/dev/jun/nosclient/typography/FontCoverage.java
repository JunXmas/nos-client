package dev.jun.nosclient.typography;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class FontCoverage {
    private static final int[] RANGES = new int[]{
        13,
        13,
        32,
        126,
        160,
        263,
        266,
        275,
        278,
        283,
        286,
        289,
        294,
        299,
        302,
        305,
        313,
        314,
        317,
        318,
        321,
        324,
        327,
        328,
        332,
        333,
        336,
        341,
        344,
        347,
        350,
        363,
        366,
        382,
        416,
        417,
        431,
        432,
        486,
        487,
        710,
        711,
        728,
        732,
        768,
        772,
        774,
        780,
        786,
        786,
        795,
        795,
        803,
        803,
        806,
        808,
        7808,
        7813,
        7838,
        7838,
        7840,
        7929,
        8211,
        8212,
        8216,
        8218,
        8220,
        8222,
        8224,
        8226,
        8230,
        8230,
        8240,
        8240,
        8249,
        8250,
        8260,
        8260,
        8304,
        8304,
        8308,
        8313,
        8320,
        8329,
        8363,
        8364,
        8482,
        8482,
        8722,
        8722,
        8725,
        8725,
        8776,
        8776,
        8800,
        8800,
        8804,
        8805
    };

    private FontCoverage() {
    }

    public static boolean contains(int codepoint) {
        int low = 0;
        int high = RANGES.length / 2 - 1;

        while (low <= high) {
            int middle = low + high >>> 1;
            if (codepoint < RANGES[middle * 2]) {
                high = middle - 1;
            } else {
                if (codepoint <= RANGES[middle * 2 + 1]) {
                    return true;
                }

                low = middle + 1;
            }
        }

        return false;
    }
}
