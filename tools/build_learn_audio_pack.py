#!/usr/bin/env python3
"""Zips a folder of Learn clips into the download the app installs and prints its checksum.

Usage: python3 tools/build_learn_audio_pack.py CLIPS_FOLDER [--out PATH]

CLIPS_FOLDER holds one folder per unit and one Opus file per clip, for example
`qaida-04/012.opus`. The zip keeps those relative names, which are the clip ids the lesson files
use plus `.opus`. The default output is awn-data/next/learn/qaida-audio.zip. Members are written in
name order with a fixed timestamp, so the same clips give the same checksum.

The app refuses any member whose name is not `<folder>/<name>.opus` (lower case letters, digits and
hyphens), so this tool refuses the same names. Add the printed entry to the manifest with
`python3 tools/build_manifests.py --add-learn-audio=PATH_TO_scripts.json`.
"""

import argparse
import hashlib
import re
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import data_paths

FIXED_ZIP_TIMESTAMP = (2026, 1, 1, 0, 0, 0)
MEMBER_NAME_PATTERN = re.compile(r"^[a-z0-9-]+/[a-z0-9-]+\.opus$")
DEFAULT_PACK_NAME = "qaida-audio.zip"


class ClipFolderError(Exception):
    """The clips folder cannot make a pack the app would accept."""


def clip_files(clips_folder):
    """Returns (member name, path) for every file under [clips_folder], sorted by member name."""
    clips_folder = Path(clips_folder)
    if not clips_folder.is_dir():
        raise ClipFolderError(f"{clips_folder} is not a folder.")
    members = []
    for path in sorted(clips_folder.rglob("*")):
        if not path.is_file():
            continue
        name = path.relative_to(clips_folder).as_posix()
        if not MEMBER_NAME_PATTERN.match(name):
            raise ClipFolderError(
                f"{path}: the member name {name!r} must look like 'qaida-04/012.opus'."
            )
        members.append((name, path))
    if not members:
        raise ClipFolderError(f"{clips_folder} holds no clips.")
    return members


def build_pack(clips_folder, out_path):
    """Writes the zip and returns (sha256, size in bytes, number of clips)."""
    members = clip_files(clips_folder)
    out_path = Path(out_path)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = out_path.with_name(out_path.name + ".part")
    with zipfile.ZipFile(temporary_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, path in members:
            member = zipfile.ZipInfo(name, FIXED_ZIP_TIMESTAMP)
            member.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(member, path.read_bytes(), compresslevel=9)
    # Replace by a new file: an older data folder may hold a hard link to the old archive.
    temporary_path.replace(out_path)
    return hashlib.sha256(out_path.read_bytes()).hexdigest(), out_path.stat().st_size, len(members)


def main(arguments):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("clips", type=Path)
    parser.add_argument("--out", type=Path, default=data_paths.workspace() / "learn" / DEFAULT_PACK_NAME)
    options = parser.parse_args(arguments)
    try:
        sha256, size, count = build_pack(options.clips, options.out)
    except ClipFolderError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    print(f"wrote {options.out}")
    print(f"sha256 {sha256}")
    print(f"bytes  {size}")
    print(f"clips  {count}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
