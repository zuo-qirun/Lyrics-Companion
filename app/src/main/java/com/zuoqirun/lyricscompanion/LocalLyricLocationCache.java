package com.zuoqirun.lyricscompanion;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;

/** Bounded positive locations only: no lyric bodies, missing results or directory permissions. */
final class LocalLyricLocationCache {
    static final int MAX_ENTRIES = 128;
    static final long TTL_MS = 5 * 60_000L;
    private final LinkedHashMap<String, Entry> locations = new LinkedHashMap<>(16, .75f, true);

    synchronized String get(String root, Set<String> names, long now) {
        String key = key(root, names);
        Entry entry = locations.get(key);
        if (entry == null) return null;
        if (now < entry.created || now - entry.created >= TTL_MS) {
            locations.remove(key);
            return null;
        }
        return entry.location;
    }

    synchronized void put(String root, Set<String> names, String location, long now) {
        locations.put(key(root, names), new Entry(location, now));
        while (locations.size() > MAX_ENTRIES) {
            locations.remove(locations.keySet().iterator().next());
        }
    }

    synchronized void remove(String root, Set<String> names) { locations.remove(key(root, names)); }
    synchronized void clear() { locations.clear(); }

    private static String key(String root, Set<String> names) {
        StringBuilder key = new StringBuilder().append(root.length()).append(':').append(root);
        for (String name : new TreeSet<>(names)) key.append(name.length()).append(':').append(name);
        return key.toString();
    }

    private static final class Entry {
        final String location;
        final long created;
        Entry(String location, long created) { this.location = location; this.created = created; }
    }
}
