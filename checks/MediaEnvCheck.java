package dev.jun.nosclient.media;

/**
 * Lời nhạc phải đọc được bài đang phát cả khi game được launcher đóng gói (PyInstaller) mở — launcher để lại
 * LD_LIBRARY_PATH trỏ vào glib riêng làm gdbus hệ thống chết. Cần một trình phát MPRIS đang chạy (Spotify…).
 * Chạy: javac -d /tmp/mec -cp build/classes/java/client checks/MediaEnvCheck.java && LD_LIBRARY_PATH=/opt/nostalgia/_internal java -cp /tmp/mec:build/classes/java/client dev.jun.nosclient.media.MediaEnvCheck
 */
public class MediaEnvCheck {
    public static void main(String[] args) throws Exception {
        String ld = System.getenv("LD_LIBRARY_PATH");
        if (ld == null || ld.isBlank()) {
            System.out.println("Bỏ qua: đặt LD_LIBRARY_PATH như launcher đóng gói để kiểm có nghĩa.");
            return;
        }

        MediaTrack t = new LinuxMedia().poll();
        if (t.title().isBlank()) {
            throw new AssertionError("Không đọc được bài đang phát với LD_LIBRARY_PATH=" + ld + " (có trình phát nào đang mở không?)");
        }

        System.out.println("MediaEnv OK: " + t.title() + " / " + t.artist());
    }
}
