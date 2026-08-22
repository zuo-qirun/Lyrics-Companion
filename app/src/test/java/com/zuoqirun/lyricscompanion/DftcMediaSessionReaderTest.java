package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Covers the transient-hold rules that keep the Dongfeng vendor session from flapping. */
public class DftcMediaSessionReaderTest {
    @Test public void usableReadRequiresNonEmptyTitleAndKnownStatus() {
        assertTrue(DftcMediaSessionReader.isUsableRead("夜曲", 1));
        assertTrue(DftcMediaSessionReader.isUsableRead(" 夜曲 ", 0));
        assertFalse(DftcMediaSessionReader.isUsableRead("", 1));
        assertFalse(DftcMediaSessionReader.isUsableRead("   ", 0));
        assertFalse(DftcMediaSessionReader.isUsableRead("夜曲", -1));
        assertFalse(DftcMediaSessionReader.isUsableRead(null, 1));
    }

    @Test public void retainsSnapshotOnlyWithinHoldWindowWhileBinderAlive() {
        assertTrue(DftcMediaSessionReader.shouldReuseRetainedSnapshot("夜曲", true, 0L));
        assertTrue(DftcMediaSessionReader.shouldReuseRetainedSnapshot(
                " 夜曲 ", true, 14_999L));
        // The window is bounded so a player that truly exits is not shown forever.
        assertFalse(DftcMediaSessionReader.shouldReuseRetainedSnapshot(
                "夜曲", true, 15_000L));
        assertFalse(DftcMediaSessionReader.shouldReuseRetainedSnapshot(
                "夜曲", true, -1L));
    }

    @Test public void neverRetainsAcrossDeadBinderOrWithoutTitle() {
        assertFalse(DftcMediaSessionReader.shouldReuseRetainedSnapshot(
                "夜曲", false, 0L));
        assertFalse(DftcMediaSessionReader.shouldReuseRetainedSnapshot("", true, 0L));
        assertFalse(DftcMediaSessionReader.shouldReuseRetainedSnapshot(null, true, 0L));
    }

    @Test
    public void lyricLineMutationsAreHeldOnTheAnchoredTrackWhilePlaying() {
        // Diagnosed on a Dongfeng unit (2026-08-22): GET_NAME alternates between the real song
        // title and every lyric/credit line of the same song while playing.
        assertFalse(DftcMediaSessionReader.shouldAcceptTitleChange(
                "只要你过得比我好 - 钟镇涛 (Kenny Bee)", "", "词：小虫", "", 1));
        assertFalse(DftcMediaSessionReader.shouldAcceptTitleChange(
                "弯弯的月亮", "", "唱着那古老的歌谣", "", 1));
        assertFalse(DftcMediaSessionReader.shouldAcceptTitleChange(
                "冬天里的一把火 - 费翔 (Kris Phillips)", "", "熊熊火焰温暖了我的心窝", "", 1));
    }

    @Test
    public void structuredTitlesAndBoundariesStillSwitchTracks() {
        // Artist-split streamed titles are genuine switches even mid-song.
        assertTrue(DftcMediaServiceAccept("别亦难 - 徐小凤"));
        assertTrue(DftcMediaServiceAccept("冬天里的一把火 - 费翔 (Kris Phillips)"));
        // USB/file-style titles with track-number prefixes or quality tags are genuine.
        assertTrue(DftcMediaServiceAccept("0009.赵洋-负我不负她～CD音轨WAV真无损《抖音神曲》"));
        assertTrue(DftcMediaServiceAccept("0007.黑大婶回乡带娃-苹果香(黑大婶版)（热门伤感神曲 ）WAV真无损"));
        assertTrue(DftcMediaServiceAccept("弯弯的月亮"));
        // A pause boundary is always a safe switch point, and so is a player-source change.
        assertTrue(DftcMediaReaderAcceptStatus("像个孩子似的神情忘不掉", 0));
        assertTrue(DftcMediaReaderAcceptType("弯弯的月亮", "bluetooth", 1));
        // First read anchors, repeats refresh, and empty anchors accept anything.
        assertTrue(DftcMediaReaderAcceptFirst("随便什么"));
    }

    private static boolean DftcMediaServiceAccept(String incoming) {
        return DftcMediaSessionReader.shouldAcceptTitleChange(
                "弯弯的月亮", "", incoming, "", 1);
    }

    private static boolean DftcMediaReaderAcceptStatus(String incoming, int status) {
        return DftcMediaSessionReader.shouldAcceptTitleChange(
                "只要你过得比我好 - 钟镇涛 (Kenny Bee)", "", incoming, "", status);
    }

    private static boolean DftcMediaReaderAcceptType(String incoming, String type, int status) {
        return DftcMediaSessionReader.shouldAcceptTitleChange(
                "弯弯的月亮", "", incoming, type, status);
    }

    private static boolean DftcMediaReaderAcceptFirst(String incoming) {
        return DftcMediaSessionReader.shouldAcceptTitleChange("", "", incoming, "", 1)
                && DftcMediaSessionReader.shouldAcceptTitleChange(
                        "弯弯的月亮", "", "弯弯的月亮", "", 1);
    }
}
