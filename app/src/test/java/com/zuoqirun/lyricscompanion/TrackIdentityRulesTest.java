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

    @Test public void aDifferentChannelOrCatalogIsNotAnotherTrack() {
        // 播放位置锚点用这个判定（review #80 / Codex P2）：入参里根本没有来源通道 / 词库设置，
        // 所以蓝牙 AVRCP 与 MediaSession 交接同一首歌、或播放中改词库时，都不会被当成换了歌、
        // 把位置清零。播放器把歌词行写进歌手栏那种（#77）也不在这里判 —— 调用点传进来的是已经过
        // #68 / #77 判定修正后的元数据，那条路径上歌手已经被换回已存歌手。
        assertFalse(TrackIdentityRules.isDifferentTrackMetadata(
                "地下铁", "地下铁", "萧亚轩", "萧亚轩", "", ""));
        assertFalse(TrackIdentityRules.isDifferentTrackMetadata(
                "地下铁", "地下铁", "萧亚轩", "萧亚轩", "12345", "12345"));
        // 标点与空白差异不算"换了歌"。
        assertFalse(TrackIdentityRules.isDifferentTrackMetadata(
                "地下铁", "地 下 铁 -", "萧亚轩", "萧 亚 轩", "", ""));
        // 只有一边知道歌手 / 媒体 ID 时不算证据：交接时蓝牙那一路通常就没有媒体 ID。
        assertFalse(TrackIdentityRules.isDifferentTrackMetadata(
                "地下铁", "地下铁", "", "萧亚轩", "", "12345"));
        assertFalse(TrackIdentityRules.isDifferentTrackMetadata(
                "地下铁", "地下铁", "萧亚轩", "萧亚轩", "", "12345"));
        // 任一侧还没有歌名时不当作换歌（没有证据）。
        assertFalse(TrackIdentityRules.isDifferentTrackMetadata(
                "", "红蜻蜓", "萧亚轩", "小虎队", "", ""));
        assertFalse(TrackIdentityRules.isDifferentTrackMetadata(
                "地下铁", "", "萧亚轩", "小虎队", "", ""));
        assertFalse(TrackIdentityRules.isDifferentTrackMetadata(null, null, null, null, null, null));
    }

    @Test public void aSameTitleSwitchToAnotherSongIsStillATrackSwitch() {
        // 同名但确实是另一首（翻唱 / 现场版 / 同名曲目）：只看歌名会漏掉，带过来的旧位置就不会被修正
        // —— 那还是 issue #76 的症状（review 第二轮 P2）。
        assertTrue(TrackIdentityRules.isDifferentTrackMetadata(
                "地下铁", "地下铁", "萧亚轩", "小虎队", "", ""));
        // 两边媒体 ID 都知道且不同，同样是明确的换歌证据。
        assertTrue(TrackIdentityRules.isDifferentTrackMetadata(
                "地下铁", "地下铁", "萧亚轩", "萧亚轩", "12345", "67890"));
        // 歌名不同当然也是另一首。
        assertTrue(TrackIdentityRules.isDifferentTrackMetadata(
                "地下铁", "红蜻蜓", "萧亚轩", "小虎队", "", ""));
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
