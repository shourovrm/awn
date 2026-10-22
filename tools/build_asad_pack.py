#!/usr/bin/env python3
"""Build the Muhammad Asad English translation pack ("The Message of the Qur'an").

Source: the alquran.cloud API, edition en.asad (GET /v1/quran/en.asad), part of the Islamic
Network. Its terms (alquran.cloud/terms-and-conditions, section IV, read 2026-10-21) say
translations "are contributed by their rights-holders or sourced from public-domain editions"
and ask that a republished translation names its translator. The edition holds the
translation only, without Asad's footnotes. The rights holder of the book is The Book
Foundation; see docs/decisions/data.md.

The API text was reviewed on 2026-10-21 (spell check, length check against the Arabic, and a
comparison with a second copy). CORRECTIONS fixes the errors found: typos from the source's
digitisation, two places where a footnote was pasted into the ayah (18:12, 51:52), and words
split by a stray hyphen. Every correction names the exact wrong text, and the build fails if
the source no longer contains it, so a fixed upstream text is noticed. Soft hyphens (U+00AD,
hyphenation hints left in 368 ayahs) are removed.

SOURCE_TEXT_SHA256 pins the 6,236 source texts (joined by newlines, before any change), so a
changed edition stops the build instead of silently changing the pack.

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

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import data_paths

SOURCE_URL = "https://api.alquran.cloud/v1/quran/en.asad"
SOURCE_TEXT_SHA256 = "a99dd372bd52b0db6ddc6ee51022d89b746d57599e672507d88bd303aaf0f51a"
DEFAULT_OUTPUT = str(data_paths.workspace() / "translations" / "en_asad.sqlite")
VERSES_PER_SURAH = [
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53,
    89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12,
    12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26,
    30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
]
SOFT_HYPHEN = "­"
LEAKED_FOOTNOTE_18_12 = (
    "Christianity was no longer persecuted and had even become the official religion of the "
    "Roman Empire. The ancient coin (dating from the reign of Decius) with which the young man "
    "wanted to pay for his purchases immediately aroused curiosity; people began to question the "
    "stranger, and the story of the Men of the Cave and their miraculous sleep came to light. "
)
# (surah, ayah): [(wrong text in the source, right text), ...]
CORRECTIONS = {
    (2, 98): [("whosover", "whosoever")],
    (2, 102): [("Hurut and Marut", "Harut and Marut")],
    (2, 214): [("para-dise", "paradise")],
    (3, 32): [("verely", "verily")],
    (4, 100): [("God iss indeed", "God is indeed")],
    (5, 13): [("[re-vealed]", "[revealed]")],
    (8, 17): [("His Own or daining", "His Own ordaining")],
    (8, 52): [("[To them shall happen.] the like", "[To them shall happen] the like"),
              ("messagesand so", "messages - and so")],
    (11, 44): [("Mount Judl And", "Mount Judi. And")],
    (18, 12): [(LEAKED_FOOTNOTE_18_12, "")],
    (18, 64): [("footsetps", "footsteps")],
    (18, 105): [("deseined", "destined")],
    (20, 98): [("[and whho]", "[and who]")],
    (21, 72): [("[Isaacs son]", "[Isaac's son]")],
    (21, 74): [("- forr,]", "- for,]")],
    (22, 30): [("houours", "honours")],
    (22, 71): [("bestoweed", "bestowed")],
    (26, 141): [("message-hearers", "message-bearers")],
    (37, 179): [("and n time", "and in time")],
    (51, 52): [(' [Lit., "sorcerer".]', "")],
    (65, 8): [("callled", "called")],
    (78, 16): [("follage", "foliage")],
    (111, 1): [("countence", "countenance")],
}


def fetch_source():
    with urllib.request.urlopen(SOURCE_URL, timeout=60) as response:
        return json.load(response)


def texts_from_source(document):
    """The 6,236 source texts in reading order, after checking the surah and ayah layout."""
    surahs = document["data"]["surahs"]
    if len(surahs) != len(VERSES_PER_SURAH):
        raise ValueError(f"expected 114 surahs, got {len(surahs)}")
    rows = []
    for surah_index, surah in enumerate(surahs):
        surah_number = surah_index + 1
        if surah["number"] != surah_number:
            raise ValueError(f"surah {surah['number']} found at position {surah_number}")
        ayahs = surah["ayahs"]
        if len(ayahs) != VERSES_PER_SURAH[surah_index]:
            raise ValueError(f"surah {surah_number} has {len(ayahs)} ayahs, expected {VERSES_PER_SURAH[surah_index]}")
        for ayah_index, ayah in enumerate(ayahs):
            ayah_number = ayah_index + 1
            if ayah["numberInSurah"] != ayah_number:
                raise ValueError(f"{surah_number}:{ayah['numberInSurah']} found at position {ayah_number}")
            rows.append((surah_number, ayah_number, ayah["text"]))
    return rows


def source_text_sha256(rows):
    joined = "\n".join(text for _, _, text in rows)
    return hashlib.sha256(joined.encode("utf-8")).hexdigest()


def clean_rows(rows):
    """Applies CORRECTIONS, removes soft hyphens and collapses whitespace."""
    cleaned = []
    for surah, ayah, text in rows:
        for wrong, right in CORRECTIONS.get((surah, ayah), []):
            if wrong not in text:
                raise ValueError(f"{surah}:{ayah} no longer contains {wrong!r}; review CORRECTIONS")
            text = text.replace(wrong, right)
        text = " ".join(text.replace(SOFT_HYPHEN, "").split())
        if not text:
            raise ValueError(f"{surah}:{ayah} is empty")
        cleaned.append((surah, ayah, text))
    return cleaned


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
                ("source_text_sha256", SOURCE_TEXT_SHA256),
                ("author", "Muhammad Asad"),
                ("language", "English"),
            ],
        )


def main():
    output_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUTPUT
    rows = texts_from_source(fetch_source())
    actual_sha256 = source_text_sha256(rows)
    if actual_sha256 != SOURCE_TEXT_SHA256:
        raise ValueError(f"source texts sha256 {actual_sha256} differs from the pinned {SOURCE_TEXT_SHA256}")
    cleaned = clean_rows(rows)
    write_pack(cleaned, output_path)
    print(f"{output_path}: {len(cleaned)} ayahs")


if __name__ == "__main__":
    main()
