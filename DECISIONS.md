# Decisions index

## Topics
- docs/decisions/build.md: toolchain, signing, version codes, fetch tasks, lint and test setup.
- docs/decisions/architecture.md: modules, contracts between them, theme and notice.
- docs/decisions/ops.md: manual machine and release steps with dates and commands.
- docs/decisions/LOG.md: one line per decision, newest last (`tail -20`).
- docs/plan.md: milestones; docs/design-system.md: tokens and gates.

## Next
- Maintainer: back up ~/.gradle/awn-release.jks and its passwords offline; add the four secrets in docs/decisions/ops.md to GitHub.
- Maintainer: create the models-v1 release with the files in awn-models/ (fetchModels downloads from it).
- Run the emulator install, notice flow and Settings theme row check (not done by the skeleton agent).
- Bundle the Roundo font; the theme uses the platform sans-serif until then.
- Milestone 2 (read) is next.

## Gotchas (cross-cutting)
- Every Gradle call needs JAVA_HOME=~/.gradle/jdks/temurin-21; the system JDK is corrupt.
- compileSdk is 37 because current AndroidX needs it; targetSdk stays 36.
- Library variant tasks are release-only; root lintGithubRelease and testGithubReleaseUnitTest fan out to them.
- sherpa-onnx Kotlin classes come from the AAR, the Linux jar holds only .so files.
