package dev.jun.nosclient.scene;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Lựa chọn của bảng "Cảnh nền": lưu ở config/nosclient.properties. */
public final class SceneConfig {
	private static final Logger LOG = LoggerFactory.getLogger("nosclient");
	public static final SceneConfig INSTANCE = load();

	public boolean blossom = true, cane = true, fx = true;
	/** 40 = hoàng hôn … 100 = đêm (thang của preview; ban ngày đã bỏ theo yêu cầu). */
	public double tod = 47;

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("nosclient.properties");
	}

	private static SceneConfig load() {
		SceneConfig c = new SceneConfig();
		Path f = file();
		if (!Files.exists(f)) return c;
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(f)) {
			p.load(r);
			c.blossom = Boolean.parseBoolean(p.getProperty("blossom", "true"));
			c.cane = Boolean.parseBoolean(p.getProperty("cane", "true"));
			c.fx = Boolean.parseBoolean(p.getProperty("shader", "true"));
			c.tod = Math.min(100, Math.max(40, Double.parseDouble(p.getProperty("tod", "47"))));
		} catch (IOException | NumberFormatException e) {
			LOG.warn("Không đọc được {}, dùng mặc định", f, e);
		}
		return c;
	}

	public void save() {
		Properties p = new Properties();
		p.setProperty("blossom", String.valueOf(blossom));
		p.setProperty("cane", String.valueOf(cane));
		p.setProperty("shader", String.valueOf(fx));
		p.setProperty("tod", String.valueOf(tod));
		try (Writer w = Files.newBufferedWriter(file())) {
			p.store(w, "Nos client - canh nen man chinh");
		} catch (IOException e) {
			LOG.warn("Không lưu được {}", file(), e);
		}
	}
}
