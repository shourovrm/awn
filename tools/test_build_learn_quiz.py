"""Tests for quiz screens and the groups, pairs and reveal blocks in build_learn_content.py.

Run: python3 -I -m unittest discover -s tools -p "test_*.py"
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_learn_content
from test_build_learn_content import ENGLISH_LESSON, LessonFixture

QUIZ = """
## Quiz: The letters

### Pick
question: Which letter is bā?
- [[ت]]
- [[ب]]
answer: 2
explain: bā has one dot under it.

### Listen
clip: qaida-01/005
- [[ج]]
- [[ح]]
answer: 1
explain: That was jīm.

### Name
show: [[ع]]
- ʿayn
- ghayn
answer: 1
explain: ʿayn has no dot.
"""

MORE_QUIZ = """
## Quiz: Shapes and dots

### Order
question: Tap these letters in alphabet order
items: [[ا]] [[ب]] [[ج]] [[د]]
explain: Alphabet order is alif, bā, jīm, dāl.

### Sort
question: Sort the letters by shape
group: Like [[ب]] | [[ب]] [[ت]] [[ث]]
group: Like [[ح]] | [[ح]] [[ج]] [[خ]]
explain: They share a stroke.

### Match
question: Match each letter to its dots
pair: [[ب]] | One below
pair: [[ت]] | Two above
explain: bā has one dot below, tā two above.
"""

FIND_QUIZ = """
## Quiz: Heavy letters

### Find
question: Tap the heavy letters
items: [[ت]] [[ط]] [[د]] [[ص]]
answer: [[ط]] [[ص]]
explain: ṭā and ṣād are heavy.
"""

BLOCKS = """
## Screen 2: Shapes

groups:
- [[ا]] [[م]] | Group 1
- [[ب]] [[ت]] [[ث]] | Group 2

pairs:
- [[ت]] [[ط]] | tā · ṭā | qaida-01/003+qaida-01/016
- [[س]] [[ش]] | sīn · shīn

reveal: [[ث]] thā; [[و]] wāw
"""


def lesson_with(extra):
    """The fixture lesson with [extra] screens put before its Check, in the letters course."""
    text = ENGLISH_LESSON.replace("basics-01-first", "letters-01-first")
    return text.replace("## Check: Check it", extra.strip() + "\n\n## Check: Check it")


class QuizTest(unittest.TestCase):
    def screens(self, extra):
        fixture = LessonFixture(self)
        fixture.write("en", "letters", "01-first.md", lesson_with(extra))
        return fixture.build()["lessons"][0]["languages"]["en"]["screens"]

    def assert_build_fails(self, extra, message_part):
        fixture = LessonFixture(self)
        fixture.write("en", "letters", "01-first.md", lesson_with(extra))
        with self.assertRaises(build_learn_content.LessonError) as raised:
            fixture.build()
        self.assertIn(message_part, str(raised.exception))

    def test_a_quiz_screen_sits_where_it_is_written_and_keeps_its_questions_in_order(self):
        screens = self.screens(QUIZ)

        self.assertEqual(["screen", "quiz", "check", "answer"], [s["kind"] for s in screens])
        quiz = screens[1]
        self.assertEqual("The letters", quiz["title"])
        self.assertEqual(["pick", "listen", "name"], [q["type"] for q in quiz["questions"]])

    def test_pick_listen_and_name_keep_options_a_zero_based_answer_and_the_explanation(self):
        pick, listen, name = self.screens(QUIZ)[1]["questions"]

        self.assertEqual(
            {"type": "pick", "question": "Which letter is bā?", "options": ["[[ت]]", "[[ب]]"],
             "answer": 1, "explain": "bā has one dot under it."},
            pick,
        )
        self.assertEqual("qaida-01/005", listen["clip"])
        self.assertEqual(0, listen["answer"])
        self.assertEqual("[[ع]]", name["show"])
        self.assertEqual(["ʿayn", "ghayn"], name["options"])

    def test_order_sort_and_match_keep_their_items(self):
        order, sort, match = self.screens(MORE_QUIZ)[1]["questions"]

        self.assertEqual(["[[ا]]", "[[ب]]", "[[ج]]", "[[د]]"], order["items"])
        self.assertEqual(
            [{"label": "Like [[ب]]", "items": ["[[ب]]", "[[ت]]", "[[ث]]"]},
             {"label": "Like [[ح]]", "items": ["[[ح]]", "[[ج]]", "[[خ]]"]}],
            sort["groups"],
        )
        self.assertEqual(
            [{"left": "[[ب]]", "right": "One below"}, {"left": "[[ت]]", "right": "Two above"}],
            match["pairs"],
        )
        self.assertEqual("They share a stroke.", sort["explain"])

    def test_find_keeps_the_items_and_the_answers(self):
        find = self.screens(FIND_QUIZ)[1]["questions"][0]

        self.assertEqual(["[[ت]]", "[[ط]]", "[[د]]", "[[ص]]"], find["items"])
        self.assertEqual(["[[ط]]", "[[ص]]"], find["answers"])

    def test_several_quizzes_may_follow_reading_screens(self):
        kinds = [s["kind"] for s in self.screens(QUIZ + BLOCKS + MORE_QUIZ)]

        self.assertEqual(["screen", "quiz", "screen", "quiz", "check", "answer"], kinds)

    def test_every_question_needs_an_explanation(self):
        self.assert_build_fails(FIND_QUIZ.replace("explain: ṭā and ṣād are heavy.\n", ""), "explain")

    def test_an_unknown_question_kind_is_refused(self):
        self.assert_build_fails(FIND_QUIZ.replace("### Find", "### Guess"), "Guess")

    def test_a_quiz_holds_one_to_three_questions(self):
        self.assert_build_fails("## Quiz: Empty\n\n", "question")
        four = QUIZ + "\n" + FIND_QUIZ.split("\n", 2)[2]
        self.assert_build_fails(four, "three")

    def test_a_pick_answer_outside_the_options_is_refused(self):
        self.assert_build_fails(QUIZ.replace("answer: 2", "answer: 3"), "answer")

    def test_find_answers_must_be_among_the_items(self):
        self.assert_build_fails(FIND_QUIZ.replace("answer: [[ط]] [[ص]]", "answer: [[ق]]"), "answer")

    def test_a_listen_clip_must_look_like_a_clip(self):
        self.assert_build_fails(QUIZ.replace("clip: qaida-01/005", "clip: Five"), "clip")

    def test_sort_needs_two_groups_and_match_two_pairs(self):
        one_group = MORE_QUIZ.replace("group: Like [[ح]] | [[ح]] [[ج]] [[خ]]\n", "")
        self.assert_build_fails(one_group, "group")
        one_pair = MORE_QUIZ.replace("pair: [[ت]] | Two above\n", "")
        self.assert_build_fails(one_pair, "pair")

    def test_arabic_outside_brackets_in_a_quiz_is_refused(self):
        self.assert_build_fails(QUIZ.replace("Which letter is bā?", "Which letter is ب?"), "Arabic")

    def test_a_translation_with_other_question_kinds_is_refused(self):
        fixture = LessonFixture(self)
        fixture.write("en", "letters", "01-first.md", lesson_with(QUIZ))
        other = lesson_with(QUIZ.replace("### Name", "### Pick").replace("show: [[ع]]\n", "question: Which?\n"))
        bangla = other.replace("reviewed_by: \"\"", 'reviewed_by: ""\ntranslated_from: en\nsource_version: "1"\ntranslation_reviewed_by: ""')
        fixture.write("bn", "letters", "01-first.md", bangla)
        with self.assertRaises(build_learn_content.LessonError):
            fixture.build()


class LetterBlocksTest(unittest.TestCase):
    def blocks(self):
        fixture = LessonFixture(self)
        fixture.write("en", "letters", "01-first.md", lesson_with(BLOCKS))
        return fixture.build()["lessons"][0]["languages"]["en"]["screens"][1]["blocks"]

    def test_a_groups_block_lists_letters_and_a_caption_per_group(self):
        groups = self.blocks()[0]

        self.assertEqual(
            {"type": "groups", "items": [{"letters": ["ا", "م"], "caption": "Group 1"},
                                         {"letters": ["ب", "ت", "ث"], "caption": "Group 2"}]},
            groups,
        )

    def test_a_pairs_block_keeps_letters_caption_and_an_optional_clip(self):
        pairs = self.blocks()[1]

        self.assertEqual("pairs", pairs["type"])
        self.assertEqual(
            {"letters": ["ت", "ط"], "caption": "tā · ṭā", "clip": "qaida-01/003+qaida-01/016"},
            pairs["items"][0],
        )
        self.assertIsNone(pairs["items"][1]["clip"])

    def test_a_reveal_line_lists_letters_with_their_names(self):
        reveal = self.blocks()[2]

        self.assertEqual(
            {"type": "reveal", "items": [{"letter": "ث", "name": "thā"}, {"letter": "و", "name": "wāw"}]},
            reveal,
        )

    def test_a_pairs_caption_may_not_hold_arabic(self):
        fixture = LessonFixture(self)
        fixture.write("en", "letters", "01-first.md", lesson_with(BLOCKS.replace("tā · ṭā", "ت · ط")))
        with self.assertRaises(build_learn_content.LessonError):
            fixture.build()


if __name__ == "__main__":
    unittest.main()
