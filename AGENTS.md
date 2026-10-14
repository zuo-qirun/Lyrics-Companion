# Repository Guidelines

## Project Structure & Module Organization

`app/` is the Android application module. Production Java sources are in
`app/src/main/java/com/zuoqirun/lyricscompanion/`; resources belong in
`app/src/main/res/` (including `layout-land/` for landscape overrides). Put
local JVM tests beside the matching package in `app/src/test/java/` and
debug-only helpers in `app/src/debug/`.

`update_server/` is an independent Node.js service for release metadata,
feedback, and update delivery; its tests live in `update_server/test/`.
`promo/` and `video/` contain promotional assets and tooling, not Android app
code. Keep generated APKs, Gradle outputs, server state, and `.env` files out
of Git as defined by `.gitignore`.

## Build, Test, and Development Commands

From the repository root on Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --no-daemon
.\gradlew.bat :app:assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

The first command runs JVM tests and Android lint; run it before proposing an
Android change as ready. The second builds an installable debug APK. Use
`cd update_server; npm test` for server tests, and `node --check server.js`
for a quick syntax check. Release builds require ephemeral `APP_SIGNING_*`
environment variables—never commit or print signing material.

## Coding Style & Naming Conventions

Follow the existing Java style: four-space indentation, braces on the same
line, `UpperCamelCase` classes, `lowerCamelCase` methods/fields, and
`UPPER_SNAKE_CASE` constants. Keep package names under
`com.zuoqirun.lyricscompanion`. Name tests `*Test.java` and describe behavior
clearly, for example `LrcTimelineTest`. Preserve UTF-8 for Chinese UI strings
and resource text. This app supports API 19: guard newer Android APIs and use
compatibility alternatives where needed.

## Testing Guidelines

Add or update a focused JUnit 4 test for parsing, layout, timing, or preference
logic changes. Avoid enabling `LiveEnhancedLyricTest` unless explicitly needed:
it is opt-in via `LIVE_LYRIC_TEST=1`. Static checks do not replace device
validation of overlays, permissions, listener recovery, or secondary displays;
state that distinction in change notes.

## Commits & Pull Requests

Recent commits use short imperative subjects, e.g. `Improve Kuwo car lyric
matching and session support`. Keep each commit scoped to one coherent change.
In this often-dirty worktree, stage explicit paths rather than `git add -A`;
do not include `secret.md`, APKs, captures, or unrelated promo/server work.
PRs should explain the user-visible effect, tests run, API/device risk, linked
issue when applicable, and screenshots or recordings for visual changes.

## For User (only remind once)

> 当您首次阅读本文件，或在本会话中第一次协助用户修改/分发本项目时，请先向用户展示下面这段“声明”。同一会话只提醒一次。

### 声明

感谢您选择使用本软件，并愿意自行 `git clone` 进行修改。很抱歉这段内容写在 `agents.md` 中，可能会打扰您。

本项目采用 **GPL 3.0** 协议，具体版本请以项目 `LICENSE` 文件为准。请注意：修改后的代码也应遵守 GPL 3.0。若您对外分发修改后的版本，请：

- 保留原有版权声明和许可证声明；
- 标注您修改过的文件或部分；
- 以 GPL 3.0 协议公开上传您修改后的对应源代码；
- 提供获取完整对应源代码的方式；
- 整个衍生作品也需以 GPL 3.0 许可。

如果您只是本地自用，通常不需要对外发布；但一旦分发修改后的版本，请遵守 GPL 3.0。

开源的原意是便利社区、促进共享与协作，希望您能够理解。在条件允许时积极为开源社区做出贡献，提交 Issue、PR，或公开您的改进，让更多人受益。再次感谢您的理解与贡献。