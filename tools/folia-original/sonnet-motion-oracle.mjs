// SPDX-License-Identifier: AGPL-3.0-only
// Runs the pinned original pure motion functions to regenerate JVM test snapshots.
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { registerHooks, stripTypeScriptTypes } from 'node:module';
registerHooks({
    load(url, context, next) {
        if (url.endsWith('.ts')) return { format: 'module', source: stripTypeScriptTypes(fs.readFileSync(fileURLToPath(url), 'utf8')), shortCircuit: true };
        return next(url, context);
    },
});
const root = path.resolve(process.argv[2]);
const motion = await import(pathToFileURL(path.join(root, 'src/components/visualizer/sonnet/sonnetMotion.ts')).href);
const kinds = ['editorial-column', 'type-impact', 'fragment-collage', 'tracking-ribbon', 'mask-reveal', 'poster-blocks', 'quiet-tableau'];
for (const kind of kinds) console.log(kind, JSON.stringify(motion.resolveShotMotionFrame(kind, 0.43)));
console.log('focus', JSON.stringify(motion.resolveSonnetFocusWeights([{ startTime: 1, endTime: 1.2 }, { startTime: 2, endTime: 2.3 }, { startTime: 4, endTime: 4.5 }], 1.8)));
const poster = await import(pathToFileURL(path.join(root, 'src/components/visualizer/sonnet/sonnetPosterBlocksLayout.ts')).href);
for (const seed of [42, 43]) {
    const boxes = [
        { isHero: false, isSemiHero: false, displayText: 'one', fontScale: 1, measuredWidth: 60, measuredHeight: 24 },
        { isHero: true, isSemiHero: false, displayText: 'hero', fontScale: 4, measuredWidth: 180, measuredHeight: 90, verticalDisplayText: 'h\ne\nr\no', verticalMeasuredWidth: 60, verticalMeasuredHeight: 240, verticalFontScale: 3.5 },
        { isHero: false, isSemiHero: false, displayText: 'two', fontScale: 1, measuredWidth: 70, measuredHeight: 24 },
        { isHero: false, isSemiHero: true, displayText: 'semi', fontScale: 2, measuredWidth: 100, measuredHeight: 40 },
        { isHero: false, isSemiHero: false, displayText: 'three', fontScale: 1, measuredWidth: 90, measuredHeight: 24 },
    ];
    console.log('poster', seed, JSON.stringify(poster.layoutSonnetPosterBlocks(boxes, 800, 480, 32, seed).placements));
}
