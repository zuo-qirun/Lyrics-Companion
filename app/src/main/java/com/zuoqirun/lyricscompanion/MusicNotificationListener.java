package com.zuoqirun.lyricscompanion;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.RemoteController;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/** Selects MediaSession on API 21+, or RemoteController on Android 4.4. */
public final class MusicNotificationListener extends NotificationListenerService
        implements RemoteController.OnClientUpdateListener {
    private static final String TAG = "LyricsMediaSession";
    private static final long SESSION_POLL_MS = 600L;
    private static final long EMPTY_SESSION_GRACE_MS = 5_000L;
    private static final long LEGACY_NATURAL_REBIND_GRACE_MS = 2_500L;
    private static final long PROCESS_CLASS_LOADED_ELAPSED_MS = SystemClock.elapsedRealtime();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MusicSessionReader sessionReader;
    private LegacyRemoteControllerReader legacyReader;
    private boolean connected;
    private int legacyConnectAttempts;
    private long lastNonEmptySessionElapsedMs;
    private static volatile boolean listenerConnected;
    private static volatile long lastSuccessfulSessionReadElapsedMs;
    private static volatile int lastSessionCount;
    private static volatile String lastSessionError = "";
    private static volatile String backendName = "";
    private static volatile long lastReconnectRequestElapsedMs;

    private final MusicSessionReader.Callback readerCallback = new MusicSessionReader.Callback() {
        @Override public void onReadSuccess(int sessionCount) {
            lastSuccessfulSessionReadElapsedMs = SystemClock.elapsedRealtime();
            lastSessionCount = Math.max(0, sessionCount);
            lastSessionError = "";
        }

        @Override public void onReadError(String message, Throwable error) {
            lastSessionError = message + (error == null ? "" : ": " + safeMessage(error));
            Log.w(TAG, message, error);
        }

        @Override public void onSession(String packageName, String applicationLabel,
                                        MusicPlaybackData data) {
            lastNonEmptySessionElapsedMs = SystemClock.elapsedRealtime();
            MusicAppRegistry.App app = MusicAppRegistry.resolve(packageName, applicationLabel);
            MusicStateStore.update(MusicNotificationListener.this,
                    app.sourceId, app.displayName, data);
        }

        @Override public void onNoSession() {
            long now = SystemClock.elapsedRealtime();
            if (shouldClearAfterEmpty(lastNonEmptySessionElapsedMs, now)) {
                MusicStateStore.clear();
            }
        }
    };

    private final Runnable sessionPoll = new Runnable() {
        @Override public void run() {
            if (!connected || sessionReader == null) return;
            sessionReader.refresh();
            handler.postDelayed(this, SESSION_POLL_MS);
        }
    };
    private final Runnable legacyConnect = new Runnable() {
        @Override public void run() {
            if (Build.VERSION.SDK_INT >= 21 || connected) return;
            legacyConnectAttempts++;
            startListening();
            if (!connected && legacyConnectAttempts < 10) {
                handler.postDelayed(this, 1_000L);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        MusicStateStore.initialize(this);
        lastNonEmptySessionElapsedMs = SystemClock.elapsedRealtime();
        // Android 4.4 predates onListenerConnected(); the system only creates this service
        // after the user grants notification-listener access, so onCreate is the bind signal.
        if (Build.VERSION.SDK_INT < 21) {
            legacyConnectAttempts = 0;
            handler.postDelayed(legacyConnect, 500L);
        }
    }

    @Override public void onListenerConnected() {
        if (Build.VERSION.SDK_INT < 21) return;
        startListening();
    }

    private void startListening() {
        if (connected) return;
        stopReader();
        ComponentName component = new ComponentName(this, MusicNotificationListener.class);
        if (Build.VERSION.SDK_INT >= 21) {
            backendName = "MediaSession";
            sessionReader = Api21.createReader(this, component, handler, readerCallback);
        } else {
            backendName = "RemoteController";
            legacyReader = new LegacyRemoteControllerReader(this, readerCallback, this);
            sessionReader = legacyReader;
            refreshLegacySourceApplication();
        }
        sessionReader.start();
        if (Build.VERSION.SDK_INT < 21
                && (legacyReader == null || !legacyReader.isRegistered())) {
            stopReader();
            return;
        }
        connected = true;
        listenerConnected = true;
        Log.i(TAG, "Notification listener connected on API " + Build.VERSION.SDK_INT
                + " using " + backendName);
        handler.removeCallbacks(sessionPoll);
        handler.postDelayed(sessionPoll, SESSION_POLL_MS);
        if (AppPreferences.mainEnabled(this) || AppPreferences.secondaryEnabled(this)) {
            LyricsDisplayService.startOrRefresh(this);
        }
    }

    @Override public void onListenerDisconnected() {
        stopListening();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (Build.VERSION.SDK_INT < 21) refreshLegacySourceApplication();
        if (sessionReader != null) sessionReader.refresh();
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (Build.VERSION.SDK_INT < 21) refreshLegacySourceApplication();
        if (sessionReader != null) sessionReader.refresh();
    }

    @Override public void onClientChange(boolean clearing) {
        if (Build.VERSION.SDK_INT < 21 && legacyReader != null) {
            legacyReader.onClientChange(clearing);
        }
    }

    @Override public void onClientMetadataUpdate(RemoteController.MetadataEditor editor) {
        if (Build.VERSION.SDK_INT < 21 && legacyReader != null) {
            legacyReader.onClientMetadataUpdate(editor);
        }
    }

    @Override public void onClientPlaybackStateUpdate(int state) {
        if (Build.VERSION.SDK_INT < 21 && legacyReader != null) {
            legacyReader.onClientPlaybackStateUpdate(state);
        }
    }

    @Override public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                                                       long currentPositionMs,
                                                       float playbackSpeed) {
        if (Build.VERSION.SDK_INT < 21 && legacyReader != null) {
            legacyReader.onClientPlaybackStateUpdate(state, stateChangeTimeMs,
                    currentPositionMs, playbackSpeed);
        }
    }

    @Override public void onClientTransportControlUpdate(int transportControlFlags) {
        if (Build.VERSION.SDK_INT < 21 && legacyReader != null) {
            legacyReader.onClientTransportControlUpdate(transportControlFlags);
        }
    }

    @Override public void onDestroy() {
        stopListening();
        super.onDestroy();
    }

    private void stopListening() {
        connected = false;
        listenerConnected = false;
        handler.removeCallbacks(sessionPoll);
        handler.removeCallbacks(legacyConnect);
        stopReader();
    }

    private void stopReader() {
        if (sessionReader != null) {
            try { sessionReader.stop(); }
            catch (Throwable ignored) { }
        }
        sessionReader = null;
        legacyReader = null;
    }

    /** Uses notification package names only to map legacy RemoteController metadata to a catalog. */
    private void refreshLegacySourceApplication() {
        if (Build.VERSION.SDK_INT >= 21) return;
        String selectedPackage = "";
        String selectedLabel = "";
        long selectedPostTime = Long.MIN_VALUE;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification notification : active) {
                    if (notification == null || getPackageName().equals(notification.getPackageName())) {
                        continue;
                    }
                    String packageName = notification.getPackageName();
                    String label = applicationLabel(packageName);
                    MusicAppRegistry.App app = MusicAppRegistry.resolve(packageName, label);
                    if (!app.known || notification.getPostTime() < selectedPostTime) continue;
                    selectedPackage = packageName;
                    selectedLabel = label;
                    selectedPostTime = notification.getPostTime();
                }
            }
        } catch (Throwable error) {
            lastSessionError = "识别 Android 4.4 播放器包名失败: " + safeMessage(error);
        }
        if (legacyReader != null) {
            legacyReader.setSourceApplication(selectedPackage, selectedLabel);
        }
    }

    private String applicationLabel(String packageName) {
        try {
            PackageManager manager = getPackageManager();
            ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            CharSequence label = manager.getApplicationLabel(info);
            return label == null ? "" : label.toString().trim();
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
            return "";
        }
    }

    static boolean isListenerConnected() {
        return listenerConnected;
    }

    static boolean isHealthy(long maxAgeMs) {
        return isHealthyAt(listenerConnected, lastSuccessfulSessionReadElapsedMs,
                SystemClock.elapsedRealtime(), maxAgeMs);
    }

    static boolean isHealthyAt(boolean connected, long lastReadElapsedMs,
                               long nowElapsedMs, long maxAgeMs) {
        long age = nowElapsedMs - lastReadElapsedMs;
        return connected && lastReadElapsedMs > 0L && age >= 0L
                && age < Math.max(1L, maxAgeMs);
    }

    static long getLastSuccessfulSessionReadElapsedMs() {
        return lastSuccessfulSessionReadElapsedMs;
    }

    static int getLastSessionCount() {
        return lastSessionCount;
    }

    static String getLastSessionError() {
        return lastSessionError;
    }

    static String getBackendName() {
        return backendName;
    }

    static boolean hasNotificationAccess(Context context) {
        if (context == null) return false;
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName expected = new ComponentName(context, MusicNotificationListener.class);
        for (String entry : enabled.split(":")) {
            if (expected.equals(ComponentName.unflattenFromString(entry))) return true;
        }
        return false;
    }

    static void requestReconnect(Context context) {
        if (context == null || !hasNotificationAccess(context) || isHealthy(3_000L)) return;
        long now = SystemClock.elapsedRealtime();
        if (Build.VERSION.SDK_INT < 24
                && now - PROCESS_CLASS_LOADED_ELAPSED_MS < LEGACY_NATURAL_REBIND_GRACE_MS) {
            return;
        }
        if (now - lastReconnectRequestElapsedMs < 2_000L) return;
        lastReconnectRequestElapsedMs = now;
        ComponentName component = new ComponentName(context, MusicNotificationListener.class);
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                Api24.requestRebind(component);
            } else {
                // requestRebind() was added in API 24. Toggling this exact service component
                // makes NotificationManager re-evaluate enabled listeners on Android 4.4-6.0.
                PackageManager manager = context.getPackageManager();
                manager.setComponentEnabledSetting(component,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
                manager.setComponentEnabledSetting(component,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            }
        } catch (Throwable error) {
            lastSessionError = "请求重连失败: " + safeMessage(error);
            Log.w(TAG, "Unable to request notification-listener rebind", error);
        }
    }

    static boolean shouldClearAfterEmpty(long lastNonEmptyElapsedMs, long nowElapsedMs) {
        return nowElapsedMs - lastNonEmptyElapsedMs >= EMPTY_SESSION_GRACE_MS;
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "未知异常";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message.trim();
    }

    @TargetApi(21)
    private static final class Api21 {
        static MusicSessionReader createReader(Context context, ComponentName component,
                                               Handler handler,
                                               MusicSessionReader.Callback callback) {
            return new ModernMediaSessionReader(context, component, handler, callback);
        }
    }

    @TargetApi(24)
    private static final class Api24 {
        static void requestRebind(ComponentName component) {
            NotificationListenerService.requestRebind(component);
        }
    }
}
