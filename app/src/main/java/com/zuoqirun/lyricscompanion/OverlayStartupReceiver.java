package com.zuoqirun.lyricscompanion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores only explicitly remembered overlays after Android has completed a boot. */
public final class OverlayStartupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            LyricsDisplayService.startRememberedFromSystem(context, "boot");
        }
    }
}
