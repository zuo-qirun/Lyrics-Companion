package com.zuoqirun.lyricscompanion;

/**
 * 酷我逐字通道（{@code mlyric.kuwo.cn}）的负缓存（issue #74）。
 *
 * <p>这条通道在部分车机 / 网络里根本连不通，而它排在歌词链路的最前面：每首歌都要先付一次连接超时
 * 才轮到老接口，用户看到的就是「酷我长时间无反应、歌词要等很久才出来」。这里把「连续失败」记成一段
 * 退避期，退避期内直接跳过这条通道（缓存与老接口照常走），成功一次立刻清零。
 *
 * <p>纯函数 + 调用方持有的两个长整型，便于在 JVM 测试里覆盖；时间由参数传入，不自己读时钟。
 */
final class WordChannelGate {
    /** 退避时长：连不上就 15 分钟内不再试，避免每首歌都白等一次超时。 */
    static final long BACKOFF_MS = 15 * 60_000L;
    /** 连续两次传输失败才退避：单次抖动（切网、刚开机）不应该把逐字通道关掉。 */
    static final int FAILURES_BEFORE_BACKOFF = 2;

    private WordChannelGate() { }

    /** 这一轮要不要走逐字通道。 */
    static boolean isOpen(long nowElapsedMs, long blockedUntilElapsedMs) {
        return blockedUntilElapsedMs <= 0L || nowElapsedMs >= blockedUntilElapsedMs;
    }

    /** 一次传输失败后的累计失败次数。 */
    static int failuresAfterFailure(int currentFailures) {
        return Math.min(FAILURES_BEFORE_BACKOFF, Math.max(0, currentFailures) + 1);
    }

    /** 按当前失败次数应封锁到什么时候；{@code 0} 表示不封锁。 */
    static long blockedUntil(long nowElapsedMs, int failures) {
        if (failures < FAILURES_BEFORE_BACKOFF) return 0L;
        return nowElapsedMs + BACKOFF_MS;
    }

    /** 还剩多久解封（毫秒），用于诊断与设置页提示。 */
    static long remainingMs(long nowElapsedMs, long blockedUntilElapsedMs) {
        if (blockedUntilElapsedMs <= 0L) return 0L;
        return Math.max(0L, blockedUntilElapsedMs - nowElapsedMs);
    }

    /**
     * 逐字通道专用的超时：连接 3 秒 / 读取 4 秒、不重试。
     *
     * <p>默认的 7 秒 / 10 秒 + 两次重试是给「一定要拿到」的主接口用的；这条通道只是锦上添花，失败
     * 必须快速落到老接口，否则一首歌就先卡十几秒。
     */
    static final int WORD_CHANNEL_CONNECT_TIMEOUT_MS = 3_000;
    static final int WORD_CHANNEL_READ_TIMEOUT_MS = 4_000;
    static final int WORD_CHANNEL_ATTEMPTS = 1;
}
