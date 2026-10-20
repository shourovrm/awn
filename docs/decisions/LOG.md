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

2026-10-21 | Download on play wraps the player in DownloadingAudioPlayer; refused or failed downloads stream | streaming plays the same audio and always works
2026-10-21 | Download amount counts from the start ayah (page, surah, juz); later files stream | a range to the end of a surah would otherwise download it all
2026-10-21 | Word highlight clears 300 ms after a word ends | Alafasy has pauses of 4.7 s inside an ayah; the packs have no overlaps
2026-10-21 | Reciter list sizes come from AudioDownloads.usedBytes, a live flow | the size was read once at creation and showed Nothing downloaded
2026-10-21 | Reciter sheet groups are exclusive | a reciter in two groups reads as a duplicate
2026-10-21 | AudioFeature holds dependencies and the view model factory for the audio composables | :app registers no audio view models
2026-10-21 | Tracker judges nothing until 5 to 7 phonemes match the text start | stray phonemes coloured words in 55 % of seeded runs; now 0 of 300
2026-10-21 | One ReciteController, ReciteBar and rememberReciteWordStates replace the Recite screen | recite is a mode of the Quran page; topic recite
2026-10-21 | Speech files, phoneme parse and recogniser load run at app start on a low-priority thread | first start waited about 45 s; topic recite
2026-10-21 | One review button in the bar, sheet opens when empty | the sheet returned without drawing for an empty list; topic recite
2026-10-21 | Madd length measured to the next matched token | extra tokens sit inside a held vowel; 8 of 10 flagged, gate 9; tracker.md
2026-10-21 | Status-bar icon colour is set by AwnTheme from the painted colour | enableEdgeToEdge picks it once from the device night setting (defect 8)
2026-10-21 | Colour modes Awn, Light, Dark, Black; day, night and follow-device stored separately | replaces Light, Dark, System; old value migrated on read
2026-10-21 | Search is plain Kotlin over 6,236 ayahs, no inverted index | first query 1.1 s with loading, later under 100 ms on the JVM
2026-10-21 | Juz tab reads a QuarterTable interface with a 30-row stand-in | the page agent's quarter table in core/data is not merged yet
2026-10-21 | Every NavHost uses None transitions | Navigation Compose's default 700 ms fade made Back slow
2026-10-21 | Settings is a hub of seven icon rows; pickers are choice dialogs | compact list, no rows that cycle on tap
2026-10-21 | One QuranScreen with Mushaf, Translation and Word by word views cycled by one button | reading, listening and reciting must share a page
2026-10-21 | Page counts as read after 20 s on screen; recent pages update at once | pages read must not count pages merely viewed
2026-10-21 | Text size is two 8-step values (0.85 to 1.6) replacing the 3-step TextSize | popup moves both, settings page sets each
2026-10-21 | Title names the first surah starting on the page, else the continuing one | pages 77, 604 and 50 as the maintainer specified
2026-10-21 | List views show a Basmalah block before surahs with bismillahPre, not as an ayah | no chip, selection or translation
