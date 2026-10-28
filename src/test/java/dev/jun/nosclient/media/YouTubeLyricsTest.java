package dev.jun.nosclient.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tiêu đề + tên kênh lấy từ YouTube thật (28/10/2026); bản thử 0.3.1 không tìm được lời cho các bài Việt này. */
class YouTubeLyricsTest {
	private static final String LRC = "[00:01.00]la";

	private static String entry(String artist, String track, double seconds) {
		return "{\"artistName\":\"" + artist + "\",\"trackName\":\"" + track + "\",\"duration\":" + seconds + ",\"instrumental\":false,\"syncedLyrics\":\"" + LRC + "\"}";
	}

	private static String pick(String title, String channel, long ms, String... entries) {
		JsonArray results = JsonParser.parseString("[" + String.join(",", entries) + "]").getAsJsonArray();
		return LyricsLookup.pick(results, LyricsLookup.Video.of(title, channel), ms, 8000L);
	}

	@Test void browserIsDetectedFromMprisBusAndWindowsAppId() {
		assertEquals(MediaSource.BROWSER, MediaSource.detect("org.mpris.MediaPlayer2.chromium.instance1924332"));
		assertEquals(MediaSource.BROWSER, MediaSource.detect("MSEdge"));
		assertEquals(MediaSource.SPOTIFY, MediaSource.detect("org.mpris.MediaPlayer2.spotify"));
		assertEquals(MediaSource.UNKNOWN, MediaSource.detect("org.mpris.MediaPlayer2.vlc"));
		String chromium = "({'Metadata': <{'xesam:title': <'Song'>, 'xesam:artist': <['Kênh']>}>, 'PlaybackStatus': <'Playing'>},)";
		assertEquals(MediaSource.BROWSER, LinuxMedia.parse(chromium, "org.mpris.MediaPlayer2.chromium.instance1", 1).source());
		assertEquals(MediaSource.BROWSER, WindowsMedia.parse("{\"title\":\"a\",\"duration_ms\":1,\"position_ms\":0,\"playing\":true,\"rate\":1,\"source\":\"Chrome\"}", 1).source());
	}

	@Test void vietnameseVideoTitlesSplitIntoSongAndSinger() {
		assertEquals(List.of("SƠN TÙNG M-TP", "CHÚNG TA CỦA TƯƠNG LAI"), LyricsLookup.Video.of("SƠN TÙNG M-TP | CHÚNG TA CỦA TƯƠNG LAI | OFFICIAL MUSIC VIDEO", "Sơn Tùng M-TP Official").parts());
		assertEquals(List.of("HIEUTHUHAI", "Không Thể Say"), LyricsLookup.Video.of("HIEUTHUHAI - Không Thể Say (prod. by Kewtiie) l Official Video", "HIEUTHUHAI").parts());
		assertEquals(List.of("Dù Cho Mai Về Sau", "buitruonglinh"), LyricsLookup.Video.of("Dù Cho Mai Về Sau (Official Music Video) / buitruonglinh", "buitruonglinh").parts());
		assertEquals(List.of("Đen", "Bài Này Chill Phết"), LyricsLookup.Video.of("Đen ft. MIN - Bài Này Chill Phết (M/V)", "Đen Vâu Official").parts());
		assertEquals(List.of("LẠ LÙNG"), LyricsLookup.Video.of("LẠ LÙNG (Strange) / Vũ. (Original)", "Vũ Official").parts().subList(0, 1));
		assertEquals("Sơn Tùng M-TP", LyricsLookup.Video.of("x", "Sơn Tùng M-TP Official").channel());
	}

	@Test void picksMatchingSongInEitherOrderAndRejectsWrongSongs() {
		assertEquals(LRC, pick("SƠN TÙNG M-TP | CHÚNG TA CỦA TƯƠNG LAI | OFFICIAL MUSIC VIDEO", "Sơn Tùng M-TP Official", 277_000,
			entry("Sơn Tùng M-TP", "Chúng Ta Của Tương Lai", 277)));
		assertEquals(LRC, pick("Vì Anh Đâu Có Biết - Madihu (Feat. Vũ.) | Official MV", "Madihu", 242_000,
			entry("Madihu & Vũ.", "Vì Anh Đâu Có Biết", 242)));
		assertEquals(LRC, pick("Bài Này Chill Phết", "Đen Vâu Official", 276_000, entry("Đen", "Bài Này Chill Phết", 276)));
		// Sai bài, sai ca sĩ, lệch thời lượng, bản remix khi video là bản gốc: đều bỏ.
		assertEquals("", pick("Ed Sheeran - Shape of You (Official Music Video)", "Ed Sheeran", 264_000, entry("Ed Sheeran", "Perfect", 264)));
		assertEquals("", pick("Ed Sheeran - Shape of You (Lyrics)", "7clouds", 232_000, entry("Someone Else", "Shape of You", 232)));
		assertEquals("", pick("Madihu - Vì Anh Đâu Có Biết (Solo Version)", "Madihu", 242_000, entry("Madihu", "Vì Anh Đâu Có Biết", 300)));
		assertEquals("", pick("MONO - Waiting For You (Official Music Video)", "Mono Official", 266_000, entry("MONO", "Waiting For You (Remix)", 266)));
	}

	@Test void closestDurationWinsBecauseMvIntrosShiftTheTimeline() {
		String near = "{\"artistName\":\"Vũ.\",\"trackName\":\"Lạ Lùng\",\"duration\":262,\"syncedLyrics\":\"[00:02.00]gần\"}";
		assertEquals("[00:02.00]gần", pick("LẠ LÙNG (Strange) / Vũ. (Original)", "Vũ Official", 262_000, entry("Vũ.", "Lạ Lùng", 268), near));
	}
}
