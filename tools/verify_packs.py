#!/usr/bin/env python3
"""Check every built pack in awn-data/ against the pack contract in docs/packs.md.

Prints one PASS or FAIL line per check and exits with status 1 when any check fails.
The packs to expect come from tools/pack_catalogue.py and tools/reciter_catalogue.py, so a pack added to
a catalogue is checked without editing this file.

AWN_BASELINE_DATA names an older data folder (default: awn-data/published, the files the app pins). When its manifests
exist, every pack that is in both folders must have the same SHA-256 and size, so a rebuild cannot change
a published pack unnoticed.

Usage: python3 tools/verify_packs.py
"""

import collections
import hashlib
import json
import os
import re
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import data_paths
import pack_catalogue
import quran_com
import reciter_catalogue

DATA_ROOT = quran_com.DATA_ROOT
BASELINE_ROOT = os.environ.get("AWN_BASELINE_DATA", str(data_paths.published()))
FONTS_ROOT = quran_com.FONTS_ROOT
ISO_DOCUMENTED_TERMS = {"yes", "no", "unclear"}
DOCUMENTED_PUBLISHABLE = {"yes", "conditional", "no"}
CHAPTERS, AYAHS, PAGES = quran_com.CHAPTER_COUNT, quran_com.AYAH_COUNT, quran_com.PAGE_COUNT
WORDS = 77429
SHORT_PAGES = (1, 2)  # Al-Fatihah and the start of Al-Baqarah are printed with 8 lines
TRANSLATION_FILES = [pack["id"] for pack in pack_catalogue.TRANSLATIONS_AND_TRANSLITERATIONS]
WORD_FILES = [pack["id"] for pack in pack_catalogue.WORD_PACKS]
# The transliteration of each word, split out of quran.sqlite; same table as the word packs.
WORD_TRANSLITERATION_FILES = ["en_transliteration_words"]
TIMING_FILES = {reciter["id"]: "word" for reciter in reciter_catalogue.RECITERS if reciter["quran_com_recitation_id"]}
TIMING_FILES["basfar"] = "ayah"
FONT_SCRIPTS = ["qpc-hafs", "indopak", "v1", "v2", "v4-tajweed"]
SINGLE_FILE_FONTS = ("qpc-hafs", "indopak")
INDOPAK_LAYOUTS = {"15": (610, 15), "16": (548, 16)}  # suffix -> (pages, lines per page)

failures = []


def check(condition, description):
    print(("PASS " if condition else "FAIL ") + description)
    if not condition:
        failures.append(description)


def open_pack(*parts):
    path = os.path.join(DATA_ROOT, *parts)
    if not os.path.exists(path):
        check(False, f"{os.path.join(*parts)} exists")
        return None
    return sqlite3.connect(path)


def scalar(connection, query):
    return connection.execute(query).fetchone()[0]


def sha256_of_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def has_readable_format_version(path):
    """True when the file has pack_info with an integer format_version, as the app requires on install."""
    connection = sqlite3.connect(path)
    try:
        row = connection.execute("SELECT value FROM pack_info WHERE key = 'format_version'").fetchone()
    except sqlite3.DatabaseError:
        return False
    finally:
        connection.close()
    return row is not None and row[0].isdigit()


def verify_pack_info():
    """The app refuses a pack database without pack_info.format_version and never retries it."""
    for folder in ("quran", "translations", "timings"):
        folder_path = os.path.join(DATA_ROOT, folder)
        if not os.path.isdir(folder_path):
            continue
        for name in sorted(os.listdir(folder_path)):
            if name.endswith(".sqlite"):
                check(has_readable_format_version(os.path.join(folder_path, name)),
                      f"{folder}/{name} has a readable pack_info format_version")


def verify_quran_text():
    connection = open_pack("quran", "quran.sqlite")
    if connection is None:
        return {}
    check(scalar(connection, "SELECT COUNT(*) FROM chapter") == CHAPTERS, "quran.sqlite has 114 chapters")
    check(scalar(connection, "SELECT COUNT(*) FROM ayah") == AYAHS, "quran.sqlite has 6236 ayahs")
    check(scalar(connection, "SELECT COUNT(*) FROM word") == WORDS, "quran.sqlite has 77429 words")
    check(scalar(connection, "SELECT COUNT(*) FROM ayah_marker") == AYAHS, "quran.sqlite has 6236 ayah-end markers")
    mismatched_chapters = scalar(connection, """
        SELECT COUNT(*) FROM chapter c WHERE c.verses_count !=
        (SELECT COUNT(*) FROM ayah a WHERE a.surah = c.number)""")
    check(mismatched_chapters == 0, "chapter.verses_count equals the ayah rows of each chapter")
    broken_positions = scalar(connection, """
        SELECT COUNT(*) FROM (SELECT surah, ayah FROM word GROUP BY surah, ayah
                              HAVING MIN(position) != 1 OR MAX(position) != COUNT(*))""")
    check(broken_positions == 0, "word positions run 1..n with no gap in every ayah")
    ayahs_without_words = scalar(connection, """
        SELECT COUNT(*) FROM ayah a WHERE NOT EXISTS
        (SELECT 1 FROM word w WHERE w.surah = a.surah AND w.ayah = a.ayah)""")
    check(ayahs_without_words == 0, "every ayah has at least one word")
    bad_markers = scalar(connection, """
        SELECT COUNT(*) FROM ayah_marker m JOIN ayah a USING (surah, ayah)
        WHERE m.page_v1 < a.page_v1 OR m.page_v2 < a.page_v2""")
    check(bad_markers == 0, "no ayah-end marker lies before its ayah starts")
    empty_codes = scalar(connection, "SELECT COUNT(*) FROM word WHERE code_v1 = '' OR code_v2 = '' OR text_uthmani = ''")
    check(empty_codes == 0, "no word has an empty glyph code or text")

    for version in ("v1", "v2"):
        page_column, line_column = f"page_{version}", f"line_{version}"
        pages = [row[0] for row in connection.execute(
            f"SELECT DISTINCT {page_column} FROM word ORDER BY {page_column}")]
        check(pages == list(range(1, PAGES + 1)), f"{page_column} runs 1..604 without a gap")
        ayah_pages = scalar(connection, f"SELECT COUNT(DISTINCT {page_column}) FROM ayah")
        check(ayah_pages <= PAGES and scalar(connection, f"SELECT MIN({page_column}) FROM ayah") == 1,
              f"ayah.{page_column} starts at page 1")
        highest_line = dict(connection.execute(f"""
            SELECT page, MAX(line) FROM (
              SELECT {page_column} AS page, {line_column} AS line FROM word UNION ALL
              SELECT {page_column}, {line_column} FROM ayah_marker) GROUP BY page"""))
        histogram = collections.Counter(highest_line.values())
        print(f"     highest line number per page, {version} (line: pages): {dict(sorted(histogram.items()))}")
        check(all(highest_line[page] == 8 for page in SHORT_PAGES), f"{version} pages 1 and 2 end at line 8")
        other_pages_ok = all(highest_line[page] in (14, 15) for page in range(3, PAGES + 1))
        check(other_pages_ok, f"{version} every other page ends at line 14 or 15")
        check(scalar(connection, f"SELECT MIN({line_column}) FROM word") >= 1
              and scalar(connection, f"SELECT MAX({line_column}) FROM word") <= 15, f"{version} lines lie in 1..15")
        fifteen = sum(1 for page in range(3, PAGES + 1) if highest_line[page] == 15)
        print(f"     {version}: {fifteen} of {PAGES - 2} pages from 3 on reach line 15")
    word_counts = {(s, a): n for s, a, n in connection.execute(
        "SELECT surah, ayah, COUNT(*) FROM word GROUP BY surah, ayah")}
    connection.close()
    return word_counts


def verify_fonts():
    fonts_root = FONTS_ROOT
    manifest_path = os.path.join(fonts_root, "fonts.json")
    if not os.path.exists(manifest_path):
        check(False, "fonts/fonts.json exists")
        return
    with open(manifest_path, encoding="utf-8") as handle:
        manifest = json.load(handle)
    check(sorted(manifest["scripts"]) == sorted(FONT_SCRIPTS),
          "fonts.json lists qpc-hafs, indopak, v1, v2 and v4-tajweed")
    for script in FONT_SCRIPTS:
        entry = manifest["scripts"][script]
        expected_count = 1 if script in SINGLE_FILE_FONTS else PAGES
        check(len(entry["files"]) == expected_count, f"{script} lists {expected_count} font files")
        sizes_match = all(
            os.path.exists(os.path.join(fonts_root, script, item["name"]))
            and os.path.getsize(os.path.join(fonts_root, script, item["name"])) == item["bytes"]
            for item in entry["files"])
        check(sizes_match, f"{script} files exist with the sizes in fonts.json")
        magic_ok = True
        for item in entry["files"]:
            with open(os.path.join(fonts_root, script, item["name"]), "rb") as handle:
                magic_ok = magic_ok and handle.read(4) in (b"\x00\x01\x00\x00", b"OTTO", b"true")
        check(magic_ok, f"{script} files start with a TrueType or OpenType header")
        if script not in SINGLE_FILE_FONTS:
            names = {item["name"] for item in entry["files"]}
            check(names == {f"p{page}.ttf" for page in range(1, PAGES + 1)}, f"{script} has p1..p604")
        print(f"     {script}: {entry['total_bytes']:,} bytes")


def verify_translations(word_counts):
    for stem in TRANSLATION_FILES:
        connection = open_pack("translations", stem + ".sqlite")
        if connection is None:
            continue
        check(scalar(connection, "SELECT COUNT(*) FROM translation") == AYAHS, f"{stem} has 6236 rows")
        check(scalar(connection, "SELECT COUNT(*) FROM translation WHERE ayah_from != ayah OR ayah_to != ayah") == 0,
              f"{stem} has ayah_from = ayah_to = ayah")
        check(scalar(connection, "SELECT COUNT(*) FROM translation WHERE TRIM(text) = ''") == 0, f"{stem} has no empty text")
        check(scalar(connection, "SELECT COUNT(*) FROM translation WHERE text LIKE '%<%>%' OR instr(text, 'foot_note') > 0") == 0,
              f"{stem} has no markup left")
        keys = {(s, a) for s, a in connection.execute("SELECT surah, ayah FROM translation")}
        check(keys == set(word_counts), f"{stem} covers exactly the ayahs of quran.sqlite")
        connection.close()
    quantum = os.path.join(DATA_ROOT, "translations", "bn_quantum.sqlite")
    if os.path.exists(quantum):
        connection = sqlite3.connect(quantum)
        check(scalar(connection, "SELECT COUNT(*) FROM translation") == AYAHS, "bn_quantum has 6236 rows")
        check(scalar(connection, "SELECT COUNT(*) FROM translation WHERE ayah NOT BETWEEN ayah_from AND ayah_to") == 0,
              "bn_quantum ranges contain their ayah")
        connection.close()
    for stem in WORD_FILES + WORD_TRANSLITERATION_FILES:
        connection = open_pack("translations", stem + ".sqlite")
        if connection is None:
            continue
        check(scalar(connection, "SELECT COUNT(*) FROM word_translation") == WORDS, f"{stem} has 77429 rows")
        counts = {(s, a): n for s, a, n in connection.execute(
            "SELECT surah, ayah, COUNT(*) FROM word_translation GROUP BY surah, ayah")}
        check(counts == word_counts, f"{stem} word count equals quran.sqlite in all 6236 ayahs")
        declared_empty = int(dict(connection.execute("SELECT key, value FROM pack_info")).get("empty_words", 0))
        check(scalar(connection, "SELECT COUNT(*) FROM word_translation WHERE TRIM(text) = ''") == declared_empty,
              f"{stem} has exactly the {declared_empty} empty words that pack_info.empty_words declares")
        connection.close()
    for stem in TRANSLATION_FILES + WORD_FILES + WORD_TRANSLITERATION_FILES:
        terms = os.path.join(DATA_ROOT, "translations", stem + ".TERMS.md")
        has_terms = os.path.exists(terms)
        if has_terms:
            with open(terms, encoding="utf-8") as handle:
                text = handle.read()
            has_terms = "Source:" in text and "Retrieved:" in text
        check(has_terms, f"{stem}.TERMS.md names the source and retrieval date")


def verify_indopak(word_counts):
    connection = open_pack("quran", "indopak.sqlite")
    if connection is None:
        return
    check(scalar(connection, "SELECT COUNT(*) FROM word") == WORDS, "indopak.sqlite has 77429 words")
    keys = {(s, a, p) for s, a, p in connection.execute("SELECT surah, ayah, position FROM word")}
    quran = sqlite3.connect(os.path.join(DATA_ROOT, "quran", "quran.sqlite"))
    check(keys == {(s, a, p) for s, a, p in quran.execute("SELECT surah, ayah, position FROM word")},
          "indopak words have the same (surah, ayah, position) keys as quran.sqlite")
    quran.close()
    check(scalar(connection, "SELECT COUNT(*) FROM ayah_marker") == AYAHS, "indopak.sqlite has 6236 ayah-end markers")
    check(scalar(connection, "SELECT COUNT(*) FROM word WHERE text_indopak = '' OR text_nastaleeq = ''") == 0,
          "indopak words have both texts")
    check(scalar(connection, "SELECT COUNT(*) FROM ayah_marker WHERE end_sign = ''") == 0, "every ayah has an end sign string")
    for suffix, (pages, lines) in INDOPAK_LAYOUTS.items():
        page_column, line_column = f"page_{suffix}", f"line_{suffix}"
        distinct_pages = [row[0] for row in connection.execute(
            f"SELECT DISTINCT {page_column} FROM word ORDER BY {page_column}")]
        check(distinct_pages == list(range(1, pages + 1)), f"{page_column} runs 1..{pages} without a gap")
        check(scalar(connection, f"SELECT MIN({line_column}) FROM word") >= 1
              and scalar(connection, f"SELECT MAX({line_column}) FROM word") <= lines,
              f"{line_column} lies in 1..{lines}")
        backwards = 0
        previous = (0, 0)
        for page, line in connection.execute(
                f"SELECT {page_column}, {line_column} FROM word ORDER BY surah, ayah, position"):
            backwards += (page, line) < previous
            previous = (page, line)
        check(backwards == 0, f"{suffix}-line print: words never go back in page and line")
        check(scalar(connection, f"""
            SELECT COUNT(*) FROM ayah_marker m JOIN
              (SELECT surah, ayah, MAX({page_column} * 100 + {line_column}) AS last FROM word GROUP BY surah, ayah) w
              USING (surah, ayah) WHERE m.{page_column} * 100 + m.{line_column} < w.last""") == 0,
              f"{suffix}-line print: no end sign lies before the last word of its ayah")
    info = dict(connection.execute("SELECT key, value FROM pack_info"))
    check(info.get("pages_15") == "610" and info.get("pages_16") == "548", "pack_info records the page counts")
    connection.close()


def verify_tajweed(word_counts):
    connection = open_pack("quran", "tajweed.sqlite")
    if connection is None:
        return
    check(scalar(connection, "SELECT COUNT(*) FROM tajweed_word") == WORDS, "tajweed.sqlite has 77429 words")
    counts = {(s, a): n for s, a, n in connection.execute(
        "SELECT surah, ayah, COUNT(*) FROM tajweed_word GROUP BY surah, ayah")}
    check(counts == word_counts, "tajweed word count equals quran.sqlite in all 6236 ayahs")
    rule_names = {name for (name,) in connection.execute("SELECT name FROM tajweed_rule")}
    tag = re.compile(r"</?rule(?: class=([a-z_-]+))?>")
    unknown_classes, bad_markup, depth_errors = set(), 0, 0
    for (markup,) in connection.execute("SELECT markup FROM tajweed_word"):
        leftover = tag.sub("", markup)
        bad_markup += ("<" in leftover or ">" in leftover or "&" in leftover or " " in leftover or leftover == "")
        depth = 0
        for match in re.finditer(r"</?rule[^>]*>", markup):
            depth += -1 if match.group(0) == "</rule>" else 1
            if depth < 0:
                break
        depth_errors += depth != 0
        unknown_classes.update(name for name in tag.findall(markup) if name and name not in rule_names)
    check(bad_markup == 0, "tajweed markup holds only <rule class=NAME> tags around letters")
    check(depth_errors == 0, "tajweed rule tags are balanced in every word")
    check(not unknown_classes, f"every tajweed class is described in tajweed_rule {sorted(unknown_classes)}")
    info = dict(connection.execute("SELECT key, value FROM pack_info"))
    mismatches = [key for key in info["skeleton_mismatches"].split(",") if key]
    check(len(mismatches) <= 250,
          f"at most 250 words differ from text_uthmani in their consonants (has {len(mismatches)}, listed in pack_info)")
    connection.close()


def verify_timings(word_counts):
    for stem, granularity in TIMING_FILES.items():
        connection = open_pack("timings", stem + ".sqlite")
        if connection is None:
            continue
        check(scalar(connection, "SELECT COUNT(*) FROM ayah_audio") == AYAHS, f"{stem} has 6236 ayah_audio rows")
        check(scalar(connection, "SELECT COUNT(*) FROM ayah_audio WHERE url NOT LIKE 'https://%' OR duration_ms <= 0") == 0,
              f"{stem} ayah_audio has https urls and no non-positive duration")
        expected_name = "SELECT COUNT(*) FROM ayah_audio WHERE url NOT LIKE '%/' || printf('%03d%03d', surah, ayah) || '.mp3'"
        check(scalar(connection, expected_name) == 0, f"{stem} urls end in SSSAAA.mp3 for their ayah")
        info = dict(connection.execute("SELECT key, value FROM pack_info"))
        check(info.get("granularity") == granularity, f"{stem} granularity is {granularity}-level")
        segments = scalar(connection, "SELECT COUNT(*) FROM segment")
        if granularity == "word":
            counts = {(s, a): n for s, a, n in connection.execute(
                "SELECT surah, ayah, COUNT(*) FROM segment GROUP BY surah, ayah")}
            skipped = {tuple(int(part) for part in key.split(":"))
                       for key in info["ayahs_without_word_timing"].split(",") if key}
            expected = {key: n for key, n in word_counts.items() if key not in skipped}
            check(counts == expected, f"{stem} has one segment per word in every ayah except the {len(skipped)} listed")
            check(len(skipped) <= 160,
                  f"{stem} lists at most 160 ayahs (2.6 percent) without word timing (has {len(skipped)})")
            check(scalar(connection, "SELECT COUNT(*) FROM segment WHERE end_ms <= start_ms OR start_ms < 0") == 0,
                  f"{stem} segments have 0 <= start_ms < end_ms")
            overlaps = scalar(connection, """
                SELECT COUNT(*) FROM segment a JOIN segment b
                ON a.surah = b.surah AND a.ayah = b.ayah AND b.position = a.position + 1
                WHERE b.start_ms < a.end_ms""")
            check(overlaps == 0, f"{stem} segments do not overlap the next word")
        else:
            check(segments == 0, f"{stem} has no word segments")
        connection.close()


def load_manifest(directory, name, key):
    path = os.path.join(directory, "manifests", name)
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)[key]


def verify_manifests():
    expected_keys = {"scripts.json": "packs", "translations.json": "packs", "reciters.json": "reciters"}
    for name, key in expected_keys.items():
        path = os.path.join(DATA_ROOT, "manifests", name)
        if not os.path.exists(path):
            check(False, f"manifests/{name} exists")
            continue
        with open(path, encoding="utf-8") as handle:
            manifest = json.load(handle)
        check(manifest.get("format_version") == 1, f"{name} has format_version 1")
        entries = manifest[key]
        check(len({entry["id"] for entry in entries}) == len(entries), f"{name} has no duplicate id")
        all_fields = all(entry.get("format_version") == 1 and entry.get("credit") and entry.get("source_url")
                         for entry in entries)
        check(all_fields, f"{name} entries have format_version 1, credit and source_url")
        for entry in entries:
            if entry["file"] is None:
                continue
            path_on_disk = str(data_paths.pack_file_path(entry["file"]))
            ok = (os.path.exists(path_on_disk) and os.path.getsize(path_on_disk) == entry["bytes"]
                  and sha256_of_file(path_on_disk) == entry["sha256"])
            check(ok, f"{name}: {entry['file']} matches its sha256 and byte count")
        if name == "scripts.json":
            by_id = {entry["id"]: entry for entry in entries}
            check(set(by_id) == {"quran-text", "qpc-hafs", "v1", "v2", "v4-tajweed", "indopak-layouts", "indopak-15",
                                 "tajweed-text"},
                  "scripts.json lists the text packs and the font scripts")
            check(all(by_id[i]["installed_bytes"] > by_id[i]["bytes"] for i in ("v1", "v2", "v4-tajweed")),
                  "font zips state installed_bytes")
            check(by_id["v4-tajweed"].get("min_sdk") == 33, "v4-tajweed has min_sdk 33 (COLRv1 needs Android 13)")
            check(all("min_sdk" not in by_id[i] for i in by_id if i != "v4-tajweed"), "only v4-tajweed has min_sdk")
            check(all(by_id[i].get("requires") == ["indopak-layouts"] for i in ("indopak-15",))
                  and by_id["indopak-layouts"].get("requires") == ["quran-text"],
                  "the IndoPak print requires indopak-layouts, which requires quran-text")
            check(all(by_id[i].get("download_url", "").startswith("https://") for i in ("indopak-15",))
                  and all("download_url" not in by_id[i] for i in by_id if i != "indopak-15"),
                  "only the IndoPak font is downloaded from another host")
            check([(by_id[i]["lines_per_page"], by_id[i]["pages"]) for i in ("indopak-15",)]
                  == [(15, 610)], "the IndoPak print states its lines per page and pages")
            layouts = {layout["id"]: (layout["pages"], layout["lines_per_page"])
                       for layout in by_id["indopak-layouts"]["layouts"]}
            check(layouts == INDOPAK_LAYOUTS, "indopak-layouts lists the layouts the file holds")
        if name == "translations.json":
            expected_ids = set(TRANSLATION_FILES) | set(WORD_FILES) | set(WORD_TRANSLITERATION_FILES)
            ids = {entry["id"] for entry in entries}
            check(expected_ids <= ids, "translations.json lists every catalogue pack")
            check({e["id"] for e in entries if e["kind"] == "transliteration"} == {"en_transliteration"},
                  "the transliteration pack has kind transliteration")
            judged = [entry for entry in entries if entry["kind"] in ("ayah", "transliteration") and entry["id"] != "bn_quantum"]
            check(all(entry.get("terms_known") in ISO_DOCUMENTED_TERMS and entry.get("publishable") in DOCUMENTED_PUBLISHABLE
                      and entry.get("rights_holder") for entry in judged),
                  "every ayah pack states rights holder, terms_known and publishable")
            check(all(entry.get("language") and entry.get("language_name") for entry in entries if entry["id"] != "bn_quantum"),
                  "every pack has language and language_name")
            check(all(entry.get("terms_file") and os.path.exists(data_paths.pack_file_path(entry["terms_file"]))
                      for entry in entries if entry["id"] != "bn_quantum"), "every pack has its TERMS.md")
        if name == "reciters.json":
            flags = {e["id"]: e["word_by_word"] for e in entries}
            expected_flags = {r["id"]: r["quran_com_recitation_id"] is not None for r in reciter_catalogue.RECITERS}
            check(flags == expected_flags, "reciters.json word_by_word flags follow the catalogue")
            raad = next(e for e in entries if e["id"] == "raad_al_kurdi")
            check(raad["timing_pack"] is None and raad["quranicaudio_path"] == "raad_mohammad_al_kurdi/mp3/",
                  "Raad al-Kurdi has surah files only")
            check(all(e["quranicaudio_path"] and e["surah_url_pattern"] for e in entries),
                  "every reciter has a QuranicAudio path and surah url pattern")
            check(all(e["timing_pack"] == (e["id"] if e["file"] else None) for e in entries),
                  "a reciter has a timing_pack exactly when it has a timing file")
            check(all(e.get("riwaya") == "hafs" and e.get("style") for e in entries), "every reciter states style and riwaya")
            check(all(e.get("audio_bytes", 0) > 10 ** 8 for e in entries), "every reciter has a measured audio_bytes")
            ayah_reciters = [e for e in entries if e["everyayah_folder"]]
            check(all(e["ayah_url_pattern"] and e.get("ayah_audio_bytes") and e.get("estimated") is True
                      for e in ayah_reciters), "every reciter with an EveryAyah folder has an estimated ayah_audio_bytes")


def verify_against_baseline():
    """Packs that exist in the baseline data folder must be byte-identical here."""
    if os.path.abspath(BASELINE_ROOT) == os.path.abspath(DATA_ROOT):
        return
    for name, key in (("scripts.json", "packs"), ("translations.json", "packs"), ("reciters.json", "reciters")):
        baseline = load_manifest(BASELINE_ROOT, name, key)
        current = load_manifest(DATA_ROOT, name, key)
        if baseline is None or current is None:
            continue
        current_by_id = {entry["id"]: entry for entry in current}
        for old in baseline:
            if old["file"] is None or old["id"] not in current_by_id:
                continue
            new = current_by_id[old["id"]]
            check((new["sha256"], new["bytes"]) == (old["sha256"], old["bytes"]),
                  f"{old['id']} is byte-identical to the baseline data folder")


def main():
    word_counts = verify_quran_text()
    verify_pack_info()
    verify_fonts()
    verify_indopak(word_counts)
    verify_tajweed(word_counts)
    verify_translations(word_counts)
    verify_timings(word_counts)
    verify_manifests()
    verify_against_baseline()
    print()
    if failures:
        print(f"{len(failures)} check(s) failed")
        sys.exit(1)
    print("all checks passed")


if __name__ == "__main__":
    main()
