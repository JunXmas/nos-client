package dev.jun.nosclient.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Cps {
    private final long[] t = new long[64];
    private int head;
    private int size;

    public synchronized void click(long nowNanos) {
        this.t[this.head] = nowNanos;
        this.head = (this.head + 1) % this.t.length;
        if (this.size < this.t.length) {
            this.size++;
        }
    }

    public synchronized int count(long nowNanos) {
        while (this.size > 0 && nowNanos - this.t[(this.head - this.size + this.t.length) % this.t.length] > 1000000000L) {
            this.size--;
        }

        return this.size;
    }

    public static void main(String[] a) {
        Cps c = new Cps();
        long s = 1000000000L;

        for (int i = 0; i < 10; i++) {
            c.click(i * s / 10L);
        }

        check(c.count(s - 1L) == 10, "10 cú trong 1 giây");
        check(c.count(s + s / 20L) == 9, "cú đầu (t=0) quá 1 giây thì rơi");
        check(c.count(3L * s) == 0, "sau 2 giây không bấm là 0");

        for (int i = 0; i < 100; i++) {
            c.click(5L * s + i);
        }

        check(c.count(5L * s + 200L) == 64, "đệm đầy thì chặn ở 64, không tràn");
        System.out.println("CPS OK");
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
