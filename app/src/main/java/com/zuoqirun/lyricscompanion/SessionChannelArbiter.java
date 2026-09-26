package com.zuoqirun.lyricscompanion;

import java.util.Locale;

/**
 * Picks which playback channel owns {@link MusicStateStore} when more than one publishes at once.
 *
 * <p>A head unit that receives audio over Bluetooth AVRCP while a phone projection session
 * (CarPlay and friends) also publishes a MediaSession gets two independent writers for the same
 * state. Every write from the other channel changes the track key, which drops the lyric
 * timeline and redraws the panel — the reported "歌词一闪一闪". This class decides, per incoming
 * signal, whether it may replace the active one.</p>
 *
 * <p>In order: a publisher that cannot identify a track never takes the slot; a challenger that
 * is far more usable than the active publisher takes over at once; one that wins because the
 * active publisher's progress has stalled takes over at once; and otherwise the challenger has to
 * wait out {@link #HOLD_MS} of silence from the active publisher. Two publishers that keep
 * writing therefore cannot take turns, and a genuine handover still goes through as soon as the
 * old publisher stops.</p>
 *
 * <p>Deliberately free of Android types so the rules are unit-testable: elapsed timestamps are
 * passed in by the caller instead of being read from {@code SystemClock}.</p>
 */
final class SessionChannelArbiter {
    /** The car receives audio over Bluetooth and reads metadata from AVRCP broadcasts. */
    static final int CHANNEL_BLUETOOTH = 0;
    /** A regular MediaSession (or notification fallback) publisher, including CarPlay. */
    static final int CHANNEL_MEDIA_SESSION = 1;

    /**
     * How long the active publisher has to stay quiet before a rival feed may take the slot.
     *
     * <p>Measured as silence, not as a plain timer. Two publishers that keep writing — AVRCP and a
     * projection session both polling every 600 ms — never let the other one reach this age, so
     * the active source stops changing and the panel stops being rebuilt. A real handover is the
     * opposite shape: the old publisher goes quiet and the new one keeps writing, and then the
     * takeover happens as soon as the silence has lasted this long.</p>
     */
    static final long HOLD_MS = 3_000L;
    /** A challenger this much better than the active channel takes over at once. */
    static final int TAKEOVER_SCORE_DELTA = 30;
    /** How long the active channel may go without a position update before it counts as stalled. */
    static final long PROGRESS_STALE_MS = 5_000L;
    /** At most one flapping diagnostic per challenger per window. */
    static final long FLAP_LOG_INTERVAL_MS = 5_000L;

    private static final int SCORE_TITLE = 40;
    private static final int SCORE_PLAYING = 10;
    private static final int SCORE_PROGRESS = 4;
    private static final int SCORE_DURATION = 2;
    private static final int SCORE_POSITION_TIMESTAMP = 1;
    /**
     * AVRCP is a degraded view of the very same playback (title, state, coarse position), so a
     * media session that can do everything AVRCP can wins: the bias plus the progress and
     * duration points keep a playing MediaSession in charge. Two fields (progress, timestamp)
     * would not be enough to out-score the bias.
     */
    private static final int BLUETOOTH_BASE = 0;
    private static final int MEDIA_SESSION_BASE = 8;

    private Signal active;
    /** The publisher that owns the slot; set together with {@link #active} on every accept. */
    private Signal activePublisher;
    private long activeLastWrittenMs;
    private Signal flapLoggedFrom;
    private long flapLoggedMs;

    /** One publisher's view of the current playback, reduced to what arbitration needs. */
    static final class Signal {
        final int channel;
        final String sourceId;
        final String packageName;
        final boolean hasTitle;
        final boolean playing;
        final boolean hasProgress;
        final boolean hasDuration;
        final boolean hasPositionTimestamp;
        final long positionUpdatedAtElapsedMs;

        Signal(int channel, String sourceId, String packageName, boolean hasTitle,
               boolean playing, boolean hasProgress, boolean hasDuration,
               long positionUpdatedAtElapsedMs) {
            this.channel = channel;
            this.sourceId = safe(sourceId).toLowerCase(Locale.ROOT);
            this.packageName = safe(packageName).toLowerCase(Locale.ROOT);
            this.hasTitle = hasTitle;
            this.playing = playing;
            this.hasProgress = hasProgress;
            this.hasDuration = hasDuration;
            this.positionUpdatedAtElapsedMs = positionUpdatedAtElapsedMs;
            this.hasPositionTimestamp = positionUpdatedAtElapsedMs > 0L;
        }

        /**
         * Classifies a write by its source id, as {@code MusicStateStore.update} receives it.
         * {@code com.android.bluetooth} is the only package MusicStateStore accepts for the
         * AVRCP channel, so narrowing on it cannot reclassify a MediaSession publisher.
         */
        static Signal of(String sourceId, String packageName, MusicPlaybackData data) {
            boolean bluetooth = "bluetooth".equals(safe(sourceId))
                    || "com.android.bluetooth".equals(safe(packageName));
            return new Signal(bluetooth ? CHANNEL_BLUETOOTH : CHANNEL_MEDIA_SESSION,
                    sourceId, packageName, hasTitle(data), isPlayingState(data),
                    hasProgress(data), data != null && data.durationMs > 0L,
                    data == null ? 0L : data.positionUpdatedAtElapsedMs);
        }

        int score() {
            int score = channel == CHANNEL_BLUETOOTH ? BLUETOOTH_BASE : MEDIA_SESSION_BASE;
            if (hasTitle) score += SCORE_TITLE;
            if (playing) score += SCORE_PLAYING;
            if (hasProgress) score += SCORE_PROGRESS;
            if (hasDuration) score += SCORE_DURATION;
            if (hasPositionTimestamp) score += SCORE_POSITION_TIMESTAMP;
            return score;
        }

        /**
         * 东风皓瀚播放器的会话（issue #75）：它的 AIDL 只给 NAME/TYPE/STATUS，数据永远是「有标题、
         * 有（恒为 0 的）进度、没有位置时间戳」，位置不会推进。别的通知型发布者同样可能没有时间戳，
         * 但它们的防抖语义必须保持不变，所以这里按包名 / 源 id 收窄。
         */
        boolean isFrozenVendorSlot() {
            return "com.dftc.media".equals(packageName) || "dftc_media".equals(sourceId);
        }

        /** Whether this feed can keep the panel populated on its own. */
        boolean isHealthy() {
            return hasTitle && hasProgress;
        }

        /**
         * Whether the feed still receives position updates instead of being a frozen snapshot.
         *
         * <p>A timestamp that appears to be in the future means the two publishers do not share a
         * clock base; that is not evidence of a stall, so the age is clamped at zero.</p>
         */
        boolean isFresh(long nowElapsedMs, long staleAfterMs) {
            if (!hasProgress) return false;
            if (!hasPositionTimestamp) return true;
            long ageMs = nowElapsedMs - positionUpdatedAtElapsedMs;
            return ageMs < 0L || ageMs < staleAfterMs;
        }

        /**
         * Whether two signals come from the same publisher. On a head unit that exposes both a
         * phone projection session and a phone audio session the two publish different source ids,
         * and alternating between them churns the state exactly like an AVRCP takeover does, so
         * any publisher change — not only a channel change — has to pass the debounce.
         */
        boolean samePublisher(Signal other) {
            return other != null && channel == other.channel
                    && sourceId.equals(other.sourceId) && packageName.equals(other.packageName);
        }

        /**
         * Whether two signals carry the same payload. Stronger than {@link #samePublisher}: a
         * refresh from one publisher changes its position and timestamp, so this is only used to
         * tell two consecutive challenger appearances apart.
         */
        boolean samePayload(Signal other) {
            return samePublisher(other)
                    && playing == other.playing && hasProgress == other.hasProgress
                    && hasDuration == other.hasDuration
                    && positionUpdatedAtElapsedMs == other.positionUpdatedAtElapsedMs;
        }

        String describe() {
            if (channel == CHANNEL_BLUETOOTH) return "蓝牙音频(com.android.bluetooth)";
            String packageValue = packageName.isEmpty()
                    ? (sourceId.isEmpty() ? "unknown" : sourceId) : packageName;
            return sourceId.isEmpty() ? packageValue : sourceId + "(" + packageValue + ")";
        }

        private static boolean hasTitle(MusicPlaybackData data) {
            return data != null && data.title != null && !data.title.trim().isEmpty();
        }

        private static boolean hasProgress(MusicPlaybackData data) {
            return data != null && data.statePresent && data.positionMs >= 0L;
        }

        private static boolean isPlayingState(MusicPlaybackData data) {
            if (data == null) return false;
            return data.state == MusicPlaybackData.STATE_PLAYING
                    || data.state == MusicPlaybackData.STATE_FAST_FORWARDING
                    || data.state == MusicPlaybackData.STATE_REWINDING;
        }

        private static String safe(String value) { return value == null ? "" : value.trim(); }
    }

    /** Outcome of one arbitration step. */
    static final class Decision {
        final boolean accepted;
        final Signal signal;
        final String reason;
        final boolean startsFlap;
        final String flapFrom;
        final String flapTo;

        private Decision(boolean accepted, Signal signal, String reason, boolean startsFlap,
                         String flapFrom, String flapTo) {
            this.accepted = accepted;
            this.signal = signal;
            this.reason = reason;
            this.startsFlap = startsFlap;
            this.flapFrom = flapFrom;
            this.flapTo = flapTo;
        }

        /** The write may proceed; {@link #reason} says why it was allowed. */
        static Decision accept(Signal signal, String reason) {
            return new Decision(true, signal, reason, false, "", "");
        }

        /** The write is dropped; the active channel keeps the state. */
        static Decision ignore(Signal signal, String reason) {
            return new Decision(false, signal, reason, false, "", "");
        }

        /** A dropped write that also has to be reported as flapping. */
        private static Decision flapping(Signal signal, String reason, Signal from, Signal to) {
            return new Decision(false, signal, reason, true, from.describe(), to.describe());
        }
    }

    /**
     * Decides one incoming signal and remembers the outcome. {@code startsFlap} on the result asks
     * the caller to write the flapping diagnostic.
     */
    Decision decide(Signal incoming, long nowElapsedMs) {
        return decide(incoming, nowElapsedMs, true);
    }

    private Decision decide(Signal incoming, long nowElapsedMs, boolean recordFlap) {
        if (incoming == null) return Decision.ignore(null, "no_signal");
        Signal owner = activePublisher;
        if (owner == null) return accept(incoming, nowElapsedMs, "first_signal", recordFlap);
        if (!incoming.hasTitle) {
            // A writer with no usable metadata must not take the slot from a publisher that can
            // still identify a track. Car media centers keep reporting PLAYING with no metadata at
            // all while a real session is active, and such a write would blank the panel.
            return Decision.ignore(active, "titleless");
        }
        if (owner.samePublisher(incoming)) {
            return accept(incoming, nowElapsedMs, "same_publisher", recordFlap);
        }

        int challengerScore = incoming.score();
        int activeScore = active.score();
        if (!active.isHealthy()) {
            return accept(incoming, nowElapsedMs, "active_unusable", recordFlap);
        }
        // The publisher that owns the slot has not written for this long; that is the debounce.
        long quietForMs = nowElapsedMs - activeLastWrittenMs;
        String reason;
        if (challengerScore - activeScore >= TAKEOVER_SCORE_DELTA) {
            reason = "score_delta";
        } else if (incoming.playing && incoming.hasPositionTimestamp
                && active.isFrozenVendorSlot() && !active.hasPositionTimestamp) {
            // 东风车机的常驻会话位置永远不动；另一路带着位置时间戳、又明确在播放的会话必须能接管，
            // 否则酷我之类的播放器永远出不来（issue #75）。
            reason = "active_no_position_evidence";
        } else if (!active.isFresh(nowElapsedMs, PROGRESS_STALE_MS)) {
            reason = "active_stalled";
        } else if (quietForMs >= HOLD_MS) {
            reason = "hold_expired";
        } else {
            reason = "flap_window";
        }
        if (!"flap_window".equals(reason)) {
            return accept(incoming, nowElapsedMs, reason, recordFlap);
        }
        // The owner keeps the slot. The limiter below only rate-limits the diagnostic; it never
        // changes the arbitration outcome.
        boolean reportFlap = recordFlap && !incoming.samePayload(flapLoggedFrom)
                && nowElapsedMs - flapLoggedMs >= FLAP_LOG_INTERVAL_MS;
        if (reportFlap) {
            flapLoggedFrom = incoming;
            flapLoggedMs = nowElapsedMs;
            return Decision.flapping(active, reason, active, incoming);
        }
        return Decision.ignore(active, reason);
    }

    /**
     * Read-only form of {@link #decide} for callers that log or remember the player package before
     * writing, so a rejected write cannot leave those side effects behind. Never mutates the
     * arbiter; the write itself still has to pass through {@code MusicStateStore.update()}, which
     * is the authoritative call.
     */
    boolean wouldAccept(Signal incoming, long nowElapsedMs) {
        if (incoming == null) return false;
        Signal owner = activePublisher;
        if (owner == null) return true;
        if (!incoming.hasTitle) return false;
        if (owner.samePublisher(incoming)) return true;
        return decide(incoming, nowElapsedMs, false).accepted;
    }

    /** Drops all history; the next write starts a new arbitration epoch. */
    void reset() {
        active = null;
        activePublisher = null;
        activeLastWrittenMs = 0L;
        flapLoggedFrom = null;
        flapLoggedMs = 0L;
    }

    /**
     * @param commit false for the read-only probe: it computes the same verdict without touching
     *               the stored state, so asking "would this be accepted?" cannot change what the
     *               next real decision sees.
     */
    private Decision accept(Signal incoming, long nowElapsedMs, String reason, boolean commit) {
        if (commit) {
            // The publisher we just accepted owns the slot, and its liveness is "it wrote now".
            // An accepted challenger therefore becomes the new owner; a refresh from the current
            // owner keeps it and only restarts the silence clock the debounce measures.
            activePublisher = incoming;
            active = incoming;
            activeLastWrittenMs = nowElapsedMs;
        }
        return Decision.accept(incoming, reason);
    }
}
