// SPDX-License-Identifier: AGPL-3.0-only
// Native port of pendoloClockworkMotion.ts / pendoloMotionProfile.ts (Folia 9279251).
// Copyright chthollyphile and Folia contributors. See third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

final class FoliaOriginalClockworkMotion {
    double phase, smoothedBass = 0.15, secondGearElapsed, secondGearAngle, secondGearVelocity, swing;
    long secondGearStep;

    void clear() {
        phase = secondGearElapsed = secondGearAngle = secondGearVelocity = swing = 0;
        smoothedBass = 0.15; secondGearStep = 0;
    }

    void step(double dt, double rawBass, boolean paused, float intensity) {
        if (paused || dt <= 0) return;
        boolean calm = intensity < 0.34f, chaotic = intensity > 0.66f;
        double response = calm ? 0.62 : chaotic ? 1.38 : 1;
        double speed = calm ? 0.72 : chaotic ? 1.3 : 1;
        double amplitude = calm ? 0.58 : chaotic ? 1.42 : 1;
        double spring = calm ? 0.72 : chaotic ? 1.3 : 1;
        double damping = calm ? 1.2 : chaotic ? 0.84 : 1;
        double bass = Math.max(0, Math.min(1, rawBass > 1 ? rawBass / 255 : rawBass));
        smoothedBass += (bass - smoothedBass) * (1 - Math.pow(1 - Math.min(0.24, 0.12 * response), dt * 60));
        phase += dt * (2.8 + smoothedBass * 3.5 * response) * speed;
        secondGearElapsed += dt;
        long steps = (long) Math.floor(secondGearElapsed);
        secondGearStep += steps; secondGearElapsed -= steps;
        double target = secondGearStep * Math.PI * 2 / 15;
        secondGearVelocity += (target - secondGearAngle) * 92 * spring * dt;
        secondGearVelocity *= Math.exp(-13 * damping * dt);
        secondGearAngle += secondGearVelocity * dt;
        swing = Math.sin(phase) * (0.15 + smoothedBass * 0.70 * response) * amplitude;
    }
}
