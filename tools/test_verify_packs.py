"""Tests for the pack_info check in verify_packs.py.

Run: python3 -I -m unittest discover -s tools -p "test_*.py"
"""

import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import verify_packs


def make_pack(directory, name, statements):
    path = os.path.join(directory, name)
    connection = sqlite3.connect(path)
    for statement in statements:
        connection.execute(statement)
    connection.commit()
    connection.close()
    return path


class PackFormatVersionTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)

    def test_pack_with_format_version_is_readable(self):
        path = make_pack(self.directory.name, "ok.sqlite", [
            "CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
            "INSERT INTO pack_info VALUES ('format_version', '1')",
        ])
        self.assertTrue(verify_packs.has_readable_format_version(path))

    def test_pack_without_pack_info_table_is_not_readable(self):
        path = make_pack(self.directory.name, "bare.sqlite", ["CREATE TABLE translation (text TEXT)"])
        self.assertFalse(verify_packs.has_readable_format_version(path))

    def test_pack_with_other_keys_only_is_not_readable(self):
        path = make_pack(self.directory.name, "other.sqlite", [
            "CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
            "INSERT INTO pack_info VALUES ('source', 'x')",
        ])
        self.assertFalse(verify_packs.has_readable_format_version(path))

    def test_non_numeric_format_version_is_not_readable(self):
        path = make_pack(self.directory.name, "text.sqlite", [
            "CREATE TABLE pack_info (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
            "INSERT INTO pack_info VALUES ('format_version', 'one')",
        ])
        self.assertFalse(verify_packs.has_readable_format_version(path))


FRAME_BYTES = 417
GAPLESS_AUDIO = [
    (1, 1, "https://download.quranicaudio.com/quran/raad_mohammad_al_kurdi/mp3/001.mp3", 3000, 1000),
    (1, 2, "https://download.quranicaudio.com/quran/raad_mohammad_al_kurdi/mp3/001.mp3", None, 4000),
    (2, 1, "https://download.quranicaudio.com/quran/raad_mohammad_al_kurdi/mp3/002.mp3", None, 500),
]
GAPLESS_BYTES = [
    (1, 1, 34 * FRAME_BYTES, 154 * FRAME_BYTES, 888),
    (1, 2, 149 * FRAME_BYTES, None, 3891),
    (2, 1, 15 * FRAME_BYTES, None, 391),
]


class GaplessPackRulesTest(unittest.TestCase):
    """The rules for a whole-surah pack, run on three ayahs; the 6236-row count fails here by design and is ignored."""

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        verify_packs.failures.clear()

    def failed(self, description_end):
        return any(failure.endswith(description_end) for failure in verify_packs.failures)

    def verify(self, audio_rows=GAPLESS_AUDIO, byte_rows=GAPLESS_BYTES):
        path = make_pack(self.directory.name, "gapless.sqlite", [
            "CREATE TABLE ayah_audio (surah INTEGER, ayah INTEGER, url TEXT, duration_ms INTEGER, offset_ms INTEGER)",
            "CREATE TABLE ayah_bytes (surah INTEGER, ayah INTEGER, start_byte INTEGER, end_byte INTEGER, start_byte_ms INTEGER)",
        ])
        connection = sqlite3.connect(path)
        connection.executemany("INSERT INTO ayah_audio VALUES (?,?,?,?,?)", audio_rows)
        connection.executemany("INSERT INTO ayah_bytes VALUES (?,?,?,?,?)", byte_rows)
        verify_packs.verify_whole_surah_audio(connection, "raad_al_kurdi")
        connection.close()

    def test_consistent_rows_pass_every_rule(self):
        self.verify()
        for description in ("urls end in SSS.mp3 for their surah", "has one url per surah",
                            "offset_ms strictly increases within a surah",
                            "duration_ms is NULL only for the last ayah of each surah",
                            "start_byte strictly increases within a surah", "start_byte_ms is at most offset_ms",
                            "end_byte lies after start_byte", "end_byte is NULL only for the last ayah of each surah"):
            self.assertFalse(self.failed(description), description)

    def test_null_duration_inside_a_surah_fails(self):
        audio = [GAPLESS_AUDIO[0], (1, 2, GAPLESS_AUDIO[1][2], None, 4000), GAPLESS_AUDIO[2]]
        audio[0] = (1, 1, GAPLESS_AUDIO[0][2], None, 1000)
        self.verify(audio_rows=audio)
        self.assertTrue(self.failed("duration_ms is NULL only for the last ayah of each surah"))

    def test_url_with_another_surah_number_fails(self):
        audio = list(GAPLESS_AUDIO)
        audio[2] = (2, 1, GAPLESS_AUDIO[0][2], None, 500)
        self.verify(audio_rows=audio)
        self.assertTrue(self.failed("urls end in SSS.mp3 for their surah"))

    def test_offset_that_does_not_increase_fails(self):
        audio = list(GAPLESS_AUDIO)
        audio[1] = (1, 2, GAPLESS_AUDIO[1][2], None, 900)
        self.verify(audio_rows=audio)
        self.assertTrue(self.failed("offset_ms strictly increases within a surah"))

    def test_start_byte_that_does_not_increase_fails(self):
        byte_rows = list(GAPLESS_BYTES)
        byte_rows[1] = (1, 2, 30 * FRAME_BYTES, None, 3891)
        self.verify(byte_rows=byte_rows)
        self.assertTrue(self.failed("start_byte strictly increases within a surah"))

    def test_start_byte_ms_after_the_ayah_start_fails(self):
        byte_rows = list(GAPLESS_BYTES)
        byte_rows[0] = (1, 1, 34 * FRAME_BYTES, 154 * FRAME_BYTES, 1001)
        self.verify(byte_rows=byte_rows)
        self.assertTrue(self.failed("start_byte_ms is at most offset_ms"))


if __name__ == "__main__":
    unittest.main()
