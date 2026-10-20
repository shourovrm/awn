# Architecture

## Current state
- Modules: :app, :core:ui, :core:data, :tracker (JVM only), :player, :feature:read, :feature:listen, :feature:recite, :feature:learn. Feature modules depend only on :core:ui; :app depends on all.
- :core:ui: theme package (Color, Type, Spacing, Theme) holds every token; components AwnButton, AwnSwitch, AwnPlayerDock use tokens only. Field colours: Read paper, Listen green, Recite night, Learn ochre. Roundo is not bundled yet.
- Gate tests in :core:ui scan every src/main .kt file in the repo: no .dp other than 0, 1, 2, 4, no .sp, no Color(0x outside the theme package; no quoted text in Text(, contentDescription =, label =, placeholder =; no banned words in strings.xml outside names starting notice_.
- Theme preference Light, Dark, System (default System) is provided through LocalThemePreference at the app root. Dark turns only the Read (paper) field into night; Listen, Recite and Learn keep their own fields.
- :core:data: WordKey(surah, ayah, wordPosition), pack models (Credit, PackEntry with format_version and sha256, PackManifest), Packs.usable (format_version at or below the supported one), Room AwnDatabase v1 with a bookmark table, no destructive fallback, MigrationTest checks every exported schema migrates to current.
- :tracker contracts: TrackerInput.nextBlock() returns 16 kHz mono floats or null at end; PhonemeSource.accept/finish return new PhonemeEvent(symbol, onsetSeconds, probability); Tracker works on reference word indexes (not WordKey, which is Android-only); WordState Neutral, Confirmed, Tentative, Wrong, Hidden.
- SherpaPhonemeSource wraps OnlineRecognizer (Zipformer2 CTC, greedy_search, endpoint off, 80-dim 16 kHz features) and reports only tokens not reported before. Blank token is <blank>.
- Notice and theme choice live in DataStore (app/AppPreferences). The notice shows English, Bangla and Arabic together, always; the three texts are non-translatable strings notice_english, notice_bangla, notice_arabic. The Arabic text is the maintainer's wording ("هذا المعلّم القرآني"); English and Bangla say "This app".
- :app wiring: `AwnApplication.onCreate` builds one `AwnServices` (hand-written holder, no DI): CoreData, ExoAudioPlayer, OkHttpAudioDownloads, learn's ProgressEvents, ReadDependencies, settings stores. `AwnViewModelFactory` builds the view models; `ReciteViewModel` holds the `RecitationSession` and the chosen range.
- Navigation: Home (bands with live detail, streak and pages tiles, day's snippet, Continue reading), Read (`read?page=`), Listen (surah list, `ListenPanel`), Reciters, Downloads, Recite, Learn (hosts Revision, Progress, lessons itself), Settings, Notice, Credits.
- Settings groups follow the design system: Reading and Reciting embed the feature screens, Listening, Learning and App are in :app. Group gap is 24 dp (the 8 dp grid), not the 20 dp of the spec. Language opens the system per-app language picker (`generateLocaleConfig`); Backup exports and imports the bookmark JSON through the document picker.
- The speech model, tokens and reference phonemes ship as assets (`copySpeechFiles`, gitignored `app/src/main/assets/models`, verified by `fetchModels` against gradle/models.sha256) and are copied to filesDir/models on first use.
- Fonts: Roundo and Noto Serif Bengali are not bundled; every screen uses the platform sans-serif until the files are added. Type.kt now has six sizes (12 caption added).

## Open items
- Bundle Roundo and Noto Serif Bengali (needs the font files and their licences).
- Theme and learn threshold rows cycle on tap; a picker would be clearer.

## Gotchas
- sherpa OnlineRecognizer takes android.content.res.AssetManager in its constructor; :tracker compiles against com.google.android:android:4.1.1.4 only for that type.
- Bangla notice text is the agent's translation; have a Bangla reader review it.

## Tried / rejected
- Material icons for the dock: Canvas glyphs avoid the large dependency.
