# Contributing to Awn

Contributions are accepted under the repository licence (MIT plus the Awn additional conditions in `LICENSE`). By sending a pull request you agree that your change is licensed that way.

## Before you start

- Read `AGENTS.md` for the commands, conventions and the memory layout under `docs/decisions/`.
- Awn depends on no other Quran app's code. Do not copy code from Quran for Android (GPL-3.0), Natlu, `recite_quran` or azani-w-qiblati.
- Content errors (Quran text, translation, word timing) are the top-priority issue type. Use the "Content error" issue template.

## Workflow

1. Open or pick an issue, then branch from `main` as `<issue>-<slug>`, for example `42-add-bookmarks`.
2. Make one logical change per commit. Commit messages follow Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, `ci:`): imperative subject under 50 characters, body says why.
3. Run the checks below. Paste the output into the pull request if one fails for a reason you cannot fix.
4. Open a pull request to `main` with `Refs #n` or `Fixes #n`. Pull requests are squash merged.

## Checks

```
./gradlew spotlessApply
./gradlew spotlessCheck detekt lintGithubRelease testGithubReleaseUnitTest assembleGithubRelease
```

Use Temurin 21 (`JAVA_HOME`) and see `README.md` for signing properties.

## Rules for user interface changes

- Colours, sizes and spacing come from `:core:ui` theme tokens. Two tests fail the build on raw `.dp`, `.sp` or `Color(0x...)` values outside the theme.
- Visible text lives in `strings.xml` (English and Bangla). UI text is short and written from the user's point of view. The copy gate test lists words that must not appear.
- Never claim that the app verifies recitation or replaces a teacher.
