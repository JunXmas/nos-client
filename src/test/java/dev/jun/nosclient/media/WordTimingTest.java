package dev.jun.nosclient.media;

import static org.junit.jupiter.api.Assertions.*;

import dev.jun.nosclient.media.lyrics.Lyrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class WordTimingTest {
    @TempDir Path directory;

    static Lyrics lyrics() {
        return new Lyrics(
                List.of(new long[] {1000, 8000}),
                List.of("one two three four"),
                true,
                "words",
                9000,
                List.of(
                        List.of(
                                Lyrics.Word.of("one", 1000, 1500),
                                Lyrics.Word.of("two", 4000, 4300),
                                Lyrics.Word.of("three", 4500, 5000),
                                Lyrics.Word.of("four", 7000, 7500))));
    }

    @Test
    void skyWordsFollowTheirActualCuesInsteadOfDividingTheLineEvenly() {
        var phrases = LyricSchedule.phrases(lyrics().lines.getFirst(), false);
        assertEquals(1, phrases.size());
        assertEquals(0, phrases.getFirst().litAt(0, 15), 0.001);
        assertEquals(3, phrases.getFirst().litAt(3, 15), 0.001);
        var chunks = LyricSchedule.phrases(lyrics().lines.getFirst(), true);
        assertEquals("one two", chunks.getFirst().text());
        assertEquals(4500, chunks.get(1).start());
        assertEquals(2.5, chunks.get(1).litAt(5, 9), 0.001);
    }

    @Test
    void diskCachePreservesWordTimesAndDuration() {
        LyricsCache cache = new LyricsCache(directory);
        Track track = new Track("Test", "Nos", "", 9000, 0, true, "Fixture");
        cache.writeCache(track, lyrics());
        Lyrics restored = cache.readCache(track);
        assertNotNull(restored);
        assertTrue(restored.wordSynced);
        assertEquals(4000, restored.lines.getFirst().words.get(1).start);
        assertEquals(7500, restored.lines.getFirst().words.get(3).end);
        assertEquals(lyrics().lines.getFirst().text, restored.lines.getFirst().text);
    }

    @Test
    void plainLrcUsesStableChunksAndInvalidLrcIsRejected() {
        assertNull(LyricsService.parse("plain untimed text", 9000));
        Lyrics lrc = LyricsService.parse("[00:01.00]one two three four\n[00:08.00]next", 9000);
        assertNotNull(lrc);
        var phrases = LyricSchedule.phrases(lrc.lines.getFirst(), true);
        assertFalse(phrases.isEmpty());
        assertEquals(1000, phrases.getFirst().start());
        assertTrue(phrases.getLast().end() > phrases.getLast().start());
    }

    @Test
    void corruptCacheIsRejectedWithoutCrashingOrInventingWordTimes() throws Exception {
        LyricsCache cache = new LyricsCache(directory);
        Track track = new Track("Test", "Nos", "", 9000, 0, true, "Fixture");
        for (String json :
                List.of(
                        "{\"synced\":true,\"texts\":[\"one\"],\"starts\":[],\"ends\":[9000]}",
                        "{\"synced\":true,\"texts\":[\"one\"],\"starts\":[-1],\"ends\":[9000]}",
                        "{\"synced\":true,\"texts\":[\"one\"],\"starts\":[1000],\"ends\":[9000],\"words\":[[\"one\"]]}",
                        "{\"synced\":true,\"texts\":null}")) {
            Files.writeString(cache.cacheFile(track), json);
            assertNull(cache.readCache(track));
        }
        cache.writeCache(track, lyrics());
        assertTrue(cache.readCache(track).wordSynced);
    }
}
