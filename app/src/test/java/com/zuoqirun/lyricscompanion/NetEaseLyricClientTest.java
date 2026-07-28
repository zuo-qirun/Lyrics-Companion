package com.zuoqirun.lyricscompanion;

import org.junit.Test;

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
}
