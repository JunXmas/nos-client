package dev.jun.nosclient.media;

/**
 * Toán chuyển động của lời nhạc trên trời — thuần, không đụng Minecraft để kiểm được ngoài game
 * ({@code checks/LyricMotionCheck.java}). Mọi hạt đều là hàm của thời gian + seed, không giữ trạng thái:
 * đổi bài, tua, mất khung đều không để lại hạt mồ côi.
 */
public final class LyricMotion {
    public static final int CONSTELLATION = 0;
    public static final int FADE = 1;
    public static final int NONE = 2;
    static final float OMEGA = 8.5F;
    static final float ZETA = 0.52F;
    /** Lúc lò xo chạm đích lần đầu — chữ "đáp" xuống, loé sáng. */
    public static final float ARRIVE = arrival(OMEGA, ZETA);

    private LyricMotion() {
    }

    public record Pose(float x, float y, float rot, float scale, float alpha, float lit, float flash, float trail) {
    }

    /** Số 0..1 ổn định theo (seed, i, salt) — SplitMix64. */
    public static float hash(long seed, int i, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L + salt * 0x94D049BB133111EBL;
        h = (h ^ h >>> 31) * 0x7FB5D329728EA185L;
        h = (h ^ h >>> 27) * 0x81DADEF4BC2DD44DL;
        h ^= h >>> 33;
        return (h >>> 40) / (float)(1L << 24);
    }

    /** Lò xo dưới tắt dần đi từ 0 tới 1: vượt nhẹ rồi lắng. */
    public static float spring(float t, float omega, float zeta) {
        if (t <= 0.0F) {
            return 0.0F;
        }

        double wd = omega * Math.sqrt(1.0 - zeta * zeta);
        return (float)(1.0 - Math.exp(-zeta * omega * t) * (Math.cos(wd * t) + zeta * omega / wd * Math.sin(wd * t)));
    }

    static float arrival(float omega, float zeta) {
        double wd = omega * Math.sqrt(1.0 - zeta * zeta);
        return (float)((Math.PI - Math.atan2(wd, zeta * omega)) / wd);
    }

    public static float smooth(float t) {
        t = Math.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    static float bump(float x, float center, float width) {
        float d = (x - center) / width;
        return (float)Math.exp(-d * d);
    }

    /** Giãn giữa hai chữ liền nhau khi bay vào; câu dài thì dồn lại để cả câu vào trong ~0.7 s. */
    static float stagger(int n) {
        return Math.min(0.05F, 0.7F / Math.max(1, n));
    }

    /** Giây (tính theo nhạc) mà chữ thứ i bắt đầu sáng — quét karaoke qua 80% thời lượng dòng. */
    public static float litAt(int i, int n, float lineSeconds) {
        return 0.12F + Math.clamp(lineSeconds, 1.2F, 9.0F) * 0.8F * i / Math.max(1, n);
    }

    /** Thời gian dòng cũ còn phải giữ để bụi sao tan hết. */
    public static float lifetime(int n, float speed) {
        return 0.12F + 0.008F * n + 0.1F + 1.9F / speed;
    }

    /**
     * Neo dòng mới theo hướng nhìn, nâng lên khỏi chân trời. Lệch ít so với dòng trước thì chỉ bám theo
     * một nửa — đứng yên nghe nhạc thì lời không nhảy lung tung. Trả {hướng độ 0..360, độ cao độ}.
     */
    public static float[] anchor(float yRot, float xRot, float prevBearing, float prevElevation, boolean hasPrev) {
        float bearing = (yRot % 360.0F + 360.0F) % 360.0F;
        float elevation = Math.clamp(-xRot + 14.0F, 20.0F, 62.0F);
        if (hasPrev) {
            float d = ((bearing - prevBearing) % 360.0F + 540.0F) % 360.0F - 180.0F;
            if (Math.abs(d) < 28.0F) {
                bearing = ((prevBearing + d * 0.55F) % 360.0F + 360.0F) % 360.0F;
            }

            float de = elevation - prevElevation;
            if (Math.abs(de) < 10.0F) {
                elevation = prevElevation + de * 0.5F;
            }
        }

        return new float[]{bearing, elevation};
    }

    /**
     * Dáng chữ thứ i (bỏ dấu cách) của một dòng.
     *
     * @param age      giây (đồng hồ thật) từ lúc dòng xuất hiện
     * @param exit     giây từ lúc dòng bị thay, âm nếu còn sống
     * @param sinceLit giây (theo nhạc) từ lúc chữ tới lượt hát; âm là chưa tới
     * @param speed    hệ số tốc độ (1 = mặc định 480 ms)
     */
    public static Pose letter(int mode, float age, float exit, int i, int n, long seed, float sinceLit, float speed) {
        float x = 0.0F;
        float y = 0.0F;
        float rot = 0.0F;
        float scale = 1.0F;
        float alpha = 1.0F;
        float lit = 1.0F;
        float flash = 0.0F;
        float trail = 0.0F;
        if (mode != NONE) {
            lit = smooth(sinceLit / 0.22F);
            float t = (age - i * stagger(n)) * speed;
            if (mode == CONSTELLATION) {
                // Bụi sao rải quanh chỗ của chữ, xoáy vào theo đường cong rồi đáp bằng lò xo.
                float p = spring(t, OMEGA, ZETA);
                double th = hash(seed, i, 0) * Math.PI * 2.0;
                float dist = 38.0F + 26.0F * hash(seed, i, 1);
                float swirl = (float)Math.sin(Math.min(1.0F, p) * Math.PI) * 12.0F * (hash(seed, i, 2) < 0.5F ? -1.0F : 1.0F);
                x = (float)(Math.cos(th) * dist * 1.4 * (1.0F - p) - Math.sin(th) * swirl);
                y = (float)(Math.sin(th) * dist * 0.7 * (1.0F - p) + Math.cos(th) * swirl);
                rot = (hash(seed, i, 3) - 0.5F) * 1.6F * (1.0F - p);
                scale = 0.3F + 0.7F * p;
                alpha = smooth(t / 0.16F);
                flash = t > 0.0F ? bump(t, ARRIVE, 0.1F) : 0.0F;
                trail = t > 0.0F ? 1.0F - smooth(t / ARRIVE) : 0.0F;
            } else {
                float k = smooth(t / 0.35F);
                y = 4.0F * (1.0F - k);
                scale = 0.94F + 0.06F * k;
                alpha = k;
            }

            // Đứng rồi vẫn thở nhẹ, mỗi chữ lệch pha một chút như sao trôi.
            y += (float)Math.sin(age * 1.3F + i * 0.6F) * 0.45F;
            rot += (float)Math.sin(age * 0.9F + i * 1.7F) * 0.02F;
            scale += bump(sinceLit, 0.08F, 0.09F) * 0.16F;
        }

        if (exit >= 0.0F) {
            if (mode == NONE) {
                alpha = 0.0F;
            } else {
                float e = Math.max(0.0F, exit - hash(seed, i, 4) * 0.12F - i * 0.008F) * speed;
                if (mode == CONSTELLATION) {
                    y -= 10.0F * e + 16.0F * e * e;
                    x += (hash(seed, i, 0) - 0.5F) * 26.0F * e;
                    rot += (hash(seed, i, 3) - 0.5F) * 1.8F * e;
                    scale *= 1.0F - 0.55F * smooth(e / 0.7F);
                    alpha *= 1.0F - smooth(e / 0.45F);
                } else {
                    y -= 3.0F * smooth(e / 0.4F);
                    alpha *= 1.0F - smooth(e / 0.4F);
                }

                flash = 0.0F;
                trail = 0.0F;
            }
        }

        return new Pose(x, y, rot, scale, alpha, lit, flash, trail);
    }

    /** Hạt bụi thứ k khi chữ i tan; null nếu chưa sinh/đã tắt. Trả {x, y, cỡ, độ trong}, toạ độ so với chỗ đứng của chữ. */
    public static float[] dust(float exit, int i, int k, long seed, float speed) {
        float e = (exit - hash(seed, i, 4) * 0.12F - i * 0.008F - k * 0.03F) * speed;
        if (e < 0.0F || e > 1.8F) {
            return null;
        }

        double th = hash(seed, i, 10 + k) * Math.PI * 2.0;
        float h = hash(seed, i, 20 + k);
        float d = (8.0F + 16.0F * h) * (1.0F - (float)Math.exp(-2.2F * e)) / 2.2F;
        float twinkle = 0.55F + 0.45F * (float)Math.sin(e * (9.0F + 8.0F * h) + 6.0F * h);
        return new float[]{
            (float)Math.cos(th) * d, (float)Math.sin(th) * d - (6.0F * e + 3.0F * e * e), 0.8F + 1.4F * h, (1.0F - smooth(e / 1.8F)) * twinkle
        };
    }

    /** Tia lửa nhỏ bay lên lúc chữ vừa được hát tới; null ngoài khoảng 0..0.9 s. */
    public static float[] spark(float sinceLit, int i, long seed) {
        if (sinceLit < 0.0F || sinceLit > 0.9F) {
            return null;
        }

        return new float[]{(hash(seed, i, 5) - 0.5F) * 7.0F * sinceLit, -7.0F - 15.0F * sinceLit, 0.7F, (1.0F - sinceLit / 0.9F) * 0.85F};
    }
}
