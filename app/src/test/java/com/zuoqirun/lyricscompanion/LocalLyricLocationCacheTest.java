package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class LocalLyricLocationCacheTest {
    @Test public void locationsAreScopedToRootAndCandidateNamesRegardlessOfOrder() {
        LocalLyricLocationCache cache = new LocalLyricLocationCache();
        Set<String> names = new LinkedHashSet<>(Arrays.asList("晴天.lrc", "周杰伦 - 晴天.lrc"));
        cache.put("usb-one", names, "album/song.lrc", 100L);
        assertEquals("album/song.lrc", cache.get("usb-one", new LinkedHashSet<>(
                Arrays.asList("周杰伦 - 晴天.lrc", "晴天.lrc")), 200L));
        assertNull(cache.get("usb-two", names, 200L));
        assertNull(cache.get("usb-one", Collections.singleton("夜曲.lrc"), 200L));
        assertNull(cache.get("usb-one", names, 100L + LocalLyricLocationCache.TTL_MS));
    }

    @Test public void leastRecentlyUsedLocationIsEvictedAndInvalidLocationsCanBeRemoved() {
        LocalLyricLocationCache cache = new LocalLyricLocationCache();
        Set<String> names = Collections.singleton("song.lrc");
        for (int i = 0; i < LocalLyricLocationCache.MAX_ENTRIES; i++) {
            cache.put("usb" + i, names, "location" + i, 100L);
        }
        assertEquals("location0", cache.get("usb0", names, 200L));
        cache.put("next", names, "new", 200L);
        assertNull(cache.get("usb1", names, 200L));
        assertEquals("location0", cache.get("usb0", names, 200L));
        cache.remove("usb0", names);
        assertNull(cache.get("usb0", names, 200L));
        cache.clear();
        assertNull(cache.get("next", names, 200L));
    }

    @Test public void legacyDirectoryIsPreservedAndAdditionalDirectoriesAreDeduplicated() {
        Set<String> roots = AppPreferences.mergeLocalLyricDirectories("usb-one",
                new LinkedHashSet<>(Arrays.asList("usb-two", "usb-one", " ")));
        assertEquals(Arrays.asList("usb-one", "usb-two"), new java.util.ArrayList<>(roots));
        assertTrue(AppPreferences.mergeLocalLyricDirectories("", Collections.emptySet()).isEmpty());
    }
}
