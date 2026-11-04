package dev.jun.nosclient.sky;

import java.util.Random;

public final class SkyPainter {
    public static final float EL_TOP = 90.0F;
    public static final float EL_BOTTOM = -8.0F;
    public static final int SPRITE = 64;

    private SkyPainter() {}

    private static int c255(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255.0F)));
    }

    public static int[] base(SkyPainter.Spec s, int w, int h) {
        SkyPainter.Buf b = new SkyPainter.Buf(w, h);
        Random rnd = new Random(s.kind().hashCode() * 7919L + 13L);
        String k = s.kind();
        boolean day = k.equals("CLOUDS") || k.equals("WINTER");
        int a1 = s.a1();
        int a2 = s.a2();
        switch (k) {
            case "NEBULA":
                nebula(b, a1, a2, 1.0F, 11);
                nebula(b, a2, 5930239, 0.35F, 29);
                horizonGlow(b, a1, 0.3F, 16.0F);
                break;
            case "GALAXY":
                milkyWay(b, a1, a2, 1.15F);
                nebula(b, a1, a2, 0.45F, 5);
                horizonGlow(b, a2, 0.25F, 14.0F);
                break;
            case "STARRY":
                milkyWay(b, 10466559, 16771280, 0.75F);
                horizonGlow(b, 3890088, 0.3F, 14.0F);
                break;
            case "MIDNIGHT":
                milkyWay(b, 8162559, 14213375, 0.95F);
                nebula(b, 3819720, 8162559, 0.25F, 41);
                horizonGlow(b, 2767504, 0.35F, 12.0F);
                break;
            case "PURPLE":
            case "NEON":
            case "ROSE":
                nebula(b, a1, a2, 0.45F, 3);
                milkyWay(b, a1, 16777215, 0.35F);
                horizonGlow(b, a1, 0.35F, 12.0F);
                break;
            case "NORTHERN":
            case "OCEAN":
            case "ICE":
            case "CUSTOM":
                milkyWay(b, 10466559, a2, 0.4F);
                horizonGlow(b, a1, 0.3F, 10.0F);
                break;
            case "PILLARS":
                nebula(b, a1, a2, 0.4F, 17);
                horizonGlow(b, a1, 0.7F, 14.0F);
                break;
            case "LAGOON":
                nebula(b, a1, a2, 0.25F, 37);
                horizonGlow(b, a2, 0.4F, 18.0F);
                break;
            case "BLOOD_MOON":
                nebula(b, 11540522, 16734780, 0.5F, 23);
                horizonGlow(b, 16726826, 0.45F, 12.0F);
                break;
            case "ECLIPSE":
                milkyWay(b, 9207039, 16766632, 0.45F);
                nebula(b, a2, a1, 0.25F, 31);
                break;
            case "SUNSET":
                rays(b, 16756848, 200.0F, 3.0F, 0.4F);
                sun(b, 16761466, 200.0F, 3.0F, 3.8F);
                horizonGlow(b, 16742972, 0.55F, 12.0F);
                clouds(b, 16752250, 5909082, 0.55F, 0.6F);
                break;
            case "SAKURA":
                sun(b, 16765158, 60.0F, 4.0F, 3.2F);
                rays(b, 16762592, 60.0F, 4.0F, 0.35F);
                horizonGlow(b, 16751819, 0.5F, 14.0F);
                nebula(b, 16757721, 13144319, 0.25F, 13);
                break;
            case "STORM":
            case "RAIN":
                horizonGlow(b, 4876954, 0.35F, 10.0F);
                break;
            case "WINTER":
                sun(b, 16771272, 150.0F, 14.0F, 2.4F);
                rays(b, 16773344, 150.0F, 14.0F, 0.1F);
                break;
            case "CLOUDS":
                sun(b, 16774352, 120.0F, 40.0F, 3.0F);
                horizonGlow(b, 16777215, 0.3F, 16.0F);
        }

        if (!day) {
            stars(b, rnd, s.stars() * (!k.equals("SUNSET") && !k.equals("SAKURA") ? 1.25F : 0.6F));
        }

        if (k.equals("ECLIPSE")) {
            eclipse(b, a1, a2);
        }

        if (s.moon() != 0) {
            moon(b, s.moon(), 200.0F, 30.0F, k.equals("BLOOD_MOON") ? 4.2F : 3.0F);
        }

        fadeBelowHorizon(b);
        return b.toEmissive();
    }

    private static void rays(SkyPainter.Buf b, int col, float azD, float elD, float strength) {
        double a = Math.toRadians(azD);
        double e = Math.toRadians(elD);
        float cx = (float) (-Math.sin(a) * Math.cos(e));
        float cy = (float) Math.sin(e);
        float cz = (float) (Math.cos(a) * Math.cos(e));
        float tx = (float) (-Math.cos(a));
        float tz = (float) (-Math.sin(a));
        float ux = (float) (Math.sin(a) * Math.sin(e));
        float uy = (float) Math.cos(e);
        float uz = (float) (-Math.cos(a) * Math.sin(e));

        for (int y = 0; y < b.h; y++) {
            if (!(b.el[y] < -2.0F)) {
                for (int x = 0; x < b.w; x++) {
                    float dx = b.dx(x, y);
                    float dy = b.dy(y);
                    float dz = b.dz(x, y);
                    float dot = dx * cx + dy * cy + dz * cz;
                    if (!(dot < 0.2F)) {
                        float th = (float) Math.toDegrees(Math.acos(Math.min(1.0F, dot)));
                        float phi =
                                (float) Math.atan2(dx * ux + dy * uy + dz * uz, dx * tx + dz * tz);
                        float r =
                                SkyPainter.Noise.noise(
                                        (float) Math.cos(phi) * 9.0F,
                                        (float) Math.sin(phi) * 9.0F,
                                        3.3F);
                        float ray = smooth(0.48F, 0.78F, r);
                        float fall = (float) Math.exp(-th / 34.0F) * smooth(1.5F, 6.0F, th);
                        if (!(ray * fall < 0.002F)) {
                            b.add(y * b.w + x, col, ray * fall * strength);
                        }
                    }
                }
            }
        }
    }

    public static int[] fx(SkyPainter.Spec s, int w, int h) {
        SkyPainter.Buf b = new SkyPainter.Buf(w, h);
        String k = s.kind();
        boolean any = true;
        switch (k) {
            case "PILLARS":
                pillars(b, s.a1(), s.a2());
                break;
            case "LAGOON":
                caustics(b, s.a1(), s.a2());
                break;
            case "NEBULA":
                wisps(b, s.a1(), s.a2());
                break;
            case "CLOUDS":
                clouds(b, 16777215, 12175842, 0.95F, 0.46F);
                break;
            case "WINTER":
                clouds(b, 16777215, 13161702, 0.85F, 0.5F);
                break;
            case "SAKURA":
                clouds(b, 16769776, 13144248, 0.55F, 0.55F);
                break;
            case "SUNSET":
                clouds(b, 16756874, 8010346, 0.6F, 0.56F);
                break;
            case "STORM":
                clouds(b, 5923960, 1711656, 0.95F, 0.38F);
                break;
            case "RAIN":
                clouds(b, 4871270, 1843760, 0.85F, 0.42F);
                break;
            default:
                any = s.aurora() && !k.equals("GALAXY");
        }

        if (s.aurora() && !k.equals("GALAXY")) {
            aurora(b, s.a1(), s.a2(), k.hashCode());
        }

        if (!any) {
            return null;
        }

        fadeBelowHorizon(b);
        return b.toEmissive();
    }

    private static void stars(SkyPainter.Buf b, Random rnd, float density) {
        if (!(density <= 0.0F)) {
            int n = Math.round(density * 1700.0F);

            for (int i = 0; i < n; i++) {
                float sinEl = -0.05F + rnd.nextFloat() * 1.05F;
                float elD = (float) Math.toDegrees(Math.asin(Math.min(1.0F, sinEl)));
                float azD = rnd.nextFloat() * 360.0F;
                float mag = (float) Math.pow(rnd.nextFloat(), 3.2);
                int col =
                        rnd.nextFloat() < 0.15F
                                ? 16770760
                                : (rnd.nextFloat() < 0.3F ? 13162751 : 16777215);
                float core = Math.max(252.0F / b.w, 0.045F + 0.06F * mag);
                float bright = 0.22F + 0.6F * mag;
                star(b, azD, elD, core, col, bright);
                if (mag > 0.82F) {
                    star(b, azD, elD, core * 5.5F, col, 0.12F * mag);
                }
            }
        }
    }

    private static void star(
            SkyPainter.Buf b, float azD, float elD, float sigma, int col, float bright) {
        double ca = Math.cos(Math.toRadians(azD));
        double sa = Math.sin(Math.toRadians(azD));
        double ce = Math.cos(Math.toRadians(elD));
        double se = Math.sin(Math.toRadians(elD));
        float sx = (float) (-sa * ce);
        float sy = (float) se;
        float sz = (float) (ca * ce);
        float pxPerDegX = b.w / 360.0F;
        float pxPerDegY = b.h / 98.0F;
        float rad = sigma * 3.0F;
        int cy = Math.round((90.0F - elD) * pxPerDegY);
        int ry = (int) Math.ceil(rad * pxPerDegY) + 1;
        float cosEl = (float) Math.max(0.05, ce);
        int cx = Math.round(azD * pxPerDegX);
        int rx = (int) Math.ceil(rad * pxPerDegX / cosEl) + 1;
        if (rx > b.w / 2) {
            rx = b.w / 2;
        }

        double sig2 = Math.toRadians(sigma);
        sig2 *= sig2;

        for (int y = cy - ry; y <= cy + ry; y++) {
            if (y >= 0 && y < b.h) {
                for (int x = cx - rx; x <= cx + rx; x++) {
                    int xx = Math.floorMod(x, b.w);
                    float d = sx * b.dx(xx, y) + sy * b.dy(y) + sz * b.dz(xx, y);
                    double ang2 = 2.0F * (1.0F - d);
                    float f = (float) Math.exp(-ang2 / (2.0 * sig2));
                    if (f > 0.003F) {
                        b.add(y * b.w + xx, col, f * bright);
                    }
                }
            }
        }
    }

    private static void milkyWay(SkyPainter.Buf b, int c1, int c2, float strength) {
        float nx = 0.32F;
        float ny = 0.58F;
        float nz = 0.75F;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= l;
        ny /= l;
        nz /= l;
        SkyPainter.Low f1 =
                new SkyPainter.Low(
                        b,
                        3,
                        (xx, yx, z) ->
                                SkyPainter.Noise.fbm(xx * 3.2F + 11.0F, yx * 3.2F, z * 3.2F, 5));
        SkyPainter.Low f2 =
                new SkyPainter.Low(
                        b,
                        2,
                        (xx, yx, z) ->
                                SkyPainter.Noise.fbm(xx * 7.0F, yx * 7.0F + 5.0F, z * 7.0F, 4));
        float fnx = nx;
        float fny = ny;
        float fnz = nz;

        for (int y = 0; y < b.h; y++) {
            for (int x = 0; x < b.w; x++) {
                float dx = b.dx(x, y);
                float dy = b.dy(y);
                float dz = b.dz(x, y);
                float d = dx * fnx + dy * fny + dz * fnz;
                float band = (float) Math.exp(-(d * d) / 0.045F);
                if (!(band < 0.01F)) {
                    float n1 = f1.get(x, y);
                    float n2 = f2.get(x, y);
                    float glow = band * (0.25F + 0.75F * n1);
                    float dust = smooth(0.52F, 0.72F, n2) * band;
                    float core = (float) Math.exp(-(d * d) / 0.008F) * smooth(0.45F, 0.8F, n1);
                    int col = lerp(c1, c2, smooth(0.3F, 0.85F, n1));
                    int i = y * b.w + x;
                    b.add(i, col, glow * 0.42F * strength * (1.0F - 0.75F * dust));
                    b.add(i, 16774374, core * 0.25F * strength * (1.0F - dust));
                    float h = SkyPainter.Noise.hash(x * 7919 + y * 104729);
                    if (h > 0.985F - band * 0.03F) {
                        b.add(i, 16777215, 0.5F * band * strength);
                    }
                }
            }
        }
    }

    private static void nebula(SkyPainter.Buf b, int c1, int c2, float strength, int seed) {
        float o = seed * 3.7F;
        SkyPainter.Low f1 =
                new SkyPainter.Low(
                        b,
                        3,
                        (dx, dy, dz) -> {
                            float wx =
                                    SkyPainter.Noise.fbm(dx * 1.4F + o, dy * 1.4F, dz * 1.4F, 3)
                                            - 0.5F;
                            float wy =
                                    SkyPainter.Noise.fbm(dx * 1.4F, dy * 1.4F + o, dz * 1.4F, 3)
                                            - 0.5F;
                            return SkyPainter.Noise.fbm(
                                    dx * 1.9F + wx * 1.3F + o, dy * 1.9F + wy * 1.3F, dz * 1.9F, 6);
                        });
        SkyPainter.Low f2 =
                new SkyPainter.Low(
                        b,
                        3,
                        (dx, dy, dz) ->
                                SkyPainter.Noise.fbm(
                                        dx * 4.2F, dy * 4.2F + o, dz * 4.2F + 3.0F, 4));

        for (int y = 0; y < b.h; y++) {
            if (!(b.el[y] < -4.0F)) {
                for (int x = 0; x < b.w; x++) {
                    float n1 = f1.get(x, y);
                    float dens = smooth(0.46F, 0.82F, n1);
                    if (!(dens <= 0.002F)) {
                        float n2 = f2.get(x, y);
                        int col = lerp(c1, c2, smooth(0.25F, 0.8F, n2));
                        int i = y * b.w + x;
                        b.over(i, col, dens * 0.62F * strength);
                        float core = smooth(0.72F, 0.95F, n1);
                        b.add(i, lerp(col, 16777215, 0.55F), core * 0.35F * strength);
                    }
                }
            }
        }
    }

    private static void wisps(SkyPainter.Buf b, int c1, int c2) {
        SkyPainter.Low f =
                new SkyPainter.Low(
                        b,
                        2,
                        (dx, dy, dz) ->
                                SkyPainter.Noise.fbm(dx * 3.4F + 40.0F, dy * 3.4F, dz * 3.4F, 5));

        for (int y = 0; y < b.h; y++) {
            if (!(b.el[y] < -4.0F)) {
                for (int x = 0; x < b.w; x++) {
                    float n = f.get(x, y);
                    float k = smooth(0.55F, 0.85F, n);
                    if (!(k <= 0.002F)) {
                        b.over(y * b.w + x, lerp(c2, 16777215, 0.25F), k * 0.38F);
                    }
                }
            }
        }
    }

    private static void aurora(SkyPainter.Buf b, int c1, int c2, int seed) {
        float s = (seed & 1023) * 0.37F;
        float[] base = new float[b.w];
        float[] height = new float[b.w];
        float[] rays = new float[b.w];
        float[] mask = new float[b.w];

        for (int x = 0; x < b.w; x++) {
            float ca = b.cosA[x];
            float sa = b.sinA[x];
            base[x] =
                    8.0F
                            + 10.0F * SkyPainter.Noise.fbm(ca * 1.6F + s, sa * 1.6F, 0.3F, 3)
                            + 3.5F * (float) Math.sin(x * 6.2832 / b.w * 7.0 + s)
                            + 1.5F * (float) Math.sin(x * 6.2832 / b.w * 19.0 + s * 2.0F);
            height[x] = 14.0F + 26.0F * SkyPainter.Noise.fbm(ca * 2.3F, sa * 2.3F + s, 1.7F, 3);
            float r1 = SkyPainter.Noise.noise(ca * 60.0F + s, sa * 60.0F, 0.5F);
            float r2 = SkyPainter.Noise.noise(ca * 160.0F, sa * 160.0F + s, 2.5F);
            rays[x] = (float) Math.pow(0.55F * r1 + 0.45F * r2, 2.2) * 1.8F;
            mask[x] =
                    smooth(
                            0.3F,
                            0.72F,
                            SkyPainter.Noise.fbm(ca * 1.7F + s * 2.0F, sa * 1.7F, 4.1F, 4));
        }

        for (int y = 0; y < b.h; y++) {
            float el = b.el[y];
            if (!(el < -2.0F) && !(el > 75.0F)) {
                for (int x = 0; x < b.w; x++) {
                    if (!(mask[x] <= 0.01F)) {
                        float rel = el - base[x];
                        float I;
                        if (rel < 0.0F) {
                            I = (float) Math.exp(rel / 1.4F);
                        } else {
                            I =
                                    (float) Math.exp(-rel / height[x])
                                            * (0.35F + 0.65F * Math.min(1.0F, rays[x]));
                        }

                        I *= mask[x];
                        if (!(I <= 0.004F)) {
                            float t = Math.max(0.0F, Math.min(1.0F, rel / (height[x] * 1.4F)));
                            int col = lerp(c1, c2, t);
                            int i = y * b.w + x;
                            b.over(i, col, Math.min(0.85F, I * 0.75F));
                            if (rel > -1.0F && rel < 3.0F) {
                                b.add(i, lerp(c1, 16777215, 0.5F), I * 0.18F);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void pillars(SkyPainter.Buf b, int c1, int c2) {
        Random r = new Random(77L);
        int n = 46;
        float[] az = new float[n];
        float[] wd = new float[n];
        float[] top = new float[n];
        float[] in = new float[n];
        int[] col = new int[n];

        for (int i = 0; i < n; i++) {
            az[i] = r.nextFloat() * 360.0F;
            wd[i] = 0.18F + r.nextFloat() * 0.7F;
            top[i] = 25.0F + r.nextFloat() * 55.0F;
            in[i] = 0.35F + r.nextFloat() * 0.65F;
            col[i] = lerp(c1, c2, r.nextFloat());
        }

        float pxPerDeg = b.w / 360.0F;

        for (int i = 0; i < n; i++) {
            for (int y = 0; y < b.h; y++) {
                float el = b.el[y];
                if (!(el < -3.0F) && !(el > top[i])) {
                    float v = smooth(-3.0F, 2.0F, el) * (float) Math.pow(1.0F - el / top[i], 1.6);
                    float cosEl = Math.max(0.1F, b.cosE[y]);
                    float w = wd[i] / cosEl;
                    int x0 = (int) ((az[i] - w * 6.0F) * pxPerDeg);
                    int x1 = (int) ((az[i] + w * 6.0F) * pxPerDeg);

                    for (int x = x0; x <= x1; x++) {
                        int xx = Math.floorMod(x, b.w);
                        float dAz = (x / pxPerDeg - az[i]) / w;
                        float core = (float) Math.exp(-dAz * dAz);
                        float glow = (float) Math.exp(-dAz * dAz / 16.0F);
                        int idx = y * b.w + xx;
                        b.add(
                                idx,
                                lerp(col[i], 16777215, 0.55F * core),
                                (core * 0.55F + glow * 0.12F) * v * in[i]);
                    }
                }
            }
        }
    }

    private static void caustics(SkyPainter.Buf b, int c1, int c2) {
        SkyPainter.Low wxF =
                new SkyPainter.Low(
                        b,
                        2,
                        (dxx, dyx, dzx) -> {
                            float kx = 3.2F / (Math.max(-0.2F, dyx) + 0.3F);
                            return (SkyPainter.Noise.fbm(
                                                    dxx * kx * 0.35F, dzx * kx * 0.35F, 1.3F, 3)
                                            - 0.5F)
                                    * 1.6F;
                        });
        SkyPainter.Low wzF =
                new SkyPainter.Low(
                        b,
                        2,
                        (dxx, dyx, dzx) -> {
                            float kx = 3.2F / (Math.max(-0.2F, dyx) + 0.3F);
                            return (SkyPainter.Noise.fbm(
                                                    dxx * kx * 0.35F + 9.0F,
                                                    dzx * kx * 0.35F,
                                                    4.7F,
                                                    3)
                                            - 0.5F)
                                    * 1.6F;
                        });

        for (int y = 0; y < b.h; y++) {
            float el = b.el[y];
            if (!(el < 1.0F)) {
                float fade = smooth(1.0F, 14.0F, el);

                for (int x = 0; x < b.w; x++) {
                    float dx = b.dx(x, y);
                    float dy = b.dy(y);
                    float dz = b.dz(x, y);
                    float k = 3.2F / (dy + 0.3F);
                    float u = dx * k + wxF.get(x, y);
                    float v = dz * k + wzF.get(x, y);
                    float e1 = cellEdge(u * 1.15F, v * 1.15F);
                    float e2 = cellEdge(u * 2.3F + 7.1F, v * 2.3F + 3.3F);
                    float line =
                            (float) Math.pow(1.0F - smooth(0.0F, 0.16F, e1), 2.2)
                                    + 0.45F * (float) Math.pow(1.0F - smooth(0.0F, 0.12F, e2), 2.5);
                    float I = Math.min(1.2F, line) * fade;
                    int i = y * b.w + x;
                    b.add(i, c1, 0.1F * fade);
                    if (I > 0.004F) {
                        b.add(i, lerp(c1, c2, Math.min(1.0F, I)), I * 0.55F);
                    }
                }
            }
        }
    }

    private static float cellEdge(float u, float v) {
        int xi = (int) Math.floor(u);
        int yi = (int) Math.floor(v);
        float f1 = 9.0F;
        float f2 = 9.0F;

        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                int cx = xi + i;
                int cy = yi + j;
                float px = cx + 0.15F + 0.7F * SkyPainter.Noise.hash(cx * 374761 + cy * 668265);
                float py = cy + 0.15F + 0.7F * SkyPainter.Noise.hash(cx * 912367 + cy * 177823 + 5);
                float d = (float) Math.sqrt((px - u) * (px - u) + (py - v) * (py - v));
                if (d < f1) {
                    f2 = f1;
                    f1 = d;
                } else if (d < f2) {
                    f2 = d;
                }
            }
        }

        return f2 - f1;
    }

    private static void clouds(SkyPainter.Buf b, int light, int shade, float cover, float thr) {
        SkyPainter.Low fn =
                new SkyPainter.Low(
                        b,
                        2,
                        (dx, dyx, dz) -> {
                            if (dyx < -0.03F) {
                                return 0.0F;
                            }

                            float k = 1.8F / (dyx + 0.1F);
                            return SkyPainter.Noise.fbm(dx * k * 0.42F, dz * k * 0.42F, 0.7F, 6);
                        });
        SkyPainter.Low fl =
                new SkyPainter.Low(
                        b,
                        3,
                        (dx, dyx, dz) -> {
                            if (dyx < -0.03F) {
                                return 0.0F;
                            }

                            float k = 1.8F / (dyx + 0.1F);
                            return SkyPainter.Noise.fbm(
                                    dx * k * 0.42F + 0.18F, dz * k * 0.42F + 0.18F, 0.7F, 4);
                        });

        for (int y = 0; y < b.h; y++) {
            float dy = b.dy(y);
            if (!(dy < -0.02F)) {
                float fade = smooth(-0.02F, 0.12F, dy);

                for (int x = 0; x < b.w; x++) {
                    float n = fn.get(x, y);
                    float d = smooth(thr, thr + 0.28F, n);
                    if (!(d <= 0.002F)) {
                        float lit = fl.get(x, y);
                        float sh = smooth(-0.08F, 0.12F, n - lit);
                        int col = lerp(light, shade, 0.25F + 0.6F * sh);
                        b.over(y * b.w + x, col, d * cover * fade);
                    }
                }
            }
        }
    }

    private static void eclipse(SkyPainter.Buf b, int c1, int c2) {
        float azC = 165.0F;
        float elC = 24.0F;
        float R = 7.5F;
        double a = Math.toRadians(azC);
        double e = Math.toRadians(elC);
        float cx = (float) (-Math.sin(a) * Math.cos(e));
        float cy = (float) Math.sin(e);
        float cz = (float) (Math.cos(a) * Math.cos(e));
        float tx = (float) (-Math.cos(a));
        float tz = (float) (-Math.sin(a));
        float ux = (float) (Math.sin(a) * Math.sin(e));
        float uy = (float) Math.cos(e);
        float uz = (float) (-Math.cos(a) * Math.sin(e));
        float tilt = (float) Math.toRadians(-11.0);
        float ct = (float) Math.cos(tilt);
        float st = (float) Math.sin(tilt);

        for (int y = 0; y < b.h; y++) {
            if (!(Math.abs(b.el[y] - elC) > R * 6.0F)) {
                for (int x = 0; x < b.w; x++) {
                    float dx = b.dx(x, y);
                    float dy = b.dy(y);
                    float dz = b.dz(x, y);
                    float dot = dx * cx + dy * cy + dz * cz;
                    if (!(dot < 0.75F)) {
                        float th = (float) Math.toDegrees(Math.acos(Math.min(1.0F, dot)));
                        float lx =
                                (float)
                                        Math.toDegrees(
                                                Math.asin(
                                                        Math.max(
                                                                -1.0F,
                                                                Math.min(
                                                                        1.0F, dx * tx + dz * tz))));
                        float lyy =
                                (float)
                                        Math.toDegrees(
                                                Math.asin(
                                                        Math.max(
                                                                -1.0F,
                                                                Math.min(
                                                                        1.0F,
                                                                        dx * ux + dy * uy
                                                                                + dz * uz))));
                        float rx = lx * ct - lyy * st;
                        float ry = lx * st + lyy * ct;
                        float squash = 0.27F;
                        float re = (float) Math.sqrt(rx * rx + ry / squash * (ry / squash));
                        float ang = (float) Math.atan2(ry / squash, rx);
                        float diskBand =
                                smooth(R * 1.25F, R * 1.6F, re)
                                        * (1.0F - smooth(R * 2.4F, R * 3.8F, re));
                        float streak =
                                0.6F + 0.4F * SkyPainter.Noise.noise(re * 3.1F, ang * 3.0F, 1.1F);
                        float doppler = 0.4F + 0.6F * (0.5F + 0.5F * (float) Math.cos(ang - 0.4F));
                        float disk = diskBand * streak * doppler;
                        int i = y * b.w + x;
                        float glow = th > R ? (float) Math.exp(-(th - R) / 3.2F) : 1.0F;
                        b.add(i, c1, glow * 0.22F);
                        boolean front = ry < 0.0F;
                        int dcol = lerp(c2, lerp(c1, 16777215, 0.6F), doppler);
                        if (!front) {
                            b.add(i, dcol, disk * 0.85F);
                        }

                        float arc =
                                (float) Math.exp(-Math.pow((th - R * 1.32F) / 0.32F, 2.0))
                                        * (0.55F + 0.45F * Math.max(0.0F, lyy / (th + 0.001F)));
                        b.add(i, lerp(c1, 16777215, 0.5F), arc * 0.55F);
                        float core = 1.0F - smooth(R - 0.08F, R + 0.04F, th);
                        if (core > 0.0F) {
                            b.over(i, 131332, core);
                        }

                        float ring = (float) Math.exp(-Math.pow((th - R * 1.03F) / 0.1F, 2.0));
                        b.add(i, lerp(c1, 16777215, 0.8F), ring * 0.95F);
                        if (front) {
                            b.add(i, dcol, disk * 0.95F);
                        }
                    }
                }
            }
        }
    }

    private static void moon(SkyPainter.Buf b, int col, float azD, float elD, float R) {
        double a = Math.toRadians(azD);
        double e = Math.toRadians(elD);
        float cx = (float) (-Math.sin(a) * Math.cos(e));
        float cy = (float) Math.sin(e);
        float cz = (float) (Math.cos(a) * Math.cos(e));

        for (int y = 0; y < b.h; y++) {
            if (!(Math.abs(b.el[y] - elD) > R * 9.0F)) {
                for (int x = 0; x < b.w; x++) {
                    float dx = b.dx(x, y);
                    float dy = b.dy(y);
                    float dz = b.dz(x, y);
                    float dot = dx * cx + dy * cy + dz * cz;
                    if (!(dot < 0.97F)) {
                        float th = (float) Math.toDegrees(Math.acos(Math.min(1.0F, dot)));
                        int i = y * b.w + x;
                        b.add(i, col, (float) Math.exp(-th / (R * 1.6F)) * 0.22F);
                        float disc = 1.0F - smooth(R - 0.06F, R + 0.06F, th);
                        if (disc > 0.0F) {
                            float cr = SkyPainter.Noise.fbm(dx * 90.0F, dy * 90.0F, dz * 90.0F, 4);
                            int mc = lerp(col, lerp(col, 0, 0.35F), smooth(0.5F, 0.75F, cr));
                            b.over(i, mc, disc);
                        }
                    }
                }
            }
        }
    }

    private static void sun(SkyPainter.Buf b, int col, float azD, float elD, float R) {
        double a = Math.toRadians(azD);
        double e = Math.toRadians(elD);
        float cx = (float) (-Math.sin(a) * Math.cos(e));
        float cy = (float) Math.sin(e);
        float cz = (float) (Math.cos(a) * Math.cos(e));

        for (int y = 0; y < b.h; y++) {
            for (int x = 0; x < b.w; x++) {
                float dot = b.dx(x, y) * cx + b.dy(y) * cy + b.dz(x, y) * cz;
                if (!(dot < 0.5F)) {
                    float th = (float) Math.toDegrees(Math.acos(Math.min(1.0F, dot)));
                    int i = y * b.w + x;
                    b.add(i, col, (float) Math.exp(-th / 18.0F) * 0.35F);
                    b.add(i, 16773848, (1.0F - smooth(R - 0.2F, R + 0.2F, th)) * 0.95F);
                }
            }
        }
    }

    private static void horizonGlow(SkyPainter.Buf b, int col, float strength, float height) {
        for (int y = 0; y < b.h; y++) {
            float el = b.el[y];
            if (!(el < -4.0F) && !(el > height * 2.0F)) {
                float f =
                        smooth(-4.0F, 0.0F, el)
                                * (float) Math.exp(-Math.max(0.0F, el) / (height * 0.45F));

                for (int x = 0; x < b.w; x++) {
                    b.add(y * b.w + x, col, f * strength);
                }
            }
        }
    }

    private static void fadeBelowHorizon(SkyPainter.Buf b) {
        for (int y = 0; y < b.h; y++) {
            float f = smooth(-7.0F, 1.0F, b.el[y]);
            if (!(f >= 0.999F)) {
                for (int x = 0; x < b.w; x++) {
                    int i = y * b.w + x;
                    b.r[i] = b.r[i] * f;
                    b.g[i] = b.g[i] * f;
                    b.b[i] = b.b[i] * f;
                    b.a[i] = b.a[i] * f;
                }
            }
        }
    }

    public static int[] sprites() {
        int S = 64;
        int W = S * 4;
        int[] out = new int[W * S];

        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                float u = (x + 0.5F) / S * 2.0F - 1.0F;
                float v = (y + 0.5F) / S * 2.0F - 1.0F;
                float t = (v + 1.0F) / 2.0F;
                float width =
                        (float)
                                (0.78
                                        * Math.pow(
                                                Math.sin(
                                                        Math.PI
                                                                * Math.min(
                                                                        1.0F, t * 0.92F + 0.04F)),
                                                0.55)
                                        * (1.0 - 0.62 * t * t));
                float edge = width - Math.abs(u);
                float notch = (0.11F - t) * 3.4F - Math.abs(u) * 3.6F;
                float a0 = smooth(0.0F, 0.07F, edge) * (1.0F - smooth(-0.05F, 0.08F, notch));
                float center = 1.0F - Math.min(1.0F, Math.abs(u) / Math.max(0.05F, width));
                float vein = (float) Math.exp(-u * u * 160.0F) * 0.25F * t;
                int c0 = lerp(16095940, 16773366, 0.25F + 0.35F * center + 0.3F * t + vein * 0.5F);
                out[y * W + x] = argb(c0, a0 * 0.97F);
                float r = (float) Math.sqrt(u * u + v * v);
                out[y * W + S + x] = argb(16777215, 1.0F - smooth(0.15F, 1.0F, r));
                float g =
                        (float) Math.exp(-r * r * 9.0F) * 0.85F
                                + (float) Math.exp(-r * r * 60.0F) * 0.6F;
                out[y * W + 2 * S + x] = argb(16777215, Math.min(1.0F, g));
                float sx = 1.0F - smooth(0.05F, 0.35F, Math.abs(u));
                float sv = smooth(-1.0F, 0.6F, v) * (1.0F - smooth(0.85F, 1.0F, v));
                out[y * W + 3 * S + x] = argb(16777215, sx * sv);
            }
        }

        return out;
    }

    private static int argb(int rgb, float a) {
        return c255(a) << 24 | rgb & 16777215;
    }

    public static int[] smooth(int[] em, int w, int h, boolean soft) {
        int[] a = (int[]) em.clone();
        int[] tmp = new int[w];
        int[] r = new int[w];
        int[] g = new int[w];
        int[] b = new int[w];

        for (int y = 0; y < h; y++) {
            float el = 90.0F - (y + 0.5F) / h * 98.0F;
            double ce = Math.max(0.015, Math.cos(Math.toRadians(el)));
            int rad = (int) Math.min(64.0, Math.floor(0.55 / ce)) + (soft ? 1 : 0);
            if (rad > 0) {
                int off = y * w;

                for (int x = 0; x < w; x++) {
                    int c = a[off + x];
                    r[x] = c >> 16 & 0xFF;
                    g[x] = c >> 8 & 0xFF;
                    b[x] = c & 0xFF;
                }

                int n = rad * 2 + 1;
                int sr = 0;
                int sg = 0;
                int sb = 0;

                for (int k = -rad; k <= rad; k++) {
                    int xx = Math.floorMod(k, w);
                    sr += r[xx];
                    sg += g[xx];
                    sb += b[xx];
                }

                for (int x = 0; x < w; x++) {
                    tmp[x] = 0xFF000000 | sr / n << 16 | sg / n << 8 | sb / n;
                    int out = Math.floorMod(x - rad, w);
                    int in = Math.floorMod(x + rad + 1, w);
                    sr += r[in] - r[out];
                    sg += g[in] - g[out];
                    sb += b[in] - b[out];
                }

                System.arraycopy(tmp, 0, a, off, w);
            }
        }

        if (soft) {
            int[] o = (int[]) a.clone();

            for (int yx = 1; yx < h - 1; yx++) {
                for (int x = 0; x < w; x++) {
                    int c0 = o[(yx - 1) * w + x];
                    int c1 = o[yx * w + x];
                    int c2 = o[(yx + 1) * w + x];
                    int rr = (c0 >> 16 & 0xFF) + 2 * (c1 >> 16 & 0xFF) + (c2 >> 16 & 0xFF) >> 2;
                    int gg = (c0 >> 8 & 0xFF) + 2 * (c1 >> 8 & 0xFF) + (c2 >> 8 & 0xFF) >> 2;
                    int bb = (c0 & 0xFF) + 2 * (c1 & 0xFF) + (c2 & 0xFF) >> 2;
                    a[yx * w + x] = 0xFF000000 | rr << 16 | gg << 8 | bb;
                }
            }
        }

        return a;
    }

    private static int rowColor(SkyPainter.Spec s, float el) {
        float t = Math.max(0.0F, Math.min(1.0F, (el + 2.0F) / 82.0F));
        t = (float) Math.pow(t, 0.65);
        int mid = lerp(lerp(s.horizon(), s.top(), 0.5F), s.a1(), 0.1F);
        return t < 0.45F
                ? lerp(s.horizon(), mid, t / 0.45F)
                : lerp(mid, s.top(), smooth(0.0F, 1.0F, (t - 0.45F) / 0.55F));
    }

    public static int[] opaqueBase(int[] em, SkyPainter.Spec s, int w, int h) {
        int[] out = new int[em.length];

        for (int y = 0; y < h; y++) {
            float el = 90.0F - (y + 0.5F) / h * 98.0F;
            int g = rowColor(s, el);
            float al = smooth(-3.0F, 3.0F, el);
            int A = Math.round(al * 255.0F);
            if (A != 0) {
                int gr = g >> 16 & 0xFF;
                int gg = g >> 8 & 0xFF;
                int gb = g & 0xFF;

                for (int x = 0; x < w; x++) {
                    int c = em[y * w + x];
                    int r = Math.min(255, gr + (c >> 16 & 0xFF));
                    int gn = Math.min(255, gg + (c >> 8 & 0xFF));
                    int b = Math.min(255, gb + (c & 0xFF));
                    out[y * w + x] = A << 24 | r << 16 | gn << 8 | b;
                }
            }
        }

        return out;
    }

    public static int[] toBlendOver(int[] em, SkyPainter.Spec s, int w, int h) {
        int[] out = new int[em.length];
        float MIN = 0.12F;

        for (int y = 0; y < h; y++) {
            float el = 90.0F - (y + 0.5F) / h * 98.0F;
            int g = rowColor(s, el);
            float gr = (g >> 16 & 0xFF) / 255.0F;
            float gg = (g >> 8 & 0xFF) / 255.0F;
            float gb = (g & 0xFF) / 255.0F;

            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int c = em[i];
                float r = (c >> 16 & 0xFF) / 255.0F;
                float gn = (c >> 8 & 0xFF) / 255.0F;
                float b = (c & 0xFF) / 255.0F;
                float m = Math.max(r, Math.max(gn, b));
                if (!(m < 0.005882353F)) {
                    float a = Math.max(0.12F, m);
                    a =
                            Math.max(
                                    a,
                                    Math.max(
                                            r / Math.max(0.02F, 1.0F - gr),
                                            Math.max(
                                                    gn / Math.max(0.02F, 1.0F - gg),
                                                    b / Math.max(0.02F, 1.0F - gb))));
                    a = Math.min(1.0F, a);
                    int rr = c255(r / a + gr);
                    int g2 = c255(gn / a + gg);
                    int bb = c255(b / a + gb);
                    out[i] = c255(a) << 24 | rr << 16 | g2 << 8 | bb;
                }
            }
        }

        return out;
    }

    public static int[] view(
            SkyPainter.Spec s,
            int[] base,
            int[] fx,
            int pw,
            int ph,
            int w,
            int h,
            float yaw,
            float pitch,
            float fovDeg,
            int rad) {
        int[] out = new int[w * h];
        double f = h / 2.0 / Math.tan(Math.toRadians(fovDeg / 2.0F));
        double ya = Math.toRadians(yaw);
        double pi = Math.toRadians(pitch);
        double fx0 = -Math.sin(ya) * Math.cos(pi);
        double fy0 = -Math.sin(pi);
        double fz0 = Math.cos(ya) * Math.cos(pi);
        double rx = -Math.cos(ya);
        double ry = 0.0;
        double rz = -Math.sin(ya);
        double ux = ry * fz0 - rz * fy0;
        double uy = rz * fx0 - rx * fz0;
        double uz = rx * fy0 - ry * fx0;
        Random rr = new Random(s.kind().hashCode());
        double ph0 = rr.nextDouble() * 10.0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double sx = x + 0.5 - w / 2.0;
                double sy = -(y + 0.5 - h / 2.0);
                double dx = fx0 * f + rx * sx + ux * sy;
                double dy = fy0 * f + ry * sx + uy * sy;
                double dz = fz0 * f + rz * sx + uz * sy;
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= l;
                dy /= l;
                dz /= l;
                double el = Math.toDegrees(Math.asin(dy));
                double az = Math.toDegrees(Math.atan2(-dx, dz));
                if (az < 0.0) {
                    az += 360.0;
                }

                int sky = rowColor(s, (float) el);
                float r = (sky >> 16 & 0xFF) / 255.0F;
                float g = (sky >> 8 & 0xFF) / 255.0F;
                float bl = (sky & 0xFF) / 255.0F;
                float u = (float) (az / 360.0 * pw);
                float v = (float) ((90.0 - el) / 98.0 * ph);

                for (int[] layer : new int[][] {base, fx}) {
                    if (layer != null) {
                        int c = sample(layer, pw, ph, u, v);
                        r = Math.min(1.0F, r + (c >> 16 & 0xFF) / 255.0F);
                        g = Math.min(1.0F, g + (c >> 8 & 0xFF) / 255.0F);
                        bl = Math.min(1.0F, bl + (c & 0xFF) / 255.0F);
                    }
                }

                double uu = az / 360.0 * 6.2832;
                double hill =
                        1.5 + 2.2 * Math.sin(uu * 3.0 + ph0) + 1.2 * Math.sin(uu * 7.0 + ph0 * 2.0);
                double tree =
                        Math.max(0.0, Math.sin(uu * 95.0 + ph0))
                                * Math.max(0.0, Math.sin(uu * 37.0 + ph0 * 3.0))
                                * 4.5;
                if (el < hill + tree) {
                    r = 0.024F;
                    g = 0.016F;
                    bl = 0.04F;
                }

                float dk = Math.max(0.0F, (y - h * 0.6F) / (h * 0.4F));
                dk = dk * dk * 0.55F;
                r *= 1.0F - dk;
                g *= 1.0F - dk;
                bl *= 1.0F - dk;
                float ca = cornerAlpha(x, y, w, h, rad);
                out[y * w + x] = c255(ca) << 24 | c255(r) << 16 | c255(g) << 8 | c255(bl);
            }
        }

        return out;
    }

    private static int sample(int[] img, int w, int h, float u, float v) {
        u -= 0.5F;
        v -= 0.5F;
        int x0 = (int) Math.floor(u);
        int y0 = (int) Math.floor(v);
        float fx = u - x0;
        float fy = v - y0;
        int c00 = px(img, w, h, x0, y0);
        int c10 = px(img, w, h, x0 + 1, y0);
        int c01 = px(img, w, h, x0, y0 + 1);
        int c11 = px(img, w, h, x0 + 1, y0 + 1);
        int out = 0;

        for (int sh = 0; sh <= 24; sh += 8) {
            float a = (c00 >>> sh & 0xFF) * (1.0F - fx) + (c10 >>> sh & 0xFF) * fx;
            float b2 = (c01 >>> sh & 0xFF) * (1.0F - fx) + (c11 >>> sh & 0xFF) * fx;
            out |= Math.round(a * (1.0F - fy) + b2 * fy) << sh;
        }

        return out;
    }

    private static int px(int[] img, int w, int h, int x, int y) {
        x = Math.floorMod(x, w);
        y = Math.max(0, Math.min(h - 1, y));
        return img[y * w + x];
    }

    private static float cornerAlpha(int x, int y, int w, int h, int rad) {
        if (rad <= 0) {
            return 1.0F;
        } else {
            float cx = x < rad ? rad : (x >= w - rad ? w - rad : -1.0F);
            float cy = y < rad ? rad : (y >= h - rad ? h - rad : -1.0F);
            if (!(cx < 0.0F) && !(cy < 0.0F)) {
                float d = (float) Math.hypot(x + 0.5F - cx, y + 0.5F - cy);
                return Math.max(0.0F, Math.min(1.0F, rad - d + 0.5F));
            } else {
                return 1.0F;
            }
        }
    }

    static float smooth(float e0, float e1, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - e0) / (e1 - e0)));
        return t * t * (3.0F - 2.0F * t);
    }

    static int lerp(int c1, int c2, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int r = (int) ((c1 >> 16 & 0xFF) + ((c2 >> 16 & 0xFF) - (c1 >> 16 & 0xFF)) * t);
        int g = (int) ((c1 >> 8 & 0xFF) + ((c2 >> 8 & 0xFF) - (c1 >> 8 & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return r << 16 | g << 8 | b;
    }

    private static final class Buf {
        final int w;
        final int h;
        final float[] r;
        final float[] g;
        final float[] b;
        final float[] a;
        final float[] el;
        final float[] sinE;
        final float[] cosE;
        final float[] sinA;
        final float[] cosA;

        Buf(int w, int h) {
            this.w = w;
            this.h = h;
            this.r = new float[w * h];
            this.g = new float[w * h];
            this.b = new float[w * h];
            this.a = new float[w * h];
            this.el = new float[h];
            this.sinE = new float[h];
            this.cosE = new float[h];

            for (int y = 0; y < h; y++) {
                this.el[y] = 90.0F - (y + 0.5F) / h * 98.0F;
                double e = Math.toRadians(this.el[y]);
                this.sinE[y] = (float) Math.sin(e);
                this.cosE[y] = (float) Math.cos(e);
            }

            this.sinA = new float[w];
            this.cosA = new float[w];

            for (int x = 0; x < w; x++) {
                double az = Math.toRadians((x + 0.5) / w * 360.0);
                this.sinA[x] = (float) Math.sin(az);
                this.cosA[x] = (float) Math.cos(az);
            }
        }

        float dx(int x, int y) {
            return -this.sinA[x] * this.cosE[y];
        }

        float dy(int y) {
            return this.sinE[y];
        }

        float dz(int x, int y) {
            return this.cosA[x] * this.cosE[y];
        }

        void over(int i, int rgb, float al) {
            if (!(al <= 5.0E-4F)) {
                if (al > 1.0F) {
                    al = 1.0F;
                }

                float k = 1.0F - al;
                this.r[i] = (rgb >> 16 & 0xFF) / 255.0F * al + this.r[i] * k;
                this.g[i] = (rgb >> 8 & 0xFF) / 255.0F * al + this.g[i] * k;
                this.b[i] = (rgb & 0xFF) / 255.0F * al + this.b[i] * k;
                this.a[i] = al + this.a[i] * k;
            }
        }

        void add(int i, int rgb, float al) {
            if (!(al <= 5.0E-4F)) {
                this.r[i] = Math.min(1.0F, this.r[i] + (rgb >> 16 & 0xFF) / 255.0F * al);
                this.g[i] = Math.min(1.0F, this.g[i] + (rgb >> 8 & 0xFF) / 255.0F * al);
                this.b[i] = Math.min(1.0F, this.b[i] + (rgb & 0xFF) / 255.0F * al);
                this.a[i] = Math.min(1.0F, this.a[i] + al * 0.85F);
            }
        }

        int[] toEmissive() {
            int[] out = new int[this.w * this.h];

            for (int i = 0; i < out.length; i++) {
                out[i] =
                        0xFF000000
                                | SkyPainter.c255(this.r[i]) << 16
                                | SkyPainter.c255(this.g[i]) << 8
                                | SkyPainter.c255(this.b[i]);
            }

            return out;
        }

        int[] toArgb() {
            int[] out = new int[this.w * this.h];

            for (int i = 0; i < out.length; i++) {
                float al = this.a[i];
                if (al <= 0.002F) {
                    out[i] = 0;
                } else {
                    int rr = SkyPainter.c255(this.r[i] / al);
                    int gg = SkyPainter.c255(this.g[i] / al);
                    int bb = SkyPainter.c255(this.b[i] / al);
                    out[i] = SkyPainter.c255(al) << 24 | rr << 16 | gg << 8 | bb;
                }
            }

            return out;
        }
    }

    private interface Field {
        float at(float var1, float var2, float var3);
    }

    private static final class Low {
        final int lw;
        final int lh;
        final int div;
        final float[] v;
        final SkyPainter.Buf b;

        Low(SkyPainter.Buf b, int div, SkyPainter.Field f) {
            this.b = b;
            this.div = div;
            this.lw = Math.max(4, b.w / div);
            this.lh = Math.max(2, b.h / div + 1);
            this.v = new float[this.lw * this.lh];

            for (int y = 0; y < this.lh; y++) {
                float el = 90.0F - (y * div + div * 0.5F) / b.h * 98.0F;
                double e = Math.toRadians(el);
                float ce = (float) Math.cos(e);
                float se = (float) Math.sin(e);

                for (int x = 0; x < this.lw; x++) {
                    double az = Math.toRadians((x + 0.5) / this.lw * 360.0);
                    this.v[y * this.lw + x] =
                            f.at((float) (-Math.sin(az) * ce), se, (float) (Math.cos(az) * ce));
                }
            }
        }

        float get(int x, int y) {
            float fx = (x + 0.5F) / this.div - 0.5F;
            float fy = (y + 0.5F) / this.div - 0.5F;
            int x0 = (int) Math.floor(fx);
            int y0 = (int) Math.floor(fy);
            float tx = fx - x0;
            float ty = fy - y0;
            int xa = Math.floorMod(x0, this.lw);
            int xb = Math.floorMod(x0 + 1, this.lw);
            int ya = Math.max(0, Math.min(this.lh - 1, y0));
            int yb = Math.max(0, Math.min(this.lh - 1, y0 + 1));
            float a =
                    this.v[ya * this.lw + xa]
                            + (this.v[ya * this.lw + xb] - this.v[ya * this.lw + xa]) * tx;
            float c =
                    this.v[yb * this.lw + xa]
                            + (this.v[yb * this.lw + xb] - this.v[yb * this.lw + xa]) * tx;
            return a + (c - a) * ty;
        }
    }

    static final class Noise {
        private Noise() {}

        static float hash(int n) {
            n = n << 13 ^ n;
            n = n * (n * n * 15731 + 789221) + 1376312589;
            return (n & 2147483647) / 2.1474836E9F;
        }

        private static float h3(int x, int y, int z) {
            return hash(x * 73856093 ^ y * 19349663 ^ z * 83492791);
        }

        static float noise(float x, float y, float z) {
            int xi = (int) Math.floor(x);
            int yi = (int) Math.floor(y);
            int zi = (int) Math.floor(z);
            float xf = x - xi;
            float yf = y - yi;
            float zf = z - zi;
            float u = fade(xf);
            float v = fade(yf);
            float w = fade(zf);
            float c000 = h3(xi, yi, zi);
            float c100 = h3(xi + 1, yi, zi);
            float c010 = h3(xi, yi + 1, zi);
            float c110 = h3(xi + 1, yi + 1, zi);
            float c001 = h3(xi, yi, zi + 1);
            float c101 = h3(xi + 1, yi, zi + 1);
            float c011 = h3(xi, yi + 1, zi + 1);
            float c111 = h3(xi + 1, yi + 1, zi + 1);
            float x00 = c000 + (c100 - c000) * u;
            float x10 = c010 + (c110 - c010) * u;
            float x01 = c001 + (c101 - c001) * u;
            float x11 = c011 + (c111 - c011) * u;
            float y0 = x00 + (x10 - x00) * v;
            float y1 = x01 + (x11 - x01) * v;
            return y0 + (y1 - y0) * w;
        }

        static float fbm(float x, float y, float z, int oct) {
            float sum = 0.0F;
            float amp = 0.5F;
            float norm = 0.0F;

            for (int i = 0; i < oct; i++) {
                sum += noise(x, y, z) * amp;
                norm += amp;
                x = x * 2.03F + 17.1F;
                y = y * 2.03F + 3.7F;
                z = z * 2.03F + 9.3F;
                amp *= 0.5F;
            }

            return sum / norm;
        }

        private static float fade(float t) {
            return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
        }
    }

    public record Spec(
            String kind,
            int top,
            int horizon,
            int a1,
            int a2,
            float stars,
            boolean aurora,
            int moon,
            String weather) {}
}
