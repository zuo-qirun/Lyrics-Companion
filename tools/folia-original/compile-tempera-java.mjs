// SPDX-License-Identifier: AGPL-3.0-only
// Development-only TypeScript -> Java formula compiler. No source or bytecode interpreter ships.
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
const require = createRequire(import.meta.url);
const ts = require(process.env.TEMPERA_TYPESCRIPT || path.join(process.env.APPDATA, 'npm/node_modules/typescript/lib/typescript.js'));
export function compileTemperaJava(upstream, output) {
    const base = path.join(upstream, 'src/components/visualizer/tempera');
    const files = ['temperaHatch.ts', 'temperaCurves.ts', 'temperaShapes.ts',
        ...fs.readdirSync(path.join(base, 'compositions')).filter(f=>f.endsWith('.ts')).map(f=>'compositions/'+f), 'temperaCompositions.ts',
        'types.ts', 'temperaShotProfiles.ts', 'temperaProgram.ts', 'temperaLayout.ts'];
    const program = ts.createProgram(files.map(f=>path.join(base,f)), {target:ts.ScriptTarget.ESNext, module:ts.ModuleKind.ESNext});
    const checker = program.getTypeChecker();
    const moduleNames = new Map(files.map((f,i)=>[path.normalize(path.join(base,f)), 'Module'+i]));
    const declarations = new Map(); let serial=0;
    const ignored = new Set(['gradientFills','buildGradientFill','createGradientFill','destroyTemperaGradientFills','toPixiColor','buildTemperaSegments']);
    function symbol(n) { return checker.getSymbolAtLocation(n); }
    function declare(name, top=false, module='') {
        if (ts.isIdentifier(name)) { const sym=symbol(name); if(!declarations.has(sym)) declarations.set(sym, top ? module+'.'+name.text : 'v'+serial++); }
        else for (const e of name.elements) if(!ts.isOmittedExpression(e)) declare(e.name,top,module);
    }
    for(const file of files) {
        const sf=program.getSourceFile(path.join(base,file)); const module=moduleNames.get(path.normalize(sf.fileName));
        function scan(n) {
            if(ts.isVariableDeclaration(n)||ts.isParameter(n)) declare(n.name,ts.isVariableDeclaration(n)&&n.parent.parent.parent===sf,module);
            ts.forEachChild(n,scan);
        }
        scan(sf);
    }
    const fail=n=>{throw new Error(`Unsupported native formula ${ts.SyntaxKind[n.kind]}: ${n.getText().slice(0,160)}`)};
    function id(n) {
        if(n.text==='undefined') return 'null';
        if(n.text==='toPixiColor') return 'COLOR';
        if(n.text==='buildGradientFill') return 'GRADIENT';
        if(n.text==='buildTemperaSegments') return 'SEGMENTS';
        if(n.text==='getLineRenderEndTime') return 'RENDER_END';
        if(n.text==='hashTemperaSeed') return 'HASH_STRING';
        if(n.text==='mixTemperaSeed') return 'MIX_SEED';
        if(n.text==='String') return 'STRING';
        if(n.text==='chooseWithoutRepeat') return 'CHOOSE';
        if(n.text==='TEMPERA_ENTER_STYLES') return 'ENTER_STYLES';
        if(n.text==='buildTemperaWordUnit') return 'BUILD_WORD';
        if(n.text==='createTemperaMeasureContext') return 'CREATE_MEASURE';
        const sym=ts.isShorthandPropertyAssignment(n.parent) ? checker.getShorthandAssignmentValueSymbol(n.parent) : symbol(n);
        if(declarations.has(sym)) return declarations.get(sym)+(declarations.get(sym).includes('.')?'':'.v');
        if(sym && (sym.flags&ts.SymbolFlags.Alias)) {
            const aliased=checker.getAliasedSymbol(sym);
            if(declarations.has(aliased)) return declarations.get(aliased);
        }
        if(n.text==='temperaHash01') return 'HASH';
        if(n.text==='resolveTemperaShotProfile') return 'PROFILE';
        if(n.text==='toPixiColor') return 'COLOR';
        if(n.text==='buildGradientFill') return 'GRADIENT';
        return fail(n);
    }
    function args(nodes) { return nodes.map(n=>ts.isSpreadElement(n)?'spread('+expr(n.expression)+')':expr(n)).join(', '); }
    function expr(n) {
        if(!n) return 'null';
        if(ts.isIdentifier(n)) return id(n);
        if(ts.isNumericLiteral(n)) return n.text+'d';
        if(ts.isStringLiteral(n)||ts.isNoSubstitutionTemplateLiteral(n)) return JSON.stringify(n.text);
        if(ts.isRegularExpressionLiteral(n))return 'regex('+JSON.stringify(n.text)+')';
        if(ts.isTemplateExpression(n)) return '('+JSON.stringify(n.head.text)+n.templateSpans.map(s=>' + string('+expr(s.expression)+') + '+JSON.stringify(s.literal.text)).join('')+')';
        if(n.kind===ts.SyntaxKind.TrueKeyword) return 'true';
        if(n.kind===ts.SyntaxKind.FalseKeyword) return 'false';
        if(n.kind===ts.SyntaxKind.NullKeyword) return 'null';
        if(ts.isParenthesizedExpression(n)||ts.isAsExpression(n)||ts.isTypeAssertionExpression(n)||ts.isNonNullExpression(n)) return expr(n.expression);
        if(ts.isArrayLiteralExpression(n)) return 'array('+args(n.elements)+')';
        if(ts.isObjectLiteralExpression(n)) return 'object('+n.properties.map(p=> {
            if(ts.isSpreadAssignment(p)) return 'spread('+expr(p.expression)+')';
            if(ts.isShorthandPropertyAssignment(p)) return JSON.stringify(p.name.text)+', '+expr(p.name);
            if(ts.isPropertyAssignment(p)) return JSON.stringify(p.name.text)+', '+expr(p.initializer);
            return fail(p);
        }).join(', ')+')';
        if(ts.isPropertyAccessExpression(n)) {
            if(ts.isIdentifier(n.expression)&&n.expression.text==='Math'&&n.name.text==='PI') return 'Math.PI';
            if(ts.isIdentifier(n.expression)&&n.expression.text==='Number') return ({POSITIVE_INFINITY:'Double.POSITIVE_INFINITY',NEGATIVE_INFINITY:'Double.NEGATIVE_INFINITY',NaN:'Double.NaN'})[n.name.text]||fail(n);
            return 'get('+expr(n.expression)+', '+JSON.stringify(n.name.text)+')';
        }
        if(ts.isElementAccessExpression(n)) return 'get('+expr(n.expression)+', '+expr(n.argumentExpression)+')';
        if(ts.isCallExpression(n)) {
            if(ts.isPropertyAccessExpression(n.expression)) {
                const member=n.expression, name=member.name.text;
                if(ts.isIdentifier(member.expression)&&member.expression.text==='Math') return 'math('+JSON.stringify(name)+(n.arguments.length?', '+args(n.arguments):'')+')';
                if(ts.isIdentifier(member.expression)&&member.expression.text==='Array'&&name==='from') return 'arrayFrom('+args(n.arguments)+')';
                if(ts.isIdentifier(member.expression)&&member.expression.text==='Number') return 'numberMethod('+JSON.stringify(name)+', '+args(n.arguments)+')';
                if(ts.isIdentifier(member.expression)&&member.expression.text==='console') return 'null';
                return 'method('+expr(member.expression)+', '+JSON.stringify(name)+(n.arguments.length?', '+args(n.arguments):'')+')';
            }
            return 'call('+expr(n.expression)+(n.arguments.length?', '+args(n.arguments):'')+')';
        }
        if(ts.isNewExpression(n) && n.expression.getText()==='pixi.Graphics') return 'node()';
        if(ts.isNewExpression(n) && n.expression.getText()==='Map') return n.arguments?.length ? 'mapFrom('+expr(n.arguments[0])+')' : 'object()';
        if(ts.isArrowFunction(n)||ts.isFunctionExpression(n)) return fn(n);
        if(ts.isConditionalExpression(n)) return '(truth('+expr(n.condition)+') ? '+expr(n.whenTrue)+' : '+expr(n.whenFalse)+')';
        if(ts.isPrefixUnaryExpression(n)) {
            const operand=expr(n.operand);
            if(n.operator===ts.SyntaxKind.ExclamationToken) return '(!truth('+operand+'))';
            if(n.operator===ts.SyntaxKind.MinusToken) return '(-number('+operand+'))';
            if(n.operator===ts.SyntaxKind.PlusToken) return 'number('+operand+')';
            return fail(n);
        }
        if(ts.isBinaryExpression(n)) {
            const op=n.operatorToken.getText(), a=expr(n.left), b=expr(n.right);
            if(op==='??') return 'fallback('+a+', '+b+')';
            if(op==='&&'||op==='||') return '(truth('+a+') '+op+' truth('+b+'))';
            if(op==='==='||op==='!=='||op==='=='||op==='!=') return (op.includes('!')?'!':'')+'equal('+a+', '+b+')';
            if(['<','>','<=','>='].includes(op)) return '(number('+a+') '+op+' number('+b+'))';
            if(op==='**') return 'Math.pow(number('+a+'), number('+b+'))';
            if(op==='=') return assign(n.left,b);
            if(['+=','-=','*=','/='].includes(op)) return assign(n.left,'(number('+a+') '+op[0]+' number('+b+'))');
            if(op==='+') return 'add('+a+', '+b+')';
            if(['-','*','/','%'].includes(op)) return '(number('+a+') '+op+' number('+b+'))';
            return fail(n);
        }
        if(ts.isPostfixUnaryExpression(n))return 'increment('+declarations.get(symbol(n.operand))+', '+(n.operator===ts.SyntaxKind.PlusPlusToken?'1':'-1')+')';
        return fail(n);
    }
    function assign(left,value) {
        if(ts.isIdentifier(left)) return '('+id(left)+' = '+value+')';
        if(ts.isPropertyAccessExpression(left)) return 'put('+expr(left.expression)+', '+JSON.stringify(left.name.text)+', '+value+')';
        if(ts.isElementAccessExpression(left)) return 'put('+expr(left.expression)+', '+expr(left.argumentExpression)+', '+value+')';
        return fail(left);
    }
    function binding(name, value) {
        if(ts.isIdentifier(name)) return 'final Cell '+declarations.get(symbol(name))+' = new Cell('+value+');\n';
        const temp='b'+serial++; let out='final Object '+temp+' = '+value+';\n';
        name.elements.forEach((e,i)=> {
            if(ts.isOmittedExpression(e)) return;
            const key=ts.isObjectBindingPattern(name)?JSON.stringify(e.propertyName?.text||e.name.text):i+'d';
            let v='get('+temp+', '+key+')'; if(e.initializer) v='fallback('+v+', '+expr(e.initializer)+')';
            out+=binding(e.name,v);
        }); return out;
    }
    function fn(n) {
        const arg='a'+serial++; let out='((Fn) ('+arg+') -> {\n';
        n.parameters.forEach((p,i)=> {let v='arg('+arg+', '+i+')';if(p.initializer)v='fallback('+v+', '+expr(p.initializer)+')';out+=binding(p.name,v);});
        if(ts.isBlock(n.body)) { out+=statements(n.body.statements); if(!terminal(n.body.statements.at(-1)))out+='return null;\n'; }
        else out+='return '+expr(n.body)+';\n';
        return out+'})';
    }
    function terminal(n) {return n&&(ts.isReturnStatement(n)||ts.isThrowStatement(n)||(ts.isIfStatement(n)&&terminal(n.thenStatement)&&terminal(n.elseStatement))||(ts.isBlock(n)&&terminal(n.statements.at(-1))));}
    function statements(list) {return list.map(statement).join('');}
    function statement(n) {
        if(ts.isVariableStatement(n))return n.declarationList.declarations.map(d=>binding(d.name,expr(d.initializer))).join('');
        if(ts.isExpressionStatement(n))return 'discard('+expr(n.expression)+');\n';
        if(ts.isReturnStatement(n))return 'return '+expr(n.expression)+';\n';
        if(ts.isBlock(n))return '{\n'+statements(n.statements)+'}\n';
        if(ts.isIfStatement(n))return 'if (truth('+expr(n.expression)+')) '+statement(n.thenStatement)+(n.elseStatement?'else '+statement(n.elseStatement):'');
        if(ts.isForStatement(n)) {
            const init=n.initializer.declarations.map(d=>binding(d.name,expr(d.initializer))).join('');
            return '{\n'+init+'for (; truth('+expr(n.condition)+'); discard('+expr(n.incrementor)+')) '+statement(n.statement)+'}\n';
        }
        if(ts.isContinueStatement(n))return 'continue;\n';
        if(ts.isBreakStatement(n))return 'break;\n';
        if(ts.isWhileStatement(n))return 'while (truth('+expr(n.expression)+')) '+statement(n.statement);
        if(ts.isSwitchStatement(n))return 'switch (String.valueOf('+expr(n.expression)+')) {\n'+n.caseBlock.clauses.map(c=>(ts.isCaseClause(c)?'case '+expr(c.expression):'default')+': {\n'+statements(c.statements)+'}\n').join('')+'}\n';
        return fail(n);
    }
    let out='// SPDX-License-Identifier: AGPL-3.0-only\n// Copyright (c) 2026 chthollyphile and Folia contributors.\n// Generated by tools/folia-original/compile-tempera-java.mjs from Folia 9279251.\n// Direct native Java geometry formulas; no script/IR interpreter or fixed seed recordings.\npackage com.zuoqirun.lyricscompanion;\n\nfinal class FoliaTemperaGeometryProgram extends FoliaTemperaGeometryOps {\n';
    out+='static Object compose(Object context) { return call('+moduleNames.get(path.normalize(path.join(base,'temperaCompositions.ts')))+'.drawTemperaComposition, context); }\n';
    out+='static Object compile(Object lines, String seed, boolean wholeLine) { return call('+moduleNames.get(path.normalize(path.join(base,'temperaProgram.ts')))+'.compileTemperaProgram, lines, seed, object("wholeLineLyrics", wholeLine)); }\n';
    out+='static Object layout(Object options) { return call('+moduleNames.get(path.normalize(path.join(base,'temperaLayout.ts')))+'.resolveTemperaLayout, options); }\n';
    for(const file of files) {
        const sf=program.getSourceFile(path.join(base,file)); const module=moduleNames.get(path.normalize(sf.fileName));
        out+='\n// Upstream '+file+'\nstatic final class '+module+' extends FoliaTemperaGeometryOps {\n';
        for(const s of sf.statements) if(ts.isVariableStatement(s))for(const d of s.declarationList.declarations) {
            if(ignored.has(d.name.text))continue;
            out+='static final Object '+d.name.text+' = '+expr(d.initializer)+';\n';
        }
        out+='}\n';
    }
    fs.writeFileSync(output,out+'}\n');
    return files.length;
}
if(process.argv[1]?.endsWith('compile-tempera-java.mjs')) console.log('Compiled modules:',compileTemperaJava(process.argv[2],process.argv[3]));
