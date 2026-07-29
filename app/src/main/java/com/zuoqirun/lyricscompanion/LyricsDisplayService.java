package com.zuoqirun.lyricscompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Owns independent overlay windows on the default display and a selected secondary display. */
public final class LyricsDisplayService extends Service implements DisplayManager.DisplayListener {
    private static final String TAG = "LyricsDisplay";
    private static final String CHANNEL_ID = "lyrics_display";
    private static final int NOTIFICATION_ID = 41;
    private static final String ACTION_REFRESH =
            "com.zuoqirun.lyricscompanion.action.REFRESH";
    private static final String ACTION_SECONDARY_POSITION =
            "com.zuoqirun.lyricscompanion.action.SECONDARY_POSITION";
    private static final String ACTION_SETTINGS_VISIBILITY =
            "com.zuoqirun.lyricscompanion.action.SETTINGS_VISIBILITY";
    private static final String EXTRA_VISIBLE = "visible";
    private static final String EXTRA_DX = "dx";
    private static final String EXTRA_DY = "dy";

    private DisplayManager displayManager;
    private WindowManager mainWindowManager;
    private WindowManager.LayoutParams mainParams;
    private LyricsPanelView mainPanel;
    private Context secondaryContext;
    private Display secondaryDisplay;
    private WindowManager secondaryWindowManager;
    private WindowManager.LayoutParams secondaryParams;
    private LyricsPanelView secondaryPanel;
    private boolean settingsVisible;
    private final Handler communityHandler = new Handler(Looper.getMainLooper());
    private final Runnable communityHeartbeat = new Runnable() {
        @Override public void run() {
            CommunityClient.heartbeatAsync(getApplicationContext(), null);
            communityHandler.postDelayed(this, 60_000L);
        }
    };

    static void startOrRefresh(Context context) {
        Intent intent = new Intent(context, LyricsDisplayService.class).setAction(ACTION_REFRESH);
        if (!AppPreferences.mainEnabled(context) && !AppPreferences.secondaryEnabled(context)) {
            context.stopService(intent);
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Unable to start display service", error);
        }
    }

    static void moveSecondaryBy(Context context, int dx, int dy) {
        startCommand(context, new Intent(context, LyricsDisplayService.class)
                .setAction(ACTION_SECONDARY_POSITION)
                .putExtra(EXTRA_DX, dx).putExtra(EXTRA_DY, dy));
    }

    static void setSettingsVisible(Context context, boolean visible) {
        startCommand(context, new Intent(context, LyricsDisplayService.class)
                .setAction(ACTION_SETTINGS_VISIBILITY).putExtra(EXTRA_VISIBLE, visible));
    }

    private static void startCommand(Context context, Intent intent) {
        if (!AppPreferences.mainEnabled(context) && !AppPreferences.secondaryEnabled(context)) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to deliver display command", error);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        MusicStateStore.initialize(this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        if (MusicNotificationListener.hasNotificationAccess(this)) {
            MusicNotificationListener.requestReconnect(this);
        }
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) displayManager.registerDisplayListener(this, null);
        communityHandler.post(communityHeartbeat);
        rebuildAll();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        Log.i(TAG, "Command=" + action + " main=" + AppPreferences.mainEnabled(this)
                + " secondary=" + AppPreferences.secondaryEnabled(this)
                + " settingsVisible=" + settingsVisible);
        if (ACTION_SECONDARY_POSITION.equals(action)) {
            applySecondaryDelta(intent.getIntExtra(EXTRA_DX, 0),
                    intent.getIntExtra(EXTRA_DY, 0));
            return START_STICKY;
        }
        if (ACTION_SETTINGS_VISIBILITY.equals(action)) {
            settingsVisible = intent.getBooleanExtra(EXTRA_VISIBLE, false);
            if (settingsVisible) dismissMain();
            else rebuildAll();
            return START_STICKY;
        }
        rebuildAll();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        communityHandler.removeCallbacks(communityHeartbeat);
        if (displayManager != null) displayManager.unregisterDisplayListener(this);
        dismissMain();
        dismissSecondary();
        super.onDestroy();
    }

    @Override public void onDisplayAdded(int displayId) { rebuildSecondary(); }
    @Override public void onDisplayRemoved(int displayId) { rebuildSecondary(); }
    @Override public void onDisplayChanged(int displayId) { rebuildSecondary(); }

    private void rebuildAll() {
        Log.i(TAG, "Rebuild main=" + AppPreferences.mainEnabled(this)
                + " secondary=" + AppPreferences.secondaryEnabled(this)
                + " settingsVisible=" + settingsVisible
                + " overlayPermission=" + canDrawOverlays());
        if (!AppPreferences.mainEnabled(this) && !AppPreferences.secondaryEnabled(this)) {
            stopSelf();
            return;
        }
        if (!canDrawOverlays()) {
            dismissMain();
            dismissSecondary();
            return;
        }
        dismissMain();
        dismissSecondary();
        if (AppPreferences.mainEnabled(this) && !settingsVisible) showMain();
        if (AppPreferences.secondaryEnabled(this)) showSecondary();
    }

    private void rebuildSecondary() {
        dismissSecondary();
        if (AppPreferences.secondaryEnabled(this) && canDrawOverlays()) showSecondary();
    }

    private void showMain() {
        mainWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (mainWindowManager == null) return;
        Point screen = displaySize(mainWindowManager.getDefaultDisplay());
        int width = Math.min(dp(this, AppPreferences.panelWidthDp(this)),
                Math.max(dp(this, 220), screen.x - dp(this, 24)));
        int height = Math.min(dp(this, AppPreferences.panelHeightDp(this)),
                Math.max(dp(this, 160), screen.y - dp(this, 24)));
        mainPanel = new LyricsPanelView(this, false);
        mainParams = overlayParams(width, height);
        mainParams.x = clamp(AppPreferences.get(this).getInt(AppPreferences.KEY_MAIN_X, dp(this, 18)),
                0, Math.max(0, screen.x - width));
        mainParams.y = clamp(AppPreferences.get(this).getInt(AppPreferences.KEY_MAIN_Y, dp(this, 100)),
                0, Math.max(0, screen.y - height));
        attachDrag(mainPanel, mainWindowManager, mainParams, screen,
                AppPreferences.KEY_MAIN_X, AppPreferences.KEY_MAIN_Y, true);
        try {
            mainWindowManager.addView(mainPanel, mainParams);
            Log.i(TAG, "Main overlay attached at " + mainParams.x + "," + mainParams.y
                    + " size=" + width + "x" + height);
        } catch (Throwable error) {
            Log.e(TAG, "Unable to add main overlay", error);
            dismissMain();
        }
    }

    private void showSecondary() {
        Display display = findSecondaryDisplay();
        if (display == null) {
            Log.i(TAG, "Secondary overlay enabled, but no secondary display is connected");
            return;
        }
        try {
            secondaryDisplay = display;
            secondaryContext = createDisplayContext(display);
            secondaryWindowManager = (WindowManager) secondaryContext.getSystemService(WINDOW_SERVICE);
            if (secondaryWindowManager == null) return;
            Point screen = displaySize(display);
            int margin = dp(secondaryContext, 20);
            int width = Math.min(dp(secondaryContext, AppPreferences.panelWidthDp(this)),
                    Math.max(dp(secondaryContext, 220), screen.x - margin * 2));
            int height = Math.min(dp(secondaryContext, AppPreferences.panelHeightDp(this)),
                    Math.max(dp(secondaryContext, 160), screen.y - margin * 2));
            secondaryPanel = new LyricsPanelView(secondaryContext, true);
            secondaryParams = overlayParams(width, height);
            int defaultX = Math.max(0, (screen.x - width) / 2);
            int defaultY = Math.max(0, Math.round(screen.y * 0.10f));
            if (!AppPreferences.get(this).contains(AppPreferences.KEY_SECONDARY_X)
                    || !AppPreferences.get(this).contains(AppPreferences.KEY_SECONDARY_Y)) {
                AppPreferences.get(this).edit()
                        .putInt(AppPreferences.KEY_SECONDARY_X, defaultX)
                        .putInt(AppPreferences.KEY_SECONDARY_Y, defaultY).apply();
            }
            secondaryParams.x = clamp(AppPreferences.get(this).getInt(
                    AppPreferences.KEY_SECONDARY_X, defaultX), 0, Math.max(0, screen.x - width));
            secondaryParams.y = clamp(AppPreferences.get(this).getInt(
                    AppPreferences.KEY_SECONDARY_Y, defaultY), 0, Math.max(0, screen.y - height));
            attachDrag(secondaryPanel, secondaryWindowManager, secondaryParams, screen,
                    AppPreferences.KEY_SECONDARY_X, AppPreferences.KEY_SECONDARY_Y, false);
            secondaryWindowManager.addView(secondaryPanel, secondaryParams);
            Log.i(TAG, "Lyrics shown on display " + display.getDisplayId()
                    + " (" + display.getName() + ")");
        } catch (Throwable error) {
            Log.e(TAG, "Unable to add secondary overlay", error);
            dismissSecondary();
        }
    }

    private void applySecondaryDelta(int dx, int dy) {
        if (secondaryWindowManager == null || secondaryPanel == null || secondaryParams == null
                || secondaryDisplay == null) return;
        Point screen = displaySize(secondaryDisplay);
        secondaryParams.x = clamp(secondaryParams.x + dx, 0,
                Math.max(0, screen.x - secondaryParams.width));
        secondaryParams.y = clamp(secondaryParams.y + dy, 0,
                Math.max(0, screen.y - secondaryParams.height));
        AppPreferences.get(this).edit()
                .putInt(AppPreferences.KEY_SECONDARY_X, secondaryParams.x)
                .putInt(AppPreferences.KEY_SECONDARY_Y, secondaryParams.y).apply();
        try { secondaryWindowManager.updateViewLayout(secondaryPanel, secondaryParams); }
        catch (Throwable error) { Log.w(TAG, "Unable to move secondary overlay", error); }
    }

    private void attachDrag(View view, WindowManager manager, WindowManager.LayoutParams params,
                            Point screen, String xKey, String yKey, boolean openOnTap) {
        view.setOnTouchListener(new View.OnTouchListener() {
            float downRawX;
            float downRawY;
            int downX;
            int downY;
            boolean moved;
            boolean lyricGesture;

            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    lyricGesture = v instanceof LyricsPanelView
                            && ((LyricsPanelView) v).isLyricGestureRegion(
                            event.getX(), event.getY());
                }
                if (lyricGesture) {
                    boolean finished = event.getActionMasked() == MotionEvent.ACTION_UP
                            || event.getActionMasked() == MotionEvent.ACTION_CANCEL;
                    if (finished) lyricGesture = false;
                    return false;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        downX = params.x;
                        downY = params.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (Math.abs(dx) > dp(v.getContext(), 4)
                                || Math.abs(dy) > dp(v.getContext(), 4)) moved = true;
                        params.x = clamp(downX + Math.round(dx), 0,
                                Math.max(0, screen.x - params.width));
                        params.y = clamp(downY + Math.round(dy), 0,
                                Math.max(0, screen.y - params.height));
                        try { manager.updateViewLayout(v, params); }
                        catch (Throwable ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP:
                        AppPreferences.get(LyricsDisplayService.this).edit()
                                .putInt(xKey, params.x).putInt(yKey, params.y).apply();
                        if (!moved) {
                            v.performClick();
                            if (openOnTap) openMainActivity();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private WindowManager.LayoutParams overlayParams(int width, int height) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    private Display findSecondaryDisplay() {
        if (displayManager == null) return null;
        int preferredId = AppPreferences.displayId(this);
        if (preferredId >= 0) {
            Display preferred = displayManager.getDisplay(preferredId);
            return preferred != null && preferred.getDisplayId() != Display.DEFAULT_DISPLAY
                    ? preferred : null;
        }
        for (Display display : displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) return display;
        }
        for (Display display : displayManager.getDisplays()) {
            if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) return display;
        }
        return null;
    }

    private void dismissMain() {
        if (mainWindowManager != null && mainPanel != null && mainPanel.getParent() != null) {
            try { mainWindowManager.removeViewImmediate(mainPanel); }
            catch (Throwable ignored) { }
        }
        mainWindowManager = null;
        mainParams = null;
        mainPanel = null;
    }

    private void dismissSecondary() {
        if (secondaryWindowManager != null && secondaryPanel != null
                && secondaryPanel.getParent() != null) {
            try { secondaryWindowManager.removeViewImmediate(secondaryPanel); }
            catch (Throwable ignored) { }
        }
        secondaryPanel = null;
        secondaryParams = null;
        secondaryWindowManager = null;
        secondaryContext = null;
        secondaryDisplay = null;
    }

    private boolean canDrawOverlays() {
        return Settings.canDrawOverlays(this);
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.service_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("保持主屏悬浮窗和副屏歌词持续更新");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification createNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText("正在同步播放器与歌词时间轴")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private static Point displaySize(Display display) {
        Point point = new Point();
        if (display != null) display.getRealSize(point);
        return point;
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
