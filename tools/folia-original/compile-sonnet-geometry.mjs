// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Compile the pinned, authored Sonnet recipes to a finite arithmetic/path IR.
// Android evaluates the recipes with the current seed and viewport, never JavaScript.
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const upstream = path.resolve(process.argv[2] || '');
if (!process.argv[2]) throw new Error('Usage: node compile-sonnet-geometry.mjs <pinned Folia checkout>');
const revision = '92792519ce4b72d540e69f3634aa408810c6e0f6';
if (execFileSync('git', ['-C', upstream, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim() !== revision) throw new Error('Unexpected upstream revision');
const ts = createRequire(path.join(process.argv[3] ? path.resolve(process.argv[3]) : upstream, 'package.json'))('typescript');
if (!ts.ScriptTarget) throw new Error('A TypeScript 5.x compiler API is required (optional third argument: compiler package directory).');
const sourceRoot = path.join(upstream, 'src/components/visualizer/sonnet');
const modules = new Map();
const moduleId = file => path.relative(sourceRoot, file).replaceAll('\\', '/').replace(/\.ts$/, '');
let current;
const fail = n => { throw new Error(`${current}:${n.getSourceFile().getLineAndCharacterOfPosition(n.pos).line + 1}: unsupported ${ts.SyntaxKind[n.kind]} ${n.getText().slice(0, 100)}`); };
const name = n => ts.isIdentifier(n) || ts.isStringLiteral(n) || ts.isNumericLiteral(n) ? n.text : fail(n);
const binding = n => {
    if (ts.isIdentifier(n)) return n.text;
    if (ts.isArrayBindingPattern(n)) return ['array', ...n.elements.map(e => ts.isOmittedExpression(e) ? null : binding(e.name))];
    if (ts.isObjectBindingPattern(n)) return ['object', ...n.elements.map(e => [name(e.propertyName || e.name), binding(e.name)])];
    return fail(n);
};
const fun = n => ['fn', n.parameters.map(p => [binding(p.name), p.initializer ? expr(p.initializer) : null]), ts.isBlock(n.body) ? stmt(n.body) : ['return', expr(n.body)]];
const expr = n => {
    if (!n) return null;
    if (ts.isParenthesizedExpression(n) || ts.isAsExpression(n) || ts.isTypeAssertionExpression(n) || ts.isNonNullExpression(n)) return expr(n.expression);
    if (ts.isNumericLiteral(n)) return Number(n.text);
    if (ts.isStringLiteral(n) || ts.isNoSubstitutionTemplateLiteral(n)) return ['str', n.text];
    if (ts.isRegularExpressionLiteral(n)) { const end = n.text.lastIndexOf('/'); return ['regex', n.text.slice(1, end), n.text.slice(end + 1)]; }
    if (ts.isIdentifier(n)) return ['get', n.text];
    if (n.kind === ts.SyntaxKind.TrueKeyword) return true;
    if (n.kind === ts.SyntaxKind.FalseKeyword) return false;
    if (n.kind === ts.SyntaxKind.NullKeyword) return null;
    if (ts.isPropertyAccessExpression(n)) return ['prop', expr(n.expression), ['str', name(n.name)]];
    if (ts.isElementAccessExpression(n)) return ['prop', expr(n.expression), expr(n.argumentExpression)];
    if (ts.isCallExpression(n)) return ['call', expr(n.expression), ...n.arguments.map(expr)];
    if (ts.isNewExpression(n)) return ['call', expr(n.expression), ...(n.arguments || []).map(expr)];
    if (ts.isArrowFunction(n) || ts.isFunctionExpression(n)) return fun(n);
    if (ts.isBinaryExpression(n)) return ['op', n.operatorToken.getText(), expr(n.left), expr(n.right)];
    if (ts.isPrefixUnaryExpression(n) || ts.isPostfixUnaryExpression(n)) return ['unary', ts.tokenToString(n.operator), expr(n.operand), ts.isPostfixUnaryExpression(n)];
    if (ts.isConditionalExpression(n)) return ['if', expr(n.condition), expr(n.whenTrue), expr(n.whenFalse)];
    if (ts.isArrayLiteralExpression(n)) return ['list', ...n.elements.map(expr)];
    if (ts.isSpreadElement(n)) return ['spread', expr(n.expression)];
    if (ts.isObjectLiteralExpression(n)) return ['obj', ...n.properties.map(p => {
        if (ts.isPropertyAssignment(p)) return [name(p.name), expr(p.initializer)];
        if (ts.isShorthandPropertyAssignment(p)) return [name(p.name), ['get', name(p.name)]];
        if (ts.isSpreadAssignment(p)) return ['...', expr(p.expression)];
        return fail(p);
    })];
    if (ts.isTemplateExpression(n)) return ['template', n.head.text, ...n.templateSpans.flatMap(p => [expr(p.expression), p.literal.text])];
    if (ts.isTypeOfExpression(n)) return ['unary', 'typeof', expr(n.expression), false];
    return fail(n);
};
const variable = n => ['let', ...n.declarations.map(d => [binding(d.name), expr(d.initializer)])];
const stmt = n => {
    if (ts.isBlock(n)) return ['block', ...n.statements.map(stmt).filter(Boolean)];
    if (ts.isVariableStatement(n)) return variable(n.declarationList);
    if (ts.isExpressionStatement(n)) {
        // The native player owns time/audio updates; this compilation only builds geometry.
        if (ts.isBinaryExpression(n.expression) && ts.isPropertyAccessExpression(n.expression.left)
            && n.expression.left.name.text === 'updateTime') return null;
        return ['do', expr(n.expression)];
    }
    if (ts.isIfStatement(n)) return ['if', expr(n.expression), stmt(n.thenStatement), n.elseStatement ? stmt(n.elseStatement) : null];
    if (ts.isForStatement(n)) return ['for', n.initializer ? ts.isVariableDeclarationList(n.initializer) ? variable(n.initializer) : ['do', expr(n.initializer)] : null,
        expr(n.condition), expr(n.incrementor), stmt(n.statement)];
    if (ts.isForOfStatement(n)) return ['each', binding(n.initializer.declarations[0].name), expr(n.expression), stmt(n.statement)];
    if (ts.isReturnStatement(n)) return ['return', expr(n.expression)];
    if (ts.isBreakStatement(n)) return ['break'];
    if (ts.isContinueStatement(n)) return ['continue'];
    if (ts.isThrowStatement(n)) return ['throw', ['str', n.expression.getText()]];
    if (ts.isSwitchStatement(n)) return ['switch', expr(n.expression), ...n.caseBlock.clauses.map(c => [ts.isCaseClause(c) ? expr(c.expression) : null, ['block', ...c.statements.map(stmt)]])];
    if (ts.isFunctionDeclaration(n)) return ['let', [name(n.name), fun(n)]];
    if (ts.isEmptyStatement(n)) return null;
    if (ts.isTypeAliasDeclaration(n) || ts.isInterfaceDeclaration(n)) return null;
    return fail(n);
};
function load(file) {
    const id = moduleId(file);
    if (modules.has(id)) return id;
    modules.set(id, null);
    const source = ts.createSourceFile(file, fs.readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true);
    const imports = [], exports = [], code = [];
    for (const node of source.statements) {
        current = id;
        if (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) {
            if (id === 'sonnetIcons') continue;
            if (!node.moduleSpecifier || node.importClause?.isTypeOnly) continue;
            const specifier = node.moduleSpecifier.text;
            if (specifier === '@chenglou/pretext') continue;
            if (!specifier.startsWith('.')) {
                if (id === 'sonnetIcons') continue;
                fail(node);
            }
            const imported = moduleId(path.resolve(path.dirname(file), `${specifier}.ts`));
            const names = (node.importClause?.namedBindings || node.exportClause)?.elements;
            if (!names) continue;
            if (specifier.endsWith('/fontStacks')) {
                imports.push(['normalizeFontWeight', '@', 'normalizeFontWeight']);
            } else if (imported === 'sonnetAnimatedGraphics') {
                imports.push(...names.map(s => [s.name.text, '@', 'AnimatedGraphics']));
            } else {
                load(path.resolve(path.dirname(file), `${specifier}.ts`));
                for (const s of names) if (!s.isTypeOnly) {
                    imports.push([s.name.text, imported, s.propertyName?.text || s.name.text]);
                    if (ts.isExportDeclaration(node)) exports.push(s.name.text);
                }
            }
            continue;
        }
        if (id === 'sonnetTypographyLayout' && ts.isVariableStatement(node)) {
            const declarationName = node.declarationList.declarations[0].name.getText();
            if (declarationName === 'measureCache') continue;
            if (declarationName === 'measureText') {
                imports.push(['measureText', '@', 'measureText']);
                exports.push('measureText');
                continue;
            }
        }
        if (id === 'sonnetIcons' && ts.isVariableStatement(node)) {
            const selected = ['buildSonnetIconParticleIndices', 'resolveSonnetIconEntryPhase', 'resolveSonnetIconEntryDuration', 'resolveSonnetIconEntryDelay'];
            if (!selected.includes(node.declarationList.declarations[0].name.getText())) continue;
        }
        const compiled = stmt(node);
        if (compiled) code.push(compiled);
        if (node.modifiers?.some(m => m.kind === ts.SyntaxKind.ExportKeyword)) {
            if (ts.isVariableStatement(node)) exports.push(...node.declarationList.declarations.map(d => name(d.name)));
            else if (ts.isFunctionDeclaration(node)) exports.push(name(node.name));
        }
    }
    modules.set(id, { imports, exports, code: ['block', ...code] });
    return id;
}
load(path.join(sourceRoot, 'sonnetShotMg.ts'));
load(path.join(sourceRoot, 'sonnetTypographyLayout.ts'));
load(path.join(sourceRoot, 'sonnetTextFixedGeo.ts'));
const destination = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../app/src/main/assets/folia_original_canvas/sonnet/geometry-program.json');
const output = { format: 'folia-sonnet-geometry-ir-1', revision, entry: ['sonnetShotMg', 'buildSonnetShotMg'], modules: Object.fromEntries(modules) };
fs.writeFileSync(destination, JSON.stringify(output) + '\n');
console.log(`Compiled ${modules.size} source modules to ${fs.statSync(destination).size} bytes of geometry formulas: ${destination}`);
