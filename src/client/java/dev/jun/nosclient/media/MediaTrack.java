package dev.jun.nosclient.media;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record MediaTrack(String title, String artist, long durationMs, long positionMs, boolean playing, double rate, long sampledAt) {
    public static final MediaTrack NONE = new MediaTrack("", "", 0L, 0L, false, 1.0, 0L);

    public MediaTrack {
        title = clean(title);
        artist = clean(artist);
        durationMs = Math.max(0L, durationMs);
        positionMs = Math.max(0L, positionMs);
        rate = Double.isFinite(rate) && rate > 0.0 && rate <= 4.0 ? rate : 1.0;
    }

    private static String clean(String text) {
        if (text == null) {
            return "";
        }

        text = text.replaceAll("[\\p{Cntrl}]", " ").strip();
        return text.codePointCount(0, text.length()) <= 512 ? text : text.substring(0, text.offsetByCodePoints(0, 512));
    }

    public String key() {
        return this.title.toLowerCase(Locale.ROOT) + "\n" + this.artist.toLowerCase(Locale.ROOT) + "\n" + this.durationMs / 1000L;
    }

    public long position(long now) {
        long position = this.positionMs + (this.playing ? (long)(Math.max(0L, now - this.sampledAt) / 1000000.0 * this.rate) : 0L);
        return this.durationMs > 0L ? Math.min(this.durationMs, position) : position;
    }
}
