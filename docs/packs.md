# Pack format, version 1

Packs are built by the scripts in `tools/` into `awn-data/` (gitignored) and published under a data tag of the GitHub Releases. Every SQLite pack has a `pack_info(key, value)` table whose `format_version` row is `1`. Build order: `build_quran_text`, `build_fonts`, `build_translations`, `build_timings`, `build_manifests`, then `verify_packs` (exit status 1 on any failed check). Raw API responses are cached in `awn-data/raw/quran.com/`.

## Word key
- A word is addressed by `(surah, ayah, position)`, all 1-based. `position` counts spoken words only.
- The Quran.com API lists the ayah-end sign as one more word with `char_type_name = "end"` and `position = word count + 1` (checked for all 6,236 ayahs). The builders drop it from every word-keyed table. Its page and line are in `ayah_marker`.
- Totals: 114 chapters, 6,236 ayahs, 77,429 words. Every word-keyed pack has the same word count per ayah as `quran.sqlite`.

## quran/quran.sqlite
- `word(surah, ayah, position, text_uthmani, code_v1, code_v2, page_v1, line_v1, page_v2, line_v2, transliteration)`; primary key `(surah, ayah, position)`. `code_v1` and `code_v2` are the private-use glyph characters for the page fonts of that print; they are valid only with that page's font.
- `ayah_marker(surah, ayah, page_v1, line_v1, page_v2, line_v2)`: where the end sign is printed. In 818 ayahs (V1 or V2) it is on a different line than the last word.
- `ayah(surah, ayah, text_uthmani, juz, hizb, page_v1, page_v2)`: `hizb` is 1..60; pages are those of the first word.
- `chapter(number, name_arabic, name_simple, name_bangla, verses_count, revelation_place, bismillah_pre)`: `name_bangla` is the Bangla translated name from `/chapters?language=bn`; `bismillah_pre` is 0 or 1.
- Pages run 1..604 in both prints. 56 ayahs (305 words) sit on a different page in V1 and V2, so the two prints must be read through their own columns. V1 page and line come from `/verses/by_page`, V2 from `/verses/by_chapter`.
- `line` is a slot number 1..15 on the page and includes surah-name and bismillah lines that hold no words. Pages 1 and 2 end at line 8. 581 of the other 602 V1 pages and 584 V2 pages end at line 15; the rest end at line 14.

## Fonts
- `fonts/qpc-hafs/UthmanicHafs1Ver18.ttf`: one Unicode font, for `word.text_uthmani`.
- `fonts/v1/`, `fonts/v2/`, `fonts/v4-tajweed/`: `p1.ttf` .. `p604.ttf`, one font per page; page N's `code_v1` (V1) or `code_v2` (V2) is drawn with `pN.ttf`. V4 tajweed uses the V2 codes with colour (COLRv1) glyphs, which Android renders from 13 on.
- Source: `quran/quran.com-frontend-next`, commit `aff1a035`, `public/fonts/quran/hafs/{uthmanic_hafs, v1/ttf, v2/ttf, v4/colrv1/ttf}`. WOFF2 is not used (Android cannot load it); OpenType-SVG V4 is not used (not rendered on Android).
- Packs for download are `fonts/<script>.zip` (members `pN.ttf`); `fonts/fonts.json` lists names and sizes. Sizes: qpc-hafs 0.24 MB; v1 94.9 MB (zip 54.5); v2 208.1 MB (zip 137.1); v4-tajweed 167.0 MB (zip 68.9).

## Translations
- Ayah packs `translations/<id>.sqlite`: `translation(surah, ayah, ayah_from, ayah_to, text)`, primary key `(surah, ayah)`, 6,236 rows. Ids: `en_saheeh`, `en_abdul_haleem`, `en_transliteration`, `bn_zakaria` (Quran.com resources 20, 85, 57, 213) and `bn_quantum` (built by `tools/build_quantum_pack.py`). Resource 131 (The Clear Quran) is not in the public API, so there is no pack.
- Merged-range rule: when a publisher gives one passage for several ayahs, every ayah in the range has a row with the same `text` and the same `ayah_from`, `ayah_to`. Show the text once, labelled `ayah_from-ayah_to`, at the row where `ayah = ayah_from`. When `ayah_from = ayah_to = ayah` the row stands alone. The Quran.com packs never merge; `bn_quantum` does.
- Stripped markup: `<sup foot_note=ID>N</sup>` elements, Bangla `[১]` call-outs in `bn_zakaria` (with the space before them), and `<b>` tags. Square-bracket insertions such as `[Prophet]` stay.
- Known source defect: `bn_zakaria` 18:32 holds the section heading "পঞ্চম রুকু’", not a translation.
- Word packs `translations/wbw_en.sqlite`, `wbw_bn.sqlite`: `word_translation(surah, ayah, position, text)`, 77,429 rows, same key as `word`.
- Each pack except `bn_quantum` has `<id>.TERMS.md` beside it: source, resource id and name, retrieval date. The API supplies no licence text; conditions must be confirmed before release.

## Timings
- `timings/<reciter>.sqlite`: `segment(surah, ayah, position, start_ms, end_ms)` (milliseconds from the start of the ayah file) and `ayah_audio(surah, ayah, url, duration_ms, offset_ms)`. `offset_ms` is 0 in all packs. `duration_ms` is the end of the last segment (the API gives no file length) and NULL for Basfar.
- Reciters: `alafasy` (Quran.com recitation 7), `husary` (6), `husary_muallim` (12), `minshawi` (9): word-level. `basfar`: ayah files only, no segments.
- Quran.com timed Husary murattal against `Husary_64kbps`, not the 128 kbps folder the reciter manifest plays; the manifest field `timing_audio_folder` names the timed folder. Offsets can differ by tens of milliseconds between encodes.
- A segment spanning several words is split equally between them (`pack_info.interpolated_words`). An ayah whose segments miss a word, run backwards or overlap gets no segment rows; `pack_info.ayahs_without_word_timing` lists them (alafasy 28, husary 19, husary_muallim 28, minshawi 35). Play those ayahs without word seek.
- Basfar: Quran.com's surah-file timestamps drift (2:286 starts after the end of the file), so the pack lists EveryAyah `Abdullah_Basfar_192kbps` URLs. The mirror returns 404 for the 64 kbps folder; it is on `everyayah.com/data` only.

## Manifests
- `manifests/scripts.json` and `translations.json` hold `packs`; `reciters.json` holds `reciters`. Each file has top-level `format_version: 1`.
- Every pack entry has `format_version`, `file` (path relative to `awn-data/`), `sha256`, `bytes`, `credit`, `source_url`; also `id`, `kind`, `name`. Translation entries add `language`, `quran_com_resource_id`, `terms_file`. Font entries add `glyph_font_per_page`, and zips add `archive_format`, `member_pattern`, `member_count`.
- Reciter entries add `quranicaudio_id`, `quranicaudio_path`, `surah_url_pattern` (`{SSS}`), `everyayah_folder`, `ayah_url_pattern` (`{SSS}{AAA}`), `fallback_domain_suffix` (`.quranicaudio.com` to `.quranicaudio.org`), `timing_pack`, `timing_granularity`, `timing_audio_folder`, `quran_com_recitation_id` and `word_by_word` (true for alafasy, husary, husary_muallim, minshawi; false for basfar and raad_al_kurdi). `raad_al_kurdi` has no `everyayah_folder` and no timing pack, so `file`, `sha256`, `bytes` are null.

## Verification in the app
1. Download `manifests/*.json` and accept only `format_version` at or below the app's supported version.
2. Download the pack at `file`; reject it if its size differs from `bytes` or its SHA-256 differs from `sha256`.
3. Open SQLite packs and read `pack_info.format_version`; reject a value above the supported one.
4. Show `credit` and `source_url` on the About screen; a CI test asserts every entry has both.
