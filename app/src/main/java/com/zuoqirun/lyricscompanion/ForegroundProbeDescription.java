package com.zuoqirun.lyricscompanion;

import android.app.usage.UsageEvents;

/**
 * 把 {@link ForegroundAppDetector.Probe} 的结果说成人话（issue #61）。
 *
 * <p>以前诊断里只有一行 {@code foreground=空}，分不清「没授权」「ROM 的 queryEvents 什么都不返回」
 * 和「暂时还没有前台事件」。这里做纯字符串格式化，便于单测；不引用任何 Android 资源。
 */
final class ForegroundProbeDescription {
    private ForegroundProbeDescription() { }

    /** 例：{@code 前台=com.x/事件=ACTIVITY_RESUMED/2s 前}；读不到时给出原因。 */
    static String describe(String reason, String packageName, int eventType, long ageMs) {
        String value = packageName == null ? "" : packageName.trim();
        String cause = causeText(reason);
        if (!cause.isEmpty() || value.isEmpty()) {
            String detail = value.isEmpty() ? "" : " 最近=" + value + ageText(ageMs);
            return "前台=空/原因=" + (cause.isEmpty() ? "未获取到" : cause) + detail;
        }
        return "前台=" + value + "/事件=" + eventName(eventType) + ageText(ageMs);
    }

    /** 读不到前台应用的原因说明；能正常读到时空串。 */
    static String causeText(String reason) {
        if (reason == null || ForegroundAppDetector.REASON_OK.equals(reason)) return "";
        if (ForegroundAppDetector.REASON_USAGE_ACCESS_MISSING.equals(reason)) {
            return "未授权「使用情况访问」";
        }
        if (ForegroundAppDetector.REASON_NO_EVENTS.equals(reason)) {
            return "queryEvents 无事件（权限已授权，ROM 可能不返回）";
        }
        if (ForegroundAppDetector.REASON_QUERY_FAILED.equals(reason)) {
            return "queryEvents 调用失败";
        }
        if (ForegroundAppDetector.REASON_LEGACY_API19.equals(reason)) {
            return "此 Android 版本没有使用情况访问，只能靠旧的运行任务接口";
        }
        return reason;
    }

    /** 这条原因下「指定应用」规则还能不能用：黑名单能用（按上一次已知前台），白名单不行。 */
    static String ruleImpact(String reason, boolean whitelist) {
        if (reason == null || ForegroundAppDetector.REASON_OK.equals(reason)) return "";
        if (whitelist) {
            return "⚠ 当前读不到前台应用（" + causeText(reason) + "）：白名单不会生效。";
        }
        return "⚠ 当前读不到前台应用（" + causeText(reason) + "）：黑名单只按上一次已知前台应用判断。";
    }

    /** 事件类型名字，仅用于诊断；未知类型给数字。 */
    static String eventName(int type) {
        if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) return "MOVE_TO_FOREGROUND";
        if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) return "MOVE_TO_BACKGROUND";
        if (UsageEvents.Event.ACTIVITY_RESUMED == type) return "ACTIVITY_RESUMED";
        if (UsageEvents.Event.ACTIVITY_PAUSED == type) return "ACTIVITY_PAUSED";
        return type <= 0 ? "无" : String.valueOf(type);
    }

    private static String ageText(long ageMs) {
        if (ageMs < 0L) return "";
        long seconds = ageMs / 1_000L;
        if (seconds < 60L) return "/" + seconds + "s 前";
        long minutes = seconds / 60L;
        if (minutes < 60L) return "/" + minutes + "min 前";
        return "/" + (minutes / 60L) + "h 前";
    }
}
