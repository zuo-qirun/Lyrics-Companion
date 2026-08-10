package com.zuoqirun.lyricscompanion;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.Display;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.TimeZone;

/** Persists the last fatal exception locally; network work is deliberately deferred until restart. */
final class CrashReporter {
    private static final String TAG = "CrashReporter";
    private static final String DIRECTORY = "diagnostics";
    private static final String PENDING_CRASH = "pending-crash.json";
    private static final int MAX_STACK_CHARS = 64_000;
    private static boolean installed;

    private CrashReporter() {}

    static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        Context appContext = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                writePending(appContext, crashPayload(appContext, thread, error));
            } catch (Throwable ignored) {
                // Never mask the original fatal exception.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
    }

    static JSONObject pending(Context context) {
        File file = pendingFile(context);
        if (!file.isFile()) return null;
        try {
            try (FileInputStream input = new FileInputStream(file);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            }
        } catch (Throwable error) {
            file.delete();
            return null;
        }
    }

    static void clearPending(Context context) {
        File file = pendingFile(context);
        if (file.isFile()) file.delete();
    }

    static JSONObject snapshot(Context context) {
        JSONObject report = new JSONObject();
        try {
            report.put("summary", "Manual diagnostic snapshot");
            report.put("details", deviceDetails(context) + "\n\nRecent app events:\n"
                    + DiagnosticLog.recent(context));
        } catch (Throwable ignored) { }
        return report;
    }

    private static JSONObject crashPayload(Context context, Thread thread, Throwable error) {
        JSONObject report = new JSONObject();
        try {
            String summary = error.getClass().getSimpleName();
            String message = error.getMessage();
            if (message != null && !message.trim().isEmpty()) summary += ": " + message.trim();
            report.put("summary", trim(summary, 500));
            report.put("details", deviceDetails(context) + "\nthread=" + thread.getName()
                    + "\n\n" + trim(Log.getStackTraceString(error), MAX_STACK_CHARS)
                    + "\n\nRecent app events:\n" + DiagnosticLog.recent(context));
        } catch (Throwable ignored) { }
        return report;
    }

    private static String deviceDetails(Context context) {
        Runtime runtime = Runtime.getRuntime();
        File files = context.getFilesDir();
        StatFs storage = new StatFs(files.getAbsolutePath());
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return "app=" + appVersion(context)
                + "\npackage=" + context.getPackageName()
                + "\nandroid=" + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")"
                + "\nsecurityPatch=" + (Build.VERSION.SDK_INT >= 23
                ? safe(Build.VERSION.SECURITY_PATCH) : "unavailable")
                + "\nmanufacturer=" + safe(Build.MANUFACTURER)
                + "\nmodel=" + safe(Build.MODEL)
                + "\ndevice=" + safe(Build.DEVICE)
                + "\nproduct=" + safe(Build.PRODUCT)
                + "\nabis=" + (Build.VERSION.SDK_INT >= 21
                ? java.util.Arrays.toString(Build.SUPPORTED_ABIS) : safe(Build.CPU_ABI))
                + "\nlocale=" + Locale.getDefault()
                + "\ntimeZone=" + TimeZone.getDefault().getID()
                + "\nprocessUptimeMs=" + SystemClock.elapsedRealtime()
                + "\nmemoryUsedBytes=" + (runtime.totalMemory() - runtime.freeMemory())
                + "\nmemoryFreeBytes=" + runtime.freeMemory()
                + "\nmemoryTotalBytes=" + runtime.totalMemory()
                + "\nmemoryMaxBytes=" + runtime.maxMemory()
                + "\nfilesAvailableBytes=" + storage.getAvailableBytes()
                + "\ndensity=" + metrics.density + " (" + metrics.densityDpi + " dpi)"
                + "\nfontScale=" + context.getResources().getConfiguration().fontScale
                + "\n\nPermissions:\n" + permissionDetails(context)
                + "\n\nDisplays:\n" + displayDetails(context)
                + "\n\nDisplay preferences:\n" + preferenceDetails(context)
                + "\n\nPlayback pipeline:\n" + playbackDetails(context);
    }

    private static String appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            return safe(info.versionName) + " (" + code + ")";
        } catch (Throwable error) {
            return "unknown: " + error.getClass().getSimpleName();
        }
    }

    private static String permissionDetails(Context context) {
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context);
        boolean recordAudio = Build.VERSION.SDK_INT < 23 || context.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean notifications = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean installPackages = Build.VERSION.SDK_INT < 26
                || context.getPackageManager().canRequestPackageInstalls();
        return "notificationAccess=" + MusicNotificationListener.hasNotificationAccess(context)
                + "\noverlay=" + overlay
                + "\nrecordAudio=" + recordAudio
                + "\npostNotifications=" + notifications
                + "\ninstallPackages=" + installPackages;
    }

    private static String displayDetails(Context context) {
        DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (manager == null) return "displayManager=unavailable";
        StringBuilder result = new StringBuilder();
        Display[] displays = manager.getDisplays();
        result.append("count=").append(displays.length);
        for (Display display : displays) {
            Point size = new Point();
            display.getRealSize(size);
            result.append("\nid=").append(display.getDisplayId())
                    .append(" name=").append(display.getName())
                    .append(" sizePx=").append(size.x).append('x').append(size.y)
                    .append(" rotation=").append(display.getRotation())
                    .append(" refreshHz=").append(display.getRefreshRate());
            if (Build.VERSION.SDK_INT >= 20) result.append(" state=").append(display.getState());
        }
        return result.toString();
    }

    private static String preferenceDetails(Context context) {
        return "mainEnabled=" + AppPreferences.mainEnabled(context)
                + "\nmainStyle=" + AppPreferences.overlayStyle(context, false)
                + "\nmainPanelDp=" + AppPreferences.panelWidthDp(context, false)
                + "x" + AppPreferences.panelHeightDp(context, false)
                + "\nmainTextScale=" + AppPreferences.textScale(context, false)
                + "\nmainOpacity=" + AppPreferences.opacity(context, false)
                + "\nmainBlur=" + AppPreferences.styleBlur(context, false)
                + "\nmainDim=" + AppPreferences.styleDim(context, false)
                + "\nmainLyricOffsetMs=" + AppPreferences.lyricOffsetMs(context, false)
                + "\nsecondaryEnabled=" + AppPreferences.secondaryEnabled(context)
                + "\nsecondaryDisplayId=" + AppPreferences.displayId(context)
                + "\nsecondaryStyle=" + AppPreferences.overlayStyle(context, true)
                + "\nsecondaryPanelDp=" + AppPreferences.panelWidthDp(context, true)
                + "x" + AppPreferences.panelHeightDp(context, true)
                + "\nsecondaryTextScale=" + AppPreferences.textScale(context, true)
                + "\nsecondaryOpacity=" + AppPreferences.opacity(context, true)
                + "\nsecondaryBlur=" + AppPreferences.styleBlur(context, true)
                + "\nsecondaryDim=" + AppPreferences.styleDim(context, true)
                + "\nsecondaryLyricOffsetMs=" + AppPreferences.lyricOffsetMs(context, true)
                + "\ncustomFont=" + !AppPreferences.customFontFile(context).isEmpty()
                + "\nnotificationLyrics=" + AppPreferences.notificationLyrics(context)
                + "\ntopLyricStrip=" + AppPreferences.topLyricStrip(context)
                + "\nlockscreenLyrics=" + AppPreferences.lockscreenLyrics(context);
    }

    /** Includes enough state to diagnose player/lyric failures without uploading lyric text. */
    private static String playbackDetails(Context context) {
        MusicSnapshot snapshot = MusicStateStore.snapshot(AppPreferences.lyricOffsetMs(context));
        long lastReadElapsedMs = MusicNotificationListener.getLastSuccessfulSessionReadElapsedMs();
        long readAgeMs = lastReadElapsedMs <= 0L ? -1L
                : Math.max(0L, SystemClock.elapsedRealtime() - lastReadElapsedMs);
        String backend = MusicNotificationListener.getBackendName();
        if (backend == null || backend.trim().isEmpty()) {
            backend = Build.VERSION.SDK_INT >= 21 ? "MediaSession" : "RemoteController";
        }
        String activePlayerPackage = singleLine(
                MusicNotificationListener.getActivePlayerPackageName(), 200);
        String lyricState = !snapshot.active ? "no_active_player"
                : !snapshot.lyricLoaded ? "loading"
                : !snapshot.lyricAvailable ? "no_match" : "ready";
        String timing = !snapshot.lyricAvailable ? "none"
                : snapshot.lyrics.wordTimed ? "word_timed"
                : snapshot.lyrics.lineStartMs >= 0L ? "line_timed" : "untimed_or_live";
        return "notificationAccess=" + MusicNotificationListener.hasNotificationAccess(context)
                + "\nlistenerConnected=" + MusicNotificationListener.isListenerConnected()
                + "\nlistenerHealthy=" + MusicNotificationListener.isHealthy(3_000L)
                + "\nbackend=" + backend
                + "\nactivePlayerPackage=" + activePlayerPackage
                + "\nplayerPackageIncluded=true"
                + "\nsessionCount=" + MusicNotificationListener.getLastSessionCount()
                + "\nlastSessionReadAgeMs=" + readAgeMs
                + "\nlastSessionError=" + singleLine(MusicNotificationListener.getLastSessionError(), 500)
                + "\nplayerActive=" + snapshot.active
                + "\nplayerSource=" + singleLine(snapshot.sourceName, 120)
                + "\ntrackTitle=" + singleLine(snapshot.title, 300)
                + "\ntrackArtist=" + singleLine(snapshot.artist, 300)
                + "\nplaying=" + snapshot.playing
                + "\npositionMs=" + snapshot.positionMs
                + "\ndurationMs=" + snapshot.durationMs
                + "\nlyricState=" + lyricState
                + "\nlyricProvider=" + singleLine(snapshot.lyricSourceName, 120)
                + "\nlyricTiming=" + timing
                + "\ncurrentLineChars=" + codePointCount(snapshot.lyrics.lyric)
                + "\ntranslationChars=" + codePointCount(snapshot.lyrics.translatedLyric)
                + "\nnextLineChars=" + codePointCount(snapshot.lyrics.nextLyric)
                + "\nlineStartMs=" + snapshot.lyrics.lineStartMs
                + "\nlineDurationMs=" + snapshot.lyrics.lineDurationMs
                + "\nlyricOffsetMs=" + AppPreferences.lyricOffsetMs(context)
                + "\nlyricCatalog=" + AppPreferences.lyricCatalog(context)
                + "\nplayerCatalogFallback=" + AppPreferences.playerCatalogFallback(context)
                + "\n\nMusic state internals:\n" + MusicStateStore.diagnosticDetails();
    }

    private static void writePending(Context context, JSONObject report) throws Exception {
        File directory = new File(context.getFilesDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) return;
        File temporary = new File(directory, PENDING_CRASH + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(report.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        File target = pendingFile(context);
        if (target.exists()) target.delete();
        if (!temporary.renameTo(target)) temporary.delete();
    }

    private static File pendingFile(Context context) {
        return new File(new File(context.getFilesDir(), DIRECTORY), PENDING_CRASH);
    }

    private static String safe(String value) { return value == null ? "unknown" : value; }
    private static String singleLine(String value, int maximum) {
        return trim(value == null ? "" : value.replace('\n', ' ').replace('\r', ' '), maximum);
    }
    private static String trim(String value, int maximum) {
        String text = value == null ? "" : value;
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
    private static int codePointCount(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }
}
