package com.zuoqirun.lyricscompanion;

import android.app.Activity;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

/** Real MediaSession playback + production panel, with native-heap samples and draw counts. */
public final class PlaybackMemoryFixtureActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long started;
    private long frames;
    private String mode;
    private final Runnable sample = new Runnable() {
        @Override public void run() {
            MusicSnapshot snapshot = MusicStateStore.snapshot(0);
            Runtime runtime = Runtime.getRuntime();
            Log.i("PlaybackMemory", "elapsedMs=" + (SystemClock.elapsedRealtime() - started)
                    + " mode=" + mode + " nativeBytes=" + Debug.getNativeHeapAllocatedSize()
                    + " javaBytes=" + (runtime.totalMemory() - runtime.freeMemory())
                    + " frames=" + frames + " playing=" + snapshot.playing
                    + " positionMs=" + snapshot.positionMs + " lines=" + snapshot.timelineLines.size()
                    + " listenerAgeMs=" + (SystemClock.elapsedRealtime()
                    - MusicNotificationListener.getLastSuccessfulSessionReadElapsedMs()));
            handler.postDelayed(this, 30_000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        mode = FoliaModeCatalog.normalize(getIntent().getStringExtra("mode"));
        AppPreferences.setOverlayStyle(this, false, "folia_original");
        AppPreferences.putDisplayString(this, false, AppPreferences.KEY_FOLIA_MODE, mode);
        AppPreferences.putDisplayBoolean(this, false, AppPreferences.KEY_FOLIA_RANDOM, false);
        AppPreferences.putDisplayInt(this, false, AppPreferences.KEY_FOLIA_FPS, 60);
        if (getIntent().hasExtra("background")) AppPreferences.putDisplayString(this, false,
                AppPreferences.KEY_FOLIA_ORIGINAL_BG_MODE, getIntent().getStringExtra("background"));
        AppPreferences.get(this).edit()
                .putBoolean(AppPreferences.KEY_MAIN_OVERLAY, true)
                .putBoolean(AppPreferences.KEY_SECONDARY_OVERLAY, false)
                .putBoolean(AppPreferences.KEY_LOCAL_LYRIC_ENABLED, true)
                .putBoolean(AppPreferences.KEY_LOW_RESOURCE_MODE, false)
                .putBoolean(AppPreferences.KEY_DIAGNOSTIC_UPLOAD_ENABLED, false).apply();
        MusicStateStore.initialize(this);
        LyricsDisplayService.setSettingsVisible(this, true);
        MusicNotificationListener.requestReconnect(this);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LyricsPanelView panel = new LyricsPanelView(this, false);
        panel.getViewTreeObserver().addOnDrawListener(() -> frames++);
        setContentView(panel);
        started = SystemClock.elapsedRealtime();
        handler.post(sample);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        AppPreferences.get(this).edit().putBoolean(AppPreferences.KEY_MAIN_OVERLAY, false).apply();
        stopService(new android.content.Intent(this, LyricsDisplayService.class));
        super.onDestroy();
    }
}
