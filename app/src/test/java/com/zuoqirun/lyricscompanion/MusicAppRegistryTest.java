package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertEquals("网易云音乐车机版", MusicAppRegistry.resolve(
                "com.netease.cloudmusic.iot", "").displayName);
    }

    @Test public void recognizesVendorWrappedPlayersByApplicationLabel() {
        assertSource("netease", "vendor.player.one", "车载版 - 网易");
        assertSource("qqmusic", "vendor.player.two", "车机 QQ");
        assertSource("kugou", "vendor.player.three", "酷狗概念版");
        assertSource("kuwo", "vendor.player.four", "KWMusic Auto");
        assertSource("qqmusic", "vendor.player.five", "腾讯音乐车载版");
        assertSource("soda", "vendor.player.six", "汽水音乐车机版");
    }

    @Test public void recognizesPlayersByPackageNameFeatures() {
        assertSource("netease", "vendor.car.cloudmusic.player", "");
        assertSource("qqmusic", "vendor.car.qqmusic.service", "");
        assertSource("kugou", "vendor.car.kugou.player", "");
        assertSource("kuwo", "vendor.car.kwmusic.player", "");
        assertSource("soda", "vendor.car.luna.music.player", "");
    }

    @Test public void mapsRecognizedPlayersToTheirOwnLyricCatalog() {
        assertCatalog("netease", "com.netease.cloudmusic.iot");
        assertCatalog("qqmusic", "com.tencent.qqmusiccar");
        assertCatalog("kugou", "com.kugou.android.auto");
        assertCatalog("kugou", "com.kugou.android.lite");
        assertCatalog("kuwo", "com.shaiban.audioplayer.mplayer");
        assertCatalog("soda", "com.luna.music");
        assertCatalog("soda", "com.luna.music.car");
    }

    @Test public void keepsUnknownPlayersCatalogNeutral() {
        MusicAppRegistry.App app = MusicAppRegistry.resolve("vendor.player", "车载播放器");
        assertEquals("media", app.sourceId);
        assertFalse(app.known);
    }

    @Test public void onlyTrustsDirectMediaIdsFromMatchingNativeCatalogs() {
        assertEquals("123456", MultiSourceLyricClient.directMediaId(
                "netease", "netease", "123456"));
        assertEquals("7031318019544614913", MultiSourceLyricClient.directMediaId(
                "soda", "soda", "7031318019544614913"));
        assertEquals("", MultiSourceLyricClient.directMediaId(
                "qqmusic", "netease", "123456"));
        assertEquals("", MultiSourceLyricClient.directMediaId(
                "media", "netease", "123456"));
        assertEquals("", MultiSourceLyricClient.directMediaId(
                "netease", "qqmusic", "123456"));
        assertEquals("", MultiSourceLyricClient.directMediaId(
                "qqmusic", "soda", "7031318019544614913"));
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
        assertEquals(Arrays.asList("kugou", "qqmusic", "netease", "kuwo", "soda"),
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

    @Test public void forcedAppCatalogDoesNotFallBackToOtherCatalogs() {
        MultiSourceLyricClient.CatalogPlan plan = MultiSourceLyricClient.catalogPlan(
                "qqmusic", "netease", true, true);
        assertEquals(Arrays.asList("netease"), plan.priority);
        assertEquals(Arrays.asList("netease"), plan.providers);
        assertTrue(plan.manualSelection);
    }

    @Test public void automaticCatalogStillUsesRecognizedPlayerFirst() {
        MultiSourceLyricClient.CatalogPlan plan = MultiSourceLyricClient.catalogPlan(
                "kuwo", "auto", false);
        assertEquals(Arrays.asList("kuwo"), plan.priority);
        assertFalse(plan.manualSelection);
        assertTrue(plan.providers.containsAll(Arrays.asList(
                "netease", "qqmusic", "kugou", "kuwo", "soda")));
        assertEquals("kuwo", plan.providers.get(0));
    }

    @Test public void sodaUsesItsOwnCatalogBeforeCrossCatalogFallback() {
        MultiSourceLyricClient.CatalogPlan plan = MultiSourceLyricClient.catalogPlan(
                "soda", "auto", true);
        assertEquals(Arrays.asList("soda"), plan.priority);
        assertEquals("soda", plan.providers.get(0));
        assertTrue(plan.providers.containsAll(Arrays.asList(
                "netease", "qqmusic", "kugou", "kuwo")));
    }

    @Test public void catalogRecognitionDoesNotBiasActiveSessionSelection() {
        assertEquals(MusicAppRegistry.selectionScore(0, true, false, false, false),
                MusicAppRegistry.selectionScore(0, true, false, true, false));
    }

    @Test public void notificationListenerHealthRequiresAFreshSuccessfulRead() {
        assertTrue(MusicNotificationListener.isHealthyAt(true, 1_000L, 3_999L, 3_000L));
        assertFalse(MusicNotificationListener.isHealthyAt(true, 1_000L, 4_000L, 3_000L));
        assertFalse(MusicNotificationListener.isHealthyAt(false, 1_000L, 2_000L, 3_000L));
        assertFalse(MusicNotificationListener.isHealthyAt(true, 0L, 2_000L, 3_000L));
    }

    @Test public void emptySessionsHaveAFiveSecondGracePeriod() {
        assertFalse(MusicNotificationListener.shouldClearAfterEmpty(10_000L, 14_999L));
        assertTrue(MusicNotificationListener.shouldClearAfterEmpty(10_000L, 15_000L));
    }

    @Test public void metadataOnlyCarSessionRemainsDisplayable() {
        assertTrue(MusicStateStore.isDisplayableSession(
                "晴天", MusicPlaybackData.STATE_NONE));
        assertTrue(MusicStateStore.isDisplayableSession(
                "晴天", MusicPlaybackData.STATE_PAUSED));
        assertFalse(MusicStateStore.isDisplayableSession(
                "晴天", MusicPlaybackData.STATE_STOPPED));
        assertFalse(MusicStateStore.isDisplayableSession(
                "", MusicPlaybackData.STATE_PLAYING));
    }

    @Test public void metadataOnlyCarSessionAdvancesItsPlaybackClock() {
        assertTrue(MusicStateStore.isPositionAdvancing(
                "晴天", true, MusicPlaybackData.STATE_NONE, false));
        assertTrue(MusicStateStore.isPositionAdvancing(
                "晴天", true, MusicPlaybackData.STATE_PAUSED, true));
        assertFalse(MusicStateStore.isPositionAdvancing(
                "晴天", true, MusicPlaybackData.STATE_PAUSED, false));
        assertFalse(MusicStateStore.isPositionAdvancing(
                "晴天", false, MusicPlaybackData.STATE_NONE, false));
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

    @Test public void keepsSodaTitleWhenCreditsArePublishedAsMetadata() {
        assertTrue(MusicStateStore.shouldKeepSodaTrackIdentity(
                "soda", true, "Deadman", "作词：蔡徐坤", "蔡徐坤", "蔡徐坤",
                201_000L, 201_000L));
    }

    @Test public void keepsSodaDynamicTitleWhilePlaybackContinuityIsStable() {
        assertTrue(MusicStateStore.shouldKeepSodaTrackIdentity(
                "soda", true, "Deadman", "You know I adore ya", "蔡徐坤", "蔡徐坤",
                201_000L, 201_000L));
        assertFalse(MusicStateStore.shouldKeepSodaTrackIdentity(
                "soda", true, "Deadman", "Next Song", "蔡徐坤", "Next Artist",
                201_000L, 240_000L));
    }

    @Test public void recognizesSodaSkipWhenTitleAndArtistChangeBeforeDuration() {
        assertFalse(MusicStateStore.shouldKeepSodaTrackIdentity(
                "soda", true, "KAMNH", "Ticking Away", "Моя Мишель",
                "Grabbitz, bbno$, VALORANT", 182_000L, 182_000L));
    }

    @Test public void recognizesSodaSkipByStableTrackIdForSameArtist() {
        assertFalse(MusicStateStore.shouldKeepSodaTrackIdentity(
                "soda", true, "First Song", "Second Song", "Same Artist", "Same Artist",
                180_000L, 180_000L, "track:7031318019544614913",
                "track:7290011223344556677"));
        assertTrue(MusicStateStore.shouldKeepSodaTrackIdentity(
                "soda", true, "First Song", "a live lyric line", "Same Artist", "Same Artist",
                180_000L, 180_000L, "track:7031318019544614913",
                "track:7031318019544614913"));
    }

    @Test public void sodaTrackKeyUsesNativeCatalogId() {
        String first = MusicStateStore.lyricTrackKey("soda", "Same", "Same", 180_000L,
                "track:7031318019544614913", "auto", true);
        String second = MusicStateStore.lyricTrackKey("soda", "Same", "Same", 180_000L,
                "track:7290011223344556677", "auto", true);
        assertFalse(first.equals(second));
    }

    @Test public void keepsSodaArtistWhenItIsReplacedByDynamicMetadata() {
        assertTrue(MusicStateStore.shouldKeepSodaTrackIdentity(
                "soda", true, "Deadman", "Deadman", "蔡徐坤", "你早知我沉溺",
                201_000L, 201_000L));
    }

    @Test public void parsesObservedSodaMetadataIntoStableTrackIdentity() {
        assertEquals("Die For You", MusicStateStore.sodaTitleFromDynamicArtist(
                "Die For You — VALORANT​, Grabbitz"));
        assertEquals("VALORANT​, Grabbitz", MusicStateStore.sodaStableArtist(
                "Die For You", "Die For You — VALORANT​, Grabbitz"));
        assertEquals("蔡徐坤", MusicStateStore.sodaStableArtist(
                "Deadman", "Deadman-蔡徐坤"));
        assertEquals("Grabbitz", MusicStateStore.sodaStableArtist(
                "Die For You", "Grabbitz"));
        assertEquals("", MusicStateStore.sodaTitleFromDynamicArtist("VALORANT​, Grabbitz"));
    }

    @Test public void liveMetadataTitleBecomesLyricOnlyAfterCatalogsMiss() {
        assertFalse(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "soda", false, LrcTimeline.EMPTY, "You know I adore ya"));
        assertTrue(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "soda", true, LrcTimeline.EMPTY, "You know I adore ya"));
        assertTrue(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "qqmusic", true, LrcTimeline.EMPTY, "You know I adore ya"));
        assertTrue(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "netease", true, LrcTimeline.EMPTY, "You know I adore ya"));
        assertTrue(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "media", true, LrcTimeline.EMPTY, "You know I adore ya"));
        assertEquals("You know I adore ya",
                LrcTimeline.liveLine("You know I adore ya").lyric);
    }

    @Test public void keepsQqMusicTitleWhenItBecomesALiveLyric() {
        assertTrue(MusicStateStore.shouldKeepLiveLyricTrackIdentity(
                "qqmusic", true, "Song Name", "a live lyric line", "Artist", "Artist",
                269_000L, 269_000L, "song-mid-a", "song-mid-a"));
        assertFalse(MusicStateStore.shouldKeepLiveLyricTrackIdentity(
                "qqmusic", true, "Song Name", "Next Song", "Artist", "Artist",
                269_000L, 269_000L, "song-mid-a", "song-mid-b"));
    }

    @Test public void allPlayersKeepTitleOnlyLiveLyricUpdates() {
        for (String source : Arrays.asList("netease", "qqmusic", "kugou", "kuwo", "soda", "media")) {
            assertTrue(source, MusicStateStore.shouldKeepLiveLyricTrackIdentity(
                    source, true, "Song Name", "a current lyric line", "Artist", "Artist",
                    269_000L, 269_000L, "track-a", "track-a"));
            assertFalse(source, MusicStateStore.shouldKeepLiveLyricTrackIdentity(
                    source, true, "Song Name", "Next Song", "Artist", "Next Artist",
                    269_000L, 269_000L, "track-a", "track-b"));
        }
    }

    @Test public void netEaseDirectSongIdStillRefreshesIdentity() {
        String first = MusicStateStore.lyricTrackKey("netease", "夜曲", "周杰伦",
                -1L, "", "auto", true);
        String direct = MusicStateStore.lyricTrackKey("netease", "夜曲", "周杰伦",
                226_000L, "song:123456", "auto", true);
        assertFalse(first.equals(direct));
    }

    @Test public void netEaseUnsupportedAutoScrollStatusIsNeverUsedAsALyricLine() {
        assertTrue(MusicStateStore.isNetEaseAutoScrollUnsupported(
                "netease", "该歌词不支持自动滚动"));
        assertTrue(MusicStateStore.isNetEaseAutoScrollUnsupported(
                "netease", "该 歌词 不支持 自动滚动"));
        assertFalse(MusicStateStore.isNetEaseAutoScrollUnsupported(
                "qqmusic", "该歌词不支持自动滚动"));
        assertFalse(MusicStateStore.isNetEaseAutoScrollUnsupported("netease", "正常歌词"));
    }

    @Test public void playerCatalogRuleOverridesOnlyThatPlayersDefault() {
        assertEquals("qqmusic", AppPreferences.resolveLyricCatalog("qqmusic", "netease"));
        assertEquals("netease", AppPreferences.resolveLyricCatalog("", "netease"));
        assertEquals("auto", AppPreferences.resolveLyricCatalog(null, "unknown"));
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
