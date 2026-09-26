package com.zuoqirun.lyricscompanion;

import android.annotation.TargetApi;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.util.List;

final class ForegroundAppDetector {
    /** 读不到前台应用时的原因（issue #61）：诊断与设置页都要说清是哪一种。 */
    static final String REASON_OK = "ok";
    static final String REASON_USAGE_ACCESS_MISSING = "usage_access_missing";
    static final String REASON_NO_EVENTS = "no_events";
    static final String REASON_QUERY_FAILED = "query_failed";
    static final String REASON_LEGACY_API19 = "legacy_api19";

    private ForegroundAppDetector() {}

    /** 一次探测的结果：包名 + 原因 + 最近一次前台事件的类型与时间。 */
    static final class Probe {
        final String packageName;
        final String reason;
        final int lastEventType;
        final long lastEventWallTimeMs;

        Probe(String packageName, String reason, int lastEventType, long lastEventWallTimeMs) {
            this.packageName = packageName == null ? "" : packageName.trim();
            this.reason = reason == null ? REASON_OK : reason;
            this.lastEventType = lastEventType;
            this.lastEventWallTimeMs = lastEventWallTimeMs;
        }

        boolean usable() {
            return REASON_OK.equals(reason) && !packageName.isEmpty();
        }
    }

    /**
     * 探测前台应用并说明原因（issue #61）：华阳车机上「指定应用」规则不生效，最常见的原因就是
     * {@code queryEvents} 拿不到任何事件——以前只能看到一行 {@code foreground=空}，分不清是没权限、
     * ROM 不返回事件，还是暂时还没有前台事件。
     */
    static Probe probe(Context context) {
        if (Build.VERSION.SDK_INT < 21) {
            String legacy = legacyForegroundPackage(context);
            return new Probe(legacy, legacy.isEmpty() ? REASON_LEGACY_API19 : REASON_OK, 0, 0L);
        }
        Probe probe = Api21.probe(context);
        if (probe.usable()) return probe;
        // 使用情况访问拿不到事件时，退回 4.4 的旧通道再试一次：能读到就还能用黑名单。
        String legacy = legacyForegroundPackage(context);
        if (!legacy.isEmpty()) return new Probe(legacy, REASON_OK, 0, 0L);
        return probe;
    }

    static boolean hasUsageAccess(Context context) {
        return Build.VERSION.SDK_INT < 21 || Api21.hasUsageAccess(context);
    }

    static boolean isPlayerInForeground(Context context, String playerPackage) {
        if (safe(playerPackage).isEmpty()) return false;
        String foregroundPackage = foregroundPackage(context);
        return samePackage(playerPackage, foregroundPackage);
    }

    static String foregroundPackage(Context context) {
        return probe(context).packageName;
    }

    static boolean samePackage(String playerPackage, String foregroundPackage) {
        String player = safe(playerPackage);
        return !player.isEmpty() && player.equals(safe(foregroundPackage));
    }

    @SuppressWarnings("deprecation")
    private static String legacyForegroundPackage(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(
                    Context.ACTIVITY_SERVICE);
            if (manager == null) return "";
            List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null) return "";
            return safe(tasks.get(0).topActivity.getPackageName());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @TargetApi(21)
    private static final class Api21 {
        private static final long INITIAL_LOOKBACK_MS = 24L * 60L * 60L * 1_000L;
        private static long lastEventWallTimeMs;
        private static String lastForegroundPackage = "";
        private static int lastEventType;

        private Api21() {}

        static boolean hasUsageAccess(Context context) {
            try {
                AppOpsManager manager = (AppOpsManager) context.getSystemService(
                        Context.APP_OPS_SERVICE);
                if (manager == null) return false;
                return manager.checkOpNoThrow("android:get_usage_stats", Process.myUid(),
                        context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
            } catch (Throwable ignored) {
                return false;
            }
        }

        /**
         * 探测 + 原因。注意「没授权」时不再把最近一次已知前台清空（旧实现会清）：保留下来才能在诊断里
         * 说明「上次看到的是谁、多久以前」（issue #61）。
         */
        static synchronized Probe probe(Context context) {
            if (!hasUsageAccess(context)) {
                return new Probe("", REASON_USAGE_ACCESS_MISSING, lastEventType,
                        lastEventWallTimeMs);
            }
            UsageStatsManager manager = (UsageStatsManager) context.getSystemService(
                    Context.USAGE_STATS_SERVICE);
            if (manager == null) {
                return new Probe("", REASON_QUERY_FAILED, lastEventType, lastEventWallTimeMs);
            }
            long now = System.currentTimeMillis();
            long begin = lastEventWallTimeMs > 0L
                    ? Math.max(0L, lastEventWallTimeMs - 1_000L)
                    : Math.max(0L, now - INITIAL_LOOKBACK_MS);
            int eventsSeen = 0;
            try {
                UsageEvents events = manager.queryEvents(begin, now);
                if (events == null) {
                    return new Probe("", REASON_NO_EVENTS, lastEventType, lastEventWallTimeMs);
                }
                UsageEvents.Event event = new UsageEvents.Event();
                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    eventsSeen++;
                    int type = event.getEventType();
                    boolean foreground = isForegroundEvent(type);
                    if (foreground && event.getTimeStamp() >= lastEventWallTimeMs) {
                        lastEventWallTimeMs = event.getTimeStamp();
                        lastForegroundPackage = safe(event.getPackageName());
                        lastEventType = type;
                    }
                }
                if (lastForegroundPackage.isEmpty()) {
                    return new Probe("", eventsSeen == 0 ? REASON_NO_EVENTS : REASON_NO_EVENTS,
                            lastEventType, lastEventWallTimeMs);
                }
                return new Probe(lastForegroundPackage, REASON_OK, lastEventType,
                        lastEventWallTimeMs);
            } catch (Throwable ignored) {
                return new Probe("", REASON_QUERY_FAILED, lastEventType, lastEventWallTimeMs);
            }
        }

        @SuppressLint("InlinedApi")
        private static boolean isForegroundEvent(int type) {
            return type == UsageEvents.Event.MOVE_TO_FOREGROUND
                    || (Build.VERSION.SDK_INT >= 29
                    && type == UsageEvents.Event.ACTIVITY_RESUMED);
        }
    }
}
