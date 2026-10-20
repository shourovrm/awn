#!/usr/bin/env python3
"""Check the lesson files against the Quran text and print every example word for review.

Run with `python3 -I tools/check_lessons.py`. The Quran database is read from
$AWN_DATA_DIR/quran/quran.sqlite (default: <repository>/awn-data, which is not present in a git
worktree; set AWN_DATA_DIR there). The script asserts the structure of every lesson and that every
referenced word exists, then prints the words. A person still has to confirm that each printed word
carries the rule; the script cannot judge that.
"""

import json
import os
import sqlite3
import sys
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parent.parent
LESSON_DIRECTORY = REPOSITORY / "feature/learn/src/main/assets/lessons"
EXPECTED_LESSON_IDS = [
    "makharij",
    "sifat",
    "noon-sakinah-tanween",
    "meem-sakinah",
    "ghunnah",
    "qalqalah",
    "madd-natural",
    "madd-extended",
    "raa",
    "lam-of-allah",
    "waqf-signs",
    "hamzat-al-wasl",
]
REQUIRED_FIELDS = ["id", "title_en", "title_bn", "body_en", "body_bn", "examples", "reviewed_by", "reviewed_on"]
EXAMPLE_FIELDS = ["surah", "ayah", "word_positions", "note_en", "note_bn"]
MINIMUM_EXAMPLES = 3


def database_path() -> Path:
    data_directory = Path(os.environ.get("AWN_DATA_DIR", REPOSITORY / "awn-data"))
    return data_directory / "quran" / "quran.sqlite"


def check_lesson(database: sqlite3.Connection, path: Path) -> list[str]:
    """Returns the problems found in one lesson file; prints its example words."""
    problems = []
    lesson = json.loads(path.read_text(encoding="utf-8"))
    for field in REQUIRED_FIELDS:
        if field not in lesson:
            problems.append(f"{path.name}: missing field {field}")
    if problems:
        return problems
    if lesson["id"] != path.stem:
        problems.append(f"{path.name}: id {lesson['id']!r} differs from the file name")
    for field in ("title_en", "title_bn", "body_en", "body_bn"):
        if not lesson[field].strip():
            problems.append(f"{path.name}: {field} is empty")
    if "#" in lesson["body_en"] or "**" in lesson["body_en"] or "**" in lesson["body_bn"]:
        problems.append(f"{path.name}: body contains markdown")
    if len(lesson["examples"]) < MINIMUM_EXAMPLES:
        problems.append(f"{path.name}: fewer than {MINIMUM_EXAMPLES} examples")

    status = "draft" if lesson["reviewed_by"] is None else f"reviewed by {lesson['reviewed_by']}"
    print(f"\n{lesson['id']} ({status}): {lesson['title_en']}")
    for example in lesson["examples"]:
        for field in EXAMPLE_FIELDS:
            if field not in example:
                problems.append(f"{path.name}: example missing {field}")
        if any(field not in example for field in EXAMPLE_FIELDS):
            continue
        surah, ayah = example["surah"], example["ayah"]
        words = dict(
            database.execute(
                "SELECT position, text_uthmani FROM word WHERE surah = ? AND ayah = ?",
                (surah, ayah),
            ).fetchall()
        )
        for position in example["word_positions"]:
            if position not in words:
                problems.append(f"{path.name}: {surah}:{ayah} has no word {position}")
        picked = [words.get(position, "?") for position in example["word_positions"]]
        print(f"  {surah}:{ayah} words {example['word_positions']}: {' '.join(picked)}")
        print(f"      {example['note_en']}")
    return problems


def main() -> int:
    database_file = database_path()
    if not database_file.is_file():
        print(f"Quran database not found at {database_file}; set AWN_DATA_DIR", file=sys.stderr)
        return 2
    database = sqlite3.connect(f"file:{database_file}?mode=ro", uri=True)

    found_ids = sorted(path.stem for path in LESSON_DIRECTORY.glob("*.json"))
    problems = []
    if found_ids != sorted(EXPECTED_LESSON_IDS):
        problems.append(f"lesson files {found_ids} differ from the expected list")
    example_count = 0
    for lesson_id in EXPECTED_LESSON_IDS:
        path = LESSON_DIRECTORY / f"{lesson_id}.json"
        if path.is_file():
            problems += check_lesson(database, path)
            example_count += len(json.loads(path.read_text(encoding="utf-8"))["examples"])

    print(f"\n{len(found_ids)} lessons, {example_count} examples, {len(problems)} problems")
    for problem in problems:
        print(f"PROBLEM: {problem}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
