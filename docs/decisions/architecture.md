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
- Navigation: Home with four mode bands, Settings (Theme row cycles Light, Dark, System; About row), About (hosts the notice), four feature placeholders.

## Open items
- Map WordKey to tracker reference indexes in milestone 4.
- Settings theme control is a cycling row; replace with the design-system rows when Settings grows.

## Gotchas
- sherpa OnlineRecognizer takes android.content.res.AssetManager in its constructor; :tracker compiles against com.google.android:android:4.1.1.4 only for that type.
- Bangla notice text is the agent's translation; have a Bangla reader review it.

## Tried / rejected
- Material icons for the dock: Canvas glyphs avoid the large dependency.
