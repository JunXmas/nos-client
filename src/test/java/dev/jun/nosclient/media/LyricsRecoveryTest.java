package dev.jun.nosclient.media;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LyricsRecoveryTest {
	@TempDir Path directory;
	static MediaTrack track(String name, long position) { return new MediaTrack(name, "Nos", 180_000, position, true, 1, System.nanoTime()); }
	static String lrc(String name) { return "[00:00.00]" + name + "\n[00:04.00]Next line"; }
	static void until(BooleanSupplier ready) {
		assertTimeoutPreemptively(Duration.ofSeconds(3), () -> { while (!ready.getAsBoolean()) Thread.sleep(2); });
	}
	static void uninterruptible(CountDownLatch latch) { boolean interrupted = false; for (;;) try { latch.await(); break; } catch (InterruptedException e) { interrupted = true; } if (interrupted) Thread.currentThread().interrupt(); }

	@Test void slowLookupCannotStopMediaPollingOrReplaceTheFourthTracksLyrics() throws Exception {
		var current = new AtomicReference<>(track("One", 1000));
		var began = new CountDownLatch(1); var release = new CountDownLatch(1);
		try (var service = new LyricsService(directory, () -> current::get, song -> {
			if (song.title().equals("One")) { began.countDown(); uninterruptible(release); }
			return lrc(song.title());
		}, System::nanoTime, false)) {
			service.configure(true, true); service.poll(); assertTrue(began.await(2, TimeUnit.SECONDS));
			for (String title : new String[]{"Two", "Three", "Four"}) {
				current.set(track(title, 2000)); assertTimeout(Duration.ofMillis(200), service::poll);
				assertEquals(title, service.snapshot().track().title()); assertEquals("loading", service.snapshot().status());
			}
			current.set(track("Four", 12000)); service.poll(); release.countDown(); until(() -> service.snapshot().status().equals("ready"));
			assertEquals("Four", service.snapshot().timeline().textAt(0)); assertEquals(12000, service.snapshot().track().positionMs());
			assertFalse(Files.exists(directory.resolve(LyricsService.fileName(track("One", 0)))));
			for (String title : new String[]{"Five", "Six", "Seven", "Eight"}) {
				current.set(track(title, 100)); service.poll(); until(() -> service.snapshot().status().equals("ready"));
				assertEquals(title, service.snapshot().timeline().textAt(0));
			}
		} finally { release.countDown(); }
	}
	@Test void transientLookupFailureRetriesWithoutRestartAndThenUsesDiskCache() {
		var now = new AtomicLong(1_000_000_000); var calls = new AtomicInteger(); var song = track("Retry", 0);
		try (var service = new LyricsService(directory, () -> () -> song, t -> { if (calls.incrementAndGet() == 1) throw new IOException("offline"); return lrc(t.title()); }, now::get, false)) {
			service.configure(true, true); service.poll(); until(() -> service.snapshot().status().equals("unavailable"));
			now.set(9_000_000_000L); service.poll(); assertEquals(1, calls.get());
			now.set(12_000_000_000L); service.poll(); until(() -> service.snapshot().status().equals("ready")); assertEquals(2, calls.get());
			service.configure(false, true); service.poll(); service.configure(true, true); service.poll();
			assertEquals("ready", service.snapshot().status()); assertEquals(2, calls.get());
		}
	}
	@Test void repeatedProviderErrorsRecreateProviderAndResumeCurrentSong() {
		var providers = new AtomicInteger(); var closes = new AtomicInteger();
		try (var service = new LyricsService(directory, () -> providers.incrementAndGet() == 1 ? new SystemMedia() {
			public MediaTrack poll() throws Exception { throw new IOException("stale provider"); }
			public void close() { closes.incrementAndGet(); }
		} : () -> track("Recovered", 0), t -> lrc(t.title()), System::nanoTime, false)) {
			service.configure(true, true); service.poll(); service.poll(); service.poll(); until(() -> service.snapshot().status().equals("ready"));
			assertEquals("Recovered", service.snapshot().timeline().textAt(0)); assertEquals(2, providers.get()); assertEquals(1, closes.get());
		}
	}
	@Test void disablingDuringDownloadRejectsLateCompletionAndCanBeEnabledAgain() throws Exception {
		var began = new CountDownLatch(1); var release = new CountDownLatch(1); var finished = new AtomicBoolean(); var calls = new AtomicInteger();
		try (var service = new LyricsService(directory, () -> () -> track("Toggle", 0), t -> {
			if (calls.incrementAndGet() == 1) { began.countDown(); uninterruptible(release); finished.set(true); } return lrc(t.title());
		}, System::nanoTime, false)) {
			service.configure(true, true); service.poll(); assertTrue(began.await(2, TimeUnit.SECONDS));
			service.configure(false, true); service.poll(); release.countDown(); until(finished::get);
			assertEquals("disabled", service.snapshot().status()); assertFalse(Files.exists(directory.resolve(LyricsService.fileName(track("Toggle", 0)))));
			service.configure(true, true); service.poll(); until(() -> service.snapshot().status().equals("ready"));
			assertEquals("Toggle", service.snapshot().timeline().textAt(0));
		} finally { release.countDown(); }
	}
	@Test void importedLyricsWinOverAnOlderInFlightDownloadOnDiskAndInMemory() throws Exception {
		var began = new CountDownLatch(1); var release = new CountDownLatch(1); var finished = new AtomicBoolean();
		var song = track("Imported", 0); Path source = directory.resolve("manual.lrc"); Files.writeString(source, lrc("Manual"));
		try (var service = new LyricsService(directory, () -> () -> song, t -> {
			began.countDown(); uninterruptible(release); finished.set(true); return lrc("Old download");
		}, System::nanoTime, false)) {
			service.configure(true, true); service.poll(); assertTrue(began.await(2, TimeUnit.SECONDS));
			service.importLrc(source); until(() -> service.snapshot().status().equals("ready"));
			release.countDown(); until(finished::get); service.poll();
			assertEquals("Manual", service.snapshot().timeline().textAt(0));
			assertEquals(lrc("Manual"), Files.readString(directory.resolve(LyricsService.fileName(song))));
		} finally { release.countDown(); }
	}
	@Test void stalledWindowsHelperIsReplacedAndOldReaderCannotClearNewSession() throws Exception {
		var clock = new AtomicLong(1_000_000_000); var starts = new AtomicInteger();
		var first = new FakeProcess(); var second = new FakeProcess();
		try (var media = new WindowsMedia(() -> starts.incrementAndGet() == 1 ? first : second, clock::get)) {
			assertEquals(MediaTrack.NONE, media.poll());
			clock.set(12_000_000_000L); media.poll(); assertFalse(first.isAlive()); assertEquals(2, starts.get());
			second.emit("{\"title\":\"Recovered\",\"artist\":\"Nos\",\"duration_ms\":180000,\"position_ms\":0,\"playing\":true,\"rate\":1}");
			until(() -> { try { return media.poll().title().equals("Recovered"); } catch (Exception e) { throw new AssertionError(e); } });
			for (int i = 0; i < 20; i++) assertEquals("Recovered", media.poll().title());
		}
		assertFalse(second.isAlive());
	}
	static class FakeProcess extends Process {
		private final PipedInputStream input = new PipedInputStream();
		private final PipedOutputStream writer;
		private volatile boolean alive = true;
		FakeProcess() throws IOException { writer = new PipedOutputStream(input); }
		void emit(String line) throws IOException { writer.write((line + "\n").getBytes(StandardCharsets.UTF_8)); writer.flush(); }
		public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
		public InputStream getInputStream() { return input; }
		public InputStream getErrorStream() { return InputStream.nullInputStream(); }
		public int waitFor() { return 0; }
		public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 0; }
		public boolean isAlive() { return alive; }
		public void destroy() { alive = false; try { writer.close(); } catch (IOException ignored) {} }
		public Process destroyForcibly() { destroy(); return this; }
	}
}
