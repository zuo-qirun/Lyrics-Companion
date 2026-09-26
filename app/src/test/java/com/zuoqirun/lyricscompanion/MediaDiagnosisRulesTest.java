package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** 「为什么没歌词」的判定（issue #62）：U 盘 / 视频场景要能一眼分清原因。 */
public class MediaDiagnosisRulesTest {
    @Test public void noSessionIsReportedAsNoMetadata() {
        assertEquals(MediaDiagnosisRules.Verdict.NO_SESSION, MediaDiagnosisRules.classify(
                false, false, false, false, false, ""));
        assertEquals(MediaDiagnosisRules.Verdict.NO_SESSION, MediaDiagnosisRules.classify(
                true, false, true, false, false, ""));
        String text = MediaDiagnosisRules.verdictText(MediaDiagnosisRules.Verdict.NO_SESSION, "");
        assertTrue(text, text.contains("媒体会话"));
    }

    @Test public void titleWithoutProgressIsItsOwnVerdict() {
        assertEquals(MediaDiagnosisRules.Verdict.TITLE_NO_PROGRESS, MediaDiagnosisRules.classify(
                true, true, false, true, false, ""));
        String text = MediaDiagnosisRules.verdictText(
                MediaDiagnosisRules.Verdict.TITLE_NO_PROGRESS, "");
        assertTrue(text, text.contains("进度"));
    }

    @Test public void matchedCatalogIsNamedInTheVerdict() {
        MediaDiagnosisRules.Verdict verdict = MediaDiagnosisRules.classify(
                true, true, true, true, true, "网易云音乐");
        assertEquals(MediaDiagnosisRules.Verdict.MATCHED, verdict);
        assertTrue(MediaDiagnosisRules.verdictText(verdict, "网易云音乐").contains("网易云音乐"));
    }

    @Test public void localLrcHitIsDistinguishableFromEmbedded() {
        String local = MediaDiagnosisRules.verdictText(MediaDiagnosisRules.Verdict.MATCHED, "本地LRC");
        String embedded = MediaDiagnosisRules.verdictText(MediaDiagnosisRules.Verdict.MATCHED,
                "内嵌歌词");
        assertNotEquals(local, embedded);
        assertTrue(local, local.contains("本地LRC"));
    }

    @Test public void lyricsLoadedButEmptyStaysUnmatched() {
        assertEquals(MediaDiagnosisRules.Verdict.NOT_FOUND, MediaDiagnosisRules.classify(
                true, true, true, true, false, ""));
        assertEquals(MediaDiagnosisRules.Verdict.LOADING, MediaDiagnosisRules.classify(
                true, true, true, false, false, ""));
    }

    @Test public void neverClaimsAMatchWithoutASourceName() {
        // 没有来源名时只能说「已就绪」，不能写「已匹配词库」
        String text = MediaDiagnosisRules.verdictText(MediaDiagnosisRules.Verdict.MATCHED, "");
        assertTrue(text, !text.contains("已匹配"));
        // 未命中时给出 U 盘可行的两条出路
        String missing = MediaDiagnosisRules.verdictText(MediaDiagnosisRules.Verdict.NOT_FOUND, "");
        assertTrue(missing, missing.contains(".lrc"));
        assertTrue(missing, missing.contains("本地歌词"));
    }

    @Test public void titleOnlyMatchingDelegatesToTheExistingRules() {
        // 「只有文件名」的视频 / U 盘标题仍然能进同一条按歌名匹配的链路
        assertTrue(MediaDiagnosisRules.acceptsTitleOnlyMatching("media", "Track01.mp3", ""));
        assertTrue(MediaDiagnosisRules.acceptsTitleOnlyMatching("media", "夜曲", "周杰伦"));
    }
}
