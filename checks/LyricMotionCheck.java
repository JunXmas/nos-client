import dev.jun.nosclient.media.LyricMotion;

/** Chạy: java -cp build/classes/java/client checks/LyricMotionCheck.java (cần -ea để assert chạy). */
public class LyricMotionCheck {
    public static void main(String[] args) {
        // Lò xo: bắt đầu 0, vượt đích đúng ở ARRIVE, lắng về 1.
        assert LyricMotion.spring(0, 8.5F, 0.52F) == 0;
        assert LyricMotion.spring(LyricMotion.ARRIVE, 8.5F, 0.52F) > 0.99F && LyricMotion.spring(LyricMotion.ARRIVE, 8.5F, 0.52F) < 1.01F;
        assert LyricMotion.spring(LyricMotion.ARRIVE * 1.6F, 8.5F, 0.52F) > 1.02F : "phải vượt đích một chút cho có lực";
        assert Math.abs(LyricMotion.spring(3, 8.5F, 0.52F) - 1) < 0.001F;
        // Chữ đứng yên đúng chỗ khi đã đáp xong và đã hát tới (bỏ qua nhịp thở ±0.45).
        LyricMotion.Pose rest = LyricMotion.letter(LyricMotion.CONSTELLATION, 4, -1, 3, 10, 42L, 2, 1);
        assert Math.abs(rest.x()) < 0.01F && Math.abs(rest.y()) < 0.5F && rest.alpha() == 1 && rest.lit() == 1 : rest;
        // Lúc mới sinh: chữ ở xa, mờ, nhỏ.
        LyricMotion.Pose born = LyricMotion.letter(LyricMotion.CONSTELLATION, 0.01F, -1, 0, 10, 42L, -1, 1);
        assert Math.hypot(born.x(), born.y()) > 30 && born.alpha() < 0.2F && born.scale() < 0.4F : born;
        // Chữ sau vào sau chữ trước (stagger), cùng một khoảnh khắc.
        assert LyricMotion.letter(LyricMotion.CONSTELLATION, 0.2F, -1, 0, 10, 42L, -1, 1).alpha()
            > LyricMotion.letter(LyricMotion.CONSTELLATION, 0.2F, -1, 9, 10, 42L, -1, 1).alpha();
        // Tan hết trước khi bị dọn khỏi danh sách: cả chữ lẫn bụi.
        float life = LyricMotion.lifetime(40, 1);
        for (int i = 0; i < 40; i++) {
            assert LyricMotion.letter(LyricMotion.CONSTELLATION, 9, life, i, 40, 7L, 9, 1).alpha() < 0.01F : "chữ " + i + " còn hiện khi bị dọn";
            for (int k = 0; k < 5; k++) {
                float[] d = LyricMotion.dust(life, i, k, 7L, 1);
                assert d == null || d[3] < 0.02F : "bụi " + i + "/" + k + " còn sáng khi bị dọn";
            }
        }
        // Karaoke: chữ đầu sáng trước chữ cuối, chữ cuối vẫn kịp sáng trước khi dòng hết.
        assert LyricMotion.litAt(0, 10, 3) < LyricMotion.litAt(9, 10, 3) && LyricMotion.litAt(9, 10, 3) < 3;
        // Neo theo góc nhìn: hướng nhìn thẳng vào yRot, nâng lên khỏi chân trời, không chạm đỉnh đầu.
        float[] a = LyricMotion.anchor(90, 0, 0, 0, false);
        assert a[0] == 90 && a[1] >= 1.5F && a[1] <= 55;
        assert LyricMotion.anchor(0, -90, 0, 0, false)[1] == 55 : "nhìn thẳng lên vẫn đặt chữ chéo cho đọc được";
        // Quay nhẹ thì lời chỉ bám nửa đường; quay hẳn thì nhảy theo. Qua mốc 0/360 không bị vòng ngược.
        assert Math.abs(LyricMotion.anchor(10, 0, 0, 1, true)[0] - 5.5F) < 0.01F;
        assert LyricMotion.anchor(120, 0, 0, 1, true)[0] == 120;
        float wrap = LyricMotion.anchor(350, 0, 10, 1, true)[0];
        assert wrap > 355 || wrap < 1 : wrap;
        // Hash ổn định và nằm trong [0, 1).
        for (int i = 0; i < 1000; i++) { float h = LyricMotion.hash(i, i * 7, 3); assert h >= 0 && h < 1; }
        assert LyricMotion.hash(5, 6, 7) == LyricMotion.hash(5, 6, 7);
        System.out.println("LyricMotion OK");
    }
}
