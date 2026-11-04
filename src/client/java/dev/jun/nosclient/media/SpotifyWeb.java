package dev.jun.nosclient.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.util.Http;

import net.minecraft.util.Util;

import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

public final class SpotifyWeb {
    public static final String REDIRECT = "http://127.0.0.1:8789/callback";
    private static final String SCOPES =
            "user-read-playback-state user-read-currently-playing user-modify-playback-state";
    private static String clientId = "";
    private static String refresh = "";
    private static String access = "";
    private static long accessUntil;
    private static volatile String state = "off";
    private static volatile String error = "";
    private static boolean loaded;
    private static volatile long loginGeneration;
    private static volatile ServerSocket loginServer;
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

    private SpotifyWeb() {}

    public static synchronized String clientId() {
        load();
        return clientId;
    }

    public static synchronized void setClientId(String id) {
        load();
        id = id == null ? "" : id.trim();
        if (!id.equals(clientId)) {
            cancelLogin();
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
        cancelLogin();
        refresh = "";
        access = "";
        accessUntil = 0L;
        state = "off";
        error = "";
        sPos = -1L;
        save();
    }

    private static Path file() {
        return MusicSettings.dir().resolve("spotify.json");
    }

    private static synchronized void load() {
        if (!loaded) {
            loaded = true;

            try {
                if (!Files.exists(file())) {
                    return;
                }

                JsonObject o =
                        JsonParser.parseString(dev.jun.nosclient.storage.ClientFiles.read(file()))
                                .getAsJsonObject();
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
                ClientResources.LOG.warn(
                        "[Nos Client] Could not read spotify.json: {}", e.toString());
            }
        }
    }

    private static synchronized void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty(
                    "_info",
                    "Spotify login for Nos Client. Kept only on this computer; delete this file to"
                            + " log out.");
            o.addProperty("clientId", clientId);
            o.addProperty("refreshToken", refresh);
            dev.jun.nosclient.storage.ClientFiles.write(file(), o.toString());
        } catch (Throwable e) {
            ClientResources.LOG.warn("[Nos Client] Could not save spotify.json: {}", e.toString());
        }
    }

    public static synchronized void login() {
        load();
        if (clientId.isEmpty()) {
            state = "error";
            error = "no_client_id";
        } else if (!"waiting".equals(state)) {
            state = "waiting";
            error = "";
            long generation = ++loginGeneration;
            String id = clientId;
            Thread t = new Thread(() -> loginFlow(generation, id), "Nos Client Spotify login");
            t.setDaemon(true);
            t.start();
        }
    }

    private static void loginFlow(long generation, String id) {
        try (ServerSocket server = new ServerSocket(8789, 4, InetAddress.getByName("127.0.0.1"))) {
            synchronized (SpotifyWeb.class) {
                if (generation != loginGeneration) return;
                loginServer = server;
            }
            byte[] rnd = new byte[48];
            SecureRandom sr = new SecureRandom();
            sr.nextBytes(rnd);
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);
            String challenge =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            byte[] st = new byte[12];
            sr.nextBytes(st);
            String stateTag = Base64.getUrlEncoder().withoutPadding().encodeToString(st);
            String url =
                    "https://accounts.spotify.com/authorize?"
                            + Http.query(
                                    Map.of(
                                            "client_id",
                                            id,
                                            "response_type",
                                            "code",
                                            "redirect_uri",
                                            "http://127.0.0.1:8789/callback",
                                            "scope",
                                            SCOPES,
                                            "code_challenge_method",
                                            "S256",
                                            "code_challenge",
                                            challenge,
                                            "state",
                                            stateTag));
            Util.getPlatform().openUri(url);
            String code =
                    SpotifyCallback.await(
                            server,
                            stateTag,
                            System.nanoTime() + 180_000_000_000L,
                            () -> generation != loginGeneration);

            Http.Response r =
                    Http.send(
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
                                            id,
                                            "code_verifier",
                                            verifier)));
            if (r.code() != 200) {
                throw new IllegalStateException("Spotify token exchange returned " + r.code());
            }

            synchronized (SpotifyWeb.class) {
                if (!takeTokens(r.body(), generation)) return;
                state = "on";
                error = "";
                fails = 0;
            }
            ClientResources.LOG.info("[Nos Client] Connected to Spotify.");
        } catch (BindException e) {
            if (generation != loginGeneration) return;
            state = "error";
            error = "port";
        } catch (SocketTimeoutException e) {
            if (generation != loginGeneration) return;
            state = "error";
            error = "timeout";
        } catch (Exception e) {
            if (generation != loginGeneration) return;
            ClientResources.LOG.warn("[Nos Client] Spotify login failed: {}", e.toString());
            state = "error";
            error = "login";
        } finally {
            if (generation == loginGeneration) loginServer = null;
        }
    }

    private static synchronized boolean takeTokens(String body, long generation) {
        if (generation != loginGeneration) return false;
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        access = o.get("access_token").getAsString();
        accessUntil =
                System.currentTimeMillis()
                        + (o.has("expires_in") ? o.get("expires_in").getAsLong() : 3600L) * 1000L
                        - 60000L;
        if (o.has("refresh_token")) {
            refresh = o.get("refresh_token").getAsString();
        }

        save();
        return true;
    }

    private static String token() throws Exception {
        final long generation;
        final String refreshToken;
        final String id;
        synchronized (SpotifyWeb.class) {
            if (refresh.isEmpty()) return null;
            generation = loginGeneration;
            refreshToken = refresh;
            id = clientId;
            if (!access.isEmpty() && System.currentTimeMillis() < accessUntil) {
                return access;
            }
        }

        Http.Response r =
                Http.send(
                        "POST",
                        "https://accounts.spotify.com/api/token",
                        null,
                        Http.query(
                                Map.of(
                                        "grant_type",
                                        "refresh_token",
                                        "refresh_token",
                                        refreshToken,
                                        "client_id",
                                        id)));
        if (r.code() == 400 || r.code() == 401) {
            synchronized (SpotifyWeb.class) {
                if (generation != loginGeneration) return null;
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

            synchronized (SpotifyWeb.class) {
                return takeTokens(r.body(), generation) ? access : null;
            }
        }
    }

    static synchronized void start() {
        if (!threadStarted) {
            threadStarted = true;
            Thread t =
                    new Thread(
                            () -> {
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
                            },
                            "Nos Client Spotify position");
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
            Http.Response r =
                    Http.send(
                            "PUT",
                            "https://api.spotify.com/v1/me/player/repeat?state=" + st,
                            Map.of("Authorization", "Bearer " + tok),
                            null);
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
                    long generation = loginGeneration;
                    String tok = token();
                    if (tok == null) {
                        return;
                    }

                    long t0 = System.nanoTime();
                    Http.Response r =
                            Http.get(
                                    "https://api.spotify.com/v1/me/player/currently-playing",
                                    Map.of("Authorization", "Bearer " + tok));
                    synchronized (SpotifyWeb.class) {
                        if (generation != loginGeneration || refresh.isEmpty()) return;
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
                        if (!o.has("item")
                                || o.get("item").isJsonNull()
                                || !o.has("progress_ms")
                                || o.get("progress_ms").isJsonNull()) {
                            sPos = -1L;
                            return;
                        }

                        JsonObject item = o.getAsJsonObject("item");
                        String artist = "";
                        if (item.has("artists") && item.getAsJsonArray("artists").size() > 0) {
                            artist =
                                    item.getAsJsonArray("artists")
                                            .get(0)
                                            .getAsJsonObject()
                                            .get("name")
                                            .getAsString();
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
                                cover =
                                        al.getAsJsonArray("images")
                                                .get(0)
                                                .getAsJsonObject()
                                                .get("url")
                                                .getAsString();
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

            Http.send(
                    method,
                    "https://api.spotify.com/v1/me/player/" + path,
                    Map.of("Authorization", "Bearer " + tok),
                    null);
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

    private static void cancelLogin() {
        loginGeneration++;
        ServerSocket server = loginServer;
        loginServer = null;
        if (server != null) {
            try {
                server.close();
            } catch (java.io.IOException error) {
                ClientResources.LOG.debug("Could not close Spotify login callback", error);
            }
        }
    }
}
