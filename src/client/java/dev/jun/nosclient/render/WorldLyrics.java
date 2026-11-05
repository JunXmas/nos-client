package dev.jun.nosclient.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.jun.nosclient.config.MusicConfig;
import dev.jun.nosclient.config.MusicSettings;
import dev.jun.nosclient.feat.AutoPerf;
import dev.jun.nosclient.media.MediaManager;
import dev.jun.nosclient.media.lyrics.Lyrics;
import dev.jun.nosclient.mixin.GameRendererAccessor;
import dev.jun.nosclient.util.Anim;
import dev.jun.nosclient.util.Col;

import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class WorldLyrics {
    private static final List<WorldLyrics.Placed> visible = new ArrayList<>();
    private static final Set<Long> shown = new HashSet<>();
    private static long lastT = -1L;
    private static MusicConfig.LyricsStyle lastStyle;
    private static long songSeq = -1L;
    private static float pauseFade = 1.0F;
    private static int layer;
    private static boolean ghostNow;
    private static float layerStep = 0.06F;
    private static final String[] NOTES = new String[] {"♪ ♫", "♫ ♪ ♬", "♪ ♫ ♪", "♬ ♪", "♫ ♪ ♫ ♪"};
    private static Lyrics.Line noteLine;
    private static long noteSlot = -1L;
    private static long noteSong = -1L;
    private static Set<WorldLyrics.Placed> ghostOnly;

    private WorldLyrics() {}

    static void onEvent(MediaManager.Event e) {
        if (e.type() == MediaManager.EventType.SONG || e.type() == MediaManager.EventType.STOP) {
            visible.clear();
            shown.clear();
        }
    }

    public static void clear() {
        visible.clear();
        shown.clear();
    }

    private static boolean chunked(MusicConfig c) {
        return c.lyricsStyle == MusicConfig.LyricsStyle.FLOATING || skyWords(c);
    }

    private static boolean skyWords(MusicConfig c) {
        return c.lyricsStyle == MusicConfig.LyricsStyle.SKY && c.skyWords;
    }

    private static List<int[]> chunks(Lyrics.Line line, MusicConfig c) {
        List<int[]> out = new ArrayList<>();
        if (!chunked(c)) out.add(new int[] {0, line.words.size()});
        else for (int i = 0; i < line.words.size(); i++) out.add(new int[] {i, i + 1});
        return out;
    }

    static void tick() {
        MediaManager mm = MediaManager.get();
        MusicConfig c = MusicSettings.get();
        if (lastStyle != c.lyricsStyle) {
            clear();
            lastT = -1;
            lastStyle = c.lyricsStyle;
        }
        if (!c.enabled || !c.showLyrics || c.lyricsStyle == MusicConfig.LyricsStyle.NOS_SKY) {
            visible.clear();
        } else {
            Lyrics lyrics = mm.lyrics();
            long t = mm.position();
            if (mm.songSeq() != songSeq) {
                songSeq = mm.songSeq();
                shown.clear();
                visible.clear();
            }

            boolean playing = mm.isPlaying();
            pauseFade = Anim.approach(pauseFade, playing ? 1.0F : 0.0F, playing ? 4.0F : 1.1F);
            boolean noLyrics = lyrics == null || lyrics.lines.isEmpty();
            boolean notesOk =
                    c.musicNotes
                            && (!noLyrics
                                    || mm.lyricsState() == MediaManager.LyricsState.NOT_FOUND);
            if (t >= 0L && (!noLyrics || notesOk)) {
                if (lastT >= 0L && t < lastT - 1500L) {
                    visible.clear();
                    shown.clear();
                }

                lastT = t;
                Lyrics.Line cur = noLyrics ? null : lyrics.lastStartedBefore(t);
                boolean skyPairs = c.lyricsStyle == MusicConfig.LyricsStyle.SKY && !c.skyWords;
                if (notesOk && playing && quiet(lyrics, cur, t)) {
                    cur = noteLine(t);
                }

                if (cur != null
                        && (float) t < (float) cur.end + 650.0F
                        && t - cur.start < 6000L
                        && !cur.words.isEmpty()) {
                    long seed = cur.index * 7919L + songSeq * 104729L;

                    for (int[] ch : chunks(cur, c)) {
                        if (find(cur, ch[0]) == null
                                && !shown.contains(cur.index * 1000L + ch[0])) {
                            long st = cur.words.get(ch[0]).start;
                            if ((!chunked(c) || t >= st - 80L)
                                    && (!chunked(c) || t <= cur.words.get(ch[1] - 1).end + 1200L)) {
                                shown.add(cur.index * 1000L + ch[0]);
                                WorldLyrics.Placed p =
                                        new WorldLyrics.Placed(
                                                cur, ch[0], ch[1], seed + ch[0] * 613L);
                                place(p, c);
                                if (ch[0] == 0) {
                                    for (WorldLyrics.Placed o : visible) {
                                        if ((cur.index >= 0 || o.line.index >= 0)
                                                && (!skyPairs
                                                        || cur.index % 2 != 1
                                                        || o.line.index != cur.index - 1)
                                                && o.exitAt < 0L
                                                && o.line != cur) {
                                            o.exitAt =
                                                    chunked(c)
                                                            ? Math.max(
                                                                    o.lastEnd(),
                                                                    Math.min(t, cur.start) + 250L)
                                                            : Math.max(
                                                                    o.line.start,
                                                                    Math.min(t, cur.start));
                                        }
                                    }
                                }

                                visible.add(p);
                            }
                        }
                    }
                }

                int limit =
                        chunked(c)
                                ? Math.max(2, c.maxLines * 3)
                                : Math.max(skyPairs ? 2 : 1, c.maxLines);
                int alive = 0;

                for (int i = visible.size() - 1; i >= 0; i--) {
                    WorldLyrics.Placed p = visible.get(i);
                    if (p.exitAt < 0L) {
                        alive++;
                        long linger =
                                chunked(c)
                                        ? (c.lyricsStyle == MusicConfig.LyricsStyle.FLOATING
                                                ? 900
                                                : 1500)
                                        : 600L;
                        if (alive > limit) {
                            p.exitAt = t;
                        } else {
                            long lineEnd = p.line.index >= 0 ? p.line.end : 0L;
                            if (skyPairs
                                    && !noLyrics
                                    && p.line.index >= 0
                                    && p.line.index % 2 == 0
                                    && p.line.index + 1 < lyrics.lines.size()) {
                                lineEnd = Math.max(lineEnd, lyrics.lines.get(p.line.index + 1).end);
                            }

                            long until = Math.max(p.lastEnd() + linger, lineEnd);
                            if (t > until) {
                                p.exitAt = until;
                            }
                        }
                    }
                }

                float speed = Math.max(0.3F, c.animSpeed);
                Iterator<WorldLyrics.Placed> it = visible.iterator();

                while (it.hasNext()) {
                    WorldLyrics.Placed p = it.next();
                    boolean gone =
                            p.exitAt >= 0L
                                    && (float) t
                                            > (float) p.exitAt
                                                    + 650.0F / speed
                                                    + (float) (60L * p.n());
                    if (gone || p.lookedAway && p.viewVis < 0.02F) {
                        it.remove();
                    }
                }

                if (!(pauseFade < 0.01F)) {
                    for (WorldLyrics.Placed p : visible) {
                        update(p, t, c);
                    }
                }
            } else {
                visible.clear();
            }
        }
    }

    private static void update(WorldLyrics.Placed p, long t, MusicConfig c) {
        Minecraft mc = Minecraft.getInstance();
        if (c.lyricsStyle == MusicConfig.LyricsStyle.SKY) {
            if (++p.occlTick % 10 == 1 && mc.level != null) {
                BlockPos at = BlockPos.containing(Proj.cx, Proj.cy, Proj.cz);
                boolean under =
                        !c.showUnderground
                                && !mc.level.canSeeSky(at.above())
                                && mc.level.getBrightness(LightLayer.SKY, at) < 4;
                p.occl = under ? 0.0F : 1.0F;
            }

            p.viewVis = 1.0F;
        } else {
            double[] a = anchor(p, c);
            double dx = a[0] - Proj.cx;
            double dy = a[1] - Proj.cy;
            double dz = a[2] - Proj.cz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = d < 1.0E-4 ? 0.0F : (float) (-Math.toDegrees(Math.asin(dy / d)));
            float relYaw = Math.abs(wrap(yaw - Proj.yaw));
            float relPitch = Math.abs(pitch - Proj.pitch);
            boolean inView =
                    relYaw < 52.0F
                            && relPitch < 42.0F
                            && d < 24.0F * Math.max(1.0F, c.lyricsDistance) + 4.0F;
            if (!inView && p.viewVis > 0.5F) {
                p.lookedAway = true;
            }

            p.viewVis =
                    Anim.approach(
                            p.viewVis,
                            inView && !p.lookedAway ? 1.0F : 0.0F,
                            p.lookedAway ? 9.0F : 12.0F);
            if (++p.occlTick % 6 == 1 && mc.level != null && mc.player != null) {
                Vec3 from = new Vec3(Proj.cx, Proj.cy, Proj.cz);
                Vec3 to = new Vec3(a[0], a[1], a[2]);
                HitResult hit =
                        mc.level.clip(
                                new ClipContext(from, to, Block.VISUAL, Fluid.NONE, mc.player));
                p.occl =
                        hit.getType() == Type.BLOCK && hit.getLocation().distanceTo(from) < d - 0.4
                                ? 0.0F
                                : 1.0F;
            }
        }

        p.occlVis = Anim.approach(p.occlVis, p.occl, 6.0F);
    }

    private static double[] anchor(WorldLyrics.Placed p, MusicConfig c) {
        if (c.lyricsStyle == MusicConfig.LyricsStyle.SKY && !c.viewLock) {
            return new double[] {p.ax, p.ay, p.az};
        } else if (c.lyricsStyle == MusicConfig.LyricsStyle.SKY) {
            float yaw = c.viewLock ? Proj.yaw + p.yaw : p.yaw;
            float pitch = c.viewLock ? Proj.pitch + p.pitch : p.pitch;
            double[] d = Proj.dir(yaw, pitch);
            return new double[] {
                Proj.cx + d[0] * p.dist, Proj.cy + d[1] * p.dist, Proj.cz + d[2] * p.dist
            };
        } else if (c.viewLock) {
            double[] d = Proj.dir(Proj.yaw + p.yaw, Proj.pitch + p.pitch);
            return new double[] {
                Proj.cx + d[0] * p.dist, Proj.cy + d[1] * p.dist, Proj.cz + d[2] * p.dist
            };
        } else {
            return new double[] {p.ax, p.ay, p.az};
        }
    }

    private static Quaternionf facing(WorldLyrics.Placed p, MusicConfig c) {
        float yaw;
        float pitch;
        if (c.viewLock) {
            yaw = Proj.yaw + p.yaw;
            pitch = Proj.pitch + p.pitch;
        } else {
            yaw = p.yaw;
            pitch = p.pitch;
        }

        if (c.lyricsStyle == MusicConfig.LyricsStyle.SKY) {
            yaw += p.turn;
        }

        pitch = Math.max(-35.0F, Math.min(35.0F, pitch));
        return new Quaternionf()
                .rotationYXZ(
                        (float) Math.PI - (float) Math.toRadians(yaw),
                        -((float) Math.toRadians(pitch)),
                        0.0F);
    }

    private static float em(WorldLyrics.Placed p, MusicConfig c) {
        if (c.lyricsStyle == MusicConfig.LyricsStyle.SKY) {
            return (float) (150.0 * Math.tan(Math.toRadians(3.1))) * c.lyricsScale;
        } else {
            return c.lyricsStyle == MusicConfig.LyricsStyle.FLOATING
                    ? (float) (0.0765 * Math.max(2.5, p.sizeDist)) * c.lyricsScale
                    : (float) (0.085 * Math.max(2.5, p.dist)) * c.lyricsScale;
        }
    }

    public static boolean lateForShaders() {
        return IrisCompat.shadersOn();
    }

    public static void renderWorld(PoseStack ps, MultiBufferSource buffers) {
        renderWorld(ps, buffers, false);
    }

    public static void renderGhost(PoseStack ps, MultiBufferSource buffers) {
        MusicConfig c = MusicSettings.get();
        if (!lateForShaders()) {
            {
                {
                    ghostOnly = playerCovers();
                    if (ghostOnly.isEmpty()) {
                        return;
                    }
                }

                try {
                    renderWorld(ps, buffers, true);
                } finally {
                    ghostOnly = null;
                }
            }
        }
    }

    private static void renderWorld(PoseStack ps, MultiBufferSource buffers, boolean ghost) {
        MusicConfig c = MusicSettings.get();
        if (c.lyricsStyle != MusicConfig.LyricsStyle.NOS_SKY
                && c.enabled
                && c.showLyrics
                && !visible.isEmpty()
                && !(pauseFade < 0.01F)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                if (buffers == null) {
                    buffers = mc.renderBuffers().bufferSource();
                }

                Camera cam = mc.gameRenderer.getMainCamera();
                Vec3 cp = cam.position();
                long t = MediaManager.get().position();
                float speed = Math.max(0.3F, c.animSpeed);
                Themes.Theme th = Themes.current();
                boolean sky = c.lyricsStyle == MusicConfig.LyricsStyle.SKY;
                DisplayMode mode = DisplayMode.NORMAL;
                if (lateForShaders() || ghost) {
                    mode = DisplayMode.SEE_THROUGH;
                }

                ghostNow = ghost;
                float beat = c.beatGlow ? Hud.beat : 0.0F;
                GpuBufferSlice oldFog = null;
                if (sky) {
                    try {
                        oldFog = RenderSystem.getShaderFog();
                        FogRenderer fr =
                                ((GameRendererAccessor) mc.gameRenderer).nosclient$fogRenderer();
                        RenderSystem.setShaderFog(fr.getBuffer(FogMode.NONE));
                    } catch (Throwable ignored) {
                        oldFog = null;
                    }
                }

                for (WorldLyrics.Placed p : visible) {
                    if (!ghost || ghostOnly == null || ghostOnly.contains(p)) {
                        if (!ghost && c.lyricsStyle == MusicConfig.LyricsStyle.FLOATING) {
                            p.coverFade =
                                    Anim.approach(
                                            p.coverFade, overPlayer(p, c) ? 0.35F : 1.0F, 6.0F);
                        }

                        String font = fontOf(p, c);
                        float globalA =
                                (ghost ? 1.0F : p.coverFade)
                                        * pauseFade
                                        * Hud.focusA
                                        * c.textOpacity
                                        * p.occlVis
                                        * (sky ? 1.0F : Anim.easeInOut(p.viewVis))
                                        * (ghost ? 0.3F : 1.0F);
                        if (!(globalA < 0.02F)) {
                            double[] a = anchor(p, c);
                            float em = em(p, c);
                            ps.pushPose();
                            ps.translate(a[0] - cp.x, a[1] - cp.y, a[2] - cp.z);
                            Quaternionf face = facing(p, c);
                            ps.mulPose(face);
                            Vector3f n = face.transform(new Vector3f(0.0F, 0.0F, 1.0F));
                            double vx = cp.x - a[0];
                            double vy = cp.y - a[1];
                            double vz = cp.z - a[2];
                            if (n.x * vx + n.y * vy + n.z * vz < 0.0) {
                                ps.mulPose(Axis.YP.rotationDegrees(180.0F));
                            }

                            ps.mulPose(Axis.ZP.rotationDegrees(-p.tilt));

                            for (int j = 0; j < p.n(); j++) {
                                Lyrics.Word w = p.line.words.get(p.w0 + j);
                                float[] s = wordAnim(p, j, w, t, c, speed);
                                if (s != null) {
                                    float alpha = s[0] * globalA;
                                    float dx = s[1];
                                    float dy = s[2];
                                    float sc = s[3];
                                    double bob =
                                            Math.sin(Anim.seconds() * 1.6 + j * 0.9 + p.seed)
                                                    * (sky ? 0.02 : 0.04);
                                    float size = p.wsize[j] * sc;
                                    ps.pushPose();
                                    ps.translate(
                                            (p.wx0[j] + dx) * em,
                                            -(p.wy0[j] + dy + (float) bob) * em,
                                            0.0F);
                                    if (p.wrot[j] != 0.0F) {
                                        ps.mulPose(Axis.ZP.rotationDegrees(-p.wrot[j]));
                                    }

                                    float k = em * size / Fonts.height(font);
                                    ps.scale(k, -k, k);
                                    boolean sung = t >= w.start && t < w.end + 80L;
                                    float glow =
                                            c.glow
                                                    ? 0.65F + (sung ? 0.35F : 0.0F) + beat * 0.5F
                                                    : 0.0F;
                                    String word = Lyrics.show(w.text);
                                    Matrix4f wm = ps.last().pose();
                                    if (s[4] < 1.0F && !word.isEmpty()) {
                                        String part =
                                                word.substring(
                                                        0,
                                                        Math.max(
                                                                1,
                                                                (int)
                                                                        Math.ceil(
                                                                                word.length()
                                                                                        * s[4])));
                                        Font mf = Minecraft.getInstance().font;
                                        float fh = Fonts.height(font);
                                        float shift =
                                                (Fonts.unitWidth(part, font, mf)
                                                                - Fonts.unitWidth(word, font, mf))
                                                        * fh
                                                        / 2.0F;
                                        wm = new Matrix4f(wm).translate(shift, 0.0F, 0.0F);
                                        word = part;
                                    }

                                    double distance = Math.sqrt(vx * vx + vy * vy + vz * vz);
                                    double raster = k * Proj.focal() * mc.getWindow().getGuiScale()
                                            / Math.max(0.1, distance);
                                    neon3d(wm, word, font, raster, th, alpha, glow, buffers, mode);
                                    ps.popPose();
                                }
                            }

                            ps.popPose();
                        }
                    }
                }

                if (buffers instanceof BufferSource bs) {
                    bs.endBatch();
                }

                if (oldFog != null) {
                    RenderSystem.setShaderFog(oldFog);
                }
            }
        }
    }

    private static void neon3d(
            Matrix4f m,
            String s,
            String font,
            double raster,
            Themes.Theme th,
            float alpha,
            float glow,
            MultiBufferSource buf,
            DisplayMode mode) {
        Font f = Minecraft.getInstance().font;
        layer = 0;
        Component comp = Fonts.rasterText(s, font, raster);
        float h = Fonts.height(font);
        float w = Fonts.unitWidth(s, font, f) * h;
        float x = -w / 2.0F;
        float y = -h / 2.0F + h * Fonts.yShift(font);
        float o = h * 0.055F;
        if (ghostNow) {
            draw(f, comp, x, y, Col.alpha(th.main(), alpha), m, buf, mode);
        } else {
            if (glow > 0.01F && AutoPerf.allow(3)) {
                float pad = h * 0.55F;
                Matrix4f hm = new Matrix4f(m).translate(0.0F, 0.0F, layerStep * layer++);
                Halo.draw(
                        hm,
                        x - pad,
                        y - pad * 0.7F,
                        x + w + pad,
                        y + h + pad * 0.7F,
                        Col.alpha(th.a(), alpha * Math.min(0.5F, 0.22F * glow)),
                        buf);
            }

            int shadow = MusicSettings.get().textShadow;
            boolean clean = MusicSettings.get().textStyle == MusicConfig.TextStyle.CLEAN;
            int custom = MusicSettings.get().shadowColor;
            if (shadow == 1) {
                int sc =
                        custom != 0
                                ? custom | 0xFF000000
                                : (clean ? -15396054 : Col.darken(th.a(), 0.75F));
                draw(f, comp, x + o * 0.6F, y + o * 1.0F, Col.alpha(sc, alpha), m, buf, mode);
            } else if (shadow == 2) {
                int sc = custom != 0 ? custom | 0xFF000000 : -16120302;
                draw(f, comp, x + o * 1.3F, y + o * 2.0F, Col.alpha(sc, alpha), m, buf, mode);
                draw(f, comp, x + o * 0.7F, y + o * 1.1F, Col.alpha(sc, alpha), m, buf, mode);
            }

            if (clean) {
                draw(f, comp, x, y, Col.alpha(Col.lerp(th.main(), -1, 0.2F), alpha), m, buf, mode);
            } else {
                float tcs = MusicSettings.get().twoColorStrength;
                if (tcs > 0.01F) {
                    int tc = MusicSettings.get().twoColorColor;
                    int ca = tc != 0 ? tc | 0xFF000000 : th.a();
                    int cb = tc != 0 ? tc | 0xFF000000 : th.b();
                    float oo = o * (0.35F + 0.65F * tcs);
                    float ta = alpha * Math.min(1.0F, 0.3F + 0.7F * tcs);
                    draw(f, comp, x - oo, y + oo * 0.5F, Col.alpha(ca, ta), m, buf, mode);
                    draw(f, comp, x + oo, y - oo * 0.5F, Col.alpha(cb, ta), m, buf, mode);
                }

                draw(f, comp, x, y, Col.alpha(th.main(), alpha), m, buf, mode);
            }
        }
    }

    private static void draw(
            Font f,
            Component c,
            float x,
            float y,
            int color,
            Matrix4f m,
            MultiBufferSource buf,
            DisplayMode mode) {
        Matrix4f mm = new Matrix4f(m).translate(0.0F, 0.0F, layerStep * layer++);
        if (color >>> 24 >= 26) {
            f.drawInBatch(c, x, y, color, false, mm, buf, mode, 0, 15728880);
        }
    }

    private static float[] wordAnim(
            WorldLyrics.Placed p, int j, Lyrics.Word w, long t, MusicConfig c, float speed) {
        float ein = Anim.progress(t, w.start - 40L, (float) (w.start - 40L) + 300.0F / speed);
        if (ein <= 0.0F) {
            return null;
        }

        float dx = 0.0F;
        float dy = 0.0F;
        float sc = 1.0F;
        float typed = 1.0F;
        float alpha;
        switch (c.enterAnim) {
            case NEON:
                float ms = (float) (t - (w.start - 40L)) * speed;
                alpha = Anim.clamp01(ms / 140.0F);
                sc = 1.0F + 0.22F * (1.0F - Anim.easeOutCubic(Anim.clamp01(ms / 240.0F)));
                typed = Anim.clamp01(ms / 230.0F);
                break;
            case FADE:
                alpha = Anim.easeInOut(ein);
                break;
            case RISE:
                {
                    float e = Anim.easeOutCubic(ein);
                    alpha = e;
                    dy = (1.0F - e) * 0.9F;
                    break;
                }
            default:
                {
                    float e = Anim.easeOutBack(ein);
                    alpha = Anim.easeOutCubic(ein);
                    dy = (1.0F - Anim.easeOutCubic(ein)) * 0.45F;
                    sc = 0.82F + 0.18F * e;
                }
        }

        if (p.exitAt >= 0L) {
            long start = p.exitAt + 55L * j;
            float eo = Anim.progress(t, start, (float) start + 650.0F / speed);
            if (eo >= 1.0F) {
                return null;
            }

            float e = Anim.easeInCubic(eo);
            alpha *= 1.0F - Anim.easeInOut(eo);
            switch (c.exitAnim) {
                case FLOAT_UP:
                    dy -= e * 1.6F;
                    sc *= 1.0F + e * 0.08F;
                    break;
                case DRIFT:
                    dx += e * 1.8F * (rnd(p.seed, 90) > 0.5F ? 1 : -1);
                    dy -= e * 0.35F;
                case FADE:
            }
        }

        if (p.w0 + j == p.line.words.size() - 1 && t >= w.start) {
            long held = Math.max(w.end, p.line.end) - w.start;
            if (held >= 650L && t < w.start + held + 300L) {
                float ph = (float) ((t - w.start) % 860L) / 860.0F;
                float beat =
                        (float)
                                (Math.exp(-Math.pow((ph - 0.08F) / 0.05F, 2.0))
                                        + 0.55 * Math.exp(-Math.pow((ph - 0.27F) / 0.05F, 2.0)));
                float fadeIn = Anim.clamp01((float) (t - w.start) / 200.0F);
                sc *= 1.0F + 0.085F * beat * fadeIn;
            }
        }

        return new float[] {alpha, dx, dy, sc, typed};
    }

    private static double clearance(float yaw, float pitch, double dist) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            double[] d = Proj.dir(yaw, pitch);
            Vec3 from = new Vec3(Proj.cx, Proj.cy, Proj.cz);
            Vec3 to = new Vec3(Proj.cx + d[0] * dist, Proj.cy + d[1] * dist, Proj.cz + d[2] * dist);
            HitResult hit =
                    mc.level.clip(new ClipContext(from, to, Block.VISUAL, Fluid.NONE, mc.player));
            return hit.getType() != Type.BLOCK ? dist : hit.getLocation().distanceTo(from);
        } else {
            return dist;
        }
    }

    private static WorldLyrics.Placed findIndex(int index) {
        for (WorldLyrics.Placed o : visible) {
            if (o.line.index == index) {
                return o;
            }
        }

        return null;
    }

    private static double skyDist() {
        Minecraft mc = Minecraft.getInstance();
        double far = mc.gameRenderer.getDepthFar() * 0.85;
        return Math.max(20.0, Math.min(far, MusicSettings.get().skyDistance));
    }

    private static double cloudY() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null
                && mc.options.getCloudsType() != CloudStatus.OFF
                && mc.level.dimensionType().hasSkyLight()) {
            try {
                Float h =
                        (Float)
                                mc.level
                                        .environmentAttributes()
                                        .getValue(
                                                EnvironmentAttributes.CLOUD_HEIGHT,
                                                new Vec3(Proj.cx, Proj.cy, Proj.cz));
                return h == null ? Double.NaN : h.floatValue();
            } catch (Throwable t) {
                return 192.33;
            }
        } else {
            return Double.NaN;
        }
    }

    private static int blocked(float yaw, float pitch, double dist, float halfWidthDeg) {
        int n = 0;
        if (clearance(yaw, pitch, dist) < dist - 0.3) {
            n++;
        }

        if (clearance(yaw - halfWidthDeg, pitch, dist) < dist - 0.3) {
            n++;
        }

        if (clearance(yaw + halfWidthDeg, pitch, dist) < dist - 0.3) {
            n++;
        }

        return n;
    }

    private static boolean inClouds(float pitch, double dist, double cloud) {
        if (Double.isNaN(cloud)) {
            return false;
        }

        double y = Proj.cy + Proj.dir(0.0F, pitch)[1] * dist;
        return Math.abs(y - cloud) < 6.0;
    }

    static float wrap(float deg) {
        deg %= 360.0F;
        if (deg > 180.0F) {
            deg -= 360.0F;
        }

        if (deg < -180.0F) {
            deg += 360.0F;
        }

        return deg;
    }

    private static boolean quiet(Lyrics lyrics, Lyrics.Line cur, long t) {
        if (cur != null && cur.index >= 0 && t <= cur.end + 1200L) {
            return false;
        }

        if (lyrics != null) {
            for (Lyrics.Line l : lyrics.lines) {
                if (l.start > t) {
                    return l.start - t > 2600L;
                }
            }
        }

        return true;
    }

    private static Lyrics.Line noteLine(long t) {
        long slot = t / 1700L;
        if (slot != noteSlot || noteSong != songSeq || noteLine == null) {
            noteSlot = slot;
            noteSong = songSeq;
            long st = slot * 1700L;
            noteLine =
                    Lyrics.Line.notes(
                            -1 - (int) (slot % 100000L),
                            NOTES[(int) (slot % NOTES.length)],
                            st,
                            st + 2900L);
        }

        return noteLine;
    }

    private static String fontOf(WorldLyrics.Placed p, MusicConfig c) {
        return p.line.index < 0 ? "minecraft" : c.font;
    }

    private static WorldLyrics.Placed find(Lyrics.Line l, int w0) {
        for (WorldLyrics.Placed p : visible) {
            if (p.line == l && p.w0 == w0) {
                return p;
            }
        }

        return null;
    }

    private static float rnd(long seed, int k) {
        return Anim.hash01(seed * 31L + k);
    }

    private static boolean overPlayer(WorldLyrics.Placed p, MusicConfig c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            boolean third = !mc.options.getCameraType().isFirstPerson();
            double[] a = anchor(p, c);
            double ex = a[0] - Proj.cx;
            double ey = a[1] - Proj.cy;
            double ez = a[2] - Proj.cz;
            double len = Math.sqrt(ex * ex + ey * ey + ez * ez);
            if (len < 0.001) {
                return false;
            }

            float ly = (float) Math.toDegrees(Math.atan2(-ex, ez));
            float lp = (float) (-Math.toDegrees(Math.asin(ey / len)));
            float k = (float) (p.dist / len);

            for (Player pl : mc.level.players()) {
                if (pl != mc.player || third) {
                    AABB bb = pl.getBoundingBox();
                    Vec3 d = bb.getCenter().subtract(Proj.cx, Proj.cy, Proj.cz);
                    double dist = d.length();
                    if (!(dist > 48.0) && !(dist < 0.3) && !(dist < len)) {
                        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                        float pitch = (float) (-Math.toDegrees(Math.asin(d.y / dist)));
                        float hw = (float) Math.toDegrees(Math.atan(bb.getXsize() * 0.5 / dist));
                        float hh = (float) Math.toDegrees(Math.atan(bb.getYsize() * 0.5 / dist));
                        if (Math.abs(wrap(ly - yaw)) < hw + p.angW * k
                                && Math.abs(lp - pitch) < hh + p.angH * k) {
                            return true;
                        }
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private static Set<WorldLyrics.Placed> playerCovers() {
        Set<WorldLyrics.Placed> out = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            MusicConfig c = MusicSettings.get();
            Vec3 eye = new Vec3(Proj.cx, Proj.cy, Proj.cz);
            boolean third = !mc.options.getCameraType().isFirstPerson();

            for (Player pl : mc.level.players()) {
                if (pl != mc.player || third) {
                    AABB bb = pl.getBoundingBox();
                    Vec3 d = bb.getCenter().subtract(eye);
                    double dist = d.length();
                    if (!(dist > 48.0) && !(dist < 0.3)) {
                        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                        float pitch = (float) (-Math.toDegrees(Math.asin(d.y / dist)));
                        float hw = (float) Math.toDegrees(Math.atan(bb.getXsize() * 0.5 / dist));
                        float hh = (float) Math.toDegrees(Math.atan(bb.getYsize() * 0.5 / dist));

                        for (WorldLyrics.Placed p : visible) {
                            double[] a = anchor(p, c);
                            double ex = a[0] - Proj.cx;
                            double ey = a[1] - Proj.cy;
                            double ez = a[2] - Proj.cz;
                            double len = Math.sqrt(ex * ex + ey * ey + ez * ez);
                            if (!(len < dist)) {
                                float ly = (float) Math.toDegrees(Math.atan2(-ex, ez));
                                float lp = (float) (-Math.toDegrees(Math.asin(ey / len)));
                                float lw = (float) Math.toDegrees(Math.atan(1.6 / len));
                                float lh = (float) Math.toDegrees(Math.atan(0.5 / len));
                                if (Math.abs(wrap(ly - yaw)) < hw + lw
                                        && Math.abs(lp - pitch) < hh + lh) {
                                    out.add(p);
                                }
                            }
                        }
                    }
                }
            }

            return out;
        } else {
            return out;
        }
    }

    private static List<float[]> avoidBoxes() {
        List<float[]> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            Vec3 eye = new Vec3(Proj.cx, Proj.cy, Proj.cz);
            List<Entity> list = new ArrayList<>();

            for (Player pl : mc.level.players()) {
                if (pl != mc.player) {
                    list.add(pl);
                }
            }

            if (mc.crosshairPickEntity instanceof LivingEntity le && !list.contains(le)) {
                list.add(le);
            }

            for (Entity e : list) {
                AABB bb = e.getBoundingBox();
                Vec3 d = bb.getCenter().subtract(eye);
                double dist = d.length();
                if (!(dist > 40.0) && !(dist < 0.3)) {
                    float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                    float pitch = (float) (-Math.toDegrees(Math.asin(d.y / dist)));
                    float hw =
                            (float) Math.toDegrees(Math.atan((bb.getXsize() * 0.5 + 0.9) / dist));
                    float hh =
                            (float) Math.toDegrees(Math.atan((bb.getYsize() * 0.5 + 1.0) / dist));
                    out.add(new float[] {yaw, pitch, hw, hh});
                }
            }

            return out;
        } else {
            return out;
        }
    }

    private static void place(WorldLyrics.Placed p, MusicConfig c) {
        long s = p.seed;
        boolean random = c.randomPosition;

        boolean skyStyle = c.lyricsStyle == MusicConfig.LyricsStyle.SKY;
        boolean shaderMode =
                c.lyricsStyle == MusicConfig.LyricsStyle.FLOATING && IrisCompat.shadersOn();
        int tries =
                random
                        ? (c.lyricsStyle == MusicConfig.LyricsStyle.FLOATING ? 40 : 12)
                        : (!skyStyle && !shaderMode ? 1 : 8);
        double cloud = cloudY();
        float bestScore = -1.0E9F;
        float bYaw = 0.0F;
        float bPitch = 0.0F;
        float bSize = 6.0F;
        float bPY = 0.0F;
        float bPP = 0.0F;
        double bDist = 6.0;
        List<float[]> avoid =
                c.lyricsStyle == MusicConfig.LyricsStyle.FLOATING ? avoidBoxes() : List.of();

        for (int k = 0; k < tries; k++) {
            float r1 = random ? rnd(s, 100 + k * 3) * 2.0F - 1.0F : 0.0F;
            float r2 = random ? rnd(s, 101 + k * 3) : 0.5F;
            float r3 = random ? rnd(s, 102 + k * 3) : 0.5F;
            float yaw = 0.0F;
            float pitch = 0.0F;
            float pY = 0.0F;
            float pP = 0.0F;
            double dist = 8.0;
            float tSize = 6.0F;
            float score = 1000.0F;
            switch (c.lyricsStyle) {
                case SKY:
                    if (c.skyWords) {
                        dist = skyDist();
                        yaw = Proj.yaw + r1 * 36.0F;
                        pitch =
                                Math.max(
                                        -55.0F,
                                        Math.min(
                                                -4.0F,
                                                Proj.pitch * 0.5F - 16.0F + (r2 - 0.5F) * 30.0F));
                        int b = blocked(yaw, pitch, dist, 6.0F);
                        score -= b * 450;
                        if (inClouds(pitch, dist, cloud)) {
                            score -= 500.0F;
                        }
                    } else {
                        dist = skyDist();
                        float emDeg = (float) Math.toDegrees(Math.atan(em(p, c) / dist));
                        float h = emDeg * 2.0F;
                        float v = emDeg * 1.9F;
                        int side = p.line.index >= 0 ? (p.line.index % 2 == 0 ? -1 : 1) : 0;
                        WorldLyrics.Placed mate = side > 0 ? findIndex(p.line.index - 1) : null;
                        if (mate != null) {
                            if (k > 0) {
                                continue;
                            }

                            pY = mate.pairYaw;
                            pP = mate.pairPitch;
                            score += 5000.0F;
                        } else {
                            float ry = k == 0 ? 0.0F : r1;
                            float rp = k == 0 ? 0.5F : r2;
                            if (k == 0) {
                                score += 120.0F;
                            }

                            pY = Proj.yaw + ry * 14.0F;
                            pP =
                                    Math.min(
                                            Proj.pitch
                                                    - (8.0F + (rp - 0.5F) * 6.0F)
                                                    - (k >= tries / 2 ? 4.0F : 0.0F),
                                            -3.0F - v);
                        }

                        yaw = pY + side * h;
                        pitch = pP + side * v;
                        float absYaw = yaw;
                        float absPitch = pitch;
                        int b = blocked(absYaw, absPitch, dist, 8.0F);
                        score -= b * 450;
                        if (inClouds(absPitch, dist, cloud)) {
                            score -= 500.0F;
                        }
                    }
                    break;
                case FLOATING:
                    if (shaderMode && !random) {
                        r1 = k == 0 ? 0.0F : rnd(s, 100 + k * 3) * 2.0F - 1.0F;
                        r2 = k == 0 ? 0.5F : rnd(s, 101 + k * 3);
                        r3 = k == 0 ? 0.5F : rnd(s, 102 + k * 3);
                        if (k == 0) {
                            score += 120.0F;
                        }
                    }

                    if (!c.followView && !c.viewLock) {
                        yaw = Proj.yaw + (r1 < 0.0F ? -1.0F : 1.0F) * (8.0F + Math.abs(r1) * 26.0F);
                    } else {
                        yaw = (c.viewLock ? 0.0F : Proj.yaw) + r1 * 13.0F;
                    }

                    float rp = (r2 - 0.5F) * 26.0F;
                    pitch =
                            Math.max(
                                    -40.0F,
                                    Math.min(
                                            28.0F,
                                            (c.viewLock ? 0.0F : Proj.pitch * 0.6F) + rp - 9.0F));
                    dist =
                            (shaderMode ? 4.5F + r3 * 2.5F : 5.0F + r3 * 2.5F)
                                    * Math.max(0.4F, Math.min(2.0F, c.lyricsDistance));
                    float absYaw = c.viewLock ? Proj.yaw + yaw : yaw;
                    float absPitch = c.viewLock ? Proj.pitch + pitch : pitch;
                    float cy = Math.abs(wrap(absYaw - Proj.yaw));
                    float cp = Math.abs(absPitch - Proj.pitch);
                    if (cy < 4.0F && cp < 3.0F) {
                        score -= 250.0F;
                    }

                    for (float[] b : avoid) {
                        float dy = Math.abs(wrap(absYaw - b[0])) - b[2] - 12.0F;
                        float dp = Math.abs(absPitch - b[1]) - b[3] - 6.0F;
                        if (dy < 0.0F && dp < 0.0F) {
                            score -= 4000.0F;
                        }
                    }

                    if (!avoid.isEmpty() && cy < 14.0F && cp < 10.0F) {
                        score -= 1500.0F;
                    }

                    float hw = (float) Math.toDegrees(Math.atan(2.8 / dist));
                    int ends = blocked(absYaw, absPitch, dist, hw);
                    score -= ends * 800;
                    float hh = (float) Math.toDegrees(Math.atan(0.7 / dist));
                    int more =
                            blocked(absYaw, absPitch - hh, dist, hw)
                                    + blocked(absYaw, absPitch + hh, dist, hw);
                    score -= (ends + more) * 700;
                    if (inClouds(absPitch, dist, cloud)) {
                        score -= 500.0F;
                    }

                    tSize = (float) (dist / Math.max(0.4F, Math.min(2.0F, c.lyricsDistance)));
                    double free = clearance(absYaw, absPitch, dist + 4.0);
                    if (free < dist + 4.0) {
                        score -= 800.0F;
                        if (free < 2.2) {
                            score -= 700.0F;
                        }

                        dist = Math.max(1.8, free - 0.8);
                        score -= (float) (8.0 - free) * 10.0F;
                    }
                    break;
                default:
            }

            float near = 1000.0F;

            for (WorldLyrics.Placed o : visible) {
                float oy = o.yaw;
                float op = o.pitch;
                if (!c.viewLock) {
                    double ex = o.ax - Proj.cx;
                    double ey = o.ay - Proj.cy;
                    double ez = o.az - Proj.cz;
                    double len = Math.sqrt(ex * ex + ey * ey + ez * ez);
                    if (len > 0.001) {
                        oy = (float) Math.toDegrees(Math.atan2(-ex, ez));
                        op = (float) (-Math.toDegrees(Math.asin(ey / len)));
                    }
                }

                float dy = Math.abs(wrap(oy - yaw));
                float dp = Math.abs(op - pitch) * 2.2F;
                near = Math.min(near, (float) Math.sqrt(dy * dy + dp * dp));
            }

            if ((c.lyricsStyle == MusicConfig.LyricsStyle.FLOATING || skyWords(c)) && !c.viewLock) {
                float emB =
                        skyWords(c)
                                ? em(p, c)
                                : (float) (0.0765 * Math.max(2.5, tSize)) * c.lyricsScale;
                int chars = 0;

                for (int j = p.w0; j < p.w1; j++) {
                    chars += p.line.words.get(j).text.length() + 1;
                }

                float cw = (float) Math.toDegrees(Math.atan(chars * 0.55F * emB / 2.0F / dist));
                float ch = (float) Math.toDegrees(Math.atan(0.8F * emB / dist));

                for (WorldLyrics.Placed o : visible) {
                    double ex = o.ax - Proj.cx;
                    double ey = o.ay - Proj.cy;
                    double ez = o.az - Proj.cz;
                    double len = Math.sqrt(ex * ex + ey * ey + ez * ez);
                    if (!(len < 0.001)) {
                        float ok = (float) (o.dist / len);
                        float oy = (float) Math.toDegrees(Math.atan2(-ex, ez));
                        float op = (float) (-Math.toDegrees(Math.asin(ey / len)));
                        if (Math.abs(wrap(oy - yaw)) < cw + o.angW * ok + 3.0F
                                && Math.abs(op - pitch) < ch + o.angH * ok + 2.5F) {
                            score -= 12000.0F;
                        } else if (Math.abs(wrap(oy - yaw)) < cw + o.angW * ok + 6.0F
                                && Math.abs(op - pitch) < ch + o.angH * ok + 4.0F) {
                            score -= 600.0F;
                        }
                    }
                }
            }

            if (near < 22.0F) {
                score -= (22.0F - near) * 40.0F;
            }

            score += Math.min(near, 60.0F) * 4.0F;
            if (score > bestScore) {
                bestScore = score;
                bYaw = yaw;
                bPitch = pitch;
                bDist = dist;
                bSize = tSize;
                bPY = pY;
                bPP = pP;
            }
        }

        p.yaw =
                c.lyricsStyle == MusicConfig.LyricsStyle.SKY && c.viewLock
                        ? wrap(bYaw - Proj.yaw)
                        : bYaw;
        p.pitch =
                c.lyricsStyle == MusicConfig.LyricsStyle.SKY && c.viewLock
                        ? bPitch - Proj.pitch
                        : bPitch;
        p.dist = bDist;
        p.sizeDist = bSize;
        p.pairYaw = bPY;
        p.pairPitch = bPP;
        if ((c.lyricsStyle == MusicConfig.LyricsStyle.FLOATING
                        || c.lyricsStyle == MusicConfig.LyricsStyle.SKY)
                && !c.viewLock) {
            double[] d = Proj.dir(bYaw, bPitch);
            p.ax = Proj.cx + d[0] * bDist;
            p.ay = Proj.cy + d[1] * bDist;
            p.az = Proj.cz + d[2] * bDist;
        }

        if (c.lyricsStyle == MusicConfig.LyricsStyle.FLOATING && !c.viewLock) {
            p.ay = p.ay + c.floatHeight;
        }

        boolean sky = c.lyricsStyle == MusicConfig.LyricsStyle.SKY;
        p.turn =
                sky && c.arcLayout
                        ? (rnd(s, 5) > 0.5F ? 1.0F : -1.0F)
                                * (c.skyWords ? 8.0F + rnd(s, 6) * 16.0F : 3.0F + rnd(s, 6) * 4.0F)
                        : 0.0F;
        p.tilt =
                c.arcLayout
                        ? (sky
                                ? (c.skyWords
                                        ? -22.0F + rnd(s, 4) * 44.0F
                                        : -1.5F + rnd(s, 4) * 3.0F)
                                : -10.0F + rnd(s, 4) * 14.0F)
                        : 0.0F;
        List<Lyrics.Word> words = p.line.words;
        int n = p.n();
        String font = fontOf(p, c);
        float space = Gfx.textWidth(" ", font, 1.0F);
        float[] widths = new float[n];

        for (int j = 0; j < n; j++) {
            int i = p.w0 + j;
            float size = 1.0F;
            if (c.varySizes) {
                float h = rnd(s, 10 + i);
                size = h > 0.82F ? 1.4F : (h > 0.55F ? 1.12F : (h < 0.2F ? 0.82F : 0.96F));
                if (j == 0 && words.get(i).text.length() <= 2 && !sky) {
                    size = 1.35F;
                }
            }

            p.wsize[j] = size;
            widths[j] = (Gfx.textWidth(Lyrics.show(words.get(i).text), font, 1.0F) + 0.08F) * size;
            p.wrot[j] = c.arcLayout && !sky ? (rnd(s, 40 + i) - 0.5F) * 8.0F : 0.0F;
        }

        float spaceEm = Math.max(0.26F, space);
        float maxRow = sky ? 18.0F : (chunked(c) ? 99.0F : 9.5F);
        List<int[]> rows = new ArrayList<>();

        for (int pass = 0; pass < 20; pass++) {
            rows.clear();
            int start = 0;
            float rowW = 0.0F;

            for (int j = 0; j < n; j++) {
                float add = widths[j] + (j > start ? spaceEm : 0.0F);
                if (j > start && rowW + add > maxRow) {
                    rows.add(new int[] {start, j});
                    start = j;
                    rowW = widths[j];
                } else {
                    rowW += add;
                }
            }

            rows.add(new int[] {start, n});
            if (!sky || rows.size() <= 3) {
                break;
            }

            maxRow *= 1.25F;
        }

        float widest = 0.0F;

        for (int[] rw : rows) {
            float w = 0.0F;

            for (int j = rw[0]; j < rw[1]; j++) {
                w += widths[j] + (j > rw[0] ? spaceEm : 0.0F);
            }

            widest = Math.max(widest, w);
        }

        float rowGap = sky ? 1.4F : 1.5F;
        float totalH = (rows.size() - 1) * rowGap;

        for (int r = 0; r < rows.size(); r++) {
            int a = rows.get(r)[0];
            int b = rows.get(r)[1];
            float w = 0.0F;

            for (int j = a; j < b; j++) {
                w += widths[j] + (j > a ? spaceEm : 0.0F);
            }

            float shift =
                    sky
                            ? 0.0F
                            : (rows.size() > 1
                                    ? (r == 0 ? -1.0F : 1.0F)
                                            * (sky ? 0.9F : 0.6F)
                                            * (random ? 0.5F + rnd(s, 60 + r) * 0.5F : 0.6F)
                                    : 0.0F);
            float x = sky ? -widest / 2.0F : -w / 2.0F + shift;

            for (int j = a; j < b; j++) {
                float cxw = x + widths[j] / 2.0F;
                p.wx0[j] = cxw;
                float arc = c.arcLayout ? (sky ? 0.008F : 0.03F) * (cxw * cxw) : 0.0F;
                p.wy0[j] =
                        -totalH / 2.0F
                                + r * rowGap
                                + (sky ? -arc : arc)
                                - (p.wsize[j] - 1.0F) * 0.35F;
                x += widths[j] + spaceEm;
            }
        }

        p.rowsN = rows.size();
        float emA = em(p, c);
        p.angW = (float) Math.toDegrees(Math.atan(widest * 0.5F * emA / Math.max(0.5, p.dist)));
        p.angH =
                (float) Math.toDegrees(Math.atan(rows.size() * 0.7F * emA / Math.max(0.5, p.dist)));
        if (sky && !c.viewLock && !c.skyWords && p.line.index >= 0 && p.line.index % 2 == 1) {
            WorldLyrics.Placed mate = findIndex(p.line.index - 1);
            if (mate != null && mate.rowsN + p.rowsN > 2) {
                float emDeg = (float) Math.toDegrees(Math.atan(em(p, c) / p.dist));
                p.pitch = p.pitch + (mate.rowsN - 1 + p.rowsN - 1) * rowGap * 0.5F * emDeg;
                double[] d = Proj.dir(p.yaw, p.pitch);
                p.ax = Proj.cx + d[0] * p.dist;
                p.ay = Proj.cy + d[1] * p.dist;
                p.az = Proj.cz + d[2] * p.dist;
            }
        }

        if (Fonts.isRtl(p.line.text)) {
            for (int j = 0; j < n; j++) {
                p.wx0[j] = -p.wx0[j];
            }
        }

        if (c.lyricsStyle == MusicConfig.LyricsStyle.FLOATING && !c.viewLock) {
            keepOutOfBlocks(p, c, widths);
        }
    }

    private static void keepOutOfBlocks(WorldLyrics.Placed p, MusicConfig c, float[] widths) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            float em = em(p, c);
            Quaternionf q = facing(p, c).mul(Axis.ZP.rotationDegrees(-p.tilt));
            double ox = p.ax;
            double oy = p.ay;
            double oz = p.az;
            if (wordsInBlocks(mc, p, q, em, widths)) {
                double ex = ox - Proj.cx;
                double ey = oy - Proj.cy;
                double ez = oz - Proj.cz;
                double len = Math.sqrt(ex * ex + ey * ey + ez * ez);

                for (double d = len - 0.4; d >= 1.8; d -= 0.4) {
                    double k = d / len;
                    p.ax = Proj.cx + ex * k;
                    p.ay = Proj.cy + ey * k;
                    p.az = Proj.cz + ez * k;
                    if (!wordsInBlocks(mc, p, q, em, widths)) {
                        p.dist = d;
                        return;
                    }
                }

                for (double up = 0.5; up <= 3.01; up += 0.5) {
                    p.ax = ox;
                    p.ay = oy + up;
                    p.az = oz;
                    if (!wordsInBlocks(mc, p, q, em, widths)) {
                        return;
                    }
                }

                p.ax = ox;
                p.ay = oy;
                p.az = oz;
            }
        }
    }

    private static boolean wordsInBlocks(
            Minecraft mc, WorldLyrics.Placed p, Quaternionf q, float em, float[] widths) {
        Vector3f v = new Vector3f();
        MutableBlockPos bp = new MutableBlockPos();

        for (int j = 0; j < p.n(); j++) {
            float hw = widths[j] * em * 0.5F;
            float hh = 0.42F * p.wsize[j] * em;
            float cx = p.wx0[j] * em;
            float cy = -p.wy0[j] * em;
            int nx = Math.max(3, (int) Math.ceil(hw * 2.0F / 0.2F) + 1);
            int ny = Math.max(2, (int) Math.ceil(hh * 2.0F / 0.2F) + 1);

            for (int ix = 0; ix < nx; ix++) {
                for (int iy = 0; iy < ny; iy++) {
                    v.set(
                            cx - hw + 2.0F * hw * ix / (nx - 1),
                            cy - hh + 2.0F * hh * iy / (ny - 1),
                            0.0F);
                    q.transform(v);
                    double wx = p.ax + v.x;
                    double wy = p.ay + v.y;
                    double wz = p.az + v.z;
                    bp.set(Math.floor(wx), Math.floor(wy), Math.floor(wz));
                    BlockState st = mc.level.getBlockState(bp);
                    if (!st.isAir()) {
                        VoxelShape shape = st.getShape(mc.level, bp);
                        if (!shape.isEmpty()) {
                            double lx = wx - bp.getX();
                            double ly = wy - bp.getY();
                            double lz = wz - bp.getZ();

                            for (AABB box : shape.toAabbs()) {
                                if (lx >= box.minX - 0.05
                                        && lx <= box.maxX + 0.05
                                        && ly >= box.minY - 0.05
                                        && ly <= box.maxY + 0.05
                                        && lz >= box.minZ - 0.05
                                        && lz <= box.maxZ + 0.05) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    private static final class Placed {
        final Lyrics.Line line;
        final int w0;
        final int w1;
        final long seed;
        float yaw;
        float pitch;
        double dist = 6.0;
        double ax;
        double ay;
        double az;
        float turn;
        float tilt;
        float pairYaw;
        float pairPitch;
        int rowsN = 1;
        float coverFade = 1.0F;
        float angW = 6.0F;
        float angH = 2.0F;
        float sizeDist = 6.0F;
        final float[] wx0;
        final float[] wy0;
        final float[] wsize;
        final float[] wrot;
        long exitAt = -1L;
        float occl = 1.0F;
        float occlVis = 1.0F;
        float viewVis = 0.0F;
        boolean lookedAway;
        int occlTick;

        Placed(Lyrics.Line line, int w0, int w1, long seed) {
            this.line = line;
            this.w0 = w0;
            this.w1 = w1;
            this.seed = seed;
            int n = w1 - w0;
            this.wx0 = new float[n];
            this.wy0 = new float[n];
            this.wsize = new float[n];
            this.wrot = new float[n];
        }

        int n() {
            return this.w1 - this.w0;
        }

        long lastEnd() {
            return this.line.words.get(this.w1 - 1).end;
        }
    }
}
