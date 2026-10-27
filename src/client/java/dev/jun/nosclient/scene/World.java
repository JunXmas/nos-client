package dev.jun.nosclient.scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class World {
    static final int STRIDE = 14;
    static final int X0 = -80;
    static final int X1 = 80;
    static final int Z0 = -72;
    static final int Z1 = 24;
    static final int MS = 2;
    static final int MX = 208;
    static final int MZ0 = -176;
    static final double LX = 1.0;
    static final double LZ = -4.0;
    static final float WY = -0.125F;
    static final String[] TILES = new String[]{
        "gtop",
        "gside",
        "dirt",
        "sand",
        "stone",
        "snow",
        "gsnow",
        "clog",
        "clogt",
        "cleaf",
        "cane",
        "poppy",
        "dand",
        "azure",
        "grassp",
        "sap",
        "ppet",
        "lily",
        "oakl",
        "oakt",
        "oakf",
        "birl",
        "birt",
        "birf",
        "tulr",
        "tulo",
        "tulp",
        "tulw",
        "corn",
        "alli",
        "daisy",
        "lotv",
        "tgb",
        "tgt",
        "fern",
        "lilb",
        "lilt",
        "roseb",
        "roset",
        "peob",
        "peot",
        "glint",
        "bone",
        "fly",
        "p0",
        "p1",
        "p2",
        "p3",
        "p4",
        "p5",
        "p6",
        "p7",
        "p8",
        "p9",
        "p10",
        "p11"
    };
    private static final Map<String, float[]> UV = new HashMap<>();
    private static final int[][] N8;
    private final Map<Long, Integer> hm = new HashMap<>();
    private long srng = 5L;
    private static final int[] ORD_A;
    private static final int[] ORD_B;
    static final String[] FACES;
    private static final Map<String, int[][]> FC;
    private static final Map<String, Float> FS;
    private static final Map<String, int[]> NRM;
    private static final float[] AOL;
    private final Set<Long> occ = new HashSet<>();
    private final Set<Long> trunks = new HashSet<>();
    private final World.Solid TS = (x, y, z) -> y < this.hgt(x, z);
    private final World.Solid TRS = (x, y, z) -> this.occ.contains(key(x, y, z)) || y < this.hgt(x, z);
    final List<World.Tree> trees = new ArrayList<>();
    final List<double[]> canes = new ArrayList<>();
    private static final double[][] SHEEP;
    static final int SHEEP_FLOATS;
    private int nSB;
    static final int CY = 36;
    static final int CH = 4;
    static final int CS = 12;
    static final int CI0 = -50;
    static final int CI1 = 50;
    static final int CJ0 = -66;
    static final int CJ1 = 13;

    static int tileSize(String n) {
        return n.matches("p\\d+") ? 3 : (!n.equals("glint") && !n.equals("fly") ? 16 : 8);
    }

    static float[] uv(String tile) {
        return UV.get(tile);
    }

    static double lerp(double a, double b, double k) {
        return a + (b - a) * k;
    }

    static double sstep(double a, double b, double v) {
        double k = Math.min(1.0, Math.max(0.0, (v - a) / (b - a)));
        return k * k * (3.0 - 2.0 * k);
    }

    static double hash(double x, double z) {
        double h = Math.sin(x * 127.1 + z * 311.7) * 43758.5453;
        return h - Math.floor(h);
    }

    static double vnoise(double x, double z) {
        double xi = Math.floor(x);
        double zi = Math.floor(z);
        double fx = s3(x - xi);
        double fz = s3(z - zi);
        return lerp(lerp(hash(xi, zi), hash(xi + 1.0, zi), fx), lerp(hash(xi, zi + 1.0), hash(xi + 1.0, zi + 1.0), fx), fz);
    }

    private static double s3(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    static double lakeE(double x, double z) {
        double a = (x + 0.5 - 1.0) / 9.5;
        double b = (z + 0.5 - -4.0) / 5.8;
        return a * a + b * b;
    }

    static boolean isW(int x, int z) {
        return lakeE(x, z) < 1.0;
    }

    static boolean isShore(int x, int z) {
        if (isW(x, z)) {
            return false;
        }

        for (int[] d : N8) {
            if (isW(x + d[0], z + d[1])) {
                return true;
            }
        }

        return false;
    }

    int hgt(int x, int z) {
        long k = (long)x << 32 ^ z & 4294967295L;
        Integer c = this.hm.get(k);
        if (c != null) {
            return c;
        }

        double e = lakeE(x, z);
        int h = e < 1.0
            ? (e < 0.3 ? -3 : (e < 0.65 ? -2 : -1))
            : (int)Math.floor(Math.min(1.0, Math.max(0.0, (-z - 12) / 12.0)) * (1.0 + 5.0 * vnoise(x * 0.09, z * 0.09)));
        double ridge = 1.0 - Math.abs(2.0 * vnoise(x * 0.028 + 7.0, z * 0.028) - 1.0);
        double m = sstep(-78.0, -150.0, z) * (1.0 - 0.55 * (1.0 - sstep(20.0, 60.0, Math.abs(x - 6))))
            + sstep(95.0, 150.0, Math.abs(x)) * sstep(-20.0, -60.0, z) * 0.6;
        if (m > 0.0 && e >= 1.0) {
            h += (int)Math.floor(m * (5.0 + 34.0 * ridge * ridge + 6.0 * vnoise(x * 0.09, z * 0.09)));
        }

        this.hm.put(k, h);
        return h;
    }

    private double sr() {
        this.srng = this.srng * 16807L % 2147483647L;
        return this.srng / 2.147483647E9;
    }

    static void quad(World.FloatList m, double[][] P, String tile, float[] shade4, float shade, World.Opt o, int rot) {
        float[] r = uv(tile);
        float[][] u = new float[][]{{r[0], r[1]}, {r[2], r[1]}, {r[2], r[3]}, {r[0], r[3]}};
        int[] ord = shade4 != null && shade4[0] + shade4[2] < shade4[1] + shade4[3] ? ORD_B : ORD_A;

        for (int i : ord) {
            double[] p = P[i];
            float[] t = u[(i + rot) % 4];
            m.add((float)p[0]);
            m.add((float)p[1]);
            m.add((float)p[2]);
            m.add(t[0]);
            m.add(t[1]);
            m.add(shade4 != null ? shade4[i] : shade);
            m.add((float)o.pv[0]);
            m.add((float)o.pv[1]);
            m.add((float)o.pv[2]);
            m.add((float)o.an[0]);
            m.add((float)o.an[1]);
            m.add((float)o.an[2]);
            m.add((float)o.an[3]);
            m.add((float)o.sw[i]);
        }
    }

    static void quad(World.FloatList m, double[][] P, String tile, float shade, World.Opt o) {
        quad(m, P, tile, null, shade, o, 0);
    }

    private static float[] aoOf(int x, int y, int z, String f, World.Solid S) {
        int[] n = NRM.get(f);
        int ax = n[0] != 0 ? 0 : (n[1] != 0 ? 1 : 2);
        int t0 = ax == 0 ? 1 : 0;
        int t1 = ax == 2 ? 1 : 2;
        int[] q = new int[]{x + n[0], y + n[1], z + n[2]};
        int[][] c4 = FC.get(f);
        float[] out = new float[4];

        for (int i = 0; i < 4; i++) {
            int[] c = c4[i];
            int[] a = (int[])q.clone();
            int[] b = (int[])q.clone();
            int[] k = (int[])q.clone();
            int d0 = c[t0] != 0 ? 1 : -1;
            int d1 = c[t1] != 0 ? 1 : -1;
            a[t0] += d0;
            b[t1] += d1;
            k[t0] += d0;
            k[t1] += d1;
            boolean s1 = S.at(a[0], a[1], a[2]);
            boolean s2 = S.at(b[0], b[1], b[2]);
            out[i] = AOL[s1 && s2 ? 0 : 3 - (s1 ? 1 : 0) - (s2 ? 1 : 0) - (S.at(k[0], k[1], k[2]) ? 1 : 0)];
        }

        return out;
    }

    static double[][] corners(String f, double x, double y, double z) {
        int[][] c = FC.get(f);
        double[][] P = new double[4][];

        for (int i = 0; i < 4; i++) {
            P[i] = new double[]{x + c[i][0], y + c[i][1], z + c[i][2]};
        }

        return P;
    }

    void face(World.FloatList m, int x, int y, int z, String f, String tile, World.Opt o, int rot, World.Solid S) {
        float fs = FS.get(f);
        float[] sh = null;
        if (S != null) {
            sh = aoOf(x, y, z, f, S);

            for (int i = 0; i < 4; i++) {
                sh[i] *= fs;
            }
        }

        quad(m, corners(f, x, y, z), tile, sh, fs, o, rot);
    }

    static void plant(World.FloatList m, double x, double y, double z, String tile, World.Opt o, double s0, double s1) {
        World.Opt q = o.sw(s1, s1, s0, s0);
        quad(m, new double[][]{{x + 0.1, y + 1.0, z + 0.1}, {x + 0.9, y + 1.0, z + 0.9}, {x + 0.9, y, z + 0.9}, {x + 0.1, y, z + 0.1}}, tile, null, 0.9F, q, 0);
        quad(m, new double[][]{{x + 0.9, y + 1.0, z + 0.1}, {x + 0.1, y + 1.0, z + 0.9}, {x + 0.1, y, z + 0.9}, {x + 0.9, y, z + 0.1}}, tile, null, 0.9F, q, 0);
    }

    static void flat(World.FloatList m, double x, double y, double z, String tile, World.Opt o, int rot) {
        quad(m, new double[][]{{x, y, z}, {x + 1.0, y, z}, {x + 1.0, y, z + 1.0}, {x, y, z + 1.0}}, tile, null, 1.0F, o, rot);
    }

    private static long key(int x, int y, int z) {
        return x & 1048575 | (long)(y & 1048575) << 20 | (long)(z & 1048575) << 40;
    }

    private void cherry(World.FloatList m, int ox, int oz, long seed, double t0) {
        long[] s = new long[]{seed};
        DoubleSupplier r = () -> (s[0] = s[0] * 16807L % 2147483647L) / 2.147483647E9;
        int by = this.hgt(ox, oz);
        int H = 7 + (r.getAsDouble() < 0.5 ? 1 : 0);
        Map<Long, World.Log> logs = new LinkedHashMap<>();
        Map<Long, World.Leaf> leaves = new LinkedHashMap<>();

        for (int y = 0; y < H; y++) {
            logs.put(key(ox, by + y, oz), new World.Log(ox, by + y, oz, 'y', 1.0 + y * 0.09));
        }

        List<double[]> att = new ArrayList<>();
        att.add(new double[]{ox, by + H, oz, 1.0 + H * 0.09});
        int[][] dirs = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int i = 3; i > 0; i--) {
            int j = (int)(r.getAsDouble() * (i + 1));
            int[] tmp = dirs[i];
            dirs[i] = dirs[j];
            dirs[j] = tmp;
        }

        int b = 0;

        for (int nb = r.getAsDouble() < 0.3 ? 1 : 2; b < nb; b++) {
            int dx = dirs[b][0];
            int dz = dirs[b][1];
            int sy = by + H - 4 - (int)(r.getAsDouble() * 2.0);
            int L = 2 + (int)(r.getAsDouble() * 3.0);
            int ey = by + H - 1 - (int)(r.getAsDouble() * 2.0);
            int py = sy;
            double t = 1.0 + (sy - by) * 0.09;

            for (int i = 1; i <= L; i++) {
                int y = (int)Math.round(sy + (double)(ey - sy) * i / L);
                t += 0.1;

                for (int yy = py + 1; yy < y; yy++) {
                    logs.put(key(ox + dx * i, yy, oz + dz * i), new World.Log(ox + dx * i, yy, oz + dz * i, 'y', t));
                }

                logs.put(key(ox + dx * i, y, oz + dz * i), new World.Log(ox + dx * i, y, oz + dz * i, (char)(dx != 0 ? 'x' : 'z'), t));
                py = y;
            }

            att.add(new double[]{ox + dx * L, ey + 1, oz + dz * L, t});
        }

        Consumer<World.Leaf> leaf = l -> {
            long k = key(l.x, l.y, l.z);
            if (!logs.containsKey(k)) {
                World.Leaf oxx = leaves.get(k);
                if (oxx == null || oxx.d > l.d) {
                    leaves.put(k, l);
                }
            }
        };
        int[][] layers = new int[][]{{2, 1, 0}, {1, 2, 0}, {0, 3, 0}, {-1, 3, 1}, {-2, 2, 1}};

        for (double[] at : att) {
            int ax = (int)at[0];
            int ay = (int)at[1];
            int az = (int)at[2];

            for (int[] ly : layers) {
                int dy = ly[0];
                int R = ly[1];
                boolean hang = ly[2] == 1;

                for (int dx = -R; dx <= R; dx++) {
                    for (int dz = -R; dz <= R; dz++) {
                        int a = Math.abs(dx);
                        int c = Math.abs(dz);
                        boolean edge = a == R || c == R;
                        if ((dy != -1 || !edge || !(r.getAsDouble() < 0.25))
                            && (R > 2 ? (a != R || c != R) && (a + c <= R * 2 - 2 || !(r.getAsDouble() < 0.25)) : a != R || c != R || !(r.getAsDouble() < 0.25))
                            )
                         {
                            double d = at[3] + 0.12 + (a + c + Math.abs(dy)) * 0.05 + r.getAsDouble() * 0.05;
                            leaf.accept(new World.Leaf(ax + dx, ay + dy, az + dz, d));
                            if (hang && edge && r.getAsDouble() < 0.16666666666666666) {
                                leaf.accept(new World.Leaf(ax + dx, ay + dy - 1, az + dz, d + 0.08));
                                if (r.getAsDouble() < 0.3333333333333333) {
                                    leaf.accept(new World.Leaf(ax + dx, ay + dy - 2, az + dz, d + 0.16));
                                }
                            }
                        }
                    }
                }
            }
        }

        this.occ.addAll(logs.keySet());
        this.occ.addAll(leaves.keySet());
        int top = by + H;
        List<int[]> lv = new ArrayList<>();

        for (World.Leaf l : leaves.values()) {
            lv.add(new int[]{l.x, l.y, l.z});
        }

        this.trees.add(new World.Tree(ox, by, oz, t0, lv));
        plant(m, ox, by, oz, "sap", World.Opt.pv(ox + 0.5, by, oz + 0.5).an(1.0, t0, 2.0, 0.0), 0.0, 0.03);

        for (World.Log g : logs.values()) {
            boolean vert = g.ax == 'y' && g.x == ox && g.z == oz;
            World.Opt o = (vert ? World.Opt.pv(g.x + 0.5, g.y, g.z + 0.5) : World.Opt.pv(g.x + 0.5, g.y + 0.5, g.z + 0.5))
                .an(1.0, t0 + g.d, vert ? 1.0 : 0.0, 0.35 + (top - g.y) * 0.04);

            for (String f : FACES) {
                boolean end = f.charAt(1) == g.ax;
                int rot = (g.ax != 'x' || f.charAt(1) == 'x') && (g.ax != 'z' || !f.equals("px") && !f.equals("nx")) ? 0 : 1;
                this.face(m, g.x, g.y, g.z, f, end ? "clogt" : "clog", o, rot, this.TRS);
            }
        }

        for (World.Leaf l : leaves.values()) {
            double w = 0.025;
            World.Opt o = World.Opt.pv(l.x + 0.5, l.y + 0.5, l.z + 0.5).an(1.0, t0 + l.d, 0.0, r.getAsDouble() * 0.3).sw(w, w, w, w);

            for (String f : FACES) {
                this.face(m, l.x, l.y, l.z, f, "cleaf", o, 0, this.TRS);
            }
        }

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = ox + dx;
                int z = oz + dz;
                if (dx * dx + dz * dz <= 14 && !isW(x, z) && !isShore(x, z) && this.hgt(x, z) == by && (dx != 0 || dz != 0) && !(r.getAsDouble() > 0.45)) {
                    World.Opt o = World.Opt.pv(x + 0.5, by, z + 0.5).an(1.0, t0 + 2.0 + r.getAsDouble() * 0.6, 0.0, r.getAsDouble() * 0.2);
                    flat(m, x, by + 0.015, z, "ppet", o, (int)(r.getAsDouble() * 4.0));
                }
            }
        }
    }

    private void blobTree(World.FloatList m, int ox, int oz, int H, String lg, String lt, String lf, long seed) {
        long[] s = new long[]{seed};
        DoubleSupplier r = () -> (s[0] = s[0] * 16807L % 2147483647L) / 2.147483647E9;
        int by = this.hgt(ox, oz);
        List<int[]> L = new ArrayList<>();
        this.trunks.add(key(ox, 0, oz));

        for (int y = 0; y < H; y++) {
            this.occ.add(key(ox, by + y, oz));
        }

        for (int i = 0; i >= -3; i--) {
            int j = 1 - (int)(i / 2.0);
            int y = by + H + i;

            for (int dx = -j; dx <= j; dx++) {
                for (int dz = -j; dz <= j; dz++) {
                    if (Math.abs(dx) != j || Math.abs(dz) != j || !(r.getAsDouble() < 0.5) && i != 0) {
                        long k = key(ox + dx, y, oz + dz);
                        if (!this.occ.contains(k)) {
                            this.occ.add(k);
                            L.add(new int[]{ox + dx, y, oz + dz});
                        }
                    }
                }
            }
        }

        for (int y = 0; y < H; y++) {
            for (String f : FACES) {
                this.face(m, ox, by + y, oz, f, f.charAt(1) == 'y' ? lt : lg, World.Opt.pv(ox + 0.5, by + y, oz + 0.5), 0, this.TRS);
            }
        }

        for (int[] p : L) {
            double w = 0.02;
            World.Opt o = World.Opt.pv(p[0] + 0.5, p[1] + 0.5, p[2] + 0.5).sw(w, w, w, w);

            for (String f : FACES) {
                this.face(m, p[0], p[1], p[2], f, lf, o, 0, this.TRS);
            }
        }
    }

    private void buildCanes(World.FloatList m) {
        this.srng = 77L;
        List<int[]> cand = new ArrayList<>();

        for (int x = -80; x < 80; x++) {
            for (int z = -72; z < 24; z++) {
                if (isShore(x, z) && (isW(x + 1, z) || isW(x - 1, z) || isW(x, z + 1) || isW(x, z - 1))) {
                    cand.add(new int[]{x, z});
                }
            }
        }

        cand.sort((a, b) -> Double.compare(Math.atan2(a[1] - -4.0, a[0] - 1.0), Math.atan2(b[1] - -4.0, b[0] - 1.0)));

        for (int i = 0; i < cand.size(); i++) {
            int x = cand.get(i)[0];
            int z = cand.get(i)[1];
            if ((z <= -1 || Math.abs(x - 1) >= 8) && i % 3 == 0 && !this.trees.stream().anyMatch(t -> Math.abs(t.x - x) + Math.abs(t.z - z) < 2)) {
                int n = this.canes.size();
                int h = 1 + (int)(this.sr() * 3.0);
                int y = this.hgt(x, z);
                this.canes.add(new double[]{x, y, z, n * 0.11});

                for (int k = 0; k < h; k++) {
                    plant(
                        m,
                        x,
                        y + k,
                        z,
                        "cane",
                        World.Opt.pv(x + 0.5, y + k, z + 0.5).an(2.0, n * 0.11 + k * 0.32, 1.0, (h - 1 - k) * 0.1 + this.sr() * 0.08),
                        k * 0.035,
                        (k + 1) * 0.035
                    );
                }
            }
        }
    }

    private void mtnCell(World.FloatList m, int x, int z, int S) {
        int h = this.hgt(x, z);
        int[][] nb = new int[][]{{S, 0}, {-S, 0}, {0, S}, {0, -S}};
        String[] nf = new String[]{"px", "nx", "pz", "nz"};
        int lo = Integer.MAX_VALUE;

        for (int[] d : nb) {
            lo = Math.min(lo, this.hgt(x + d[0], z + d[1]));
        }

        boolean snow = h >= 26;
        boolean rock = !snow && (h >= 15 || h - lo >= 3);
        quad(m, new double[][]{{x, h, z}, {x + S, h, z}, {x + S, h, z + S}, {x, h, z + S}}, snow ? "snow" : (rock ? "stone" : "gtop"), 1.0F, World.Opt.NONE);

        for (int i = 0; i < 4; i++) {
            int n = this.hgt(x + nb[i][0], z + nb[i][1]);
            if (n < h) {
                int[][] c = FC.get(nf[i]);

                for (int y = Math.max(n, h - 12); y < h; y += S) {
                    int y1 = Math.min(h, y + S);
                    String t = y1 == h ? (snow ? "gsnow" : (rock ? "stone" : "gside")) : (y >= h - 3 && !rock && !snow ? "dirt" : "stone");
                    double[][] P = new double[4][];

                    for (int k = 0; k < 4; k++) {
                        P[k] = new double[]{x + c[k][0] * S, c[k][1] != 0 ? y1 : y, z + c[k][2] * S};
                    }

                    quad(m, P, t, FS.get(nf[i]), World.Opt.NONE);
                }
            }
        }
    }

    World.Mesh build() {
        World.FloatList m = new World.FloatList();
        World.FloatList w = new World.FloatList();
        World.FloatList far = new World.FloatList();
        this.srng = 5L;

        for (int x = -208; x < 208; x += 2) {
            for (int z = -176; z < 24; z += 2) {
                if (x < -80 || x >= 80 || z < -72) {
                    this.mtnCell(far, x, z, 2);
                }
            }
        }

        String[] sf = new String[]{"px", "nx", "pz", "nz"};
        int[][] sd = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int x = -80; x < 80; x++) {
            for (int z = -72; z < 24; z++) {
                int h = this.hgt(x, z);
                boolean sand = isW(x, z) || isShore(x, z);
                if (h > 3) {
                    this.mtnCell(m, x, z, 1);
                } else {
                    this.face(m, x, h - 1, z, "py", sand ? "sand" : "gtop", World.Opt.NONE, 0, this.TS);

                    for (int i = 0; i < 4; i++) {
                        for (int y = Math.max(this.hgt(x + sd[i][0], z + sd[i][1]), h - 6); y < h; y++) {
                            this.face(m, x, y, z, sf[i], sand ? "sand" : (y == h - 1 ? "gside" : "dirt"), World.Opt.NONE, 0, this.TS);
                        }
                    }

                    if (isW(x, z)) {
                        quad(w, new double[][]{{x, -0.125, z}, {x + 1, -0.125, z}, {x + 1, -0.125, z + 1}, {x, -0.125, z + 1}}, "full", 1.0F, World.Opt.NONE);
                        if (this.sr() < 0.06) {
                            flat(m, x, -0.105, z, "lily", World.Opt.NONE, (int)(this.sr() * 4.0));
                        }
                    }
                }
            }
        }

        int[][] ch = new int[][]{{-11, -9, 11}, {13, -10, 23}, {28, -5, 37}, {-19, -15, 51}, {22, -18, 63}, {-30, -24, 71}, {11, -27, 83}, {-21, 4, 97}};

        for (int i = 0; i < ch.length; i++) {
            this.cherry(m, ch[i][0], ch[i][1], ch[i][2], i * 0.3);
        }

        int[][] bt = new int[][]{
            {-4, -23, 5, 0},
            {34, -13, 6, 0},
            {-35, -5, 5, 0},
            {5, -35, 6, 0},
            {41, 6, 5, 0},
            {-27, -12, 6, 1},
            {18, -31, 7, 1},
            {-13, -31, 6, 1},
            {33, -25, 6, 1},
            {-41, 9, 7, 1}
        };

        for (int i = 0; i < bt.length; i++) {
            boolean b = bt[i][3] == 1;
            this.blobTree(m, bt[i][0], bt[i][1], bt[i][2], b ? "birl" : "oakl", b ? "birt" : "oakt", b ? "birf" : "oakf", 131L + i * 17L);
        }

        for (World.Tree t : this.trees) {
            this.trunks.add(key(t.x, 0, t.z));
        }

        this.buildCanes(m);

        for (double[] c : this.canes) {
            this.trunks.add(key((int)c[0], 0, (int)c[2]));
        }

        this.srng = 19L;
        String[] FL = new String[]{"tulr", "tulo", "tulp", "tulw", "corn", "alli", "daisy", "lotv", "poppy", "dand", "azure"};
        String[][] DBL = new String[][]{{"lilb", "lilt"}, {"roseb", "roset"}, {"peob", "peot"}};

        for (int x = -36; x < 40; x++) {
            for (int z = -30; z < 19; z++) {
                int h = this.hgt(x, z);
                if ((Math.abs(x - 1) >= 7 || z <= 0 || z >= 8)
                    && !isW(x, z)
                    && !isShore(x, z)
                    && !this.trunks.contains(key(x, 0, z))
                    && !this.occ.contains(key(x, h, z))) {
                    double r = this.sr();
                    double g = vnoise(x * 0.16, z * 0.16);
                    double fp = vnoise(x * 0.21 + 40.0, z * 0.21 - 7.0);
                    World.Opt pv = World.Opt.pv(x + 0.5, h, z + 0.5);
                    if (fp > 0.64 && r < 0.5) {
                        plant(m, x, h, z, FL[(int)(vnoise(x * 0.06 + 9.0, z * 0.06 + 3.0) * FL.length * 1.6) % FL.length], pv, 0.0, 0.1);
                    } else if (r < 0.012) {
                        String[] d = DBL[(int)(this.sr() * 3.0)];
                        plant(m, x, h, z, d[0], pv, 0.0, 0.04);
                        plant(m, x, h + 1, z, d[1], World.Opt.pv(x + 0.5, h + 1, z + 0.5), 0.04, 0.09);
                    } else if (g > 0.5 && r < 0.42) {
                        if (r < 0.1) {
                            plant(m, x, h, z, "tgb", pv, 0.0, 0.05);
                            plant(m, x, h + 1, z, "tgt", World.Opt.pv(x + 0.5, h + 1, z + 0.5), 0.05, 0.11);
                        } else {
                            plant(m, x, h, z, vnoise(x * 0.3, z * 0.3 + 5.0) > 0.62 ? "fern" : "grassp", pv, 0.0, 0.1);
                        }
                    } else if (r < 0.07) {
                        plant(m, x, h, z, "grassp", pv, 0.0, 0.1);
                    }
                }
            }
        }

        float[] land = new float[m.n + far.n];
        System.arraycopy(m.a, 0, land, 0, m.n);
        System.arraycopy(far.a, 0, land, m.n, far.n);
        return new World.Mesh(land, m.n / 14, w.toArray());
    }

    private World.Pose sheepPose(double[] sd, double t) {
        double T = t + sd[4];
        double k = T % 16.0;
        double W = 7.0;
        double sp = 0.5;
        double dist = (Math.floor(T / 16.0) * W + Math.min(k, W)) * sp;
        double a = dist / sd[2] * sd[3];
        double x = sd[0] + Math.cos(a) * sd[2];
        double z = sd[1] + Math.sin(a) * sd[2];
        double hx = -Math.sin(a) * sd[3];
        double hz = Math.cos(a) * sd[3];
        double mv = sstep(0.0, 0.5, k) * (1.0 - sstep(W - 0.5, W, k));
        return new World.Pose(
            x,
            this.hgt((int)Math.floor(x), (int)Math.floor(z)),
            z,
            Math.atan2(-hx, -hz),
            Math.sin(dist * 6.0) * 0.75 * mv,
            sstep(W + 0.3, W + 1.0, k) * (1.0 - sstep(12.2, 13.0, k)),
            sd[5]
        );
    }

    private void sbox(
        float[] SB, World.Pose P, int ox, int u, int v, double[] pv, double rx, double x0, double y0, double z0, double w, double h, double d, double g
    ) {
        double X0 = pv[0] + x0 - g;
        double X1 = pv[0] + x0 + w + g;
        double Y0 = pv[1] + y0 - g;
        double Y1 = pv[1] + y0 + h + g;
        double Z0 = pv[2] + z0 - g;
        double Z1 = pv[2] + z0 + d + g;
        double c = Math.cos(rx);
        double s = Math.sin(rx);
        double cy = Math.cos(P.yaw);
        double sy = Math.sin(P.yaw);
        double S = P.sc / 16.0;
        Function<double[], double[]> tf = e -> {
            double y = e[1] - pv[1];
            double z = e[2] - pv[2];
            double Yr = y * c - z * s + pv[1];
            double Zr = y * s + z * c + pv[2];
            double wx = e[0] * S;
            double wy = (24.0 - Yr) * S;
            double wz = Zr * S;
            return new double[]{wx * cy + wz * sy + P.x, wy + P.y, -wx * sy + wz * cy + P.z};
        };
        double[] C = tf.apply(new double[]{(X0 + X1) / 2.0, (Y0 + Y1) / 2.0, (Z0 + Z1) / 2.0});
        double[][][] faces = new double[][][]{
            {{X0, Y0, Z0}, {X1, Y0, Z0}, {X1, Y0, Z1}, {X0, Y0, Z1}},
            {{X0, Y1, Z1}, {X1, Y1, Z1}, {X1, Y1, Z0}, {X0, Y1, Z0}},
            {{X1, Y0, Z0}, {X0, Y0, Z0}, {X0, Y1, Z0}, {X1, Y1, Z0}},
            {{X0, Y0, Z1}, {X1, Y0, Z1}, {X1, Y1, Z1}, {X0, Y1, Z1}},
            {{X0, Y0, Z0}, {X0, Y0, Z1}, {X0, Y1, Z1}, {X0, Y1, Z0}},
            {{X1, Y0, Z1}, {X1, Y0, Z0}, {X1, Y1, Z0}, {X1, Y1, Z1}}
        };
        double[][] uvs = new double[][]{
            {u + d, v, u + d + w, v + d},
            {u + d + w, v, u + d + 2.0 * w, v + d},
            {u + d, v + d, u + d + w, v + d + h},
            {u + 2.0 * d + w, v + d, u + 2.0 * d + 2.0 * w, v + d + h},
            {u, v + d, u + d, v + d + h},
            {u + d + w, v + d, u + 2.0 * d + w, v + d + h}
        };

        for (int fi = 0; fi < 6; fi++) {
            double[][] p = new double[4][];

            for (int i = 0; i < 4; i++) {
                p[i] = tf.apply(faces[fi][i]);
            }

            double nx = (p[0][0] + p[2][0]) / 2.0 - C[0];
            double ny = (p[0][1] + p[2][1]) / 2.0 - C[1];
            double nz = (p[0][2] + p[2][2]) / 2.0 - C[2];
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            float sh = Math.abs(ny) > 0.7 * l ? (ny > 0.0 ? 1.0F : 0.5F) : (Math.abs(nz) > Math.abs(nx) ? 0.8F : 0.6F);
            double[] q = uvs[fi];
            double[][] U = new double[][]{{q[0], q[1]}, {q[2], q[1]}, {q[2], q[3]}, {q[0], q[3]}};

            for (int i : ORD_A) {
                int o = this.nSB++ * 14;
                SB[o] = (float)p[i][0];
                SB[o + 1] = (float)p[i][1];
                SB[o + 2] = (float)p[i][2];
                SB[o + 3] = (float)((ox + U[i][0]) / 256.0);
                SB[o + 4] = (float)((192.0 + U[i][1]) / 256.0);
                SB[o + 5] = sh;
                SB[o + 6] = (float)P.x;
                SB[o + 7] = (float)P.y;
                SB[o + 8] = (float)P.z;

                for (int j = 9; j < 14; j++) {
                    SB[o + j] = 0.0F;
                }
            }
        }
    }

    int buildSheep(float[] SB, double t) {
        this.nSB = 0;

        for (double[] sd : SHEEP) {
            World.Pose P = this.sheepPose(sd, t);
            double[] hp = new double[]{0.0, 6.0 + P.g * 8.0, -8.0};
            double hr = P.g * 1.05;
            double[][] L = new double[][]{{-3.0, 7.0, P.leg}, {3.0, 7.0, -P.leg}, {-3.0, -5.0, -P.leg}, {3.0, -5.0, P.leg}};

            for (double[] ly : new double[][]{{0.0, 0.0, 0.0, 0.0, 12.0}, {64.0, 0.6, 1.75, 0.5, 6.0}}) {
                int ox = (int)ly[0];
                if (ox != 0) {
                    this.sbox(SB, P, ox, 0, 0, hp, hr, -3.0, -4.0, -4.0, 6.0, 6.0, 6.0, ly[1]);
                } else {
                    this.sbox(SB, P, ox, 0, 0, hp, hr, -3.0, -4.0, -6.0, 6.0, 6.0, 8.0, 0.0);
                }

                this.sbox(SB, P, ox, 28, 8, new double[]{0.0, 5.0, 2.0}, Math.PI / 2, -4.0, -10.0, -7.0, 8.0, 16.0, 6.0, ly[2]);

                for (double[] lg : L) {
                    this.sbox(SB, P, ox, 0, 16, new double[]{lg[0], 12.0, lg[1]}, lg[2], -2.0, 0.0, -2.0, 4.0, ly[4], 4.0, ly[3]);
                }
            }
        }

        return this.nSB;
    }

    static float[] buildClouds(boolean[] cm, int shift) {
        World.FloatList m = new World.FloatList();

        for (int i = -50; i < 50; i++) {
            for (int j = -66; j < 13; j++) {
                if (on(cm, i, j, shift)) {
                    int x0 = i * 12;
                    int x1 = x0 + 12;
                    int z0 = j * 12;
                    int z1 = z0 + 12;
                    int y0 = 36;
                    int y1 = 40;
                    quad(m, new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}, "full", 1.0F, World.Opt.NONE);
                    quad(m, new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}}, "full", 0.5F, World.Opt.NONE);
                    if (!on(cm, i + 1, j, shift)) {
                        quad(m, new double[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}}, "full", 0.6F, World.Opt.NONE);
                    }

                    if (!on(cm, i - 1, j, shift)) {
                        quad(m, new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}}, "full", 0.6F, World.Opt.NONE);
                    }

                    if (!on(cm, i, j + 1, shift)) {
                        quad(m, new double[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}}, "full", 0.8F, World.Opt.NONE);
                    }

                    if (!on(cm, i, j - 1, shift)) {
                        quad(m, new double[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}}, "full", 0.8F, World.Opt.NONE);
                    }
                }
            }
        }

        return m.toArray();
    }

    private static boolean on(boolean[] cm, int i, int j, int shift) {
        return cm[Math.floorMod(j, 256) * 256 + Math.floorMod(i - shift, 256)];
    }

    static {
        UV.put("full", new float[]{0.0F, 0.0F, 1.0F, 1.0F});

        for (int i = 0; i < TILES.length; i++) {
            int x = i % 16 * 16;
            int y = i / 16 * 16;
            int s = tileSize(TILES[i]);
            float e = 0.01F;
            UV.put(TILES[i], new float[]{(x + e) / 256.0F, (y + e) / 256.0F, (x + s - e) / 256.0F, (y + s - e) / 256.0F});
        }

        N8 = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        ORD_A = new int[]{0, 1, 2, 0, 2, 3};
        ORD_B = new int[]{1, 2, 3, 1, 3, 0};
        FACES = new String[]{"px", "nx", "pz", "nz", "py", "ny"};
        FC = Map.of(
            "px",
            new int[][]{{1, 1, 1}, {1, 1, 0}, {1, 0, 0}, {1, 0, 1}},
            "nx",
            new int[][]{{0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {0, 0, 0}},
            "pz",
            new int[][]{{0, 1, 1}, {1, 1, 1}, {1, 0, 1}, {0, 0, 1}},
            "nz",
            new int[][]{{1, 1, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}},
            "py",
            new int[][]{{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}},
            "ny",
            new int[][]{{0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {0, 0, 0}}
        );
        FS = Map.of("px", 0.6F, "nx", 0.6F, "pz", 0.8F, "nz", 0.8F, "py", 1.0F, "ny", 0.5F);
        NRM = Map.of(
            "px",
            new int[]{1, 0, 0},
            "nx",
            new int[]{-1, 0, 0},
            "py",
            new int[]{0, 1, 0},
            "ny",
            new int[]{0, -1, 0},
            "pz",
            new int[]{0, 0, 1},
            "nz",
            new int[]{0, 0, -1}
        );
        AOL = new float[]{0.42F, 0.6F, 0.78F, 1.0F};
        SHEEP = new double[][]{
            {-15.0, 1.0, 2.4, 1.0, 0.0, 1.0},
            {17.0, -1.0, 2.2, -1.0, 3.0, 1.0},
            {10.0, -15.0, 2.4, 1.0, 7.0, 1.0},
            {-8.0, -17.0, 2.0, -1.0, 11.0, 1.0},
            {-12.5, 3.2, 1.3, 1.0, 5.0, 0.62}
        };
        SHEEP_FLOATS = SHEEP.length * 12 * 36 * 14;
    }

    @Environment(EnvType.CLIENT)
    static final class FloatList {
        float[] a = new float[65536];
        int n;

        void add(float v) {
            if (this.n == this.a.length) {
                this.a = Arrays.copyOf(this.a, this.n * 2);
            }

            this.a[this.n++] = v;
        }

        float[] toArray() {
            return Arrays.copyOf(this.a, this.n);
        }
    }

    @Environment(EnvType.CLIENT)
    private record Leaf(int x, int y, int z, double d) {
    }

    @Environment(EnvType.CLIENT)
    private record Log(int x, int y, int z, char ax, double d) {
    }

    @Environment(EnvType.CLIENT)
    record Mesh(float[] land, int nearVerts, float[] water) {
    }

    @Environment(EnvType.CLIENT)
    record Opt(double[] pv, double[] an, double[] sw) {
        static final World.Opt NONE = new World.Opt(new double[3], new double[4], new double[4]);

        static World.Opt pv(double x, double y, double z) {
            return new World.Opt(new double[]{x, y, z}, new double[4], new double[4]);
        }

        World.Opt an(double a, double b, double c, double d) {
            return new World.Opt(this.pv, new double[]{a, b, c, d}, this.sw);
        }

        World.Opt sw(double a, double b, double c, double d) {
            return new World.Opt(this.pv, this.an, new double[]{a, b, c, d});
        }
    }

    @Environment(EnvType.CLIENT)
    private record Pose(double x, double y, double z, double yaw, double leg, double g, double sc) {
    }

    @Environment(EnvType.CLIENT)
    interface Solid {
        boolean at(int var1, int var2, int var3);
    }

    @Environment(EnvType.CLIENT)
    record Tree(int x, int y, int z, double t0, List<int[]> leaves) {
    }
}
