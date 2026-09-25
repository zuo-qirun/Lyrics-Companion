package com.zuoqirun.lyricscompanion;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Restores only explicitly remembered overlays after Android has completed a boot. */
public final class OverlayStartupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        // Preferences live in credential-protected storage. A direct-boot broadcast arrives
        // before that storage can be read; BOOT_COMPLETED / USER_UNLOCKED will retry later.
        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            LyricsDisplayService.startRememberedFromSystem(context, "boot");
            // Some dashboards deliver BOOT_COMPLETED before the status area accepts overlay
            // windows. Replaying the same remembered targets is idempotent and lets the
            // service's status-strip retry path attach after SystemUI has settled.
            Context appContext = context.getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> LyricsDisplayService.startRememberedFromSystem(appContext, "boot_retry"),
                    3_000L);
        }
    }
}
