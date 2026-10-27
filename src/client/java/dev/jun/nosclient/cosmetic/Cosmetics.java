package dev.jun.nosclient.cosmetic;

import com.google.gson.JsonParser;
import dev.jun.nosclient.gui.CosmeticScreen;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.LoggerFactory;

/** Bundled model library and explicit self-only try-on; no ownership or payment state. */
public final class Cosmetics {
	public static final int MENU_PREVIEW_ID = Integer.MIN_VALUE + 31;
	private static final long STARTED = System.nanoTime();
	private static Map<String, CosmeticMesh> models = Map.of();
	private static CosmeticLook applied = CosmeticLook.NONE;
	private Cosmetics() {}
	/**
	 * Tạm tắt cho bản phát hành (jun, 28/10): cosmetic mới là mặc thử cục bộ, chưa có sở hữu/đồng bộ. Tắt thì không
	 * đăng ký gì cả — không lớp vẽ, không nạp model, không ẩn cape vanilla, không nút vào. Bật lại: đổi thành true.
	 */
	public static final boolean ENABLED = false;
	public static void init() {
		if (!ENABLED) return;
		CosmeticMaterials.init();
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override public Identifier getFabricId() { return Identifier.fromNamespaceAndPath("nosclient", "cosmetics"); }
			@Override public void onResourceManagerReload(ResourceManager manager) { Cosmetics.reload(manager); }
		});
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) helper.register(new CosmeticLayer(avatar));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> applied = CosmeticLook.NONE);
	}
	private static void reload(ResourceManager manager) {
		Map<String, CosmeticMesh> loaded = new HashMap<>();
		for (String theme : new String[]{"angel", "crimson"}) for (String part : new String[]{"wings", "cape", "headband"}) {
			Identifier id = Identifier.fromNamespaceAndPath("nosclient", "cosmetics/" + theme + "/" + part + ".json");
			try (var stream = manager.getResourceOrThrow(id).open(); var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				loaded.put(theme + "/" + part, new CosmeticMesh(JsonParser.parseReader(reader).getAsJsonObject()));
			} catch (Exception error) { LoggerFactory.getLogger("NosCosmetics").warn("Unable to load cosmetic {}", id, error); }
		}
		models = Map.copyOf(loaded);
	}
	public static boolean ready() { return models.size() == 6; }
	public static double seconds() { return (System.nanoTime() - STARTED) / 1_000_000_000.0; }
	public static CosmeticMesh model(String theme, String slot) { return models.get(theme + "/" + slot); }
	public static CosmeticLook applied() { return applied; }
	public static void tryOn(CosmeticLook look) { applied = look; }
	public static CosmeticLook look(AvatarRenderState state) {
		Minecraft mc = Minecraft.getInstance();
		if (state.id == MENU_PREVIEW_ID && mc.screen instanceof CosmeticScreen screen) return screen.draft();
		if (mc.player == null || state.id != mc.player.getId()) return CosmeticLook.NONE;
		return mc.screen instanceof CosmeticScreen screen ? screen.draft() : applied;
	}
}
