package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MatchScoringTest {
    @Test public void artistSegmentsSplitCommonDelimiters() {
        assertEquals(Arrays.asList("A-Lin", "黄霄雲"),
                ArtistMatchRules.segments("A-Lin/黄霄雲"));
        assertEquals(Arrays.asList("周杰伦", "费玉清", "阿信"),
                ArtistMatchRules.segments("周杰伦,费玉清 feat. 阿信"));
        assertEquals(Arrays.asList("周杰伦", "袁咏琳"),
                ArtistMatchRules.segments("周杰伦、袁咏琳"));
    }

    @Test public void artistCanonicalDropsPunctuationAndCase() {
        assertEquals("alin", ArtistMatchRules.canonical("A-Lin"));
        assertEquals("gem", ArtistMatchRules.canonical("G.E.M."));
        assertEquals("黄霄雲", ArtistMatchRules.canonical("黄霄雲"));
    }

    @Test public void artistScoreRewardsExactSegmentMatchAndPenalizesMismatch() {
        assertEquals(70, ArtistMatchRules.score("周杰伦", "周杰伦"));
        assertEquals(45, ArtistMatchRules.score("A-Lin/黄霄雲", "黄丽玲,黄霄雲"));
        assertEquals(45, ArtistMatchRules.score("周杰伦", "周杰伦、费玉清"));
        assertEquals(0, ArtistMatchRules.score("", "周杰伦"));
        assertEquals(0, ArtistMatchRules.score("周杰伦", ""));
        assertTrue(ArtistMatchRules.score("周杰伦", "林俊杰") < 0);
    }

    @Test public void softNoiseCostsLessThanHardNoise() {
        int soft = NetEaseLyricClient.matchScore("夜曲", "周杰伦", 226_000L,
                "夜曲 (Live)", "周杰伦", 226_000L);
        int hard = NetEaseLyricClient.matchScore("夜曲", "周杰伦", 226_000L,
                "夜曲 (伴奏)", "周杰伦", 226_000L);
        assertEquals(135, soft);
        assertEquals(100, hard);
        assertTrue(soft > hard);
    }

    @Test public void lxDuetCandidateScoresAboveThresholdAndStaysPlausible() {
        int score = NetEaseLyricClient.matchScore("千年之恋", "A-Lin/黄霄雲", 239_000L,
                "千年之恋 (live)", "黄丽玲,黄霄雲", 240_000L);
        assertEquals(110, score);
        assertTrue(score >= 100);
        assertTrue(NetEaseLyricClient.plausibleMatch("千年之恋", "A-Lin/黄霄雲", 239_000L,
                "千年之恋 (live)", "黄丽玲,黄霄雲", 240_000L));
    }

    @Test public void hardNoiseCandidateIsRejectedButSoftNoiseIsAccepted() {
        assertTrue(NetEaseLyricClient.plausibleMatch("夜曲", "周杰伦", 226_000L,
                "夜曲 (Live)", "周杰伦", 226_000L));
        assertFalse(NetEaseLyricClient.plausibleMatch("夜曲", "周杰伦", 226_000L,
                "夜曲 (伴奏)", "周杰伦", 226_000L));
        assertFalse(NetEaseLyricClient.plausibleMatch("夜曲", "周杰伦", 226_000L,
                "夜曲", "其他歌手", 226_000L));
    }
}
