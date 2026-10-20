#!/usr/bin/env python3
"""Build the ayah translation packs and the word-by-word packs from the Quran.com API v4.

Output under awn-data/translations/, one SQLite file per pack plus a TERMS.md
beside each (<pack>.TERMS.md):
  en_saheeh.sqlite           resource 20   Saheeh International
  en_abdul_haleem.sqlite     resource 85   M.A.S. Abdel Haleem
  en_transliteration.sqlite  resource 57   Transliteration
  bn_zakaria.sqlite          resource 213  Dr. Abu Bakr Muhammad Zakaria
  wbw_<language>.sqlite      word-by-word packs (English, Bangla, Urdu, Hindi, Indonesian, Turkish)
  <id>.sqlite                every other pack of tools/pack_catalogue.py TRANSLATION_PACKS

Resource 131 (The Clear Quran, Dr. Mustafa Khattab) is not built: the public
API lists no such resource and answers translations=131 with no translation, so
there is no en_clear_quran pack.

Ayah packs have translation(surah, ayah, ayah_from, ayah_to, text), the shape
of tools/build_quantum_pack.py. Quran.com translates one ayah per row, so
ayah_from = ayah_to = ayah in every row.

The four ayah packs of the first release (AYAH_PACKS) are read from the verse
responses of /verses/by_chapter, as before, so their bytes do not change. Every other
pack is read from GET /quran/translations/<resource id>, which returns the 6,236 texts
in reading order (the response has no verse key, so the order is checked against
quran.sqlite and against a known pack).

Markup removed from the API text, because the app shows plain text and the
footnote bodies are not part of the response:
  - <sup foot_note=ID>N</sup>  footnote call-outs (Saheeh International, Haleem), whole element dropped
  - [১] [২] ...                Bangla footnote call-outs in Zakaria's text, dropped together with the
                               space before them; a space left before , ; : . or the danda is closed
  - <b>...</b>                 bold tags (one Zakaria row), tags dropped and the text kept
  - runs of whitespace are collapsed to one space and the ends trimmed
Square-bracket insertions such as [Prophet] are part of the English
translations and stay.

Word-by-word packs have word_translation(surah, ayah, position, text) keyed
like quran.sqlite; ayah-end signs have no row.

Usage: python3 tools/build_translations.py
"""

import hashlib
import os
import re
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_com
import pack_catalogue

AYAH_PACKS = [
    # file stem, Quran.com resource id, language label
    ("en_saheeh", 20, "English"),
    ("en_abdul_haleem", 85, "English"),
    ("en_transliteration", 57, "English transliteration"),
    ("bn_zakaria", 213, "Bangla"),
]
WORD_PACKS = [
    (pack["id"], pack["language"], pack["language_name"], pack["api_language_name"])
    for pack in pack_catalogue.WORD_PACKS
]

FIRST_RELEASE_AYAH_IDS = {stem for stem, _, _ in AYAH_PACKS}

FOOTNOTE_ELEMENT = re.compile(r"<sup[^>]*>.*?</sup>", re.DOTALL)
ANY_TAG = re.compile(r"<[^>]+>")
BANGLA_FOOTNOTE_CALLOUT = re.compile(r"\s*\[[০-৯]+\]")
SPACE_BEFORE_PUNCTUATION = re.compile(r"\s+([,;:.।])")
WHITESPACE_RUN = re.compile(r"\s+")


def clean_translation(raw_text):
    text = FOOTNOTE_ELEMENT.sub("", raw_text)
    text = ANY_TAG.sub("", text)
    if BANGLA_FOOTNOTE_CALLOUT.search(text):
        text = BANGLA_FOOTNOTE_CALLOUT.sub("", text)
        text = SPACE_BEFORE_PUNCTUATION.sub(r"\1", text)
    return WHITESPACE_RUN.sub(" ", text).strip()


def create_database(path, table_sql):
    if os.path.exists(path):
        os.remove(path)
    connection = sqlite3.connect(path)
    connection.execute(table_sql)
    connection.execute("CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    connection.execute("INSERT INTO pack_info VALUES ('format_version', '1')")
    return connection


def sha256_of_file(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        digest.update(handle.read())
    return digest.hexdigest()


def write_terms(pack_path, title, resource_line, retrieval_date, extra_lines, catalogue_entry=None):
    terms_path = pack_path[: -len(".sqlite")] + ".TERMS.md"
    lines = [
        f"# {title}",
        "",
        "Source: Quran.com API v4, https://api.quran.com/api/v4 (Quran.com / Quran Foundation).",
        resource_line,
        f"Retrieved: {retrieval_date}.",
        "Licence text: the API supplies none (GET /resources/translations/<id>/info returns an empty info field).",
        "Check the publisher's conditions before a release.",
        "",
    ]
    if catalogue_entry:
        lines += [
            f"Translator: {catalogue_entry['author']}.",
            f"Rights holder: {catalogue_entry['rights_holder']}.",
            f"Terms known: {catalogue_entry['terms_known']}. Publishable: {catalogue_entry['publishable']}. {catalogue_entry['note']}",
            "The table in docs/packs.md lists the same judgement for every pack; the maintainer decides what is published.",
            "",
        ]
    lines += extra_lines
    # Remove first: a hard-linked copy of an older data folder must not be written through.
    if os.path.exists(terms_path):
        os.remove(terms_path)
    with open(terms_path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


def resource_catalogue():
    translations = quran_com.fetch("/resources/translations")["translations"]
    return {entry["id"]: entry for entry in translations}


def build_ayah_pack(file_stem, resource_id, language_label, verses, catalogue):
    resource = catalogue[resource_id]
    pack_path = quran_com.sqlite_path("translations", file_stem + ".sqlite")
    connection = create_database(
        pack_path,
        "CREATE TABLE translation (surah INTEGER NOT NULL, ayah INTEGER NOT NULL, ayah_from INTEGER NOT NULL,"
        " ayah_to INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY (surah, ayah))",
    )
    footnote_rows = 0
    for verse in verses:
        surah, ayah = quran_com.split_verse_key(verse)
        matching = [item for item in verse["translations"] if item["resource_id"] == resource_id]
        if len(matching) != 1:
            raise ValueError(f"{surah}:{ayah} has {len(matching)} translations with resource {resource_id}")
        raw_text = matching[0]["text"]
        text = clean_translation(raw_text)
        if not text:
            raise ValueError(f"{surah}:{ayah} is empty after cleaning in resource {resource_id}")
        footnote_rows += text != WHITESPACE_RUN.sub(" ", raw_text).strip()
        connection.execute("INSERT INTO translation VALUES (?,?,?,?,?)", (surah, ayah, ayah, ayah, text))
    connection.executemany("INSERT INTO pack_info VALUES (?, ?)", [
        ("source", "Quran.com API v4"), ("resource_id", str(resource_id)),
        ("resource_name", resource["name"]), ("author", resource["author_name"]),
        ("language", language_label),
    ])
    connection.commit()
    row_count = connection.execute("SELECT COUNT(*) FROM translation").fetchone()[0]
    connection.close()

    retrieval_date = quran_com.retrieval_date(
        "/verses/by_chapter/1", words="true", word_fields=quran_com.WORD_FIELDS, fields="text_uthmani",
        translations=",".join(str(i) for i in quran_com.AYAH_TRANSLATION_IDS),
        word_translation_language="en", per_page=50, page=1)
    write_terms(
        pack_path, f"{resource['name']} ({language_label})",
        f"Quran.com resource id {resource_id}: {resource['name']} (author: {resource['author_name']}).",
        retrieval_date,
        [f"Rows: {row_count}, one per ayah, ayah_from = ayah_to = ayah.",
         f"Markup stripped from {footnote_rows} rows: footnote call-outs and tags, as listed in tools/build_translations.py."],
        catalogue_entry=catalogue_entry_for(file_stem),
    )
    print(f"{file_stem}: {row_count} rows, markup stripped in {footnote_rows} rows, {os.path.getsize(pack_path)} bytes")


def catalogue_entry_for(pack_id):
    for entry in pack_catalogue.TRANSLATIONS_AND_TRANSLITERATIONS:
        if entry["id"] == pack_id:
            return entry
    return None


def ayah_keys_in_reading_order():
    with sqlite3.connect(quran_com.sqlite_path("quran", "quran.sqlite")) as connection:
        return connection.execute("SELECT surah, ayah FROM ayah ORDER BY surah, ayah").fetchall()


def build_catalogue_ayah_pack(entry, catalogue, ayah_keys):
    """One ayah pack from GET /quran/translations/<id>, which lists the 6,236 texts in reading order."""
    resource_id = entry["resource_id"]
    resource = catalogue[resource_id]
    response = quran_com.fetch(f"/quran/translations/{resource_id}")
    texts = response["translations"]
    if len(texts) != len(ayah_keys):
        raise ValueError(f"resource {resource_id} has {len(texts)} texts, expected {len(ayah_keys)}")
    if any(item["resource_id"] != resource_id for item in texts):
        raise ValueError(f"resource {resource_id} response mixes resources")
    pack_path = quran_com.sqlite_path("translations", entry["id"] + ".sqlite")
    connection = create_database(
        pack_path,
        "CREATE TABLE translation (surah INTEGER NOT NULL, ayah INTEGER NOT NULL, ayah_from INTEGER NOT NULL,"
        " ayah_to INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY (surah, ayah))",
    )
    stripped_rows = 0
    for (surah, ayah), item in zip(ayah_keys, texts):
        text = clean_translation(item["text"])
        if not text:
            raise ValueError(f"{surah}:{ayah} is empty after cleaning in resource {resource_id}")
        stripped_rows += text != WHITESPACE_RUN.sub(" ", item["text"]).strip()
        connection.execute("INSERT INTO translation VALUES (?,?,?,?,?)", (surah, ayah, ayah, ayah, text))
    connection.executemany("INSERT INTO pack_info VALUES (?, ?)", [
        ("source", "Quran.com API v4, /quran/translations"), ("resource_id", str(resource_id)),
        ("resource_name", resource["name"]), ("author", resource["author_name"]),
        ("language", entry["language_name"]),
    ])
    connection.commit()
    row_count = connection.execute("SELECT COUNT(*) FROM translation").fetchone()[0]
    connection.close()
    write_terms(
        pack_path, f"{resource['name']} ({entry['language_name']})",
        f"Quran.com resource id {resource_id}: {resource['name']} (author: {resource['author_name']}).",
        quran_com.retrieval_date(f"/quran/translations/{resource_id}"),
        [f"Rows: {row_count}, one per ayah, ayah_from = ayah_to = ayah.",
         f"Markup stripped from {stripped_rows} rows: footnote call-outs and tags, as listed in tools/build_translations.py."],
        catalogue_entry=entry,
    )
    print(f"{entry['id']}: {row_count} rows, markup stripped in {stripped_rows} rows, {os.path.getsize(pack_path)} bytes")


WORD_LANGUAGES_WITHOUT_GAPS = ("en", "bn")


def build_word_pack(file_stem, language_code, label, api_language_name, verses):
    empty_word_keys = []
    pack_path = quran_com.sqlite_path("translations", file_stem + ".sqlite")
    connection = create_database(
        pack_path,
        "CREATE TABLE word_translation (surah INTEGER NOT NULL, ayah INTEGER NOT NULL, position INTEGER NOT NULL,"
        " text TEXT NOT NULL, PRIMARY KEY (surah, ayah, position))",
    )
    for verse in verses:
        surah, ayah = quran_com.split_verse_key(verse)
        for word in verse["words"]:
            if word["char_type_name"] != "word":
                continue
            translation = word.get("translation") or {}
            text = clean_translation(translation.get("text") or "")
            if translation.get("language_name") != api_language_name:
                # Where a language has no word text the API answers with the English one. A pack of one
                # language must not hold English words, so such a word is stored empty.
                if language_code in WORD_LANGUAGES_WITHOUT_GAPS:
                    raise ValueError(f"{surah}:{ayah}:{word['position']} language is {translation.get('language_name')}")
                text = ""
            if not text:
                # The English and Bangla sources have a text for every word; the other languages
                # leave some words blank. A blank is stored as '' so the row count stays 77,429.
                if language_code in WORD_LANGUAGES_WITHOUT_GAPS:
                    raise ValueError(f"{surah}:{ayah}:{word['position']} has an empty word translation")
                empty_word_keys.append(f"{surah}:{ayah}:{word['position']}")
            connection.execute("INSERT INTO word_translation VALUES (?,?,?,?)",
                               (surah, ayah, word["position"], text))
    connection.executemany("INSERT INTO pack_info VALUES (?, ?)", [
        ("source", "Quran.com API v4, words[].translation"), ("language", label),
    ])
    if language_code not in WORD_LANGUAGES_WITHOUT_GAPS:
        connection.execute("INSERT INTO pack_info VALUES ('empty_words', ?)", (str(len(empty_word_keys)),))
    connection.commit()
    row_count = connection.execute("SELECT COUNT(*) FROM word_translation").fetchone()[0]
    connection.close()
    retrieval_date = quran_com.retrieval_date(
        "/verses/by_chapter/1", words="true", word_fields=quran_com.WORD_FIELDS, fields="text_uthmani",
        translations=",".join(str(i) for i in quran_com.AYAH_TRANSLATION_IDS),
        word_translation_language=language_code, per_page=50, page=1,
        **({} if language_code == "en" else {"language": language_code}))
    write_terms(
        pack_path, f"Word-by-word translation ({label})",
        f"Quran.com word translation, query parameters word_translation_language={language_code}&language={language_code}; "
        "the API reports no separate resource id or name for it.",
        retrieval_date,
        [f"Rows: {row_count}, one per word, keyed (surah, ayah, position) like quran.sqlite."]
        + ([] if language_code in WORD_LANGUAGES_WITHOUT_GAPS
           else [f"Words with an empty text (the source has none): {len(empty_word_keys)}."]),
    )
    print(f"{file_stem}: {row_count} rows, {len(empty_word_keys)} empty, {os.path.getsize(pack_path)} bytes")


def main():
    catalogue = resource_catalogue()
    english_verses = quran_com.all_chapter_verses("en")
    for file_stem, resource_id, language_label in AYAH_PACKS:
        build_ayah_pack(file_stem, resource_id, language_label, english_verses, catalogue)
    ayah_keys = ayah_keys_in_reading_order()
    for entry in pack_catalogue.TRANSLATION_PACKS:
        if entry["id"] in FIRST_RELEASE_AYAH_IDS:
            continue
        build_catalogue_ayah_pack(entry, catalogue, ayah_keys)
    for file_stem, language_code, label, api_language_name in WORD_PACKS:
        build_word_pack(file_stem, language_code, label, api_language_name, quran_com.all_chapter_verses(language_code))


if __name__ == "__main__":
    main()
