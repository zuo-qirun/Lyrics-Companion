package com.zuoqirun.lyricscompanion;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * “安装未知应用”权限的安全查询。
 *
 * <p>{@code PackageManager.canRequestPackageInstalls()} 在权限没有真正授予时会直接抛出
 * SecurityException，而不是返回 false。实测某些车机 ROM（东风日产 HSAE CCS2.0，Android 9）
 * 即使在清单里声明了 {@code android.permission.REQUEST_INSTALL_PACKAGES}，平台仍会拒绝该
 * 权限，于是任何未捕获的调用都会让主线程崩溃、应用闪退。所有调用点统一走这里，把失败当成
 * “未授权”处理，更新流程会退回到系统安装器或应用详情页。</p>
 */
final class InstallPermission {
    private static final String TAG = "InstallPermission";

    private InstallPermission() { }

    /**
     * @return true 表示可以请求安装 APK；Android 8.0 以下没有该权限的概念，恒为 true。
     */
    static boolean canInstallUpdates(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 26) return true;
        try {
            // 权限没有授予时不要再去问系统，那一句调用本身就是崩溃来源。
            if (context.checkSelfPermission(Manifest.permission.REQUEST_INSTALL_PACKAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            return context.getPackageManager().canRequestPackageInstalls();
        } catch (RuntimeException error) {
            DiagnosticLog.record(context, TAG,
                    "canRequestPackageInstalls 调用失败，按未授权处理: " + error);
            return false;
        }
    }
}
