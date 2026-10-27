package dev.jun.nosclient.media;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record LrcTimeline(List<LrcTimeline.Line> lines) {
    public static final LrcTimeline EMPTY = new LrcTimeline(List.of());
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,4}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d{1,8})]", 2);

    public LrcTimeline {
        lines = List.copyOf(lines);
    }

    public static LrcTimeline parse(String source) {
        if (source != null && source.length() <= 262144) {
            long offset = 0L;
            Matcher offsetTag = OFFSET.matcher(source);
            if (offsetTag.find()) {
                offset = Long.parseLong(offsetTag.group(1));
            }

            TreeMap<Long, String> ordered = new TreeMap<>();

            for (String row : source.split("\\R", 10001)) {
                Matcher matcher = TIME.matcher(row);
                ArrayList<Long> stamps = new ArrayList<>();
                int last = 0;

                while (matcher.find() && stamps.size() < 64) {
                    int seconds = Integer.parseInt(matcher.group(2));
                    if (seconds < 60) {
                        String fraction = matcher.group(3);
                        long ms = fraction == null ? 0L : Integer.parseInt((fraction + "000").substring(0, 3));
                        stamps.add(Long.parseLong(matcher.group(1)) * 60000L + seconds * 1000L + ms + offset);
                        last = matcher.end();
                    }
                }

                String text = row.substring(last).replaceAll("[\\p{Cntrl}]", " ").strip();
                if (text.codePointCount(0, text.length()) > 180) {
                    text = text.substring(0, text.offsetByCodePoints(0, 180));
                }

                for (long stamp : stamps) {
                    ordered.put(Math.max(0L, stamp), text);
                }

                if (ordered.size() > 10000) {
                    return EMPTY;
                }
            }

            return new LrcTimeline(ordered.entrySet().stream().map(e -> new LrcTimeline.Line(e.getKey(), e.getValue())).toList());
        } else {
            return EMPTY;
        }
    }

    public int indexAt(long positionMs) {
        int low = 0;
        int high = this.lines.size() - 1;
        int result = -1;

        while (low <= high) {
            int mid = low + high >>> 1;
            if (this.lines.get(mid).atMs <= positionMs) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return result;
    }

    public String textAt(long positionMs) {
        int i = this.indexAt(positionMs);
        return i < 0 ? "" : this.lines.get(i).text;
    }

    @Environment(EnvType.CLIENT)
    public record Line(long atMs, String text) {
    }
}
