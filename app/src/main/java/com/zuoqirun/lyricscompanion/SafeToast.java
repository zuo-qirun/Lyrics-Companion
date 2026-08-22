package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

/**
 * Toast front-end that survives OEM resource corruption. On some head units the framework
 * fails to inflate the system toast layout (InflateException raised from StringBlock while
 * reading TextView attributes) and every unguarded Toast call crashes the whole app. A
 * missing hint must never take the player down, so failures degrade to a log line.
 */
final class SafeToast {
    private static final String TAG = "LyricsToast";

    private SafeToast() {}

    static void show(Context context, CharSequence message) {
        show(context, message, Toast.LENGTH_SHORT);
    }

    static void show(Context context, CharSequence message, int duration) {
        if (context == null || message == null || message.length() == 0) return;
        try {
            Toast.makeText(context, message, duration).show();
        } catch (Throwable error) {
            Log.w(TAG, "Unable to show toast: " + message, error);
        }
    }
}
