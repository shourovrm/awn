"""Tests for the gapless (whole-surah) pack builder in build_timings.py, on a tiny fake database and synthetic MP3s.

Run: python3 -I -m unittest discover -s tools -p "test_*.py"
"""

import os
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_timings

# MPEG-1 Layer III, 128 kbps, 44100 Hz: 417-byte frames of 1152 samples, 26.122 ms each.
FRAME_HEADER = bytes([0xFF, 0xFB, 0x90, 0x00])
FRAME_BYTES = 417
FRAME_COUNT = 400  # about 10.4 s, longer than any ayah in the fake database
PACK = "raad_al_kurdi"
SURAH_ONE_URL = "https://download.quranicaudio.com/quran/raad_mohammad_al_kurdi/mp3/001.mp3"
SURAH_TWO_URL = "https://download.quranicaudio.com/quran/raad_mohammad_al_kurdi/mp3/002.mp3"
WORD_COUNTS = {(1, 1): 2, (1, 2): 2, (2, 1): 1}


def synthetic_surah_file():
    return b"".join(FRAME_HEADER + bytes(FRAME_BYTES - len(FRAME_HEADER)) for _ in range(FRAME_COUNT))


def write_database(directory, rows):
    """A stand-in for Quran for Android's timings table, with the same column types."""
    path = os.path.join(directory, "source.db")
    connection = sqlite3.connect(path)
    connection.execute("CREATE TABLE timings (sura INTEGER, ayah INTEGER, time TEXT, words TEXT)")
    connection.executemany("INSERT INTO timings VALUES (?,?,?,?)", rows)
    connection.commit()
    connection.close()
    return path


def good_rows():
    return [
        (1, 1, "1000.0", "1:1100:1200,2:1200:1500"),
        (1, 2, "4000", "1:4100:4300,2:4300:4600"),
        (2, 1, "500.0", "1:600:900"),
    ]


class GaplessBuildTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)

    def build(self, rows, word_counts=WORD_COUNTS):
        database_path = write_database(self.directory.name, rows)
        pack_path = os.path.join(self.directory.name, "pack.sqlite")
        connection = build_timings.create_database(pack_path)
        build_timings.build_from_gapless_database(
            connection, database_path, word_counts, PACK,
            chunks_of_url=lambda url: [synthetic_surah_file()])
        connection.commit()
        return connection

    def test_segment_table_stays_empty_because_the_pack_has_no_word_times(self):
        connection = self.build(good_rows())
        self.assertEqual(0, connection.execute("SELECT COUNT(*) FROM segment").fetchone()[0])

    def test_source_word_lists_are_not_read(self):
        # A word list that repeats positions or has the wrong count must not stop the ayah timings.
        rows = good_rows()
        rows[0] = (1, 1, "1000.0", "1:1100:1200,2:1200:1300,1:1300:1400")
        rows[1] = (1, 2, "4000", "1:4100:4300,2:4300:4600,3:4600:4700")
        connection = self.build(rows)
        self.assertEqual(3, connection.execute("SELECT COUNT(*) FROM ayah_audio").fetchone()[0])

    def test_ayah_rows_point_at_the_surah_file_with_its_start_as_offset(self):
        connection = self.build(good_rows())
        audio = connection.execute(
            "SELECT surah, ayah, url, offset_ms FROM ayah_audio ORDER BY surah, ayah").fetchall()
        self.assertEqual([(1, 1, SURAH_ONE_URL, 1000), (1, 2, SURAH_ONE_URL, 4000), (2, 1, SURAH_TWO_URL, 500)], audio)

    def test_duration_is_the_gap_to_the_next_ayah_and_null_for_the_last_ayah_of_a_surah(self):
        connection = self.build(good_rows())
        durations = dict(((surah, ayah), duration) for surah, ayah, duration in connection.execute(
            "SELECT surah, ayah, duration_ms FROM ayah_audio"))
        self.assertEqual({(1, 1): 3000, (1, 2): None, (2, 1): None}, durations)

    def test_ayah_list_that_differs_from_the_quran_text_fails(self):
        with self.assertRaisesRegex(ValueError, "does not list the ayahs of the Quran text in order"):
            self.build(good_rows()[:2], word_counts={(1, 1): 2, (1, 2): 2, (2, 1): 1})

    def test_byte_rows_start_a_few_frames_before_the_ayah(self):
        connection = self.build(good_rows())
        rows = connection.execute(
            "SELECT surah, ayah, start_byte, end_byte, start_byte_ms FROM ayah_bytes ORDER BY surah, ayah").fetchall()
        starts_ms = {(surah, ayah): offset for surah, ayah, offset in connection.execute(
            "SELECT surah, ayah, offset_ms FROM ayah_audio")}
        for surah, ayah, start_byte, end_byte, start_byte_ms in rows:
            lead_ms = starts_ms[(surah, ayah)] - start_byte_ms
            self.assertTrue(0 <= lead_ms <= build_timings.MAXIMUM_START_LEAD_MS, (surah, ayah, lead_ms))
            self.assertEqual(0, start_byte % FRAME_BYTES, "a frame starts at a multiple of 417 bytes here")
        self.assertEqual(3, len(rows))

    def test_byte_end_is_null_for_the_last_ayah_of_a_surah_only(self):
        connection = self.build(good_rows())
        ends = dict(((surah, ayah), end) for surah, ayah, end in connection.execute(
            "SELECT surah, ayah, end_byte FROM ayah_bytes"))
        self.assertIsNotNone(ends[(1, 1)])
        self.assertIsNone(ends[(1, 2)])
        self.assertIsNone(ends[(2, 1)])

    def test_byte_start_is_the_frame_four_frames_before_the_one_holding_the_start(self):
        connection = self.build(good_rows())
        start_byte, start_byte_ms = connection.execute(
            "SELECT start_byte, start_byte_ms FROM ayah_bytes WHERE surah = 1 AND ayah = 1").fetchone()
        # 1000 ms lies in frame 38 (992.6 ms); four frames back is frame 34 at 888.2 ms.
        self.assertEqual((34 * FRAME_BYTES, 888), (start_byte, start_byte_ms))

    def test_ayah_starting_after_the_end_of_the_file_fails(self):
        rows = good_rows()
        rows[2] = (2, 1, "99999.0", "1:100:200")
        with self.assertRaisesRegex(ValueError, r"2:1 starts at 99999 ms"):
            self.build(rows)


if __name__ == "__main__":
    unittest.main()
