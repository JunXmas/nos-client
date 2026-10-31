package dev.jun.nosclient.lyricsflow.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jun.nosclient.lyricsflow.LyricsFlow;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.util.Http;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.Util;

public final class SpotifyWeb {
   public static final String REDIRECT = "http://127.0.0.1:8789/callback";
   private static final String SCOPES = "user-read-playback-state user-read-currently-playing user-modify-playback-state";
   private static String clientId = "";
   private static String refresh = "";
   private static String access = "";
   private static long accessUntil;
   private static volatile String state = "off";
   private static volatile String error = "";
   private static boolean loaded;
   private static volatile String sTitle = "";
   private static volatile String sArtist = "";
   private static volatile String sAlbum = "";
   private static volatile String sCover = "";
   private static volatile long sPos = -1L;
   private static volatile long sAt;
   private static volatile long sDur;
   private static volatile boolean sPlaying;
   private static long nextPoll;
   private static int fails;
   private static boolean threadStarted;

   private SpotifyWeb() {
   }

   public static synchronized String clientId() {
      load();
      return clientId;
   }

   public static synchronized void setClientId(String id) {
      load();
      id = id == null ? "" : id.trim();
      if (!id.equals(clientId)) {
         clientId = id;
         refresh = "";
         access = "";
         accessUntil = 0L;
         state = "off";
         save();
      }
   }

   public static boolean connected() {
      load();
      return !refresh.isEmpty();
   }

   public static String state() {
      load();
      return !refresh.isEmpty() && !"error".equals(state) ? "on" : state;
   }

   public static String error() {
      return error;
   }

   public static synchronized void disconnect() {
      load();
      refresh = "";
      access = "";
      accessUntil = 0L;
      state = "off";
      error = "";
      sPos = -1L;
      save();
   }

   private static Path file() {
      return ConfigManager.dir().resolve("spotify.json");
   }

   private static synchronized void load() {
      if (!loaded) {
         loaded = true;

         try {
            if (!Files.exists(file())) {
               return;
            }

            JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            if (o.has("clientId")) {
               clientId = o.get("clientId").getAsString();
            }

            if (o.has("refreshToken")) {
               refresh = o.get("refreshToken").getAsString();
            }

            if (!refresh.isEmpty()) {
               state = "on";
            }
         } catch (Throwable e) {
            LyricsFlow.LOG.warn("[LyricsFlow] Could not read spotify.json: {}", e.toString());
         }
      }
   }

   private static synchronized void save() {
      try {
         JsonObject o = new JsonObject();
         o.addProperty("_info", "Spotify login of LyricsFlow (only used to read the exact song position). Delete this file to log out.");
         o.addProperty("clientId", clientId);
         o.addProperty("refreshToken", refresh);
         Files.writeString(file(), o.toString());
      } catch (Throwable e) {
         LyricsFlow.LOG.warn("[LyricsFlow] Could not save spotify.json: {}", e.toString());
      }
   }

   public static void login() {
      load();
      if (clientId.isEmpty()) {
         state = "error";
         error = "no_client_id";
      } else if (!"waiting".equals(state)) {
         state = "waiting";
         error = "";
         Thread t = new Thread(SpotifyWeb::loginFlow, "LyricsFlow Spotify login");
         t.setDaemon(true);
         t.start();
      }
   }

   private static void loginFlow() {
      try (ServerSocket server = new ServerSocket(8789, 4, InetAddress.getByName("127.0.0.1"))) {
         server.setSoTimeout(180000);
         byte[] rnd = new byte[48];
         SecureRandom sr = new SecureRandom();
         sr.nextBytes(rnd);
         String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);
         String challenge = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
         byte[] st = new byte[12];
         sr.nextBytes(st);
         String stateTag = Base64.getUrlEncoder().withoutPadding().encodeToString(st);
         String url = "https://accounts.spotify.com/authorize?"
            + Http.query(
               Map.of(
                  "client_id",
                  clientId,
                  "response_type",
                  "code",
                  "redirect_uri",
                  "http://127.0.0.1:8789/callback",
                  "scope",
                  "user-read-playback-state user-read-currently-playing user-modify-playback-state",
                  "code_challenge_method",
                  "S256",
                  "code_challenge",
                  challenge,
                  "state",
                  stateTag
               )
            );
         Util.getPlatform().openUri(url);
         String code = null;
         String err = null;

         while (code == null && err == null) {
            try (Socket s = server.accept()) {
               s.setSoTimeout(5000);
               String first = readLine(s.getInputStream());
               String path = first.split(" ").length > 1 ? first.split(" ")[1] : "";
               if (path.startsWith("/callback")) {
                  Map<String, String> q = parseQuery(path.contains("?") ? path.substring(path.indexOf(63) + 1) : "");
                  if (!stateTag.equals(q.get("state"))) {
                     reply(s, 400, "Wrong state, try again from the game.");
                  } else {
                     if (q.containsKey("error")) {
                        err = q.get("error");
                     } else {
                        code = q.get("code");
                     }

                     reply(
                        s,
                        200,
                        err == null
                           ? "LyricsFlow is connected to Spotify. You can close this tab and go back to the game."
                           : "Spotify login was cancelled (" + err + ")."
                     );
                  }
               } else {
                  reply(s, 404, "Not found");
               }
            }
         }

         if (err != null) {
            throw new IllegalStateException(err);
         }

         Http.Response r = Http.send(
            "POST",
            "https://accounts.spotify.com/api/token",
            null,
            Http.query(
               Map.of(
                  "grant_type",
                  "authorization_code",
                  "code",
                  code,
                  "redirect_uri",
                  "http://127.0.0.1:8789/callback",
                  "client_id",
                  clientId,
                  "code_verifier",
                  verifier
               )
            )
         );
         if (r.code() != 200) {
            throw new IllegalStateException("token " + r.code() + " " + r.body());
         }

         takeTokens(r.body());
         state = "on";
         error = "";
         fails = 0;
         LyricsFlow.LOG.info("[LyricsFlow] Connected to Spotify.");
      } catch (BindException e) {
         state = "error";
         error = "port";
      } catch (SocketTimeoutException e) {
         state = "error";
         error = "timeout";
      } catch (Throwable e) {
         LyricsFlow.LOG.warn("[LyricsFlow] Spotify login failed: {}", e.toString());
         state = "error";
         error = "login";
      }
   }

   private static synchronized void takeTokens(String body) {
      JsonObject o = JsonParser.parseString(body).getAsJsonObject();
      access = o.get("access_token").getAsString();
      accessUntil = System.currentTimeMillis() + (o.has("expires_in") ? o.get("expires_in").getAsLong() : 3600L) * 1000L - 60000L;
      if (o.has("refresh_token")) {
         refresh = o.get("refresh_token").getAsString();
      }

      save();
   }

   private static String token() throws Exception {
      synchronized (SpotifyWeb.class) {
         if (!access.isEmpty() && System.currentTimeMillis() < accessUntil) {
            return access;
         }
      }

      Http.Response r = Http.send(
         "POST",
         "https://accounts.spotify.com/api/token",
         null,
         Http.query(Map.of("grant_type", "refresh_token", "refresh_token", refresh, "client_id", clientId))
      );
      if (r.code() == 400 || r.code() == 401) {
         synchronized (SpotifyWeb.class) {
            refresh = "";
            access = "";
            save();
         }

         state = "error";
         error = "expired";
         return null;
      } else {
         if (r.code() != 200) {
            return null;
         }

         takeTokens(r.body());
         return access;
      }
   }

   static synchronized void start() {
      if (!threadStarted) {
         threadStarted = true;
         Thread t = new Thread(() -> {
            while (true) {
               try {
                  poll();
               } catch (Throwable var2) {
               }

               try {
                  Thread.sleep(100L);
               } catch (InterruptedException e) {
                  return;
               }
            }
         }, "LyricsFlow Spotify position");
         t.setDaemon(true);
         t.start();
      }
   }

   static boolean setRepeat(int mode) {
      load();
      if (refresh.isEmpty()) {
         return false;
      }

      try {
         String tok = token();
         if (tok == null) {
            return false;
         }

         String st = mode == 1 ? "track" : (mode == 2 ? "context" : "off");
         Http.Response r = Http.send("PUT", "https://api.spotify.com/v1/me/player/repeat?state=" + st, Map.of("Authorization", "Bearer " + tok), null);
         return r.code() / 100 == 2;
      } catch (Throwable e) {
         return false;
      }
   }

   private static void poll() {
      load();
      if (!refresh.isEmpty()) {
         long now = System.currentTimeMillis();
         if (now >= nextPoll) {
            nextPoll = now + 1000L;

            try {
               String tok = token();
               if (tok == null) {
                  return;
               }

               long t0 = System.nanoTime();
               Http.Response r = Http.get("https://api.spotify.com/v1/me/player/currently-playing", Map.of("Authorization", "Bearer " + tok));
               long t1 = System.nanoTime();
               if (r.code() == 204) {
                  sPos = -1L;
                  return;
               }

               if (r.code() == 429) {
                  nextPoll = now + 10000L;
                  return;
               }

               if (r.code() == 401) {
                  synchronized (SpotifyWeb.class) {
                     access = "";
                     return;
                  }
               }

               if (r.code() != 200) {
                  fails++;
                  nextPoll = now + Math.min(30000L, 2000L * fails);
                  return;
               }

               fails = 0;
               JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
               if (!o.has("item") || o.get("item").isJsonNull() || !o.has("progress_ms") || o.get("progress_ms").isJsonNull()) {
                  sPos = -1L;
                  return;
               }

               JsonObject item = o.getAsJsonObject("item");
               String artist = "";
               if (item.has("artists") && item.getAsJsonArray("artists").size() > 0) {
                  artist = item.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
               } else if (item.has("show")) {
                  artist = item.getAsJsonObject("show").get("name").getAsString();
               }

               boolean playing = o.has("is_playing") && o.get("is_playing").getAsBoolean();
               long rtt = (t1 - t0) / 1000000L;
               long pos = o.get("progress_ms").getAsLong() + (playing ? rtt / 2L : 0L);
               sTitle = item.get("name").getAsString();
               sArtist = artist;
               String album = "";
               String cover = "";
               if (item.has("album") && item.get("album").isJsonObject()) {
                  JsonObject al = item.getAsJsonObject("album");
                  if (al.has("name")) {
                     album = al.get("name").getAsString();
                  }

                  if (al.has("images") && al.getAsJsonArray("images").size() > 0) {
                     cover = al.getAsJsonArray("images").get(0).getAsJsonObject().get("url").getAsString();
                  }
               }

               sAlbum = album;
               sCover = cover;
               sDur = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0L;
               sPlaying = playing;
               sAt = t1;
               sPos = pos;
               if (!"on".equals(state)) {
                  state = "on";
                  error = "";
               }
            } catch (Throwable e) {
               fails++;
               nextPoll = now + Math.min(30000L, 2000L * fails);
            }
         }
      }
   }

   static long positionFor(Track t) {
      long p = sPos;
      if (p >= 0L && t != null) {
         long age = (System.nanoTime() - sAt) / 1000000L;
         if (age > 4000L) {
            return -1L;
         } else if (!same(t.title, sTitle)) {
            return -1L;
         } else if (sDur > 0L && t.durationMs > 0L && Math.abs(sDur - t.durationMs) > 3000L) {
            return -1L;
         } else {
            return sPlaying ? p + age : p;
         }
      } else {
         return -1L;
      }
   }

   static Track nowPlaying() {
      start();
      long p = sPos;
      if (p >= 0L && !sTitle.isEmpty()) {
         long age = (System.nanoTime() - sAt) / 1000000L;
         if (age > 6000L) {
            return null;
         }

         long pos = sPlaying ? p + age : p;
         return new Track(sTitle, sArtist, sAlbum, sDur, pos, sPlaying, "Spotify", pos);
      } else {
         return null;
      }
   }

   static String coverUrl() {
      return sCover;
   }

   static void command(String method, String path) {
      try {
         String tok = token();
         if (tok == null) {
            return;
         }

         Http.send(method, "https://api.spotify.com/v1/me/player/" + path, Map.of("Authorization", "Bearer " + tok), null);
         nextPoll = 0L;
      } catch (Throwable var3) {
      }
   }

   static boolean isPlaying() {
      return sPlaying;
   }

   static long sampleAt() {
      return sAt;
   }

   private static boolean same(String a, String b) {
      String x = norm(a);
      String y = norm(b);
      return !x.isEmpty() && (x.equals(y) || x.startsWith(y) || y.startsWith(x));
   }

   private static String norm(String s) {
      return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]+", "");
   }

   private static String readLine(InputStream in) throws Exception {
      StringBuilder sb = new StringBuilder();

      int c;
      while ((c = in.read()) != -1 && c != 10 && sb.length() < 8192) {
         if (c != 13) {
            sb.append((char)c);
         }
      }

      return sb.toString();
   }

   private static void reply(Socket s, int code, String text) throws Exception {
      String html = "<!doctype html><meta charset=utf-8><title>LyricsFlow</title><body style=\"background:#14111c;color:#eee;font:16px sans-serif;display:grid;place-items:center;height:90vh\"><p>"
         + text
         + "</p>";
      byte[] b = html.getBytes(StandardCharsets.UTF_8);
      OutputStream out = s.getOutputStream();
      out.write(
         ("HTTP/1.1 " + code + " OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII)
      );
      out.write(b);
      out.flush();
   }

   private static Map<String, String> parseQuery(String q) {
      Map<String, String> m = new HashMap<>();

      for (String part : q.split("&")) {
         int i = part.indexOf(61);
         if (i > 0) {
            m.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
         }
      }

      return m;
   }
}
