package dev.jun.nosclient.media;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
interface SystemMedia extends AutoCloseable {
    MediaTrack poll() throws Exception;

    @Override
    default void close() {
    }

    static SystemMedia create() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new WindowsMedia();
        } else {
            return os.contains("linux") ? new LinuxMedia() : () -> MediaTrack.NONE;
        }
    }
}
