package com.zuoqirun.lyricscompanion;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.os.Build;
import android.os.IBinder;

/** Debug-only API 19 fixture that behaves like a legacy music player. */
public final class LegacyRemoteControlFixtureService extends Service {
    private AudioManager audioManager;
    private RemoteControlClient remoteControlClient;
    private ComponentName mediaButtonReceiver;

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager == null) return;
        mediaButtonReceiver = new ComponentName(this, LegacyRemoteControlFixtureReceiver.class);
        audioManager.registerMediaButtonEventReceiver(mediaButtonReceiver);
        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(mediaButtonReceiver);
        // AudioManager's legacy media-button registration uses requestCode 0 internally;
        // RemoteControlClient must use an equivalent PendingIntent to attach to that stack entry.
        int pendingIntentFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent,
                pendingIntentFlags);
        remoteControlClient = new RemoteControlClient(pendingIntent);
        remoteControlClient.setTransportControlFlags(
                RemoteControlClient.FLAG_KEY_MEDIA_PLAY
                        | RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
                        | RemoteControlClient.FLAG_KEY_MEDIA_NEXT
                        | RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS);
        audioManager.registerRemoteControlClient(remoteControlClient);
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (remoteControlClient == null) return START_NOT_STICKY;
        String title = value(intent, "title", "TICKING AWAY");
        String artist = value(intent, "artist", "VALORANT, Grabbitz");
        String album = value(intent, "album", "API19 Compatibility Test");
        long durationMs = intent == null ? 190_041L
                : intent.getLongExtra("duration", 190_041L);
        long positionMs = intent == null ? 12_000L
                : intent.getLongExtra("position", 12_000L);

        RemoteControlClient.MetadataEditor editor = remoteControlClient.editMetadata(true);
        editor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title);
        editor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artist);
        editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, album);
        editor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, durationMs);
        editor.apply();
        remoteControlClient.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING,
                positionMs, 1f);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (audioManager != null && remoteControlClient != null) {
            audioManager.unregisterRemoteControlClient(remoteControlClient);
            audioManager.abandonAudioFocus(null);
        }
        if (audioManager != null && mediaButtonReceiver != null) {
            audioManager.unregisterMediaButtonEventReceiver(mediaButtonReceiver);
        }
        remoteControlClient = null;
        audioManager = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private static String value(Intent intent, String key, String fallback) {
        if (intent == null) return fallback;
        String value = intent.getStringExtra(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
