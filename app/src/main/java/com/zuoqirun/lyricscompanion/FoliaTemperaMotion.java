// SPDX-License-Identifier: AGPL-3.0-only
// Folia temperaCamera.ts / temperaMotion.ts / temperaEnterStyles.ts at 9279251.
package com.zuoqirun.lyricscompanion;

/** Original absolute-time camera and seven glyph entrances; no frame state can drift during a pause. */
final class FoliaTemperaMotion {
    private FoliaTemperaMotion() { }

    static double clamp(double value) { return Math.min(1, Math.max(0, value)); }

    static long hash(String text) {
        int value = 0x811c9dc5;
        for (int i = 0; i < text.length(); i++) value = (value ^ text.charAt(i)) * 16777619;
        return value & 0xffffffffL;
    }

    static double random(long seed, int index, int salt) {
        int mixed = ((int) (seed + (index + 1) * 97) ^ salt) * (int) 2654435761L;
        return (mixed & 0xffffffffL) / 4294967296.0;
    }

    private static double cubic(double p1, double p2, double time) {
        double inverse = 1 - time;
        return 3 * inverse * inverse * time * p1 + 3 * inverse * time * time * p2 + time * time * time;
    }

    static double bezier(double x1, double y1, double x2, double y2, double value) {
        double target = clamp(value);
        if (target == 0 || target == 1) return target;
        double low = 0, high = 1, parameter = target;
        for (int i = 0; i < 12; i++) {
            if (cubic(x1, x2, parameter) < target) low = parameter;
            else high = parameter;
            parameter = (low + high) / 2;
        }
        return cubic(y1, y2, parameter);
    }

    static double enter(double value) { return bezier(.22, 1, .36, 1, value); }
    static double inOut(double value) { return bezier(.62, 0, .32, 1, value); }
    static double softBack(double value) {
        double t = clamp(value) - 1;
        return 1 + 2.42 * t * t * t + 1.42 * t * t;
    }

    static double cubicInOut(double value) {
        double t = clamp(value);
        return t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    static double cameraAmount(double progress) {
        double clamped = clamp(progress);
        return clamped * .5 + cubicInOut(clamped) * .5 + Math.max(0, progress - 1) * .35;
    }

    static final class Frame {
        boolean visible;
        double alpha, x, y, rotation, scaleX, scaleY, echoX, echoY, echoAlpha;
    }

    static final class Glyph {
        String text;
        double x, y, fontSize, rotation, start, end, settle, release, trackingX, trackingY;
        double enterX, enterY, enterRotation, enterScale;
        int style;
    }

    static void glyph(Glyph glyph, double time, double motion, Frame out) {
        double window = Math.max(glyph.settle - glyph.start, .08);
        double linear = clamp((time - glyph.start) / window), travel = 1 - enter(linear);
        double uniform = glyph.enterScale + (1 - glyph.enterScale) * softBack(linear);
        double x = glyph.enterX * travel, y = glyph.enterY * travel;
        double rotation = glyph.enterRotation * travel, scale = uniform, echo = travel;
        if (glyph.style >= 1 && glyph.style <= 4) {
            double[] dx = {-1, 1, .12, -.12}, dy = {.12, -.12, -1, 1};
            double magnitude = Math.hypot(glyph.enterX, glyph.enterY) / Math.hypot(dx[glyph.style - 1], dy[glyph.style - 1]);
            x = dx[glyph.style - 1] * magnitude * travel;
            y = dy[glyph.style - 1] * magnitude * travel;
            rotation *= .4;
        } else if (glyph.style == 5) {
            x *= .6;
            y *= .6;
            rotation = (glyph.enterRotation + Math.signum(glyph.enterRotation == 0 ? 1 : glyph.enterRotation) * .95) * travel;
            echo *= .8;
        } else if (glyph.style == 6) {
            x = y = echo = 0;
            rotation *= .6;
            scale = 1 + travel * .7;
        }
        double reveal = clamp((time - glyph.start) / Math.min(window, 1.35));
        double sungWindow = glyph.end - glyph.start;
        double attack = Math.min(.12, sungWindow * .5);
        double emphasis = sungWindow <= 0 ? 0 : inOut((time - glyph.start) / Math.max(attack, .02))
                * (1 - inOut((time - glyph.end) / .26));
        double releaseStart = Math.max(glyph.end, glyph.settle);
        double release = inOut((time - releaseStart) / Math.max(glyph.release - releaseStart, .001));
        double spread = release * clamp(motion) * .055;
        double swell = emphasis * .05 * clamp(motion);
        out.visible = time >= glyph.start;
        out.alpha = inOut(clamp(reveal * 2.4));
        out.x = x * motion + glyph.trackingX * spread;
        out.y = y * motion + glyph.trackingY * spread;
        out.rotation = glyph.rotation + rotation * motion;
        out.scaleX = out.scaleY = scale + (1 - scale) * (1 - clamp(motion)) + swell;
        out.echoX = x * motion;
        out.echoY = y * motion;
        out.echoAlpha = echo * (1 - enter(reveal)) * .5 * clamp(motion);
    }

    static double[] breath(double time, double phase) {
        double tau = time * Math.PI * 2;
        return new double[]{
                (Math.sin(tau * .13 + phase) * .65 + Math.sin(tau * .31 + phase * 1.7) * .35) * .006,
                (Math.cos(tau * .11 + phase * 2.3) * .65 + Math.sin(tau * .29 + phase * .9) * .35) * .006,
                Math.sin(tau * .09 + phase * 1.3) * .002,
                Math.sin(tau * .07 + phase * 2.9) * .0015
        };
    }

    static final class Transition {
        double x, y, scale = 1, rotation, alpha = 1, blur, wipe, angle;
    }

    /** Direct port of resolveTemperaTransitionEffectFrame; absolute playback time only. */
    static Transition transition(String kind, boolean incoming, double progress, double angle) {
        Transition frame = new Transition(); frame.angle = angle;
        double linear = clamp(progress), eased = inOut(linear);
        if ("block-wipe".equals(kind)) { frame.wipe = incoming ? 1 + eased : eased; return frame; }
        if ("camera-pan".equals(kind)) {
            double offset = (incoming ? -(1 - eased) : eased) * .5;
            frame.x = Math.cos(angle) * offset; frame.y = Math.sin(angle) * offset;
            frame.scale = 1 + (incoming ? 1 - eased : eased) * .03;
            frame.alpha = incoming ? clamp(linear / .3) : 1 - clamp((linear - .72) / .28);
            return frame;
        }
        double drift = (incoming ? eased - 1 : eased) * .09, away = incoming ? 1 - eased : eased;
        frame.x = Math.cos(angle) * drift; frame.y = Math.sin(angle) * drift;
        frame.scale = 1 + away * .07; frame.blur = away * 6;
        frame.alpha = incoming ? clamp(linear / .45) : 1 - clamp((linear - .55) / .45);
        return frame;
    }
}
