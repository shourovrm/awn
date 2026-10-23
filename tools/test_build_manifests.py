"""Tests for the IndoPak entries of build_manifests.py.

Run: cd tools && python3 -m unittest test_build_manifests
"""

import hashlib
import json
import os
import sys
import tempfile
import unittest

# build_manifests reads the data folders when it is imported, so point them at a throwaway folder first.
DATA_ROOT = tempfile.mkdtemp(prefix="awn-manifest-test-")
os.environ["AWN_DATA_ROOT"] = DATA_ROOT
os.environ.pop("AWN_DATA", None)
FONT_BYTES = b"font bytes"
LAYOUT_BYTES = b"layout bytes"
for folder, name, content in (
    ("fonts/indopak", "indopak-nastaleeq-waqf-lazim-v4.2.1.ttf", FONT_BYTES),
    ("next/quran", "indopak.sqlite", LAYOUT_BYTES),
):
    os.makedirs(os.path.join(DATA_ROOT, folder), exist_ok=True)
    with open(os.path.join(DATA_ROOT, folder, name), "wb") as handle:
        handle.write(content)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_manifests


class IndopakEntriesTest(unittest.TestCase):
    def setUp(self):
        self.entries = {entry["id"]: entry for entry in build_manifests.indopak_entries()}

    def test_one_layout_pack_and_one_font_entry(self):
        self.assertEqual({"indopak-layouts", "indopak-15"}, set(self.entries))

    def test_layout_pack_names_the_file_in_the_data_release_and_both_layouts(self):
        layouts = self.entries["indopak-layouts"]
        self.assertEqual("quran/indopak.sqlite", layouts["file"])
        self.assertEqual(hashlib.sha256(LAYOUT_BYTES).hexdigest(), layouts["sha256"])
        self.assertEqual(len(LAYOUT_BYTES), layouts["bytes"])
        self.assertNotIn("download_url", layouts)
        self.assertEqual(
            [{"id": "15", "pages": 610, "lines_per_page": 15}, {"id": "16", "pages": 548, "lines_per_page": 16}],
            layouts["layouts"])

    def test_fonts_are_downloaded_from_quran_com_and_checked_by_hash_and_size(self):
        for pack_id in ("indopak-15",):
            font = self.entries[pack_id]
            self.assertEqual(hashlib.sha256(FONT_BYTES).hexdigest(), font["sha256"])
            self.assertEqual(len(FONT_BYTES), font["bytes"])
            self.assertTrue(font["download_url"].startswith(
                "https://raw.githubusercontent.com/quran/quran.com-frontend-next/aff1a035b09b66f28047b3216edcae4c5c949a49/"))
            self.assertTrue(font["download_url"].endswith("indopak-nastaleeq-waqf-lazim-v4.2.1.ttf"))
            self.assertEqual(["indopak-layouts"], font["requires"])

    def test_the_font_states_its_lines_and_pages(self):
        self.assertEqual((15, 610), (self.entries["indopak-15"]["lines_per_page"], self.entries["indopak-15"]["pages"]))

    def test_fonts_credit_quran_com_and_quranwbw(self):
        credit = self.entries["indopak-15"]["credit"]
        self.assertIn("quranwbw.com", credit)
        self.assertIn("Quran.com", credit)


class AddIndopakToManifestTest(unittest.TestCase):
    def write_manifest(self, packs):
        path = os.path.join(tempfile.mkdtemp(prefix="awn-manifest-test-"), "scripts.json")
        with open(path, "w", encoding="utf-8") as handle:
            json.dump({"format_version": 1, "packs": packs}, handle)
        return path

    def read_ids(self, path):
        with open(path, encoding="utf-8") as handle:
            return [entry["id"] for entry in json.load(handle)["packs"]]

    def test_other_entries_stay_and_the_indopak_ones_follow(self):
        path = self.write_manifest([{"id": "quran-text"}, {"id": "v1"}])

        build_manifests.add_indopak_to_manifest(path)

        self.assertEqual(["quran-text", "v1", "indopak-layouts", "indopak-15"], self.read_ids(path))

    def test_an_entry_of_the_retired_16_line_print_is_dropped(self):
        path = self.write_manifest([{"id": "quran-text"}, {"id": "indopak-16"}])

        build_manifests.add_indopak_to_manifest(path)

        self.assertNotIn("indopak-16", self.read_ids(path))

    def test_running_twice_does_not_repeat_entries_and_drops_the_old_single_font_entry(self):
        path = self.write_manifest([{"id": "quran-text"}, {"id": "indopak"}])

        build_manifests.add_indopak_to_manifest(path)
        build_manifests.add_indopak_to_manifest(path)

        self.assertEqual(["quran-text", "indopak-layouts", "indopak-15"], self.read_ids(path))


class LearnAudioEntryTest(unittest.TestCase):
    def make_archive(self, members):
        import zipfile
        folder = os.path.join(DATA_ROOT, "next", "learn")
        os.makedirs(folder, exist_ok=True)
        path = os.path.join(folder, "qaida-audio.zip")
        with zipfile.ZipFile(path, "w") as archive:
            for name, content in members.items():
                archive.writestr(name, content)
        return path

    def write_manifest(self, packs):
        path = os.path.join(tempfile.mkdtemp(prefix="awn-manifest-test-"), "scripts.json")
        with open(path, "w", encoding="utf-8") as handle:
            json.dump({"format_version": 1, "packs": packs}, handle)
        return path

    def test_the_entry_describes_the_zip_by_hash_size_and_member_count(self):
        archive = self.make_archive({"qaida-01/002.opus": b"abc", "qaida-04/012.opus": b"defg"})

        entry = build_manifests.learn_audio_entry(archive)

        with open(archive, "rb") as handle:
            data = handle.read()
        self.assertEqual(hashlib.sha256(data).hexdigest(), entry["sha256"])
        self.assertEqual(len(data), entry["bytes"])
        self.assertEqual(2, entry["member_count"])
        self.assertEqual(7, entry["installed_bytes"])
        self.assertEqual("learn-audio", entry["kind"])
        self.assertEqual("learn-qaida-audio", entry["id"])
        self.assertIn("Islamic Home Schooling Academy", entry["credit"])

    def test_other_entries_stay_and_running_twice_keeps_one_learn_entry(self):
        archive = self.make_archive({"qaida-01/002.opus": b"abc"})
        path = self.write_manifest([{"id": "quran-text"}])

        build_manifests.add_learn_audio_to_manifest(path, archive)
        build_manifests.add_learn_audio_to_manifest(path, archive)

        with open(path, encoding="utf-8") as handle:
            ids = [entry["id"] for entry in json.load(handle)["packs"]]
        self.assertEqual(["quran-text", "learn-qaida-audio"], ids)


if __name__ == "__main__":
    unittest.main()
