"""Tests for the MPEG frame walk in walk_mp3_frames.py, on synthetic Layer III streams.

Run: python3 -I -m unittest discover -s tools -p "test_*.py"
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import walk_mp3_frames

# MPEG-1 Layer III, no CRC, 44100 Hz, no padding: 128 kbps gives 417-byte frames of 1152 samples (26.122 ms).
HEADER_128_KBPS = bytes([0xFF, 0xFB, 0x90, 0x00])
FRAME_128_KBPS_BYTES = 417
# The same stream at 320 kbps: 144 * 320000 // 44100 = 1044 bytes.
HEADER_320_KBPS = bytes([0xFF, 0xFB, 0xE0, 0x00])
FRAME_320_KBPS_BYTES = 1044
FRAME_MS = 1152 * 1000 / 44100


def frame_bytes(header, length):
    return header + bytes(length - len(header))


def stream(*frames):
    return b"".join(frames)


def chunked(data, chunk_size):
    return [data[start:start + chunk_size] for start in range(0, len(data), chunk_size)]


class WalkTest(unittest.TestCase):
    def setUp(self):
        self.frames_128 = [frame_bytes(HEADER_128_KBPS, FRAME_128_KBPS_BYTES) for _ in range(3)]

    def test_frame_offsets_and_times_follow_the_frame_lengths(self):
        frames = list(walk_mp3_frames.walk_mp3_frames([stream(*self.frames_128)]))
        self.assertEqual([0, 417, 834], [frame.offset for frame in frames])
        self.assertAlmostEqual(0.0, frames[0].time_ms)
        self.assertAlmostEqual(2 * FRAME_MS, frames[2].time_ms, places=6)
        self.assertEqual({128}, {frame.bitrate_kbps for frame in frames})

    def test_chunk_boundaries_do_not_change_the_frames(self):
        data = stream(*self.frames_128)
        whole = list(walk_mp3_frames.walk_mp3_frames([data]))
        one_byte_at_a_time = list(walk_mp3_frames.walk_mp3_frames(chunked(data, 1)))
        self.assertEqual(whole, one_byte_at_a_time)

    def test_id3v2_tag_at_the_start_is_skipped(self):
        # Header: "ID3", version 4.0, flags 0, syncsafe size 10 (bytes 0, 0, 0, 10), then ten bytes of tag body.
        tag = b"ID3\x04\x00\x00\x00\x00\x00\x0a" + bytes(10)
        frames = list(walk_mp3_frames.walk_mp3_frames([tag + stream(*self.frames_128)]))
        self.assertEqual([len(tag), len(tag) + 417, len(tag) + 834], [frame.offset for frame in frames])

    def test_bitrate_change_is_recorded_with_the_frame_where_it_happens(self):
        data = stream(self.frames_128[0], frame_bytes(HEADER_320_KBPS, FRAME_320_KBPS_BYTES), self.frames_128[0])
        table = walk_mp3_frames.collect_frame_table(walk_mp3_frames.walk_mp3_frames([data]))
        self.assertEqual([(FRAME_MS, 128, 320, 1), (FRAME_MS + 1152 * 1000 / 44100, 320, 128, 2)],
                         table.bitrate_changes)
        self.assertEqual([0, 417, 417 + 1044], list(table.offsets))

    def test_total_duration_is_the_end_of_the_last_frame(self):
        table = walk_mp3_frames.collect_frame_table(walk_mp3_frames.walk_mp3_frames([stream(*self.frames_128)]))
        self.assertAlmostEqual(3 * FRAME_MS, table.total_ms, places=6)

    def test_invalid_header_after_the_first_frame_fails_with_its_byte_offset(self):
        data = stream(self.frames_128[0], b"\x00" * 417)
        with self.assertRaisesRegex(ValueError, "invalid frame header .* at byte 417"):
            list(walk_mp3_frames.walk_mp3_frames([data]))

    def test_truncated_last_frame_fails(self):
        data = stream(self.frames_128[0], self.frames_128[1][:100])
        with self.assertRaisesRegex(ValueError, "ends inside the frame at byte 417"):
            list(walk_mp3_frames.walk_mp3_frames([data]))

    def test_id3v1_tag_at_the_end_ends_the_walk(self):
        data = stream(*self.frames_128) + b"TAG" + bytes(125)
        self.assertEqual(3, len(list(walk_mp3_frames.walk_mp3_frames([data]))))

    def test_bytes_after_the_id3v1_tag_fail(self):
        data = stream(*self.frames_128) + b"TAG" + bytes(125) + b"x"
        with self.assertRaisesRegex(ValueError, "after the ID3v1 tag"):
            list(walk_mp3_frames.walk_mp3_frames([data]))

    def test_stream_without_a_frame_fails(self):
        with self.assertRaisesRegex(ValueError, "no MPEG Layer III frame header"):
            list(walk_mp3_frames.walk_mp3_frames([b"\x00" * 10]))

    def test_empty_table_fails(self):
        with self.assertRaisesRegex(ValueError, "no frames"):
            walk_mp3_frames.collect_frame_table([])


if __name__ == "__main__":
    unittest.main()
