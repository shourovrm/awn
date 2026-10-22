"""Tests for build_learn_audio_pack.py.

Run: cd tools && python3 -m unittest test_build_learn_audio_pack
"""

import hashlib
import os
import shutil
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_learn_audio_pack

SAMPLE_CLIPS = Path("/home/rms/repos/awn/sessions/qaida-audio/lesson1-letters")


class PackTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def write_clip(self, name, content=b"opus"):
        path = self.root / "clips" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)

    def test_the_zip_keeps_the_clip_ids_as_member_names_in_order(self):
        self.write_clip("qaida-04/012.opus")
        self.write_clip("qaida-01/002.opus")

        build_learn_audio_pack.build_pack(self.root / "clips", self.root / "pack.zip")

        with zipfile.ZipFile(self.root / "pack.zip") as archive:
            self.assertEqual(["qaida-01/002.opus", "qaida-04/012.opus"], archive.namelist())

    def test_the_result_names_the_checksum_size_and_count_of_the_file(self):
        self.write_clip("qaida-01/002.opus", b"a")
        self.write_clip("qaida-01/003.opus", b"b")

        sha256, size, count = build_learn_audio_pack.build_pack(
            self.root / "clips", self.root / "pack.zip"
        )

        data = (self.root / "pack.zip").read_bytes()
        self.assertEqual(hashlib.sha256(data).hexdigest(), sha256)
        self.assertEqual(len(data), size)
        self.assertEqual(2, count)

    def test_the_same_clips_give_the_same_checksum(self):
        self.write_clip("qaida-01/002.opus", b"a")

        first = build_learn_audio_pack.build_pack(self.root / "clips", self.root / "one.zip")
        second = build_learn_audio_pack.build_pack(self.root / "clips", self.root / "two.zip")

        self.assertEqual(first, second)

    def test_a_name_the_app_would_refuse_is_refused_here(self):
        for bad in ("002.opus", "qaida-01/002.mp3", "Qaida-01/002.opus", "a/b/c.opus"):
            with self.subTest(bad=bad):
                shutil.rmtree(self.root / "clips", ignore_errors=True)
                self.write_clip(bad)
                with self.assertRaises(build_learn_audio_pack.ClipFolderError):
                    build_learn_audio_pack.build_pack(self.root / "clips", self.root / "p.zip")

    def test_an_empty_folder_is_refused(self):
        (self.root / "clips").mkdir()
        with self.assertRaises(build_learn_audio_pack.ClipFolderError):
            build_learn_audio_pack.build_pack(self.root / "clips", self.root / "p.zip")

    @unittest.skipUnless(SAMPLE_CLIPS.is_dir(), "the sample clips are not present")
    def test_the_thirty_sample_clips_make_a_pack(self):
        for number, source in enumerate(sorted(SAMPLE_CLIPS.glob("*.opus")), start=2):
            self.write_clip(f"qaida-01/{number:03d}.opus", source.read_bytes())

        _, _, count = build_learn_audio_pack.build_pack(self.root / "clips", self.root / "p.zip")

        self.assertEqual(30, count)


if __name__ == "__main__":
    unittest.main()
