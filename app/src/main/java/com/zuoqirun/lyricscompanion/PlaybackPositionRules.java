package com.zuoqirun.lyricscompanion;

/**
 * 播放位置锚点的纯判定（issue #76）。
 *
 * <p>两类车机行为都会让歌词"停在一行"：切歌那一瞬间播放器还在上报**上一首的位置**，以及播放中反复
 * 上报**同一个旧位置**（但每次都带一个新的位置时间戳）。{@code MusicStateStore} 的时钟是"取一个锚点
 * 再自己按速度外推"，锚点一旦被旧值反复覆盖，外推就永远停在原地。
 *
 * <p>这里的判定只描述"该不该采信这次上报"，不碰任何 Android 类型，便于单测覆盖。
 */
final class PlaybackPositionRules {
    /** 位置值相同与否的容差（毫秒）：车机上报常有 ±几十毫秒的抖动。 */
    static final long SAME_POSITION_TOLERANCE_MS = 100L;
    /** 位置没变化但时间戳新鲜时，伴侣自己的估计至少领先这么多才夺回锚点。 */
    static final long STALE_REPORT_LEAD_MS = 1_500L;
    /** 新曲目开头附近的位置不算残留：这一段本来就不需要"修正"。 */
    static final long TRACK_START_TOLERANCE_MS = 3_000L;

    private PlaybackPositionRules() { }

    /**
     * 切歌这一轮上报的位置是否明显是上一首的遗留值（应当忽略它、让新曲目从 0 开始）。
     *
     * <p>注意调用方传的是「曲目身份变了」（标题 / 歌手 / 媒体 ID），而不是"来源包名变了" ——
     * 同一首歌在两个发布通道之间切换（蓝牙 AVRCP ↔ MediaSession）时位置是连续有效的，不能重置。
     *
     * @param identityChanged      这一轮的曲目身份是否真的变了
     * @param hadPreviousTrack     之前已经有一首曲目的身份（首次收到元数据时不算切歌）
     * @param previousReportedMs   切歌前最后一次上报的位置
     * @param incomingMs           本轮上报的位置
     * @param incomingDurationMs   本轮上报的曲目时长，未知时传 {@code <= 0}
     */
    static boolean staleOnTrackChange(boolean identityChanged, boolean hadPreviousTrack,
                                      long previousReportedMs, long incomingMs,
                                      long incomingDurationMs) {
        if (!identityChanged || incomingMs <= 0L) return false;
        // 越界：新曲目不可能已经播过它的整首时长，这种值一定是上一首（或上一次会话）的残留。
        if (incomingDurationMs > 0L && incomingMs >= incomingDurationMs) return true;
        // 开头附近：不需要修正（新歌本来就从这里开始）。
        if (incomingMs <= TRACK_START_TOLERANCE_MS) return false;
        // 位置值在切歌前后一模一样：位置不可能"刚好"没动，只可能是播放器还没换到新曲目。
        return hadPreviousTrack && previousReportedMs > 0L
                && Math.abs(incomingMs - previousReportedMs) <= SAME_POSITION_TOLERANCE_MS;
    }

    /**
     * 是否应当保留伴侣自己的单调估计，而不是把锚点写回这次上报的位置。
     *
     * <p>覆盖三种情况（前两种是原有行为，第三种是 issue #76 新增）：
     * <ol>
     *     <li>瞬时零位置：导航提示等场景把位置短暂报成 0，别让歌词跳回开头；</li>
     *     <li>播放器没给位置时间戳：保持自己的估计；</li>
     *     <li>给了新鲜时间戳、但位置值一直没动，而我们的估计已经和它差出一截：这一路上报是
     *         卡住的旧值，继续用自己的时钟（差值取绝对值 —— 旧值可能比我们的估计大，也可能小）。</li>
     * </ol>
     */
    static boolean keepMonotonicEstimate(boolean changed, boolean playing,
                                         boolean positionChanged, boolean timestampPresent,
                                         boolean transientZero, long estimatedMs, long incomingMs) {
        if (changed) return false;
        if (transientZero) return true;
        if (!playing || positionChanged) return false;
        if (!timestampPresent) return true;
        return Math.abs(estimatedMs - incomingMs) > STALE_REPORT_LEAD_MS;
    }
}
