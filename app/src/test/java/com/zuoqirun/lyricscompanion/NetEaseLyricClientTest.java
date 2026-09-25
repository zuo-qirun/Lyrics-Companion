package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NetEaseLyricClientTest {
    @Test public void exactTitleArtistAndDurationWins() {
        int exact = NetEaseLyricClient.matchScore("夜曲", "周杰伦", 226_000L,
                "夜曲", "周杰伦", 225_500L);
        int wrongArtist = NetEaseLyricClient.matchScore("夜曲", "周杰伦", 226_000L,
                "夜曲", "其他歌手", 225_500L);
        assertTrue(exact >= 200);
        assertTrue(exact > wrongArtist);
    }

    @Test public void unwantedAccompanimentVersionIsPenalized() {
        int original = NetEaseLyricClient.matchScore("晴天", "周杰伦", 269_000L,
                "晴天", "周杰伦", 269_000L);
        int accompaniment = NetEaseLyricClient.matchScore("晴天", "周杰伦", 269_000L,
                "晴天 (KTV版伴奏)", "周杰伦", 269_000L);
        assertTrue(original > accompaniment);
    }

    @Test public void parsesOnlyStandaloneNetEaseSongIds() {
        assertEquals(123456L, NetEaseLyricClient.parseSongId("123456"));
        assertEquals(123456L, NetEaseLyricClient.parseSongId("song:123456"));
        assertEquals(-1L, NetEaseLyricClient.parseSongId(
                "497605AF857F4122A09B0FFDFE3471D5"));
        assertEquals(-1L, NetEaseLyricClient.parseSongId(
                "media-497605AF857F4122A09B0FFDFE3471D5"));
    }

    @Test public void rejectsWrongArtistOrDifferentLengthBeforeSelectingSearchHit() {
        assertTrue(NetEaseLyricClient.plausibleMatch("夜曲", "周杰伦", 226_000L,
                "夜曲", "周杰伦", 225_500L));
        assertFalse(NetEaseLyricClient.plausibleMatch("夜曲", "周杰伦",
                226_000L, "夜曲", "其他歌手", 225_500L));
        assertFalse(NetEaseLyricClient.plausibleMatch("夜曲", "周杰伦",
                226_000L, "夜曲", "周杰伦", 310_000L));
        assertFalse(NetEaseLyricClient.plausibleMatch("夜曲", "周杰伦",
                226_000L, "夜曲 (Live)", "周杰伦", 225_500L));
    }

    @Test public void keepsRomanizationBesideTranslationAndWordTiming() throws Exception {
        String response = "{\"code\":200,\"lrc\":{\"lyric\":\"[00:01.000]原文\"},"
                + "\"tlyric\":{\"lyric\":\"[00:01.000]译文\"},"
                + "\"yrc\":{\"lyric\":\"[1000,2000](1000,1000,0)原(2000,1000,0)文\"},"
                + "\"yromalrc\":{\"lyric\":\"[1000,2000](1000,1000,0)ro (2000,1000,0)ma\"}}";
        LrcTimeline timeline = NetEaseLyricClient.parseLyrics(123456L, response).timeline;
        assertEquals("译文", timeline.at(1_500L).translatedLyric);
        assertEquals("ro ma", timeline.at(1_500L).romajiLyric);
        assertEquals("ro ma", LrcTimeline.fromCacheBytes(timeline.toCacheBytes())
                .at(1_500L).romajiLyric);
    }
}
