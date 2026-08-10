package com.zuoqirun.lyricscompanion;

import android.app.Application;

import com.google.android.material.color.DynamicColors;
import androidx.appcompat.app.AppCompatDelegate;

public final class LyricsCompanionApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        applyMaterialTheme(AppPreferences.themeMode(this));
        CrashReporter.install(this);
        DiagnosticLog.record(this, "Application", "process started");
        DynamicColors.applyToActivitiesIfAvailable(this);
        if (AppPreferences.get(this).getBoolean(AppPreferences.KEY_DIAGNOSTIC_UPLOAD_ENABLED, false)) {
            CommunityClient.uploadPendingCrashAsync(this, null);
        }
    }

    static void applyMaterialTheme(String mode) {
        int nightMode = "light".equals(mode) ? AppCompatDelegate.MODE_NIGHT_NO
                : "dark".equals(mode) ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    @Override public void onLowMemory() {
        AlbumArtLoader.clearMemoryCache();
        super.onLowMemory();
    }

    @Override public void onTrimMemory(int level) {
        if (level >= TRIM_MEMORY_BACKGROUND && level != TRIM_MEMORY_UI_HIDDEN) {
            AlbumArtLoader.clearMemoryCache();
        }
        super.onTrimMemory(level);
    }
}
