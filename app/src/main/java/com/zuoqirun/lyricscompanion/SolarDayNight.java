package com.zuoqirun.lyricscompanion;

import java.util.Calendar;
import java.util.TimeZone;

/** Solar elevation from NOAA's published fractional-year equations (solareqns.PDF).
 * Uses apparent sunrise/sunset at -0.833 degrees, including polar day/night. */
final class SolarDayNight {
    private SolarDayNight() { }

    static boolean isDark(long timeMs, double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude) || Math.abs(latitude) > 90d
                || Math.abs(longitude) > 180d) throw new IllegalArgumentException("Invalid coordinates");
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(timeMs);
        double hour = utc.get(Calendar.HOUR_OF_DAY) + utc.get(Calendar.MINUTE) / 60d
                + utc.get(Calendar.SECOND) / 3600d;
        double gamma = 2d * Math.PI / utc.getActualMaximum(Calendar.DAY_OF_YEAR)
                * (utc.get(Calendar.DAY_OF_YEAR) - 1d + (hour - 12d) / 24d);
        double eq = 229.18d * (0.000075d + 0.001868d * Math.cos(gamma) - 0.032077d * Math.sin(gamma)
                - 0.014615d * Math.cos(2d * gamma) - 0.040849d * Math.sin(2d * gamma));
        double dec = 0.006918d - 0.399912d * Math.cos(gamma) + 0.070257d * Math.sin(gamma)
                - 0.006758d * Math.cos(2d * gamma) + 0.000907d * Math.sin(2d * gamma)
                - 0.002697d * Math.cos(3d * gamma) + 0.00148d * Math.sin(3d * gamma);
        double solarMinutes = ((hour * 60d + eq + 4d * longitude) % 1440d + 1440d) % 1440d;
        double angle = Math.toRadians(solarMinutes / 4d - 180d);
        double lat = Math.toRadians(latitude);
        double elevation = Math.asin(Math.sin(lat) * Math.sin(dec) + Math.cos(lat) * Math.cos(dec) * Math.cos(angle));
        return elevation < Math.toRadians(-0.833d);
    }

    static String foregroundOverride(String target, String actual, boolean fresh, String mode) {
        return fresh && target != null && !target.isEmpty() && target.equals(actual)
                && ("light".equals(mode) || "dark".equals(mode)) ? mode : "";
    }
}
