#!/usr/bin/env python3
"""Build the tajweed-coloured Unicode text pack from the Quran.com API v4.

Output: awn-data/quran/tajweed.sqlite with
  tajweed_word(surah, ayah, position, markup)
      one row per spoken word, keyed like quran.sqlite.word; markup is the Quran.com
      word field text_uthmani_tajweed (Uthmani Unicode text with inline rule tags).
  tajweed_rule(name, meaning)
      the rule class names that occur in the markup and what each one marks.
  pack_info(key, value)

Markup rules (checked for all 77,429 words while building; the build fails on any violation):
  - the only tags are <rule class=NAME> and </rule>, written exactly so (no quotes, no other attribute);
  - NAME is one of the class names in tajweed_rule or "custom-alef-maksora";
  - rules may nest: <rule class=madda_normal><rule class=custom-alef-maksora>ٰ</rule></rule>;
    the innermost rule that covers a letter is the one to colour it by, except custom-alef-maksora,
    which only repairs the drawing of a dagger alef and carries no colour;
  - a rule never spans a space or a word boundary: a rule that Quran.com's verse text draws across two
    words is split in two at the boundary, so every row is self-contained;
  - the text has no "&", no other "<" or ">", and no ayah-end sign (those words are not rows).
  One source word is damaged (32:3:3, an HTML entity in the place of alef maksura) and is repaired;
  pack_info.repaired_words lists it.
  Removing every tag leaves the word's Unicode text. Its consonant skeleton (letters without tatweel, alef
  and hamza; alef maksura and dotless ba read as ya) equals that of quran.sqlite text_uthmani; the marks
  differ slightly because Quran.com's tajweed text spells some signs with other code points, and it
  writes the dagger alef carrier as dotless ba (U+066E) in places.

Usage: python3 tools/build_tajweed.py
"""

import collections
import os
import re
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_com

TAG = re.compile(r"</?rule(?: class=[a-z_-]+)?>")
OPEN_TAG = re.compile(r"<rule class=([a-z_-]+)>")
ARABIC_LETTER = re.compile("[\u0621-\u063f\u0641-\u064a\u066e\u0671\u0679-\u06d3]")
# Letters that the two spellings write differently and that carry no consonant: alef and hamza in all forms.
SKELETON_TRANSLATION = str.maketrans({
    "\u0623": "", "\u0625": "", "\u0622": "", "\u0671": "", "\u0627": "",  # alef forms
    "\u0621": "", "\u0624": "", "\u0626": "",  # hamza and its seats
    "\u0649": "\u064a", "\u066e": "\u064a",  # alef maksura and dotless ba (the ya carrier of Quran.com) count as ya
})

RULE_MEANINGS = {
    "ham_wasl": "hamzat al-wasl (connecting alef)",
    "slnt": "silent letter",
    "laam_shamsiyah": "lam shamsiyyah (assimilated lam of the article)",
    "madda_normal": "natural madd, 2 counts",
    "madda_permissible": "permissible madd, 2, 4 or 6 counts",
    "madda_obligatory_mottasel": "obligatory connected madd (madd muttasil), 4 or 5 counts",
    "madda_obligatory_monfasel": "obligatory separated madd (madd munfasil), 4 or 5 counts",
    "madda_necessary": "necessary madd, 6 counts",
    "qalaqah": "qalqalah (echo)",
    "ikhafa_shafawi": "ikhfa shafawi (labial concealment of meem)",
    "ikhafa": "ikhfa (concealment of noon or tanween)",
    "idgham_shafawi": "idgham shafawi (labial merging of meem)",
    "iqlab": "iqlab (noon or tanween turned into meem)",
    "idgham_ghunnah": "idgham with ghunnah",
    "idgham_wo_ghunnah": "idgham without ghunnah",
    "idgham_mutajanisayn": "idgham of two letters of the same articulation point",
    "idgham_mutaqaribayn": "idgham of two letters of close articulation points",
    "ghunnah": "ghunnah (nasal sound on a doubled noon or meem)",
    "custom-alef-maksora": "drawing helper for a dagger alef, no tajweed meaning",
}
WORD_FIELDS = "text_uthmani_tajweed"

# One word of the source is damaged: 32:3:3 has the HTML entity "&gt;" and a dotless ba (U+066E) where the
# plain text has alef maksura (ى). The letters around it are intact, so the damaged pair is replaced.
SOURCE_REPAIRS = {(32, 3, 3): ("&gt;\u066e", "\u0649")}


def fetch_chapter_words(chapter_number):
    verses = []
    page = 1
    while page:
        response = quran_com.fetch(
            f"/verses/by_chapter/{chapter_number}", words="true", word_fields=WORD_FIELDS,
            fields="verse_key", per_page=50, page=page)
        verses.extend(response["verses"])
        page = response["pagination"]["next_page"]
    return verses


def check_markup(markup, where):
    if "&" in markup:
        raise ValueError(f"{where}: ampersand in markup {markup!r}")
    without_tags = TAG.sub("", markup)
    if "<" in without_tags or ">" in without_tags:
        raise ValueError(f"{where}: unexpected tag in {markup!r}")
    depth = 0
    for match in re.finditer(r"</?rule[^>]*>", markup):
        depth += -1 if match.group(0) == "</rule>" else 1
        if depth < 0:
            raise ValueError(f"{where}: closing tag without opening tag in {markup!r}")
    if depth != 0:
        raise ValueError(f"{where}: unclosed rule in {markup!r}")
    if " " in without_tags:
        raise ValueError(f"{where}: a space inside one word {markup!r}")
    for name in OPEN_TAG.findall(markup):
        if name not in RULE_MEANINGS:
            raise ValueError(f"{where}: unknown rule class {name}")
    return without_tags


def consonant_skeleton(text):
    return "".join(ARABIC_LETTER.findall(text)).translate(SKELETON_TRANSLATION)


def main():
    with sqlite3.connect(quran_com.sqlite_path("quran", "quran.sqlite")) as quran:
        uthmani_text = {(s, a, p): t for s, a, p, t in quran.execute(
            "SELECT surah, ayah, position, text_uthmani FROM word")}
    path = quran_com.sqlite_path("quran", "tajweed.sqlite")
    if os.path.exists(path):
        os.remove(path)
    connection = sqlite3.connect(path)
    connection.executescript("""
        CREATE TABLE tajweed_word (
            surah INTEGER NOT NULL, ayah INTEGER NOT NULL, position INTEGER NOT NULL,
            markup TEXT NOT NULL, PRIMARY KEY (surah, ayah, position));
        CREATE TABLE tajweed_rule (name TEXT PRIMARY KEY, meaning TEXT NOT NULL);
        CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    """)
    rule_counts = collections.Counter()
    skeleton_mismatches = []
    repaired_words = []
    seen = set()
    for chapter in range(1, quran_com.CHAPTER_COUNT + 1):
        for verse in fetch_chapter_words(chapter):
            surah, ayah = quran_com.split_verse_key(verse)
            for word in verse["words"]:
                if word["char_type_name"] != "word":
                    continue
                key = (surah, ayah, word["position"])
                markup = word["text_uthmani_tajweed"]
                if not markup:
                    raise ValueError(f"{key}: empty tajweed text")
                if key in SOURCE_REPAIRS:
                    damaged, repaired = SOURCE_REPAIRS[key]
                    if damaged not in markup:
                        raise ValueError(f"{key}: the known damage is gone from the source; remove the repair")
                    markup = markup.replace(damaged, repaired)
                    repaired_words.append(f"{surah}:{ayah}:{word['position']}")
                plain = check_markup(markup, key)
                rule_counts.update(OPEN_TAG.findall(markup))
                if consonant_skeleton(plain) != consonant_skeleton(uthmani_text[key]):
                    skeleton_mismatches.append(f"{surah}:{ayah}:{word['position']}")
                connection.execute("INSERT INTO tajweed_word VALUES (?,?,?,?)", (*key, markup))
                seen.add(key)
    if seen != set(uthmani_text):
        raise ValueError("tajweed words differ from quran.sqlite words")
    connection.executemany("INSERT INTO tajweed_rule VALUES (?, ?)",
                           [(name, RULE_MEANINGS[name]) for name in sorted(rule_counts)])
    connection.executemany("INSERT INTO pack_info VALUES (?, ?)", [
        ("format_version", "1"),
        ("source", "Quran.com API v4, words[].text_uthmani_tajweed"),
        ("skeleton_mismatches", ",".join(skeleton_mismatches)),
        ("repaired_words", ",".join(repaired_words)),
        ("rule_counts", ",".join(f"{name}={count}" for name, count in sorted(rule_counts.items()))),
    ])
    connection.commit()
    connection.close()
    print(f"tajweed.sqlite: {len(seen)} words, {sum(rule_counts.values())} rules in {len(rule_counts)} classes,"
          f" {len(skeleton_mismatches)} consonant-skeleton mismatches, {os.path.getsize(path)} bytes")
    print(dict(sorted(rule_counts.items())))
    if skeleton_mismatches:
        print("first mismatches:", skeleton_mismatches[:10])


if __name__ == "__main__":
    main()
