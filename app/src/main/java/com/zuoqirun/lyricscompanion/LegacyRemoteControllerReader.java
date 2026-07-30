package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadataEditor;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

/** Android 4.4 media reader backed by RemoteControlClient metadata. */
final class LegacyRemoteControllerReader implements MusicSessionReader {
    private static final String TAG = "LyricsLegacyMedia";
    private final Context context;
    private final Callback callback;
    private final RemoteController.OnClientUpdateListener updateListener;
    private AudioManager audioManager;
    private RemoteController remoteController;
    private boolean registered;
    private String sourcePackage = "";
    private String sourceLabel = "";
    private String title = "";
    private String artist = "";
    private Bitmap albumArt;
    private long durationMs = -1L;
    private boolean statePresent;
    private int playbackState = MusicPlaybackData.STATE_NONE;
    private long positionMs;
    private long positionUpdatedAtElapsedMs;
    private float speed;

    LegacyRemoteControllerReader(Context context, Callback callback,
                                 RemoteController.OnClientUpdateListener updateListener) {
        this.context = context;
        this.callback = callback;
        this.updateListener = updateListener;
    }

    boolean isRegistered() {
        return registered;
    }

    void setSourceApplication(String packageName, String applicationLabel) {
        sourcePackage = value(packageName);
        sourceLabel = value(applicationLabel);
        if (registered && !title.isEmpty()) publish();
    }

    @Override public void start() {
        try {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) throw new IllegalStateException("AudioManager 不可用");
            // AudioManager derives the authorized NotificationListenerService component from
            // updateListener.getClass(). The listener must therefore be the service instance,
            // not this helper class, on Android 4.4.
            remoteController = new RemoteController(context, updateListener);
            registered = audioManager.registerRemoteController(remoteController);
            if (!registered) throw new IllegalStateException("RemoteController 注册失败");
            try { remoteController.setArtworkConfiguration(512, 512); }
            catch (Throwable ignored) { }
            callback.onReadSuccess(0);
        } catch (Throwable error) {
            registered = false;
            callback.onReadError("注册 Android 4.4 RemoteController 失败", error);
        }
    }

    @Override public void refresh() {
        if (!registered || remoteController == null) {
            return;
        }
        try {
            long estimated = remoteController.getEstimatedMediaPosition();
            if (estimated >= 0L) {
                positionMs = estimated;
                positionUpdatedAtElapsedMs = SystemClock.elapsedRealtime();
            }
            callback.onReadSuccess(title.isEmpty() ? 0 : 1);
            if (title.isEmpty()) callback.onNoSession();
            else publish();
        } catch (Throwable error) {
            callback.onReadError("读取 Android 4.4 RemoteController 失败", error);
        }
    }

    @Override public boolean dispatchControl(MediaControlAction action) {
        if (audioManager == null || action == null) return false;
        int keyCode;
        switch (action) {
            case PREVIOUS:
                keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                break;
            case NEXT:
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT;
                break;
            case TOGGLE_PLAY_PAUSE:
            default:
                keyCode = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
                break;
        }
        try {
            long now = SystemClock.uptimeMillis();
            audioManager.dispatchMediaKeyEvent(new KeyEvent(now, now,
                    KeyEvent.ACTION_DOWN, keyCode, 0));
            audioManager.dispatchMediaKeyEvent(new KeyEvent(now, now,
                    KeyEvent.ACTION_UP, keyCode, 0));
            return true;
        } catch (Throwable error) {
            callback.onReadError("发送 Android 4.4 播放器控制命令失败", error);
            return false;
        }
    }

    @Override public void stop() {
        if (audioManager != null && remoteController != null && registered) {
            try { audioManager.unregisterRemoteController(remoteController); }
            catch (Throwable ignored) { }
        }
        registered = false;
        remoteController = null;
        audioManager = null;
    }

    void onClientChange(boolean clearing) {
        Log.d(TAG, "RemoteController client changed; clearing=" + clearing);
        if (!clearing) return;
        title = "";
        artist = "";
        albumArt = null;
        durationMs = -1L;
        statePresent = false;
        playbackState = MusicPlaybackData.STATE_NONE;
        positionMs = 0L;
        speed = 0f;
        callback.onReadSuccess(0);
        callback.onNoSession();
    }

    void onClientMetadataUpdate(RemoteController.MetadataEditor editor) {
        if (editor == null) return;
        title = value(editor.getString(MediaMetadataRetriever.METADATA_KEY_TITLE, ""));
        artist = value(editor.getString(MediaMetadataRetriever.METADATA_KEY_ARTIST, ""));
        if (artist.isEmpty()) {
            artist = value(editor.getString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, ""));
        }
        durationMs = editor.getLong(MediaMetadataRetriever.METADATA_KEY_DURATION, -1L);
        Log.d(TAG, "RemoteController metadata: title=" + title + ", artist=" + artist
                + ", durationMs=" + durationMs);
        try {
            albumArt = editor.getBitmap(MediaMetadataEditor.BITMAP_KEY_ARTWORK, null);
        } catch (Throwable ignored) { }
        callback.onReadSuccess(title.isEmpty() ? 0 : 1);
        if (title.isEmpty()) callback.onNoSession();
        else publish();
    }

    void onClientPlaybackStateUpdate(int state) {
        Log.d(TAG, "RemoteController playback state=" + state);
        statePresent = true;
        playbackState = normalizeState(state);
        speed = isAdvancing(playbackState) ? 1f : 0f;
        positionUpdatedAtElapsedMs = SystemClock.elapsedRealtime();
        if (!title.isEmpty()) publish();
    }

    void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                                     long currentPositionMs, float playbackSpeed) {
        Log.d(TAG, "RemoteController playback state=" + state + ", positionMs="
                + currentPositionMs + ", speed=" + playbackSpeed);
        statePresent = true;
        playbackState = normalizeState(state);
        positionMs = Math.max(0L, currentPositionMs);
        positionUpdatedAtElapsedMs = stateChangeTimeMs > 0L
                ? stateChangeTimeMs : SystemClock.elapsedRealtime();
        speed = isAdvancing(playbackState)
                ? (playbackSpeed > 0f ? playbackSpeed : 1f) : 0f;
        if (!title.isEmpty()) publish();
    }

    void onClientTransportControlUpdate(int transportControlFlags) {
        // Playback controls are not currently exposed by Lyrics Companion.
    }

    private void publish() {
        callback.onSession(sourcePackage, sourceLabel,
                new MusicPlaybackData("", title, artist, albumArt, "", durationMs,
                        statePresent, playbackState, positionMs,
                        positionUpdatedAtElapsedMs, speed));
    }

    static int normalizeState(int remoteState) {
        switch (remoteState) {
            case RemoteControlClient.PLAYSTATE_STOPPED:
                return MusicPlaybackData.STATE_STOPPED;
            case RemoteControlClient.PLAYSTATE_PAUSED:
                return MusicPlaybackData.STATE_PAUSED;
            case RemoteControlClient.PLAYSTATE_PLAYING:
                return MusicPlaybackData.STATE_PLAYING;
            case RemoteControlClient.PLAYSTATE_FAST_FORWARDING:
                return MusicPlaybackData.STATE_FAST_FORWARDING;
            case RemoteControlClient.PLAYSTATE_REWINDING:
                return MusicPlaybackData.STATE_REWINDING;
            case RemoteControlClient.PLAYSTATE_BUFFERING:
                return MusicPlaybackData.STATE_BUFFERING;
            case RemoteControlClient.PLAYSTATE_ERROR:
                return MusicPlaybackData.STATE_ERROR;
            case RemoteControlClient.PLAYSTATE_SKIPPING_BACKWARDS:
                return MusicPlaybackData.STATE_SKIPPING_TO_PREVIOUS;
            case RemoteControlClient.PLAYSTATE_SKIPPING_FORWARDS:
                return MusicPlaybackData.STATE_SKIPPING_TO_NEXT;
            default:
                return MusicPlaybackData.STATE_NONE;
        }
    }

    private static boolean isAdvancing(int state) {
        return state == MusicPlaybackData.STATE_PLAYING
                || state == MusicPlaybackData.STATE_FAST_FORWARDING
                || state == MusicPlaybackData.STATE_REWINDING;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
