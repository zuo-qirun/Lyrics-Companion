package com.zuoqirun.lyricscompanion;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

/** Debug-only regression checks against Android preferences, real button rendering and hit testing. */
public final class DisplayRegressionFixtureActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Context isolated = new ContextWrapper(this) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(name + "_issue_fixture", mode);
            }
        };
        try {
            SharedPreferences raw = isolated.getSharedPreferences(AppPreferences.FILE, MODE_PRIVATE);
            raw.edit().clear().putBoolean(AppPreferences.KEY_SHOW_PREVIOUS_BUTTON, false)
                    .putBoolean(AppPreferences.KEY_SHOW_PREVIOUS_BUTTON + "_secondary", true).apply();
            require(!AppPreferences.showPreviousButton(isolated, false), "legacy main copied");
            require(AppPreferences.showPreviousButton(isolated, true), "secondary override retained");
            AppPreferences.putDisplayBoolean(isolated, false, AppPreferences.KEY_SHOW_PREVIOUS_BUTTON, true);
            AppPreferences.putDisplayBoolean(isolated, false, AppPreferences.KEY_SHOW_NEXT_BUTTON, false);
            require(AppPreferences.showNextButton(isolated, true), "main does not rewrite secondary");
            AppPreferences.putDisplayBoolean(isolated, true, AppPreferences.KEY_SHOW_PLAY_PAUSE_BUTTON, false);
            require(AppPreferences.showPlayPauseButton(isolated, false), "secondary does not rewrite main");
            require(InstalledAppListCache.resolveInstalledPackage(this, "com.android.providers.downloads") != null,
                    "no-launcher installed package accepted");
            require(InstalledAppListCache.resolveInstalledPackage(this, "com.fixture.missing") == null, "missing package rejected");
            require(InstalledAppListCache.resolveInstalledPackage(this, getPackageName()) == null, "self rejected");

            AppPreferences.setOverlayStyle(isolated, false, "folia");
            AppPreferences.setOverlayStyle(isolated, true, "folia");
            AppPreferences.putDisplayBoolean(isolated, false, AppPreferences.KEY_FOLIA_RANDOM, true);
            AppPreferences.putDisplayBoolean(isolated, true, AppPreferences.KEY_FOLIA_RANDOM, true);
            AppPreferences.putDisplayString(isolated, false, AppPreferences.KEY_FOLIA_RANDOM_POOL, "tilt");
            AppPreferences.putDisplayString(isolated, true, AppPreferences.KEY_FOLIA_RANDOM_POOL, "still");
            MusicSnapshot snapshot = new MusicSnapshot(true, false, "Fixture", "Song", "Artist", null,
                    10000, 1000, false, false, "", LrcTimeline.EMPTY.at(0), Collections.emptyList());
            require("tilt".equals(AppPreferences.foliaModeForTrack(isolated, false, snapshot)), "main pool");
            require("still".equals(AppPreferences.foliaModeForTrack(isolated, true, snapshot)), "secondary pool");
            require("tilt".equals(AppPreferences.foliaModeForTrack(isolated, false, snapshot)), "recreated view retains mode");

            raw.edit().putBoolean(AppPreferences.KEY_PLAYBACK_FADE, true)
                    .putInt(AppPreferences.KEY_PLAYBACK_FADE_SECONDS, 3).apply();
            checkControls(isolated, false, false, "default", "main");
            checkControls(isolated, true, false, "compact", "secondary");
            checkControls(new DisplaySlotContext(isolated, 2), true, false, "folia", "extra");
            checkControls(isolated, false, true, "folia", "fullscreen-main");
            checkControls(isolated, true, true, "default", "fullscreen-secondary");
            android.util.Log.i("DisplayFixture", "PASS: migration, package validation, scoped random pools, five fade/touch/hover/hit-test surfaces");
            TextView result = new TextView(this);
            result.setText(getClass().getSimpleName());
            setContentView(result);
            if (getIntent().getBooleanExtra("openSettings", false)) {
                startActivity(new android.content.Intent(this, OverlayVisibilitySettingsActivity.class));
            }
        } catch (Exception error) { throw new IllegalStateException("Display fixture failed", error); }
    }

    private void checkControls(Context context, boolean secondary, boolean fullscreen, String style, String name) throws Exception {
        AppPreferences.setOverlayStyle(context, secondary, style);
        for (String key : PlaybackPreferenceMigration.KEYS) AppPreferences.putDisplayBoolean(context, secondary, key, true);
        AppPreferences.putDisplayBoolean(context, secondary, AppPreferences.KEY_SECONDARY_PLAYBACK_CONTROLS, true);
        LyricsPanelView view = new LyricsPanelView(context, secondary, fullscreen);
        view.layout(0, 0, 1600, 900);
        Field interaction = LyricsPanelView.class.getDeclaredField("playbackInteractionMs");
        interaction.setAccessible(true);
        Method draw = LyricsPanelView.class.getDeclaredMethod("drawPlaybackControls", Canvas.class, MusicSnapshot.class, float.class);
        draw.setAccessible(true);
        interaction.setLong(view, SystemClock.elapsedRealtime());
        Bitmap full = render(view, draw);
        interaction.setLong(view, SystemClock.elapsedRealtime() - 5000);
        Bitmap faded = render(view, draw);
        require(!full.sameAs(faded), name + " fades");
        Method layoutMethod = LyricsPanelView.class.getDeclaredMethod("playbackControlLayout", float.class);
        layoutMethod.setAccessible(true);
        Object layout = layoutMethod.invoke(view, getResources().getDisplayMetrics().density);
        Field centerX = layout.getClass().getDeclaredField("centerX"), centerY = layout.getClass().getDeclaredField("centerY");
        centerX.setAccessible(true); centerY.setAccessible(true);
        require(view.playbackControlAt(centerX.getFloat(layout), centerY.getFloat(layout)) == MediaControlAction.TOGGLE_PLAY_PAUSE,
                name + " faded buttons remain clickable");
        long now = SystemClock.uptimeMillis();
        MotionEvent touch = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10, 10, 0);
        view.dispatchTouchEvent(touch); touch.recycle();
        Bitmap restored = render(view, draw);
        require(full.sameAs(restored), name + " touch restores pixels");
        touch = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 10, 10, 0);
        view.dispatchTouchEvent(touch); touch.recycle();
        interaction.setLong(view, SystemClock.elapsedRealtime() - 5000);
        MotionEvent hover = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_ENTER, 10, 10, 0);
        view.onHoverEvent(hover); hover.recycle();
        Bitmap hovered = render(view, draw);
        require(full.sameAs(hovered), name + " hover restores pixels");
        write(full, name + "-full.png"); write(faded, name + "-faded.png");
        AppPreferences.get(context).edit().putInt(AppPreferences.KEY_PLAYBACK_CONTROL_SCALE, 20).apply();
        require(AppPreferences.playbackControlScale(context) == 20, "20 percent size accepted");
        view.reloadStyle();
        Bitmap small = render(view, draw); write(small, name + "-small.png");
        require(!full.sameAs(small), name + " 20 percent renders smaller");
        AppPreferences.get(context).edit().putInt(AppPreferences.KEY_PLAYBACK_CONTROL_SCALE, 100).apply();
        full.recycle(); faded.recycle(); restored.recycle(); hovered.recycle(); small.recycle();
    }

    private Bitmap render(LyricsPanelView view, Method draw) throws Exception {
        Bitmap image = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image); canvas.drawColor(0xFF09090B);
        draw.invoke(view, canvas, MusicStateStore.snapshot(0), getResources().getDisplayMetrics().density);
        return image;
    }

    private void write(Bitmap image, String name) throws Exception {
        File directory = new File(getCacheDir(), "display-regression-fixture");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Output directory");
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
    }

    private static void require(boolean success, String message) {
        if (!success) throw new IllegalStateException(message);
    }
}
