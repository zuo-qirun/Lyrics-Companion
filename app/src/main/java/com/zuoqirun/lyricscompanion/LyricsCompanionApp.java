package com.zuoqirun.lyricscompanion;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public final class LyricsCompanionApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
        RealtimeCaptionEngine.installLocalRecognizer(new SherpaBilingualRecognizer(this));
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
