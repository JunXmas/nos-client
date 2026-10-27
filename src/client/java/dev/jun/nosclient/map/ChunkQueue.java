package dev.jun.nosclient.map;

import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ChunkQueue<T> {
    private final int capacity;
    private final LinkedHashMap<Long, T> entries = new LinkedHashMap<>();

    public ChunkQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Positive queue capacity required");
        }

        this.capacity = capacity;
    }

    public void add(long coordinate, T world) {
        this.entries.remove(coordinate);
        this.entries.put(coordinate, world);
        if (this.entries.size() > this.capacity) {
            this.entries.pollFirstEntry();
        }
    }

    public ChunkQueue.Entry<T> poll() {
        Map.Entry<Long, T> entry = this.entries.pollFirstEntry();
        return entry == null ? null : new ChunkQueue.Entry<>(entry.getKey(), entry.getValue());
    }

    public int size() {
        return this.entries.size();
    }

    public void clear() {
        this.entries.clear();
    }

    @Environment(EnvType.CLIENT)
    public record Entry<T>(long coordinate, T world) {
    }
}
