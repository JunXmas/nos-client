package dev.jun.nosclient.util;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeoutException;

public final class Http {
    public static final String USER_AGENT =
            "NosClient/0.4.2 (https://github.com/JunXmas/nos-client)";
    private static final HttpClient CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(4L))
                    .followRedirects(Redirect.NORMAL)
                    .build();
    public static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();

    private Http() {}

    public static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    public static String query(Map<String, String> params) {
        StringJoiner j = new StringJoiner("&");
        params.forEach(
                (k, v) -> {
                    if (v != null) {
                        j.add(enc(k) + "=" + enc(v));
                    }
                });
        return j.toString();
    }

    public static Http.Response get(String url, Map<String, String> headers) throws Exception {
        long ms = 8000L;
        Long dl = DEADLINE.get();
        if (dl != null) {
            ms = Math.min(ms, dl - System.currentTimeMillis());
            if (ms < 300L) {
                throw new TimeoutException("lyrics search time is up");
            }
        }

        Builder b =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMillis(ms))
                        .GET()
                        .header("User-Agent", USER_AGENT);
        if (headers != null) {
            headers.forEach(b::header);
        }

        HttpResponse<byte[]> r =
                CLIENT.send(b.build(), ignored -> new LimitedBody(2 * 1024 * 1024));
        return new Http.Response(r.statusCode(), new String(r.body(), StandardCharsets.UTF_8));
    }

    public static byte[] getBytes(String url) throws Exception {
        HttpRequest req =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10L))
                        .GET()
                        .header("User-Agent", USER_AGENT)
                        .build();
        HttpResponse<byte[]> r = CLIENT.send(req, ignored -> new LimitedBody(8 * 1024 * 1024));
        return r.statusCode() == 200 ? r.body() : null;
    }

    public static Http.Response send(
            String method, String url, Map<String, String> headers, String form) throws Exception {
        BodyPublisher body = form == null ? BodyPublishers.noBody() : BodyPublishers.ofString(form);
        Builder b =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10L))
                        .method(method, body)
                        .header("User-Agent", USER_AGENT);
        if (form != null) {
            b.header("Content-Type", "application/x-www-form-urlencoded");
        }

        if (headers != null) {
            headers.forEach(b::header);
        }

        HttpResponse<byte[]> r =
                CLIENT.send(b.build(), ignored -> new LimitedBody(2 * 1024 * 1024));
        return new Http.Response(r.statusCode(), new String(r.body(), StandardCharsets.UTF_8));
    }

    public record Response(int code, String body) {}
}
