#!/usr/bin/env python3
"""Build awn-data/quran/quran.sqlite from the Quran.com API v4.

Tables:
  word(surah, ayah, position, text_uthmani, code_v1, code_v2, page_v1, line_v1,
       page_v2, line_v2)  one row per spoken word
  ayah_marker(surah, ayah, page_v1, line_v1, page_v2, line_v2)  the ayah-end sign
  ayah(surah, ayah, text_uthmani, juz, hizb, page_v1, page_v2)
  chapter(number, name_arabic, name_simple, name_bangla, verses_count,
          revelation_place, bismillah_pre)
  pack_info(key, value)

The per-word transliteration is not stored here: it is the downloadable pack built by
build_transliteration_pack.py, so the only content that ships in the app is the text and layout.

Word key rule: `position` counts words only, from 1. The API lists the ayah-end
sign as one more word with char_type_name "end" and position = word count + 1;
that entry goes to ayah_marker and never into word.

Page and line sources. The two King Fahd prints are laid out differently
(56 ayahs sit on a different page in V1 and V2), and the API numbers lines
per print through two endpoints: /verses/by_page/{page} follows the V1 print
(1405H) and /verses/by_chapter/{n} follows the V2 print (1421H). page_v1 and
line_v1 come from the by_page words; page_v2 and line_v2 come from the
by_chapter words. Each by_chapter word also carries v1_page, which is
cross-checked against the by_page result. line_number counts every line slot
on the page, including surah-name and bismillah lines that hold no words, so
a page is 15 lines when its highest line number is 15.

Usage: python3 tools/build_quran_text.py
"""

import collections
import os
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_com

SCHEMA = """
CREATE TABLE word (
    surah INTEGER NOT NULL, ayah INTEGER NOT NULL, position INTEGER NOT NULL,
    text_uthmani TEXT NOT NULL, code_v1 TEXT NOT NULL, code_v2 TEXT NOT NULL,
    page_v1 INTEGER NOT NULL, line_v1 INTEGER NOT NULL,
    page_v2 INTEGER NOT NULL, line_v2 INTEGER NOT NULL,
    PRIMARY KEY (surah, ayah, position)
);
CREATE TABLE ayah_marker (
    surah INTEGER NOT NULL, ayah INTEGER NOT NULL,
    page_v1 INTEGER NOT NULL, line_v1 INTEGER NOT NULL,
    page_v2 INTEGER NOT NULL, line_v2 INTEGER NOT NULL,
    PRIMARY KEY (surah, ayah)
);
CREATE TABLE ayah (
    surah INTEGER NOT NULL, ayah INTEGER NOT NULL, text_uthmani TEXT NOT NULL,
    juz INTEGER NOT NULL, hizb INTEGER NOT NULL,
    page_v1 INTEGER NOT NULL, page_v2 INTEGER NOT NULL,
    PRIMARY KEY (surah, ayah)
);
CREATE TABLE chapter (
    number INTEGER PRIMARY KEY, name_arabic TEXT NOT NULL, name_simple TEXT NOT NULL,
    name_bangla TEXT, verses_count INTEGER NOT NULL,
    revelation_place TEXT NOT NULL, bismillah_pre INTEGER NOT NULL
);
CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE INDEX word_page_v1 ON word (page_v1, line_v1);
CREATE INDEX word_page_v2 ON word (page_v2, line_v2);
"""


def build_chapter_rows():
    english = quran_com.fetch("/chapters", language="en")["chapters"]
    bangla = quran_com.fetch("/chapters", language="bn")["chapters"]
    bangla_names = {chapter["id"]: chapter["translated_name"]["name"] for chapter in bangla}
    rows = []
    for chapter in english:
        rows.append((
            chapter["id"], chapter["name_arabic"], chapter["name_simple"],
            bangla_names.get(chapter["id"]), chapter["verses_count"],
            chapter["revelation_place"], 1 if chapter["bismillah_pre"] else 0,
        ))
    return rows


def read_version_one_layout():
    """Map (surah, ayah, position) to (page, line) in the V1 print, from /verses/by_page."""
    layout = {}
    for page_number in range(1, quran_com.PAGE_COUNT + 1):
        for surah, ayah, word in quran_com.fetch_page_words(page_number):
            key = (surah, ayah, word["position"])
            if key in layout:
                raise ValueError(f"{key} appears twice in the V1 layout")
            layout[key] = (page_number, word["line_number"])
    return layout


def build_rows(verses, version_one_layout):
    word_rows, marker_rows, ayah_rows = [], [], []
    character_types = collections.Counter()
    marker_offsets = collections.Counter()
    for verse in verses:
        surah, ayah = quran_com.split_verse_key(verse)
        spoken_words = []
        for word in verse["words"]:
            character_types[word["char_type_name"]] += 1
            if word["char_type_name"] == "word":
                spoken_words.append(word)
            elif word["char_type_name"] == "end":
                marker_offsets[word["position"] - len(
                    [w for w in verse["words"] if w["char_type_name"] == "word"])] += 1
                page_v1, line_v1 = version_one_layout[(surah, ayah, word["position"])]
                marker_rows.append((surah, ayah, page_v1, line_v1, word["v2_page"], word["line_number"]))
            else:
                raise ValueError(f"unexpected char_type_name {word['char_type_name']!r} in {surah}:{ayah}")
        positions = [word["position"] for word in spoken_words]
        if positions != list(range(1, len(spoken_words) + 1)):
            raise ValueError(f"{surah}:{ayah} word positions are not 1..n: {positions}")
        for word in spoken_words:
            page_v1, line_v1 = version_one_layout[(surah, ayah, word["position"])]
            if page_v1 != word["v1_page"]:
                raise ValueError(f"{surah}:{ayah}:{word['position']} V1 page differs between endpoints")
            word_rows.append((
                surah, ayah, word["position"], word["text_uthmani"], word["code_v1"], word["code_v2"],
                page_v1, line_v1, word["v2_page"], word["line_number"],
            ))
        first_word = spoken_words[0]
        ayah_rows.append((
            surah, ayah, verse["text_uthmani"], verse["juz_number"], verse["hizb_number"],
            version_one_layout[(surah, ayah, 1)][0], first_word["v2_page"],
        ))
    return word_rows, marker_rows, ayah_rows, character_types, marker_offsets


def line_count_histogram(connection, page_column, line_column):
    """Map 'highest line number on a page' to 'number of pages with that value'."""
    # The ayah-end sign is printed on the line after the last word in some ayahs,
    # so the markers count towards the lines a page uses.
    query = (
        f"SELECT page, MAX(line) FROM ("
        f"SELECT {page_column} AS page, {line_column} AS line FROM word UNION ALL "
        f"SELECT {page_column}, {line_column} FROM ayah_marker) GROUP BY page"
    )
    highest_lines = connection.execute(query).fetchall()
    return collections.Counter(count for _, count in highest_lines)


def main():
    output_path = quran_com.sqlite_path("quran", "quran.sqlite")
    verses = quran_com.all_chapter_verses("en")
    version_one_layout = read_version_one_layout()
    word_rows, marker_rows, ayah_rows, character_types, marker_offsets = build_rows(
        verses, version_one_layout)
    if len(version_one_layout) != len(word_rows) + len(marker_rows):
        raise ValueError("by_page and by_chapter disagree on the number of words")

    if os.path.exists(output_path):
        os.remove(output_path)
    connection = sqlite3.connect(output_path)
    connection.executescript(SCHEMA)
    connection.executemany("INSERT INTO word VALUES (?,?,?,?,?,?,?,?,?,?)", word_rows)
    connection.executemany("INSERT INTO ayah_marker VALUES (?,?,?,?,?,?)", marker_rows)
    connection.executemany("INSERT INTO ayah VALUES (?,?,?,?,?,?,?)", ayah_rows)
    connection.executemany("INSERT INTO chapter VALUES (?,?,?,?,?,?,?)", build_chapter_rows())
    sample_path = "/verses/by_chapter/1"
    connection.executemany("INSERT INTO pack_info VALUES (?, ?)", [
        ("format_version", "1"),
        ("source", "Quran.com API v4 " + quran_com.API_BASE),
        ("retrieved", quran_com.retrieval_date(
            sample_path, words="true", word_fields=quran_com.WORD_FIELDS, fields="text_uthmani",
            translations=",".join(str(i) for i in quran_com.AYAH_TRANSLATION_IDS),
            word_translation_language="en", per_page=50, page=1)),
    ])
    connection.commit()

    print("char_type_name counts:", dict(character_types))
    print("ayah-end marker position minus word count:", dict(marker_offsets), "(1 means position = words + 1)")
    print(f"chapters {connection.execute('SELECT COUNT(*) FROM chapter').fetchone()[0]}, "
          f"ayahs {len(ayah_rows)}, words {len(word_rows)}, markers {len(marker_rows)}")
    different_pages = connection.execute("SELECT COUNT(*) FROM word WHERE page_v1 != page_v2").fetchone()[0]
    print("words whose page_v1 != page_v2:", different_pages)
    for label, page_column, line_column in (("v1", "page_v1", "line_v1"), ("v2", "page_v2", "line_v2")):
        histogram = line_count_histogram(connection, page_column, line_column)
        print(f"highest line number per page, {label} (line: pages):", dict(sorted(histogram.items())))
    connection.close()
    print("wrote", output_path, os.path.getsize(output_path), "bytes")


if __name__ == "__main__":
    main()
