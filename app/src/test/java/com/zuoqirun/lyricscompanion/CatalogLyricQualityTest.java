package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class CatalogLyricQualityTest {
    @Test public void sparseOrPlaceholderCatalogResultsDoNotStopFallback() {
        assertFalse(CatalogLyricQuality.usable(LrcTimeline.EMPTY));
        assertFalse(CatalogLyricQuality.usable(LrcTimeline.parse("[99:00.00]暂无歌词", "")));
        assertFalse(CatalogLyricQuality.usable(LrcTimeline.parse(
                "[00:00.00]纯音乐，请欣赏\n[00:10.00]纯音乐，请欣赏", "")));
        assertFalse(CatalogLyricQuality.usable(LrcTimeline.parse("[00:00.00]词：作者", "")));
    }

    @Test public void ordinaryLineTimedLyricsRemainUsableWithoutWordTiming() {
        assertTrue(CatalogLyricQuality.usable(LrcTimeline.parse(
                "[00:10.00]第一句\n[00:20.00]第二句", "")));
    }
}
