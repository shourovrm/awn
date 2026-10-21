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
2026-10-21 | V2 (1421 print) is the default script; V1 and V4 tajweed stay page types | maintainer wants the standard printed Madani Mushaf
2026-10-21 | Pause-mark spaces removed in the Hafs fallback text | a combining mark after a space is drawn on a dotted circle (read.md)
2026-10-21 | Ayah-end sign is the page-font glyph after the last word's code | exact print; checked for every ayah, 41 V1 exceptions mapped to U+FBD3
2026-10-21 | Banner of a surah that starts on line 1 or 2 goes to the foot of the previous page | print order; the Basmalah was missing on 18 V2 surahs
2026-10-21 | Quarter table generated from Quran.com into Kotlin with a pinned checksum | Juz index and page header need it; no pack download needed
2026-10-21 | English wbw phrase repeats emptied at read time | Quran.com repeats a phrase on each word in surahs 9 to 58
2026-10-21 | Page header names the first surah that starts on the page | page 604 is Al-Ikhlas, page 77 An-Nisa
2026-10-21 | Mushaf settings previews are bundled WebP images of page 50 | fonts are not installed before the user chooses a print
2026-10-21 | Late layout loads dropped after a script change | they overwrote the new layout, so switching looked like nothing happened
2026-10-21 | Single Quran screen replaces Read, Listen, Recite modes; slots wired in :app | phone test and Quran for Android study
2026-10-21 | Any pack install drops loaded layouts and rebuilds the font cache | page types appeared not to change after a download
2026-10-21 | Copy pass: docs/copy.md vocabulary and rules, 62 dead strings removed, 23 rewritten | design-system copy review
2026-10-21 | App icon is concept 3, Voice page, scaled 0.948 into the safe zone | maintainer's choice
2026-10-21 | Pack installs run in WorkManager with a wanted flag restarted at launch | a 137 MB font download died with the screen or the process
2026-10-21 | Downloads stay in filesDir; update means recorded checksum differs from the manifest | phone cleaners emptied Quran for Android's external folder
2026-10-21 | Reading settings and a Downloads page install, update and remove translations | no code installed translations, so they were never available
2026-10-21 | Tracker.append adds reference words without a reset; recite loads the next page when under 15 words are left | recitation must continue across pages and surahs
2026-10-21 | Recite bar: show next word and show ayah without the microphone, four one-tap grades (Easy, Good, Hard, Forgot) | memorisers self-test silently and grade for revision
2026-10-21 | Automatic grade is Good for 0 missed words, Hard for 1, Forgot for 2 or more; Easy only by the user; a tap replaces it | recited and revealed ayahs feed FSRS alike
2026-10-21 | Revision opens at the first due ayah in recite mode with words hidden, then moves to the next due ayah after each grade | revision leads to the page (onReviseAt, onOpenAyah)
2026-10-21 | Recite bar colours come from Awn.field (ReciteBarColors); the main button is ochre where the button colour equals the bar | the bar follows Awn, Light, Dark and Black
2026-10-21 | Resume point saved 1.5 s after a swipe settles and on stop; lookups only if the reader stays | lookups moved the place (usability rule 2)
2026-10-21 | Following is a switch: pager drag off, playing page or back-to-playing control on | pager snapped back on every ayah
2026-10-21 | Long press finds the nearest ayah and drag makes a range; optional onPlayRange and playPanelRange slots | Play tab needs ranges
2026-10-21 | Bars show 2 s on open; volume keys only when audio is idle; saved view loaded before the first page | code lessons Y6, Z20, usability 15
2026-10-21 | Mushaf ayahs exposed as one Unicode node each, drawn glyphs hidden; top bar wraps above font scale 1.3 | TalkBack and 200 % text
2026-10-21 | Gapless by preparing the next ayah in a two item Exo playlist, not surah files | no surah timing; keeps page sized downloads
2026-10-21 | Basmalah is its own turn, once per surah per session, from the reciter 1:1 file | rule 7: never between repeats
2026-10-21 | Cumulative drill is read at range start; repeat, speed and pause apply live | changing them must not restart audio
2026-10-21 | Big downloads (over 20 MB or metered) ask first via a prompt flow, not a PlaybackState | no surprise downloads; keeps whens exhaustive
2026-10-21 | Downloads run in AudioDownloadWorker (WorkManager 2.11.2, dataSync foreground) | resume after process death
2026-10-21 | Stop on task removed is on by default and saved with the playback options | swiping the app away should mean silence
2026-10-21 | IndoPak layouts from Quran.com qdc API mushaf 6 (15 lines) and 7 (16 lines), not QUL | QUL downloads need login and state no licence
2026-10-21 | IndoPak font stays out of the release plan | its name table forbids distribution without written notice from quranwbw.com
2026-10-21 | Tajweed text built from word-level text_uthmani_tajweed | verse-level markup crosses word boundaries
2026-10-21 | Translation terms judged by Tanzil provenance (identical ayahs) | Tanzil's non-commercial terms bind only matching text
2026-10-21 | 14 reciters use everyayah.com for ayah files | the QuranicAudio mirror lacks their folders
2026-10-21 | Search tries three dagger-alef readings per ayah | keyboard alef and Uthmani dagger alef do not map one to one
2026-10-21 | Pages measured on a worker thread and cached (8) | font load and shaping took 85-110 ms inside a swipe frame
2026-10-21 | Night tajweed = patched font copy with CPAL palette 0 -> palette 1 | Android cannot pick a palette; docs/decisions/read.md Page
2026-10-21 | Mushaf scale grows lines and glyphs up to the width limit, then only spacing | same breaks and no horizontal scroll cap glyph growth
2026-10-21 | Recite marks are soft fills behind words, ink keeps its colour | COLR tajweed glyphs ignore text colour
2026-10-21 | Views are Mushaf and LineByLine; a layers menu ticks each text layer | the user asked for one list view with layers
2026-10-21 | Text size is saved per layer; Mushaf has its own step (1.0 to 1.6 x width) | fit-width must stay the default; the page scrolls when larger
2026-10-21 | A page tap closes the word card or ayah bar before it toggles bars | outside taps never closed them
2026-10-21 | Ayah bar Play toggles pause and resume from the real playback state | it restarted the audio on every press
2026-10-21 | Word meanings are matched by word position, not list index | an extra token must not shift meanings
2026-10-21 | Quran-page and app orientation are saved separately; the Quran screen restores the app one on exit | the user wants rotation defaults for both
2026-10-21 | bn_quantum rebuilt with pack_info; verify_packs fails any pack without format_version | the app rejected the old file as unreadable and never retried
2026-10-21 | Speech model loads on recite start and is released on exit | it held 144 MB of native memory for the whole app life
2026-10-21 | Screens slide 200 ms; manifest opts out of predictive back | back-to-home animation shrank the window; Nav default was 700 ms
2026-10-21 | Index juz headers are plain text, no filled band | the bands were heavy; the list opens at the last-read surah with the list state hoisted
2026-10-21 | Ship Asad from the Kristories/quran GitHub file, permission pending | maintainer's choice; data.md open items
2026-10-21 | Asad pack source is alquran.cloud en.asad, not the GitHub copy | its terms allow republishing translations with attribution; data.md
2026-10-21 | Line view draws glyph prints word by word in each word's page font; recite states are fills there | COLR tajweed glyphs ignore text colour
2026-10-21 | Font lookups run on IO and screens wait for them; no familyFor on the main thread | page fonts stalled swipes
2026-10-21 | Mushaf size has 2 steps (1.0, 1.15) | glyphs stop growing at 4 to 12 % because line breaks are fixed
2026-10-21 | Measured-page key uses layout content; font cache and recent layouts outlive the screen | the page was blank during the Back slide from Settings
2026-10-21 | Landscape Mushaf is width-fitted at 1.5 x width tall and scrolls | fitting the height printed a narrow block with overlapping lines
2026-10-21 | Word card uses bar colours and sits at the tapped word; glosses are LTR | the mint fill clashed at night and the card covered other text
2026-10-21 | Settings moves into the overflow menu when the top bar is short of width | the juz number was cut off
2026-10-21 | Switches are thin-rail with a round knob; single choices are value-with-menu rows | the user chose them from mockups; the old ones were too big
2026-10-21 | Settings hub is grouped, shows values under names and has search | the user chose the quiet list plus search; a result opens the row
2026-10-21 | One list page per download group with ticks, remove-with-confirm and language headings | the user wants to free space and see all ticked layers
2026-10-21 | Quantum translation gets a meaning-based note by pack id in app code | the pinned manifest cannot change; the text is a referred meaning
2026-10-21 | Rotation defaults sit on the Reading page | they change how the Quran is read; Appearance holds colours and language
2026-10-21 | recite: list views never got word states (fixed); grace period + preparing bar + word problem card added | user report; phone logs showed tracker fine
2026-10-21 | Per-app language through AppCompat (setApplicationLocales + autoStoreLocales) | one route for Android 8 to 16; Compose needs an AppCompatActivity below 13
2026-10-21 | Language screen shows once before the notice; accepted notice means skip | upgrading users keep their flow; see architecture.md
2026-10-21 | Line view scrolls to the recited ayah | live recitation moved below the screen, so its fills were never seen (read.md)
2026-10-21 | Page follows the next expected word, animated for one page, jump for more | audio-style jump hid the recitation (read.md)
2026-10-21 | Word card has its own play button; a word tap never plays | sound only on request, mic may be listening (read.md)
2026-10-21 | Recite bar shows audio controls and pauses the mic while audio plays | the mic heard the reciter (recite.md)
