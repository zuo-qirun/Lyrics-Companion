// SPDX-License-Identifier: AGPL-3.0-only
// Unmodified source lineWrap outputs with the same text metrics as upstream's own tests.
// Usage: node lumiere-layout-oracle.mjs <upstream> <pretext-package-folder> [output-json]
import fs from 'node:fs';
import path from 'node:path';
import { registerHooks } from 'node:module';
import { pathToFileURL, fileURLToPath } from 'node:url';
const pretext = path.resolve(process.argv[3]);
registerHooks({ resolve(specifier, context, next) {
    if (specifier.startsWith('@chenglou/pretext')) return {
        url: pathToFileURL(path.join(pretext, 'dist', specifier.endsWith('/rich-inline') ? 'rich-inline.js' : 'layout.js')).href, shortCircuit: true,
    };
    try { return next(specifier, context); }
    catch (error) { if (specifier.startsWith('.') && !path.extname(specifier)) return next(`${specifier}.ts`, context); throw error; }
} });
const upright = c => /[ᄀ-ᇿ⺀-⿿　-〿぀-ヿ㄀-ㇿ㈀-鿿가-힯豈-﫿︰-﹏＀-￯]|[\u{20000}-\u{3134f}]/u.test(c);
globalThis.OffscreenCanvas = class {
    getContext() { return {
        font: '10px sans-serif',
        measureText(text) {
            const px = Number(/([\d.]+)px/.exec(this.font)?.[1] ?? 10);
            return { width: Array.from(text).reduce((sum, c) => sum + px * (!c.trim() ? .3 : upright(c) ? 1 : .55), 0) };
        },
    }; }
};
const base = pathToFileURL(path.join(path.resolve(process.argv[2]), 'src/components/visualizer/lumiere/text/'));
const { flowLine, createTextMeasurer, frameBand } = await import(new URL('lineWrap.ts', base));
const { createWindowGeometry } = await import(new URL('windowGeometry.ts', base));
const { freePlacements } = await import(new URL('windowSlots.ts', base));
const { createRng } = await import(new URL('../lumiereRandom.ts', base));
const { segmentWords, wordScales, wordJags } = await import(new URL('wordStyle.ts', base));
const texts = [
    ['光落在晨雾里'], ['光A光'], ['迷失在无边际这幽深的森林', ['迷失在', '无边际', '这幽深的', '森林']],
    ['我们慢慢走回去，星河与风一起落在很远很远的地方'], ['他说「风会带走名字」然后离开'],
    ['supercalifragilistic love'], ['Until the morning comes'], ['   光  落在  the haze   '], ['森林', ['森林']],
    ['我听见远方的海潮一遍一遍拍打着沉默的礁石和灯塔我听见远方的海潮一遍一遍拍打着沉默的礁石和灯塔'],
];
const cases = [];
for (const [fullText, wordSegments] of texts) for (const styled of [false, true]) for (const limit of [600, 1e6]) {
    const hero = 100, spacing = .04, seed = 'fixture:window:0:' + fullText;
    const words = segmentWords({ fullText, wordSegments });
    const scales = styled ? wordScales(words, seed) : words.map(() => 1);
    const jags = styled ? wordJags(words, scales, seed) : words.map(() => 0);
    const glyphs = Array.from(fullText).map((char, index) => {
        const word = Math.max(0, words.findIndex(word => index >= word.start && index < word.end));
        const scale = scales[word] ?? 1;
        return { char, scale, advance: hero * (!char.trim() ? .3 : upright(char) ? 1 : .55) * scale + hero * spacing * scale,
            jag: (jags[word] ?? 0) * hero, upright: upright(char) };
    });
    const options = { heroPx: hero, limits: { horizontal: limit, vertical: limit } };
    cases.push({ fullText, words, glyphs, hero, spacing, limit, flow: flowLine(createTextMeasurer('sans-serif', 500, spacing), glyphs, words, options) });
}
const destination = process.argv[4] || path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../app/src/test/resources/folia_lumiere_layout.json');
fs.writeFileSync(destination, JSON.stringify({ revision: '9279251', cases }));
console.log(`Exported ${cases.length} original four-way word layouts to ${destination}`);
const windows = [];
for (const [width, height] of [[800, 600], [400, 800], [1600, 600]]) for (const kinds of [
    ['horizontal', 'horizontal', 'horizontal', 'horizontal', 'horizontal', 'horizontal'],
    ['vertical', 'vertical', 'vertical', 'vertical', 'vertical', 'vertical'],
    ['crossed', 'crossed', 'crossed', 'crossed', 'crossed', 'crossed'],
    ['horizontal', 'vertical', 'crossed', 'horizontal', 'crossed', 'vertical'],
]) {
    const hero = 51, region = { cx: .5 * width / height, cy: .54, w: .72 * width / height, h: .5 };
    const band = frameBand(width, height), columnCap = (band.bottom - band.top) * height;
    const maxWidthOf = kind => (band.right - band.left) * height * (kind === 'crossed' ? .9 : 1);
    const starts = [2, 4, 4.4, 6, 9, 10.2];
    const inputs = cases.filter(c => c.limit === 600).slice(0, 6);
    const lines = inputs.map((input, index) => {
        const glyphs = input.glyphs.map(g => ({ ...g, advance: g.advance * hero / 100, jag: g.jag * hero / 100 }));
        const flow = flowLine(createTextMeasurer('sans-serif', 500, .04), glyphs, input.words,
            { heroPx: hero, limits: { horizontal: maxWidthOf('horizontal'), vertical: columnCap } });
        const random = createRng('windowfixture:random:' + index);
        return { line: { startTime: starts[index] }, flow,
            placements: freePlacements(createRng('windowfixture:placements:' + index), region, .95),
            jitter: (random() - .5) * .16, velocity: { x: (random() - .5) * .044, y: (random() - .5) * .044 }, motionPhase: random() * Math.PI * 2 };
    });
    const geometry = createWindowGeometry({ options: { width, height, heroPx: hero, region, lines: lines.map(l => l.line),
        typography: 'horizontal', typographyOf: i => kinds[i], decay: { delay: 1, strength: 1 } },
        lineCount: lines.length, band, columnCap, maxWidthOf, nextAlpha: .95, lineOf: index => lines[index] });
    windows.push({ width, height, hero, region, kinds,
        lines: lines.map(l => ({ ...l, placements: Array.from(l.placements.entries()) })),
        frames: [0, 1.3, 2.1, 2.7, 3, 3.01, 4.7, 6.7, 8, 9, 12.3, 16, 20].map(time => ({ time,
            transforms: lines.map((_, index) => geometry.lineTransform(index, time)),
        })),
    });
}
const windowDestination = path.join(path.dirname(destination), 'folia_lumiere_window.json');
fs.writeFileSync(windowDestination, JSON.stringify({ revision: '9279251', windows }));
console.log(`Exported ${windows.length} original continuous windows (936 line transforms) to ${windowDestination}`);
