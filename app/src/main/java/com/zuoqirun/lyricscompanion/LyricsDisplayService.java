package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns independent overlay windows on the default display, one selected secondary display, and
 * any number of additional screens the user turned on. Each extra screen draws from its own
 * preference file, so a HUD and a driving display keep separate styles, sizes and positions.
 */
public final class LyricsDisplayService extends Service implements DisplayManager.DisplayListener {
    private static final String TAG = "LyricsDisplay";
    private static final String CHANNEL_ID = "lyrics_display";
    private static final int NOTIFICATION_ID = 41;
    private static final long NOTIFICATION_POLL_MS = 500L;
    private static final long LISTENER_HEALTH_POLL_MS = 10_000L;
    private static final long SECONDARY_INITIAL_RETRY_MS = 1_500L;
    private static final long SECONDARY_MAX_RETRY_MS = 12_000L;
    private static final String ACTION_REFRESH =
            "com.zuoqirun.lyricscompanion.action.REFRESH";
    private static final String ACTION_SECONDARY_POSITION =
            "com.zuoqirun.lyricscompanion.action.SECONDARY_POSITION";
    private static final String ACTION_REFRESH_SECONDARY =
            "com.zuoqirun.lyricscompanion.action.REFRESH_SECONDARY";
    private static final String ACTION_SETTINGS_VISIBILITY =
            "com.zuoqirun.lyricscompanion.action.SETTINGS_VISIBILITY";
    private static final String EXTRA_VISIBLE = "visible";
    private static final String EXTRA_DX = "dx";
    private static final String EXTRA_DY = "dy";
    /** Which screen a position nudge applies to; absent means the legacy secondary slot. */
    private static final String EXTRA_SLOT = "slot";

    private DisplayManager displayManager;
    private WindowManager mainWindowManager;
    private WindowManager.LayoutParams mainParams;
    private LyricsPanelView mainPanel;
    private TextView mainUnlockHandle;
    private WindowManager.LayoutParams mainUnlockParams;
    private View mainPlaybackPad;
    private WindowManager.LayoutParams mainPlaybackPadParams;
    private Context secondaryContext;
    private Display secondaryDisplay;
    private WindowManager secondaryWindowManager;
    private WindowManager.LayoutParams secondaryParams;
    private LyricsPanelView secondaryPanel;
    private TextView secondaryUnlockHandle;
    private WindowManager.LayoutParams secondaryUnlockParams;
    private View secondaryPlaybackPad;
    private WindowManager.LayoutParams secondaryPlaybackPadParams;
    private LyricsPanelView statusLyricStrip;
    private WindowManager.LayoutParams statusLyricParams;
    /** One overlay per additional screen (slots 2+), so several can be shown at once. */
    private final List<ExtraOverlay> extraOverlays = new ArrayList<>();
    private WindowManager bottomSpectrumManager;
    private BottomSpectrumView bottomSpectrumView;
    private boolean settingsVisible;
    private boolean overlaysHiddenForPlayback;
    private boolean secondaryHiddenForPlayback;
    private String lastVisibilityDiagnostic = "";
    private boolean screenReceiverRegistered;
    private String lastNotificationSignature = "";
    private final Handler communityHandler = new Handler(Looper.getMainLooper());
    private final Handler notificationHandler = new Handler(Looper.getMainLooper());
    private final Handler recoveryHandler = new Handler(Looper.getMainLooper());
    private int secondaryRetryAttempts;
    private int statusLyricRetryAttempts;
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
    /** The overlay can survive while NotificationManager loses its listener binding. */
    private final Runnable listenerHealthProbe = new Runnable() {
        @Override public void run() {
            if (!AppPreferences.serviceStoppedByUser(LyricsDisplayService.this)
                    && hasRememberedOverlayTarget(LyricsDisplayService.this)) {
                MusicNotificationListener.requestReconnect(LyricsDisplayService.this);
                recoveryHandler.postDelayed(this, LISTENER_HEALTH_POLL_MS);
            }
        }
    };
    /** Some car systems report a secondary display before its WindowManager is ready. */
    private final Runnable secondaryRetry = new Runnable() {
        @Override public void run() {
            if (!AppPreferences.secondaryEnabled(LyricsDisplayService.this)
                    || AppPreferences.serviceStoppedByUser(LyricsDisplayService.this)
                    || !canDrawOverlays() || shouldHideOverlays(true)) return;
            rebuildSecondary();
            if (secondaryPanel == null) scheduleSecondaryRetry("still_unavailable");
        }
    };
    /** The status bar's WindowManager is often the last surface ready during automotive boot. */
    private final Runnable statusLyricRetry = new Runnable() {
        @Override public void run() {
            if (!AppPreferences.topLyricStrip(LyricsDisplayService.this)
                    || AppPreferences.serviceStoppedByUser(LyricsDisplayService.this)
                    || !canDrawOverlays() || shouldHideOverlays()) return;
            showStatusLyricStrip();
        }
    };
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent == null ? null : intent.getAction())) {
                Log.i(TAG, "Screen-on receiver fired");
                DiagnosticLog.record(context, "Overlay", "screen-on receiver fired");
                startRememberedFromSystem(context, "screen_on");
            }
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

    /** Nudges the secondary screen's overlay (slot 1), which is what the joystick used to do. */
    static void moveSecondaryBy(Context context, int dx, int dy) {
        moveSecondaryBy(context, DisplaySlotRegistry.SECONDARY_SLOT, dx, dy);
    }

    /** Nudges one screen's overlay: slot 1 is the 副屏, 2 and up are the extra screens. */
    static void moveSecondaryBy(Context context, int slot, int dx, int dy) {
        startCommand(context, new Intent(context, LyricsDisplayService.class)
                .setAction(ACTION_SECONDARY_POSITION)
                .putExtra(EXTRA_SLOT, slot)
                .putExtra(EXTRA_DX, dx).putExtra(EXTRA_DY, dy));
    }

    static void refreshSecondary(Context context) {
        startCommand(context, new Intent(context, LyricsDisplayService.class)
                .setAction(ACTION_REFRESH_SECONDARY));
    }

    static boolean startRememberedFromLauncher(Context context) {
        return startRemembered(context, "launcher");
    }

    static void startRememberedFromSystem(Context context, String reason) {
        boolean enabled = AppPreferences.autoStartOverlays(context);
        Log.i(TAG, "System restore reason=" + reason + " enabled=" + enabled);
        DiagnosticLog.record(context, "Overlay", reason + " received enabled=" + enabled);
        if (!enabled) return;
        boolean addedDefaultTarget = AppPreferences.ensureAutoStartOverlayTarget(context);
        if (addedDefaultTarget) {
            DiagnosticLog.record(context, "Overlay", reason
                    + " enabled default main overlay because no target was remembered");
        }
        startRemembered(context, reason);
    }

    private static boolean startRemembered(Context context, String reason) {
        if (!hasRememberedOverlayTarget(context)) {
            DiagnosticLog.record(context, "Overlay", reason + " skipped: no remembered target");
            return false;
        }
        AppPreferences.setServiceStoppedByUser(context, false);
        Intent intent = new Intent(context, LyricsDisplayService.class).setAction(ACTION_REFRESH);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            DiagnosticLog.record(context, "Overlay", reason + " restored remembered overlays");
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "Unable to launch remembered overlays", error);
            DiagnosticLog.record(context, "Overlay", reason + " start failed="
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        }
    }

    static void setSettingsVisible(Context context, boolean visible) {
        startCommand(context, new Intent(context, LyricsDisplayService.class)
                .setAction(ACTION_SETTINGS_VISIBILITY).putExtra(EXTRA_VISIBLE, visible));
    }

    static void stopAndRememberOverlays(Context context) {
        AppPreferences.get(context).edit()
                .putBoolean(AppPreferences.KEY_NOTIFICATION_LYRICS, false)
                .putBoolean(AppPreferences.KEY_MAIN_OVERLAY_TOUCH_THROUGH, false)
                .putBoolean(AppPreferences.KEY_SECONDARY_OVERLAY_TOUCH_THROUGH, false)
                .putBoolean(AppPreferences.KEY_SERVICE_STOPPED_BY_USER, true)
                .remove(AppPreferences.KEY_LAUNCH_OVERLAY_LAST_AT)
                .apply();
        // ACTION_SCREEN_ON can only be received by a context-registered receiver.  When the
        // user keeps auto-start enabled, retain this foreground service in a no-overlay standby
        // state so it can receive the next wake event; otherwise stop it completely.
        if (AppPreferences.autoStartOverlays(context)) {
            startCommand(context, new Intent(context, LyricsDisplayService.class)
                    .setAction(ACTION_REFRESH));
        } else {
            context.stopService(new Intent(context, LyricsDisplayService.class));
        }
        MusicNotificationListener.stopObservation();
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
        syncScreenReceiver();
        notificationHandler.post(notificationRefresh);
        recoveryHandler.postDelayed(listenerHealthProbe, 1_500L);
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) displayManager.registerDisplayListener(this, null);
        communityHandler.post(communityHeartbeat);
        rebuildAll();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        syncScreenReceiver();
        String action = intent == null ? "" : intent.getAction();
        refreshPlaybackNotification();
        Log.i(TAG, "Command=" + action + " main=" + AppPreferences.mainEnabled(this)
                + " secondary=" + AppPreferences.secondaryEnabled(this)
                + " settingsVisible=" + settingsVisible);
        if (ACTION_SECONDARY_POSITION.equals(action)) {
            applySecondaryDelta(intent.getIntExtra(EXTRA_SLOT,
                            DisplaySlotRegistry.SECONDARY_SLOT),
                    intent.getIntExtra(EXTRA_DX, 0),
                    intent.getIntExtra(EXTRA_DY, 0));
            return START_STICKY;
        }
        if (ACTION_REFRESH_SECONDARY.equals(action)) {
            rebuildSecondary();
            return START_STICKY;
        }
        if (ACTION_SETTINGS_VISIBILITY.equals(action)) {
            settingsVisible = intent.getBooleanExtra(EXTRA_VISIBLE, false);
            AudioSpectrumSource.sync(this);
            if (settingsVisible) dismissMain();
            else rebuildAll();
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
        recoveryHandler.removeCallbacks(listenerHealthProbe);
        recoveryHandler.removeCallbacks(statusLyricRetry);
        recoveryHandler.removeCallbacks(secondaryRetry);
        unregisterScreenReceiver();
        if (displayManager != null) displayManager.unregisterDisplayListener(this);
        dismissMain();
        dismissSecondary();
        dismissExtras();
        dismissStatusLyricStrip();
        dismissBottomSpectrum();
        AudioSpectrumSource.release();
        super.onDestroy();
    }

    @Override public void onDisplayAdded(int displayId) {
        DiagnosticLog.record(this, "Display", "added id=" + displayId);
        rebuildSecondary();
        rebuildExtras();
        scheduleSecondaryRetry("display_added");
    }
    @Override public void onDisplayRemoved(int displayId) {
        DiagnosticLog.record(this, "Display", "removed id=" + displayId);
        rebuildSecondary();
        rebuildExtras();
        scheduleSecondaryRetry("display_removed");
    }
    @Override public void onDisplayChanged(int displayId) {
        DiagnosticLog.record(this, "Display", "changed id=" + displayId);
        rebuildSecondary();
        rebuildExtras();
        scheduleSecondaryRetry("display_changed");
    }

    private void rebuildAll() {
        AudioSpectrumSource.sync(this);
        overlaysHiddenForPlayback = shouldHideOverlays(false);
        secondaryHiddenForPlayback = shouldHideOverlays(true);
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
        if (AppPreferences.serviceStoppedByUser(this)) {
            // Keep only the screen-on receiver and foreground-service lifecycle for an enabled
            // auto-start option.  No overlay or music/session listener remains active here.
            dismissMain();
            dismissSecondary();
            dismissExtras();
            dismissStatusLyricStrip();
            dismissBottomSpectrum();
            return;
        }
        if (!canDrawOverlays()) {
            dismissMain();
            dismissSecondary();
            dismissExtras();
            dismissStatusLyricStrip();
            dismissBottomSpectrum();
            return;
        }
        dismissMain();
        dismissSecondary();
        dismissExtras();
        dismissBottomSpectrum();
        if (overlaysHiddenForPlayback) dismissStatusLyricStrip();
        else if (AppPreferences.mainEnabled(this) && !settingsVisible) showMain();
        if (AppPreferences.secondaryEnabled(this) && !secondaryHiddenForPlayback) showSecondary();
        else recoveryHandler.removeCallbacks(secondaryRetry);
        if (!secondaryHiddenForPlayback) rebuildExtras();
        if (!overlaysHiddenForPlayback && AppPreferences.topLyricStrip(this)) showStatusLyricStrip();
        else dismissStatusLyricStrip();
        if (!overlaysHiddenForPlayback && AppPreferences.bottomSpectrum(this)) showBottomSpectrum();
    }

    private void rebuildSecondary() {
        dismissSecondary();
        if (AppPreferences.secondaryEnabled(this) && canDrawOverlays()
                && !shouldHideOverlays(true)) {
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
        watchPlaybackPadLayout(mainPanel, false);
        mainParams = overlayParams(width, height);
        String style = AppPreferences.overlayStyle(this, false);
        String xKey = AppPreferences.overlayPositionKey(false, style, true);
        String yKey = AppPreferences.overlayPositionKey(false, style, false);
        mainParams.x = clamp(AppPreferences.overlayPosition(this, false, style, true, dp(this, 18)),
                0, Math.max(0, screen.x - width));
        mainParams.y = clamp(AppPreferences.overlayPosition(this, false, style, false, dp(this, 100)),
                0, Math.max(0, screen.y - height));
        attachDrag(mainPanel, mainWindowManager, mainParams, screen, xKey, yKey, true, false);
        try {
            mainWindowManager.addView(mainPanel, mainParams);
            DiagnosticLog.record(this, "Overlay", "main attached position=" + mainParams.x
                    + "," + mainParams.y + " sizePx=" + width + "x" + height
                    + " screenPx=" + screen.x + "x" + screen.y + " style="
                    + AppPreferences.overlayStyle(this, false));
            Log.i(TAG, "Main overlay attached at " + mainParams.x + "," + mainParams.y
                    + " size=" + width + "x" + height);
            // Re-apply whatever mode is stored: a locked or pass-through overlay has to come back
            // the way the user left it.
            applyOverlayInteraction(false);
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
            scheduleSecondaryRetry("no_display");
            return;
        }
        try {
            secondaryDisplay = display;
            secondaryContext = createDisplayContext(display);
            secondaryWindowManager = (WindowManager) secondaryContext.getSystemService(WINDOW_SERVICE);
            if (secondaryWindowManager == null) {
                scheduleSecondaryRetry("no_window_manager");
                return;
            }
            Point screen = displaySize(display);
            int width = Math.min(dp(secondaryContext, AppPreferences.panelWidthDp(this, true)),
                    screen.x);
            int height = Math.min(dp(secondaryContext, AppPreferences.panelHeightDp(this, true)),
                    screen.y);
            secondaryPanel = new LyricsPanelView(secondaryContext, true);
            watchPlaybackPadLayout(secondaryPanel, true);
            secondaryParams = overlayParams(width, height);
            int defaultX = Math.max(0, (screen.x - width) / 2);
            int defaultY = Math.max(0, Math.round(screen.y * 0.10f));
            String style = AppPreferences.overlayStyle(this, true);
            String xKey = AppPreferences.overlayPositionKey(true, style, true);
            String yKey = AppPreferences.overlayPositionKey(true, style, false);
            secondaryParams.x = clamp(AppPreferences.overlayPosition(this, true, style, true, defaultX),
                    0, Math.max(0, screen.x - width));
            secondaryParams.y = clamp(AppPreferences.overlayPosition(this, true, style, false, defaultY),
                    0, Math.max(0, screen.y - height));
            attachDrag(secondaryPanel, secondaryWindowManager, secondaryParams, screen,
                    xKey, yKey, false, true);
            secondaryWindowManager.addView(secondaryPanel, secondaryParams);
            DiagnosticLog.record(this, "Display", "secondary attached id="
                    + display.getDisplayId() + " name=" + display.getName() + " position="
                    + secondaryParams.x + "," + secondaryParams.y + " sizePx=" + width + "x"
                    + height + " screenPx=" + screen.x + "x" + screen.y + " style="
                    + AppPreferences.overlayStyle(this, true));
            Log.i(TAG, "Lyrics shown on display " + display.getDisplayId()
                    + " (" + display.getName() + ")");
            secondaryRetryAttempts = 0;
            recoveryHandler.removeCallbacks(secondaryRetry);
            applyOverlayInteraction(true);
        } catch (Throwable error) {
            DiagnosticLog.record(this, "Display", "secondary attach failed="
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            Log.e(TAG, "Unable to add secondary overlay", error);
            dismissSecondary();
            scheduleSecondaryRetry("attach_failed");
        }
    }

    private void scheduleSecondaryRetry(String reason) {
        if (!AppPreferences.secondaryEnabled(this) || AppPreferences.serviceStoppedByUser(this)
                || secondaryPanel != null || secondaryRetryAttempts >= 12) return;
        recoveryHandler.removeCallbacks(secondaryRetry);
        long delay = Math.min(SECONDARY_MAX_RETRY_MS,
                SECONDARY_INITIAL_RETRY_MS * (1L << Math.min(3, secondaryRetryAttempts)));
        secondaryRetryAttempts++;
        DiagnosticLog.record(this, "Display", "secondary retry=" + secondaryRetryAttempts
                + " delayMs=" + delay + " reason=" + reason);
        recoveryHandler.postDelayed(secondaryRetry, delay);
    }

    private void applySecondaryDelta(int slot, int dx, int dy) {
        if (slot >= DisplaySlotRegistry.FIRST_EXTRA_SLOT) {
            applyExtraDelta(slot, dx, dy);
            return;
        }
        if (secondaryWindowManager == null || secondaryPanel == null || secondaryParams == null
                || secondaryDisplay == null) return;
        Point screen = displaySize(secondaryDisplay);
        secondaryParams.x = clamp(secondaryParams.x + dx, 0,
                Math.max(0, screen.x - secondaryParams.width));
        secondaryParams.y = clamp(secondaryParams.y + dy, 0,
                Math.max(0, screen.y - secondaryParams.height));
        String style = AppPreferences.overlayStyle(this, true);
        AppPreferences.putOverlayPosition(this, true,
                AppPreferences.overlayPositionKey(true, style, true), secondaryParams.x);
        AppPreferences.putOverlayPosition(this, true,
                AppPreferences.overlayPositionKey(true, style, false), secondaryParams.y);
        try { secondaryWindowManager.updateViewLayout(secondaryPanel, secondaryParams); }
        catch (Throwable error) { Log.w(TAG, "Unable to move secondary overlay", error); }
    }

    /**
     * The same nudge for an extra screen. The window may not be up while its display is
     * disconnected, in which case the joystick simply has nothing to move.
     */
    private void applyExtraDelta(int slot, int dx, int dy) {
        for (ExtraOverlay overlay : extraOverlays) {
            if (overlay.slot != slot) continue;
            Point screen = displaySize(overlay.display);
            overlay.params.x = clamp(overlay.params.x + dx, 0,
                    Math.max(0, screen.x - overlay.params.width));
            overlay.params.y = clamp(overlay.params.y + dy, 0,
                    Math.max(0, screen.y - overlay.params.height));
            AppPreferences.putOverlayPosition(overlay.context, true, overlay.xKey,
                    overlay.params.x);
            AppPreferences.putOverlayPosition(overlay.context, true, overlay.yKey,
                    overlay.params.y);
            try { overlay.windowManager.updateViewLayout(overlay.panel, overlay.params); }
            catch (Throwable error) { Log.w(TAG, "Unable to move extra overlay", error); }
            return;
        }
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
                    showOverlayQuickMenu(v, secondary);
                    return true;
                }
                if (lyricGesture) {
                    boolean finished = event.getActionMasked() == MotionEvent.ACTION_UP
                            || event.getActionMasked() == MotionEvent.ACTION_CANCEL;
                    if (event.getActionMasked() == MotionEvent.ACTION_UP && !moved && openOnTap) {
                        v.performClick();
                        tapHandler.removeCallbacks(singleTap);
                        tapHandler.postDelayed(singleTap, ViewConfiguration.getDoubleTapTimeout());
                    }
                    if (finished) lyricGesture = false;
                    return false;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (AppPreferences.overlayPositionLocked(LyricsDisplayService.this,
                                secondary)) return true;
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

    /** Long press intentionally opens a small action menu; position lock now remains an option. */
    private void showOverlayQuickMenu(View anchor, boolean secondary) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(this, 8);
        content.setPadding(padding, padding, padding, padding);
        // The rounded frame lives on the scroll host so it stays put while the rows scroll.
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF0202B3A);
        background.setCornerRadius(dp(this, 16));
        background.setStroke(dp(this, 1), 0x556EE7F2);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(background);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        PopupWindow popup = new PopupWindow(scroll, dp(this, 220),
                WindowManager.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popup.setElevation(dp(this, 10));
        }
        addQuickMenuButton(content, "尺寸与透明度", () -> openDisplaySettings(secondary, popup));
        addQuickMenuButton(content, "字体", () -> {
            popup.dismiss();
            openMainActivity();
        });
        addQuickMenuButton(content, "频谱颜色", () -> {
            popup.dismiss();
            Intent intent = new Intent(this, ColorSettingsActivity.class)
                    .putExtra(ColorSettingsActivity.EXTRA_SCOPE, secondary ? 1 : 0)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        boolean locked = AppPreferences.overlayPositionLocked(this, secondary);
        addQuickMenuButton(content, locked ? "解除位置锁定" : "锁定位置（保留控制按键，其余穿透）", () -> {
            AppPreferences.putDisplayBoolean(this, secondary,
                    AppPreferences.KEY_OVERLAY_POSITION_LOCKED, !locked);
            popup.dismiss();
            applyOverlayInteraction(secondary);
            if (!locked) notifyPassThroughDimming();
        });
        addQuickMenuButton(content, "锁定并触摸穿透", () -> {
            popup.dismiss();
            setOverlayTouchThrough(secondary, true);
        });
        showQuickMenuWithinScreen(popup, scroll, anchor);
    }

    /**
     * Positions the long-press menu entirely inside the anchor's display. showAsDropDown clips
     * against the anchor window's frame, which for an overlay panel is the panel itself — when
     * the overlay hugs a screen edge the last rows ("锁定并触摸穿透") became untappable. The
     * window height is capped on small displays so the inner ScrollView scrolls instead of the
     * menu being clipped.
     */
    private void showQuickMenuWithinScreen(PopupWindow popup, View menu, View anchor) {
        int menuWidth = dp(this, 220);
        menu.measure(View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        // The anchor's resources carry its display context, so secondary displays clamp
        // against their own metrics instead of the default screen.
        android.util.DisplayMetrics metrics = anchor.getResources().getDisplayMetrics();
        int margin = dp(this, 8);
        int maxHeight = Math.max(dp(this, 120), metrics.heightPixels - 2 * margin);
        int popupHeight = Math.min(menu.getMeasuredHeight(), maxHeight);
        popup.setWidth(menuWidth);
        popup.setHeight(popupHeight);
        int maxX = Math.max(margin, metrics.widthPixels - menuWidth - margin);
        int maxY = Math.max(margin, metrics.heightPixels - popupHeight - margin);
        int[] origin = new int[2];
        anchor.getLocationOnScreen(origin);
        // Keep the original right-aligned-with-anchor intent, then clamp into the display.
        int x = Math.max(margin, Math.min(origin[0] + anchor.getWidth() - menuWidth, maxX));
        int y = origin[1] - popupHeight;
        if (y < margin) y = origin[1] + anchor.getHeight();
        y = Math.max(margin, Math.min(y, maxY));
        popup.showAtLocation(anchor, Gravity.TOP | Gravity.START, x, y);
    }

    private void addQuickMenuButton(LinearLayout content, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13f);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setOnClickListener(v -> action.run());
        content.addView(button, new LinearLayout.LayoutParams(-1, dp(this, 42)));
    }

    private void openDisplaySettings(boolean secondary, PopupWindow popup) {
        popup.dismiss();
        Intent intent = new Intent(this, DisplaySettingsActivity.class)
                .putExtra(DisplaySettingsActivity.EXTRA_SECONDARY, secondary)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void setOverlayTouchThrough(boolean secondary, boolean enabled) {
        if (enabled) {
            // "锁定并触摸穿透" promises a fully transparent overlay: it replaces the locked mode
            // rather than stacking on top of it, otherwise the playback pad would survive it.
            AppPreferences.putDisplayBoolean(this, secondary,
                    AppPreferences.KEY_OVERLAY_POSITION_LOCKED, false);
        }
        AppPreferences.putOverlayTouchThrough(this, secondary, enabled);
        applyOverlayInteraction(secondary);
        if (enabled) notifyPassThroughDimming();
    }

    /**
     * Applies whichever interaction mode the preferences ask for, for one overlay window.
     *
     * <p>A locked position cannot follow a drag, so the window stops taking touches altogether and
     * everything that is not a control falls through to whatever is underneath. The pieces that
     * stay useful get a touchable surface of their own: the playback buttons (when they are shown)
     * and the × handle that gets the user back out.
     */
    private void applyOverlayInteraction(boolean secondary) {
        WindowManager manager = secondary ? secondaryWindowManager : mainWindowManager;
        View panel = secondary ? secondaryPanel : mainPanel;
        WindowManager.LayoutParams params = secondary ? secondaryParams : mainParams;
        if (manager == null || panel == null || params == null || panel.getParent() == null) return;
        boolean touchThrough = AppPreferences.overlayTouchThrough(this, secondary);
        boolean locked = AppPreferences.overlayPositionLocked(this, secondary);
        boolean passThrough = touchThrough || locked;
        int touchFlag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        params.flags = passThrough ? params.flags | touchFlag : params.flags & ~touchFlag;
        // Android 12 blocks touches through opaque, non-touchable overlays as untrusted input.
        params.alpha = passThrough ? touchThroughWindowAlpha() : 1f;
        try {
            manager.updateViewLayout(panel, params);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to change overlay interaction", error);
            return;
        }
        boolean keepControls = locked && !touchThrough;
        if (passThrough) {
            if (!addUnlockHandle(secondary)) {
                // Without a way out the window must stay touchable, whatever the preference says.
                params.flags &= ~touchFlag;
                params.alpha = 1f;
                try { manager.updateViewLayout(panel, params); } catch (Throwable ignored) { }
                removePlaybackPad(secondary);
                return;
            }
        } else {
            removeUnlockHandle(secondary);
        }
        if (keepControls && AppPreferences.showPlaybackControls(this, secondary)) {
            addPlaybackPad(secondary);
        } else {
            removePlaybackPad(secondary);
        }
        DiagnosticLog.record(this, "Overlay", (passThrough
                ? (touchThrough ? "touch through" : "position locked, controls kept")
                : "touch through disabled") + " " + (secondary ? "secondary" : "main")
                + " alpha=" + params.alpha);
    }

    /**
     * A transparent window over exactly the playback buttons. While the overlay itself is
     * untouchable this is what keeps 上一首 / 播放暂停 / 下一首 usable, and taps anywhere else keep
     * falling through to the content below.
     */
    private void addPlaybackPad(boolean secondary) {
        WindowManager manager = secondary ? secondaryWindowManager : mainWindowManager;
        WindowManager.LayoutParams panelParams = secondary ? secondaryParams : mainParams;
        LyricsPanelView panel = secondary ? secondaryPanel : mainPanel;
        if (manager == null || panelParams == null || panel == null || panel.getParent() == null) {
            return;
        }
        android.graphics.Rect bounds = panel.playbackControlsBounds();
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            removePlaybackPad(secondary);
            return;
        }
        if (isPlaybackPadCurrent(secondary, bounds)) return;
        removePlaybackPad(secondary);
        Context context = secondary ? secondaryContext : this;
        View pad = new View(context);
        pad.setBackgroundColor(Color.TRANSPARENT);
        pad.setContentDescription("播放控制");
        WindowManager.LayoutParams padParams = new WindowManager.LayoutParams(
                bounds.width(), bounds.height(),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        padParams.gravity = Gravity.TOP | Gravity.START;
        Point screen = displaySize(secondary ? secondaryDisplay
                : mainWindowManager.getDefaultDisplay());
        padParams.x = clamp(panelParams.x + bounds.left, 0,
                Math.max(0, screen.x - bounds.width()));
        padParams.y = clamp(panelParams.y + bounds.top, 0,
                Math.max(0, screen.y - bounds.height()));
        final MediaControlAction[] pressed = new MediaControlAction[1];
        final boolean[] handledByLongPress = new boolean[1];
        final Handler padHandler = new Handler(Looper.getMainLooper());
        // The window itself no longer answers touches, so a long press here is the escape hatch
        // for users whose × handle is hidden by the close-button setting.
        final Runnable openMenu = () -> {
            if (pressed[0] == null) return;
            handledByLongPress[0] = true;
            pressed[0] = null;
            pad.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showOverlayQuickMenu(pad, secondary);
        };
        pad.setOnTouchListener((v, event) -> {
            float localX = event.getX() + bounds.left;
            float localY = event.getY() + bounds.top;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pressed[0] = panel.playbackControlAt(localX, localY);
                    handledByLongPress[0] = false;
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    padHandler.removeCallbacks(openMenu);
                    padHandler.postDelayed(openMenu, ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (pressed[0] != null && panel.playbackControlAt(localX, localY) != pressed[0]) {
                        pressed[0] = null;
                        padHandler.removeCallbacks(openMenu);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    padHandler.removeCallbacks(openMenu);
                    if (!handledByLongPress[0] && pressed[0] != null) {
                        MusicNotificationListener.requestPlaybackControl(
                                LyricsDisplayService.this, pressed[0]);
                    }
                    pressed[0] = null;
                    return true;
                default:
                    padHandler.removeCallbacks(openMenu);
                    pressed[0] = null;
                    return true;
            }
        });
        try {
            manager.addView(pad, padParams);
            if (secondary) {
                secondaryPlaybackPad = pad;
                secondaryPlaybackPadParams = padParams;
            } else {
                mainPlaybackPad = pad;
                mainPlaybackPadParams = padParams;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Unable to add playback pad", error);
        }
    }

    /** addView returns before the panel has a size; build or realign the pad after layout. */
    private void watchPlaybackPadLayout(LyricsPanelView panel, boolean secondary) {
        panel.addOnLayoutChangeListener((view, left, top, right, bottom,
                                         oldLeft, oldTop, oldRight, oldBottom) -> {
            if (view != (secondary ? secondaryPanel : mainPanel)
                    || right <= left || bottom <= top) return;
            if (AppPreferences.overlayPositionLocked(this, secondary)
                    && !AppPreferences.overlayTouchThrough(this, secondary)
                    && AppPreferences.showPlaybackControls(this, secondary)) {
                addPlaybackPad(secondary);
            }
        });
    }

    private boolean isPlaybackPadCurrent(boolean secondary, android.graphics.Rect bounds) {
        View pad = secondary ? secondaryPlaybackPad : mainPlaybackPad;
        WindowManager.LayoutParams padParams = secondary
                ? secondaryPlaybackPadParams : mainPlaybackPadParams;
        WindowManager.LayoutParams panelParams = secondary ? secondaryParams : mainParams;
        if (pad == null || pad.getParent() == null || padParams == null || panelParams == null) {
            return false;
        }
        return padParams.width == bounds.width() && padParams.height == bounds.height()
                && padParams.x == panelParams.x + bounds.left
                && padParams.y == panelParams.y + bounds.top;
    }

    private void removePlaybackPad(boolean secondary) {
        WindowManager manager = secondary ? secondaryWindowManager : mainWindowManager;
        View pad = secondary ? secondaryPlaybackPad : mainPlaybackPad;
        if (manager != null && pad != null && pad.getParent() != null) {
            try { manager.removeViewImmediate(pad); } catch (Throwable ignored) { }
        }
        if (secondary) {
            secondaryPlaybackPad = null;
            secondaryPlaybackPadParams = null;
        } else {
            mainPlaybackPad = null;
            mainPlaybackPadParams = null;
        }
    }

    /** Leaves either pass-through mode and gives the overlay its touches back. */
    private void exitOverlayPassThrough(boolean secondary) {
        AppPreferences.get(this).edit().putBoolean(secondary
                ? AppPreferences.KEY_SECONDARY_OVERLAY_TOUCH_THROUGH
                : AppPreferences.KEY_MAIN_OVERLAY_TOUCH_THROUGH, false).apply();
        AppPreferences.putDisplayBoolean(this, secondary,
                AppPreferences.KEY_OVERLAY_POSITION_LOCKED, false);
        applyOverlayInteraction(secondary);
        // The × sits right next to the lyrics, so it gets tapped by accident while reaching for
        // them. Say what just happened instead of silently clearing both switches (issue #36);
        // the extra screens already announce their own unlock the same way.
        SafeToast.show(this, "已解除位置锁定与触摸穿透", android.widget.Toast.LENGTH_SHORT);
    }

    /**
     * Android 12 and later block touches that pass through a window which is not (nearly)
     * transparent, so a pass-through overlay has to give up five hundredths of its opacity. The
     * lyrics then look dimmer than the user's own 背景不透明度 asks for, which is worth a word
     * instead of leaving them to guess (issue #32). Below Android 12 nothing changes.
     */
    private void notifyPassThroughDimming() {
        if (touchThroughWindowAlpha() >= 1f) return;
        SafeToast.show(this, "Android 12 及以上要求穿透窗口的不透明度低于 80%，"
                        + "所以整窗（包括歌词）会比平时略暗；这是系统限制，关掉锁定/穿透即恢复。",
                android.widget.Toast.LENGTH_LONG);
    }

    private static float touchThroughWindowAlpha() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? 0.79f : 1f;
    }

    /**
     * One tap only arms the × handle, a second tap inside {@link #EXIT_CONFIRM_MS} leaves
     * pass-through. It used to exit on the first touch, which is exactly what happens when the
     * user reaches for the lyrics and hits the handle instead (issue #36).
     */
    private static final long EXIT_CONFIRM_MS = 3_000L;

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
        String closeMode = AppPreferences.overlayCloseMode(this);
        boolean weakened = "fade".equals(closeMode);
        circle.setColor(weakened ? 0x55202124 : 0xCC202124);
        circle.setStroke(dp(handle.getContext(), 1), weakened ? 0x55FFFFFF : 0xAAFFFFFF);
        // Armed look for the first tap of the two-tap exit: different enough to be noticed even
        // when the user did not mean to touch the handle at all (issue #36).
        final GradientDrawable armedCircle = new GradientDrawable();
        armedCircle.setShape(GradientDrawable.OVAL);
        armedCircle.setColor(0xE6FF8A00);
        armedCircle.setStroke(dp(handle.getContext(), 2), 0xFFFFFFFF);
        final float restingAlpha = weakened ? 0.42f : 1f;
        handle.setBackground(circle);
        handle.setAlpha(restingAlpha);
        if ("hidden".equals(closeMode)) handle.setVisibility(View.GONE);
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
        final Object armedTag = new Object();
        handle.setOnClickListener(v -> {
            if (handle.getTag() == armedTag) {
                handle.setTag(null);
                exitOverlayPassThrough(secondary);
                return;
            }
            // A single tap only shows what a second tap would do. The handle sits next to the
            // lyrics, so tapping it while reaching for them used to reset both lock switches
            // without a word (issue #36).
            handle.animate().cancel();
            handle.setTag(armedTag);
            handle.setBackground(armedCircle);
            handle.setAlpha(1f);
            SafeToast.show(this, "再点一次解除位置锁定与触摸穿透", android.widget.Toast.LENGTH_SHORT);
            handle.postDelayed(() -> {
                if (handle.getTag() != armedTag) return;
                handle.setTag(null);
                handle.setBackground(circle);
                handle.setAlpha(restingAlpha);
                scheduleHandleAutoFade(handle, closeMode, 1_000L);
            }, EXIT_CONFIRM_MS);
        });
        try {
            manager.addView(handle, handleParams);
            if (secondary) {
                secondaryUnlockHandle = handle;
                secondaryUnlockParams = handleParams;
            } else {
                mainUnlockHandle = handle;
                mainUnlockParams = handleParams;
            }
            scheduleHandleAutoFade(handle, closeMode, 2_000L);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to add overlay unlock handle", error);
            return false;
        }
        return true;
    }

    /**
     * The "weak"/"hidden" close modes do not show the handle all the time: it fades after a
     * moment. Re-armed from the two-tap exit so the handle keeps behaving the way that mode asks
     * for, and skipped while the handle is armed so the invitation stays visible.
     */
    private void scheduleHandleAutoFade(TextView handle, String closeMode, long delayMs) {
        final float target;
        if ("auto_fade".equals(closeMode)) {
            target = 0.42f;
        } else if ("auto_hide".equals(closeMode)) {
            // Keep the fixed upper-right hit target so a hidden handle can still unlock.
            target = 0f;
        } else {
            return;
        }
        handle.postDelayed(() -> {
            if (handle.getTag() != null) return;
            if (handle.getParent() != null) {
                handle.animate().alpha(target).setDuration(180L).start();
            }
        }, delayMs);
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
        removePlaybackPad(false);
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
        removePlaybackPad(true);
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

    /**
     * Brings up one window per extra screen in the user's list. Entry {@code i} always owns slot
     * {@code i + 2}, so unplugging one screen neither drops nor renames another one's settings.
     */
    private void rebuildExtras() {
        dismissExtras();
        if (displayManager == null || !canDrawOverlays()
                || AppPreferences.serviceStoppedByUser(this)) return;
        List<DisplaySlotRegistry.Entry> entries = DisplaySlotRegistry.entries(this);
        if (entries.isEmpty()) return;
        Display secondary = AppPreferences.secondaryEnabled(this) ? findSecondaryDisplay() : null;
        int secondaryId = secondary == null ? -1 : secondary.getDisplayId();
        // Screens that already carry a lyric window, for the duplicate-panel check below.
        List<DisplayIdentity.Screen> showingLyrics = new ArrayList<>();
        if (secondary != null) showingLyrics.add(screenOf(secondary));
        for (int index = 0; index < entries.size(); index++) {
            int slot = DisplaySlotRegistry.slotFor(index);
            DisplaySlotRegistry.Entry entry = entries.get(index);
            if (!entry.enabled) continue;
            Display display = DisplaySlotRegistry.resolve(entry, displayManager);
            if (display == null) {
                DiagnosticLog.record(this, "Display", "extra slot " + slot + " unavailable: "
                        + entry.describe());
                continue;
            }
            if (display.getDisplayId() == Display.DEFAULT_DISPLAY
                    || display.getDisplayId() == secondaryId) {
                // Two windows on one screen would draw the lyrics twice.
                DiagnosticLog.record(this, "Display", "extra slot " + slot + " skipped, display "
                        + display.getDisplayId() + " already shows lyrics");
                continue;
            }
            DisplayIdentity.Screen candidate = screenOf(display);
            for (DisplayIdentity.Screen other : showingLyrics) {
                String reason = DisplayIdentity.duplicateReason(other, candidate);
                if (reason.isEmpty()) continue;
                // A car can expose one physical panel as two Display channels. Both windows then
                // draw the same lyrics on top of each other: a second render pass for a slightly
                // bolder look (issue #23).
                DiagnosticLog.record(this, "Display", "extra slot " + slot + " looks like a screen "
                        + "already showing lyrics（" + reason + "）：叠在同一块屏上只是更粗更亮，"
                        + "还会多跑一遍渲染");
                break;
            }
            showingLyrics.add(candidate);
            showExtra(slot, display);
        }
    }

    /** One display reduced to what the duplicate-panel check needs (issue #23). */
    private static DisplayIdentity.Screen screenOf(Display display) {
        Point size = displaySize(display);
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        if (display != null) display.getRealMetrics(metrics);
        return new DisplayIdentity.Screen(display == null ? "" : display.getName(), size.x, size.y,
                metrics.densityDpi);
    }

    private void showExtra(int slot, Display display) {
        try {
            Context context = new DisplaySlotContext(createDisplayContext(display), slot);
            WindowManager manager = (WindowManager) context.getSystemService(WINDOW_SERVICE);
            if (manager == null) return;
            Point screen = displaySize(display);
            int width = Math.min(dp(context, AppPreferences.panelWidthDp(context, true)), screen.x);
            int height = Math.min(dp(context, AppPreferences.panelHeightDp(context, true)),
                    screen.y);
            LyricsPanelView panel = new LyricsPanelView(context, true);
            WindowManager.LayoutParams params = overlayParams(width, height);
            String style = AppPreferences.overlayStyle(context, true);
            String xKey = AppPreferences.overlayPositionKey(true, style, true);
            String yKey = AppPreferences.overlayPositionKey(true, style, false);
            params.x = clamp(AppPreferences.overlayPosition(context, true, style, true,
                    Math.max(0, (screen.x - width) / 2)), 0, Math.max(0, screen.x - width));
            params.y = clamp(AppPreferences.overlayPosition(context, true, style, false,
                    Math.max(0, Math.round(screen.y * 0.10f))), 0, Math.max(0, screen.y - height));
            ExtraOverlay overlay = new ExtraOverlay(slot, display, context, manager, params, panel,
                    xKey, yKey);
            attachExtraDrag(overlay, screen);
            manager.addView(panel, params);
            extraOverlays.add(overlay);
            DiagnosticLog.record(this, "Display", "extra slot " + slot + " attached id="
                    + display.getDisplayId() + " name=" + display.getName() + " style=" + style
                    + " position=" + params.x + "," + params.y);
        } catch (Throwable error) {
            DiagnosticLog.record(this, "Display", "extra slot " + slot + " attach failed="
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            Log.w(TAG, "Unable to add overlay for extra display slot " + slot, error);
        }
    }

    private void dismissExtras() {
        for (ExtraOverlay overlay : extraOverlays) {
            try {
                if (overlay.panel.getParent() != null) {
                    overlay.windowManager.removeViewImmediate(overlay.panel);
                }
            } catch (Throwable ignored) { }
        }
        extraOverlays.clear();
    }

    /** True while this slot already has its own window. */
    /**
     * Drag, tap and long press for an extra screen. It deliberately carries only the essentials —
     * move the window, remember where it was put, tap for the player, long press for that screen's
     * settings. The lock/click-through mode and its companion touch pad stay with the main and
     * secondary windows, which is where they were designed to be used.
     */
    private void attachExtraDrag(ExtraOverlay overlay, Point screen) {
        final ViewConfiguration configuration = ViewConfiguration.get(overlay.context);
        final int touchSlop = configuration.getScaledTouchSlop();
        final Handler handler = new Handler(Looper.getMainLooper());
        overlay.panel.setOnTouchListener(new View.OnTouchListener() {
            float downRawX;
            float downRawY;
            int downX;
            int downY;
            boolean moved;
            boolean longPressed;
            final Runnable longPress = new Runnable() {
                @Override public void run() {
                    if (moved) return;
                    longPressed = true;
                    overlay.panel.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
            };

            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        downX = overlay.params.x;
                        downY = overlay.params.y;
                        moved = false;
                        longPressed = false;
                        handler.postDelayed(longPress, configuration.getLongPressTimeout());
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (dx * dx + dy * dy > touchSlop * touchSlop) {
                            moved = true;
                            handler.removeCallbacks(longPress);
                        }
                        if (!moved || AppPreferences.overlayPositionLocked(overlay.context, true)) {
                            return true;
                        }
                        overlay.params.x = clamp(downX + Math.round(dx), 0,
                                Math.max(0, screen.x - overlay.params.width));
                        overlay.params.y = clamp(downY + Math.round(dy), 0,
                                Math.max(0, screen.y - overlay.params.height));
                        try { overlay.windowManager.updateViewLayout(view, overlay.params); }
                        catch (Throwable ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(longPress);
                        if (moved) {
                            AppPreferences.putOverlayPosition(overlay.context, true,
                                    overlay.xKey, overlay.params.x);
                            AppPreferences.putOverlayPosition(overlay.context, true,
                                    overlay.yKey, overlay.params.y);
                        } else if (longPressed) {
                            boolean locked = AppPreferences.overlayPositionLocked(
                                    overlay.context, true);
                            AppPreferences.putDisplayBoolean(overlay.context, true,
                                    AppPreferences.KEY_OVERLAY_POSITION_LOCKED, !locked);
                            SafeToast.show(LyricsDisplayService.this, locked
                                            ? "已解除本屏位置锁定"
                                            : "已锁定本屏位置",
                                    android.widget.Toast.LENGTH_SHORT);
                        } else if (!MusicNotificationListener.openActivePlayer(
                                LyricsDisplayService.this)) {
                            openMainActivity();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPress);
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    /** One additional screen's window: its own display-scoped context, panel and position. */
    private static final class ExtraOverlay {
        final int slot;
        final Display display;
        final Context context;
        final WindowManager windowManager;
        final WindowManager.LayoutParams params;
        final LyricsPanelView panel;
        final String xKey;
        final String yKey;

        ExtraOverlay(int slot, Display display, Context context, WindowManager windowManager,
                     WindowManager.LayoutParams params, LyricsPanelView panel, String xKey,
                     String yKey) {
            this.slot = slot;
            this.display = display;
            this.context = context;
            this.windowManager = windowManager;
            this.params = params;
            this.panel = panel;
            this.xKey = xKey;
            this.yKey = yKey;
        }
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

    /** A non-interactive, top-pinned lyric strip. Android keeps status-bar icons above it. */
    private void showStatusLyricStrip() {
        WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (manager == null) return;
        int screenWidth = displaySize(manager.getDefaultDisplay()).x;
        if (screenWidth <= 0) return;
        int regionPercent = AppPreferences.topLyricRegionPercent(this);
        int stripWidth = Math.max(dp(this, 160), screenWidth * regionPercent / 100);
        int topInset = statusBarHeightPx();
        int contentHeight = AppPreferences.topLyricSpectrum(this) ? 66 : 44;
        WindowManager.LayoutParams params = overlayParams(screenWidth,
                topInset + dp(this, contentHeight));
        boolean windowBlurActive = applyTopLyricBlur(params, manager);
        // Keep the transparent renderer in the status area. System icons remain on top and
        // the two lyric lines are centered through the remaining horizontal space.
        params.width = stripWidth;
        int centeredX = (screenWidth - stripWidth) / 2;
        params.x = clamp(centeredX + dp(this, AppPreferences.topLyricOffsetXDp(this)),
                0, Math.max(0, screenWidth - stripWidth));
        params.y = -Math.max(dp(this, 6), topInset * 2 / 3)
                + dp(this, AppPreferences.topLyricOffsetYDp(this));
        // This strip is informational only: every tap falls through to the launcher/player.
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        // ...but Android 12+ refuses to pass touches through an opaque non-touchable overlay, so
        // the alpha has to come down for the pass-through to actually happen.
        params.alpha = touchThroughWindowAlpha();
        if (statusLyricStrip != null) {
            // Settings may change while the strip stays attached. Reload its compact renderer
            // and update its actual window bounds instead of returning with stale values.
            statusLyricStrip.reloadStyle();
            statusLyricStrip.setTopWindowBlurActive(windowBlurActive);
            try {
                manager.updateViewLayout(statusLyricStrip, params);
                statusLyricParams = params;
            } catch (Throwable error) {
                Log.w(TAG, "Unable to update top lyric strip", error);
                dismissStatusLyricStrip();
                scheduleStatusLyricRetry();
            }
            return;
        }
        LyricsPanelView strip = new LyricsPanelView(this, false, false, true);
        strip.setTopWindowBlurActive(windowBlurActive);
        try {
            manager.addView(strip, params);
            statusLyricStrip = strip;
            statusLyricParams = params;
            strip.reloadStyle();
            statusLyricRetryAttempts = 0;
            recoveryHandler.removeCallbacks(statusLyricRetry);
            DiagnosticLog.record(this, "Overlay", "top lyric strip attached widthPx="
                    + screenWidth + " heightPx=" + params.height);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to add top lyric strip", error);
            DiagnosticLog.record(this, "Overlay", "top lyric strip attach failed="
                    + error.getClass().getSimpleName());
            statusLyricStrip = null;
            statusLyricParams = null;
            scheduleStatusLyricRetry();
        }
    }

    private void scheduleStatusLyricRetry() {
        if (!AppPreferences.topLyricStrip(this) || statusLyricRetryAttempts >= 5) return;
        long delay = Math.min(15_000L, 1_500L << statusLyricRetryAttempts++);
        recoveryHandler.removeCallbacks(statusLyricRetry);
        recoveryHandler.postDelayed(statusLyricRetry, delay);
        DiagnosticLog.record(this, "Overlay", "top lyric strip retry="
                + statusLyricRetryAttempts + " delayMs=" + delay);
    }

    private void showBottomSpectrum() {
        bottomSpectrumManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (bottomSpectrumManager == null) return;
        int width = displaySize(bottomSpectrumManager.getDefaultDisplay()).x;
        if (width <= 0) return;
        bottomSpectrumView = new BottomSpectrumView(this);
        WindowManager.LayoutParams params = overlayParams(width,
                dp(this, AppPreferences.bottomSpectrumHeightDp(this)));
        params.gravity = Gravity.BOTTOM | Gravity.START;
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try {
            bottomSpectrumManager.addView(bottomSpectrumView, params);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to add bottom spectrum", error);
            dismissBottomSpectrum();
        }
    }

    private void dismissBottomSpectrum() {
        if (bottomSpectrumManager != null && bottomSpectrumView != null
                && bottomSpectrumView.getParent() != null) {
            try { bottomSpectrumManager.removeViewImmediate(bottomSpectrumView); }
            catch (Throwable ignored) { }
        }
        bottomSpectrumView = null;
        bottomSpectrumManager = null;
    }

    private void updateStatusLyricStrip(MusicSnapshot snapshot) {
        if (statusLyricStrip != null) statusLyricStrip.postInvalidateOnAnimation();
    }

    private boolean applyTopLyricBlur(WindowManager.LayoutParams params, WindowManager manager) {
        if (Build.VERSION.SDK_INT >= 31) {
            // FLAG_BLUR_BEHIND is implemented as a display-wide backdrop on some ROMs, even
            // when this overlay window itself is narrow. Never use it for a lyric strip: the
            // compact renderer supplies the safe local translucent-glass fallback instead.
            params.setBlurBehindRadius(0);
            params.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        }
        return false;
    }

    private void dismissStatusLyricStrip() {
        if (statusLyricStrip != null && statusLyricStrip.getParent() != null) {
            try { ((WindowManager) getSystemService(WINDOW_SERVICE)).removeViewImmediate(statusLyricStrip); }
            catch (Throwable ignored) { }
        }
        statusLyricStrip = null;
        statusLyricParams = null;
        if (!AppPreferences.topLyricStrip(this)) {
            statusLyricRetryAttempts = 0;
            recoveryHandler.removeCallbacks(statusLyricRetry);
        }
    }

    private int statusBarHeightPx() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id == 0 ? dp(this, 24) : getResources().getDimensionPixelSize(id);
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
        return AppPreferences.autoStartOverlays(context)
                || !AppPreferences.serviceStoppedByUser(context)
                && (AppPreferences.mainEnabled(context)
                || AppPreferences.secondaryEnabled(context)
                || !DisplaySlotRegistry.entries(context).isEmpty()
                || AppPreferences.notificationLyrics(context)
                || AppPreferences.topLyricStrip(context)
                || AppPreferences.bottomSpectrum(context));
    }

    private static boolean hasRememberedOverlayTarget(Context context) {
        return AppPreferences.mainEnabled(context) || AppPreferences.secondaryEnabled(context)
                || !DisplaySlotRegistry.entries(context).isEmpty()
                || AppPreferences.topLyricStrip(context) || AppPreferences.bottomSpectrum(context);
    }

    private void syncScreenReceiver() {
        if (AppPreferences.autoStartOverlays(this)) {
            if (screenReceiverRegistered) return;
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(screenReceiver, filter);
            }
            screenReceiverRegistered = true;
        } else {
            unregisterScreenReceiver();
        }
    }

    private void unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return;
        try { unregisterReceiver(screenReceiver); }
        catch (Throwable ignored) { }
        screenReceiverRegistered = false;
    }

    private void refreshPlaybackNotification() {
        MusicSnapshot snapshot = MusicStateStore.snapshot(AppPreferences.lyricOffsetMs(this));
        syncOverlayVisibility(snapshot);
        updateStatusLyricStrip(snapshot);
        boolean showLyrics = AppPreferences.notificationLyrics(this);
        String lyric = showLyrics ? notificationLyric(snapshot) : "正在同步播放器与歌词时间轴";
        String translation = showLyrics ? notificationTranslation(snapshot) : "";
        String trackTitle = snapshot.active && !snapshot.title.trim().isEmpty()
                ? snapshot.title : getString(R.string.service_notification_title);
        // OEMs commonly show only the collapsed notification title. Put the current lyric
        // there instead of the track title so it remains useful in narrow notification areas.
        String title = showLyrics ? lyric : getString(R.string.service_notification_title);
        String subtext = showLyrics && snapshot.active
                ? joinMetadata(snapshot.artist, snapshot.lyricSourceName) : "";
        String signature = showLyrics + "|" + title + "|" + trackTitle + "|" + lyric + "|" + translation + "|"
                + subtext + "|" + snapshot.playing + "|"
                + AppPreferences.lockscreenLyrics(this);
        if (signature.equals(lastNotificationSignature)) return;
        lastNotificationSignature = signature;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID,
                createNotification(title, lyric, translation, subtext, trackTitle));
    }

    private boolean shouldHideOverlays() {
        return shouldHideOverlays(false);
    }

    private boolean shouldHideOverlays(boolean secondary) {
        MusicSnapshot snapshot = MusicStateStore.snapshot(AppPreferences.lyricOffsetMs(this));
        return shouldHideOverlays(snapshot, secondary);
    }

    private boolean shouldHideOverlays(MusicSnapshot snapshot) {
        return shouldHideOverlays(snapshot, false);
    }

    /** 「指定应用」这条规则在某一屏上的方向，用于诊断文案（issue #43）。 */
    private String appRuleMode(boolean secondary) {
        boolean on = secondary ? AppPreferences.hideSelectedAppsOnSecondary(this)
                : AppPreferences.hideSelectedAppsOnMain(this);
        if (!on) return "关闭";
        return AppPreferences.appRuleWhitelist(this, secondary) ? "白名单" : "黑名单";
    }

    private boolean shouldHideOverlays(MusicSnapshot snapshot, boolean secondary) {
        return shouldHideOverlays(snapshot, secondary, null);
    }

    /**
     * @param foregroundPackage the foreground package to judge with, or {@code null} to look it up
     */
    private boolean shouldHideOverlays(MusicSnapshot snapshot, boolean secondary,
                                       String foregroundPackage) {
        boolean hideInPlayer = AppPreferences.hideOverlaysInPlayer(this);
        boolean appRuleOn = secondary
                ? AppPreferences.hideSelectedAppsOnSecondary(this)
                : AppPreferences.hideSelectedAppsOnMain(this);
        boolean whitelist = AppPreferences.appRuleWhitelist(this, secondary);
        java.util.Set<String> listedApps = AppPreferences.hiddenOverlayApps(this);
        String foreground = foregroundPackage != null ? foregroundPackage
                : hideInPlayer || appRuleOn ? ForegroundAppDetector.foregroundPackage(this) : "";
        boolean playerInForeground = hideInPlayer && ForegroundAppDetector.samePackage(
                MusicNotificationListener.activePlayerPackageName(), foreground);
        // 黑名单 / 白名单的判定口径见 AppRuleDecision（issue #43 / #28）。白名单唯一的例外是
        // 「连使用情况访问都没授权」：那意味着永远识别不到任何前台应用，白名单会把歌词一律藏掉，
        // 用户只会以为应用坏了；这种可检测的情况按不隐藏处理，设置页与诊断日志都会提示去授权。
        boolean whitelistUsable = !foreground.isEmpty() || ForegroundAppDetector.hasUsageAccess(this);
        boolean appRuleSaysHide = AppRuleDecision.hides(appRuleOn, whitelist, whitelistUsable,
                listedApps, foreground);
        return OverlayPlaybackVisibility.shouldHide(
                AppPreferences.hideOverlaysWhenNotPlaying(this), snapshot.playing,
                hideInPlayer, playerInForeground, appRuleSaysHide);
    }

    private void syncOverlayVisibility(MusicSnapshot snapshot) {
        String foreground = ForegroundAppDetector.foregroundPackage(this);
        boolean hideMain = shouldHideOverlays(snapshot, false, foreground);
        boolean hideSecondary = shouldHideOverlays(snapshot, true, foreground);
        boolean usageAccess = ForegroundAppDetector.hasUsageAccess(this);
        String diagnostic = "visibility mainHidden=" + hideMain
                + " secondaryHidden=" + hideSecondary
                + " foreground=" + (foreground.isEmpty() ? "空" : foreground)
                + " usageAccess=" + usageAccess
                + " 规则=主屏" + appRuleMode(false) + "/副屏" + appRuleMode(true);
        if (!diagnostic.equals(lastVisibilityDiagnostic)) {
            DiagnosticLog.record(this, "Overlay", diagnostic);
            lastVisibilityDiagnostic = diagnostic;
        }
        if (hideMain == overlaysHiddenForPlayback && hideSecondary == secondaryHiddenForPlayback) return;
        overlaysHiddenForPlayback = hideMain;
        secondaryHiddenForPlayback = hideSecondary;
        if (hideMain) {
            dismissMain();
            dismissStatusLyricStrip();
            dismissBottomSpectrum();
        } else if (canDrawOverlays()) {
            if (AppPreferences.mainEnabled(this) && !settingsVisible && mainPanel == null) showMain();
            if (AppPreferences.topLyricStrip(this) && statusLyricStrip == null) showStatusLyricStrip();
            if (AppPreferences.bottomSpectrum(this) && bottomSpectrumView == null) showBottomSpectrum();
        }
        if (hideSecondary) {
            dismissSecondary();
            dismissExtras();
        } else if (canDrawOverlays()) {
            if (AppPreferences.secondaryEnabled(this) && secondaryPanel == null) showSecondary();
            if (extraOverlays.isEmpty()) rebuildExtras();
        }
    }

    private Notification createNotification() {
        MusicSnapshot snapshot = MusicStateStore.snapshot(AppPreferences.lyricOffsetMs(this));
        boolean showLyrics = AppPreferences.notificationLyrics(this);
        String lyric = showLyrics ? notificationLyric(snapshot) : "正在同步播放器与歌词时间轴";
        String translation = showLyrics ? notificationTranslation(snapshot) : "";
        String trackTitle = snapshot.active && !snapshot.title.trim().isEmpty()
                ? snapshot.title : getString(R.string.service_notification_title);
        String title = showLyrics ? lyric : getString(R.string.service_notification_title);
        String subtext = showLyrics && snapshot.active
                ? joinMetadata(snapshot.artist, snapshot.lyricSourceName) : "";
        return createNotification(title, lyric, translation, subtext, trackTitle);
    }

    private Notification createNotification(String title, String lyric, String translation,
                                            String subtext, String trackTitle) {
        Intent open = new Intent(this, MainActivity.class);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open,
                pendingFlags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        String compactLyric = compactNotificationText(lyric, 180);
        String compactTranslation = compactNotificationText(translation, 180);
        String track = compactNotificationText(trackTitle, 120);
        builder.setSmallIcon(Build.VERSION.SDK_INT >= 21
                        ? R.drawable.ic_launcher : android.R.drawable.ic_media_play)
                .setContentTitle(compactLyric)
                .setContentText(compactTranslation.isEmpty() ? track : compactTranslation)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false);
        if (!subtext.isEmpty()) builder.setSubText(compactNotificationText(subtext, 120));
        Notification.BigTextStyle expanded = new Notification.BigTextStyle()
                .setBigContentTitle(track)
                .bigText(compactTranslation.isEmpty() ? compactLyric
                        : compactLyric + "\n" + compactTranslation);
        if (!subtext.isEmpty()) expanded.setSummaryText(compactNotificationText(subtext, 120));
        builder.setStyle(expanded);
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
        if (!snapshot.lyricLoaded) {
            // 面板在匹配中显示歌名 + 发散动画，通知栏说同一件事，避免"面板显示歌名、通知说正在匹配"
            // 的不一致（issue #45）。
            return snapshot.title.trim().isEmpty() ? "正在匹配歌词"
                    : snapshot.title.trim() + "  ·  正在匹配歌词";
        }
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

    /** Keeps both collapsed and expanded notification layouts within OEM text limits. */
    private static String compactNotificationText(String value, int maxCodePoints) {
        if (value == null) return "";
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.codePointCount(0, compact.length()) <= maxCodePoints) return compact;
        int end = compact.offsetByCodePoints(0, Math.max(1, maxCodePoints - 1));
        return compact.substring(0, end).trim() + "…";
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
