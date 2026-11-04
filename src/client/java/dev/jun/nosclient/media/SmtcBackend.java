package dev.jun.nosclient.media;

import com.sun.jna.Memory;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.config.MusicConfig;

import java.util.Locale;

public final class SmtcBackend implements MediaBackend {
    private static final String MANAGER_CLASS =
            "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
    private static final Memory IID_MANAGER_STATICS =
            Com.guid("2050c4ee-11a0-57de-aed7-c97c70338245");
    private static final long FILETIME_EPOCH_DIFF_MS = 11644473600000L;
    private Pointer manager;
    private Pointer session;
    private boolean failed;
    private String status = "nosclient.music.status.searching";
    private int repeat = -1;

    @Override
    public boolean available() {
        return Platform.isWindows() && !this.failed;
    }

    private boolean ensureManager() {
        if (this.manager != null) {
            return true;
        }

        if (Platform.isWindows() && !this.failed) {
            try {
                Com.init();
                Pointer statics =
                        Com.activationFactory(
                                "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager",
                                IID_MANAGER_STATICS);
                if (statics == null) {
                    this.failed = true;
                    return false;
                } else {
                    Pointer op = Com.outPtr(statics, 6);
                    Com.release(statics);
                    this.manager = Com.await(op, 5000L);
                    if (this.manager == null) {
                        this.failed = true;
                        return false;
                    } else {
                        return true;
                    }
                }
            } catch (Throwable t) {
                ClientResources.LOG.warn("Windows media controls unavailable", t);
                this.failed = true;
                return false;
            }
        } else {
            return false;
        }
    }

    private static boolean isSpotify(String aumid) {
        return aumid.toLowerCase(Locale.ROOT).contains("spotify");
    }

    private static boolean isBrowser(String aumid) {
        String a = aumid.toLowerCase(Locale.ROOT);
        return a.contains("chrome")
                || a.contains("msedge")
                || a.contains("edge")
                || a.contains("firefox")
                || a.contains("308046b0af4a39cb")
                || a.contains("opera")
                || a.contains("brave")
                || a.contains("vivaldi")
                || a.contains("yandex")
                || a.contains("arc")
                || a.contains("zen");
    }

    private boolean matches(MusicConfig.Source src, String aumid) {
        return switch (src) {
            case SPOTIFY -> isSpotify(aumid);
            case YOUTUBE -> isBrowser(aumid) || aumid.toLowerCase(Locale.ROOT).contains("youtube");
            case ANY -> true;
        };
    }

    @Override
    public Track poll(MusicConfig.Source source) {
        if (!this.ensureManager()) {
            this.status = "nosclient.music.status.unsupported";
            return null;
        }

        Pointer chosen = null;
        int chosenStatus = -1;
        String chosenApp = "";
        Pointer sessions = Com.outPtr(this.manager, 7);
        if (sessions == null) {
            return null;
        }

        try {
            int size = Com.outInt(sessions, 7);

            for (int i = 0; i < size; i++) {
                PointerByReference r = new PointerByReference();
                if (Com.call(sessions, 6, i, r) >= 0 && r.getValue() != null) {
                    Pointer s = r.getValue();
                    String app = Com.hstringProp(s, 6);
                    if (!this.matches(source, app)) {
                        Com.release(s);
                    } else {
                        int st = playbackStatus(s);
                        if (chosen != null && (st != 4 || chosenStatus == 4)) {
                            Com.release(s);
                        } else {
                            Com.release(chosen);
                            chosen = s;
                            chosenStatus = st;
                            chosenApp = app;
                        }
                    }
                }
            }
        } finally {
            Com.release(sessions);
        }

        Com.release(this.session);
        this.session = chosen;
        if (chosen == null) {
            this.status =
                    source == MusicConfig.Source.SPOTIFY
                            ? "nosclient.music.status.open_spotify"
                            : "nosclient.music.status.nothing";
            return null;
        }

        this.repeat = repeatOf(chosen);
        Pointer props = Com.await(Com.outPtr(chosen, 7), 1500L);
        if (props == null) {
            return null;
        }

        String title;
        String artist;
        String album;
        try {
            title = Com.hstringProp(props, 6);
            artist = Com.hstringProp(props, 9);
            album = Com.hstringProp(props, 10);
            if (artist.isEmpty()) {
                artist = Com.hstringProp(props, 8);
            }
        } finally {
            Com.release(props);
        }

        if (title.isEmpty()) {
            this.status = "nosclient.music.status.nothing";
            return null;
        }

        long durationMs = 0L;
        long positionMs = 0L;
        long rawMs = -1L;
        long updatedFt = -1L;
        Pointer tl = Com.outPtr(chosen, 8);
        if (tl != null) {
            try {
                long start = Com.outLong(tl, 6);
                long end = Com.outLong(tl, 7);
                long pos = Com.outLong(tl, 10);
                long updated = Com.outLong(tl, 11);
                if (end > 0L && end != Long.MIN_VALUE) {
                    durationMs = (end - Math.max(0L, start)) / 10000L;
                }

                if (pos != Long.MIN_VALUE) {
                    positionMs = pos / 10000L;
                    rawMs = positionMs;
                }

                if (updated > 0L && updated != Long.MIN_VALUE) {
                    updatedFt = updated;
                }

                if (chosenStatus == 4 && updated > 0L && updated != Long.MIN_VALUE) {
                    long nowFt = (System.currentTimeMillis() + 11644473600000L) * 10000L;
                    long delta = (nowFt - updated) / 10000L;
                    if (delta > 0L && delta < 21600000L) {
                        positionMs += delta;
                    }
                }
            } finally {
                Com.release(tl);
            }
        }

        boolean playing = chosenStatus == 4;
        this.status = playing ? "nosclient.music.status.playing" : "nosclient.music.status.paused";
        String app =
                isSpotify(chosenApp) ? "Spotify" : (isBrowser(chosenApp) ? "Browser" : chosenApp);
        return new Track(
                title,
                artist,
                album,
                durationMs,
                positionMs,
                playing,
                app,
                rawMs,
                updatedFt,
                System.nanoTime());
    }

    private static int playbackStatus(Pointer s) {
        Pointer info = Com.outPtr(s, 9);
        if (info == null) {
            return -1;
        }

        try {
            return Com.outInt(info, 7);
        } finally {
            Com.release(info);
        }
    }

    private static int repeatOf(Pointer s) {
        Pointer info = Com.outPtr(s, 9);
        if (info == null) {
            return -1;
        }

        try {
            Pointer ref = Com.outPtr(info, 9);
            if (ref == null) {
                return -1;
            }

            try {
                return Com.outInt(ref, 6);
            } finally {
                Com.release(ref);
            }
        } catch (Throwable t) {
            return -1;
        } finally {
            Com.release(info);
        }
    }

    @Override
    public int repeatMode() {
        return this.repeat;
    }

    @Override
    public void setRepeat(int mode) {
        if (this.session != null) {
            try {
                Com.awaitAction(Com.outPtr(this.session, 21, mode));
            } catch (Throwable var3) {
            }
        }
    }

    @Override
    public byte[] cover() {
        if (this.session == null) {
            return null;
        }

        try {
            Pointer props = Com.await(Com.outPtr(this.session, 7), 1500L);
            if (props == null) {
                return null;
            }

            Pointer ref;
            try {
                ref = Com.outPtr(props, 15);
            } finally {
                Com.release(props);
            }

            if (ref == null) {
                return null;
            }

            Pointer stream;
            try {
                stream = Com.await(Com.outPtr(ref, 6), 3000L);
            } finally {
                Com.release(ref);
            }

            if (stream == null) {
                return null;
            }

            try {
                return Com.readRandomAccessStream(stream, 8388608);
            } finally {
                Com.release(stream);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private void action(int index) {
        if (this.session != null) {
            try {
                Com.awaitAction(Com.outPtr(this.session, index));
            } catch (Throwable var3) {
            }
        }
    }

    @Override
    public void playPause() {
        this.action(20);
    }

    @Override
    public void next() {
        this.action(16);
    }

    @Override
    public void previous() {
        this.action(17);
    }

    @Override
    public void seek(long ms) {
        if (this.session != null) {
            try {
                Com.awaitAction(Com.outPtr(this.session, 24, Math.max(0L, ms) * 10000L));
            } catch (Throwable var4) {
            }
        }
    }

    @Override
    public String statusKey() {
        return this.status;
    }

    @Override
    public void close() {
        Com.release(this.session);
        this.session = null;
        Com.release(this.manager);
        this.manager = null;
    }
}
