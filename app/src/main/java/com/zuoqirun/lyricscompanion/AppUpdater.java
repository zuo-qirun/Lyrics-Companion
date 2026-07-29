package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/** Update client compatible with the release manifest used by AMap Companion. */
final class AppUpdater {
    interface Listener { void onStatus(String message); }

    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final String UPDATE_APK_NAME = "lyrics_companion_update.apk";
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final String[] INSTALLERX_PACKAGES = {
            "com.rosan.installer.x.revived",
            "com.rosan.installer.x",
            "com.rosan.installer"
    };

    private AppUpdater() {}

    static UpdateInfo check(Context context, String manifestUrl) throws Exception {
        JSONObject manifest = new JSONObject(readText(manifestUrl));
        String packageName = manifest.optString("packageName", context.getPackageName());
        if (!context.getPackageName().equals(packageName)) {
            throw new IllegalStateException("更新包名不匹配: " + packageName);
        }
        String apkUrl = resolveUrl(manifestUrl, manifest.optString("apkUrl", ""));
        String changelog = changelogText(manifest);
        String changelogUrl = resolveUrl(manifestUrl,
                manifest.optString("changelogUrl", ""));
        if (!TextUtils.isEmpty(changelogUrl)) {
            try {
                String remote = readText(changelogUrl).trim();
                if (!remote.isEmpty()) changelog = remote;
            } catch (Throwable ignored) { }
        }
        return new UpdateInfo(localVersionCode(context), localVersionName(context),
                manifest.optInt("versionCode", -1), manifest.optString("versionName", ""),
                apkUrl, manifest.optString("sha256", ""), manifest.optLong("size", -1L),
                manifest.optBoolean("force", false), changelog);
    }

    static void downloadAndInstall(Context context, UpdateInfo info, Listener listener) {
        try {
            if (!info.hasUpdate()) {
                notify(listener, "已是最新版本");
                return;
            }
            if (TextUtils.isEmpty(info.apkUrl)) {
                throw new IllegalStateException("更新接口未提供 APK 地址");
            }
            File apk = new File(context.getCacheDir(), UPDATE_APK_NAME);
            download(info.apkUrl, apk, listener);
            verify(context, apk, info);
            notify(listener, "下载和 SHA-256 校验完成，正在选择单一安装器");
            Context appContext = context.getApplicationContext();
            if (isPackageInstalled(appContext, SHIZUKU_PACKAGE)
                    && tryPreferredInstallerX(appContext, apk, listener)) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 21) {
                ModernPackageInstaller.install(appContext, apk, listener);
            } else {
                notify(listener, "Android 4.4 使用系统 APK 安装器");
                tryFallbackInstallers(appContext, apk, listener, "正在打开系统安装器");
            }
        } catch (Throwable error) {
            notify(listener, "更新失败: " + safeMessage(error));
        }
    }

    private static void download(String address, File destination, Listener listener)
            throws Exception {
        HttpURLConnection connection = connect(address);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("APK HTTP " + code);
            long total = connection.getContentLength();
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[64 * 1024];
                long done = 0L;
                int lastPercent = -10;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    output.write(buffer, 0, read);
                    done += read;
                    if (total > 0) {
                        int percent = (int) (done * 100L / total);
                        if (percent >= lastPercent + 10) {
                            lastPercent = percent;
                            notify(listener, "正在下载更新: " + percent + "%");
                        }
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void verify(Context context, File apk, UpdateInfo info) throws Exception {
        if (info.size > 0L && apk.length() != info.size) {
            throw new IllegalStateException("APK 大小校验失败");
        }
        if (!TextUtils.isEmpty(info.sha256)
                && !info.sha256.equalsIgnoreCase(sha256(apk))) {
            throw new IllegalStateException("APK SHA-256 校验失败");
        }
        PackageInfo archive = context.getPackageManager().getPackageArchiveInfo(
                apk.getAbsolutePath(), 0);
        if (archive == null || !context.getPackageName().equals(archive.packageName)) {
            throw new IllegalStateException("APK 包名校验失败");
        }
    }

    static void tryFallbackInstallers(Context context, File apk,
                                      Listener listener, String prefix) {
        notify(listener, prefix);
        if (isPackageInstalled(context, SHIZUKU_PACKAGE)) {
            notify(listener, "检测到 Shizuku，将优先交给支持 Shizuku 的 InstallerX");
        }
        for (String packageName : INSTALLERX_PACKAGES) {
            if (startExternalInstaller(context, apk, packageName, "InstallerX", listener)) {
                return;
            }
        }
        if (startExternalInstaller(context, apk, null, "系统默认安装器", listener)) {
            return;
        }
        notify(listener, "未找到可用安装器；可安装 InstallerX 并启用 Shizuku 后重试");
    }

    private static boolean tryPreferredInstallerX(Context context, File apk,
                                                  Listener listener) {
        for (String packageName : INSTALLERX_PACKAGES) {
            if (!isPackageInstalled(context, packageName)) continue;
            notify(listener, "检测到 Shizuku 与 InstallerX，直接交给 InstallerX，"
                    + "避免和系统安装器冲突");
            if (startExternalInstaller(context, apk, packageName,
                    "InstallerX", listener)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startExternalInstaller(Context context, File apk,
                                                  String packageName, String label,
                                                  Listener listener) {
        try {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".updatefiles", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.getPackageName());
            if (!TextUtils.isEmpty(packageName)) intent.setPackage(packageName);
            if (!canHandleIntent(context, intent)) return false;
            grantReadPermission(context, uri, intent);
            context.startActivity(intent);
            notify(listener, "已通过 " + label + " 打开安装器");
            return true;
        } catch (Throwable error) {
            notify(listener, label + " 启动失败: " + safeMessage(error));
            return false;
        }
    }

    private static boolean canHandleIntent(Context context, Intent intent) {
        List<ResolveInfo> activities = context.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return activities != null && !activities.isEmpty();
    }

    private static void grantReadPermission(Context context, Uri uri, Intent intent) {
        List<ResolveInfo> activities = context.getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (activities == null) return;
        for (ResolveInfo info : activities) {
            ActivityInfo activity = info.activityInfo;
            if (activity == null || TextUtils.isEmpty(activity.packageName)) continue;
            context.grantUriPermission(activity.packageName, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private static String readText(String address) throws Exception {
        HttpURLConnection connection = connect(address);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            StringBuilder value = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) value.append(line).append('\n');
            }
            return value.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection connect(String address) throws Exception {
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpURLConnection connection = HttpCompat.open(address);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json,*/*");
            connection.setRequestProperty("User-Agent", "LyricsCompanionUpdater/1.0");
            int code = connection.getResponseCode();
            if (code < 300 || code >= 400) return connection;
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (TextUtils.isEmpty(location)) throw new IllegalStateException("重定向缺少地址");
            address = new URL(new URL(address), location).toString();
        }
        throw new IllegalStateException("更新地址重定向次数过多");
    }

    private static String resolveUrl(String base, String value) {
        if (TextUtils.isEmpty(value)) return "";
        try { return new URL(new URL(base), value).toString(); }
        catch (Exception ignored) { return value; }
    }

    private static int localVersionCode(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= 28 ? (int) info.getLongVersionCode() : info.versionCode;
    }

    private static String localVersionName(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return info.versionName == null ? "" : info.versionName;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        StringBuilder value = new StringBuilder();
        for (byte part : digest.digest()) {
            value.append(String.format(Locale.US, "%02x", part & 0xff));
        }
        return value.toString();
    }

    private static String changelogText(JSONObject manifest) {
        String direct = manifest.optString("changelogText", "");
        if (!TextUtils.isEmpty(direct)) return direct;
        JSONArray entries = manifest.optJSONArray("changelog");
        if (entries == null) return manifest.optString("changelog", "");
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < entries.length(); index++) {
            String entry = entries.optString(index, "").trim();
            if (!entry.isEmpty()) value.append("- ").append(entry).append('\n');
        }
        return value.toString().trim();
    }

    static String safeMessage(Throwable error) {
        return TextUtils.isEmpty(error.getMessage())
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    static void notify(Listener listener, String message) {
        if (listener != null) listener.onStatus(message);
    }

    static final class UpdateInfo {
        final int localVersionCode;
        final String localVersionName;
        final int remoteVersionCode;
        final String remoteVersionName;
        final String apkUrl;
        final String sha256;
        final long size;
        final boolean force;
        final String changelog;

        UpdateInfo(int localVersionCode, String localVersionName, int remoteVersionCode,
                   String remoteVersionName, String apkUrl, String sha256, long size,
                   boolean force, String changelog) {
            this.localVersionCode = localVersionCode;
            this.localVersionName = localVersionName;
            this.remoteVersionCode = remoteVersionCode;
            this.remoteVersionName = remoteVersionName;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
            this.size = size;
            this.force = force;
            this.changelog = changelog;
        }

        boolean hasUpdate() { return remoteVersionCode > localVersionCode; }

        String detailText() {
            StringBuilder value = new StringBuilder();
            value.append("当前版本：").append(localVersionName).append(" (")
                    .append(localVersionCode).append(")\n")
                    .append("最新版本：").append(remoteVersionName).append(" (")
                    .append(remoteVersionCode).append(")");
            if (size > 0L) value.append("\nAPK 大小：").append(size / 1024L).append(" KB");
            if (force) value.append("\n强制更新：是");
            if (!TextUtils.isEmpty(changelog)) value.append("\n\n更新日志：\n").append(changelog);
            return value.toString();
        }
    }
}
