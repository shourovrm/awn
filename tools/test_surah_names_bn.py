"""Tests for the Bangla surah name list and its use on a built quran.sqlite.

Run: cd tools && python3 -m unittest test_surah_names_bn
"""

import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import apply_surah_names_bn
import surah_names_bn


class SurahNamesFileTest(unittest.TestCase):
    def test_the_file_holds_one_spoken_name_for_each_of_the_114_surahs(self):
        names = surah_names_bn.read_surah_names()

        self.assertEqual(list(range(1, 115)), sorted(names))
        self.assertTrue(all(spoken for spoken, _ in names.values()))

    def test_well_known_surahs_have_their_spoken_names(self):
        names = surah_names_bn.read_surah_names()

        self.assertEqual("আল-ফাতিহা", names[1][0])
        self.assertEqual("আল-বাকারা", names[2][0])
        self.assertEqual("আল-ইখলাস", names[112][0])

    def test_a_surah_without_a_meaning_in_the_source_has_none(self):
        names = surah_names_bn.read_surah_names()

        self.assertIsNone(names[36][1])
        self.assertEqual("গুহা", names[18][1])


class ApplyNamesTest(unittest.TestCase):
    def test_the_chapter_table_gets_the_spoken_name_and_the_meaning(self):
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "quran.sqlite")
            connection = sqlite3.connect(path)
            connection.execute("CREATE TABLE chapter (number INTEGER PRIMARY KEY, name_bangla TEXT)")
            connection.execute("CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            connection.executemany(
                "INSERT INTO chapter VALUES (?, ?)", [(number, "x") for number in range(1, 115)])
            connection.commit()
            connection.close()

            apply_surah_names_bn.apply_names(path)

            connection = sqlite3.connect(path)
            row = connection.execute(
                "SELECT name_bangla, name_bangla_meaning FROM chapter WHERE number = 18").fetchone()
            source = connection.execute(
                "SELECT value FROM pack_info WHERE key = 'bangla_names_source'").fetchone()
            connection.close()
        self.assertEqual(("আল-কাহফ", "গুহা"), row)
        self.assertIn("Wikipedia", source[0])


if __name__ == "__main__":
    unittest.main()
