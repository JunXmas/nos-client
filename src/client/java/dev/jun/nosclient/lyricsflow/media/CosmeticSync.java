package dev.jun.nosclient.lyricsflow.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jun.nosclient.lyricsflow.LyricsFlow;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import dev.jun.nosclient.lyricsflow.util.Http;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;

public final class CosmeticSync {
   private static final String TOPIC = "https://ntfy.sh/lyricsflow-cosmetics-v1-7f3a9c";
   private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "LyricsFlow cosmetics");
      t.setDaemon(true);
      return t;
   });
   private static final Map<UUID, String> others = new ConcurrentHashMap<>();
   private static final ExecutorService SEND = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "LyricsFlow cosmetics send");
      t.setDaemon(true);
      return t;
   });
   private static volatile boolean busy;
   private static volatile boolean sending;
   private static volatile String sent = null;
   private static long sentAt;
   private static long readAt;
   private static String pending;
   private static long pendingAt;
   private static volatile boolean live;
   private static volatile boolean liveStarted;

   private CosmeticSync() {
   }

   public static String of(UUID id) {
      return others.getOrDefault(id, "none");
   }

   public static void tick(Minecraft mc) {
      LFConfig c = ConfigManager.get();
      if (mc.player != null && mc.level != null) {
         startLive(mc.getUser().getProfileId());
         long now = System.currentTimeMillis();
         String mine = c.enabled && c.cosmetic != null && c.cosmetic.matches("[a-z0-9_]{1,40}") ? c.cosmetic : "none";
         UUID me = mc.getUser().getProfileId();
         if (me != null && (!mine.equals(sent) || now - sentAt > 18000000L)) {
            if (!mine.equals(pending)) {
               pending = mine;
               pendingAt = now;
               return;
            }

            if (now - pendingAt < 250L || sending) {
               return;
            }

            sending = true;
            sentAt = now;
            String send = mine;
            String id = me.toString().replace("-", "");
            SEND.execute(
               () -> {
                  try {
                     Http.Response r = Http.send(
                        "POST", "https://ntfy.sh/lyricsflow-cosmetics-v1-7f3a9c", Map.of("Content-Type", "text/plain"), "lf1 " + id + " " + send
                     );
                     if (r.code() == 200) {
                        sent = send;
                     }
                  } catch (Throwable e) {
                     LyricsFlow.LOG.info("[LyricsFlow] Could not share the cosmetic: {}", e.toString());
                  } finally {
                     sending = false;
                  }
               }
            );
         }

         if (!busy) {
            if (now - readAt > (live ? 60000L : 5000L)) {
               readAt = now;
               busy = true;
               EXEC.execute(() -> {
                  try {
                     read(me);
                  } finally {
                     busy = false;
                  }
               });
            }
         }
      }
   }

   private static synchronized void startLive(UUID me) {
      if (!liveStarted && me != null) {
         liveStarted = true;
         Thread t = new Thread(
            () -> {
               HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
               long wait = 2000L;

               while (true) {
                  try {
                     HttpRequest req = HttpRequest.newBuilder(URI.create("https://ntfy.sh/lyricsflow-cosmetics-v1-7f3a9c/json?since=12h"))
                        .header("User-Agent", "LyricsFlow/1.0 (Minecraft mod; https://modrinth.com/mod/lyricsflow)")
                        .GET()
                        .build();
                     HttpResponse<Stream<String>> r = client.send(req, BodyHandlers.ofLines());
                     if (r.statusCode() == 200) {
                        live = true;
                        wait = 2000L;
                        r.body().forEach(line -> take(line, me));
                     }
                  } catch (Throwable var11) {
                  } finally {
                     live = false;
                  }

                  try {
                     Thread.sleep(wait);
                  } catch (InterruptedException e) {
                     return;
                  }

                  wait = Math.min(60000L, wait * 2L);
               }
            },
            "LyricsFlow cosmetics live"
         );
         t.setDaemon(true);
         t.start();
      }
   }

   private static void take(String line, UUID me) {
      try {
         if (line == null || line.isBlank()) {
            return;
         }

         JsonObject o = JsonParser.parseString(line).getAsJsonObject();
         if (!o.has("event") || !"message".equals(o.get("event").getAsString()) || !o.has("message")) {
            return;
         }

         String[] p = o.get("message").getAsString().trim().split(" ");
         if (p.length != 3 || !p[0].equals("lf1") || !p[1].matches("[0-9a-f]{32}") || !p[2].matches("[a-z0-9_]{1,40}")) {
            return;
         }

         UUID id = UUID.fromString(p[1].replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
         if (id.equals(me)) {
            return;
         }

         if ("none".equals(p[2])) {
            others.remove(id);
         } else {
            others.put(id, p[2]);
         }
      } catch (Throwable var5) {
      }
   }

   private static void read(UUID me) {
      try {
         Http.Response r = Http.get("https://ntfy.sh/lyricsflow-cosmetics-v1-7f3a9c/json?poll=1&since=12h", null);
         if (r.code() != 200) {
            return;
         }

         Map<UUID, String> next = new ConcurrentHashMap<>();

         for (String line : r.body().split("\n")) {
            if (!line.isBlank()) {
               JsonObject o = JsonParser.parseString(line).getAsJsonObject();
               if (o.has("event") && "message".equals(o.get("event").getAsString()) && o.has("message")) {
                  String[] p = o.get("message").getAsString().trim().split(" ");
                  if (p.length == 3 && p[0].equals("lf1") && p[1].matches("[0-9a-f]{32}") && p[2].matches("[a-z0-9_]{1,40}")) {
                     UUID id = UUID.fromString(p[1].replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
                     if (!id.equals(me)) {
                        next.put(id, p[2]);
                     }
                  }
               }
            }
         }

         next.values().removeIf("none"::equals);
         others.keySet().retainAll(next.keySet());
         others.putAll(next);
      } catch (Throwable var10) {
      }
   }
}
