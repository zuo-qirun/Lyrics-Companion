package com.zuoqirun.lyricscompanion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Debug-only media-button target required by RemoteControlClient on Android 4.4. */
public final class LegacyRemoteControlFixtureReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        // The fixture publishes metadata only; transport controls are intentionally ignored.
    }
}
