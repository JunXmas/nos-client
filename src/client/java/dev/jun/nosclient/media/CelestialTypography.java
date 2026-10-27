package dev.jun.nosclient.media;

import dev.jun.nosclient.theme.ThemePalette;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class CelestialTypography {
    private CelestialTypography() {
    }

    public static List<String> clusters(String value) {
        String text = Normalizer.normalize(value, Form.NFC);
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        List<String> parts = new ArrayList<>();
        int start = iterator.first();

        for (int end = iterator.next(); end != -1; end = iterator.next()) {
            parts.add(text.substring(start, end));
            start = end;
        }

        return List.copyOf(parts);
    }

    public static double width(String line, ToDoubleFunction<String> measure, double tracking) {
        List<String> parts = clusters(line);
        return parts.stream().mapToDouble(measure).sum() + Math.max(0, parts.size() - 1) * tracking;
    }

    public static List<String> wrap(String text, ToDoubleFunction<String> measure, double tracking, double maximum) {
        maximum = Math.max(40.0, maximum);
        tracking = Math.max(0.0, Math.min(2.0, tracking));
        List<String> lines = new ArrayList<>();
        String current = "";

        for (String word : Normalizer.normalize(text, Form.NFC).strip().split("\\s+")) {
            if (!word.isEmpty()) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (width(candidate, measure, tracking) <= maximum) {
                    current = candidate;
                } else {
                    if (!current.isEmpty()) {
                        lines.add(current);
                        current = "";
                    }

                    for (String part : clusters(word)) {
                        if (!current.isEmpty() && width(current + part, measure, tracking) > maximum) {
                            lines.add(current);
                            current = "";
                        }

                        current = current + part;
                    }
                }
            }
        }

        if (!current.isEmpty()) {
            lines.add(current);
        }

        for (int i = lines.size() - 2; i >= 0; i--) {
            String before = lines.get(i);
            String after = lines.get(i + 1);

            for (int space = before.lastIndexOf(32); space > 0; space = before.lastIndexOf(32)) {
                String shorter = before.substring(0, space);
                String longer = before.substring(space + 1) + " " + after;
                double previousDifference = Math.abs(width(before, measure, tracking) - width(after, measure, tracking));
                if (width(longer, measure, tracking) > maximum
                    || Math.abs(width(shorter, measure, tracking) - width(longer, measure, tracking)) >= previousDifference) {
                    break;
                }

                before = shorter;
                after = longer;
            }

            lines.set(i, before);
            lines.set(i + 1, after);
        }

        return List.copyOf(lines);
    }

    public static CelestialTypography.Ink ink(int sky, int themeText, int accent, boolean blend) {
        float day = (float)Math.clamp((ThemePalette.luminance(sky) - 0.03) / 0.3, 0.0, 1.0);
        int body = ThemePalette.mix(themeText, -197121, 0.96);
        int edge = ThemePalette.readable(-15656921, body, 7.0);
        int halo = blend ? ThemePalette.mix(accent, ThemePalette.mix(sky, body, 0.7), 0.45) : ThemePalette.mix(accent, body, 0.35);
        return new CelestialTypography.Ink(body, edge, halo, 0.32F + 0.56F * day, 0.1F - 0.04F * day);
    }

    public static double wave(double x, double halfWidth, double seconds) {
        double normal = halfWidth <= 0.0 ? 0.0 : x / halfWidth;
        return -0.3 * normal * normal + Math.sin(seconds * 0.65 + x * 0.035) * 0.08;
    }

    @Environment(EnvType.CLIENT)
    public record Ink(int body, int edge, int halo, float edgeOpacity, float haloOpacity) {
    }
}
