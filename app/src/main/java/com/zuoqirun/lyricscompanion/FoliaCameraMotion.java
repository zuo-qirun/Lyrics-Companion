// SPDX-License-Identifier: AGPL-3.0-only
// Folia 0.7.17 fumeCamera.ts / fumeCameraStep.ts and native spring integration.
// Copyright chthollyphile and Folia contributors. See third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

/** Stateful, velocity-preserving motion. No Android clock or frame-rate dependency. */
final class FoliaCameraMotion {
    private FoliaCameraMotion() { }

    static final class Spring {
        double value, velocity;
        boolean initialized;

        void snap(double target) { value = target; velocity = 0; initialized = true; }
        void clear() { initialized = false; velocity = 0; }

        double step(double target, double seconds, double stiffness, double damping, double mass) {
            if (!initialized) snap(target);
            if (seconds <= 0) return value;
            double t = Math.min(seconds, 0.1);
            double x = value - target;
            double decay = damping / (2 * mass);
            double discriminant = stiffness / mass - decay * decay;
            if (discriminant > 1e-8) {
                double omega = Math.sqrt(discriminant);
                double b = (velocity + decay * x) / omega;
                double cos = Math.cos(omega * t), sin = Math.sin(omega * t);
                double e = Math.exp(-decay * t);
                value = target + e * (x * cos + b * sin);
                velocity = e * ((-decay * x + b * omega) * cos
                        + (-decay * b - x * omega) * sin);
            } else if (discriminant < -1e-8) {
                double root = Math.sqrt(-discriminant);
                double r1 = -decay + root, r2 = -decay - root;
                double a = (velocity - r2 * x) / (r1 - r2), b = x - a;
                double e1 = Math.exp(r1 * t), e2 = Math.exp(r2 * t);
                value = target + a * e1 + b * e2;
                velocity = a * r1 * e1 + b * r2 * e2;
            } else {
                double b = velocity + decay * x, e = Math.exp(-decay * t);
                value = target + (x + b * t) * e;
                velocity = (b - decay * (x + b * t)) * e;
            }
            return value;
        }

        boolean moving(double target) { return Math.abs(value - target) > 0.001 || Math.abs(velocity) > 0.001; }
    }

    static final class Clock {
        long lastWall = -1, lastPosition = -1, animationMs, deltaMs;
        boolean wasPlaying, seek;

        void clear() { lastWall = lastPosition = -1; animationMs = deltaMs = 0; wasPlaying = seek = false; }

        void step(long position, long wall, boolean playing) {
            long elapsed = lastWall < 0 ? 0 : Math.max(0, wall - lastWall);
            long expected = lastPosition + (wasPlaying ? elapsed : 0);
            seek = lastPosition >= 0 && (position < lastPosition - 250
                    || playing && wasPlaying && Math.abs(position - lastPosition) > 250
                    && Math.abs(position - expected) > 1500
                    || !playing && Math.abs(position - lastPosition) > 1500);
            // A hidden/detached view or a resumed player never catches up missed animation time.
            deltaMs = playing && wasPlaying && !seek ? Math.min(100, elapsed) : 0;
            animationMs += deltaMs;
            lastPosition = position;
            lastWall = wall;
            wasPlaying = playing;
        }
    }

    /** The original article camera: entry bias, overview flight, float and damped tracking. */
    static final class Article {
        double x, y, scale = 1.18, velocityX, velocityY, velocityScale;
        double focusX, focusY, focusScale = 1.18;
        private double fromX, fromY, fromScale, waypointX, waypointY, waypointScale;
        private double elapsed, duration = 0.09;
        private int source = Integer.MIN_VALUE, bridge;
        private boolean initialized;

        void clear() { initialized = false; source = Integer.MIN_VALUE; velocityX = velocityY = velocityScale = 0; }
        /** Freezes the camera at the article center until the player actually advances time. */
        void ensureInitialized(double targetX, double targetY) {
            if (initialized) return;
            snap(targetX, targetY, 1.18);
        }
        void snap(double targetX, double targetY, double targetScale) {
            x = focusX = targetX; y = focusY = targetY; scale = focusScale = targetScale;
            velocityX = velocityY = velocityScale = 0; initialized = true;
        }

        void step(int line, double targetX, double targetY, double targetScale,
                  double entryX, double entryY, double overviewX, double overviewY, double overviewScale,
                  double width, double height, double clockSeconds, double dt, boolean moving,
                  boolean snap, float strength) {
            if (!initialized || snap) {
                snap(targetX, targetY, targetScale);
                source = line;
                elapsed = duration;
                return;
            }
            if (!moving || dt <= 0) return;
            boolean changed = source != line;
            if (changed) {
                source = line; fromX = x; fromY = y; fromScale = scale;
                elapsed = 0;
                duration = line == -2 ? clamp(Math.min(width, height) / 1500, 0.38, 0.58) : 0.09;
            } else elapsed += dt;
            double phase = clamp(elapsed / duration, 0, 1);
            double boost = Math.pow(1 - phase, 3);
            if (line >= 0) {
                double bias = Math.pow(boost, 0.58);
                targetX = mix(targetX, entryX, bias); targetY = mix(targetY, entryY, bias);
            }
            double distance = strength < 0.34f ? 14 : strength > 0.66f ? 24 : 18;
            double period = strength < 0.34f ? 8.5 : strength > 0.66f ? 5.8 : 7;
            double amplitude = strength < 0.34f ? 0.008 : strength > 0.66f ? 0.014 : 0.011;
            double floatPhase = clockSeconds / period * Math.PI * 2;
            double attenuation = line == -2 ? 0.36 : 1;
            targetX -= Math.sin(floatPhase * 0.74 + 0.8) * distance * 0.34 / targetScale;
            targetY -= (Math.sin(floatPhase) * distance + Math.sin(floatPhase * 0.5 + 1.1)
                    * distance * 0.22) * attenuation / targetScale;
            targetScale = clamp(targetScale * (1 + Math.sin(floatPhase + 0.9) * amplitude * attenuation), 0.22, 2.24);
            if (changed) {
                double screenDistance = Math.hypot(targetX - fromX, targetY - fromY) * Math.max(fromScale, targetScale);
                double side = Math.max(1, Math.min(width, height));
                bridge = screenDistance >= side * 0.42 ? 1 : 0;
                if (line >= 0 && screenDistance >= side * 2.75) {
                    bridge = 2;
                    double loft = clamp((screenDistance / side - 2.75) / 3.4, 0, 1);
                    double bias = mix(0.18, 0.42, loft);
                    waypointX = mix((fromX + targetX) / 2, overviewX, bias);
                    waypointY = mix((fromY + targetY) / 2, overviewY, bias);
                    double endpoint = Math.max(fromScale, targetScale);
                    waypointScale = clamp(Math.max(endpoint * mix(0.62, 0.4, loft),
                            overviewScale * mix(1.85, 1.55, loft)), 0.22, Math.max(endpoint * 0.92, 0.22));
                    duration = clamp(0.26 + screenDistance / (side * 5.5) * 0.28, 0.3, 0.68);
                }
            }
            if (bridge != 0 && phase < 1) {
                double p = bridge == 2 ? 1 - Math.pow(1 - phase, 3) : ease(phase);
                double bx = bridge == 2 ? bezier(fromX, waypointX, targetX, p) : mix(fromX, targetX, p);
                double by = bridge == 2 ? bezier(fromY, waypointY, targetY, p) : mix(fromY, targetY, p);
                double bs = bridge == 2 ? bezier(fromScale, waypointScale, targetScale, p) : mix(fromScale, targetScale, p);
                double catchUp = 1 - Math.exp(-dt * (bridge == 2 ? mix(12.5, 22, 1 - phase) : mix(10.5, 17.5, 1 - phase)));
                focusX = bx; focusY = by; focusScale = bs;
                x += (bx - x) * catchUp; y += (by - y) * catchUp; scale += (bs - scale) * catchUp;
                velocityX *= 0.72; velocityY *= 0.72; velocityScale *= 0.68;
            } else {
                double catchUp = 1 - Math.exp(-dt * mix(11.2, clamp(4.8 / Math.max(duration, 0.05), 20, 54), boost));
                focusX += (targetX - focusX) * catchUp; focusY += (targetY - focusY) * catchUp;
                focusScale += (targetScale - focusScale) * (1 - Math.exp(-dt * mix(5.4, 12.8, boost)));
                double distanceToTarget = Math.hypot(targetX - x, targetY - y);
                double spring = mix(208, clamp(15.8 / Math.max(duration * duration, 0.0064), 260, 780), boost);
                double damping = mix(24, clamp(Math.sqrt(spring) * 1.36, 24, 40), boost);
                double maxVelocity = mix(1320, clamp(distanceToTarget / Math.max(duration * 0.28, 0.028), 2600, 8800), boost);
                // Bound integration substeps so 24/30/60 fps all remain stable.
                int steps = Math.max(1, (int) Math.ceil(dt / (1.0 / 120)));
                double h = dt / steps;
                for (int i = 0; i < steps; i++) {
                    velocityX = clamp(velocityX + ((focusX - x) * spring - velocityX * damping) * h, -maxVelocity, maxVelocity);
                    velocityY = clamp(velocityY + ((focusY - y) * spring - velocityY * damping) * h, -maxVelocity, maxVelocity);
                    x += velocityX * h; y += velocityY * h;
                    velocityScale = clamp(velocityScale + ((focusScale - scale) * mix(54, 108, boost)
                            - velocityScale * mix(13.5, 21, boost)) * h, -1.6, 1.6);
                    scale += velocityScale * h;
                }
            }
            scale = clamp(scale, 0.22, 2.24);
        }
    }

    static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    static double mix(double a, double b, double p) { return a + (b - a) * p; }
    static double ease(double p) { return p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2; }
    private static double bezier(double a, double b, double c, double p) { return (1 - p) * (1 - p) * a + 2 * (1 - p) * p * b + p * p * c; }
}
