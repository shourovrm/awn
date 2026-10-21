"""Tests for the source checks and the 7:1 correction in build_asad_pack.py.

Run: python3 -I -m unittest discover -s tools -p "test_*.py"
"""

import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_asad_pack


def full_source(text_for=lambda surah, ayah: f"text {surah}:{ayah}"):
    """A source shaped like the GitHub file, with every ayah present."""
    return [
        {
            "number": surah,
            "verses": [
                {"number": ayah, "translation": text_for(surah, ayah)}
                for ayah in range(1, count + 1)
            ],
        }
        for surah, count in enumerate(build_asad_pack.VERSES_PER_SURAH, start=1)
    ]


def source_with_upstream_typo():
    return full_source(lambda surah, ayah: "Alif. Lam. Mim." if (surah, ayah) == (7, 1) else "x")


class RowsFromSourceTest(unittest.TestCase):
    def test_every_ayah_becomes_one_row(self):
        rows = build_asad_pack.rows_from_source(source_with_upstream_typo())
        self.assertEqual(6236, len(rows))

    def test_the_dropped_letter_of_7_1_is_restored(self):
        rows = build_asad_pack.rows_from_source(source_with_upstream_typo())
        texts = {(surah, ayah): text for surah, ayah, text in rows}
        self.assertEqual("Alif. Lam. Mim. Sad.", texts[(7, 1)])

    def test_a_changed_upstream_text_stops_the_build(self):
        with self.assertRaises(ValueError):
            build_asad_pack.rows_from_source(full_source())

    def test_a_missing_ayah_stops_the_build(self):
        source = source_with_upstream_typo()
        source[1]["verses"].pop()
        with self.assertRaises(ValueError):
            build_asad_pack.rows_from_source(source)

    def test_an_empty_ayah_stops_the_build(self):
        source = source_with_upstream_typo()
        source[0]["verses"][0]["translation"] = "  "
        with self.assertRaises(ValueError):
            build_asad_pack.rows_from_source(source)


class WritePackTest(unittest.TestCase):
    def test_the_pack_has_rows_and_a_format_version(self):
        rows = build_asad_pack.rows_from_source(source_with_upstream_typo())
        with tempfile.TemporaryDirectory() as directory:
            path = os.path.join(directory, "en_asad.sqlite")
            build_asad_pack.write_pack(rows, path)
            with sqlite3.connect(path) as connection:
                count = connection.execute("SELECT count(*) FROM translation").fetchone()[0]
                version = connection.execute(
                    "SELECT value FROM pack_info WHERE key = 'format_version'"
                ).fetchone()[0]
        self.assertEqual(6236, count)
        self.assertEqual("1", version)


if __name__ == "__main__":
    unittest.main()
