#!/usr/bin/env python3
"""Cheap check of a reciter's word timings against the real audio.

Takes random ayahs from the reciter's timing pack, downloads each ayah file, cuts every word out
of it with ffmpeg by its start and end time, and counts the cuts that hold speech-level sound for
most of their length with no long silent edge. Mistimed segments (cut too early, too late or
across a pause) fail that test. It is a stand-in for the later check that has the on-device
speech model transcribe each cut.

A cut matches when all three hold:
  1. at least 60 % of its length is above the speech threshold,
  2. it does not start with more than 30 % silence,
  3. it does not end with more than 30 % silence.
The speech threshold is set per ayah between its noise floor and its median level (see
speech_threshold), so a quiet recording and a loud one are judged alike.

Usage:
  python3 tools/verify_timings.py --reciter alafasy --count 20 --workdir /tmp/awn-verify
Needs Python 3 (standard library only) and ffmpeg on PATH. Exit status is 1 when the share of
matching words is below --min-share (default 0.9) or no ayah could be checked.
"""

import argparse
import array
import json
import math
import os
import random
import sqlite3
import subprocess
import sys
import urllib.error
import urllib.request
import wave

SAMPLE_RATE = 16000
FRAME_MS = 10
FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS // 1000
MIN_ACTIVE_SHARE = 0.6
MAX_SILENT_EDGE_SHARE = 0.3
THRESHOLD_POSITION = 0.4
MIN_SPEECH_ABOVE_FLOOR_DB = 10.0
DEFAULT_DATA_ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "awn-data")


def load_ayahs_with_word_timing(pack_path):
    """Returns {(surah, ayah): (url, [(position, start_ms, end_ms), ...])} for ayahs with segments."""
    connection = sqlite3.connect(pack_path)
    try:
        urls = {(s, a): url for s, a, url in connection.execute("SELECT surah, ayah, url FROM ayah_audio")}
        words = {}
        for surah, ayah, position, start_ms, end_ms in connection.execute(
            "SELECT surah, ayah, position, start_ms, end_ms FROM segment ORDER BY surah, ayah, position"
        ):
            words.setdefault((surah, ayah), []).append((position, start_ms, end_ms))
    finally:
        connection.close()
    return {key: (urls[key], segments) for key, segments in words.items() if key in urls}


def fallback_urls(url, reciter_entry, ayah_key):
    """The timed URL first, then the reciter's own ayah pattern on both quranicaudio domains."""
    candidates = [url]
    pattern = reciter_entry.get("ayah_url_pattern")
    if pattern:
        surah, ayah = ayah_key
        primary = pattern.replace("{SSS}", f"{surah:03d}").replace("{AAA}", f"{ayah:03d}")
        candidates.append(primary)
        candidates.append(primary.replace(".quranicaudio.com", ".quranicaudio.org"))
    return candidates


def download(urls, destination):
    if os.path.exists(destination) and os.path.getsize(destination) > 0:
        return True
    last_error = None
    for url in urls:
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "awn-verify"}), timeout=30) as response:
                data = response.read()
            if data:
                with open(destination, "wb") as handle:
                    handle.write(data)
                return True
        except (urllib.error.URLError, TimeoutError) as error:
            last_error = f"{url}: {error}"
    print(f"  download failed ({last_error})")
    return False


def run_ffmpeg(arguments):
    result = subprocess.run(["ffmpeg", "-v", "error", "-nostdin", *arguments], capture_output=True)
    if result.returncode != 0:
        raise RuntimeError("ffmpeg failed: " + result.stderr.decode(errors="replace").strip())
    return result.stdout


def frame_levels(samples):
    """Root-mean-square level of each 10 ms frame."""
    levels = []
    for start in range(0, len(samples) - FRAME_SAMPLES + 1, FRAME_SAMPLES):
        frame = samples[start : start + FRAME_SAMPLES]
        levels.append(math.sqrt(sum(value * value for value in frame) / FRAME_SAMPLES))
    return levels


def speech_threshold(levels):
    """Level between the ayah's noise floor and its typical speech level, in the same units.

    The floor is the 5th-percentile frame and the typical level is the median frame. The threshold
    sits 40 % of the way from the floor to the median, measured in dB. When the median is less than
    10 dB above the floor the ayah has no real silence, and every frame counts as sound.
    """
    ordered = sorted(max(level, 1.0) for level in levels)
    floor_db = 20 * math.log10(ordered[len(ordered) // 20])
    median_db = 20 * math.log10(ordered[len(ordered) // 2])
    if median_db - floor_db < MIN_SPEECH_ABOVE_FLOOR_DB:
        return 0.0
    return 10 ** ((floor_db + THRESHOLD_POSITION * (median_db - floor_db)) / 20)


def read_wav_samples(path):
    with wave.open(path, "rb") as handle:
        frames = handle.readframes(handle.getnframes())
    samples = array.array("h")
    samples.frombytes(frames)
    return samples


def judge_cut(levels, threshold):
    """Returns (matched, active_share, leading_share, trailing_share) for one word cut."""
    if not levels:
        return False, 0.0, 1.0, 1.0
    active = [level > threshold for level in levels]
    total = len(active)
    leading = next((index for index, flag in enumerate(active) if flag), total)
    trailing = next((index for index, flag in enumerate(reversed(active)) if flag), total)
    active_share = sum(active) / total
    leading_share, trailing_share = leading / total, trailing / total
    matched = (
        active_share >= MIN_ACTIVE_SHARE
        and leading_share <= MAX_SILENT_EDGE_SHARE
        and trailing_share <= MAX_SILENT_EDGE_SHARE
    )
    return matched, active_share, leading_share, trailing_share


def check_ayah(ayah_key, audio_path, segments, cut_directory):
    full = array.array("h")
    full.frombytes(run_ffmpeg(["-i", audio_path, "-f", "s16le", "-ac", "1", "-ar", str(SAMPLE_RATE), "-"]))
    threshold = speech_threshold(frame_levels(full))
    results = []
    for position, start_ms, end_ms in segments:
        cut_path = os.path.join(cut_directory, f"{ayah_key[0]:03d}{ayah_key[1]:03d}_{position:02d}.wav")
        run_ffmpeg(
            ["-y", "-ss", f"{start_ms / 1000:.3f}", "-t", f"{(end_ms - start_ms) / 1000:.3f}", "-i", audio_path,
             "-ac", "1", "-ar", str(SAMPLE_RATE), cut_path]
        )
        results.append((position, end_ms - start_ms, *judge_cut(frame_levels(read_wav_samples(cut_path)), threshold)))
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--reciter", required=True, help="reciter id in reciters.json, for example alafasy")
    parser.add_argument("--count", type=int, default=20, help="number of random ayahs (default 20)")
    parser.add_argument("--seed", type=int, default=1, help="random seed, so a run can be repeated")
    parser.add_argument("--data-root", default=DEFAULT_DATA_ROOT, help="folder with timings/ and manifests/")
    parser.add_argument("--workdir", default="/tmp/awn-verify-timings", help="where audio and cuts are written")
    parser.add_argument("--min-share", type=float, default=0.9, help="share of matching words needed to pass")
    arguments = parser.parse_args()

    with open(os.path.join(arguments.data_root, "manifests", "reciters.json"), encoding="utf-8") as handle:
        entries = {entry["id"]: entry for entry in json.load(handle)["reciters"]}
    entry = entries.get(arguments.reciter)
    if entry is None or not entry.get("timing_pack"):
        sys.exit(f"{arguments.reciter} has no word-timing pack (known: {', '.join(sorted(entries))})")

    ayahs = load_ayahs_with_word_timing(os.path.join(arguments.data_root, entry["file"]))
    chosen = random.Random(arguments.seed).sample(sorted(ayahs), min(arguments.count, len(ayahs)))
    audio_directory = os.path.join(arguments.workdir, arguments.reciter, "audio")
    cut_directory = os.path.join(arguments.workdir, arguments.reciter, "cuts")
    os.makedirs(audio_directory, exist_ok=True)
    os.makedirs(cut_directory, exist_ok=True)

    matched_words = total_words = checked_ayahs = 0
    for key in chosen:
        url, segments = ayahs[key]
        audio_path = os.path.join(audio_directory, f"{key[0]:03d}{key[1]:03d}.mp3")
        if not download(fallback_urls(url, entry, key), audio_path):
            continue
        results = check_ayah(key, audio_path, segments, cut_directory)
        matches = sum(1 for result in results if result[2])
        matched_words += matches
        total_words += len(results)
        checked_ayahs += 1
        failed = [f"w{position}({duration}ms act={active:.0%} lead={lead:.0%} trail={trail:.0%})"
                  for position, duration, ok, active, lead, trail in results if not ok]
        print(f"{key[0]}:{key[1]:<3} words={len(results):<3} matched={matches:<3} {' '.join(failed)}")

    if total_words == 0:
        sys.exit("no ayah could be checked")
    share = matched_words / total_words
    print(f"reciter={arguments.reciter} ayahs={checked_ayahs}/{len(chosen)} "
          f"matched_words={matched_words}/{total_words} share={share:.1%}")
    sys.exit(0 if share >= arguments.min_share else 1)


if __name__ == "__main__":
    main()
