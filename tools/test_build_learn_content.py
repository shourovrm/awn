"""Tests for the lesson pipeline in build_learn_content.py.

Run: python3 -I -m unittest discover -s tools -p "test_*.py"
"""

import os
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_learn_content

BASMALA_WORD = "بِسْمِ"

ENGLISH_LESSON = """---
id: basics-01-first
title: First
order: 1
appears_after: start
reference: "Follows Wikibooks, Arabic, https://en.wikibooks.org/wiki/Arabic"
licence: CC BY-SA 4.0
examples: [1:1:1, 1:1]
reviewed_by: ""
---

## Screen 1: The word {{1:1:1}}

{{1:1:1}} starts with the letter [[ب]].

- first {{1:1:1}}
- second

| A | B |
|---|---|
| x | {{1:1:1}} |

{{1:1}}

## Check: Check it

question: Which word?
ayah: 1:1
highlight: 1:1:1
- one
- two
answer: 2

## Answer: Answered

It is two.
"""


def quran_database(directory):
    path = os.path.join(directory, "quran.sqlite")
    database = sqlite3.connect(path)
    database.execute(
        "CREATE TABLE word (surah INTEGER, ayah INTEGER, position INTEGER, text_uthmani TEXT)"
    )
    database.execute("CREATE TABLE ayah (surah INTEGER, ayah INTEGER, text_uthmani TEXT)")
    database.execute("INSERT INTO word VALUES (1, 1, 1, ?)", (BASMALA_WORD,))
    database.execute("INSERT INTO ayah VALUES (1, 1, ?)", (BASMALA_WORD,))
    database.commit()
    database.close()
    return path


class LessonFixture:
    """A temporary lesson folder and Quran database."""

    def __init__(self, test_case):
        self.directory = tempfile.TemporaryDirectory()
        test_case.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.quran = quran_database(self.directory.name)

    def write(self, language, course, name, text):
        path = self.root / "lessons" / language / course / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def build(self):
        return build_learn_content.build(self.root / "lessons", self.quran)


class ParsingTest(unittest.TestCase):
    def test_a_lesson_becomes_screens_with_typed_blocks(self):
        fixture = LessonFixture(self)
        fixture.write("en", "basics", "01-first.md", ENGLISH_LESSON)

        lesson = fixture.build()["lessons"][0]

        screens = lesson["languages"]["en"]["screens"]
        self.assertEqual(["screen", "check", "answer"], [screen["kind"] for screen in screens])
        blocks = screens[0]["blocks"]
        self.assertEqual(
            ["paragraph", "bullets", "table", "ayah"], [block["type"] for block in blocks]
        )
        self.assertEqual("1:1", blocks[3]["key"])
        self.assertEqual(["A", "B"], blocks[2]["header"])

    def test_the_check_keeps_its_options_and_a_zero_based_answer(self):
        fixture = LessonFixture(self)
        fixture.write("en", "basics", "01-first.md", ENGLISH_LESSON)

        check = fixture.build()["lessons"][0]["languages"]["en"]["screens"][1]["check"]

        self.assertEqual(["one", "two"], check["options"])
        self.assertEqual(1, check["answer"])
        self.assertEqual("1:1:1", check["highlight"])

    def test_a_lesson_may_end_with_several_checks(self):
        fixture = LessonFixture(self)
        second = "\n## Check: Again\n\nquestion: Which letter?\n- [[ا]]\n- [[ب]]\nanswer: 2\n\n## Answer: Baa\n\nIt is baa.\n"
        fixture.write("en", "basics", "01-first.md", ENGLISH_LESSON + second)

        screens = fixture.build()["lessons"][0]["languages"]["en"]["screens"]

        self.assertEqual(
            ["screen", "check", "answer", "check", "answer"], [screen["kind"] for screen in screens]
        )
        self.assertEqual(1, screens[3]["check"]["answer"])

    def test_a_letters_line_becomes_a_grid_of_letters_and_names(self):
        fixture = LessonFixture(self)
        text = ENGLISH_LESSON.replace(
            "- first {{1:1:1}}", "letters: [[ا]] alif; [[ب]] baa\n\n- first {{1:1:1}}"
        )
        fixture.write("en", "basics", "01-first.md", text)

        blocks = fixture.build()["lessons"][0]["languages"]["en"]["screens"][0]["blocks"]

        letters = next(block for block in blocks if block["type"] == "letters")
        self.assertEqual(
            [{"letter": "ا", "name": "alif"}, {"letter": "ب", "name": "baa"}], letters["items"]
        )

    def test_a_sum_of_parts_becomes_an_equation_block(self):
        fixture = LessonFixture(self)
        text = ENGLISH_LESSON.replace(
            "- first {{1:1:1}}",
            "[[ب]] (in) + [[سم]] (name) = {{1:1:1}} (in the name)\n\n- first {{1:1:1}}",
        )
        fixture.write("en", "basics", "01-first.md", text)

        blocks = fixture.build()["lessons"][0]["languages"]["en"]["screens"][0]["blocks"]

        equation = next(block for block in blocks if block["type"] == "equation")
        self.assertEqual(["in", "name"], [part["gloss"] for part in equation["parts"]])
        self.assertEqual("{{1:1:1}}", equation["result"]["text"])

    def test_a_sentence_with_a_plus_sign_stays_a_paragraph(self):
        fixture = LessonFixture(self)
        text = ENGLISH_LESSON.replace(
            "- first {{1:1:1}}", "One + one = two, said once.\n\n- first {{1:1:1}}"
        )
        fixture.write("en", "basics", "01-first.md", text)

        blocks = fixture.build()["lessons"][0]["languages"]["en"]["screens"][0]["blocks"]

        self.assertEqual("paragraph", blocks[1]["type"])

    def test_references_with_a_link_are_listed_once(self):
        fixture = LessonFixture(self)
        fixture.write("en", "basics", "01-first.md", ENGLISH_LESSON)

        references = fixture.build()["references"]

        self.assertEqual(
            [{"label": "Wikibooks, Arabic", "url": "https://en.wikibooks.org/wiki/Arabic"}],
            references,
        )


class ValidationTest(unittest.TestCase):
    def assert_build_fails(self, text, message_part, language="en", course="basics"):
        fixture = LessonFixture(self)
        fixture.write(language, course, "01-first.md", text)
        with self.assertRaises(build_learn_content.LessonError) as raised:
            fixture.build()
        self.assertIn(message_part, str(raised.exception))

    def test_an_unknown_word_key_is_refused(self):
        self.assert_build_fails(ENGLISH_LESSON.replace("{{1:1:1}} starts", "{{1:1:9}} starts"), "1:1:9")

    def test_arabic_outside_a_token_is_refused(self):
        self.assert_build_fails(ENGLISH_LESSON.replace("[[ب]]", "ب"), "Arabic outside")

    def test_examples_must_match_the_keys_in_the_body(self):
        self.assert_build_fails(
            ENGLISH_LESSON.replace("examples: [1:1:1, 1:1]", "examples: [1:1:1]"), "examples differs"
        )

    def test_the_id_must_name_the_course_and_the_file(self):
        self.assert_build_fails(ENGLISH_LESSON.replace("id: basics-01-first", "id: first"), "must be")

    def test_front_matter_is_required(self):
        self.assert_build_fails("## Screen 1: A\n\ntext\n", "must start with a ---")

    def test_a_missing_front_matter_key_is_named(self):
        self.assert_build_fails(ENGLISH_LESSON.replace("licence: CC BY-SA 4.0\n", ""), "lacks licence")

    def test_a_check_answer_outside_the_options_is_refused(self):
        self.assert_build_fails(ENGLISH_LESSON.replace("answer: 2", "answer: 3"), "answer must be")

    def test_a_check_needs_its_answer_screen(self):
        text = ENGLISH_LESSON.split("## Answer")[0]
        self.assert_build_fails(text, "each followed by its Answer screen")

    def test_a_reading_screen_after_the_checks_is_refused(self):
        self.assert_build_fails(ENGLISH_LESSON + "\n## Screen 2: Late\n\nText.\n", "each followed by its Answer")

    def test_a_letters_entry_needs_a_letter_and_a_name(self):
        text = ENGLISH_LESSON.replace("- first {{1:1:1}}", "letters: [[ا]]\n\n- first {{1:1:1}}")
        self.assert_build_fails(text, "a letters entry looks like")

    def test_a_heading_must_use_the_known_forms(self):
        self.assert_build_fails(ENGLISH_LESSON.replace("## Screen 1:", "## Example:"), "heading must be")

    def test_introduced_by_is_for_grammar_lessons_only(self):
        self.assert_build_fails(
            ENGLISH_LESSON.replace("order: 1", "order: 1\nintroduced_by: [1:1:1]"),
            "grammar lessons only",
        )


class TranslationTest(unittest.TestCase):
    TRANSLATION_FRONT = "translated_from: en\nsource_version: 2026-10-21\ntranslation_reviewed_by: \"\"\n"

    def translation(self, text):
        return text.replace("order: 1\n", "order: 1\n" + self.TRANSLATION_FRONT)

    def test_a_translation_joins_the_lesson_of_the_same_id(self):
        fixture = LessonFixture(self)
        fixture.write("en", "basics", "01-first.md", ENGLISH_LESSON)
        fixture.write("bn", "basics", "01-first.md", self.translation(ENGLISH_LESSON))

        lesson = fixture.build()["lessons"][0]

        self.assertEqual(["bn", "en"], sorted(lesson["languages"]))

    def test_a_translation_with_other_screens_is_refused(self):
        fixture = LessonFixture(self)
        fixture.write("en", "basics", "01-first.md", ENGLISH_LESSON)
        shorter = self.translation(ENGLISH_LESSON).replace("{{1:1}}\n\n", "", 1)
        fixture.write("bn", "basics", "01-first.md", shorter)
        with self.assertRaises(build_learn_content.LessonError) as raised:
            fixture.build()
        self.assertIn("differ from English", str(raised.exception))

    def test_a_translation_needs_its_source_fields(self):
        fixture = LessonFixture(self)
        fixture.write("en", "basics", "01-first.md", ENGLISH_LESSON)
        fixture.write("bn", "basics", "01-first.md", ENGLISH_LESSON)
        with self.assertRaises(build_learn_content.LessonError) as raised:
            fixture.build()
        self.assertIn("translated_from", str(raised.exception))

    def test_a_lesson_without_an_english_file_is_refused(self):
        fixture = LessonFixture(self)
        fixture.write("bn", "basics", "01-first.md", self.translation(ENGLISH_LESSON))
        with self.assertRaises(build_learn_content.LessonError) as raised:
            fixture.build()
        self.assertIn("no English file", str(raised.exception))


class RepositoryContentTest(unittest.TestCase):
    """The committed lessons obey the format, checked with a database that holds their keys."""

    def test_every_committed_lesson_parses_against_the_real_quran_text(self):
        repository = Path(__file__).resolve().parent.parent
        data_directory = Path(os.environ.get("AWN_DATA_DIR", repository / "awn-data"))
        quran = data_directory / "quran" / "quran.sqlite"
        if not quran.is_file():
            self.skipTest(f"{quran} is not present")

        content = build_learn_content.build(repository / "content" / "lessons", quran)

        self.assertGreaterEqual(len(content["lessons"]), 6)


if __name__ == "__main__":
    unittest.main()
