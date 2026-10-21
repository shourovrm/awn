"""Tests for the Al-Fatihah renumbering in build_quantum_pack.py.

Run: python3 -I -m unittest discover -s tools -p "test_*.py"
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_quantum_pack

PUBLISHER_FATIHA = [
    (1, 1, 1, 1, "praise"),
    (1, 2, 2, 2, "merciful"),
    (1, 3, 3, 3, "master"),
    (1, 4, 4, 4, "worship"),
    (1, 5, 5, 6, build_quantum_pack.FATIHA_FIVE_SIX),
    (1, 6, 5, 6, build_quantum_pack.FATIHA_FIVE_SIX),
    (1, 7, 7, 7, "protect"),
]


class FatihaNumberingTest(unittest.TestCase):
    def hafs(self):
        rows = build_quantum_pack.with_fatiha_in_hafs_numbering(PUBLISHER_FATIHA + [(2, 1, 1, 1, "alif")])
        return {(surah, ayah): (start, end, text) for surah, ayah, start, end, text in rows}

    def test_the_basmalah_comes_first(self):
        self.assertEqual((1, 1, build_quantum_pack.FATIHA_BASMALAH), self.hafs()[(1, 1)])

    def test_each_meaning_moves_one_ayah_later(self):
        self.assertEqual("praise", self.hafs()[(1, 2)][2])
        self.assertEqual("worship", self.hafs()[(1, 5)][2])

    def test_the_last_two_ayahs_each_have_their_own_row(self):
        self.assertEqual((6, 6, "(প্রভু হে!) সাফল্যের সরলপথে,"), self.hafs()[(1, 6)])
        self.assertEqual((7, 7, "তোমার প্রিয়জনদের পথে আমাদের পরিচালিত করো, protect"), self.hafs()[(1, 7)])

    def test_a_changed_five_six_sentence_stops_the_build(self):
        with self.assertRaises(ValueError):
            build_quantum_pack.split_fatiha_five_six("something else")

    def test_the_basmalah_is_the_site_template_wording(self):
        self.assertEqual(
            "দ\u09dfা" + "ম\u09df মেহেরবান আল্লাহর নামে।", build_quantum_pack.FATIHA_BASMALAH
        )
        self.assertIn("\u09df", build_quantum_pack.FATIHA_BASMALAH)
        self.assertNotIn("\u09bc", build_quantum_pack.FATIHA_BASMALAH)

    def test_the_vowel_slip_in_surah_106_is_repaired(self):
        self.assertEqual("কোরাইশ", build_quantum_pack.repair_known_typos("কা\u09c7রাইশ"))
        self.assertEqual("কোরআন", build_quantum_pack.repair_known_typos("কোরআন"))

    def test_other_surahs_are_unchanged(self):
        self.assertEqual((1, 1, "alif"), self.hafs()[(2, 1)])

    def test_an_already_moved_fatiha_is_recognised(self):
        self.assertTrue(build_quantum_pack.is_publisher_fatiha(PUBLISHER_FATIHA))
        moved = build_quantum_pack.with_fatiha_in_hafs_numbering(PUBLISHER_FATIHA)
        self.assertFalse(build_quantum_pack.is_publisher_fatiha(moved))


if __name__ == "__main__":
    unittest.main()
