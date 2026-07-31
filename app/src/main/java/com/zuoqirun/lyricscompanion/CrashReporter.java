package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Persists the last fatal exception locally; network work is deliberately deferred until restart. */
final class CrashReporter {
    private static final String TAG = "CrashReporter";
    private static final String DIRECTORY = "diagnostics";
    private static final String PENDING_CRASH = "pending-crash.json";
    private static final int MAX_STACK_CHARS = 12_000;
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
        return "android=" + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")"
                + "\nmanufacturer=" + safe(Build.MANUFACTURER)
                + "\nmodel=" + safe(Build.MODEL)
                + "\nmainOverlay=" + AppPreferences.mainEnabled(context)
                + "\nsecondaryOverlay=" + AppPreferences.secondaryEnabled(context);
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
    private static String trim(String value, int maximum) {
        String text = value == null ? "" : value;
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
