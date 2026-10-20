# Data (:core:data)

## Current state
- Real repositories are wired by `CoreData(context)`; interfaces and fakes are unchanged.
- Bundled packs (quran.sqlite, Hafs font, en_saheeh, bn_zakaria, wbw_en, wbw_bn, en_transliteration, three manifests) sit in gitignored `core/data/src/main/assets/packs/`. `:core:data:fetchBundledPacks` copies them from `awn-data/` (or `$AWN_DATA_DIR`, needed in git worktrees) and otherwise downloads from the data-v1 release; hashes are pinned in `core/data/bundled-packs.sha256`. A rebuilt pack needs its pin updated.
- Installed layout under filesDir/packs: `<file>.sqlite`, `fonts/<id>/pN.ttf`, `fonts/qpc-hafs/<file>`; partial downloads in `.downloads/<file>.part` (Range resume, SHA-256 and byte size checked, zip members must be `pN.ttf` and match `member_count`).
- Bundled packs are copied out of the APK on first use and cannot be removed.
- Layouts: V1 uses page_v1/line_v1; V2, QpcHafs and V4Tajweed use page_v2/line_v2; IndoPak throws UnsupportedOperationException.
- Banner/bismillah slots are derived: banner at first-word line minus 1 (minus 2 with bismillah_pre). Where only line 1 is free (V1 page 77, 21 surahs) the banner and Basmala share line 1.
- User database version 2 adds bookmarked_page, saved_ayah, recent_page (cap 50). The version 1 word `bookmark` table is unused but kept (no drops).
- Manifests are read from the bundled JSON only; PackEntry.licence is "Not stated by the source" because manifests carry none.

## Open items
- data-v1 release does not exist yet (404 on 2026-10-20); the download fallback and installs need it.
- No instrumented test for the asset copy on a device.

## Gotchas
- `org.json` `optString` returns "null" for JSON null; use isNull.
- HttpURLConnection needs `Accept-Encoding: identity` or Range byte counts break.

## Tried / rejected
- Editing the contract `PackEntry` to carry bytes/archive info: not allowed; the catalog keeps a side record per id.
