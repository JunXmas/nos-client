import dev.jun.nosclient.media.LyricChunks;
import java.util.ArrayList;
import java.util.List;

/** Chạy: java -ea -cp build/classes/java/client checks/LyricChunksCheck.java */
public class LyricChunksCheck {
    public static void main(String[] args) {
        String line = "Và em đã biết sẽ không thể yêu ai khác ngoài anh ra";
        List<String> chunks = LyricChunks.split(line);
        assert String.join(" ", chunks).equals(line) : "chia cụm không được mất hay đổi chữ: " + chunks;
        assert chunks.size() >= 3 : "câu dài phải ra nhiều cụm: " + chunks;
        for (String c : chunks) {
            assert c.split(" ").length <= 3 : "cụm quá nhiều từ: " + c;
            assert c.length() <= 14 || !c.contains(" ") : "cụm quá dài: " + c;
        }
        assert LyricChunks.split("Kết thúc").size() == 1 : "câu ngắn giữ nguyên một cụm";
        assert LyricChunks.split("Ồ hoá ra em thật nhỏ bé a").stream().noneMatch(c -> c.length() <= 1) : "không để một chữ lẻ đứng riêng";

        // Thời gian: tăng dần, cụm đầu từ 0, cụm cuối kết thúc trước câu sau, offset nào cũng ra đúng cụm.
        long[] at = LyricChunks.starts(chunks, 4000);
        assert at[0] == 0;
        for (int i = 1; i < at.length; i++) assert at[i] > at[i - 1] : "mốc phải tăng";
        assert at[chunks.size() - 1] < 4000 * 0.9 : "cụm cuối phải bắt đầu sớm đủ để đọc";
        assert LyricChunks.indexAt(at, 0) == 0 && LyricChunks.indexAt(at, 3999) == chunks.size() - 1;
        // Câu kéo dài 12 s (có nhạc nền sau câu): các cụm vẫn ra theo nhịp nói (xong trong ~5 s), cụm cuối nằm lại.
        long[] slow = LyricChunks.starts(chunks, 12000);
        assert slow[chunks.size() - 1] < 5200 : "câu dài không được kéo giãn cụm: " + slow[chunks.size() - 1];
        assert slow[chunks.size()] == 12000 : "cụm cuối phải nằm tới hết câu";

        // Chỗ đặt: trong khung nhìn ngang, luôn trên trời, và các cụm liên tiếp không chồng lên nhau.
        List<float[]> recent = new ArrayList<>();
        for (int k = 0; k < 40; k++) {
            float[] p = LyricChunks.place(90, 0, 70, 16.0 / 9.0, 1234L + k, recent);
            double db = ((p[0] - 90) % 360 + 540) % 360 - 180;
            assert Math.abs(db) < 30 : "cụm sát mép màn hình, chữ dài sẽ bị cắt: " + db;
            assert p[1] >= 22 && p[1] <= 35 + 0.01 : "cụm phải trên trời và trong tầm nhìn dọc: " + p[1];
            for (float[] old : recent) {
                double d = Math.hypot(((p[0] - old[0]) % 360 + 540) % 360 - 180, (p[1] - old[1]) * 1.6);
                assert d > 6 : "cụm chồng lên cụm vừa hiện: " + d;
            }
            if (!recent.isEmpty()) {
                float[] prev = recent.getLast();
                double d = Math.hypot(((p[0] - prev[0]) % 360 + 540) % 360 - 180, (p[1] - prev[1]) * 1.6);
                assert d < 40 : "cụm sau nhảy quá xa cụm trước, mắt không theo kịp: " + d;
            }
            recent.add(p);
            if (recent.size() > 2) recent.removeFirst();
        }
        // Cụm trước nằm sát mép trái: cụm sau không được vọt sang tận mép phải (mắt mất dấu câu).
        int far = 0;
        for (long seed = 0; seed < 200; seed++) {
            float[] edge = {90 - 24, 30};
            float[] p = LyricChunks.place(90, 0, 70, 16.0 / 9.0, seed, List.of(edge));
            double d = Math.hypot(((p[0] - edge[0]) % 360 + 540) % 360 - 180, (p[1] - edge[1]) * 1.6);
            if (d > 32) far++;
        }
        assert far < 20 : "cụm sau hay nhảy xa cụm trước: " + far + "/200";
        // Nhìn xuống đất: vẫn trên trời.
        assert LyricChunks.place(0, -60, 70, 16.0 / 9.0, 7L, List.of())[1] >= 22;
        System.out.println("LyricChunks OK " + chunks);
    }
}
