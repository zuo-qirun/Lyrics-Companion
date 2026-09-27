package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers issue #77: a player that writes its current lyric line (or any changing status) into the
 * ARTIST slot while the title stays the same must not be mistaken for a new track — otherwise every
 * lyric line triggers a fresh catalog match and the panel jumps between "matched" and "no lyrics".
 */
public class TrackIdentityRulesTest {
    private boolean ignore(String title, String incomingTitle, String storedArtist,
                           String incomingArtist, long storedDuration, long incomingDuration,
                           String storedId, String incomingId) {
        return TrackIdentityRules.shouldIgnoreArtistOnlyChange(
                true, title, incomingTitle, storedArtist, incomingArtist,
                storedDuration, incomingDuration, storedId, incomingId);
    }

    @Test public void changingArtistWithStableTitleIsIgnored() {
        assertTrue(ignore("地下铁", "地下铁", "萧亚轩", "下一站的出口 你等着我",
                240_000L, 240_000L, "12345", "12345"));
        // 媒体 ID 有一边未知时不算换歌证据。
        assertTrue(ignore("地下铁", "地下铁", "萧亚轩", "眼泪无声渲染画中的风雅",
                240_000L, 240_000L, "", "12345"));
        // 时长都未知时同样成立。
        assertTrue(ignore("地下铁", "地下铁", "萧亚轩", "我们都已经长大",
                -1L, -1L, "", ""));
    }

    @Test public void aRealTrackSwitchIsStillATrackSwitch() {
        // 标题变了：这是 TITLE 侧规则的事，这里必须返回 false。
        assertFalse(ignore("地下铁", "红蜻蜓", "萧亚轩", "小虎队",
                240_000L, 200_000L, "12345", "67890"));
        // 两个媒体 ID 都已知且不同。
        assertFalse(ignore("地下铁", "地下铁", "萧亚轩", "新歌手",
                240_000L, 240_000L, "12345", "67890"));
        // 时长差超过容差。
        assertFalse(ignore("地下铁", "地下铁", "萧亚轩", "新歌手",
                240_000L, 200_000L, "", ""));
    }

    @Test public void unchangedArtistOrUnknownFieldsNeverTriggerTheRule() {
        assertFalse(ignore("地下铁", "地下铁", "萧亚轩", "萧亚轩",
                240_000L, 240_000L, "12345", "12345"));
        // 标点与空白差异不算"变了"：归一化后仍然相同。
        assertFalse(ignore("地下铁", "地下铁", "萧亚轩", "萧 亚 轩",
                240_000L, 240_000L, "", ""));
        assertFalse(ignore("", "地下铁", "萧亚轩", "新歌手", 240_000L, 240_000L, "", ""));
        assertFalse(ignore("地下铁", "", "萧亚轩", "新歌手", 240_000L, 240_000L, "", ""));
        // 来源换了就是另一路发布者，不能沿用身份。
        assertFalse(TrackIdentityRules.shouldIgnoreArtistOnlyChange(
                false, "地下铁", "地下铁", "萧亚轩", "新歌手",
                240_000L, 240_000L, "12345", "12345"));
    }
}
