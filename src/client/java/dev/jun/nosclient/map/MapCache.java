package dev.jun.nosclient.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.jun.nosclient.feat.Waypoints;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bản đồ đã đi qua, 1 pixel = 1 khối, chia vùng 512×512 (32×32 chunk) — mỗi vùng một texture, chỉ upload vùng đổi.
 * Lưu đĩa theo máy chủ/chiều: config/nosclient-map/&lt;khoá&gt;/&lt;chiều&gt;/r.X.Z.png, nên bản đồ nhớ nơi đã tới.
 *
 * Bản cũ (hud/Minimap) quét lại ô vuông quanh người mỗi tick và tỉ lệ đổi theo "bán kính chunk" — chạy nhanh là
 * địa hình hiện trễ, không nhớ gì, và đổi phạm vi là đổi luôn zoom. Ở đây vẽ chunk khi nó nạp (một lần) và vẽ lại vài
 * chunk quanh người mỗi tick để bắt khối bị đặt/phá.
 *
 * Màu: MapColor của khối mặt (như bản đồ giấy) + bóng theo chênh cao với khối phía bắc; nước tô đậm theo độ sâu.
 * Chiều có trần (Nether) thì quét từ Y=100 xuống để không vẽ nóc đá.
 */
public final class MapCache {
	private MapCache() {}

	private static final Logger LOG = LoggerFactory.getLogger("nosclient");
	public static final int R = 512, RC = R / 16;

	public static final class Region {
		final int rx, rz;
		final DynamicTexture tex;
		final Identifier id;
		boolean dirty, unsaved;
		long lastUse;

		Region(int rx, int rz) {
			this.rx = rx;
			this.rz = rz;
			tex = new DynamicTexture(() -> "nosclient map " + rx + "," + rz, R, R, true);
			id = Identifier.fromNamespaceAndPath("nosclient", "map/" + (rx < 0 ? "m" + -rx : rx) + "_" + (rz < 0 ? "m" + -rz : rz) + "_" + ++serial);
			Minecraft.getInstance().getTextureManager().register(id, tex);
		}

		public Identifier id() {
			return id;
		}
	}

	private static int serial;
	private static final Map<Long, Region> regions = new HashMap<>();
	private static final ArrayDeque<Long> pending = new ArrayDeque<>();
	private static String worldKey = "";
	private static long lastFlush;

	private static long key(int a, int b) {
		return (long) a << 32 | (b & 0xFFFFFFFFL);
	}

	/** Thư mục của thế giới + chiều hiện tại; tên đã lọc ký tự lạ (IP có ':'). */
	private static Path dir(Minecraft mc) {
		String server = Waypoints.serverKey(mc).replaceAll("[^A-Za-z0-9._-]", "_");
		String dim = mc.level.dimension().identifier().toString().replaceAll("[^A-Za-z0-9._-]", "_");
		return FabricLoader.getInstance().getConfigDir().resolve("nosclient-map").resolve(server).resolve(dim);
	}

	/** Đổi thế giới/chiều: lưu hết vùng cũ, bỏ texture. */
	private static void ensureWorld(Minecraft mc) {
		String k = mc.level == null ? "" : Waypoints.serverKey(mc) + "|" + mc.level.dimension().identifier();
		if (k.equals(worldKey)) return;
		flush(true);
		for (Region r : regions.values()) close(r);
		regions.clear();
		// KHÔNG xoá hàng đợi chunk: chunk quanh chỗ xuất hiện nạp TRƯỚC khi có người chơi/khoá thế giới, xoá là thủng
		// một lỗ đen đúng chỗ mình đứng. Chunk của chiều cũ còn sót thì drawChunk thấy không nạp và bỏ qua.
		worldKey = k;
	}

	private static void close(Region r) {
		Minecraft.getInstance().getTextureManager().release(r.id);
	}

	/** Vùng chứa (rx, rz); nạp từ đĩa nếu có. create=false thì trả null khi chưa có trên đĩa lẫn bộ nhớ. */
	public static Region region(int rx, int rz, boolean create) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		ensureWorld(mc);
		Region r = regions.get(key(rx, rz));
		if (r == null) {
			Path f = dir(mc).resolve("r." + rx + "." + rz + ".png");
			boolean onDisk = Files.isRegularFile(f);
			if (!create && !onDisk) return null;
			r = new Region(rx, rz);
			if (onDisk) try (var in = Files.newInputStream(f)) {
				NativeImage img = NativeImage.read(in);
				if (img.getWidth() == R && img.getHeight() == R) r.tex.getPixels().copyFrom(img);
				img.close();
				r.dirty = true;
			} catch (IOException | RuntimeException e) {
				LOG.debug("Bỏ qua ô bản đồ hỏng {}", f, e);
			}
			regions.put(key(rx, rz), r);
		}
		r.lastUse = System.currentTimeMillis();
		return r;
	}

	public static void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		if (!enabled()) return;
		ChunkPos p = chunk.getPos();
		pending.add(key(p.x, p.z));
	}

	public static boolean enabled() {
		var c = dev.jun.nosclient.scene.SceneConfig.INSTANCE;
		return c.minimap;
	}

	/** Mỗi tick: vẽ tối đa 24 chunk vừa nạp + 9 chunk quanh người (bắt khối đổi), upload vùng bẩn, lưu đĩa mỗi 30 giây. */
	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			if (!worldKey.isEmpty()) ensureWorld(mc);
			return;
		}
		if (!enabled()) return;
		ensureWorld(mc);
		for (int n = 0; n < 24 && !pending.isEmpty(); n++) {
			long k = pending.poll();
			drawChunk(mc.level, (int) (k >> 32), (int) k);
		}
		ChunkPos me = mc.player.chunkPosition();
		int t = (int) (mc.level.getGameTime() % 9); // một chunk quanh người mỗi tick, đủ 3×3 sau 9 tick
		drawChunk(mc.level, me.x - 1 + t % 3, me.z - 1 + t / 3);
		for (Region r : regions.values()) if (r.dirty) {
			r.tex.upload();
			r.dirty = false;
		}
		long now = System.currentTimeMillis();
		if (now - lastFlush > 30_000) {
			flush(false);
			// vùng lâu không dùng (đi xa) thì trả texture, khỏi phình bộ nhớ (mỗi vùng 1 MiB)
			for (Iterator<Region> it = regions.values().iterator(); it.hasNext(); ) {
				Region r = it.next();
				if (now - r.lastUse > 120_000 && !r.unsaved) {
					close(r);
					it.remove();
				}
			}
		}
	}

	/** Ghi các vùng đã đổi ra đĩa. force: cả khi vừa ghi. */
	public static void flush(boolean force) {
		Minecraft mc = Minecraft.getInstance();
		lastFlush = System.currentTimeMillis();
		if (worldKey.isEmpty() || mc.level == null && !force) return;
		Path d = null;
		for (Region r : regions.values()) {
			if (!r.unsaved) continue;
			try {
				if (d == null) {
					if (mc.level == null) return; // đã rời thế giới: không biết thư mục, bỏ (lần lưu 30 giây trước vẫn còn)
					d = dir(mc);
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

	private static final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

	private static void drawChunk(ClientLevel lvl, int cx, int cz) {
		LevelChunk ch = lvl.getChunkSource().getChunk(cx, cz, false);
		if (ch == null) return;
		Region r = region(Math.floorDiv(cx, RC), Math.floorDiv(cz, RC), true);
		NativeImage img = r.tex.getPixels();
		int ox = Math.floorMod(cx, RC) * 16, oz = Math.floorMod(cz, RC) * 16;
		boolean ceiling = lvl.dimensionType().hasCeiling();
		int bx = cx << 4, bz = cz << 4;
		int[] north = new int[16];
		for (int x = 0; x < 16; x++) north[x] = surface(lvl, ch, bx + x, bz - 1, ceiling);
		for (int z = 0; z < 16; z++)
			for (int x = 0; x < 16; x++) {
				int wx = bx + x, wz = bz + z, h = surface(lvl, ch, wx, wz, ceiling);
				int col = 0;
				if (h > Integer.MIN_VALUE) {
					var st = lvl.getBlockState(mp.set(wx, h, wz));
					FluidState fl = st.getFluidState();
					if (!fl.isEmpty() && fl.is(net.minecraft.tags.FluidTags.WATER)) {
						int depth = 1; // nước: càng sâu càng đậm, như bản đồ giấy
						while (depth < 12 && !lvl.getBlockState(mp.set(wx, h - depth, wz)).getFluidState().isEmpty()) depth++;
						MapColor.Brightness b = depth > 7 ? MapColor.Brightness.LOWEST : depth > 3 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
						col = MapColor.WATER.calculateARGBColor(b);
					} else {
						MapColor mcol = st.getMapColor(lvl, mp);
						int hn = north[x];
						MapColor.Brightness b = hn == Integer.MIN_VALUE || h == hn ? MapColor.Brightness.NORMAL : h > hn ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
						col = mcol == MapColor.NONE ? 0 : mcol.calculateARGBColor(b);
					}
				}
				north[x] = h;
				int abgr = col == 0 ? 0 : 0xFF000000 | (col & 0xFF) << 16 | (col & 0xFF00) | (col >> 16 & 0xFF);
				img.setPixelABGR(ox + x, oz + z, abgr);
			}
		r.dirty = true;
		r.unsaved = true;
	}

	/** Y của khối mặt; ngoài chunk đã nạp thì MIN_VALUE. Chiều có trần: dò từ Y=100 xuống khối rắn đầu tiên dưới khoảng trống. */
	private static int surface(ClientLevel lvl, LevelChunk ch, int x, int z, boolean ceiling) {
		LevelChunk c = (x >> 4) == ch.getPos().x && (z >> 4) == ch.getPos().z ? ch : lvl.getChunkSource().getChunk(x >> 4, z >> 4, false);
		if (c == null) return Integer.MIN_VALUE;
		if (!ceiling) return c.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) - 1; // heightmap = ô trống ngay trên khối mặt
		int y = Math.min(100, lvl.getMaxY());
		while (y > lvl.getMinY() && !c.getBlockState(mp.set(x, y, z)).isAir()) y--; // ra khỏi đá trần
		while (y > lvl.getMinY() && c.getBlockState(mp.set(x, y, z)).isAir()) y--;
		return y;
	}
}
