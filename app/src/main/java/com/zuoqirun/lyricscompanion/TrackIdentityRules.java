package com.zuoqirun.lyricscompanion;

import java.util.Locale;

/**
 * 曲目身份的纯判定：判断"这次元数据变化该不该当成换了一首歌"。
 *
 * <p>{@link MusicStateStore} 里已经有三条针对"播放器把歌词/状态写进元数据字段"的保护：实时歌词
 * （TITLE 变）、复合歌手（TITLE 变 + ARTIST 是「歌名 - 歌手」）、以及汽水的复合身份。本类补上
 * **反对称的那种**（issue #77）：TITLE、时长、媒体 ID 都没变，只有 ARTIST 在不断变化 —— 播放器把
 * 歌词行或状态写进了歌手栏。这种情况按换歌处理会让每一句歌词都触发一次重新匹配。
 *
 * <p>只描述判定，不碰 Android 类型，便于单测覆盖。这里的文本归一化与
 * {@code MusicStateStore.identityText()} 保持一致（小写 + 去掉标点与空白）。
 *
 * <p><b>已知取舍（review #80 / Codex P2）</b>：{@link #shouldIgnoreArtistOnlyChange} 只认「歌名没变、
 * 时长和媒体 ID 都不构成反证」，所以它同时会吞掉正常的歌手补全 —— 先报「未知歌手」再报真歌手，或新歌的
 * TITLE 先到而 ARTIST 还是上一首的，修正值在整首歌里都不会被采纳，也不会用修正后的元数据重新匹配歌词。
 * 要收窄的话需要额外证据（进来的歌手像歌词 / 状态行，或同一首歌里歌手已经变过多次）；目前按「宁可不重匹配」
 * 处理。
 */
final class TrackIdentityRules {
    /** 时长差异容差：车机上报的时长常有一两秒的抖动。 */
    static final long DURATION_TOLERANCE_MS = 2_000L;

    private TrackIdentityRules() { }

    /**
     * TITLE 稳定、只有 ARTIST 在变时，是否应当忽略这次变化（保留原曲目身份与已匹配的歌词）。
     *
     * @param sameSource       这一轮是否还是同一个播放器来源（source id）
     * @param samePublisher    这一轮是否还是同一个发布者（包名）。只比 source id 不够：VLC / Poweramp /
     *                         AIMP… 都注册成 {@code media}，换了应用却仍算"同一个来源"，于是会把上一个
     *                         应用报的歌手留下来、拿它去匹配新包的歌词（review 第四轮 P2）
     * @param storedTitle      已保存的曲目标题
     * @param incomingTitle    本轮上报的标题
     * @param storedArtist     已保存的歌手
     * @param incomingArtist   本轮上报的歌手
     * @param storedDurationMs 已保存的时长，未知时 {@code <= 0}
     * @param incomingDurationMs 本轮上报的时长，未知时 {@code <= 0}
     * @param storedMediaId    已保存的媒体 ID
     * @param incomingMediaId  本轮上报的媒体 ID
     */
    static boolean shouldIgnoreArtistOnlyChange(boolean sameSource, boolean samePublisher,
                                                String storedTitle, String incomingTitle,
                                                String storedArtist, String incomingArtist,
                                                long storedDurationMs, long incomingDurationMs,
                                                String storedMediaId, String incomingMediaId) {
        if (!sameSource || !samePublisher) return false;
        if (safe(storedTitle).trim().isEmpty() || safe(incomingTitle).trim().isEmpty()) return false;
        // 标题必须完全没变：标题变了就是真的换歌（那是 TITLE 侧规则的事）。
        if (!sameIdentityText(storedTitle, incomingTitle)) return false;
        if (safe(storedArtist).trim().isEmpty() || safe(incomingArtist).trim().isEmpty()) return false;
        if (sameIdentityText(storedArtist, incomingArtist)) return false;
        // 时长明显不同 = 换歌证据。
        if (storedDurationMs > 0L && incomingDurationMs > 0L
                && Math.abs(storedDurationMs - incomingDurationMs) > DURATION_TOLERANCE_MS) {
            return false;
        }
        // 两个媒体 ID 都已知且不同 = 明确的换歌证据；只要有一边未知就不算证据。
        String leftId = safe(storedMediaId).trim();
        String rightId = safe(incomingMediaId).trim();
        return leftId.isEmpty() || rightId.isEmpty() || leftId.equals(rightId);
    }

    /**
     * 是不是真的换成了另一首歌（播放位置锚点用：见 {@link PlaybackPositionRules#staleOnTrackChange}）。
     *
     * <p>只看曲目自己的元数据（歌名 / 歌手 / 媒体 ID）。**来源通道与词库设置不参与** —— 蓝牙 AVRCP 与
     * MediaSession 交接同一首歌、或播放中改词库时它们会变，而播放位置是连续有效的，拿它们判残留会把歌词
     * 打回开头（review #80 / Codex P2）。但只看歌名又会漏掉同名换歌（翻唱 / 现场版 / 同名曲目）——那样
     * 带过来的旧位置不会被修正，正是 issue #76 要修的症状（review 第二轮）。
     *
     * <p>歌名不同 = 另一首；歌名相同则要求「两边都知道、且确实不同」的证据（媒体 ID、其次是歌手），只有
     * 一边知道不算证据 —— 与 {@link #shouldIgnoreArtistOnlyChange} 的取证口径一致。歌手这一路是安全的：
     * 调用点传进来的是已经过 #68 / #77 判定修正后的元数据，播放器把歌词行写进歌手栏时 {@code newArtist}
     * 已经被换回已存歌手。时长不参与：车机上它经常迟到或抖动，而且它本来就不在 {@code lyricTrackKey()} 里。
     */
    static boolean isDifferentTrackMetadata(String storedTitle, String incomingTitle,
                                            String storedArtist, String incomingArtist,
                                            String storedMediaId, String incomingMediaId) {
        if (safe(storedTitle).trim().isEmpty() || safe(incomingTitle).trim().isEmpty()) return false;
        if (!sameIdentityText(storedTitle, incomingTitle)) return true;
        String leftId = safe(storedMediaId).trim();
        String rightId = safe(incomingMediaId).trim();
        if (!leftId.isEmpty() && !rightId.isEmpty() && !leftId.equals(rightId)) return true;
        return !safe(storedArtist).trim().isEmpty() && !safe(incomingArtist).trim().isEmpty()
                && !sameIdentityText(storedArtist, incomingArtist);
    }

    private static boolean sameIdentityText(String left, String right) {
        String normalizedLeft = identityText(left);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(identityText(right));
    }

    private static String identityText(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\s]+", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
