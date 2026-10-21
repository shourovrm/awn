#!/usr/bin/env python3
"""Build the Quantum Foundation Bangla translation pack from alquran.org.bd.

The site exposes one JSON document per surah at /api/surah/<n>. Each document
holds "blocks": a block covers one ayah or a range of ayahs
(ayat_number_start..ayat_number_end) and carries the Bangla rendering as HTML.
Inside a multi-ayah block the text of each ayah is introduced by its number in
Bangla digits ("২. ..." or "৫-৬. ..."), which this script uses to split the
block back into per-ayah rows. Where the publisher merged several ayahs into
one passage, every ayah in the range stores the same text together with the
range, so the app can show "5-6" instead of repeating the passage.

Output: a SQLite file with table translation(surah, ayah, ayah_from, ayah_to,
text) and table pack_info(key, value), plus a coverage report on stdout. The app
refuses a pack database that has no pack_info format_version, so the table is
required. Permission to redistribute the text is handled by the maintainer and is
not checked here.

Usage:
  python3 tools/build_quantum_pack.py [output.sqlite]
      fetch the site and write a new pack
  python3 tools/build_quantum_pack.py --add-pack-info existing.sqlite output.sqlite
      copy the rows of an earlier build that has no pack_info into a new file with
      the current schema, without fetching anything
"""

import html
import json
import os
import re
import sqlite3
import sys
import time
import unicodedata
import urllib.request

SITE = "https://alquran.org.bd"
VERSES_PER_SURAH = [
    7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
    112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53,
    89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12,
    12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26,
    30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
]
BANGLA_DIGITS = "০১২৩৪৫৬৭৮৯"
# "২." or "৫-৬." at the start of a sentence, preceded by whitespace or a closing quote.
MARKER = re.compile(r"(?:^|[\s’”)])([০-৯]+)(?:-([০-৯]+))?\.\s")


def bangla_to_int(digits):
    return int("".join(str(BANGLA_DIGITS.index(ch)) for ch in digits))


def fetch_surah(number):
    with urllib.request.urlopen(f"{SITE}/api/surah/{number}", timeout=30) as response:
        return json.load(response)["results"]


def repair_known_typos(text):
    """Fix the publisher's keyboard slip "া" + "ে" (visible in 106:1, "কােরাইশদের"), which is never valid
    Bangla spelling; the intended letter is the single vowel sign "ো"."""
    return text.replace("\u09be\u09c7", "\u09cb")


# Square brackets hold the publisher's commentary ("[অর্থাৎ ...]", "[এখানে আল্লাহ ...]"), 17 notes of 48 to
# 627 characters; the maintainer chose to show the translation only. The one short bracket, "[সেজদা]"
# (7:206), marks a prostration and stays. Round brackets are part of the translation and stay.
LONGEST_KEPT_BRACKET = 20
SQUARE_BRACKET_NOTE = re.compile(r"\s*\[[^\]]*\]")
SPACE_BEFORE_DANDA = re.compile(r"\s+(।)")


def remove_publisher_notes(text):
    """Drops square-bracketed commentary longer than a short marker such as [সেজদা]."""

    def drop_long(match):
        return "" if len(match.group(0).strip()) > LONGEST_KEPT_BRACKET else match.group(0)

    return SQUARE_BRACKET_NOTE.sub(drop_long, text)


def tidy_translation(text):
    """The text a reader sees: typos repaired, notes removed, no space before the danda."""
    text = remove_publisher_notes(repair_known_typos(text))
    text = SPACE_BEFORE_DANDA.sub(r"\1", text)
    return " ".join(text.split())


def clean(html_text):
    text = html.unescape(re.sub(r"<[^>]+>", " ", html_text or ""))
    return repair_known_typos(re.sub(r"\s+", " ", text).strip())


def split_block(text, first, last):
    """Return {ayah: (ayah_from, ayah_to, text)} for one block."""
    rows = {}
    markers = list(MARKER.finditer(text))
    # The first ayah of a block usually carries no marker: its text runs from
    # the start of the block to the first marker.
    segments = []
    # The first ayah of a block carries no marker: its text runs from the start
    # of the block to the first marker, and it covers every ayah before that
    # marker's number (the whole block when there is no marker at all).
    head = text[: markers[0].start()].strip() if markers else text
    if head:
        head_end = bangla_to_int(markers[0].group(1)) - 1 if markers else last
        segments.append((first, max(first, head_end), head))
    for index, match in enumerate(markers):
        start = bangla_to_int(match.group(1))
        end = bangla_to_int(match.group(2)) if match.group(2) else start
        tail = markers[index + 1].start() if index + 1 < len(markers) else len(text)
        segments.append((start, end, text[match.end():tail].strip()))
    # The inline markers are more reliable than the block's declared range:
    # the site sometimes files ayah 94 inside the block labelled 93-93.
    for start, end, segment in segments:
        for ayah in range(start, end + 1):
            rows[ayah] = (start, end, segment)
    return rows


# The publisher numbers Al-Fatihah without the Basmalah and splits the last Hafs ayah in two
# (their 1 is "Alhamdulillah", their "5-6" and 7 are the sentences the app shows for Hafs 6 and 7).
# Awn keys every pack by the Hafs numbering, so their surah 1 is moved: Hafs 2-5 take their 1-4,
# Hafs 6 takes their "5-6" sentence and Hafs 7 takes their 7. Their site shows the Basmalah above every
# surah except At-Tawbah from one shared template line, not from the API: this exact wording is the
# <p class="banglaStart"> of https://alquran.org.bd/static/js/views/includes/surah-detail-tpl-bn.html
# (hidden only when the surah is 9). The template has no closing danda; one is added to match every
# other row. The template spells the letter yo with the single code point U+09DF, like the rest of the pack.
FATIHA_BASMALAH = "দয়াময় মেহেরবান আল্লাহর নামে।"


# Their "5-6" sentence also holds the opening of Hafs 7 ("the path of Your beloved ones",
# صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ). The maintainer's split: Hafs 6 ends at the first comma;
# the rest opens Hafs 7, its full stop turned into a comma so their sentence 7 continues it.
FATIHA_FIVE_SIX = "(প্রভু হে!) সাফল্যের সরলপথে, তোমার প্রিয়জনদের পথে আমাদের পরিচালিত করো।"


def split_fatiha_five_six(text):
    """Splits the publisher's 5-6 sentence into Hafs 6 and the opening of Hafs 7."""
    # The site spells "য়" as two code points (U+09AF U+09BC); compare in one normal form.
    if unicodedata.normalize("NFC", text) != unicodedata.normalize("NFC", FATIHA_FIVE_SIX):
        raise ValueError(f"Al-Fatihah 5-6 changed upstream to {text!r}; review the split")
    head, tail = text.split(", ", 1)
    return head + ",", tail.removesuffix("।") + ","


def fatiha_in_hafs_numbering(publisher_rows):
    """Maps {ayah: (from, to, text)} in the publisher's Al-Fatihah numbering to Hafs numbering."""
    if publisher_rows.get(5, (0, 0, ""))[:2] != (5, 6) or publisher_rows.get(7, (0, 0, ""))[:2] != (7, 7):
        raise ValueError("Al-Fatihah no longer has the publisher's 5-6 and 7 blocks; review the mapping")
    hafs_six, opening_of_seven = split_fatiha_five_six(publisher_rows[5][2])
    return {
        1: (1, 1, FATIHA_BASMALAH),
        2: (2, 2, publisher_rows[1][2]),
        3: (3, 3, publisher_rows[2][2]),
        4: (4, 4, publisher_rows[3][2]),
        5: (5, 5, publisher_rows[4][2]),
        6: (6, 6, hafs_six),
        7: (7, 7, opening_of_seven + " " + publisher_rows[7][2]),
    }


def with_fatiha_in_hafs_numbering(rows):
    """Replaces the surah 1 rows of (surah, ayah, from, to, text) rows by their Hafs numbering."""
    publisher = {ayah: (start, end, text) for surah, ayah, start, end, text in rows if surah == 1}
    hafs = fatiha_in_hafs_numbering(publisher)
    fatiha = [(1, ayah, start, end, text) for ayah, (start, end, text) in sorted(hafs.items())]
    return fatiha + [row for row in rows if row[0] != 1]


def is_publisher_fatiha(rows):
    """True while surah 1 is still in the publisher's numbering (1:5 and 1:6 share one block)."""
    fatiha = {ayah: (start, end) for surah, ayah, start, end, _ in rows if surah == 1}
    return fatiha.get(5) == (5, 6)


def create_pack_tables(connection):
    """Create the same schema as tools/build_translations.py, so every pack is read the same way."""
    connection.execute(
        "CREATE TABLE translation (surah INTEGER NOT NULL, ayah INTEGER NOT NULL,"
        " ayah_from INTEGER NOT NULL, ayah_to INTEGER NOT NULL, text TEXT NOT NULL,"
        " PRIMARY KEY (surah, ayah))"
    )
    connection.execute("CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    connection.executemany(
        "INSERT INTO pack_info VALUES (?, ?)",
        [
            ("format_version", "1"),
            ("source", "alquran.org.bd"),
            ("author", "Quantum Foundation"),
            ("language", "Bangla"),
        ],
    )


def add_pack_info(existing_path, output_path):
    """Copy an earlier pack into the current schema, moving Al-Fatihah to Hafs numbering."""
    if os.path.exists(output_path):
        os.remove(output_path)
    source = sqlite3.connect(existing_path)
    rows = source.execute(
        "SELECT surah, ayah, ayah_from, ayah_to, text FROM translation ORDER BY surah, ayah"
    ).fetchall()
    source.close()
    if is_publisher_fatiha(rows):
        rows = with_fatiha_in_hafs_numbering(rows)
    rows = [(surah, ayah, start, end, tidy_translation(text)) for surah, ayah, start, end, text in rows]
    connection = sqlite3.connect(output_path)
    create_pack_tables(connection)
    connection.executemany("INSERT INTO translation VALUES (?, ?, ?, ?, ?)", rows)
    connection.commit()
    connection.close()
    print(f"copied {len(rows)} rows into {output_path}")


def main():
    if len(sys.argv) == 4 and sys.argv[1] == "--add-pack-info":
        add_pack_info(sys.argv[2], sys.argv[3])
        return
    output = sys.argv[1] if len(sys.argv) > 1 else "awn-data/translations/bn_quantum.sqlite"
    if os.path.exists(output):
        os.remove(output)
    connection = sqlite3.connect(output)
    create_pack_tables(connection)
    missing = []
    merged_ranges = 0
    for surah in range(1, 115):
        blocks = fetch_surah(surah)
        rows = {}
        for block in blocks:
            first = block["ayat_number_start"] or block["ayat_number"]
            last = block["ayat_number_end"] or first
            rows.update(split_block(clean(block["ayat_bangla_description"]), first, last))
        if surah == 1:
            rows = fatiha_in_hafs_numbering(rows)
        for ayah in range(1, VERSES_PER_SURAH[surah - 1] + 1):
            if ayah not in rows or not rows[ayah][2]:
                missing.append(f"{surah}:{ayah}")
                continue
            start, end, text = rows[ayah]
            text = tidy_translation(text)
            if end > start and ayah == start:
                merged_ranges += 1
            connection.execute(
                "INSERT INTO translation VALUES (?, ?, ?, ?, ?)", (surah, ayah, start, end, text)
            )
        connection.commit()
        time.sleep(0.3)
    total = connection.execute("SELECT COUNT(*) FROM translation").fetchone()[0]
    print(f"rows: {total} of 6236; merged ranges: {merged_ranges}; missing: {len(missing)}")
    if missing:
        print("missing ayahs:", " ".join(missing[:80]), "..." if len(missing) > 80 else "")


if __name__ == "__main__":
    main()
