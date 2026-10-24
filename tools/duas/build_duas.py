"""Builds feature/duas/src/main/assets/duas.json from duas.tsv and definitions.json.

Usage: python3 -I tools/duas/build_duas.py
The start and end word texts are read from the bundled Quran database, so the
file stays in step with the text the app shows.
"""

import json
import sqlite3
import sys
import unicodedata
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DATABASE_PATH = REPOSITORY_ROOT / "core/data/src/main/assets/packs/quran.sqlite"
OUTPUT_PATH = REPOSITORY_ROOT / "feature/duas/src/main/assets/duas.json"
SOURCE_PATH = Path(__file__).with_name("duas.tsv")
DEFINITIONS_PATH = Path(__file__).with_name("definitions.json")

# Plain letters of the opening words, written as code points to avoid bidi confusion.
RABBANA = "ربنا"
RABBI = "رب"
ALLAHUMMA = "اللهم"
WAW = "و"
ALEF_WASLA = "ٱ"
ALEF = "ا"
ORNAMENT = "۞"
TATWEEL = "ـ"


def plain_arabic(word):
    """Drops harakat, marks and ornaments so the opening word can be compared."""
    decomposed = unicodedata.normalize("NFD", word)
    kept = [c for c in decomposed if not unicodedata.category(c).startswith("M")]
    return "".join(kept).replace(ORNAMENT, "").replace(TATWEEL, "").strip()


def opening_of(first_word):
    letters = plain_arabic(first_word).replace(ALEF_WASLA, ALEF)
    if letters.startswith(WAW) and len(letters) > 1:
        letters = letters[1:]
    if letters == RABBANA:
        return "rabbana"
    if letters == RABBI:
        return "rabbi"
    if letters == ALLAHUMMA:
        return "allahumma"
    return "other"


def word_text(database, surah, ayah, position):
    row = database.execute(
        "select text_uthmani from word where surah=? and ayah=? and position=?",
        (surah, ayah, position),
    ).fetchone()
    if row is None:
        sys.exit(f"No word at {surah}:{ayah} position {position}")
    return row[0]


def read_rows():
    rows = []
    for line in SOURCE_PATH.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        columns = line.split("\t")
        if len(columns) != 8:
            sys.exit(f"Bad row (expected 8 columns): {line}")
        rows.append(columns)
    return rows


def build_dua(database, columns, used_ids):
    surah, start_ayah, start_word, end_ayah, end_word, group, prophet, starter = columns
    surah, start_ayah, start_word = int(surah), int(start_ayah), int(start_word)
    end_ayah, end_word = int(end_ayah), int(end_word)
    dua_id = f"{surah}-{start_ayah}"
    if dua_id in used_ids:
        dua_id += "-b"
    used_ids.add(dua_id)
    start_text = word_text(database, surah, start_ayah, start_word)
    return {
        "id": dua_id,
        "surah": surah,
        "start": {"ayah": start_ayah, "word": start_word, "text": start_text},
        "end": {
            "ayah": end_ayah,
            "word": end_word,
            "text": word_text(database, surah, end_ayah, end_word),
        },
        "group": group,
        "opening": opening_of(start_text),
        "prophet": None if prophet == "-" else prophet,
        "starter": starter == "1",
    }


def main():
    definitions = json.loads(DEFINITIONS_PATH.read_text(encoding="utf-8"))
    database = sqlite3.connect(DATABASE_PATH)
    used_ids = set()
    duas = [build_dua(database, columns, used_ids) for columns in read_rows()]
    catalogue = {
        "note": definitions["note"],
        "groups": definitions["groups"],
        "prophets": definitions["prophets"],
        "excluded": definitions["excluded"],
        "duas": duas,
    }
    OUTPUT_PATH.write_text(
        json.dumps(catalogue, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Wrote {len(duas)} duas to {OUTPUT_PATH}")


main()
