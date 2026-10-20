# Data (:core:data)

## Current state
- Real repositories are wired by `CoreData(context)`; interfaces and fakes are unchanged.
- Bundled packs (quran.sqlite, Hafs font, en_saheeh, bn_zakaria, wbw_en, wbw_bn, en_transliteration, three manifests) sit in gitignored `core/data/src/main/assets/packs/`. `:core:data:fetchBundledPacks` copies them from `awn-data/` (or `$AWN_DATA_DIR`, needed in git worktrees) and otherwise downloads from the data-v1 release; hashes are pinned in `core/data/bundled-packs.sha256`. A rebuilt pack needs its pin updated.
- Installed layout under filesDir/packs: `<file>.sqlite`, `fonts/<id>/pN.ttf`, `fonts/qpc-hafs/<file>`; partial downloads in `.downloads/<file>.part` (Range resume, SHA-256 and byte size checked, zip members must be `pN.ttf` and match `member_count`).
- Bundled packs are copied out of the APK on first use and cannot be removed.
- Layouts: V1 uses page_v1/line_v1; V2, QpcHafs and V4Tajweed use page_v2/line_v2; IndoPak throws UnsupportedOperationException.
- Second catalogue (built in awn-data-v2, not released; docs/packs.md has the details): IndoPak 15-line (610 pages) and 16-line (548 pages) layout pack plus Nastaleeq font, tajweed Unicode text pack, 31 more translations (each with a terms judgement), 4 more word packs, 28 more reciters (8 with word timings); manifests keep format_version 1 and add optional fields (min_sdk 33 on v4-tajweed).
- Search compares each ayah in three readings of the dagger alef (`DaggerAlefReading`) and tries a waw form of queries ending in alef plus ta marbuta; tests in UthmaniSpellingSearchTest use strings copied from quran.sqlite.
- Banner/bismillah slots are derived: banner at first-word line minus 1 (minus 2 with bismillah_pre). A surah whose first word is on line 2 (18 in V2, 21 in V1) has its Basmalah on line 1 and its banner on line 15 of the previous page; on line 1 it has banner and Basmalah on lines 14 and 15 of the previous page (`readNextPageStarts`). Tested for every surah and script.
- `LineItem.AyahEnd.glyphCode` is the printed end sign in the page font: code of the ayah's last word + 1 (all 6,236 in V2, all but 41 in V1; those end with U+FBB1 and print U+FBD3). Every such code exists in the page fonts (checked).
- `Quarters` (240 rub' al-hizb, first ayah and page in V1 and V2; juz and hizb follow from the number) is generated from Quran.com `rub_el_hizb_number` by `tools/build_quarters.py` into `QuarterData.kt`; a test pins its SHA-256 (09952942...ef37).
- `PackRepository.sizeOf(id)` gives the download bytes (manifest) and the installed bytes (null when not installed).
- User database version 2 adds bookmarked_page, saved_ayah, recent_page (cap 50). The version 1 word `bookmark` table is unused but kept (no drops).
- Storage: every download lives under `filesDir` (`packs/`, `audio/`, `models/`). Nothing uses external or shared storage and the app asks for no storage permission, so phone cleaners that empty external app folders cannot delete a download (the most discussed Quran for Android problem). Cloud backup and device transfer exclude `filesDir`.
- `PackRepository.catalogue()` lists `CatalogueItem`s in manifest order. `CatalogueGrouping` sorts them without naming ids: a font that is not bundled is a PageType; translation kind "word" is WordByWord; kind "transliteration" or an id containing it is Transliteration; kind "ayah" is Translation. The text, the bundled Hafs font and reciter timings are not items.
- An update is offered when the checksum recorded at install (`packs/.installed/<id>`) differs from the manifest's. A copy with no record (bundled, or installed by an older app) never shows an update.
- `PackSpace`: a database or single font needs the bytes still to download; a font zip needs those plus two more copies of its size (zip and unpacked fonts coexist while unpacking). `install` checks free space first and throws `PackInstallException(reason = NoSpace, neededBytes, freeBytes)`; other reasons are NoInternet, ServerError, Damaged, Other.
- Installs run in WorkManager (`PackInstallWorker`, unique work `pack-install:<id>`, expedited, network required, foreground notification with Cancel). `PackDownloads.request` writes the id to the wanted list (SharedPreferences `wanted_downloads`) first; success or a failure a retry cannot fix removes it, a dropped connection keeps it and retries, and `AwnApplication` calls `resumeWanted` at every start. `.part` files resume by Range.
- `CatalogueModel` (state, space check, install, cancel, remove) is shared by the Reading settings, the Downloads page and `FontPackController`.
- Manifests are read from the bundled JSON only; PackEntry.licence is "Not stated by the source" because manifests carry none.

## Open items
- Nothing from awn-data-v2 is released: the IndoPak font forbids distribution without written notice from quranwbw.com, 22 packs have no publication permission, and the app's DATA_RELEASE_URL points at data-v1 (tools/write_release_plan.py writes the plan).
- English word-by-word source repeats a phrase on each of its words in surahs 9 to 58 (1,225 rows); `PhraseRepeats` empties the repeats at read time and keeps 4:24:16. The pack is unchanged; rebuilding it with a phrase flag would be cleaner.
- data-v1 release does not exist yet (404 on 2026-10-20); the download fallback and installs need it.
- No instrumented test for the asset copy on a device.
- Downloads use any network; there is no Wi-Fi-only switch for packs yet (audio has one).
- The 3x space estimate for font zips is 29 MB short for v4-tajweed; the unpack step then fails with NoSpace.

## Gotchas
- `org.json` `optString` returns "null" for JSON null; use isNull.
- HttpURLConnection needs `Accept-Encoding: identity` or Range byte counts break.

## Tried / rejected
- WorkManager's default start-up: it runs before `AwnApplication` has built the services the worker needs; the manifest removes it and the application is the `Configuration.Provider`.
- QUL downloads for IndoPak layouts (login required, no licence text) and the KFGQPC Nastaleeq font site (unreachable from here): not used; Quran.com's qdc API with mushaf=6 and 7 has the layouts.
- Quran.com v4 `mushaf=` parameter: ignored, returns the Madani layout whatever the id.
- Editing the contract `PackEntry` to carry bytes/archive info: not allowed; the catalog keeps a side record per id.
