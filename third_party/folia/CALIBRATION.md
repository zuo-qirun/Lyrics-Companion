# Folia source calibration (2026-10-13 beta)

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

## Remaining differences

All fourteen modes were inspected at 6.7 seconds. The eight typography/chat/clock/
portrait modes also have reference captures around a line entry (3.05 seconds) and
later in the line (8.5 seconds). These samples cover a single synthetic lyric fixture.
They do not cover every language, chorus, long song, dynamic theme, audio response,
manual font, physical display, car ROM or weak-device performance.

Sonnet, Tempera, Lumiere and Diorama remain native approximations: full procedural
shot programs, all rigs/formations and shaders/postprocessing are not ported.
Fume's paper geometry is adapted, but its camera bridges, overview and decor differ.
Cappella does not implement upstream emotion-image messages or TTML agent identities.
Monet lacks album metadata, custom portrait controls and floating decor. Pendolo's
geometry is ported from Canvas2D, but its RAF-based mechanical simulation and theme
icons differ. ICU segmentation on Android 24+ differs from upstream Intl.Segmenter;
Android 19-23 use supplied lyric words. Blur and font rasterization remain platform
specific. No pixel-identity or upstream-relative performance claim is made.
