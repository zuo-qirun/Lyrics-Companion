package com.zuoqirun.lyricscompanion;

import android.content.BroadcastReceiver;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;

/** Consumes public and common vendor AVRCP-controller broadcasts exposed by car ROMs. */
public final class BluetoothAvrcpReceiver extends BroadcastReceiver {
    static final String[] ACTIONS = {
            "android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT",
            "android.bluetooth.avrcp-controller.profile.action.PLAYBACK_STATE_CHANGED",
            "com.android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT",
            "com.android.bluetooth.avrcp-controller.profile.action.PLAYBACK_STATE_CHANGED",
            "com.android.bluetooth.music.metachanged",
            "com.android.bluetooth.music.playstatechanged"
    };
    private static String title = "";
    private static String artist = "";
    private static String mediaId = "";
    private static long durationMs = -1L;
    private static long positionMs;
    private static int state = MusicPlaybackData.STATE_NONE;

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if ("android.bluetooth.avrcp-controller.profile.action.CONNECTION_STATE_CHANGED"
                .equals(intent.getAction()) && intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                BluetoothProfile.STATE_CONNECTED) == BluetoothProfile.STATE_DISCONNECTED) {
            if ("bluetooth".equals(MusicStateStore.activeSourceId())) MusicStateStore.clear();
            return;
        }
        if (!AppPreferences.avrcpEnabled(context)) return;
        Bundle extras = intent.getExtras();
        Object metadata = findObject(extras, "android.media.MediaMetadata");
        Object playback = findObject(extras, "android.media.session.PlaybackState");
        String nextTitle = metadata == null ? firstString(extras,
                "title", "track", "song", "name", "android.media.metadata.TITLE")
                : firstMetadataText(metadata, "android.media.metadata.TITLE",
                "android.media.metadata.DISPLAY_TITLE");
        String nextArtist = metadata == null ? firstString(extras,
                "artist", "singer", "android.media.metadata.ARTIST")
                : firstMetadataText(metadata, "android.media.metadata.ARTIST",
                "android.media.metadata.ALBUM_ARTIST");
        if (!TextUtils.isEmpty(nextTitle)) title = nextTitle.trim();
        if (!TextUtils.isEmpty(nextArtist)) artist = nextArtist.trim();
        if (metadata != null) {
            String id = firstMetadataText(metadata, "android.media.metadata.MEDIA_ID");
            if (!id.isEmpty()) mediaId = id;
            long duration = metadataLong(metadata, "android.media.metadata.DURATION");
            if (duration > 0L) durationMs = duration;
        } else {
            durationMs = positiveLong(extras, durationMs, "duration", "durationMs", "length");
        }
        if (playback != null) {
            state = normalizeState(reflectInt(playback, "getState", 0));
            positionMs = Math.max(0L, reflectLong(playback, "getPosition", 0L));
        } else {
            positionMs = positiveLong(extras, positionMs, "position", "positionMs", "elapsed");
            state = stateFromExtras(extras, state);
        }
        if (title.isEmpty()) return;
        DiagnosticLog.record(context, "AVRCP", "metadata received action=" + intent.getAction()
                + " titlePresent=true artistPresent=" + !artist.isEmpty() + " state=" + state);
        MusicStateStore.update(context, "bluetooth", "蓝牙音频", "com.android.bluetooth",
                new MusicPlaybackData(mediaId, title, artist, null, "", "", durationMs,
                        true, state, positionMs, SystemClock.elapsedRealtime(),
                        state == MusicPlaybackData.STATE_PLAYING ? 1f : 0f));
    }

    static boolean ownsCurrentState() {
        return "bluetooth".equals(MusicStateStore.activeSourceId());
    }

    private static Object findObject(Bundle extras, String className) {
        if (extras == null) return null;
        for (String key : extras.keySet()) {
            try {
                Object value = extras.get(key);
                if (value != null && className.equals(value.getClass().getName())) return value;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static String firstMetadataText(Object metadata, String... keys) {
        if (metadata == null) return "";
        for (String key : keys) {
            try {
                Object value = metadata.getClass().getMethod("getText", String.class)
                        .invoke(metadata, key);
                if (value instanceof CharSequence && !value.toString().trim().isEmpty()) {
                    return value.toString().trim();
                }
            } catch (Throwable ignored) { }
        }
        return "";
    }

    private static long metadataLong(Object metadata, String key) {
        try {
            Object value = metadata.getClass().getMethod("getLong", String.class)
                    .invoke(metadata, key);
            return value instanceof Number ? ((Number) value).longValue() : -1L;
        } catch (Throwable ignored) { return -1L; }
    }

    private static long reflectLong(Object target, String method, long fallback) {
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            return value instanceof Number ? ((Number) value).longValue() : fallback;
        } catch (Throwable ignored) { return fallback; }
    }

    private static int reflectInt(Object target, String method, int fallback) {
        return (int) reflectLong(target, method, fallback);
    }

    private static String firstString(Bundle extras, String... keys) {
        if (extras == null) return "";
        for (String key : keys) {
            Object value = extras.get(key);
            if (value instanceof CharSequence && !value.toString().trim().isEmpty()) {
                return value.toString().trim();
            }
        }
        return "";
    }

    private static long positiveLong(Bundle extras, long fallback, String... keys) {
        if (extras == null) return fallback;
        for (String key : keys) {
            Object value = extras.get(key);
            if (value instanceof Number && ((Number) value).longValue() >= 0L) {
                return ((Number) value).longValue();
            }
        }
        return fallback;
    }

    private static int stateFromExtras(Bundle extras, int fallback) {
        if (extras == null) return fallback;
        for (String key : new String[]{"playing", "isPlaying"}) {
            if (extras.containsKey(key)) return extras.getBoolean(key)
                    ? MusicPlaybackData.STATE_PLAYING : MusicPlaybackData.STATE_PAUSED;
        }
        for (String key : new String[]{"state", "playstate", "playbackState"}) {
            Object value = extras.get(key);
            if (value instanceof Number) return normalizeState(((Number) value).intValue());
            if (value instanceof CharSequence) {
                String text = value.toString().toLowerCase();
                if (text.contains("play")) return MusicPlaybackData.STATE_PLAYING;
                if (text.contains("pause")) return MusicPlaybackData.STATE_PAUSED;
                if (text.contains("stop")) return MusicPlaybackData.STATE_STOPPED;
            }
        }
        return fallback == MusicPlaybackData.STATE_NONE ? MusicPlaybackData.STATE_PLAYING : fallback;
    }

    private static int normalizeState(int value) {
        switch (value) {
            case 3: return MusicPlaybackData.STATE_PLAYING;
            case 2: return MusicPlaybackData.STATE_PAUSED;
            case 1: return MusicPlaybackData.STATE_STOPPED;
            case 6: return MusicPlaybackData.STATE_BUFFERING;
            default: return value >= MusicPlaybackData.STATE_NONE
                    && value <= MusicPlaybackData.STATE_SKIPPING_TO_QUEUE_ITEM
                    ? value : MusicPlaybackData.STATE_NONE;
        }
    }
}
