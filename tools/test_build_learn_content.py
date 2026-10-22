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
import data_paths

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


GRID_BLOCK = """
## Grid: Fatha

- بَ | ba | qaida-04/012
- تَ | ta | qaida-04/013
"""

PRACTICE_BLOCK = """
## Practice: Heard sound

question: Which one did you hear?
clip: qaida-01/014
- [[سَ]]
- [[صَ]]
answer: 2
explain: The heavy s is said with the back of the tongue raised.
"""


class GridAndPracticeTest(unittest.TestCase):
    def build_screens(self, extra, course="tajweed", name="11-first.md", lesson=None):
        fixture = LessonFixture(self)
        text = (lesson or ENGLISH_LESSON).replace("basics-01-first", f"{course}-{name[:-3]}")
        fixture.write("en", course, name, text + extra)
        return fixture.build()["lessons"][0]["languages"]["en"]["screens"]

    def assert_build_fails(self, extra, message_part):
        fixture = LessonFixture(self)
        text = ENGLISH_LESSON.replace("basics-01-first", "tajweed-11-first") + extra
        fixture.write("en", "tajweed", "11-first.md", text)
        with self.assertRaises(build_learn_content.LessonError) as raised:
            fixture.build()
        self.assertIn(message_part, str(raised.exception))

    def test_a_grid_lists_its_cells_with_arabic_transliteration_and_clip(self):
        screens = self.build_screens(GRID_BLOCK)

        grid = screens[-1]
        self.assertEqual("grid", grid["kind"])
        self.assertEqual("Fatha", grid["title"])
        self.assertEqual(
            [
                {"arabic": "بَ", "transliteration": "ba", "clip": "qaida-04/012"},
                {"arabic": "تَ", "transliteration": "ta", "clip": "qaida-04/013"},
            ],
            grid["cells"],
        )

    def test_a_practice_item_keeps_its_clip_options_answer_and_explanation(self):
        screens = self.build_screens(PRACTICE_BLOCK)

        practice = screens[-1]
        self.assertEqual("practice", practice["kind"])
        check = practice["check"]
        self.assertEqual("qaida-01/014", check["clip"])
        self.assertEqual(["[[سَ]]", "[[صَ]]"], check["options"])
        self.assertEqual(1, check["answer"])
        self.assertEqual("The heavy s is said with the back of the tongue raised.", check["explain"])

    def test_grids_and_practice_items_may_sit_between_check_and_answer_pairs(self):
        extra = GRID_BLOCK + PRACTICE_BLOCK + "\n## Check: Again\n\nquestion: Which?\n- one\n- two\nanswer: 1\n\n## Answer: One\n\nIt is one.\n" + PRACTICE_BLOCK

        kinds = [screen["kind"] for screen in self.build_screens(extra)]

        self.assertEqual(
            ["screen", "check", "answer", "grid", "practice", "check", "answer", "practice"], kinds
        )

    def test_a_grid_between_a_check_and_its_answer_is_refused(self):
        text = ENGLISH_LESSON.replace("## Answer: Answered", GRID_BLOCK.strip() + "\n\n## Answer: Answered")
        fixture = LessonFixture(self)
        fixture.write("en", "tajweed", "11-first.md", text.replace("basics-01-first", "tajweed-11-first"))
        with self.assertRaises(build_learn_content.LessonError) as raised:
            fixture.build()
        self.assertIn("each followed by its Answer", str(raised.exception))

    def test_the_tajweed_folder_is_a_course(self):
        fixture = LessonFixture(self)
        text = ENGLISH_LESSON.replace("basics-01-first", "tajweed-11-first")
        fixture.write("en", "tajweed", "11-first.md", text)

        self.assertEqual("tajweed", fixture.build()["lessons"][0]["course"])

    def test_a_grid_line_needs_three_parts(self):
        self.assert_build_fails("\n## Grid: Bad\n\n- بَ | ba\n", "a grid line looks like")

    def test_a_grid_cell_needs_arabic_in_the_first_part(self):
        self.assert_build_fails("\n## Grid: Bad\n\n- ba | ba | qaida-04/012\n", "holds no Arabic")

    def test_a_transliteration_may_not_hold_arabic(self):
        self.assert_build_fails("\n## Grid: Bad\n\n- بَ | بَ | qaida-04/012\n", "transliteration")

    def test_a_clip_id_must_look_like_a_clip(self):
        self.assert_build_fails("\n## Grid: Bad\n\n- بَ | ba | 12\n", "clip id")

    def test_several_clip_ids_joined_by_plus_are_kept_and_each_is_checked(self):
        line = "\n## Grid: Joined\n\n- بلب | bā lām bā | qaida-01/002+qaida-01/023+qaida-01/002\n"
        cell = self.build_screens(line)[-1]["cells"][0]
        self.assertEqual("qaida-01/002+qaida-01/023+qaida-01/002", cell["clip"])
        self.assert_build_fails(line.replace("qaida-01/023", "23"), "clip id")

    def test_a_practice_clip_may_join_several_ids(self):
        practice = PRACTICE_BLOCK.replace("qaida-01/014", "qaida-01/014+qaida-08a/003")
        check = self.build_screens(practice)[-1]["check"]
        self.assertEqual("qaida-01/014+qaida-08a/003", check["clip"])

    def test_a_read_aloud_item_keeps_its_arabic_and_clip(self):
        extra = "\n## Practice: Read a row\n\nread: [[حَ]] [[خَ]] [[دَ]]\nclip: qaida-04/012+qaida-04/013\n"

        item = self.build_screens(extra)[-1]

        self.assertEqual("practice", item["kind"])
        self.assertEqual("[[حَ]] [[خَ]] [[دَ]]", item["read_aloud"]["read"])
        self.assertEqual("qaida-04/012+qaida-04/013", item["read_aloud"]["clip"])
        self.assertNotIn("check", item)

    def test_a_read_aloud_item_mixed_with_question_lines_is_refused(self):
        extra = "\n## Practice: Mix\n\nread: [[حَ]]\nclip: qaida-04/012\nquestion: Which?\n"
        self.assert_build_fails(extra, "only read and clip lines")

    def test_a_read_aloud_item_needs_a_clip_and_marked_arabic(self):
        self.assert_build_fails("\n## Practice: R\n\nread: [[حَ]]\n", "needs a clip")
        self.assert_build_fails("\n## Practice: R\n\nread: ba\nclip: qaida-04/012\n", "read needs Arabic")

    def build_with_clips(self, extra, clip_names):
        fixture = LessonFixture(self)
        text = ENGLISH_LESSON.replace("basics-01-first", "tajweed-11-first") + extra
        fixture.write("en", "tajweed", "11-first.md", text)
        clips = fixture.root / "clips"
        for name in clip_names:
            (clips / name).parent.mkdir(parents=True, exist_ok=True)
            (clips / name).write_bytes(b"opus")
        return build_learn_content.build(fixture.root / "lessons", fixture.quran, clips)

    def test_every_named_clip_must_exist_when_a_clip_folder_is_given(self):
        lessons = self.build_with_clips(GRID_BLOCK, ["qaida-04/012.opus", "qaida-04/013.opus"])
        self.assertEqual(1, len(lessons["lessons"]))

        with self.assertRaises(build_learn_content.LessonError) as raised:
            self.build_with_clips(GRID_BLOCK, ["qaida-04/012.opus"])
        self.assertIn("qaida-04/013", str(raised.exception))
        self.assertIn("11-first.md", str(raised.exception))

    def test_each_part_of_a_joined_clip_must_exist(self):
        joined = "\n## Grid: J\n\n- بلب | bā lām bā | qaida-01/002+qaida-01/023\n"
        with self.assertRaises(build_learn_content.LessonError) as raised:
            self.build_with_clips(joined, ["qaida-01/002.opus"])
        self.assertIn("qaida-01/023", str(raised.exception))

    def test_practice_and_read_aloud_clips_are_checked_too(self):
        with self.assertRaises(build_learn_content.LessonError):
            self.build_with_clips(PRACTICE_BLOCK, [])
        read = "\n## Practice: R\n\nread: [[حَ]]\nclip: qaida-04/012\n"
        with self.assertRaises(build_learn_content.LessonError):
            self.build_with_clips(read, [])

    def test_a_grid_without_cells_is_refused(self):
        self.assert_build_fails("\n## Grid: Empty\n\ntext only\n", "a grid line looks like")

    def test_a_practice_item_needs_an_explanation(self):
        self.assert_build_fails(PRACTICE_BLOCK.replace("explain:", "note:"), "unknown")

    def test_a_practice_item_without_explain_is_refused(self):
        lines = [line for line in PRACTICE_BLOCK.split("\n") if not line.startswith("explain:")]
        self.assert_build_fails("\n".join(lines), "needs an explain")

    def test_a_practice_answer_outside_the_options_is_refused(self):
        self.assert_build_fails(PRACTICE_BLOCK.replace("answer: 2", "answer: 5"), "answer must be")

    def test_a_check_may_not_carry_a_clip_or_an_explanation(self):
        text = ENGLISH_LESSON.replace("answer: 2", "answer: 2\nexplain: No.")
        fixture = LessonFixture(self)
        fixture.write("en", "basics", "01-first.md", text)
        with self.assertRaises(build_learn_content.LessonError) as raised:
            fixture.build()
        self.assertIn("unknown check line", str(raised.exception))

    def test_an_error_names_the_file_and_the_line(self):
        fixture = LessonFixture(self)
        text = ENGLISH_LESSON.replace("basics-01-first", "tajweed-11-first") + "\n## Grid: Bad\n\n- بَ | ba\n"
        path = fixture.write("en", "tajweed", "11-first.md", text)
        with self.assertRaises(build_learn_content.LessonError) as raised:
            fixture.build()
        self.assertIn(f"{path}:", str(raised.exception))

    def test_a_translation_must_keep_the_clips_of_the_english_grid(self):
        fixture = LessonFixture(self)
        english = ENGLISH_LESSON.replace("basics-01-first", "tajweed-11-first") + GRID_BLOCK
        fixture.write("en", "tajweed", "11-first.md", english)
        front = "order: 1\ntranslated_from: en\nsource_version: 2026-10-21\ntranslation_reviewed_by: \"\"\n"
        other = english.replace("order: 1\n", front).replace("qaida-04/013", "qaida-04/099")
        fixture.write("bn", "tajweed", "11-first.md", other)
        with self.assertRaises(build_learn_content.LessonError) as raised:
            fixture.build()
        self.assertIn("differ from English", str(raised.exception))


class RepositoryContentTest(unittest.TestCase):
    """The committed lessons obey the format, checked with a database that holds their keys."""

    def test_every_committed_lesson_parses_against_the_real_quran_text(self):
        repository = Path(__file__).resolve().parent.parent
        quran = data_paths.published() / "quran" / "quran.sqlite"
        if not quran.is_file():
            self.skipTest(f"{quran} is not present")

        content = build_learn_content.build(repository / "content" / "lessons", quran)

        self.assertGreaterEqual(len(content["lessons"]), 6)


if __name__ == "__main__":
    unittest.main()
