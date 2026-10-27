package dev.jun.nosclient.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Flow.Subscription;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class LyricsLookup {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3L)).followRedirects(Redirect.NEVER).build();

    private LyricsLookup() {
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    static List<LyricsLookup.Query> queries(MediaTrack track) {
        LyricsLookup.Query exact = new LyricsLookup.Query(track.title(), track.artist());
        String title = track.title()
            .replaceFirst("(?i)\\s*[-|]?\\s*[\\[(](?:official\\s+(?:music\\s+)?(?:video|audio)|lyrics?|visuali[sz]er|audio)[\\])]\\s*$", "")
            .strip();
        String artist = track.artist().replaceFirst("(?i)\\s*-\\s*Topic$", "").strip();
        int dash = title.indexOf(" - ");
        if (dash > 0 && dash < title.length() - 3) {
            artist = title.substring(0, dash).strip();
            title = title.substring(dash + 3).strip();
        }

        LyricsLookup.Query normalized = new LyricsLookup.Query(title, artist);
        return !normalized.equals(exact) && !title.isBlank() ? List.of(exact, normalized) : List.of(exact);
    }

    static String fetch(MediaTrack track) throws Exception {
        long deadline = System.nanoTime() + 10000000000L;

        for (LyricsLookup.Query query : queries(track)) {
            String lrc = fetch(query, track.durationMs(), deadline);
            if (!lrc.isBlank()) {
                return lrc;
            }
        }

        return "";
    }

    private static String fetch(LyricsLookup.Query track, long durationMs, long deadline) throws Exception {
        String query = "track_name=" + encode(track.title()) + "&artist_name=" + encode(track.artist());
        if (durationMs > 0L) {
            query = query + "&duration=" + Math.round(durationMs / 1000.0);
        }

        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
            throw new IOException("Lyrics lookup timed out");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://lrclib.net/api/get?" + query))
            .timeout(Duration.ofSeconds(8L))
            .header("Accept", "application/json")
            .header("User-Agent", "NosClient/0.2 (synced-lyrics)")
            .build();
        LyricsLookup.LimitedBody body = new LyricsLookup.LimitedBody();
        CompletableFuture<HttpResponse<byte[]>> future = HTTP.sendAsync(request, _info -> body);

        try {
            HttpResponse<byte[]> response = future.get(remaining, TimeUnit.NANOSECONDS);
            if (response.statusCode() == 404) {
                return "";
            } else if (response.statusCode() != 200) {
                throw new IOException("Lyrics unavailable");
            } else {
                JsonObject data = JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (data.has("syncedLyrics") && !data.get("syncedLyrics").isJsonNull()) {
                    String lrc = data.get("syncedLyrics").getAsString();
                    return lrc.length() <= 262144 ? lrc : "";
                } else {
                    return "";
                }
            }
        } finally {
            body.cancel();
            future.cancel(true);
        }
    }

    @Environment(EnvType.CLIENT)
    private static final class LimitedBody implements BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private volatile Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return this.result;
        }

        @Override
        public void onSubscribe(Subscription value) {
            this.subscription = value;
            value.request(1L);
        }

        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (this.output.size() + buffer.remaining() > 524288) {
                    this.onError(new IOException("Lyrics response too large"));
                    this.cancel();
                    return;
                }

                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                this.output.writeBytes(bytes);
            }

            this.subscription.request(1L);
        }

        @Override
        public void onError(Throwable error) {
            this.result.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            this.result.complete(this.output.toByteArray());
        }

        void cancel() {
            if (this.subscription != null) {
                this.subscription.cancel();
            }
        }
    }

    @Environment(EnvType.CLIENT)
    record Query(String title, String artist) {
    }
}
