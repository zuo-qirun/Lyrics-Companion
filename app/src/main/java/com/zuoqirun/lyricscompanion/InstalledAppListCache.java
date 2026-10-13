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
        return load(context, retainedPackages, false);
    }

    /**
     * @param includeDesktop also list the system home screens.  A launcher declares
     *                       {@code CATEGORY_HOME} rather than {@code CATEGORY_LAUNCHER}, so it is
     *                       invisible to the regular query — but hiding the overlay on the desktop
     *                       needs exactly that package.
     */
    static List<AppChoice> load(Context context, Set<String> retainedPackages,
                                boolean includeDesktop) {
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
        for (AppChoice app : base) {
            if (includeDesktop || !app.desktop) merged.put(app.packageName, app);
        }
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
        Map<String, AppChoice> unique = new LinkedHashMap<>();
        for (ResolveInfo info : resolveActivities(context, Intent.CATEGORY_LAUNCHER)) {
            addChoice(context, unique, info, false);
        }
        for (ResolveInfo info : resolveActivities(context, Intent.CATEGORY_HOME)) {
            addChoice(context, unique, info, true);
        }
        return new ArrayList<>(unique.values());
    }

    private static List<ResolveInfo> resolveActivities(Context context, String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        try {
            return context.getPackageManager().queryIntentActivities(intent, 0);
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static void addChoice(Context context, Map<String, AppChoice> unique, ResolveInfo info,
                                  boolean desktop) {
        if (info == null || info.activityInfo == null) return;
        String packageName = info.activityInfo.packageName;
        if (packageName == null || packageName.equals(context.getPackageName())) return;
        AppChoice existing = unique.get(packageName);
        if (existing != null) {
            // A home screen can also expose a normal launcher entry; keep the first label and just
            // remember that this package is the desktop.
            if (desktop && !existing.desktop) {
                unique.put(packageName, new AppChoice(packageName, existing.label, true));
            }
            return;
        }
        CharSequence label = info.loadLabel(context.getPackageManager());
        unique.put(packageName, new AppChoice(packageName,
                label == null ? packageName : label.toString().trim(), desktop));
    }

    static AppChoice resolveInstalledPackage(Context context, String entered) {
        String name = entered == null ? "" : entered.trim();
        if (name.isEmpty() || name.equals(context.getPackageName())) return null;
        try {
            android.content.pm.ApplicationInfo info = context.getPackageManager().getApplicationInfo(name, 0);
            CharSequence label = info.loadLabel(context.getPackageManager());
            return new AppChoice(name, label == null ? name : label.toString());
        } catch (android.content.pm.PackageManager.NameNotFoundException | SecurityException unavailable) {
            return null;
        }
    }

    static final class AppChoice {
        final String packageName;
        final String label;
        final boolean desktop;

        AppChoice(String packageName, String label) {
            this(packageName, label, false);
        }

        AppChoice(String packageName, String label, boolean desktop) {
            this.packageName = packageName;
            this.label = label == null || label.isEmpty() ? packageName : label;
            this.desktop = desktop;
        }

        /** The label with the desktop marker the picker rows and the search box both work on. */
        String displayLabel() {
            return desktop ? label + "（桌面）" : label;
        }
    }
}
