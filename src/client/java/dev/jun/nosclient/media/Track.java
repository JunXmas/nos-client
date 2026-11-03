package dev.jun.nosclient.media;

import java.util.Locale;

public final class Track {
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    public final long positionMs;
    public final long sampledAtNanos;
    public final boolean playing;
    public final String sourceApp;
    public final long rawPositionMs;
    public final long updatedFt;
    public final double rate;

    public Track(
            String title,
            String artist,
            String album,
            long durationMs,
            long positionMs,
            boolean playing,
            String sourceApp) {
        this(title, artist, album, durationMs, positionMs, playing, sourceApp, -1L);
    }

    public Track(
            String title,
            String artist,
            String album,
            long durationMs,
            long positionMs,
            boolean playing,
            String sourceApp,
            long rawPositionMs) {
        this(
                title,
                artist,
                album,
                durationMs,
                positionMs,
                playing,
                sourceApp,
                rawPositionMs,
                -1L,
                System.nanoTime());
    }

    public Track(
            String title,
            String artist,
            String album,
            long durationMs,
            long positionMs,
            boolean playing,
            String sourceApp,
            long rawPositionMs,
            long updatedFt,
            long sampledAtNanos) {
        this(
                title,
                artist,
                album,
                durationMs,
                positionMs,
                playing,
                sourceApp,
                rawPositionMs,
                updatedFt,
                sampledAtNanos,
                1.0);
    }

    private Track(
            String title,
            String artist,
            String album,
            long durationMs,
            long positionMs,
            boolean playing,
            String sourceApp,
            long rawPositionMs,
            long updatedFt,
            long sampledAtNanos,
            double rate) {
        this.rate = Double.isFinite(rate) && rate > 0 && rate <= 4 ? rate : 1;
        this.title = title == null ? "" : title.trim();
        this.artist = artist == null ? "" : artist.trim();
        this.album = album == null ? "" : album.trim();
        this.durationMs = Math.max(0L, durationMs);
        this.positionMs = Math.max(0L, positionMs);
        this.sampledAtNanos = sampledAtNanos;
        this.playing = playing;
        this.sourceApp = sourceApp == null ? "" : sourceApp;
        this.rawPositionMs = rawPositionMs;
        this.updatedFt = updatedFt;
    }

    public Track keepClock(Track fresh) {
        return new Track(
                fresh.title,
                fresh.artist,
                fresh.album,
                fresh.durationMs,
                this.positionMs,
                fresh.playing,
                fresh.sourceApp,
                fresh.rawPositionMs,
                fresh.updatedFt,
                this.sampledAtNanos,
                fresh.rate);
    }

    public static Track at(Track fresh, long pos) {
        return new Track(
                fresh.title,
                fresh.artist,
                fresh.album,
                fresh.durationMs,
                pos,
                fresh.playing,
                fresh.sourceApp,
                fresh.rawPositionMs,
                fresh.updatedFt,
                System.nanoTime(),
                fresh.rate);
    }

    public Track withRate(double playbackRate) {
        return new Track(
                title,
                artist,
                album,
                durationMs,
                positionMs,
                playing,
                sourceApp,
                rawPositionMs,
                updatedFt,
                sampledAtNanos,
                playbackRate);
    }

    public String key() {
        return (this.artist + "\u0000" + this.title).toLowerCase(Locale.ROOT);
    }

    public long positionNow() {
        long p = this.positionMs;
        if (this.playing) {
            p += (long) ((System.nanoTime() - this.sampledAtNanos) / 1000000.0 * rate);
        }

        if (this.durationMs > 0L) {
            p = Math.min(p, this.durationMs);
        }

        return p;
    }

    public boolean isEmpty() {
        return this.title.isEmpty();
    }
}
