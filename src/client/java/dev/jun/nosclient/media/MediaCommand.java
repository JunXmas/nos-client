package dev.jun.nosclient.media;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
final class MediaCommand {
    private MediaCommand() {
    }

    static String run(List<String> arguments) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(arguments).redirectError(Redirect.DISCARD);
        // Launcher đóng gói (PyInstaller) để lại LD_LIBRARY_PATH trỏ vào glib riêng của nó; gdbus hệ thống
        // nạp nhầm glib đó rồi chết "undefined symbol" — lời nhạc tắt im lặng. Công cụ hệ thống phải chạy
        // bằng thư viện hệ thống.
        builder.environment().remove("LD_LIBRARY_PATH");
        builder.environment().remove("LD_PRELOAD");
        Process process = builder.start();

        try {
            CompletableFuture<byte[]> output = new CompletableFuture<>();
            Thread.ofVirtual().name("nos-media-output").start(() -> {
                try {
                    output.complete(process.getInputStream().readNBytes(65537));
                } catch (IOException e) {
                    output.completeExceptionally(e);
                }
            });
            if (!process.waitFor(2L, TimeUnit.SECONDS)) {
                throw new IOException("Media query timed out");
            } else {
                byte[] bytes = output.get(1L, TimeUnit.SECONDS);
                if (process.exitValue() == 0 && bytes.length <= 65536) {
                    return new String(bytes, StandardCharsets.UTF_8);
                } else {
                    throw new IOException("Media query unavailable");
                }
            }
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) {
                process.destroyForcibly();
            }

            process.getInputStream().close();
        }
    }
}
