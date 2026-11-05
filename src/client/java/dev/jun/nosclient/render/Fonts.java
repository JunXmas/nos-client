package dev.jun.nosclient.render;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.typography.Typography;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription.Resource;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Fonts {
    public static final float SIZE = 16.0F;
    public static final List<Fonts.LyricFont> LYRICS =
            List.of(
                    new Fonts.LyricFont("nos-celestial", "Nos Celestial"),
                    new Fonts.LyricFont("be-vietnam", "Be Vietnam Pro"),
                    new Fonts.LyricFont("outfit", "Outfit"),
                    new Fonts.LyricFont("syne", "Syne"),
                    new Fonts.LyricFont("nosclient_script", "Nos Client Script"),
                    new Fonts.LyricFont("kaushan", "Kaushan Script"),
                    new Fonts.LyricFont("dancing", "Dancing Script"),
                    new Fonts.LyricFont("pacifico", "Pacifico"),
                    new Fonts.LyricFont("yellowtail", "Yellowtail"),
                    new Fonts.LyricFont("sacramento", "Sacramento"),
                    new Fonts.LyricFont("greatvibes", "Great Vibes"),
                    new Fonts.LyricFont("allura", "Allura"),
                    new Fonts.LyricFont("parisienne", "Parisienne"),
                    new Fonts.LyricFont("alexbrush", "Alex Brush"),
                    new Fonts.LyricFont("damion", "Damion"),
                    new Fonts.LyricFont("marck", "Marck Script"),
                    new Fonts.LyricFont("courgette", "Courgette"),
                    new Fonts.LyricFont("norican", "Norican"),
                    new Fonts.LyricFont("grandhotel", "Grand Hotel"),
                    new Fonts.LyricFont("yesteryear", "Yesteryear"),
                    new Fonts.LyricFont("quicksand", "Quicksand"),
                    new Fonts.LyricFont("poppins", "Poppins"),
                    new Fonts.LyricFont("outfit_bold", "Outfit Bold"),
                    new Fonts.LyricFont("minecraft", "Minecraft"));
    public static final List<Fonts.LyricFont> UI =
            List.of(
                    new Fonts.LyricFont("outfit", "Outfit"),
                    new Fonts.LyricFont("syne", "Syne"),
                    new Fonts.LyricFont("quicksand", "Quicksand"),
                    new Fonts.LyricFont("minecraft", "Minecraft"));
    public static final String UI_REGULAR = "outfit";
    public static final String UI_BOLD = "outfit_bold";
    private static final Map<String, MutableComponent> TEXT_CACHE = new HashMap<>();
    private static final Map<String, Float> WIDTH_CACHE = new HashMap<>();
    private static final Map<String, Float> SHIFT =
            Map.ofEntries(
                    Map.entry("alexbrush", 0.35F),
                    Map.entry("be-vietnam", 0.36F),
                    Map.entry("allura", 0.285F),
                    Map.entry("courgette", 0.379F),
                    Map.entry("damion", 0.326F),
                    Map.entry("dancing", 0.326F),
                    Map.entry("grandhotel", 0.348F),
                    Map.entry("greatvibes", 0.354F),
                    Map.entry("kaushan", 0.367F),
                    Map.entry("nosclient_script", 0.367F),
                    Map.entry("marck", 0.315F),
                    Map.entry("norican", 0.35F),
                    Map.entry("outfit", 0.357F),
                    Map.entry("outfit_bold", 0.36F),
                    Map.entry("poppins", 0.378F),
                    Map.entry("pacifico", 0.399F),
                    Map.entry("parisienne", 0.337F),
                    Map.entry("quicksand", 0.37F),
                    Map.entry("sacramento", 0.325F),
                    Map.entry("syne", 0.349F),
                    Map.entry("yellowtail", 0.354F),
                    Map.entry("yesteryear", 0.329F));

    public static List<Fonts.LyricFont> lyricsAll() {
        List<Fonts.LyricFont> l = new ArrayList<>(LYRICS);
        l.addAll(CustomFonts.list());
        return l;
    }

    private Fonts() {}

    public static Fonts.LyricFont lyric(String id) {
        for (Fonts.LyricFont f : lyricsAll()) {
            if (f.id.equals(id)) {
                return f;
            }
        }

        return LYRICS.get(0);
    }

    public static Style style(String id) {
        if (id != null && !id.equals("minecraft")) {
            Identifier fid = ClientResources.id(id.equals("nos-celestial") ? "sky_4" : id);
            return Style.EMPTY.withFont(new Resource(fid));
        } else {
            return Style.EMPTY;
        }
    }

    public static MutableComponent text(String s, String fontId) {
        s = Normalizer.normalize(s, Normalizer.Form.NFC);
        if (fontId != null && fontId.startsWith("ui_")) {
            return dev.jun.nosclient.Ui.uiText(
                    s,
                    fontId.startsWith("ui_bold"),
                    Minecraft.getInstance().getWindow().getGuiScale());
        }
        String key = fontId + "\u0000" + s;
        MutableComponent c = TEXT_CACHE.get(key);
        if (c == null) {
            if (TEXT_CACHE.size() > 3000) {
                TEXT_CACHE.clear();
            }

            c = Component.literal(s).withStyle(style(fontId));
            TEXT_CACHE.put(key, c);
        }

        return c;
    }

    /** Chọn atlas theo mật độ pixel; giữ nguyên kích thước và metrics của font đang chọn. */
    public static MutableComponent rasterText(String s, String fontId, double pixelsPerUnit) {
        if ("nos-celestial".equals(fontId)) {
            int density = Typography.WORLD_DENSITIES[Typography.worldDensityIndex(pixelsPerUnit)];
            return Ui.fallback(text(s, "sky_" + density));
        }
        if (fontId == null || fontId.equals("minecraft") || fontId.startsWith("custom_")) {
            return text(s, fontId);
        }
        for (LyricFont choice : LYRICS) {
            if (choice.id.equals(fontId)) return text(s, fontId + "_lyrics");
        }
        return text(s, fontId);
    }

    public static float unitWidth(String s, String fontId, Font font) {
        if (fontId != null && fontId.startsWith("ui_"))
            return font.getSplitter().stringWidth(text(s, fontId)) / 7;
        String key = fontId + "\u0000" + s;
        Float w = WIDTH_CACHE.get(key);
        if (w == null) {
            if (WIDTH_CACHE.size() > 3000) {
                WIDTH_CACHE.clear();
            }

            w = font.getSplitter().stringWidth(text(s, fontId)) / height(fontId);
            WIDTH_CACHE.put(key, w);
        }

        return w;
    }

    public static void clearCaches() {
        TEXT_CACHE.clear();
        WIDTH_CACHE.clear();
    }

    public static boolean isRtl(String s) {
        if (s == null) {
            return false;
        }

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 1424 && ch <= 2303 || ch >= 'יִ' && ch <= '﷿' || ch >= 'ﹰ' && ch <= '\ufeff') {
                return true;
            }
        }

        return false;
    }

    public static float yShift(String id) {
        if (id != null && id.startsWith("ui_")) return 0;
        if (id != null && !id.equals("minecraft")) {
            Float f = SHIFT.get(id);
            if (f == null) {
                f = CustomFonts.shift(id);
            }

            return f == null ? 0.35F : f;
        } else {
            return 0.0F;
        }
    }

    public static float height(String id) {
        if ("nos-celestial".equals(id)) return 8.0F;
        if (id != null && id.startsWith("ui_")) return 7.0F;
        return id != null && !id.equals("minecraft") ? 16.0F : 9.0F;
    }

    public record LyricFont(String id, String name) {}
}
