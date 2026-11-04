import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.media.DesktopBackend;
import dev.jun.nosclient.media.Track;

public final class MprisControlCheck {
    static final DesktopBackend backend = new DesktopBackend();

    static Track sample() {
        Track track = backend.poll(MusicConfig.Source.ANY);
        if (track == null) throw new AssertionError("Missing private MPRIS fixture");
        return track;
    }

    static void near(long expected, long actual) {
        if (Math.abs(expected - actual) > 1000)
            throw new AssertionError("Expected " + expected + "ms, got " + actual);
    }

    public static void main(String[] args) throws Exception {
        Track initial = sample();
        if (!initial.playing) throw new AssertionError("Initial state");
        backend.playPause();
        Track paused = sample();
        if (paused.playing) throw new AssertionError("Pause failed");
        Thread.sleep(200);
        near(paused.positionMs, sample().positionMs);
        backend.seek(40000);
        near(40000, sample().positionMs);
        backend.seek(5000);
        near(5000, sample().positionMs);
        backend.playPause();
        if (!sample().playing) throw new AssertionError("Resume failed");
        backend.next();
        if (sample().title.equals(initial.title)) throw new AssertionError("Next failed");
        backend.previous();
        if (!sample().title.equals(initial.title)) throw new AssertionError("Previous failed");
        System.out.println(
                "MPRIS CONTROL PASS pause/resume/seek-forward/seek-backward/next/previous");
    }
}
