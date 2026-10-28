package dev.jun.nosclient.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Flow.Subscription;
import java.util.regex.Pattern;
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
        if (track.title().isBlank()) {
            return "";
        }

        long deadline = System.nanoTime() + 12000000000L;
        // Trình phát nhạc đưa đúng tên bài/ca sĩ: tra khớp chính xác trước (nhanh, ít nhầm).
        if (track.source() != MediaSource.BROWSER) {
            for (LyricsLookup.Query query : queries(track)) {
                JsonElement data = request("get?" + parameters(query) + (track.durationMs() > 0L ? "&duration=" + Math.round(track.durationMs() / 1000.0) : ""), deadline);
                String lrc = data != null && data.isJsonObject() ? synced(data.getAsJsonObject()) : "";
                if (!lrc.isBlank()) {
                    return lrc;
                }
            }
        }

        // Tab YouTube: title là tiêu đề video ("Bài - Ca sĩ | Official MV", có khi ngược thứ tự), artist là tên kênh.
        // Tra khớp chính xác không bao giờ trúng → tìm rộng theo cụm chữ rồi tự chọn bản khớp tên + thời lượng.
        Video video = Video.of(track);
        if (video.parts().isEmpty()) {
            return "";
        }

        JsonElement found = request("search?q=" + encode(String.join(" ", video.parts()) + (video.parts().size() == 1 ? " " + video.channel() : "")), deadline);
        return found != null && found.isJsonArray()
            ? pick(found.getAsJsonArray(), video, track.durationMs(), track.source() == MediaSource.BROWSER ? 8000L : 3000L)
            : "";
    }

    private static String parameters(LyricsLookup.Query query) {
        return "track_name=" + encode(query.title()) + "&artist_name=" + encode(query.artist());
    }

    /** Bản khớp tên bài + ca sĩ + thời lượng (lệch quá {@code tolerance} thì lời chạy lệch nhạc — thà không hiện). */
    static String pick(JsonArray results, Video video, long durationMs, long tolerance) {
        String best = "";
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < Math.min(100, results.size()); i++) {
            try {
                JsonObject data = results.get(i).getAsJsonObject();
                String name = string(data, "trackName");
                String lrc = synced(data);
                if (lrc.isBlank() || data.has("instrumental") && !data.get("instrumental").isJsonNull() && data.get("instrumental").getAsBoolean()) {
                    continue;
                }

                if (!video.version() && VERSION.matcher(name).find() || !video.matches(Video.of(name, "").parts(), string(data, "artistName"))) {
                    continue;
                }

                double distance = durationMs > 0L && data.has("duration") && !data.get("duration").isJsonNull()
                    ? Math.abs(data.get("duration").getAsDouble() * 1000.0 - durationMs)
                    : 0.0;
                if (distance <= tolerance && distance < bestDistance) {
                    best = lrc;
                    bestDistance = distance;
                }
            } catch (RuntimeException skipped) {
            }
        }

        return best;
    }

    private static String synced(JsonObject data) {
        String lrc = string(data, "syncedLyrics");
        return lrc.length() <= 262144 && !LrcTimeline.parse(lrc).lines().isEmpty() ? lrc : "";
    }

    private static String string(JsonObject data, String key) {
        return data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsString() : "";
    }

    static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT).replace("đ", "d").replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private static final Pattern BRACKETS = Pattern.compile("[(\\[「【][^)\\]」】]*[)\\]」】]");
    // " l " là chữ L thay cho "|" — kênh Việt hay viết "... l Official Video".
    private static final Pattern SEPARATOR = Pattern.compile("\\s+(?:[-–—|/•]|l)\\s+");
    private static final Pattern PROMO = Pattern.compile(
        "(?iu)^(?:official|offical|music|lyrics?|video|audio|mv|m/v|visuali[sz]er|hd|4k|vietsub|engsub|full|original|teaser|clip|chính|thức|\\s)+$"
    );
    private static final Pattern VERSION = Pattern.compile("(?iu)\\b(?:live|remix|cover|acoustic|instrumental|karaoke|beat|slowed|sped\\s*up|nightcore|lofi|lo-fi)\\b");
    private static final Pattern FEAT = Pattern.compile("(?iu)\\s+(?:ft\\.?|feat\\.?|featuring|prod\\.?)\\s+.*$");
    private static final Pattern CHANNEL = Pattern.compile("(?iu)(?:\\s*-\\s*topic|\\s+vevo|\\s+official|\\s+music|\\s+channel)+\\s*$");

    /** Tiêu đề tách thành các mảnh (tên bài, ca sĩ — chưa biết mảnh nào là gì) + tên kênh đã bỏ đuôi "Official/Topic". */
    record Video(List<String> parts, String channel, boolean version) {
        static Video of(MediaTrack track) {
            return of(track.title(), track.artist());
        }

        static Video of(String title, String channel) {
            List<String> parts = new ArrayList<>();
            for (String part : SEPARATOR.split(BRACKETS.matcher(title).replaceAll(" ").replaceAll("\\s+", " ").strip())) {
                part = FEAT.matcher(part.strip()).replaceFirst("").strip();
                if (!part.isBlank() && !PROMO.matcher(part).matches() && !fold(part).isEmpty()) {
                    parts.add(part);
                }
            }

            return new Video(List.copyOf(parts), CHANNEL.matcher(channel).replaceFirst("").strip(), VERSION.matcher(title).find());
        }

        /** Một mảnh trùng tên bài, và ca sĩ khớp một mảnh còn lại hoặc tên kênh (thứ tự nào cũng được). */
        boolean matches(List<String> name, String artist) {
            String singer = fold(artist);
            for (String part : this.parts) {
                String song = fold(part);
                if (singer.isEmpty() || name.stream().noneMatch(n -> fold(n).equals(song))) {
                    continue;
                }

                for (String other : this.others(part)) {
                    String folded = fold(other);
                    if (!folded.isEmpty() && (singer.contains(folded) || folded.contains(singer))) {
                        return true;
                    }
                }
            }

            return false;
        }

        private List<String> others(String song) {
            List<String> others = new ArrayList<>(this.parts);
            others.remove(song);
            others.add(this.channel);
            return others;
        }
    }

    private static JsonElement request(String path, long deadline) throws Exception {
        for (int attempt = 0; ; attempt++) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L || Thread.currentThread().isInterrupted()) {
                throw new IOException("Lyrics lookup timed out");
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create("https://lrclib.net/api/" + path))
                .timeout(Duration.ofNanos(Math.min(remaining, 4000000000L)))
                .header("Accept", "application/json")
                .header("User-Agent", "NosClient/0.3 (synced-lyrics)")
                .build();
            LyricsLookup.LimitedBody body = new LyricsLookup.LimitedBody();
            CompletableFuture<HttpResponse<byte[]>> future = HTTP.sendAsync(request, _info -> body);

            try {
                HttpResponse<byte[]> response = future.get(remaining, TimeUnit.NANOSECONDS);
                int status = response.statusCode();
                if (status == 404) {
                    return null;
                }

                // lrclib hay báo 503 "bận" vài giây (nhất là tên có dấu) — chờ chút rồi thử lại thay vì bỏ cả bài.
                if ((status == 503 || status == 429) && attempt < 3 && deadline - System.nanoTime() > 2500000000L) {
                    Thread.sleep(1200L + 800L * attempt);
                    continue;
                }

                if (status != 200) {
                    throw new IOException("Lyrics unavailable (HTTP " + status + ")");
                }

                return JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8));
            } finally {
                body.cancel();
                future.cancel(true);
            }
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
                // search trả tới 20 bản kèm cả lời thường lẫn lời đồng bộ (~0,5–1 MB với bài nhiều bản).
                if (this.output.size() + buffer.remaining() > 2097152) {
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
