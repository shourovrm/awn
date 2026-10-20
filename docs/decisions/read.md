# Read (:feature:read)

## Current state
- Entry points: `QuranScreen(dependencies, slots, modifier, target)` with `QuranScreenSlots` (contract in docs/redesign.md) and `QuranTarget.Page | Ayah`; `ReadingSettingsPage(dependencies)` for the Settings hub. `ReadHost` is a thin wrapper with empty slots until :app wires the slots; `ReadSettingsScreen` delegates to `ReadingSettingsPage` for the same reason.
- `QuranViewModel` holds one state: current page, `jumpToken` (jumps bump it, swipes do not, so the pager moves at once in every view), bars, selection, panel, recite mode, popups, playing ayah and word.
- Three views (Mushaf, Translation, Word by word) are cycled by one top-bar button and saved in `ReadSettings.pageView`; Back never changes it. Old stored "AyahList" reads as Translation.
- List views build `PageContent` per page: surah band, then a centred Basmalah (not an ayah: no chip, no selection) when `Chapter.bismillahPre`, then ayah blocks.
- Title surah: the first surah that starts on the page, else the one that continues (`titleSurahNumber`, `quran/TitleSurah.kt`).
- Top bar right side: view, day/night, text size, settings, overflow (Search, Go to page, Bookmark this page, Learn). Bars fade in 120 ms; panel and popups appear and close at once.
- Back order is `QuranViewModel.onBack`: popups, word popup, panel, selection, recite mode, bars, then false (host leaves). `BackHandler` is enabled only while `hasBackWork`.
- Text size is two steps (`arabicSizeStep`, `translationSizeStep`, 0 to 7 = 0.85 to 1.6, default 3); the popup moves both. The old `text_size` key is migrated to the nearest step and removed.
- Pages read: `recordPageOpened` at once (resume position), `ProgressEvents.pageOpened` only after 20 s on the page while the screen is started (`QuranViewModel.setScreenActive`); the same page is reported once in a row.
- Playback following: the pager turns when the playing ayah moves to another page and the reader was on the previous ayah's page (`pageToFollow`); list views scroll the playing ayah to the top.
- Mushaf is drawn by the page agent's `MushafPage`, called only from `quran/MushafAdapter.kt` (`MushafPageHost`). Until `PageDecorations` lands, a tap on a word selects its ayah, the playing word uses the selected-word block, and ayah fills are not drawn on the Mushaf. List-view fills come from `ReaderFills` (from `Awn.field`).
- The ayah panel crops the page (layout at full height, shifted, clipped) instead of scaling; `ayahSpan` computes the ayah's slots to keep it visible.
- `ReadDependencies` gained optional `reciters` and `selectedReciterId` for word taps (`playWord` only when the reciter has timings).

## Open items
- Switch the Mushaf call to `PageDecorations` and a 500 ms long press when the page branch merges.
- Replace `titleSurahNumber` with the page agent's shared header-surah function.
- Delete `page/MushafPageScreen.kt`, `ReadChrome` toolbar, `dock/` and `ReadSettings.textSize` (`TextZoom`) once nothing uses them.
- Screenshots at font scale 1.3, night, and a phone check are not done (no emulator or phone).

## Gotchas
- Tests that build a view model in a field initialiser run before `MainDispatcherRule`; build it lazily.
- `lintRelease` rejects `LocalContext.current.resources.configuration`; use `LocalConfiguration`.
- Material Symbols XML needs its `android:tint` line removed together with care for the closing `>`.
- The recite bar must stop reciting when it leaves composition: Back calls `exitReciteMode`, which only removes the bar.

## Tried / rejected
- `ModalBottomSheet` for the translation sheet: replaced by a plain panel, which runs under Robolectric.
