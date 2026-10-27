package dev.jun.nosclient;

import dev.jun.nosclient.gui.Glass;
import dev.jun.nosclient.gui.UiOpacity;
import dev.jun.nosclient.theme.Theme;
import dev.jun.nosclient.theme.ThemePalette;
import dev.jun.nosclient.typography.FontCoverage;
import dev.jun.nosclient.typography.Typography;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription.Resource;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class Ui {
    public static int TEXT = -1183770;
    public static int MUTED = -5785681;
    public static int FAINT = -8679799;
    public static int ACCENT = -4927299;
    public static int ACCENT_HI = -2431272;
    public static int BRAND = -7225463;
    public static int ACC0 = -11107477;
    public static int ACC1 = -5845325;
    private static final String[] NAMES = new String[]{"ui", "ui_bold", "small", "big", "logo", "num", "hud", "hud_num", "sky"};
    private static final int TIERS = Typography.DENSITIES.length;
    private static final FontDescription[][] FONTS = new FontDescription[NAMES.length][TIERS];
    public static float hint;
    private static final Style[][] STYLES;
    private static final Style[] SKY_STYLES;
    private static final int UI = 0;
    private static final int UI_BOLD = 1;
    private static final int SMALL = 2;
    private static final int BIG = 3;
    private static final int LOGO = 4;
    private static final int NUM = 5;
    private static final int HUD = 6;
    private static final int HUD_NUM = 7;
    private static final int SKY = 8;
    private static final FontDescription FALLBACK;

    private Ui() {
    }

    private static Style style(int i) {
        return styleAt(i, Minecraft.getInstance().getWindow().getGuiScale() * hint);
    }

    private static Style styleAt(int i, double pixelsPerUnit) {
        int k2 = Typography.densityIndex(pixelsPerUnit);
        Style st = STYLES[i][k2];
        if (st == null) {
            st = STYLES[i][k2] = Style.EMPTY.withFont(FONTS[i][k2]);
        }

        return st;
    }

    private static MutableComponent fallback(MutableComponent text) {
        if (text.getString().codePoints().allMatch(cp -> Character.isWhitespace(cp) || FontCoverage.contains(cp))) {
            return text;
        }

        MutableComponent result = Component.empty().withStyle(text.getStyle());
        text.visit((span, value) -> {
            int start = 0;
            int i = 0;

            while (i < value.length()) {
                int cp = value.codePointAt(i);
                int end = i + Character.charCount(cp);
                if (!Character.isWhitespace(cp) && !FontCoverage.contains(cp)) {
                    if (start < i) {
                        result.append(Component.literal(value.substring(start, i)).withStyle(span));
                    }

                    result.append(Component.literal(value.substring(i, end)).withStyle(span.withFont(FALLBACK)));
                    start = end;
                }

                i = end;
            }

            if (start < value.length()) {
                result.append(Component.literal(value.substring(start)).withStyle(span));
            }

            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    public static MutableComponent ui(Component c) {
        return fallback(c.copy().withStyle(style(0)));
    }

    public static MutableComponent ui(String s) {
        return fallback(Component.literal(s).withStyle(style(0)));
    }

    public static MutableComponent worldText(String text, double pixelsPerUnit) {
        int index = Typography.worldDensityIndex(pixelsPerUnit);
        Style style = SKY_STYLES[index];
        if (style == null) {
            style = SKY_STYLES[index] = Style.EMPTY
                .withFont(new Resource(Identifier.fromNamespaceAndPath("nosclient", "sky_" + Typography.WORLD_DENSITIES[index])));
        }

        return fallback(Component.literal(text).withStyle(style));
    }

    public static MutableComponent hud(String s) {
        boolean numeric = !s.isBlank() && s.codePoints().allMatch(c -> Character.isDigit(c) || "−-+.,°%| :/".indexOf(c) >= 0);
        return fallback(Component.literal(s).withStyle(style(numeric ? 7 : 6)));
    }

    public static MutableComponent bold(Component c) {
        return fallback(c.copy().withStyle(style(1)));
    }

    public static MutableComponent small(Component c) {
        return fallback(c.copy().withStyle(style(2)));
    }

    public static MutableComponent big(Component c) {
        return fallback(c.copy().withStyle(style(3)));
    }

    public static MutableComponent logo(String s) {
        return fallback(Component.literal(s).withStyle(style(4)));
    }

    public static MutableComponent num(String s) {
        return fallback(Component.literal(s).withStyle(style(5)));
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static void text(GuiGraphics g, Component c, int x, int y, int col) {
        col = UiOpacity.tint(col);
        if (col >>> 24 > 3) {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            snapText(g);
            g.drawString(font(), c, 0, 0, col, false);
            g.pose().popMatrix();
        }
    }

    public static void snapText(GuiGraphics g) {
        if (!(Math.abs(g.pose().m01()) > 1.0E-4F) && !(Math.abs(g.pose().m10()) > 1.0E-4F)) {
            double scale = Minecraft.getInstance().getWindow().getGuiScale();
            g.pose().setTranslation((float)Typography.snap(g.pose().m20(), scale), (float)Typography.snap(g.pose().m21(), scale));
        }
    }

    public static int width(Component c) {
        return font().width(c);
    }

    public static Component fit(Component c, int maxW) {
        Font f = font();
        if (f.width(c) <= maxW) {
            return c;
        }

        FormattedText cut = f.substrByWidth(c, Math.max(0, maxW - f.width(Component.literal("…").withStyle(c.getStyle()))));
        return fallback(Component.literal(cut.getString() + "…").withStyle(c.getStyle()));
    }

    public static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        col = UiOpacity.tint(col);
        g.fill(x0, y0, x1, y0 + 1, col);
        g.fill(x0, y1 - 1, x1, y1, col);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, col);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, col);
    }

    public static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int tint) {
        Glass.panel(g, x0, y0, x1 - x0, y1 - y0, tint != 0 ? tint : -720367335, 1560281087);
    }

    public static int lerpCol(int a, int b, float k) {
        int r = 0;

        for (int s = 0; s < 32; s += 8) {
            r |= Math.round((a >>> s & 0xFF) + ((b >>> s & 0xFF) - (a >>> s & 0xFF)) * k) << s;
        }

        return r;
    }

    public static int alpha(int col, float a) {
        return Math.round((col >>> 24 & 0xFF) * a) << 24 | col & 16777215;
    }

    public static void toggle(GuiGraphics g, int x, int y, float k, int accent) {
        int w = 24;
        int h = 12;
        ThemePalette p = Theme.palette();
        // Viên thuốc bo tròn hẳn, núm tròn trượt qua; bật thì toả quầng accent nhẹ.
        if (k > 0.01F) {
            Glass.glow(g, x, y, w, h, alpha(p.accent() | 0xFF000000, 0.32F * k));
        }

        Glass.fill(g, x, y, w, h, lerpCol(p.raised(), p.accent(), k));
        Glass.outline(g, x, y, w, h, lerpCol(alpha(p.text(), 0.15F), alpha(p.accent(), 0.6F), k));
        int kx = Math.round(x + 2 + k * (w - 12));
        Glass.glow(g, kx, y + 2, 8, 8, alpha(0xFF000000, 0.35F));
        Glass.fill(g, kx, y + 2, 8, 8, lerpCol(p.muted(), p.ink(), k));
        Glass.sheen(g, kx, y + 2, 8, 8, alpha(0xFFFFFFFF, 0.25F));
    }

    public static void toggle(GuiGraphics g, int x, int y, float k) {
        toggle(g, x, y, k, ACC0);
    }

    public static void brackets(GuiGraphics g, int x0, int y0, int x1, int y1, int len, int col) {
        g.fill(x0, y0, x0 + len, y0 + 1, col);
        g.fill(x0, y0, x0 + 1, y0 + len, col);
        g.fill(x1 - len, y0, x1, y0 + 1, col);
        g.fill(x1 - 1, y0, x1, y0 + len, col);
        g.fill(x0, y1 - 1, x0 + len, y1, col);
        g.fill(x0, y1 - len, x0 + 1, y1, col);
        g.fill(x1 - len, y1 - 1, x1, y1, col);
        g.fill(x1 - 1, y1 - len, x1, y1, col);
    }

    public static float ease(float x) {
        return 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
    }

    public static float approach(float v, float target, float dt, float speed) {
        return v + (target - v) * (1.0F - (float)Math.exp(-dt * speed));
    }

    static {
        for (int i = 0; i < NAMES.length; i++) {
            for (int k = 0; k < TIERS; k++) {
                FONTS[i][k] = new Resource(Identifier.fromNamespaceAndPath("nosclient", NAMES[i] + "_" + Typography.DENSITIES[k]));
            }
        }

        hint = 1.0F;
        STYLES = new Style[NAMES.length][TIERS];
        SKY_STYLES = new Style[Typography.WORLD_DENSITIES.length];
        FALLBACK = new Resource(Identifier.withDefaultNamespace("default"));
    }
}
