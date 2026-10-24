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

## Second review additions
- Step 1: give `PageFontCache` a seam (a `fontFile` lambda or an interface), so the render test
  can check glyph drawing with a fake font instead of reading pack files from disk.
- Step 3: pick `codeV1` or `codeV2` from `ReadSettings.script` and the night flag, as
  `familyFor(script, page, isNight)` does. When the pack is missing, draw `textUthmani` in Hafs,
  never glyph codes in Hafs.
- Step 4: "outside the dua" is an explicit input. Hidden means the word is left out of the list;
  shown means a dim fill. The render test covers both.
- Step 5: the Mushaf top toggle in `QuranBars.kt` switches `pageView`. The dua toggle copies the
  layer flags `showWordByWord`, `translations`, `showTransliteration` into `DuaProgressStore`
  (new DataStore keys, defaults from `ReadSettings`), one setting for all dua and topic cards.
- Step 6: `DuaModeBar` (Listen, Recite) sits at the top in `CardChrome.kt` today; the user wants
  it at the bottom, with the layer toggle at the top.
- Topics reuse `DuaCardsScreen` (`topics/TopicsScreen.kt` 167-174): `TopicsScreenTest` and
  `DuaCardsScreenTest` join the step 4 and 6 gates; the main session rewrites them.
- Step 2 phone check adds hint letters, the focused word during recite and the night tajweed font.
- Step 8 decides whether `CardWord` and `CardWordsTest` stay as the dua-only mapping.

## Worker split (3 sonnet workers, worktrees from local main)
- Main session first, on main: step 1, plus two contracts so slices compile alone:
  `WordFlowInput` (the 7 fields, `playingWord`, per-word dim) in a new `feature/read` file, and
  `CardLayers` (three booleans) as a defaulted field of `CardPageState` in `DuaCardPage.kt`.
- Worker A, step 2, parallel with C. Owns `quran/WordsFlow.kt`, the `AyahItem` call site in
  `quran/PageListViews.kt`, the input file. Verify:
  `:feature:read:testReleaseUnitTest --tests '*WordsFlowRenderTest' --tests '*WordCardIconsTest'
  --tests '*WordAudioOfferRenderTest' --tests '*AddToMyWordsRenderTest'`.
- Worker C, steps 5 and 6, parallel with A. Owns `ui/CardChrome.kt`, `ui/DuaCardsScreen.kt`,
  `ui/CardScreenState.kt`, `data/DuaProgress.kt`, `data/DataStoreDuaProgressStore.kt`. Verify:
  `:feature:duas:testReleaseUnitTest --tests '*DataStoreDuaProgressStoreTest'`.
- Worker B, steps 3 and 4, after A is merged. Owns `card/DuaCardData.kt`, `card/DuaCardLoader.kt`,
  `ui/DuaCardPage.kt`, the font fields of `DuasDependencies.kt`, new `ui/DuaLineView.kt`,
  `state/DuasViewModel.kt` only if the loader signature changes. Verify:
  `:feature:duas:testReleaseUnitTest --tests '*CardWordsTest' --tests '*DuaLineViewTest'`.
- Shared tests (`DuaCardsScreenTest`, `TopicsScreenTest`) belong to the main session; workers
  report failures there and do not edit them. At most two Gradle runs at once.
- Order: step 1 -> A and C together -> merge A -> B -> merge -> steps 7 and 8.

## Revert point and the dotted circle
- Revert point: local tag `pre-dua-line-view` at `dc1e646` (CI green on GitHub, 2026-10-24).
- The "weird circle" on the cards (seen on Ali 'Imran 3:193, after ءَامِنُوا۟) is likely U+06DF
  ARABIC SMALL HIGH ROUNDED ZERO, which the bundled card font cannot place, so a dotted
  placeholder circle shows. The line view's page font draws it. The pack-missing fallback must
  therefore use the same fallback as Mushaf line mode (`rememberFontLookup`), not the card's
  current Hafs style. Phone check: 3:193 shows no circle.
