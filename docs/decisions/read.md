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
- Entry points: `ReadHost` (whole Read mode), `MushafPageScreen`, `AyahListScreen(source = Surah | Page)`, `BrowseScreen`, `ReadSettingsScreen`, `MushafSettingsPage`; all take `ReadDependencies` and make their own view models. The redesign replaces the first four with the reader agent's Quran screen (docs/redesign.md).
- Settings live in a Preferences DataStore (`DataStoreReadSettingsStore`); defaults are the V2 (1421) print, Mushaf view, translations `bn_zakaria` then `en_saheeh`, transliteration and word by word on, text size Medium, `showPageInfo` on.
- `MushafPage(layout, chapters, fontFamily, plainFamily, decorations, showPageInfo, onWordClick, onAyahLongPress)` is the stateless page. Words and ayah-end signs are placed by `layoutPage` (PageGeometry.kt, pure, tested): font size is the largest at which the widest line fits the width (capped at 0.8 of a slot), lines at least 80 % of the block are justified to its edges, shorter lines are centred. Text sits so the middle of its ink (0.35 em above the baseline) is the middle of its 1/15 slot.
- Pages 1 and 2 are a centred block: slot height 1.2 x normal, banner centre at 27 % of the height, block width 0.9, no justification.
- Glyph pages draw each word's code with the page font; the ayah-end sign is the glyph `AyahEnd.glyphCode` (last word code + 1; V1 `U+FBB1` ends print `U+FBD3`). Without the font the page draws `Word.textUthmani` in QPC Hafs, with a ring and Arabic-Indic number for the end sign.
- Banner and Basmalah are drawn from Unicode text in every script (page fonts have no glyphs for them): the banner is a double frame with two diamonds and "سُورَةُ" plus the chapter name; the Basmalah is centred at 62 % of the block width. Remaining difference from the print: its ornamental banner and calligraphic Basmalah are not in the data.
- Fills (`PageDecorations`: selected ayah, playing ayah, playing word, bookmarked ayahs, word states) are drawn behind each line fragment of the ayah; Hidden words are a 30 % block. A long press (500 ms) on a word or end sign calls `onAyahLongPress(ayah, bounds in window coordinates)`; a tap calls `onWordClick`. Taps use the nearest line within 0.75 slot and the nearest token within half a gap.
- Frame: surah name left, juz right ("Juz 2 · ¼ Hizb 3" only where a quarter starts, from `Quarters`), page number at the outer corner (right on odd pages). `showPageInfo` off removes both. `headerSurah(layout)` is the first surah that starts on the page, else the one that continues.
- Font packs: `FontPackController` (install, remove, status with download and installed MB) and `FontDownloadPrompt` (name, MB, Wi-Fi, progress, keep fallback). `MushafViewModel.state.showFontPrompt` is true while the chosen print has no font and the user has not kept the fallback.
- Script changes reload the open page at once; a layout load that finishes after the type changed is dropped (it used to overwrite the new layout).
- Font families are cached per (script, page) for the page plus or minus five (`PageFontCache`).

## Open items
- Screenshots at font scale 1.3 and night are not checked (no emulator was available); the page was checked only in a desktop browser with the same fonts and algorithm.
- The settings row still cycles `ScriptId.entries` (reader's file); it must link to `MushafSettingsPage` instead.
- `MushafPageScreen` and `ReadHeader` stay for the old Read mode until the reader agent's screen replaces them.

## Gotchas
- Tests that build a view model in a field initialiser run before `MainDispatcherRule`; build it lazily.
- `lintRelease` rejects `LocalContext.current.resources.configuration`; use `LocalConfiguration`.
- Material Symbols XML needs its `android:tint` line removed together with care for the closing `>`.
- The recite bar must stop reciting when it leaves composition: Back calls `exitReciteMode`, which only removes the bar.

## Tried / rejected
- `ModalBottomSheet` for the translation sheet: replaced by a plain panel, which runs under Robolectric.
- Robolectric text widths do not scale with font size, so geometry is measured a second time at the final size.
- ktfmt rewrites `'۞'` escapes into the raw character; use integer code points for invisible characters.

## Dotted circles (root cause)
- The Quran.com Unicode text has a space before each pause mark ("رَيْبَ ۛ", 4,362 words) and a combining mark after a space has no letter to sit on, so Android draws it on U+25CC. `normalizeFallbackText` removes the space; after it no word has a mark on a space or at its start (checked over all 77,429 words). U+06DE (199 words) and U+06E9 are symbols with glyphs in the Hafs font, not marks, and are kept. U+06E5 and U+06E6 are letters (Lm). Glyph pages are not affected: a word's code holds the mark as its own private-use glyph.
- Page 1 first glyph: the V2 font's glyph reaches 545 units (0.22 em) left of its advance box and the old word view clipped to that box; the new page does not clip.

## Tried / rejected
- `ModalBottomSheet` for the translation sheet: replaced by a plain animated panel, which matches the green sheet of the mockup and runs under Robolectric.
- Drawing the ۞ sign as a vector: it is a symbol with a real glyph in the font.
