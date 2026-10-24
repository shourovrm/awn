"""Checks topics_source.py against the Quran text and writes the app's topics.json.

Run from the repository root: python3 -I tools/topics/build_topics.py
"""
import json
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from topics_source import CATEGORIES, TOPICS  # noqa: E402

QURAN = ROOT / "core/data/src/main/assets/packs/quran.sqlite"
DUAS = ROOT / "feature/duas/src/main/assets/duas.json"
OUTPUT = ROOT / "feature/duas/src/main/assets/topics.json"
MAX_AYAHS_PER_CARD = 8


def parse_range(text):
    surah_text, ayahs = text.split(":")
    first_text, _, last_text = ayahs.partition("-")
    first = int(first_text)
    last = int(last_text) if last_text else first
    return int(surah_text), first, last


def check_range(database, text, where):
    surah, first, last = parse_range(text)
    if last < first:
        raise SystemExit(f"{where}: {text} ends before it starts")
    if last - first + 1 > MAX_AYAHS_PER_CARD:
        raise SystemExit(f"{where}: {text} has more than {MAX_AYAHS_PER_CARD} ayahs")
    count = database.execute(
        "SELECT verses_count FROM chapter WHERE number = ?", (surah,)
    ).fetchone()
    if count is None or last > count[0]:
        raise SystemExit(f"{where}: {text} is not in the Quran")
    return {"surah": surah, "first": first, "last": last}


def main():
    database = sqlite3.connect(QURAN)
    dua_groups = {group["key"] for group in json.loads(DUAS.read_text())["groups"]}
    category_keys = {key for key, _ in CATEGORIES}
    topics = []
    seen_topics = set()
    for key, category, (name_en, name_bn), duas_group, parts in TOPICS:
        if key in seen_topics:
            raise SystemExit(f"topic {key} appears twice")
        seen_topics.add(key)
        if category not in category_keys:
            raise SystemExit(f"topic {key}: unknown category {category}")
        if duas_group is not None and duas_group not in dua_groups:
            raise SystemExit(f"topic {key}: unknown duas group {duas_group}")
        part_keys = set()
        written_parts = []
        for part_key, (part_en, part_bn), ranges in parts:
            if part_key in part_keys:
                raise SystemExit(f"topic {key}: part {part_key} appears twice")
            part_keys.add(part_key)
            where = f"{key}/{part_key}"
            written_parts.append({
                "key": part_key,
                "name_en": part_en,
                "name_bn": part_bn,
                "passages": [check_range(database, text, where) for text in ranges],
            })
        topics.append({
            "key": key,
            "category": category,
            "name_en": name_en,
            "name_bn": name_bn,
            "duas_group": duas_group,
            "parts": written_parts,
        })
    output = {
        "note": "Ayah ranges chosen by the Awn project; written by tools/topics/build_topics.py.",
        "categories": [
            {"key": key, "name_en": en, "name_bn": bn} for key, (en, bn) in CATEGORIES
        ],
        "topics": topics,
    }
    OUTPUT.write_text(json.dumps(output, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    passages = sum(len(part["passages"]) for topic in topics for part in topic["parts"])
    print(f"{len(topics)} topics, {sum(len(t['parts']) for t in topics)} parts, {passages} passages")


if __name__ == "__main__":
    main()
