"""Tests for build_transliteration_pack.py.

Run: python3 -I -m unittest discover -s tools -p "test_*.py"
"""

import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_transliteration_pack


def word(position, transliteration, char_type="word"):
    return {"position": position, "char_type_name": char_type,
            "transliteration": None if transliteration is None else {"text": transliteration}}


FATIHA_START = {
    "verse_key": "1:1",
    "words": [word(1, "bis'mi"), word(2, "l-lahi"), word(3, None, char_type="end")],
}


class TransliterationRowsTest(unittest.TestCase):
    def test_each_spoken_word_becomes_one_row_in_reading_order(self):
        rows = build_transliteration_pack.transliteration_rows([FATIHA_START])
        self.assertEqual([(1, 1, 1, "bis'mi"), (1, 1, 2, "l-lahi")], rows)

    def test_the_ayah_end_sign_has_no_row(self):
        rows = build_transliteration_pack.transliteration_rows([FATIHA_START])
        self.assertEqual(2, len(rows))

    def test_a_spoken_word_without_text_stops_the_build(self):
        verse = {"verse_key": "2:1", "words": [word(1, None)]}
        with self.assertRaises(ValueError):
            build_transliteration_pack.transliteration_rows([verse])

    def test_a_blank_text_stops_the_build(self):
        verse = {"verse_key": "2:1", "words": [word(1, "  ")]}
        with self.assertRaises(ValueError):
            build_transliteration_pack.transliteration_rows([verse])


class WritePackTest(unittest.TestCase):
    def test_the_file_has_the_word_table_and_the_format_version(self):
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "pack.sqlite")
            build_transliteration_pack.write_pack(path, [(1, 1, 1, "bis'mi")])
            with sqlite3.connect(path) as connection:
                text = connection.execute(
                    "SELECT text FROM word_translation WHERE surah = 1 AND ayah = 1 AND position = 1"
                ).fetchone()[0]
                info = dict(connection.execute("SELECT key, value FROM pack_info").fetchall())
            self.assertEqual("bis'mi", text)
            self.assertEqual("1", info["format_version"])


if __name__ == "__main__":
    unittest.main()
