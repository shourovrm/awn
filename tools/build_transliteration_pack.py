#!/usr/bin/env python3
"""Build awn-data/translations/en_transliteration_words.sqlite, the word-by-word transliteration.

Until this pack existed the transliteration of each word sat in the `transliteration` column of
quran.sqlite, which therefore had to ship inside the app. The Quran text is the only content that
ships in the app, so the column moved into its own downloadable pack. It holds exactly the text the
column held, read from the same cached Quran.com API v4 responses that build_quran_text.py reads.

Table: word_translation(surah, ayah, position, text), primary key (surah, ayah, position), one row
per spoken word (77,429), the same table as the word-by-word meaning packs. pack_info(key, value)
holds format_version, source and language.

Usage: python3 tools/build_transliteration_pack.py
"""

import os
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_com

PACK_ID = "en_transliteration_words"
WORD_COUNT = 77429

SCHEMA = """
CREATE TABLE word_translation (
    surah INTEGER NOT NULL, ayah INTEGER NOT NULL, position INTEGER NOT NULL,
    text TEXT NOT NULL,
    PRIMARY KEY (surah, ayah, position)
);
CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL);
"""


def transliteration_rows(verses):
    """Return (surah, ayah, position, text) for every spoken word of `verses`.

    The ayah-end sign is also listed as a word by the API (char_type_name "end") and has no
    transliteration; it is skipped like build_quran_text.py skips it. A spoken word without a
    transliteration stops the build, because a hole would show as a missing word in the app.
    """
    rows = []
    for verse in verses:
        surah, ayah = quran_com.split_verse_key(verse)
        for word in verse["words"]:
            if word["char_type_name"] != "word":
                continue
            text = (word.get("transliteration") or {}).get("text")
            if not text or not text.strip():
                raise ValueError(f"{surah}:{ayah}:{word['position']} has no transliteration")
            rows.append((surah, ayah, word["position"], text))
    return rows


def write_pack(path, rows):
    if os.path.exists(path):
        # Remove first: a hard-linked copy in an older data folder must not be written through.
        os.remove(path)
    connection = sqlite3.connect(path)
    connection.executescript(SCHEMA)
    connection.executemany("INSERT INTO word_translation VALUES (?,?,?,?)", rows)
    connection.executemany("INSERT INTO pack_info VALUES (?, ?)", [
        ("format_version", "1"),
        ("source", "Quran.com API v4, words[].transliteration"),
        ("language", "English (Latin letters)"),
    ])
    connection.commit()
    connection.close()


def write_terms(path, row_count, retrieval_date):
    lines = [
        "# Word-by-word transliteration (English, Latin letters)",
        "",
        "Source: Quran.com API v4, https://api.quran.com/api/v4 (Quran.com / Quran Foundation).",
        "Quran.com word field `transliteration`, the same text that quran.sqlite held in its transliteration column.",
        f"Retrieved: {retrieval_date}.",
        "Licence text: the API supplies none (GET /resources/translations/<id>/info returns an empty info field).",
        "Check the publisher's conditions before a release.",
        "",
        f"Rows: {row_count}, one per word, keyed (surah, ayah, position) like quran.sqlite.",
    ]
    if os.path.exists(path):
        os.remove(path)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")


def main():
    rows = transliteration_rows(quran_com.all_chapter_verses("en"))
    if len(rows) != WORD_COUNT:
        raise ValueError(f"expected {WORD_COUNT} words but found {len(rows)}")
    pack_path = quran_com.sqlite_path("translations", PACK_ID + ".sqlite")
    write_pack(pack_path, rows)
    retrieval_date = quran_com.retrieval_date(
        "/verses/by_chapter/1", words="true", word_fields=quran_com.WORD_FIELDS, fields="text_uthmani",
        translations=",".join(str(i) for i in quran_com.AYAH_TRANSLATION_IDS),
        word_translation_language="en", per_page=50, page=1)
    write_terms(pack_path[: -len(".sqlite")] + ".TERMS.md", len(rows), retrieval_date)
    print(f"{PACK_ID}: {len(rows)} rows, {os.path.getsize(pack_path)} bytes")


if __name__ == "__main__":
    main()
