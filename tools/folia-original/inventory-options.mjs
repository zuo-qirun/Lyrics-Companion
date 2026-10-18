// SPDX-License-Identifier: AGPL-3.0-only
// Build-time inventory of the pinned Folia input contracts; does not add unimplemented UI controls.
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const ts = require(process.env.FOLIA_TYPESCRIPT || path.join(process.env.APPDATA, 'npm/node_modules/typescript/lib/typescript.js'));
const root = path.resolve(process.argv[2] || '');
const files = ['src/types.ts', 'src/components/visualizer/definition.ts',
    'src/components/visualizer/backgrounds/definition.ts', 'src/components/visualizer/VisualizerSubtitleOverlay.tsx'];
const declarations = {}, defaults = {};
for (const relative of files) {
    const source = ts.createSourceFile(relative, fs.readFileSync(path.join(root, relative), 'utf8'), ts.ScriptTarget.Latest, true);
    for (const node of source.statements) {
        if (ts.isVariableStatement(node)) for (const declaration of node.declarationList.declarations) {
            if (/^DEFAULT_.*TUNING$/.test(declaration.name.getText(source)) && declaration.initializer) {
                defaults[declaration.name.getText(source)] = declaration.initializer.getText(source);
            }
        }
        if (ts.isInterfaceDeclaration(node) && (/Tuning$/.test(node.name.text)
                || ['Theme', 'VisualizerSharedProps', 'VisualizerBackgroundConfig', 'VisualizerSubtitleOverlayProps'].includes(node.name.text))) {
            declarations[node.name.text] = {
                source: relative,
                fields: node.members.filter(ts.isPropertySignature).map(member => ({
                    name: member.name.getText(source), optional: Boolean(member.questionToken),
                    type: member.type?.getText(source), line: source.getLineAndCharacterOfPosition(member.getStart(source)).line + 1,
                })),
            };
        }
    }
}
const result = { revision: '92792519ce4b72d540e69f3634aa408810c6e0f6',
    note: 'Source contracts for the later options phase. A listed field is not evidence of native implementation.',
    declarations, defaults };
const output = process.argv[3] || 'third_party/folia/OPTIONS_SOURCE_INVENTORY.json';
fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, JSON.stringify(result, null, 2) + '\n');
process.stdout.write(JSON.stringify({ contracts: Object.keys(declarations).length,
    fields: Object.values(declarations).reduce((n, x) => n + x.fields.length, 0), defaults: Object.keys(defaults).length }) + '\n');
