"""One local data folder, awn-data/, with a fixed layout (2026-10-22, the maintainer's request):

    awn-data/published/  the exact files the app pins (core/data/bundled-packs.sha256, test-packs.sha256)
    awn-data/next/       the workspace for the next data release (manifests, quran, translations, timings)
    awn-data/fonts/      each print once, as the zips that are uploaded
    awn-data/raw/        source downloads, one copy for every tool

Every tool finds these through tools/data_paths.py. Environment variables still override the roots so a
git worktree or CI can point elsewhere. No tool names the old folders (awn-data-v2, awn-data-next,
awn-data-unbundle) or a home-directory path.
"""

import os
import re
import unittest
from pathlib import Path
from unittest import mock

import data_paths

TOOLS = Path(__file__).resolve().parent
REPOSITORY = TOOLS.parent


class DefaultLayoutTest(unittest.TestCase):
    def test_every_folder_sits_under_awn_data_in_the_repository(self):
        with mock.patch.dict(os.environ, {}, clear=True):
            self.assertEqual(REPOSITORY / "awn-data", data_paths.data_root())
            self.assertEqual(REPOSITORY / "awn-data" / "published", data_paths.published())
            self.assertEqual(REPOSITORY / "awn-data" / "next", data_paths.workspace())
            self.assertEqual(REPOSITORY / "awn-data" / "fonts", data_paths.fonts())
            self.assertEqual(REPOSITORY / "awn-data" / "raw", data_paths.raw())


class OverrideTest(unittest.TestCase):
    def test_awn_data_root_moves_every_folder(self):
        with mock.patch.dict(os.environ, {"AWN_DATA_ROOT": "/tmp/elsewhere"}, clear=True):
            self.assertEqual(Path("/tmp/elsewhere/next"), data_paths.workspace())
            self.assertEqual(Path("/tmp/elsewhere/raw"), data_paths.raw())

    def test_awn_data_still_names_the_workspace_for_old_commands(self):
        with mock.patch.dict(os.environ, {"AWN_DATA": "/tmp/workspace"}, clear=True):
            self.assertEqual(Path("/tmp/workspace"), data_paths.workspace())


class NoOldPathsTest(unittest.TestCase):
    OLD_NAMES = re.compile(r"awn-data-(v2|next|unbundle)|/home/rms")

    def test_no_tool_names_an_old_folder_or_a_home_path(self):
        offenders = []
        for source in sorted(TOOLS.glob("*.py")):
            if source.name == Path(__file__).name:
                continue
            if self.OLD_NAMES.search(source.read_text(encoding="utf-8")):
                offenders.append(source.name)
        self.assertEqual([], offenders)

    def test_the_build_reads_pinned_files_from_published(self):
        build_file = (REPOSITORY / "core" / "data" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('"awn-data/published"', build_file)


if __name__ == "__main__":
    unittest.main()
