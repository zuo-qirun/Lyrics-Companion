// Copyright (c) 2026 chthollyphile and Lyrics Companion contributors.
// SPDX-License-Identifier: AGPL-3.0-only
// Usage: node export-lumiere-profiles.mjs <upstream-checkout> [output-java]
// Requires Node 24+. Only executes the checked-out upstream's pure profile definitions at build time.
import fs from 'node:fs';
import path from 'node:path';
import { registerHooks } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = path.resolve(process.argv[2] || '');
const output = path.resolve(process.argv[3] || path.join(path.dirname(fileURLToPath(import.meta.url)),
    '../../app/src/main/java/com/zuoqirun/lyricscompanion/FoliaLumiereProfiles.java'));
registerHooks({
    resolve(specifier, context, nextResolve) {
        try { return nextResolve(specifier, context); }
        catch (error) {
            if (specifier.startsWith('.') && !path.extname(specifier)) return nextResolve(`${specifier}.ts`, context);
            throw error;
        }
    },
});
const base = pathToFileURL(path.join(root, 'src/components/visualizer/lumiere/'));
const { LUMIERE_PROFILES } = await import(new URL('catalog.ts', base));
const numbers = (_key, value) => typeof value === 'number' ? +value.toFixed(8) : value;
const rows = LUMIERE_PROFILES.map(profile => {
    const { light, lineArt, ...data } = profile;
    // These are metadata only. FoliaLumiereGeometry executes light / lineArt recipes using
    // the actual scene seed, shot index and aspect. Never freeze geometry at sample viewports.
    return JSON.stringify(data, numbers);
});
const chunks = row => {
    const pieces = [];
    // Keep both modified-UTF8 constants and javac method bodies within their bytecode limits.
    for (let start = 0; start < row.length; start += 12000) pieces.push(JSON.stringify(row.slice(start, start + 12000)));
    return `join(new String[]{${pieces.join(',\n                ')}})`;
};
const java = `// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Generated from Folia 92792519ce4b72d540e69f3634aa408810c6e0f6. Do not hand-edit profile data.
// Regenerate with tools/folia-original/export-lumiere-profiles.mjs. See third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import org.json.JSONException;
import org.json.JSONObject;

/** All 100 profile metadata; dynamic light/line-art recipes are in FoliaLumiereGeometryProgram. */
final class FoliaLumiereProfiles {
    private FoliaLumiereProfiles() { }
    static final String[] KINDS = {${LUMIERE_PROFILES.map(p => JSON.stringify(p.kind)).join(', ')}};
    static final String[] FAMILIES = {${LUMIERE_PROFILES.map(p => JSON.stringify(p.family)).join(', ')}};
    static final String[] MOODS = {${LUMIERE_PROFILES.map(p => JSON.stringify(p.mood)).join(', ')}};
    private static String join(String[] parts) {
        StringBuilder result = new StringBuilder();
        for (String part : parts) result.append(part);
        return result.toString();
    }
    private static final String[] DATA = {\n        ${rows.map(chunks).join(',\n        ')}\n    };
    private static final JSONObject[] CACHE = new JSONObject[DATA.length];
    static int size() { return DATA.length; }
    static synchronized JSONObject get(int index) {
        if (index < 0 || index >= DATA.length) index = 0;
        if (CACHE[index] == null) {
            try { CACHE[index] = new JSONObject(DATA[index]); }
            catch (JSONException error) { throw new IllegalStateException("Invalid bundled Folia profile", error); }
        }
        return CACHE[index];
    }
}
`;
fs.writeFileSync(output, java, 'utf8');
console.log(`Exported ${rows.length} profiles; ${Buffer.byteLength(java)} Java source bytes to ${output}`);
const shaderTs = fs.readFileSync(path.join(root, 'src/components/visualizer/lumiere/light/lightFieldShader.ts'), 'utf8');
let shader = shaderTs.match(/const fragment = `([\s\S]*?)`;/)[1]
    .replace('precision highp float;', '')
    .replace('in vec2 vPos;', '').replace('in float vAlpha;', '').replace('out vec4 finalColor;', '')
    .replace('#define MAX_BEAMS ${MAX_BEAMS}', '')
    .replaceAll('MAX_BEAMS', '6')
    .replaceAll(/\bvec([234])\b/g, 'float$1').replaceAll(/\bmat2\b/g, 'float2x2')
    .replace('void main(void)', 'half4 main(float2 pixel)')
    .replace('float2 p = vPos / H;', 'float2 p = pixel / H;')
    .replaceAll('gl_FragCoord.xy', 'pixel')
    .replace('finalColor = float4(rgb, alpha) * vAlpha;', 'return half4(rgb, alpha);');
fs.writeFileSync(path.join(path.dirname(output), 'FoliaLumiereShader.java'), `// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Generated AGSL translation of Folia lumiere/light/lightFieldShader.ts at 9279251.
package com.zuoqirun.lyricscompanion;

final class FoliaLumiereShader {
    private FoliaLumiereShader() { }
    static final String SOURCE = ${JSON.stringify(shader)};
}
`, 'utf8');
