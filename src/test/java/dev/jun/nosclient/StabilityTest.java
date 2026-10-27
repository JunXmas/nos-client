package dev.jun.nosclient;

import dev.jun.nosclient.map.ChunkQueue;
import dev.jun.nosclient.scene.ConfigValues;
import dev.jun.nosclient.storage.ClientFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StabilityTest {
	@TempDir Path directory;

	@Test void settingsRoundTripUnicodeAndReplacePreviousFile() throws IOException {
		Path path = directory.resolve("nested/config.properties");
		ClientFiles.write(path, "name=Thế giới của bạn\n");
		assertEquals("name=Thế giới của bạn\n", ClientFiles.read(path));
		ClientFiles.write(path, "name=Second\n");
		assertEquals("name=Second\n", ClientFiles.read(path));
		try (var files = Files.list(path.getParent())) { assertEquals(1, files.count()); }
	}

	@Test void oversizedWritePreservesExistingConfigurationAndDoesNotLeavePartialFile() throws IOException {
		Path path = directory.resolve("config.properties");
		ClientFiles.write(path, "hud.coords=false\n");
		assertThrows(IOException.class, () -> ClientFiles.write(path, "x".repeat(ClientFiles.MAX_CONFIG_BYTES + 1)));
		assertEquals("hud.coords=false\n", ClientFiles.read(path));
		try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
	}

	@Test void fileReadHasHardSizeBound() throws IOException {
		Path path = directory.resolve("invalid.properties");
		Files.write(path, new byte[ClientFiles.MAX_CONFIG_BYTES + 1]);
		assertThrows(IOException.class, () -> ClientFiles.read(path));
	}

	@Test void failedReplaceCleansTemporaryFileWithoutTouchingDestination() throws IOException {
		Path target = directory.resolve("occupied");
		Files.createDirectory(target);
		Files.writeString(target.resolve("keep"), "original");
		assertThrows(IOException.class, () -> ClientFiles.write(target, "new"));
		assertEquals("original", Files.readString(target.resolve("keep")));
		try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
	}

	@Test void nonFiniteNumbersAndInvalidBooleansNeverBecomeRenderSettings() {
		for (String value : new String[]{"NaN", "Infinity", "-Infinity", "1e500", "broken"})
			assertThrows(IllegalArgumentException.class, () -> ConfigValues.number(value));
		assertThrows(IllegalArgumentException.class, () -> ConfigValues.bool("not-a-boolean"));
		assertTrue(ConfigValues.bool(" TRUE "));
		assertFalse(ConfigValues.bool("false"));
		assertEquals(.8, ConfigValues.number(" 0.8 "));
		assertEquals(.8, ConfigValues.bounded(Double.NaN, .5, 1.5, .8));
		assertEquals(.8, ConfigValues.bounded(Double.POSITIVE_INFINITY, .5, 1.5, .8));
		assertEquals(1.5, ConfigValues.bounded(200, .5, 1.5, .8));
		assertEquals(.5, ConfigValues.bounded(-100, .5, 1.5, .8));
	}

	@Test void chunkLoadBurstsAreBoundedAndDuplicatesKeepMostRecentWorld() {
		var queue = new ChunkQueue<String>(3);
		queue.add(1, "old-world"); queue.add(2, "old-world");
		queue.add(1, "new-world");
		assertEquals(2, queue.size());
		assertEquals(new ChunkQueue.Entry<>(2L, "old-world"), queue.poll());
		assertEquals(new ChunkQueue.Entry<>(1L, "new-world"), queue.poll());
		assertNull(queue.poll());
		for (int n = 0; n < 10000; n++) queue.add(n, "world");
		assertEquals(3, queue.size());
		assertEquals(9997, queue.poll().coordinate());
		queue.clear();
		assertNull(queue.poll());
	}
}
