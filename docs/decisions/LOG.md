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
2026-10-20 | Recite stores the tracker's DurationCheck and Substitution events, local copies deleted | the tracker now provides them; one shape per fact
2026-10-20 | A red (Wrong) recite word counts as passed, and as a mistake in hifz | matches Tracker.currentIndex; a clean hifz rating needs no red words
2026-10-20 | Recite scrolls to the current word with BringIntoViewRequester | one modifier on the current word, no layout bookkeeping
2026-10-20 | Added AwnTypography.caption (12 sp) | design system lists 12 sp for word-by-word meanings
2026-10-20 | Platform fonts stay; Roundo and Noto Serif Bengali not bundled | font files and licences are not in the repo yet
2026-10-20 | Third-party coordinates moved to the version catalog, UseTomlInstead lint on again | one place for versions
2026-10-20 | Hand-written AwnServices in Application.onCreate and one ViewModelProvider.Factory | no DI framework for ten modules
2026-10-20 | Speech files copied from awn-models into app assets by copySpeechFiles, to filesDir on first use | sherpa-onnx opens the model by path
2026-10-20 | Reference phonemes parsed lazily and read off the main thread | the 12.8 MB JSON would block start-up and the UI
2026-10-20 | POST_NOTIFICATIONS requested when audio first plays | the media notification needs it on Android 13 and later
2026-10-20 | Language row opens the system per-app language picker | no in-app locale switching needed; generateLocaleConfig lists en and bn
2026-10-20 | Learn screen hosts Revision, Progress and lessons itself, no separate app routes | LearnScreen(quranText) already routes between them
2026-10-20 | Exit check runs with -Dorg.gradle.jvmargs=-Xmx2g | R8 runs out of memory in the 1 GB user-level daemon

