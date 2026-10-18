// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Development-only recorder for the pinned Sonnet AnimatedGraphics drawers.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { registerHooks, stripTypeScriptTypes } from 'node:module';
import { execFileSync } from 'node:child_process';

const upstream = path.resolve(process.argv[2] || '');
const revision = '92792519ce4b72d540e69f3634aa408810c6e0f6';
if (!process.argv[2]) throw new Error('Usage: node tools/folia-original/export-sonnet-graphics.mjs <Folia checkout>');
const head = execFileSync('git', ['-C', upstream, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
if (head !== revision) throw new Error(`Expected ${revision}, got ${head}`);
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
const pixi = {
    Graphics: Node, Container: Node, Sprite: Node,
    Color: { shared: { value: 1, setValue(value) { this.value = value === '#000002' ? 2 : 1; return this; }, toNumber() { return this.value; } } },
};
const root = path.join(upstream, 'src/components/visualizer/sonnet');
const { buildSonnetShotMg } = await import(pathToFileURL(path.join(root, 'sonnetShotMg.ts')).href);
const out = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../app/src/main/assets/folia_original_canvas/sonnet');
fs.mkdirSync(out, { recursive: true });
const seeds = [173, 389, 617, 887];
let commands = 0;
const round = (_, value) => typeof value === 'number' ? Math.round(value * 1e6) / 1e6 : value;
const record = (kind, seed) => {
    const previousRandom = Math.random;
    let rng = seed >>> 0;
    Math.random = () => { rng = (Math.imul(rng, 1664525) + 1013904223) >>> 0; return rng / 4294967296; };
    let scene;
    try {
        scene = buildSonnetShotMg(pixi, kind, { primaryColor: '#000001', secondaryColor: '#000002' }, 800, 600, seed, new Map());
    } finally { Math.random = previousRandom; }
    for (const graphics of [scene.bg, scene.geo, ...scene.fixedGeoParts].filter(Boolean)) {
        graphics.display.draws = graphics.commands;
        graphics.display.animated = true;
        commands += graphics.commands.length;
    }
    if (scene.geoLayer) scene.geoLayer.motion = 'geometry';
    if (scene.fixedGeoLayer) scene.fixedGeoLayer.motion = 'fixed';
    if (scene.particleLayer) scene.particleLayer.motion = 'particles';
    const clean = node => {
        if (node.mask) node.mask.isMask = true;
        return {
            x: node.x, y: node.y, rotation: node.rotation, alpha: node.alpha,
            scaleX: node.scale.x, scaleY: node.scale.y,
            pivotX: node.pivot.x, pivotY: node.pivot.y,
            motion: node.motion, animated: node.animated, isMask: node.isMask,
            mask: node.mask ? clean(node.mask) : undefined,
            draws: node.draws, children: node.children.map(clean),
        };
    };
    // Mark masks before traversing siblings so they never become visible solid rectangles.
    const mark = node => { if (node.mask) node.mask.isMask = true; node.children.forEach(mark); };
    mark(scene);
    return { seed, nodes: scene.children.map(clean) };
};
for (let variant = 0; variant < 100; variant++) {
    const variants = seeds.map(cycle => record('type-impact', cycle * 100 + variant));
    fs.writeFileSync(path.join(out, `mg-${variant}.json`), `${JSON.stringify({ variant, variants }, round)}\n`);
}
const kinds = ['editorial-column', 'tracking-ribbon', 'mask-reveal', 'poster-blocks', 'quiet-tableau'];
for (const kind of kinds) {
    const variants = seeds.map(seed => record(kind, seed * 101));
    fs.writeFileSync(path.join(out, `${kind}.json`), `${JSON.stringify({ kind, variants }, round)}\n`);
}
fs.writeFileSync(path.join(out, 'manifest.json'), `${JSON.stringify({
    project: 'Folia', version: '0.7.17', revision, width: 800, height: 600,
    geometryVariants: 100, variantsPerGeometry: seeds.length, seedCycles: seeds, kinds,
    limitation: 'Original vector command recordings cover every MG motif, with four deterministic seed examples at 800x600. Arbitrary song-seed subvariants, viewport-sensitive recipes, icon textures and shader postprocessing are not equivalent to upstream execution.',
}, null, 2)}\n`);
console.log(`Exported 100 Sonnet geometry motifs and ${kinds.length} non-geometry shot families; ${commands} animated path commands.`);
