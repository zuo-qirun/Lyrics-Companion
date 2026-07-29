package com.zuoqirun.lyricscompanion;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Android 5.0+ implementation of the native active-MediaSession path. */
@TargetApi(21)
final class ModernMediaSessionReader implements MusicSessionReader {
    private final Context context;
    private final ComponentName listenerComponent;
    private final Handler handler;
    private final Callback callback;
    private final Map<MediaSession.Token, MediaController> observedControllers = new HashMap<>();
    private MediaSessionManager sessionManager;
    private MediaController selectedController;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged =
            this::handleSessions;
    private final MediaController.Callback sessionCallback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { refresh(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
        @Override public void onSessionDestroyed() { refresh(); }
    };

    ModernMediaSessionReader(Context context, ComponentName listenerComponent, Handler handler,
                             Callback callback) {
        this.context = context;
        this.listenerComponent = listenerComponent;
        this.handler = handler;
        this.callback = callback;
    }

    @Override public void start() {
        sessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (sessionManager == null) {
            callback.onReadError("MediaSessionManager 不可用", null);
            return;
        }
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                    sessionsChanged, listenerComponent, handler);
        } catch (Throwable error) {
            callback.onReadError("监听会话变化失败", error);
        }
        refresh();
    }

    @Override public void refresh() {
        try {
            if (sessionManager == null) throw new IllegalStateException("MediaSessionManager 不可用");
            List<MediaController> sessions = sessionManager.getActiveSessions(listenerComponent);
            if (sessions == null) sessions = Collections.emptyList();
            callback.onReadSuccess(sessions.size());
            handleSessions(sessions);
        } catch (Throwable error) {
            callback.onReadError("读取 MediaSession 失败", error);
        }
    }

    @Override public void stop() {
        try {
            if (sessionManager != null) {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged);
            }
        } catch (Throwable ignored) { }
        for (MediaController observed : observedControllers.values()) {
            try { observed.unregisterCallback(sessionCallback); }
            catch (Throwable ignored) { }
        }
        observedControllers.clear();
        selectedController = null;
        sessionManager = null;
    }

    private void handleSessions(List<MediaController> sessions) {
        syncObservedSessions(sessions);
        MediaController best = null;
        int bestScore = Integer.MIN_VALUE;
        if (sessions != null) {
            for (MediaController candidate : sessions) {
                if (candidate == null || context.getPackageName().equals(candidate.getPackageName())) {
                    continue;
                }
                if (!isUsableSession(candidate)) continue;
                MediaMetadata metadata = candidate.getMetadata();
                PlaybackState state = candidate.getPlaybackState();
                MusicAppRegistry.App app = MusicAppRegistry.resolve(candidate.getPackageName(),
                        applicationLabel(candidate.getPackageName()));
                int score = MusicAppRegistry.selectionScore(playbackRank(state),
                        hasMetadata(metadata), supportsControls(state), app.known,
                        sameSession(selectedController, candidate));
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        selectedController = best;
        if (best == null) {
            callback.onNoSession();
            return;
        }
        callback.onSession(best.getPackageName(), applicationLabel(best.getPackageName()),
                playbackData(best.getMetadata(), best.getPlaybackState()));
    }

    private void syncObservedSessions(List<MediaController> sessions) {
        Map<MediaSession.Token, MediaController> next = new HashMap<>();
        if (sessions != null) {
            for (MediaController candidate : sessions) {
                if (candidate == null || context.getPackageName().equals(candidate.getPackageName())) {
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
                try { entry.getValue().unregisterCallback(sessionCallback); }
                catch (Throwable ignored) { }
            }
        }
        observedControllers.clear();
        observedControllers.putAll(next);
    }

    private MusicPlaybackData playbackData(MediaMetadata metadata, PlaybackState state) {
        String title = firstNonEmpty(metadata,
                MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        String artist = firstNonEmpty(metadata,
                MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        String mediaId = firstNonEmpty(metadata, MediaMetadata.METADATA_KEY_MEDIA_ID);
        Bitmap art = firstBitmap(metadata, MediaMetadata.METADATA_KEY_ALBUM_ART,
                MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        String artUri = firstNonEmpty(metadata, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        long duration = metadata == null ? -1L
                : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        return new MusicPlaybackData(mediaId, title, artist, art, artUri, duration,
                state != null,
                state == null ? MusicPlaybackData.STATE_NONE : state.getState(),
                state == null ? 0L : Math.max(0L, state.getPosition()),
                state == null ? 0L : state.getLastPositionUpdateTime(),
                state == null ? 0f : state.getPlaybackSpeed());
    }

    private String applicationLabel(String packageName) {
        try {
            PackageManager manager = context.getPackageManager();
            ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            CharSequence label = manager.getApplicationLabel(info);
            return label == null ? "" : label.toString().trim();
        } catch (PackageManager.NameNotFoundException | SecurityException ignored) {
            return "";
        }
    }

    private static String firstNonEmpty(MediaMetadata metadata, String... keys) {
        if (metadata == null) return "";
        for (String key : keys) {
            String value = metadata.getString(key);
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static Bitmap firstBitmap(MediaMetadata metadata, String... keys) {
        if (metadata == null) return null;
        for (String key : keys) {
            Bitmap bitmap = metadata.getBitmap(key);
            if (bitmap != null) return bitmap;
        }
        return null;
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
        int value = state == null ? PlaybackState.STATE_NONE : state.getState();
        return playbackRank(state) > 0 || hasMetadata(candidate.getMetadata())
                && value != PlaybackState.STATE_STOPPED && value != PlaybackState.STATE_ERROR;
    }

    private static boolean hasMetadata(MediaMetadata metadata) {
        return metadata != null && (!empty(metadata.getString(MediaMetadata.METADATA_KEY_TITLE))
                || !empty(metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)));
    }

    private static boolean supportsControls(PlaybackState state) {
        if (state == null) return false;
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        return (state.getActions() & actions) != 0L;
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
