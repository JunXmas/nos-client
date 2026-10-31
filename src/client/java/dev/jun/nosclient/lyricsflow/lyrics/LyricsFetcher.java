package dev.jun.nosclient.lyricsflow.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.jun.nosclient.lyricsflow.media.TitleCleaner;
import dev.jun.nosclient.lyricsflow.util.Http;
import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsFetcher {
   private static final Pattern LRC = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
   private static final ExecutorService POOL = Executors.newFixedThreadPool(6, r -> {
      Thread t = new Thread(r, "LyricsFlow-Search");
      t.setDaemon(true);
      return t;
   });
   private static final Pattern SPEED = Pattern.compile("(?i)\\b(sped\\s*up|speed\\s*up|slowed(\\s*(down|\\+|and|&)?\\s*reverb)?|nightcore|daycore)\\b");
   private static final Pattern REMIX = Pattern.compile("(?i)\\b(remix|rmx|bootleg|mashup|flip|vip mix)\\b");
   private static final ThreadLocal<Double> MAX_DIFF = ThreadLocal.withInitial(() -> 3.0);
   private static final String MXM = "https://apic-desktop.musixmatch.com/ws/1.1/";
   private static final Map<String, String> MXM_HEADERS = Map.of("authority", "apic-desktop.musixmatch.com", "cookie", "AWSELBCORS=0; AWSELB=0");
   private static volatile String mxmToken;
   private static volatile long mxmTokenAt;
   private static final Map<String, String> NE_HEADERS = Map.of(
      "Referer",
      "https://music.163.com/",
      "User-Agent",
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
   );
   private static final Pattern YRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)\\](.*)$");
   private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)([^(]*)");
   private static final Pattern WORD_TAG = Pattern.compile("<(\\d+):(\\d+)(?:\\.(\\d+))?>");

   private LyricsFetcher() {
   }

   public static Lyrics fetch(String artist, String title, String album, long durationMs) {
      long deadline = System.currentTimeMillis() + 15000L;
      List<String[]> variants = TitleCleaner.variants(artist, title);
      List<Callable<Lyrics>> first = new ArrayList<>();
      List<Callable<Lyrics>> more = new ArrayList<>();
      String[] v0 = variants.get(0);
      String[] vLast = variants.get(variants.size() - 1);
      first.add(() -> exact(v0, durationMs));
      first.add(() -> search(v0, durationMs));
      first.add(() -> bestOf(Http.get("https://lrclib.net/api/search?" + Http.query(Map.of("q", (vLast[0] + " " + vLast[1]).trim())), null), durationMs, vLast));
      first.add(() -> bestByTitle(Http.get("https://lrclib.net/api/search?" + Http.query(Map.of("track_name", v0[1])), null), durationMs, v0));
      first.add(() -> netease(v0, durationMs));

      for (int i = 1; i < variants.size(); i++) {
         String[] v = variants.get(i);
         more.add(() -> exact(v, durationMs));
         more.add(() -> search(v, durationMs));
         if (i == 1) {
            more.add(() -> bestByTitle(Http.get("https://lrclib.net/api/search?" + Http.query(Map.of("track_name", v[1])), null), durationMs, v));
            more.add(() -> netease(v, durationMs));
         }
      }

      String raw = title == null ? "" : title;
      if (SPEED.matcher(raw).find()) {
         String[] clean = new String[]{TitleCleaner.mainArtist(artist), TitleCleaner.clean(SPEED.matcher(raw).replaceAll(" "))};
         List<Callable<Lyrics>> sp = new ArrayList<>();
         sp.add(() -> exact(v0, durationMs));
         sp.add(() -> stretched(clean, durationMs));
         return firstSynced(sp, deadline);
      }

      boolean remix = REMIX.matcher(raw).find();
      Future<Lyrics> wordsF = POOL.submit(() -> {
         Http.DEADLINE.set(deadline);
         if (remix) {
            MAX_DIFF.set(2.0);
         }

         try {
            return neteaseWords(v0, durationMs);
         } catch (Exception e) {
            return null;
         } finally {
            Http.DEADLINE.remove();
            MAX_DIFF.remove();
         }
      });
      Future<Lyrics> mxmF = POOL.submit(() -> {
         Http.DEADLINE.set(deadline);
         if (remix) {
            MAX_DIFF.set(2.0);
         }

         try {
            return musixmatchWords(v0, durationMs);
         } catch (Exception e) {
            return null;
         } finally {
            Http.DEADLINE.remove();
            MAX_DIFF.remove();
         }
      });
      Lyrics l = firstSynced(first, deadline, remix);
      if (l == null && !more.isEmpty()) {
         l = firstSynced(more, deadline, remix);
      }

      boolean fromMxm = false;

      try {
         Lyrics w = mxmF.get(Math.max(1L, Math.min(4000L, deadline - System.currentTimeMillis())), TimeUnit.MILLISECONDS);
         if (w != null && w.lines.size() >= 3) {
            l = w;
            fromMxm = true;
         }
      } catch (Exception ignored) {
         mxmF.cancel(true);
      }

      if (!fromMxm && (l == null || !l.wordSynced)) {
         try {
            Lyrics w = wordsF.get(Math.max(1L, Math.min(4000L, deadline - System.currentTimeMillis())), TimeUnit.MILLISECONDS);
            if (w != null && w.wordSynced && w.lines.size() >= 3) {
               l = w;
            }
         } catch (Exception ignored) {
            wordsF.cancel(true);
         }
      }

      if (l == null && System.currentTimeMillis() < deadline - 1500L) {
         l = firstSynced(first, deadline, 10.0);
      }

      if (l != null && !sameScript(v0, l)) {
         l = null;
      }

      return l;
   }

   private static Lyrics neonWay(String artist, String title, String album, long durationMs) throws Exception {
      if (title != null && !title.isBlank()) {
         String ar = artist == null ? "" : artist;
         String synced = null;
         Map<String, String> q = new LinkedHashMap<>();
         q.put("track_name", title);
         q.put("artist_name", ar);
         if (album != null && !album.isBlank()) {
            q.put("album_name", album);
         }

         if (durationMs > 0L) {
            q.put("duration", String.valueOf(Math.round(durationMs / 1000.0)));
         }

         Http.Response r = Http.get("https://lrclib.net/api/get?" + Http.query(q), null);
         if (r.code() == 200) {
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            String v = str(o, "syncedLyrics");
            if (!v.isBlank()) {
               synced = v;
            }
         }

         if (synced == null) {
            String clean = title.replaceAll("\\s*[-–]\\s.*$", "").replaceAll("\\s*[(\\[].*?[)\\]]", "").trim();
            Map<String, String> sq = new LinkedHashMap<>();
            sq.put("track_name", clean.isEmpty() ? title : clean);
            sq.put("artist_name", ar);
            Http.Response sr = Http.get("https://lrclib.net/api/search?" + Http.query(sq), null);
            if (sr.code() == 200) {
               double best = Double.MAX_VALUE;

               for (JsonElement e : JsonParser.parseString(sr.body()).getAsJsonArray()) {
                  JsonObject o = e.getAsJsonObject();
                  String sl = str(o, "syncedLyrics");
                  if (!sl.isBlank()) {
                     double d = durationMs <= 0L
                        ? 0.0
                        : (o.has("duration") && !o.get("duration").isJsonNull() ? Math.abs(o.get("duration").getAsDouble() - durationMs / 1000.0) : 30.0);
                     if (d < best && d < 8.0) {
                        best = d;
                        synced = sl;
                     }
                  }
               }
            }
         }

         if (synced == null) {
            return null;
         }

         Lyrics l = parseLrc(synced, durationMs);
         return l != null && !sameScript(new String[]{ar, title}, l) ? null : l;
      } else {
         return null;
      }
   }

   private static double maxDiff() {
      return MAX_DIFF.get();
   }

   private static String mxmToken(boolean fresh) throws Exception {
      String t = mxmToken;
      if (!fresh && t != null && System.currentTimeMillis() - mxmTokenAt < 21600000L) {
         return t;
      } else {
         Http.Response r = Http.get(
            "https://apic-desktop.musixmatch.com/ws/1.1/token.get?app_id=web-desktop-app-v1.0&t=" + Long.toString(System.nanoTime(), 36), MXM_HEADERS
         );
         if (r.code() != 200) {
            return null;
         } else {
            JsonObject msg = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("message");
            if (msg.getAsJsonObject("header").get("status_code").getAsInt() != 200) {
               return null;
            } else {
               t = msg.getAsJsonObject("body").get("user_token").getAsString();
               if (t != null && !t.isBlank() && !t.startsWith("UpgradeOnly")) {
                  mxmToken = t;
                  mxmTokenAt = System.currentTimeMillis();
                  return t;
               } else {
                  return null;
               }
            }
         }
      }
   }

   private static JsonObject mxmCall(String method, Map<String, String> params) throws Exception {
      for (int attempt = 0; attempt < 2; attempt++) {
         String token = mxmToken(attempt > 0);
         if (token == null) {
            return null;
         }

         Map<String, String> q = new LinkedHashMap<>(params);
         q.put("app_id", "web-desktop-app-v1.0");
         q.put("usertoken", token);
         q.put("t", Long.toString(System.nanoTime(), 36));
         Http.Response r = Http.get("https://apic-desktop.musixmatch.com/ws/1.1/" + method + "?" + Http.query(q), MXM_HEADERS);
         if (r.code() != 200) {
            return null;
         }

         JsonObject msg = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonObject("message");
         int code = msg.getAsJsonObject("header").get("status_code").getAsInt();
         if (code != 401) {
            if (code == 200 && msg.get("body").isJsonObject()) {
               return msg.getAsJsonObject("body");
            }

            return null;
         }

         mxmToken = null;
      }

      return null;
   }

   private static Lyrics musixmatchWords(String[] v, long durationMs) throws Exception {
      JsonObject body = mxmCall("matcher.track.get", Map.of("q_artist", v[0], "q_track", v[1]));
      if (body != null && body.has("track")) {
         JsonObject tr = body.getAsJsonObject("track");
         String gotTitle = norm(str(tr, "track_name"));
         String gotArtist = norm(str(tr, "artist_name"));
         String wantTitle = norm(v[1]);
         String wantArtist = norm(v[0]);
         boolean titleOk = !wantTitle.isEmpty() && (gotTitle.equals(wantTitle) || gotTitle.startsWith(wantTitle) || wantTitle.startsWith(gotTitle));
         boolean artistOk = wantArtist.isEmpty() || gotArtist.contains(wantArtist) || wantArtist.contains(gotArtist);
         if (titleOk && artistOk) {
            if (durationMs > 0L && tr.has("track_length") && tr.get("track_length").getAsDouble() > 0.0) {
               double diff = Math.abs(tr.get("track_length").getAsDouble() - durationMs / 1000.0);
               if (diff > maxDiff()) {
                  return null;
               }
            }

            String id = tr.get("track_id").getAsString();
            if (tr.has("has_richsync") && tr.get("has_richsync").getAsInt() == 1) {
               JsonObject rs = mxmCall("track.richsync.get", Map.of("track_id", id));
               if (rs != null && rs.has("richsync")) {
                  Lyrics l = parseRichsync(rs.getAsJsonObject("richsync").get("richsync_body").getAsString(), durationMs);
                  if (l != null && sameScript(v, l)) {
                     return l;
                  }
               }
            }

            if (tr.has("has_subtitles") && tr.get("has_subtitles").getAsInt() == 1) {
               JsonObject st = mxmCall("track.subtitle.get", Map.of("track_id", id, "subtitle_format", "lrc"));
               if (st != null && st.has("subtitle")) {
                  Lyrics l = parseLrc(st.getAsJsonObject("subtitle").get("subtitle_body").getAsString(), durationMs);
                  if (l != null && l.synced && l.lines.size() >= 3 && sameScript(v, l)) {
                     return l;
                  }
               }
            }

            return null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static Lyrics parseRichsync(String json, long durationMs) {
      JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
      List<long[]> times = new ArrayList<>();
      List<String> texts = new ArrayList<>();
      List<List<Lyrics.Word>> words = new ArrayList<>();

      for (JsonElement e : arr) {
         JsonObject o = e.getAsJsonObject();
         double ts = o.get("ts").getAsDouble();
         double te = o.get("te").getAsDouble();
         JsonArray parts = o.getAsJsonArray("l");
         List<Lyrics.Word> ws = new ArrayList<>();
         StringBuilder cur = new StringBuilder();
         double curStart = -1.0;
         List<double[]> starts = new ArrayList<>();
         List<String> wtext = new ArrayList<>();

         for (JsonElement pe : parts) {
            JsonObject p = pe.getAsJsonObject();
            String c = p.get("c").getAsString();
            double at = ts + p.get("o").getAsDouble();
            if (c.isBlank()) {
               if (cur.length() > 0) {
                  wtext.add(cur.toString());
                  starts.add(new double[]{curStart});
                  cur.setLength(0);
               }
            } else {
               if (cur.length() == 0) {
                  curStart = at;
               }

               cur.append(c.strip());
               if (c.endsWith(" ")) {
                  wtext.add(cur.toString());
                  starts.add(new double[]{curStart});
                  cur.setLength(0);
               }
            }
         }

         if (cur.length() > 0) {
            wtext.add(cur.toString());
            starts.add(new double[]{curStart});
         }

         if (!wtext.isEmpty()) {
            for (int i = 0; i < wtext.size(); i++) {
               long s0 = Math.round(starts.get(i)[0] * 1000.0);
               long e0 = i + 1 < wtext.size() ? Math.round(starts.get(i + 1)[0] * 1000.0) : Math.round(te * 1000.0);
               ws.add(Lyrics.Word.of(wtext.get(i), s0, Math.max(s0 + 60L, e0)));
            }

            String text = o.has("x") ? o.get("x").getAsString().trim() : String.join(" ", wtext);
            times.add(new long[]{Math.round(ts * 1000.0), Math.round(te * 1000.0)});
            texts.add(text.isEmpty() ? String.join(" ", wtext) : text);
            words.add(ws);
         }
      }

      return texts.size() < 3 ? null : new Lyrics(times, texts, true, "Musixmatch", durationMs, words);
   }

   private static Lyrics stretched(String[] v, long durationMs) throws Exception {
      if (durationMs <= 0L) {
         return null;
      }

      MAX_DIFF.set(1.0E9);

      try {
         Map<String, String> q = new LinkedHashMap<>();
         q.put("track_name", v[1]);
         if (!v[0].isEmpty()) {
            q.put("artist_name", v[0]);
         }

         Http.Response r = Http.get("https://lrclib.net/api/search?" + Http.query(q), null);
         if (r.code() != 200) {
            return null;
         }

         String wantTitle = norm(v[1]);
         String wantArtist = norm(v[0]);
         JsonObject best = null;
         double bestScore = Double.MAX_VALUE;

         for (JsonElement e : JsonParser.parseString(r.body()).getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull() && o.has("duration") && !o.get("duration").isJsonNull()) {
               String name = norm(str(o, "trackName"));
               String art = norm(str(o, "artistName"));
               if (!SPEED.matcher(str(o, "trackName")).find()
                  && (name.equals(wantTitle) || name.startsWith(wantTitle) || wantTitle.startsWith(name))
                  && (wantArtist.isEmpty() || art.contains(wantArtist) || wantArtist.contains(art))) {
                  double k = durationMs / (o.get("duration").getAsDouble() * 1000.0);
                  if (!(k < 0.55) && !(k > 1.6)) {
                     double score = (name.equals(wantTitle) ? 0 : 10) + (REMIX.matcher(str(o, "trackName")).find() ? 50 : 0);
                     if (score < bestScore) {
                        bestScore = score;
                        best = o;
                     }
                  }
               }
            }
         }

         if (best == null) {
            return null;
         }

         long orig = (long)(best.get("duration").getAsDouble() * 1000.0);
         Lyrics l = parseLrc(best.get("syncedLyrics").getAsString(), orig);
         return l == null ? null : scaled(l, (double)durationMs / orig, durationMs);
      } finally {
         MAX_DIFF.remove();
      }
   }

   private static Lyrics scaled(Lyrics l, double k, long durationMs) {
      List<long[]> times = new ArrayList<>();
      List<String> texts = new ArrayList<>();
      List<List<Lyrics.Word>> words = l.wordSynced ? new ArrayList<>() : null;

      for (Lyrics.Line line : l.lines) {
         times.add(new long[]{Math.round(line.start * k), Math.round(line.end * k)});
         texts.add(line.text);
         if (words != null) {
            List<Lyrics.Word> w = new ArrayList<>();

            for (Lyrics.Word x : line.words) {
               w.add(Lyrics.Word.of(x.text, Math.round(x.start * k), Math.round(x.end * k)));
            }

            words.add(w);
         }
      }

      return new Lyrics(times, texts, true, l.source + " (stretched)", durationMs, words);
   }

   private static Lyrics firstSynced(List<Callable<Lyrics>> tasks, long deadline) {
      return firstSynced(tasks, deadline, false);
   }

   private static Lyrics firstSynced(List<Callable<Lyrics>> tasks, long deadline, boolean strict) {
      return firstSynced(tasks, deadline, strict ? 2.0 : 3.0);
   }

   private static Lyrics firstSynced(List<Callable<Lyrics>> tasks, long deadline, double maxDiff) {
      List<Future<Lyrics>> fs = new ArrayList<>();

      for (Callable<Lyrics> t : tasks) {
         fs.add(POOL.submit(() -> {
            Http.DEADLINE.set(deadline);
            MAX_DIFF.set(maxDiff);

            try {
               Lyrics l = t.call();
               return l != null && l.synced ? l : null;
            } catch (Exception e) {
               return null;
            } finally {
               Http.DEADLINE.remove();
               MAX_DIFF.remove();
            }
         }));
      }

      Lyrics found = null;

      for (Future<Lyrics> f : fs) {
         if (found != null) {
            f.cancel(true);
         } else {
            try {
               long wait = Math.max(1L, deadline - System.currentTimeMillis());
               found = f.get(wait, TimeUnit.MILLISECONDS);
            } catch (Exception var11) {
            }
         }
      }

      return found;
   }

   private static Lyrics exact(String[] v, long durationMs) throws Exception {
      Map<String, String> q = new LinkedHashMap<>();
      q.put("artist_name", v[0]);
      q.put("track_name", v[1]);
      if (durationMs > 0L) {
         q.put("duration", String.valueOf(Math.round(durationMs / 1000.0)));
      }

      Http.Response r = Http.get("https://lrclib.net/api/get?" + Http.query(q), null);
      return r.code() != 200 ? null : fromLrclib(JsonParser.parseString(r.body()).getAsJsonObject(), durationMs);
   }

   private static Lyrics search(String[] v, long durationMs) throws Exception {
      Map<String, String> q = new LinkedHashMap<>();
      if (v[0].isEmpty()) {
         q.put("q", v[1]);
      } else {
         q.put("track_name", v[1]);
         q.put("artist_name", v[0]);
      }

      return bestOf(Http.get("https://lrclib.net/api/search?" + Http.query(q), null), durationMs, v);
   }

   private static Lyrics bestOf(Http.Response r, long durationMs, String[] v) {
      if (r.code() != 200) {
         return null;
      }

      JsonArray arr = JsonParser.parseString(r.body()).getAsJsonArray();
      JsonObject best = null;
      double bestScore = Double.MAX_VALUE;
      String wantTitle = norm(v[1]);
      String wantArtist = norm(TitleCleaner.mainArtist(v[0]));

      for (JsonElement e : arr) {
         JsonObject o = e.getAsJsonObject();
         if (!o.has("instrumental") || !o.get("instrumental").getAsBoolean()) {
            boolean synced = o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull();
            double score = synced ? 0.0 : 1000.0;
            if (durationMs > 0L && o.has("duration") && !o.get("duration").isJsonNull()) {
               double dd = Math.abs(o.get("duration").getAsDouble() - durationMs / 1000.0);
               if (dd > maxDiff()) {
                  continue;
               }

               score += dd * 4.0;
            }

            String name = norm(str(o, "trackName"));
            String art = norm(str(o, "artistName"));
            boolean titleOk = !name.isEmpty() && (name.contains(wantTitle) || wantTitle.contains(name));
            boolean artistOk = wantArtist.isEmpty() || !art.isEmpty() && (art.contains(wantArtist) || wantArtist.contains(art));
            if (titleOk || artistOk) {
               if (!titleOk) {
                  score += 200.0;
               }

               if (!artistOk) {
                  score += 300.0;
               }

               if (score < bestScore) {
                  bestScore = score;
                  best = o;
               }
            }
         }
      }

      return best != null && !(bestScore > 1200.0) ? fromLrclib(best, durationMs) : null;
   }

   private static Lyrics bestByTitle(Http.Response r, long durationMs, String[] v) {
      if (r.code() != 200) {
         return null;
      }

      JsonArray arr = JsonParser.parseString(r.body()).getAsJsonArray();
      String wantArtist = norm(TitleCleaner.mainArtist(v[0]));
      String wantTitle = norm(v[1]);
      JsonObject best = null;
      double bestScore = Double.MAX_VALUE;

      for (JsonElement e : arr) {
         JsonObject o = e.getAsJsonObject();
         if (!o.has("instrumental") || !o.get("instrumental").getAsBoolean()) {
            String name = str(o, "trackName");
            String art = str(o, "artistName");
            if (norm(name).equals(wantTitle) || norm(name).startsWith(wantTitle)) {
               boolean artistOk = wantArtist.isEmpty() || norm(art).contains(wantArtist) || wantArtist.contains(norm(art));
               double dd = durationMs > 0L && o.has("duration") && !o.get("duration").isJsonNull()
                  ? Math.abs(o.get("duration").getAsDouble() - durationMs / 1000.0)
                  : 0.0;
               if ((artistOk || !(dd > 2.5)) && !(dd > maxDiff())) {
                  boolean synced = o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull();
                  double score = (synced ? 0 : 500) + dd * 4.0 + (artistOk ? 0 : 50);
                  if (score < bestScore) {
                     bestScore = score;
                     best = o;
                  }
               }
            }
         }
      }

      return best != null && (durationMs <= 0L || !(bestScore > 560.0)) ? fromLrclib(best, durationMs) : null;
   }

   private static Lyrics netease(String[] v, long durationMs) {
      try {
         String q = (v[0] + " " + v[1]).trim();
         Http.Response r = Http.get("https://music.163.com/api/search/get?" + Http.query(Map.of("s", q, "type", "1", "limit", "10")), NE_HEADERS);
         if (r.code() != 200) {
            return null;
         }

         JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
         if (root.has("result") && root.getAsJsonObject("result").has("songs")) {
            String wantTitle = norm(v[1]);
            String wantArtist = norm(TitleCleaner.mainArtist(v[0]));
            long bestId = -1L;
            double bestScore = Double.MAX_VALUE;

            for (JsonElement e : root.getAsJsonObject("result").getAsJsonArray("songs")) {
               JsonObject o = e.getAsJsonObject();
               String name = norm(str(o, "name"));
               if (name.equals(wantTitle) || name.startsWith(wantTitle) || wantTitle.startsWith(name)) {
                  StringBuilder arts = new StringBuilder();
                  if (o.has("artists")) {
                     for (JsonElement a : o.getAsJsonArray("artists")) {
                        arts.append(norm(str(a.getAsJsonObject(), "name"))).append('|');
                     }
                  }

                  boolean artistOk = wantArtist.isEmpty() || arts.toString().contains(wantArtist);
                  double dd = durationMs > 0L && o.has("duration") ? Math.abs(o.get("duration").getAsLong() - durationMs) / 1000.0 : 0.0;
                  if ((artistOk || !(dd > 2.5)) && !(dd > maxDiff())) {
                     double score = dd * 4.0 + (artistOk ? 0 : 50) + (name.equals(wantTitle) ? 0 : 20);
                     if (score < bestScore) {
                        bestScore = score;
                        bestId = o.get("id").getAsLong();
                     }
                  }
               }
            }

            if (bestId < 0L) {
               return null;
            }

            Http.Response lr = Http.get("https://music.163.com/api/song/lyric?id=" + bestId + "&lv=1&kv=1&tv=-1", NE_HEADERS);
            if (lr.code() != 200) {
               return null;
            }

            JsonObject lo = JsonParser.parseString(lr.body()).getAsJsonObject();
            if (lo.has("lrc") && lo.getAsJsonObject("lrc").has("lyric")) {
               String lrc = lo.getAsJsonObject("lrc").get("lyric").getAsString();
               StringBuilder clean = new StringBuilder();

               for (String line : lrc.split("\\r?\\n")) {
                  String text = line.replaceAll("^(\\[[^\\]]*\\])+", "");
                  if (!text.matches(".*[\\u4e00-\\u9fff].*[:：].*") && !text.matches("(?i)\\s*(lyrics|composer|producer|arranger|written by)\\s*[:：].*")) {
                     clean.append(line).append('\n');
                  }
               }

               Lyrics l = parseLrc(clean.toString(), durationMs);
               return l != null && l.lines.size() >= 3 ? l : null;
            } else {
               return null;
            }
         } else {
            return null;
         }
      } catch (Exception e) {
         return null;
      }
   }

   private static long neteaseId(String[] v, long durationMs) throws Exception {
      String q = (v[0] + " " + v[1]).trim();
      Http.Response r = Http.get("https://music.163.com/api/search/get?" + Http.query(Map.of("s", q, "type", "1", "limit", "10")), NE_HEADERS);
      if (r.code() != 200) {
         return -1L;
      }

      JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
      if (root.has("result") && root.getAsJsonObject("result").has("songs")) {
         String wantTitle = norm(v[1]);
         String wantArtist = norm(TitleCleaner.mainArtist(v[0]));
         long bestId = -1L;
         double bestScore = Double.MAX_VALUE;

         for (JsonElement e : root.getAsJsonObject("result").getAsJsonArray("songs")) {
            JsonObject o = e.getAsJsonObject();
            String name = norm(str(o, "name"));
            if (name.equals(wantTitle) || name.startsWith(wantTitle) || wantTitle.startsWith(name)) {
               StringBuilder arts = new StringBuilder();
               if (o.has("artists")) {
                  for (JsonElement a : o.getAsJsonArray("artists")) {
                     arts.append(norm(str(a.getAsJsonObject(), "name"))).append('|');
                  }
               }

               boolean artistOk = wantArtist.isEmpty() || arts.toString().contains(wantArtist);
               double dd = durationMs > 0L && o.has("duration") ? Math.abs(o.get("duration").getAsLong() - durationMs) / 1000.0 : 0.0;
               if (artistOk && !(dd > Math.min(maxDiff(), 6.0))) {
                  double score = dd * 4.0 + (name.equals(wantTitle) ? 0 : 20);
                  if (score < bestScore) {
                     bestScore = score;
                     bestId = o.get("id").getAsLong();
                  }
               }
            }
         }

         return bestId;
      } else {
         return -1L;
      }
   }

   private static Lyrics neteaseWords(String[] v, long durationMs) throws Exception {
      long id = neteaseId(v, durationMs);
      if (id < 0L) {
         return null;
      } else {
         Http.Response lr = Http.get("https://music.163.com/api/song/lyric/v1?id=" + id + "&cp=false&tv=0&lv=0&rv=0&kv=0&yv=0&ytv=0&yrv=0", NE_HEADERS);
         if (lr.code() != 200) {
            return null;
         } else {
            JsonObject lo = JsonParser.parseString(lr.body()).getAsJsonObject();
            if (lo.has("yrc") && lo.getAsJsonObject("yrc").has("lyric")) {
               Lyrics y = parseYrc(lo.getAsJsonObject("yrc").get("lyric").getAsString(), durationMs);
               return y != null && sameScript(v, y) ? y : null;
            } else {
               return null;
            }
         }
      }
   }

   public static Lyrics parseYrc(String yrc, long durationMs) {
      List<long[]> times = new ArrayList<>();
      List<String> texts = new ArrayList<>();
      List<List<Lyrics.Word>> words = new ArrayList<>();

      for (String raw : yrc.split("\\r?\\n")) {
         Matcher m = YRC_LINE.matcher(raw.trim());
         if (m.matches()) {
            long ls = Long.parseLong(m.group(1));
            long ld = Long.parseLong(m.group(2));
            Matcher w = YRC_WORD.matcher(m.group(3));
            List<Lyrics.Word> lw = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            StringBuilder cur = new StringBuilder();
            long cs = -1L;
            long ce = -1L;

            while (w.find()) {
               long ws = Long.parseLong(w.group(1));
               long wd = Long.parseLong(w.group(2));
               String piece = w.group(3);
               if (cs < 0L) {
                  cs = ws;
               }

               cur.append(piece);
               ce = ws + wd;
               if (piece.endsWith(" ") || piece.matches(".*[\\u3000-\\u9fff\\uac00-\\ud7af].*")) {
                  String t = cur.toString().trim();
                  if (!t.isEmpty()) {
                     lw.add(Lyrics.Word.of(t, cs, ce));
                  }

                  cur.setLength(0);
                  cs = -1L;
               }

               text.append(piece);
            }

            String t = cur.toString().trim();
            if (!t.isEmpty()) {
               lw.add(Lyrics.Word.of(t, cs, ce));
            }

            String line = text.toString().trim();
            if (!line.isEmpty() && !lw.isEmpty() && !line.matches(".*[\\u4e00-\\u9fff].*[:：].*")) {
               times.add(new long[]{ls, ls + ld});
               texts.add(line);
               words.add(lw);
            }
         }
      }

      return texts.size() < 3 ? null : new Lyrics(times, texts, true, "netease words", durationMs, words);
   }

   private static String str(JsonObject o, String k) {
      return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
   }

   static boolean sameScript(String[] v, Lyrics l) {
      String title = v[0] + " " + v[1];
      int tl = 0;
      int tn = 0;

      for (int i = 0; i < title.length(); i++) {
         char ch = title.charAt(i);
         if (Character.isLetter(ch)) {
            if (UnicodeScript.of(ch) == UnicodeScript.LATIN) {
               tl++;
            } else {
               tn++;
            }
         }
      }

      if (tl != 0 && tn <= tl) {
         int ll = 0;
         int ln = 0;

         for (Lyrics.Line line : l.lines) {
            for (int i = 0; i < line.text.length(); i++) {
               char ch = line.text.charAt(i);
               if (Character.isLetter(ch)) {
                  if (UnicodeScript.of(ch) == UnicodeScript.LATIN) {
                     ll++;
                  } else {
                     ln++;
                  }
               }
            }
         }

         return ll + ln == 0 || ln <= (ll + ln) * 0.5;
      } else {
         return true;
      }
   }

   private static String norm(String s) {
      String n = TitleCleaner.fold(s).replaceAll("[^\\p{L}\\p{N}]+", "");
      if (n.isEmpty() && s != null) {
         n = s.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
      }

      return n;
   }

   private static Lyrics fromLrclib(JsonObject o, long durationMs) {
      if (durationMs <= 0L && o.has("duration") && !o.get("duration").isJsonNull()) {
         durationMs = (long)(o.get("duration").getAsDouble() * 1000.0);
      }

      if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) {
         Lyrics l = parseLrc(o.get("syncedLyrics").getAsString(), durationMs);
         if (l != null) {
            return l;
         }
      }

      return o.has("plainLyrics") && !o.get("plainLyrics").isJsonNull() ? spread(o.get("plainLyrics").getAsString(), durationMs, "lrclib") : null;
   }

   public static Lyrics parseLrc(String lrc, long durationMs) {
      List<long[]> times = new ArrayList<>();
      List<String> texts = new ArrayList<>();
      List<Object[]> entries = new ArrayList<>();

      for (String raw : lrc.split("\\r?\\n")) {
         Matcher m = LRC.matcher(raw);
         List<Long> stamps = new ArrayList<>();

         int end;
         for (end = 0; m.find() && m.start() == end; end = m.end()) {
            long ms = Long.parseLong(m.group(1)) * 60000L + Long.parseLong(m.group(2)) * 1000L;
            if (m.group(3) != null) {
               String f = m.group(3);
               ms += f.length() == 1 ? Long.parseLong(f) * 100L : (f.length() == 2 ? Long.parseLong(f) * 10L : Long.parseLong(f));
            }

            stamps.add(ms);
         }

         if (!stamps.isEmpty()) {
            String body = raw.substring(end);
            String text = body.replaceAll("<\\d+:\\d+(?:\\.\\d+)?>", "").trim();
            List<Lyrics.Word> words = enhancedWords(body);

            for (long s : stamps) {
               entries.add(new Object[]{s, text, stamps.size() == 1 ? words : null});
            }
         }
      }

      entries.sort((a, b) -> Long.compare((Long)a[0], (Long)b[0]));
      List<List<Lyrics.Word>> wl = new ArrayList<>();
      boolean anyWords = false;

      for (int i = 0; i < entries.size(); i++) {
         String t = (String)entries.get(i)[1];
         if (!t.isEmpty() && !t.equals("♪")) {
            long s = (Long)entries.get(i)[0];
            long e = i + 1 < entries.size() ? (Long)entries.get(i + 1)[0] : -1L;
            times.add(e > s ? new long[]{s, e} : new long[]{s});
            texts.add(t);
            List<Lyrics.Word> w = (List<Lyrics.Word>)((Object[])entries.get(i))[2];
            if (w != null && !w.isEmpty()) {
               anyWords = true;
               Lyrics.Word last = w.get(w.size() - 1);
               if (last.end <= last.start) {
                  w.set(w.size() - 1, Lyrics.Word.of(last.text, last.start, e > last.start ? Math.min(e, last.start + 1500L) : last.start + 600L));
               }
            }

            wl.add(w);
         }
      }

      return texts.isEmpty() ? null : new Lyrics(times, texts, true, "lrclib", durationMs, anyWords ? wl : null);
   }

   private static List<Lyrics.Word> enhancedWords(String body) {
      Matcher m = WORD_TAG.matcher(body);
      List<long[]> at = new ArrayList<>();
      List<int[]> pos = new ArrayList<>();

      while (m.find()) {
         long ms = Long.parseLong(m.group(1)) * 60000L + Long.parseLong(m.group(2)) * 1000L;
         if (m.group(3) != null) {
            String f = m.group(3);
            ms += f.length() == 1 ? Long.parseLong(f) * 100L : (f.length() == 2 ? Long.parseLong(f) * 10L : Long.parseLong(f.substring(0, 3)));
         }

         at.add(new long[]{ms});
         pos.add(new int[]{m.start(), m.end()});
      }

      if (at.size() < 2) {
         return null;
      }

      List<Lyrics.Word> out = new ArrayList<>();

      for (int i = 0; i < at.size(); i++) {
         int from = pos.get(i)[1];
         int to = i + 1 < pos.size() ? pos.get(i + 1)[0] : body.length();
         String seg = body.substring(from, to).trim();
         if (!seg.isEmpty()) {
            String[] parts = seg.split("\\s+");
            long st = at.get(i)[0];
            long en = i + 1 < at.size() ? at.get(i + 1)[0] : st + parts.length * 450L;

            for (int k = 0; k < parts.length; k++) {
               long a = st + (en - st) * k / parts.length;
               long b = st + (en - st) * (k + 1) / parts.length;
               out.add(Lyrics.Word.of(parts[k], a, b));
            }
         }
      }

      return out.isEmpty() ? null : out;
   }

   public static Lyrics spread(String plain, long durationMs, String source) {
      List<String> lines = new ArrayList<>();

      for (String s : plain.split("\\r?\\n")) {
         String t = s.trim();
         if (!t.isEmpty() && !t.startsWith("[")) {
            lines.add(t);
         }
      }

      if (lines.isEmpty()) {
         return null;
      }

      long start = 9000L;
      long end = durationMs > 30000L ? durationMs - 6000L : start + lines.size() * 3600L;
      double step = Math.max(1800.0, (double)(end - start) / lines.size());
      List<long[]> times = new ArrayList<>();

      for (int i = 0; i < lines.size(); i++) {
         times.add(new long[]{start + (long)(i * step)});
      }

      return new Lyrics(times, lines, false, source, durationMs);
   }
}
