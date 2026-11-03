package dev.jun.nosclient.media;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class SpotifyCallbackTest {
    private static String request(ServerSocket server, String path) throws Exception {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
            socket.setSoTimeout(2000);
            socket.getOutputStream()
                    .write(
                            ("GET " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n")
                                    .getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void wrongStatePathAndDuplicateParametersCannotFinishLogin() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
                var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var result =
                    worker.submit(
                            () ->
                                    SpotifyCallback.await(
                                            server,
                                            "expected",
                                            System.nanoTime() + 5_000_000_000L,
                                            () -> false));
            assertTrue(
                    request(server, "/callback?state=wrong&code=ignored")
                            .startsWith("HTTP/1.1 400"));
            assertTrue(
                    request(server, "/callback-evil?state=expected&code=ignored")
                            .startsWith("HTTP/1.1 404"));
            assertTrue(
                    request(server, "/callback?state=wrong&state=expected&code=ignored")
                            .startsWith("HTTP/1.1 400"));
            assertFalse(result.isDone());
            assertTrue(
                    request(server, "/callback?state=expected&code=chosen%2Bcode")
                            .startsWith("HTTP/1.1 200"));
            assertEquals("chosen+code", result.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void loginHasAnOverallDeadlineAndCanBeCancelled() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress())) {
            assertThrows(
                    SocketTimeoutException.class,
                    () ->
                            SpotifyCallback.await(
                                    server,
                                    "expected",
                                    System.nanoTime() + 30_000_000,
                                    () -> false));
        }
        AtomicBoolean cancelled = new AtomicBoolean();
        try (ServerSocket server = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
                var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var result =
                    worker.submit(
                            () ->
                                    SpotifyCallback.await(
                                            server,
                                            "expected",
                                            System.nanoTime() + 5_000_000_000L,
                                            cancelled::get));
            cancelled.set(true);
            ExecutionException failure =
                    assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertInstanceOf(java.util.concurrent.CancellationException.class, failure.getCause());
        }
    }
}
