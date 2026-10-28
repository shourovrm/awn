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
2026-10-21 | Quran-Lab approved our use of Zipformer P-Arabic v3 from Hugging Face | maintainer asked the authors; tracker.md
2026-10-21 | Speech model moved to a :speech process | in-process PSS stayed +54 MB after release; separate process returns to baseline (recite.md)
2026-10-21 | Font lookups reload on key change | tajweed list words stayed black after switching to night (read.md)
2026-10-21 | Start after Listen continues the recitation | user: listening must not throw away the marks (recite.md)
2026-10-21 | Quantum Al-Fatihah moved to Hafs numbering; 1:1 uses their Basmalah from 27:30 | 1:1 showed 1:2's meaning (ops.md)
2026-10-21 | Quantum 1:1 uses the site's surah-header Basmalah; Fatihah 6 and 7 are separate rows | user request; audit found no other shifts (ops.md)
2026-10-21 | Unit tests run with a 2-thread Default pool and a 10-minute task timeout | CI hung on blocked Default threads (recite.md)
2026-10-21 | One-time print card replaces the page-load font prompt; Go to page is a floating card; language screen smaller; Language row on the hub | user decisions (read.md)
2026-10-21 | Quantum Fatihah 6 ends at the first comma; the rest opens 7 | their 5-6 held the start of Hafs 7 (ops.md)
2026-10-21 | Unmeasurable Mushaf page falls back to Hafs text (exception, NaN, 10 s) | page 92 stayed blank on the phone; no JVM repro (read.md)
2026-10-21 | Quantum shows the translation only: square-bracket notes removed, [সেজদা] kept | maintainer's choice (ops.md)
2026-10-21 | Back on an idle Quran screen goes to the index; index is stack bottom | the app opened on Quran, so Back left the app (architecture.md)
2026-10-21 | Recite lists pad by the measured bar height and add a tail space | last ayahs stayed under the bar and never scrolled (read.md)
2026-10-21 | Mushaf page slides up to keep the recited word above the recite bar | bar covered the last lines while reciting (read.md)
2026-10-21 | Review is a non-modal card; ReciteController.focusedWord drives page, ring and Listen | modal sheet hid the page and ignored which mistake was open (recite.md)
2026-10-21 | Tap on a flagged word focuses it in the review card instead of opening the plain word card | the plain card gave no way to review (read.md)
2026-10-21 | Word-by-word language is its own setting (null = app language), radio list, English fallback | one flag ticked every row and read the device locale
2026-10-21 | Word-timing packs are a Word audio download group; ayah-level timings (Basfar) are not offered | nothing installed timings, so word play hid
2026-10-21 | Word audio downloads by itself for the chosen reciter on unmetered networks; the card offers it otherwise | same rule as audio, 3 MB each
2026-10-21 | Only Quran text, font and manifests ship in the APK; all other content downloads | user decision (data.md)
2026-10-21 | Word transliteration is the pack en_transliteration_words, not a quran.sqlite column | the column was content (packs.md)
2026-10-21 | Speech model downloads from models-v1 (SHA-256, atomic rename); recite shows a card first | sherpa loads by path (data.md)
2026-10-21 | New installs tick no translation; first launch offers en_saheeh+wbw_en or bn_zakaria+wbw_bn | nothing ships (read.md)
2026-10-21 | bn_zakaria stays the Bangla starter download despite unstated terms | maintainer judges it usable (packs.md)
2026-10-21 | Three bottom tabs (Quran, Learn, Hifz); the Quran screen opens above them; no dashboard, no Open-the-app-on setting | user decision after the tab mockups
2026-10-21 | Hifz tab icon is Material Symbols psychology | the crossed-out eye reads as hidden, a heart as favourites
2026-10-21 | Today rows come from TodayProvider composables, at most two, capped load, section absent when empty | no backlog counts on the home tab
2026-10-21 | Surah meaning shows by default; Bangla from the pack, else English from surah_meanings_en | switch kept in Settings, default on
2026-10-21 | Juz rail maps the finger height to 1..30 and prints 7 landmarks | 30 numbers do not fit small screens
2026-10-21 | Learn tab reuses LearnScreen with a barActions slot for Settings | the Learn agent replaces it later
2026-10-21 | FSRS port moved from :feature:learn to :core:data | Hifz needs it too, two real callers (learn.md)
2026-10-21 | Hifz methods and daily assignment are pure Kotlin; no overdue carry-over, revision capped per day | piled-up revision makes users quit (hifz.md)
2026-10-21 | Hifz pages follow the reader's print (Madani 1421 or 1405); records remap on a print change | a hafiz knows page images (hifz.md)
2026-10-21 | Hifz tab built from the approved mocks; Method row opens the chooser page, not a menu | the chooser holds the method's settings (hifz.md)
2026-10-21 | Hifz practice runs in the Quran screen's recite mode through a hifzBar slot | one reader, no second screen (hifz.md)
2026-10-21 | Hifz tab and Today row wired into the tab host; practice returns to the Hifz tab | the placeholder is gone (hifz.md)
2026-10-22 | Hifz and revision runs hide words for the run only; Today revise passes a cap | saved recite setting stayed hidden and every due ayah was revised
2026-10-22 | Hifz bar listens to a movable window; Memorised up to sets the cursor | the loop was stuck on the lesson and users could not say how far they know
2026-10-22 | Print offer allows several ticked prints; Download installs all | the card showed many options but took one
2026-10-22 | Audio bar gets a reciter button; a reciter change while playing continues from the same ayah | reciter was hard to reach from the page
2026-10-22 | Arabic text styles use the bundled Hafs font through HafsFont installed by AwnTheme | generic serif drew marks badly (read.md)
2026-10-22 | Do not ask for notifications when audio starts | The dialog stopped the first Listen; media notifications show without it (player.md)
2026-10-22 | Search lists surahs by number or name above ayah results | users typed a surah name and found nothing
2026-10-22 | Downloads space used leaves out bundled items | a fresh install showed 12 MB the user never downloaded
2026-10-22 | Word course reachable: tab opens on first-open choices then the hub; Today row and word card use ports | the course screens had no route (learn.md)
2026-10-22 | Robolectric tests in :app name a plain Application (RobolectricApplicationGateTest) | AwnApplication.onCreate WorkManager check threw after tests, failing a later test at random on CI
2026-10-22 | Listening while reciting plays on until the microphone; the ayahs only heard are never marked left out | user rule for the combined bar (recite.md)
2026-10-22 | Recite bar is ochre; the microphone works while audio plays and Listen is listen-onward | combined bar (recite.md)
2026-10-22 | Audio bar ends with a Recite microphone; the playing bar drops the speed button | combined bar needs room for it (player.md)
2026-10-22 | A page tap shrinks a busy bottom bar to a corner button instead of hiding or ending it | combined bar (read.md)
2026-10-22 | :app gives the audio bar onRecite and the screen isAudioActive | the combined bar needs both (player.md, read.md)
2026-10-22 | Hub row "Letters and daily time" reopens the first-open choices; saving keeps cards and progress | users could not undo "I cannot read letters" (learn.md)
2026-10-22 | Counted Learn strings are plurals; PluralsCandidate ignores removed | English read "1 words" (learn.md)
2026-10-22 | Listen-onward end and resume ayah are read from the player before our own stop, not only from observed states | conflated StateFlow made a test fail (recite.md)
2026-10-22 | Green by weighted WordScore (near sounds cheaper, length budget); passed words end green or red | Awn said unknown; see tracker.md
2026-10-22 | Yellow Mispronounced = sound or madd finding on a green word; no Tentative fill; not a Hifz mistake | name the problem, not unclear
2026-10-22 | Microphone stop keeps marks; corner button acts; Listen after the last word goes to the next ayah | phone bugs, build 227 (recite.md, read.md)
2026-10-22 | Leaving the app and Hifz Play stop the microphone with stopMicrophone() | plain session.stop() cleared the marks on next start
2026-10-22 | Word card: add is an icon in the heading; words get their own reciter setting (Word audio) | listening reciter had no word timings, play button vanished
2026-10-22 | One local data folder awn-data/ with published/, next/, fonts/, raw/; tools find it through tools/data_paths.py; AWN_DATA_DIR now names the folder that holds published/ | four folders held duplicates and the build read a stale one
2026-10-23 | versionCode = (MINOR*100+PATCH)*10000 + commits since tag, ABI step 100M | old scheme made the first tag a downgrade from commit-count builds
2026-10-23 | Tajweed units open at any time; the Reading-unit-7 rule is a note, not a lock | rows ignored taps for users who chose the letters; read the track as broken
2026-10-23 | Reading track in Noorani Qaida order, drills folded into checks, new unit 8 | maintainer: shorter Qaida, non-commercial use
2026-10-23 | IndoPak prints are two scripts (IndoPak15, IndoPak16) sharing one layout pack; font downloaded from Quran.com via download_url | font licence forbids our hosting it
2026-10-23 | Mushaf size is 0.7 to 2.0 times the default; too-wide printed lines are re-broken by greedy fill | the old control did almost nothing (read.md)
2026-10-23 | Mushaf page reserves bar-height strips top and bottom, measured from the bars; scrolling pages pad their content | no text under a bar (read.md)
2026-10-23 | Pages built ahead of a swipe reveal two rows per frame | one 36 ms frame per swipe was the likely jank (read.md)
2026-10-23 | Font cache keeps page +-2, one family per font file; measured-page cache 5; loaded layouts within 4 pages | memory grew 93 MB over 30 swipes (read.md)
2026-10-23 | IndoPak page drawing reuses the glyph-script path with one font and extra header lines shifted into slots | no new renderer; Hafs text in lists
2026-10-23 | Lesson builder takes a tajweed course folder and Grid and Practice blocks; clip ids must match across languages | units get a Qaida grid and a random practice set (learn.md)
2026-10-23 | Tajweed track reads the nine tajweed units; old JSON lessons and loader removed; done ids carried over by LegacyLessonProgress | no lost progress (learn.md)
2026-10-23 | Learn sounds are a learn-audio entry in scripts.json (zip of qaida-NN/NNN.opus), group LearnAudio, extracted to packs/learn-audio/<id> | reuses font-zip install, no new manifest file (learn.md)
2026-10-23 | Reading units use the ten Qaida-order files; unit pages show grids and a seeded 10-item practice; read-aloud items count as done | matches mock A (learn.md)
2026-10-23 | Lesson sounds row in Settings > Downloads, credits for Qaida, academy and tajweed book, Bangla Reading units added | completes the guide (learn.md)
2026-10-23 | Lesson language is its own Learn setting (follow app, English, Bangla) in Settings > Recite; chrome stays in the app language | lessons and UI can differ (learn.md)
2026-10-23 | Page top strip no longer adds the status bar height; audio bar clearance is measured to the area bottom | screen is already inset, 100 px gap (read.md)
2026-10-23 | Hifz bar gets a second row with show next word and show ayah while words are hidden | one row has no room for two more 44 dp buttons (hifz.md)
2026-10-23 | Home Show choice is Surahs and Juz only; Pages list, PageRow, buildPageRows removed | Go to page covers pages (architecture.md)
2026-10-23 | IndoPak 16-line removed, IndoPak 15 in print card, uninstalled IndoPak never saved | v0.1.0 force close: layouts missing (read.md)
2026-10-23 | Notice moves from first launch to the first Learn visit, short text in the app language; LICENSE 4 reworded | maintainer: concise, one language
2026-10-23 | Quran words page lists sets, today words and pages only; no Reading or Basics lessons | user: the page had nothing for words
2026-10-23 | Words on a page opens from words page, Quran menu and Hifz menu; Learn N new queues them | user: link words to reading and Hifz
2026-10-23 | Show my words marks known (green) and learning (ochre) words on the Quran page via MyWords.marks | user: learnt words linked to the page
2026-10-23 | Lessons gain words (tappable Quran word tiles, coloured letters, two-word tiles) and tip blocks; tajweed units 11-18 rewritten with them | user: lessons too thin (learn.md)
2026-10-23 | Grammar track grows to 17 units (12 new, en and bn), one trigger per set by real form rank | user: grammar not enough; four landed after set 1 (learn.md)
2026-10-24 | Word reciter defaults to husary_muallim; snippets get structured sources and tap-for-another; mouth drawing in tajweed unit 11 | user: slice B (learn.md, player.md)
2026-10-24 | Surah rows show the spoken Bangla name; the meaning is its own column and search folds Bangla spellings | Quran.com's bn translated_name was a meaning, often wrong (data.md)
2026-10-24 | Search by reciting: mic on the Search screen finds the ayah from about 5 s of recitation, offline | user: ayah voice search like Natlu (tracker.md)
2026-10-24 | Duas live on the Quran tab (Browse row), not Learn; 89 duas in 17 groups, word ranges checked against quran.sqlite | user chose placement B; reviewed by Opus (duas.md)
2026-10-24 | End part asks one firmness per ayah and one grade per page; partial saves stay on Today | Finish part's one grade lost partial progress (hifz.md)
2026-10-24 | Revision spacing: Manzil cycle, fixed steps or adaptive; Hifz journal and map page sheet | user asked for customised spacing and a journal (hifz.md)
2026-10-24 | Ayahs by topic built on the dua cards (32 topics); dua Listen bar drops speed and opens the reciter sheet | user asked for Topics, a Mushaf-like listen bar and the missing pronunciation (duas.md)
2026-10-25 | Wi-Fi only governs every download and is off by default | the switch only covered audio; user wants both networks allowed unless limited (data.md)
2026-10-25 | One welcome card (single-select Mushaf plus translation tick) replaces the print and starter cards; notice before first recite | two download cards in a row read as pushy; recite was usable before the notice (read.md)
2026-10-25 | Settings regrouped by the user's question; Mushaf and Reading merged; Downloads is Storage; print is Mushaf in the UI | first-time user review found jargon and a junk-drawer page (architecture.md, copy.md)
2026-10-25 | Back from page words reopens the Quran page; Learn leaves the Quran menu | Back landed on the Learn words list, the user lost the page
2026-10-25 | Leaving the Quran screen cancels audio that has not started; playing audio continues | audio began seconds after Back, on another screen
2026-10-25 | Keep screen on while reading is a setting, on by default | the screen dimmed mid-page; the app had no wake option
2026-10-25 | Words session rates by tap: Forgot/Hard/Good/Easy with intervals, earned one filled | Anki-style; one tap on the filled one is the quick path
2026-10-25 | Ayah cards in the words session mark every place of the form, stronger fill | new-word ayahs and repeated forms showed no or one mark
2026-10-25 | AyahFinder returns the matched word span (firstWord, lastWord) per ayah | Voice search rows highlight the recited words; accuracy and speed unchanged
2026-10-25 | Search rows drop the space before pause marks; voice rows mark the recited span | no dotted circles; shows what was heard
2026-10-25 | About shows the developer's name and the README's free, non-commercial and dua line | maintainer's request
2026-10-25 | Tracker is a word cursor over a few candidates; no re-routing, at most 2 red per step | slips reddened whole pages (tracker.md)
2026-10-25 | A word passes on cost, 70 % coverage and closure; short words need bounds | a hint of a letter passed as said (tracker.md)
2026-10-25 | Global search needs 30 failed phonemes and a unique place; jumps leave Unread words | chance phrases caused page-wide reds (tracker.md)
2026-10-25 | Repeats and corrections are tracker events with every mark kept | repeating an ayah turned words red (tracker.md)
2026-10-25 | Strictness levels Lenient, Normal, Strict replace the tracker speed; the stored speed maps to them | maintainer approved named levels (tracker.md)
2026-10-25 | Ayah left out whose opening equals the next ayah's is flagged, turning its opening red | mutashabihat slips (tracker.md)
2026-10-25 | No automatic page turn while reciting; the recite bar offers Next page | a slip read as a jump turned the page away (recite.md)
2026-10-25 | Words passed by a jump are Unread; a red word said right is Corrected, listed, not counted | jumps and repeats are no mistakes (recite.md)
2026-10-25 | Tracker.closePending closes a held word when a run ends | silence sent no token; the word went red after Listen or stayed Tentative
2026-10-25 | Held word refitted on closing, not stored | the 64-phoneme trim left stored positions past the end (crash)
2026-10-25 | Last consonant must match itself to end a word | any next sound substituted for it and passed
2026-10-25 | Next page follows the current word's page | an ayah over a page break showed no Next page on its second page
2026-10-27 | Voice search lists every ayah within 85% of the best, up to 100 | a fragment ties many ayahs; a fixed 3 hid them (tracker.md)
2026-10-27 | Raad al-Kurdi removed from the reciter list; Minshawi is the default | user: surah-only audio with no ayah times (re-add when they exist)
2026-10-28 | In-app update check lives in the github flavor only (app/src/github, About row) | Play must not hold REQUEST_INSTALL_PACKAGES; Wi-Fi only gates the APK; SHA256SUMS.txt is required, else no install
