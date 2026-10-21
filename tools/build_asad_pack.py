#!/usr/bin/env python3
"""Build the Muhammad Asad English translation pack ("The Message of the Qur'an").

Source: the public GitHub repository Kristories/quran, file fixtures/en/asad/source.json,
pinned to one commit so every build reads the same bytes. The file is a list of 114 surahs,
each with "verses": [{"number": n, "translation": text}]. It holds the translation only,
without Asad's footnotes.

The text was compared with a second independent copy (alquran.cloud edition en.asad) on
2026-10-21: all 6,236 ayahs match after normalising quotes and dashes, except 7:1, where the
GitHub file drops the last of the opening letters. CORRECTIONS restores it; the build fails if
the source no longer has the expected wrong text, so a fixed upstream file is noticed.

Rights: the repository is MIT licensed, but that licence covers the repository, not Asad's
text; the rights holder is The Book Foundation. The maintainer chose to ship it with
permission pending (docs/decisions/data.md).

Output: a SQLite file with table translation(surah, ayah, ayah_from, ayah_to, text) and table
pack_info(key, value), the shape of tools/build_quantum_pack.py.

Usage: python3 tools/build_asad_pack.py [output.sqlite]
"""

import hashlib
import json
import os
import sqlite3
import sys
import urllib.request

SOURCE_COMMIT = "3a796d5d7f80c80b83adae5b39affcec2aeb426c"
SOURCE_URL = (
    f"https://raw.githubusercontent.com/Kristories/quran/{SOURCE_COMMIT}/fixtures/en/asad/source.json"
)
SOURCE_SHA256 = "067ab21d971840b7c50128746fcbb7013de62e411f458253ba79e9999539e9db"
DEFAULT_OUTPUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "awn-data", "translations", "en_asad.sqlite"
)
VERSES_PER_SURAH = [
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53,
    89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12,
    12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26,
    30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
]
# (surah, ayah): (text in the source, corrected text)
CORRECTIONS = {
    (7, 1): ("Alif. Lam. Mim.", "Alif. Lam. Mim. Sad."),
}


def fetch_source():
    with urllib.request.urlopen(SOURCE_URL, timeout=60) as response:
        return response.read()


def rows_from_source(surahs):
    """Turns the source list into (surah, ayah, text) rows and applies CORRECTIONS."""
    if len(surahs) != len(VERSES_PER_SURAH):
        raise ValueError(f"expected 114 surahs, got {len(surahs)}")
    rows = []
    for surah_index, surah in enumerate(surahs):
        surah_number = surah_index + 1
        if surah["number"] != surah_number:
            raise ValueError(f"surah {surah['number']} found at position {surah_number}")
        verses = surah["verses"]
        if len(verses) != VERSES_PER_SURAH[surah_index]:
            raise ValueError(f"surah {surah_number} has {len(verses)} ayahs, expected {VERSES_PER_SURAH[surah_index]}")
        for verse_index, verse in enumerate(verses):
            ayah_number = verse_index + 1
            if verse["number"] != ayah_number:
                raise ValueError(f"{surah_number}:{verse['number']} found at position {ayah_number}")
            text = " ".join(verse["translation"].split())
            if not text:
                raise ValueError(f"{surah_number}:{ayah_number} is empty")
            rows.append((surah_number, ayah_number, text))
    return apply_corrections(rows)


def apply_corrections(rows):
    corrected = []
    for surah, ayah, text in rows:
        correction = CORRECTIONS.get((surah, ayah))
        if correction is not None:
            expected_wrong, right = correction
            if text != expected_wrong:
                raise ValueError(f"{surah}:{ayah} changed upstream to {text!r}; review CORRECTIONS")
            text = right
        corrected.append((surah, ayah, text))
    return corrected


def write_pack(rows, output_path):
    if os.path.exists(output_path):
        os.remove(output_path)
    with sqlite3.connect(output_path) as connection:
        connection.execute(
            "CREATE TABLE translation (surah INTEGER NOT NULL, ayah INTEGER NOT NULL, "
            "ayah_from INTEGER NOT NULL, ayah_to INTEGER NOT NULL, text TEXT NOT NULL, "
            "PRIMARY KEY (surah, ayah))"
        )
        connection.executemany(
            "INSERT INTO translation VALUES (?, ?, ?, ?, ?)",
            [(surah, ayah, ayah, ayah, text) for surah, ayah, text in rows],
        )
        connection.execute("CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        connection.executemany(
            "INSERT INTO pack_info VALUES (?, ?)",
            [
                ("format_version", "1"),
                ("source", SOURCE_URL),
                ("source_sha256", SOURCE_SHA256),
                ("author", "Muhammad Asad"),
                ("language", "English"),
            ],
        )


def main():
    output_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUTPUT
    source_bytes = fetch_source()
    source_sha256 = hashlib.sha256(source_bytes).hexdigest()
    if source_sha256 != SOURCE_SHA256:
        raise ValueError(f"source sha256 {source_sha256} differs from the pinned {SOURCE_SHA256}")
    rows = rows_from_source(json.loads(source_bytes))
    write_pack(rows, output_path)
    print(f"{output_path}: {len(rows)} ayahs")


if __name__ == "__main__":
    main()
