package com.zuoqirun.lyricscompanion;

import com.google.android.material.color.DynamicColors;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

public final class LyricsCompanionApp extends MultiDexApplication {
    @Override public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
        // 按时间段的配色在启动时就要落地（issue #34），否则设置页会停在系统主题上。
        applyMaterialTheme(AppPreferences.resolvedThemeMode(this));
        DiagnosticLog.record(this, "Application", "process started");
        DynamicColors.applyToActivitiesIfAvailable(this);
        if (AppPreferences.get(this).getBoolean(AppPreferences.KEY_DIAGNOSTIC_UPLOAD_ENABLED, false)) {
            CommunityClient.uploadPendingCrashAsync(this, null);
        }
    }

    /**
     * Applies one theme mode. {@code auto} hands the decision to the system, while a scheduled
     * mode has already been resolved to light or dark by {@link AppPreferences#resolvedThemeMode}.
     */
    static void applyMaterialTheme(String mode) {
        int nightMode = "light".equals(mode) ? AppCompatDelegate.MODE_NIGHT_NO
                : "dark".equals(mode) ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    @Override public void onLowMemory() {
        recordMemoryPressure("low", -1);
        AlbumArtLoader.clearMemoryCache();
        LyricsPanelView.trimRenderMemory();
        super.onLowMemory();
    }

    @Override public void onTrimMemory(int level) {
        recordMemoryPressure("trim", level);
        if (level >= TRIM_MEMORY_RUNNING_LOW && level != TRIM_MEMORY_UI_HIDDEN) {
            AlbumArtLoader.clearMemoryCache();
            LyricsPanelView.trimRenderMemory();
        }
        super.onTrimMemory(level);
    }

    private void recordMemoryPressure(String event, int level) {
        Runtime runtime = Runtime.getRuntime();
        DiagnosticLog.record(this, "Memory", event + " level=" + level
                + " javaBytes=" + (runtime.totalMemory() - runtime.freeMemory())
                + " nativeBytes=" + android.os.Debug.getNativeHeapAllocatedSize());
    }
}
