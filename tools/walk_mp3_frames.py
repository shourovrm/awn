#!/usr/bin/env python3
"""Read the MPEG audio frames of a Layer III stream: each frame's byte offset, start time and bitrate.

A whole-surah file cannot be cut at a byte position computed from one bitrate, because a file may change
bitrate in the middle (surah 18 of the Raad al-Kurdi files has a 320 kbps run inside 128 kbps audio). So the
walk reads every frame header and adds up the frame durations, which gives the exact byte where each frame starts.

Only MPEG-1, MPEG-2 and MPEG-2.5 Layer III are read. An invalid header after the first frame is an error,
because a timing computed past it would be wrong; bytes before the first frame (padding after an ID3v2 tag)
are skipped up to LEADING_JUNK_LIMIT bytes.

stream_url_chunks streams a file as it arrives. Nothing is written to disk, because the whole set of
surah files is about 1.9 GB and the build machine has little free disk.

Used by tools/build_timings.py (the Raad al-Kurdi gapless pack). The walk is covered by tools/test_walk_mp3_frames.py.
"""

import array
import collections
import os
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_com

# One frame: its byte offset in the file, its start time in ms, its length in bytes and its header's values.
Frame = collections.namedtuple("Frame", "offset time_ms length bitrate_kbps sample_rate_hz samples")
# The frames as parallel arrays (about 4.6 million frames over all surahs, so no Python objects per frame);
# bitrate_changes lists (time_ms, from_kbps, to_kbps, frame_index) for every frame whose bitrate differs from the one before.
FrameTable = collections.namedtuple("FrameTable", "offsets times_ms bitrates_kbps total_ms bitrate_changes")

MPEG1_BITRATES_KBPS = (0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
MPEG2_BITRATES_KBPS = (0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
# Keyed by the two version bits of the header: 3 is MPEG-1, 2 is MPEG-2, 0 is MPEG-2.5 (1 is reserved).
SAMPLE_RATES_HZ = {3: (44100, 48000, 32000), 2: (22050, 24000, 16000), 0: (11025, 12000, 8000)}
ID3V1_TAG_BYTES = 128
LEADING_JUNK_LIMIT = 4096
CHUNK_BYTES = 1 << 16
# The reader drops consumed bytes once this many are waiting, so a 137 MB file never sits in memory whole.
COMPACT_AFTER_BYTES = 1 << 20


class ChunkReader:
    """Serves a byte stream by file position, reading the next chunk only when the buffer runs short."""

    def __init__(self, byte_chunks):
        self._chunks = iter(byte_chunks)
        self._buffer = bytearray()
        self._buffer_file_offset = 0
        self._position = 0
        self._exhausted = False

    def file_offset(self):
        return self._buffer_file_offset + self._position

    def available(self, count):
        """True when count bytes can be read from the current position; reads chunks until they are, or the file ends."""
        while len(self._buffer) - self._position < count and not self._exhausted:
            chunk = next(self._chunks, None)
            if chunk is None:
                self._exhausted = True
            else:
                self._buffer += chunk
        return len(self._buffer) - self._position >= count

    def peek(self, count):
        """The next count bytes without consuming them; call available(count) first."""
        return bytes(self._buffer[self._position:self._position + count])

    def skip(self, count):
        """Consume count bytes; the file must hold them, otherwise the stream is truncated."""
        if not self.available(count):
            raise ValueError(f"the file ends inside a {count} byte block starting at byte {self.file_offset()}")
        self._position += count
        if self._position >= COMPACT_AFTER_BYTES:
            del self._buffer[:self._position]
            self._buffer_file_offset += self._position
            self._position = 0


def parse_header(header):
    """(frame_length, bitrate_kbps, sample_rate_hz, samples_per_frame) of a 4-byte Layer III header, or None when it is not one."""
    if header[0] != 0xFF or header[1] & 0xE0 != 0xE0:
        return None
    version_bits = (header[1] >> 3) & 3
    layer_bits = (header[1] >> 1) & 3
    if version_bits == 1 or layer_bits != 1:
        return None
    bitrate_index = header[2] >> 4
    sample_rate_index = (header[2] >> 2) & 3
    padding_bytes = (header[2] >> 1) & 1
    if version_bits == 3:
        bitrate_kbps = MPEG1_BITRATES_KBPS[bitrate_index]
        samples_per_frame, bytes_per_kilobit_second = 1152, 144
    else:
        bitrate_kbps = MPEG2_BITRATES_KBPS[bitrate_index]
        samples_per_frame, bytes_per_kilobit_second = 576, 72
    if bitrate_kbps == 0 or sample_rate_index == 3:
        return None
    sample_rate_hz = SAMPLE_RATES_HZ[version_bits][sample_rate_index]
    frame_length = bytes_per_kilobit_second * bitrate_kbps * 1000 // sample_rate_hz + padding_bytes
    return frame_length, bitrate_kbps, sample_rate_hz, samples_per_frame


def skip_id3v2_tag(reader):
    """Skip an ID3v2 tag at the start; its size is a 28-bit 'syncsafe' number in bytes 6 to 9 of the header."""
    if not reader.available(10) or reader.peek(3) != b"ID3":
        return
    header = reader.peek(10)
    tag_bytes = (header[6] << 21) | (header[7] << 14) | (header[8] << 7) | header[9]
    footer_bytes = 10 if header[5] & 0x10 else 0
    reader.skip(10 + tag_bytes + footer_bytes)


def skip_leading_junk(reader):
    """Skip bytes before the first frame header; a file that never shows one is an error."""
    skipped = 0
    while True:
        if not reader.available(4):
            raise ValueError("no MPEG Layer III frame header in the file")
        if parse_header(reader.peek(4)) is not None:
            return
        if skipped >= LEADING_JUNK_LIMIT:
            raise ValueError(f"no frame header within the first {LEADING_JUNK_LIMIT} bytes of the file")
        reader.skip(1)
        skipped += 1


def walk_mp3_frames(byte_chunks):
    """Yield a Frame for every frame of the stream in file order.

    byte_chunks is any iterable of bytes, so a test can pass a list and a download can pass a generator.
    """
    reader = ChunkReader(byte_chunks)
    skip_id3v2_tag(reader)
    skip_leading_junk(reader)
    time_ms = 0.0
    while True:
        if not reader.available(4):
            if reader.available(1):
                raise ValueError(f"trailing bytes after the last frame at byte {reader.file_offset()}")
            return
        if reader.peek(3) == b"TAG":
            skip_id3v1_tag(reader)
            return
        header = reader.peek(4)
        parsed = parse_header(header)
        if parsed is None:
            raise ValueError(f"invalid frame header {header.hex()} at byte {reader.file_offset()}")
        frame_length, bitrate_kbps, sample_rate_hz, samples_per_frame = parsed
        if not reader.available(frame_length):
            raise ValueError(f"the file ends inside the frame at byte {reader.file_offset()}")
        yield Frame(reader.file_offset(), time_ms, frame_length, bitrate_kbps, sample_rate_hz, samples_per_frame)
        time_ms += samples_per_frame * 1000 / sample_rate_hz
        reader.skip(frame_length)


def skip_id3v1_tag(reader):
    """The fixed 128-byte ID3v1 tag at the very end; nothing may follow it."""
    if not reader.available(ID3V1_TAG_BYTES):
        raise ValueError(f"truncated ID3v1 tag at byte {reader.file_offset()}")
    reader.skip(ID3V1_TAG_BYTES)
    if reader.available(1):
        raise ValueError(f"bytes after the ID3v1 tag at byte {reader.file_offset()}")


def collect_frame_table(frames):
    """Gather the frames into a FrameTable and note every bitrate change."""
    offsets = array.array("q")
    times_ms = array.array("d")
    bitrates_kbps = array.array("H")
    bitrate_changes = []
    total_ms = 0.0
    for frame in frames:
        if bitrates_kbps and frame.bitrate_kbps != bitrates_kbps[-1]:
            bitrate_changes.append((frame.time_ms, bitrates_kbps[-1], frame.bitrate_kbps, len(offsets)))
        offsets.append(frame.offset)
        times_ms.append(frame.time_ms)
        bitrates_kbps.append(frame.bitrate_kbps)
        total_ms = frame.time_ms + frame.samples * 1000 / frame.sample_rate_hz
    if not offsets:
        raise ValueError("the stream has no frames")
    return FrameTable(offsets, times_ms, bitrates_kbps, total_ms, bitrate_changes)


def stream_url_chunks(url):
    """Yield the bytes of url as they arrive. The download is not kept on disk."""
    request = urllib.request.Request(url, headers={"User-Agent": quran_com.USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        while True:
            chunk = response.read(CHUNK_BYTES)
            if not chunk:
                return
            yield chunk


NETWORK_ATTEMPTS = 4


def collect_frame_table_from_url(url, chunks_of_url=stream_url_chunks, attempts=NETWORK_ATTEMPTS):
    """Walk the file at url, starting that file again when the connection stalls or drops.

    A surah file is up to 137 MB and the walk streams it without keeping it, so a stalled read
    cannot be resumed in the middle of the frame walk; the whole file is read again instead.
    """
    last_error = None
    for attempt in range(1, attempts + 1):
        try:
            return collect_frame_table(walk_mp3_frames(chunks_of_url(url)))
        except (TimeoutError, ConnectionError, urllib.error.URLError) as error:
            last_error = error
            print(f"{url}: attempt {attempt} of {attempts} failed: {error!r}", file=sys.stderr)
    raise RuntimeError(f"{url}: the download failed {attempts} times") from last_error
