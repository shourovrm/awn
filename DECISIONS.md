# Decisions index

## Topics
- docs/decisions/build.md: toolchain, signing, version codes, fetch tasks, lint and test setup.
- docs/decisions/architecture.md: modules, contracts between them, theme and notice.
- docs/decisions/data.md: :core:data packs, repositories, user database.
- docs/decisions/read.md: Read screens, settings and page rendering.
- docs/decisions/player.md: audio player, downloads and the Listen screens.
- docs/decisions/recite.md: Recite screen, session and review sheet.
- docs/decisions/learn.md: progress, FSRS, lessons and snippets.
- docs/decisions/tracker.md: alignment tracker, thresholds and measurements.
- docs/decisions/ops.md: manual machine and release steps with dates and commands.
- docs/decisions/LOG.md: one line per decision, newest last (`tail -20`).
- docs/plan.md: milestones; docs/design-system.md: tokens and gates.

## Next
- Maintainer: back up ~/.gradle/awn-release.jks and its passwords offline; add the four secrets in docs/decisions/ops.md to GitHub.
- Maintainer: create the models-v1 and data-v1 releases (fetchModels and fetchBundledPacks download from them).
- Phone test of the wired app: open items in recite.md, player.md and architecture.md (fonts, speech start-up cost).
- Bundle the Roundo and Noto Serif Bengali fonts; the theme uses the platform sans-serif until then.

## Gotchas (cross-cutting)
- Every Gradle call needs JAVA_HOME=~/.gradle/jdks/temurin-21; the system JDK is corrupt.
- compileSdk is 37 because current AndroidX needs it; targetSdk stays 36.
- Library variant tasks are release-only; root lintGithubRelease and testGithubReleaseUnitTest fan out to them.
- sherpa-onnx Kotlin classes come from the AAR, the Linux jar holds only .so files.
- R8 on :app needs a 2 GB Gradle daemon; the user-level 1 GB cap makes assembleGithubRelease run out of memory (see build.md).
