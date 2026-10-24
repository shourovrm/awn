# Dua and topic cards on the shared line view

Status: plan, reviewed, not built. Start only after the CI/CD push in the other session lands on
main.

## Goal
- A dua or topic card shows one ayah, or the part of an ayah that is the dua, drawn like the
  Mushaf line mode (`WordsFlow` in `feature/read`).
- A word tap opens the green word card (`WordPopupCard`) with Play and Stop. Today a tap in the
  dua word-by-word grid (`WordTileGrid.kt`) only plays the word.
- A toggle at the top, like the Mushaf, picks translation, word by word and transliteration.
- The bottom keeps two tabs: Listen and Recite.
- The page dots (`PageDots` in `CardChrome.kt`, one per card) give way to a "3 / 30" counter.
  To confirm with the user: these are the "weird circles".

## Module choice
`:feature:duas` adds `implementation(project(":feature:read"))`, and `:feature:read` exposes a
thin public surface: a line-view word flow, `WordPopupCard`, `WordPopup`, `AyahWord`. This has no
dependency cycle, because `:feature:read` depends only on `:core:ui`, `:core:data`, `:player` and
`:tracker`. `:feature:duas` already depends on `:feature:recite`, so a feature-to-feature
dependency has a precedent.

A separate module limited to `:core:ui` and `:core:data` cannot hold the code. `WordPopup.problem`
is `tracker.WordProblem`, `WordState` is in `:tracker`, and the drawing path needs `PageFontCache`,
`SharedPageFontCache`, `PackRepository`, `PageFontWord`, `ReadSettings`, `WordCellFill` and
`wordColor`. Moving them would be a large extraction of the reader.

`docs/decisions/architecture.md` line 4 ("Feature modules depend only on :core:ui") is already
stale and gets updated in step 1.

## Facts
- `WordsFlow`, `WordCell` and `ArabicWord` read 7 of the ~25 `ListPageEnvironment` fields:
  `settings`, `wordStates`, `myWordMarks`, `hintLetters`, `focusedWord`, `onWordTap`, `fontCache`.
- The `playingWordPosition` `State` is read in `AyahItem` (`PageListViews.kt` 209-210,
  `derivedStateOf`); `WordsFlow` gets a plain `Int?`. `AyahItem` stays untouched, so the
  recompose scope is kept.
- The existing word card tests (`ReciteWordCardTest`, `WordCardPlayTest`,
  `WordCardFallbackReciterTest`, `QuranPopupsTest`) test `QuranViewModel` on the JVM; none draws
  `WordsFlow` or `WordCell`.
- Duas draws `CardWord(core.data.Word, isDuaWord)` in the bundled Hafs font
  (`AwnTypography.quran`). The line view needs `AyahWord` with `WordGlyphs` and a page font from
  `PageFontCache`.

## Steps (one commit each)
1. Add a Robolectric render test of the line-view words: fills for recite state, my-word,
   playing word, hint letters, and the tap callback payload. Add the duas -> read dependency and
   the public surface; update `architecture.md`.
2. Inside `:feature:read`, with no file moves: replace the `ListPageEnvironment` parameter of
   `WordsFlow` with a small input of the 7 fields plus `playingWord: Int?`. Run the render test
   and the `WordPopupCard` render tests (`WordCardIconsTest`, `WordAudioOfferRenderTest`,
   `AddToMyWordsRenderTest`). Phone check of Mushaf line mode: word by word on and off, tap a
   word and get the card, play and stop, recite fills, my-word fills, page-font prints.
3. Dua page data: build `AyahWord`s from `Word.codeV1`/`codeV2` with the meaning and
   transliteration from `DuaCardLoader`. Get the page font via
   `SharedPageFontCache.obtain(packs, revision)`. When the page font pack is not downloaded,
   fall back to the bundled Hafs font (today duas never needs a pack). Decide whether
   `problem` and my-words come from the tracker on the dua screen.
4. Dua page view: draw with the shared flow. Words outside the dua follow the existing "dua only"
   switch. Recite fills and the ochre playing word map onto the per-word fills. Word tap opens
   `WordPopupCard`; `canPlay`, `audioOffer` and `onDownloadAudio` map onto the duas player.
5. Top toggle (translation, word by word, transliteration): starts from the reader settings and
   changes only the dua screen.
6. Bottom tabs Listen and Recite; counter replaces the page dots.
7. Lint, full tests, release APK, phone pass on duas, topics and Mushaf line mode.
8. After the phone pass, delete `WordTileGrid` and `CardWordFlow` in their own commit.
