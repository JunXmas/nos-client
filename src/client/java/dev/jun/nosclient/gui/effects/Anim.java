package dev.jun.nosclient.gui.effects;

import java.util.HashMap;
import java.util.Map;

public final class Anim {
    private double value;
    private double target;
    private final double speed;
    private long last = System.nanoTime();

    public Anim(double start, double speed) {
        this.value = start;
        this.target = start;
        this.speed = speed;
    }

    public Anim target(double t) {
        this.target = t;
        return this;
    }

    public float get() {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - this.last) / 1.0E9);
        this.last = now;
        this.value = this.value + (this.target - this.value) * (1.0 - Math.exp(-this.speed * dt));
        if (Math.abs(this.target - this.value) < 1.0E-4) {
            this.value = this.target;
        }

        return (float) this.value;
    }

    public void snap(double v) {
        this.value = v;
        this.target = v;
    }

    public double target() {
        return this.target;
    }

    public static float outCubic(float t) {
        t = clamp(t);
        float f = 1.0F - t;
        return 1.0F - f * f * f;
    }

    public static float outBack(float t) {
        t = clamp(t);
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(t - 1.0F, 3.0) + c1 * (float) Math.pow(t - 1.0F, 2.0);
    }

    public static float outElastic(float t) {
        t = clamp(t);
        return t != 0.0F && t != 1.0F
                ? (float)
                        (Math.pow(2.0, -10.0F * t)
                                        * Math.sin((t * 10.0F - 0.75) * (Math.PI * 2.0 / 3.0))
                                + 1.0)
                : t;
    }

    public static float clamp(float t) {
        return Math.max(0.0F, Math.min(1.0F, t));
    }

    public static float since(long startMs, long durationMs) {
        return clamp((float) (System.currentTimeMillis() - startMs) / (float) durationMs);
    }

    public static float pulse(double periodMs) {
        return (float)
                (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / periodMs * Math.PI * 2.0));
    }

    public static final class Pool {
        private final Map<String, Anim> map = new HashMap<>();
        private final double speed;

        public Pool(double speed) {
            this.speed = speed;
        }

        public float to(String id, double target) {
            return this.map
                    .computeIfAbsent(id, k -> new Anim(target, this.speed))
                    .target(target)
                    .get();
        }

        public float toFrom(String id, double start, double target) {
            return this.map
                    .computeIfAbsent(id, k -> new Anim(start, this.speed))
                    .target(target)
                    .get();
        }

        public void reset(String prefix) {
            this.map.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }
}
