# Folia source calibration (2026-10-17 beta)

Source reference: [Folia 9279251](https://github.com/chthollyphile/folia-major/tree/92792519ce4b72d540e69f3634aa408810c6e0f6).
The main renderer is Android Canvas; the reference renderer was run separately in Chromium.

## Fixture

The debug-only `FoliaRenderFixtureActivity` exports 800 x 600 software Canvas PNGs
into `cache/folia-render-fixture`. It is DUMP-protected and absent from release builds.
The fixed fixture has six lines at 3-second intervals, individually timed graphemes,
one translation, a generated gradient cover, no audio, font scale 1, strength 50,
primary #f5f8ff, accent #ff984b and background #09090b. `--el time` is milliseconds.

```powershell
adb -s emulator-5556 shell am start -S -n com.zuoqirun.lyricscompanion/.FoliaRenderFixtureActivity --el time 6700 --ez verifyPause true
adb -s emulator-5556 shell am start -S -n com.zuoqirun.lyricscompanion/.FoliaRenderFixtureActivity --el time 8500
adb -s emulator-5556 shell am start -S -n com.zuoqirun.lyricscompanion/.FoliaRenderFixtureActivity --el time 3050
adb -s emulator-5556 shell am start -S -n com.zuoqirun.lyricscompanion/.FoliaRenderFixtureActivity --el time 6700 --ei fps 0 --ez verifyPause true
adb -s emulator-5556 shell am start -S -n com.zuoqirun.lyricscompanion/.FoliaRenderFixtureActivity --el time 6700 --ez translations false
adb -s emulator-5556 logcat -d -s FoliaFixture:I AndroidRuntime:E
```

`mode`, `width`, `height`, `fps`, `translations` and `verifyPause` are optional extras.
The pause check redraws with the same lyric time and a monotonic clock advanced by
five seconds, comparing every pixel. It found and fixed a retained Paint alpha bug
in the native light-field shader. It verifies a paused raster, not frame pacing.

Reference captures used the same fixture in the unmodified upstream visualizer
components, default per-mode tuning, zero audio and hidden player chrome. Android
Roboto / Noto Sans CJK fonts were supplied to Chromium and awaited before mount.
MotionValues were updated at the fixed time and the mounted scene settled for
2.5 seconds. This is a snapshot comparison: upstream Framer Motion / RAF state
and native playback-clock envelopes have different histories. It is not proof of
frame-by-frame equality. The fixed seed also does not reproduce every track's shot
selection. Generated images and the external reference checkout are not shipped.

## Source-aligned algorithms

`FoliaSceneMathTest` checks the upstream exported Partita chunk lengths and random
stagger/scale values, font clamps, springs and Cadenza glow envelope.
`FoliaCadenzaLayoutTest` checks upstream exported placement coordinates with identical
measured advances, plus words fragmented across wrapped lines. This separates pure
placement fidelity from Android/Chromium font measurement differences.
`FoliaArticleLayoutTest` checks seeded article identity and repeatability.

## 还原版 folia validation (2026-10-17)

`FoliaRenderFixtureActivity` also exercises the restored renderers with
`--es renderer original --es mode <mode>`; `--ez hardware true` draws the live
hardware-accelerated view and logs `FoliaFixture: Hardware Canvas=true`.
Software PNGs cover all 14 modes at 1.28x and 720p; the hardware path was checked
on API 31 and all 14 modes reported `Canvas=true` with no exception. The
`verifyPause` check now covers all 14 restored modes and logged
`Paused frame stable: <mode>` for each, so the paused raster is deterministic.

The restored renderers were developed against the exported upstream data
(121 Tempera compositions, 100 Sonnet graphic programs) and the GPU paths
described in `NOTICE.md`. Emulator-only frame-pacing numbers were collected with
`dumpsys gfxinfo` (API 31, SwiftShader software GPU, hardware canvas, 800x600,
60 fps request): the restored renderer is heavier than 低配版, most visibly for
Claddagh, Diorama and Fume where layered blur/glare runs on the software GPU;
Lumiere's AGSL path is unavailable below Android 13 and falls back to the
bounded native raster. These numbers are not real-device GPU evidence and are
not a claim of upstream-relative performance.

Still unverified for 还原版: physical car head units, secondary displays,
weak-device thermals, Android 13+ AGSL hardware devices and long playback
sessions. API 19/API 28 runtime smoke tests are separate emulator checks.

## Remaining differences

All fourteen modes were inspected at 6.7 seconds. The eight typography/chat/clock/
portrait modes also have reference captures around a line entry (3.05 seconds) and
later in the line (8.5 seconds). These samples cover a single synthetic lyric fixture.
They do not cover every language, chorus, long song, dynamic theme, audio response,
manual font, physical display, car ROM or weak-device performance.

In 低配版folia, Sonnet, Tempera, Lumiere and Diorama remain native approximations:
full procedural shot programs, all rigs/formations and shaders/postprocessing are
not ported. In 还原版folia the exported Tempera/Sonnet compositions and shot
layouts are played natively, but upstream shaders are approximated (AGSL light
field on Android 13+, layered native strokes otherwise), so pixel identity is
still not claimed.
Fume's paper geometry is adapted, but its camera bridges, overview and decor differ.
Cappella does not implement upstream emotion-image messages or TTML agent identities.
Monet lacks album metadata, custom portrait controls and floating decor. Pendolo's
geometry is ported from Canvas2D, but its RAF-based mechanical simulation and theme
icons differ. ICU segmentation on Android 24+ differs from upstream Intl.Segmenter;
Android 19-23 use supplied lyric words. Blur and font rasterization remain platform
specific. No pixel-identity or upstream-relative performance claim is made.
