package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.session.PlaybackState;

import org.junit.Test;

import java.util.Arrays;

public class MusicAppRegistryTest {
    @Test public void recognizesCarEditionPackageNames() {
        assertSource("qqmusic", "com.tencent.qqmusiccar", "");
        assertSource("kuwo", "cn.kuwo.kwmusiccar", "");
        assertSource("kugou", "com.kugou.auto", "");
        assertSource("kugou", "com.kugou.android.auto", "");
        assertSource("kugou", "com.kugou.android.lite", "");
        assertSource("kuwo", "com.shaiban.audioplayer.mplayer", "");
        assertSource("soda", "com.luna.music.car", "");
        assertSource("netease", "com.netease.cloudmusic.iot", "");
    }

    @Test public void recognizesVendorWrappedPlayersByApplicationLabel() {
        assertSource("netease", "vendor.player.one", "车载版 - 网易");
        assertSource("qqmusic", "vendor.player.two", "车机 QQ");
        assertSource("kugou", "vendor.player.three", "酷狗概念版");
        assertSource("kuwo", "vendor.player.four", "KWMusic Auto");
        assertSource("qqmusic", "vendor.player.five", "腾讯音乐车载版");
        assertSource("soda", "vendor.player.six", "汽水音乐车机版");
    }

    @Test public void mapsRecognizedPlayersToTheirOwnLyricCatalog() {
        assertCatalog("netease", "com.netease.cloudmusic.iot");
        assertCatalog("qqmusic", "com.tencent.qqmusiccar");
        assertCatalog("kugou", "com.kugou.android.auto");
        assertCatalog("kugou", "com.kugou.android.lite");
        assertCatalog("kuwo", "com.shaiban.audioplayer.mplayer");
        assertCatalog("", "com.luna.music");
        assertCatalog("", "com.luna.music.car");
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

    @Test public void unknownPlayersDoNotHaveAHardCodedNetEasePreference() {
        MultiSourceLyricClient.Result qq = new MultiSourceLyricClient.Result(
                LrcTimeline.EMPTY, "QQ 音乐", "qqmusic");
        MultiSourceLyricClient.Result netease = new MultiSourceLyricClient.Result(
                LrcTimeline.EMPTY, "网易云音乐", "netease");
        assertEquals("qqmusic", MultiSourceLyricClient.chooseResult(
                Arrays.asList(), Arrays.asList(qq, netease)).providerId);
        assertEquals("netease", MultiSourceLyricClient.chooseResult(
                Arrays.asList("netease"), Arrays.asList(qq, netease)).providerId);
    }

    @Test public void manualCatalogCanFallbackToRecognizedPlayerCatalog() {
        MultiSourceLyricClient.CatalogPlan plan = MultiSourceLyricClient.catalogPlan(
                "qqmusic", "kugou", true);
        assertEquals(Arrays.asList("kugou", "qqmusic"), plan.priority);
        assertEquals(Arrays.asList("kugou", "qqmusic", "netease", "kuwo"),
                plan.providers);
        assertTrue(plan.providers.contains("qqmusic"));
        assertTrue(plan.manualSelection);
    }

    @Test public void playerCatalogCanBeExcludedFromManualFallback() {
        MultiSourceLyricClient.CatalogPlan plan = MultiSourceLyricClient.catalogPlan(
                "qqmusic", "kugou", false);
        assertEquals(Arrays.asList("kugou"), plan.priority);
        assertFalse(plan.providers.contains("qqmusic"));
        assertTrue(plan.providers.contains("kugou"));
    }

    @Test public void automaticCatalogStillUsesRecognizedPlayerFirst() {
        MultiSourceLyricClient.CatalogPlan plan = MultiSourceLyricClient.catalogPlan(
                "kuwo", "auto", false);
        assertEquals(Arrays.asList("kuwo"), plan.priority);
        assertFalse(plan.manualSelection);
        assertTrue(plan.providers.containsAll(Arrays.asList(
                "netease", "qqmusic", "kugou", "kuwo")));
        assertEquals("kuwo", plan.providers.get(0));
    }

    @Test public void catalogRecognitionDoesNotBiasActiveSessionSelection() {
        assertEquals(MusicAppRegistry.selectionScore(0, true, false, false, false),
                MusicAppRegistry.selectionScore(0, true, false, true, false));
    }

    @Test public void metadataOnlyCarSessionRemainsDisplayable() {
        assertTrue(MusicStateStore.isDisplayableSession("晴天", PlaybackState.STATE_NONE));
        assertTrue(MusicStateStore.isDisplayableSession("晴天", PlaybackState.STATE_PAUSED));
        assertFalse(MusicStateStore.isDisplayableSession("晴天", PlaybackState.STATE_STOPPED));
        assertFalse(MusicStateStore.isDisplayableSession("", PlaybackState.STATE_PLAYING));
    }

    @Test public void metadataOnlyCarSessionAdvancesItsPlaybackClock() {
        assertTrue(MusicStateStore.isPositionAdvancing(
                "晴天", true, PlaybackState.STATE_NONE, false));
        assertTrue(MusicStateStore.isPositionAdvancing(
                "晴天", true, PlaybackState.STATE_PAUSED, true));
        assertFalse(MusicStateStore.isPositionAdvancing(
                "晴天", true, PlaybackState.STATE_PAUSED, false));
        assertFalse(MusicStateStore.isPositionAdvancing(
                "晴天", false, PlaybackState.STATE_NONE, false));
    }

    @Test public void rawPositionSamplesDistinguishPollingFromSeeking() {
        assertFalse(MusicStateStore.hasMeaningfulPositionChange(-1L, 12_000L));
        assertFalse(MusicStateStore.hasMeaningfulPositionChange(12_000L, 12_050L));
        assertTrue(MusicStateStore.hasMeaningfulPositionChange(12_000L, 12_600L));
        assertTrue(MusicStateStore.hasMeaningfulPositionChange(12_000L, 4_000L));
    }

    @Test public void lateCarMetadataDoesNotCreateANewLyricGeneration() {
        String first = MusicStateStore.lyricTrackKey("soda", "Nothin' on Me",
                "Leah Marie Perez", -1L, "temporary", "auto", true);
        String refined = MusicStateStore.lyricTrackKey("soda", "Nothin’ on Me",
                "Leah Marie Perez", 217_000L, "final-media-id", "auto", true);
        assertEquals(first, refined);
    }

    @Test public void recognizesSodaMetadataTitleAsAnExistingLyricLine() {
        LrcTimeline timeline = LrcTimeline.parse(
                "[00:01.00]Keep it moving\n[00:04.00]I can see it in your eyes", "");
        assertTrue(timeline.containsLyricText("I can see it in your eyes"));
        assertFalse(timeline.containsLyricText("Nothin' on Me"));
    }

    @Test public void netEaseDirectSongIdStillRefreshesIdentity() {
        String first = MusicStateStore.lyricTrackKey("netease", "夜曲", "周杰伦",
                -1L, "", "auto", true);
        String direct = MusicStateStore.lyricTrackKey("netease", "夜曲", "周杰伦",
                226_000L, "song:123456", "auto", true);
        assertFalse(first.equals(direct));
    }

    private static void assertSource(String expected, String packageName, String label) {
        MusicAppRegistry.App app = MusicAppRegistry.resolve(packageName, label);
        assertEquals(expected, app.sourceId);
        assertTrue(app.known);
    }

    private static void assertCatalog(String expected, String packageName) {
        MusicAppRegistry.App app = MusicAppRegistry.resolve(packageName, "");
        assertEquals(expected, MusicAppRegistry.lyricCatalogForSource(app.sourceId));
    }
}
