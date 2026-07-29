package com.zuoqirun.lyricscompanion;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** PackageInstaller implementation isolated so Dalvik 4.4 never verifies API 21 classes. */
@TargetApi(21)
final class ModernPackageInstaller {
    private ModernPackageInstaller() { }

    static void install(Context context, File apk, AppUpdater.Listener listener) {
        try {
            installInternal(context, apk, listener);
        } catch (Throwable error) {
            AppUpdater.notify(listener,
                    "PackageInstaller 启动失败: " + AppUpdater.safeMessage(error));
            AppUpdater.tryFallbackInstallers(context, apk, listener, "正在尝试备用安装器");
        }
    }

    private static void installInternal(Context context, File apk,
                                        AppUpdater.Listener listener) throws Exception {
        if (Build.VERSION.SDK_INT >= 26
                && !context.getPackageManager().canRequestPackageInstalls()) {
            AppUpdater.notify(listener, "未授予“安装未知应用”权限，跳过 PackageInstaller");
            AppUpdater.tryFallbackInstallers(context, apk, listener, "正在尝试备用安装器");
            return;
        }
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        int sessionId = installer.createSession(params);
        String action = context.getPackageName() + ".UPDATE_INSTALL_RESULT." + sessionId;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context receiverContext, Intent intent) {
                int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE);
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = Build.VERSION.SDK_INT >= 33
                            ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
                            : intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        receiverContext.startActivity(confirm);
                        AppUpdater.notify(listener, "请在系统安装窗口中确认更新");
                    } else {
                        safeUnregister(receiverContext, this);
                        AppUpdater.tryFallbackInstallers(
                                receiverContext.getApplicationContext(), apk, listener,
                                "系统未提供安装确认窗口，正在尝试备用安装器");
                    }
                    return;
                }
                safeUnregister(receiverContext, this);
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    AppUpdater.notify(listener, "更新安装成功");
                } else {
                    String detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    AppUpdater.notify(listener, "系统安装会话失败"
                            + (TextUtils.isEmpty(detail) ? "" : ": " + detail));
                    AppUpdater.tryFallbackInstallers(
                            receiverContext.getApplicationContext(), apk, listener,
                            "正在尝试备用安装器");
                }
            }
        };
        IntentFilter filter = new IntentFilter(action);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream input = new FileInputStream(apk);
                 OutputStream output = session.openWrite("package", 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) output.write(buffer, 0, read);
                }
                session.fsync(output);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(context, sessionId,
                    new Intent(action).setPackage(context.getPackageName()), flags);
            IntentSender sender = pending.getIntentSender();
            session.commit(sender);
            AppUpdater.notify(listener, "已提交系统安装会话");
        } catch (Throwable error) {
            safeUnregister(context, receiver);
            installer.abandonSession(sessionId);
            AppUpdater.tryFallbackInstallers(context, apk, listener, "正在尝试备用安装器");
        }
    }

    private static void safeUnregister(Context context, BroadcastReceiver receiver) {
        try { context.unregisterReceiver(receiver); }
        catch (Throwable ignored) { }
    }
}
