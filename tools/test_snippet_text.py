"""Tests for snippet_text.py.

Run: python3 -I -m unittest discover -s tools -p "test_snippet_text.py"
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import snippet_text


class QuranSnippetTextTest(unittest.TestCase):
    def test_a_stop_sign_joins_the_word_before_it(self):
        # 2:2 has the embracing stop (three dots) after رَيْبَ and after فِيهِ.
        text = snippet_text.quran_snippet_text("لَا رَيْبَ ۛ فِيهِ ۛ هُدًى", 2)
        self.assertTrue(text.startswith("لَا رَيْبَۛ فِيهِۛ هُدًى"))

    def test_every_stop_sign_is_joined(self):
        for sign in "ۖۗۘۙۚۛۜ":
            with self.subTest(sign=hex(ord(sign))):
                text = snippet_text.quran_snippet_text(f"قَالَ {sign} نَعَمْ", 1)
                self.assertIn(f"قَالَ{sign} نَعَمْ", text)

    def test_the_ayah_ends_with_its_number_in_arabic_indic_digits(self):
        # The Hafs font draws these digits inside the ayah-end ornament.
        self.assertTrue(snippet_text.quran_snippet_text("تَرْتِيلًا", 4).endswith("تَرْتِيلًا ٤"))
        self.assertTrue(snippet_text.quran_snippet_text("عَلِيمٌ", 114).endswith(" ١١٤"))

    def test_text_without_stop_signs_keeps_its_spaces(self):
        self.assertEqual("بِسْمِ ٱللَّهِ ١", snippet_text.quran_snippet_text("بِسْمِ ٱللَّهِ", 1))


if __name__ == "__main__":
    unittest.main()
