package com.zuoqirun.lyricscompanion;

import java.util.Set;

/**
 * 决定「指定应用」这条规则这一次要不要隐藏歌词（issue #43 / #28）。
 *
 * <p>两个方向：
 * <ul>
 *   <li><b>黑名单</b>：前台应用在名单里就隐藏；识别不到前台应用时不动（保持原来的行为）。</li>
 *   <li><b>白名单</b>：只在名单里的应用里显示——<b>识别不到前台应用时也隐藏</b>。这一条是
 *       「只在桌面显示」的关键：很多车机的桌面是常驻底层的，根本不发前台事件，靠黑名单永远
 *       命中不了它，而白名单不需要识别桌面就能把别处都藏起来。</li>
 * </ul>
 *
 * <p>唯一的例外是「连使用情况访问都没授权」（{@code whitelistUsable == false}）：那意味着永远
 * 识别不到任何前台应用，白名单会把歌词一律藏掉，用户只会以为应用坏了。这种可检测的情况按不隐藏
 * 处理，设置页与诊断日志都会提示去授权。
 *
 * <p>不含 Android 类型，便于单测。
 */
final class AppRuleDecision {
    /** 「上一次已知前台应用」的有效期：超过它就不再拿旧值当依据（issue #61）。 */
    static final long LAST_KNOWN_TTL_MS = 10L * 60L * 1_000L;

    private AppRuleDecision() {}

    /**
     * 这一次判定用哪个「前台应用」（issue #61）。
     *
     * <p>ROM（哈弗华阳实测）常常让 {@code queryEvents} 什么都不返回，于是黑名单永远命中不了；退一步用
     * 「上一次已知前台应用」，但只在授权可读、且旧值仍然新鲜（{@value #LAST_KNOWN_TTL_MS} 毫秒内）时
     * 才用——否则一个过期包名会把歌词莫名其妙地藏起来。白名单那边仍然按「读不到就不隐藏」的安全阀走，
     * 所以这里只影响黑名单的实际命中率。
     */
    static String effectiveForeground(String liveForeground, String lastKnownForeground,
                                      long lastKnownAgeMs, boolean accessGranted) {
        String live = liveForeground == null ? "" : liveForeground.trim();
        if (!live.isEmpty()) return live;
        if (!accessGranted) return "";
        String lastKnown = lastKnownForeground == null ? "" : lastKnownForeground.trim();
        if (lastKnown.isEmpty() || lastKnownAgeMs < 0L || lastKnownAgeMs > LAST_KNOWN_TTL_MS) {
            return "";
        }
        return lastKnown;
    }

    static boolean hides(boolean ruleEnabled, boolean whitelist, boolean whitelistUsable,
                         Set<String> listedApps, String foregroundPackage) {
        if (!ruleEnabled) return false;
        String foreground = foregroundPackage == null ? "" : foregroundPackage.trim();
        boolean listed = !foreground.isEmpty() && listedApps != null
                && listedApps.contains(foreground);
        if (!whitelist) return listed;
        if (!whitelistUsable) return false;
        return foreground.isEmpty() || !listed;
    }
}
