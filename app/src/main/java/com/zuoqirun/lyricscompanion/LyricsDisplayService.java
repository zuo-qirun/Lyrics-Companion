package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;

/** Owns independent overlay windows on the default display and a selected secondary display. */
public final class LyricsDisplayService extends Service implements DisplayManager.DisplayListener {
    private static final String TAG = "LyricsDisplay";
    private static final String CHANNEL_ID = "lyrics_display";
    private static final int NOTIFICATION_ID = 41;
    private static final long NOTIFICATION_POLL_MS = 500L;
    private static final String ACTION_REFRESH =
            "com.zuoqirun.lyricscompanion.action.REFRESH";
    private static final String ACTION_SECONDARY_POSITION =
            "com.zuoqirun.lyricscompanion.action.SECONDARY_POSITION";
    private static final String ACTION_REFRESH_SECONDARY =
            "com.zuoqirun.lyricscompanion.action.REFRESH_SECONDARY";
    private static final String ACTION_SETTINGS_VISIBILITY =
            "com.zuoqirun.lyricscompanion.action.SETTINGS_VISIBILITY";
    private static final String ACTION_LAUNCH_SELECTED =
            "com.zuoqirun.lyricscompanion.action.LAUNCH_SELECTED";
    private static final String EXTRA_VISIBLE = "visible";
    private static final String EXTRA_TARGET_SECONDARY = "target_secondary";
    private static final String EXTRA_DX = "dx";
    private static final String EXTRA_DY = "dy";

    private DisplayManager displayManager;
    private WindowManager mainWindowManager;
    private WindowManager.LayoutParams mainParams;
    private LyricsPanelView mainPanel;
    private TextView mainUnlockHandle;
    private WindowManager.LayoutParams mainUnlockParams;
    private Context secondaryContext;
    private Display secondaryDisplay;
    private WindowManager secondaryWindowManager;
    private WindowManager.LayoutParams secondaryParams;
    private LyricsPanelView secondaryPanel;
    private TextView secondaryUnlockHandle;
    private WindowManager.LayoutParams secondaryUnlockParams;
    private boolean settingsVisible;
    private boolean launcherOnly;
    private boolean launcherOnlySecondary;
    private boolean overlaysHiddenForPlayback;
    private String lastNotificationSignature = "";
    private final Handler communityHandler = new Handler(Looper.getMainLooper());
    private final Handler notificationHandler = new Handler(Looper.getMainLooper());
    private final Runnable communityHeartbeat = new Runnable() {
        @Override public void run() {
            CommunityClient.heartbeatAsync(getApplicationContext(), null);
            communityHandler.postDelayed(this, 60_000L);
        }
    };
    private final Runnable notificationRefresh = new Runnable() {
        @Override public void run() {
            refreshPlaybackNotification();
            notificationHandler.postDelayed(this, NOTIFICATION_POLL_MS);
        }
    };

    static void startOrRefresh(Context context) {
        Intent intent = new Intent(context, LyricsDisplayService.class).setAction(ACTION_REFRESH);
        if (!hasServiceWork(context)) {
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
            DiagnosticLog.record(context, "Overlay", "service start failed="
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    static void moveSecondaryBy(Context context, int dx, int dy) {
        startCommand(context, new Intent(context, LyricsDisplayService.class)
                .setAction(ACTION_SECONDARY_POSITION)
                .putExtra(EXTRA_DX, dx).putExtra(EXTRA_DY, dy));
    }

    static void refreshSecondary(Context context) {
        startCommand(context, new Intent(context, LyricsDisplayService.class)
                .setAction(ACTION_REFRESH_SECONDARY));
    }

    static boolean startSelectedFromLauncher(Context context) {
        boolean secondary = AppPreferences.launchOverlaySecondary(context);
        boolean selectedEnabled = secondary ? AppPreferences.secondaryEnabled(context)
                : AppPreferences.mainEnabled(context);
        if (!selectedEnabled) {
            secondary = !secondary;
            selectedEnabled = secondary ? AppPreferences.secondaryEnabled(context)
                    : AppPreferences.mainEnabled(context);
        }
        if (!selectedEnabled) return false;
        Intent intent = new Intent(context, LyricsDisplayService.class)
                .setAction(ACTION_LAUNCH_SELECTED)
                .putExtra(EXTRA_TARGET_SECONDARY, secondary);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "Unable to launch selected overlay", error);
            DiagnosticLog.record(context, "Overlay", "launcher start failed="
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        }
    }

    static void setSettingsVisible(Context context, boolean visible) {
        startCommand(context, new Intent(context, LyricsDisplayService.class)
                .setAction(ACTION_SETTINGS_VISIBILITY).putExtra(EXTRA_VISIBLE, visible));
    }

    static void stopAndDisable(Context context) {
        AppPreferences.get(context).edit()
                .putBoolean(AppPreferences.KEY_MAIN_OVERLAY, false)
                .putBoolean(AppPreferences.KEY_SECONDARY_OVERLAY, false)
                .putBoolean(AppPreferences.KEY_NOTIFICATION_LYRICS, false)
                .putBoolean(AppPreferences.KEY_MAIN_OVERLAY_TOUCH_THROUGH, false)
                .putBoolean(AppPreferences.KEY_SECONDARY_OVERLAY_TOUCH_THROUGH, false)
                .remove(AppPreferences.KEY_LAUNCH_OVERLAY_LAST_AT)
                .apply();
        context.stopService(new Intent(context, LyricsDisplayService.class));
    }

    private static void startCommand(Context context, Intent intent) {
        if (!hasServiceWork(context)) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to deliver display command", error);
            DiagnosticLog.record(context, "Overlay", "command delivery failed action="
                    + intent.getAction() + " error=" + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        }
    }

    // The merged manifest declares foregroundServiceType="specialUse". Lint 8.7 does not
    // associate that type with this call when the same service also supports pre-29 devices.
    @SuppressLint("ForegroundServiceType")
    @Override public void onCreate() {
        super.onCreate();
        DiagnosticLog.record(this, "Overlay", "display service created api="
                + Build.VERSION.SDK_INT);
        MusicStateStore.initialize(this);
        AudioSpectrumSource.sync(this);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        notificationHandler.post(notificationRefresh);
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) displayManager.registerDisplayListener(this, null);
        communityHandler.post(communityHeartbeat);
        rebuildAll();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        refreshPlaybackNotification();
        Log.i(TAG, "Command=" + action + " main=" + AppPreferences.mainEnabled(this)
                + " secondary=" + AppPreferences.secondaryEnabled(this)
                + " settingsVisible=" + settingsVisible);
        if (ACTION_SECONDARY_POSITION.equals(action)) {
            applySecondaryDelta(intent.getIntExtra(EXTRA_DX, 0),
                    intent.getIntExtra(EXTRA_DY, 0));
            return START_STICKY;
        }
        if (ACTION_REFRESH_SECONDARY.equals(action)) {
            rebuildSecondary();
            return START_STICKY;
        }
        if (ACTION_SETTINGS_VISIBILITY.equals(action)) {
            settingsVisible = intent.getBooleanExtra(EXTRA_VISIBLE, false);
            if (settingsVisible) launcherOnly = false;
            AudioSpectrumSource.sync(this);
            if (settingsVisible) dismissMain();
            else rebuildAll();
            return START_STICKY;
        }
        if (ACTION_LAUNCH_SELECTED.equals(action)) {
            launcherOnly = true;
            launcherOnlySecondary = intent.getBooleanExtra(EXTRA_TARGET_SECONDARY, false);
            settingsVisible = false;
            rebuildAll();
            return START_STICKY;
        }
        rebuildAll();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        DiagnosticLog.record(this, "Overlay", "display service destroyed mainAttached="
                + (mainPanel != null && mainPanel.getParent() != null)
                + " secondaryAttached="
                + (secondaryPanel != null && secondaryPanel.getParent() != null));
        communityHandler.removeCallbacks(communityHeartbeat);
        notificationHandler.removeCallbacks(notificationRefresh);
        if (displayManager != null) displayManager.unregisterDisplayListener(this);
        dismissMain();
        dismissSecondary();
        AudioSpectrumSource.release();
        super.onDestroy();
    }

    @Override public void onDisplayAdded(int displayId) {
        DiagnosticLog.record(this, "Display", "added id=" + displayId);
        rebuildSecondary();
    }
    @Override public void onDisplayRemoved(int displayId) {
        DiagnosticLog.record(this, "Display", "removed id=" + displayId);
        rebuildSecondary();
    }
    @Override public void onDisplayChanged(int displayId) {
        DiagnosticLog.record(this, "Display", "changed id=" + displayId);
        rebuildSecondary();
    }

    private void rebuildAll() {
        AudioSpectrumSource.sync(this);
        overlaysHiddenForPlayback = shouldHideOverlays();
        DiagnosticLog.record(this, "Overlay", "rebuild main=" + AppPreferences.mainEnabled(this)
                + " secondary=" + AppPreferences.secondaryEnabled(this)
                + " ruleHidden=" + overlaysHiddenForPlayback
                + " permission=" + canDrawOverlays());
        Log.i(TAG, "Rebuild main=" + AppPreferences.mainEnabled(this)
                + " secondary=" + AppPreferences.secondaryEnabled(this)
                + " settingsVisible=" + settingsVisible
                + " overlayPermission=" + canDrawOverlays());
        if (!hasServiceWork(this)) {
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
        if (overlaysHiddenForPlayback) return;
        if (AppPreferences.mainEnabled(this) && !settingsVisible
                && (!launcherOnly || !launcherOnlySecondary)) showMain();
        if (AppPreferences.secondaryEnabled(this)
                && (!launcherOnly || launcherOnlySecondary)) showSecondary();
    }

    private void rebuildSecondary() {
        dismissSecondary();
        if (AppPreferences.secondaryEnabled(this) && canDrawOverlays()
                && !shouldHideOverlays() && (!launcherOnly || launcherOnlySecondary)) {
            showSecondary();
        }
    }

    private void showMain() {
        mainWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (mainWindowManager == null) return;
        Point screen = displaySize(mainWindowManager.getDefaultDisplay());
        int width = Math.min(dp(this, AppPreferences.panelWidthDp(this)), screen.x);
        int height = Math.min(dp(this, AppPreferences.panelHeightDp(this)), screen.y);
        mainPanel = new LyricsPanelView(this, false);
        mainParams = overlayParams(width, height);
        mainParams.x = clamp(AppPreferences.get(this).getInt(AppPreferences.KEY_MAIN_X, dp(this, 18)),
                0, Math.max(0, screen.x - width));
        mainParams.y = clamp(AppPreferences.get(this).getInt(AppPreferences.KEY_MAIN_Y, dp(this, 100)),
                0, Math.max(0, screen.y - height));
        attachDrag(mainPanel, mainWindowManager, mainParams, screen,
                AppPreferences.KEY_MAIN_X, AppPreferences.KEY_MAIN_Y, true, false);
        try {
            mainWindowManager.addView(mainPanel, mainParams);
            DiagnosticLog.record(this, "Overlay", "main attached position=" + mainParams.x
                    + "," + mainParams.y + " sizePx=" + width + "x" + height
                    + " screenPx=" + screen.x + "x" + screen.y + " style="
                    + AppPreferences.overlayStyle(this, false));
            Log.i(TAG, "Main overlay attached at " + mainParams.x + "," + mainParams.y
                    + " size=" + width + "x" + height);
            if (AppPreferences.overlayTouchThrough(this, false)) {
                setOverlayTouchThrough(false, true);
            }
        } catch (Throwable error) {
            DiagnosticLog.record(this, "Overlay", "main attach failed="
                    + error.getClass().getSimpleName());
            Log.e(TAG, "Unable to add main overlay", error);
            dismissMain();
        }
    }

    private void showSecondary() {
        Display display = findSecondaryDisplay();
        if (display == null) {
            DiagnosticLog.record(this, "Display", "secondary enabled but unavailable preferredId="
                    + AppPreferences.displayId(this) + " detected="
                    + (displayManager == null ? -1 : displayManager.getDisplays().length));
            Log.i(TAG, "Secondary overlay enabled, but no secondary display is connected");
            return;
        }
        try {
            secondaryDisplay = display;
            secondaryContext = createDisplayContext(display);
            secondaryWindowManager = (WindowManager) secondaryContext.getSystemService(WINDOW_SERVICE);
            if (secondaryWindowManager == null) return;
            Point screen = displaySize(display);
            int width = Math.min(dp(secondaryContext, AppPreferences.panelWidthDp(this, true)),
                    screen.x);
            int height = Math.min(dp(secondaryContext, AppPreferences.panelHeightDp(this, true)),
                    screen.y);
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
                    AppPreferences.KEY_SECONDARY_X, AppPreferences.KEY_SECONDARY_Y, false, true);
            secondaryWindowManager.addView(secondaryPanel, secondaryParams);
            DiagnosticLog.record(this, "Display", "secondary attached id="
                    + display.getDisplayId() + " name=" + display.getName() + " position="
                    + secondaryParams.x + "," + secondaryParams.y + " sizePx=" + width + "x"
                    + height + " screenPx=" + screen.x + "x" + screen.y + " style="
                    + AppPreferences.overlayStyle(this, true));
            Log.i(TAG, "Lyrics shown on display " + display.getDisplayId()
                    + " (" + display.getName() + ")");
            if (AppPreferences.overlayTouchThrough(this, true)) {
                setOverlayTouchThrough(true, true);
            }
        } catch (Throwable error) {
            DiagnosticLog.record(this, "Display", "secondary attach failed="
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
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
                            Point screen, String xKey, String yKey, boolean openOnTap,
                            boolean secondary) {
        final int touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        view.setOnTouchListener(new View.OnTouchListener() {
            final Handler longPressHandler = new Handler(Looper.getMainLooper());
            final Handler tapHandler = new Handler(Looper.getMainLooper());
            float downRawX;
            float downRawY;
            int downX;
            int downY;
            boolean moved;
            boolean longPressReady;
            boolean lyricGesture;
            boolean doubleTap;
            MediaControlAction playbackControl;
            View pressedView;
            long lastTapUpAt;
            final Runnable lockForTouchThrough = new Runnable() {
                @Override public void run() {
                    if (pressedView == null || moved) return;
                    longPressReady = true;
                    pressedView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
            };
            final Runnable singleTap = new Runnable() {
                @Override public void run() {
                    if (playbackControl != null || !openOnTap) return;
                    if (!AppPreferences.tapOverlayReturnsToPlayer(
                            LyricsDisplayService.this)
                            || !MusicNotificationListener.openActivePlayer(
                            LyricsDisplayService.this)) {
                        openMainActivity();
                    }
                }
            };

            @Override public boolean onTouch(View v, MotionEvent event) {
                boolean confirmLongPress = false;
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    pressedView = v;
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    downX = params.x;
                    downY = params.y;
                    moved = false;
                    longPressReady = false;
                    playbackControl = v instanceof LyricsPanelView
                            ? ((LyricsPanelView) v).playbackControlAt(event.getX(), event.getY())
                            : null;
                    lyricGesture = playbackControl == null && v instanceof LyricsPanelView
                            && ((LyricsPanelView) v).isLyricGestureRegion(event.getX(), event.getY());
                    doubleTap = !lyricGesture && playbackControl == null
                            && lastTapUpAt > 0L
                            && event.getEventTime() - lastTapUpAt
                            <= ViewConfiguration.getDoubleTapTimeout();
                    if (doubleTap) {
                        tapHandler.removeCallbacks(singleTap);
                        lastTapUpAt = 0L;
                    }
                    longPressHandler.removeCallbacks(lockForTouchThrough);
                    longPressHandler.postDelayed(lockForTouchThrough,
                            ViewConfiguration.getLongPressTimeout());
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        moved = true;
                        longPressReady = false;
                        longPressHandler.removeCallbacks(lockForTouchThrough);
                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    moved = true;
                    longPressReady = false;
                    longPressHandler.removeCallbacks(lockForTouchThrough);
                    tapHandler.removeCallbacks(singleTap);
                    lastTapUpAt = 0L;
                    pressedView = null;
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    longPressHandler.removeCallbacks(lockForTouchThrough);
                    confirmLongPress = longPressReady && !moved;
                    longPressReady = false;
                    pressedView = null;
                }
                if (confirmLongPress) {
                    if (v instanceof LyricsPanelView) {
                        ((LyricsPanelView) v).cancelLyricBrowseForOverlayLock();
                    }
                    setOverlayTouchThrough(secondary, true);
                    return true;
                }
                if (lyricGesture) {
                    boolean finished = event.getActionMasked() == MotionEvent.ACTION_UP
                            || event.getActionMasked() == MotionEvent.ACTION_CANCEL;
                    if (finished) lyricGesture = false;
                    return false;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
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
                            if (playbackControl != null) {
                                MusicNotificationListener.requestPlaybackControl(
                                        LyricsDisplayService.this, playbackControl);
                            } else if (doubleTap) {
                                forceReturnOverlay(!secondary);
                            } else if (!lyricGesture) {
                                lastTapUpAt = event.getEventTime();
                                tapHandler.removeCallbacks(singleTap);
                                if (openOnTap) {
                                    tapHandler.postDelayed(singleTap,
                                            ViewConfiguration.getDoubleTapTimeout());
                                }
                            }
                        }
                        playbackControl = null;
                        doubleTap = false;
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        playbackControl = null;
                        doubleTap = false;
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void setOverlayTouchThrough(boolean secondary, boolean enabled) {
        WindowManager manager = secondary ? secondaryWindowManager : mainWindowManager;
        View panel = secondary ? secondaryPanel : mainPanel;
        WindowManager.LayoutParams params = secondary ? secondaryParams : mainParams;
        if (manager == null || panel == null || params == null || panel.getParent() == null) return;
        int touchFlag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        params.flags = enabled ? params.flags | touchFlag : params.flags & ~touchFlag;
        // Android 12 blocks touches through opaque, non-touchable overlays as untrusted input.
        params.alpha = enabled ? touchThroughWindowAlpha() : 1f;
        try {
            manager.updateViewLayout(panel, params);
            AppPreferences.get(this).edit().putBoolean(secondary
                    ? AppPreferences.KEY_SECONDARY_OVERLAY_TOUCH_THROUGH
                    : AppPreferences.KEY_MAIN_OVERLAY_TOUCH_THROUGH, enabled).apply();
            if (enabled) {
                if (!addUnlockHandle(secondary)) {
                    params.flags &= ~touchFlag;
                    params.alpha = 1f;
                    manager.updateViewLayout(panel, params);
                    AppPreferences.get(this).edit().putBoolean(secondary
                            ? AppPreferences.KEY_SECONDARY_OVERLAY_TOUCH_THROUGH
                            : AppPreferences.KEY_MAIN_OVERLAY_TOUCH_THROUGH, false).apply();
                    return;
                }
            } else {
                removeUnlockHandle(secondary);
            }
            DiagnosticLog.record(this, "Overlay", (enabled ? "touch through enabled "
                    : "touch through disabled ") + (secondary ? "secondary" : "main"));
        } catch (Throwable error) {
            Log.w(TAG, "Unable to change overlay touch-through", error);
        }
    }

    private static float touchThroughWindowAlpha() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? 0.79f : 1f;
    }

    private boolean addUnlockHandle(final boolean secondary) {
        WindowManager manager = secondary ? secondaryWindowManager : mainWindowManager;
        WindowManager.LayoutParams panelParams = secondary ? secondaryParams : mainParams;
        if (manager == null || panelParams == null) return false;
        removeUnlockHandle(secondary);
        final TextView handle = new TextView(secondary ? secondaryContext : this);
        handle.setText("×");
        handle.setTextColor(Color.WHITE);
        handle.setTextSize(20f);
        handle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("点击取消悬浮窗穿透");
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xCC202124);
        circle.setStroke(dp(handle.getContext(), 1), 0xAAFFFFFF);
        handle.setBackground(circle);
        int size = dp(handle.getContext(), 36);
        int height = size;
        final WindowManager.LayoutParams handleParams = new WindowManager.LayoutParams(
                size, height,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        handleParams.gravity = Gravity.TOP | Gravity.START;
        handleParams.x = clamp(panelParams.x + panelParams.width - size, 0,
                Math.max(0, displaySize(secondary ? secondaryDisplay
                        : mainWindowManager.getDefaultDisplay()).x - size));
        handleParams.y = clamp(panelParams.y, 0,
                Math.max(0, displaySize(secondary ? secondaryDisplay
                        : mainWindowManager.getDefaultDisplay()).y - height));
        handle.setOnClickListener(v -> setOverlayTouchThrough(secondary, false));
        try {
            manager.addView(handle, handleParams);
            if (secondary) {
                secondaryUnlockHandle = handle;
                secondaryUnlockParams = handleParams;
            } else {
                mainUnlockHandle = handle;
                mainUnlockParams = handleParams;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Unable to add overlay unlock handle", error);
            return false;
        }
        return true;
    }

    private void removeUnlockHandle(boolean secondary) {
        WindowManager manager = secondary ? secondaryWindowManager : mainWindowManager;
        TextView handle = secondary ? secondaryUnlockHandle : mainUnlockHandle;
        if (manager != null && handle != null && handle.getParent() != null) {
            try { manager.removeViewImmediate(handle); }
            catch (Throwable ignored) { }
        }
        if (secondary) {
            secondaryUnlockHandle = null;
            secondaryUnlockParams = null;
        } else {
            mainUnlockHandle = null;
            mainUnlockParams = null;
        }
    }

    private void forceReturnOverlay(boolean mainOverlay) {
        String key = mainOverlay ? AppPreferences.KEY_MAIN_OVERLAY
                : AppPreferences.KEY_SECONDARY_OVERLAY;
        AppPreferences.get(this).edit().putBoolean(key, false)
                .putBoolean(mainOverlay ? AppPreferences.KEY_MAIN_OVERLAY_TOUCH_THROUGH
                        : AppPreferences.KEY_SECONDARY_OVERLAY_TOUCH_THROUGH, false).apply();
        if (mainOverlay) dismissMain();
        else dismissSecondary();
        DiagnosticLog.record(this, "Overlay", "double tap forced return "
                + (mainOverlay ? "main" : "secondary"));
        openMainActivity();
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
        removeUnlockHandle(false);
        if (mainWindowManager != null && mainPanel != null && mainPanel.getParent() != null) {
            try { mainWindowManager.removeViewImmediate(mainPanel); }
            catch (Throwable ignored) { }
        }
        mainWindowManager = null;
        mainParams = null;
        mainPanel = null;
    }

    private void dismissSecondary() {
        removeUnlockHandle(true);
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
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            startActivity(intent);
        } catch (Throwable error) {
            // Some car launchers reject activity launches from a background overlay service.
            // The ongoing foreground-service notification already carries the same PendingIntent.
            Log.w(TAG, "Unable to open main activity from overlay", error);
            DiagnosticLog.record(this, "Overlay", "open main activity failed="
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            refreshPlaybackNotification();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.service_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("保持主屏悬浮窗和副屏歌词持续更新");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private static boolean hasServiceWork(Context context) {
        return AppPreferences.mainEnabled(context) || AppPreferences.secondaryEnabled(context)
                || AppPreferences.notificationLyrics(context);
    }

    private void refreshPlaybackNotification() {
        MusicSnapshot snapshot = MusicStateStore.snapshot(AppPreferences.lyricOffsetMs(this));
        syncOverlayVisibility(snapshot);
        boolean showLyrics = AppPreferences.notificationLyrics(this);
        String lyric = showLyrics ? notificationLyric(snapshot) : "正在同步播放器与歌词时间轴";
        String translation = showLyrics ? notificationTranslation(snapshot) : "";
        String title = showLyrics && snapshot.active && !snapshot.title.trim().isEmpty()
                ? snapshot.title : getString(R.string.service_notification_title);
        String subtext = showLyrics && snapshot.active
                ? joinMetadata(snapshot.artist, snapshot.lyricSourceName) : "";
        String signature = showLyrics + "|" + title + "|" + lyric + "|" + translation + "|"
                + subtext + "|" + snapshot.playing + "|"
                + AppPreferences.lockscreenLyrics(this);
        if (signature.equals(lastNotificationSignature)) return;
        lastNotificationSignature = signature;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID,
                createNotification(title, lyric, translation, subtext));
    }

    private boolean shouldHideOverlays() {
        MusicSnapshot snapshot = MusicStateStore.snapshot(AppPreferences.lyricOffsetMs(this));
        return shouldHideOverlays(snapshot);
    }

    private boolean shouldHideOverlays(MusicSnapshot snapshot) {
        boolean hideInPlayer = AppPreferences.hideOverlaysInPlayer(this);
        boolean playerInForeground = hideInPlayer && ForegroundAppDetector.isPlayerInForeground(
                this, MusicNotificationListener.activePlayerPackageName());
        return OverlayPlaybackVisibility.shouldHide(
                AppPreferences.hideOverlaysWhenNotPlaying(this), snapshot.playing,
                hideInPlayer, playerInForeground);
    }

    private void syncOverlayVisibility(MusicSnapshot snapshot) {
        boolean shouldHide = shouldHideOverlays(snapshot);
        if (shouldHide == overlaysHiddenForPlayback) return;
        overlaysHiddenForPlayback = shouldHide;
        DiagnosticLog.record(this, "Overlay", shouldHide
                ? "hidden by visibility rule"
                : "restored because no visibility rule matches");
        if (shouldHide) {
            dismissMain();
            dismissSecondary();
            return;
        }
        if (!canDrawOverlays()) return;
        if (AppPreferences.mainEnabled(this) && !settingsVisible && mainPanel == null
                && (!launcherOnly || !launcherOnlySecondary)) showMain();
        if (AppPreferences.secondaryEnabled(this) && secondaryPanel == null
                && (!launcherOnly || launcherOnlySecondary)) showSecondary();
    }

    private Notification createNotification() {
        MusicSnapshot snapshot = MusicStateStore.snapshot(AppPreferences.lyricOffsetMs(this));
        boolean showLyrics = AppPreferences.notificationLyrics(this);
        String title = showLyrics && snapshot.active && !snapshot.title.trim().isEmpty()
                ? snapshot.title : getString(R.string.service_notification_title);
        String lyric = showLyrics ? notificationLyric(snapshot) : "正在同步播放器与歌词时间轴";
        String translation = showLyrics ? notificationTranslation(snapshot) : "";
        String subtext = showLyrics && snapshot.active
                ? joinMetadata(snapshot.artist, snapshot.lyricSourceName) : "";
        return createNotification(title, lyric, translation, subtext);
    }

    private Notification createNotification(String title, String lyric, String translation,
                                            String subtext) {
        Intent open = new Intent(this, MainActivity.class);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open,
                pendingFlags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(Build.VERSION.SDK_INT >= 21
                        ? R.drawable.ic_launcher : android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(lyric)
                .setContentIntent(contentIntent)
                .setOngoing(true);
        if (!subtext.isEmpty()) builder.setSubText(subtext);
        builder.setStyle(new Notification.BigTextStyle().bigText(
                translation.isEmpty() ? lyric : lyric + "\n" + translation));
        if (Build.VERSION.SDK_INT >= 21) {
            boolean publicLyrics = AppPreferences.lockscreenLyrics(this);
            builder.setVisibility(publicLyrics ? Notification.VISIBILITY_PUBLIC
                    : Notification.VISIBILITY_PRIVATE);
            if (!publicLyrics) {
                Notification.Builder redacted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? new Notification.Builder(this, CHANNEL_ID)
                        : new Notification.Builder(this);
                redacted.setSmallIcon(Build.VERSION.SDK_INT >= 21
                                ? R.drawable.ic_launcher : android.R.drawable.ic_media_play)
                        .setContentTitle(getString(R.string.service_notification_title))
                        .setContentText("歌词同步服务正在运行")
                        .setContentIntent(contentIntent)
                        .setOngoing(true)
                        .setVisibility(Notification.VISIBILITY_PUBLIC);
                builder.setPublicVersion(redacted.build());
            }
        }
        if (Build.VERSION.SDK_INT >= 21) builder.setCategory(Notification.CATEGORY_SERVICE);
        return builder.build();
    }

    private static String notificationLyric(MusicSnapshot snapshot) {
        if (!snapshot.active) return "等待播放器";
        if (snapshot.lyricAvailable && snapshot.lyrics != null
                && !snapshot.lyrics.lyric.trim().isEmpty()) return snapshot.lyrics.lyric.trim();
        if (!snapshot.lyricLoaded) return "正在匹配歌词";
        if (!snapshot.lyricAvailable) return "未匹配到歌词";
        return "等待下一行歌词";
    }

    private static String notificationTranslation(MusicSnapshot snapshot) {
        if (!snapshot.lyricAvailable || snapshot.lyrics == null) return "";
        String translation = snapshot.lyrics.translatedLyric == null
                ? "" : snapshot.lyrics.translatedLyric.trim();
        String lyric = snapshot.lyrics.lyric == null ? "" : snapshot.lyrics.lyric.trim();
        return translation.isEmpty() || translation.equals(lyric) ? "" : translation;
    }

    private static String joinMetadata(String artist, String source) {
        String left = artist == null ? "" : artist.trim();
        String right = source == null ? "" : source.trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left + " · " + right;
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
