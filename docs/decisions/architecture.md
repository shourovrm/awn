# Architecture

## Current state
- Modules: :app, :core:ui, :core:data, :tracker (JVM only), :player, :feature:read, :feature:listen, :feature:recite, :feature:learn. Feature modules depend only on :core:ui; :app depends on all.
- :core:ui: theme package (Color, Type, Spacing, Theme, FieldColors, ColourMode) holds every token; components AwnButton, AwnSwitch, AwnPlayerDock, AwnIconButton, AwnTopBar (extends behind the status bar; use `AwnTheme(barBehindStatusBar = true)`) and AwnChoiceDialog (the picker for settings rows). Icons are Material Symbols XML copied per module (`ic_ui_*`, `ic_app_*`).
- Gate tests in :core:ui scan every src/main .kt file in the repo: no .dp other than 0, 1, 2, 4, no .sp, no Color(0x outside the theme package; no quoted text in Text(, contentDescription =, label =, placeholder =; no banned words in strings.xml outside names starting notice_.
- Colour modes: Awn (paper, green bars, ochre), Light (white, grey bars, green), Dark (#121212), Black (#000000). `ColourSettings(day, night, followDevice, manualNight)` resolves to a `ColourMode`; `LocalColourMode` is set at the app root. `awnFieldColors(field, mode)`: Awn keeps one colour per field, the other modes draw every field on the page colour (Green is the raised bar colour). `AwnFieldColors` holds the four highlight fills and `barBackground`/`onBar`. The Quran screen's day/night button calls `rememberNightToggle(preferences)`, which flips `manualNight` and stops following the device.
- :core:data: WordKey(surah, ayah, wordPosition), pack models (Credit, PackEntry with format_version and sha256, PackManifest), Packs.usable (format_version at or below the supported one), Room AwnDatabase v1 with a bookmark table, no destructive fallback, MigrationTest checks every exported schema migrates to current.
- :tracker contracts: TrackerInput.nextBlock() returns 16 kHz mono floats or null at end; PhonemeSource.accept/finish return new PhonemeEvent(symbol, onsetSeconds, probability); Tracker works on reference word indexes (not WordKey, which is Android-only); WordState Neutral, Confirmed, Tentative, Wrong, Hidden.
- SherpaPhonemeSource wraps OnlineRecognizer (Zipformer2 CTC, greedy_search, endpoint off, 80-dim 16 kHz features) and reports only tokens not reported before. Blank token is <blank>.
- Notice, colour choices and surah name meaning live in DataStore (app/AppPreferences); the old `theme_preference` (Light, Dark, System) is read as colour settings until the first write. Status-bar icons are set by `AwnTheme` from the colour behind the bar: `enableEdgeToEdge()` chooses them once from the device night setting, which left white icons on cream (defect 8). The notice shows English, Bangla and Arabic together, always; the three texts are non-translatable strings notice_english, notice_bangla, notice_arabic. The Arabic text is the maintainer's wording.
- :app wiring: `AwnApplication.onCreate` builds one `AwnServices` (hand-written holder, no DI): CoreData, ExoAudioPlayer, OkHttpAudioDownloads, learn's ProgressEvents, ReadDependencies, settings stores. `AwnViewModelFactory` builds the view models; `ReciteViewModel` holds the `RecitationSession` and the chosen range.
- Navigation: the app starts on `quran?page=&surah=&ayah=` (today `ReadHost` at the last page; the ayah is ignored until `QuranScreen` replaces it). Index (surahs by juz, juz quarters, bookmarks, recent pages; callback `onOpen(page, ayah)`), Search (`CoreData.search`), Learn, Settings hub with section pages, Notice, Credits; Listen, Recite, Reciters and Downloads stay until the maintainer removes them. Every NavHost sets enter, exit, pop-enter and pop-exit to None (Navigation Compose's default 700 ms fade made Back slow); NavTransitionsTest scans the source for this.
- Settings is a hub of seven icon rows (Mushaf, Reading, Audio, Recite and memorise, Appearance, Backup, About), each a page. Appearance, Backup, About and the Memorised after row (a choice dialog, under the recite module's rows) are in :app; the other four pages come from `SettingsPageSlots`, today filled by existing screens. The Juz tab reads a `QuarterTable`; `JuzStartQuarterTable` (30 rows) stands in until the core/data quarter table is connected.
- The speech model, tokens and reference phonemes ship as assets (`copySpeechFiles`, gitignored `app/src/main/assets/models`, verified by `fetchModels` against gradle/models.sha256) and are copied to filesDir/models on first use.
- Fonts: Roundo and Noto Serif Bengali are not bundled; every screen uses the platform sans-serif until the files are added. Type.kt now has six sizes (12 caption added).

## Open items
- Bundle Roundo and Noto Serif Bengali (needs the font files and their licences).

## Gotchas
- sherpa OnlineRecognizer takes android.content.res.AssetManager in its constructor; :tracker compiles against com.google.android:android:4.1.1.4 only for that type.
- Bangla notice text is the agent's translation; have a Bangla reader review it.

## Tried / rejected
- Material icons for the dock: Canvas glyphs avoid the large dependency.
