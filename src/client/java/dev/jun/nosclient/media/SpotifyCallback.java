package dev.jun.nosclient.media;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Chỉ nhận callback đúng đường dẫn và state; giới hạn thời gian kể cả khi có request lạ. */
final class SpotifyCallback {
    private SpotifyCallback() {}

    static String await(ServerSocket server, String state, long deadline, BooleanSupplier cancelled)
            throws IOException {
        while (System.nanoTime() < deadline) {
            if (cancelled.getAsBoolean())
                throw new CancellationException("Spotify login cancelled");
            long remaining = Math.max(1, (deadline - System.nanoTime()) / 1_000_000);
            server.setSoTimeout((int) Math.min(500, remaining));
            try (Socket socket = server.accept()) {
                socket.setSoTimeout((int) Math.min(2000, remaining));
                String line = readLine(socket.getInputStream());
                String[] request = line.split(" ");
                if (request.length != 3 || !request[0].equals("GET")) {
                    reply(socket, 400, "Invalid request");
                    continue;
                }
                Map<String, String> query;
                try {
                    URI target = URI.create(request[1]);
                    if (!"/callback".equals(target.getPath())) {
                        reply(socket, 404, "Not found");
                        continue;
                    }
                    query = query(target.getRawQuery());
                } catch (IllegalArgumentException error) {
                    reply(socket, 400, "Invalid callback");
                    continue;
                }
                if (!state.equals(query.get("state"))) {
                    reply(socket, 400, "Wrong state; retry from Nos Client");
                    continue;
                }
                if (query.containsKey("error")) {
                    reply(
                            socket,
                            200,
                            "Spotify login was cancelled. Return to Nos Client to try again.");
                    throw new IOException("Spotify authorization denied");
                }
                String code = query.get("code");
                if (code == null || code.isBlank()) {
                    reply(socket, 400, "Missing authorization code");
                    continue;
                }
                if (cancelled.getAsBoolean())
                    throw new CancellationException("Spotify login cancelled");
                reply(socket, 200, "Authorization received. You can return to Nos Client.");
                return code;
            } catch (SocketTimeoutException timeout) {
                // Kiểm lại hạn chờ chung và trạng thái hủy thay vì gia hạn theo từng kết nối.
            }
        }
        throw new SocketTimeoutException("Spotify callback timed out");
    }

    private static String readLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int next; (next = input.read()) != -1 && next != '\n'; ) {
            if (line.length() >= 8192) throw new IOException("Spotify callback request too large");
            if (next != '\r') line.append((char) next);
        }
        return line.toString();
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> result = new HashMap<>();
        if (raw == null) return result;
        for (String part : raw.split("&")) {
            int at = part.indexOf('=');
            if (at < 1) continue;
            String key = URLDecoder.decode(part.substring(0, at), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(part.substring(at + 1), StandardCharsets.UTF_8);
            if (result.putIfAbsent(key, value) != null)
                throw new IllegalArgumentException("Duplicate callback parameter");
        }
        return result;
    }

    private static void reply(Socket socket, int status, String text) throws IOException {
        byte[] body =
                ("<!doctype html><meta charset=utf-8><title>Nos Client</title><p>" + text + "</p>")
                        .getBytes(StandardCharsets.UTF_8);
        String reason = status == 200 ? "OK" : status == 404 ? "Not Found" : "Bad Request";
        var output = socket.getOutputStream();
        output.write(
                ("HTTP/1.1 "
                                + status
                                + " "
                                + reason
                                + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                                + body.length
                                + "\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }
}
