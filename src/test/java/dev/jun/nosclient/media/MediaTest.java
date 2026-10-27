package dev.jun.nosclient.media;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaTest {
	@Test void lrcSupportsRepeatedTimestampsOffsetsAndSeekingBothDirections() {
		var timeline = LrcTimeline.parse("[ar:Test]\n[offset:250]\n[00:10.50][00:30.500]Original line\n[00:20.0]Second line\n[00:22.000]\n[00:99.000]invalid\n");
		assertEquals(4, timeline.lines().size());
		assertEquals("", timeline.textAt(10_749));
		assertEquals("Original line", timeline.textAt(10_750));
		assertEquals("Second line", timeline.textAt(20_250));
		assertEquals("", timeline.textAt(22_250));
		assertEquals("Original line", timeline.textAt(35_000));
		assertEquals("Second line", timeline.textAt(21_000));
	}
	@Test void plainOrOversizedLyricsDoNotBecomeFabricatedSyncedLyrics() {
		assertTrue(LrcTimeline.parse("Just a line without timing").lines().isEmpty());
		assertTrue(LrcTimeline.parse("[00:01.0]".repeat(40000)).lines().isEmpty());
	}
	@Test void mediaClockHandlesPauseRateSeekAndTrackEnd() {
		var track = new MediaTrack("Song", "Artist", 20_000, 5000, true, 2, 1_000_000_000);
		assertEquals(9000, track.position(3_000_000_000L));
		assertEquals(20_000, track.position(90_000_000_000L));
		assertEquals(5000, new MediaTrack("Song", "Artist", 20_000, 5000, false, 1, 1).position(90_000_000_000L));
		assertNotEquals(track.key(), new MediaTrack("Song", "Artist", 30_000, 0, true, 1, 0).key());
	}
	@Test void linuxParsesPublicGVariantMetadataWithoutAssumingAsciiNames() {
		String metadata = "({'PlaybackStatus': <'Playing'>, 'Position': <int64 3500000>, 'Metadata': <{'xesam:title': <\"Trời xanh's song\">, 'xesam:artist': <['Nostalgia']>, 'mpris:length': <int64 120000000>}>},)";
		var track = LinuxMedia.parse(metadata, 42);
		assertEquals("Trời xanh's song", track.title()); assertEquals("Nostalgia", track.artist());
		assertTrue(track.playing()); assertEquals(3500, track.positionMs()); assertEquals(120000, track.durationMs());
	}
	@Test void windowsParsesSessionSnapshotAndEmptySessionFailsClosed() {
		var track = WindowsMedia.parse("{\"title\":\"Sky\",\"artist\":\"Nos\",\"duration_ms\":10000,\"position_ms\":2500,\"playing\":false,\"rate\":1}", 42);
		assertEquals("Sky", track.title()); assertEquals(2500, track.position(500000000)); assertFalse(track.playing());
		assertEquals(MediaTrack.NONE, WindowsMedia.parse("{}", 42));
	}
	@Test void browserTitlesHaveOneConservativeFallbackAndUnterminatedVariantsStayBounded() {
		var queries = LyricsLookup.queries(new MediaTrack("Artist - Song (Official Music Video)", "Artist - Topic", 120_000, 0, true, 1, 0));
		assertEquals(2, queries.size());
		assertEquals(new LyricsLookup.Query("Song", "Artist"), queries.get(1));
		assertEquals(1, LyricsLookup.queries(new MediaTrack("Song", "Artist", 0, 0, false, 1, 0)).size());
		assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
			assertEquals("", LinuxMedia.parse("{'xesam:title': <'" + "\\\\".repeat(20000), 1).title());
		});
		assertEquals(2, LinuxMedia.parse("{'Rate': <2.0>}", 1).rate());
	}
	@Test void systemQueryOutputAndChildLifetimeAreBounded() throws Exception {
		String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
		String classes = Path.of(MediaTest.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
		assertEquals("ready", MediaCommand.run(List.of(java, "-cp", classes, Child.class.getName(), "ready")));
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThrows(Exception.class, () -> MediaCommand.run(List.of(java, "-cp", classes, Child.class.getName(), "sleep"))));
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThrows(Exception.class, () -> MediaCommand.run(List.of(java, "-cp", classes, Child.class.getName(), "spam"))));
	}
	public static class Child {
		public static void main(String[] args) throws Exception {
			if (args[0].equals("sleep")) Thread.sleep(20_000);
			else if (args[0].equals("spam")) System.out.print("x".repeat(100000));
			else System.out.print("ready");
		}
	}
}
