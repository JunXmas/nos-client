package dev.jun.nosclient.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ClientFiles {
    public static final int MAX_CONFIG_BYTES = 262144;

    private ClientFiles() {
    }

    public static String read(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] data = input.readNBytes(262145);
            if (data.length > 262144) {
                throw new IOException("Client settings exceed size limit");
            } else {
                return new String(data, StandardCharsets.UTF_8);
            }
        }
    }

    public static void write(Path path, String text) throws IOException {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (data.length > 262144) {
            throw new IOException("Client settings exceed size limit");
        }

        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".nosclient-", ".tmp");

        try {
            Files.write(temporary, data);

            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
