// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Lyrics Companion contributors.
// Compile the pure upstream geometry recipes to a small native expression program.
// No JavaScript engine, reference viewport, recorded song seed or raster is used on Android.
// Usage: node compile-lumiere-geometry.mjs <upstream> <acorn-module-path> [output-java]
import fs from 'node:fs';
import path from 'node:path';
import { stripTypeScriptTypes } from 'node:module';
import { pathToFileURL, fileURLToPath } from 'node:url';

const root = path.resolve(process.argv[2]);
const { parse } = await import(pathToFileURL(path.resolve(process.argv[3])));
const destination = process.argv[4] || path.resolve(path.dirname(fileURLToPath(import.meta.url)),
    '../../app/src/main/java/com/zuoqirun/lyricscompanion/FoliaLumiereGeometryProgram.java');
const modules = [], loaded = new Map();
const expression = n => {
    if (!n) return null;
    switch (n.type) {
        case 'Literal': return ['l', n.value];
        case 'Identifier': return ['v', n.name];
        case 'ArrayExpression': return ['a', ...n.elements.map(expression)];
        case 'ObjectExpression': return ['o', ...n.properties.map(p => p.type === 'SpreadElement'
            ? ['s', expression(p.argument)] : ['p', p.computed ? expression(p.key) : ['l', p.key.name ?? p.key.value], expression(p.value)])];
        case 'SpreadElement': return ['s', expression(n.argument)];
        case 'MemberExpression': return ['g', expression(n.object), n.computed ? expression(n.property) : ['l', n.property.name]];
        case 'CallExpression': return ['c', expression(n.callee), ...n.arguments.map(expression)];
        case 'ArrowFunctionExpression': case 'FunctionExpression':
            return ['f', n.params.map(pattern), n.body.type === 'BlockStatement' ? statement(n.body) : ['r', expression(n.body)]];
        case 'BinaryExpression': case 'LogicalExpression': return ['b', n.operator, expression(n.left), expression(n.right)];
        case 'ConditionalExpression': return ['t', expression(n.test), expression(n.consequent), expression(n.alternate)];
        case 'UnaryExpression': return ['u', n.operator, expression(n.argument)];
        case 'AssignmentExpression': return ['=', n.operator, expression(n.left), expression(n.right)];
        case 'UpdateExpression': return ['++', n.operator === '++' ? 1 : -1, expression(n.argument), n.prefix];
        case 'SequenceExpression': return ['q', ...n.expressions.map(expression)];
        default: throw new Error(`Unsupported geometry expression ${n.type}: ${JSON.stringify(n).slice(0, 120)}`);
    }
};
const pattern = n => {
    switch (n.type) {
        case 'Identifier': return n.name;
        case 'AssignmentPattern': return ['default', pattern(n.left), expression(n.right)];
        case 'RestElement': return ['rest', pattern(n.argument)];
        case 'ObjectPattern': return ['object', ...n.properties.map(p => [p.key.name ?? p.key.value, pattern(p.value)])];
        case 'ArrayPattern': return ['array', ...n.elements.map(e => e ? pattern(e) : null)];
        default: throw new Error(`Unsupported geometry binding ${n.type}`);
    }
};
const statement = n => {
    if (!n) return null;
    switch (n.type) {
        case 'BlockStatement': return ['q', ...n.body.map(statement)];
        case 'VariableDeclaration': return ['q', ...n.declarations.map(d => ['d', pattern(d.id), expression(d.init)])];
        case 'ExpressionStatement': return expression(n.expression);
        case 'ReturnStatement': return ['r', expression(n.argument)];
        case 'IfStatement': return ['if', expression(n.test), statement(n.consequent), statement(n.alternate)];
        case 'ForStatement': return ['for', n.init?.type === 'VariableDeclaration' ? statement(n.init) : expression(n.init),
            expression(n.test), expression(n.update), statement(n.body)];
        case 'ForOfStatement': return ['each', pattern(n.left.declarations[0].id), expression(n.right), statement(n.body)];
        case 'BreakStatement': return ['break'];
        case 'ContinueStatement': return ['continue'];
        case 'EmptyStatement': return ['q'];
        default: throw new Error(`Unsupported geometry statement ${n.type}`);
    }
};
function load(relative) {
    relative = relative.replaceAll('\\', '/');
    if (loaded.has(relative)) return loaded.get(relative);
    const source = stripTypeScriptTypes(fs.readFileSync(path.join(root, 'src/components/visualizer/lumiere', relative), 'utf8'));
    const ast = parse(source, { ecmaVersion: 'latest', sourceType: 'module' });
    const imports = [], code = [], exports = [];
    for (let n of ast.body) {
        if (n.type === 'ImportDeclaration') {
            const dependency = path.posix.normalize(path.posix.join(path.posix.dirname(relative), `${n.source.value}.ts`));
            const id = load(dependency);
            for (const p of n.specifiers) imports.push([p.local.name, id, p.imported.name]);
            continue;
        }
        const exported = n.type === 'ExportNamedDeclaration';
        if (exported && !n.declaration) {
            for (const specifier of n.specifiers) {
                exports.push(specifier.exported.name);
                code.push(['d', specifier.exported.name, ['v', specifier.local.name]]);
            }
            continue;
        }
        if (exported) n = n.declaration;
        if (!n) continue;
        if (relative === 'catalog.ts' && (n.type !== 'VariableDeclaration'
                || !n.declarations.some(d => d.id.name === 'LUMIERE_PROFILES'))) continue;
        if (n.type === 'VariableDeclaration') {
            n.declarations = n.declarations.filter(d => !['lumiereScaleMask', 'LUMIERE_NEUTRAL_OFFSET'].includes(d.id.name));
            if (!n.declarations.length) continue;
            if (exported) for (const d of n.declarations) exports.push(d.id.name);
        }
        code.push(statement(n));
    }
    const index = modules.length;
    loaded.set(relative, index); modules.push({ name: relative, imports, code, exports });
    return index;
}
const entry = load('catalog.ts');
const json = JSON.stringify({ revision: '92792519ce4b72d540e69f3634aa408810c6e0f6', entry, modules });
const chunks = [];
for (let i = 0; i < json.length; i += 10000) chunks.push(JSON.stringify(json.slice(i, i + 10000)));
fs.writeFileSync(destination, `// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Generated from upstream 9279251 by compile-lumiere-geometry.mjs. Do not hand-edit.
package com.zuoqirun.lyricscompanion;

final class FoliaLumiereGeometryProgram {
    private FoliaLumiereGeometryProgram() { }
    static String source() {
        StringBuilder result = new StringBuilder(${json.length});
        for (String part : new String[]{${chunks.join(',\n                ')}}) result.append(part);
        return result.toString();
    }
}
`, 'utf8');
console.log(`Compiled ${modules.length} native recipe modules, ${json.length} characters: ${destination}`);
