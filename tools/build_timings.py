#!/usr/bin/env python3
"""Build per-reciter timing packs from the Quran.com API v4. No audio is downloaded.

Output: awn-data/timings/<reciter>.sqlite with
  segment(surah, ayah, position, start_ms, end_ms)
      one row per word; times are milliseconds from the start of the ayah file
  ayah_audio(surah, ayah, url, duration_ms, offset_ms)
      the per-ayah audio file the times refer to; offset_ms is 0 (kept so a
      surah-file source can be stored later); duration_ms may be NULL
  pack_info(key, value)

Two source shapes are used:
  ayah files (Alafasy, Husary, Husary Muallim, Minshawi): GET
      /verses/by_chapter/{n}?audio=<recitation id> returns, per ayah, the file
      URL and segments [first_index, end_index, start_ms, end_ms]: the words
      first_index + 1 .. end_index (1-based). A segment that spans several
      words is split equally. An ayah whose segments leave a word uncovered,
      run backwards or overlap gets no segment rows and is listed in
      pack_info key ayahs_without_word_timing.
      duration_ms is end_ms of the last segment because the API gives no file
      length; the player's own duration is the true length.
  no segments (Basfar): see build_from_everyayah_folder.

Reciters are found by name in GET /resources/recitations. Basfar is not listed
there (his id 163 exists only under /chapter_recitations).

Usage: python3 tools/build_timings.py
"""

import json
import os
import re
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_com

VERSES_AUDIO_BASE = "https://verses.quran.com/"
EVERYAYAH_URL = "https://mirrors.quranicaudio.com/everyayah/{folder}/{surah:03d}{ayah:03d}.mp3"
# The mirror serves the 192 kbps folder; the 64 kbps folder named in the plan returns 404 there.
BASFAR_EVERYAYAH_FOLDER = "Abdullah_Basfar_192kbps"

RECITERS = [
    # pack name, name fragment, style, expected recitation id, source shape
    {"pack": "alafasy", "name": "Afasy", "style": None, "recitation_id": 7, "shape": "ayah_files"},
    {"pack": "husary", "name": "Husary", "style": None, "recitation_id": 6, "shape": "ayah_files"},
    {"pack": "husary_muallim", "name": "Husary", "style": "Muallim", "recitation_id": 12, "shape": "ayah_files"},
    {"pack": "minshawi", "name": "Minshawi", "style": "Murattal", "recitation_id": 9, "shape": "ayah_files"},
    {"pack": "basfar", "name": None, "style": None, "recitation_id": None, "shape": "everyayah_folder"},
]


def find_recitation_id(listing, name_fragment, style):
    matches = [
        entry for entry in listing
        if name_fragment.lower() in entry["reciter_name"].lower() and entry["style"] == style
    ]
    if len(matches) != 1:
        raise ValueError(f"{len(matches)} recitations match name {name_fragment!r} style {style!r}")
    return matches[0]["id"]


def absolute_audio_url(url):
    if url.startswith("//"):
        return "https:" + url
    if url.startswith("http"):
        return url
    return VERSES_AUDIO_BASE + url


def collapse_double_slash(url):
    """The API returns .../abdullaah_basfar//001.mp3; the host serves both spellings."""
    return re.sub(r"(?<!:)//+", "/", url)


def word_counts_per_ayah():
    path = quran_com.sqlite_path("quran", "quran.sqlite")
    with sqlite3.connect(path) as connection:
        rows = connection.execute("SELECT surah, ayah, COUNT(*) FROM word GROUP BY surah, ayah").fetchall()
    return {(surah, ayah): count for surah, ayah, count in rows}


def create_database(path):
    if os.path.exists(path):
        os.remove(path)
    connection = sqlite3.connect(path)
    connection.executescript("""
        CREATE TABLE segment (
            surah INTEGER NOT NULL, ayah INTEGER NOT NULL, position INTEGER NOT NULL,
            start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL,
            PRIMARY KEY (surah, ayah, position));
        CREATE TABLE ayah_audio (
            surah INTEGER NOT NULL, ayah INTEGER NOT NULL, url TEXT NOT NULL,
            duration_ms INTEGER, offset_ms INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY (surah, ayah));
        CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    """)
    return connection


def expand_segments(segments, word_count):
    """Turn API segments into one (position, start_ms, end_ms) per word, or None if unusable.

    A segment [first_index, end_index, start_ms, end_ms] covers the words
    first_index + 1 .. end_index (1-based). Most segments cover one word; where
    one segment covers several words its time span is divided equally between
    them, and the number of such words is returned as the second value.
    An ayah is unusable when the segments do not cover words 1..word_count
    exactly once each, run backwards in time, or overlap.
    """
    rows = []
    interpolated_words = 0
    previous_end = 0
    for first_index, end_index, start_ms, end_ms in segments:
        words_in_segment = end_index - first_index
        if words_in_segment < 1 or end_ms <= start_ms or start_ms < previous_end:
            return None, 0
        previous_end = end_ms
        if words_in_segment > 1:
            interpolated_words += words_in_segment
        for offset in range(words_in_segment):
            word_start = start_ms + (end_ms - start_ms) * offset // words_in_segment
            word_end = start_ms + (end_ms - start_ms) * (offset + 1) // words_in_segment
            rows.append((first_index + 1 + offset, word_start, word_end))
    if [row[0] for row in rows] != list(range(1, word_count + 1)):
        return None, 0
    return rows, interpolated_words


def build_from_ayah_files(connection, recitation_id, word_counts):
    audio_rows, skipped_ayahs, interpolated_total = [], [], 0
    for chapter in range(1, quran_com.CHAPTER_COUNT + 1):
        for verse in quran_com.fetch_chapter_audio(chapter, recitation_id):
            surah, ayah = quran_com.split_verse_key(verse)
            audio = verse.get("audio")
            if not audio or not audio["segments"]:
                raise ValueError(f"recitation {recitation_id} has no audio for {surah}:{ayah}")
            audio_rows.append((surah, ayah, absolute_audio_url(audio["url"]), audio["segments"][-1][3], 0))
            word_rows, interpolated_words = expand_segments(audio["segments"], word_counts[(surah, ayah)])
            if word_rows is None:
                skipped_ayahs.append(f"{surah}:{ayah}")
                continue
            interpolated_total += interpolated_words
            connection.executemany("INSERT INTO segment VALUES (?,?,?,?,?)",
                                   [(surah, ayah, position, start, end) for position, start, end in word_rows])
    connection.executemany("INSERT INTO ayah_audio VALUES (?,?,?,?,?)", audio_rows)
    connection.execute("INSERT INTO pack_info VALUES ('ayahs_without_word_timing', ?)", (",".join(skipped_ayahs),))
    connection.execute("INSERT INTO pack_info VALUES ('interpolated_words', ?)", (str(interpolated_total),))
    return "word"


def build_from_everyayah_folder(connection, word_counts):
    """Basfar: Quran.com has no ayah files for him, so the pack lists the EveryAyah files.

    The only Quran.com data for Basfar (chapter recitation 163) is ayah
    timestamps inside the surah files. They are not usable: from Al-Baqarah on,
    the start of the last ayah lies after the end of the file (2:286 starts at
    8,401 s in a 7,151 s file), because the offsets accumulate an error. The
    pack therefore holds no timings; duration_ms is NULL because no source
    gives it.
    """
    audio_rows = [
        (surah, ayah, EVERYAYAH_URL.format(folder=BASFAR_EVERYAYAH_FOLDER, surah=surah, ayah=ayah), None, 0)
        for surah, ayah in sorted(word_counts)
    ]
    connection.executemany("INSERT INTO ayah_audio VALUES (?,?,?,?,?)", audio_rows)
    return "ayah"


def main():
    listing = quran_com.fetch("/resources/recitations", language="en")["recitations"]
    word_counts = word_counts_per_ayah()
    summary = {}
    for reciter in RECITERS:
        if reciter["name"]:
            found_id = find_recitation_id(listing, reciter["name"], reciter["style"])
            if found_id != reciter["recitation_id"]:
                raise ValueError(f"{reciter['pack']}: listing says id {found_id}, script expects {reciter['recitation_id']}")
        path = quran_com.sqlite_path("timings", reciter["pack"] + ".sqlite")
        connection = create_database(path)
        if reciter["shape"] == "ayah_files":
            granularity = build_from_ayah_files(connection, reciter["recitation_id"], word_counts)
        else:
            granularity = build_from_everyayah_folder(connection, word_counts)
        segment_total = connection.execute("SELECT COUNT(*) FROM segment").fetchone()[0]
        ayah_total = connection.execute("SELECT COUNT(*) FROM ayah_audio").fetchone()[0]
        covered_ayahs = connection.execute("SELECT COUNT(DISTINCT surah * 1000 + ayah) FROM segment").fetchone()[0]
        complete_ayahs = 0
        if granularity == "word":
            per_ayah = connection.execute("SELECT surah, ayah, COUNT(*) FROM segment GROUP BY surah, ayah").fetchall()
            complete_ayahs = sum(1 for s, a, n in per_ayah if word_counts[(s, a)] == n)
        connection.executemany("INSERT INTO pack_info VALUES (?, ?)", [
            ("format_version", "1"),
            ("quran_com_recitation_id", str(reciter["recitation_id"]) if reciter["recitation_id"] else "none"),
            ("granularity", granularity),
            ("source", "Quran.com API v4"),
        ])
        connection.commit()
        connection.close()
        summary[reciter["pack"]] = {
            "recitation_id": reciter["recitation_id"], "granularity": granularity,
            "ayahs": ayah_total, "segments": segment_total, "ayahs_with_segments": covered_ayahs,
            "ayahs_with_every_word": complete_ayahs, "bytes": os.path.getsize(path),
        }
        print(reciter["pack"], json.dumps(summary[reciter["pack"]]))


if __name__ == "__main__":
    main()
