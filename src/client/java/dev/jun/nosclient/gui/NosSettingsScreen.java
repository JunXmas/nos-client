package dev.jun.nosclient.gui;

import dev.jun.nosclient.Mods;
import dev.jun.nosclient.Ui;
import dev.jun.nosclient.feat.Waypoints;
import dev.jun.nosclient.hud.HudSurface;
import dev.jun.nosclient.media.LyricsService;
import dev.jun.nosclient.media.SkyLyrics;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.theme.Theme;
import java.nio.file.Path;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

@Environment(EnvType.CLIENT)
public final class NosSettingsScreen extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("nosclient", "textures/gui/logo.png");
    private static final String[] CATS = Mods.CATS;
    private static final String[] CAT_ICON = new String[]{"hud", "inv", "world", "pvp", "sys"};
    private static final int GAP = 8;
    private final Screen parent;
    private final MenuMotion motion = new MenuMotion();
    private final HubMotion hubMotion = new HubMotion();
    private final HubCarousel carousel = new HubCarousel();
    private float paneOpen = 1.0F;
    private UiTransform rootTransform = UiTransform.IDENTITY;
    private UiTransform paneTransform = UiTransform.IDENTITY;
    private UiTransform inputTransform = UiTransform.IDENTITY;
    private GuiGraphics drawing;
    private int pointerX;
    private int pointerY;
    private final SceneConfig c = SceneConfig.INSTANCE;
    private final Minecraft mc = Minecraft.getInstance();
    private final long opened = System.nanoTime();
    private long paneAt = this.opened;
    private long last = this.opened;
    private float dt;
    private float t;
    private int mx;
    private int my;
    private int cat = 0;
    private int tab;
    private String detail;
    private String query = "";
    private String cachedQuery;
    private String cachedLanguage;
    private List<Mods.Mod> cachedResults = List.of();
    private boolean searchFocus;
    private Component note;
    private float scroll;
    private float dScroll;
    private double scrollTarget;
    private double dTarget;
    private int maxScroll;
    private int dMax;
    private final Map<String, Float> anim = new HashMap<>();
    private final List<NosSettingsScreen.Hit> hits = new ArrayList<>();
    private int[] clip;
    private Opt drag;
    private int dragX0;
    private int dragX1;
    private int dragLook = -1;

    public NosSettingsScreen(Screen parent) {
        super(Component.translatable("nosclient.settings.title"));
        this.parent = parent;
    }

    public NosSettingsScreen(Screen parent, boolean appearance) {
        this(parent);
        this.tab = appearance ? 1 : 0;
    }

    @Override
    protected void init() {
        this.motion.enter();
        this.carousel.snap(this.cat);
        this.hits.clear();
        this.clip = null;
        this.drag = null;
        this.dragLook = -1;
        this.setDragging(false);
    }

    private int pw() {
        return Math.min(this.width - 32, 296);
    }

    private int ph() {
        return Math.max(68, Math.min(this.height - 104, 282));
    }

    private int px() {
        return (this.width - this.pw()) / 2;
    }

    private int py() {
        return (this.height - this.ph()) / 2;
    }

    private int searchY() {
        return this.py() + (this.ph() < 150 ? 25 : 38);
    }

    private int side() {
        return 0;
    }

    private int dw() {
        return this.pw();
    }

    private void setQuery(String q) {
        this.query = q.codePointCount(0, q.length()) > 40 ? q.substring(0, q.offsetByCodePoints(0, 40)) : q;
        this.scroll = 0.0F;
        this.scrollTarget = 0.0;
    }

    private boolean hov(int x0, int y0, int x1, int y1) {
        return this.mx >= x0
            && this.mx < x1
            && this.my >= y0
            && this.my < y1
            && (this.clip == null || this.mx >= this.clip[0] && this.mx < this.clip[2] && this.my >= this.clip[1] && this.my < this.clip[3]);
    }

    private void hit(int x0, int y0, int x1, int y1, Runnable r) {
        if (!(UiOpacity.value < 0.2F)) {
            if (this.clip != null) {
                x0 = Math.max(x0, this.clip[0]);
                y0 = Math.max(y0, this.clip[1]);
                x1 = Math.min(x1, this.clip[2]);
                y1 = Math.min(y1, this.clip[3]);
            }

            if (x0 < x1 && y0 < y1) {
                this.hits.add(new NosSettingsScreen.Hit(x0, y0, x1, y1, this.inputTransform, this.drawing.scissorStack.peek(), r));
            }
        }
    }

    private void input(UiTransform transform) {
        this.inputTransform = transform;
        this.mx = (int)transform.localX(this.pointerX);
        this.my = (int)transform.localY(this.pointerY);
    }

    private static void fill(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, UiOpacity.tint(color));
    }

    private void clipOn(GuiGraphics g, int x0, int y0, int x1, int y1) {
        this.clip = new int[]{x0, y0, x1, y1};
        g.enableScissor(x0, y0, x1, y1);
    }

    private void clipOff(GuiGraphics g) {
        this.clip = null;
        g.disableScissor();
    }

    private float k(String key, boolean on) {
        float v = this.c.animations ? Ui.approach(this.anim.getOrDefault(key, on ? 1.0F : 0.0F), on ? 1.0F : 0.0F, this.dt, 14.0F) : (on ? 1.0F : 0.0F);
        this.anim.put(key, v);
        return Ui.ease(v);
    }

    private void click() {
        this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static Component tr(String key) {
        return Component.translatable(key);
    }

    private static Component name(Mods.Mod m) {
        return tr("nosclient.mod." + m.id());
    }

    private static boolean on(Mods.Mod m) {
        return m.get().get();
    }

    private void flip(Mods.Mod m) {
        boolean v = !on(m);
        m.set().accept(v);
        this.c.save();
    }

    private int lines(GuiGraphics g, Component text, int x, int y, int w, int col) {
        List<FormattedCharSequence> ls = this.font.split(text, w);
        col = UiOpacity.tint(col);
        if (col >>> 24 > 3) {
            for (int i = 0; i < ls.size(); i++) {
                g.drawString(this.font, ls.get(i), x, y + i * 10, col, false);
            }
        }

        return ls.size() * 10;
    }

    private int button(GuiGraphics g, Component label, int x, int y, int h, boolean active, Runnable r) {
        int w = Ui.width(label) + 16;
        boolean hv = active && this.hov(x, y, x + w, y + h);
        Glass.panel(g, x, y, w, h, !active ? 184549375 : (hv ? -13553089 : -568911581), hv ? -1493172225 : 1090519039);
        Ui.text(g, label, x + 8, y + (h - 7) / 2, active ? Ui.TEXT : Ui.FAINT);
        if (active) {
            this.hit(x, y, x + w, y + h, r);
        }

        return w;
    }

    static String fold(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd');
    }

    private boolean matches(Mods.Mod m, String q) {
        StringBuilder b = new StringBuilder(I18n.get("nosclient.mod." + m.id())).append(' ').append(I18n.get("nosclient.mod." + m.id() + ".desc"));

        for (Opt o : Opt.BY_MOD.getOrDefault(m.id(), List.of())) {
            b.append(' ').append(I18n.get("nosclient." + o.label()));
        }

        return fold(b.toString()).contains(q);
    }

    private List<Mods.Mod> results() {
        if (this.query.equals(this.cachedQuery) && this.mc.options.languageCode.equals(this.cachedLanguage)) {
            return this.cachedResults;
        }

        String q = fold(this.query.trim());
        this.cachedQuery = this.query;
        this.cachedLanguage = this.mc.options.languageCode;
        return this.cachedResults = Mods.ALL.stream().filter(m -> this.matches(m, q)).toList();
    }

    private boolean searching() {
        return !this.query.isBlank();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        if (this.mc.level == null) {
            if (this.c.menuAurora) {
                AuroraBackground.render(g, this.width, this.height);
            } else {
                this.renderPanorama(g, pt);
            }

            this.renderBlurredBackground(g);
            g.fill(0, 0, this.width, this.height, 604507411);
            // Tối dần về mép trên/dưới để mắt dồn vào khung giữa.
            g.fillGradient(0, 0, this.width, this.height / 3, 0x70000000, 0);
            g.fillGradient(0, this.height * 2 / 3, this.width, this.height, 0, 0x80000000);
        } else {
            super.renderBackground(g, mouseX, mouseY, pt);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        long now = System.nanoTime();
        this.dt = Math.min(0.05F, (float)(now - this.last) / 1.0E9F);
        this.t = (float)(now - this.opened) / 1.0E9F;
        this.last = now;
        this.pointerX = mouseX;
        this.pointerY = mouseY;
        this.drawing = g;
        this.hits.clear();
        float opacity = this.motion.opacity();
        float previousOpacity = UiOpacity.value;
        float previousHint = Ui.hint;
        this.rootTransform = UiTransform.opening(opacity, this.width / 2.0F, this.height / 2.0F);
        g.pose().pushMatrix();
        g.pose().translate(this.rootTransform.x(), this.rootTransform.y());
        g.pose().scale(this.rootTransform.scale(), this.rootTransform.scale());
        UiOpacity.value = opacity;
        Ui.hint = this.rootTransform.scale();
        this.input(this.rootTransform);

        try {
            this.draw(g);
        } finally {
            g.pose().popMatrix();
            UiOpacity.value = previousOpacity;
            Ui.hint = previousHint;
            this.drawing = null;
        }
    }

    @Override
    public void tick() {
        this.motion.tick();
    }

    private void draw(GuiGraphics g) {
        int x = this.px();
        int y = this.py();
        int w = this.pw();
        int h = this.ph();
        this.button(
            g,
            Ui.small(Component.literal(this.mc.options.languageCode.equals("vi_vn") ? "Tiếng Việt" : "English")),
            12,
            12,
            18,
            true,
            () -> this.setLang(this.mc.options.languageCode.equals("vi_vn") ? "en_us" : "vi_vn")
        );
        Component resetLabel = Ui.small(tr("nosclient.settings.reset"));
        this.button(g, resetLabel, this.width - Ui.width(resetLabel) - 28, 12, 18, true, this::askReset);
        Component brand = Ui.big(Component.literal("Nos Client"));
        int brandY = y - (this.width >= 600 ? 28 : 45);
        Ui.text(g, brand, (this.width - Ui.width(brand)) / 2, brandY, Ui.TEXT);
        int line = Math.round(Ui.width(brand) * (0.35F + 0.1F * (float)Math.sin(this.t * 1.4F)));
        Glass.glow(g, (this.width - line) / 2, brandY + 20, line, 1, Ui.alpha(Theme.palette().accent() | 0xFF000000, 0.45F));
        Glass.fill(g, (this.width - line) / 2, brandY + 20, line, 1, Ui.alpha(Theme.palette().accent() | 0xFF000000, 0.9F));
        float opacity = UiOpacity.value;
        HubCarousel.Frame orbit = this.carousel.frame(System.nanoTime(), this.c.animations);
        this.paneOpen = orbit.open();
        this.input(this.rootTransform);
        this.categories(g, orbit, x, y, w, h);
        HubMotion.Frame frame = this.hubMotion.frame(System.nanoTime(), this.c.animations);
        UiOpacity.value = opacity * frame.opacity() * this.paneOpen;
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        g.pose().pushMatrix();
        float unfold = 0.94F + 0.06F * this.paneOpen;
        float shiftX = (x + w / 2.0F) * (1.0F - unfold) + frame.x();
        float shiftY = (y + h / 2.0F) * (1.0F - unfold) + frame.y() + 8.0F * (1.0F - this.paneOpen);
        g.pose().translate(shiftX, shiftY);
        g.pose().scale(unfold, unfold);
        this.paneTransform = this.rootTransform.translated(shiftX, shiftY);
        this.paneTransform = new UiTransform(this.paneTransform.scale() * unfold, this.paneTransform.x(), this.paneTransform.y());
        this.input(this.paneTransform);
        if (this.detail != null) {
            Mods.ALL.stream().filter(m -> m.id().equals(this.detail)).findFirst().ifPresent(m -> this.detailPanel(g, m, x + 1, y + 1, x + w - 1, y + h - 1));
        } else {
            int ci = Math.max(0, this.cat);
            boolean compact = h < 150;
            if (!compact) {
                // Ô biểu tượng có nền accent mờ + quầng: tiêu đề có điểm nhấn thay vì icon trôi lơ lửng.
                int accent = Theme.palette().accent() | 0xFF000000;
                Glass.glow(g, x + 9, y + 6, 28, 28, Ui.alpha(accent, 0.25F));
                Glass.fill(g, x + 9, y + 6, 28, 28, Ui.alpha(accent, 0.16F));
                Glass.outline(g, x + 9, y + 6, 28, 28, Ui.alpha(accent, 0.45F));
                Icons.draw(g, this.tab == 1 ? "look" : CAT_ICON[ci], x + 10, y + 7, 26.0F);
            }

            Component title = Ui.bold(tr(this.tab == 1 ? "nosclient.look.title" : "nosclient.cat." + CATS[ci]));
            Ui.text(g, title, compact ? x + (w - Ui.width(title)) / 2 : x + 43, y + (compact ? 10 : 16), Ui.TEXT);
            if (!compact && this.tab == 0 && !this.searching()) {
                // Chip đếm số tính năng đang bật trong nhóm.
                List<Mods.Mod> mods = Mods.of(CATS[ci]);
                long active = mods.stream().filter(NosSettingsScreen::on).count();
                Component chip = Ui.small(Component.literal(active + " / " + mods.size()));
                int cw = Ui.width(chip) + 12;
                int chipX = x + w - 14 - cw;
                Glass.fill(g, chipX, y + 13, cw, 13, Ui.alpha(Theme.palette().accent() | 0xFF000000, 0.18F));
                Glass.outline(g, chipX, y + 13, cw, 13, Ui.alpha(Theme.palette().accent() | 0xFF000000, 0.5F));
                Ui.text(g, chip, chipX + 6, y + 16, Ui.TEXT);
            }
            this.scroll = this.c.animations ? Ui.approach(this.scroll, (float)this.scrollTarget, this.dt, 16.0F) : (float)this.scrollTarget;
            if (this.tab == 1 && !this.searching()) {
                this.clipOn(g, x + 10, y + (compact ? 25 : 46), x + w - 10, y + h - 10);
                int bottom = this.look(g, x + 14, x + w - 14, y + (compact ? 27 : 48) - (int)this.scroll);
                this.maxScroll = Math.max(0, bottom + (int)this.scroll - (y + h - 10));
                this.clipOff(g);
            } else {
                this.search(g, x + 12, this.searchY(), w - 24);
                List<Mods.Mod> list = this.searching() ? this.results() : Mods.of(CATS[ci]);
                int top = y + (compact ? 48 : 63);
                int bottom = y + h - 8;
                this.clipOn(g, x + 10, top, x + w - 10, bottom);

                float since = (System.nanoTime() - this.paneAt) / 1.0E9F;
                float paneOpacity = UiOpacity.value;

                for (int i = 0; i < list.size(); i++) {
                    int ry = top + i * 21 - (int)this.scroll;
                    if (ry + 19 >= top && ry < bottom) {
                        // Hàng vào so le từ trên xuống, trượt nhẹ theo hướng chuyển danh mục.
                        float e = this.c.animations ? Ui.ease(Math.clamp((since - 0.05F - Math.max(0, (ry - top) / 21) * 0.028F) / 0.26F, 0.0F, 1.0F)) : 1.0F;
                        UiOpacity.value = paneOpacity * e;
                        this.row(g, list.get(i), x + 12 + Math.round(12.0F * (1.0F - e) * Math.signum(this.hubMotion.direction())), ry, w - 24);
                    }
                }

                UiOpacity.value = paneOpacity;

                if (list.isEmpty()) {
                    Ui.text(g, Ui.ui(tr("nosclient.settings.none")), x + 16, top + 7, Ui.MUTED);
                }

                this.clipOff(g);
                this.maxScroll = Math.max(0, list.size() * 21 - 2 - (bottom - top));
            }

            this.scrollTarget = Math.max(0.0, Math.min(this.scrollTarget, this.maxScroll));
            if (this.maxScroll > 0) {
                int top = y + (this.tab == 1 ? (compact ? 27 : 48) : (compact ? 48 : 63));
                int bottom = y + h - 8;
                int track = bottom - top;
                int len = Math.max(12, track * track / (track + this.maxScroll));
                int pos = top + Math.round((track - len) * Math.max(0.0F, Math.min(1.0F, this.scroll / this.maxScroll)));
                Glass.fill(g, x + w - 7, pos, 2, len, -1962934273);
            }
        }

        g.pose().popMatrix();
        g.disableScissor();
        this.clip = null;
        UiOpacity.value = opacity;
        this.input(this.rootTransform);
        this.dock(g, x, y + h + 8, w);
    }

    private void categories(GuiGraphics g, HubCarousel.Frame orbit, int x, int y, int w, int h) {
        float opacity = UiOpacity.value;
        ArrayList<Integer> order = new ArrayList<>();

        for (int i = 0; i < CATS.length; i++) {
            order.add(i);
        }

        order.sort(Comparator.<Integer>comparingDouble(ix -> Math.abs(HubCarousel.relative(ix.intValue() - orbit.cursor()))).reversed());

        for (int index : order) {
            HubCarousel.Card card = HubCarousel.card(orbit, index, this.width, x, y, w, h);
            if (!(card.opacity() < 0.01F)) {
                int cx = Math.round(card.x());
                int cy = Math.round(card.y());
                int cw = Math.round(card.width());
                int ch = Math.round(card.height());
                UiOpacity.value = opacity * card.opacity();
                boolean selectable = index != orbit.selected();
                boolean hover = selectable && this.hov(cx, cy, cx + cw, cy + ch);
                float glow = this.k("hover.cat." + index, hover);
                if (card.open() < 0.05F) {
                    // Thẻ chờ lơ lửng lệch pha nhau; trỏ vào thì nhấc lên và nở ra.
                    float bob = this.c.animations ? (float)Math.sin(this.t * 1.6F + index * 1.3F) * 1.5F * (1.0F - glow) : 0.0F;
                    int grow = Math.round(3.0F * glow);
                    cx -= grow;
                    cw += grow * 2;
                    cy += Math.round(bob - 4.0F * glow) - grow;
                    ch += grow * 2;
                }
                if (glow > 0.01F) {
                    Glass.glow(g, cx, cy, cw, ch, Ui.alpha(Theme.palette().accent() | 0xFF000000, 0.4F * glow));
                }

                Glass.panel(g, cx, cy, cw, ch, Ui.lerpCol(-653124319, -315603652, glow), Ui.lerpCol(1539167944, -858134051, glow));
                float labelOpacity = 1.0F - (float)Math.clamp(card.open() / 0.6, 0.0, 1.0);
                UiOpacity.value *= labelOpacity;
                if (ch >= 45) {
                    float icon = Math.min(38, Math.min(cw - 12, ch - 30));
                    Icons.draw(g, CAT_ICON[index], cx + (cw - icon) / 2.0F, cy + 7, icon);
                }

                Component label = Ui.fit(Ui.small(tr("nosclient.cat." + CATS[index])), cw - 8);
                Ui.text(g, label, cx + (cw - Ui.width(label)) / 2, cy + (ch >= 45 ? 47 : (ch - 7) / 2), Ui.TEXT);
                UiOpacity.value = opacity * card.opacity();
                if (selectable) {
                    this.hit(cx, cy, cx + cw, cy + ch, () -> this.openCat(index));
                } else {
                    this.hit(cx, cy, cx + cw, cy + ch, () -> {});
                }
            }
        }

        UiOpacity.value = opacity;
    }

    private void search(GuiGraphics g, int x, int y, int w) {
        Glass.panel(g, x, y, w, 18, -14931413, this.searchFocus ? -1493172225 : 687865855);
        Ui.frame(g, x + 6, y + 6, x + 10, y + 10, Ui.MUTED);
        fill(g, x + 10, y + 10, x + 12, y + 12, Ui.MUTED);
        this.hit(x, y, x + w, y + 18, () -> this.searchFocus = true);
        String shown = this.query;

        while (!shown.isEmpty() && Ui.width(Ui.ui(shown)) > w - 30) {
            shown = shown.substring(shown.offsetByCodePoints(0, 1));
        }

        Component text = shown.isEmpty() ? Ui.small(tr("nosclient.settings.search")) : Ui.ui(shown);
        Ui.text(g, text, x + 17, y + 6, shown.isEmpty() ? Ui.MUTED : Ui.TEXT);
        if (this.searchFocus && this.t % 1.0F < 0.6F) {
            fill(g, x + 17 + (shown.isEmpty() ? 0 : Ui.width(text)), y + 4, x + 18 + (shown.isEmpty() ? 0 : Ui.width(text)), y + 13, Ui.TEXT);
        }
    }

    private void row(GuiGraphics g, Mods.Mod m, int x, int y, int w) {
        boolean hover = this.hov(x, y, x + w, y + 19);
        float glow = this.k("hover.row." + m.id(), hover);
        Glass.panel(g, x, y, w, 19, Ui.lerpCol(-585488345, -14075337, glow), Ui.lerpCol(385875967, 1509949439, glow));
        if (glow > 0.01F) {
            int bar = Math.round(11.0F * glow);
            Glass.fill(g, x + 1, y + 9 - bar / 2, 2, bar, Ui.alpha(Theme.palette().accent() | 0xFF000000, glow));
        }
        if (on(m)) {
            Glass.fill(g, x + 3, y + 1, 18, 17, Ui.alpha(Theme.palette().accent() | 0xFF000000, 0.12F + 0.1F * glow));
        }

        Icons.draw(g, m.id(), x + 3, y, 18.0F);
        Ui.text(g, Ui.fit(Ui.ui(name(m)), w - 64), x + 27, y + 6, Ui.TEXT);
        Ui.toggle(g, x + w - 30, y + 3, this.k(Opt.field(m), on(m)));
        this.hit(x, y, x + w - 34, y + 19, () -> this.openDetail(m.id()));
        this.hit(x + w - 34, y, x + w, y + 19, () -> this.flip(m));
    }

    private void dock(GuiGraphics g, int x, int y, int w) {
        String[] keys = new String[]{"nosclient.dock.search", "nosclient.dock.look", "nosclient.dock.edit", "nosclient.cosmetic.title", "gui.done"};
        String[] icons = new String[]{"search", "look", "edit", "armor", "back"};
        Runnable[] actions = new Runnable[]{
            this::openSearch,
            this::toggleAppearance,
            () -> this.motion.exit(() -> this.mc.setScreen(new HudEditScreen(this))),
            () -> this.motion.exit(() -> this.mc.setScreen(new CosmeticScreen(this))),
            this::onClose
        };
        int bw = (w - 24) / 5;

        for (int i = 0; i < 5; i++) {
            int cx = x + i * (bw + 6);
            boolean enabled = i != 2 || this.mc.player != null;
            boolean hover = enabled && this.hov(cx, y, cx + bw, y + 27);
            float glow = this.k("hover.dock." + i, hover);
            if (glow > 0.01F) {
                Glass.glow(g, cx, y, bw, 27, Ui.alpha(Theme.palette().accent() | 0xFF000000, 0.35F * glow));
            }

            Glass.panel(g, cx, y, bw, 27, Ui.lerpCol(-569106654, -265272260, glow), Ui.lerpCol(1303692988, -1277564451, glow));
            Icons.draw(g, icons[i], cx + bw / 2.0F - 9.0F, y, 18.0F);
            Component label = Ui.fit(Ui.small(tr(keys[i])), bw - 6);
            Ui.text(g, label, cx + (bw - Ui.width(label)) / 2, y + 18, enabled ? Ui.TEXT : Ui.FAINT);
            if (!enabled && this.hov(cx, y, cx + bw, y + 27)) {
                Component why = Ui.fit(Ui.small(tr("nosclient.edit.noworld")), w);
                Ui.text(g, why, x + (w - Ui.width(why)) / 2, y + 32, Ui.MUTED);
            }

            if (enabled) {
                this.hit(cx, y, cx + bw, y + 27, actions[i]);
            }
        }
    }

    private void detailPanel(GuiGraphics g, Mods.Mod m, int x0, int y0, int x1, int y1) {
        int accent = Mods.catColor(m.cat());
        Glass.fill(g, x0, y0, x1 - x0, y1 - y0, Theme.palette().surface());
        this.dScroll = this.c.animations ? Ui.approach(this.dScroll, (float)this.dTarget, this.dt, 16.0F) : (float)this.dTarget;
        this.clipOn(g, x0 + 1, y0, x1, y1);
        int px = x0 + 12;
        int pw = x1 - x0 - 24;
        int y = y0 + 12 - (int)this.dScroll;
        fill(g, px, y, px + 26, y + 26, 536870911);
        Icons.draw(g, m.id(), px - 2, y - 2, 30.0F);
        Ui.text(g, Ui.fit(Ui.bold(name(m)), pw - 34 - 14), px + 34, y + 3, Ui.TEXT);
        y += 36;
        boolean on = on(m);
        fill(g, px, y, px + pw, y + 24, 268435455);
        Ui.frame(g, px, y, px + pw, y + 24, 352321535);
        Ui.text(g, Ui.bold(tr(on ? "nosclient.detail.on" : "nosclient.detail.off")), px + 8, y + 6, Ui.TEXT);
        Ui.toggle(g, px + pw - 32, y + 6, this.k(Opt.field(m), on), accent);
        this.hit(px, y, px + pw, y + 24, () -> this.flip(m));
        y += 32;
        if (m.id().equals("lyrics")) {
            this.button(g, Ui.small(tr("nosclient.lyrics.place")), px, y, 18, this.mc.level != null, () -> {
                Camera camera = this.mc.gameRenderer.getMainCamera();
                this.c.lyricsBearing = Math.floorMod(Math.round(camera.yRot()), 360);
                this.c.lyricsElevation = Math.clamp(Math.round(-camera.xRot()), 15, 80);
                this.c.lyricsFollow = false;
                this.c.save();
            });
            y += 24;
        }

        List<Opt> opts = Opt.BY_MOD.getOrDefault(m.id(), List.of());
        boolean extra = m.id().equals("waypoint") || m.id().equals("mp") || m.id().equals("lyrics") || m.id().equals("sky") || m.id().equals("weather");
        if (!opts.isEmpty() || extra) {
            Ui.text(g, Ui.small(tr("nosclient.detail.opts")), px + 2, y, Ui.FAINT);
            y += 12;
            int top = y;

            for (int i = 0; i < opts.size(); i++) {
                if (i > 0) {
                    fill(g, px + 8, y, px + pw - 8, y + 1, 268435455);
                }

                y = this.option(g, opts.get(i), px, y, pw, accent);
            }

            if (!opts.isEmpty()) {
                Ui.frame(g, px, top, px + pw, y, 352321535);
            }

            y = this.extras(g, m, px, y + 6, pw);
        }

        this.clipOff(g);
        this.dMax = Math.max(0, y + (int)this.dScroll - y1);
        this.dTarget = Math.min(this.dTarget, this.dMax);
        boolean hv = this.hov(x1 - 18, y0 + 4, x1 - 4, y0 + 18);
        Ui.text(g, Ui.ui(Component.literal("×")), x1 - 14, y0 + 7, hv ? Ui.TEXT : Ui.MUTED);
        this.hit(x1 - 20, y0 + 2, x1 - 2, y0 + 20, this::closeDetail);
    }

    private Component optLabel(String key) {
        return key.startsWith("opt.") ? tr("nosclient." + key) : Component.literal(key);
    }

    private int option(GuiGraphics g, Opt o, int x, int y, int w, int accent) {
        Component label = Ui.ui(this.optLabel(o.label()));
        switch (o.type()) {
            case "chk": {
                boolean v = o.bool();
                Ui.text(g, Ui.fit(label, w - 48), x + 8, y + 7, Ui.TEXT);
                Ui.toggle(g, x + w - 32, y + 5, this.k(o.field(), v), accent);
                this.hit(x, y, x + w, y + 22, () -> {
                    o.set(v ? 0.0 : 1.0);
                    this.c.save();
                });
                return y + 22;
            }
            case "seg":
                int sel = o.segIndex();
                int segW = 0;
                Component[] ls = new Component[o.choices().length];

                for (int i = 0; i < ls.length; i++) {
                    segW += Ui.width(ls[i] = Ui.small(this.optLabel(o.choices()[i]))) + 12;
                }

                boolean wrap = Ui.width(label) + 12 + segW > w - 16;
                Ui.text(g, Ui.fit(label, w - 16), x + 8, y + 7, Ui.TEXT);
                int sx = x + w - 8 - segW;
                int sy = wrap ? y + 19 : y + 4;
                fill(g, sx, sy, sx + segW, sy + 14, 352321535);

                for (int i = 0; i < ls.length; i++) {
                    int bw = Ui.width(ls[i]) + 12;
                    if (i == sel) {
                        fill(g, sx + 1, sy + 1, sx + bw - 1, sy + 13, Ui.alpha(accent, 0.4F));
                    }

                    Ui.text(g, ls[i], sx + 6, sy + 4, i == sel ? -722945 : Ui.MUTED);
                    int ci = i;
                    this.hit(sx, sy, sx + bw, sy + 14, () -> {
                        o.setSeg(ci);
                        this.c.save();
                    });
                    sx += bw;
                }

                return y + (wrap ? 38 : 22);
            case "rng": {
                double v = o.num();
                String shown = o.field().equals("gamma")
                    ? (v < 0.0 ? I18n.get("nosclient.gamma.game") : Math.round(v * 100.0) + "%" + (v >= 10.0 ? " · Full bright" : ""))
                    : (
                        o.field().equals("lyricsWidth")
                            ? Math.round(v * 100.0) + "%"
                            : (
                                o.field().equals("lyricsTracking")
                                    ? String.format(Locale.ROOT, "%.2f", v)
                                    : (
                                        o.field().equals("skyHour")
                                            ? String.format(Locale.ROOT, "%02d:%02d", (int)v, (int)Math.round(v % 1.0 * 60.0))
                                            : (
                                                o.field().equals("skyStars")
                                                    ? Math.round(v * 100.0) + "%"
                                                    : (
                                                        !o.field().equals("lyricsSize") && !o.field().equals("lyricsHeight") && !o.field().equals("lyricsGlow")
                                                            ? (int)Math.round(v) + o.unit()
                                                            : String.format(Locale.ROOT, "%.2f×", v)
                                                    )
                                            )
                                    )
                            )
                    );
                Component val = Ui.bold(Component.literal(shown));
                Ui.text(g, Ui.fit(label, w - 24 - Ui.width(val)), x + 8, y + 7, Ui.TEXT);
                Ui.text(g, val, x + w - 8 - Ui.width(val), y + 7, Ui.lerpCol(accent, -1, 0.35F));
                int tx0 = x + 10;
                int tx1 = x + w - 10;
                int ty = y + 24;
                double f = v < 0.0 ? (0.5 - o.min()) / (o.max() - o.min()) : (v - o.min()) / (o.max() - o.min());
                int kx = (int)Math.round(tx0 + Math.min(1.0, Math.max(0.0, f)) * (tx1 - tx0));
                fill(g, tx0, ty, tx1, ty + 2, 620756991);
                fill(g, tx0, ty, kx, ty + 2, accent);
                fill(g, kx - 2, ty - 4, kx + 2, ty + 6, -1);
                this.hit(tx0 - 4, ty - 7, tx1 + 4, ty + 9, () -> {
                    this.drag = o;
                    this.dragX0 = tx0;
                    this.dragX1 = tx1;
                    this.slide(this.mx);
                });
                return y + 34;
            }
            default: {
                KeyMapping km = this.keyOf(o.field());
                Component key = Ui.bold(km == null ? Component.literal("?") : km.getTranslatedKeyMessage());
                Ui.text(g, Ui.fit(label, w - 40 - Ui.width(key)), x + 8, y + 7, Ui.TEXT);
                int kw = Ui.width(key) + 12;
                int kx = x + w - 8 - kw;
                boolean hv = this.hov(kx, y + 4, kx + kw, y + 18);
                fill(g, kx, y + 4, kx + kw, y + 18, hv ? 861813386 : 536870911);
                Ui.frame(g, kx, y + 4, kx + kw, y + 18, hv ? 1507043573 : 620756991);
                Ui.text(g, key, kx + 6, y + 8, Ui.TEXT);
                this.hit(x, y, x + w, y + 22, () -> this.mc.setScreen(new KeyBindsScreen(this, this.mc.options)));
                return y + 22;
            }
        }
    }

    private int extras(GuiGraphics g, Mods.Mod m, int x, int y, int w) {
        boolean inWorld = this.mc.player != null && this.mc.level != null;
        if (m.id().equals("waypoint")) {
            this.button(g, Ui.bold(tr("nosclient.wp.add")), x, y, 18, inWorld && this.c.waypoint, () -> {
                Waypoints.Wp wp = Waypoints.addHere(this.mc);
                this.note = Component.translatable("nosclient.wp.added", wp.name(), wp.x(), wp.y(), wp.z());
            });
            y += 22;
            Component sub = this.note != null
                ? this.note
                : (inWorld ? Component.translatable("nosclient.wp.count", Waypoints.here(this.mc).size()) : tr("nosclient.wp.noworld"));
            y += this.lines(g, Ui.small(sub), x + 2, y, w - 4, this.note != null ? Ui.ACCENT_HI : Ui.FAINT) + 4;
        } else if (m.id().equals("mp")) {
            boolean sp = this.mc.getSingleplayerServer() != null;
            this.button(g, Ui.bold(tr("nosclient.mp.openroom")), x, y, 18, sp && this.c.mp, () -> this.mc.setScreen(new PlayTogetherScreen(this)));
            y += 22;
            if (!sp) {
                y += this.lines(g, Ui.small(tr("nosclient.mp.needsp")), x + 2, y, w - 4, Ui.FAINT) + 4;
            }
        } else if (m.id().equals("lyrics")) {
            LyricsService service = SkyLyrics.service;
            if (service != null) {
                LyricsService.Snapshot snapshot = service.snapshot();
                y += this.lines(g, Ui.small(tr("nosclient.lyrics.status." + snapshot.status())), x + 2, y, w - 4, Ui.MUTED) + 5;
                if (!snapshot.track().title().isBlank()) {
                    Ui.text(g, Ui.fit(Ui.ui(snapshot.track().title()), w - 4), x + 2, y, Ui.TEXT);
                    y += 16;
                }

                this.button(g, Ui.small(tr("nosclient.lyrics.folder")), x, y, 18, true, service::openDirectory);
                y += 24;
            }

            y += this.lines(g, Ui.small(tr("nosclient.lyrics.drop")), x + 2, y, w - 4, Ui.MUTED) + 5;
            if (this.c.lyricsAutoFetch) {
                y += this.lines(g, Ui.small(tr("nosclient.lyrics.privacy")), x + 2, y, w - 4, Ui.MUTED) + 5;
            }
        } else if (m.id().equals("sky") || m.id().equals("weather")) {
            y += this.lines(g, Ui.small(tr("nosclient.visual.local")), x + 2, y, w - 4, Ui.MUTED) + 6;
        }

        return y;
    }

    private KeyMapping keyOf(String name) {
        for (KeyMapping km : this.mc.options.keyMappings) {
            if (km.getName().equals(name)) {
                return km;
            }
        }

        return null;
    }

    private void slide(double x) {
        if (this.drag != null) {
            double f = Math.min(1.0, Math.max(0.0, (x - this.dragX0) / (this.dragX1 - this.dragX0)));
            double v = this.drag.min() + f * (this.drag.max() - this.drag.min());
            double steps = this.drag.field().equals("gamma")
                ? 10.0
                : (this.drag.field().equals("skyHour") ? 4.0 : (this.drag.max() - this.drag.min() <= 2.0 ? 20.0 : 1.0));
            this.drag.set(Math.round(v * steps) / steps);
        }
    }

    private int lookTrackEnd(int x0, int x1) {
        return x0 + Math.min(220, (x1 - x0) * 55 / 100);
    }

    private int look(GuiGraphics g, int x0, int x1, int y) {
        int hudAccent = Mods.catColor("hud");
        Glass.panel(g, x0, y + 3, x1 - x0, 28, -686152147, 1442840575);
        Icons.draw(g, "look", x0 + 6, y + 6, 20.0F);
        Ui.text(g, Ui.fit(Ui.ui(tr("nosclient.theme.open")), x1 - x0 - 84), x0 + 31, y + 13, Ui.TEXT);
        int[] colors = new int[]{this.c.themeAccent, this.c.themeSurface, this.c.themeText};

        for (int i = 0; i < 3; i++) {
            Glass.fill(g, x1 - 43 + i * 12, y + 12, 8, 8, 0xFF000000 | colors[i]);
        }

        this.hit(x0, y + 3, x1, y + 31, () -> this.mc.setScreen(new ThemeScreen(this)));
        y += 42;
        double[] val = new double[]{this.c.hudScale - 0.5, this.c.hudBg};
        String[] lab = new String[]{"nosclient.look.size", "nosclient.look.bg"};
        String[] shown = new String[]{Math.round(this.c.hudScale * 100.0) + "%", Math.round(this.c.hudBg * 100.0) + "%"};
        int te = this.lookTrackEnd(x0, x1);

        for (int i = 0; i < 2; i++) {
            int ry = y + i * 34;
            Ui.text(g, Ui.ui(tr(lab[i])), x0, ry, Ui.TEXT);
            Component v = Ui.bold(Component.literal(shown[i]));
            Ui.text(g, v, te - Ui.width(v), ry, Ui.lerpCol(hudAccent, -1, 0.35F));
            int ty = ry + 15;
            int kx = (int)Math.round(x0 + val[i] * (te - x0));
            fill(g, x0, ty, te, ty + 2, 620756991);
            fill(g, x0, ty, kx, ty + 2, hudAccent);
            fill(g, kx - 2, ty - 4, kx + 2, ty + 6, -1);
            int li = i;
            this.hit(x0 - 4, ry + 8, te + 4, ry + 24, () -> {
                this.dragLook = li;
                this.dragX0 = x0;
                this.dragX1 = te;
                this.setLook(this.mx);
            });
        }

        int fy = y + 72;
        Ui.toggle(g, x0, fy, this.k("hudIdleFade", this.c.hudIdleFade), hudAccent);
        Ui.text(g, Ui.ui(tr("nosclient.look.fade")), x0 + 30, fy + 2, Ui.TEXT);
        this.hit(x0, fy - 2, x1, fy + 22, () -> {
            this.c.hudIdleFade = !this.c.hudIdleFade;
            this.c.save();
        });
        int iy = fy + 25;
        Ui.toggle(g, x0, iy, this.k("intro", this.c.intro), hudAccent);
        Ui.text(g, Ui.ui(tr("nosclient.intro.opt")), x0 + 30, iy + 2, Ui.TEXT);
        this.hit(x0, iy - 2, x1, iy + 22, () -> {
            this.c.intro = !this.c.intro;
            this.c.save();
        });
        int ry = iy + 25;
        Ui.toggle(g, x0, ry, this.k("hudReadable", this.c.hudReadable));
        Ui.text(g, Ui.ui(tr("nosclient.look.readable")), x0 + 30, ry + 2, Ui.TEXT);
        this.hit(x0, ry - 2, x1, ry + 20, () -> {
            this.c.hudReadable = !this.c.hudReadable;
            this.c.save();
        });
        int sy = ry + 25;
        Ui.toggle(g, x0, sy, this.k("hudShadow", this.c.hudShadow), hudAccent);
        Ui.text(g, Ui.ui(tr("nosclient.look.shadow")), x0 + 30, sy + 2, Ui.TEXT);
        this.hit(x0, sy - 2, x1, sy + 20, () -> {
            this.c.hudShadow = !this.c.hudShadow;
            this.c.save();
        });
        int ay = sy + 25;
        Ui.toggle(g, x0, ay, this.k("animations", this.c.animations));
        Ui.text(g, Ui.ui(tr("nosclient.look.animations")), x0 + 30, ay + 2, Ui.TEXT);
        this.hit(x0, ay - 2, x1, ay + 20, () -> {
            this.c.animations = !this.c.animations;
            this.c.save();
        });
        int pvx = te + 24;
        if (pvx + 60 < x1) {
            Ui.text(g, Ui.small(tr("nosclient.look.preview")), pvx, y, Ui.FAINT);
            String[] rows = new String[]{"144 FPS", "X 120 · Y 64 · Z −35", "0 | 0 CPS"};
            int widest = Arrays.stream(rows).mapToInt(value -> Ui.width(Ui.hud(value)) + 14).max().orElse(90);
            float k = Math.min((float)this.c.hudScale, (float)(x1 - pvx) / widest);
            float hint = Ui.hint;
            g.pose().pushMatrix();
            g.pose().translate(pvx, y + 14);
            g.pose().scale(k, k);
            Ui.hint = hint * k;

            try {
                for (int r = 0; r < rows.length; r++) {
                    Component hud = Ui.hud(rows[r]);
                    HudSurface.draw(g, 0, r * 23, Ui.width(hud) + 14, 19, (float)this.c.hudBg, 1.0F);
                    Ui.text(g, hud, 7, r * 23 + 5, Ui.TEXT);
                }
            } finally {
                Ui.hint = hint;
                g.pose().popMatrix();
            }
        }

        return ay + 26;
    }

    private void setLook(double x) {
        double k = Math.min(1.0, Math.max(0.0, (x - this.dragX0) / (this.dragX1 - this.dragX0)));
        if (this.dragLook == 0) {
            this.c.hudScale = Math.round((0.5 + k) * 20.0) / 20.0;
        } else {
            this.c.hudBg = Math.round(k * 20.0) / 20.0;
        }
    }

    private void paneChanged(int direction) {
        this.hubMotion.enter(direction);
        this.paneAt = System.nanoTime();
        this.hits.clear();
        this.maxScroll = 0;
        this.dMax = 0;
        this.drag = null;
        this.dragLook = -1;
        this.searchFocus = false;
        this.setDragging(false);
    }

    private void openDetail(String id) {
        this.paneChanged(1);
        this.detail = id;
        this.dScroll = 0.0F;
        this.dTarget = 0.0;
        this.note = null;
    }

    private void closeDetail() {
        this.paneChanged(-1);
        this.detail = null;
    }

    private void openSearch() {
        if (this.detail != null || this.tab != 0) {
            this.paneChanged(-1);
        }

        this.detail = null;
        this.tab = 0;
        this.searchFocus = true;
    }

    private void toggleAppearance() {
        this.carousel.select(0);
        this.paneChanged(this.tab == 0 ? 1 : -1);
        this.detail = null;
        this.cat = 0;
        this.tab = this.tab == 1 ? 0 : 1;
        this.scroll = 0.0F;
        this.scrollTarget = 0.0;
        this.setQuery("");
    }

    private void openCat(int i) {
        if (i != this.cat || this.tab != 0 || this.detail != null || this.searching()) {
            this.paneChanged(Math.floorMod(i - this.cat + CATS.length / 2, CATS.length) - CATS.length / 2);
            this.carousel.select(i);
            this.cat = i;
            this.tab = 0;
            this.detail = null;
            this.scroll = 0.0F;
            this.scrollTarget = 0.0;
            if (this.searching()) {
                this.setQuery("");
            }
        }
    }

    private void setLang(String code) {
        if (!code.equals(this.mc.options.languageCode)) {
            this.mc.options.languageCode = code;
            this.mc.getLanguageManager().setSelected(code);
            this.mc.getLanguageManager().onResourceManagerReload(this.mc.getResourceManager());
            this.mc.options.save();
        }
    }

    private List<Mods.Mod> scope() {
        return this.cat < 0 ? Mods.ALL : Mods.of(CATS[this.cat]);
    }

    private void askReset() {
        Component what = tr("nosclient.cat." + (this.cat < 0 ? "all" : CATS[this.cat]));
        this.mc.setScreen(new GlassConfirmScreen(ok -> {
            if (ok) {
                this.reset();
            }

            this.mc.setScreen(this);
        }, Component.translatable("nosclient.reset.title", what), tr("nosclient.reset.msg")));
    }

    private void reset() {
        boolean keys = false;

        for (Mods.Mod m : this.scope()) {
            Opt.reset(Opt.field(m));

            for (Opt o : Opt.BY_MOD.getOrDefault(m.id(), List.of())) {
                if (!o.type().equals("key")) {
                    Opt.reset(o.field());
                } else if (this.keyOf(o.field()) instanceof KeyMapping km) {
                    km.setKey(km.getDefaultKey());
                    keys = true;
                }
            }
        }

        if (this.cat <= 0) {
            for (String f : new String[]{"hudScale", "hudBg", "hudIdleFade", "animations"}) {
                Opt.reset(f);
            }
        }

        if (keys) {
            KeyMapping.resetMapping();
            this.mc.options.save();
        }

        this.c.save();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (this.motion.closing()) {
            return true;
        }

        if (e.button() != 0) {
            return false;
        }

        this.searchFocus = false;

        for (int i = this.hits.size() - 1; i >= 0; i--) {
            NosSettingsScreen.Hit h = this.hits.get(i);
            if (h.contains(e.x(), e.y())) {
                this.mx = (int)h.transform.localX(e.x());
                this.my = (int)h.transform.localY(e.y());
                h.run.run();
                this.hits.clear();
                this.click();
                this.setDragging(this.drag != null || this.dragLook >= 0);
                return true;
            }
        }

        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (this.motion.closing()) {
            return true;
        }

        if (this.drag != null) {
            this.slide(this.paneTransform.localX(e.x()));
        } else {
            if (this.dragLook < 0) {
                return super.mouseDragged(e, dx, dy);
            }

            this.setLook(this.paneTransform.localX(e.x()));
        }

        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (this.drag == null && this.dragLook < 0) {
            return super.mouseReleased(e);
        }

        if (this.drag != null) {
            this.slide(this.paneTransform.localX(e.x()));
        } else {
            this.setLook(this.paneTransform.localX(e.x()));
        }

        this.drag = null;
        this.dragLook = -1;
        this.c.save();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (!this.motion.closing() && !(this.paneOpen < 0.8F) && this.drag == null && this.dragLook < 0) {
            x = this.paneTransform.localX(x);
            y = this.paneTransform.localY(y);
            if (x < this.px() || x >= this.px() + this.pw() || y < this.py() || y >= this.py() + this.ph()) {
                return false;
            } else if (this.detail != null) {
                this.dTarget = Math.max(0.0, Math.min(this.dMax, this.dTarget - sy * 24.0));
                return true;
            } else if (this.maxScroll > 0) {
                this.scrollTarget = Math.max(0.0, Math.min(this.maxScroll, this.scrollTarget - sy * 24.0));
                return true;
            } else {
                return super.mouseScrolled(x, y, sx, sy);
            }
        } else {
            return true;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (this.motion.closing()) {
            return true;
        }

        if (!this.searchFocus) {
            if (e.key() == 344) {
                this.onClose();
                return true;
            } else if (e.key() == 256 && this.detail != null) {
                this.closeDetail();
                return true;
            } else if (e.key() == 70 && (e.modifiers() & 2) != 0) {
                this.openSearch();
                return true;
            } else {
                return super.keyPressed(e);
            }
        } else {
            int k = e.key();
            if ((e.modifiers() & 2) != 0 && k == 86) {
                this.setQuery(this.query + this.mc.keyboardHandler.getClipboard().replaceAll("[\\r\\n\\t]", " "));
                return true;
            }

            if ((e.modifiers() & 2) != 0 && k == 67) {
                this.mc.keyboardHandler.setClipboard(this.query);
                return true;
            }

            if (k == 256 || k == 257) {
                this.searchFocus = false;
            } else if (k == 259 && !this.query.isEmpty()) {
                this.setQuery((e.modifiers() & 2) != 0 ? "" : this.query.substring(0, this.query.offsetByCodePoints(this.query.length(), -1)));
            }

            return true;
        }
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (!this.motion.closing() && this.searchFocus && e.isAllowedChatCharacter()) {
            this.setQuery(this.query + e.codepointAsString());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onClose() {
        this.c.save();
        this.motion.exit(() -> this.mc.setScreen(this.parent));
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths.size() == 1 && paths.getFirst().getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lrc")) {
            this.openDetail("lyrics");
            if (SkyLyrics.service != null) {
                SkyLyrics.service.importLrc(paths.getFirst());
            }
        } else {
            super.onFilesDrop(paths);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Environment(EnvType.CLIENT)
    private record Hit(int x0, int y0, int x1, int y1, UiTransform transform, ScreenRectangle scissor, Runnable run) {
        boolean contains(double x, double y) {
            double lx = this.transform.localX(x);
            double ly = this.transform.localY(y);
            return lx >= this.x0 && lx < this.x1 && ly >= this.y0 && ly < this.y1 && (this.scissor == null || this.scissor.containsPoint((int)x, (int)y));
        }
    }
}
