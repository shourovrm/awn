# Read (:feature:read)

## Current state
- Entry points: `QuranScreen(dependencies, slots, modifier, target)` with `QuranScreenSlots` (contract in docs/redesign.md) and `QuranTarget.Page | Ayah`; `ReadingSettingsPage` and `MushafSettingsPage` for the Settings hub. :app wires the slots in `QuranRoute`.
- `QuranViewModel` holds one state: current page, `jumpToken` (jumps bump it, swipes do not, so the pager moves at once in every view), bars, selection (`selection` first ayah, `selectionEnd` last of a dragged range), panel, recite mode, popups, playing ayah and page, `isFollowing`, `isAudioIdle`, font status. The playing word is a separate `playingWordPosition` flow so a tick never changes the state.
- Three views (Mushaf, Translation, Word by word) are cycled by one top-bar button and saved in `ReadSettings.pageView`; Back never changes it. Old stored "AyahList" reads as Translation.
- List views build `PageContent` per page: surah band, a centred Basmalah when `Chapter.bismillahPre` (no chip, no selection), then ayah blocks.
- Title surah: the first surah that starts on the page, else the one that continues; `titleSurahNumber` (title) and `headerSurah` (page frame) apply the same rule.
- Top bar right side: view, day/night, text size, settings, overflow (Search, Go to page, Bookmark this page, Learn). Bars fade in 120 ms; panel and popups appear and close at once. Above font scale 1.3 the buttons move to a second row under the title.
- Bars show for 2 s (`BARS_REVEAL_MS`) when the screen opens, whatever the entry, then hide; a manual toggle or Back cancels that timer, so a bar the reader showed never hides itself. A tap toggles on finger-up; no gesture on the screen listens for a double tap (`InteractionBudgetTest` scans for it).
- Back order is `QuranViewModel.onBack`: popups, word popup, panel, selection, recite mode, bars, then the host leaves. `BackHandler` is enabled only while there is something to close.
- Text size is two steps (`arabicSizeStep`, `translationSizeStep`, 0 to 7 = 0.85 to 1.6, default 3) for the list views; the Mushaf page always fits the width. The old `text_size` key is migrated and removed.
- Resume point (`ResumeTracker`): a swipe or volume-key turn is saved 1.5 s after the pager settles and at once on stop; a lookup (search, bookmark, Go to page, index, panel arrows, playback turn) is saved only if the reader stays 1.5 s, and a lookup still pending at stop is dropped. It is stored as the page number through `recordPageOpened`; all prints have the same 604 pages, so it survives a change of print. Pages read are separate: `ProgressEvents.pageOpened` after 20 s on the page while the screen is started, once per page in a row.
- `open` reads the saved settings first, so every entry (resume, bookmark, search, notification, index) opens in the saved view and print; the screen shows nothing until they are known.
- Playback following: `isFollowing` is on at the start of a playback. A pager or list drag turns it off; arriving on the playing page by any route (swipe back, back-to-playing control) turns it on; going to another page while audio plays turns it off. While on, the pager turns to the playing ayah's page and lists scroll to it only when it is not fully visible, leaving 5 % of the list height above it. `BackToPlayingButton` shows while audio plays and following is off. The playing-ayah fill cross-fades over 500 ms (`PlayingFadeState`); each page gets only the marks that fall on it (`highlightsFor`), so a tick recomposes one page.
- Volume keys turn pages only while audio is Idle or Error; otherwise they are not consumed and change the volume.
- `MushafPage(layout, chapters, fontFamily, plainFamily, decorations, showPageInfo, onWordClick, onAyahLongPress)` is the stateless page, called only from `quran/MushafAdapter.kt`. A tap anywhere toggles the bars (the page consumes the tap-up, no parent toggles twice); a 500 ms long press anywhere selects the nearest ayah (`PageGeometry.nearestAyahAt`: the ayah under the point, else the nearest line's nearest token), dragging extends it to a range, lifting ends it. `onSelectionBounds` reports the first-line and whole-selection rectangles in page coordinates. `layoutPage` (PageGeometry.kt, pure, tested) places words: the font size is the largest at which the widest line fits (capped at 0.8 of a slot), lines at least 80 % of the block are justified, shorter lines centred.
- Pages 1 and 2 are a centred block: slot height 1.2 x normal, banner centre at 27 % of the height, block width 0.9, no justification.
- Glyph pages draw each word's code with the page font, and the end sign is `AyahEnd.glyphCode` (last word code + 1; V1 `U+FBB1` ends print `U+FBD3`). Without the font the page draws `Word.textUthmani` in QPC Hafs after `normalizeFallbackText`.
- Banner and Basmalah are Unicode text in every print (page fonts have no glyphs for them): a double-frame banner with "سُورَةُ" and the name, the Basmalah centred at 62 % width.
- Floating bar: `placeSelectionBar` puts it above the first line, below the last line when the first line is within one bar height of the top, under the first line when below does not fit, centred on the selection, clamped to the screen; it shows after the finger lifts. `ReadIconButton` shows its name in a tooltip on long press. The range feeds `QuranScreenSlots.onPlayRange` and `playPanelRange` (both optional, null falls back to `onPlayFrom` and `playPanel` with the first ayah); copy and share send one line per ayah.
- The ayah panel overlays the Mushaf page: the page keeps its full height and moves up only as far as the selection needs (never more than the panel height); list views shrink to the part above the panel.
- Screen readers: each Mushaf ayah is one invisible node ("Al-Baqarah, ayah 255. Arabic") built 500 ms after the page appears, in reading order, with a long-click action that selects it; the drawn words and rings are `invisibleToUser`. List words and blocks expose click and long-click actions.
- Fills (`PageDecorations`: selected ayahs, playing ayah, playing word, bookmarked ayahs, word states) are drawn behind each line fragment; Hidden words are a 30 % block.
- Frame: surah left, juz right ("Juz 2 · ¼ Hizb 3" only where a quarter starts, from `Quarters`), page number at the outer corner. `showPageInfo` off removes both.
- Page fonts: `FontPackController` (install, remove, status with MB) in `QuranViewModel`; `FontDownloadPrompt` shows above the bottom bar while the chosen print has no font and the user has not chosen "Not now". Installing or removing any pack drops the loaded layouts and rebuilds the font cache, so the open page redraws in the new print at once.
- Reading settings: translation rows come from the download catalogue. A row shows size or status, a download button, remove (after a confirm dialog that names the translation), the order button and the switch. Downloading a translation turns it on; removing turns it off. Word by word and transliteration show the missing download under their switch while on.
- The Translation view shows `MissingTranslationPrompt` (one line, Download action, then progress) for the first enabled translation that is not on the phone. Any pack install clears the cached page lists, so a new translation appears at once.
- `FontPackController` is a thin view of `CatalogueModel`, so page fonts download in the background like every other item.
- Font families are cached per (print, page, night) for the page plus or minus five (`PageFontCache`); see Page below.

## Open items
- Phone check of the merged screen: long press, panel shift, font prompt, page types, font scale 1.3, Black mode.
- Resume stores a page number; an ayah column in `recent_page` (core/data) would let a print with another pagination resume exactly.
- Phone check: long press and drag, bar placement and flip, tooltips, back-to-playing, 2 s bar reveal, TalkBack on a glyph page, font scale 2.0 (Robolectric measures every glyph one pixel wide, so width clipping is not tested on the JVM).
- Drag-to-range works on the Mushaf page only; list views select one ayah.

## Gotchas
- Tests that build a view model in a field initialiser run before `MainDispatcherRule`; build it lazily.
- `lintRelease` rejects `LocalContext.current.resources.configuration`; use `LocalConfiguration`.
- Material Symbols XML needs its `android:tint` line removed together with care for the closing `>`.
- The recite bar must stop reciting when it leaves composition: Back calls `exitReciteMode`, which only removes the bar.
- Dotted circles: the Quran.com Unicode text has a space before each pause mark (4,362 words); a combining mark after a space has no letter and Android draws it on U+25CC. U+06DE and U+06E9 are symbols, kept; glyph pages are unaffected.

## Tried / rejected
- Recomposition counting of a page in a JVM test: no hook exists without instrumenting production code, so skipping is covered by `highlightsFor` equality tests.
- `ModalBottomSheet` for the translation sheet: replaced by a plain panel, which runs under Robolectric.
- Robolectric text widths do not scale with font size, so geometry is measured a second time at the final size.
- ktfmt rewrites `'۞'` escapes into the raw character; use integer code points for invisible characters.
- Drawing the ۞ sign as a vector: it is a symbol with a real glyph in the font.

## Page (wave 3, page agent)
- Pages are measured off the main thread: `rememberMeasuredPage` (PageMeasurement.kt) runs `measurePage` on `PageWork.dispatcher` (Default) and keeps the last 8 results in `sharedPageMeasurements`; until ready the page draws nothing. Compose tests must add `InlinePageWorkRule`.
- `PageFontCache.familyFor(script, page, isNight)` is keyed by (script, page, night); `retainAround` also prepares pages +-2 on IO for the script and mode last asked for. Fonts are `FontFamily(Font(file))`; the typeface itself loads inside the worker's measure.
- Tajweed at night: Android draws CPAL palette 0 only, so `NightFontFiles` writes `night/pN.ttf` beside the font with palette 0 index set to palette 1's (`CpalNightPatch`, checksums kept valid); fonts without a second palette use the original. Night = `!Awn.field.isLight`.
- `MushafPage(scale)` (1 or more): page height is body height x scale and the body scrolls; font = min(unscaled x scale, width limit with gaps squeezed to 0.04 em, 0.8 slot). Line breaks never change and no line leaves the width, so glyph growth stops at about the width limit (roughly 10 to 15 %); further scale only adds line spacing.
- Word states are soft fills behind the word (`wordStateFills`, tokens `AwnReciteFills`, day family for light fields, night family for dark ones); the ink keeps its colour, Hidden words keep their block.
- `FontDownloadPrompt` shows bar, percent and Cancel (`onCancel`) instead of Download while `progress` is not null. `PageFontWord` draws one word with its page font.
