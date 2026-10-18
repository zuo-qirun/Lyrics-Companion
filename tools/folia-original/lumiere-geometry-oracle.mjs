// SPDX-License-Identifier: AGPL-3.0-only
// Executes the unmodified upstream recipes, generating full coordinate fixtures for Java.
// Usage: node lumiere-geometry-oracle.mjs <upstream> [output-json.gz]
import fs from 'node:fs';
import path from 'node:path';
import { registerHooks } from 'node:module';
import { gzipSync } from 'node:zlib';
import { pathToFileURL, fileURLToPath } from 'node:url';
registerHooks({ resolve(specifier, context, next) {
    try { return next(specifier, context); }
    catch (error) { if (specifier.startsWith('.') && !path.extname(specifier)) return next(`${specifier}.ts`, context); throw error; }
} });
const base = pathToFileURL(path.join(path.resolve(process.argv[2]), 'src/components/visualizer/lumiere/'));
const { LUMIERE_PROFILES } = await import(new URL('catalog.ts', base));
const { createRng } = await import(new URL('lumiereRandom.ts', base));
const cases = [];
for (const [aspect, seed, shot] of [[.5625, 'Fixture\n微光\nFolia:lumiere-p0', 0],
    [4 / 3, 'Song B\n另一首歌\nArtist:lumiere-p3', 2], [2.4, '宽屏\n光线与海\nArtist:lumiere-p1', 7]]) {
    for (let index = 0; index < LUMIERE_PROFILES.length; index++) {
        const profile = LUMIERE_PROFILES[index];
        const prefix = `${seed}:${shot}:${profile.kind}`;
        cases.push({ index, aspect, seed, shot,
            rig: profile.light({ aspect, random: createRng(`${prefix}:light`) }),
            art: profile.lineArt({ aspect, random: createRng(`${prefix}:art`) }),
        });
    }
}
const destination = process.argv[3] || path.resolve(path.dirname(fileURLToPath(import.meta.url)),
    '../../app/src/test/resources/folia_lumiere_geometry.json.gz');
fs.mkdirSync(path.dirname(destination), { recursive: true });
const data = gzipSync(JSON.stringify({ revision: '92792519ce4b72d540e69f3634aa408810c6e0f6', cases }));
fs.writeFileSync(destination, data);
console.log(`Exported ${cases.length} exact upstream light/line-art fixtures (${data.length} compressed bytes): ${destination}`);
