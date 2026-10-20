# Data (:core:data)

## Current state
- Real repositories are wired by `CoreData(context)`; interfaces and fakes are unchanged.
- Bundled packs (quran.sqlite, Hafs font, en_saheeh, bn_zakaria, wbw_en, wbw_bn, en_transliteration, three manifests) sit in gitignored `core/data/src/main/assets/packs/`. `:core:data:fetchBundledPacks` copies them from `awn-data/` (or `$AWN_DATA_DIR`, needed in git worktrees) and otherwise downloads from the data-v1 release; hashes are pinned in `core/data/bundled-packs.sha256`. A rebuilt pack needs its pin updated.
- Installed layout under filesDir/packs: `<file>.sqlite`, `fonts/<id>/pN.ttf`, `fonts/qpc-hafs/<file>`; partial downloads in `.downloads/<file>.part` (Range resume, SHA-256 and byte size checked, zip members must be `pN.ttf` and match `member_count`).
- Bundled packs are copied out of the APK on first use and cannot be removed.
- Layouts: V1 uses page_v1/line_v1; V2, QpcHafs and V4Tajweed use page_v2/line_v2; IndoPak throws UnsupportedOperationException.
- Banner/bismillah slots are derived: banner at first-word line minus 1 (minus 2 with bismillah_pre). A surah whose first word is on line 2 (18 in V2, 21 in V1) has its Basmalah on line 1 and its banner on line 15 of the previous page; on line 1 it has banner and Basmalah on lines 14 and 15 of the previous page (`readNextPageStarts`). Tested for every surah and script.
- `LineItem.AyahEnd.glyphCode` is the printed end sign in the page font: code of the ayah's last word + 1 (all 6,236 in V2, all but 41 in V1; those end with U+FBB1 and print U+FBD3). Every such code exists in the page fonts (checked).
- `Quarters` (240 rub' al-hizb, first ayah and page in V1 and V2; juz and hizb follow from the number) is generated from Quran.com `rub_el_hizb_number` by `tools/build_quarters.py` into `QuarterData.kt`; a test pins its SHA-256 (09952942...ef37).
- `PackRepository.sizeOf(id)` gives the download bytes (manifest) and the installed bytes (null when not installed).
- User database version 2 adds bookmarked_page, saved_ayah, recent_page (cap 50). The version 1 word `bookmark` table is unused but kept (no drops).
- Manifests are read from the bundled JSON only; PackEntry.licence is "Not stated by the source" because manifests carry none.

## Open items
- English word-by-word source repeats a phrase on each of its words in surahs 9 to 58 (1,225 rows); `PhraseRepeats` empties the repeats at read time and keeps 4:24:16. The pack is unchanged; rebuilding it with a phrase flag would be cleaner.
- data-v1 release does not exist yet (404 on 2026-10-20); the download fallback and installs need it.
- No instrumented test for the asset copy on a device.

## Gotchas
- `org.json` `optString` returns "null" for JSON null; use isNull.
- HttpURLConnection needs `Accept-Encoding: identity` or Range byte counts break.

## Tried / rejected
- Editing the contract `PackEntry` to carry bytes/archive info: not allowed; the catalog keeps a side record per id.
