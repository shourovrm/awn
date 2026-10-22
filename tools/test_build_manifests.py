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

    def test_one_layout_pack_and_one_font_entry_per_line_count(self):
        self.assertEqual({"indopak-layouts", "indopak-15", "indopak-16"}, set(self.entries))

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
        for pack_id in ("indopak-15", "indopak-16"):
            font = self.entries[pack_id]
            self.assertEqual(hashlib.sha256(FONT_BYTES).hexdigest(), font["sha256"])
            self.assertEqual(len(FONT_BYTES), font["bytes"])
            self.assertTrue(font["download_url"].startswith(
                "https://raw.githubusercontent.com/quran/quran.com-frontend-next/aff1a035b09b66f28047b3216edcae4c5c949a49/"))
            self.assertTrue(font["download_url"].endswith("indopak-nastaleeq-waqf-lazim-v4.2.1.ttf"))
            self.assertEqual(["indopak-layouts"], font["requires"])

    def test_fonts_differ_in_lines_and_pages(self):
        self.assertEqual((15, 610), (self.entries["indopak-15"]["lines_per_page"], self.entries["indopak-15"]["pages"]))
        self.assertEqual((16, 548), (self.entries["indopak-16"]["lines_per_page"], self.entries["indopak-16"]["pages"]))

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

        self.assertEqual(["quran-text", "v1", "indopak-layouts", "indopak-15", "indopak-16"], self.read_ids(path))

    def test_running_twice_does_not_repeat_entries_and_drops_the_old_single_font_entry(self):
        path = self.write_manifest([{"id": "quran-text"}, {"id": "indopak"}])

        build_manifests.add_indopak_to_manifest(path)
        build_manifests.add_indopak_to_manifest(path)

        self.assertEqual(["quran-text", "indopak-layouts", "indopak-15", "indopak-16"], self.read_ids(path))


if __name__ == "__main__":
    unittest.main()
