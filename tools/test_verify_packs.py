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


if __name__ == "__main__":
    unittest.main()
