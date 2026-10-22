"""Tests for the form ranking in build_learn_words.py.

Run: python3 -I -m unittest discover -s tools -p "test_*.py"
"""

import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_learn_words

# Surah 1 has two ayahs and surah 2 has one; the other surahs are empty in the fixture databases,
# so the fixture checks the file layout, not the Quran.
FIXTURE_WORDS = [
    # surah, ayah, position, text
    (1, 1, 1, "بِسْمِ"),
    (1, 1, 2, "ٱللَّهِ"),
    (1, 2, 1, "ٱللَّهَ"),
    (1, 2, 2, "ٱللَّهُ"),
    (2, 1, 1, "ٱللَّهُ"),
    (2, 1, 2, "بِسْمِ"),
]


def write_database(directory, words):
    path = os.path.join(directory, "quran.sqlite")
    quran = sqlite3.connect(path)
    quran.execute(
        "CREATE TABLE word (surah INTEGER, ayah INTEGER, position INTEGER, text_uthmani TEXT)"
    )
    for row in words:
        quran.execute("INSERT INTO word VALUES (?, ?, ?, ?)", row)
    quran.commit()
    quran.close()
    return path


class NormaliseTest(unittest.TestCase):
    def test_vowel_marks_are_removed(self):
        self.assertEqual("بسم", build_learn_words.normalise("بِسْمِ"))

    def test_alef_wasla_becomes_alef(self):
        self.assertEqual("الله", build_learn_words.normalise("ٱللَّهِ"))

    def test_dagger_alef_and_tatweel_are_removed(self):
        self.assertEqual("الرحمن", build_learn_words.normalise("ٱلرَّحْمَـٰنِ"))

    def test_attached_particle_stays_part_of_the_form(self):
        self.assertEqual("ولا", build_learn_words.normalise("وَلَا"))

    def test_small_high_waw_is_removed(self):
        self.assertEqual(build_learn_words.normalise("هُ"), build_learn_words.normalise("هُۥ"))


class FileTest(unittest.TestCase):
    def build_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            return build_learn_words.build(write_database(directory, FIXTURE_WORDS))

    def test_forms_are_ranked_by_count_then_first_appearance(self):
        _, _, form_ranks, _ = build_learn_words.decode(self.build_fixture())
        # الله appears four times and بسم twice, so الله is rank 1.
        self.assertEqual([2, 1, 1, 1, 1, 2], form_ranks)

    def test_ayah_and_surah_counts_describe_the_reading_order(self):
        ayah_counts, words_per_ayah, _, _ = build_learn_words.decode(self.build_fixture())
        self.assertEqual([2, 1], ayah_counts[:2])
        self.assertEqual(0, sum(ayah_counts[2:]))
        self.assertEqual([2, 2, 2], words_per_ayah)

    def test_representative_is_the_first_word_of_the_commonest_spelling(self):
        _, _, _, representatives = build_learn_words.decode(self.build_fixture())
        # الله: the spelling ٱللَّهُ (two words) beats ٱللَّهِ and ٱللَّهَ (one word each); its first
        # word is index 3.
        self.assertEqual(3, representatives[0])
        # بسم has one spelling, so its first word wins.
        self.assertEqual(0, representatives[1])

    def test_an_unknown_file_is_refused(self):
        with self.assertRaises(ValueError):
            build_learn_words.decode(b"nope" + bytes(20))


if __name__ == "__main__":
    unittest.main()
