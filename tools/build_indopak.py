#!/usr/bin/env python3
"""Build the IndoPak layout pack (15-line and 16-line prints) from the Quran.com QDC API.

Output: awn-data/quran/indopak.sqlite with
  word(surah, ayah, position, text_indopak, text_nastaleeq,
       page_15, line_15, page_16, line_16)
      one row per spoken word, keyed like quran.sqlite.word. text_indopak is the
      Quran.com field text_indopak (Unicode "IndoPak" script); text_nastaleeq is the
      field text_indopak_nastaleeq, the script the bundled Nastaleeq font draws.
      page_15/line_15 come from mushaf 6 (15-line print, 610 pages), page_16/line_16
      from mushaf 7 (16-line Taj Company print, 548 pages).
  ayah_marker(surah, ayah, page_15, line_15, page_16, line_16, end_sign)
      where the ayah-end sign is printed (the API lists it as a word of type "end") and the
      text_indopak_nastaleeq string of that sign; the font draws the sign and its number from it.
  pack_info(key, value)

Source: GET https://api.quran.com/api/qdc/verses/by_page/{page}?mushaf={6|7}&words=true.
The v4 API ignores the mushaf parameter; only the qdc API honours it. The mushaf ids are
those of the Quran.com frontend (types/QuranReader.ts: Indopak15Lines = 6, Indopak16Lines = 7).

Checks made while building (the build fails on any of them):
  - every ayah and word of quran.sqlite is present exactly once in each layout;
  - the end sign comes after the last word;
  - page and line numbers lie in 1..page_count and 1..lines_per_page and do not go
    backwards in reading order.

Usage: python3 tools/build_indopak.py
"""

import os
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_com

LAYOUTS = {
    # layout suffix -> (QDC mushaf id, page count, lines per page)
    "15": (6, 610, 15),
    "16": (7, 548, 16),
}
WORD_FIELDS = "text_indopak,text_indopak_nastaleeq,line_number,page_number,char_type_name,position"


def fetch_layout_page(mushaf_id, page_number):
    """Words printed on one page of an IndoPak print: (surah, ayah, word) for words whose page is this page.

    The API marks the ayah-end sign with char_type_name "end", except in 2:181, where it is typed "word"
    (position 14 of a 13-word ayah, text "١٨١"). That one is corrected here: the last item of a verse whose
    text_indopak is the ayah number in Arabic-Indic digits is the end sign.
    """
    words = []
    page = 1
    while page:
        response = quran_com.fetch(
            f"/verses/by_page/{page_number}", api_base=quran_com.QDC_API_BASE,
            words="true", mushaf=mushaf_id, word_fields=WORD_FIELDS, per_page=50, page=page)
        for verse in response["verses"]:
            surah, ayah = quran_com.split_verse_key(verse)
            last_word = verse["words"][-1]
            if last_word["char_type_name"] != "end" and last_word["text_indopak"] == arabic_indic_number(ayah):
                last_word["char_type_name"] = "end"
            for word in verse["words"]:
                if word["page_number"] == page_number:
                    words.append((surah, ayah, word))
        page = response["pagination"]["next_page"]
    return words


def arabic_indic_number(number):
    return str(number).translate(str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩"))


def read_layout(suffix):
    """Return ({(surah, ayah, position): (text_indopak, text_nastaleeq, page, line)},
    {(surah, ayah): (page, line, end_sign_text_nastaleeq)})."""
    mushaf_id, page_count, lines_per_page = LAYOUTS[suffix]
    spoken, markers = {}, {}
    previous_slot = (0, 0)
    for page_number in range(1, page_count + 1):
        for surah, ayah, word in fetch_layout_page(mushaf_id, page_number):
            line = word["line_number"]
            if not 1 <= line <= lines_per_page:
                raise ValueError(f"layout {suffix}: {surah}:{ayah} line {line} outside 1..{lines_per_page}")
            if (page_number, line) < previous_slot:
                raise ValueError(f"layout {suffix}: {surah}:{ayah}:{word['position']} goes back from {previous_slot}")
            previous_slot = (page_number, line)
            if word["char_type_name"] == "end":
                if (surah, ayah) in markers:
                    raise ValueError(f"layout {suffix}: duplicate end sign {surah}:{ayah}")
                markers[(surah, ayah)] = (page_number, line, word["text_indopak_nastaleeq"])
            else:
                key = (surah, ayah, word["position"])
                if key in spoken:
                    raise ValueError(f"layout {suffix}: duplicate word {key}")
                spoken[key] = (word["text_indopak"], word["text_indopak_nastaleeq"], page_number, line)
    return spoken, markers


def expected_words():
    path = quran_com.sqlite_path("quran", "quran.sqlite")
    with sqlite3.connect(path) as connection:
        return {(s, a, p) for s, a, p in connection.execute("SELECT surah, ayah, position FROM word")}


def main():
    expected = expected_words()
    layouts = {suffix: read_layout(suffix) for suffix in LAYOUTS}
    for suffix, (spoken, markers) in layouts.items():
        if set(spoken) != expected:
            missing = sorted(expected - set(spoken))[:5]
            extra = sorted(set(spoken) - expected)[:5]
            raise ValueError(f"layout {suffix}: word keys differ from quran.sqlite; missing {missing}, extra {extra}")
        if len(markers) != quran_com.AYAH_COUNT:
            raise ValueError(f"layout {suffix}: {len(markers)} end signs, expected {quran_com.AYAH_COUNT}")

    path = quran_com.sqlite_path("quran", "indopak.sqlite")
    if os.path.exists(path):
        os.remove(path)
    connection = sqlite3.connect(path)
    connection.executescript("""
        CREATE TABLE word (
            surah INTEGER NOT NULL, ayah INTEGER NOT NULL, position INTEGER NOT NULL,
            text_indopak TEXT NOT NULL, text_nastaleeq TEXT NOT NULL,
            page_15 INTEGER NOT NULL, line_15 INTEGER NOT NULL,
            page_16 INTEGER NOT NULL, line_16 INTEGER NOT NULL,
            PRIMARY KEY (surah, ayah, position));
        CREATE TABLE ayah_marker (
            surah INTEGER NOT NULL, ayah INTEGER NOT NULL,
            page_15 INTEGER NOT NULL, line_15 INTEGER NOT NULL,
            page_16 INTEGER NOT NULL, line_16 INTEGER NOT NULL,
            end_sign TEXT NOT NULL,
            PRIMARY KEY (surah, ayah));
        CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    """)
    spoken_15, markers_15 = layouts["15"]
    spoken_16, markers_16 = layouts["16"]
    for key in sorted(expected):
        text_indopak, text_nastaleeq, page_15, line_15 = spoken_15[key]
        other_indopak, other_nastaleeq, page_16, line_16 = spoken_16[key]
        if (text_indopak, text_nastaleeq) != (other_indopak, other_nastaleeq):
            raise ValueError(f"{key}: the two layouts give different text")
        connection.execute("INSERT INTO word VALUES (?,?,?,?,?,?,?,?,?)",
                           (*key, text_indopak, text_nastaleeq, page_15, line_15, page_16, line_16))
    for surah_ayah in sorted(markers_15):
        page_15, line_15, end_sign = markers_15[surah_ayah]
        page_16, line_16, other_end_sign = markers_16[surah_ayah]
        if end_sign != other_end_sign:
            raise ValueError(f"{surah_ayah}: the two layouts give different end signs")
        connection.execute("INSERT INTO ayah_marker VALUES (?,?,?,?,?,?,?)",
                           (*surah_ayah, page_15, line_15, page_16, line_16, end_sign))
    connection.executemany("INSERT INTO pack_info VALUES (?, ?)", [
        ("format_version", "1"),
        ("source", "Quran.com QDC API, /verses/by_page?mushaf=6 and mushaf=7"),
        ("pages_15", str(LAYOUTS["15"][1])), ("lines_per_page_15", str(LAYOUTS["15"][2])),
        ("pages_16", str(LAYOUTS["16"][1])), ("lines_per_page_16", str(LAYOUTS["16"][2])),
        ("font", "indopak-nastaleeq-waqf-lazim-v4.2.1.ttf"),
    ])
    connection.commit()
    word_total = connection.execute("SELECT COUNT(*) FROM word").fetchone()[0]
    connection.close()
    print(f"indopak.sqlite: {word_total} words, {len(markers_15)} end signs, {os.path.getsize(path)} bytes")


if __name__ == "__main__":
    main()
