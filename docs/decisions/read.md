# Read (:feature:read)

## Current state
- Entry points: `QuranScreen(dependencies, slots, modifier, target)` with `QuranScreenSlots` (contract in docs/redesign.md) and `QuranTarget.Page | Ayah`; `ReadingSettingsPage` and `MushafSettingsPage` for the Settings hub. :app wires the slots in `QuranRoute`.
- `QuranViewModel` holds one state: current page, `jumpToken` (jumps bump it, swipes do not, so the pager moves at once in every view), bars, selection, panel, recite mode, popups, playing ayah and word, font status.
- Three views (Mushaf, Translation, Word by word) are cycled by one top-bar button and saved in `ReadSettings.pageView`; Back never changes it. Old stored "AyahList" reads as Translation.
- List views build `PageContent` per page: surah band, a centred Basmalah when `Chapter.bismillahPre` (no chip, no selection), then ayah blocks.
- Title surah: the first surah that starts on the page, else the one that continues; `titleSurahNumber` (title) and `headerSurah` (page frame) apply the same rule.
- Top bar right side: view, day/night, text size, settings, overflow (Search, Go to page, Bookmark this page, Learn). Bars fade in 120 ms; panel and popups appear and close at once.
- Back order is `QuranViewModel.onBack`: popups, word popup, panel, selection, recite mode, bars, then the host leaves. `BackHandler` is enabled only while there is something to close.
- Text size is two steps (`arabicSizeStep`, `translationSizeStep`, 0 to 7 = 0.85 to 1.6, default 3) for the list views; the Mushaf page always fits the width. The old `text_size` key is migrated and removed.
- Pages read: `recordPageOpened` at once (resume position), `ProgressEvents.pageOpened` only after 20 s on the page while the screen is started; the same page is reported once in a row.
- Playback following: the pager turns when the playing ayah moves to another page and the reader was on the previous ayah's page; list views scroll the playing ayah to the top.
- `MushafPage(layout, chapters, fontFamily, plainFamily, decorations, showPageInfo, onWordClick, onAyahLongPress)` is the stateless page, called only from `quran/MushafAdapter.kt`. A tap toggles the bars; a 500 ms long press selects the ayah. `layoutPage` (PageGeometry.kt, pure, tested) places words: the font size is the largest at which the widest line fits (capped at 0.8 of a slot), lines at least 80 % of the block are justified, shorter lines centred.
- Pages 1 and 2 are a centred block: slot height 1.2 x normal, banner centre at 27 % of the height, block width 0.9, no justification.
- Glyph pages draw each word's code with the page font, and the end sign is `AyahEnd.glyphCode` (last word code + 1; V1 `U+FBB1` ends print `U+FBD3`). Without the font the page draws `Word.textUthmani` in QPC Hafs after `normalizeFallbackText`.
- Banner and Basmalah are Unicode text in every print (page fonts have no glyphs for them): a double-frame banner with "سُورَةُ" and the name, the Basmalah centred at 62 % width.
- Fills (`PageDecorations`: selected ayah, playing ayah, playing word, bookmarked ayahs, word states) are drawn behind each line fragment; Hidden words are a 30 % block.
- Frame: surah left, juz right ("Juz 2 · ¼ Hizb 3" only where a quarter starts, from `Quarters`), page number at the outer corner. `showPageInfo` off removes both.
- Page fonts: `FontPackController` (install, remove, status with MB) in `QuranViewModel`; `FontDownloadPrompt` shows above the bottom bar while the chosen print has no font and the user has not chosen "Not now". Installing or removing any pack drops the loaded layouts and rebuilds the font cache, so the open page redraws in the new print at once.
- Font families are cached per (print, page) for the page plus or minus five (`PageFontCache`).

## Open items
- Phone check of the merged screen: long press, panel shift, font prompt, page types, font scale 1.3, Black mode.
- Translations, word-by-word and transliteration packs have no install path ("Not installed" only): next wave's catalogue.
- Save the resume position on a short debounce and keep lookups from moving it (sessions/baseline lessons).

## Gotchas
- Tests that build a view model in a field initialiser run before `MainDispatcherRule`; build it lazily.
- `lintRelease` rejects `LocalContext.current.resources.configuration`; use `LocalConfiguration`.
- Material Symbols XML needs its `android:tint` line removed together with care for the closing `>`.
- The recite bar must stop reciting when it leaves composition: Back calls `exitReciteMode`, which only removes the bar.
- Dotted circles: the Quran.com Unicode text has a space before each pause mark (4,362 words); a combining mark after a space has no letter and Android draws it on U+25CC. U+06DE and U+06E9 are symbols, kept; glyph pages are unaffected.

## Tried / rejected
- `ModalBottomSheet` for the translation sheet: replaced by a plain panel, which runs under Robolectric.
- Robolectric text widths do not scale with font size, so geometry is measured a second time at the final size.
- ktfmt rewrites `'۞'` escapes into the raw character; use integer code points for invisible characters.
- Drawing the ۞ sign as a vector: it is a symbol with a real glyph in the font.
