package com.zuoqirun.lyricscompanion;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
    private static final long LEGACY_REBIND_DISABLE_MS = 750L;
    private static final long COMPONENT_RECOVERY_DELAY_MS = 6_000L;
    private static final long COMPONENT_RECOVERY_COOLDOWN_MS = 15_000L;
    /**
     * 东风会话超过这么久没有任何变化（换歌 / 播放状态变化）就不再按住别的播放器（issue #75）：
     * 车机上它经常常驻却并不真的在放歌，用户看到的就是「一直停在东风播放器」。
     */
    static final long DFTC_HOLD_MAX_MS = 60_000L;
    private static final long PROCESS_CLASS_LOADED_ELAPSED_MS = SystemClock.elapsedRealtime();
    private static final Handler REBIND_HANDLER = new Handler(Looper.getMainLooper());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MusicSessionReader sessionReader;
    private LegacyRemoteControllerReader legacyReader;
    private DftcMediaSessionReader dftcReader;
    private final NotificationMusicReader notificationReader = new NotificationMusicReader();
    private boolean connected;
    private int legacyConnectAttempts;
    private long lastNonEmptySessionElapsedMs;
    private long lastStandardSessionElapsedMs;
    private int lastLoggedSessionCount = -1;
    private String lastLoggedSessionError = "";
    /** 上一条「被东风会话按住」的诊断指纹（包名 + 30 秒时间桶），用于限流（issue #75）。 */
    private String lastLoggedYieldedSession = "";
    private static volatile boolean listenerConnected;
    private static volatile long lastSuccessfulSessionReadElapsedMs;
    private static volatile int lastSessionCount;
    private static volatile String lastSessionError = "";
    private static volatile String backendName = "";
    private static volatile boolean notificationSessionActive;
    private static volatile long lastReconnectRequestElapsedMs;
    private static volatile long reconnectStartedElapsedMs;
    private static volatile long lastComponentRecoveryElapsedMs;
    private static volatile boolean legacyRebindInProgress;
    private static volatile MusicNotificationListener activeInstance;
    private static volatile String activePlayerPackageName = "";

    private final MusicSessionReader.Callback readerCallback = new MusicSessionReader.Callback() {
        @Override public void onReadSuccess(int sessionCount) {
            lastSuccessfulSessionReadElapsedMs = SystemClock.elapsedRealtime();
            lastSessionCount = Math.max(0, sessionCount);
            lastSessionError = "";
            reconnectStartedElapsedMs = 0L;
            if (lastLoggedSessionCount != lastSessionCount) {
                lastLoggedSessionCount = lastSessionCount;
                DiagnosticLog.record(MusicNotificationListener.this, "MediaSession",
                        "read success sessions=" + lastSessionCount + " backend=" + backendName);
            }
            lastLoggedSessionError = "";
        }

        @Override public void onReadError(String message, Throwable error) {
            lastSessionError = message + (error == null ? "" : ": " + safeMessage(error));
            Log.w(TAG, message, error);
            if (!lastSessionError.equals(lastLoggedSessionError)) {
                lastLoggedSessionError = lastSessionError;
                DiagnosticLog.record(MusicNotificationListener.this, "MediaSession",
                        "read error=" + lastSessionError);
            }
        }

        @Override public void onSession(String packageName, String applicationLabel,
                                        MusicPlaybackData data) {
            if (!hasUsableTitle(data)) {
                // Empty system sessions must neither replace a usable player nor suppress the
                // notification fallback. Some car media centers continuously report PLAYING
                // while publishing no metadata at all.
                refreshNotificationSession();
                return;
            }
            lastStandardSessionElapsedMs = SystemClock.elapsedRealtime();
            long now = SystemClock.elapsedRealtime();
            boolean dftcAdvancing = dftcSessionStillAdvancing(now);
            if (shouldYieldToActiveDftcSession(activePlayerPackageName,
                    dftcReader != null && dftcReader.hasUsableSession(),
                    dftcReader != null && dftcReader.reportsPlaying(),
                    packageName,
                    data != null && data.state == MusicPlaybackData.STATE_PLAYING,
                    dftcAdvancing,
                    AppPreferences.dftcAlwaysPreferred(MusicNotificationListener.this))) {
                logYieldedSession(packageName, dftcAdvancing, now);
                return;
            }
            acceptSession(packageName, applicationLabel, data);
        }

        @Override public void onNoSession() {
            if (BluetoothAvrcpReceiver.ownsCurrentState()) return;
            if (dftcReader != null && dftcReader.hasUsableSession()) return;
            if (refreshNotificationSession()) return;
            long now = SystemClock.elapsedRealtime();
            if (shouldClearAfterEmpty(lastNonEmptySessionElapsedMs, now)) {
                if (!activePlayerPackageName.isEmpty()) {
                    DiagnosticLog.record(MusicNotificationListener.this, "MediaSession",
                            "no usable session after graceMs=" + EMPTY_SESSION_GRACE_MS);
                }
                activePlayerPackageName = "";
                MusicStateStore.clear();
            }
        }
    };

    private final MusicSessionReader.Callback dftcReaderCallback =
            new MusicSessionReader.Callback() {
        @Override public void onReadSuccess(int sessionCount) {
            // The standard backend remains the notification-listener health signal. This reader
            // is an optional compatibility path for one player without a usable MediaSession.
        }

        @Override public void onReadError(String message, Throwable error) {
            Log.w(TAG, message, error);
            DiagnosticLog.record(MusicNotificationListener.this, "DftcMedia",
                    message + (error == null ? "" : ": " + safeMessage(error)));
        }

        @Override public void onSession(String packageName, String applicationLabel,
                                        MusicPlaybackData data) {
            long now = SystemClock.elapsedRealtime();
            boolean alreadyActive = "com.dftc.media".equals(activePlayerPackageName);
            boolean playing = data != null && data.state == MusicPlaybackData.STATE_PLAYING;
            if (!playing && !alreadyActive
                    && now - lastStandardSessionElapsedMs < EMPTY_SESSION_GRACE_MS) return;
            acceptSession(packageName, applicationLabel, data);
        }

        @Override public void onNoSession() {
            // A missing vendor session must not clear a valid standard MediaSession.
        }
    };

    /** 东风会话最近一次变化（换歌 / 状态变化）距今是否还在 {@link #DFTC_HOLD_MAX_MS} 以内（issue #75）。 */
    private boolean dftcSessionStillAdvancing(long nowElapsedMs) {
        if (dftcReader == null) return false;
        long evidence = dftcReader.lastEvidenceElapsedMs();
        if (evidence <= 0L) return false;
        return nowElapsedMs - evidence < DFTC_HOLD_MAX_MS;
    }

    private long dftcEvidenceAgeMs(long nowElapsedMs) {
        if (dftcReader == null) return -1L;
        long evidence = dftcReader.lastEvidenceElapsedMs();
        return evidence <= 0L ? -1L : Math.max(0L, nowElapsedMs - evidence);
    }

    /** 被「东风会话按住」而丢掉的会话记一条（同一路每 30 秒最多一条，避免 600ms 轮询刷屏）。 */
    private void logYieldedSession(String incomingPackage, boolean dftcAdvancing, long nowElapsedMs) {
        String key = incomingPackage + "\u0000" + (nowElapsedMs / 30_000L);
        if (key.equals(lastLoggedYieldedSession)) return;
        lastLoggedYieldedSession = key;
        DiagnosticLog.record(this, "MediaSession", "yielded incoming session package="
                + incomingPackage + " dftcAdvancing=" + dftcAdvancing
                + " dftcEvidenceAgeMs=" + dftcEvidenceAgeMs(nowElapsedMs));
    }

    private void acceptSession(String packageName, String applicationLabel,
                               MusicPlaybackData data) {
        String nextPackage = packageName == null ? "" : packageName;
        // 用户把某个应用列进「忽略这些应用的媒体元数据」后，它的发布不再进入歌词状态（issue #35）：
        // 车机自带媒体中心、导航或蓝牙通道抢歌词时，直接忽略掉它。
        if (AppPreferences.ignoredPlayerPackage(this, nextPackage)) return;
        MusicAppRegistry.App app = MusicAppRegistry.resolve(packageName, applicationLabel);
        // Channel arbitration happens inside MusicStateStore. Ask first so a write it is about to
        // drop does not leave the remembered player package or the active-player log pointing at a
        // channel that lost the slot.
        if (!MusicStateStore.isChannelAccepted(app.sourceId, nextPackage, data)) return;
        // 东风会话长时间没有变化、把活跃位让出来时留一条诊断，方便区分「没收到会话」和「收到了但被
        // 按住」（issue #75）。
        if ("com.dftc.media".equals(activePlayerPackageName) && !"com.dftc.media".equals(nextPackage)) {
            DiagnosticLog.record(this, "MediaSession", "dftc session released package="
                    + nextPackage + " dftcAdvancing=" + dftcSessionStillAdvancing(
                    SystemClock.elapsedRealtime()));
        }
        notificationSessionActive = false;
        lastNonEmptySessionElapsedMs = SystemClock.elapsedRealtime();
        if (!nextPackage.equals(activePlayerPackageName)) {
            DiagnosticLog.record(this, "MediaSession",
                    "active player changed package=" + nextPackage + " label=" + applicationLabel);
        }
        activePlayerPackageName = nextPackage;
        AppPreferences.rememberPlayerPackage(this, nextPackage);
        MusicStateStore.update(this, app.sourceId, app.displayName, nextPackage, data);
    }

    private final Runnable sessionPoll = new Runnable() {
        @Override public void run() {
            if (!connected || sessionReader == null) return;
            sessionReader.refresh();
            if (dftcReader != null) dftcReader.refresh();
            refreshNotificationSession();
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
    private final Runnable reconnectAfterSystemDisconnect = new Runnable() {
        @Override public void run() {
            if (Build.VERSION.SDK_INT >= 24 && !connected
                    && !AppPreferences.serviceStoppedByUser(MusicNotificationListener.this)
                    && hasNotificationAccess(MusicNotificationListener.this)) {
                requestReconnect(MusicNotificationListener.this);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        DiagnosticLog.record(this, "MediaSession", "listener service created api="
                + Build.VERSION.SDK_INT + " access=" + hasNotificationAccess(this));
        activeInstance = this;
        if (AppPreferences.serviceStoppedByUser(this)) {
            stopForExplicitExit();
            return;
        }
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
        if (AppPreferences.serviceStoppedByUser(this)) {
            stopForExplicitExit();
            return;
        }
        handler.removeCallbacks(reconnectAfterSystemDisconnect);
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
            // Notification metadata remains usable even when the RCC backend cannot register.
            backendName = "通知识别";
        }
        connected = true;
        dftcReader = new DftcMediaSessionReader(this, dftcReaderCallback);
        dftcReader.start();
        listenerConnected = true;
        DiagnosticLog.record(this, "MediaSession", "listener connected backend="
                + backendName + " api=" + Build.VERSION.SDK_INT);
        Log.i(TAG, "Notification listener connected on API " + Build.VERSION.SDK_INT
                + " using " + backendName);
        handler.removeCallbacks(sessionPoll);
        handler.postDelayed(sessionPoll, SESSION_POLL_MS);
        if (AppPreferences.mainEnabled(this) || AppPreferences.secondaryEnabled(this)
                || AppPreferences.notificationLyrics(this)
                || AppPreferences.topLyricStrip(this)) {
            LyricsDisplayService.startOrRefresh(this);
        }
    }

    @Override public void onListenerDisconnected() {
        DiagnosticLog.record(this, "MediaSession", "system disconnected listener");
        stopListening();
        // API 24+ tells us that the system side has disconnected. Retry from this lifecycle
        // callback instead of racing the initial bind when the permission screen closes.
        if (Build.VERSION.SDK_INT >= 24 && !AppPreferences.serviceStoppedByUser(this)
                && hasNotificationAccess(this)) {
            handler.postDelayed(reconnectAfterSystemDisconnect, 500L);
        }
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        handler.post(() -> refreshAfterNotification(sbn));
    }

    private void refreshAfterNotification(StatusBarNotification sbn) {
        if (!connected) return;
        notificationReader.invalidate(sbn);
        if (Build.VERSION.SDK_INT < 21) refreshLegacySourceApplication();
        if (sessionReader != null) sessionReader.refresh();
        if (dftcReader != null) dftcReader.refresh();
        refreshNotificationSession();
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        handler.post(() -> refreshAfterNotification(sbn));
    }

    private boolean refreshNotificationSession() {
        if (!connected || BluetoothAvrcpReceiver.ownsCurrentState()
                || dftcReader != null && dftcReader.hasUsableSession()) return false;
        long now = SystemClock.elapsedRealtime();
        if (lastStandardSessionElapsedMs > 0L
                && now - lastStandardSessionElapsedMs < EMPTY_SESSION_GRACE_MS) return false;
        try {
            NotificationMusicReader.Entry entry = notificationReader.select(this, getActiveNotifications());
            if (entry == null) return false;
            lastSuccessfulSessionReadElapsedMs = now;
            lastSessionCount = 1;
            lastSessionError = "";
            acceptSession(entry.packageName, entry.label, entry.data());
            notificationSessionActive = true;
            return true;
        } catch (RuntimeException error) {
            Log.d(TAG, "Notification fallback unavailable", error);
            return false;
        }
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
        DiagnosticLog.record(this, "MediaSession", "listener service destroyed");
        handler.removeCallbacks(reconnectAfterSystemDisconnect);
        stopListening();
        if (activeInstance == this) activeInstance = null;
        super.onDestroy();
    }

    static void stopObservation() {
        MusicNotificationListener instance = activeInstance;
        if (instance != null) instance.stopForExplicitExit();
    }

    private void stopForExplicitExit() {
        handler.removeCallbacks(legacyConnect);
        handler.removeCallbacks(reconnectAfterSystemDisconnect);
        stopListening();
        if (Build.VERSION.SDK_INT >= 24) {
            try { requestUnbind(); }
            catch (Throwable ignored) { }
        }
        stopSelf();
    }

    static boolean openActivePlayer(Context context) {
        String packageName = activePlayerPackageName;
        if (packageName == null || packageName.trim().isEmpty()) return false;
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(launch);
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "Unable to reopen active player " + packageName, error);
            return false;
        }
    }

    static String activePlayerPackageName() {
        String packageName = activePlayerPackageName;
        return packageName == null ? "" : packageName.trim();
    }

    static void requestPlaybackControl(Context context, MediaControlAction action) {
        MusicNotificationListener listener = activeInstance;
        if (listener == null || action == null) return;
        listener.handler.post(() -> listener.dispatchPlaybackControl(action));
    }

    private void dispatchPlaybackControl(MediaControlAction action) {
        if (!connected || sessionReader == null) {
            lastSessionError = "播放器控制不可用：通知读取服务未连接";
            DiagnosticLog.record(this, "MediaControl", "action=" + action
                    + " result=listener_unavailable");
            return;
        }
        if ("com.dftc.media".equals(activePlayerPackageName) && dftcReader != null
                && dftcReader.dispatchControl(action)) {
            DiagnosticLog.record(this, "MediaControl", "action=" + action
                    + " result=dispatched backend=DftcMedia");
            handler.postDelayed(() -> {
                if (dftcReader != null) dftcReader.refresh();
            }, 180L);
            return;
        }
        sessionReader.refresh();
        if (!sessionReader.dispatchControl(action)) {
            lastSessionError = "播放器未提供可用的控制会话";
            DiagnosticLog.record(this, "MediaControl", "action=" + action
                    + " result=no_controllable_session");
            return;
        }
        DiagnosticLog.record(this, "MediaControl", "action=" + action + " result=dispatched");
        handler.postDelayed(() -> {
            if (connected && sessionReader != null) sessionReader.refresh();
        }, 180L);
    }

    private void stopListening() {
        connected = false;
        listenerConnected = false;
        handler.removeCallbacks(sessionPoll);
        handler.removeCallbacks(legacyConnect);
        stopReader();
    }

    private void stopReader() {
        notificationSessionActive = false;
        if (dftcReader != null) {
            try { dftcReader.stop(); }
            catch (Throwable ignored) { }
        }
        dftcReader = null;
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
        return notificationSessionActive && !activePlayerPackageName.isEmpty()
                ? "通知识别（无播放进度）" : backendName;
    }

    static String getActivePlayerPackageName() {
        return activePlayerPackageName;
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
        if (context == null || !hasNotificationAccess(context)) return;
        if (isHealthy(3_000L)) {
            reconnectStartedElapsedMs = 0L;
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (reconnectStartedElapsedMs <= 0L) reconnectStartedElapsedMs = now;
        if (Build.VERSION.SDK_INT < 24
                && now - PROCESS_CLASS_LOADED_ELAPSED_MS < LEGACY_NATURAL_REBIND_GRACE_MS) {
            return;
        }
        if (now - lastReconnectRequestElapsedMs < 2_000L) return;
        lastReconnectRequestElapsedMs = now;
        Context appContext = context.getApplicationContext();
        ComponentName component = new ComponentName(appContext, MusicNotificationListener.class);
        DiagnosticLog.record(appContext, "MediaSession", "reconnect requested api="
                + Build.VERSION.SDK_INT + " unhealthyForMs="
                + Math.max(0L, now - reconnectStartedElapsedMs));
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                boolean shouldRecoverComponent = now - reconnectStartedElapsedMs
                        >= COMPONENT_RECOVERY_DELAY_MS
                        && (lastComponentRecoveryElapsedMs <= 0L
                        || now - lastComponentRecoveryElapsedMs >= COMPONENT_RECOVERY_COOLDOWN_MS);
                if (shouldRecoverComponent) {
                    lastComponentRecoveryElapsedMs = now;
                    lastSessionError = "\u7cfb\u7edf\u76d1\u542c\u5668\u672a\u7ed1\u5b9a\uff0c\u6b63\u5728\u91cd\u542f\u76d1\u542c\u7ec4\u4ef6";
                    requestLegacyRebind(appContext, component);
                } else {
                    Api24.requestRebind(component);
                }
            } else {
                requestLegacyRebind(appContext, component);
            }
        } catch (Throwable error) {
            lastSessionError = "请求重连失败: " + safeMessage(error);
            Log.w(TAG, "Unable to request notification-listener rebind", error);
        }
    }

    /**
     * Android 4.4-6.0 has no NotificationListenerService.requestRebind(). A back-to-back
     * disable/enable call is commonly coalesced by the package manager, leaving the access entry
     * present in Settings but without a live listener after the app process is recreated. Keep
     * the component disabled briefly so NotificationManager observes both transitions.
     */
    private static void requestLegacyRebind(Context context, ComponentName component) {
        if (legacyRebindInProgress) return;
        legacyRebindInProgress = true;
        DiagnosticLog.record(context, "MediaSession", "component recovery started component="
                + component.flattenToShortString());
        PackageManager manager = context.getPackageManager();
        try {
            manager.setComponentEnabledSetting(component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Throwable error) {
            legacyRebindInProgress = false;
            throw error;
        }
        REBIND_HANDLER.postDelayed(() -> {
            try {
                manager.setComponentEnabledSetting(component,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            } catch (Throwable error) {
                lastSessionError = "Unable to re-enable notification listener: "
                        + safeMessage(error);
                Log.w(TAG, "Unable to re-enable notification listener", error);
            } finally {
                legacyRebindInProgress = false;
            }
            if (Build.VERSION.SDK_INT >= 24 && hasNotificationAccess(context)) {
                try {
                    Api24.requestRebind(component);
                } catch (Throwable error) {
                    lastSessionError = "Unable to rebind notification listener: "
                            + safeMessage(error);
                    Log.w(TAG, "Unable to rebind notification listener", error);
                }
            }
        }, LEGACY_REBIND_DISABLE_MS);
    }

    static boolean shouldClearAfterEmpty(long lastNonEmptyElapsedMs, long nowElapsedMs) {
        return nowElapsedMs - lastNonEmptyElapsedMs >= EMPTY_SESSION_GRACE_MS;
    }

    static boolean hasUsableTitle(MusicPlaybackData data) {
        return data != null && data.title != null && !data.title.trim().isEmpty();
    }

    /**
     * Anti ping-pong guard for the Dongfeng head unit: while its vendor player owns the active
     * slot, transient system media cards (WecarFlow) may appear and vanish every few seconds.
     * Those blips must not steal the slot, or the visible source flips back and forth and the
     * lyric timeline reloads each time. The vendor player itself always proceeds; a different
     * player takes over only once it is playing while the vendor session stopped reporting
     * playback, or once the retained vendor snapshot ages out (usable=false).
     *
     * <p>四个参数的重载保留原语义（假定东风会话仍在推进），既有调用点与用例无需改动。
     */
    static boolean shouldYieldToActiveDftcSession(String activePlayerPackage, boolean dftcUsable,
                                                  boolean dftcReportsPlaying,
                                                  String incomingPackage, boolean incomingPlaying) {
        return shouldYieldToActiveDftcSession(activePlayerPackage, dftcUsable, dftcReportsPlaying,
                incomingPackage, incomingPlaying, true);
    }

    static boolean shouldYieldToActiveDftcSession(String activePlayerPackage, boolean dftcUsable,
                                                  boolean dftcReportsPlaying,
                                                  String incomingPackage, boolean incomingPlaying,
                                                  boolean dftcAdvancing) {
        return shouldYieldToActiveDftcSession(activePlayerPackage, dftcUsable, dftcReportsPlaying,
                incomingPackage, incomingPlaying, dftcAdvancing, false);
    }

    /**
     * 东风会话「按住」别的播放器的判据（issue #75）。
     *
     * <p>原来只要东风仍在报播放就按住一切：它的会话常驻却不真的在放歌时（一直报 PLAYING、位置不动），
     * 酷我之类的会话永远抢不到活跃位，用户看到的就是「停在东风播放器」。现在东风必须**仍在推进**
     * （位置前进或换歌）才继续按住；它超过 {@link #DFTC_HOLD_STALE_MS} 没动静、而进来的这一路又在播放，
     * 就让位给它。进来的一路没在播放时照旧让位，防抖语义不变。
     *
     * @param dftcAdvancing 东风会话最近 {@link #DFTC_HOLD_STALE_MS} 内是否推进过
     * @param alwaysPreferDftc 用户选了「始终优先东风会话」：回到老行为，不自动让位
     */
    static boolean shouldYieldToActiveDftcSession(String activePlayerPackage, boolean dftcUsable,
                                                  boolean dftcReportsPlaying,
                                                  String incomingPackage, boolean incomingPlaying,
                                                  boolean dftcAdvancing,
                                                  boolean alwaysPreferDftc) {
        if (!"com.dftc.media".equals(activePlayerPackage)) return false;
        if (!dftcUsable) return false;
        if ("com.dftc.media".equals(incomingPackage)) return false;
        if (alwaysPreferDftc) return !incomingPlaying || dftcReportsPlaying;
        return !incomingPlaying || (dftcReportsPlaying && dftcAdvancing);
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
