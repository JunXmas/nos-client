package dev.jun.nosclient.media;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

class LyricsRecoveryTest {
    @TempDir Path directory;

    static Track track(String title) {
        return new Track(title, "Nos", "", 180000, 12000, true, "Fixture");
    }

    static String lrc(String title) {
        return "[00:00.00]" + title + "\n[00:04.00]Next line";
    }

    static void until(BooleanSupplier ready) {
        assertTimeoutPreemptively(
                Duration.ofSeconds(3),
                () -> {
                    while (!ready.getAsBoolean()) Thread.sleep(2);
                });
    }

    static void uninterruptible(CountDownLatch latch) {
        boolean interrupted = false;
        for (; ; )
            try {
                latch.await();
                break;
            } catch (InterruptedException error) {
                interrupted = true;
            }
        if (interrupted) Thread.currentThread().interrupt();
    }

    static void awaitWorker(LyricsService service) throws Exception {
        var field = LyricsService.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(service)).submit(() -> {}).get(2, TimeUnit.SECONDS);
    }

    @Test
    void slowLookupCannotStopSamplingOrReplaceTheFourthTrack() throws Exception {
        CountDownLatch began = new CountDownLatch(1), release = new CountDownLatch(1);
        try (LyricsService service =
                new LyricsService(
                        directory,
                        song -> {
                            if (song.title.equals("One")) {
                                began.countDown();
                                uninterruptible(release);
                            }
                            return LyricsService.parse(lrc(song.title), song.durationMs);
                        },
                        System::nanoTime)) {
            service.poll(track("One"), true, true);
            assertTrue(began.await(2, TimeUnit.SECONDS));
            for (String title : new String[] {"Two", "Three", "Four"}) {
                assertTimeout(Duration.ofMillis(200), () -> service.poll(track(title), true, true));
                assertEquals(title, service.snapshot().track().title);
            }
            release.countDown();
            until(() -> service.snapshot().status().equals("ready"));
            assertEquals("Four", service.snapshot().lyrics().lines.getFirst().text);
            assertEquals(12000, service.snapshot().track().positionMs);
            assertFalse(Files.exists(directory.resolve(LyricsService.fileName(track("One")))));
        } finally {
            release.countDown();
        }
    }

    @Test
    void transientFailureRetriesWithoutRestart() {
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        AtomicInteger calls = new AtomicInteger();
        try (LyricsService service =
                new LyricsService(
                        directory,
                        song -> {
                            if (calls.incrementAndGet() == 1) throw new IOException("offline");
                            return LyricsService.parse(lrc("Retry"), song.durationMs);
                        },
                        clock::get)) {
            service.poll(track("Retry"), true, true);
            until(() -> service.snapshot().status().equals("unavailable"));
            clock.set(9_000_000_000L);
            service.poll(track("Retry"), true, true);
            assertEquals(1, calls.get());
            clock.set(12_000_000_000L);
            service.poll(track("Retry"), true, true);
            until(() -> service.snapshot().status().equals("ready"));
            assertEquals(2, calls.get());
        }
    }

    @Test
    void disableRejectsLateCompletionAndCanBeEnabledAgain() throws Exception {
        CountDownLatch began = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (LyricsService service =
                new LyricsService(
                        directory,
                        song -> {
                            if (calls.incrementAndGet() == 1) {
                                began.countDown();
                                uninterruptible(release);
                            }
                            return LyricsService.parse(lrc("Toggle"), song.durationMs);
                        },
                        System::nanoTime)) {
            service.poll(track("Toggle"), true, true);
            assertTrue(began.await(2, TimeUnit.SECONDS));
            service.poll(null, false, true);
            release.countDown();
            awaitWorker(service);
            assertEquals("disabled", service.snapshot().status());
            service.poll(track("Toggle"), true, true);
            until(() -> service.snapshot().status().equals("ready"));
            assertEquals("Toggle", service.snapshot().lyrics().lines.getFirst().text);
        } finally {
            release.countDown();
        }
    }

    @Test
    void importedLyricsWinOverDownloadOnDiskAndInMemory() throws Exception {
        CountDownLatch began = new CountDownLatch(1), release = new CountDownLatch(1);
        Path source = directory.resolve("manual.lrc");
        Files.writeString(source, lrc("Manual"));
        try (LyricsService service =
                new LyricsService(
                        directory,
                        song -> {
                            began.countDown();
                            uninterruptible(release);
                            return LyricsService.parse(lrc("Old download"), song.durationMs);
                        },
                        System::nanoTime)) {
            Track song = track("Imported");
            service.poll(song, true, true);
            assertTrue(began.await(2, TimeUnit.SECONDS));
            service.importLrc(source);
            until(() -> service.snapshot().status().equals("ready"));
            release.countDown();
            awaitWorker(service);
            service.poll(song, true, true);
            assertEquals("Manual", service.snapshot().lyrics().lines.getFirst().text);
            assertEquals(
                    lrc("Manual"),
                    Files.readString(directory.resolve(LyricsService.fileName(song))));
        } finally {
            release.countDown();
        }
    }

    @Test
    void refetchReplacesDownloadedCacheButPreservesManualLyrics() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Track song = track("Refresh");
        try (LyricsService service =
                new LyricsService(
                        directory,
                        current ->
                                LyricsService.parse(
                                        lrc("Remote " + calls.incrementAndGet()),
                                        current.durationMs),
                        System::nanoTime)) {
            service.poll(song, true, true);
            until(() -> service.snapshot().status().equals("ready"));
            service.refetch();
            service.poll(song, true, true);
            until(() -> calls.get() == 2 && service.snapshot().status().equals("ready"));
            assertEquals("Remote 2", service.snapshot().lyrics().lines.getFirst().text);
            Path source = directory.resolve("manual.lrc");
            Files.writeString(source, lrc("Manual"));
            service.importLrc(source);
            until(() -> service.snapshot().lyrics().lines.getFirst().text.equals("Manual"));
            service.refetch();
            service.poll(song, true, true);
            assertEquals("Manual", service.snapshot().lyrics().lines.getFirst().text);
            assertEquals(2, calls.get());
        }
    }

    @Test
    void unreadableLocalFileCannotReuseThePreviousSongsLyrics() throws Exception {
        try (LyricsService service =
                new LyricsService(
                        directory,
                        current -> LyricsService.parse(lrc(current.title), current.durationMs),
                        System::nanoTime)) {
            service.poll(track("Previous"), true, true);
            until(() -> service.snapshot().status().equals("ready"));
            Track next = track("Next");
            Files.writeString(directory.resolve(LyricsService.fileName(next)), "x".repeat(262145));
            service.poll(next, true, true);
            assertEquals("Next", service.snapshot().track().title);
            assertNull(service.snapshot().lyrics());
        }
    }
}
