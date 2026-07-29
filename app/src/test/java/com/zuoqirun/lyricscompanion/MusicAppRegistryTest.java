package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MusicAppRegistryTest {
    @Test public void recognizesCarEditionPackageNames() {
        assertSource("qqmusic", "com.tencent.qqmusiccar", "");
        assertSource("kuwo", "cn.kuwo.kwmusiccar", "");
        assertSource("kugou", "com.kugou.auto", "");
    }

    @Test public void recognizesVendorWrappedPlayersByApplicationLabel() {
        assertSource("netease", "vendor.player.one", "网易云音乐车机版");
        assertSource("qqmusic", "vendor.player.two", "QQ音乐 HD");
        assertSource("kugou", "vendor.player.three", "酷狗概念版");
        assertSource("kuwo", "vendor.player.four", "酷我音乐");
    }

    @Test public void keepsUnknownPlayersCatalogNeutral() {
        MusicAppRegistry.App app = MusicAppRegistry.resolve("vendor.player", "车载播放器");
        assertEquals("media", app.sourceId);
        assertFalse(app.known);
    }

    @Test public void onlyTrustsMediaIdFromNetEaseSession() {
        assertEquals("123456", MultiSourceLyricClient.directMediaId(
                "netease", "netease", "123456"));
        assertEquals("", MultiSourceLyricClient.directMediaId(
                "qqmusic", "netease", "123456"));
        assertEquals("", MultiSourceLyricClient.directMediaId(
                "media", "netease", "123456"));
        assertEquals("", MultiSourceLyricClient.directMediaId(
                "netease", "qqmusic", "123456"));
    }

    private static void assertSource(String expected, String packageName, String label) {
        MusicAppRegistry.App app = MusicAppRegistry.resolve(packageName, label);
        assertEquals(expected, app.sourceId);
        assertTrue(app.known);
    }
}
