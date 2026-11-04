package dev.jun.nosclient.render;

import dev.jun.nosclient.mixin.ClientPacketListenerAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public final class LivePing {
    private static volatile long lastMs = -1L;
    private static volatile long lastAt;
    private static float shown = -1.0F;
    private static long sentAt;

    private LivePing() {}

    public static void record(long ms) {
        if (ms >= 0L && ms <= 10000L) {
            lastMs = ms;
            lastAt = System.currentTimeMillis();
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.getConnection() != null && mc.player != null) {
            long now = System.currentTimeMillis();
            if (now - sentAt >= 1000L) {
                sentAt = now;

                try {
                    ((ClientPacketListenerAccessor) mc.getConnection())
                            .nosclient$pingMonitor()
                            .tick();
                } catch (Throwable var4) {
                }
            }
        } else {
            shown = -1.0F;
        }
    }

    public static int get(Minecraft mc) {
        long now = System.currentTimeMillis();
        int target;
        if (lastMs >= 0L && now - lastAt < 5000L) {
            target = (int) lastMs;
        } else {
            PlayerInfo info =
                    mc.getConnection() != null && mc.player != null
                            ? mc.getConnection().getPlayerInfo(mc.player.getUUID())
                            : null;
            target = info == null ? 0 : info.getLatency();
        }

        if (!(shown < 0.0F) && !(Math.abs(target - shown) > 150.0F)) {
            shown = shown + (target - shown) * 0.08F;
        } else {
            shown = target;
        }

        return Math.round(shown);
    }
}
