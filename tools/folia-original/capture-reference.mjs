// Development-only reference capture. Runs the pinned upstream components, never packaged in the APK.
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import fs from 'node:fs/promises';
import path from 'node:path';

const [upstream, destination, mode = 'claddagh', time = '6.7', width = '800', height = '600', playback = '0', gap = '0'] = process.argv.slice(2);
if (!upstream || !destination) throw new Error('Usage: node capture-reference.mjs UPSTREAM OUTPUT [mode time width height playback gap]');
const require = createRequire(pathToFileURL(path.join(upstream, 'package.json')));
const { chromium } = require('playwright');
await fs.mkdir(destination, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true, args: ['--use-angle=swiftshader', '--enable-webgl'] });
try {
    const page = await browser.newPage({ viewport: { width: Number(width), height: Number(height) }, deviceScaleFactor: 1 });
    const errors = [];
    page.on('pageerror', error => errors.push(String(error)));
    await page.goto(`http://127.0.0.1:6170/folia-reference.html?mode=${encodeURIComponent(mode)}&time=${Number(time)}&gap=${gap === '1' ? 1 : 0}`, { waitUntil: 'networkidle' });
    await page.waitForFunction(() => Boolean(window.foliaReference));
    await page.evaluate(() => document.fonts.ready);
    if (playback === '1') {
        // Clock-driven modes never leave their pre-entry state when currentTime stops
        // updating, so replay the timeline in real time before capturing the frame.
        await page.waitForTimeout(400);
        await page.evaluate(async (target) => { await window.foliaReference.playTo(target); }, Number(time));
        await page.waitForTimeout(120);
    } else {
        await page.waitForTimeout(2500);
    }
    const prefix = `${mode}-${Math.round(Number(time) * 1000)}-${width}x${height}`;
    await page.screenshot({ path: path.join(destination, `${prefix}.png`) });
    const spans = await page.locator('span').evaluateAll(elements => elements.map(element => {
        const style = getComputedStyle(element);
        const bounds = element.getBoundingClientRect();
        return {
            text: element.textContent, transform: element.style.transform, opacity: style.opacity,
            color: style.color, font: style.font, shadow: style.textShadow, filter: style.filter,
            bounds: { x: bounds.x, y: bounds.y, width: bounds.width, height: bounds.height },
        };
    }));
    await fs.writeFile(path.join(destination, `${prefix}.json`), JSON.stringify({ mode, time, width, height, errors, spans }, null, 2));
    const boxes = await page.locator('div').evaluateAll(elements => elements.filter(e => !e.childElementCount && e.textContent.trim()).map(element => {
        const style = getComputedStyle(element), b = element.getBoundingClientRect();
        return { text: element.textContent, className: element.className, font: style.font, opacity: style.opacity,
            bounds: { x: b.x, y: b.y, width: b.width, height: b.height } };
    }));
    await fs.writeFile(path.join(destination, `${prefix}-boxes.json`), JSON.stringify(boxes, null, 2));
    if (mode === 'claddagh') {
        const spacing = await page.evaluate(async () => {
            const { buildMeasuredSpacingInfo } = await import('/src/components/visualizer/claddagh/claddaghLayout.ts');
            const { buildLineGraphemeTimeline } = await import('/src/utils/lyrics/graphemeTiming.ts');
            const { adjustCladdaghTimeline } = await import('/src/components/visualizer/claddagh/claddaghTimeline.ts');
            const font = getComputedStyle(document.querySelector('span'));
            const spec = `${font.fontWeight} ${font.fontSize} ${font.fontFamily}`;
            const context = document.createElement('canvas').getContext('2d');
            context.font = spec;
            return window.foliaReference.lines.map(line => {
                const items = adjustCladdaghTimeline(buildLineGraphemeTimeline(line), line);
                const prefixes = [0, ...items.map((_, i) => context.measureText(items.slice(0, i + 1).map(x => x.char).join('')).width)];
                return { text: line.fullText, prefixes,
                    values: buildMeasuredSpacingInfo(items, spec, 72, Math.min(innerWidth * .44, 560), 1, 0) };
            });
        });
        await fs.writeFile(path.join(destination, `${prefix}-spacing.json`), JSON.stringify(spacing, null, 2));
    }
    if (errors.length) throw new Error(errors.join('\n'));
    process.stdout.write(JSON.stringify({ image: path.join(destination, `${prefix}.png`), spans: spans.length }) + '\n');
} finally {
    await browser.close();
}
