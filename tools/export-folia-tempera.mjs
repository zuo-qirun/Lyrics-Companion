// SPDX-License-Identifier: AGPL-3.0-only
// Development-only exporter: run the pinned original drawers, then record their native vector commands.
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { registerHooks, stripTypeScriptTypes } from 'node:module';
import { execFileSync } from 'node:child_process';
import { gzipSync } from 'node:zlib';
import { compileTemperaJava } from './folia-original/compile-tempera-java.mjs';

const upstream = path.resolve(process.argv[2] || '');
const revision = '92792519ce4b72d540e69f3634aa408810c6e0f6';
if (!process.argv[2]) throw new Error('Usage: node tools/export-folia-tempera.mjs <original Folia checkout>');
const head = execFileSync('git', ['-C', upstream, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
if (head !== revision) throw new Error(`Expected Folia ${revision}, got ${head}`);

registerHooks({
    resolve(specifier, context, next) {
        if (specifier.startsWith('.') && context.parentURL?.startsWith('file:')) {
            const candidate = fileURLToPath(new URL(specifier, context.parentURL));
            if (!path.extname(candidate) && fs.existsSync(`${candidate}.ts`)) {
                return next(pathToFileURL(`${candidate}.ts`).href, context);
            }
        }
        return next(specifier, context);
    },
    load(url, context, next) {
        if (url.endsWith('.ts')) return { format: 'module', source: stripTypeScriptTypes(fs.readFileSync(fileURLToPath(url), 'utf8')), shortCircuit: true };
        return next(url, context);
    },
});

class Node {
    x = 0; y = 0; rotation = 0; children = []; draws = []; current = []; painted = false;
    pivot = { x: 0, y: 0, set: (x, y = x) => { this.pivot.x = x; this.pivot.y = y; } };
    position = { set: (x, y = x) => { this.x = x; this.y = y; } };
    addChild(node) { this.children.push(node); return node; }
    begin() { if (this.painted) { this.current = []; this.painted = false; } }
    poly(points) { this.begin(); this.current.push(['poly', points]); return this; }
    rect(x, y, width, height) { this.begin(); this.current.push(['rect', x, y, width, height]); return this; }
    circle(x, y, radius) { this.begin(); this.current.push(['circle', x, y, radius]); return this; }
    moveTo(x, y) { this.begin(); this.current.push(['move', x, y]); return this; }
    lineTo(x, y) { this.begin(); this.current.push(['line', x, y]); return this; }
    fill(style) { this.draws.push({ path: this.current, fill: true, ...style, holes: [] }); this.painted = true; return this; }
    stroke(style) { this.draws.push({ path: this.current, fill: false, ...style, holes: [] }); this.painted = true; return this; }
    cut() { this.draws.at(-1).holes.push(...this.current); this.current = []; this.painted = false; return this; }
    toJSON() {
        return { x: this.x, y: this.y, rotation: this.rotation, pivot: { x: this.pivot.x, y: this.pivot.y },
            children: this.children, draws: this.draws, motion: this.motion, driftPhase: this.driftPhase };
    }
}
const pixi = { Graphics: Node, Container: Node, Color: { shared: { value: 0, setValue(value) { this.value = parseInt(value.slice(1), 16); return this; }, toNumber() { return this.value; } } } };
const moduleRoot = path.join(upstream, 'src/components/visualizer/tempera');
const { TEMPERA_SHOT_KINDS } = await import(pathToFileURL(path.join(moduleRoot, 'types.ts')).href);
const { TEMPERA_SHOT_PROFILES } = await import(pathToFileURL(path.join(moduleRoot, 'temperaShotProfiles.ts')).href);
const { drawTemperaComposition } = await import(pathToFileURL(path.join(moduleRoot, 'temperaCompositions.ts')).href);
const { temperaHash01 } = await import(pathToFileURL(path.join(moduleRoot, 'temperaRandom.ts')).href);
const { resolveTemperaGlyphMotion, easeTemperaEnter, easeTemperaInOut } = await import(pathToFileURL(path.join(moduleRoot, 'temperaMotion.ts')).href);
const { resolveTemperaCameraBreath, resolveTemperaCameraFrame } = await import(pathToFileURL(path.join(moduleRoot, 'temperaCamera.ts')).href);
const { compileTemperaProgram, buildTemperaSegments } = await import(pathToFileURL(path.join(moduleRoot, 'temperaProgram.ts')).href);
const { getLineRenderEndTime } = await import(pathToFileURL(path.join(upstream, 'src/utils/lyrics/renderHints.ts')).href);
const { resolveTemperaLayout } = await import(pathToFileURL(path.join(moduleRoot, 'temperaLayout.ts')).href);
const paletteKeys = ['paper', 'ink', 'blockA', 'blockB', 'blockC', 'accent', 'line', 'shadow', 'tone1', 'tone2', 'tone3', 'tone4'];
const palette = Object.fromEntries(paletteKeys.map((key, index) => [key, `#${(index + 1).toString(16).padStart(6, '0')}`]));
palette.gradient = palette.textGradient = null;
const outDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../app/src/main/assets/folia_original_canvas/tempera');
fs.mkdirSync(outDir, { recursive: true });
const nativeOutput = path.resolve(outDir, '../../../java/com/zuoqirun/lyricscompanion/FoliaTemperaGeometryProgram.java');
const generatedModules = compileTemperaJava(upstream, nativeOutput);
let commandCount = 0;
const round = (_, value) => typeof value === 'number' ? Math.round(value * 1e6) / 1e6 : value;
const cases = [];
const specs = [
    { width: 800, height: 600, seed: 0, flowAngle: 1.49, motif: 'diamonds', hatchAngle: .2, crossCount: 1 },
    { width: 1920, height: 720, seed: 4294967295, flowAngle: -1.82, motif: 'hatch-twin', hatchAngle: -.39, crossCount: 3 },
    { width: 430, height: 932, seed: 2039917801, flowAngle: 1.76, motif: 'band-cross', hatchAngle: .51, crossCount: 2 },
    { width: 1373, height: 777, seed: 279073917, flowAngle: -1.37, motif: 'poster-diamond', hatchAngle: -.71, crossCount: 2 },
    { width: 853, height: 479, seed: 689246010, flowAngle: 1.67, motif: 'doodle', hatchAngle: .33, crossCount: 3 },
];
for (const kind of TEMPERA_SHOT_KINDS) {
    for (const [index, spec] of specs.entries()) {
        const { seed, width, height, flowAngle, motif, hatchAngle, crossCount } = spec;
        const root = new Node(), items = [];
        const scribbleSeed = (seed + 72973) >>> 0;
        const showDecor = index !== 3;
        const ctx = {
            pixi, kind, palette, width, height, seed, showDecor, bleed: Math.max(width, height) * .17, flowAngle, gradient: null,
            decor: { motif, hatchAngle, crossCount, scribbleSeed },
            add(node, options = {}, parent) { node.motion = options; node.driftPhase = temperaHash01(seed, items.length, 173) * Math.PI * 2; items.push(node); (parent || root).addChild(node); },
            createGroup(rotation, x, y) { const group = new Node(); group.rotation = rotation; group.x = x; group.y = y; root.addChild(group); return group; },
        };
        drawTemperaComposition(ctx);
        commandCount += items.reduce((sum, node) => sum + node.draws.length, 0);
        cases.push({ kind, ...spec, scribbleSeed, showDecor, nodes: root.children });
    }
    // These old fixed recordings are replaced by native formulas, not loaded at runtime.
    fs.rmSync(path.join(outDir, `${kind}.json`), { force: true });
}
fs.writeFileSync(path.join(outDir, 'manifest.json'), `${JSON.stringify({
    project: 'Folia', version: '0.7.17', revision, geometry: 'native-java-formulas', generatedModules, paletteKeys,
    kinds: TEMPERA_SHOT_KINDS, profiles: TEMPERA_SHOT_PROFILES,
}, null, 2)}\n`);
const fixtureDir = path.resolve(outDir, '../../../../test/resources');
fs.mkdirSync(fixtureDir, { recursive: true });
const programs = [];
const sampleLines = [
    [1, 4.3, 'First, the quiet light returns.'], [4.4, 7.7, 'Across every window we sing!'],
    [8, 11.4, 'Bright wings carry us home!', 'chorus'], [11.5, 14.3, 'Stay here!', 'chorus'],
    [27, 29, 'Into the bridge', 'bridge'], [29.2, 32.8, 'Still moving through the clouds', 'bridge'],
    [34, 38.2, 'The words begin to fall again.'], [38.5, 43, 'One last soft wave of light.'],
].map(([startTime,endTime,fullText,songPart]) => ({startTime,endTime,fullText,songPart,words:[{text:fullText,startTime,endTime}]}));
for(const seed of ['Folia visual reference', 'song-42', '天气晴朗', '']) for(const wholeLine of [false,true]) {
    const lines = sampleLines.map(line => ({...line, renderEndTime:getLineRenderEndTime(line), nativeSegments:buildTemperaSegments(line)}));
    programs.push({seed,wholeLine,lines,expected:compileTemperaProgram(lines,seed,{wholeLineLyrics:wholeLine})});
}
const layouts = [];
for (const kind of TEMPERA_SHOT_KINDS) for (const [index, spec] of specs.slice(0,3).entries()) {
    const lines = [buildTemperaSegments(sampleLines[0]).filter(segment=>segment.text.trim())];
    const options = { lines, shotKind:kind, width:spec.width, height:spec.height, baseFontSize:92,
        fontFamily:'Tempera numeric fixture', fontWeight:600, seed:spec.seed, settleStretch:index/2 };
    // Node has no DOM Canvas: the original measure module takes its documented length*.6 fallback.
    layouts.push({options, expected:resolveTemperaLayout(options)});
}
fs.writeFileSync(path.join(fixtureDir, 'folia_tempera_geometry_reference.json.gz'), gzipSync(JSON.stringify({ revision, cases, programs, layouts }, round)));
console.log(`Compiled ${TEMPERA_SHOT_KINDS.length} original compositions into ${generatedModules} native formula modules; recorded ${cases.length} independent reference cases / ${commandCount} draw commands.`);
const glyph = { startTime: 1, endTime: 1.5, settleTime: 2.4, releaseTime: 4.4,
    enterX: 13, enterY: -5, enterRotation: .2, enterScale: .75, rotation: -.03, trackingX: 20, trackingY: -10 };
const fixture = {
    revision, time: 1.23, glyph,
    easing: [.1, .37, .6, .9].map(t => ({ t, enter: easeTemperaEnter(t), inOut: easeTemperaInOut(t) })),
    styles: ['slide', 'from-left', 'from-right', 'from-above', 'from-below', 'swing', 'stamp']
        .map(enterStyle => ({ enterStyle, ...resolveTemperaGlyphMotion({ ...glyph, enterStyle }, 1.23, 1) })),
    breath: resolveTemperaCameraBreath(6.7, .42),
    camera: resolveTemperaCameraFrame({ camera: { x: -.05, y: .03, zoom: 1.06, rotation: .01 }, cameraEnd: { x: .05, y: -.03, zoom: 1.14, rotation: -.01 } }, .37),
};
fs.writeFileSync(path.resolve(outDir, '../../../../../../third_party/folia/TEMPERA_MOTION_REFERENCE.json'), `${JSON.stringify(fixture, null, 2)}\n`);
