# Folia native Canvas adaptations

Upstream: https://github.com/chthollyphile/folia-major

Reference revision: `92792519ce4b72d540e69f3634aa408810c6e0f6` (Folia 0.7.17).
Copyright: chthollyphile and Folia contributors.
License: GNU Affero General Public License version 3, reproduced in `LICENSE` and
packaged in the APK at `assets/licenses/Folia-AGPL-3.0.txt`.

The Android files `FoliaRingMath.java`, `FoliaGlyphTimeline.java` and
`FoliaCanvasRenderer.java`, `FoliaSceneMath.java`, `FoliaCadenzaLayout.java`,
`FoliaArticleLayout.java` and `FoliaClockworkRenderer.java` are native adaptations licensed under AGPL-3.0-only.
The containing application's GPLv3 license allows combination with AGPLv3 code
under section 13. The source for these adaptations is distributed in this repository.

The Claddagh adaptation follows `claddaghLayout.ts`, `claddaghTimeline.ts`,
`CladdaghRingLine.tsx`, `claddaghGlow.ts` and `VisualizerCladdagh.tsx`:
72px base text, 0.22em total tracking, 4.25rad arc cap, 0.09 minor radius,
0.28 word following, 0.52 orbit following, 340ms pre-entry, depth/focus/opacity
curves and the 55/14/0.9 spring are preserved. Pendolo uses the original
100-degree arc, 9-line wheel, 0.42 radius and distance fade from `pendoloGeometry.ts`.

The October 2026 source-calibration pass additionally follows:
- `classic/Visualizer.tsx`, `GlowWord.tsx`, `glowWordVariants.ts`: peak-scale spacing,
  word springs, reveal timing and distinct body/glow layers.
- `partitaLayout.ts`, `PartitaChunk.tsx`: two-pass seeded uneven chunks, per-word
  timing/scale, stagger, CSS font clamps and full-width guide lines.
- `cadenzaPreparedState.ts`, `cadenzaPlacements.ts`, `cadenzaEnvelopes.ts`: measured
  wrapping, hero emphasis, bounded collision search and passed-word drift. Tests
  compare exported upstream coordinates using the same measured advances.
- `fumeArticleLayout.ts`, `fumeTextMeasure.ts`, `fumeCamera.ts`: seeded paper order,
  column/density search, font fitting and the focus/scale formula. Native font
  measurement, simplified camera retargeting and missing article decor still differ.
- `VisualizerTilt.tsx`, `VisualizerStill.tsx`, Cappella sender/bubble modules and
  Monet's shell: row choice, typography, subtitles, message senders/avatars and cover frame.
- `pendoloClockworkDraw2d.ts`, `pendoloClockworkScene.ts`: native mechanical assembly,
  including teeth, spoked wheels, planetary gears, hairspring, engraving and focal arrow.
  Its playback-clock phase and seconds gear do not reproduce the upstream RAF simulation.
- Sonnet role/flow formulas, Lumiere's neutral window/camera formulas and Diorama's
  projection constants guide the native approximations.

## 还原版 folia (restored renderers, October 2026)

Two Android display styles now share the Folia mode ids: 低配版folia (`folia`, the
adaptation described above, unchanged) and 还原版folia (`folia_original`). The
restored renderers are native Canvas ports, not a WebView wrapping the web player.
They are licensed under AGPL-3.0-only like the rest of this directory.

Data exported from the upstream TypeScript sources at the same revision and stored
under `app/src/main/assets/folia_original_canvas/` drives the restored scenes:
all 121 Tempera compositions (`profiles`, paragraph plans, drawings and motion
vocabulary) and 100 Sonnet geometric graphic programs plus the editorial-column,
mask-reveal, poster-blocks, quiet-tableau and tracking-ribbon layouts with their
manifests. The upstream Tempera source snapshot is additionally archived in
`tempera-source-9279251.zip` for audit. Native renderers include
`FoliaOriginalCanvasRenderer`, `FoliaOriginalTemperaRenderer`,
`FoliaOriginalSonnetProgram/Graphics/Layout/Motion/Renderer`,
`FoliaOriginalDioramaRenderer`, `FoliaOriginalLumiereRenderer`,
`FoliaOriginalClockworkMotion/Renderer`, `FoliaOriginalVectorScene`,
`FoliaTemperaMotion`, `FoliaCameraMotion`, `FoliaDioramaMath`,
`FoliaDioramaSurface`, `FoliaLumiereMath/Field/Profiles/Shader` and
`FoliaGpuText`.

Rendering differences from upstream that remain: upstream WebGL/Three.js/Pixi
shaders and postprocessing are not executed; the Lumiere light field runs as an
AGSL `RuntimeShader` on Android 13+ hardware canvases and as a bounded native
gradient raster with native beams/glare below that; glyph glow/blur uses
`RenderNode` plus `RenderEffect` on Android 12+ and a bounded mask cache below
that; Tempera's 5-tap partially transparent stroke shader is approximated by
layered native strokes (documented in `FoliaOriginalTemperaRenderer`); fonts,
ICU word segmentation, audio normalization and random layout seeds remain
platform specific. No pixel identity with the web renderer and no
real-device performance claim is made; the video/emulator captures in the beta
notes are the only measured motion evidence.

The original 14 mode names and ids remain effect choices inside both styles, with
main/secondary display settings. These differences must also be disclosed in the
beta release notes.
