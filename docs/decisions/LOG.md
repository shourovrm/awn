2026-10-20 | compileSdk 37, targetSdk 36 | current Compose, Navigation and core-ktx require compileSdk 37; Play needs target 36
2026-10-20 | Convention plugins in buildSrc, not build-logic | smallest setup for ten modules
2026-10-20 | Root lintGithubRelease and testGithubReleaseUnitTest aggregate library tasks | flavored tasks exist only in :app, libraries would be skipped
2026-10-20 | Library and app testBuildType release | no debug build is used anywhere
2026-10-20 | sherpa Kotlin classes taken from the AAR classes.jar for the JVM | the Linux jar contains only .so files
2026-10-20 | fetchModels writes into awn-models/ in place | tests read the same folder, no copy needed
2026-10-20 | Repo slug shourovrm/awn used in the models-v1 URL | taken from the gh login; change in build.gradle.kts if the repo lives elsewhere
2026-10-20 | CI signs with a per-run throwaway key | fork PRs get no secrets but the build must be signed
2026-10-20 | Tracker keyed by reference word index, not WordKey | :tracker has no Android dependency, WordKey is in an Android library
2026-10-20 | Notice shows English, Bangla and Arabic together | the licence wants all three; Arabic is always required
2026-10-20 | Theme setting Light, Dark, System, default System, stored in DataStore | maintainer request; Dark only changes the Read field
2026-10-20 | detekt MagicNumber, LongParameterList and MatchingDeclarationName relaxed for theme and Composables | token files and Compose signatures are number and parameter tables by nature
2026-10-20 | Dock glyphs drawn on Canvas | avoids the material-icons dependency
