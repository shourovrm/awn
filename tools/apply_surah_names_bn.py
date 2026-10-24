#!/usr/bin/env python3
"""Put the spoken Bangla surah names into an already built quran.sqlite.

Rebuilding quran.sqlite from the Quran.com API would change every table if the API data moved on;
this script changes only the chapter table: name_bangla becomes the spoken name, the new column
name_bangla_meaning holds the meaning, and pack_info records the source of the names.

Usage: python3 tools/apply_surah_names_bn.py INPUT.sqlite OUTPUT.sqlite
"""

import os
import shutil
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import surah_names_bn


def apply_names(database_path):
    names = surah_names_bn.read_surah_names()
    connection = sqlite3.connect(database_path)
    columns = [row[1] for row in connection.execute("PRAGMA table_info(chapter)")]
    if "name_bangla_meaning" not in columns:
        connection.execute("ALTER TABLE chapter ADD COLUMN name_bangla_meaning TEXT")
    for number, (spoken_name, meaning) in names.items():
        updated = connection.execute(
            "UPDATE chapter SET name_bangla = ?, name_bangla_meaning = ? WHERE number = ?",
            (spoken_name, meaning, number),
        ).rowcount
        if updated != 1:
            raise ValueError(f"chapter {number} not found in {database_path}")
    connection.execute(
        "INSERT OR REPLACE INTO pack_info VALUES ('bangla_names_source', ?)",
        (surah_names_bn.SOURCE_NOTE,),
    )
    connection.commit()
    connection.execute("VACUUM")
    connection.close()


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    input_path, output_path = sys.argv[1], sys.argv[2]
    shutil.copyfile(input_path, output_path)
    apply_names(output_path)
    print("wrote", output_path)


if __name__ == "__main__":
    main()
