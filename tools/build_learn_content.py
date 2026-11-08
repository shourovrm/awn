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
HEADING_PATTERN = re.compile(r"^## (Screen \d+|Check|Answer|Grid|Practice|Quiz): (.+)$")
CLIP_PATTERN = re.compile(r"^[a-z0-9-]+/[a-z0-9-]+$")
GRID_LINE_PREFIX = "- "
# Grid, Practice and Quiz screens sit outside the Check and Answer pairing.
FREE_KINDS = ("grid", "practice", "quiz")
URL_PATTERN = re.compile(r"https?://\S+")
TERM_PATTERN = re.compile(r"^(.+?) \((.+)\)$")
LETTERS_PREFIX = "letters:"
REVEAL_PREFIX = "reveal:"
GROUPS_PREFIX = "groups:"
PAIRS_PREFIX = "pairs:"
LETTER_ENTRY_PATTERN = re.compile(r"^\[\[([^\]]+)\]\]\s+(.+)$")
WORDS_PREFIX = "words:"
TIP_PREFIX = "tip:"
MOUTH_PREFIX = "mouth:"
# The areas a mouth block can fill in; `none` fills nothing and shows the labels only.
MOUTH_AREAS = ("throat", "tongue", "lips", "nose", "open_mouth")
WORD_LINE_PATTERN = re.compile(r"^- \{\{(\d{1,3}:\d{1,3}:\d{1,3})\}\}(?:\s+\{\{(\d{1,3}:\d{1,3}:\d{1,3})\}\})?\s*\|\s*([^|]+?)\s*(?:\|\s*([\d,\s-]+))?$")
# The kinds of quiz question, and the lines each kind has; every line is required. Options, groups
# and pairs may repeat.
QUIZ_FIELDS = {
    "pick": ("question", "options", "answer", "explain"),
    "listen": ("clip", "options", "answer", "explain"),
    "name": ("show", "options", "answer", "explain"),
    "order": ("question", "items", "explain"),
    "sort": ("question", "group", "explain"),
    "match": ("question", "pair", "explain"),
    "find": ("question", "items", "answer", "explain"),
}
REPEATED_QUIZ_FIELDS = ("options", "group", "pair")
QUESTION_HEADING_PREFIX = "### "
MAX_QUIZ_QUESTIONS = 3
# The colour a words block gives its marked letters: a tajweed rule, or a grammar ending.
WORD_MARKS = ("ghunnah", "qalqalah", "madd", "ikhfa", "idgham", "iqlab", "heavy", "silent", "ending")
# Code points that start a letter; every other Arabic mark belongs to the letter before it.
LETTER_RANGES = ((0x0621, 0x063A), (0x0641, 0x064A), (0x0671, 0x06D3), (0x06D5, 0x06D5), (0x06EE, 0x06EF), (0x06FA, 0x06FC))


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


def parse_letter_names(text, prefix, where, used_keys):
    """Reads `letters: [[ا]] alif; [[ب]] baa; ...` (or `reveal:`) as letters with their names."""
    items = []
    for entry in text[len(prefix) :].split(";"):
        entry = entry.strip()
        match = LETTER_ENTRY_PATTERN.match(entry)
        if not match:
            raise LessonError(f"{where}: a letters entry looks like '[[ب]] baa', not {entry!r}.")
        scan_text(entry, where, used_keys)
        items.append({"letter": match.group(1), "name": match.group(2).strip()})
    if not items:
        raise LessonError(f"{where}: a {prefix[:-1]} block needs at least one letter.")
    return items


def letter_count(text):
    """The letters of a word as a reader counts them: vowel marks and signs are not letters."""
    return sum(1 for char in text if any(low <= ord(char) <= high for low, high in LETTER_RANGES))


def parse_letter_positions(value, where):
    """Reads `2`, `2-3` or `1, 4` as the 1-based positions of the letters to colour."""
    positions = []
    for part in value.split(","):
        part = part.strip()
        if "-" in part:
            first, last = (int(number) for number in part.split("-"))
            positions.extend(range(first, last + 1))
        elif part:
            positions.append(int(part))
    if any(position < 1 for position in positions):
        raise LessonError(f"{where}: letter positions start at 1, not {value!r}.")
    return positions


def parse_words(paragraph, where, used_keys):
    """Reads a `words: mark` line and its `- {{key}} | caption | letters` lines as word tiles.

    A line may name two neighbouring words, for rules that join the end of one word to the next;
    its letters are then counted across both.
    """
    mark = paragraph[0][len(WORDS_PREFIX) :].strip() or None
    if mark is not None and mark not in WORD_MARKS:
        raise LessonError(f"{where}: the words mark {mark!r} is not one of {WORD_MARKS}.")
    items = []
    for line in paragraph[1:]:
        match = WORD_LINE_PATTERN.match(line.strip())
        if not match:
            raise LessonError(f"{where}: a words line looks like '- {{{{2:6:1}}}} | caption | 2-3' and starts with a word key, not {line!r}.")
        first_key, second_key, caption, letters = match.groups()
        keys = [first_key] + ([second_key] if second_key else [])
        scan_text(caption, where, used_keys)
        used_keys.extend(keys)
        positions = parse_letter_positions(letters, where) if letters else []
        items.append({"keys": keys, "caption": caption, "letters": positions})
    if not items:
        raise LessonError(f"{where}: a words block needs at least one word line.")
    return {"type": "words", "mark": mark, "items": items}


def parse_mouth(text, where):
    """Reads `mouth: throat lips` as the drawing of the mouth with those areas filled in."""
    names = text[len(MOUTH_PREFIX) :].split()
    if names == ["none"]:
        return {"type": "mouth", "areas": []}
    unknown = [name for name in names if name not in MOUTH_AREAS]
    if not names or unknown:
        raise LessonError(f"{where}: a mouth line names areas from {MOUTH_AREAS} or says none, not {unknown or 'nothing'}.")
    return {"type": "mouth", "areas": names}


def parse_fragments(value, where, used_keys):
    """Reads `[[ا]] [[م]]` as its fragments, brackets kept; any other text in the value is refused."""
    scan_text(value, where, used_keys)
    if FRAGMENT_PATTERN.sub("", value).strip():
        raise LessonError(f"{where}: {value!r} holds text beside the [[fragments]].")
    fragments = [match.group(0) for match in FRAGMENT_PATTERN.finditer(value)]
    if not fragments:
        raise LessonError(f"{where}: {value!r} holds no [[fragment]].")
    return fragments


def parse_single_fragment(value, where, used_keys):
    fragments = parse_fragments(value, where, used_keys)
    if len(fragments) != 1:
        raise LessonError(f"{where}: {value!r} must be one [[fragment]].")
    return fragments[0]


def split_bar_line(line, where):
    """Splits a `- first | second | third` line into its stripped parts."""
    if not line.startswith("- "):
        raise LessonError(f"{where}: a line of this block starts with '- ', not {line!r}.")
    return [part.strip() for part in line[2:].split("|")]


def check_caption(caption, where):
    """A caption is written in the file's language, so it holds no Arabic."""
    if ARABIC_PATTERN.search(caption):
        raise LessonError(f"{where}: the caption {caption!r} holds Arabic; write it in the file's language.")
    return caption


def parse_group_line(line, where, used_keys):
    """Reads `- [[ا]] [[م]] | caption`: letters drawn with the same stroke, and their caption."""
    parts = split_bar_line(line, where)
    if len(parts) != 2:
        raise LessonError(f"{where}: a groups line looks like '- [[ا]] [[م]] | caption', not {line!r}.")
    fragments = parse_fragments(parts[0], where, used_keys)
    if len(fragments) < 2:
        raise LessonError(f"{where}: a group needs at least two letters, not {line!r}.")
    return {"letters": [fragment.strip("[]") for fragment in fragments], "caption": check_caption(parts[1], where)}


def parse_pair_line(line, where, used_keys):
    """Reads `- [[ت]] [[ط]] | caption | clip id`: two letters to compare, a caption and an optional sound."""
    parts = split_bar_line(line, where)
    if not 2 <= len(parts) <= 3:
        raise LessonError(f"{where}: a pairs line looks like '- [[ت]] [[ط]] | caption | clip id', not {line!r}.")
    fragments = parse_fragments(parts[0], where, used_keys)
    if len(fragments) != 2:
        raise LessonError(f"{where}: a pair holds two letters, not {line!r}.")
    clip = parse_clip(parts[2], where) if len(parts) == 3 else None
    return {"letters": [fragment.strip("[]") for fragment in fragments], "caption": check_caption(parts[1], where), "clip": clip}


def parse_groups(paragraph, where, used_keys):
    items = [parse_group_line(line, where, used_keys) for line in paragraph[1:]]
    if not items:
        raise LessonError(f"{where}: a groups block needs at least one group line.")
    return {"type": "groups", "items": items}


def parse_pairs(paragraph, where, used_keys):
    items = [parse_pair_line(line, where, used_keys) for line in paragraph[1:]]
    if not items:
        raise LessonError(f"{where}: a pairs block needs at least one pair line.")
    return {"type": "pairs", "items": items}


def parse_blocks(lines, where, used_keys):
    """Turns the lines of a screen into blocks: paragraph, bullets, table, ayah, letters, words, tip or mouth."""
    blocks = []
    for paragraph in split_paragraphs(lines):
        first = paragraph[0]
        text = " ".join(paragraph)
        if first.startswith(WORDS_PREFIX):
            blocks.append(parse_words(paragraph, where, used_keys))
        elif first.startswith(MOUTH_PREFIX):
            blocks.append(parse_mouth(text, where))
        elif first.startswith(TIP_PREFIX):
            blocks.append({"type": "tip", "text": scan_text(text[len(TIP_PREFIX) :].strip(), where, used_keys)})
        elif first.startswith(LETTERS_PREFIX):
            blocks.append({"type": "letters", "items": parse_letter_names(text, LETTERS_PREFIX, where, used_keys)})
        elif first.startswith(REVEAL_PREFIX):
            blocks.append({"type": "reveal", "items": parse_letter_names(text, REVEAL_PREFIX, where, used_keys)})
        elif first.startswith(GROUPS_PREFIX):
            blocks.append(parse_groups(paragraph, where, used_keys))
        elif first.startswith(PAIRS_PREFIX):
            blocks.append(parse_pairs(paragraph, where, used_keys))
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
    """Checks a clip value: one clip id, or several joined by '+' that play one after another."""
    for clip_id in value.split("+"):
        if not CLIP_PATTERN.match(clip_id):
            raise LessonError(f"{where}: clip id must look like 'qaida-04/012', not {clip_id!r}.")
    return value


def parse_read_aloud(lines, where, used_keys):
    """Reads a read-aloud Practice item: a `read:` line with the Arabic and a `clip:` line, nothing else."""
    item = {}
    for line in lines:
        if not line.strip():
            continue
        name, _, value = line.partition(":")
        name = name.strip()
        value = value.strip()
        if name == "read":
            if not FRAGMENT_PATTERN.search(value):
                raise LessonError(f"{where}: read needs Arabic written as [[...]], not {value!r}.")
            item["read"] = scan_text(value, where, used_keys)
        elif name == "clip":
            item["clip"] = parse_clip(value, where)
        else:
            raise LessonError(
                f"{where}: a read-aloud item has only read and clip lines, not {line!r}; "
                "a question item has no read line."
            )
    if "clip" not in item:
        raise LessonError(f"{where}: a read-aloud item needs a clip line.")
    return item


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


def split_questions(lines, where):
    """Splits the lines of a Quiz screen at its `### Kind` headings into (kind, lines) pairs."""
    chunks = []
    for line in lines:
        if line.startswith(QUESTION_HEADING_PREFIX):
            name = line[len(QUESTION_HEADING_PREFIX) :].strip()
            if name.lower() not in QUIZ_FIELDS:
                raise LessonError(f"{where}: a question heading is one of {', '.join(QUIZ_FIELDS)}, not ### {name}.")
            chunks.append((name.lower(), []))
        elif chunks:
            chunks[-1][1].append(line)
        elif line.strip():
            raise LessonError(f"{where}: text before the first question heading.")
    return chunks


def read_question_fields(kind, lines, where):
    """Reads the `name: value` and `- option` lines of one question, and checks every line it needs."""
    allowed = QUIZ_FIELDS[kind]
    fields = {}
    for line in lines:
        if not line.strip():
            continue
        if line.startswith("- "):
            name, value = "options", line[2:].strip()
        else:
            name, separator, value = line.partition(":")
            if not separator:
                raise LessonError(f"{where}: expected 'name: value', got {line!r}.")
            name, value = name.strip(), value.strip()
        if name not in allowed:
            raise LessonError(f"{where}: a {kind} question has no {name} line.")
        if name in REPEATED_QUIZ_FIELDS:
            fields.setdefault(name, []).append(value)
        elif name in fields:
            raise LessonError(f"{where}: {name} appears twice in a {kind} question.")
        else:
            fields[name] = value
    missing = [name for name in allowed if name not in fields]
    if missing:
        raise LessonError(f"{where}: a {kind} question needs {', '.join(missing)}.")
    return fields


def parse_options(fields, where, used_keys):
    """Reads the bullets and the `answer` of a Pick, Listen or Name question; the answer counts from 0."""
    options = [scan_text(option, where, used_keys) for option in fields["options"]]
    if len(options) < 2:
        raise LessonError(f"{where}: a question needs at least two options.")
    answer = fields["answer"]
    if not answer.isdigit() or not 1 <= int(answer) <= len(options):
        raise LessonError(f"{where}: answer must be an option number from 1 to {len(options)}, not {answer!r}.")
    return options, int(answer) - 1


def parse_sort_group(value, where, used_keys):
    """Reads `group: Like [[ب]] | [[ب]] [[ت]]`: a label and the letters that belong to it."""
    parts = [part.strip() for part in value.split("|")]
    if len(parts) != 2:
        raise LessonError(f"{where}: a group line looks like 'group: Label | [[ب]] [[ت]]', not {value!r}.")
    return {"label": scan_text(parts[0], where, used_keys), "items": parse_fragments(parts[1], where, used_keys)}


def parse_match_pair(value, where, used_keys):
    """Reads `pair: [[ب]] | One below`: a letter and the text it goes with."""
    parts = [part.strip() for part in value.split("|")]
    if len(parts) != 2:
        raise LessonError(f"{where}: a pair line looks like 'pair: [[ب]] | One below', not {value!r}.")
    return {"left": parse_single_fragment(parts[0], where, used_keys), "right": scan_text(parts[1], where, used_keys)}


def parse_question(kind, lines, where, used_keys):
    """Turns the lines of one quiz question into its data, with the same names as the JSON."""
    fields = read_question_fields(kind, lines, where)
    explanation = scan_text(fields["explain"], where, used_keys)
    if kind == "pick":
        options, answer = parse_options(fields, where, used_keys)
        question = scan_text(fields["question"], where, used_keys)
        return {"type": kind, "question": question, "options": options, "answer": answer, "explain": explanation}
    if kind == "listen":
        options, answer = parse_options(fields, where, used_keys)
        return {"type": kind, "clip": parse_clip(fields["clip"], where), "options": options, "answer": answer, "explain": explanation}
    if kind == "name":
        options, answer = parse_options(fields, where, used_keys)
        shown = parse_single_fragment(fields["show"], where, used_keys)
        return {"type": kind, "show": shown, "options": options, "answer": answer, "explain": explanation}
    if kind == "order":
        items = parse_fragments(fields["items"], where, used_keys)
        if len(items) < 2:
            raise LessonError(f"{where}: an order question needs two or more items.")
        question = scan_text(fields["question"], where, used_keys)
        return {"type": kind, "question": question, "items": items, "explain": explanation}
    if kind == "sort":
        groups = [parse_sort_group(value, where, used_keys) for value in fields["group"]]
        if len(groups) < 2:
            raise LessonError(f"{where}: a sort question needs two or more group lines.")
        question = scan_text(fields["question"], where, used_keys)
        return {"type": kind, "question": question, "groups": groups, "explain": explanation}
    if kind == "match":
        pairs = [parse_match_pair(value, where, used_keys) for value in fields["pair"]]
        if len(pairs) < 2:
            raise LessonError(f"{where}: a match question needs two or more pair lines.")
        question = scan_text(fields["question"], where, used_keys)
        return {"type": kind, "question": question, "pairs": pairs, "explain": explanation}
    items = parse_fragments(fields["items"], where, used_keys)
    answers = parse_fragments(fields["answer"], where, used_keys)
    stray = [answer for answer in answers if answer not in items]
    if stray:
        raise LessonError(f"{where}: each answer must be one of the items; {stray} is not.")
    question = scan_text(fields["question"], where, used_keys)
    return {"type": kind, "question": question, "items": items, "answers": answers, "explain": explanation}


def parse_quiz(lines, where, used_keys):
    """Reads the one to three questions of a Quiz screen."""
    questions = [
        parse_question(kind, question_lines, where, used_keys)
        for kind, question_lines in split_questions(lines, where)
    ]
    if not 1 <= len(questions) <= MAX_QUIZ_QUESTIONS:
        raise LessonError(f"{where}: a quiz holds one to three questions, not {len(questions)}.")
    return questions


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


def is_read_aloud(lines):
    return any(line.startswith("read:") for line in lines)


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
        elif screen["kind"] == "practice" and is_read_aloud(screen["lines"]):
            entry["read_aloud"] = parse_read_aloud(screen["lines"], screen["where"], used_keys)
        elif screen["kind"] == "practice":
            entry["check"] = parse_check(
                screen["lines"], screen["where"], used_keys, is_practice=True
            )
        elif screen["kind"] == "grid":
            entry["cells"] = parse_grid(screen["lines"], screen["where"])
        elif screen["kind"] == "quiz":
            entry["questions"] = parse_quiz(screen["lines"], screen["where"], used_keys)
        else:
            entry["blocks"] = parse_blocks(screen["lines"], screen["where"], used_keys)
        result.append(entry)
    check_screen_order(result, name)
    return result


def check_screen_order(screens, name):
    """Reading screens come first; the lesson may end with Check and Answer pairs."""
    for position, screen in enumerate(screens):
        following = screens[position + 1]["kind"] if position + 1 < len(screens) else None
        if screen["kind"] == "check" and following != "answer":
            raise LessonError(f"{name}: a lesson ends with Check screens, each followed by its Answer screen, with no Grid or Practice between them.")
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


def check_marked_letters(database, screens, name):
    """Fails when a words line colours a letter its word does not have."""
    for screen in screens:
        for block in screen.get("blocks", []):
            if block["type"] != "words":
                continue
            for item in block["items"]:
                if not item["letters"]:
                    continue
                count = sum(letter_count(word_text(database, key)) for key in item["keys"])
                if max(item["letters"]) > count:
                    raise LessonError(f"{name}: {' '.join(item['keys'])} has {count} letters, so letter {max(item['letters'])} cannot be marked.")


def word_text(database, key):
    surah, ayah, position = (int(part) for part in key.split(":"))
    text = database.execute(
        "SELECT text_uthmani FROM word WHERE surah = ? AND ayah = ? AND position = ?",
        (surah, ayah, position),
    ).fetchone()[0]
    # A pause sign written after a space is not part of the word.
    return text.split(" ")[0]


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
    check_marked_letters(database, screens, name)
    if set(used_keys) != set(front["examples"]):
        missing = sorted(set(used_keys) - set(front["examples"]))
        unused = sorted(set(front["examples"]) - set(used_keys))
        raise LessonError(f"{name}: examples differs from the keys used; missing {missing}, unused {unused}.")
    return {"language": language, "course": course, "front": front, "screens": screens, "name": name}


def check_clips_exist(lesson_file, clips_folder):
    """Fails when a grid cell or practice item names a clip that is not in [clips_folder]."""
    for screen in lesson_file["screens"]:
        for clip_value in clips_of_screen(screen):
            for clip_id in clip_value.split("+"):
                if not (Path(clips_folder) / f"{clip_id}.opus").is_file():
                    raise LessonError(
                        f"{lesson_file['name']}: the clip {clip_id} is not in {clips_folder} "
                        f"(screen {screen['title']!r})."
                    )


def shape_of(screens):
    """What a translation must share with the English file: screen kinds and keys per screen."""
    return [
        (
            screen["kind"],
            keys_of_screen(screen),
            clips_of_screen(screen),
            marks_of_screen(screen),
            mouths_of_screen(screen),
            question_kinds_of_screen(screen),
        )
        for screen in screens
    ]


def marks_of_screen(screen):
    """The coloured letters of a screen's word tiles; a translation colours the same letters."""
    return [
        (block["mark"], [(item["keys"], item["letters"]) for item in block["items"]])
        for block in screen.get("blocks", [])
        if block["type"] == "words"
    ]


def mouths_of_screen(screen):
    """The areas a screen's mouth drawings fill in; a translation fills the same ones."""
    return [block["areas"] for block in screen.get("blocks", []) if block["type"] == "mouth"]


def question_kinds_of_screen(screen):
    """The kinds of a quiz screen's questions in order; a translation asks the same kinds."""
    return [question["type"] for question in screen.get("questions", [])]


def clips_of_screen(screen):
    """The sound ids a screen plays; they are not translated, so both languages must agree."""
    clips = [cell["clip"] for cell in screen.get("cells", [])]
    clips += [
        item["clip"]
        for block in screen.get("blocks", [])
        if block["type"] == "pairs"
        for item in block["items"]
        if item["clip"]
    ]
    clips += [question["clip"] for question in screen.get("questions", []) if question["type"] == "listen"]
    clip = screen.get("check", {}).get("clip") or screen.get("read_aloud", {}).get("clip")
    return clips + ([clip] if clip else [])


def keys_of_screen(screen):
    keys = set()
    text = json.dumps(screen, ensure_ascii=False)
    keys.update(match.group(1).strip() for match in TOKEN_PATTERN.finditer(text))
    for block in screen.get("blocks", []):
        if block["type"] == "ayah":
            keys.add(block["key"])
        if block["type"] == "words":
            keys.update(key for item in block["items"] for key in item["keys"])
    check = screen.get("check")
    if check:
        keys.update(check[name] for name in ("ayah", "highlight") if name in check)
    return sorted(keys)


def build(lesson_directory, quran_database, clips_folder=None):
    database = sqlite3.connect(quran_database)
    files = sorted(Path(lesson_directory).glob("*/*/*.md"))
    if not files:
        raise LessonError(f"No lesson files under {lesson_directory}.")
    by_id = {}
    for path in files:
        lesson_file = read_lesson_file(path, database)
        if clips_folder is not None:
            check_clips_exist(lesson_file, clips_folder)
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
    parser.add_argument(
        "--clips",
        type=Path,
        help="folder of clips (<prefix>/NNN.opus); every clip id a lesson names must exist in it",
    )
    options = parser.parse_args(arguments)
    try:
        content = build(options.lessons, options.quran, options.clips)
    except LessonError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    options.out.parent.mkdir(parents=True, exist_ok=True)
    options.out.write_text(json.dumps(content, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"Wrote {options.out}: {len(content['lessons'])} lessons.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
