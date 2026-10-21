package com.zuoqirun.lyricscompanion;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Byte-bounded references to render buffers. Eviction must not destroy a buffer still in a frame. */
final class RenderBufferCache<T> {
    private final LinkedHashMap<String, Entry<T>> entries = new LinkedHashMap<>(16, .75f, true);
    private final boolean retainOversized;
    private long bytes;
    private int budget;

    RenderBufferCache(int budget, boolean retainOversized) {
        this.budget = budget;
        this.retainOversized = retainOversized;
    }

    T get(String key) {
        Entry<T> entry = entries.get(key);
        return entry == null ? null : entry.value;
    }

    void put(String key, T value, int byteCount) {
        Entry<T> previous = entries.remove(key);
        if (previous != null) bytes -= previous.bytes;
        if (byteCount <= budget || retainOversized) {
            entries.put(key, new Entry<>(value, byteCount));
            bytes += byteCount;
        }
        trimToSize(budget);
    }

    void trimToSize(int budget) {
        this.budget = budget;
        Iterator<Map.Entry<String, Entry<T>>> iterator = entries.entrySet().iterator();
        while (bytes > budget && iterator.hasNext()) {
            // CPU composition may need a single full-panel result larger than the normal cache.
            // Retain that one result to avoid allocating it every frame; never retain two.
            if (retainOversized && entries.size() == 1) break;
            Entry<T> removed = iterator.next().getValue();
            bytes -= removed.bytes;
            iterator.remove();
        }
    }

    void evictAll() {
        entries.clear();
        bytes = 0;
    }

    long sizeBytes() { return bytes; }

    private static final class Entry<T> {
        final T value;
        final int bytes;

        Entry(T value, int bytes) { this.value = value; this.bytes = bytes; }
    }
}
