// SPDX-License-Identifier: AGPL-3.0-only
// Native adaptation of Folia's Claddagh geometry and timeline by chthollyphile and contributors.
// Reference: 92792519ce4b72d540e69f3634aa408810c6e0f6. See third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

final class FoliaRingMath {
    static final double MAX_ARC = 4.25;
    static final long ENTRY_LEAD_MS = 340;

    private FoliaRingMath() { }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static double radius(double width, double radiusScale) {
        return Math.min(width * 0.44, 560) * radiusScale;
    }

    static double readableAngle(double angle) {
        while (angle > 90) angle -= 180;
        while (angle < -90) angle += 180;
        return angle;
    }

    static final class Pose {
        double x, y, rotation, scale, opacity, blur, focus, depth;
    }

    /** Preserves the upstream projection, depth, focus, boundary fade and readable tangent. */
    static void project(Pose out, double psi, double radius, double tiltDegrees,
                        double lineDistance, double focusRatio, double spacingScale,
                        boolean past, boolean upcoming, double entry,
                        double lengthFade, double lengthScale) {
        double cos = Math.cos(psi);
        double depth = (cos + 1) / 2;
        double spacing = 0.35 + 0.65 * Math.pow(depth, 1.2);
        double rawX = Math.sin(psi) * radius * spacing;
        double rawY = cos * radius * 0.09;
        double theta = -tiltDegrees * Math.PI / 180;
        double rotCos = Math.cos(theta), rotSin = Math.sin(theta);
        out.x = rawX * rotCos - rawY * rotSin;
        out.y = rawX * rotSin + rawY * rotCos;
        double tangentX = cos * radius;
        double tangentY = -Math.sin(psi) * radius * 0.09;
        double tangent = readableAngle(Math.atan2(tangentX * rotSin + tangentY * rotCos,
                tangentX * rotCos - tangentY * rotSin) * 180 / Math.PI);
        double focus = Math.max(0, 1 - lineDistance)
                * Math.pow(1 - Math.min(1, Math.abs(psi) / 0.48), 1.8);
        double opacity = (0.35 + 0.65 * Math.pow(depth, 1.5) * (0.35 + 0.65 * focus))
                * (0.68 + 0.32 * Math.pow(depth, 1.9));
        opacity *= clamp(2 - lineDistance, 0, 1);
        if (past) opacity *= Math.max(0, 1 - lineDistance);
        opacity *= lengthFade;
        if (upcoming) opacity *= 0.18 + 0.82 * entry;
        if (lineDistance > 0.02) {
            double threshold = 1 - 1.2 * clamp((lineDistance - 0.3) / 0.6, 0, 1);
            if (cos > threshold) opacity *= clamp(1 - (cos - threshold) / 0.15, 0, 1);
        }
        out.scale = (0.22 + 0.98 * Math.pow(depth, 1.5)) * (1 + focusRatio * focus)
                * lengthScale * spacingScale;
        out.opacity = opacity;
        out.rotation = clamp(tangent * (0.4 + 0.6 * depth), -38, 38);
        out.blur = 8 * (1 - depth) * (1 - 0.5 * focus);
        out.focus = focus;
        out.depth = depth;
    }

    static double fractionalIndex(long[] starts, long[] ends, long time, long renderEnd) {
        if (starts.length == 0) return 0;
        if (starts.length == 1) {
            return time <= starts[0] ? 0 : (time - starts[0]) / (double) Math.max(200, renderEnd - starts[0]);
        }
        if (time <= starts[0]) return 0;
        int last = starts.length - 1;
        if (time >= starts[last]) {
            if (renderEnd > starts[last]) {
                return last + clamp((time - starts[last]) / (double) (renderEnd - starts[last]), 0, 1) * 2;
            }
            long duration = ends[last] - starts[last];
            long gap = starts[last] - starts[last - 1];
            return last + Math.min((time - starts[last]) / (double) (duration > 0 ? duration : gap > 0 ? gap : 500), 1.8);
        }
        for (int i = 0; i < last; i++) {
            if (time >= starts[i] && time < starts[i + 1]) {
                return i + (time - starts[i]) / (double) (starts[i + 1] - starts[i]);
            }
        }
        return last;
    }

    static double wordOffset(double[] angles, long[] starts, long[] ends, long time, long renderEnd) {
        if (angles.length == 0) return 0;
        double index = fractionalIndex(starts, ends, time, renderEnd);
        int last = angles.length - 1;
        if (index <= last) {
            int i = (int) Math.floor(index);
            return angles[i] + (angles[Math.min(i + 1, last)] - angles[i]) * (index - i);
        }
        return angles[last] + (angles[last] - angles[Math.max(0, last - 1)]) * (index - last);
    }

    static double spring(double seconds) {
        // Analytic response of the upstream spring: stiffness=55, damping=14, mass=0.9.
        double decay = 14 / (2 * 0.9);
        double omega = Math.sqrt(55 / 0.9 - decay * decay);
        return 1 - Math.exp(-decay * Math.max(0, seconds))
                * (Math.cos(omega * seconds) + decay / omega * Math.sin(omega * seconds));
    }

    static long frameDelay(String mode, int fps, boolean playing, boolean transitioning) {
        if (!FoliaModeCatalog.animated(mode) || fps <= 0 || !playing) return 400;
        return Math.round(1000f / (fps == 60 ? 60 : fps == 24 ? 24 : 30));
    }
}
