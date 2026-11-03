package dev.jun.nosclient.media;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TitleCleaner {
    private static final Pattern BRACKETS =
            Pattern.compile("\\s*[\\(\\[\\{【][^\\)\\]\\}】]*[\\)\\]\\}】]");
    private static final Pattern NOISE =
            Pattern.compile(
                    "(?i)\\b(official\\s*(music\\s*)?(video|audio|lyric(s)?\\s*video|visuali[sz]er)|lyrics?\\s*video|lyrics?|audio|video\\s*clip|clip\\s*officiel|hd|hq|4k|remaster(ed)?(\\s*\\d{4})?|sped\\s*up|slowed(\\s*\\+?\\s*reverb)?|nightcore|8d\\s*audio|extended|radio\\s*edit|bass\\s*boosted)\\b");
    private static final Pattern FEAT =
            Pattern.compile(
                    "(?i)\\s*[\\(\\[]?\\s*\\b(feat\\.?|ft\\.?|featuring|with)\\s+[^\\)\\]\\-]*[\\)\\]]?");

    private TitleCleaner() {}

    public static String clean(String s) {
        if (s == null) {
            return "";
        }

        String r = BRACKETS.matcher(s).replaceAll(" ");
        r = NOISE.matcher(r).replaceAll(" ");
        r = r.replaceAll("\\s+-\\s*$", "").replaceAll("\\s{2,}", " ").trim();
        return r.isEmpty() ? s.trim() : r;
    }

    public static String mainArtist(String a) {
        if (a == null) {
            return "";
        }

        String r = a.split("(?i)\\s*(,|&|;|\\bx\\b|\\bfeat\\.?|\\bft\\.?|\\bwith\\b|/)\\s*")[0];
        return r.trim().isEmpty() ? a.trim() : r.trim();
    }

    public static String fold(String s) {
        if (s == null) {
            return "";
        }

        String r = s.toLowerCase(Locale.ROOT).replace('ı', 'i').replace("İ", "i");
        return Normalizer.normalize(r, Form.NFD).replaceAll("\\p{M}+", "");
    }

    public static List<String[]> variants(String artist, String title) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String[]> out = new ArrayList<>();
        String[][] cands =
                new String[][] {
                    {artist, title},
                    {artist, FEAT.matcher(title).replaceAll(" ").trim()},
                    {artist, clean(title)},
                    {mainArtist(artist), clean(FEAT.matcher(title).replaceAll(" "))},
                    {mainArtist(artist), clean(title).replaceAll("\\s+-\\s+.*$", "")}
                };
        String ft = clean(FEAT.matcher(title).replaceAll(" "));
        String folded =
                Normalizer.normalize(ft.replace('ı', 'i').replace('İ', 'I'), Form.NFD)
                        .replaceAll("\\p{M}+", "");
        String foldedArtist =
                Normalizer.normalize(
                                mainArtist(artist).replace('ı', 'i').replace('İ', 'I'), Form.NFD)
                        .replaceAll("\\p{M}+", "");
        cands = Arrays.copyOf(cands, cands.length + 1);
        cands[cands.length - 1] = new String[] {foldedArtist, folded};

        for (String[] c : cands) {
            String a = c[0].trim();
            String t = c[1].replaceAll("\\s{2,}", " ").trim();
            if (!t.isEmpty()) {
                String k = (a + "|" + t).toLowerCase(Locale.ROOT);
                if (seen.add(k)) {
                    out.add(new String[] {a, t});
                }
            }
        }

        return out;
    }
}
