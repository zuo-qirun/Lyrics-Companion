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
    private ForegroundAppDetector() {}

    static boolean hasUsageAccess(Context context) {
        return Build.VERSION.SDK_INT < 21 || Api21.hasUsageAccess(context);
    }

    static boolean isPlayerInForeground(Context context, String playerPackage) {
        if (safe(playerPackage).isEmpty()) return false;
        String foregroundPackage = Build.VERSION.SDK_INT >= 21
                ? Api21.foregroundPackage(context) : legacyForegroundPackage(context);
        return samePackage(playerPackage, foregroundPackage);
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

        @SuppressLint("InlinedApi")
        static synchronized String foregroundPackage(Context context) {
            if (!hasUsageAccess(context)) {
                lastEventWallTimeMs = 0L;
                lastForegroundPackage = "";
                return "";
            }
            UsageStatsManager manager = (UsageStatsManager) context.getSystemService(
                    Context.USAGE_STATS_SERVICE);
            if (manager == null) return "";
            long now = System.currentTimeMillis();
            long begin = lastEventWallTimeMs > 0L
                    ? Math.max(0L, lastEventWallTimeMs - 1_000L)
                    : Math.max(0L, now - INITIAL_LOOKBACK_MS);
            try {
                UsageEvents events = manager.queryEvents(begin, now);
                if (events == null) return "";
                UsageEvents.Event event = new UsageEvents.Event();
                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    int type = event.getEventType();
                    boolean foreground = type == UsageEvents.Event.MOVE_TO_FOREGROUND
                            || (Build.VERSION.SDK_INT >= 29
                            && type == UsageEvents.Event.ACTIVITY_RESUMED);
                    if (foreground && event.getTimeStamp() >= lastEventWallTimeMs) {
                        lastEventWallTimeMs = event.getTimeStamp();
                        lastForegroundPackage = safe(event.getPackageName());
                    }
                }
                return lastForegroundPackage;
            } catch (Throwable ignored) {
                return "";
            }
        }
    }
}
