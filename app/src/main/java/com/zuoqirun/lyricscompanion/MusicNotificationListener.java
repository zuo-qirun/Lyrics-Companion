package com.zuoqirun.lyricscompanion;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.app.Notification;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<MediaSession.Token, MediaController> observedControllers = new HashMap<>();
    private final Map<String, NotificationSnapshot> observedNotifications = new HashMap<>();
    private MediaSessionManager sessionManager;
    private MediaController controller;
    private boolean connected;
    private static volatile boolean listenerConnected;

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
            Log.w(TAG, "Unable to subscribe to active sessions", error);
        }
        handler.removeCallbacks(sessionPoll);
        seedNotifications();
        handler.post(sessionPoll);
        if (AppPreferences.mainEnabled(this) || AppPreferences.secondaryEnabled(this)) {
            LyricsDisplayService.startOrRefresh(this);
        }
    }

    @Override public void onListenerDisconnected() {
        stopListening();
        super.onListenerDisconnected();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        rememberNotification(sbn);
        refreshSessions();
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) observedNotifications.remove(sbn.getKey());
        refreshSessions();
    }

    @Override public void onDestroy() {
        stopListening();
        super.onDestroy();
    }

    private void refreshSessions() {
        List<MediaController> sessions = Collections.emptyList();
        try {
            if (sessionManager != null) {
                sessions = sessionManager.getActiveSessions(
                        new ComponentName(this, MusicNotificationListener.class));
            }
        } catch (Throwable error) {
            Log.w(TAG, "Unable to read active sessions", error);
        }
        onSessionsChanged(sessions);
    }

    private void onSessionsChanged(List<MediaController> sessions) {
        syncObservedSessions(sessions);
        MediaController best = null;
        MediaMetadata bestMetadata = null;
        int bestScore = Integer.MIN_VALUE;
        if (sessions != null) {
            for (MediaController candidate : sessions) {
                if (candidate == null || getPackageName().equals(candidate.getPackageName())) {
                    continue;
                }
                MediaMetadata nativeMetadata = candidate.getMetadata();
                MediaMetadata metadata = hasMetadata(nativeMetadata) ? nativeMetadata
                        : notificationMetadata(candidate.getPackageName());
                if (!isUsableSession(candidate, metadata)) continue;
                PlaybackState state = candidate.getPlaybackState();
                MusicAppRegistry.App app = MusicAppRegistry.resolve(candidate.getPackageName(),
                        applicationLabel(candidate.getPackageName()));
                int score = MusicAppRegistry.selectionScore(playbackRank(state),
                        hasMetadata(metadata), supportsControls(state), app.known,
                        sameSession(controller, candidate));
                if (!hasMetadata(nativeMetadata) && hasMetadata(metadata)) {
                    // A notification fallback is useful for old car players, but must not
                    // outrank a real playing session that publishes native metadata.
                    score -= 500;
                }
                if (score > bestScore) {
                    best = candidate;
                    bestMetadata = metadata;
                    bestScore = score;
                }
            }
        }
        controller = best;
        if (best == null) {
            MusicStateStore.clear();
            return;
        }
        MusicAppRegistry.App app = MusicAppRegistry.resolve(best.getPackageName(),
                applicationLabel(best.getPackageName()));
        MusicStateStore.update(this, app.sourceId, app.displayName,
                bestMetadata, best.getPlaybackState());
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
        observedNotifications.clear();
        controller = null;
        MusicStateStore.clear();
    }

    static boolean isListenerConnected() {
        return listenerConnected;
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

    private static boolean isUsableSession(MediaController candidate, MediaMetadata metadata) {
        PlaybackState state = candidate.getPlaybackState();
        int stateValue = state == null ? PlaybackState.STATE_NONE : state.getState();
        return playbackRank(state) > 0 || hasMetadata(metadata)
                && stateValue != PlaybackState.STATE_STOPPED
                && stateValue != PlaybackState.STATE_ERROR;
    }

    private void seedNotifications() {
        observedNotifications.clear();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) return;
            for (StatusBarNotification notification : active) rememberNotification(notification);
        } catch (Throwable error) {
            Log.d(TAG, "Unable to seed notification metadata", error);
        }
    }

    private void rememberNotification(StatusBarNotification sbn) {
        NotificationSnapshot snapshot = NotificationSnapshot.from(sbn);
        if (snapshot == null) {
            if (sbn != null) observedNotifications.remove(sbn.getKey());
            return;
        }
        observedNotifications.put(sbn.getKey(), snapshot);
    }

    private MediaMetadata notificationMetadata(String packageName) {
        NotificationSnapshot best = null;
        for (NotificationSnapshot snapshot : observedNotifications.values()) {
            if (!snapshot.packageName.equals(packageName) || !snapshot.isLikelyPlayback()) {
                continue;
            }
            if (best == null || snapshot.score() > best.score()
                    || snapshot.score() == best.score() && snapshot.postTime > best.postTime) {
                best = snapshot;
            }
        }
        if (best == null) return null;
        return best.metadata;
    }

    static int notificationCandidateScore(boolean transport, boolean mediaSession,
                                          boolean ongoing, boolean hasLargeIcon) {
        int score = transport ? 1_000 : 0;
        if (mediaSession) score += 900;
        if (ongoing) score += 300;
        if (hasLargeIcon) score += 100;
        return score;
    }

    private static final class NotificationSnapshot {
        final String packageName;
        final String title;
        final String artist;
        final Bitmap largeIcon;
        final MediaMetadata metadata;
        final boolean transport;
        final boolean mediaSession;
        final boolean ongoing;
        final long postTime;

        NotificationSnapshot(String packageName, String title, String artist, Bitmap largeIcon,
                             boolean transport, boolean mediaSession, boolean ongoing,
                             long postTime) {
            this.packageName = packageName;
            this.title = title;
            this.artist = artist;
            this.largeIcon = largeIcon;
            MediaMetadata.Builder builder = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, artist);
            if (largeIcon != null) {
                builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, largeIcon);
            }
            metadata = builder.build();
            this.transport = transport;
            this.mediaSession = mediaSession;
            this.ongoing = ongoing;
            this.postTime = postTime;
        }

        static NotificationSnapshot from(StatusBarNotification sbn) {
            if (sbn == null || sbn.getNotification() == null) return null;
            Notification notification = sbn.getNotification();
            Bundle extras = notification.extras;
            if (extras == null) return null;
            String title = text(extras.getCharSequence(Notification.EXTRA_TITLE));
            if (title.isEmpty()) {
                title = text(extras.getCharSequence(Notification.EXTRA_TITLE_BIG));
            }
            String artist = text(extras.getCharSequence(Notification.EXTRA_TEXT));
            if (artist.isEmpty()) {
                artist = text(extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
            }
            if (title.isEmpty()) return null;
            Object iconValue = extras.get(Notification.EXTRA_LARGE_ICON_BIG);
            if (!(iconValue instanceof Bitmap)) {
                iconValue = extras.get(Notification.EXTRA_LARGE_ICON);
            }
            Bitmap icon = iconValue instanceof Bitmap ? (Bitmap) iconValue
                    : notification.largeIcon;
            return new NotificationSnapshot(sbn.getPackageName(), title, artist, icon,
                    Notification.CATEGORY_TRANSPORT.equals(notification.category),
                    extras.containsKey("android.mediaSession"),
                    (notification.flags & Notification.FLAG_ONGOING_EVENT) != 0,
                    sbn.getPostTime());
        }

        boolean isLikelyPlayback() {
            return !title.isEmpty() && (transport || mediaSession || ongoing);
        }

        int score() {
            return notificationCandidateScore(transport, mediaSession, ongoing,
                    largeIcon != null);
        }

        private static String text(CharSequence value) {
            return value == null ? "" : value.toString().trim();
        }
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
}
