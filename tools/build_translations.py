#!/usr/bin/env python3
"""Build the ayah translation packs and the word-by-word packs from the Quran.com API v4.

Output under awn-data/translations/, one SQLite file per pack plus a TERMS.md
beside each (<pack>.TERMS.md):
  en_saheeh.sqlite           resource 20   Saheeh International
  en_abdul_haleem.sqlite     resource 85   M.A.S. Abdel Haleem
  en_transliteration.sqlite  resource 57   Transliteration
  bn_zakaria.sqlite          resource 213  Dr. Abu Bakr Muhammad Zakaria
  wbw_en.sqlite              word-by-word English
  wbw_bn.sqlite              word-by-word Bangla

Resource 131 (The Clear Quran, Dr. Mustafa Khattab) is not built: the public
API lists no such resource and answers translations=131 with no translation, so
there is no en_clear_quran pack.

Ayah packs have translation(surah, ayah, ayah_from, ayah_to, text), the shape
of tools/build_quantum_pack.py. Quran.com translates one ayah per row, so
ayah_from = ayah_to = ayah in every row.

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

AYAH_PACKS = [
    # file stem, Quran.com resource id, language label
    ("en_saheeh", 20, "English"),
    ("en_abdul_haleem", 85, "English"),
    ("en_transliteration", 57, "English transliteration"),
    ("bn_zakaria", 213, "Bangla"),
]
WORD_PACKS = [
    # file stem, word_translation_language, label
    ("wbw_en", "en", "English"),
    ("wbw_bn", "bn", "Bangla"),
]

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


def write_terms(pack_path, title, resource_line, retrieval_date, extra_lines):
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
    ] + extra_lines
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
    )
    print(f"{file_stem}: {row_count} rows, markup stripped in {footnote_rows} rows, {os.path.getsize(pack_path)} bytes")


def build_word_pack(file_stem, language_code, label, verses):
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
            if translation.get("language_name") != {"en": "english", "bn": "bengali"}[language_code]:
                raise ValueError(f"{surah}:{ayah}:{word['position']} language is {translation.get('language_name')}")
            text = clean_translation(translation.get("text") or "")
            if not text:
                raise ValueError(f"{surah}:{ayah}:{word['position']} has an empty word translation")
            connection.execute("INSERT INTO word_translation VALUES (?,?,?,?)",
                               (surah, ayah, word["position"], text))
    connection.executemany("INSERT INTO pack_info VALUES (?, ?)", [
        ("source", "Quran.com API v4, words[].translation"), ("language", label),
    ])
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
        [f"Rows: {row_count}, one per word, keyed (surah, ayah, position) like quran.sqlite."],
    )
    print(f"{file_stem}: {row_count} rows, {os.path.getsize(pack_path)} bytes")


def main():
    catalogue = resource_catalogue()
    english_verses = quran_com.all_chapter_verses("en")
    for file_stem, resource_id, language_label in AYAH_PACKS:
        build_ayah_pack(file_stem, resource_id, language_label, english_verses, catalogue)
    for file_stem, language_code, label in WORD_PACKS:
        build_word_pack(file_stem, language_code, label, quran_com.all_chapter_verses(language_code))


if __name__ == "__main__":
    main()
