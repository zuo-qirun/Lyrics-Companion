package com.zuoqirun.lyricscompanion;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;

/** Reads an already available location; never starts GPS or stores coordinates in preferences/logs. */
final class ThemeEnvironment {
    static final String SOLAR = "theme_solar_enabled";
    static final String PACKAGE = "theme_foreground_package";
    static final String MODE = "theme_foreground_mode";
    private static String foreground = "";
    private static long foregroundAt;
    private static long locationCheckedAt = -1L;
    private static Location location;
    private static long resolvedAt = -1L;
    private static Boolean solarDark;
    private ThemeEnvironment() { }

    static void foreground(String packageName) {
        foreground = packageName == null ? "" : packageName;
        foregroundAt = SystemClock.elapsedRealtime();
    }

    static String resolve(Context context) {
        long now = SystemClock.elapsedRealtime();
        String override = SolarDayNight.foregroundOverride(
                AppPreferences.get(context).getString(PACKAGE, ""), foreground,
                now >= foregroundAt && now - foregroundAt < 3000L,
                AppPreferences.get(context).getString(MODE, "dark"));
        if (!override.isEmpty()) return override;
        if (!AppPreferences.get(context).getBoolean(SOLAR, false)) return "";
        if (!hasLocationAccess(context)) { location = null; locationCheckedAt = -1L; return ""; }
        if (locationCheckedAt < 0L || now - locationCheckedAt > 300_000L) {
            locationCheckedAt = now;
            try {
                LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                if (manager != null) for (String provider : manager.getProviders(true)) {
                    try {
                        Location candidate = manager.getLastKnownLocation(provider);
                        if (candidate != null && (location == null || candidate.getTime() > location.getTime())) location = candidate;
                    } catch (SecurityException ignored) { /* Coarse access may exclude GPS on some ROMs. */ }
                }
            } catch (RuntimeException ignored) { /* Keep a still-fresh cached location if background access is restricted. */ }
            resolvedAt = -1L;
        }
        long wall = System.currentTimeMillis();
        if (location == null || wall < location.getTime() || wall - location.getTime() > 86_400_000L) return "";
        if (resolvedAt < 0L || now - resolvedAt >= 30_000L) {
            solarDark = SolarDayNight.isDark(wall, location.getLatitude(), location.getLongitude());
            resolvedAt = now;
        }
        return Boolean.TRUE.equals(solarDark) ? "dark" : "light";
    }

    static boolean hasLocationAccess(Context context) {
        return Build.VERSION.SDK_INT < 23 ? context.checkCallingOrSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED : context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
