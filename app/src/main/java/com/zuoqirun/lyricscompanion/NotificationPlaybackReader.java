package com.zuoqirun.lyricscompanion;

import android.annotation.TargetApi;
import android.app.Notification;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.SystemClock;

/** Optional standard media token attached to the same notification, guarded at the API 21 call site. */
@TargetApi(21)
final class NotificationPlaybackReader {
    private NotificationPlaybackReader() { }
    static MusicPlaybackData read(Context context, String packageName, Notification notification,
                                  NotificationTrackParser.Track track) {
        Bundle extras = notification.extras;
        if (extras == null) return null;
        try {
            Object token = extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
            if (!(token instanceof MediaSession.Token)) return null;
            MediaController controller = new MediaController(context, (MediaSession.Token) token);
            if (!packageName.equals(controller.getPackageName())) return null;
            MediaMetadata metadata = controller.getMetadata();
            if (metadata == null || metadata.getString(MediaMetadata.METADATA_KEY_TITLE) == null
                    || !track.title.equalsIgnoreCase(metadata.getString(MediaMetadata.METADATA_KEY_TITLE).trim())) return null;
            long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            PlaybackState state = controller.getPlaybackState();
            long position = -1L, at = SystemClock.elapsedRealtime();
            int stateValue = MusicPlaybackData.STATE_NONE;
            float speed = 0f;
            if (state != null && (state.getState() == PlaybackState.STATE_PLAYING
                    || state.getState() == PlaybackState.STATE_PAUSED) && state.getPosition() >= 0L) {
                position = state.getPosition();
                stateValue = state.getState();
                at = state.getLastPositionUpdateTime();
                speed = state.getPlaybackSpeed();
            } else {
                position = NotificationProgressRules.position(extras.getInt(Notification.EXTRA_PROGRESS, -1),
                        extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1),
                        extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false), duration);
            }
            if (position < 0L) return null;
            return new MusicPlaybackData("", track.title, track.artist, null, "", track.mediaUri,
                    duration, true, stateValue, position, at, speed);
        } catch (RuntimeException ignored) { return null; }
    }
}
