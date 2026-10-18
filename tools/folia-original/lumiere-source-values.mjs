// SPDX-License-Identifier: AGPL-3.0-only
// Reproduce the pure upstream values asserted by FoliaLumiereMathTest. Requires Node 24+.
import path from 'node:path';
import { registerHooks } from 'node:module';
import { pathToFileURL } from 'node:url';
registerHooks({ resolve(specifier, context, next) {
    try { return next(specifier, context); }
    catch (error) { if (specifier.startsWith('.') && !path.extname(specifier)) return next(`${specifier}.ts`, context); throw error; }
} });
const base = pathToFileURL(path.join(path.resolve(process.argv[2]), 'src/components/visualizer/lumiere/'));
const random = await import(new URL('lumiereRandom.ts', base));
const layout = await import(new URL('lumiereUnitLayout.ts', base));
const rig = await import(new URL('light/rig.ts', base));
const program = await import(new URL('lumiereProgram.ts', base));
const rng = random.createRng('fixture');
const texts = ['在无人的世界里', '听见风穿过树梢', '所有微光 汇成星河', '让这首歌陪你远行', 'Until the morning comes', '我们仍然向前走'];
const lines = texts.map((fullText, i) => ({ fullText, startTime: i * 3, endTime: (i + 1) * 3, words: [] }));
console.log(JSON.stringify({
    random: Array.from({ length: 5 }, () => rng()),
    camera: layout.createLumiereCamera({ width: 800, height: 600, camera: { push: .035, driftX: 0, driftY: -.006 }, startTime: 0, endTime: 18 })(6.7),
    noise: [rig.hash11(-12.3), rig.hash12(1.25, 7.5), rig.valueNoise1(3.7), rig.valueNoise2(1.25, 7.5)],
    gobo: [1, 2, 3, 4].map(p => rig.goboMask(p, 5, .4, .3, .2, .7)),
    program: program.compileLumiereProgram(lines, 'Fixture\n微光\nFolia', {}, { duration: 18 }),
}, null, 2));
