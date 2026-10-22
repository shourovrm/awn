"""Planning and applying the move of the four old data folders into awn-data/, on a temporary tree."""

import hashlib
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import migrate_data_layout


V2 = migrate_data_layout.VERSION_TWO_NAME
UNBUNDLE = migrate_data_layout.UNBUNDLE_NAME
NEXT = migrate_data_layout.NEXT_COPY_NAME


def sha256_of(content):
    return hashlib.sha256(content).hexdigest()


class MigrationTestCase(unittest.TestCase):
    QURAN = b"pinned quran text"
    SCRIPTS = b"pinned scripts manifest"
    FONT = b"pinned hafs font"
    SAHEEH = b"pinned saheeh"

    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.repository = Path(temporary.name)
        self.old_root = self.repository / "awn-data"
        self.write_pins()
        self.build_old_folders()

    def write(self, relative, content):
        path = self.repository / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return path

    def write_pins(self):
        bundled = [(self.QURAN, "quran.sqlite"), (self.FONT, "UthmanicHafs1Ver18.ttf"), (self.SCRIPTS, "scripts.json")]
        tested = [(self.SAHEEH, "en_saheeh.sqlite")]
        for file_name, entries in (("bundled-packs.sha256", bundled), ("test-packs.sha256", tested)):
            lines = [f"{sha256_of(content)}  {name}" for content, name in entries]
            self.write(f"core/data/{file_name}", "\n".join(lines).encode() + b"\n")

    def build_old_folders(self):
        # Older awn-data: stale quran text and manifest, the shared fonts, the first raw downloads.
        self.write("awn-data/quran/quran.sqlite", b"older quran build")
        self.write("awn-data/manifests/scripts.json", b"older manifest")
        self.write("awn-data/translations/en_saheeh.sqlite", self.SAHEEH)
        self.write("awn-data/translations/en_asad.sqlite", b"asad only here")
        self.write("awn-data/fonts/qpc-hafs/UthmanicHafs1Ver18.ttf", self.FONT)
        self.write("awn-data/fonts/fonts.json", json.dumps({"scripts": {"v1": {"files": 1}}}).encode())
        self.write("awn-data/fonts/v1.zip", b"zip of v1")
        self.write("awn-data/raw/quran.com/same.json", b"same")
        self.write("awn-data/raw/quran.com/clash.json", b"old bytes")
        # data-v2 workspace: shares the fonts, adds indopak and one more raw download.
        self.write(f"{V2}/quran/quran.sqlite", b"v2 quran build")
        self.write(f"{V2}/RELEASE-PLAN.md", b"plan")
        self.write(f"{V2}/translations/en_saheeh.sqlite", self.SAHEEH)
        self.write(f"{V2}/fonts/v1.zip", b"zip of v1")
        both_scripts = {"v1": {"files": 1}, "indopak": {"files": 1}}
        self.write(f"{V2}/fonts/fonts.json", json.dumps({"scripts": both_scripts}).encode())
        self.write(f"{V2}/fonts/indopak/nastaleeq.ttf", b"indopak")
        self.write(f"{V2}/raw/quran.com/same.json", b"same")
        self.write(f"{V2}/raw/quran.com/clash.json", b"new bytes")
        self.write(f"{V2}/raw/tanzil/en.txt", b"tanzil")
        # The pinned files, and a words pack that only the unbundle folder holds.
        self.write(f"{UNBUNDLE}/quran/quran.sqlite", self.QURAN)
        self.write(f"{UNBUNDLE}/manifests/scripts.json", self.SCRIPTS)
        self.write(f"{UNBUNDLE}/translations/words.sqlite", b"words pack")
        # The second pinned copy keeps symlinks into awn-data, as the real folder does.
        self.write(f"{NEXT}/quran/quran.sqlite", self.QURAN)
        self.write(f"{NEXT}/translations/words.sqlite", b"words pack")
        os.symlink(self.old_root / "fonts", self.repository / f"{NEXT}" / "fonts")
        self.write("awn-models/model.onnx", b"never touched")

    def plan(self):
        planner = migrate_data_layout.Planner(self.repository)
        operations = planner.plan()
        return planner, operations

    def apply(self):
        planner, operations = self.plan()
        migrate_data_layout.apply_operations(operations, planner.published, planner.pins)
        return planner


class PlanTest(MigrationTestCase):
    def test_planning_changes_nothing_on_disk(self):
        before = sorted(str(path) for path in self.repository.rglob("*"))
        self.plan()
        self.assertEqual(before, sorted(str(path) for path in self.repository.rglob("*")))

    def test_pinned_files_in_the_unbundle_folder_are_moved_not_copied(self):
        _, operations = self.plan()
        quran = [op for op in operations if op.source.name == "quran.sqlite" and op.destination]
        moved = [op for op in quran if op.kind == migrate_data_layout.MOVE and "published" in op.destination.parts]
        self.assertEqual(1, len(moved))
        self.assertEqual(self.repository / f"{UNBUNDLE}" / "quran" / "quran.sqlite", moved[0].source)

    def test_a_pin_missing_from_the_unbundle_folder_is_copied_from_the_folder_that_matches(self):
        _, operations = self.plan()
        copies = [op for op in operations if op.kind == migrate_data_layout.COPY]
        self.assertEqual(
            {"UthmanicHafs1Ver18.ttf", "en_saheeh.sqlite"},
            {op.source.name for op in copies},
        )

    def test_an_unbundle_file_that_misses_its_pin_aborts(self):
        self.write(f"{UNBUNDLE}/quran/quran.sqlite", b"tampered")
        with self.assertRaises(migrate_data_layout.MigrationError):
            self.plan()

    def test_a_pin_without_any_matching_file_aborts(self):
        (self.old_root / "fonts" / "qpc-hafs" / "UthmanicHafs1Ver18.ttf").unlink()
        with self.assertRaises(migrate_data_layout.MigrationError):
            self.plan()

    def test_shared_fonts_with_different_bytes_abort(self):
        self.write(f"{V2}/fonts/v1.zip", b"a different zip")
        with self.assertRaises(migrate_data_layout.MigrationError):
            self.plan()

    def test_a_second_migration_is_refused(self):
        (self.old_root / "published").mkdir()
        with self.assertRaises(migrate_data_layout.MigrationError):
            self.plan()

    def test_no_operation_touches_awn_models(self):
        _, operations = self.plan()
        for operation in operations:
            for path in (operation.source, operation.destination):
                if path is not None:
                    self.assertNotIn("awn-models", path.parts)


class ApplyTest(MigrationTestCase):
    def test_published_holds_every_pinned_file_with_its_pinned_bytes(self):
        self.apply()
        published = self.old_root / "published"
        self.assertEqual(self.QURAN, (published / "quran" / "quran.sqlite").read_bytes())
        self.assertEqual(self.SCRIPTS, (published / "manifests" / "scripts.json").read_bytes())
        self.assertEqual(self.FONT, (published / "fonts" / "qpc-hafs" / "UthmanicHafs1Ver18.ttf").read_bytes())
        self.assertEqual(self.SAHEEH, (published / "translations" / "en_saheeh.sqlite").read_bytes())

    def test_next_holds_the_v2_workspace_and_files_found_in_no_other_place(self):
        self.apply()
        workspace = self.old_root / "next"
        self.assertEqual(b"v2 quran build", (workspace / "quran" / "quran.sqlite").read_bytes())
        self.assertEqual(b"plan", (workspace / "RELEASE-PLAN.md").read_bytes())
        self.assertEqual(b"words pack", (workspace / "translations" / "words.sqlite").read_bytes())
        self.assertEqual(b"asad only here", (workspace / "translations" / "en_asad.sqlite").read_bytes())

    def test_fonts_keep_one_copy_and_the_newer_fonts_json(self):
        self.apply()
        fonts = self.old_root / "fonts"
        self.assertEqual(b"indopak", (fonts / "indopak" / "nastaleeq.ttf").read_bytes())
        self.assertEqual({"v1", "indopak"}, set(json.loads((fonts / "fonts.json").read_text())["scripts"]))
        self.assertTrue((fonts / "v1.zip").is_file())

    def test_raw_is_merged_and_a_clash_keeps_both_versions(self):
        planner = self.apply()
        raw = self.old_root / "raw" / "quran.com"
        self.assertEqual(b"old bytes", (raw / "clash.json").read_bytes())
        self.assertEqual(b"new bytes", (raw / f"clash.from-{V2}.json").read_bytes())
        self.assertEqual(b"tanzil", (self.old_root / "raw" / "tanzil" / "en.txt").read_bytes())
        self.assertEqual(1, len(planner.reports))

    def test_old_folders_and_stale_files_are_gone_and_awn_models_stays(self):
        self.apply()
        for name in (f"{V2}", f"{UNBUNDLE}", f"{NEXT}"):
            self.assertFalse((self.repository / name).exists(), name)
        for name in ("quran", "manifests", "translations"):
            self.assertFalse((self.old_root / name).exists(), name)
        self.assertEqual(b"never touched", (self.repository / "awn-models" / "model.onnx").read_bytes())

    def test_a_folder_with_an_unplaced_file_is_not_removed(self):
        _, operations = self.plan()
        self.write(f"{UNBUNDLE}/surprise.txt", b"created after planning")
        planner = migrate_data_layout.Planner(self.repository)
        with self.assertRaises(migrate_data_layout.MigrationError):
            migrate_data_layout.apply_operations(operations, planner.published, planner.pins)
        self.assertTrue((self.repository / f"{UNBUNDLE}" / "surprise.txt").exists())


if __name__ == "__main__":
    unittest.main()
