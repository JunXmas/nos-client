package dev.jun.nosclient.media;

import com.google.gson.Gson;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.media.lyrics.Lyrics;
import dev.jun.nosclient.storage.ClientFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class LyricsCache {
    private static final Gson GSON = new Gson();
    private final Path directory;

    public LyricsCache(Path directory) {
        this.directory = directory;
    }

    public Path cacheFile(Track t) throws Exception {
        Path dir = directory;
        Files.createDirectories(dir);
        byte[] h =
                MessageDigest.getInstance("SHA-1")
                        .digest((t.key() + "|v6").getBytes(StandardCharsets.UTF_8));
        return dir.resolve(HexFormat.of().formatHex(h, 0, 10) + ".json");
    }

    public Lyrics readCache(Track t) {
        try {
            Path f = cacheFile(t);
            if (!Files.exists(f)) {
                return null;
            }

            CacheEntry e = GSON.fromJson(ClientFiles.read(f), CacheEntry.class);
            validate(e);
            if (e != null && !e.texts.isEmpty()) {
                List<long[]> times = new ArrayList<>();

                for (int i = 0; i < e.texts.size(); i++) {
                    times.add(new long[] {e.starts.get(i), e.ends.get(i)});
                }

                List<List<Lyrics.Word>> words = null;
                if (e.words != null && e.words.size() == e.texts.size()) {
                    words = new ArrayList<>();

                    for (int i = 0; i < e.words.size(); i++) {
                        List<Lyrics.Word> w = new ArrayList<>();

                        for (int k = 0; k < e.words.get(i).size(); k++) {
                            w.add(
                                    Lyrics.Word.of(
                                            e.words.get(i).get(k),
                                            e.wordStarts.get(i).get(k),
                                            e.wordEnds.get(i).get(k)));
                        }

                        words.add(w);
                    }
                }

                return new Lyrics(times, e.texts, e.synced, e.source, e.duration, words);
            } else {
                return null;
            }
        } catch (Exception ex) {
            ClientResources.LOG.warn("Không đọc được cache lời cho {}", t.title, ex);
            return null;
        }
    }

    public void writeCache(Track t, Lyrics l) {
        try {
            CacheEntry e = new CacheEntry();
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

            ClientFiles.write(cacheFile(t), GSON.toJson(e));
        } catch (Exception error) {
            ClientResources.LOG.warn("Không lưu được cache lời cho {}", t.title, error);
        }
    }

    public void remove(Track track) {
        try {
            Files.deleteIfExists(cacheFile(track));
        } catch (Exception error) {
            ClientResources.LOG.warn("Không xoá được cache lời cho {}", track.title, error);
        }
    }

    private static void validate(CacheEntry entry) throws IOException {
        if (entry == null
                || !entry.synced
                || entry.texts == null
                || entry.starts == null
                || entry.ends == null
                || entry.texts.isEmpty()
                || entry.texts.size() > 4096
                || entry.starts.size() != entry.texts.size()
                || entry.ends.size() != entry.texts.size()) {
            throw new IOException("Invalid cached lyric lines");
        }
        boolean words = entry.words != null;
        if (words
                && (entry.wordStarts == null
                        || entry.wordEnds == null
                        || entry.words.size() != entry.texts.size()
                        || entry.wordStarts.size() != entry.words.size()
                        || entry.wordEnds.size() != entry.words.size())) {
            throw new IOException("Invalid cached word timing arrays");
        }
        long previous = -1;
        for (int i = 0; i < entry.texts.size(); i++) {
            Long start = entry.starts.get(i), end = entry.ends.get(i);
            if (entry.texts.get(i) == null
                    || start == null
                    || end == null
                    || start < 0
                    || start < previous
                    || end <= start
                    || end > 86_400_000) {
                throw new IOException("Invalid cached lyric timestamps");
            }
            previous = start;
            if (words) {
                List<String> text = entry.words.get(i);
                List<Long> starts = entry.wordStarts.get(i), ends = entry.wordEnds.get(i);
                if (text == null
                        || starts == null
                        || ends == null
                        || text.size() > 512
                        || text.size() != starts.size()
                        || text.size() != ends.size()) {
                    throw new IOException("Invalid cached words");
                }
                long last = -1;
                for (int k = 0; k < text.size(); k++) {
                    Long from = starts.get(k), to = ends.get(k);
                    if (text.get(k) == null
                            || from == null
                            || to == null
                            || from < last
                            || from < 0
                            || to < from
                            || to > 86_400_000) {
                        throw new IOException("Invalid cached word timestamps");
                    }
                    last = from;
                }
            }
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
}
