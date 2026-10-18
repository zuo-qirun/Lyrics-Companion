// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Execute the pinned ORIGINAL recipe functions, never the Android IR, to produce test evidence.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { registerHooks, stripTypeScriptTypes } from 'node:module';
import { execFileSync } from 'node:child_process';

const upstream = path.resolve(process.argv[2] || '');
const revision = '92792519ce4b72d540e69f3634aa408810c6e0f6';
if (!process.argv[2] || execFileSync('git', ['-C', upstream, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim() !== revision) throw new Error('Pass the pinned Folia checkout');
registerHooks({
    resolve(specifier, context, next) {
        if (specifier.startsWith('.') && context.parentURL?.startsWith('file:')) {
            const candidate = fileURLToPath(new URL(specifier, context.parentURL));
            if (!path.extname(candidate) && fs.existsSync(`${candidate}.ts`)) return next(pathToFileURL(`${candidate}.ts`).href, context);
        }
        return next(specifier, context);
    },
    load(url, context, next) {
        if (url.endsWith('.ts')) return { format: 'module', source: stripTypeScriptTypes(fs.readFileSync(fileURLToPath(url), 'utf8')), shortCircuit: true };
        return next(url, context);
    },
});
class Node {
    x = 0; y = 0; rotation = 0; alpha = 1; children = []; draws = []; current = []; painted = false;
    scale = { x: 1, y: 1, set: (x, y = x) => { this.scale.x = x; this.scale.y = y; } };
    pivot = { x: 0, y: 0, set: (x, y = x) => { this.pivot.x = x; this.pivot.y = y; } };
    position = { set: (x, y = x) => { this.x = x; this.y = y; } };
    anchor = { set() {} };
    clear() { this.current = []; this.draws = []; this.painted = false; return this; }
    addChild(...nodes) { this.children.push(...nodes); return nodes[0]; }
    begin() { if (this.painted) { this.current = []; this.painted = false; } }
    moveTo(x, y) { this.begin(); this.current.push({ type: 'moveTo', x, y }); return this; }
    lineTo(x, y) { this.begin(); this.current.push({ type: 'lineTo', x, y }); return this; }
    rect(x, y, w, h) { this.begin(); this.current.push({ type: 'rect', x, y, w, h }); return this; }
    circle(x, y, r) { this.begin(); this.current.push({ type: 'circle', x, y, r }); return this; }
    quadraticCurveTo(cx, cy, tx, ty) { this.begin(); this.current.push({ type: 'quadraticCurveTo', cx, cy, tx, ty }); return this; }
    bezierCurveTo(c1x, c1y, c2x, c2y, tx, ty) { this.begin(); this.current.push({ type: 'bezierCurveTo', c1x, c1y, c2x, c2y, tx, ty }); return this; }
    arc(cx, cy, r, start, end, anticlockwise = false) { this.begin(); this.current.push({ type: 'arc', cx, cy, r, start, end, anticlockwise }); return this; }
    fill(options) { this.draws.push({ type: 'fill', path: this.current, options }); this.painted = true; return this; }
    stroke(options) { this.draws.push({ type: 'stroke', path: this.current, options }); this.painted = true; return this; }
}
const pixi = { Graphics: Node, Container: Node, Sprite: Node,
    Color: { shared: { value: 1, setValue(value) { this.value = typeof value === 'number' ? value : value === '#000002' ? 2 : 1; return this; }, toNumber() { return this.value; } } } };
const { buildSonnetShotMg } = await import(pathToFileURL(path.join(upstream, 'src/components/visualizer/sonnet/sonnetShotMg.ts')).href);
const clean = node => ({
    x: node.x, y: node.y, rotation: node.rotation, alpha: node.alpha,
    scaleX: node.scale.x, scaleY: node.scale.y, pivotX: node.pivot.x, pivotY: node.pivot.y,
    motion: node.motion, animated: node.animated, isMask: node.isMask,
    draws: node.draws, mask: node.mask ? clean(node.mask) : undefined, children: node.children.map(clean),
});
const record = (kind, width, height, seed) => {
    const previousRandom = Math.random;
    let rng = seed >>> 0;
    let randomCalls = 0;
    Math.random = () => { randomCalls++; rng = (Math.imul(rng, 1664525) + 1013904223) >>> 0; return rng / 4294967296; };
    let scene;
    try { scene = buildSonnetShotMg(pixi, kind, { primaryColor: '#000001', secondaryColor: '#000002' }, width, height, seed, new Map()); }
    finally { Math.random = previousRandom; }
    for (const graphics of [scene.bg, scene.geo, ...scene.fixedGeoParts].filter(Boolean)) {
        graphics.display.draws = graphics.commands; graphics.display.animated = true;
    }
    if (scene.geoLayer) scene.geoLayer.motion = 'geometry';
    if (scene.fixedGeoLayer) scene.fixedGeoLayer.motion = 'fixed';
    if (scene.particleLayer) scene.particleLayer.motion = 'particles';
    const mark = node => { if (node.mask) node.mask.isMask = true; node.children.forEach(mark); };
    mark(scene);
    return { kind, width, height, seed, randomCalls, nodes: scene.children.map(clean) };
};
const fixtures = [];
for (let variant = 0; variant < 100; variant++) {
    // Deliberately outside the old 173/389/617/887 seed cycles and 800x600 recording viewport.
    fixtures.push(record(variant % 2 ? 'fragment-collage' : 'type-impact', 1137, 641, 123456700 + variant));
    fixtures.push(record('type-impact', 431, 997, 7654300 + variant));
}
for (const kind of ['editorial-column', 'tracking-ribbon', 'mask-reveal', 'poster-blocks', 'quiet-tableau']) {
    for (const [width, height, seed] of [[1137, 641, 3456789], [431, 997, 2147483677]]) fixtures.push(record(kind, width, height, seed));
}
for (const seed of [3, 103, 203, 303, 8, 108, 208, 308]) fixtures.push(record('type-impact', 901, 509, seed));
const output = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../app/src/test/resources/folia/sonnet-geometry-oracle.json.gz');
fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, zlib.gzipSync(JSON.stringify({ revision, fixtures }), { level: 9 }));
console.log(`Recorded ${fixtures.length} independently executed upstream geometry scenes (${fs.statSync(output).size} compressed bytes).`);
const { buildSonnetTextFixedGeo } = await import(pathToFileURL(path.join(upstream, 'src/components/visualizer/sonnet/sonnetTextFixedGeo.ts')).href);
const textFixtures = [];
for (let seed = 0; seed < 120; seed++) for (const chorus of [false, true]) {
    const fontSize = 27 + seed % 19 * 3.5, layoutWidth = seed % 2 ? 431 : 1137;
    const node = buildSonnetTextFixedGeo(pixi, { seed, isChorusEffect: chorus, fontSize, layoutWidth,
        theme: { primaryColor: 1, secondaryColor: 2, accentColor: 3 } });
    textFixtures.push({ seed, chorus, fontSize, layoutWidth, node: clean(node) });
}
const textOutput = path.join(path.dirname(output), 'sonnet-text-geometry-oracle.json.gz');
fs.writeFileSync(textOutput, zlib.gzipSync(JSON.stringify({ revision, fixtures: textFixtures }), { level: 9 }));
console.log(`Recorded ${textFixtures.length} original text fixed geometry plans (${fs.statSync(textOutput).size} compressed bytes).`);

const { createSonnetGuide } = await import(pathToFileURL(path.join(upstream, 'src/components/visualizer/sonnet/sonnetGuides.ts')).href);
const guideFixtures = [];
for (const hero of [false, true]) for (const seed of [42, 103, 3456789, 2147483677]) {
    let rng = seed >>> 0;
    const randomValues = [], previousRandom = Math.random;
    Math.random = () => { rng = (Math.imul(rng, 1664525) + 1013904223) >>> 0; const value = rng / 4294967296; randomValues.push(value); return value; };
    let guide;
    const size = 43.5, enterX = seed % 2 ? 0 : 20, enterY = seed % 3 ? -35 : 0, timingPhase = seed % 2 ? 0.2 : 0.8;
    try { guide = createSonnetGuide(pixi, { startTime: 2, endTime: 3 }, { role: hero ? 'hero' : 'support', x: 0, y: 0, enterX, enterY, timingPhase },
        { primaryColor: 1, secondaryColor: 2, accentColor: 3 }, size); }
    finally { Math.random = previousRandom; }
    for (const progress of [0, 0.03, 0.17, 0.31, 0.43, 0.65, 0.9, 1]) {
        guide.update(progress);
        const [graphics, particles] = guide.container.children;
        const draws = graphics.draws.map(draw => {
            const first = draw.path[0], alpha = draw.options.alpha * guide.maxAlpha, white = draw.options.color === 0xffffff;
            if (first.type === 'circle') return { op: 'circle', x: first.x, y: first.y, radius: first.r, fill: draw.type === 'fill', width: draw.options.width || 0, alpha, white };
            return { op: 'line', x1: draw.path[0].x, y1: draw.path[0].y, x2: draw.path[1].x, y2: draw.path[1].y, width: draw.options.width, alpha, white };
        });
        for (const node of particles.children) {
            const first = node.draws[0], primitive = first.path[0];
            const kind = primitive.type === 'circle' ? 0 : primitive.type === 'rect' ? 1 : first.type === 'stroke' ? 2 : 3;
            const particleSize = kind === 0 ? primitive.r : kind === 1 ? primitive.w / 2 : kind === 2 ? Math.abs(primitive.x) : Math.abs(primitive.y);
            draws.push({ op: 'particle', kind, size: particleSize, x: node.x, y: node.y, rotation: node.rotation,
                scale: node.scale.x, alpha: node.alpha * first.options.alpha * guide.maxAlpha });
        }
        guideFixtures.push({ hero, seed, size, enterX, enterY, timingPhase, progress, randomValues, draws });
    }
}
const guideOutput = path.join(path.dirname(output), 'sonnet-guide-oracle.json.gz');
fs.writeFileSync(guideOutput, zlib.gzipSync(JSON.stringify({ revision, fixtures: guideFixtures }), { level: 9 }));
console.log(`Recorded ${guideFixtures.length} original guide states with ordered LCG random traces (${fs.statSync(guideOutput).size} compressed bytes).`);
