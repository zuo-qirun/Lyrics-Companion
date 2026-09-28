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
     * @param sameSource       这一轮是否还是同一个播放器来源
     * @param storedTitle      已保存的曲目标题
     * @param incomingTitle    本轮上报的标题
     * @param storedArtist     已保存的歌手
     * @param incomingArtist   本轮上报的歌手
     * @param storedDurationMs 已保存的时长，未知时 {@code <= 0}
     * @param incomingDurationMs 本轮上报的时长，未知时 {@code <= 0}
     * @param storedMediaId    已保存的媒体 ID
     * @param incomingMediaId  本轮上报的媒体 ID
     */
    static boolean shouldIgnoreArtistOnlyChange(boolean sameSource,
                                                String storedTitle, String incomingTitle,
                                                String storedArtist, String incomingArtist,
                                                long storedDurationMs, long incomingDurationMs,
                                                String storedMediaId, String incomingMediaId) {
        if (!sameSource) return false;
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
     * 歌名是不是真的换成了另一首（播放位置锚点用：见 {@link PlaybackPositionRules#staleOnTrackChange}）。
     *
     * <p>只比歌名。来源通道、词库设置、歌手都不参与 —— 蓝牙 AVRCP 与 MediaSession 交接同一首歌、播放中改
     * 词库、或播放器把歌词行写进歌手栏时，这些字段会变而播放位置是连续有效的，拿它们判残留会把歌词打回
     * 开头（review #80 / Codex P2）。任一侧歌名为空时返回 false：没有证据就不当成换歌。
     */
    static boolean isDifferentTrackTitle(String storedTitle, String incomingTitle) {
        if (safe(storedTitle).trim().isEmpty() || safe(incomingTitle).trim().isEmpty()) return false;
        return !sameIdentityText(storedTitle, incomingTitle);
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
