package com.zuoqirun.lyricscompanion;

import java.net.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 「网络直连」的全局状态与网络故障计数（issue #153）。
 *
 * <p>车机开着 VPN 或 HTTP 代理时，六个在线词库可能同时返回 0 条结果：系统
 * {@code ProxySelector} 把请求交给一个并不转发歌词接口的代理。打开直连后，
 * {@link HttpCompat#open} 会逐条连接传入 {@link Proxy#NO_PROXY}，绕开
 * {@code ProxySelector} 直接访问词库。它只改变本应用自己的 HTTP 请求，
 * 不会关闭系统的 VPN 隧道，也不影响其它应用的代理设置。</p>
 *
 * <p>本类只依赖 JDK，方便 JVM 单元测试；Android 侧的状态在
 * {@code LyricsCompanionApp.onCreate} 里按偏好落地。</p>
 */
final class NetworkDirectPolicy {
    /** 计数只用于提示文案，封顶避免长期运行后出现难看的数字。 */
    static final int MAX_FAILURES = 999;

    private static final AtomicInteger FAILURES = new AtomicInteger();
    private static volatile boolean direct;

    private NetworkDirectPolicy() { }

    /** 当前进程实际生效的模式；打开直连后为 {@code true}。 */
    static boolean direct() {
        return direct;
    }

    /**
     * 落地开关状态。切换模式时清空失败计数，免得旧模式下的失败继续触发提示。
     */
    static void apply(boolean value) {
        direct = value;
        FAILURES.set(0);
    }

    /**
     * 返回该连接应使用的代理：直连时 {@link Proxy#NO_PROXY}，否则 {@code null}
     * 表示沿用系统默认（即 {@code ProxySelector}，与改动前完全一致）。
     */
    static Proxy proxy() {
        return direct ? Proxy.NO_PROXY : null;
    }

    /**
     * 网络歌词请求在最后一次重试仍因传输层 IO 失败时调用；HTTP 状态码错误
     * 代表连接是通的，不计入。计数封顶在 {@link #MAX_FAILURES}。
     */
    static void noteFailure() {
        int current = FAILURES.get();
        while (current < MAX_FAILURES) {
            if (FAILURES.compareAndSet(current, current + 1)) return;
            current = FAILURES.get();
        }
    }

    static int failureCount() {
        return FAILURES.get();
    }

    /** 任意一次歌词请求成功就说明链路是通的，清掉历史失败，免得诊断提示一直挂着。 */
    static void noteSuccess() {
        FAILURES.set(0);
    }

    /**
     * 把进程内状态恢复到默认值：不直连、计数为零。只给测试和诊断基线用，
     * 不读也不写偏好；线上开关走 {@link AppPreferences#setNetworkDirect}。
     */
    static void reset() {
        direct = false;
        FAILURES.set(0);
    }

    /**
     * 「音乐状态与诊断」里的一行提示：直连已开、或还没失败过时返回空串。
     */
    static String diagnosticHint() {
        int failures = FAILURES.get();
        if (direct || failures <= 0) return "";
        return "网络歌词最近 " + failures + " 次请求失败；若车机开着 VPN/代理，"
                + "请在「网络与代理」中开启「网络直连（忽略系统代理）」";
    }
}
