package dev.jun.nosclient.hud;

import dev.jun.nosclient.Ui;
import dev.jun.nosclient.feat.Feats;
import dev.jun.nosclient.feat.Waypoints;
import dev.jun.nosclient.gui.Glass;
import dev.jun.nosclient.mixin.BossHealthOverlayAccessor;
import dev.jun.nosclient.mixin.MultiPlayerGameModeAccessor;
import dev.jun.nosclient.scene.SceneConfig;
import dev.jun.nosclient.theme.Theme;
import dev.jun.nosclient.theme.ThemePalette;
import dev.jun.nosclient.typography.Typography;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState.TraverseRange;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class Hud {
    public final Cps left = new Cps();
    public final Cps right = new Cps();
    private final SceneConfig c = SceneConfig.INSTANCE;
    private static int TEXT = -855319;
    private static int DIM = -5653067;
    private static int ACC = -6568787;
    private static final AnimatedText LABELS = new AnimatedText();
    private static String activeGadget = "";
    private static int metricIndex;
    private static long frameTime;
    private static float frameStep;
    private final Map<String, Hud.GadgetMotion> movement = new HashMap<>();
    private long renderedFrame;
    private final Map<String, Float> bars = new HashMap<>();
    private static final int PAD = 7;
    private static final int H = 19;
    private static final int GAP = 3;
    private static final float TS = 1.0F;
    private static final boolean COUNT = Boolean.getBoolean("nosclient.countHud");
    private int counted;
    private final long[] ft = new long[240];
    private int fi;
    private int fn;
    private long lastFrame;
    private long lastLow;
    private long lastSpark;
    private long lastDay;
    private long lastBusy;
    private int low;
    private final int[] spark = new int[24];
    private int sp;
    private String dayText = "";
    private final float[] keyGlow = new float[7];
    private float fade = 1.0F;
    private static float TA = 1.0F;
    private static float BA = 0.45F;
    private String biomeKey = "";
    private String biomeName = "";
    private String biomeLanguage = "";
    private static float mx0;
    private static float my0;
    private static float mx1;
    private static float my1;
    public boolean editing;
    private final Map<String, float[]> local = new HashMap<>();
    public final Map<String, float[]> rects = new LinkedHashMap<>();
    private float S = 1.0F;
    private int W;
    private int Hs;
    private static final float[] ZERO = new float[4];
    private static final String[] DIRS = new String[]{"n", "ne", "e", "se", "s", "sw", "w", "nw"};
    private long lastLook;
    private Object lastTarget;
    private String lookName = "";
    private String lookSub = "";
    private ItemStack lookIcon = ItemStack.EMPTY;
    private ItemStack lookTool = ItemStack.EMPTY;
    private boolean lookHas;
    private float lookHp = -1.0F;
    private static final ItemStack[] TOOLS = new ItemStack[]{
        new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.IRON_HOE)
    };
    private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    private final int[] armorKey = new int[ARMOR.length];
    private final String[] armorText = new String[ARMOR.length];
    private final ItemStack[] inv = new ItemStack[27];
    private int invStamp = -1;
    private boolean invEmpty = true;
    private static final int INV_W = 157;
    private static final int INV_H = 55;
    public static final Hud INSTANCE = new Hud();

    private Hud() {
    }

    public void render(GuiGraphics g, DeltaTracker dt) {
        if (!COUNT) {
            this.draw(g);
        } else {
            int[] n = new int[]{0};
            GuiRenderState st = g.guiRenderState;
            st.forEachElement(e -> n[0]++, TraverseRange.ALL);
            int before = n[0];
            this.draw(g);
            n[0] = 0;
            st.forEachElement(e -> n[0]++, TraverseRange.ALL);
            if (++this.counted % 120 == 0) {
                LoggerFactory.getLogger("nosclient").info("HUD: {} phần tử GUI/khung", n[0] - before);
            }
        }
    }

    private void draw(GuiGraphics g) {
        ThemePalette palette = Theme.palette();
        TEXT = palette.text();
        DIM = palette.muted();
        ACC = palette.accent();
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float d = this.lastFrame == 0L ? 0.0F : Math.min(0.05F, (float)(now - this.lastFrame) / 1.0E9F);
        if (this.lastFrame != 0L) {
            this.ft[this.fi] = now - this.lastFrame;
            this.fi = (this.fi + 1) % this.ft.length;
            if (this.fn < this.ft.length) {
                this.fn++;
            }
        }

        this.lastFrame = now;
        frameTime = now;
        frameStep = d;
        this.renderedFrame++;
        if (now - this.lastSpark > 125000000L) {
            this.spark[this.sp] = mc.getFps();
            this.sp = (this.sp + 1) % this.spark.length;
            this.lastSpark = now;
        }

        LocalPlayer p = mc.player;
        if (p != null && !mc.options.hideGui && !mc.debugEntries.isOverlayVisible()) {
            if (!mc.gui.getChat().isChatFocused() && !mc.options.keyPlayerList.isDown()) {
                boolean busy = p.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 || this.left.count(now) + this.right.count(now) > 0 || p.hurtTime > 0;
                if (busy) {
                    this.lastBusy = now;
                }

                this.fade = Ui.approach(this.fade, !this.editing && this.c.hudIdleFade && now - this.lastBusy >= 3000000000L ? 0.5F : 1.0F, d, 3.0F);
                TA = this.fade;
                BA = (float)this.c.hudBg * this.fade;
                this.S = (float)this.c.hudScale;
                this.W = Math.round(g.guiWidth() / this.S);
                this.Hs = Math.round(g.guiHeight() / this.S);
                g.pose().pushMatrix();
                g.pose().scale(this.S, this.S);
                Ui.hint = this.S * 1.0F;
                if (!this.editing) {
                    this.rects.clear();
                }

                try {
                    this.drawScaled(g, mc, p, mc.font, now, d, this.W);
                } finally {
                    Ui.hint = 1.0F;
                    g.pose().popMatrix();
                }
            }
        }
    }

    private static int T(int col) {
        return TA >= 0.999F ? col : Ui.alpha(col, TA);
    }

    private static void bg(GuiGraphics g, int x, int y, int w, int h) {
        mark(x, y, x + w, y + h);
        HudSurface.draw(g, x, y, w, h, BA, TA);
    }

    public static void mark(float x0, float y0, float x1, float y1) {
        mx0 = Math.min(mx0, x0);
        my0 = Math.min(my0, y0);
        mx1 = Math.max(mx1, x1);
        my1 = Math.max(my1, y1);
    }

    private int place(GuiGraphics g, String id, int fx, int fy, IntBinaryOperator draw) {
        HudLayout.Slot s = HudLayout.get(id);
        float k = (float)(Typography.hudScale(this.S * s.scale, Minecraft.getInstance().getWindow().getGuiScale(), this.c.hudReadable) / this.S);
        float[] b = this.local.getOrDefault(id, ZERO);
        float ox;
        float oy;
        if (s.auto()) {
            ox = fx;
            oy = fy;
        } else {
            ox = (float)(s.x * this.W) - (s.ax == 0 ? b[0] : (s.ax == 1 ? (b[0] + b[2]) / 2.0F : b[2])) * k;
            oy = (float)(s.y * this.Hs) - (s.ay == 0 ? b[1] : b[3]) * k;
        }

        Hud.GadgetMotion position = this.movement.computeIfAbsent(id, key -> new Hud.GadgetMotion());
        if (position.seen != this.renderedFrame - 1L) {
            position.alpha = 0.0F;
        }

        position.seen = this.renderedFrame;
        if (!this.editing && this.c.animations && Float.isFinite(position.x)) {
            position.x = Ui.approach(position.x, ox, frameStep, 15.0F);
            position.y = Ui.approach(position.y, oy, frameStep, 15.0F);
        } else {
            position.x = ox;
            position.y = oy;
            position.alpha = !this.editing && this.c.animations ? 0.0F : 1.0F;
        }

        position.alpha = Ui.approach(position.alpha, 1.0F, frameStep, 12.0F);
        ox = position.x;
        oy = position.y;
        activeGadget = id;
        metricIndex = 0;
        float ta = TA;
        float ba = BA;
        TA = TA * position.alpha;
        BA = BA * position.alpha;
        TA = TA * (float)s.alpha;
        BA = BA * (float)s.alpha;
        my0 = Float.MAX_VALUE;
        mx0 = Float.MAX_VALUE;
        my1 = -Float.MAX_VALUE;
        mx1 = -Float.MAX_VALUE;
        g.pose().pushMatrix();
        g.pose().translate(ox, oy);
        g.pose().scale(k, k);
        Ui.hint = this.S * k * 1.0F;

        int bottom;
        try {
            bottom = draw.applyAsInt(0, 0);
            if (this.editing && mx1 < mx0) {
                String n = Component.translatable("nosclient.mod." + id).getString();
                bg(g, 0, 0, sw(Minecraft.getInstance().font, n) + 14, 19);
                txt(g, Minecraft.getInstance().font, n, 7.0F, 5.5F, T(DIM));
                bottom = 19;
            }
        } finally {
            g.pose().popMatrix();
            TA = ta;
            BA = ba;
            Ui.hint = this.S * 1.0F;
        }

        if (mx1 >= mx0) {
            this.local.put(id, new float[]{mx0, my0, mx1, my1});
            this.rects.put(id, new float[]{(ox + mx0 * k) * this.S, (oy + my0 * k) * this.S, (ox + mx1 * k) * this.S, (oy + my1 * k) * this.S});
        } else {
            this.rects.remove(id);
        }

        return s.auto() ? Math.round(oy + bottom * k) : fy;
    }

    private void drawScaled(GuiGraphics g, Minecraft mc, LocalPlayer p, Font f, long now, float d, int W) {
        int x = 6;
        int y = 6;
        if (this.editing) {
            this.rects.clear();
        }

        if (this.c.fps) {
            y = this.place(g, "fps", x, y, (xx, yy) -> this.fps(g, f, xx, yy, mc.getFps(), this.low(now))) + 3;
        }

        if (this.c.coords) {
            BlockPos b = p.blockPosition();
            String k = mc.level.getBiome(b).unwrapKey().map(r -> r.identifier().toString()).orElse("");
            if (!k.equals(this.biomeKey) || !this.biomeLanguage.equals(mc.options.languageCode)) {
                this.biomeLanguage = mc.options.languageCode;
                this.biomeKey = k;
                this.biomeName = Component.translatable("biome." + k.replace(':', '.').replace('/', '.')).getString();
            }

            y = this.place(g, "coords", x, y, (xx, yy) -> this.coordinates(g, f, p, xx, yy)) + 3;
        }

        if (this.c.day) {
            if (now - this.lastDay > 2000000000L || this.dayText.isEmpty()) {
                long t = mc.level.getDayTime();
                int h = (int)((t / 1000L + 6L) % 24L);
                int m = (int)(t % 1000L * 60L / 1000L);
                this.dayText = Component.translatable("nosclient.hud.day.label").getString()
                    + " "
                    + (t / 24000L + 1L)
                    + (this.c.dayTime ? "  " + (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m : "");
                this.lastDay = now;
            }

            y = this.place(g, "day", x, y, (xx, yy) -> row(g, f, xx, yy, null, this.dayText, null)) + 3;
        }

        if (this.c.cps) {
            y = this.place(
                    g,
                    "cps",
                    x,
                    y,
                    (xx, yy) -> row(
                        g,
                        f,
                        xx,
                        yy,
                        null,
                        this.c.cpsRight ? this.left.count(now) + " | " + this.right.count(now) : String.valueOf(this.left.count(now)),
                        "CPS"
                    )
                )
                + 3;
        }

        if (this.c.ping && mc.getConnection() != null) {
            PlayerInfo pi = mc.getConnection().getPlayerInfo(p.getUUID());
            y = this.place(g, "ping", x, y, (xx, yy) -> row(g, f, xx, yy, null, String.valueOf(pi == null ? 0 : pi.getLatency()), "ms")) + 3;
        }

        if (this.c.keys) {
            y = this.place(g, "keys", x, y + 3, (xx, yy) -> this.keys(g, f, mc, xx, yy, now, d)) + 3;
        }

        if (this.c.sprint && this.c.sprintShow && (Feats.sprintOn || this.editing)) {
            y = this.place(g, "sprint", x, y, (xx, yy) -> row(g, f, xx, yy, null, Component.translatable("nosclient.hud.sprint").getString(), null)) + 3;
        }

        int ry = Math.round(this.Hs * 0.3F);
        if (this.c.minimap) {
            ry = this.place(g, "minimap", W - 6, ry, (xx, yy) -> Minimap.render(g, mc, xx, yy, BA, TA));
        }

        if (this.c.effects) {
            ry = this.place(g, "effects", W - 6, ry, (xx, yy) -> this.effects(g, f, p, xx, yy)) + 6;
        }

        if (this.c.armor) {
            ry = this.place(g, "armor", W - 6, ry, (xx, yy) -> this.armor(g, f, p, xx, yy));
        }

        if (this.c.invhud) {
            this.place(g, "invhud", W / 2 - 78, this.Hs - 22 - 40 - 55, (xx, yy) -> this.invhud(g, mc, p, xx, yy));
        }

        if (this.c.waypoint) {
            this.waypoints(g, f, mc, p, W, this.Hs);
        }

        if (this.c.blockinfo) {
            this.place(g, "blockinfo", W / 2, this.c.direction ? 44 : 6, (xx, yy) -> this.blockinfo(g, f, mc, xx, yy, now));
        }

        if (this.c.direction) {
            int boss = ((BossHealthOverlayAccessor)mc.gui.getBossOverlay()).nosclient$events().size();
            this.place(g, "direction", W / 2, boss > 0 ? 6 + boss * 19 : 6, (xx, yy) -> compass(g, f, p, xx, yy));
        }
    }

    private int low(long now) {
        if (now - this.lastLow > 500000000L && this.fn > 20) {
            long[] s = Arrays.copyOf(this.ft, this.fn);
            Arrays.sort(s);
            this.low = (int)Math.round(1.0E9 / Math.max(1L, s[Math.max(0, this.fn - 1 - this.fn / 100)]));
            this.lastLow = now;
        }

        return this.low;
    }

    private static int row(GuiGraphics g, Font f, int x, int y, String label, String val, String suffix) {
        int valueWidth = suffix != null && suffix.equals("ms") ? Math.max(sw(f, val), sw(f, "888")) : sw(f, val);
        int w = 7 + sw(f, label != null ? label + " " : "") + valueWidth + (suffix != null ? sw(f, " " + suffix) : 0) + 7;
        bg(g, x, y, w, 19);
        float tx = x + 7;
        float ty = y + 5.5F;
        if (label != null) {
            txt(g, f, label, tx, ty, T(DIM));
            tx += sw(f, label + " ");
        }

        metric(g, f, val, tx, ty, T(TEXT));
        if (suffix != null) {
            txt(g, f, suffix, tx + valueWidth + sw(f, " "), ty, T(DIM));
        }

        return y + 19;
    }

    private int coordinates(GuiGraphics g, Font f, LocalPlayer p, int x, int y) {
        BlockPos b = p.blockPosition();
        String[] values = this.c.coordsDecimal
            ? new String[]{
                String.format(Locale.ROOT, "%.1f", p.getX()), String.format(Locale.ROOT, "%.1f", p.getY()), String.format(Locale.ROOT, "%.1f", p.getZ())
            }
            : new String[]{Integer.toString(b.getX()), Integer.toString(b.getY()), Integer.toString(b.getZ())};
        String[] axes = new String[]{"X", "Y", "Z"};
        int[] spans = new int[3];
        int content = 0;

        for (int i = 0; i < 3; i++) {
            spans[i] = sw(f, axes[i] + " ") + Math.max(sw(f, values[i]), sw(f, i == 1 ? "88" : "−88"));
            content += spans[i] + (i == 2 ? 0 : 6);
        }

        int w = Math.max(content, this.c.coordsBiome ? sw(f, this.biomeName) : 0) + 14;
        int h = this.c.coordsBiome ? 31 : 19;
        bg(g, x, y, w, h);
        int tx = x + 7;

        for (int i = 0; i < 3; i++) {
            txt(g, f, axes[i], tx, y + 6, T(DIM));
            metric(g, f, values[i], tx + sw(f, axes[i] + " "), y + 6, T(TEXT));
            tx += spans[i] + 6;
        }

        if (this.c.coordsBiome) {
            txt(g, f, this.biomeName, x + 7, y + 19, T(DIM));
        }

        return y + h;
    }

    private static int sw(Font f, String s) {
        return Math.round(f.width(Ui.hud(s)) * 1.0F);
    }

    private static void txt(GuiGraphics g, Font f, String s, float x, float y, int col) {
        if (col >>> 24 >= 4) {
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(1.0F, 1.0F);
            Ui.snapText(g);
            g.drawString(f, Ui.hud(s), 0, 0, col, SceneConfig.INSTANCE.hudStyle == 1);
            g.pose().popMatrix();
        }
    }

    private static void metric(GuiGraphics g, Font font, String text, float x, float y, int color) {
        AnimatedText.Frame frame = LABELS.update(activeGadget + ":" + metricIndex++, text, frameTime, SceneConfig.INSTANCE.animations);
        if (frame.progress() < 1.0F && !frame.previous().equals(frame.current()) && sw(font, frame.previous()) <= sw(font, frame.current())) {
            txt(g, font, frame.previous(), x, y - frame.progress() * 1.5F, Ui.alpha(color, (1.0F - frame.progress()) * 0.3F));
        }

        txt(g, font, frame.current(), x, y + (1.0F - frame.progress()) * 1.5F, Ui.alpha(color, 0.7F + 0.3F * frame.progress()));
    }

    private int fps(GuiGraphics g, Font f, int x, int y, int fps, int low) {
        String v = String.valueOf(fps);
        String lo = "1% " + low;
        if (!this.c.fpsLow) {
            return row(g, f, x, y, null, v, "FPS");
        }

        int spw = 18;
        int valueWidth = Math.max(sw(f, v), sw(f, "888"));
        int lowWidth = Math.max(sw(f, lo), sw(f, "1% 888"));
        int w = 7 + valueWidth + sw(f, " FPS") + 6 + spw + 6 + lowWidth + 7;
        bg(g, x, y, w, 19);
        float ty = y + 5.5F;
        int tx = x + 7;
        metric(g, f, v, tx, ty, T(TEXT));
        txt(g, f, "FPS", tx + valueWidth + sw(f, " "), ty, T(DIM));
        tx += valueWidth + sw(f, " FPS") + 6;
        int sw = spw;
        int mn = Integer.MAX_VALUE;
        int mx = 0;

        for (int s : this.spark) {
            mn = Math.min(mn, s);
            mx = Math.max(mx, s);
        }

        int prev = -1;

        for (int i = 0; i < this.spark.length; i++) {
            int s = this.spark[(this.sp + i) % this.spark.length];
            int yy = y + 19 - 6 - Math.round((float)(s - mn) / Math.max(1, mx - mn) * 5.0F);
            int xx = tx + Math.round((float)(i * (sw - 1)) / (this.spark.length - 1));
            if (prev >= 0) {
                g.fill(xx - 1, Math.min(prev, yy), xx, Math.max(prev, yy) + 1, T(Ui.alpha(ACC, 0.8F)));
            }

            prev = yy;
        }

        metric(g, f, lo, tx + sw + 6, ty, T(DIM));
        return y + 19;
    }

    private static int compass(GuiGraphics g, Font f, LocalPlayer p, int cx, int y) {
        Minecraft mc = Minecraft.getInstance();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float yaw = (p.getViewYRot(partial) % 360.0F + 540.0F) % 360.0F;
        boolean degrees = SceneConfig.INSTANCE.directionDeg;
        int w = degrees ? 196 : 158;
        int h = 22;
        int x0 = cx - w / 2;
        int center = cx - (degrees ? 18 : 0);
        bg(g, x0, y, w, h);

        for (int degree = -180; degree <= 540; degree += 15) {
            float dx = (degree - yaw) * 1.35F;
            if (!(Math.abs(dx) > 66.0F)) {
                int xx = Math.round(center + dx);
                float edge = 1.0F - Math.abs(dx) / 76.0F;
                if (degree % 45 == 0) {
                    int direction = Math.floorMod(degree / 45, 8);
                    String label = Component.translatable("nosclient.dir." + DIRS[direction]).getString();
                    txt(g, f, label, xx - sw(f, label) / 2.0F, y + 7, T(Ui.alpha(direction == 0 ? ACC : TEXT, edge)));
                } else {
                    g.fill(xx, y + 14, xx + 1, y + 17, T(Ui.alpha(DIM, edge * 0.5F)));
                }
            }
        }

        if (SceneConfig.INSTANCE.waypoint) {
            for (Waypoints.Wp point : Waypoints.here(mc)) {
                double angle = (Math.toDegrees(Math.atan2(-(point.x() + 0.5 - p.getX()), point.z() + 0.5 - p.getZ())) + 540.0) % 360.0;
                float dx = (float)((angle - yaw + 540.0) % 360.0 - 180.0) * 1.35F;
                if (Math.abs(dx) <= 66.0F) {
                    int xx = Math.round(center + dx);
                    g.fill(xx - 1, y + 2, xx + 2, y + 4, T(point.color()));
                }
            }
        }

        Glass.fill(g, center - 1, y + 2, 2, 3, T(ACC));
        if (degrees) {
            int split = x0 + w - 37;
            g.fill(split, y + 6, split + 1, y + h - 6, T(Ui.alpha(DIM, 0.2F)));
            String value = Math.round(yaw) % 360 + "°";
            txt(g, f, value, split + (37 - sw(f, value)) / 2.0F, y + 7, T(DIM));
        }

        return y + h;
    }

    private int keys(GuiGraphics g, Font f, Minecraft mc, int x, int y, long now, float dt) {
        Options o = mc.options;
        int s = 20;
        int gap = 3;
        this.key(g, f, 0, x + s + gap, y, s, s, "W", o.keyUp, dt);
        this.key(g, f, 1, x, y + s + gap, s, s, "A", o.keyLeft, dt);
        this.key(g, f, 2, x + s + gap, y + s + gap, s, s, "S", o.keyDown, dt);
        this.key(g, f, 3, x + 2 * (s + gap), y + s + gap, s, s, "D", o.keyRight, dt);
        int full = 3 * s + 2 * gap;
        int mw = (full - gap) / 2;
        int my = y + 2 * (s + gap);
        if (this.c.keysMouse) {
            this.key(g, f, 4, x, my, mw, this.c.keysCps ? 26 : s, this.c.keysCps ? "LMB\n" + this.left.count(now) + " CPS" : "LMB", o.keyAttack, dt);
            this.key(g, f, 5, x + mw + gap, my, mw, this.c.keysCps ? 26 : s, this.c.keysCps ? "RMB\n" + this.right.count(now) + " CPS" : "RMB", o.keyUse, dt);
            my += (this.c.keysCps ? 26 : s) + gap;
        }

        if (!this.c.keysSpace) {
            return my - gap;
        }

        this.key(g, f, 6, x, my, full, 10, "", o.keyJump, dt);
        return my + 10;
    }

    private void waypoints(GuiGraphics g, Font f, Minecraft mc, LocalPlayer p, int W, int Hs) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 cp = cam.position();
        double yaw = Math.toRadians(cam.yRot());
        double pitch = Math.toRadians(cam.xRot());
        double tanY = Math.tan(Math.toRadians(Feats.lastFov) / 2.0);
        double aspect = (double)W / Hs;

        for (Waypoints.Wp w : Waypoints.here(mc)) {
            double dx = w.x() + 0.5 - cp.x;
            double dy = w.y() + 1.2 - cp.y;
            double dz = w.z() + 0.5 - cp.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double rx = dx * Math.cos(yaw) + dz * Math.sin(yaw);
            double rz = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
            double ry = dy * Math.cos(pitch) + rz * Math.sin(pitch);
            double fwd = -dy * Math.sin(pitch) + rz * Math.cos(pitch);
            boolean on = fwd > 0.1;
            double sx;
            double sy;
            if (!on) {
                sx = rx > 0.0 ? 10.0 : W - 10;
                sy = Hs / 2.0;
            } else {
                sx = W / 2.0 - rx / fwd / (tanY * aspect) * W / 2.0;
                sy = Hs / 2.0 - ry / fwd / tanY * Hs / 2.0;
                on = sx > 8.0 && sx < W - 8 && sy > 8.0 && sy < Hs - 8;
            }

            if (!on) {
                sx = Math.max(10.0, Math.min(W - 10, sx));
                sy = Math.max(10.0, Math.min(Hs - 30, sy));
            }

            int x = (int)Math.round(sx);
            int y = (int)Math.round(sy);

            for (int k = 0; k < 4; k++) {
                g.fill(x - k, y - 3 + k, x + k + 1, y - 2 + k, T(w.color()));
            }

            for (int k = 0; k < 3; k++) {
                g.fill(x - 2 + k, y + 1 + k, x + 3 - k, y + 2 + k, T(w.color()));
            }

            String label = w.name() + "  " + Math.round(dist) + " m";
            int lw = sw(f, label) + 14;
            bg(g, x - lw / 2, y + 6, lw, 13);
            txt(g, f, label, x - sw(f, label) / 2.0F, y + 6 + 2.5F, T(TEXT));
        }
    }

    private static Object lookTarget(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult eh) {
            return eh.getEntity().getId();
        } else {
            return hit instanceof BlockHitResult bh && hit.getType() == Type.BLOCK && mc.level != null
                ? List.of(bh.getBlockPos(), mc.level.getBlockState(bh.getBlockPos()))
                : null;
        }
    }

    private int blockinfo(GuiGraphics g, Font f, Minecraft mc, int cx, int y, long now) {
        Object target = lookTarget(mc);
        if (!Objects.equals(target, this.lastTarget) || now - this.lastLook > 250000000L) {
            if (!Objects.equals(target, this.lastTarget)) {
                this.bars.remove("target");
            }

            this.lastTarget = target;
            this.lastLook = now;
            this.readLook(mc);
        }

        if (!this.lookHas) {
            return y;
        }

        int iw = this.lookIcon.isEmpty() ? 0 : 20;
        int w = Math.max(sw(f, this.lookName), sw(f, this.lookSub) + (this.lookTool.isEmpty() ? 0 : 14)) + iw + 14;
        int h = this.lookSub.isEmpty() && this.lookTool.isEmpty() ? 18 : 28;
        int x = cx - w / 2;
        bg(g, x, y, w, h);
        if (iw > 0) {
            g.renderItem(this.lookIcon, x + 7 - 1, y + 6);
        }

        int tx = x + 7 + iw;
        metric(g, f, this.lookName, tx, y + 5, T(TEXT));
        metric(g, f, this.lookSub, tx, y + 16, T(DIM));
        if (!this.lookTool.isEmpty()) {
            g.pose().pushMatrix();
            g.pose().translate(tx + sw(f, this.lookSub) + 3, y + 14);
            g.pose().scale(0.6F, 0.6F);
            g.renderItem(this.lookTool, 0, 0);
            g.pose().popMatrix();
        }

        float bar = this.lookHp >= 0.0F ? this.lookHp : ((MultiPlayerGameModeAccessor)mc.gameMode).nosclient$progress();
        if (bar > 0.0F) {
            float shown = this.c.animations ? Ui.approach(this.bars.getOrDefault("target", bar), bar, frameStep, 14.0F) : bar;
            this.bars.put("target", shown);
            g.fill(x + 3, y + h - 2, x + 3 + Math.round((w - 6) * Math.min(1.0F, shown)), y + h - 1, T(this.lookHp >= 0.0F ? -2256495 : ACC));
        } else {
            this.bars.remove("target");
        }

        return y + h;
    }

    private void readLook(Minecraft mc) {
        this.lookHas = false;
        this.lookHp = -1.0F;
        HitResult hit = mc.hitResult;
        if (hit != null && mc.level != null) {
            if (hit instanceof EntityHitResult eh) {
                Entity e = eh.getEntity();
                this.lookName = e.getDisplayName().getString();
                this.lookSub = this.c.blockId ? BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString() : "";
                this.lookIcon = ItemStack.EMPTY;
                this.lookTool = ItemStack.EMPTY;
                if (this.c.blockHp && e instanceof LivingEntity le) {
                    this.lookHp = le.getHealth() / Math.max(1.0F, le.getMaxHealth());
                    this.lookSub = Math.round(le.getHealth()) + "/" + Math.round(le.getMaxHealth()) + " ♥  " + this.lookSub;
                }

                this.lookHas = true;
            } else if (hit instanceof BlockHitResult bh && hit.getType() == Type.BLOCK) {
                BlockState st = mc.level.getBlockState(bh.getBlockPos());
                if (st.isAir()) {
                    return;
                }

                this.lookName = st.getBlock().getName().getString();
                this.lookSub = this.c.blockId ? BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString() : "";
                this.lookIcon = new ItemStack(st.getBlock().asItem());
                this.lookTool = !this.c.blockTool
                    ? ItemStack.EMPTY
                    : (
                        st.is(BlockTags.MINEABLE_WITH_PICKAXE)
                            ? TOOLS[0]
                            : (
                                st.is(BlockTags.MINEABLE_WITH_AXE)
                                    ? TOOLS[1]
                                    : (st.is(BlockTags.MINEABLE_WITH_SHOVEL) ? TOOLS[2] : (st.is(BlockTags.MINEABLE_WITH_HOE) ? TOOLS[3] : ItemStack.EMPTY))
                            )
                    );
                this.lookHas = true;
            }
        }
    }

    private int effects(GuiGraphics g, Font f, LocalPlayer p, int xr, int y) {
        for (MobEffectInstance e : p.getActiveEffects()) {
            if (e.showIcon()) {
                String name = e.getEffect().value().getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "");
                String time = !this.c.fxTime ? "" : (e.isInfiniteDuration() ? "∞" : mmss(e.getDuration() / 20));
                int w = 26 + sw(f, name) + (time.isEmpty() ? 0 : 6 + sw(f, time)) + 7;
                int h = 21;
                int x = xr - w;
                bg(g, x, y, w, h);
                float blink = !e.isInfiniteDuration() && e.endsWithin(200) ? 0.45F + 0.55F * (float)Math.abs(Math.sin(System.nanoTime() / 2.5E8)) : 1.0F;
                g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(e.getEffect()), x + 7, y + 1, 14, 14, T(Ui.alpha(-1, blink)));
                float ty = y + (h - 8.0F) / 2.0F;
                txt(g, f, name, x + 7 + 19, ty, T(e.getEffect().value().isBeneficial() ? TEXT : -25958));
                txt(g, f, time, x + w - 7 - sw(f, time), ty, T(Ui.alpha(DIM, blink)));
                y += h + 3;
            }
        }

        return y;
    }

    private static String mmss(int s) {
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    private static String roman(int n) {
        return n <= 10 ? new String[]{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"}[n] : String.valueOf(n);
    }

    private int armor(GuiGraphics g, Font f, LocalPlayer p, int xr, int y) {
        for (int i = 0; i < ARMOR.length; i++) {
            ItemStack it = p.getItemBySlot(ARMOR[i]);
            if ((i < 4 || this.c.armorHand) && !it.isEmpty() && it.isDamageableItem()) {
                int left = it.getMaxDamage() - it.getDamageValue();
                int k = (System.identityHashCode(it.getItem()) * 31 + left) * 4 + this.c.armorFmt;
                float frac = (float)left / Math.max(1, it.getMaxDamage());
                if (this.armorKey[i] != k || this.armorText[i] == null) {
                    this.armorKey[i] = k;
                    this.armorText[i] = this.c.armorFmt == 0 ? String.valueOf(left) : (this.c.armorFmt == 1 ? Math.round(frac * 100.0F) + "%" : "");
                }

                int col = frac > 0.5F ? -8398733 : (frac > 0.2F ? -670639 : -895654);
                boolean warn = this.c.armorWarn && frac < 0.1F;
                float bl = warn ? 0.4F + 0.6F * (float)Math.abs(Math.sin(System.nanoTime() / 2.0E8)) : 1.0F;
                int w = 23 + (this.armorText[i].isEmpty() ? 0 : 5 + sw(f, this.armorText[i])) + 7;
                int h = 18;
                int x = xr - w;
                bg(g, x, y, w, h);
                if (warn) {
                    g.fill(x, y, x + w, y + h, T(Ui.alpha(1727157594, bl)));
                }

                g.renderItem(it, x + 7 - 1, y + 1);
                if (!this.armorText[i].isEmpty()) {
                    txt(g, f, this.armorText[i], x + 7 + 20, y + (h - 8.0F) / 2.0F, T(Ui.alpha(col, bl)));
                }

                g.fill(x, y + h - 1, x + Math.round(w * frac), y + h, T(col));
                y += h + 3;
            }
        }

        return y;
    }

    private int invhud(GuiGraphics g, Minecraft mc, LocalPlayer p, int x, int y) {
        Inventory pi = p.getInventory();
        int stamp = pi.getTimesChanged() + (p.isDeadOrDying() ? 1073741824 : 0);
        if (stamp != this.invStamp) {
            this.invEmpty = true;

            for (int i = 0; i < 27; i++) {
                this.inv[i] = pi.getItem(9 + i).copy();
                this.invEmpty = this.invEmpty & this.inv[i].isEmpty();
            }

            this.invStamp = stamp;
        }

        if (this.invEmpty) {
            return y;
        }

        int cell = 17;
        int cols = 9;
        int w = 157;
        int h = 55;
        mark(x, y, x + w, y + h);
        if (this.c.invhudBg) {
            bg(g, x, y, w, h);
        }

        for (int i = 0; i < 27; i++) {
            if (!this.inv[i].isEmpty()) {
                int cx = x + 2 + i % cols * cell;
                int cy = y + 2 + i / cols * cell;
                g.renderItem(this.inv[i], cx, cy);
                g.renderItemDecorations(mc.font, this.inv[i], cx, cy);
            }
        }

        return y + h;
    }

    private void key(GuiGraphics g, Font f, int i, int x, int y, int w, int h, String label, KeyMapping binding, float dt) {
        mark(x, y, x + w, y + h);
        this.keyGlow[i] = this.c.animations ? Ui.approach(this.keyGlow[i], binding.isDown() ? 1.0F : 0.0F, dt, 20.0F) : (binding.isDown() ? 1.0F : 0.0F);
        float e = Ui.ease(this.keyGlow[i]);
        ThemePalette palette = Theme.palette();
        int color = ThemePalette.mix(palette.surface(), palette.accent(), e * 0.9);
        Glass.fill(g, x, y, w, h, Ui.alpha(color, BA + (Math.max(BA, 0.9F * TA) - BA) * e));
        Glass.outline(g, x, y, w, h, T(Ui.alpha(ACC, 0.1F + e * 0.45F)));
        int foreground = T(Ui.lerpCol(TEXT, palette.ink(), e));
        float press = this.c.animations ? e * 0.6F : 0.0F;
        if (label.isEmpty()) {
            Glass.fill(g, x + w / 2 - 8, y + h / 2, 16, 1, foreground);
        } else {
            String[] lines = label.split("\\n");
            if (lines.length == 1) {
                txt(g, f, label, x + (w - sw(f, label)) / 2.0F, y + (h - 8) / 2.0F + press, foreground);
            } else {
                txt(g, f, lines[0], x + (w - sw(f, lines[0])) / 2.0F, y + 4 + press, foreground);
                txt(g, f, lines[1], x + (w - sw(f, lines[1])) / 2.0F, y + 15 + press, Ui.alpha(foreground, 0.8F));
            }
        }
    }

    @Environment(EnvType.CLIENT)
    private static final class GadgetMotion {
        float x = Float.NaN;
        float y;
        float alpha;
        long seen;
    }
}
