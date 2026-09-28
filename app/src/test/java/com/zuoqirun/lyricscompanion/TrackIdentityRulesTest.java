package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
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
                true, true, title, incomingTitle, storedArtist, incomingArtist,
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
        // 只有一边知道歌手 / 目录 ID 时不算证据：交接时蓝牙那一路通常就没有目录 ID。
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
        // 两边稳定目录 ID 都知道且不同，同样是明确的换歌证据。
        assertTrue(TrackIdentityRules.isDifferentTrackMetadata(
                "地下铁", "地下铁", "萧亚轩", "萧亚轩", "12345", "67890"));
        // 歌名不同当然也是另一首。
        assertTrue(TrackIdentityRules.isDifferentTrackMetadata(
                "地下铁", "红蜻蜓", "萧亚轩", "小虎队", "", ""));
    }

    @Test public void onlyCatalogSourcesYieldAStableTrackId() {
        // 只有酷我 / 汽水 / 网易云能从 mediaId 解出稳定的曲目 id；其它来源（车机的不透明 ID）一律为空，
        // 于是它们的抖动不会变成"换了歌"的证据、把同一首歌的位置清 0（review 第五轮 P2）。
        // Lyrics 侧的曲目身份（lyricTrackKey）用的也是这一个实现。
        assertEquals("12345", TrackIdentityRules.catalogTrackId("kuwo", "MUSIC_12345"));
        assertEquals("12345", TrackIdentityRules.catalogTrackId("kuwo", " 12345 "));
        assertEquals("", TrackIdentityRules.catalogTrackId("kuwo", "ab12"));
        assertEquals("123456", TrackIdentityRules.catalogTrackId("netease", "123456"));
        assertEquals("123456", TrackIdentityRules.catalogTrackId("soda", "123456"));
        assertEquals("", TrackIdentityRules.catalogTrackId("soda", "not-a-track-id"));
        assertEquals("", TrackIdentityRules.catalogTrackId("media", "12345"));
        assertEquals("", TrackIdentityRules.catalogTrackId("bluetooth", "12345"));
        assertEquals("", TrackIdentityRules.catalogTrackId("qqmusic", "12345"));
        assertEquals("", TrackIdentityRules.catalogTrackId(null, "12345"));
        // 不透明 ID 抖动时两边都解不出目录 ID —— 即使原始值不同，也不判成换歌。
        assertFalse(TrackIdentityRules.isDifferentTrackMetadata(
                "地下铁", "地下铁", "萧亚轩", "萧亚轩",
                TrackIdentityRules.catalogTrackId("media", "session-1"),
                TrackIdentityRules.catalogTrackId("media", "session-2")));
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
                false, true, "地下铁", "地下铁", "萧亚轩", "新歌手",
                240_000L, 240_000L, "12345", "12345"));
    }

    @Test public void anotherPackageSharingTheSourceIdIsAnotherPublisher() {
        // 同一个 source id 但换了应用（VLC -> Poweramp，两个都注册成 media）：不能沿用上一个应用
        // 报的歌手 —— 新包的歌词要用新应用给的歌手去匹配（review 第四轮 P2）。
        assertFalse(TrackIdentityRules.shouldIgnoreArtistOnlyChange(
                true, false, "地下铁", "地下铁", "萧亚轩", "小虎队",
                240_000L, 240_000L, "", ""));
        // 同一个发布者时才抑制。
        assertTrue(TrackIdentityRules.shouldIgnoreArtistOnlyChange(
                true, true, "地下铁", "地下铁", "萧亚轩", "小虎队",
                240_000L, 240_000L, "", ""));
    }
}
