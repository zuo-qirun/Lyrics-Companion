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
        assertSource("ximalaya", "com.ximalaya.ting.android", "");
        assertSource("ximalaya", "com.ximalaya.ting.android.car", "");
        assertSource("dftc_media", "com.dftc.media", "");
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
        assertSource("ximalaya", "vendor.player.seven", "喜马拉雅车载版");
    }

    @Test public void recognizesPlayersByPackageNameFeatures() {
        assertSource("netease", "vendor.car.cloudmusic.player", "");
        assertSource("qqmusic", "vendor.car.qqmusic.service", "");
        assertSource("kugou", "vendor.car.kugou.player", "");
        assertSource("kuwo", "vendor.car.kwmusic.player", "");
        assertSource("soda", "vendor.car.luna.music.player", "");
        assertSource("ximalaya", "vendor.car.ximalaya.player", "");
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
        assertSource("kuwo", "cn.kuwo.autolite", "");
        assertEquals("228908", MultiSourceLyricClient.directMediaId("kuwo", "kuwo", "228908"));
        assertEquals("", MultiSourceLyricClient.directMediaId("kuwo", "netease", "228908"));
        assertEquals("", MultiSourceLyricClient.directMediaId("netease", "kuwo", "228908"));
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

    @Test public void kuwoNamesAndBothCarPackagesDefaultToKuwoCatalog() {
        assertSource("kuwo", "cn.kuwo.kwmusiccar", "");
        assertSource("kuwo", "cn.kuwo.autolite", "");
        assertSource("kuwo", "vendor.qq.player", "车载酷我音乐");
        assertSource("kuwo", "com.netease.cloudmusic", "酷我定制版");
        assertEquals("kuwo", AppPreferences.resolvePlayerLyricCatalog("kuwo", "", "netease"));
        assertEquals("qqmusic", AppPreferences.resolvePlayerLyricCatalog("kuwo", "qqmusic", "auto"));
        assertEquals("netease", AppPreferences.resolvePlayerLyricCatalog("media", "", "netease"));
    }

    @Test public void ximalayaAndDftcRemainCrossCatalogSources() {
        assertEquals("", MusicAppRegistry.lyricCatalogForSource("ximalaya"));
        assertEquals("", MusicAppRegistry.lyricCatalogForSource("dftc_media"));
        assertFalse(MultiSourceLyricClient.catalogPlan(
                "ximalaya", "auto", true).providers.isEmpty());
        assertFalse(MultiSourceLyricClient.catalogPlan(
                "dftc_media", "auto", true).providers.isEmpty());
    }

    @Test public void manualCatalogCanFallbackToRecognizedPlayerCatalog() {
        MultiSourceLyricClient.CatalogPlan plan = MultiSourceLyricClient.catalogPlan(
                "qqmusic", "kugou", true);
        assertEquals(Arrays.asList("kugou", "qqmusic"), plan.priority);
        assertEquals(Arrays.asList("kugou", "qqmusic", "netease", "kuwo", "soda", "migu"),
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

    @Test public void metadataSessionOutranksPlayingShellSession() {
        assertTrue(MusicAppRegistry.selectionScore(5_000, true, false, false, false)
                > MusicAppRegistry.selectionScore(10_000, false, true, false, false));
    }

    @Test public void emptySessionCannotSuppressNotificationFallback() {
        assertFalse(MusicNotificationListener.hasUsableTitle(
                new MusicPlaybackData("", "", "", null, "", "", 0L, true,
                        MusicPlaybackData.STATE_PLAYING, 0L, 0L, 0f)));
        assertTrue(MusicNotificationListener.hasUsableTitle(
                new MusicPlaybackData("", "晴天", "周杰伦", null, "", "", 0L, true,
                        MusicPlaybackData.STATE_PAUSED, 0L, 0L, 0f)));
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
        assertEquals("Die For You", MusicStateStore.titleFromCompositeArtist(
                "Die For You — VALORANT​, Grabbitz"));
        assertEquals("VALORANT​, Grabbitz", MusicStateStore.stableArtistFromComposite(
                "Die For You", "Die For You — VALORANT​, Grabbitz"));
        assertEquals("蔡徐坤", MusicStateStore.stableArtistFromComposite(
                "Deadman", "Deadman-蔡徐坤"));
        assertEquals("Grabbitz", MusicStateStore.stableArtistFromComposite(
                "Die For You", "Grabbitz"));
        assertEquals("", MusicStateStore.titleFromCompositeArtist("VALORANT​, Grabbitz"));
    }

    /**
     * The composite identity of #16: the car receives "歌名 - 歌手" in the artist slot, so the song
     * name has to be lifted out of it — while artist names that merely contain a dash or a slash
     * stay whole.
     */
    @Test public void parsesTheSongNameOutOfACompositeArtistSlot() {
        assertEquals("稻香", MusicStateStore.titleFromCompositeArtist("稻香 - 周杰伦"));
        assertEquals("稻香", MusicStateStore.titleFromCompositeArtist("稻香 — 周杰伦"));
        assertEquals("稻香", MusicStateStore.titleFromCompositeArtist("稻香·周杰伦"));
        assertEquals("稻香", MusicStateStore.titleFromCompositeArtist("稻香-周杰伦"));
        assertEquals("稻香", MusicStateStore.titleFromCompositeArtist("稻香/周杰伦"));
        assertEquals("稻香", MusicStateStore.titleFromCompositeArtist("稻香：周杰伦"));
        assertEquals("", MusicStateStore.titleFromCompositeArtist("A-Lin"));
        assertEquals("", MusicStateStore.titleFromCompositeArtist("AC/DC"));
        assertEquals("", MusicStateStore.titleFromCompositeArtist("Lo-Fi Boy"));
        assertEquals("", MusicStateStore.titleFromCompositeArtist("周杰伦"));
        assertEquals("", MusicStateStore.titleFromCompositeArtist(""));
    }

    @Test public void compositeArtistSuffixIsCleanedOfEverySeparatorItCanCarry() {
        assertEquals("周杰伦", MusicStateStore.stableArtistFromComposite("稻香", "稻香-周杰伦"));
        assertEquals("周杰伦", MusicStateStore.stableArtistFromComposite("稻香", "稻香/周杰伦"));
        assertEquals("周杰伦", MusicStateStore.stableArtistFromComposite("稻香", "稻香·周杰伦"));
        assertEquals("周杰伦", MusicStateStore.stableArtistFromComposite("稻香", "稻香 — 周杰伦"));
        assertEquals("周杰伦", MusicStateStore.stableArtistFromComposite("稻香", "周杰伦"));
    }

    @Test public void sodaLiveMetadataDisplaysWhileCatalogLookupContinues() {
        assertTrue(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "soda", false, LrcTimeline.EMPTY, "You know I adore ya"));
        assertTrue(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "soda", true, LrcTimeline.EMPTY, "You know I adore ya"));
        assertTrue(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "qqmusic", true, LrcTimeline.EMPTY, "You know I adore ya"));
        assertTrue(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "netease", true, LrcTimeline.EMPTY, "You know I adore ya"));
        assertTrue(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "media", true, LrcTimeline.EMPTY, "You know I adore ya"));
        assertTrue(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "dftc_media", false, LrcTimeline.parse("[00:01.00]词库歌词", ""),
                "车机原生歌词"));
        assertEquals("You know I adore ya",
                LrcTimeline.liveLine("You know I adore ya").lyric);
    }

    /**
     * Issue #52, measured on 汽水音乐: the stuck-line detection rejected a timeline that actually had
     * usable lines, the player published no live lyric (or only whitespace), and the panel stayed on
     * 「即将开始」 for the whole track. A blank live lyric must neither count as available nor take
     * the display away from the matched timeline.
     */
    @Test public void blankLiveLyricNeverTakesOverTheMatchedTimeline() {
        LrcTimeline catalog = LrcTimeline.parse("[00:01.00]第一句\n[00:08.00]第二句", "");
        LrcTimeline.At catalogAt = catalog.at(2_000L);
        assertEquals("第一句", catalogAt.lyric);

        for (String blank : new String[] {"", " ", "   ", "\n", "\t\n "}) {
            assertFalse("空白实时歌词不算可用：" + blank.replace("\n", "\\n"),
                    MusicStateStore.isLiveSessionLyricFallbackAvailable(
                            "soda", true, catalog, blank, true));
            assertFalse(MusicStateStore.isLiveLyricUsable(blank));
            // The trap the old judgement walked into: rendering a blank live lyric yields empty
            // text, which is what LyricsPanelView.currentText() turns into 「即将开始」.
            assertTrue(LrcTimeline.liveLine(blank).lyric.isEmpty());
            // The hand-over that used to strand the panel: it must keep the matched line.
            assertEquals("第一句", MusicStateStore.atLocked(catalogAt, blank, false).lyric);
        }

        // 车机原生歌词分支同样按 trim 后判空（issue #52 建议 4）。
        assertFalse(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "dftc_media", false, LrcTimeline.parse("[00:01.00]词库歌词", ""), "   "));

        // 有词时行为不变：实时歌词照旧接管，匹配时间轴被判定不可用时也照样回退。
        assertTrue(MusicStateStore.isLiveLyricUsable("You know I adore ya"));
        assertTrue(MusicStateStore.isLiveSessionLyricFallbackAvailable(
                "soda", true, catalog, "You know I adore ya", true));
        assertEquals("You know I adore ya",
                MusicStateStore.atLocked(catalogAt, "You know I adore ya", true).lyric);
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
        for (String source : Arrays.asList("netease", "qqmusic", "kugou", "kuwo", "soda",
                "ximalaya", "dftc_media", "media")) {
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

    @Test public void kuwoRidChangeDistinguishesVersionsOfTheSameSong() {
        String first = MusicStateStore.lyricTrackKey("kuwo", "晴天", "周杰伦",
                269000L, "228908", "auto", true);
        String same = MusicStateStore.lyricTrackKey("kuwo", "晴天", "周杰伦",
                270000L, "MUSIC_228908", "auto", true);
        String another = MusicStateStore.lyricTrackKey("kuwo", "晴天", "周杰伦",
                269000L, "51685512", "auto", true);
        assertEquals(first, same);
        assertFalse(first.equals(another));
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
