package dev.jun.nosclient.media;

import dev.jun.nosclient.config.MusicConfig;

public interface MediaBackend {
    Track poll(MusicConfig.Source var1) throws Exception;

    byte[] cover();

    void playPause();

    void next();

    void previous();

    default int repeatMode() {
        return -1;
    }

    default void setRepeat(int mode) {}

    default void seek(long ms) {}

    String statusKey();

    boolean available();

    void close();
}
