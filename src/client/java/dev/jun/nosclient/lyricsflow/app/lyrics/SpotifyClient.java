package dev.jun.nosclient.lyricsflow.app.lyrics;

import dev.jun.nosclient.lyricsflow.app.Core;
import dev.jun.nosclient.lyricsflow.app.config.Config;
import dev.jun.nosclient.lyricsflow.app.hud.Island;
import dev.jun.nosclient.lyricsflow.app.sky.SkyPreset;
import dev.jun.nosclient.lyricsflow.app.util.Fx;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.lyrics.Lyrics;
import dev.jun.nosclient.lyricsflow.media.MediaManager;
import dev.jun.nosclient.lyricsflow.media.SpotifyWeb;
import dev.jun.nosclient.lyricsflow.media.Track;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class SpotifyClient {
   public static final int PORT = 8789;
   public static final String REDIRECT = "http://127.0.0.1:8789/callback";
   public static final String DASHBOARD = "https://developer.spotify.com/dashboard";
   private static volatile NowPlaying now = NowPlaying.EMPTY;
   private static final Deque<SpotifyClient.Played> HISTORY = new ArrayDeque<>();
   private static Lyrics shownLyrics;
   private static boolean shownCaps;
   private static List<NowPlaying.Line> lines = List.of();
   private static long coverSeq = -1L;
   private static String lastKey = "";

   private SpotifyClient() {
   }

   public static void init() {
      ClientLifecycleEvents.CLIENT_STARTED.register(client -> MediaManager.get().start());
      Config c = Core.cfg();
      if (c.spotifyClientId != null && !c.spotifyClientId.isBlank() && SpotifyWeb.clientId().isEmpty()) {
         SpotifyWeb.setClientId(c.spotifyClientId);
      }

      if ((c.spotifyClientId == null || c.spotifyClientId.isBlank()) && !SpotifyWeb.clientId().isEmpty()) {
         c.spotifyClientId = SpotifyWeb.clientId();
         if (SpotifyWeb.connected()) {
            c.spotifyMode = 1;
         }

         Core.saveConfig();
      }
   }

   public static void tick() {
      MediaManager mm = MediaManager.get();
      Track t = mm.track();
      if (t != null && !t.isEmpty()) {
         Lyrics l = mm.lyrics();
         boolean caps = ConfigManager.get().allCaps;
         if (l != shownLyrics || caps != shownCaps) {
            shownLyrics = l;
            shownCaps = caps;
            lines = lines(l);
         }
         NowPlaying.LyricsState st = switch (mm.lyricsState()) {
            case SEARCHING -> NowPlaying.LyricsState.LOADING;
            case FOUND -> lines.isEmpty() ? NowPlaying.LyricsState.NOT_FOUND : NowPlaying.LyricsState.SYNCED;
            case NOT_FOUND -> NowPlaying.LyricsState.NOT_FOUND;
            default -> NowPlaying.LyricsState.NONE;
         };
         String key = t.key();
         long pos = Math.max(0L, mm.position());
         now = new NowPlaying(key, t.title, t.artist, t.durationMs, t.playing, pos, System.currentTimeMillis(), lines, st);
         if (!key.equals(lastKey)) {
            lastKey = key;
            remember(t.title, t.artist);
            Config cf = Core.cfg();
            if (cf.skyShuffle && cf.sky >= 0) {
               SkyPreset.shuffle();
            }
         }
      } else {
         now = NowPlaying.EMPTY;
         lastKey = "";
      }
   }

   public static List<NowPlaying.Line> lines(Lyrics l) {
      if (l != null && !l.lines.isEmpty()) {
         List<NowPlaying.Line> out = new ArrayList<>();

         for (Lyrics.Line line : l.lines) {
            int n = line.words.size();
            String[] words = new String[n];
            long[] times = new long[n];

            for (int i = 0; i < n; i++) {
               words[i] = Lyrics.show(line.words.get(i).text);
               times[i] = line.words.get(i).start;
            }

            out.add(new NowPlaying.Line(line.start, Lyrics.show(line.text), words, n > 0 ? times : null));
         }

         return Collections.unmodifiableList(out);
      } else {
         return List.of();
      }
   }

   private static void remember(String title, String artist) {
      synchronized (HISTORY) {
         HISTORY.removeIf(p -> p.title().equals(title) && p.artist().equals(artist));
         HISTORY.addFirst(new SpotifyClient.Played(title, artist, System.currentTimeMillis()));

         while (HISTORY.size() > 30) {
            HISTORY.removeLast();
         }
      }
   }

   public static List<SpotifyClient.Played> history() {
      synchronized (HISTORY) {
         return new ArrayList<>(HISTORY);
      }
   }

   public static NowPlaying now() {
      return now;
   }

   public static SpotifyClient.Status status() {
      return switch (SpotifyWeb.state()) {
         case "on" -> SpotifyClient.Status.CONNECTED;
         case "waiting" -> SpotifyClient.Status.WAITING_LOGIN;
         case "error" -> SpotifyClient.Status.ERROR;
         default -> SpotifyWeb.clientId().isBlank() ? SpotifyClient.Status.NOT_CONFIGURED : SpotifyClient.Status.DISCONNECTED;
      };
   }

   public static String lastError() {
      return SpotifyWeb.error();
   }

   public static void login() {
      SpotifyWeb.setClientId(Core.cfg().spotifyClientId);
      SpotifyWeb.login();
   }

   public static void logout() {
      SpotifyWeb.disconnect();
   }

   public static boolean autoMode() {
      return Core.cfg().spotifyMode == 0;
   }

   public static void setMode(int mode) {
      Core.cfg().spotifyMode = mode == 1 ? 1 : 0;
      Core.saveConfig();
   }

   public static void restartDemo() {
   }

   public static void syncHere() {
      NowPlaying np = now();
      Config c = Core.cfg();
      if (np.hasTrack() && !np.lines.isEmpty()) {
         long raw = np.progressNow(0);
         long cur = raw + c.lyricsOffsetMs;
         long best = Long.MAX_VALUE;
         long bestT = 0L;

         for (NowPlaying.Line l : np.lines) {
            if (!l.text().isBlank()) {
               long d = Math.abs(l.timeMs() - cur);
               if (d < best) {
                  best = d;
                  bestT = l.timeMs();
               }
            }
         }

         c.lyricsOffsetMs = (int)Math.max(-5000L, Math.min(5000L, bestT - raw));
         Core.saveConfig();
         Island.notify(Fx.t("lyricsflow.media.synced", (c.lyricsOffsetMs > 0 ? "+" : "") + c.lyricsOffsetMs), true);
      } else {
         Island.notify(Fx.t("lyricsflow.media.nolyrics"), false);
      }
   }

   public static void control(SpotifyClient.Cmd cmd) {
      MediaManager mm = MediaManager.get();
      switch (cmd) {
         case NEXT:
            mm.next();
            break;
         case PREVIOUS:
            mm.previous();
            break;
         case PLAYPAUSE:
            mm.playPause();
      }
   }

   public enum Cmd {
      NEXT,
      PREVIOUS,
      PLAYPAUSE;
   }

   public record Played(String title, String artist, long at) {
   }

   public enum Status {
      NOT_CONFIGURED,
      DISCONNECTED,
      WAITING_LOGIN,
      CONNECTED,
      ERROR;
   }
}
