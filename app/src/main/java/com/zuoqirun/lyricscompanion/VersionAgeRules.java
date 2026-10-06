package com.zuoqirun.lyricscompanion;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Release codes are timestamps, not sequential version counts. Unknown stays unknown. */
final class VersionAgeRules {
    private VersionAgeRules() { }

    static int daysBehind(String local, String latest) {
        long localDate = date(local);
        long latestDate = date(latest);
        return localDate < 0L || latestDate < 0L ? -1
                : (int) Math.max(0L, (latestDate - localDate) / 86_400_000L);
    }

    static boolean shouldWarn(boolean outdated, int days, int releases) {
        return outdated && (days >= 30 || releases >= 3);
    }

    private static long date(String name) {
        if (name == null || !name.matches("[0-9]{8}(?:[-_].*)?")) return -1L;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            format.setLenient(false);
            Date parsed = format.parse(name.substring(0, 8));
            return parsed == null ? -1L : parsed.getTime();
        } catch (Exception ignored) {
            return -1L;
        }
    }
}
