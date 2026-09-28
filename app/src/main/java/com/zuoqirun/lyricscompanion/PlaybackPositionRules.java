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
    /** 上报的位置值连续这么久没有变过，就认为播放器卡在同一个值上（review #80）。 */
    static final long STALE_REPORT_LEAD_MS = 1_500L;
    /** 新曲目开头附近的位置不算残留：这一段本来就不需要"修正"。 */
    static final long TRACK_START_TOLERANCE_MS = 3_000L;

    private PlaybackPositionRules() { }

    /**
     * 切歌这一轮上报的位置是否明显是上一首的遗留值（应当忽略它、让新曲目从 0 开始）。
     *
     * <p>注意调用方传进来的必须只是「曲目自己的元数据是不是真的换了」
     * （{@link TrackIdentityRules#isDifferentTrackMetadata}：歌名 / 歌手 / 媒体 ID），不能是
     * {@code MusicStateStore.lyricTrackKey()} 那种带来源通道 / 词库设置的曲目身份 —— 同一首歌在蓝牙
     * AVRCP 与 MediaSession 之间交接、或播放中改词库时它同样会变，而这两种情况下位置是连续有效的，
     * 拿来判残留会把歌词打回开头（review #80 / Codex P2）。
     *
     * <p>两类证据：位置值在切歌前后**没动过**（连续同一份上报），或者**明显超出**新曲目的时长。
     * 位置正好等于时长不算证据 —— 播放器把新选中的曲目停在"已完成"位置是合法状态，而暂停的会话照样
     * 会显示，判成残留会把歌词打回开头（review 第三轮 P2）。
     *
     * @param identityChanged      这一轮是不是真的换成了另一首歌（只认曲目自己的元数据）
     * @param hadPreviousTrack     之前已经有一首曲目的身份（首次收到元数据时不算切歌）
     * @param previousReportedMs   切歌前最后一次上报的位置
     * @param incomingMs           本轮上报的位置
     * @param incomingDurationMs   本轮上报的曲目时长，未知时传 {@code <= 0}
     */
    static boolean staleOnTrackChange(boolean identityChanged, boolean hadPreviousTrack,
                                      long previousReportedMs, long incomingMs,
                                      long incomingDurationMs) {
        // 首次收到元数据时不算切歌，也就没有"上一首的残留"可言。
        if (!identityChanged || !hadPreviousTrack || incomingMs <= 0L) return false;
        // 位置值在切歌前后一模一样：位置不可能"刚好"没动，只可能是播放器还没换到新曲目。
        boolean carriedOver = previousReportedMs > 0L
                && Math.abs(incomingMs - previousReportedMs) <= SAME_POSITION_TOLERANCE_MS;
        // 明显越界（严格的 >）：新曲目不可能已经播过整首时长之后。等于时长要落到 carriedOver 去判。
        if (incomingDurationMs > 0L && incomingMs > incomingDurationMs) return true;
        // 开头附近：不需要修正（新歌本来就从这里开始）。
        if (incomingMs <= TRACK_START_TOLERANCE_MS) return false;
        return carriedOver;
    }

    /**
     * 播放器上报的原始位置是否已经不值得采信：位置值在超过 {@link #STALE_REPORT_LEAD_MS} 的时间里
     * 一直没有变，或者上一轮已经把它判成了切歌残留。
     *
     * <p>必须按「位置值距上次变化过了多久」判断，不能拿「我们自己的估计领先它多少」判断：这一轮一旦
     * 采信了上报值，调用方就会把锚点的值和时刻一起刷成这次上报，下一轮的领先量又从零开始长 —— 车机
     * 一两秒报一次（或更快）时永远长不到阈值，歌词就冻在那句上（review #80 / Codex P2）。
     *
     * @param positionChanged     本轮上报的位置值相对上一次是否真的变了
     * @param previouslyUntrusted 上一轮的上报是否已被判为切歌残留：残留值要等它真的变了再采信，
     *                            否则新曲目的锚点（0）下一轮就被旧值写回去
     * @param unchangedForMs      位置值距上次变化已经过去了多久
     */
    static boolean isStaleReport(boolean positionChanged, boolean previouslyUntrusted,
                                 long unchangedForMs) {
        if (positionChanged) return false;
        return previouslyUntrusted || unchangedForMs > STALE_REPORT_LEAD_MS;
    }

    /**
     * 已经被判为残留下来的那份上报，是否已经"降回"新曲目该在的位置、可以重新采信。
     *
     * <p>光看"值变了"不够：车机切歌之后可能还在继续推进**上一首**的位置 —— 值一直在变，但都是旧曲目的
     * （review 第六轮 P2）。只有位置明显**低于**被判残留的那个值（回到新曲目开头附近，或者用户往后
     * seek 回来）才算它回到了新曲目的时间轴上。
     */
    static boolean residualReleased(long rejectedMs, long incomingMs) {
        return rejectedMs - incomingMs > SAME_POSITION_TOLERANCE_MS;
    }

    /**
     * 是否应当保留伴侣自己的单调估计，而不是把锚点写回这次上报的位置。
     *
     * <p>覆盖三种情况（前两种是原有行为，第三种是 issue #76 新增）：
     * <ol>
     *     <li>瞬时零位置：导航提示等场景把位置短暂报成 0，别让歌词跳回开头；</li>
     *     <li>播放器没给位置时间戳：保持自己的估计；</li>
     *     <li>给了新鲜时间戳、但这份上报已经被 {@link #isStaleReport} 判为卡住的旧值：继续用自己的
     *         时钟（旧值可能比我们的估计大，也可能小）；暂停时把估计**冻在原地**，而不是因为
     *         {@code playing == false} 就重新采信旧值 —— 否则暂停会让歌词往回跳、恢复播放又从那个
     *         旧值接着走（review 第三轮 P2）。</li>
     * </ol>
     *
     * @param trustedPositionChanged 位置值是否**可信地**变了：调用方在"这份上报已被判为残留、还没降
     *                               回来"时必须传 {@code false}，否则旧曲目继续推进的位置会被当成
     *                               新曲目的位置写进锚点（review 第六轮 P2）
     */
    static boolean keepMonotonicEstimate(boolean changed, boolean playing,
                                         boolean trustedPositionChanged, boolean timestampPresent,
                                         boolean transientZero, boolean staleReport) {
        if (changed) return false;
        if (transientZero) return true;
        // 位置值可信地动了：以这次上报为准（用户 seek 过，或者播放器终于追上来了）。
        if (trustedPositionChanged) return false;
        if (staleReport) return true;
        if (!playing) return false;
        return !timestampPresent;
    }
}
