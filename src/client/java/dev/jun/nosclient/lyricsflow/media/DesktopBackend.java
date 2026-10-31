package dev.jun.nosclient.lyricsflow.media;

import com.sun.jna.Platform;
import dev.jun.nosclient.lyricsflow.LyricsFlow;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DesktopBackend implements MediaBackend {
   private static final boolean LINUX = Platform.isLinux() || Platform.isFreeBSD();
   private static final boolean MAC = Platform.isMac();
   private String status = "lyricsflow.status.searching";
   private String player = "";
   private String artUrl = "";
   private boolean web;
   private long lastPosUs;
   private boolean noGdbus;
   private static final Pattern NAME = Pattern.compile("'(org\\.mpris\\.MediaPlayer2\\.[^']+)'");

   @Override
   public boolean available() {
      return LINUX || MAC;
   }

   @Override
   public Track poll(LFConfig.Source source) {
      Track t = null;
      this.web = false;

      try {
         if (LINUX && !this.noGdbus) {
            t = this.linux(source);
         } else if (MAC) {
            t = this.mac(source);
         }
      } catch (Throwable e) {
         LyricsFlow.LOG.debug("now playing failed", e);
      }

      if (t == null && SpotifyWeb.connected() && source != LFConfig.Source.YOUTUBE) {
         t = SpotifyWeb.nowPlaying();
         if (t != null) {
            this.web = true;
            this.artUrl = SpotifyWeb.coverUrl();
            t = track(t.title, t.artist, t.album, t.durationMs, t.positionMs, t.playing, "Spotify");
         }
      }

      if (t == null) {
         this.status = SpotifyWeb.connected() ? "lyricsflow.status.nothing" : "lyricsflow.status.connect_other";
         return null;
      } else {
         this.status = t.playing ? "lyricsflow.status.playing" : "lyricsflow.status.paused";
         return t;
      }
   }

   private Track linux(LFConfig.Source source) throws Exception {
      String names = run(
         3000L,
         "gdbus",
         "call",
         "--session",
         "--dest",
         "org.freedesktop.DBus",
         "--object-path",
         "/org/freedesktop/DBus",
         "--method",
         "org.freedesktop.DBus.ListNames"
      );
      if (names == null) {
         this.noGdbus = true;
         return null;
      }

      List<String> players = new ArrayList<>();
      Matcher m = NAME.matcher(names);

      while (m.find()) {
         players.add(m.group(1));
      }

      Track best = null;
      String bestName = "";
      long bestPos = 0L;
      String bestArt = "";

      for (String name : players) {
         String low = name.toLowerCase(Locale.ROOT);
         boolean spotify = low.contains("spotify");
         boolean browser = low.contains("chrom")
            || low.contains("firefox")
            || low.contains("brave")
            || low.contains("edge")
            || low.contains("opera")
            || low.contains("vivaldi")
            || low.contains("plasma-browser");
         if ((source != LFConfig.Source.SPOTIFY || spotify) && (source != LFConfig.Source.YOUTUBE || browser)) {
            String props = run(
               2000L,
               "gdbus",
               "call",
               "--session",
               "--dest",
               name,
               "--object-path",
               "/org/mpris/MediaPlayer2",
               "--method",
               "org.freedesktop.DBus.Properties.GetAll",
               "org.mpris.MediaPlayer2.Player"
            );
            if (props != null) {
               String title = str(props, "xesam:title");
               if (!title.isEmpty()) {
                  String artist = first(props, "xesam:artist");
                  String album = str(props, "xesam:album");
                  long lenUs = num(props, "mpris:length");
                  long posUs = num(props, "Position");
                  boolean playing = "Playing".equals(str(props, "PlaybackStatus"));
                  String app = spotify ? "Spotify" : (browser ? "Browser" : name.substring("org.mpris.MediaPlayer2.".length()));
                  if (!spotify) {
                     String[] split = TitleCleaner.splitYoutube(title, artist);
                     artist = split[0];
                     title = split[1];
                  }

                  Track t = track(title, artist, album, Math.max(0L, lenUs / 1000L), Math.max(0L, posUs / 1000L), playing, app);
                  if (best == null || playing && !best.playing) {
                     best = t;
                     bestName = name;
                     bestPos = posUs;
                     bestArt = str(props, "mpris:artUrl");
                  }
               }
            }
         }
      }

      this.player = bestName;
      this.lastPosUs = bestPos;
      this.artUrl = bestArt;
      return best;
   }

   private static String str(String props, String key) {
      Matcher m = Pattern.compile("'" + Pattern.quote(key) + "': <'((?:[^'\\\\]|\\\\.)*)'>").matcher(props);
      if (m.find()) {
         return unescape(m.group(1));
      }

      m = Pattern.compile("'" + Pattern.quote(key) + "': <\"((?:[^\"\\\\]|\\\\.)*)\">").matcher(props);
      return m.find() ? unescape(m.group(1)) : "";
   }

   private static String first(String props, String key) {
      Matcher m = Pattern.compile("'" + Pattern.quote(key) + "': <\\[['\"]((?:[^'\"\\\\]|\\\\.)*)['\"]").matcher(props);
      return m.find() ? unescape(m.group(1)) : "";
   }

   private static long num(String props, String key) {
      Matcher m = Pattern.compile("'" + Pattern.quote(key) + "': <(?:u?int64 |uint32 |int32 )?(-?\\d+)>").matcher(props);
      return m.find() ? Long.parseLong(m.group(1)) : 0L;
   }

   private static String unescape(String s) {
      return s.replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\");
   }

   private void mpris(String method, String... args) {
      if (!this.player.isEmpty()) {
         List<String> cmd = new ArrayList<>(
            List.of(
               "gdbus",
               "call",
               "--session",
               "--dest",
               this.player,
               "--object-path",
               "/org/mpris/MediaPlayer2",
               "--method",
               "org.mpris.MediaPlayer2.Player." + method
            )
         );
         cmd.addAll(List.of(args));
         run(2000L, cmd.toArray(new String[0]));
      }
   }

   private static String script(String app, boolean musicApp) {
      return "if application \""
         + app
         + "\" is running then\ntell application \""
         + app
         + "\"\nif player state is stopped then return \"\"\nset t to current track\nreturn (name of t) & \"\\n\" & (artist of t) & \"\\n\" & (album of t) & \"\\n\" & ((duration of t) as string) & \"\\n\" & ((player position) as string) & \"\\n\" & ((player state) as string)"
         + (musicApp ? "" : " & \"\\n\" & (artwork url of t)")
         + "\nend tell\nend if\nreturn \"\"";
   }

   private Track mac(LFConfig.Source source) {
      if (source == LFConfig.Source.YOUTUBE) {
         return null;
      }

      Track t = this.macApp("Spotify", false);
      if (t == null && source != LFConfig.Source.SPOTIFY) {
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
            return track(p[0], p[1], p[2], durMs, posMs, playing, app.equals("Spotify") ? "Spotify" : "Music");
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static Track track(String title, String artist, String album, long durMs, long posMs, boolean playing, String app) {
      long nowFt = (System.currentTimeMillis() + 11644473600000L) * 10000L;
      return new Track(title, artist, album, durMs, posMs, playing, app, posMs, nowFt, System.nanoTime());
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
         run(3000L, "osascript", "-e", "if application \"" + this.player + "\" is running then tell application \"" + this.player + "\" to " + what);
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
         this.tellMac("set player position to " + String.format(Locale.ROOT, "%.2f", ms / 1000.0));
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
               HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).connectTimeout(Duration.ofSeconds(5L)).build();
               HttpResponse<byte[]> r = client.send(HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(8L)).build(), BodyHandlers.ofByteArray());
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
   public void close() {
   }

   private static String run(long timeoutMs, String... cmd) {
      try {
         Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
         ByteArrayOutputStream out = new ByteArrayOutputStream();

         try (InputStream in = p.getInputStream()) {
            Thread reader = new Thread(() -> {
               try {
                  in.transferTo(out);
               } catch (Exception var3x) {
               }
            }, "LyricsFlow media read");
            reader.setDaemon(true);
            reader.start();
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
               p.destroyForcibly();
               return null;
            }

            reader.join(500L);
         }

         return p.exitValue() != 0 ? null : out.toString(StandardCharsets.UTF_8);
      } catch (Throwable e) {
         return null;
      }
   }
}
