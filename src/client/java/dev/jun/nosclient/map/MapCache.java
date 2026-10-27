package dev.jun.nosclient.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.jun.nosclient.feat.Waypoints;
import dev.jun.nosclient.gui.WaypointEditorScreen;
import dev.jun.nosclient.gui.WaypointScreen;
import dev.jun.nosclient.gui.WorldMapScreen;
import dev.jun.nosclient.scene.SceneConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.MapColor.Brightness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class MapCache {
    private static final Logger LOG = LoggerFactory.getLogger("nosclient");
    public static final int R = 512;
    public static final int RC = 32;
    private static final int MAX_REGIONS = 64;
    private static int serial;
    private static final Map<Long, MapCache.Region> regions = new HashMap<>();
    private static final ChunkQueue<ClientLevel> pending = new ChunkQueue<>(4096);
    private static String worldKey = "";
    private static Path regionDirectory;
    private static long lastFlush;
    private static long lastTrim;
    private static ClientLevel seeded;
    private static final Set<Long> absent = new LinkedHashSet<>();
    private static final MutableBlockPos mp = new MutableBlockPos();

    private MapCache() {
    }

    private static long key(int a, int b) {
        return (long)a << 32 | b & 4294967295L;
    }

    private static Path dir(Minecraft mc) {
        String server = Waypoints.dirName(Waypoints.serverKey(mc));
        String dim = mc.level.dimension().identifier().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        return FabricLoader.getInstance().getConfigDir().resolve("nosclient-map").resolve(server).resolve(dim);
    }

    private static void ensureWorld(Minecraft mc) {
        String k = mc.level == null ? "" : Waypoints.serverKey(mc) + "|" + mc.level.dimension().identifier();
        if (!k.equals(worldKey)) {
            flush(true);

            for (MapCache.Region r : regions.values()) {
                close(r);
            }

            regions.clear();
            absent.clear();
            seeded = null;
            if (mc.level == null) {
                pending.clear();
            }

            worldKey = k;
            regionDirectory = mc.level == null ? null : dir(mc);
        }
    }

    private static void close(MapCache.Region r) {
        Minecraft.getInstance().getTextureManager().release(r.id);
    }

    public static MapCache.Region region(int rx, int rz, boolean create) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }

        ensureWorld(mc);
        MapCache.Region r = regions.get(key(rx, rz));
        if (r == null) {
            if (regions.size() >= 64) {
                return null;
            }

            if (!create && absent.contains(key(rx, rz))) {
                return null;
            }

            Path f = regionDirectory.resolve("r." + rx + "." + rz + ".png");
            boolean onDisk = Files.isRegularFile(f);
            if (!create && !onDisk) {
                absent.add(key(rx, rz));
                if (absent.size() > 2048) {
                    absent.remove(absent.iterator().next());
                }

                return null;
            }

            absent.remove(key(rx, rz));
            r = new MapCache.Region(rx, rz);
            if (onDisk) {
                try (
                    InputStream in = Files.newInputStream(f);
                    NativeImage img = NativeImage.read(in);
                ) {
                    if (img.getWidth() == 512 && img.getHeight() == 512) {
                        r.tex.getPixels().copyFrom(img);
                    }

                    r.dirty = true;
                } catch (IOException | RuntimeException e) {
                    LOG.debug("Bỏ qua ô bản đồ hỏng {}", f, e);
                }
            }

            regions.put(key(rx, rz), r);
        }

        r.lastUse = System.currentTimeMillis();
        return r;
    }

    public static void onChunkLoad(ClientLevel level, LevelChunk chunk) {
        if (enabled()) {
            ChunkPos p = chunk.getPos();
            pending.add(key(p.x, p.z), level);
        }
    }

    public static boolean enabled() {
        SceneConfig c = SceneConfig.INSTANCE;
        Screen screen = Minecraft.getInstance().screen;
        return c.minimap || screen instanceof WorldMapScreen || screen instanceof WaypointEditorScreen || screen instanceof WaypointScreen;
    }

    public static void tick(Minecraft mc) {
        if (mc.level != null && mc.player != null) {
            if (!enabled()) {
                if (!regions.isEmpty()) {
                    flush(true);

                    for (MapCache.Region r : regions.values()) {
                        close(r);
                    }

                    regions.clear();
                }

                pending.clear();
                seeded = null;
            } else {
                ensureWorld(mc);
                if (seeded != mc.level) {
                    seeded = mc.level;
                    ChunkPos center = mc.player.chunkPosition();
                    int radius = Math.min(12, mc.options.getEffectiveRenderDistance());

                    for (int ring = 0; ring <= radius; ring++) {
                        for (int dx = -ring; dx <= ring; dx++) {
                            for (int dz = -ring; dz <= ring; dz++) {
                                if (Math.max(Math.abs(dx), Math.abs(dz)) == ring
                                    && mc.level.getChunkSource().getChunk(center.x + dx, center.z + dz, false) != null) {
                                    pending.add(key(center.x + dx, center.z + dz), mc.level);
                                }
                            }
                        }
                    }
                }

                trim();
                long budget = System.nanoTime() + 4000000L;

                for (int n = 0; n < 4 && pending.size() > 0 && System.nanoTime() < budget; n++) {
                    ChunkQueue.Entry<ClientLevel> entry = pending.poll();
                    if (entry.world() == mc.level) {
                        long k = entry.coordinate();
                        drawChunk(mc.level, (int)(k >> 32), (int)k);
                    }
                }

                ChunkPos me = mc.player.chunkPosition();
                int t = (int)(mc.level.getGameTime() % 9L);
                drawChunk(mc.level, me.x - 1 + t % 3, me.z - 1 + t / 3);

                for (MapCache.Region r : regions.values()) {
                    if (r.dirty) {
                        r.tex.upload();
                        r.dirty = false;
                    }
                }

                long now = System.currentTimeMillis();
                if (now - lastFlush > 30000L) {
                    flush(false);
                    Iterator<MapCache.Region> it = regions.values().iterator();

                    while (it.hasNext()) {
                        MapCache.Region r = it.next();
                        if (now - r.lastUse > 120000L && !r.unsaved) {
                            close(r);
                            it.remove();
                        }
                    }
                }
            }
        } else {
            if (!worldKey.isEmpty()) {
                ensureWorld(mc);
            }
        }
    }

    public static void flush(boolean force) {
        lastFlush = System.currentTimeMillis();
        if (regionDirectory != null) {
            Path d = null;

            for (MapCache.Region r : regions.values()) {
                if (r.unsaved) {
                    try {
                        if (d == null) {
                            d = regionDirectory;
                            Files.createDirectories(d);
                        }

                        r.tex.getPixels().writeToFile(d.resolve("r." + r.rx + "." + r.rz + ".png"));
                        r.unsaved = false;
                    } catch (IOException e) {
                        LOG.warn("Không lưu được bản đồ vào {}", d, e);
                        return;
                    }
                }
            }
        }
    }

    private static void trim() {
        long now = System.currentTimeMillis();
        if (regions.size() >= 64 && now - lastTrim >= 1000L) {
            lastTrim = now;
            flush(false);

            for (MapCache.Region r : regions.values().stream().sorted(Comparator.comparingLong(rx -> rx.lastUse)).toList()) {
                if (regions.size() <= 56) {
                    break;
                }

                if (!r.unsaved) {
                    close(r);
                    regions.remove(key(r.rx, r.rz));
                }
            }
        }
    }

    private static void drawChunk(ClientLevel lvl, int cx, int cz) {
        LevelChunk ch = lvl.getChunkSource().getChunk(cx, cz, false);
        if (ch != null) {
            MapCache.Region r = region(Math.floorDiv(cx, 32), Math.floorDiv(cz, 32), true);
            if (r != null) {
                NativeImage img = r.tex.getPixels();
                int ox = Math.floorMod(cx, 32) * 16;
                int oz = Math.floorMod(cz, 32) * 16;
                boolean ceiling = lvl.dimensionType().hasCeiling();
                int bx = cx << 4;
                int bz = cz << 4;
                int[] north = new int[16];

                for (int x = 0; x < 16; x++) {
                    north[x] = surface(lvl, ch, bx + x, bz - 1, ceiling);
                }

                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int wx = bx + x;
                        int wz = bz + z;
                        int h = surface(lvl, ch, wx, wz, ceiling);
                        int col = 0;
                        if (h > Integer.MIN_VALUE) {
                            BlockState st = lvl.getBlockState(mp.set(wx, h, wz));
                            FluidState fl = st.getFluidState();
                            if (!fl.isEmpty() && fl.is(FluidTags.WATER)) {
                                int depth = 1;

                                while (depth < 12 && !lvl.getBlockState(mp.set(wx, h - depth, wz)).getFluidState().isEmpty()) {
                                    depth++;
                                }

                                Brightness b = depth > 7 ? Brightness.LOWEST : (depth > 3 ? Brightness.LOW : Brightness.NORMAL);
                                col = MapColor.WATER.calculateARGBColor(b);
                            } else {
                                MapColor mcol = st.getMapColor(lvl, mp);
                                int hn = north[x];
                                Brightness b = hn != Integer.MIN_VALUE && h != hn ? (h > hn ? Brightness.HIGH : Brightness.LOW) : Brightness.NORMAL;
                                col = mcol == MapColor.NONE ? 0 : mcol.calculateARGBColor(b);
                            }
                        }

                        north[x] = h;
                        int abgr = col == 0 ? 0 : 0xFF000000 | (col & 0xFF) << 16 | col & 0xFF00 | col >> 16 & 0xFF;
                        img.setPixelABGR(ox + x, oz + z, abgr);
                    }
                }

                r.dirty = true;
                r.unsaved = true;
            }
        }
    }

    private static int surface(ClientLevel lvl, LevelChunk ch, int x, int z, boolean ceiling) {
        LevelChunk c = x >> 4 == ch.getPos().x && z >> 4 == ch.getPos().z ? ch : lvl.getChunkSource().getChunk(x >> 4, z >> 4, false);
        if (c == null) {
            return Integer.MIN_VALUE;
        }

        if (!ceiling) {
            return c.getHeight(Types.WORLD_SURFACE, x & 15, z & 15);
        }

        int y = Math.min(100, lvl.getMaxY());

        while (y > lvl.getMinY() && !c.getBlockState(mp.set(x, y, z)).isAir()) {
            y--;
        }

        while (y > lvl.getMinY() && c.getBlockState(mp.set(x, y, z)).isAir()) {
            y--;
        }

        return y;
    }

    @Environment(EnvType.CLIENT)
    public static final class Region {
        final int rx;
        final int rz;
        final DynamicTexture tex;
        final Identifier id;
        boolean dirty;
        boolean unsaved;
        long lastUse;

        Region(int rx, int rz) {
            this.rx = rx;
            this.rz = rz;
            this.tex = new DynamicTexture(() -> "nosclient map " + rx + "," + rz, 512, 512, true);
            this.id = Identifier.fromNamespaceAndPath(
                "nosclient", "map/" + (rx < 0 ? "m" + -rx : rx) + "_" + (rz < 0 ? "m" + -rz : rz) + "_" + ++MapCache.serial
            );
            Minecraft.getInstance().getTextureManager().register(this.id, this.tex);
        }

        public Identifier id() {
            return this.id;
        }
    }
}
