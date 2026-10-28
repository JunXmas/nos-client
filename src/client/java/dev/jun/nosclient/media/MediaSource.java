package dev.jun.nosclient.media;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Bài phát từ đâu. Trình duyệt (tab YouTube…) đặt tiêu đề video vào title và tên kênh vào artist, nên phải bóc lại
 * tên bài/ca sĩ trước khi tìm lời; Spotify và trình phát nhạc thì đưa đúng metadata.
 */
@Environment(EnvType.CLIENT)
public enum MediaSource {
    UNKNOWN,
    SPOTIFY,
    BROWSER;

    /** {@code application}: tên bus MPRIS (Linux) hoặc AppUserModelId (Windows). */
    static MediaSource detect(String application) {
        String app = application.toLowerCase(Locale.ROOT);
        if (app.contains("spotify")) {
            return SPOTIFY;
        }

        for (String browser : new String[]{"chrome", "chromium", "firefox", "msedge", "brave", "opera", "vivaldi"}) {
            if (app.contains(browser)) {
                return BROWSER;
            }
        }

        return UNKNOWN;
    }
}
