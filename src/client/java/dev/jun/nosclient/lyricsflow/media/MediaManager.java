package dev.jun.nosclient.lyricsflow.media;

import com.google.gson.Gson;
import com.sun.jna.Platform;
import dev.jun.nosclient.lyricsflow.LyricsFlow;
import dev.jun.nosclient.lyricsflow.config.ConfigManager;
import dev.jun.nosclient.lyricsflow.config.LFConfig;
import dev.jun.nosclient.lyricsflow.lyrics.Lyrics;
import dev.jun.nosclient.lyricsflow.lyrics.LyricsFetcher;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MediaManager {
   private static final MediaManager INSTANCE = new MediaManager();
   private final SmtcBackend smtc = new SmtcBackend();
   private final ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<>();
   private final ConcurrentLinkedQueue<MediaManager.Event> events = new ConcurrentLinkedQueue<>();
   private final ExecutorService lyricsPool = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "LyricsFlow-Lyrics");
      t.setDaemon(true);
      return t;
   });
   private volatile Track track;
   private volatile Lyrics lyrics;
   private volatile MediaManager.LyricsState lyricsState = MediaManager.LyricsState.NONE;
   private volatile String statusKey = "lyricsflow.status.searching";
   private volatile boolean running;
   private volatile long songSeq;
   private Thread thread;
   private String lastKey = "";
   private boolean lastPlaying;
   private long missingSince = -1L;
   private long pausedUpdatedFt = -2L;
   private long lastOffset = Long.MIN_VALUE;
   private long lastPollNs = -1L;
   private int stable;
   private volatile byte[] cover;
   private volatile long coverSeq = -1L;
   private volatile int repeat;
   private volatile long repeatHold;
   private volatile boolean pollSoon;
   private volatile boolean forceSync;
   private final MediaBackend desktop = Platform.isWindows() ? null : new DesktopBackend();
   private long lastApiAt;
   private final long[] apiOffs = new long[5];
   private int apiN;
   private static final Gson GSON = new Gson();

   public static MediaManager get() {
      return INSTANCE;
   }

   private MediaManager() {
   }

   public void start() {
      if (!this.running) {
         this.running = true;
         this.thread = new Thread(this::loop, "LyricsFlow-Media");
         this.thread.setDaemon(true);
         this.thread.start();
      }
   }

   public void stop() {
      this.running = false;
      if (this.thread != null) {
         this.thread.interrupt();
      }

      this.lyricsPool.shutdownNow();
   }

   public Track track() {
      return this.track;
   }

   public Lyrics lyrics() {
      return this.lyrics;
   }

   public MediaManager.LyricsState lyricsState() {
      return this.lyricsState;
   }

   public String statusKey() {
      return this.statusKey;
   }

   public long songSeq() {
      return this.songSeq;
   }

   public byte[] cover() {
      return this.cover;
   }

   public long coverSeq() {
      return this.coverSeq;
   }

   public boolean isPlaying() {
      Track t = this.track;
      return t != null && t.playing;
   }

   public long positionRaw() {
      Track t = this.track;
      return t == null ? -1L : t.positionNow();
   }

   public long position() {
      Track t = this.track;
      if (t == null) {
         return -1L;
      }

      LFConfig c = ConfigManager.get();
      Integer own = c.songOffsets == null ? null : c.songOffsets.get(t.key());
      return Math.max(0L, t.positionNow() + c.syncOffsetMs + (own == null ? 0 : own));
   }

   public MediaManager.Event pollEvent() {
      return this.events.poll();
   }

   public void playPause() {
      this.commands.add(() -> {
         this.backend().playPause();
         this.pollSoon = true;
      });
   }

   public void next() {
      this.commands.add(() -> {
         this.backend().next();
         this.pollSoon = true;
      });
   }

   public void previous() {
      this.commands.add(() -> {
         this.backend().previous();
         this.pollSoon = true;
      });
   }

   public int repeat() {
      return this.repeat;
   }

   public void cycleRepeat() {
      int next = this.repeat == 0 ? 2 : (this.repeat == 2 ? 1 : 0);
      this.repeat = next;
      this.repeatHold = System.currentTimeMillis() + 4000L;
      this.commands.add(() -> {
         this.backend().setRepeat(next);
         Track t = this.track;
         if (t != null && "Spotify".equals(t.sourceApp)) {
            Thread th = new Thread(() -> SpotifyWeb.setRepeat(next), "LyricsFlow repeat");
            th.setDaemon(true);
            th.start();
         }

         this.pollSoon = true;
      });
   }

   public void seek(long ms) {
      this.commands.add(() -> {
         this.backend().seek(ms);
         Track t = this.track;
         if (t != null) {
            this.track = new Track(t.title, t.artist, t.album, t.durationMs, ms, t.playing, t.sourceApp, ms);
         }

         this.pollSoon = true;
      });
   }

   public void reconnect() {
      this.commands.add(() -> {
         this.smtc.close();
         this.lastKey = "";
         this.track = null;
         this.lyrics = null;
         this.lyricsState = MediaManager.LyricsState.NONE;
      });
   }

   public void refetch() {
      Track t = this.track;
      if (t != null) {
         try {
            Files.deleteIfExists(cacheFile(t));
         } catch (Exception var3) {
         }

         this.fetchLyrics(t, this.songSeq);
      }
   }

   public void resync() {
      this.forceSync = true;
      this.pollSoon = true;
   }

   private MediaBackend backend() {
      return this.desktop != null ? this.desktop : this.smtc;
   }

   private void loop() {
      while (this.running) {
         long started = System.currentTimeMillis();

         try {
            Runnable r;
            while ((r = this.commands.poll()) != null) {
               try {
                  r.run();
               } catch (Throwable t) {
                  LyricsFlow.LOG.debug("media command failed", t);
               }
            }

            this.tick();
         } catch (Throwable t) {
            LyricsFlow.LOG.debug("media poll failed", t);
         }

         long interval = 250L;
         if (this.pollSoon) {
            this.pollSoon = false;
            interval = 120L;
         }

         long sleep = interval - (System.currentTimeMillis() - started);
         if (sleep > 0L) {
            try {
               Thread.sleep(sleep);
            } catch (InterruptedException e) {
               if (!this.running) {
                  return;
               }
            }
         }
      }

      this.smtc.close();
   }

   private void tick() throws Exception {
      LFConfig c = ConfigManager.get();
      Track t = null;
      if (c.enabled) {
         MediaBackend b = this.backend();
         t = b.poll(c.source);
         int rp = b.repeatMode();
         if (rp >= 0 && rp <= 2 && System.currentTimeMillis() > this.repeatHold) {
            this.repeat = rp;
         }

         this.statusKey = b.statusKey();
      }

      if (t != null && !t.isEmpty()) {
         this.missingSince = -1L;
         String key = t.key();
         if (!key.equals(this.lastKey)) {
            this.lastKey = key;
            this.lastPlaying = t.playing;
            this.pausedUpdatedFt = t.playing ? -2L : t.updatedFt;
            this.stable = 0;
            this.lastOffset = Long.MIN_VALUE;
            this.apiN = 0;
            this.track = t;
            long seq = ++this.songSeq;
            this.lyrics = null;
            this.events.add(new MediaManager.Event(MediaManager.EventType.SONG, t, System.nanoTime()));
            this.cover = null;
            this.commands.add(() -> {
               byte[] b = null;

               try {
                  b = this.backend().cover();
               } catch (Throwable var5x) {
               }

               if (seq == this.songSeq) {
                  this.cover = b;
                  this.coverSeq = seq;
               }
            });
            this.fetchLyrics(t, seq);
         } else {
            Track prev = this.track;
            boolean fresh = t.updatedFt > 0L && t.updatedFt != this.pausedUpdatedFt;
            long nowNs = System.nanoTime();
            if (prev != null && t.playing) {
               if (prev.playing) {
                  long ours = prev.positionNow();
                  long offset = t.positionMs - ours;
                  long sinceMs = this.lastPollNs > 0L ? (nowNs - this.lastPollNs) / 1000000L : 250L;
                  if (this.lastOffset != Long.MIN_VALUE && Math.abs(offset - this.lastOffset) <= 60L + sinceMs / 5L) {
                     this.stable++;
                  } else {
                     this.stable = 0;
                  }

                  this.lastOffset = offset;
                  long diff = Math.abs(offset);
                  boolean seek = prev.rawPositionMs >= 0L
                     && t.rawPositionMs >= 0L
                     && Math.abs(t.rawPositionMs - prev.rawPositionMs) > 400L
                     && t.updatedFt != prev.updatedFt;
                  boolean apiLive = c.source == LFConfig.Source.SPOTIFY && SpotifyWeb.positionFor(t) >= 0L;
                  boolean trusted = seek && this.stable >= 1 || fresh && this.stable >= 2 && !apiLive;
                  if (trusted && diff > (this.forceSync ? 120 : 250)) {
                     LyricsFlow.LOG
                        .debug(
                           "[LyricsFlow sync] re-sync {} ms (ours {} -> windows {}), fresh={}, seek={}, stable={}",
                           offset,
                           ours,
                           t.positionMs,
                           fresh,
                           seek,
                           this.stable
                        );
                     t = Track.at(t, t.positionMs);
                     this.lastOffset = 0L;
                  } else {
                     t = prev.keepClock(t);
                  }
               } else {
                  this.stable = 0;
                  this.lastOffset = Long.MIN_VALUE;
                  if (!fresh && t.rawPositionMs >= 0L) {
                     long from = Math.abs(t.rawPositionMs - prev.positionMs) < 3000L ? prev.positionMs : t.rawPositionMs;
                     t = Track.at(t, from);
                  }
               }
            }

            if (c.source == LFConfig.Source.SPOTIFY && t.playing) {
               SpotifyWeb.start();
               long at = SpotifyWeb.sampleAt();
               long api = SpotifyWeb.positionFor(t);
               if (api >= 0L && at != this.lastApiAt) {
                  this.lastApiAt = at;
                  long off = api - t.positionNow();
                  this.apiOffs[this.apiN++ % this.apiOffs.length] = off;
                  int n = Math.min(this.apiN, this.apiOffs.length);
                  long[] s = Arrays.copyOf(this.apiOffs, n);
                  Arrays.sort(s);
                  long med = s[n / 2];
                  boolean big = Math.abs(off) > 1500L;
                  if (big || n >= 3 && Math.abs(med) > 90L) {
                     LyricsFlow.LOG.debug("[LyricsFlow sync] spotify re-sync {} ms", big ? off : med);
                     t = Track.at(t, t.positionNow() + (big ? off : med));
                     this.apiN = 0;
                  }
               }
            } else {
               this.apiN = 0;
            }

            this.lastPollNs = nowNs;
            this.forceSync = false;
            if (!t.playing) {
               this.pausedUpdatedFt = t.updatedFt;
            }

            this.track = t;
            if (prev != null && t.playing != this.lastPlaying) {
               this.events.add(new MediaManager.Event(t.playing ? MediaManager.EventType.RESUME : MediaManager.EventType.PAUSE, t, System.nanoTime()));
            }

            this.lastPlaying = t.playing;
         }
      } else {
         if (this.track != null) {
            if (this.missingSince < 0L) {
               this.missingSince = System.currentTimeMillis();
            }

            if (System.currentTimeMillis() - this.missingSince > 2500L) {
               this.events.add(new MediaManager.Event(MediaManager.EventType.STOP, this.track, System.nanoTime()));
               this.track = null;
               this.lastKey = "";
            }
         }
      }
   }

   private void fetchLyrics(Track t, long seq) {
      this.lyricsState = MediaManager.LyricsState.SEARCHING;
      this.lyricsPool.submit(() -> {
         Lyrics l = readCache(t);

         for (int attempt = 1; l == null || !l.synced; attempt++) {
            if (seq != this.songSeq) {
               return;
            }

            Lyrics got = null;

            try {
               got = LyricsFetcher.fetch(t.artist, t.title, t.album, t.durationMs);
            } catch (Throwable var11) {
            }

            if (got != null) {
               if (l == null || got.synced) {
                  l = got;
               }

               writeCache(t, got);
            }

            if (l != null && l.synced || attempt >= 5) {
               break;
            }

            long until = System.currentTimeMillis() + 1500L * attempt;

            while (System.currentTimeMillis() < until) {
               if (seq != this.songSeq) {
                  return;
               }

               try {
                  Thread.sleep(100L);
               } catch (InterruptedException e) {
                  return;
               }
            }
         }

         if (seq == this.songSeq) {
            if (l != null && !l.synced) {
               l = null;
            }

            this.lyrics = l;
            this.lyricsState = l == null ? MediaManager.LyricsState.NOT_FOUND : MediaManager.LyricsState.FOUND;
         }
      });
   }

   private static Path cacheFile(Track t) throws Exception {
      Path dir = ConfigManager.dir().resolve("cache");
      Files.createDirectories(dir);
      byte[] h = MessageDigest.getInstance("SHA-1").digest((t.key() + "|v6").getBytes(StandardCharsets.UTF_8));
      return dir.resolve(HexFormat.of().formatHex(h, 0, 10) + ".json");
   }

   private static Lyrics readCache(Track t) {
      try {
         Path f = cacheFile(t);
         if (!Files.exists(f)) {
            return null;
         }

         MediaManager.CacheEntry e = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), MediaManager.CacheEntry.class);
         if (e != null && !e.texts.isEmpty()) {
            List<long[]> times = new ArrayList<>();

            for (int i = 0; i < e.texts.size(); i++) {
               times.add(new long[]{e.starts.get(i), e.ends.get(i)});
            }

            List<List<Lyrics.Word>> words = null;
            if (e.words != null && e.words.size() == e.texts.size()) {
               words = new ArrayList<>();

               for (int i = 0; i < e.words.size(); i++) {
                  List<Lyrics.Word> w = new ArrayList<>();

                  for (int k = 0; k < e.words.get(i).size(); k++) {
                     w.add(Lyrics.Word.of(e.words.get(i).get(k), e.wordStarts.get(i).get(k), e.wordEnds.get(i).get(k)));
                  }

                  words.add(w);
               }
            }

            return new Lyrics(times, e.texts, e.synced, e.source, e.duration, words);
         } else {
            return null;
         }
      } catch (Exception ex) {
         return null;
      }
   }

   private static void writeCache(Track t, Lyrics l) {
      try {
         MediaManager.CacheEntry e = new MediaManager.CacheEntry();
         e.synced = l.synced;
         e.source = l.source;
         e.duration = t.durationMs;
         if (l.wordSynced) {
            e.words = new ArrayList<>();
            e.wordStarts = new ArrayList<>();
            e.wordEnds = new ArrayList<>();
         }

         for (Lyrics.Line line : l.lines) {
            e.starts.add(line.start);
            e.ends.add(line.end);
            e.texts.add(line.text);
            if (l.wordSynced) {
               List<String> w = new ArrayList<>();
               List<Long> ws = new ArrayList<>();
               List<Long> we = new ArrayList<>();

               for (Lyrics.Word x : line.words) {
                  w.add(x.text);
                  ws.add(x.start);
                  we.add(x.end);
               }

               e.words.add(w);
               e.wordStarts.add(ws);
               e.wordEnds.add(we);
            }
         }

         Files.writeString(cacheFile(t), GSON.toJson(e), StandardCharsets.UTF_8);
      } catch (Exception var10) {
      }
   }

   private static final class CacheEntry {
      boolean synced;
      String source;
      long duration;
      List<Long> starts = new ArrayList<>();
      List<Long> ends = new ArrayList<>();
      List<String> texts = new ArrayList<>();
      List<List<String>> words;
      List<List<Long>> wordStarts;
      List<List<Long>> wordEnds;
   }

   public record Event(MediaManager.EventType type, Track track, long atNanos) {
   }

   public enum EventType {
      SONG,
      PAUSE,
      RESUME,
      STOP;
   }

   public enum LyricsState {
      NONE,
      SEARCHING,
      FOUND,
      NOT_FOUND;
   }
}
