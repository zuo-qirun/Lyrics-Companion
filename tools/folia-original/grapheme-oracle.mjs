// SPDX-License-Identifier: AGPL-3.0-only
// Execute the unmodified pinned timeline functions to regenerate native parity fixtures.
import path from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
import fs from 'node:fs';
import { registerHooks, createRequire } from 'node:module';
const base = pathToFileURL(path.resolve(process.argv[2]) + path.sep);
const require = createRequire(new URL('package.json', base));
let ts = require('typescript');
if (!ts.transpileModule || !ts.ScriptTarget) {
    ts = require(process.env.FOLIA_TYPESCRIPT || path.join(process.env.APPDATA, 'npm/node_modules/typescript/lib/typescript.js'));
}
registerHooks({ load(url, context, next) {
    if (url.endsWith('.ts')) return { format: 'module', shortCircuit: true,
        source: ts.transpileModule(fs.readFileSync(fileURLToPath(url), 'utf8'), {
            compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
        }).outputText };
    return next(url, context);
}, resolve(specifier, context, next) {
    try { return next(specifier, context); }
    catch (error) {
        if (specifier.startsWith('.') && !path.extname(specifier)) return next(`${specifier}.ts`, context);
        throw error;
    }
} });
const timing = await import(new URL('src/utils/lyrics/graphemeTiming.ts', base));
const ring = await import(new URL('src/components/visualizer/claddagh/claddaghTimeline.ts', base));
const lines = [
    { fullText: 'Hello, world!', startTime: 1, endTime: 2.5, words: [
        { text: 'Hello', startTime: 1, endTime: 1.5 }, { text: 'world', startTime: 1.7, endTime: 2.2 },
    ] },
    { fullText: ' 你好！', startTime: 0, endTime: 0.6, words: [
        { text: '你好', startTime: 0, endTime: 0.5 },
    ] },
    { fullText: 'abcd', startTime: 1, endTime: 2, words: [
        { text: 'xx', startTime: 1, endTime: 1.4 }, { text: 'cd', startTime: 1.5, endTime: 2 },
    ] },
    { fullText: '👨‍👩‍👧‍👦 é 🇨🇳', startTime: 0, endTime: 1, words: [] },
];
const output = lines.map(line => {
    const timeline = timing.buildLineGraphemeTimeline(line);
    return { line, timeline, claddagh: ring.adjustCladdaghTimeline(timeline, line) };
});
const result = JSON.stringify(output, null, 2);
if (process.argv[3]) {
    fs.mkdirSync(path.dirname(process.argv[3]), { recursive: true });
    fs.writeFileSync(process.argv[3], result + '\n');
}
else process.stdout.write(result + '\n');
