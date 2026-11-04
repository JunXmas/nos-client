package dev.jun.nosclient.media;

import com.sun.jna.Platform;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.config.MusicConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DesktopBackend implements MediaBackend {
    private static final boolean LINUX = Platform.isLinux() || Platform.isFreeBSD();
    private static final boolean MAC = Platform.isMac();
    private String status = "nosclient.music.status.searching";
    private String player = "";
    private String artUrl = "";
    private boolean web;
    private long lastPosUs;
    private long gdbusRetryAt;
    private static final Pattern NAME = Pattern.compile("'(org\\.mpris\\.MediaPlayer2\\.[^']+)'");

    @Override
    public boolean available() {
        return LINUX || MAC;
    }

    @Override
    public Track poll(MusicConfig.Source source) {
        Track t = null;
        this.web = false;

        try {
            if (LINUX && System.nanoTime() >= this.gdbusRetryAt) {
                t = this.linux(source);
            } else if (MAC) {
                t = this.mac(source);
            }
        } catch (Exception e) {
            ClientResources.LOG.debug("now playing failed", e);
        }

        if (t == null && SpotifyWeb.connected() && source != MusicConfig.Source.YOUTUBE) {
            t = SpotifyWeb.nowPlaying();
            if (t != null) {
                this.web = true;
                this.artUrl = SpotifyWeb.coverUrl();
                t =
                        track(
                                t.title,
                                t.artist,
                                t.album,
                                t.durationMs,
                                t.positionMs,
                                t.playing,
                                "Spotify");
            }
        }

        if (t == null) {
            this.status =
                    SpotifyWeb.connected()
                            ? "nosclient.music.status.nothing"
                            : "nosclient.music.status.connect_other";
            return null;
        } else {
            this.status =
                    t.playing ? "nosclient.music.status.playing" : "nosclient.music.status.paused";
            return t;
        }
    }

    private Track linux(MusicConfig.Source source) throws Exception {
        long deadline = System.nanoTime() + 900_000_000L;
        String names =
                run(
                        500L,
                        "gdbus",
                        "call",
                        "--session",
                        "--dest",
                        "org.freedesktop.DBus",
                        "--object-path",
                        "/org/freedesktop/DBus",
                        "--method",
                        "org.freedesktop.DBus.ListNames");
        if (names == null) {
            this.gdbusRetryAt = System.nanoTime() + 10_000_000_000L;
            return null;
        }

        List<String> players = playerNames(names, source);

        Track best = null;
        String bestName = "";
        long bestPos = 0L;
        String bestArt = "";

        for (String name : players) {
            long remaining = (deadline - System.nanoTime()) / 1_000_000L;
            if (remaining <= 0) break;
            String app = playerApp(name);
            String props =
                    run(
                            Math.min(500L, remaining),
                            "gdbus",
                            "call",
                            "--session",
                            "--dest",
                            name,
                            "--object-path",
                            "/org/mpris/MediaPlayer2",
                            "--method",
                            "org.freedesktop.DBus.Properties.GetAll",
                            "org.mpris.MediaPlayer2.Player");
            if (props != null) {
                String title = str(props, "xesam:title");
                if (!title.isEmpty()) {
                    String artist = first(props, "xesam:artist");
                    String album = str(props, "xesam:album");
                    long lenUs = num(props, "mpris:length");
                    long posUs = num(props, "Position");
                    boolean playing = "Playing".equals(str(props, "PlaybackStatus"));
                    Track t =
                            track(
                                            title,
                                            artist,
                                            album,
                                            Math.max(0L, lenUs / 1000L),
                                            Math.max(0L, posUs / 1000L),
                                            playing,
                                            app)
                                    .withRate(
                                            MprisMetadata.parse(props, name, System.nanoTime())
                                                    .rate());
                    if (best == null || playing && !best.playing) {
                        best = t;
                        bestName = name;
                        bestPos = posUs;
                        bestArt = str(props, "mpris:artUrl");
                    }
                }
            }
        }

        this.player = bestName;
        this.lastPosUs = bestPos;
        this.artUrl = bestArt;
        return best;
    }

    static List<String> playerNames(String response, MusicConfig.Source source) {
        List<String> players = new ArrayList<>();
        Matcher matcher = NAME.matcher(response);
        while (matcher.find() && players.size() < 6) {
            String name = matcher.group(1);
            String app = playerApp(name);
            if (source == MusicConfig.Source.ANY
                    || source == MusicConfig.Source.SPOTIFY && app.equals("Spotify")
                    || source == MusicConfig.Source.YOUTUBE && app.equals("Browser"))
                players.add(name);
        }
        return players;
    }

    private static String playerApp(String name) {
        String low = name.toLowerCase(Locale.ROOT);
        if (low.contains("spotify")) return "Spotify";
        if (low.contains("chrom")
                || low.contains("firefox")
                || low.contains("brave")
                || low.contains("edge")
                || low.contains("opera")
                || low.contains("vivaldi")
                || low.contains("plasma-browser")) return "Browser";
        return name.substring("org.mpris.MediaPlayer2.".length());
    }

    private static String str(String props, String key) {
        return MprisMetadata.string(props, key);
    }

    private static String first(String props, String key) {
        return MprisMetadata.string(props, key);
    }

    private static long num(String props, String key) {
        return MprisMetadata.number(props, key);
    }

    private void mpris(String method, String... args) {
        if (!this.player.isEmpty()) {
            List<String> cmd =
                    new ArrayList<>(
                            List.of(
                                    "gdbus",
                                    "call",
                                    "--session",
                                    "--dest",
                                    this.player,
                                    "--object-path",
                                    "/org/mpris/MediaPlayer2",
                                    "--method",
                                    "org.mpris.MediaPlayer2.Player." + method));
            // GVariant âm phải nằm sau dấu kết thúc tuỳ chọn của gdbus.
            if (args.length > 0) cmd.add("--");
            cmd.addAll(List.of(args));
            run(2000L, cmd.toArray(new String[0]));
        }
    }

    private static String script(String app, boolean musicApp) {
        return "if application \""
                + app
                + "\" is running then\ntell application \""
                + app
                + "\"\n"
                + "if player state is stopped then return \"\"\n"
                + "set t to current track\n"
                + "return (name of t) & \"\\n"
                + "\" & (artist of t) & \"\\n"
                + "\" & (album of t) & \"\\n"
                + "\" & ((duration of t) as string) & \"\\n"
                + "\" & ((player position) as string) & \"\\n"
                + "\" & ((player state) as string)"
                + (musicApp ? "" : " & \"\\n\" & (artwork url of t)")
                + "\nend tell\nend if\nreturn \"\"";
    }

    private Track mac(MusicConfig.Source source) {
        if (source == MusicConfig.Source.YOUTUBE) {
            return null;
        }

        Track t = this.macApp("Spotify", false);
        if (t == null && source != MusicConfig.Source.SPOTIFY) {
            t = this.macApp("Music", true);
        }

        return t;
    }

    private Track macApp(String app, boolean musicApp) {
        String out = run(3000L, "osascript", "-e", script(app, musicApp));
        if (out != null && !out.isBlank()) {
            String[] p = out.strip().split("\n", -1);
            if (p.length >= 6 && !p[0].isBlank()) {
                double dur = parse(p[3]);
                double pos = parse(p[4]);
                long durMs = musicApp ? Math.round(dur * 1000.0) : Math.round(dur);
                long posMs = Math.round(pos * 1000.0);
                boolean playing = p[5].trim().toLowerCase(Locale.ROOT).startsWith("playing");
                this.player = app;
                this.artUrl = !musicApp && p.length > 6 ? p[6].trim() : "";
                return track(
                        p[0],
                        p[1],
                        p[2],
                        durMs,
                        posMs,
                        playing,
                        app.equals("Spotify") ? "Spotify" : "Music");
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static Track track(
            String title,
            String artist,
            String album,
            long durMs,
            long posMs,
            boolean playing,
            String app) {
        long nowFt = (System.currentTimeMillis() + 11644473600000L) * 10000L;
        return new Track(
                title, artist, album, durMs, posMs, playing, app, posMs, nowFt, System.nanoTime());
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private void tellMac(String what) {
        if (!this.player.isEmpty()) {
            run(
                    3000L,
                    "osascript",
                    "-e",
                    "if application \""
                            + this.player
                            + "\" is running then tell application \""
                            + this.player
                            + "\" to "
                            + what);
        }
    }

    @Override
    public void playPause() {
        if (this.web) {
            SpotifyWeb.command("PUT", SpotifyWeb.isPlaying() ? "pause" : "play");
        } else if (LINUX) {
            this.mpris("PlayPause");
        } else if (MAC) {
            this.tellMac("playpause");
        }
    }

    @Override
    public void next() {
        if (this.web) {
            SpotifyWeb.command("POST", "next");
        } else if (LINUX) {
            this.mpris("Next");
        } else if (MAC) {
            this.tellMac("next track");
        }
    }

    @Override
    public void previous() {
        if (this.web) {
            SpotifyWeb.command("POST", "previous");
        } else if (LINUX) {
            this.mpris("Previous");
        } else if (MAC) {
            this.tellMac("previous track");
        }
    }

    @Override
    public void seek(long ms) {
        ms = Math.max(0L, ms);
        if (this.web) {
            SpotifyWeb.command("PUT", "seek?position_ms=" + ms);
        } else if (LINUX) {
            this.mpris("Seek", Long.toString(ms * 1000L - this.lastPosUs));
        } else if (MAC) {
            this.tellMac(
                    "set player position to " + String.format(Locale.ROOT, "%.2f", ms / 1000.0));
        }
    }

    @Override
    public byte[] cover() {
        String u = this.artUrl;
        if (u != null && !u.isBlank()) {
            try {
                if (u.startsWith("file://")) {
                    Path p = Path.of(URI.create(u));
                    return Files.size(p) < 8388608L ? Files.readAllBytes(p) : null;
                }

                if (u.startsWith("http")) {
                    HttpClient client =
                            HttpClient.newBuilder()
                                    .followRedirects(Redirect.NORMAL)
                                    .connectTimeout(Duration.ofSeconds(5L))
                                    .build();
                    HttpResponse<byte[]> r =
                            client.send(
                                    HttpRequest.newBuilder(URI.create(u))
                                            .timeout(Duration.ofSeconds(8L))
                                            .build(),
                                    ignored ->
                                            new dev.jun.nosclient.util.LimitedBody(
                                                    8 * 1024 * 1024));
                    return r.statusCode() == 200 ? r.body() : null;
                }
            } catch (Throwable var4) {
            }

            return null;
        } else {
            return null;
        }
    }

    @Override
    public String statusKey() {
        return this.status;
    }

    @Override
    public void close() {}

    private static String run(long timeoutMs, String... cmd) {
        try {
            return MediaCommand.run(java.util.List.of(cmd), timeoutMs);
        } catch (Exception error) {
            ClientResources.LOG.debug("Lệnh media không khả dụng: {}", cmd[0], error);
            return null;
        }
    }
}
