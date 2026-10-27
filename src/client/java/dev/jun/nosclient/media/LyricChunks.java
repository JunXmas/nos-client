package dev.jun.nosclient.media;

import java.util.ArrayList;
import java.util.List;

/**
 * Chia một câu lời thành cụm ngắn hiện lần lượt (thay vì cả câu dài một lúc), chia thời gian câu cho từng cụm theo
 * số ký tự, và chọn chỗ rải cụm trên trời trong tầm nhìn. Thuần, không đụng Minecraft — kiểm ở checks/LyricChunksCheck.
 */
public final class LyricChunks {
    /** Cụm dài tối đa chừng này ký tự (≈ 2–4 từ tiếng Việt). */
    static final int TARGET = 12;
    /** Phần cuối câu để trống: cụm cuối còn kịp đọc trước khi câu sau tới. */
    static final double TAIL = 0.12;
    /**
     * Câu dài hơn chừng này (ms) thì phần dư là nhạc nền sau câu hát (LRC chỉ đánh dấu khi câu sau bắt đầu): chia
     * cụm theo nhịp nói bình thường rồi để cụm cuối nằm lại, không kéo giãn từng cụm cho đủ khoảng trống.
     */
    static final long SUNG = 5200L;

    private LyricChunks() {
    }

    public static List<String> split(String line) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int words = 0;

        for (String word : line.strip().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }

            if (current.length() > 0 && (current.length() + 1 + word.length() > TARGET || words >= 3)) {
                chunks.add(current.toString());
                current.setLength(0);
                words = 0;
            }

            if (current.length() > 0) {
                current.append(' ');
            }

            current.append(word);
            words++;
        }

        if (current.length() > 0) {
            // Cụm cuối quá ngắn (một từ lẻ) thì gộp vào cụm trước cho khỏi nháy một chữ.
            if (!chunks.isEmpty() && current.length() <= 3) {
                chunks.set(chunks.size() - 1, chunks.getLast() + " " + current);
            } else {
                chunks.add(current.toString());
            }
        }

        return chunks;
    }

    /** Mốc bắt đầu (ms, tính từ đầu câu) của từng cụm, chia theo số ký tự; phần tử cuối = mốc kết thúc cụm cuối. */
    public static long[] starts(List<String> chunks, long lengthMs) {
        long[] at = new long[chunks.size() + 1];
        int total = chunks.stream().mapToInt(String::length).sum();
        double usable = Math.min(lengthMs, SUNG) * (1.0 - TAIL);
        int sum = 0;

        for (int i = 0; i < chunks.size(); i++) {
            at[i] = Math.round(usable * sum / Math.max(1, total));
            sum += chunks.get(i).length();
        }

        at[chunks.size()] = Math.max(at[chunks.size() - 1] + 1, lengthMs);
        return at;
    }

    /** Cụm đang hát ở thời điểm offset (ms từ đầu câu). */
    public static int indexAt(long[] starts, long offsetMs) {
        int index = 0;
        for (int i = 0; i < starts.length - 1; i++) {
            if (offsetMs >= starts[i]) {
                index = i;
            }
        }

        return index;
    }

    /**
     * Chỗ đặt cụm: ngẫu nhiên (theo seed) trong khung nhìn, luôn trên trời (≥ 22°), tránh xa các cụm vừa hiện.
     * Thử vài ứng viên, lấy cái cách các chỗ cũ xa nhất. Trả {hướng°, độ cao°}.
     *
     * @param yaw      hướng nhìn (độ, kiểu Minecraft)
     * @param pitchUp  góc ngẩng lên (độ, dương là nhìn lên)
     * @param vfov     góc nhìn dọc (độ)
     * @param aspect   tỉ lệ khung hình rộng/cao
     * @param recent   các chỗ vừa dùng {hướng, độ cao}
     */
    public static float[] place(float yaw, float pitchUp, double vfov, double aspect, long seed, List<float[]> recent) {
        double halfV = vfov / 2.0;
        double halfH = Math.toDegrees(Math.atan(Math.tan(Math.toRadians(halfV)) * aspect));
        double low = Math.max(22.0, pitchUp - halfV + 10.0);
        double high = Math.max(low + 4.0, Math.min(60.0, pitchUp + halfV - 12.0));
        // Chừa nửa bề ngang cụm (~15°) ở hai bên để cụm dài không bị mép màn hình cắt.
        double spread = Math.max(6.0, halfH * 0.5 - 6.0);
        float[] best = null;
        double bestGap = -1.0;

        for (int k = 0; k < 8; k++) {
            float bearing = (float)(((yaw + (LyricMotion.hash(seed, k, 40) * 2.0 - 1.0) * spread) % 360.0 + 360.0) % 360.0);
            float elevation = (float)(low + LyricMotion.hash(seed, k, 41) * (high - low));
            double gap = Double.MAX_VALUE;

            for (float[] old : recent) {
                double db = ((bearing - old[0]) % 360.0 + 540.0) % 360.0 - 180.0;
                gap = Math.min(gap, Math.hypot(db, (elevation - old[1]) * 1.6));
            }

            if (gap > bestGap) {
                bestGap = gap;
                best = new float[]{bearing, elevation};
            }
        }

        return best;
    }
}
