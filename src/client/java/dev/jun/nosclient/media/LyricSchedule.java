package dev.jun.nosclient.media;

import dev.jun.nosclient.media.lyrics.Lyrics;

import java.util.ArrayList;
import java.util.List;

/** Chia cụm và mốc sáng theo lời từng chữ; LRC thường vẫn dùng nhịp ước lượng của Nos. */
public final class LyricSchedule {
    private LyricSchedule() {}

    public record Phrase(String text, long start, long end, float[] letterStarts) {
        public float litAt(int index, int count) {
            return index < letterStarts.length
                    ? letterStarts[index]
                    : LyricMotion.litAt(index, count, (end - start) / 1000.0F);
        }
    }

    public static List<Phrase> phrases(Lyrics.Line line, boolean poster) {
        List<String> chunks = poster ? LyricChunks.split(line.text) : List.of(line.text);
        if (chunks.isEmpty()) return List.of();
        List<Phrase> result = new ArrayList<>();
        long[] fallback = LyricChunks.starts(chunks, Math.max(1, line.end - line.start));
        boolean timed =
                !line.estimated && line.words.size() == line.text.strip().split("\\s+").length;
        int firstWord = 0;
        for (int index = 0; index < chunks.size(); index++) {
            String text = chunks.get(index);
            int wordCount = text.strip().split("\\s+").length;
            int lastWord = firstWord + wordCount;
            long start = timed ? line.words.get(firstWord).start : line.start + fallback[index];
            long end =
                    timed
                            ? (lastWord < line.words.size()
                                    ? line.words.get(lastWord).start
                                    : line.end)
                            : line.start + fallback[index + 1];
            List<Float> cues = new ArrayList<>();
            if (timed) {
                for (int wordIndex = firstWord; wordIndex < lastWord; wordIndex++) {
                    Lyrics.Word word = line.words.get(wordIndex);
                    List<String> letters =
                            CelestialTypography.clusters(word.text).stream()
                                    .filter(s -> !s.isBlank())
                                    .toList();
                    for (int letter = 0; letter < letters.size(); letter++) {
                        cues.add(
                                (word.start
                                                - start
                                                + (word.end - word.start)
                                                        * letter
                                                        / (float) Math.max(1, letters.size()))
                                        / 1000.0F);
                    }
                }
            }
            float[] starts = new float[cues.size()];
            for (int letter = 0; letter < starts.length; letter++)
                starts[letter] = cues.get(letter);
            result.add(new Phrase(text, start, Math.max(start + 1, end), starts));
            firstWord = lastWord;
        }
        return List.copyOf(result);
    }
}
