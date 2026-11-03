package dev.jun.nosclient.sky;

import dev.jun.nosclient.ClientEffects;
import dev.jun.nosclient.config.EffectsConfig;

import java.util.Random;

public final class LiveSky {
    public static final LiveSky MAIN = new LiveSky();
    private SkyPreset P;
    private float tr;
    private float tg;
    private float tb;
    private float hr;
    private float hg;
    private float hb;
    private float ar1;
    private float ag1;
    private float ab1;
    private float ar2;
    private float ag2;
    private float ab2;
    private double T;
    private float pattern = 1.0F;
    private float lively = 0.6F;
    private float breath = 1.0F;
    private float auroraOn = 1.0F;
    private double sunX;
    private double sunY;
    private double sunZ;
    private LiveSky.Body body;
    private float R;
    private float G;
    private float B;
    private static final double[][] PILLAR = new double[16][];

    public static LiveSky.Body body(SkyPreset p) {
        return switch (p) {
            case SYNTHWAVE -> new LiveSky.Body(180.0, 12.0, 12.0, 16765803, 3);
            case GOLDEN_HOUR -> new LiveSky.Body(200.0, 4.0, 3.6, 16761466, 0);
            case COTTON_CANDY -> new LiveSky.Body(120.0, 38.0, 2.4, 16774368, 0);
            case SAKURA -> new LiveSky.Body(60.0, 9.0, 2.6, 16769774, 0);
            case COSMOS -> new LiveSky.Body(60.0, 28.0, 5.0, 12166911, 1);
            case BLACK_HOLE -> new LiveSky.Body(165.0, 24.0, 5.2, 16757850, 2);
            case BLOOD_MOON -> new LiveSky.Body(200.0, 22.0, 4.4, 16728120, 1);
            case FROST -> new LiveSky.Body(210.0, 30.0, 2.6, p.moon, 1);
            default -> p.moon != 0 ? new LiveSky.Body(200.0, 26.0, 2.4, p.moon, 1) : null;
        };
    }

    public static boolean synthGap(double v, double t) {
        if (v > 0.45) {
            return false;
        }

        double s = -v * 6.0 + t * 0.12;
        s -= Math.floor(s);
        return s < 0.06 + 0.46 * Math.max(0.0, Math.min(1.0, (0.45 - v) / 1.45));
    }

    public void prepare(SkyPreset p, int a1, int a2, double t, EffectsConfig c) {
        this.P = p;
        this.tr = r(p.top);
        this.tg = g(p.top);
        this.tb = b(p.top);
        this.hr = r(p.horizon);
        this.hg = g(p.horizon);
        this.hb = b(p.horizon);
        this.ar1 = r(a1);
        this.ag1 = g(a1);
        this.ab1 = b(a1);
        this.ar2 = r(a2);
        this.ag2 = g(a2);
        this.ab2 = b(a2);
        this.T = t * 2.2;
        if (c != null) {
            this.pattern = Math.max(0.2F, Math.min(1.5F, c.skyPattern));
            this.lively = Math.max(0.0F, Math.min(1.0F, c.skyLively));
            this.breath =
                    c.skyBreath <= 0.01F
                            ? 1.0F
                            : 0.85F
                                    + 0.15F
                                            * (float)
                                                    Math.sin(
                                                            t * 0.45 * Math.max(0.2F, c.skyBreath));
            this.auroraOn = c.fxAurora ? 1.0F : 0.0F;
        }

        this.body = body(p);
        if (this.body != null) {
            double a = Math.toRadians(this.body.az());
            double e = Math.toRadians(this.body.el());
            this.sunX = -Math.sin(a) * Math.cos(e);
            this.sunY = Math.sin(e);
            this.sunZ = Math.cos(a) * Math.cos(e);
        }
    }

    public static int synthColor(double v) {
        float k = (float) Math.max(0.0, Math.min(1.0, (v + 1.0) / 2.0));
        int lo = 16727450;
        int mid = 16747100;
        int hi = 16769658;
        return k < 0.5F ? mix(lo, mid, k * 2.0F) : mix(mid, hi, (k - 0.5F) * 2.0F);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }

    private static float r(int c) {
        return (c >> 16 & 0xFF) / 255.0F;
    }

    private static float g(int c) {
        return (c >> 8 & 0xFF) / 255.0F;
    }

    private static float b(int c) {
        return (c & 0xFF) / 255.0F;
    }

    private void add(float r, float g, float b, float k) {
        this.R += r * k;
        this.G += g * k;
        this.B += b * k;
    }

    private void over(float r, float g, float b, float k) {
        k = Math.max(0.0F, Math.min(1.0F, k));
        this.R = this.R + (r - this.R) * k;
        this.G = this.G + (g - this.G) * k;
        this.B = this.B + (b - this.B) * k;
    }

    public int color(double x, double y, double z, float elDeg) {
        SkyPreset p = this.P;
        double az = Math.toDegrees(Math.atan2(-x, z));
        if (az < 0.0) {
            az += 360.0;
        }

        double t = this.T;
        float k = (float) Math.pow(clamp(elDeg / 72.0F), 0.55);
        this.R = this.hr + (this.tr - this.hr) * k;
        this.G = this.hg + (this.tg - this.hg) * k;
        this.B = this.hb + (this.tb - this.hb) * k;
        if (elDeg < 0.0F) {
            float d = clamp(-elDeg / 6.0F) * 0.55F;
            this.R *= 1.0F - d;
            this.G *= 1.0F - d;
            this.B *= 1.0F - d;
        }

        float lowGlow = (float) Math.exp(-Math.max(0.0F, elDeg) / 9.0);
        switch (p) {
            case SYNTHWAVE:
                this.synth(elDeg);
                break;
            case GOLDEN_HOUR:
                this.add(1.0F, 0.55F, 0.28F, 0.5F * lowGlow);
                this.clouds(x, y, z, 0.54F, 0.85F, 16761994, 5909598, 0.02);
                break;
            case COTTON_CANDY:
                this.add(1.0F, 0.8F, 0.92F, 0.14F * lowGlow);
                this.clouds(x, y, z, 0.6F, 0.92F, 16777215, 15907032, 0.025);
                break;
            case SAKURA:
                this.add(1.0F, 0.61F, 0.8F, 0.4F * lowGlow);
                this.clouds(x, y, z, 0.57F, 0.6F, 16771314, 13144248, 0.02);
                break;
            case COSMOS:
                this.galaxyBand(x, y, z);
                this.add(this.ar2, this.ag2, this.ab2, 0.08F * lowGlow);
                break;
            case BLACK_HOLE:
                this.milkyWay(x, y, z, 0.22F);
                this.accretion(az, elDeg);
                break;
            case BLOOD_MOON:
                this.add(1.0F, 0.23F, 0.16F, 0.25F * lowGlow);
                this.clouds(x, y, z, 0.6F, 0.75F, 12070970, 1704968, 0.03);
                break;
            case FROST:
                this.iceBands(az, elDeg);
                this.curtains(az, elDeg, 0.45F);
                this.milkyWay(x, y, z, 0.2F);
                break;
            case AURORA:
            case CUSTOM:
                this.milkyWay(x, y, z, 0.16F);
                this.curtains(az, elDeg, 1.0F);
                this.add(this.ar1, this.ag1, this.ab1, 0.06F * lowGlow);
                break;
            case NEBULA:
                this.swirl(x, y, z);
                this.add(this.ar1, this.ag1, this.ab1, 0.1F * lowGlow);
                break;
            case NEON_CITY:
                this.cityGlow(az, elDeg);
                this.clouds(x, y, z, 0.62F, 0.55F, 6958202, 789022, 0.03);
                break;
            case MIDNIGHT:
                this.milkyWay(x, y, z, 0.5F);
                this.add(0.16F, 0.23F, 0.56F, 0.18F * lowGlow);
                break;
            case DEEP_SEA:
                this.caustics(x, y, z, elDeg);
                this.add(this.ar2, this.ag2, this.ab2, 0.16F * lowGlow);
                break;
            case STORM:
                this.clouds(x, y, z, 0.3F, 0.97F, 5923960, 1185311, 0.11);
                break;
            case INFERNO:
                this.lava(x, y, z, elDeg);
                this.add(1.0F, 0.35F, 0.08F, 0.35F * lowGlow);
                break;
            case MATRIX:
                this.code(az, elDeg);
                break;
            case PRISM:
                this.prism(az, elDeg);
                break;
            case PULSAR:
                this.milkyWay(x, y, z, 0.25F);
                this.pulsar(x, y, z);
                break;
            case AQUARELL:
                this.paint(x, y, z, elDeg);
        }

        if (this.body != null) {
            this.bodyGlow(x, y, z, az, elDeg);
        }

        int ri = Math.round(soft(this.R) * 255.0F);
        int gi = Math.round(soft(this.G) * 255.0F);
        int bi = Math.round(soft(this.B) * 255.0F);
        return ri << 16 | gi << 8 | bi;
    }

    private static float soft(float v) {
        if (v <= 0.8F) {
            return Math.max(0.0F, v);
        }

        float o = v - 0.8F;
        return 0.8F + 0.2F * (o / (o + 0.2F));
    }

    private void synth(float el) {
        float glow = (float) Math.exp(-Math.max(0.0F, el) / 9.0);
        this.add(this.ar1, this.ag1, this.ab1, 0.5F * glow);
        double line = Math.exp(-Math.pow((el - 0.7) / 0.6, 2.0));
        float pulse = 0.75F + 0.25F * (float) Math.sin(this.T * 1.6);
        this.add(this.ar2, this.ag2, this.ab2, (float) (line * 0.85 * pulse));
        this.add(this.ar2, this.ag2, this.ab2, 0.08F * (float) Math.exp(-Math.max(0.0F, el) / 3.0));
        if (el > 20.0F) {
            double sl = 0.5 + 0.5 * Math.sin(el * 1.3 - this.T * 0.5);
            this.add(
                    this.ar1,
                    this.ag1,
                    this.ab1,
                    (float) (0.03 * sl * smooth(20.0, 40.0, el) * this.pattern));
        }
    }

    private void curtains(double az, float el, float strength) {
        if (!(this.auroraOn <= 0.0F) && !(el < -2.0F)) {
            double azr = Math.toRadians(az);
            double t = this.T * (0.3 + this.lively * 0.45);

            for (int k = 0; k < 2; k++) {
                double base =
                        17
                                + k * 11
                                + 7.0 * Math.sin(azr * 2.0 + t * 0.22 + k * 1.7)
                                + 3.0 * Math.sin(azr * 5.0 - t * 0.35 + k);
                double h = el - base;
                double vert = h < 0.0 ? Math.exp(-h * h / 3.0) : Math.exp(-h / (15.0 + 6 * k));
                if (!(vert < 0.01)) {
                    double fold =
                            0.55
                                    + 0.45
                                            * Math.sin(
                                                    azr * 9.0
                                                            + 2.5 * Math.sin(azr * 2.0 + t * 0.45)
                                                            + t * 0.7
                                                            + k * 3);
                    double where =
                            smooth(
                                    0.25,
                                    0.65,
                                    noise(
                                            Math.cos(azr) * 0.6876 + k * 5,
                                            Math.sin(azr) * 0.6876 + t * 0.04,
                                            k + 0.3));
                    float in =
                            (float)
                                    (vert
                                            * (0.35 + 0.65 * fold)
                                            * (0.25 + 0.75 * where)
                                            * (k == 0 ? 1.0 : 0.6)
                                            * strength
                                            * this.pattern
                                            * this.breath);
                    float m = clamp((float) (h / 18.0));
                    this.add(
                            this.ar1 + (this.ar2 - this.ar1) * m,
                            this.ag1 + (this.ag2 - this.ag1) * m,
                            this.ab1 + (this.ab2 - this.ab1) * m,
                            in * 0.85F);
                }
            }
        }
    }

    private void swirl(double x, double y, double z) {
        double t = this.T * 0.012 * (0.5 + this.lively);
        double ca = Math.cos(t);
        double sa = Math.sin(t);
        double rx = x * ca - z * sa;
        double rz = x * sa + z * ca;
        double w1 = fbm(rx * 1.2 + 3.0, y * 1.2, rz * 1.2, 2);
        double w2 = fbm(rx * 1.2, y * 1.2 + 7.0, rz * 1.2 + 1.0, 2);
        double n = fbm(rx * 1.8 + w1 * 2.2, y * 1.8 + w2 * 2.2, rz * 1.8 - w1 * 1.5 + t * 2.0, 3);
        double cloud = smooth(0.36, 0.78, n);
        double core = smooth(0.58, 0.86, n);
        if (!(cloud < 0.01)) {
            float mm = (float) smooth(0.3, 0.7, w1);
            float k = (float) (cloud * 0.6 * this.pattern * this.breath);
            this.add(
                    this.ar2 + (this.ar1 - this.ar2) * mm,
                    this.ag2 + (this.ag1 - this.ag2) * mm,
                    this.ab2 + (this.ab1 - this.ab2) * mm,
                    k);
            this.add(1.0F, 0.92F, 1.0F, (float) (core * 0.22 * this.pattern));
        }
    }

    private void accretion(double az, float el) {
        LiveSky.Body bd = this.body;
        if (bd != null) {
            double du = ((az - bd.az()) % 360.0 + 540.0) % 360.0 - 180.0;
            du *= Math.cos(Math.toRadians(el));
            double dv = el - bd.el();
            double R = bd.radius();
            double flat = 0.2;
            double rr = Math.sqrt(du * du + Math.pow(dv / flat, 2.0));
            double dist = Math.sqrt(du * du + dv * dv);
            if (dist > R) {
                this.add(
                        this.ar1,
                        this.ag1,
                        this.ab1,
                        (float) (Math.exp(-(dist - R) / (R * 0.9)) * 0.35 * this.pattern));
            }

            if (!(rr < R * 1.1) && !(rr > R * 6.5)) {
                double band = smooth(R * 1.1, R * 1.35, rr) * (1.0 - smooth(R * 3.0, R * 6.5, rr));
                double ang = Math.atan2(dv / flat, du);
                double a2 = ang - this.T * 0.3;
                double streak =
                        0.5 + 0.5 * noise(Math.cos(a2) * 3.0, Math.sin(a2) * 3.0 + rr * 0.3, 4.2);
                double doppler = 1.0 + 0.75 * (-du / rr);
                float in = (float) (band * streak * doppler * this.pattern);
                float hot = clamp((float) (1.0 - (rr - R * 1.1) / (R * 3.0)));
                this.add(
                        this.ar2 + (1.0F - this.ar2) * hot * 0.7F,
                        this.ag2 + (this.ag1 - this.ag2) * hot + (1.0F - this.ag1) * hot * 0.3F,
                        this.ab2 + (this.ab1 - this.ab2) * hot,
                        in * 1.1F);
            }
        }
    }

    private void galaxyBand(double x, double y, double z) {
        double t = this.T * 0.004;
        double nx = 0.36 * Math.cos(t) - 0.73 * Math.sin(t);
        double ny = 0.58;
        double nz = 0.36 * Math.sin(t) + 0.73 * Math.cos(t);
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double d = (x * nx + y * ny + z * nz) / l;
        double band = Math.exp(-d * d / 0.04);
        if (!(band < 0.01)) {
            double core = Math.exp(-d * d / 0.005);
            double tint = fbm(x * 2.2 + 5.0, y * 2.2, z * 2.2, 2);
            this.add(
                    this.ar1,
                    this.ag1,
                    this.ab1,
                    (float) (band * smooth(0.45, 0.75, tint) * 0.22 * this.pattern));
            double n = fbm(x * 5.0 + 2.0, y * 5.0, z * 5.0 - 1.0, 3);
            double dust =
                    Math.exp(-d * d / 0.0025)
                            * smooth(0.45, 0.7, fbm(x * 11.0, y * 11.0 + 3.0, z * 11.0, 2));
            float in = (float) (band * (0.4 + 0.8 * n) * (1.0 - 0.7 * dust) * this.pattern);
            float m = (float) smooth(0.3, 0.8, n);
            this.add(
                    this.ar2 + (this.ar1 - this.ar2) * m,
                    this.ag2 + (this.ag1 - this.ag2) * m,
                    this.ab2 + (this.ab1 - this.ab2) * m,
                    in * 0.45F);
            this.add(1.0F, 0.9F, 0.8F, (float) (core * (1.0 - 0.7 * dust) * 0.35 * this.pattern));
        }
    }

    private void cityGlow(double az, float el) {
        float low = (float) Math.exp(-Math.max(0.0F, el) / 10.0);
        this.add(this.ar1, this.ag1, this.ab1, 0.42F * low);
        this.add(this.ar2, this.ag2, this.ab2, 0.22F * (float) Math.exp(-Math.max(0.0F, el) / 3.5));
        double azc = Math.toRadians(az);
        double city =
                smooth(0.4, 0.8, noise(Math.cos(azc) * 2.865, 0.5 + Math.sin(azc) * 2.865, 1.3));
        this.add(
                this.ar1,
                this.ag1,
                this.ab1,
                (float) (0.22 * city * Math.exp(-Math.max(0.0F, el) / 5.0)));
        if (!(el < -1.0F)) {
            for (int i = 0; i < 3; i++) {
                double center = 60 + i * 120 + 28.0 * Math.sin(this.T * 0.25 + i * 2.1);
                double tilt = 18.0 * Math.sin(this.T * 0.18 + i);
                double daz = ((az - center - tilt * el / 60.0) % 360.0 + 540.0) % 360.0 - 180.0;
                double w = 1.8 + el * 0.06;
                double beam =
                        (ClientEffects.cfg().fxBeams ? 1.0 : 0.0)
                                * Math.exp(-(daz * daz) / (w * w))
                                * Math.exp(-Math.max(0.0F, el) / 50.0)
                                * smooth(-1.0, 3.0, el);
                if (!(beam < 0.005)) {
                    float br = (float) (beam * 0.5 * this.pattern);
                    if (i == 1) {
                        this.add(this.ar2, this.ag2, this.ab2, br);
                    } else if (i == 0) {
                        this.add(this.ar1, this.ag1, this.ab1, br);
                    } else {
                        this.add(0.9F, 0.9F, 1.0F, br * 0.8F);
                    }
                }
            }
        }
    }

    private void iceBands(double az, float el) {
        if (!(el < 2.0F)) {
            double azi = Math.toRadians(az);
            double n = noise(Math.cos(azi) * 1.719, this.T * 0.05 + Math.sin(azi) * 1.719, 2.2);
            double band = Math.pow(0.5 + 0.5 * Math.sin(el * 0.35 + n * 3.0 - this.T * 0.15), 6.0);
            this.add(
                    this.ar1,
                    this.ag1,
                    this.ab1,
                    (float) (0.16 * band * smooth(5.0, 25.0, el) * this.pattern));
        }
    }

    private void lava(double x, double y, double z, float el) {
        double t = this.T * 0.03 * (0.6 + this.lively);
        double w = fbm(x * 1.6 + 3.0, y * 1.6 - t, z * 1.6, 2);
        double n = fbm(x * 2.4 + w * 1.6 + t, y * 2.4 - t * 0.7, z * 2.4 - w * 1.2, 3);
        double vein = Math.pow(1.0 - Math.abs(2.0 * n - 1.0), 6.0);
        double pulse = 0.75 + 0.25 * Math.sin(this.T * 1.3 + n * 12.0);
        float k =
                (float)
                        (vein
                                * pulse
                                * this.pattern
                                * this.breath
                                * (0.4 + 0.6 * smooth(-2.0, 20.0, el)));
        this.add(this.ar1, this.ag1, this.ab1, k * 1.1F);
        this.add(this.ar2, this.ag2, this.ab2, (float) (Math.pow(vein, 3.0) * 0.8 * this.pattern));
        this.add(0.5F, 0.06F, 0.02F, (float) (smooth(0.45, 0.8, n) * 0.18));
    }

    private void code(double az, float el) {
        if (!(el < -2.0F)) {
            double colW = 4.0;
            double col = Math.floor(az / colW);
            double h1 = hash3((int) col, 7, 3);
            double h2 = hash3((int) col, 11, 5);
            double speed = 0.25 + h1 * 0.5 * (0.5 + this.lively);
            double head = 90.0 - (this.T * speed * 0.35 + h2) % 1.0 * 110.0;
            double d = head - el;
            double cell = Math.floor(el / 3.0);
            double flick =
                    hash3((int) col, (int) cell, (int) Math.floor(this.T * 3.0 + h1 * 7.0)) > 0.25
                            ? 1.0
                            : 0.35;
            double edge = Math.abs(az / colW - col - 0.5) < 0.38 ? 1.0 : 0.2;
            if (d < 0.0) {
                double trail = Math.exp(d / 14.0) * flick * edge;
                this.add(this.ar1, this.ag1, this.ab1, (float) (trail * 0.75 * this.pattern));
            }

            if (Math.abs(d) < 2.2) {
                this.add(
                        this.ar2,
                        this.ag2,
                        this.ab2,
                        (float) ((1.0 - Math.abs(d) / 2.2) * edge * this.pattern));
            }

            this.add(
                    this.ar1,
                    this.ag1,
                    this.ab1,
                    (float) (0.05 * flick * edge * (0.5 + 0.5 * Math.sin(this.T + col))));
        }
    }

    private void prism(double az, float el) {
        double azr = Math.toRadians(az);
        double t = this.T * (0.15 + this.lively * 0.2);
        double beams =
                (ClientEffects.cfg().fxBeams ? 1.0 : 0.0)
                        * Math.pow(0.5 + 0.5 * Math.sin(azr * 7.0 + t + el * 0.035), 14.0);
        double beams2 =
                (ClientEffects.cfg().fxBeams ? 1.0 : 0.0)
                        * Math.pow(
                                0.5 + 0.5 * Math.sin(azr * 11.0 - t * 1.3 + el * 0.02 + 1.0), 18.0);
        double fall = smooth(0.0, 75.0, el);
        double hue = (az / 360.0 + this.T * 0.02) % 1.0;
        int c = hsv(hue, 0.6, 1.0);
        int c2 = hsv((hue + 0.33) % 1.0, 0.6, 1.0);
        this.add(r(c), g(c), b(c), (float) (beams * fall * 0.5 * this.pattern * this.breath));
        this.add(r(c2), g(c2), b(c2), (float) (beams2 * fall * 0.35 * this.pattern));
        double top = Math.exp(-(90.0F - el) / 18.0);
        this.add(1.0F, 0.95F, 1.0F, (float) (top * 0.25 * this.pattern));
    }

    private void pulsar(double x, double y, double z) {
        double a = Math.toRadians(30.0);
        double e = Math.toRadians(50.0);
        double px = -Math.sin(a) * Math.cos(e);
        double py = Math.sin(e);
        double pz = Math.cos(a) * Math.cos(e);
        double dot = x * px + y * py + z * pz;
        double ang = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        this.add(
                1.0F,
                1.0F,
                1.0F,
                (float) (Math.exp(-ang / 1.2) * 0.9 + Math.exp(-ang / 6.0) * 0.25));
        this.add(this.ar1, this.ag1, this.ab1, (float) (Math.exp(-ang / 14.0) * 0.2));
        double t = this.T * (0.5 + this.lively * 0.6);
        double ux = -Math.sin(a + (Math.PI / 2));
        double uz = Math.cos(a + (Math.PI / 2));
        double vx = py * uz;
        double vy = pz * ux - px * uz;
        double vz = -py * ux;
        double nx = Math.cos(t) * ux + Math.sin(t) * vx;
        double ny = Math.sin(t) * vy;
        double nz = Math.cos(t) * uz + Math.sin(t) * vz;
        double band = x * nx + y * ny + z * nz;
        double beam =
                (ClientEffects.cfg().fxBeams ? 1.0 : 0.0)
                        * Math.exp(-band * band / 0.0025)
                        * Math.exp(-ang / 55.0);
        this.add(this.ar1, this.ag1, this.ab1, (float) (beam * 0.7 * this.pattern));
        double ringR = this.T * 0.25 % 1.0 * 70.0;
        double ring = Math.exp(-Math.pow((ang - ringR) / 2.0, 2.0)) * (1.0 - ringR / 70.0);
        this.add(this.ar2, this.ag2, this.ab2, (float) (ring * 0.45 * this.pattern * this.breath));
    }

    private void paint(double x, double y, double z, float el) {
        double t = this.T * 0.02 * (0.5 + this.lively);
        int[] cols = new int[] {16752329, 10475775, 16765850, 12186312, 13215487};

        for (int k = 0; k < 3; k++) {
            double n = fbm(x * 1.3 + k * 7 + t, y * 1.3 - t * 0.6, z * 1.3 + k * 3, 3);
            double blob = smooth(0.44, 0.62, n);
            if (!(blob < 0.01)) {
                double rim = Math.exp(-Math.pow((n - 0.5) / 0.05, 2.0));
                int c = cols[(k * 2 + (int) Math.floor(n * 5.0)) % cols.length];
                this.over(r(c), g(c), b(c), (float) (blob * 0.65 * this.pattern));
                this.R *= 1.0F - 0.12F * (float) rim;
                this.G *= 1.0F - 0.12F * (float) rim;
                this.B *= 1.0F - 0.12F * (float) rim;
            }
        }
    }

    private static int hsv(double h, double s, double v) {
        double hh = (h % 1.0 + 1.0) % 1.0 * 6.0;
        double f = hh - Math.floor(hh);
        double p = v * (1.0 - s);
        double q = v * (1.0 - s * f);
        double u = v * (1.0 - s * (1.0 - f));
        double rr;
        double gg;
        double bb;
        switch ((int) Math.floor(hh)) {
            case 0:
                rr = v;
                gg = u;
                bb = p;
                break;
            case 1:
                rr = q;
                gg = v;
                bb = p;
                break;
            case 2:
                rr = p;
                gg = v;
                bb = u;
                break;
            case 3:
                rr = p;
                gg = q;
                bb = v;
                break;
            case 4:
                rr = u;
                gg = p;
                bb = v;
                break;
            default:
                rr = v;
                gg = p;
                bb = q;
        }

        return (int) Math.round(rr * 255.0) << 16
                | (int) Math.round(gg * 255.0) << 8
                | (int) Math.round(bb * 255.0);
    }

    private void aurora(double az, float el, float strength) {
        if (!(this.auroraOn <= 0.0F) && !(el < -2.0F)) {
            double azr = Math.toRadians(az);
            double t = this.T * (0.6 + this.lively * 0.8);

            for (int k = 0; k < 2; k++) {
                double base =
                        22
                                + k * 9
                                + 10.0 * Math.sin(azr * 2.0 + t * 0.3 + k * 2.1)
                                + 5.0 * Math.sin(azr * 5.0 - t * 0.55 + k)
                                + 2.5 * Math.sin(azr * 11.0 + t * 1.1);
                double h = el - base;
                double vert = h < 0.0 ? Math.exp(-h * h / 5.0) : Math.exp(-h / (13.0 + 5 * k));
                if (!(vert < 0.01)) {
                    double rays = noise(az * 0.2 + k * 41 + t * 0.35, t * 0.9, k * 7.3);
                    rays = 0.35 + 1.1 * rays * rays;
                    double band = smooth(0.28, 0.72, noise(az * 0.018 + k * 3, t * 0.06, 1.7 + k));
                    double pulse =
                            0.65
                                    + 0.9
                                            * Math.pow(
                                                    0.5
                                                            + 0.5
                                                                    * Math.sin(
                                                                            azr * 3.0 - t * 1.4
                                                                                    + k * 2),
                                                    3.0);
                    float in =
                            (float)
                                    (vert
                                            * rays
                                            * pulse
                                            * (0.25 + 0.75 * band)
                                            * (k == 0 ? 1.0 : 0.6)
                                            * strength
                                            * this.pattern
                                            * this.breath);
                    float m = clamp((float) (h / 20.0));
                    this.add(
                            this.ar1 + (this.ar2 - this.ar1) * m,
                            this.ag1 + (this.ag2 - this.ag1) * m,
                            this.ab1 + (this.ab2 - this.ab1) * m,
                            in * 0.9F);
                }
            }
        }
    }

    private void milkyWay(double x, double y, double z, float strength) {
        double nx = 0.36;
        double ny = 0.58;
        double nz = 0.73;
        double d = x * nx + y * ny + z * nz;
        double band = Math.exp(-d * d / 0.03);
        if (!(band < 0.01)) {
            double n =
                    fbm(
                            x * 4.0 + this.T * 0.03,
                            y * 4.0 - this.T * 0.02,
                            z * 4.0 + this.T * 0.015,
                            3);
            double dust =
                    Math.exp(-d * d / 0.0035)
                            * smooth(0.42, 0.7, fbm(x * 9.0, y * 9.0 + 3.0, z * 9.0, 2));
            float in =
                    (float)
                            (band
                                    * (0.35 + 0.9 * n)
                                    * (1.0 - 0.75 * dust)
                                    * strength
                                    * this.pattern);
            float m = (float) n;
            this.add(0.62F + 0.38F * m, 0.7F + 0.2F * m, 1.0F - 0.18F * m, in * 0.55F);
        }
    }

    private void nebula(double x, double y, double z, float strength, double scale) {
        double t = this.T * (0.035 + this.lively * 0.05);
        double wx = noise(x * 1.3 + t * 0.7, y * 1.3, z * 1.3 - t * 0.5) - 0.5;
        double wy = noise(x * 1.3 + 7.0, y * 1.3 + t * 0.6, z * 1.3 + 3.0) - 0.5;
        double n =
                fbm(
                        x * scale + t + wx * 1.4,
                        y * scale - t * 0.6 + wy * 1.4,
                        z * scale + t * 0.4 - wx,
                        3);
        double cloud = smooth(0.42, 0.85, n);
        if (!(cloud < 0.01)) {
            double m = fbm(x * scale * 1.7 - t, y * scale * 1.7, z * scale * 1.7 + 5.0, 2);
            float in = (float) (cloud * strength * this.pattern * this.breath);
            float mm = (float) smooth(0.3, 0.75, m);
            this.add(
                    this.ar1 + (this.ar2 - this.ar1) * mm,
                    this.ag1 + (this.ag2 - this.ag1) * mm,
                    this.ab1 + (this.ab2 - this.ab1) * mm,
                    in * 0.6F);
        }
    }

    private void pillars(double az, float el) {
        if (!(el < -2.0F)) {
            double t = this.T * (0.5 + this.lively);
            float sum = 0.0F;

            for (double[] q : PILLAR) {
                double da =
                        Math.abs(
                                ((az - q[0] - 6.0 * Math.sin(t * 0.2 * q[3] + q[2])) % 360.0
                                                        + 540.0)
                                                % 360.0
                                        - 180.0);
                if (!(da > q[1] * 4.0)) {
                    double w = Math.exp(-da * da / (q[1] * q[1]));
                    double height = 18.0 + 22.0 * (0.5 + 0.5 * Math.sin(t * 0.5 * q[3] + q[2]));
                    double vert = Math.exp(-Math.max(0.0F, el) / height) * smooth(-2.0, 1.0, el);
                    sum +=
                            (float)
                                    (w
                                            * vert
                                            * (0.75
                                                    + 0.25
                                                            * Math.sin(
                                                                    t * 2.2 * q[3] + q[2] * 3.0)));
                }
            }

            if (!(sum <= 0.003F)) {
                float m = clamp(el / 35.0F);
                this.add(
                        this.ar1 + (this.ar2 - this.ar1) * m,
                        this.ag1 + (this.ag2 - this.ag1) * m,
                        this.ab1 + (this.ab2 - this.ab1) * m,
                        sum * 0.85F * this.pattern * this.breath);
            }
        }
    }

    private void caustics(double x, double y, double z, float el) {
        double t = this.T * (0.45 + this.lively * 0.5);
        double s = 3.2;
        double n1 = noise(x * s + t, y * s + t * 0.7, z * s - t * 0.4);
        double n2 = noise(x * s * 1.6 - t * 0.8, y * s * 1.6 + 4.0, z * s * 1.6 + t * 0.5);
        double w1 = 1.0 - Math.abs(2.0 * n1 - 1.0);
        double w2 = 1.0 - Math.abs(2.0 * n2 - 1.0);
        double c = Math.pow(w1, 7.0) + 0.7 * Math.pow(w2, 9.0);
        float high = smooth01((el - 5.0F) / 50.0F);
        this.add(
                this.ar1,
                this.ag1,
                this.ab1,
                (float) (c * 0.5 * high * this.pattern * this.breath));
        double az = Math.atan2(-x, z) + t * 0.05;
        double sh = noise(Math.cos(az) * 3.0, 2.5 + Math.sin(az) * 3.0, 7.0);
        this.add(
                this.ar2,
                this.ag2,
                this.ab2,
                (float) (Math.pow(sh, 4.0) * 0.35 * high * this.pattern));
    }

    private void clouds(
            double x,
            double y,
            double z,
            float thr,
            float opacity,
            int light,
            int dark,
            double speed) {
        if (!(y < 0.01)) {
            double s = 1.0 / (y + 0.12);
            double px = x * s;
            double pz = z * s;
            double t = this.T * speed * (1.2 + 1.5 * this.lively);
            double n = fbm(px * 0.85 + t, pz * 0.85 + t * 0.35, t * 0.6, 4);
            double cov = smooth(thr, thr + 0.22, n) * smooth(0.0, 0.14, y);
            if (!(cov < 0.01)) {
                double lit = smooth(0.35, 0.95, n + 0.15 * y);
                if (this.body != null && this.body.kind() == 0) {
                    lit =
                            Math.min(
                                    1.0,
                                    lit
                                            + 0.35
                                                    * Math.max(
                                                            0.0,
                                                            x * this.sunX
                                                                    + y * this.sunY
                                                                    + z * this.sunZ));
                }

                float lr = r(light);
                float lg = g(light);
                float lb = b(light);
                float dr = r(dark);
                float dg = g(dark);
                float db = b(dark);
                float l = (float) lit;
                this.over(
                        dr + (lr - dr) * l,
                        dg + (lg - dg) * l,
                        db + (lb - db) * l,
                        (float) cov * opacity);
            }
        }
    }

    private void bodyGlow(double x, double y, double z, double az, float el) {
        double dot = x * this.sunX + y * this.sunY + z * this.sunZ;
        double ang = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        LiveSky.Body bd = this.body;
        float cr = r(bd.color());
        float cg = g(bd.color());
        float cb = b(bd.color());
        double rad = bd.radius();
        if (bd.kind() == 0) {
            this.add(
                    cr,
                    cg,
                    cb,
                    (float) (Math.exp(-ang / 14.0) * 0.55 + Math.exp(-ang / 4.0) * 0.45));
            if (this.P == SkyPreset.GOLDEN_HOUR || this.P == SkyPreset.SAKURA) {
                double around =
                        Math.atan2(el - bd.el(), ((az - bd.az()) % 360.0 + 540.0) % 360.0 - 180.0);
                double ray =
                        Math.pow(
                                0.5
                                        + 0.5
                                                * Math.sin(
                                                        around * 11.0
                                                                + this.T * 0.3
                                                                + noise(
                                                                                Math.cos(around)
                                                                                        * 2.0,
                                                                                this.T * 0.25
                                                                                        + Math.sin(
                                                                                                        around)
                                                                                                * 2.0,
                                                                                3.0)
                                                                        * 3.0),
                                5.0);
                this.add(
                        cr,
                        cg,
                        cb,
                        (float)
                                (ray
                                        * Math.exp(-ang / 26.0)
                                        * 0.28
                                        * smooth(rad, rad * 2.5, ang)
                                        * this.pattern));
            }
        } else if (bd.kind() == 1) {
            this.add(
                    cr,
                    cg,
                    cb,
                    (float) (Math.exp(-ang / 5.0) * 0.32 + Math.exp(-ang / 22.0) * 0.14));
        } else if (bd.kind() == 3) {
            double out = Math.max(0.0, ang - rad);
            this.add(
                    this.ar1,
                    this.ag1,
                    this.ab1,
                    (float) (Math.exp(-out / 10.0) * 0.45 + Math.exp(-out / 3.0) * 0.35));
        } else {
            if (ang < rad) {
                this.over(0.008F, 0.004F, 0.02F, 1.0F);
                return;
            }

            double around =
                    Math.atan2(el - bd.el(), ((az - bd.az()) % 360.0 + 540.0) % 360.0 - 180.0);
            double fl =
                    0.6
                            + 0.5
                                    * noise(
                                            Math.cos(around) * 3.0,
                                            this.T * 0.9 + Math.sin(around) * 3.0,
                                            5.0);
            double a5 = around + this.T * 0.02;
            double streams =
                    0.5
                            + 1.0
                                    * Math.pow(
                                            noise(
                                                    Math.cos(a5) * 5.0 + 9.0,
                                                    this.T * 0.35 + Math.sin(a5) * 5.0,
                                                    2.0),
                                            2.0)
                                    * 2.0;
            double out = ang - rad;
            this.add(this.ar1, this.ag1, this.ab1, (float) (Math.exp(-out / 1.6) * 1.2 * fl));
            this.add(
                    this.ar2,
                    this.ag2,
                    this.ab2,
                    (float) (Math.exp(-out / 7.0) * 0.45 * streams * this.pattern));
        }
    }

    public int[] view(
            SkyPreset p,
            int a1,
            int a2,
            double t,
            int w,
            int h,
            float yaw,
            float pitch,
            float fovDeg) {
        this.prepare(p, a1, a2, t, null);
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
        Random rr = new Random(p.name().hashCode());
        double ph0 = rr.nextDouble() * 10.0;

        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                double sx = xx + 0.5 - w / 2.0;
                double sy = -(yy + 0.5 - h / 2.0);
                double dx = fx0 * f + rx * sx + ux * sy;
                double dy = fy0 * f + ry * sx + uy * sy;
                double dz = fz0 * f + rz * sx + uz * sy;
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= l;
                dy /= l;
                dz /= l;
                float el = (float) Math.toDegrees(Math.asin(dy));
                int c = this.color(dx, dy, dz, el);
                float cr = r(c);
                float cg = g(c);
                float cb = b(c);
                if (p.stars > 0.0F && el > 3.0F && ClientEffects.cfg().fxStars) {
                    double hs =
                            hash3(
                                    (int) Math.floor(dx * 260.0),
                                    (int) Math.floor(dy * 260.0),
                                    (int) Math.floor(dz * 260.0));
                    if (hs > 1.0 - 0.0045 * p.stars * ClientEffects.cfg().starDensity) {
                        float st =
                                (float)
                                                ((hs
                                                                - (1.0
                                                                        - 0.0045
                                                                                * p.stars
                                                                                * ClientEffects
                                                                                        .cfg()
                                                                                        .starDensity))
                                                        / (0.0045
                                                                * p.stars
                                                                * ClientEffects.cfg().starDensity))
                                        * 0.9F;
                        cr = Math.min(1.0F, cr + st);
                        cg = Math.min(1.0F, cg + st);
                        cb = Math.min(1.0F, cb + st);
                    }
                }

                LiveSky.Body bd = this.body;
                if (bd != null) {
                    double ang =
                            Math.toDegrees(
                                    Math.acos(
                                            Math.max(
                                                    -1.0,
                                                    Math.min(
                                                            1.0,
                                                            dx * this.sunX
                                                                    + dy * this.sunY
                                                                    + dz * this.sunZ))));
                    double edge = clamp((float) ((bd.radius() - ang) / 0.5 + 0.5));
                    if (bd.kind() == 3) {
                        double v = (el - bd.el()) / bd.radius();
                        if (edge > 0.0 && !synthGap(v, this.T / 2.2)) {
                            int sc = synthColor(v);
                            cr = (float) (cr + (r(sc) - cr) * edge);
                            cg = (float) (cg + (g(sc) - cg) * edge);
                            cb = (float) (cb + (b(sc) - cb) * edge);
                        }
                    } else if (bd.kind() == 2) {
                        double ring = Math.exp(-Math.pow((ang - bd.radius()) / 0.35, 2.0));
                        cr = (float) Math.min(1.0, cr * (1.0 - edge) + ring);
                        cg = (float) Math.min(1.0, cg * (1.0 - edge) + ring * 0.92);
                        cb = (float) Math.min(1.0, cb * (1.0 - edge) + ring * 0.8);
                    } else if (edge > 0.0) {
                        float br = bd.kind() == 0 ? 1.0F : 0.92F;
                        cr =
                                (float)
                                        (cr
                                                + (Math.min(1.0F, r(bd.color()) * 0.3F + 0.7F * br)
                                                                - cr)
                                                        * edge);
                        cg =
                                (float)
                                        (cg
                                                + (Math.min(1.0F, g(bd.color()) * 0.6F + 0.4F * br)
                                                                - cg)
                                                        * edge);
                        cb =
                                (float)
                                        (cb
                                                + (Math.min(1.0F, b(bd.color()) * 0.6F + 0.4F * br)
                                                                - cb)
                                                        * edge);
                    }
                }

                double az = Math.atan2(-dx, dz);
                double hill =
                        1.5 + 2.2 * Math.sin(az * 3.0 + ph0) + 1.2 * Math.sin(az * 7.0 + ph0 * 2.0);
                double tree =
                        Math.max(0.0, Math.sin(az * 95.0 + ph0))
                                * Math.max(0.0, Math.sin(az * 37.0 + ph0 * 3.0))
                                * 4.5;
                if (el < hill + tree) {
                    cr = 0.024F;
                    cg = 0.016F;
                    cb = 0.04F;
                }

                float dk = Math.max(0.0F, (yy - h * 0.6F) / (h * 0.4F));
                dk = dk * dk * 0.55F;
                cr *= 1.0F - dk;
                cg *= 1.0F - dk;
                cb *= 1.0F - dk;
                out[yy * w + xx] =
                        0xFF000000
                                | Math.round(clamp(cr) * 255.0F) << 16
                                | Math.round(clamp(cg) * 255.0F) << 8
                                | Math.round(clamp(cb) * 255.0F);
            }
        }

        return out;
    }

    private static double hash3(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ h >>> 13) * 1274126177;
        h ^= h >>> 16;
        return (h & 16777215) / 1.6777215E7;
    }

    static double noise(double x, double y, double z) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        int iz = (int) Math.floor(z);
        double fx = x - ix;
        double fy = y - iy;
        double fz = z - iz;
        double ux = fx * fx * (3.0 - 2.0 * fx);
        double uy = fy * fy * (3.0 - 2.0 * fy);
        double uz = fz * fz * (3.0 - 2.0 * fz);
        double a = hash3(ix, iy, iz);
        double b = hash3(ix + 1, iy, iz);
        double c = hash3(ix, iy + 1, iz);
        double d = hash3(ix + 1, iy + 1, iz);
        double e = hash3(ix, iy, iz + 1);
        double f = hash3(ix + 1, iy, iz + 1);
        double g = hash3(ix, iy + 1, iz + 1);
        double h = hash3(ix + 1, iy + 1, iz + 1);
        double x00 = a + (b - a) * ux;
        double x10 = c + (d - c) * ux;
        double x01 = e + (f - e) * ux;
        double x11 = g + (h - g) * ux;
        double y0 = x00 + (x10 - x00) * uy;
        double y1 = x01 + (x11 - x01) * uy;
        return y0 + (y1 - y0) * uz;
    }

    static double fbm(double x, double y, double z, int oct) {
        double sum = 0.0;
        double amp = 0.5;
        double norm = 0.0;

        for (int i = 0; i < oct; i++) {
            sum += noise(x, y, z) * amp;
            norm += amp;
            x = x * 2.03 + 17.1;
            y = y * 2.03 - 9.7;
            z = z * 2.03 + 3.3;
            amp *= 0.5;
        }

        return sum / norm;
    }

    private static double smooth(double a, double b, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - a) / (b - a)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static float smooth01(float x) {
        float t = clamp(x);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    static {
        Random r = new Random(77L);

        for (int i = 0; i < PILLAR.length; i++) {
            PILLAR[i] =
                    new double[] {
                        r.nextDouble() * 360.0,
                        1.2 + r.nextDouble() * 2.2,
                        r.nextDouble() * 6.28,
                        0.6 + r.nextDouble() * 0.8
                    };
        }
    }

    public record Body(double az, double el, double radius, int color, int kind) {
        public static final int SUN = 0;
        public static final int MOON = 1;
        public static final int ECLIPSE = 2;
        public static final int SYNTH = 3;
    }
}
