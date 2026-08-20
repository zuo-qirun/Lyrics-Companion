package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LocalTrackQueryRulesTest {
    @Test public void stripsUsbPathTrackNumberExtensionAndQualityTag() {
        assertEquals("夜曲", LocalTrackQueryRules.cleanFileTitle(
                "/storage/usb/Music/03. 夜曲 [24bit Hi-Res].flac"));
    }

    @Test public void recognizesArtistThenTitleFilename() {
        List<LocalTrackQueryRules.Query> queries = LocalTrackQueryRules.fallbackQueries(
                "media", "01 - 周杰伦 - 夜曲.flac", "");
        assertTrue(contains(queries, "夜曲", "周杰伦"));
    }

    @Test public void doesNotTreatOrdinaryBracketTextAsAQualityTag() {
        assertEquals("Stay (Alive)", LocalTrackQueryRules.cleanFileTitle("Stay (Alive).flac"));
    }

    @Test public void recognizesTitleThenArtistFilenameAsFallback() {
        List<LocalTrackQueryRules.Query> queries = LocalTrackQueryRules.fallbackQueries(
                "media", "夜曲 - 周杰伦.mp3", "");
        assertTrue(contains(queries, "夜曲", "周杰伦"));
    }

    @Test public void keepsOrdinaryMetadataOutOfFallbackRules() {
        assertTrue(LocalTrackQueryRules.fallbackQueries(
                "netease", "2002年的第一场雪", "刀郎").isEmpty());
    }

    @Test public void ximalayaAndDftcCanSplitCompositeTrackTitles() {
        assertTrue(contains(LocalTrackQueryRules.fallbackQueries(
                "ximalaya", "周杰伦 - 夜曲", "喜马拉雅音乐"), "夜曲", "周杰伦"));
        assertTrue(contains(LocalTrackQueryRules.fallbackQueries(
                "ximalaya", "周杰伦-夜曲", "音乐频道"), "夜曲", "周杰伦"));
        assertTrue(contains(LocalTrackQueryRules.fallbackQueries(
                "dftc_media", "周杰伦 - 夜曲", ""), "夜曲", "周杰伦"));
    }

    private static boolean contains(List<LocalTrackQueryRules.Query> queries,
                                    String title, String artist) {
        for (LocalTrackQueryRules.Query query : queries) {
            if (title.equals(query.title) && artist.equals(query.artist)) return true;
        }
        return false;
    }
}
