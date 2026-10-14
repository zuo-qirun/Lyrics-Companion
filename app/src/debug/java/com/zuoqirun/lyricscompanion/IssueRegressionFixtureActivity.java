package com.zuoqirun.lyricscompanion;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.view.Display;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** DUMP-protected Android regression fixture; preference files are isolated from the application. */
public final class IssueRegressionFixtureActivity extends Activity {
    private final Set<Integer> blocked = new HashSet<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Context isolated = new ContextWrapper(this) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(name + "_issues_v2_fixture", mode);
            }
            @Override public Context createDisplayContext(Display display) {
                Context preferences = this;
                Context target = super.createDisplayContext(display);
                return new ContextWrapper(target) {
                    @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                        return preferences.getSharedPreferences(name, mode);
                    }
                    @Override public Object getSystemService(String name) {
                        return WINDOW_SERVICE.equals(name) && blocked.contains(display.getDisplayId())
                                ? null : super.getSystemService(name);
                    }
                };
            }
        };
        try {
            AppPreferences.get(isolated).edit().clear().apply();
            checkMatchingAndColors(isolated);
            checkProfile(isolated);
            if (getIntent().getBooleanExtra("displays", false)) checkDisplayRecovery(isolated);
            android.util.Log.i("IssueFixture", "PASS: matching, stable colors, profile roundtrip"
                    + (getIntent().getBooleanExtra("displays", false) ? ", virtual display recovery after 20 failures" : ""));
            TextView result = new TextView(this); result.setText(getClass().getSimpleName()); setContentView(result);
            if (getIntent().getBooleanExtra("openMain", false)) startActivity(new android.content.Intent(this, MainActivity.class));
        } catch (Exception error) { throw new IllegalStateException("Issue regression fixture failed", error); }
    }

    private void checkMatchingAndColors(Context context) throws Exception {
        MusicSnapshot pending = new MusicSnapshot(true, false, "Fixture", "Song", "", null, 10000, 2000,
                false, false, "", LrcTimeline.EMPTY.at(0), Collections.emptyList());
        Method currentText = LyricsPanelView.class.getDeclaredMethod("currentText", MusicSnapshot.class);
        currentText.setAccessible(true);
        LyricsPanelView main = new LyricsPanelView(context, false);
        main.layout(0, 0, 800, 600);
        require(!"".equals(currentText.invoke(main, pending)), "default matching message");
        AppPreferences.putDisplayBoolean(context, false, AppPreferences.KEY_HIDE_MATCHING_PLACEHOLDER, true);
        main.reloadStyle();
        require("".equals(currentText.invoke(main, pending)), "main matching blank");
        require(!"".equals(currentText.invoke(new LyricsPanelView(context, true), pending)), "secondary remains independent");
        require(main.getHeight() == 600 && main.getWidth() == 800, "blank does not collapse height");
        int chosen = AppPreferences.randomTrackColor(context, false, pending);
        require(chosen == AppPreferences.randomTrackColor(context, false, pending), "track color stable across views");
        MusicSnapshot complete = new MusicSnapshot(true, false, "Fixture", "Song", "Artist", null,
                10000, 8000, false, false, "", LrcTimeline.EMPTY.at(0), Collections.emptyList());
        require(chosen == AppPreferences.randomTrackColor(context, false, complete), "artist completion keeps color");
    }

    private void checkProfile(Context context) throws Exception {
        AppPreferences.setOverlayStyle(context, false, "folia");
        AppPreferences.putDisplayInt(context, false, AppPreferences.KEY_TEXT_SCALE, 133);
        AppPreferences.putDisplayString(context, false, AppPreferences.KEY_REFINED_BACKGROUND_TYPE, "starfield");
        AppPreferences.putDisplayString(context, false, AppPreferences.KEY_CURRENT_LYRIC_COLOR_MODE, "artwork");
        SharedPreferences raw = AppPreferences.get(context);
        raw.edit().putBoolean(AppPreferences.KEY_LOW_RESOURCE_MODE, true).apply();
        LyricsPanelView view = new LyricsPanelView(context, false);
        require("solid".equals(field(view, "refinedBackgroundType")), "effective low background");
        require(!AppPreferences.lyricFollowsArtwork(context, false, true), "artwork extraction disabled");
        require(AppPreferences.displayInt(context, false, AppPreferences.KEY_TEXT_SCALE, 100) == 133, "font preference preserved");
        require("starfield".equals(AppPreferences.displayString(context, false, AppPreferences.KEY_REFINED_BACKGROUND_TYPE, "")), "background preference preserved");
        Bitmap original = Bitmap.createBitmap(1400, 800, Bitmap.Config.ARGB_8888);
        Bitmap smaller = AlbumArtLoader.limitShared(original, true);
        require(smaller.getWidth() == 350 && !original.isRecycled(), "cover shrinks without recycling owner");
        require(smaller == AlbumArtLoader.limitShared(original, true), "repeated metadata reuses scaled cover");
        require(AlbumArtLoader.cachedKiB() <= ResourceProfile.artCacheKiB(true), "cover cache budget");
        org.json.JSONObject exported = ConfigurationCodec.exportConfiguration(context);
        require(exported.getJSONObject("settings").getJSONObject(AppPreferences.KEY_LOW_RESOURCE_MODE).getBoolean("value"), "profile exported");
        raw.edit().putBoolean(AppPreferences.KEY_LOW_RESOURCE_MODE, false).apply();
        view.reloadStyle();
        require("starfield".equals(field(view, "refinedBackgroundType")), "background restored");
        require(AppPreferences.lyricFollowsArtwork(context, false, true), "artwork choice restored");
        ConfigurationCodec.importConfiguration(context, exported);
        require(AppPreferences.lowResourceMode(context), "profile imported");
        raw.edit().putBoolean(AppPreferences.KEY_LOW_RESOURCE_MODE, false).apply();
        view.reloadStyle(); original.recycle(); smaller.recycle();
    }

    private void checkDisplayRecovery(Context context) throws Exception {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        List<ImageReader> readers = new ArrayList<>();
        List<VirtualDisplay> displays = new ArrayList<>();
        LyricsDisplayService service = new LyricsDisplayService();
        Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
        attach.setAccessible(true); attach.invoke(service, context);
        setField(service, "displayManager", manager);
        try {
            for (int i = 0; i < 3; i++) {
                ImageReader reader = ImageReader.newInstance(800, 600, PixelFormat.RGBA_8888, 2);
                readers.add(reader);
                VirtualDisplay display = manager.createVirtualDisplay("Issue fixture " + i, 800, 600, 160, reader.getSurface(), 0);
                require(display != null, "virtual display created"); displays.add(display);
            }
            Display secondary = displays.get(0).getDisplay(), healthy = displays.get(1).getDisplay(), late = displays.get(2).getDisplay();
            AppPreferences.get(context).edit().putBoolean(AppPreferences.KEY_SECONDARY_OVERLAY, true)
                    .putInt(AppPreferences.KEY_DISPLAY_ID, secondary.getDisplayId()).apply();
            DisplaySlotRegistry.putEntries(context, Arrays.asList(DisplaySlotRegistry.of(healthy), DisplaySlotRegistry.of(late)));
            blocked.add(secondary.getDisplayId()); blocked.add(late.getDisplayId());
            DisplayRecoveryBackoff secondaryRetry = (DisplayRecoveryBackoff) field(service, "secondaryRecovery");
            DisplayRecoveryBackoff extraRetry = (DisplayRecoveryBackoff) field(service, "extraRecovery");
            Object healthyPanel = null;
            for (int i = 0; i < 20; i++) {
                setField(secondaryRetry, "nextElapsedMs", -1L); setField(extraRetry, "nextElapsedMs", -1L);
                invoke(service, "showSecondary"); invoke(service, "ensureExtraOverlays");
                List<?> windows = (List<?>) field(service, "extraOverlays");
                require(windows.size() == 1, "healthy extra survives while late extra fails");
                Object panel = field(windows.get(0), "panel");
                if (healthyPanel == null) healthyPanel = panel;
                require(healthyPanel == panel, "retry must not recreate healthy extra");
            }
            require(secondaryRetry.attempts() == 20 && extraRetry.attempts() == 20, "retries pass old limit");
            blocked.clear();
            setField(secondaryRetry, "nextElapsedMs", -1L); setField(extraRetry, "nextElapsedMs", -1L);
            Method sync = LyricsDisplayService.class.getDeclaredMethod("syncOverlayVisibility", MusicSnapshot.class);
            sync.setAccessible(true); sync.invoke(service, MusicStateStore.snapshot(0));
            require(field(service, "secondaryPanel") != null, "secondary heals despite unchanged visibility");
            require(((List<?>) field(service, "extraOverlays")).size() == 2, "missing extra heals beside healthy extra");
            require(secondaryRetry.attempts() == 0 && extraRetry.attempts() == 0, "success stops retries");
        } finally {
            ((Handler) field(service, "recoveryHandler")).removeCallbacksAndMessages(null);
            invoke(service, "dismissSecondary"); invoke(service, "dismissExtras");
            for (VirtualDisplay display : displays) display.release();
            for (ImageReader reader : readers) reader.close();
        }
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void setField(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static void invoke(Object object, String name) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(object);
    }
    private static void require(boolean result, String message) {
        if (!result) throw new IllegalStateException(message);
    }
}
