"""Downloads the voice search test recordings into tracker/fixtures/search/ (gitignored).

40 ayahs are drawn with a fixed seed from the whole Quran, alternating between two reciters that
are not Husary (the tracker's own reciter), so AyahFinderAccuracyTest measures other voices.
Each EveryAyah MP3 becomes a 16 kHz mono WAV named SSSAAA.wav. Existing files are kept.

Usage: python3 -I tools/fetch_search_fixtures.py
"""

import json
import random
import subprocess
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REFERENCE = ROOT / "awn-models" / "ordered_quran_phonemes.json"
OUTPUT = ROOT / "tracker" / "fixtures" / "search"
MIRROR = "https://mirrors.quranicaudio.com/everyayah"
RECITER_FOLDERS = ["Alafasy_128kbps", "Minshawy_Murattal_128kbps"]
AYAH_COUNT = 40
SEED = 20261024


def ayah_keys():
    verses = json.loads(REFERENCE.read_text(encoding="utf-8"))["verses"]
    keys = [tuple(int(part) for part in key.split(":")) for key in verses]
    return sorted(keys)


def fetch(surah, ayah, folder):
    target = OUTPUT / f"{surah:03d}{ayah:03d}.wav"
    if target.is_file():
        return
    url = f"{MIRROR}/{folder}/{surah:03d}{ayah:03d}.mp3"
    mp3 = OUTPUT / f"{surah:03d}{ayah:03d}.mp3"
    with urllib.request.urlopen(url, timeout=60) as response:
        mp3.write_bytes(response.read())
    subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-y", "-i", str(mp3), "-ac", "1", "-ar", "16000", str(target)],
        check=True,
    )
    mp3.unlink()
    print(f"{surah}:{ayah} {folder}")


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    chosen = random.Random(SEED).sample(ayah_keys(), AYAH_COUNT)
    for index, (surah, ayah) in enumerate(sorted(chosen)):
        fetch(surah, ayah, RECITER_FOLDERS[index % len(RECITER_FOLDERS)])


if __name__ == "__main__":
    main()
