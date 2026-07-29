package com.zuoqirun.lyricscompanion;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Keeps the selected MediaSession fresh even on players that omit metadata callbacks. */
public final class MusicNotificationListener extends NotificationListenerService {
    private static final String TAG = "LyricsMediaSession";
    private static final long SESSION_POLL_MS = 600L;
    private static final long EMPTY_SESSION_GRACE_MS = 5_000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<MediaSession.Token, MediaController> observedControllers = new HashMap<>();
    private MediaSessionManager sessionManager;
    private MediaController controller;
    private boolean connected;
    private long lastNonEmptySessionElapsedMs;
    private static volatile boolean listenerConnected;
    private static volatile long lastSuccessfulSessionReadElapsedMs;
    private static volatile int lastSessionCount;
    private static volatile String lastSessionError = "";

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged =
            this::onSessionsChanged;
    private final MediaController.Callback sessionCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { refreshSessions(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { refreshSessions(); }
        @Override public void onSessionDestroyed() { refreshSessions(); }
    };
    private final Runnable sessionPoll = new Runnable() {
        @Override public void run() {
            if (!connected) return;
            refreshSessions();
            handler.postDelayed(this, SESSION_POLL_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        MusicStateStore.initialize(this);
        sessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        lastNonEmptySessionElapsedMs = SystemClock.elapsedRealtime();
    }

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        connected = true;
        listenerConnected = true;
        Log.i(TAG, "Notification listener connected");
        try {
            if (sessionManager != null) {
                sessionManager.addOnActiveSessionsChangedListener(sessionsChanged,
                        new ComponentName(this, MusicNotificationListener.class), handler);
            }
        } catch (Throwable error) {
            lastSessionError = "监听会话变化失败：" + safeMessage(error);
            Log.w(TAG, "Unable to subscribe to active sessions", error);
        }
        handler.removeCallbacks(sessionPoll);
        refreshSessions();
        handler.postDelayed(sessionPoll, SESSION_POLL_MS);
        if (AppPreferences.mainEnabled(this) || AppPreferences.secondaryEnabled(this)) {
            LyricsDisplayService.startOrRefresh(this);
        }
    }

    @Override public void onListenerDisconnected() {
        stopListening();
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        refreshSessions();
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        refreshSessions();
    }

    @Override public void onDestroy() {
        stopListening();
        super.onDestroy();
    }

    private void refreshSessions() {
        try {
            if (sessionManager == null) throw new IllegalStateException("MediaSessionManager 不可用");
            List<MediaController> sessions = sessionManager.getActiveSessions(
                    new ComponentName(this, MusicNotificationListener.class));
            if (sessions == null) sessions = Collections.emptyList();
            lastSuccessfulSessionReadElapsedMs = SystemClock.elapsedRealtime();
            lastSessionCount = sessions.size();
            onSessionsChanged(sessions);
        } catch (Throwable error) {
            lastSessionError = safeMessage(error);
            Log.w(TAG, "Unable to read active sessions", error);
        }
    }

    private void onSessionsChanged(List<MediaController> sessions) {
        syncObservedSessions(sessions);
        MediaController best = null;
        int bestScore = Integer.MIN_VALUE;
        if (sessions != null) {
            for (MediaController candidate : sessions) {
                if (candidate == null || getPackageName().equals(candidate.getPackageName())) {
                    continue;
                }
                if (!isUsableSession(candidate)) continue;
                MediaMetadata metadata = candidate.getMetadata();
                PlaybackState state = candidate.getPlaybackState();
                MusicAppRegistry.App app = MusicAppRegistry.resolve(candidate.getPackageName(),
                        applicationLabel(candidate.getPackageName()));
                int score = MusicAppRegistry.selectionScore(playbackRank(state),
                        hasMetadata(metadata), supportsControls(state), app.known,
                        sameSession(controller, candidate));
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        controller = best;
        if (best == null) {
            long now = SystemClock.elapsedRealtime();
            if (shouldClearAfterEmpty(lastNonEmptySessionElapsedMs, now)) {
                MusicStateStore.clear();
            }
            return;
        }
        lastNonEmptySessionElapsedMs = SystemClock.elapsedRealtime();
        MusicAppRegistry.App app = MusicAppRegistry.resolve(best.getPackageName(),
                applicationLabel(best.getPackageName()));
        MusicStateStore.update(this, app.sourceId, app.displayName,
                best.getMetadata(), best.getPlaybackState());
    }

    private void syncObservedSessions(List<MediaController> sessions) {
        Map<MediaSession.Token, MediaController> next = new HashMap<>();
        if (sessions != null) {
            for (MediaController candidate : sessions) {
                if (candidate == null || getPackageName().equals(candidate.getPackageName())) {
                    continue;
                }
                MediaSession.Token token = candidate.getSessionToken();
                next.put(token, candidate);
                if (!observedControllers.containsKey(token)) {
                    candidate.registerCallback(sessionCallback, handler);
                }
            }
        }
        for (Map.Entry<MediaSession.Token, MediaController> entry
                : observedControllers.entrySet()) {
            if (!next.containsKey(entry.getKey())) {
                entry.getValue().unregisterCallback(sessionCallback);
            }
        }
        observedControllers.clear();
        observedControllers.putAll(next);
    }

    private void stopListening() {
        connected = false;
        listenerConnected = false;
        handler.removeCallbacks(sessionPoll);
        try {
            if (sessionManager != null) {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged);
            }
        } catch (Throwable ignored) { }
        for (MediaController observed : observedControllers.values()) {
            observed.unregisterCallback(sessionCallback);
        }
        observedControllers.clear();
        controller = null;
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
        if (context == null || !hasNotificationAccess(context)
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        try {
            NotificationListenerService.requestRebind(
                    new ComponentName(context, MusicNotificationListener.class));
        } catch (Throwable error) {
            lastSessionError = "请求重连失败：" + safeMessage(error);
            Log.w(TAG, "Unable to request notification-listener rebind", error);
        }
    }

    static boolean shouldClearAfterEmpty(long lastNonEmptyElapsedMs, long nowElapsedMs) {
        return nowElapsedMs - lastNonEmptyElapsedMs >= EMPTY_SESSION_GRACE_MS;
    }

    private static boolean sameSession(MediaController left, MediaController right) {
        return left == right || left != null && right != null
                && left.getSessionToken().equals(right.getSessionToken());
    }

    private static int playbackRank(PlaybackState state) {
        if (state == null) return 0;
        switch (state.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
                return 10_000;
            case PlaybackState.STATE_BUFFERING: return 9_000;
            case PlaybackState.STATE_CONNECTING: return 8_000;
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return 7_000;
            case PlaybackState.STATE_PAUSED: return 5_000;
            default: return 0;
        }
    }

    private static boolean isUsableSession(MediaController candidate) {
        PlaybackState state = candidate.getPlaybackState();
        int stateValue = state == null ? PlaybackState.STATE_NONE : state.getState();
        return playbackRank(state) > 0 || hasMetadata(candidate.getMetadata())
                && stateValue != PlaybackState.STATE_STOPPED
                && stateValue != PlaybackState.STATE_ERROR;
    }

    private static boolean hasMetadata(MediaMetadata metadata) {
        return metadata != null && (!empty(metadata.getString(MediaMetadata.METADATA_KEY_TITLE))
                || !empty(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)));
    }

    private static boolean supportsControls(PlaybackState state) {
        if (state == null) return false;
        long transportActions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        return (state.getActions() & transportActions) != 0L;
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

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "未知异常";
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message.trim();
    }
}
