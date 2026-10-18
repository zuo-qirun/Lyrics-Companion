// SPDX-License-Identifier: AGPL-3.0-only
// Exercise unmodified createLyricEcho with a measured-glyph fake and observable Pixi containers.
// Usage: node lumiere-echo-oracle.mjs <upstream> [output-json]
import fs from 'node:fs';
import path from 'node:path';
import { registerHooks } from 'node:module';
import { pathToFileURL, fileURLToPath } from 'node:url';
registerHooks({ resolve(specifier, context, next) {
    try { return next(specifier, context); }
    catch (error) { if (specifier.startsWith('.') && !path.extname(specifier)) return next(`${specifier}.ts`, context); throw error; }
}, load(url, context, next) {
    if (url.endsWith('/lumiere/text/glyphLine.ts')) return { format: 'module', shortCircuit: true, source: `
        export function buildGlyphLine(pixi, options) {
            let x=0; const glyphs=Array.from(options.text).map(char=>{
                const width=options.fontPx*(!char.trim()?.3:/[\\p{Script=Han}]/u.test(char)?1:.55)+options.fontPx*(options.letterSpacing??0);
                const result={char,charX:x,charWidth:width,anchorX:.5,anchorY:.5,texture:{},blank:!char.trim()};x+=width;return result;
            }); return {glyphs,destroy(){}};
        }` };
    return next(url, context);
} });
const base = pathToFileURL(path.join(path.resolve(process.argv[2]), 'src/components/visualizer/lumiere/'));
const { createLyricEcho } = await import(new URL('text/lyricEcho.ts', base));
const { segmentWords } = await import(new URL('text/wordStyle.ts', base));
const { buildGlyphTimings } = await import(new URL('text/reveal.ts', base));
class Container {
    children = []; visible = true; rotation = 0; alpha = 1;
    position = { x:0, y:0, set(x,y){this.x=x;this.y=y;} };
    scale = { x:1, y:1, set(x,y=x){this.x=x;this.y=y;} };
    skew = { x:0, y:0, set(x,y){this.x=x;this.y=y;} };
    anchor = { set(){} };
    addChild(...children){this.children.push(...children);return children[0];}
    removeChildren(){const children=this.children;this.children=[];return children;}
    destroy(){}
}
class Sprite extends Container { }
const lines = ['光落在晨雾里','Until the morning comes','我们仍然 向前走'].map((fullText,i)=>({
    fullText,startTime:2+i*3,endTime:5+i*3,words:[],
}));
const seeds = ['Fixture\n微光\nFolia:lumiere-p0','Other song:lumiere-p3'];
const cases = [];
for(const [width,height] of [[800,600],[400,800]]) for(const seed of seeds){
    const font=height*.34;
    const inputs=lines.map(line=>{
        let x=0;const offsets=[0];for(const char of Array.from(line.fullText)){
            x+=font*(!char.trim()?.3:/[\p{Script=Han}]/u.test(char)?1:.55)+font*.04;offsets.push(x);
        }
        return {glyphs:Array.from(line.fullText),starts:buildGlyphTimings(line).map(g=>g.start),offsets,
            words:segmentWords(line).map(w=>[w.start,w.end])};
    });
    const echo=createLyricEcho({Container,Sprite},{width,height,lines,font:'sans',weight:500,resolution:1,seed,size:.34,opacity:1});
    const beam={ox:.21,oy:.02,dx:.6,dy:.8,halfWidth:.025,tanSpread:.12,intensity:1,length:1,softness:.6,
        streaks:0,streakFreq:0,streakPhase:0,core:.5,gobo:0,goboFreq:0,goboDuty:1,goboPhase:0};
    const frames=[];
    for(const time of [0,2.4,3.5,4.5,6.7,8,10.2,13,16]){
        echo.update({time,beams:[beam],color:0xffdd99,intensity:1});
        frames.push({time,holders:echo.view.children.map(c=>({visible:c.visible,x:c.position.x,y:c.position.y,rotation:c.rotation,
            scaleX:c.scale.x,scaleY:c.scale.y,skew:c.skew.x,glyphs:c.children.map(g=>({x:g.position.x,y:g.position.y,rotation:g.rotation}))}))});
    }
    cases.push({width,height,font,seed,inputs,beam,frames});
}
const destination=process.argv[3]||path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../app/src/test/resources/folia_lumiere_echo.json');
fs.writeFileSync(destination,JSON.stringify({revision:'9279251',cases}));
console.log(`Exported ${cases.length} original echo scenes to ${destination}`);
