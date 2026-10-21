"""Tests for the source checks and the corrections in build_asad_pack.py.

Run: python3 -I -m unittest discover -s tools -p "test_*.py"
"""

import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_asad_pack


def api_document(text_for=lambda surah, ayah: f"text {surah}:{ayah}"):
    """A response shaped like GET /v1/quran/en.asad, with every ayah present."""
    surahs = [
        {
            "number": surah,
            "ayahs": [
                {"numberInSurah": ayah, "text": text_for(surah, ayah)}
                for ayah in range(1, count + 1)
            ],
        }
        for surah, count in enumerate(build_asad_pack.VERSES_PER_SURAH, start=1)
    ]
    return {"data": {"surahs": surahs}}


def source_with_known_errors(surah, ayah):
    """Each corrected ayah holds its wrong texts, as the real source does."""
    wrongs = [wrong for wrong, _ in build_asad_pack.CORRECTIONS.get((surah, ayah), [])]
    return "start " + " | ".join(wrongs) + " end" if wrongs else f"text {surah}:{ayah}"


class TextsFromSourceTest(unittest.TestCase):
    def test_every_ayah_is_read_in_order(self):
        rows = build_asad_pack.texts_from_source(api_document())
        self.assertEqual(6236, len(rows))
        self.assertEqual((114, 6), rows[-1][:2])

    def test_a_missing_ayah_stops_the_build(self):
        document = api_document()
        document["data"]["surahs"][1]["ayahs"].pop()
        with self.assertRaises(ValueError):
            build_asad_pack.texts_from_source(document)


class CleanRowsTest(unittest.TestCase):
    def cleaned_texts(self):
        rows = build_asad_pack.texts_from_source(api_document(source_with_known_errors))
        return {(surah, ayah): text for surah, ayah, text in build_asad_pack.clean_rows(rows)}

    def test_typos_are_corrected(self):
        texts = self.cleaned_texts()
        self.assertIn("bestowed", texts[(22, 71)])
        self.assertIn("message-bearers", texts[(26, 141)])

    def test_the_footnote_pasted_into_18_12_is_removed(self):
        self.assertNotIn("Christianity", self.cleaned_texts()[(18, 12)])

    def test_a_changed_upstream_text_stops_the_build(self):
        rows = build_asad_pack.texts_from_source(api_document())
        with self.assertRaises(ValueError):
            build_asad_pack.clean_rows(rows)

    def test_soft_hyphens_are_removed(self):
        rows = [(1, 1, "apos­tasy")]
        self.assertEqual("apostasy", build_asad_pack.clean_rows(rows)[0][2])


class WritePackTest(unittest.TestCase):
    def test_the_pack_has_rows_and_a_format_version(self):
        rows = build_asad_pack.clean_rows(
            build_asad_pack.texts_from_source(api_document(source_with_known_errors))
        )
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
