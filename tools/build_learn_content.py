#!/usr/bin/env python3
"""Turns the lesson Markdown in content/lessons into the lesson data the Learn tab ships.

Run with `python3 -I tools/build_learn_content.py --lessons content/lessons --quran quran.sqlite
--out lessons.json`. The Gradle task `generateLearnContent` of :feature:learn runs it at build time,
so the output is not committed. The file format is described in content/lessons/README.md.

The script fails, with the file and line, on: missing or unknown front matter, a word or ayah key
that is not in the Quran text, Arabic outside `{{key}}` and `[[fragment]]`, an `examples` list that
differs from the keys the lesson uses, a check or practice item without a valid answer, a grid line that is not
`Arabic | transliteration | clip id`, and a translation whose screens, keys or clip ids differ
from the English file.
"""

import argparse
import json
import re
import sqlite3
import sys
from pathlib import Path

FORMAT_VERSION = 1
SOURCE_LANGUAGE = "en"
COURSES = ("basics", "grammar", "letters", "tajweed")
REQUIRED_KEYS = ("id", "title", "order", "appears_after", "reference", "licence", "examples")
TRANSLATION_KEYS = ("translated_from", "source_version", "translation_reviewed_by")
GRAMMAR_KEYS = ("introduced_by",)
OPTIONAL_KEYS = ("reviewed_by", "version") + GRAMMAR_KEYS + TRANSLATION_KEYS
KEY_PATTERN = re.compile(r"^\d{1,3}:\d{1,3}(:\d{1,3})?$")
TOKEN_PATTERN = re.compile(r"\{\{([^}]*)\}\}")
FRAGMENT_PATTERN = re.compile(r"\[\[([^\]]*)\]\]")
ARABIC_PATTERN = re.compile("[؀-ۿݐ-ݿﭐ-﷿ﹰ-﻿]")
HEADING_PATTERN = re.compile(r"^## (Screen \d+|Check|Answer|Grid|Practice): (.+)$")
CLIP_PATTERN = re.compile(r"^[a-z0-9-]+/[a-z0-9-]+$")
GRID_LINE_PREFIX = "- "
# Grid and Practice screens sit outside the Check and Answer pairing.
FREE_KINDS = ("grid", "practice")
URL_PATTERN = re.compile(r"https?://\S+")
TERM_PATTERN = re.compile(r"^(.+?) \((.+)\)$")
LETTERS_PREFIX = "letters:"
LETTER_ENTRY_PATTERN = re.compile(r"^\[\[([^\]]+)\]\]\s+(.+)$")


class LessonError(Exception):
    """A lesson file breaks the format; the message names the file."""


def parse_scalar(raw):
    raw = raw.strip()
    if raw.startswith('"') and raw.endswith('"') and len(raw) >= 2:
        return raw[1:-1].replace('\\"', '"')
    if raw.startswith("[") and raw.endswith("]"):
        inner = raw[1:-1].strip()
        return [item.strip() for item in inner.split(",")] if inner else []
    return raw


def split_front_matter(text, name):
    """Returns (front matter as a dict, body, number of lines the front matter took)."""
    lines = text.split("\n")
    if not lines or lines[0] != "---":
        raise LessonError(f"{name}: the file must start with a --- line.")
    try:
        end = lines.index("---", 1)
    except ValueError as error:
        raise LessonError(f"{name}: the front matter has no closing --- line.") from error
    front = {}
    for number, line in enumerate(lines[1:end], start=2):
        if not line.strip():
            continue
        if ":" not in line:
            raise LessonError(f"{name}:{number}: expected 'key: value', got {line!r}.")
        key, _, value = line.partition(":")
        key = key.strip()
        if key in front:
            raise LessonError(f"{name}:{number}: the key {key} appears twice.")
        front[key] = parse_scalar(value)
    return front, "\n".join(lines[end + 1 :]), end + 1


def check_front_matter(front, name, is_translation):
    for key in REQUIRED_KEYS:
        if key not in front:
            raise LessonError(f"{name}: front matter lacks {key}.")
    unknown = set(front) - set(REQUIRED_KEYS) - set(OPTIONAL_KEYS)
    if unknown:
        raise LessonError(f"{name}: unknown front matter keys {sorted(unknown)}.")
    if is_translation:
        for key in TRANSLATION_KEYS:
            if key not in front:
                raise LessonError(f"{name}: a translation needs {key}.")
    try:
        front["order"] = int(front["order"])
    except ValueError as error:
        raise LessonError(f"{name}: order must be a number.") from error
    for key_name in ("examples", "introduced_by"):
        for key in front.get(key_name, []):
            if not KEY_PATTERN.match(key):
                raise LessonError(f"{name}: {key_name} holds {key!r}, not a word or ayah key.")


def scan_text(text, where, used_keys):
    """Checks one line of lesson text and returns it with the keys it uses added to [used_keys].

    Arabic outside `{{key}}` and `[[fragment]]` is refused: a Quran word must be named by its key
    so the app can draw it in the reader's script, and a fragment must be marked as one.
    """
    for match in TOKEN_PATTERN.finditer(text):
        key = match.group(1).strip()
        if not KEY_PATTERN.match(key):
            raise LessonError(f"{where}: {{{{{key}}}}} is not a word or ayah key.")
        used_keys.append(key)
    remainder = TOKEN_PATTERN.sub("", text)
    for match in FRAGMENT_PATTERN.finditer(remainder):
        if not ARABIC_PATTERN.search(match.group(1)):
            raise LessonError(f"{where}: [[{match.group(1)}]] holds no Arabic.")
    remainder = FRAGMENT_PATTERN.sub("", remainder)
    if ARABIC_PATTERN.search(remainder):
        raise LessonError(
            f"{where}: Arabic outside {{{{key}}}} and [[fragment]] in {remainder.strip()!r}."
        )
    return text


def parse_table(lines, where, used_keys):
    if len(lines) < 3 or not set(lines[1].replace("|", "").strip()) <= set("- "):
        raise LessonError(f"{where}: a table needs a header row and a |---| row.")

    def cells(line):
        return [scan_text(cell.strip(), where, used_keys) for cell in line.strip().strip("|").split("|")]

    header = cells(lines[0])
    rows = [cells(line) for line in lines[2:]]
    for row in rows:
        if len(row) != len(header):
            raise LessonError(f"{where}: a table row has {len(row)} cells, the header {len(header)}.")
    return {"type": "table", "header": header, "rows": rows}


def parse_letters(text, where, used_keys):
    """Reads `letters: [[ا]] alif; [[ب]] baa; ...` as a grid of letters with their names."""
    items = []
    for entry in text[len(LETTERS_PREFIX) :].split(";"):
        entry = entry.strip()
        match = LETTER_ENTRY_PATTERN.match(entry)
        if not match:
            raise LessonError(f"{where}: a letters entry looks like '[[ب]] baa', not {entry!r}.")
        scan_text(entry, where, used_keys)
        items.append({"letter": match.group(1), "name": match.group(2).strip()})
    if not items:
        raise LessonError(f"{where}: a letters block needs at least one letter.")
    return {"type": "letters", "items": items}


def parse_blocks(lines, where, used_keys):
    """Turns the lines of a screen into blocks: paragraph, bullets, table, ayah or letters."""
    blocks = []
    for paragraph in split_paragraphs(lines):
        first = paragraph[0]
        text = " ".join(paragraph)
        if first.startswith(LETTERS_PREFIX):
            blocks.append(parse_letters(text, where, used_keys))
        elif first.startswith("|"):
            blocks.append(parse_table(paragraph, where, used_keys))
        elif first.startswith("- "):
            items = [scan_text(line[2:].strip(), where, used_keys) for line in paragraph]
            blocks.append({"type": "bullets", "items": items})
        elif re.fullmatch(r"\{\{\d{1,3}:\d{1,3}\}\}", text.strip()):
            key = text.strip()[2:-2]
            used_keys.append(key)
            blocks.append({"type": "ayah", "key": key})
        else:
            equation = parse_equation(text, where, used_keys)
            if equation:
                blocks.append(equation)
            else:
                blocks.append({"type": "paragraph", "text": scan_text(text, where, used_keys)})
    return blocks


def parse_equation(text, where, used_keys):
    """Reads `A (gloss) + B (gloss) = C (gloss)` as the parts of a word and the word they make.

    Returns None when the paragraph is not of that form, so that ordinary sentences with a plus
    sign stay paragraphs.
    """
    if " + " not in text or text.count(" = ") != 1:
        return None
    left, right = text.split(" = ")
    terms = [match for match in (TERM_PATTERN.match(t) for t in left.split(" + ") + [right]) if match]
    if len(terms) != len(left.split(" + ")) + 1:
        return None
    parts = [
        {"text": scan_text(match.group(1), where, used_keys), "gloss": match.group(2)}
        for match in terms
    ]
    return {"type": "equation", "parts": parts[:-1], "result": parts[-1]}


def split_paragraphs(lines):
    paragraphs = []
    current = []
    for line in lines:
        if line.strip():
            current.append(line.rstrip())
        elif current:
            paragraphs.append(current)
            current = []
    if current:
        paragraphs.append(current)
    return paragraphs


def parse_clip(value, where):
    if not CLIP_PATTERN.match(value):
        raise LessonError(f"{where}: clip id must look like 'qaida-04/012', not {value!r}.")
    return value


def parse_check(lines, where, used_keys, is_practice=False):
    """Reads the question lines of a Check screen, or of a Practice item when [is_practice]."""
    check = {"options": []}
    for line in lines:
        if not line.strip():
            continue
        if line.startswith("- "):
            check["options"].append(scan_text(line[2:].strip(), where, used_keys))
            continue
        name, _, value = line.partition(":")
        name = name.strip()
        value = value.strip()
        if name == "question":
            check["question"] = scan_text(value, where, used_keys)
        elif name in ("ayah", "highlight"):
            if not KEY_PATTERN.match(value):
                raise LessonError(f"{where}: {name} must be a key, not {value!r}.")
            used_keys.append(value)
            check[name] = value
        elif name == "answer":
            check["answer"] = int(value) if value.isdigit() else -1
        elif is_practice and name == "clip":
            check["clip"] = parse_clip(value, where)
        elif is_practice and name == "explain":
            check["explain"] = scan_text(value, where, used_keys)
        else:
            raise LessonError(f"{where}: unknown check line {line!r}.")
    if "question" not in check:
        raise LessonError(f"{where}: a check needs a question.")
    if len(check["options"]) < 2:
        raise LessonError(f"{where}: a check needs at least two options.")
    if not 1 <= check.get("answer", -1) <= len(check["options"]):
        raise LessonError(f"{where}: answer must be an option number from 1 to {len(check['options'])}.")
    if is_practice and "explain" not in check:
        raise LessonError(f"{where}: a practice item needs an explain line.")
    if "highlight" in check and "ayah" not in check:
        raise LessonError(f"{where}: highlight needs an ayah.")
    check["answer"] -= 1
    return check


def parse_grid(lines, where):
    """Reads the lines of a Grid screen: `- Arabic | transliteration | clip id` per cell."""
    cells = []
    for line in lines:
        if not line.strip():
            continue
        parts = [part.strip() for part in line[len(GRID_LINE_PREFIX) :].split("|")]
        if not line.startswith(GRID_LINE_PREFIX) or len(parts) != 3 or not all(parts):
            raise LessonError(
                f"{where}: a grid line looks like '- بَ | ba | qaida-04/012', not {line!r}."
            )
        arabic, transliteration, clip = parts
        if not ARABIC_PATTERN.search(arabic):
            raise LessonError(f"{where}: grid cell {arabic!r} holds no Arabic.")
        if ARABIC_PATTERN.search(transliteration):
            raise LessonError(f"{where}: the transliteration {transliteration!r} holds Arabic.")
        cells.append(
            {"arabic": arabic, "transliteration": transliteration, "clip": parse_clip(clip, where)}
        )
    if not cells:
        raise LessonError(f"{where}: a grid line looks like '- بَ | ba | qaida-04/012'; there is none.")
    return cells


def parse_body(body, name, first_line, used_keys):
    """Returns the screens of a lesson body: reading screens, then check and answer."""
    screens = []
    current = None
    for offset, line in enumerate(body.split("\n")):
        number = first_line + offset
        if line.startswith("## "):
            match = HEADING_PATTERN.match(line)
            if not match:
                raise LessonError(f"{name}:{number}: heading must be '## Screen N: title', '## Check: title', '## Answer: title', '## Grid: title' or '## Practice: title'.")
            kind = match.group(1).split(" ")[0].lower()
            title = scan_text(match.group(2).strip(), f"{name}:{number}", used_keys)
            current = {"kind": kind, "title": title, "lines": [], "where": f"{name}:{number}"}
            screens.append(current)
        elif current is None:
            if line.strip():
                raise LessonError(f"{name}:{number}: text before the first screen heading.")
        else:
            current["lines"].append(line)
    if not screens:
        raise LessonError(f"{name}: the lesson has no screens.")
    result = []
    for screen in screens:
        entry = {"kind": screen["kind"], "title": screen["title"]}
        if screen["kind"] == "check":
            entry["check"] = parse_check(screen["lines"], screen["where"], used_keys)
        elif screen["kind"] == "practice":
            entry["check"] = parse_check(
                screen["lines"], screen["where"], used_keys, is_practice=True
            )
        elif screen["kind"] == "grid":
            entry["cells"] = parse_grid(screen["lines"], screen["where"])
        else:
            entry["blocks"] = parse_blocks(screen["lines"], screen["where"], used_keys)
        result.append(entry)
    check_screen_order(result, name)
    return result


def check_screen_order(screens, name):
    """Reading screens come first; the lesson may end with Check and Answer pairs."""
    kinds = [screen["kind"] for screen in screens if screen["kind"] not in FREE_KINDS]
    if "check" in kinds:
        position = kinds.index("check")
        tail = kinds[position:]
        pair_count = len(tail) // 2
        if tail != ["check", "answer"] * pair_count or pair_count == 0:
            raise LessonError(f"{name}: a lesson ends with Check screens, each followed by its Answer screen.")
    elif "answer" in kinds:
        raise LessonError(f"{name}: an Answer screen needs a Check screen before it.")


def check_keys_exist(database, keys, name):
    for key in sorted(set(keys)):
        parts = [int(part) for part in key.split(":")]
        if len(parts) == 3:
            row = database.execute(
                "SELECT 1 FROM word WHERE surah = ? AND ayah = ? AND position = ?", parts
            ).fetchone()
        else:
            row = database.execute(
                "SELECT 1 FROM ayah WHERE surah = ? AND ayah = ?", parts
            ).fetchone()
        if row is None:
            raise LessonError(f"{name}: the key {key} is not in the Quran text.")


def reference_link(reference):
    """Returns {label, url} for a front matter reference, or None when it has no link."""
    match = URL_PATTERN.search(reference)
    if not match:
        return None
    label = reference[: match.start()].strip().rstrip(",").strip()
    if label.startswith("Follows "):
        label = label[len("Follows ") :]
    return {"label": label, "url": match.group(0)}


def read_lesson_file(path, database):
    name = str(path)
    front, body, offset = split_front_matter(path.read_text(encoding="utf-8"), name)
    language = path.parent.parent.name
    course = path.parent.name
    check_front_matter(front, name, is_translation=language != SOURCE_LANGUAGE)
    if course not in COURSES:
        raise LessonError(f"{name}: the course folder must be one of {COURSES}.")
    expected_id = f"{course}-{path.stem}"
    if front["id"] != expected_id:
        raise LessonError(f"{name}: id {front['id']!r} must be {expected_id!r}.")
    if "introduced_by" in front and course != "grammar":
        raise LessonError(f"{name}: introduced_by belongs to grammar lessons only.")
    used_keys = []
    screens = parse_body(body, name, offset + 1, used_keys)
    check_keys_exist(database, used_keys + front["examples"] + front.get("introduced_by", []), name)
    if set(used_keys) != set(front["examples"]):
        missing = sorted(set(used_keys) - set(front["examples"]))
        unused = sorted(set(front["examples"]) - set(used_keys))
        raise LessonError(f"{name}: examples differs from the keys used; missing {missing}, unused {unused}.")
    return {"language": language, "course": course, "front": front, "screens": screens}


def shape_of(screens):
    """What a translation must share with the English file: screen kinds and keys per screen."""
    return [(screen["kind"], keys_of_screen(screen), clips_of_screen(screen)) for screen in screens]


def clips_of_screen(screen):
    """The sound ids a screen plays; they are not translated, so both languages must agree."""
    clips = [cell["clip"] for cell in screen.get("cells", [])]
    clip = screen.get("check", {}).get("clip")
    return clips + ([clip] if clip else [])


def keys_of_screen(screen):
    keys = set()
    text = json.dumps(screen, ensure_ascii=False)
    keys.update(match.group(1).strip() for match in TOKEN_PATTERN.finditer(text))
    for block in screen.get("blocks", []):
        if block["type"] == "ayah":
            keys.add(block["key"])
    check = screen.get("check")
    if check:
        keys.update(check[name] for name in ("ayah", "highlight") if name in check)
    return sorted(keys)


def build(lesson_directory, quran_database):
    database = sqlite3.connect(quran_database)
    files = sorted(Path(lesson_directory).glob("*/*/*.md"))
    if not files:
        raise LessonError(f"No lesson files under {lesson_directory}.")
    by_id = {}
    for path in files:
        lesson_file = read_lesson_file(path, database)
        by_id.setdefault(lesson_file["front"]["id"], {})[lesson_file["language"]] = lesson_file
    lessons = []
    references = {}
    for lesson_id, versions in sorted(by_id.items()):
        if SOURCE_LANGUAGE not in versions:
            raise LessonError(f"{lesson_id}: there is no English file; English is the source text.")
        source = versions[SOURCE_LANGUAGE]
        for language, translation in versions.items():
            if language == SOURCE_LANGUAGE:
                continue
            if translation["course"] != source["course"]:
                raise LessonError(f"{lesson_id}: the {language} file is in another course.")
            if shape_of(translation["screens"]) != shape_of(source["screens"]):
                raise LessonError(f"{lesson_id}: the {language} screens or keys differ from English.")
            if translation["front"].get("introduced_by") != source["front"].get("introduced_by"):
                raise LessonError(f"{lesson_id}: introduced_by differs between {language} and English.")
            if translation["front"]["examples"] != source["front"]["examples"]:
                raise LessonError(f"{lesson_id}: examples differ between {language} and English.")
        link = reference_link(source["front"]["reference"])
        if link:
            references[link["url"]] = link
        lessons.append(
            {
                "id": lesson_id,
                "course": source["course"],
                "order": source["front"]["order"],
                "introducedBy": source["front"].get("introduced_by", []),
                "examples": source["front"]["examples"],
                "reference": source["front"]["reference"],
                "licence": source["front"]["licence"],
                "reviewed": bool(source["front"].get("reviewed_by")),
                "languages": {
                    language: {"title": version["front"]["title"], "screens": version["screens"]}
                    for language, version in sorted(versions.items())
                },
            }
        )
    lessons.sort(key=lambda lesson: (COURSES.index(lesson["course"]), lesson["order"]))
    return {
        "formatVersion": FORMAT_VERSION,
        "lessons": lessons,
        "references": sorted(references.values(), key=lambda link: link["label"]),
    }


def main(arguments):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--lessons", required=True, type=Path)
    parser.add_argument("--quran", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    options = parser.parse_args(arguments)
    try:
        content = build(options.lessons, options.quran)
    except LessonError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    options.out.parent.mkdir(parents=True, exist_ok=True)
    options.out.write_text(json.dumps(content, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"Wrote {options.out}: {len(content['lessons'])} lessons.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
