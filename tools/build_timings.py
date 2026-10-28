#!/usr/bin/env python3
"""Build per-reciter timing packs from the Quran.com API v4. No audio is stored.

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
  whole-surah files with ayah offsets (Raad al-Kurdi): see build_from_gapless_database.

Reciters are found by name in GET /resources/recitations. Basfar is not listed
there (his id 163 exists only under /chapter_recitations).

Usage: python3 tools/build_timings.py [--gapless-db=PATH] [pack ...]
  (no pack argument builds every pack; the gapless pack is built only when --gapless-db is given)
"""

import bisect
import itertools
import json
import math
import os
import pathlib
import re
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_com
import reciter_catalogue
import walk_mp3_frames

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
    # Added with the second catalogue: every other recitation of GET /resources/recitations that has segments.
    {"pack": "sudais", "name": "Sudais", "style": None, "recitation_id": 3, "shape": "ayah_files"},
    {"pack": "shuraim", "name": "Shuraym", "style": None, "recitation_id": 10, "shape": "ayah_files"},
    {"pack": "shatri", "name": "Shatri", "style": None, "recitation_id": 4, "shape": "ayah_files"},
    {"pack": "abdul_basit_murattal", "name": "AbdulBaset", "style": "Murattal", "recitation_id": 2, "shape": "ayah_files"},
    {"pack": "abdul_basit_mujawwad", "name": "AbdulBaset", "style": "Mujawwad", "recitation_id": 1, "shape": "ayah_files"},
    {"pack": "rifai", "name": "Rifai", "style": None, "recitation_id": 5, "shape": "ayah_files"},
    {"pack": "tablawi", "name": "Tablawi", "style": None, "recitation_id": 11, "shape": "ayah_files"},
    {"pack": "minshawi_mujawwad", "name": "Minshawi", "style": "Mujawwad", "recitation_id": 8, "shape": "ayah_files"},
    {"pack": "raad_al_kurdi", "name": None, "style": None, "recitation_id": None, "shape": "gapless_database"},
]

GAPLESS_DATABASE_OPTION = "--gapless-db="
GAPLESS_SOURCE = "Quran for Android timing database (files.quran.app)"
WALK_REPORT_OPTION = "--walk-report="


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
    for raw_segment in segments:
        # Recitation 3 (Sudais) delivers the four numbers as strings, the others as integers.
        first_index, end_index, start_ms, end_ms = (int(number) for number in raw_segment)
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
            if not audio:
                raise ValueError(f"recitation {recitation_id} has no audio for {surah}:{ayah}")
            if not audio["segments"]:
                # The file exists but the API lists no segments (Shuraym 12:76): keep the file, no word timing.
                audio_rows.append((surah, ayah, absolute_audio_url(audio["url"]), None, 0))
                skipped_ayahs.append(f"{surah}:{ayah}")
                continue
            audio_rows.append((surah, ayah, absolute_audio_url(audio["url"]), int(audio["segments"][-1][3]), 0))
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


def whole_milliseconds(text):
    """The database stores times as text such as '2320.0'; a fraction would be a unit this code does not handle."""
    value = float(text)
    if not value.is_integer():
        raise ValueError(f"time {text!r} is not a whole number of milliseconds")
    return int(value)


def read_gapless_database(database_path):
    """(surah, ayah, start_ms) for every ayah, in surah and ayah order; the file is opened read-only.

    The source's word lists are not read: they repeat positions, and their counts differ from the Quran
    text (see docs/packs.md), so the pack carries ayah timings only.
    """
    uri = pathlib.Path(database_path).resolve().as_uri() + "?mode=ro"
    with sqlite3.connect(uri, uri=True) as connection:
        rows = connection.execute("SELECT sura, ayah, time FROM timings ORDER BY sura, ayah").fetchall()
    return [(int(surah), int(ayah), whole_milliseconds(time_text)) for surah, ayah, time_text in rows]


def ayah_windows(rows):
    """Add each ayah's end: the start of the next ayah in the same surah, or None for a surah's last ayah.

    The last ayah's length is not stated anywhere, because the surah file runs on past it, so its end is None.
    """
    windows = []
    for index, (surah, ayah, start_ms) in enumerate(rows):
        next_row = rows[index + 1] if index + 1 < len(rows) else None
        if next_row is not None and next_row[0] == surah:
            end_ms = next_row[2]
            if end_ms <= start_ms:
                raise ValueError(f"{surah}:{ayah} starts at {start_ms} ms, not before {surah}:{next_row[1]} at {end_ms} ms")
        else:
            end_ms = None
        windows.append((surah, ayah, start_ms, end_ms))
    return windows


AYAH_BACK_OFF_FRAMES = 4
MAXIMUM_START_LEAD_MS = 200


def ayah_frame_span(surah, ayah, frame_table, start_ms, end_ms):
    """(first_index, end_index) of an ayah in its surah file's frames.

    first_index is the frame that contains start_ms, moved back AYAH_BACK_OFF_FRAMES so a decoder starts
    before the ayah. end_index is the first frame at or after end_ms, the next ayah's start; None for a
    surah's last ayah, whose end the file does not state.
    """
    if start_ms >= frame_table.total_ms:
        raise ValueError(f"{surah}:{ayah} starts at {start_ms} ms, after the end of the file ({frame_table.total_ms:.0f} ms)")
    containing_index = bisect.bisect_right(frame_table.times_ms, start_ms) - 1
    first_index = max(containing_index - AYAH_BACK_OFF_FRAMES, 0)
    if end_ms is None:
        return first_index, None
    end_index = bisect.bisect_left(frame_table.times_ms, end_ms)
    if end_index >= len(frame_table.times_ms):
        raise ValueError(f"{surah}:{ayah} ends at {end_ms} ms, after the last frame of the file")
    return first_index, end_index


def check_ayah_spans_in_order(surah, ayahs, spans):
    """Each ayah must start in a later frame than the one before, and end at most a few frames into the next one."""
    for index in range(len(spans) - 1):
        ayah = ayahs[index][0]
        next_ayah = ayahs[index + 1][0]
        first_index, end_index = spans[index]
        next_first_index = spans[index + 1][0]
        if next_first_index <= first_index:
            raise ValueError(f"{surah}:{next_ayah} starts in a frame that is not after {surah}:{ayah}")
        if end_index is not None and end_index - next_first_index > AYAH_BACK_OFF_FRAMES + 1:
            raise ValueError(f"{surah}:{ayah} ends {end_index - next_first_index} frames after {surah}:{next_ayah} starts")


def ayah_byte_rows(surah, frame_table, ayahs):
    """(surah, ayah, start_byte, end_byte, start_byte_ms) for each (ayah, start_ms, end_ms) of one surah file.

    start_byte_ms is the start time of the frame at start_byte, rounded down; the app seeks to start_byte
    and reads the ayah's offset_ms from the same timeline, so the lead must be small and non-negative.
    """
    spans = [ayah_frame_span(surah, ayah, frame_table, start_ms, end_ms) for ayah, start_ms, end_ms in ayahs]
    check_ayah_spans_in_order(surah, ayahs, spans)
    rows = []
    for (ayah, start_ms, _), (first_index, end_index) in zip(ayahs, spans):
        start_byte = frame_table.offsets[first_index]
        start_byte_ms = math.floor(frame_table.times_ms[first_index])
        lead_ms = start_ms - start_byte_ms
        if not 0 <= lead_ms <= MAXIMUM_START_LEAD_MS:
            raise ValueError(f"{surah}:{ayah} starts {lead_ms} ms after its frame, outside 0 to {MAXIMUM_START_LEAD_MS} ms")
        end_byte = None if end_index is None else frame_table.offsets[end_index]
        rows.append((surah, ayah, start_byte, end_byte, start_byte_ms))
    return rows


def walk_report_lines(surah, surah_url, frame_table):
    """A summary line per surah file, then one line per bitrate change, for /tmp reports and for reading the walk."""
    lines = [f"{surah:03d} frames {len(frame_table.offsets)} duration_ms {frame_table.total_ms:.0f} "
             f"bitrates_kbps {sorted(set(frame_table.bitrates_kbps))} bitrate_changes {len(frame_table.bitrate_changes)} "
             f"{surah_url}"]
    for time_ms, from_kbps, to_kbps, frame_index in frame_table.bitrate_changes:
        lines.append(f"    frame {frame_index} at {time_ms:.0f} ms: {from_kbps} to {to_kbps} kbps")
    return lines


def build_from_gapless_database(connection, database_path, word_counts, pack, chunks_of_url=None, report_lines=None):
    """Raad al-Kurdi: whole-surah files, with ayah starts from Quran for Android's timing database.

    Each ayah row points at its surah file. offset_ms is the ayah's start in that file, duration_ms its
    length (NULL for a surah's last ayah). ayah_bytes gives the byte positions of the ayah's frames: each
    surah file is streamed once and walked frame by frame, since a bitrate-based byte estimate is wrong
    where the bitrate changes. The segment table stays empty: this pack has no word times.
    """
    if chunks_of_url is None:
        chunks_of_url = walk_mp3_frames.stream_url_chunks
    catalogue_entry = next(entry for entry in reciter_catalogue.RECITERS if entry["id"] == pack)
    rows = read_gapless_database(database_path)
    if [(surah, ayah) for surah, ayah, _ in rows] != sorted(word_counts):
        raise ValueError(f"{pack}: the database does not list the ayahs of the Quran text in order")
    audio_rows, byte_rows = [], []
    for surah, surah_windows in itertools.groupby(ayah_windows(rows), key=lambda window: window[0]):
        surah_windows = list(surah_windows)
        surah_url = reciter_catalogue.SURAH_URL.format(path=catalogue_entry["quranicaudio_path"], surah=surah)
        frame_table = walk_mp3_frames.collect_frame_table_from_url(surah_url, chunks_of_url)
        if report_lines is not None:
            report_lines.extend(walk_report_lines(surah, surah_url, frame_table))
        byte_rows.extend(ayah_byte_rows(surah, frame_table, [(ayah, start, end) for _, ayah, start, end in surah_windows]))
        for _, ayah, start_ms, end_ms in surah_windows:
            duration_ms = None if end_ms is None else end_ms - start_ms
            audio_rows.append((surah, ayah, surah_url, duration_ms, start_ms))
    connection.execute("""
        CREATE TABLE ayah_bytes (
            surah INTEGER NOT NULL, ayah INTEGER NOT NULL, start_byte INTEGER NOT NULL,
            end_byte INTEGER, start_byte_ms INTEGER NOT NULL,
            PRIMARY KEY (surah, ayah))""")
    connection.executemany("INSERT INTO ayah_audio VALUES (?,?,?,?,?)", audio_rows)
    connection.executemany("INSERT INTO ayah_bytes VALUES (?,?,?,?,?)", byte_rows)
    connection.executemany("INSERT INTO pack_info VALUES (?, ?)", [("audio_layout", "whole_surah")])
    return "ayah"


def source_name(reciter):
    return GAPLESS_SOURCE if reciter["shape"] == "gapless_database" else "Quran.com API v4"


def main():
    listing = quran_com.fetch("/resources/recitations", language="en")["recitations"]
    word_counts = word_counts_per_ayah()
    summary = {}
    gapless_database_path = None
    walk_report_path = None
    wanted_packs = set()
    for argument in sys.argv[1:]:
        if argument.startswith(GAPLESS_DATABASE_OPTION):
            gapless_database_path = argument[len(GAPLESS_DATABASE_OPTION):]
        elif argument.startswith(WALK_REPORT_OPTION):
            walk_report_path = argument[len(WALK_REPORT_OPTION):]
        else:
            wanted_packs.add(argument)
    walk_report_lines = [] if walk_report_path else None
    for reciter in RECITERS:
        if wanted_packs and reciter["pack"] not in wanted_packs:
            continue
        if reciter["shape"] == "gapless_database" and gapless_database_path is None:
            if reciter["pack"] in wanted_packs:
                raise ValueError(f"{reciter['pack']}: pass {GAPLESS_DATABASE_OPTION}PATH with the unzipped database")
            print(f"skipped {reciter['pack']}: no {GAPLESS_DATABASE_OPTION}PATH given")
            continue
        if reciter["name"]:
            found_id = find_recitation_id(listing, reciter["name"], reciter["style"])
            if found_id != reciter["recitation_id"]:
                raise ValueError(f"{reciter['pack']}: listing says id {found_id}, script expects {reciter['recitation_id']}")
        path = quran_com.sqlite_path("timings", reciter["pack"] + ".sqlite")
        connection = create_database(path)
        if reciter["shape"] == "ayah_files":
            granularity = build_from_ayah_files(connection, reciter["recitation_id"], word_counts)
        elif reciter["shape"] == "everyayah_folder":
            granularity = build_from_everyayah_folder(connection, word_counts)
        else:
            granularity = build_from_gapless_database(
                connection, gapless_database_path, word_counts, reciter["pack"], report_lines=walk_report_lines)
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
            ("source", source_name(reciter)),
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
