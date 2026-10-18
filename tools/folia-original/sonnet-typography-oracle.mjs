// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Runs original typography roles/flow/poster/decoration code with an explicit font metric oracle.
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
        if (!url.endsWith('.ts')) return next(url, context);
        let source = fs.readFileSync(fileURLToPath(url), 'utf8');
        if (url.endsWith('/sonnetTypographyLayout.ts')) {
            // Both platforms receive precisely these measured advances; all packing runs upstream.
            source = source.replace(/^import \{ layoutWithLines, prepareWithSegments \} from '@chenglou\/pretext';/m, '');
            const first = source.indexOf('export const measureText =');
            const end = source.indexOf('export const resolveSonnetTypographyLayout =', first);
            source = source.slice(0, first) + `export const measureText = (text, fontSpec, fontSize) => {
                let units = 0;
                for (const char of text) units += char.codePointAt(0) >= 0x2e80 ? 1 : /\\s/.test(char) ? 0.32 : 0.55;
                return units * fontSize * (1 + parseInt(fontSpec) / 10000);
            };\n` + source.slice(end);
        }
        return { format: 'module', source: stripTypeScriptTypes(source), shortCircuit: true };
    },
});
const { resolveSonnetTypographyLayout } = await import(pathToFileURL(path.join(upstream, 'src/components/visualizer/sonnet/sonnetTypographyLayout.ts')).href);
const fixtures = [];
for (let sample = 0; sample < 20; sample++) {
    const tokens = ['月光', '穿过', '森林', 'rises', '再次', '远方', 'silent', '晚星', '照耀', '海面'];
    tokens[0] += '明'.repeat(sample % 5);
    const segments = tokens.slice(0, 3 + sample % 8).map((text, index) => ({
        text, startOffset: index * 10, endOffset: index * 10 + text.length, isWordLike: true,
        startTime: index * 0.6, endTime: index * 0.6 + 0.3 + index % 3 * 0.3,
        graphemes: [...text].map((char, glyph) => ({ char, startTime: index * 0.6 + glyph * 0.04, endTime: index * 0.6 + glyph * 0.04 + 0.04 })),
    }));
    const lines = sample % 3 ? [segments] : [segments.slice(0, 2), segments.slice(2)];
    for (const shotKind of ['editorial-column', 'type-impact', 'fragment-collage', 'tracking-ribbon', 'mask-reveal', 'poster-blocks', 'quiet-tableau']) {
        const options = { lines, shotKind, paragraphKind: 'verse', width: sample % 2 ? 431 : 1137,
            height: sample % 2 ? 997 : 641, baseFontSize: 24 + sample * 3.5, fontFamily: 'oracle', fontWeight: null };
        fixtures.push({ options, placements: resolveSonnetTypographyLayout(options) });
    }
}
const output = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../app/src/test/resources/folia/sonnet-typography-oracle.json.gz');
fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, zlib.gzipSync(JSON.stringify({ revision, fixtures }), { level: 9 }));
console.log(`Recorded ${fixtures.length} original typography layouts (${fs.statSync(output).size} compressed bytes).`);

class FrameNode {
    children = []; draws = []; current = []; painted = false; alpha = 1;
    position = { set() {} };
    addChild(...nodes) { this.children.push(...nodes); }
    clear() { this.draws = []; this.current = []; this.painted = false; }
    begin() { if (this.painted) { this.current = []; this.painted = false; } }
    moveTo(x, y) { this.begin(); this.current.push(x, y); return this; }
    lineTo(x, y) { this.begin(); this.current.push(x, y); return this; }
    poly(points) { this.begin(); this.current = points; return this; }
    circle(x, y, r) { this.begin(); this.current = { x, y, r }; return this; }
    stroke(options) { this.draws.push({ points: this.current, fill: false, width: options.width, alpha: options.alpha }); this.painted = true; return this; }
    fill(options) {
        this.draws.push(Array.isArray(this.current)
            ? { points: this.current, fill: true, width: 0, alpha: options.alpha }
            : { ...this.current, alpha: options.alpha, white: options.color === 0xffffff });
        this.painted = true; return this;
    }
}
const pixi = { Container: FrameNode, Graphics: FrameNode, Color: { shared: { value: 0, setValue(v) { this.value = v; return this; }, toNumber() { return this.value; } } } };
const { buildSonnetFrameDecor, resolveSonnetFrameDecorSpec } = await import(pathToFileURL(path.join(upstream, 'src/components/visualizer/sonnet/sonnetFrameDecor.ts')).href);
const frameFixtures = [];
for (let variant = 0; variant < 4; variant++) {
    let segment;
    for (let index = 0; ; index++) {
        segment = { text: `frame-${index}`, startOffset: 0, endOffset: 6 };
        const spec = resolveSonnetFrameDecorSpec(segment);
        if (spec.applied && spec.variant === variant) break;
    }
    for (const rotation of [0, Math.PI / 2, 0.35]) {
        const placement = { role: 'hero', x: 0, y: 0, rotation, measuredWidth: 187, measuredHeight: 79 };
        const frame = buildSonnetFrameDecor(pixi, { segment, placement, theme: { primaryColor: 0xabcdef },
            fontSize: 43.5, shotStartTime: 0, shotEndTime: 5, firstGlyphStartTime: 0 });
        for (const progress of [0, 0.03, 0.09, 0.2, 0.43, 0.79, 1]) {
            frame.update(progress);
            frameFixtures.push({ variant, rotation, measuredWidth: 187, measuredHeight: 79, fontSize: 43.5,
                progress, draws: frame.container.children[0].draws });
        }
    }
}
const frameOutput = path.join(path.dirname(output), 'sonnet-frame-oracle.json.gz');
fs.writeFileSync(frameOutput, zlib.gzipSync(JSON.stringify({ revision, fixtures: frameFixtures }), { level: 9 }));
console.log(`Recorded ${frameFixtures.length} original frame animation states (${fs.statSync(frameOutput).size} compressed bytes).`);
