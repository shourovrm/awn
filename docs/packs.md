# Pack format, version 1

Packs are built by the scripts in `tools/` into the workspace `awn-data/next/` (gitignored; fonts go to `awn-data/fonts/`, downloads to `awn-data/raw/`, the files the app pins sit in `awn-data/published/`; `tools/data_paths.py` names them) and published under a data tag of the GitHub Releases. Every SQLite pack has a `pack_info(key, value)` table whose `format_version` row is `1`. Build order: `build_quran_text`, `build_fonts`, `build_indopak`, `build_tajweed`, `build_translations`, `build_timings`, `reciter_catalogue` (probes audio URLs), `build_manifests`, then `verify_packs` (exit status 1 on any failed check) and `write_release_plan`. Raw API responses are cached in `awn-data/raw/quran.com/`. The second catalogue was built into the workspace `awn-data/next/`; `verify_packs` compares every pack it shares with `awn-data/published/` (or `$AWN_BASELINE_DATA`) byte for byte. The scripts replace a file by writing a new one, never by writing through an existing name, because a hard link would change the older folder too.

Only `quran/quran.sqlite`, `fonts/qpc-hafs/UthmanicHafs1Ver18.ttf` and the three manifests ship in the app; every other file below is downloaded after install (the speech model from the `models-v1` release, everything else from `data-v1`).

## Word key
- A word is addressed by `(surah, ayah, position)`, all 1-based. `position` counts spoken words only.
- The Quran.com API lists the ayah-end sign as one more word with `char_type_name = "end"` and `position = word count + 1` (checked for all 6,236 ayahs). The builders drop it from every word-keyed table. Its page and line are in `ayah_marker`.
- Totals: 114 chapters, 6,236 ayahs, 77,429 words. Every word-keyed pack has the same word count per ayah as `quran.sqlite`.

## quran/quran.sqlite
- `word(surah, ayah, position, text_uthmani, code_v1, code_v2, page_v1, line_v1, page_v2, line_v2)`; primary key `(surah, ayah, position)`. `code_v1` and `code_v2` are the private-use glyph characters for the page fonts of that print; they are valid only with that page's font.
- `ayah_marker(surah, ayah, page_v1, line_v1, page_v2, line_v2)`: where the end sign is printed. In 818 ayahs (V1 or V2) it is on a different line than the last word.
- `ayah(surah, ayah, text_uthmani, juz, hizb, page_v1, page_v2)`: `hizb` is 1..60; pages are those of the first word.
- `chapter(number, name_arabic, name_simple, name_bangla, verses_count, revelation_place, bismillah_pre, name_bangla_meaning)`: `name_bangla` is the spoken Bangla name ("আল-বাকারা") and `name_bangla_meaning` its meaning ("বকনা-বাছুর-গরু", null for surahs 20 and 36), both from `tools/data/surah_names_bn.tsv`, a copy of Bangla Wikipedia's list of surahs (CC BY-SA 4.0, credited in the About screen and the README); `pack_info.bangla_names_source` records it; `bismillah_pre` is 0 or 1. `tools/apply_surah_names_bn.py` writes the two columns into a built file.
- Pages run 1..604 in both prints. 56 ayahs (305 words) sit on a different page in V1 and V2, so the two prints must be read through their own columns. V1 page and line come from `/verses/by_page`, V2 from `/verses/by_chapter`.
- `line` is a slot number 1..15 on the page and includes surah-name and bismillah lines that hold no words. Pages 1 and 2 end at line 8. 581 of the other 602 V1 pages and 584 V2 pages end at line 15; the rest end at line 14.

## IndoPak
- `quran/indopak.sqlite`: `word(surah, ayah, position, text_indopak, text_nastaleeq, page_15, line_15, page_16, line_16)` (77,429 rows, same key as `quran.sqlite.word`) and `ayah_marker(surah, ayah, page_15, line_15, page_16, line_16, end_sign)` (6,236 rows). `pack_info` records `pages_15` 610, `lines_per_page_15` 15, `pages_16` 548, `lines_per_page_16` 16.
- Source: `GET https://api.quran.com/api/qdc/verses/by_page/{page}?mushaf=6|7&words=true`. The v4 API ignores `mushaf`; only the qdc API honours it. Mushaf ids are those of the Quran.com frontend (`types/QuranReader.ts`: `Indopak15Lines = 6`, `Indopak16Lines = 7`). Page counts match the layouts that QUL lists: 15 lines = 610 pages ("follows the Mushaf published by Qudratullah Company, Lahore"), 16 lines = 548 pages ("published by Taj Company"); that these are the same layouts is inferred from the page counts and was not stated by Quran.com.
- Text: draw `text_nastaleeq` (Quran.com `text_indopak_nastaleeq`) with `fonts/indopak/indopak-nastaleeq-waqf-lazim-v4.2.1.ttf`; all 131 characters of that field are in the font's cmap. `text_indopak` is the older IndoPak spelling (the font lacks only U+200B and U+FEFF for it) and is kept for search. Some words end in a private-use code point (for example U+F65D, the iqlab sign); the font draws it. A word may contain a space (`بَعۡدَ مَا`).
- The ayah-end sign is a string in `ayah_marker.end_sign` (the font draws the sign and its number from it, for example `۟ؕ` plus U+F5B4 for ayah 181). Draw it after the last word of the ayah, on the line given by the marker.
- `line` is a slot number 1..15 (or 1..16) counted from the top of the page. Slots without words are the surah banner and the bismillah, directly above the first word of a surah (banner one slot above, bismillah between when `bismillah_pre` = 1). Pages 1 and 2 are bottom-aligned: the first word of Al-Fatihah is on line 9 (15-line) or 11 (16-line), of Al-Baqarah on line 10 or 12, and the slots above are empty. Pages with fewer than 15 (16) filled lines: 15-line pages 261, 404, 502, 506, 511, 610; 16-line pages 364, 548.
- Header gap: where a surah starts on line 1 or 2 there are fewer free slots than the header needs (15-line: surahs 46 and 48 start on line 1 of pages 502 and 511 and the page before ends on line 15 with words; 16-line: surahs 7, 17, 19, 23, 48, 81, 87, 95, 97, 99, 105 and 109 start on line 2). The data does not say where the banner goes in these 14 cases; the renderer has to draw the header as extra lines above slot 1 or shrink the line height for that page.
- One source defect is corrected while building: the API types the end sign of 2:181 as a word (position 14 of a 13-word ayah); `build_indopak.py` treats the last item of a verse that is the ayah number in Arabic-Indic digits as the end sign.
- Needed by the page renderer: a layout mode with 15 or 16 slots per page and 610 or 548 pages (`ScriptId.IndoPak` currently throws `UnsupportedOperationException`), the Unicode font above loaded once like QPC Hafs (one file, not one per page), words drawn right to left in `word` order with `text_nastaleeq`, and justified lines (the printed lines are full width).
- Terms: the font file's name table says "NOT FOR SALE, NOT FOR MODIFICATION, NOT FOR DISTRIBUTION OR NOT FOR DEVELOPMENT WITHOUT WRITTEN NOTICE BY QURANWBW.COM" (contact quranwbw@gmail.com). The Quran.com frontend repository hosts the file but states no licence for it. The layout data comes from the Quran.com API, which states no licence. QUL (qul.tarteel.ai) offers IndoPak layouts too (15-line Qudratullah, 16-line Taj, 13-line Taj and Qudratullah, KFGQPC Nastaleeq 15-line), but its downloads need a login (HTTP 401 without one) and its pages state no licence, so QUL was not used. The KFGQPC Nastaleeq font is hosted at fonts.qurancomplex.gov.sa, which resets connections from this network and from a Singapore VPN exit.

## Tajweed text
- `quran/tajweed.sqlite`: `tajweed_word(surah, ayah, position, markup)` (77,429 rows), `tajweed_rule(name, meaning)` (18 classes), `pack_info` (`skeleton_mismatches`, `repaired_words`, `rule_counts`). Source: Quran.com v4 `words[].text_uthmani_tajweed`.
- Markup: the only tags are `<rule class=NAME>` and `</rule>`, written exactly so; rules may nest (`<rule class=madda_normal><rule class=custom-alef-maksora>ٰ</rule></rule>`); colour a letter by its innermost rule except `custom-alef-maksora`, which only repairs the drawing of a dagger alef; a rule never crosses a word boundary; no entities and no other tags. Strip the tags to get the plain word. Classes and counts: ham_wasl 13,252, ikhafa 8,531, madda_normal 9,028, idgham_ghunnah 7,866, ghunnah 4,919, madda_permissible 4,541, slnt 4,159, qalaqah 3,834, madda_obligatory_mottasel 2,678, madda_obligatory_monfasel 2,488, laam_shamsiyah 2,608, idgham_wo_ghunnah 2,072, idgham_shafawi 1,664, custom-alef-maksora 1,377, iqlab 1,053, ikhafa_shafawi 992, madda_necessary 143, idgham_mutajanisayn 58, idgham_mutaqaribayn 13. No colours are stored; the app chooses them.
- The verse-level text (`/quran/verses/uthmani_tajweed`) draws some rules across two words and uses `<tajweed>` tags; the word-level field splits them, so the pack is built from the word-level field.
- One word is damaged in the source (32:3:3 holds `&gt;` and a dotless ba where the plain text has alef maksura) and is repaired; 201 words (0.26 percent) have a different consonant skeleton from `text_uthmani` because the tajweed text drops the alef maksora carrier of a dagger alef (for example ءَاتَـٰهُ against ءَاتَىٰهُ); they are listed in `pack_info.skeleton_mismatches`. The marks differ from `quran.sqlite` in places, so draw this text only with the QPC Hafs font and do not mix it with `text_uthmani` of the same word.
- The V4 tajweed font pack is the page-print version of the same colours; its manifest entry has `min_sdk: 33` because COLRv1 glyphs are drawn only from Android 13. On Android 12 and older, offer this text pack instead (coloured letters in Compose).
- Terms: same as the other Quran.com API data (no licence text).

## Fonts
- `fonts/qpc-hafs/UthmanicHafs1Ver18.ttf`: one Unicode font, for `word.text_uthmani`.
- `fonts/indopak/indopak-nastaleeq-waqf-lazim-v4.2.1.ttf`: one Unicode font for the IndoPak text; 0.34 MB.
- `fonts/v1/`, `fonts/v2/`, `fonts/v4-tajweed/`: `p1.ttf` .. `p604.ttf`, one font per page; page N's `code_v1` (V1) or `code_v2` (V2) is drawn with `pN.ttf`. V4 tajweed uses the V2 codes with colour (COLRv1) glyphs, which Android renders from 13 on.
- Source: `quran/quran.com-frontend-next`, commit `aff1a035`, `public/fonts/quran/hafs/{uthmanic_hafs, v1/ttf, v2/ttf, v4/colrv1/ttf}`. WOFF2 is not used (Android cannot load it); OpenType-SVG V4 is not used (not rendered on Android).
- Packs for download are `fonts/<script>.zip` (members `pN.ttf`); `fonts/fonts.json` lists names and sizes. Sizes: qpc-hafs 0.24 MB; v1 94.9 MB (zip 54.5); v2 208.1 MB (zip 137.1); v4-tajweed 167.0 MB (zip 68.9).

## Translations
- Ayah packs `translations/<id>.sqlite`: `translation(surah, ayah, ayah_from, ayah_to, text)`, primary key `(surah, ayah)`, 6,236 rows. The first release holds `en_saheeh`, `en_abdul_haleem`, `en_transliteration`, `bn_zakaria` (Quran.com resources 20, 85, 57, 213) and `bn_quantum` (built by `tools/build_quantum_pack.py`); the second catalogue adds 31 more, listed below, in `tools/pack_catalogue.py`. Resource 131 (The Clear Quran) is not in the public API, so there is no pack. The Quran.com resource list has no Arabic translation (only tafsirs), and `en_transliteration` (57) is its only transliteration; `Maududi (Roman Urdu)` is a translation in Latin letters. Resource 95 (Tafhim) and the Urdu tafsir resources 97, 151 and 156 carry commentary and are not built.
- New packs are read from `GET /quran/translations/<resource id>` (6,236 texts in reading order, no verse key); the first release packs keep the `/verses/by_chapter` path so their bytes do not change.
- Merged-range rule: when a publisher gives one passage for several ayahs, every ayah in the range has a row with the same `text` and the same `ayah_from`, `ayah_to`. Show the text once, labelled `ayah_from-ayah_to`, at the row where `ayah = ayah_from`. When `ayah_from = ayah_to = ayah` the row stands alone. The Quran.com packs never merge; `bn_quantum` does.
- Stripped markup: `<sup foot_note=ID>N</sup>` elements, Bangla `[১]` call-outs (with the space before them), and `<b>` tags. Square-bracket insertions such as `[Prophet]` stay.
- Known source defect: `bn_zakaria` 18:32 holds the section heading "পঞ্চম রুকু’", not a translation.
- Word transliteration `translations/en_transliteration_words.sqlite` (id `en_transliteration_words`, manifest kind `word_transliteration`, built by `tools/build_transliteration_pack.py`): `word_translation(surah, ayah, position, text)`, 77,429 rows, the text that `quran.sqlite` held in its `transliteration` column until the Quran text became the only content in the app. The Transliteration layer reads it; `en_transliteration` (whole ayah, resource 57) is the fallback when this pack is missing.
- Word packs `translations/wbw_<language>.sqlite` for en, bn, ur, hi, id and tr: `word_translation(surah, ayah, position, text)`, 77,429 rows, same key as `word`. Where the API has no text in the language it answers in English; such a word is stored as '' and `pack_info.empty_words` counts them (ur 280, hi 6,907, tr 6,890; en, bn, id none). The other languages of the API (fa, ta) were not built.
- Each pack except `bn_quantum` has `<id>.TERMS.md` beside it: source, resource id and name, retrieval date, translator, rights holder and the judgement of the table below.
- Terms judgement. `terms known`: yes = a public statement of terms applies to the text Awn ships; unclear = a statement exists but it is not certain it covers this text; no = no public statement granting redistribution was found. `publishable`: yes = public domain or permissive; conditional = only under stated conditions; no = not without the owner's permission. The maintainer decides what is released. The statement used is Tanzil's (tanzil.net/trans: "for non-commercial purposes only; otherwise obtain permission from the translator or the publisher", a link back when more than three translations are used); it binds a pack only when the Quran.com text is Tanzil's, which `tools/check_tanzil_provenance.py` measures (identical ayahs after removing punctuation and case: pickthall 6,231, yusuf_ali 6,219, junagarhi 6,145, jalandhari 6,232, elmalili 6,236, basmeih 6,231, kuliev 6,232, muntahab 6,236, abu_adel 6,236 of 6,236: these nine are "yes"; saheeh 3,349, hilali 1,800, diyanet 4,218, hamidullah 4,342, garcia 3,399, kemenag 320 and the transliteration 7 differ in footnotes, spelling or edition, so "unclear"). Packs with no Tanzil counterpart are "no". Sizes are bytes of the SQLite file.

| id | Quran.com resource | language | translator | rights holder | terms known | publishable | bytes |
|---|---|---|---|---|---|---|---|
| en_transliteration | 57 | English (Latin letters) | Transliteration | not stated | unclear | conditional | 811,008 |
| en_saheeh | 20 | English | Saheeh International | Dar Abul-Qasim (AbulQasim Publishing House), Jeddah | unclear | conditional | 1,105,920 |
| en_abdul_haleem | 85 | English | M.A.S. Abdel Haleem | Oxford University Press | no | no | 1,011,712 |
| en_pickthall | 19 | English | Mohammed Marmaduke William Pickthall (died 1936) | public domain | yes | yes | 1,052,672 |
| en_yusuf_ali | 22 | English | Abdullah Yusuf Ali (died 1953) | original 1934-37 text public domain; revised Amana editions copyrighted | yes | conditional | 1,130,496 |
| en_usmani | 84 | English | Mufti Taqi Usmani | Maktaba Ma'ariful Quran (The Noble Quran / Meanings of the Noble Quran) | no | no | 1,126,400 |
| en_hilali_khan | 203 | English | Muhammad Taqi-ud-Din al-Hilali and Muhammad Muhsin Khan | Darussalam Publishers (Noble Quran) | unclear | conditional | 1,384,448 |
| en_bridges | 149 | English | Fadel Soliman | Bridges Foundation | no | no | 1,064,960 |
| bn_zakaria | 213 | Bangla | Dr. Abu Bakr Muhammad Zakaria | King Fahd Quran Printing Complex edition (rights holder not stated by the source) | no | no | 2,535,424 |
| bn_taisirul | 161 | Bangla | Taisirul Quran | Tawheed Publication | no | no | 2,646,016 |
| bn_rawai_al_bayan | 162 | Bangla | Rawai Al-Bayan | Bayaan Foundation | no | no | 2,547,712 |
| bn_mujibur_rahman | 163 | Bangla | Sheikh Mujibur Rahman | Darussalam Publication | no | no | 2,531,328 |
| ur_junagarhi | 54 | Urdu | Maulana Muhammad Junagarhi (died 1941) | translator died 1941 | yes | conditional | 1,695,744 |
| ur_jalandhari | 234 | Urdu | Fateh Muhammad Jalandhari (died 1981) | not stated | yes | conditional | 1,687,552 |
| ur_bayan_ul_quran | 158 | Urdu | Dr. Israr Ahmad | Markazi Anjuman Khuddam-ul-Quran | no | no | 1,794,048 |
| ur_wahiduddin_khan | 819 | Urdu | Maulana Wahiduddin Khan | Goodword Books / Centre for Peace and Spirituality | no | no | 1,572,864 |
| ur_roman_maududi | 831 | Roman Urdu | Abul Ala Maududi | Islamic Publications, Lahore | no | no | 1,294,336 |
| hi_azizul_haque | 122 | Hindi | Maulana Azizul Haque al-Umari | not stated | no | no | 2,576,384 |
| id_kemenag | 33 | Indonesian | Indonesian Islamic Affairs Ministry (Kemenag) | Government of Indonesia, Ministry of Religious Affairs | unclear | conditional | 1,323,008 |
| id_king_fahad | 134 | Indonesian | King Fahad Quran Complex | King Fahd Quran Printing Complex | no | no | 1,376,256 |
| id_sabiq | 141 | Indonesian | The Sabiq Company | The Sabiq Company | no | no | 1,335,296 |
| tr_diyanet | 77 | Turkish | Diyanet Isleri (Presidency of Religious Affairs) | Diyanet Isleri Baskanligi | unclear | conditional | 1,232,896 |
| tr_elmalili | 52 | Turkish | Elmalili Hamdi Yazir (died 1942) | translator died 1942 | yes | conditional | 1,134,592 |
| tr_shaban_britch | 112 | Turkish | Shaban Britch | not stated | no | no | 1,048,576 |
| tr_muslim_shahin | 124 | Turkish | Muslim Shahin | not stated | no | no | 1,146,880 |
| fr_hamidullah | 31 | French | Muhammad Hamidullah (died 2002) | not stated | unclear | conditional | 1,167,360 |
| fr_montada | 136 | French | Montada Islamic Foundation | Montada Islamic Foundation | no | no | 1,212,416 |
| fr_rashid_maash | 779 | French | Rashid Maash | not stated | no | no | 1,236,992 |
| es_isa_garcia | 83 | Spanish | Muhammad Isa Garcia | not stated | unclear | conditional | 1,126,400 |
| es_montada | 140 | Spanish | Montada Islamic Foundation | Montada Islamic Foundation | no | no | 1,302,528 |
| es_noor | 199 | Spanish | Noor International Center | Noor International Center | no | no | 1,302,528 |
| ms_basmeih | 39 | Malay | Abdullah Muhammad Basmeih | Department of Islamic Development Malaysia (JAKIM) | yes | conditional | 1,716,224 |
| ru_kuliev | 45 | Russian | Elmir Kuliev | not stated | yes | conditional | 1,687,552 |
| ru_muntahab | 78 | Russian | Ministry of Awqaf, Egypt | Ministry of Awqaf, Egypt | yes | conditional | 3,727,360 |
| ru_abu_adel | 79 | Russian | Abu Adel | not stated | yes | conditional | 3,256,320 |


## Timings
- `timings/<reciter>.sqlite`: `segment(surah, ayah, position, start_ms, end_ms)` (milliseconds from the start of the ayah file) and `ayah_audio(surah, ayah, url, duration_ms, offset_ms)`. `offset_ms` is 0 in all packs. `duration_ms` is the end of the last segment (the API gives no file length) and NULL for Basfar.
- Reciters: `alafasy` (Quran.com recitation 7), `husary` (6), `husary_muallim` (12), `minshawi` (9), and from the second catalogue `sudais` (3), `shuraim` (10), `shatri` (4), `abdul_basit_murattal` (2), `abdul_basit_mujawwad` (1), `rifai` (5), `tablawi` (11), `minshawi_mujawwad` (8): word-level. `basfar`: ayah files only, no segments. Quran.com recitation 3 (Sudais) delivers segment numbers as strings; an ayah with an audio file but an empty segment list (Shuraim 12:76) keeps its file with a NULL duration and is listed as without word timing. Every other Quran.com recitation id of `GET /resources/recitations` was built.
- Quran.com timed Husary murattal against `Husary_64kbps`, not the 128 kbps folder the reciter manifest plays; the manifest field `timing_audio_folder` names the timed folder. Offsets can differ by tens of milliseconds between encodes.
- A segment spanning several words is split equally between them (`pack_info.interpolated_words`). An ayah whose segments miss a word, run backwards or overlap gets no segment rows; `pack_info.ayahs_without_word_timing` lists them (alafasy 28, husary 19, husary_muallim 28, minshawi 35, sudais 30, shuraim 52, shatri 33, abdul_basit_murattal 34, abdul_basit_mujawwad 128, rifai 40, tablawi 55, minshawi_mujawwad 88). Play those ayahs without word seek.
- Basfar: Quran.com's surah-file timestamps drift (2:286 starts after the end of the file), so the pack lists EveryAyah `Abdullah_Basfar_192kbps` URLs. The mirror returns 404 for the 64 kbps folder; it is on `everyayah.com/data` only.

## Manifests
- `manifests/scripts.json` and `translations.json` hold `packs`; `reciters.json` holds `reciters`. Each file has top-level `format_version: 1`.
- The second catalogue keeps `format_version: 1` and adds only optional fields: scripts `min_sdk` (33 for v4-tajweed), `requires` (ids), `script_id`, `layouts`; font zips `installed_bytes` (unpacked size); translations `kind` "transliteration" for `en_transliteration` (it was "ayah" in data-v1; the repository must learn the kind), `language_name`, `content` ("translation", "transliteration", "word_translation"), `rights_holder`, `terms_known`, `publishable`; reciters `style`, `riwaya`, `audio_bytes`, `ayah_audio_bytes`, `estimated`, `ayah_base_url`. New script entries: `indopak-layouts` (text, layouts 15 and 16), `indopak` (font, requires indopak-layouts, script_id indopak) and `tajweed-text` (text). `build_manifests.py --publishable=yes,conditional --exclude=id,id` writes the release manifests without held packs.
- Every pack entry has `format_version`, `file` (path relative to `awn-data/next/`, except fonts, which read `fonts/...` and live in `awn-data/fonts/`), `sha256`, `bytes`, `credit`, `source_url`; also `id`, `kind`, `name`. Translation entries add `language`, `quran_com_resource_id`, `terms_file`. Font entries add `glyph_font_per_page`, and zips add `archive_format`, `member_pattern`, `member_count`.
- Reciter entries add `quranicaudio_id`, `quranicaudio_path`, `surah_url_pattern` (`{SSS}`), `everyayah_folder`, `ayah_url_pattern` (`{SSS}{AAA}`), `fallback_domain_suffix` (`.quranicaudio.com` to `.quranicaudio.org`), `timing_pack`, `timing_granularity`, `timing_audio_folder`, `quran_com_recitation_id` and `word_by_word` (true for the 12 reciters with Quran.com segments). A reciter without a timing pack (every reciter without Quran.com segments, such as Ghamdi) has `timing_pack`, `file`, `sha256`, `bytes` null.

## Reciter catalogue
- 34 reciters in `tools/reciter_catalogue.py`, all Hafs from Asim: 6 of the first release and 28 new. Sources: `quranicaudio.com/api/qaris` (surah files `https://download.quranicaudio.com/quran/<path><SSS>.mp3`), `everyayah.com/data/recitations.js` (ayah files `<base><folder>/<SSS><AAA>.mp3`) and `GET /resources/recitations` (segments). `python3 tools/reciter_catalogue.py` sends HEAD requests for all 114 surah files and for 14 sample ayah files (surahs 1, 108, 112) of every reciter and writes `awn-data/reciter_probe.json`; all 114 surah files and all 14 ayah files answered 200 for the 33 reciters with an EveryAyah folder on 2026-10-21 (QuranicAudio `maher_256/` lacks surah 5, so Muaiqly uses `maher_almu3aiqly/year1440/`; no audio was downloaded).
- The QuranicAudio mirror of EveryAyah lacks some folders, so 14 reciters' ayah files are served by `https://everyayah.com/data/` instead (manifest `ayah_base_url`; the `.quranicaudio.com` to `.org` fallback does not apply there). Ayah files of several reciters are lower bitrate than the surah files (Ghamdi 40 kbps), so the two size columns differ.
- `audio_bytes` is the exact sum of the Content-Length of the 114 surah files. `ayah_audio_bytes` is an estimate: the surah total times the ratio of the sample ayah files to the same surahs' files; manifest `estimated: true` marks it.

| id | name | style | word timing | surah files (114) | ayah files (estimate) | ayah files host |
|---|---|---|---|---|---|---|
| alafasy | Mishari Rashid al-Afasy | murattal | word | 1.72 GB | 1.36 GB | mirrors.quranicaudio.com |
| husary | Mahmoud Khalil Al-Husary (Murattal) | murattal | word | 2.47 GB | 1.93 GB | mirrors.quranicaudio.com |
| husary_muallim | Mahmoud Khalil Al-Husary (Muallim) | muallim | word | 2.64 GB | 2.63 GB | mirrors.quranicaudio.com |
| minshawi | Mohamed Siddiq al-Minshawi (Murattal) | murattal | word | 1.67 GB | 1.31 GB | mirrors.quranicaudio.com |
| basfar | Abdullah Basfar | murattal | none | 1.47 GB | 1.92 GB | mirrors.quranicaudio.com |
| sudais | Abdur-Rahman as-Sudais | murattal | word | 1.68 GB | 1.41 GB | mirrors.quranicaudio.com |
| shuraim | Sa'ud ash-Shuraim | murattal | word | 1.05 GB | 0.88 GB | mirrors.quranicaudio.com |
| ghamdi | Saad al-Ghamdi | murattal | none | 2.85 GB | 0.35 GB | mirrors.quranicaudio.com |
| ajmi | Ahmed ibn Ali al-Ajmi | murattal | none | 1.90 GB | 1.32 GB | everyayah.com/data |
| muaiqly | Maher al-Muaiqly | murattal | none | 1.41 GB | 0.78 GB | everyayah.com/data |
| shatri | Abu Bakr al-Shatri | murattal | word | 1.50 GB | 1.08 GB | mirrors.quranicaudio.com |
| abdul_basit_murattal | Abdul Basit Abdus-Samad (Murattal) | murattal | word | 2.69 GB | 2.23 GB | mirrors.quranicaudio.com |
| abdul_basit_mujawwad | Abdul Basit Abdus-Samad (Mujawwad) | mujawwad | word | 3.51 GB | 2.60 GB | mirrors.quranicaudio.com |
| hudhaify | Ali al-Hudhaify | murattal | none | 2.55 GB | 1.39 GB | mirrors.quranicaudio.com |
| ayyub | Muhammad Ayyub | murattal | none | 1.86 GB | 1.40 GB | mirrors.quranicaudio.com |
| rifai | Hani ar-Rifai | murattal | word | 2.18 GB | 1.44 GB | mirrors.quranicaudio.com |
| tablawi | Mohamed al-Tablawi | murattal | word | 1.71 GB | 1.36 GB | mirrors.quranicaudio.com |
| minshawi_mujawwad | Mohamed Siddiq al-Minshawi (Mujawwad) | mujawwad | word | 4.78 GB | 2.61 GB | mirrors.quranicaudio.com |
| husary_mujawwad | Mahmoud Khalil Al-Husary (Mujawwad) | mujawwad | none | 3.57 GB | 3.11 GB | mirrors.quranicaudio.com |
| jibreel | Muhammad Jibreel | murattal | none | 1.51 GB | 1.14 GB | mirrors.quranicaudio.com |
| budair | Salah al-Budair | murattal | none | 1.79 GB | 1.28 GB | everyayah.com/data |
| qahtani | Khalid al-Qahtani | murattal | none | 2.36 GB | 1.79 GB | everyayah.com/data |
| fares_abbad | Fares Abbad | murattal | none | 1.97 GB | 0.50 GB | everyayah.com/data |
| qatami | Nasser al-Qatami | murattal | none | 1.19 GB | 1.15 GB | everyayah.com/data |
| dussary | Yasser ad-Dussary | murattal | none | 1.49 GB | 1.44 GB | mirrors.quranicaudio.com |
| juhani | Abdullah Awad al-Juhani | murattal | none | 1.17 GB | 1.01 GB | everyayah.com/data |
| mustafa_ismail | Mustafa Ismail | murattal | none | 1.74 GB | 0.66 GB | everyayah.com/data |
| matroud | Abdullah Matroud | murattal | none | 1.61 GB | 1.30 GB | everyayah.com/data |
| ali_jaber | Ali Jaber | murattal | none | 1.51 GB | 0.37 GB | everyayah.com/data |
| qasim | Abdul Muhsin al-Qasim | murattal | none | 1.66 GB | 1.32 GB | everyayah.com/data |
| akhdar | Ibrahim al-Akhdar | murattal | none | 0.59 GB | 0.38 GB | everyayah.com/data |
| sahl_yasin | Sahl Yasin | murattal | none | 1.40 GB | 1.18 GB | everyayah.com/data |
| bukhatir | Salah Bukhatir | murattal | none | 1.36 GB | 1.13 GB | everyayah.com/data |

## Verification in the app
1. Download `manifests/*.json` and accept only `format_version` at or below the app's supported version.
2. Download the pack at `file`; reject it if its size differs from `bytes` or its SHA-256 differs from `sha256`.
3. Open SQLite packs and read `pack_info.format_version`; reject a value above the supported one.
4. Show `credit` and `source_url` on the About screen; a CI test asserts every entry has both.
