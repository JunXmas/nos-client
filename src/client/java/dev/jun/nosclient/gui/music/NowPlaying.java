package dev.jun.nosclient.gui.music;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.media.AppVolume;
import dev.jun.nosclient.media.MediaManager;
import dev.jun.nosclient.media.Track;
import dev.jun.nosclient.media.lyrics.Lyrics;
import dev.jun.nosclient.render.Fonts;
import dev.jun.nosclient.render.Gfx;
import dev.jun.nosclient.render.Themes;
import dev.jun.nosclient.util.Anim;
import dev.jun.nosclient.util.Col;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class NowPlaying {
    private static final float[] btnHover = new float[5];
    private static final float[] btnPress = new float[5];
    private static final float[][] btn = new float[5][4];
    private static float playMorph = -1.0F;
    private static float barX;
    private static float barY;
    private static float barW;
    private static float barHover;
    private static float dragFrac = -1.0F;
    private static boolean barOn;
    private static boolean barDrag;
    private static long dragSentAt;
    private static float volX;
    private static float volY;
    private static float volW;
    private static float volIconX;
    private static float volHover;
    private static float volFrac;
    private static boolean volOn;
    private static boolean volDrag;

    public static boolean pressBar(double mx, double my) {
        if (AppVolume.supported()
                && Ui.inside(mx, my, volIconX - 6.0F, volY - 6.0F, 12.0F, 12.0F)) {
            AppVolume.toggleMute();
            UiSounds.play(UiSounds.CLICK, 1.1F);
            return true;
        } else if (volOn && Ui.inside(mx, my, volX - 3.0F, volY - 5.0F, volW + 6.0F, 10.0F)) {
            volDrag = true;
            volFrac = (float) Math.max(0.0, Math.min(1.0, (mx - volX) / volW));
            AppVolume.set(volFrac);
            UiSounds.play(UiSounds.SLIDER, 1.0F);
            return true;
        } else if (barOn && Ui.inside(mx, my, barX - 3.0F, barY - 5.0F, barW + 6.0F, 13.0F)) {
            barDrag = true;
            dragFrac = (float) Math.max(0.0, Math.min(1.0, (mx - barX) / barW));
            UiSounds.play(UiSounds.CLICK, 1.2F);
            return true;
        } else {
            return false;
        }
    }

    public static boolean dragging() {
        return barDrag || volDrag;
    }

    public static void dragBar(double mx) {
        if (volDrag) {
            float nf = (float) Math.max(0.0, Math.min(1.0, (mx - volX) / volW));
            if (Math.abs(nf - volFrac) > 0.01F) {
                volFrac = nf;
                AppVolume.set(nf);
            }
        } else if (barDrag) {
            float nf = (float) Math.max(0.0, Math.min(1.0, (mx - barX) / barW));
            if (Math.abs(nf - dragFrac) > 0.004F) {
                UiSounds.play(UiSounds.SLIDER, 0.9F + nf * 0.4F);
            }

            dragFrac = nf;
        }
    }

    public static void releaseBar() {
        volDrag = false;
        if (barDrag) {
            barDrag = false;
            Track t = MediaManager.get().track();
            if (t != null && t.durationMs > 0L) {
                MediaManager.get().seek((long) (dragFrac * (float) t.durationMs));
                dragSentAt = System.currentTimeMillis();
            }
        }
    }

    private NowPlaying() {}

    private static float coverH(float w) {
        return w - 24.0F;
    }

    public static float height(float w) {
        return 36.0F + coverH(w) + 10.0F + 13.0F + 11.0F + 10.0F + 18.0F + 16.0F + 30.0F + 10.0F
                + 13.0F + 16.0F;
    }

    public static void renderHero(
            GuiGraphics g, float x, float y, float w, float h, double mx, double my, float a) {
        Themes.Theme th = Themes.current();
        MediaManager mm = MediaManager.get();
        Track t = mm.track();
        MusicConfig c = MusicSettings.get();
        Gfx.shadow(g, x, y, w, h, 14.0F, 12.0F, Col.alpha(-16777216, a * 0.5F));
        Gfx.roundGradient(
                g,
                x,
                y,
                w,
                h,
                14.0F,
                Col.alpha(dev.jun.nosclient.theme.Theme.palette().raised(), a),
                Col.alpha(-15593447, a));
        Gfx.roundOutline(g, x, y, w, h, 14.0F, 0.6F, Col.alpha(587202559, a));
        float pad = 12.0F;
        float cx = x + pad;
        float cw = w - pad * 2.0F;
        float yy = y + 12.0F;
        boolean spotify = t == null || t.sourceApp.equals("Spotify");
        String src =
                t == null ? "Spotify" : (t.sourceApp.equals("Browser") ? "YouTube" : t.sourceApp);
        int srcCol =
                t == null
                        ? -14756000
                        : (spotify
                                ? -14756000
                                : (t.sourceApp.equals("Browser") ? -45499 : Themes.accent()));
        boolean yt = t != null && t.sourceApp.equals("Browser");
        if (!spotify && !yt) {
            Gfx.circle(g, cx + 7.0F, yy + 7.0F, 7.0F, Col.alpha(srcCol, a));
            Icons.draw(g, "play", cx + 7.6F, yy + 7.0F, 7.0F, Col.alpha(-1, a));
        } else {
            Gfx.image(
                    g,
                    ClientResources.id(
                            spotify
                                    ? "textures/gui/logos/spotify.png"
                                    : "textures/gui/logos/youtube.png"),
                    128,
                    128,
                    cx,
                    yy,
                    14.0F,
                    14.0F,
                    Col.alpha(-1, a));
        }

        Gfx.text(g, src, Ui.FB, cx + 18.0F, yy + 0.5F, 7.6F, Col.alpha(-856070, a));
        Gfx.text(
                g,
                Ui.tr("nosclient.music.hero.now_playing"),
                Ui.F,
                cx + 18.0F,
                yy + 9.4F,
                5.6F,
                Col.alpha(-6515020, a));
        boolean live = t != null && t.playing;
        if (live) {
            double time = Anim.seconds();

            for (int i = 0; i < 3; i++) {
                float bh = 2.5F + 5.0F * (float) (0.5 + 0.5 * Math.sin(time * (7.0 + i * 2.3) + i));
                Gfx.round(
                        g,
                        cx + cw - 12.0F + i * 4,
                        yy + 11.0F - bh,
                        2.4F,
                        bh,
                        1.2F,
                        Col.alpha(srcCol, a));
            }
        }

        yy += 24.0F;
        float bh = coverH(w);
        boolean art = CoverArt.ready();
        if (art) {
            Gfx.shadow(g, cx, yy, cw, bh, 6.0F, 6.0F, Col.alpha(-16777216, a * 0.5F));
            CoverArt.draw(g, cx, yy, cw, a);
            Gfx.roundOutline(g, cx, yy, cw, bh, 4.0F, 0.5F, Col.alpha(822083583, a));
            Gfx.gradientV(
                    g,
                    cx,
                    yy + bh * 0.55F,
                    cw,
                    bh * 0.45F,
                    Col.alpha(0, a),
                    Col.alpha(-1073741824, a));
            g.enableScissor(
                    (int) cx,
                    (int) (yy + bh * 0.5F),
                    (int) Math.ceil(cx + cw),
                    (int) Math.ceil(yy + bh));
            previewLyrics(g, cx, yy + bh * 0.5F, cw, bh * 0.5F, a, th, c);
            g.disableScissor();
        } else {
            Gfx.roundGradient(
                    g,
                    cx,
                    yy,
                    cw,
                    bh,
                    10.0F,
                    Col.alpha(th.skyTop(), a),
                    Col.alpha(th.skyBottom(), a));
            double time = Anim.seconds();

            for (int i = 0; i < 20; i++) {
                float sx2 = cx + 6.0F + Anim.hash01(i * 13L) * (cw - 12.0F);
                float sy2 = yy + 5.0F + Anim.hash01(i * 29L + 7L) * (bh * 0.55F);
                float tw =
                        0.35F
                                + 0.65F
                                        * (float)
                                                (0.5
                                                        + 0.5
                                                                * Math.sin(
                                                                        time
                                                                                        * (1.0F
                                                                                                + Anim
                                                                                                                .hash01(
                                                                                                                        i)
                                                                                                        * 2.0F)
                                                                                + i));
                Gfx.circle(
                        g,
                        sx2,
                        sy2,
                        0.5F + Anim.hash01(i * 7L) * 0.5F,
                        Col.alpha(-1, a * tw * 0.8F));
            }

            g.enableScissor((int) cx, (int) yy, (int) Math.ceil(cx + cw), (int) Math.ceil(yy + bh));
            previewLyrics(g, cx, yy, cw, bh, a, th, c);
            g.disableScissor();
        }

        yy += bh + 10.0F;
        String title = t == null ? Ui.tr("nosclient.music.hero.nothing_title") : t.title;
        String artist = t == null ? Ui.tr("nosclient.music.hero.nothing_sub") : t.artist;
        Gfx.text(
                g,
                Gfx.ellipsize(title, Ui.FB, 10.5F, cw),
                Ui.FB,
                cx,
                yy,
                10.5F,
                Col.alpha(-856070, a));
        yy += 13.0F;
        Gfx.text(
                g,
                Gfx.ellipsize(artist, Ui.F, 7.4F, cw),
                Ui.F,
                cx,
                yy,
                7.4F,
                Col.alpha(-6515020, a));
        yy += 21.0F;
        long pos = Math.max(0L, mm.positionRaw());
        long dur = t == null ? 0L : t.durationMs;
        float frac = dur > 0L ? Math.min(1.0F, (float) pos / (float) dur) : 0.0F;
        if (barDrag) {
            frac = dragFrac;
        } else if (dragFrac >= 0.0F && System.currentTimeMillis() - dragSentAt < 900L) {
            frac = dragFrac;
        } else {
            dragFrac = -1.0F;
        }

        barX = cx;
        barY = yy;
        barW = cw;
        barOn = dur > 0L;
        boolean overBar = barOn && Ui.inside(mx, my, cx - 3.0F, yy - 5.0F, cw + 6.0F, 13.0F);
        barHover = Anim.approach(barHover, !overBar && !barDrag ? 0.0F : 1.0F, 14.0F);
        float bt = 3.0F + barHover * 1.2F;
        int green = spotify ? -14756000 : Themes.accent();
        Gfx.round(g, cx, yy + 1.5F - bt / 2.0F, cw, bt, bt / 2.0F, Col.alpha(822083583, a));
        if (frac > 0.0F) {
            Gfx.round(
                    g,
                    cx,
                    yy + 1.5F - bt / 2.0F,
                    Math.max(bt, cw * frac),
                    bt,
                    bt / 2.0F,
                    Col.alpha(barHover > 0.05F ? green : -1, a));
        }

        Gfx.circle(g, cx + cw * frac, yy + 1.5F, 2.6F + barHover * 1.4F, Col.alpha(-1, a));
        Gfx.text(
                g,
                fmt(t == null ? 0L : (dur > 0L ? (long) (frac * (float) dur) : pos)),
                Ui.F,
                cx,
                yy + 6.0F,
                5.6F,
                Col.alpha(barDrag ? -856070 : -9541498, a));
        String right = dur > 0L ? fmt(dur) : "--:--";
        Gfx.text(
                g,
                right,
                Ui.F,
                cx + cw - Gfx.textWidth(right, Ui.F, 5.6F),
                yy + 6.0F,
                5.6F,
                Col.alpha(-9541498, a));
        yy += 18.0F;
        float vol = AppVolume.get();
        if (volDrag) {
            vol = volFrac;
        }

        boolean mute = AppVolume.muted();
        volIconX = cx + 4.0F;
        volX = cx + 14.0F;
        volY = yy + 4.0F;
        volW = cw - 40.0F;
        volOn = vol >= 0.0F;
        boolean overVol = volOn && Ui.inside(mx, my, volX - 3.0F, volY - 5.0F, volW + 6.0F, 10.0F);
        volHover = Anim.approach(volHover, !overVol && !volDrag ? 0.0F : 1.0F, 14.0F);
        Icons.draw(
                g,
                !mute && (!volOn || !(vol < 0.01F)) ? "volume" : "mute",
                volIconX,
                volY,
                8.0F,
                Col.alpha(volOn ? -6515020 : -9541498, a));
        if (volOn) {
            float f = mute ? 0.0F : vol;
            float vt = 2.5F + volHover;
            Gfx.round(g, volX, volY - vt / 2.0F, volW, vt, vt / 2.0F, Col.alpha(822083583, a));
            if (f > 0.0F) {
                Gfx.round(
                        g,
                        volX,
                        volY - vt / 2.0F,
                        Math.max(vt, volW * f),
                        vt,
                        vt / 2.0F,
                        Col.alpha(volHover > 0.05F ? green : -1, a));
            }

            Gfx.circle(g, volX + volW * f, volY, 2.4F + volHover * 1.2F, Col.alpha(-1, a));
            Gfx.text(
                    g,
                    Math.round(f * 100.0F) + "%",
                    Ui.F,
                    volX + volW + 5.0F,
                    volY - 2.8F,
                    5.6F,
                    Col.alpha(-9541498, a));
        } else {
            Gfx.text(
                    g,
                    Gfx.ellipsize(
                            Ui.tr(
                                    AppVolume.supported()
                                            ? "nosclient.music.player.volume_none"
                                            : "nosclient.music.player.volume_unsupported"),
                            Ui.F,
                            5.6F,
                            cw - 18.0F),
                    Ui.F,
                    volX,
                    volY - 2.8F,
                    5.6F,
                    Col.alpha(-9541498, a));
        }

        yy += 16.0F;
        float by = yy + 13.0F;
        float mid = x + w / 2.0F;
        drawButton(g, 0, mid - 34.0F, by, 10.0F, "prev", mx, my, a, false);
        boolean playing = t != null && t.playing;
        if (playMorph < 0.0F) {
            playMorph = playing ? 1.0F : 0.0F;
        }

        playMorph = Anim.approach(playMorph, playing ? 1.0F : 0.0F, 12.0F);
        drawButton(g, 1, mid, by, 13.0F, playing ? "pause" : "play", mx, my, a, true);
        drawButton(g, 2, mid + 34.0F, by, 10.0F, "next", mx, my, a, false);
        drawRepeat(g, 4, mid + 62.0F, by, 7.5F, mx, my, a, -14756000);
        yy += 40.0F;
        MediaManager.LyricsState ls = mm.lyricsState();
        Lyrics l = mm.lyrics();
        String st;
        int stc;
        if (t == null) {
            st = Ui.tr(mm.statusKey());
            stc = -9541498;
        } else if (ls == MediaManager.LyricsState.SEARCHING) {
            st = Ui.tr("nosclient.music.lyrics.searching");
            stc = -11414;
        } else if (ls == MediaManager.LyricsState.NOT_FOUND) {
            st = Ui.tr("nosclient.music.lyrics.not_found");
            stc = -34182;
        } else if (l != null && !l.synced) {
            st = Ui.tr("nosclient.music.lyrics.estimated");
            stc = -18326;
        } else {
            st = Ui.tr("nosclient.music.lyrics.synced");
            stc = -11870592;
        }

        float chipY = yy;
        st = Gfx.ellipsize(st, Ui.F, 6.4F, cw - 36.0F);
        float chw = Gfx.textWidth(st, Ui.F, 6.4F) + 18.0F + (t != null ? 14 : 0);
        float chx = mid - chw / 2.0F;
        Gfx.round(g, chx, chipY, chw, 13.0F, 6.5F, Col.alpha(805306368, a));
        Gfx.circle(g, chx + 7.0F, chipY + 6.5F, 2.4F, Col.alpha(stc, a));
        Gfx.text(g, st, Ui.F, chx + 12.0F, chipY + 3.3F, 6.4F, Col.alpha(-856070, a));
        if (t != null) {
            boolean over = Ui.inside(mx, my, chx + chw - 15.0F, chipY, 15.0F, 13.0F);
            btnHover[3] = Anim.approach(btnHover[3], over ? 1.0F : 0.0F, 14.0F);
            btn[3][0] = chx + chw - 8.0F;
            btn[3][1] = chipY + 6.5F;
            btn[3][2] = 7.0F;
            g.pose().pushMatrix();
            g.pose().translate(btn[3][0], btn[3][1]);
            g.pose().rotate(btnHover[3] * 1.2F);
            Icons.draw(
                    g,
                    "refresh",
                    0.0F,
                    0.0F,
                    7.0F,
                    Col.alpha(Col.lerp(-6515020, -1, btnHover[3]), a));
            g.pose().popMatrix();
        } else {
            btn[3][2] = 0.0F;
        }

        if (t != null && l != null) {
            Gfx.textCentered(
                    g,
                    Ui.tr("nosclient.music.hero.source", sourceName(l.source)),
                    Ui.F,
                    mid,
                    y + h - 9.5F,
                    5.4F,
                    Col.alpha(-9541498, a));
        }
    }

    static String sourceName(String src) {
        String s = src == null ? "" : src.toLowerCase(Locale.ROOT);
        String base =
                s.contains("musixmatch")
                        ? "Musixmatch"
                        : (s.contains("netease")
                                ? "NetEase"
                                : (s.contains("lrclib")
                                        ? "LRCLIB"
                                        : (src != null && !src.isBlank() ? src : "?")));
        if (s.contains("word")) {
            base = base + " · " + Ui.tr("nosclient.music.hero.word_by_word");
        }

        return base;
    }

    private static String fmt(long ms) {
        long s = Math.max(0L, ms / 1000L);
        return s / 60L + ":" + String.format("%02d", s % 60L);
    }

    private static void drawButton(
            GuiGraphics g,
            int i,
            float cx,
            float cy,
            float r,
            String icon,
            double mx,
            double my,
            float a,
            boolean primary) {
        boolean over = Ui.inside(mx, my, cx - r, cy - r, r * 2.0F, r * 2.0F);
        if (over && btnHover[i] < 0.05F) {
            UiSounds.play(UiSounds.HOVER, 1.25F);
        }

        btnHover[i] = Anim.approach(btnHover[i], over ? 1.0F : 0.0F, 14.0F);
        btnPress[i] = Anim.approach(btnPress[i], 0.0F, 9.0F);
        btn[i][0] = cx;
        btn[i][1] = cy;
        btn[i][2] = r;
        float rr = r * (1.0F + btnHover[i] * 0.07F - btnPress[i] * 0.12F);
        if (primary) {
            Gfx.glow(
                    g,
                    cx - rr,
                    cy - rr,
                    rr * 2.0F,
                    rr * 2.0F,
                    rr,
                    5.0F,
                    Col.alpha(Themes.accent(), a * (0.25F + 0.3F * btnHover[i])));
            Gfx.circle(g, cx, cy, rr, Col.alpha(-1, a));
            Icons.draw(
                    g,
                    icon,
                    cx + (icon.equals("play") ? 1.0F : 0.0F),
                    cy,
                    rr * 0.95F,
                    Col.alpha(-15330529, a));
        } else {
            Gfx.circle(g, cx, cy, rr, Col.alpha(Col.lerp(553648127, 956301311, btnHover[i]), a));
            Icons.draw(g, icon, cx, cy, rr * 0.95F, Col.alpha(-1, a));
        }
    }

    static void drawRepeat(
            GuiGraphics g,
            int i,
            float cx,
            float cy,
            float r,
            double mx,
            double my,
            float a,
            int on) {
        int mode = MediaManager.get().repeat();
        boolean over = Ui.inside(mx, my, cx - r, cy - r, r * 2.0F, r * 2.0F);
        if (over && btnHover[i] < 0.05F) {
            UiSounds.play(UiSounds.HOVER, 1.25F);
        }

        btnHover[i] = Anim.approach(btnHover[i], over ? 1.0F : 0.0F, 14.0F);
        btnPress[i] = Anim.approach(btnPress[i], 0.0F, 9.0F);
        btn[i][0] = cx;
        btn[i][1] = cy;
        btn[i][2] = r;
        float rr = r * (1.0F + btnHover[i] * 0.07F - btnPress[i] * 0.12F);
        int col = mode == 0 ? Col.lerp(-2434342, -1, btnHover[i]) : on;
        Icons.draw(
                g,
                mode == 1 ? "repeat_one" : "repeat",
                cx,
                cy - 0.5F,
                rr * 1.35F,
                Col.alpha(col, a));
        if (mode != 0) {
            Gfx.circle(g, cx, cy + rr + 1.6F, 1.3F, Col.alpha(on, a));
        }
    }

    public static void click(double mx, double my) {
        MediaManager mm = MediaManager.get();
        if (pressBar(mx, my)) {
            if (!volDrag) {
                releaseBar();
            } else {
                volDrag = false;
            }
        } else {
            for (int i = 0; i < 5; i++) {
                float[] b = btn[i];
                if (!(b[2] <= 0.0F)) {
                    double dx = mx - b[0];
                    double dy = my - b[1];
                    if (dx * dx + dy * dy <= (b[2] + 2.0F) * (b[2] + 2.0F)) {
                        btnPress[i] = 1.0F;
                        UiSounds.play(UiSounds.CLICK, i == 1 ? 1.0F : 1.15F);
                        switch (i) {
                            case 0:
                                mm.previous();
                                break;
                            case 1:
                                mm.playPause();
                                break;
                            case 2:
                                mm.next();
                                break;
                            case 3:
                                mm.refetch();
                                break;
                            case 4:
                                mm.cycleRepeat();
                        }

                        return;
                    }
                }
            }
        }
    }

    private static void previewLyrics(
            GuiGraphics g,
            float x,
            float y,
            float w,
            float h,
            float a,
            Themes.Theme th,
            MusicConfig c) {
        MediaManager mm = MediaManager.get();
        Lyrics l = mm.lyrics();
        long t = mm.position();
        String font = c.font;
        if (l != null && t >= 0L) {
            Lyrics.Line line = l.lineAt(t);
            if (line == null) {
                line = l.lastStartedBefore(t);
            }

            if (line != null) {
                float size = 13.0F;
                List<List<Lyrics.Word>> rows = new ArrayList<>();
                List<Lyrics.Word> row = new ArrayList<>();
                float rw = 0.0F;
                float space = Gfx.textWidth(" ", font, size);

                for (Lyrics.Word wd : line.words) {
                    float ww = Gfx.textWidth(Lyrics.show(wd.text), font, size);
                    if (!row.isEmpty() && rw + space + ww > w - 18.0F) {
                        rows.add(row);
                        row = new ArrayList<>();
                        rw = 0.0F;
                    }

                    rw += (row.isEmpty() ? 0.0F : space) + ww;
                    row.add(wd);
                }

                if (!row.isEmpty()) {
                    rows.add(row);
                }

                if (Fonts.isRtl(line.text)) {
                    for (List<Lyrics.Word> r : rows) {
                        Collections.reverse(r);
                    }
                }

                float lineH = size * 1.25F;
                float top = y + h * 0.5F - rows.size() * lineH / 2.0F;
                g.pose().pushMatrix();
                g.pose().translate(x + w / 2.0F, y + h / 2.0F);
                g.pose().rotate((float) Math.toRadians(-4.0));
                g.pose().translate(-(x + w / 2.0F), -(y + h / 2.0F));

                for (int r = 0; r < rows.size(); r++) {
                    List<Lyrics.Word> rr = rows.get(r);
                    float total = 0.0F;

                    for (int i = 0; i < rr.size(); i++) {
                        total +=
                                Gfx.textWidth(Lyrics.show(rr.get(i).text), font, size)
                                        + (i > 0 ? space : 0.0F);
                    }

                    float wx = x + w / 2.0F - total / 2.0F;

                    for (Lyrics.Word wd : rr) {
                        float ww = Gfx.textWidth(Lyrics.show(wd.text), font, size);
                        float p = Anim.progress(t, wd.start - 40L, wd.start + 260L);
                        if (p > 0.0F) {
                            float e = Anim.easeOutCubic(p);
                            boolean sung = t >= wd.start && t < wd.end + 80L;
                            Gfx.neon(
                                    g,
                                    Lyrics.show(wd.text),
                                    font,
                                    wx + ww / 2.0F,
                                    top + r * lineH + (1.0F - e) * 4.0F,
                                    size,
                                    th.main(),
                                    th.a(),
                                    th.b(),
                                    a * e,
                                    false,
                                    (c.glow ? 0.55F : 0.0F) + (sung ? 0.4F : 0.0F));
                        }

                        wx += ww + space;
                    }
                }

                g.pose().popMatrix();
            }
        } else {
            String s =
                    mm.track() == null
                            ? Ui.tr("nosclient.music.hero.preview_idle")
                            : Ui.tr("nosclient.music.hero.preview_wait");
            float size = 12.0F;
            Gfx.neon(
                    g,
                    Gfx.ellipsize(s, font, size, w - 16.0F),
                    font,
                    x + w / 2.0F,
                    y + h / 2.0F - size / 2.0F - 4.0F,
                    size,
                    th.main(),
                    th.a(),
                    th.b(),
                    a * 0.85F,
                    false,
                    c.glow ? 0.6F : 0.0F);
        }
    }
}
