# Read (:feature:read)

## Current state
- Entry points: `ReadHost` (whole Read mode), `MushafPageScreen`, `AyahListScreen(source = Surah | Page)`, `BrowseScreen`, `ReadSettingsScreen`; all take `ReadDependencies` and make their own view models.
- The script is read from `ReadSettings`, not passed to `MushafPageScreen`; `MushafPage(layout, ...)` is the stateless page for other modes (Listen, Recite) and takes `wordStates`.
- Settings live in a Preferences DataStore (`DataStoreReadSettingsStore`); defaults are QPC Hafs Unicode, Mushaf view, translations `bn_zakaria` then `en_saheeh`, transliteration and word by word on, text size Medium.
- Text size is a zoom (1.0, 1.15, 1.3) applied to the 26 sp Quran token only.
- Page text is fitted by shrinking the Quran font until the widest line fits the width (never enlarged); lines at least 80 % full are justified, shorter ones centred.
- A glyph script whose page font is missing draws `Word.textUthmani` in the QPC Hafs font and shows "Download font"; the pack is found in manifest `scripts` by `ScriptId.packId`.
- If `pageLayout` throws for the chosen script, the Hafs layout is used; if that fails too, the page shows "Page not available".
- Font families are cached for the current page plus or minus five (`PageFontCache`); Compose loads the typeface lazily on first draw.
- Juz starts come from a fixed table (`JuzTable`), because `QuranTextRepository` has no juz query.
- Merged translation ranges show once, at the first ayah, labelled `from-to`; in the tap sheet they always show for the tapped ayah.
- Both views call `recordPageOpened`; switching view reopens on the most recent page.

## Open items
- Screenshots at font scale 1.3 and night are not checked (no emulator was available).
- Basmala and surah band use Unicode text in every script; no glyph codes exist for them in the tables.

## Gotchas
- Tests that build a view model in a field initialiser run before `MainDispatcherRule`; build it lazily.
- `lintRelease` rejects `LocalContext.current.resources.configuration`; use `LocalConfiguration`.

## Tried / rejected
- `ModalBottomSheet` for the translation sheet: replaced by a plain animated panel, which matches the green sheet of the mockup and runs under Robolectric.
