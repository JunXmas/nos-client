package dev.jun.nosclient.media;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Đọc GVariant có giới hạn, giữ đúng dấu nháy và Unicode của tiêu đề. */
final class MprisMetadata {
    private MprisMetadata() {}

    static MediaTrack parse(String text, long now) {
        return parse(text, "", now);
    }

    static MediaTrack parse(String text, String player, long now) {
        String title = string(text, "xesam:title");
        String artist = string(text, "xesam:artist");
        return new MediaTrack(
                title,
                artist,
                number(text, "mpris:length") / 1000L,
                number(text, "Position") / 1000L,
                "Playing".equals(string(text, "PlaybackStatus")),
                rate(text),
                now,
                MediaSource.detect(player));
    }

    static String string(String data, String key) {
        Matcher matcher =
                Pattern.compile(
                                "['\"]"
                                        + Pattern.quote(key)
                                        + "['\"]\\s*:\\s*<\\s*(?:\\[\\s*)?(['\"])")
                        .matcher(data);
        if (!matcher.find()) {
            return "";
        }

        char quote = matcher.group(1).charAt(0);
        StringBuilder value = new StringBuilder();

        for (int i = matcher.end(); i < data.length(); i++) {
            char character = data.charAt(i);
            if (character == quote) {
                return value.toString();
            }

            if (character == '\\' && i + 1 < data.length()) {
                character = data.charAt(++i);

                character =
                        switch (character) {
                            case 'n' -> '\n';
                            case 'r' -> '\r';
                            case 't' -> '\t';
                            default -> character;
                        };
            }

            value.append(character);
        }

        return "";
    }

    private static double rate(String data) {
        Matcher matcher = Pattern.compile("['\"]Rate['\"]\\s*:\\s*<([0-9.]+)>").matcher(data);

        try {
            return matcher.find() ? Double.parseDouble(matcher.group(1)) : 1.0;
        } catch (NumberFormatException error) {
            return 1.0;
        }
    }

    static long number(String data, String key) {
        Matcher matcher =
                Pattern.compile(
                                "['\"]"
                                        + Pattern.quote(key)
                                        + "['\"]\\s*:\\s*<(?:int64 |uint64 |int32 )?(-?\\d+)")
                        .matcher(data);

        try {
            return matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
        } catch (NumberFormatException error) {
            return 0L;
        }
    }
}
