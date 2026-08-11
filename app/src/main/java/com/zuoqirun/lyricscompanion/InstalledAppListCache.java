package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Process-wide, short-lived cache for the common launchable-app picker. */
final class InstalledAppListCache {
    private static final long CACHE_TTL_MS = 5 * 60_000L;
    private static final Object LOCK = new Object();
    private static List<AppChoice> cachedApps = Collections.emptyList();
    private static long cachedAtElapsedMs;

    private InstalledAppListCache() {}

    static List<AppChoice> load(Context context, Set<String> retainedPackages) {
        List<AppChoice> base;
        long now = SystemClock.elapsedRealtime();
        synchronized (LOCK) {
            if (cachedApps.isEmpty() || now - cachedAtElapsedMs >= CACHE_TTL_MS) {
                cachedApps = queryLaunchableApps(context.getApplicationContext());
                cachedAtElapsedMs = now;
            }
            base = new ArrayList<>(cachedApps);
        }
        Map<String, AppChoice> merged = new LinkedHashMap<>();
        for (AppChoice app : base) merged.put(app.packageName, app);
        if (retainedPackages != null) {
            for (String packageName : retainedPackages) {
                if (packageName != null && !packageName.trim().isEmpty()
                        && !merged.containsKey(packageName)) {
                    merged.put(packageName, new AppChoice(packageName, packageName));
                }
            }
        }
        List<AppChoice> result = new ArrayList<>(merged.values());
        Collections.sort(result, (left, right) -> String.CASE_INSENSITIVE_ORDER.compare(
                left.label, right.label));
        return result;
    }

    private static List<AppChoice> queryLaunchableApps(Context context) {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved;
        try {
            resolved = context.getPackageManager().queryIntentActivities(launcher, 0);
        } catch (Throwable ignored) {
            resolved = Collections.emptyList();
        }
        Map<String, AppChoice> unique = new LinkedHashMap<>();
        for (ResolveInfo info : resolved) {
            if (info == null || info.activityInfo == null) continue;
            String packageName = info.activityInfo.packageName;
            if (packageName == null || packageName.equals(context.getPackageName())) continue;
            CharSequence label = info.loadLabel(context.getPackageManager());
            unique.put(packageName, new AppChoice(packageName,
                    label == null ? packageName : label.toString().trim()));
        }
        return new ArrayList<>(unique.values());
    }

    static final class AppChoice {
        final String packageName;
        final String label;

        AppChoice(String packageName, String label) {
            this.packageName = packageName;
            this.label = label == null || label.isEmpty() ? packageName : label;
        }
    }
}
