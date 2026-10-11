# Folia native Canvas adaptations

Upstream: https://github.com/chthollyphile/folia-major

Reference revision: `92792519ce4b72d540e69f3634aa408810c6e0f6` (Folia 0.7.17).
Copyright: chthollyphile and Folia contributors.
License: GNU Affero General Public License version 3, reproduced in `LICENSE` and
packaged in the APK at `assets/licenses/Folia-AGPL-3.0.txt`.

The Android files `FoliaRingMath.java`, `FoliaGlyphTimeline.java` and
`FoliaCanvasRenderer.java` are native adaptations licensed under AGPL-3.0-only.
The containing application's GPLv3 license allows combination with AGPLv3 code
under section 13. The source for these adaptations is distributed in this repository.

The Claddagh adaptation follows `claddaghLayout.ts`, `claddaghTimeline.ts`,
`CladdaghRingLine.tsx`, `claddaghGlow.ts` and `VisualizerCladdagh.tsx`:
72px base text, 0.22em total tracking, 4.25rad arc cap, 0.09 minor radius,
0.28 word following, 0.52 orbit following, 340ms pre-entry, depth/focus/opacity
curves and the 55/14/0.9 spring are preserved. Pendolo uses the original
100-degree arc, 9-line wheel, 0.42 radius and distance fade from `pendoloGeometry.ts`.

Other modes implement native Canvas interpretations of Folia's typography,
article camera, staggered rows, conversation, portrait, light field and scene
compositions. They do not execute Folia's WebGL shaders, Pixi scene programs,
Three.js geometry, semantic segmentation or postprocessing. In particular,
Sonnet, Tempera, Lumiere and Diorama are native interpretations, not equivalent
ports of all of their procedural programs. Canvas text rasterization, blur,
fonts, audio normalization, glow and random layouts differ. No pixel identity
or performance improvement over the upstream renderer is claimed.

This scope follows the maintainer's choice of native Canvas with accepted pixel
differences. The original 14 mode names and ids are retained as effect choices
inside one Folia style, with main/secondary display settings. These differences
must also be disclosed in the beta release notes.
