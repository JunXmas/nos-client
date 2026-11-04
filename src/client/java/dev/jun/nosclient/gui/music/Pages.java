package dev.jun.nosclient.gui.music;

import dev.jun.nosclient.ClientEffects;
import dev.jun.nosclient.MediaKeys;
import dev.jun.nosclient.config.EffectsConfig;
import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.feat.AutoPerf;
import dev.jun.nosclient.media.AppVolume;
import dev.jun.nosclient.media.MediaManager;
import dev.jun.nosclient.media.SpotifyWeb;
import dev.jun.nosclient.media.Track;
import dev.jun.nosclient.render.Chroma;
import dev.jun.nosclient.render.Fonts;
import dev.jun.nosclient.render.Gfx;
import dev.jun.nosclient.render.Themes;
import dev.jun.nosclient.render.WorldLyrics;
import dev.jun.nosclient.sky.SkyImage;
import dev.jun.nosclient.sky.SkyPreset;
import dev.jun.nosclient.sky.WorldSky;
import dev.jun.nosclient.util.Anim;
import dev.jun.nosclient.util.Col;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

final class Pages {
    private static int skyPage;
    private static final int[] DARK =
            argb(
                    new int[] {
                        131848, 460058, 656920, 1378328, 201240, 1053982, 1839672, 2758216, 4068404,
                        668480, 1784412, 4852242
                    });
    private static final int[] BRIGHT =
            argb(
                    new int[] {
                        10719743, 13127912, 15757496, 15756906, 16296026, 16310384, 5957792,
                        3856584, 8046840, 5929200, 15263984, 16777215
                    });
    private static final int[] PLAIN_PALETTE =
            new int[] {
                -2084816, -1013212, -863174, -12793766, -12793640, -12948240, -7451928, -1021264,
                -855310, -16119286
            };
    private static final int[] SKY_PALETTE =
            new int[] {
                -16777216, -16053473, -15592914, -14020560, -16045520, -15459284, -14017974,
                -11916694, -10870704, -15050134, -14005622, -9826280
            };
    private static final int[] AURORA_PALETTE =
            new int[] {
                -5726977, -3649304, -1019720, -1021334, -481190, -466832, -10819424, -12920632,
                -8730376, -10848016, -1513232, -1
            };
    private static String pasteStatus;
    private static final int[] TARGETS = new int[] {0, 60, 90, 120, 144, 240};

    private Pages() {}

    private static MusicConfig c() {
        return MusicSettings.get();
    }

    private static String pct(double v) {
        return Math.round(v * 100.0) + "%";
    }

    private static String ms(double v) {
        long m = Math.round(v);
        return m == 0L
                ? "0 ms"
                : (m > 0L ? "+" : "") + String.format(Locale.ROOT, "%.2f s", m / 1000.0);
    }

    private static String mult(double v) {
        return String.format(Locale.ROOT, "%.2fx", v);
    }

    static List<Ui.Widget> build(MusicPanels.Tab tab, MusicPanels screen, boolean heroVisible) {
        List<Ui.Widget> l = new ArrayList<>();
        switch (tab) {
            case NOW:
                now(l, heroVisible);
                break;
            case LYRICS:
                lyrics(l, screen);
                break;
            case STYLE:
                style(l, screen);
                break;
            case SKY:
                sky(l, screen);
                break;
            case DISPLAY:
                display(l);
                break;
            case KEYS:
                keys(l);
                break;
            case SAVED:
                saved(l, screen);
                break;
            case PERF:
                perf(l, screen);
        }

        l.add(new Ui.Spacer(8.0F));
        return l;
    }

    private static void now(List<Ui.Widget> l, boolean heroVisible) {
        if (!heroVisible) {
            l.add(
                    new Ui.Custom(
                            w -> NowPlaying.height(w),
                            (g, x, y, w, h, mx, my, a) ->
                                    NowPlaying.renderHero(g, x, y, w, h, mx, my, a),
                            (mx, my, x, y, w, h) -> {
                                if (!Ui.inside(mx, my, x, y, w, h)) {
                                    return false;
                                }

                                NowPlaying.click(mx, my);
                                return true;
                            }));
        }

        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Button(
                                        "nosclient.music.opt.open_player",
                                        "nosclient.music.opt.open_player.desc",
                                        () -> Ui.tr("nosclient.music.button.open"),
                                        "music",
                                        true,
                                        () ->
                                                Minecraft.getInstance()
                                                        .setScreen(new PlayerScreen()))));
        l.add(new Ui.Section("nosclient.music.section.quick"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.enabled",
                                        "nosclient.music.opt.enabled.desc",
                                        () -> c().enabled,
                                        v -> c().enabled = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.show_lyrics",
                                        "nosclient.music.opt.show_lyrics.desc",
                                        () -> c().showLyrics,
                                        v -> {
                                            c().showLyrics = v;
                                        }))
                        .add(
                                new Ui.Choice(
                                        "nosclient.music.opt.lyrics_style",
                                        null,
                                        new String[] {
                                            "nosclient.music.style.sky",
                                            "nosclient.music.style.floating"
                                        },
                                        () -> Math.min(1, c().lyricsStyle.ordinal()),
                                        i -> {
                                            c().lyricsStyle = MusicConfig.LyricsStyle.values()[i];
                                            WorldLyrics.clear();
                                        }))
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.opt.scale",
                                        0.4,
                                        2.5,
                                        0.05,
                                        () ->
                                                c().lyricsStyle == MusicConfig.LyricsStyle.SKY
                                                        ? dev.jun.nosclient.scene.SceneConfig
                                                                .INSTANCE
                                                                .lyricsSize
                                                        : c().lyricsScale,
                                        v -> {
                                            if (c().lyricsStyle == MusicConfig.LyricsStyle.SKY)
                                                dev.jun.nosclient.scene.SceneConfig.INSTANCE
                                                                .lyricsSize =
                                                        v;
                                            else c().lyricsScale = (float) v;
                                        },
                                        Pages::mult))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.top_bar",
                                        "nosclient.music.opt.top_bar.desc",
                                        () -> c().showTopBar,
                                        v -> c().showTopBar = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.chroma",
                                        "nosclient.music.opt.chroma.desc",
                                        () -> c().chroma,
                                        v -> c().chroma = v))
                        .add(
                                new Ui.Choice(
                                        "nosclient.music.opt.chroma_color",
                                        null,
                                        Arrays.stream(Chroma.COLORS)
                                                .map(n -> "nosclient.music.chroma." + n)
                                                .toArray(String[]::new),
                                        () -> c().chromaColor,
                                        i -> c().chromaColor = i))
                        .add(
                                AppVolume.supported()
                                        ? new Ui.Slider(
                                                "nosclient.music.opt.music_volume",
                                                0.0,
                                                1.0,
                                                0.01,
                                                () -> Math.max(0.0F, AppVolume.get()),
                                                v -> AppVolume.set((float) v),
                                                v ->
                                                        AppVolume.get() < 0.0F
                                                                ? "—"
                                                                : Math.round(v * 100.0) + "%")
                                        : new Ui.Info(
                                                () ->
                                                        Ui.tr(
                                                                "nosclient.music.ui.volume.unsupported"))));
        source(l);
    }

    private static void lyrics(List<Ui.Widget> l, MusicPanels screen) {
        l.add(new Ui.Section("nosclient.music.section.general"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.show_lyrics",
                                        "nosclient.music.opt.show_lyrics.desc",
                                        () -> c().showLyrics,
                                        v -> {
                                            c().showLyrics = v;
                                        }))
                        .add(
                                new Ui.Choice(
                                        "nosclient.music.opt.lyrics_style",
                                        "nosclient.music.opt.lyrics_style.desc",
                                        new String[] {
                                            "nosclient.music.style.sky",
                                            "nosclient.music.style.floating"
                                        },
                                        () -> Math.min(1, c().lyricsStyle.ordinal()),
                                        i -> {
                                            c().lyricsStyle = MusicConfig.LyricsStyle.values()[i];
                                            WorldLyrics.clear();
                                            screen.rebuildSoon();
                                        })));
        l.add(new Ui.Section("nosclient.music.section.size"));
        Ui.Card size =
                new Ui.Card()
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.opt.scale",
                                        0.4,
                                        2.5,
                                        0.05,
                                        () ->
                                                c().lyricsStyle == MusicConfig.LyricsStyle.SKY
                                                        ? dev.jun.nosclient.scene.SceneConfig
                                                                .INSTANCE
                                                                .lyricsSize
                                                        : c().lyricsScale,
                                        v -> {
                                            if (c().lyricsStyle == MusicConfig.LyricsStyle.SKY)
                                                dev.jun.nosclient.scene.SceneConfig.INSTANCE
                                                                .lyricsSize =
                                                        v;
                                            else c().lyricsScale = (float) v;
                                        },
                                        Pages::mult));
        if (c().lyricsStyle == MusicConfig.LyricsStyle.FLOATING) {
            size.add(
                            new Ui.Slider(
                                    "nosclient.music.opt.distance",
                                    0.4,
                                    2.0,
                                    0.05,
                                    () -> c().lyricsDistance,
                                    v -> c().lyricsDistance = (float) v,
                                    Pages::mult))
                    .add(
                            new Ui.Toggle(
                                    "nosclient.music.opt.follow_view",
                                    "nosclient.music.opt.follow_view.desc",
                                    () -> c().followView,
                                    v -> c().followView = v))
                    .add(
                            new Ui.Slider(
                                    "nosclient.music.opt.float_height",
                                    -1.0,
                                    4.0,
                                    0.25,
                                    () -> c().floatHeight,
                                    v -> c().floatHeight = (float) v,
                                    v ->
                                            (v > 0.0 ? "+" : "")
                                                    + String.format(Locale.ROOT, "%.2f", v)
                                                            .replaceAll("0+$", "")
                                                            .replaceAll("\\.$", "")));
        }

        if (c().lyricsStyle == MusicConfig.LyricsStyle.SKY) {
            size.add(
                            new Ui.Slider(
                                    "nosclient.music.opt.distance",
                                    30.0,
                                    300.0,
                                    10.0,
                                    () -> c().skyDistance,
                                    v -> c().skyDistance = (float) v,
                                    v -> Math.round(v) + "m"))
                    .add(
                            new Ui.Toggle(
                                    "nosclient.music.opt.sky_follow",
                                    "nosclient.music.opt.sky_follow.desc",
                                    () -> dev.jun.nosclient.scene.SceneConfig.INSTANCE.lyricsFollow,
                                    v ->
                                            dev.jun.nosclient.scene.SceneConfig.INSTANCE
                                                            .lyricsFollow =
                                                    v));
        }

        size.add(
                new Ui.Slider(
                        "nosclient.music.opt.opacity",
                        0.2,
                        1.0,
                        0.05,
                        () -> c().textOpacity,
                        v -> c().textOpacity = (float) v,
                        Pages::pct));
        if (c().lyricsStyle == MusicConfig.LyricsStyle.FLOATING) {
            size.add(
                    new Ui.Slider(
                            "nosclient.music.opt.max_lines",
                            1.0,
                            3.0,
                            1.0,
                            () -> c().maxLines,
                            v -> c().maxLines = (int) Math.round(v),
                            v -> String.valueOf((int) Math.round(v))));
        }
        l.add(size);
        if (c().lyricsStyle == MusicConfig.LyricsStyle.FLOATING) {
            l.add(new Ui.Section("nosclient.music.section.animation"));
            l.add(
                    new Ui.Card()
                            .add(
                                    new Ui.Choice(
                                            "nosclient.music.opt.enter",
                                            null,
                                            new String[] {
                                                "nosclient.music.enter.word",
                                                "nosclient.music.enter.fade",
                                                "nosclient.music.enter.rise",
                                                "nosclient.music.enter.neon"
                                            },
                                            () -> c().enterAnim.ordinal(),
                                            i -> c().enterAnim = MusicConfig.EnterAnim.values()[i]))
                            .add(
                                    new Ui.Choice(
                                            "nosclient.music.opt.exit",
                                            null,
                                            new String[] {
                                                "nosclient.music.exit.float_up",
                                                "nosclient.music.exit.drift",
                                                "nosclient.music.exit.fade"
                                            },
                                            () -> c().exitAnim.ordinal(),
                                            i -> c().exitAnim = MusicConfig.ExitAnim.values()[i]))
                            .add(
                                    new Ui.Slider(
                                            "nosclient.music.opt.anim_speed",
                                            0.5,
                                            2.0,
                                            0.05,
                                            () -> c().animSpeed,
                                            v -> c().animSpeed = (float) v,
                                            Pages::mult)));
        }
        l.add(new Ui.Section("nosclient.music.section.advanced"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.underground",
                                        "nosclient.music.opt.underground.desc",
                                        () -> c().showUnderground,
                                        v -> c().showUnderground = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.music_notes",
                                        "nosclient.music.opt.music_notes.desc",
                                        () -> c().musicNotes,
                                        v -> c().musicNotes = v)));
    }

    private static void style(List<Ui.Widget> l, MusicPanels screen) {
        l.add(new Ui.Section("nosclient.music.section.font"));
        List<Fonts.LyricFont> fonts = Fonts.lyricsAll();
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Chips(
                                                "nosclient.music.section.font",
                                                null,
                                                fonts.stream()
                                                        .map(Fonts.LyricFont::name)
                                                        .toArray(String[]::new),
                                                () -> Math.max(0, indexOf(fonts, c().font)),
                                                i -> {
                                                    c().font = fonts.get(i).id();
                                                    WorldLyrics.clear();
                                                })
                                        .fonts(
                                                fonts.stream()
                                                        .map(Fonts.LyricFont::id)
                                                        .toArray(String[]::new))
                                        .reset(
                                                () -> {
                                                    c().font = (new MusicConfig()).font;
                                                    WorldLyrics.clear();
                                                }))
                        .add(
                                new Ui.Choice(
                                        "nosclient.music.opt.text_shadow",
                                        null,
                                        new String[] {
                                            "nosclient.music.shadow.off",
                                            "nosclient.music.shadow.soft",
                                            "nosclient.music.shadow.strong"
                                        },
                                        () -> c().textShadow,
                                        i -> c().textShadow = i))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.all_caps",
                                        null,
                                        () -> c().allCaps,
                                        v -> {
                                            c().allCaps = v;
                                            WorldLyrics.clear();
                                        })));
        l.add(new Ui.Section("nosclient.music.section.look"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Colors(
                                        "nosclient.music.opt.colour",
                                        "nosclient.music.opt.colour.desc",
                                        () -> c().singleColor,
                                        v -> c().singleColor = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.beat_glow",
                                        "nosclient.music.opt.beat_glow.desc",
                                        () -> c().beatGlow,
                                        v -> c().beatGlow = v)));
        themeGrid(l);
    }

    private static void themeGrid(List<Ui.Widget> l) {
        l.add(new Ui.Section("nosclient.music.section.text_color"));
        List<Themes.Theme> all = Themes.ALL;
        IntSupplier cur =
                () -> {
                    for (int i = 0; i < all.size(); i++) {
                        if (all.get(i).id().equals(c().theme)) {
                            return i;
                        }
                    }

                    return 0;
                };
        IntConsumer pick =
                i -> {
                    c().theme = all.get(Math.floorMod(i, all.size())).id();
                    c().customTheme = false;
                    c().singleColor = 0;
                };
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Chips(
                                                "nosclient.music.section.text_color",
                                                null,
                                                all.stream()
                                                        .map(Themes.Theme::key)
                                                        .toArray(String[]::new),
                                                () ->
                                                        !c().customTheme && c().singleColor == 0
                                                                ? cur.getAsInt()
                                                                : -1,
                                                pick)
                                        .dots(all.stream().mapToInt(Themes.Theme::a).toArray())
                                        .reset(
                                                () -> {
                                                    MusicConfig d = new MusicConfig();
                                                    c().theme = d.theme;
                                                    c().customTheme = false;
                                                    c().singleColor = 0;
                                                })));
        Ui.Card custom = new Ui.Card();
        custom.add(
                new Ui.Toggle(
                        "nosclient.music.opt.custom_theme",
                        "nosclient.music.opt.custom_theme.desc",
                        () -> c().customTheme,
                        v -> {
                            c().customTheme = v;
                            if (v) {
                                Themes.Theme t = Themes.byId(c().theme);
                                c().customMain = t.main();
                                c().customA = t.a();
                                c().customB = t.b();
                            }
                        }));
        custom.add(
                hueSlider(
                        "nosclient.music.opt.custom_main",
                        () -> c().customMain,
                        v -> c().customMain = v,
                        0.12F,
                        1.0F));
        custom.add(
                hueSlider(
                        "nosclient.music.opt.custom_a",
                        () -> c().customA,
                        v -> c().customA = v,
                        0.7F,
                        1.0F));
        custom.add(
                hueSlider(
                        "nosclient.music.opt.custom_b",
                        () -> c().customB,
                        v -> c().customB = v,
                        0.6F,
                        1.0F));
        l.add(custom);
    }

    private static Ui.Widget hueSlider(
            String label, IntSupplier get, IntConsumer set, float sat, float val) {
        return new Ui.Slider(
                label,
                0.0,
                358.0,
                2.0,
                () -> Math.min(358, Math.round(Col.toHsv(get.getAsInt())[0] * 360.0F)),
                v -> {
                    set.accept(Col.hsv((float) (v / 360.0), sat, val));
                    c().customTheme = true;
                },
                v -> Math.round(v) + "°") {
            @Override
            public void render(GuiGraphics g, float ox, float oy, double mx, double my, float a) {
                super.render(g, ox, oy, mx, my, a);
                Gfx.circle(
                        g,
                        ox + this.x + Gfx.textWidth(Ui.tr(this.label), Ui.FB, 8.4F) + 10.0F,
                        oy + this.y + 9.5F,
                        3.6F,
                        Col.alpha(get.getAsInt(), a));
            }
        };
    }

    private static Ui.Widget fontGrid() {
        List<Fonts.LyricFont> fonts = Fonts.lyricsAll();
        float[] hover = new float[fonts.size()];
        float cellH = 34.0F;
        return new Ui.Custom(
                w -> (float) Math.ceil(fonts.size() / 2.0) * (cellH + 6.0F) + 4.0F,
                (g, x, y, w, h, mx, my, a) -> {
                    float cw = (w - 6.0F) / 2.0F;
                    Themes.Theme th = Themes.current();

                    for (int i = 0; i < fonts.size(); i++) {
                        Fonts.LyricFont f = fonts.get(i);
                        float fx = x + i % 2 * (cw + 6.0F);
                        float fy = y + i / 2 * (cellH + 6.0F);
                        boolean sel = c().font.equals(f.id());
                        boolean over = Ui.inside(mx, my, fx, fy, cw, cellH);
                        if (over && hover[i] < 0.05F) {
                            UiSounds.play(UiSounds.HOVER, 1.2F);
                        }

                        hover[i] = Anim.approach(hover[i], over ? 1.0F : 0.0F, 14.0F);
                        float lift = -hover[i] * 1.2F;
                        if (sel) {
                            Gfx.glow(
                                    g,
                                    fx,
                                    fy + lift,
                                    cw,
                                    cellH,
                                    8.0F,
                                    4.0F,
                                    Col.alpha(Themes.accent(), a * 0.35F));
                        }

                        Gfx.round(
                                g,
                                fx,
                                fy + lift,
                                cw,
                                cellH,
                                8.0F,
                                Col.alpha(Col.lerp(-15001562, -14541264, hover[i]), a));
                        Gfx.roundOutline(
                                g,
                                fx,
                                fy + lift,
                                cw,
                                cellH,
                                8.0F,
                                sel ? 1.2F : 0.5F,
                                Col.alpha(sel ? Themes.accent() : 352321535, a));
                        float size = 14.5F;
                        String name = Gfx.ellipsize(f.name(), f.id(), size, cw - 24.0F);
                        if (sel) {
                            Gfx.neon(
                                    g,
                                    name,
                                    f.id(),
                                    fx + cw / 2.0F - 4.0F,
                                    fy + lift + (cellH - size) / 2.0F - 1.0F,
                                    size,
                                    th.main(),
                                    th.a(),
                                    th.b(),
                                    a,
                                    true,
                                    0.4F);
                            Gfx.circle(
                                    g,
                                    fx + cw - 10.0F,
                                    fy + lift + 10.0F,
                                    5.0F,
                                    Col.alpha(Themes.accent(), a));
                            Icons.draw(
                                    g,
                                    "check",
                                    fx + cw - 10.0F,
                                    fy + lift + 10.0F,
                                    6.5F,
                                    Col.alpha(-1, a));
                        } else {
                            Gfx.textCentered(
                                    g,
                                    name,
                                    f.id(),
                                    fx + cw / 2.0F - 4.0F,
                                    fy + lift + (cellH - size) / 2.0F - 1.0F,
                                    size,
                                    Col.alpha(Col.lerp(-6515020, -856070, hover[i]), a));
                        }
                    }
                },
                (mx, my, x, y, w, h) -> {
                    float cw = (w - 6.0F) / 2.0F;

                    for (int i = 0; i < fonts.size(); i++) {
                        float fx = x + i % 2 * (cw + 6.0F);
                        float fy = y + i / 2 * (cellH + 6.0F);
                        if (Ui.inside(mx, my, fx, fy, cw, cellH)) {
                            c().font = fonts.get(i).id();
                            MusicSettings.save();
                            WorldLyrics.clear();
                            UiSounds.play(UiSounds.TOGGLE_ON, 0.9F + i * 0.02F);
                            return true;
                        }
                    }

                    return false;
                });
    }

    private static EffectsConfig sc() {
        return ClientEffects.cfg();
    }

    private static int[] argb(int[] rgb) {
        int[] o = new int[rgb.length];

        for (int i = 0; i < rgb.length; i++) {
            o[i] = 0xFF000000 | rgb[i];
        }

        return o;
    }

    private static void sky(List<Ui.Widget> l, MusicPanels screen) {
        EffectsConfig c = sc();
        l.add(new Ui.Section("nosclient.music.ui.sky.picture"));
        Ui.Card pic =
                new Ui.Card()
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.ui.sky.picture.on",
                                        "nosclient.music.ui.sky.picture.on.sub",
                                        () -> c.skyImage,
                                        v -> {
                                            c.skyImage = v;
                                            screen.rebuildSoon();
                                        }));
        List<String> pics = SkyImage.files();
        pic.add(
                new Ui.Button(
                        "nosclient.music.ui.sky.picture.folder",
                        null,
                        () -> Ui.tr("nosclient.music.ui.sky.picture.folder"),
                        "download",
                        false,
                        SkyImage::openFolder));
        pic.add(
                new Ui.Button(
                        "nosclient.music.ui.sky.picture.reload",
                        null,
                        () -> Ui.tr("nosclient.music.ui.sky.picture.reload"),
                        "refresh",
                        false,
                        () -> {
                            SkyImage.reload();
                            screen.rebuildSoon();
                        }));
        l.add(pic);
        if (!pics.isEmpty()) {
            float cellH = 58.0F;
            float[] hov = new float[pics.size()];
            l.add(
                    new Ui.Custom(
                            w -> {
                                int cols = w > 330.0F ? 3 : 2;
                                return (float) Math.ceil((double) pics.size() / cols)
                                                * (cellH + 6.0F)
                                        + 2.0F;
                            },
                            (g, x, y, w, h, mx, my, a) -> {
                                int cols = w > 330.0F ? 3 : 2;
                                float cw = (w - 6 * (cols - 1)) / cols;
                                String curx =
                                        pics.contains(c.skyImageFile)
                                                ? c.skyImageFile
                                                : pics.get(0);

                                for (int ix = 0; ix < pics.size(); ix++) {
                                    String name = pics.get(ix);
                                    float fx = x + ix % cols * (cw + 6.0F);
                                    float fy = y + ix / cols * (cellH + 6.0F);
                                    boolean on = c.skyImage && name.equals(curx);
                                    boolean over = Ui.inside(mx, my, fx, fy, cw, cellH);
                                    hov[ix] = Anim.approach(hov[ix], over ? 1.0F : 0.0F, 14.0F);
                                    float ih = cellH - 14.0F;
                                    Gfx.round(
                                            g,
                                            fx - 1.0F,
                                            fy - 1.0F,
                                            cw + 2.0F,
                                            cellH + 2.0F,
                                            7.0F,
                                            Col.alpha(
                                                    on
                                                            ? Themes.accent()
                                                            : Col.lerp(
                                                                    -14541264, -13883584, hov[ix]),
                                                    a));
                                    Identifier id = SkyImage.thumb(name);
                                    if (id != null) {
                                        Gfx.image(
                                                g,
                                                id,
                                                160,
                                                80,
                                                fx + 2.0F,
                                                fy + 2.0F,
                                                cw - 4.0F,
                                                ih - 2.0F,
                                                Col.alpha(-1, a));
                                    } else {
                                        Gfx.round(
                                                g,
                                                fx + 2.0F,
                                                fy + 2.0F,
                                                cw - 4.0F,
                                                ih - 2.0F,
                                                5.0F,
                                                Col.alpha(-15396065, a));
                                    }

                                    Gfx.text(
                                            g,
                                            Gfx.ellipsize(name, Ui.F, 6.4F, cw - 8.0F),
                                            Ui.F,
                                            fx + 4.0F,
                                            fy + ih + 2.5F,
                                            6.4F,
                                            Col.alpha(on ? -856070 : -6515020, a));
                                }
                            },
                            (mx, my, x, y, w, h) -> {
                                int cols = w > 330.0F ? 3 : 2;
                                float cw = (w - 6 * (cols - 1)) / cols;

                                for (int ix = 0; ix < pics.size(); ix++) {
                                    float fx = x + ix % cols * (cw + 6.0F);
                                    float fy = y + ix / cols * (cellH + 6.0F);
                                    if (Ui.inside(mx, my, fx, fy, cw, cellH)) {
                                        c.skyImageFile = pics.get(ix);
                                        c.skyImage = true;
                                        ClientEffects.saveConfig();
                                        UiSounds.play(UiSounds.TOGGLE_ON, 0.9F + ix * 0.03F);
                                        return true;
                                    }
                                }

                                return false;
                            }));
        }

        l.add(
                new Ui.Info(
                        () -> {
                            String info =
                                    Ui.tr("nosclient.music.ui.sky.picture.info", "4096 x 2048");
                            if (c.skyImage && SkyImage.files().isEmpty()) {
                                info = info + "\n" + Ui.tr("nosclient.music.ui.sky.picture.none");
                            } else if (!SkyImage.size().isEmpty()) {
                                info =
                                        info
                                                + "\n"
                                                + Ui.tr(
                                                        "nosclient.music.ui.sky.picture.loaded",
                                                        SkyImage.size());
                            }

                            return info;
                        }));
        l.add(new Ui.Section("nosclient.music.ui.sky.style"));
        l.add(
                new Ui.SkyGrid(
                        SkyPreset.order(),
                        () -> c.sky >= 0 ? SkyPreset.get(c.sky) : null,
                        p -> {
                            c.sky = p.ordinal();
                            c.lastSky = p.ordinal();
                            screen.rebuildSoon();
                        }));
        SkyPreset cur = SkyPreset.get(c.sky >= 0 ? c.sky : c.lastSky);
        if (cur == SkyPreset.CUSTOM && c.sky >= 0) {
            Class<SkyPreset> A = SkyPreset.class;
            SkyPreset.Weather[] ws = SkyPreset.Weather.values();
            String[] wn =
                    Arrays.stream(ws)
                            .map(
                                    w ->
                                            w == SkyPreset.Weather.NONE
                                                    ? "nosclient.music.skyed.noweather"
                                                    : "nosclient.music.feat."
                                                            + w.name().toLowerCase(Locale.ROOT))
                            .toArray(String[]::new);
            l.add(new Ui.Section("nosclient.music.skyed.title"));
            l.add(
                    new Ui.Card()
                            .add(
                                    new Ui.Colors(
                                            "nosclient.music.skyed.top",
                                            null,
                                            () -> 0xFF000000 | c.customTop,
                                            v -> {
                                                c.customTop = v & 16777215;
                                                SkyPreset.applyCustom();
                                            },
                                            DARK,
                                            false))
                            .add(
                                    new Ui.Colors(
                                            "nosclient.music.skyed.horizon",
                                            null,
                                            () -> 0xFF000000 | c.customHorizon,
                                            v -> {
                                                c.customHorizon = v & 16777215;
                                                SkyPreset.applyCustom();
                                            },
                                            DARK,
                                            false))
                            .add(
                                    new Ui.Colors(
                                            "nosclient.music.skyed.aurora1",
                                            null,
                                            () -> 0xFF000000 | c.customAurora1,
                                            v -> {
                                                c.customAurora1 = v & 16777215;
                                                SkyPreset.applyCustom();
                                            },
                                            BRIGHT,
                                            false))
                            .add(
                                    new Ui.Colors(
                                            "nosclient.music.skyed.aurora2",
                                            null,
                                            () -> 0xFF000000 | c.customAurora2,
                                            v -> {
                                                c.customAurora2 = v & 16777215;
                                                SkyPreset.applyCustom();
                                            },
                                            BRIGHT,
                                            false))
                            .add(
                                    new Ui.Toggle(
                                            "nosclient.music.feat.aurora",
                                            null,
                                            () -> c.customAurora,
                                            v -> {
                                                c.customAurora = v;
                                                SkyPreset.applyCustom();
                                            }))
                            .add(
                                    new Ui.Toggle(
                                            "nosclient.music.feat.stars",
                                            null,
                                            () -> c.customStars,
                                            v -> {
                                                c.customStars = v;
                                                SkyPreset.applyCustom();
                                            }))
                            .add(
                                    new Ui.Toggle(
                                            "nosclient.music.feat.moon",
                                            null,
                                            () -> c.customMoon,
                                            v -> {
                                                c.customMoon = v;
                                                SkyPreset.applyCustom();
                                            }))
                            .add(
                                    new Ui.Toggle(
                                            "nosclient.music.feat.shooting",
                                            null,
                                            () -> c.customShooting,
                                            v -> {
                                                c.customShooting = v;
                                                SkyPreset.applyCustom();
                                            }))
                            .add(
                                    new Ui.Chips(
                                            "nosclient.music.skyed.weather",
                                            null,
                                            wn,
                                            () -> Math.floorMod(c.customWeather, ws.length),
                                            ix -> {
                                                c.customWeather = ix;
                                                SkyPreset.applyCustom();
                                            })));
        }

        l.add(new Ui.Section("nosclient.music.ui.sky.tune"));
        String[] pals = new String[WorldSky.PALETTE_KEYS.length + 1];
        pals[0] = "nosclient.music.ui.palette.original";

        for (int i = 0; i < WorldSky.PALETTE_KEYS.length; i++) {
            pals[i + 1] = "nosclient.music.ui.palette." + WorldSky.PALETTE_KEYS[i];
        }

        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Chips(
                                        "nosclient.music.ui.sky.palette",
                                        null,
                                        pals,
                                        () -> c.skyPalette + 1,
                                        ix -> c.skyPalette = ix - 1))
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.ui.sky.speed",
                                        0.25,
                                        2.0,
                                        0.05,
                                        () -> c.skySpeed,
                                        v -> c.skySpeed = (float) v,
                                        Pages::mult))
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.ui.sky.lively",
                                        0.0,
                                        1.0,
                                        0.05,
                                        () -> c.skyLively,
                                        v -> c.skyLively = (float) v,
                                        Pages::pct))
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.ui.sky.pattern",
                                        0.2,
                                        1.5,
                                        0.05,
                                        () -> c.skyPattern,
                                        v -> c.skyPattern = (float) v,
                                        Pages::pct))
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.ui.sky.hour",
                                        -1,
                                        24,
                                        0.25,
                                        () -> c.visualHour,
                                        v -> c.visualHour = (float) v,
                                        v ->
                                                v < 0
                                                        ? Ui.tr(
                                                                "nosclient.music.ui.sky.server_time")
                                                        : String.format(Locale.ROOT, "%.2f h", v)))
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.ui.sky.breath",
                                        0.0,
                                        2.0,
                                        0.05,
                                        () -> c.skyBreath,
                                        v -> c.skyBreath = (float) v,
                                        Pages::mult))
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.ui.sky.star_density",
                                        0.0,
                                        2.0,
                                        0.05,
                                        () -> c.starDensity,
                                        v -> c.starDensity = (float) v,
                                        Pages::pct))
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.opt.skystrength",
                                        0.1,
                                        1.0,
                                        0.05,
                                        () -> c.skyStrength,
                                        v -> c.skyStrength = (float) v,
                                        Pages::pct))
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.opt.weatheramount",
                                        0.0,
                                        2.0,
                                        0.05,
                                        () -> c.weatherAmount,
                                        v -> c.weatherAmount = (float) v,
                                        Pages::pct)));
        l.add(new Ui.Section("nosclient.music.ui.sky.behaviour"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.realweather",
                                        "nosclient.music.ui.sky.realweather.sub",
                                        () -> c.realWeather,
                                        v -> c.realWeather = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.hideclouds",
                                        "nosclient.music.ui.sky.hideclouds.sub",
                                        () -> c.hideClouds,
                                        v -> c.hideClouds = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.extras.skyday",
                                        "nosclient.music.ui.sky.skyday.sub",
                                        () -> c.skyDay,
                                        v -> {
                                            c.skyDay = v;
                                            if (v) {
                                                c.fxDayNight = false;
                                            }
                                        }))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.extras.const",
                                        "nosclient.music.ui.sky.const.sub",
                                        () -> c.fxConstellations,
                                        v -> c.fxConstellations = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.extras.skymusic",
                                        "nosclient.music.ui.sky.music.sub",
                                        () -> c.fxSkyMusic,
                                        v -> c.fxSkyMusic = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.ui.sky.beat",
                                        null,
                                        () -> c.fxBeatPulse,
                                        v -> c.fxBeatPulse = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.ui.sky.beams",
                                        null,
                                        () -> c.fxBeams,
                                        v -> c.fxBeams = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.ui.sky.day_night",
                                        null,
                                        () -> c.fxDayNight,
                                        v -> {
                                            c.fxDayNight = v;
                                            if (v) c.skyDay = false;
                                        }))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.ui.sky.shuffle",
                                        null,
                                        () -> c.skyShuffle,
                                        v -> {
                                            c.skyShuffle = v;
                                            if (v) {
                                                SkyPreset.shuffle();
                                                screen.rebuildSoon();
                                            }
                                        }))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.ui.sky.always",
                                        null,
                                        () -> c.skyAlways,
                                        v -> c.skyAlways = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.perf.stars",
                                        null,
                                        () -> c.fxStars,
                                        v -> c.fxStars = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.perf.aurora",
                                        null,
                                        () -> c.fxAurora,
                                        v -> c.fxAurora = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.perf.weather",
                                        null,
                                        () -> c.fxWeather,
                                        v -> c.fxWeather = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.opt.lyrics.aurora",
                                        null,
                                        () ->
                                                dev.jun.nosclient.scene.SceneConfig.INSTANCE
                                                        .lyricsAurora,
                                        v ->
                                                dev.jun.nosclient.scene.SceneConfig.INSTANCE
                                                                .lyricsAurora =
                                                        v)));
    }

    private static void display(List<Ui.Widget> l) {
        EffectsConfig sc = sc();
        l.add(new Ui.Section("nosclient.music.section.top_bar"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.top_bar",
                                        "nosclient.music.opt.top_bar.desc",
                                        () -> c().showTopBar,
                                        v -> c().showTopBar = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.custom_hotbar",
                                        "nosclient.music.opt.custom_hotbar.desc",
                                        () -> c().customHotbar,
                                        v -> c().customHotbar = v))
                        .add(
                                new Ui.Choice(
                                        "nosclient.music.opt.top_bar_style",
                                        "nosclient.music.opt.top_bar_style.desc",
                                        new String[] {
                                            "nosclient.music.topbar.karaoke",
                                            "nosclient.music.topbar.simple"
                                        },
                                        () -> c().topBarStyle.ordinal(),
                                        i -> c().topBarStyle = MusicConfig.TopBarStyle.values()[i]))
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.opt.top_bar_scale",
                                        0.6,
                                        1.8,
                                        0.05,
                                        () -> c().topBarScale,
                                        v -> c().topBarScale = (float) v,
                                        Pages::mult)));
        l.add(new Ui.Section("nosclient.music.section.behaviour"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.only_playing",
                                        "nosclient.music.opt.only_playing.desc",
                                        () -> c().showOnlyWhenPlaying,
                                        v -> c().showOnlyWhenPlaying = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.hide_paused",
                                        "nosclient.music.opt.hide_paused.desc",
                                        () -> c().hideWhenStopped,
                                        v -> c().hideWhenStopped = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.dim_idle",
                                        "nosclient.music.opt.dim_idle.desc",
                                        () -> c().dimWhenIdle,
                                        v -> c().dimWhenIdle = v))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.song_chime",
                                        "nosclient.music.opt.song_chime.desc",
                                        () -> c().songChime,
                                        v -> c().songChime = v)));
    }

    private static int indexOf(List<Fonts.LyricFont> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return i;
            }
        }

        return -1;
    }

    private static void source(List<Ui.Widget> l) {
        MediaManager mm = MediaManager.get();
        l.add(new Ui.Section("nosclient.music.section.status"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Status(
                                        () -> Ui.tr(mm.statusKey()),
                                        () -> {
                                            Track t = mm.track();
                                            return t == null
                                                    ? Ui.tr("nosclient.music.status.hint")
                                                    : t.title + "  ·  " + t.artist;
                                        },
                                        () -> {
                                            Track t = mm.track();
                                            return t == null
                                                    ? -9541498
                                                    : (t.playing ? -11870592 : -11414);
                                        })));
        l.add(new Ui.Section("nosclient.music.section.connection"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Choice(
                                        "nosclient.music.opt.source",
                                        "nosclient.music.opt.source.desc",
                                        new String[] {
                                            "nosclient.music.source.spotify",
                                            "nosclient.music.source.youtube"
                                        },
                                        () -> Math.min(1, c().source.ordinal()),
                                        i -> {
                                            c().source = MusicConfig.Source.values()[i];
                                            mm.reconnect();
                                        }))
                        .add(
                                new Ui.Button(
                                        "nosclient.music.opt.reconnect",
                                        null,
                                        () -> Ui.tr("nosclient.music.button.reconnect"),
                                        "refresh",
                                        false,
                                        mm::reconnect)));
        l.add(new Ui.Info(() -> Ui.tr("nosclient.music.info.automatic")));
        l.add(new Ui.Section("nosclient.music.section.spotify_exact"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Status(
                                        () ->
                                                Ui.tr(
                                                        "nosclient.music.spotify.state."
                                                                + SpotifyWeb.state()),
                                        () -> {
                                            String e = SpotifyWeb.error();
                                            return "error".equals(SpotifyWeb.state())
                                                            && !e.isEmpty()
                                                    ? Ui.tr("nosclient.music.spotify.err." + e)
                                                    : Ui.tr(
                                                            "nosclient.music.spotify.redirect",
                                                            "http://127.0.0.1:8789/callback");
                                        },
                                        () -> {
                                            return switch (SpotifyWeb.state()) {
                                                case "on" -> -11870592;
                                                case "waiting" -> -11414;
                                                case "error" -> -495247;
                                                default -> -9541498;
                                            };
                                        }))
                        .add(
                                new Ui.TextField(
                                        "nosclient.music.opt.spotify_client",
                                        "nosclient.music.opt.spotify_client.desc",
                                        "nosclient.music.opt.spotify_client.hint",
                                        SpotifyWeb::clientId,
                                        SpotifyWeb::setClientId))
                        .add(
                                new Ui.Button(
                                        "nosclient.music.opt.spotify_login",
                                        null,
                                        () ->
                                                Ui.tr(
                                                        SpotifyWeb.connected()
                                                                ? "nosclient.music.button.disconnect"
                                                                : "nosclient.music.button.connect"),
                                        "refresh",
                                        !SpotifyWeb.connected(),
                                        () -> {
                                            if (SpotifyWeb.connected()) {
                                                SpotifyWeb.disconnect();
                                            } else {
                                                SpotifyWeb.login();
                                            }
                                        })));
        l.add(new Ui.Info(() -> Ui.tr("nosclient.music.info.spotify_exact")));
        l.add(new Ui.Section("nosclient.music.section.playback"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.opt.sync_offset",
                                        -2000.0,
                                        2000.0,
                                        50.0,
                                        () -> c().syncOffsetMs,
                                        v -> c().syncOffsetMs = (int) Math.round(v),
                                        Pages::ms))
                        .add(
                                new Ui.Button(
                                        "nosclient.music.opt.refetch",
                                        "nosclient.music.opt.refetch.desc",
                                        () -> Ui.tr("nosclient.music.button.retry"),
                                        "refresh",
                                        false,
                                        mm::refetch)));
        l.add(new Ui.Info(() -> Ui.tr("nosclient.music.info.sync_offset")));
    }

    private static void keys(List<Ui.Widget> l) {
        l.add(new Ui.Section("nosclient.music.section.keys"));
        l.add(
                new Ui.Card()
                        .add(new Ui.KeyBind("key.nosclient.music.menu", null, MediaKeys.openMenu))
                        .add(
                                new Ui.KeyBind(
                                        "key.nosclient.music.player", null, MediaKeys.openPlayer))
                        .add(
                                new Ui.KeyBind(
                                        "key.nosclient.music.onoff",
                                        "nosclient.music.opt.toggle_key.desc",
                                        MediaKeys.toggle))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.media_keys",
                                        "nosclient.music.opt.media_keys.desc",
                                        () -> c().mediaKeys,
                                        v -> c().mediaKeys = v))
                        .add(
                                new Ui.KeyBind(
                                        "key.nosclient.music.play_pause",
                                        null,
                                        MediaKeys.playPause))
                        .add(new Ui.KeyBind("key.nosclient.music.next", null, MediaKeys.next))
                        .add(
                                new Ui.KeyBind(
                                        "key.nosclient.music.previous", null, MediaKeys.previous))
                        .add(
                                new Ui.KeyBind(
                                        "key.nosclient.music.earlier",
                                        null,
                                        MediaKeys.lyricsEarlier))
                        .add(
                                new Ui.KeyBind(
                                        "key.nosclient.music.later", null, MediaKeys.lyricsLater)));
        l.add(new Ui.Info(() -> Ui.tr("nosclient.music.info.keys")));
        l.add(new Ui.Section("nosclient.music.section.sounds"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.opt.ui_sounds",
                                        "nosclient.music.opt.ui_sounds.desc",
                                        () -> c().uiSounds,
                                        v -> c().uiSounds = v)));
    }

    private static void saved(List<Ui.Widget> l, MusicPanels screen) {
        MediaManager mm = MediaManager.get();
        l.add(new Ui.Section("nosclient.music.section.timing"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Slider(
                                        "nosclient.music.opt.sync_offset",
                                        -2000.0,
                                        2000.0,
                                        50.0,
                                        () -> c().syncOffsetMs,
                                        v -> c().syncOffsetMs = (int) Math.round(v),
                                        Pages::ms))
                        .add(
                                new Ui.Button(
                                        "nosclient.music.opt.clear_song_offsets",
                                        "nosclient.music.opt.clear_song_offsets.desc",
                                        () ->
                                                Ui.tr(
                                                        "nosclient.music.button.clear_n",
                                                        c().songOffsets.size()),
                                        "trash",
                                        false,
                                        () -> {
                                            c().songOffsets.clear();
                                            MusicSettings.save();
                                            screen.rebuildSoon();
                                        }))
                        .add(
                                new Ui.Button(
                                        "nosclient.music.opt.refetch",
                                        "nosclient.music.opt.refetch.desc",
                                        () -> Ui.tr("nosclient.music.button.retry"),
                                        "refresh",
                                        false,
                                        mm::refetch))
                        .add(
                                new Ui.Button(
                                        "nosclient.music.opt.reconnect",
                                        null,
                                        () -> Ui.tr("nosclient.music.button.reconnect"),
                                        "refresh",
                                        false,
                                        mm::reconnect)));
        l.add(new Ui.Section("nosclient.music.section.profiles"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Button(
                                        "nosclient.music.opt.save_profile",
                                        "nosclient.music.opt.save_profile.desc",
                                        () -> Ui.tr("nosclient.music.button.save"),
                                        "download",
                                        true,
                                        () -> {
                                            c().profiles
                                                    .put(
                                                            freeName(
                                                                    "nosclient.music.profile.name"),
                                                            MusicSettings.snapshot());
                                            MusicSettings.save();
                                            screen.rebuildSoon();
                                        }))
                        .add(
                                new Ui.Button(
                                        "nosclient.music.opt.paste_profile",
                                        "nosclient.music.opt.paste_profile.desc",
                                        () -> Ui.tr("nosclient.music.button.paste"),
                                        "paste",
                                        false,
                                        () -> {
                                            String json =
                                                    MusicSettings.importCode(
                                                            Minecraft.getInstance()
                                                                    .keyboardHandler
                                                                    .getClipboard());
                                            if (json == null) {
                                                pasteStatus =
                                                        Ui.tr("nosclient.music.profile.paste_bad");
                                                UiSounds.play(UiSounds.TOGGLE_OFF, 0.8F);
                                            } else {
                                                String namex =
                                                        freeName("nosclient.music.profile.pasted");
                                                c().profiles.put(namex, json);
                                                MusicSettings.save();
                                                pasteStatus =
                                                        Ui.tr(
                                                                "nosclient.music.profile.paste_ok",
                                                                namex);
                                                UiSounds.play(UiSounds.TOGGLE_ON, 1.1F);
                                            }

                                            screen.rebuildSoon();
                                        })));
        if (pasteStatus != null) {
            String ps = pasteStatus;
            l.add(new Ui.Info(() -> ps));
        }

        if (c().profiles.isEmpty()) {
            l.add(new Ui.Info(() -> Ui.tr("nosclient.music.info.no_profiles")));
        } else {
            for (Entry<String, String> e : new ArrayList<>(c().profiles.entrySet())) {
                String[] name = new String[] {e.getKey()};
                String[] draft = new String[] {e.getKey()};
                Ui.Card card = new Ui.Card();
                card.add(
                        new Ui.TextField(
                                "nosclient.music.opt.profile_name",
                                null,
                                "nosclient.music.profile.name_hint",
                                () -> draft[0],
                                v -> {
                                    draft[0] = v;
                                    String nn = v.trim();
                                    if (!nn.isEmpty()
                                            && !nn.equals(name[0])
                                            && !c().profiles.containsKey(nn)) {
                                        renameProfile(name[0], nn);
                                        name[0] = nn;
                                    }
                                }));
                card.add(
                        new Ui.Button(
                                "nosclient.music.opt.load_profile",
                                null,
                                () -> Ui.tr("nosclient.music.button.load"),
                                "check",
                                true,
                                () -> {
                                    MusicSettings.applySnapshot(c().profiles.get(name[0]));
                                    WorldLyrics.clear();
                                    screen.rebuildSoon();
                                }));
                card.add(
                        new Ui.Button(
                                "nosclient.music.opt.copy_profile",
                                "nosclient.music.opt.copy_profile.desc",
                                () -> Ui.tr("nosclient.music.button.copy"),
                                "copy",
                                false,
                                () -> {
                                    String json = c().profiles.get(name[0]);
                                    if (json != null) {
                                        Minecraft.getInstance()
                                                .keyboardHandler
                                                .setClipboard(MusicSettings.exportCode(json));
                                        pasteStatus =
                                                Ui.tr("nosclient.music.profile.copied", name[0]);
                                        screen.rebuildSoon();
                                    }
                                }));
                card.add(
                        new Ui.Button(
                                "nosclient.music.opt.delete_profile",
                                null,
                                () -> Ui.tr("nosclient.music.button.delete"),
                                "trash",
                                false,
                                () -> {
                                    c().profiles.remove(name[0]);
                                    MusicSettings.save();
                                    screen.rebuildSoon();
                                }));
                l.add(card);
                l.add(new Ui.Spacer(4.0F));
            }
        }

        l.add(new Ui.Section("nosclient.music.section.reset"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Button(
                                        "nosclient.music.opt.reset_all",
                                        "nosclient.music.opt.reset_all.desc",
                                        () -> Ui.tr("nosclient.music.button.reset"),
                                        "refresh",
                                        false,
                                        () -> {
                                            MusicSettings.reset();
                                            ClientEffects.resetConfig();
                                            SkyImage.reload();
                                            AutoPerf.reset();
                                            WorldLyrics.clear();
                                            Fonts.clearCaches();
                                            dev.jun.nosclient.gui.effects.Gfx.clearWidths();
                                            note("nosclient.music.opt.reset_all.done");
                                            screen.rebuildSoon();
                                        })));
    }

    private static String freeName(String key) {
        int n = c().profiles.size() + 1;
        String name = Ui.tr(key, n);

        while (c().profiles.containsKey(name)) {
            name = Ui.tr(key, ++n);
        }

        return name;
    }

    private static void renameProfile(String from, String to) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();

        for (Entry<String, String> e : c().profiles.entrySet()) {
            m.put(e.getKey().equals(from) ? to : e.getKey(), e.getValue());
        }

        c().profiles.clear();
        c().profiles.putAll(m);
        MusicSettings.save();
    }

    private static void perf(List<Ui.Widget> l, MusicPanels screen) {
        l.add(new Ui.Section("nosclient.music.section.perf_now"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Custom(
                                        w -> 50.0F,
                                        (g, x, y, w, h, mx, my, a) -> {
                                            String num = Integer.toString(AutoPerf.fps());
                                            Gfx.text(
                                                    g,
                                                    num,
                                                    Ui.FB,
                                                    x + 4.0F,
                                                    y + 9.0F,
                                                    22.0F,
                                                    Col.alpha(-856070, a));
                                            Gfx.text(
                                                    g,
                                                    "FPS",
                                                    Ui.F,
                                                    x
                                                            + 4.0F
                                                            + Gfx.textWidth(num, Ui.FB, 22.0F)
                                                            + 6.0F,
                                                    y + 24.0F,
                                                    8.0F,
                                                    Col.alpha(-6515020, a));
                                            int lv = AutoPerf.level();
                                            String st =
                                                    !c().autoPerf
                                                            ? Ui.tr("nosclient.music.perf.auto_off")
                                                            : (lv == 0
                                                                    ? Ui.tr(
                                                                            "nosclient.music.perf.auto_full")
                                                                    : Ui.tr(
                                                                            "nosclient.music.perf.auto_level",
                                                                            lv,
                                                                            6));
                                            String cost =
                                                    Ui.tr(
                                                            "nosclient.music.perf.cost",
                                                            String.format(
                                                                    Locale.ROOT,
                                                                    "%.1f",
                                                                    AutoPerf.modMs()));
                                            Gfx.text(
                                                    g,
                                                    cost,
                                                    Ui.F,
                                                    x + w - 4.0F - Gfx.textWidth(cost, Ui.F, 7.2F),
                                                    y + 15.0F,
                                                    7.2F,
                                                    Col.alpha(-6515020, a));
                                            Gfx.text(
                                                    g,
                                                    st,
                                                    Ui.F,
                                                    x + w - 4.0F - Gfx.textWidth(st, Ui.F, 7.2F),
                                                    y + 27.0F,
                                                    7.2F,
                                                    Col.alpha(-6515020, a));
                                        },
                                        null)));
        l.add(new Ui.Section("nosclient.music.section.perf_auto"));
        String[] targets = new String[TARGETS.length];

        for (int i = 0; i < TARGETS.length; i++) {
            targets[i] = TARGETS[i] == 0 ? "nosclient.music.perf.target_auto" : TARGETS[i] + " FPS";
        }

        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.perf.auto",
                                        "nosclient.music.perf.auto.desc",
                                        () -> c().autoPerf,
                                        v -> {
                                            c().autoPerf = v;
                                            AutoPerf.reset();
                                        }))
                        .add(
                                new Ui.Choice(
                                        "nosclient.music.perf.target",
                                        "nosclient.music.perf.target.desc",
                                        targets,
                                        () -> {
                                            for (int i = 0; i < TARGETS.length; i++) {
                                                if (TARGETS[i] == c().targetFps) {
                                                    return i;
                                                }
                                            }

                                            return 1;
                                        },
                                        i -> {
                                            c().targetFps = TARGETS[i];
                                            AutoPerf.reset();
                                        }))
                        .add(
                                new Ui.Choice(
                                        "nosclient.music.perf.quality",
                                        null,
                                        new String[] {
                                            "nosclient.music.perf.quality.0",
                                            "nosclient.music.perf.quality.1",
                                            "nosclient.music.perf.quality.2"
                                        },
                                        () -> c().quality,
                                        i -> {
                                            c().quality = i;
                                        })));
        l.add(new Ui.Section("nosclient.music.section.perf_effects"));
        l.add(
                new Ui.Card()
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.perf.sky_color",
                                        null,
                                        () -> sc().fxSkyColor,
                                        v -> {
                                            sc().fxSkyColor = v;
                                        }))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.perf.weather",
                                        null,
                                        () -> sc().fxWeather,
                                        v -> {
                                            sc().fxWeather = v;
                                        }))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.perf.aurora",
                                        null,
                                        () -> sc().fxAurora,
                                        v -> {
                                            sc().fxAurora = v;
                                        }))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.perf.stars",
                                        null,
                                        () -> sc().fxStars,
                                        v -> {
                                            sc().fxStars = v;
                                        }))
                        .add(
                                new Ui.Toggle(
                                        "nosclient.music.perf.glow",
                                        "nosclient.music.perf.glow.desc",
                                        () -> c().glow,
                                        v -> c().glow = v)));
    }

    private static void note(String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(Component.literal(Ui.tr(key)), false);
        }
    }
}
