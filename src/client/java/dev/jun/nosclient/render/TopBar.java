package dev.jun.nosclient.render;

import dev.jun.nosclient.ClientResources;
import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.gui.music.CoverArt;
import dev.jun.nosclient.media.MediaManager;
import dev.jun.nosclient.media.Track;
import dev.jun.nosclient.media.lyrics.Lyrics;
import dev.jun.nosclient.util.Anim;
import dev.jun.nosclient.util.Col;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class TopBar {
    private static final Identifier SPOTIFY = ClientResources.id("textures/gui/logos/spotify.png");
    private static final Identifier YOUTUBE = ClientResources.id("textures/gui/logos/youtube.png");
    private static TopBar.Phase phase = TopBar.Phase.TITLE;
    private static long phaseStart = System.nanoTime();
    private static float vis;
    private static float width = 150.0F;
    private static float hlX;
    private static float hlW;
    private static float scroll;
    private static float swap = 1.0F;
    private static String contentKey = "";
    private static float spin;
    private static final float[] bars = new float[4];
    public static float lastX;
    public static float lastY;
    public static float lastW;
    public static float lastH;

    private TopBar() {}

    static void onEvent(MediaManager.Event e) {
        switch (e.type()) {
            case SONG:
                setPhase(TopBar.Phase.TITLE);
                break;
            case PAUSE:
                setPhase(TopBar.Phase.PAUSED);
                break;
            case RESUME:
                setPhase(TopBar.Phase.RESUMED);
            case STOP:
        }
    }

    private static void setPhase(TopBar.Phase p) {
        phase = p;
        phaseStart = System.nanoTime();
    }

    private static float phaseAge() {
        return (float) (System.nanoTime() - phaseStart) / 1.0E9F;
    }

    public static void render(GuiGraphics g, boolean preview) {
        Anim.frame();
        MusicConfig c = MusicSettings.get();
        dev.jun.nosclient.hud.HudLayout.Slot dock = dev.jun.nosclient.hud.HudLayout.get("karaoke");
        MediaManager mm = MediaManager.get();
        Track track = mm.track();
        Lyrics lyrics = mm.lyrics();
        long t = mm.position();
        if (phase == TopBar.Phase.TITLE && phaseAge() > 3.2F && lyrics != null) {
            phase = TopBar.Phase.LYRICS;
        }

        if (phase == TopBar.Phase.RESUMED && phaseAge() > 1.3F) {
            setPhase(lyrics != null ? TopBar.Phase.LYRICS : TopBar.Phase.TITLE);
        }

        if (track != null && track.playing && phase == TopBar.Phase.PAUSED) {
            setPhase(TopBar.Phase.RESUMED);
        }

        boolean simple = c.topBarStyle == MusicConfig.TopBarStyle.SIMPLE;
        boolean want = track != null || preview;
        if (track != null
                && !track.playing
                && c.hideWhenStopped
                && phase == TopBar.Phase.PAUSED
                && phaseAge() > 3.0F) {
            want = false;
        }

        if (!c.showOnlyWhenPlaying && !preview) {
            want = true;
        }

        vis = Anim.approach(vis, want ? 1.0F : 0.0F, want ? 7.0F : 5.0F);
        if (!(vis < 0.01F)) {
            float dim =
                    track != null
                                    && !track.playing
                                    && c.dimWhenIdle
                                    && phase == TopBar.Phase.PAUSED
                                    && phaseAge() > 2.0F
                            ? 0.6F
                            : 1.0F;
            if (!preview) {
                dim *= 0.25F + 0.75F * Hud.focusA;
            }

            float k = c.topBarScale * (float) dock.scale;
            float h = 18.0F * k;
            String font = c.font != null && !c.font.isEmpty() ? c.font : c.topBarFont;
            float ts = (font.equals("minecraft") ? 9.0F : 8.6F) * k;
            float textY =
                    (h - (font.equals("minecraft") ? ts * 0.78F : ts)) / 2.0F
                            + (font.equals("minecraft") ? 0.5F * k : -0.3F * k);
            Lyrics.Line line = null;
            int word = -1;
            if (lyrics != null && t >= 0L) {
                line = lyrics.lineAt(t);
                if (line == null) {
                    Lyrics.Line last = lyrics.lastStartedBefore(t);
                    if (last != null && t - last.end < 2500L) {
                        line = last;
                    }
                }

                if (line != null) {
                    word = line.wordAt(t);
                }
            }

            String title =
                    track != null
                            ? track.title
                            : I18n.get("nosclient.music.topbar.preview_title", new Object[0]);
            String artist = track != null ? track.artist : "Nos Client";
            boolean showLyrics = !simple && phase == TopBar.Phase.LYRICS && line != null;
            String status = null;
            if (phase == TopBar.Phase.PAUSED) {
                status = I18n.get("nosclient.music.topbar.paused", new Object[0]);
            } else if (phase == TopBar.Phase.RESUMED) {
                status = I18n.get("nosclient.music.topbar.resumed", new Object[0]);
            }

            String key =
                    showLyrics ? "L" + line.index : (status != null ? "S" + phase : "T" + title);
            if (!key.equals(contentKey)) {
                contentKey = key;
                swap = 0.0F;
                if (showLyrics) {
                    scroll = 0.0F;
                    hlW = 0.0F;
                }
            }

            swap = Anim.approach(swap, 1.0F, 9.0F);
            String text =
                    showLyrics ? line.text : (status != null ? status + "  ·  " + title : title);
            float textW = Gfx.textWidth(text, font, ts);
            float iconW = h;
            float barsW = 14.0F * k;
            float pad = 7.0F * k;
            float maxText = Math.min(330.0F * k, g.guiWidth() * 0.62F);
            float targetW = iconW + Math.min(textW, maxText) + barsW + pad * 2.0F;
            targetW = Math.max(targetW, 150.0F * k);
            float available = Math.max(1, g.guiWidth() - 4.0F);
            targetW = Math.min(targetW, available);
            width = Math.min(available, Anim.approach(width, targetW, 8.0F));
            float x = c.topBarX * g.guiWidth() - width / 2.0F;
            float y = c.topBarY * g.guiHeight() + (1.0F - Anim.easeOutCubic(vis)) * -10.0F;
            x = Math.max(2.0F, Math.min(g.guiWidth() - width - 2.0F, x));
            y = Math.max(1.0F, Math.min(g.guiHeight() - h - 1.0F, y));
            if (!dock.auto()) {
                x =
                        (float) (dock.x * g.guiWidth())
                                - (dock.ax == 0 ? 0 : dock.ax == 1 ? width / 2 : width);
                y = (float) (dock.y * g.guiHeight()) - (dock.ay == 0 ? 0 : h);
                x = Math.max(2, Math.min(g.guiWidth() - width - 2, x));
                y = Math.max(1, Math.min(g.guiHeight() - h - 1, y));
            }
            dev.jun.nosclient.hud.Hud.INSTANCE.rects.put(
                    "karaoke", new float[] {x, y, x + width, y + h});
            lastX = x;
            lastY = y;
            lastW = width;
            lastH = h;
            float a = Anim.easeOutCubic(vis) * dim * (float) dock.alpha;
            Themes.Theme th = Themes.current();
            int accent = Themes.accent();
            Gfx.shadow(g, x, y, width, h, h / 2.0F, 5.0F * k, Col.alpha(-16777216, a * 0.5F));
            int barBg =
                    c.barColor != 0
                            ? Col.withAlpha(Col.darken(c.barColor, 0.78F), 240)
                            : -266988767;
            Gfx.round(g, x, y, width, h, h / 2.0F, Col.alpha(barBg, a));
            Gfx.roundOutline(g, x, y, width, h, h / 2.0F, 0.5F, Col.alpha(1090519039, a));
            if (Hud.beat > 0.05F && c.beatGlow) {
                Gfx.glow(
                        g,
                        x,
                        y,
                        width,
                        h,
                        h / 2.0F,
                        3.0F * k,
                        Col.alpha(accent, a * 0.35F * Hud.beat));
            }

            float icx = x + h / 2.0F + 1.0F * k;
            float icy = y + h / 2.0F;
            float ir = h * 0.34F;
            if (phase == TopBar.Phase.PAUSED) {
                Gfx.circle(g, icx, icy, ir, Col.alpha(-1514251, a));
                Gfx.rect(
                        g,
                        icx - ir * 0.42F,
                        icy - ir * 0.45F,
                        ir * 0.28F,
                        ir * 0.9F,
                        Col.alpha(-15330527, a));
                Gfx.rect(
                        g,
                        icx + ir * 0.14F,
                        icy - ir * 0.45F,
                        ir * 0.28F,
                        ir * 0.9F,
                        Col.alpha(-15330527, a));
            } else if (showLyrics && CoverArt.ready()) {
                CoverArt.drawRound(g, icx, icy, ir * 1.15F, a);
            } else if (showLyrics) {
                spin = spin + Anim.dt() * (track != null && track.playing ? 2.4F : 0.0F);
                Gfx.circle(g, icx, icy, ir, Col.alpha(Col.lerp(accent, -1, 0.25F), a));
                Gfx.circle(g, icx, icy, ir * 0.72F, Col.alpha(-14015680, a));
                float sx = (float) Math.cos(spin) * ir * 0.45F;
                float sy = (float) Math.sin(spin) * ir * 0.45F;
                Gfx.circle(g, icx + sx, icy + sy, ir * 0.16F, Col.alpha(-1862270977, a));
                Gfx.circle(g, icx, icy, ir * 0.22F, Col.alpha(accent, a));
            } else {
                float pop = Anim.easeOutBack(Math.min(1.0F, phaseAge() * 3.0F));
                float rr = ir * (0.6F + 0.4F * pop);
                String src = track != null ? track.sourceApp : "Spotify";
                boolean yt = !"Spotify".equals(src);
                float s = Gfx.scale();
                int px = Math.max(1, Math.round(rr * 2.15F * s));
                g.pose().pushMatrix();
                g.pose().translate(icx, icy);
                g.pose().scale(1.0F / s, 1.0F / s);
                g.blit(
                        RenderPipelines.GUI_TEXTURED,
                        yt ? YOUTUBE : SPOTIFY,
                        -px / 2,
                        -px / 2,
                        0.0F,
                        0.0F,
                        px,
                        px,
                        128,
                        128,
                        128,
                        128,
                        Col.alpha(-1, a));
                g.pose().popMatrix();
            }

            float tx = x + iconW + 2.0F * k;
            float tw = Math.max(0, width - iconW - barsW - pad - 2.0F * k);
            int sx0 = (int) Math.floor(tx);
            int sx1 = (int) Math.ceil(tx + tw);
            g.enableScissor(sx0, (int) y, sx1, (int) Math.ceil(y + h));
            float slide = (1.0F - Anim.easeOutCubic(swap)) * 6.0F * k;
            float ta = a * Anim.easeOutCubic(swap);
            if (showLyrics) {
                drawKaraoke(g, line, word, t, font, ts, tx, y + textY + slide, tw, ta, accent);
            } else {
                float yy = y + textY + slide;
                if (status != null) {
                    Gfx.text(
                            g,
                            Gfx.ellipsize(text, font, ts, tw),
                            font,
                            tx,
                            yy,
                            ts,
                            Col.alpha(-1514251, ta));
                } else {
                    String full = title + (artist.isEmpty() ? "" : "  ·  " + artist);
                    String shown = Gfx.ellipsize(full, font, ts, tw);
                    float w1 =
                            Gfx.text(
                                    g,
                                    Gfx.ellipsize(title, font, ts, tw),
                                    font,
                                    tx,
                                    yy,
                                    ts,
                                    Col.alpha(-1, ta));
                    if (shown.length() > title.length() && w1 < tw) {
                        Gfx.text(
                                g,
                                Gfx.ellipsize("  ·  " + artist, font, ts, tw - w1),
                                font,
                                tx + w1,
                                yy,
                                ts,
                                Col.alpha(-6646859, ta));
                    }
                }
            }

            g.disableScissor();
            float bx = x + width - barsW - pad + 3.0F * k;
            float by = y + h / 2.0F;
            boolean playing = track != null && track.playing;
            double time = Anim.seconds();

            for (int i = 0; i < bars.length; i++) {
                float target =
                        playing
                                ? 0.25F
                                        + 0.35F
                                                * (float)
                                                        Math.abs(
                                                                Math.sin(
                                                                        time * (3.1 + i * 1.7) + i))
                                        + 0.45F * Hud.beat * (i % 2 == 0 ? 1.0F : 0.7F)
                                : 0.18F;
                bars[i] = Anim.approach(bars[i], Math.min(1.0F, target), 14.0F);
                float bh = Math.max(1.5F * k, bars[i] * h * 0.5F);
                Gfx.round(
                        g,
                        bx + i * 3.0F * k,
                        by - bh / 2.0F,
                        1.8F * k,
                        bh,
                        0.9F * k,
                        Col.alpha(Col.lerp(th.b(), accent, i / 3.0F), a));
            }
        }
    }

    private static void drawKaraoke(
            GuiGraphics g,
            Lyrics.Line line,
            int word,
            long t,
            String font,
            float ts,
            float tx,
            float ty,
            float tw,
            float a,
            int accent) {
        List<Lyrics.Word> words = line.words;
        float space = Gfx.textWidth(" ", font, ts);
        float[] xs = new float[words.size()];
        float[] ws = new float[words.size()];
        float cx = 0.0F;

        for (int i = 0; i < words.size(); i++) {
            xs[i] = cx;
            ws[i] = Gfx.textWidth(Lyrics.show(words.get(i).text), font, ts);
            cx += ws[i] + space;
        }

        float total = cx - space;
        boolean rtl = Fonts.isRtl(line.text);
        if (rtl) {
            for (int i = 0; i < words.size(); i++) {
                xs[i] = total - xs[i] - ws[i];
            }
        }

        float target = rtl && total > tw ? total - tw : 0.0F;
        if (word >= 0 && total > tw) {
            float wanted = xs[word] + ws[word] / 2.0F - tw * 0.55F;
            target = Math.max(0.0F, Math.min(total - tw, wanted));
        }

        scroll = Anim.approach(scroll, target, 6.0F);
        float ox = tx - scroll;
        if (word >= 0) {
            float pad = 2.2F;
            float targetX = xs[word] - pad;
            float targetW = ws[word] + pad * 2.0F;
            if (hlW <= 0.0F) {
                hlX = targetX;
                hlW = targetW;
            }

            hlX = Anim.approach(hlX, targetX, 16.0F);
            hlW = Anim.approach(hlW, targetW, 16.0F);
            float bh = ts + 2.6F;
            float left = Math.max(tx, ox + hlX);
            float right = Math.min(tx + tw, ox + hlX + hlW);
            if (right > left) {
                Gfx.round(g, left, ty - 1.3F, right - left, bh, 3.0F, Col.alpha(accent, a * 0.95F));
            }
        }

        for (int i = 0; i < words.size(); i++) {
            int col = i < word ? -1 : (i == word ? -1 : -4607018);
            Gfx.text(
                    g, Lyrics.show(words.get(i).text), font, ox + xs[i], ty, ts, Col.alpha(col, a));
        }
    }

    private static void drawArc(
            GuiGraphics g, float cx, float cy, float r, float thick, int color, float part) {
        int n = 18;
        int m = Math.max(2, Math.round(n * part) + 1);
        float[] xs = new float[m];
        float[] ys = new float[m];
        double from = Math.toRadians(200.0);
        double span = Math.toRadians(140.0) * part;

        for (int i = 0; i < m; i++) {
            double ang = from + span * i / (m - 1);
            xs[i] = cx + (float) Math.cos(ang) * r;
            ys[i] = cy + (float) Math.sin(ang) * r;
        }

        Gfx.bandPath(g, xs, ys, m, thick * 1.4F, color);
    }

    private enum Phase {
        TITLE,
        LYRICS,
        PAUSED,
        RESUMED;
    }
}
