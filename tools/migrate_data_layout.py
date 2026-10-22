#!/usr/bin/env python3
"""Turn the four old local data folders into the one awn-data/ layout (see data_paths.py).

Old folders, all in the repository root (the names are built in code from the awn-data name plus a
suffix, because tools/test_data_paths.py forbids spelling them anywhere else in tools/):
  awn-data           an older build of the data-v1 content; keeps its fonts/ and raw/ in place
  suffix -v2         the workspace for the unreleased data-v2; becomes awn-data/next/
  suffix -unbundle   the files the app pins today; become awn-data/published/
  suffix -next       a second copy of the pinned files, with symlinks into awn-data

Default is a dry run that prints every move, copy and delete with sizes. `--apply` performs the plan.
Nothing is copied when a rename does; an old folder is removed only after its last file has a place
or is a proven duplicate; awn-models is never touched. Any mismatch aborts before the first change.

Usage: python3 -I tools/migrate_data_layout.py [--apply] [--repository /path/to/checkout]
"""

import argparse
import collections
import filecmp
import hashlib
import json
import os
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import data_paths

OLD_ROOT_NAME = data_paths.DATA_FOLDER
VERSION_TWO_NAME = OLD_ROOT_NAME + "-v2"
UNBUNDLE_NAME = OLD_ROOT_NAME + "-unbundle"
NEXT_COPY_NAME = OLD_ROOT_NAME + "-next"
CHECKSUM_FILES = ("bundled-packs.sha256", "test-packs.sha256")
# The old awn-data keeps these two folders, so they are never planned as stale.
KEPT_IN_PLACE = (data_paths.FONTS_PREFIX, data_paths.RAW_FOLDER)
NEW_FOLDERS = (data_paths.PUBLISHED_FOLDER, data_paths.WORKSPACE_FOLDER)
FONTS_JSON = "fonts.json"

MOVE = "move"
COPY = "copy"
REPLACE = "replace"
DELETE = "delete"
REMOVE_FOLDER = "remove-folder"


class MigrationError(Exception):
    """The folders are not in a state the migration can handle safely."""


@dataclass(frozen=True)
class Operation:
    kind: str
    source: Path
    destination: Path  # None for delete and remove-folder
    size_bytes: int
    file_count: int
    note: str


def file_sha256(path):
    with open(path, "rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def same_content(first, second):
    first_status = first.stat()
    second_status = second.stat()
    if (first_status.st_dev, first_status.st_ino) == (second_status.st_dev, second_status.st_ino):
        return True
    if first_status.st_size != second_status.st_size:
        return False
    return filecmp.cmp(first, second, shallow=False)


def list_files(folder, skipped_top_level=()):
    """Relative paths of the regular files under folder; symlinks are never followed or listed."""
    found = []
    for directory, subdirectories, names in os.walk(folder):
        relative_directory = Path(directory).relative_to(folder)
        if relative_directory == Path("."):
            subdirectories[:] = [name for name in subdirectories if name not in skipped_top_level]
        subdirectories[:] = [
            name for name in subdirectories if not (Path(directory) / name).is_symlink()
        ]
        for name in names:
            path = Path(directory) / name
            if path.is_file() and not path.is_symlink():
                found.append(relative_directory / name)
    return sorted(found)


def folder_size_and_count(path):
    if path.is_file():
        return path.stat().st_size, 1
    relative_files = list_files(path)
    return sum((path / relative).stat().st_size for relative in relative_files), len(relative_files)


def read_pins(repository):
    pins = {}
    for checksum_name in CHECKSUM_FILES:
        checksum_path = repository / "core" / "data" / checksum_name
        for line in checksum_path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            digest, name = line.split()
            if pins.setdefault(name, digest) != digest:
                raise MigrationError(f"{name} is pinned with two different hashes")
    return pins


class Planner:
    def __init__(self, repository):
        self.repository = repository
        self.old_root = repository / OLD_ROOT_NAME
        self.version_two = repository / VERSION_TWO_NAME
        self.unbundle = repository / UNBUNDLE_NAME
        self.next_copy = repository / NEXT_COPY_NAME
        self.published = self.old_root / data_paths.PUBLISHED_FOLDER
        self.workspace = self.old_root / data_paths.WORKSPACE_FOLDER
        self.fonts = self.old_root / data_paths.FONTS_PREFIX
        self.raw = self.old_root / data_paths.RAW_FOLDER
        self.pins = read_pins(repository)
        self.operations = []
        self.reports = []
        self.planned_workspace_files = {}
        self.moved_to_published = set()

    def plan(self):
        for new_folder in NEW_FOLDERS:
            if (self.old_root / new_folder).exists():
                raise MigrationError(f"{self.old_root / new_folder} exists: already migrated")
        if not self.old_root.is_dir():
            raise MigrationError(f"{self.old_root} is missing")
        self.plan_published()
        self.plan_workspace_from_version_two()
        self.plan_carried_files()
        self.plan_fonts()
        self.plan_raw()
        self.plan_folder_removals()
        return self.operations

    def add(self, kind, source, destination, note, size_bytes=None, file_count=None):
        for path in (source, destination):
            if path is not None and "awn-models" in path.parts:
                raise MigrationError(f"{path} is in awn-models, which the migration never touches")
        if size_bytes is None:
            size_bytes, file_count = folder_size_and_count(source)
        self.operations.append(Operation(kind, source, destination, size_bytes, file_count, note))

    def old_folders_in_preference_order(self):
        return [self.unbundle, self.next_copy, self.old_root, self.version_two]

    def plan_published(self):
        # A pinned name that exists in the unbundle folder with other bytes is an error, not a skip.
        if self.unbundle.is_dir():
            for relative in list_files(self.unbundle):
                expected = self.pins.get(relative.name)
                if expected and file_sha256(self.unbundle / relative) != expected:
                    raise MigrationError(f"{self.unbundle / relative} does not match its pin")
        for name, expected in sorted(self.pins.items()):
            source_root, relative = self.find_pinned_file(name, expected)
            destination = self.published / relative
            if source_root == self.unbundle:
                self.add(MOVE, source_root / relative, destination, f"pinned {name}")
                self.moved_to_published.add(source_root / relative)
            else:
                self.add(COPY, source_root / relative, destination, f"pinned {name}")

    def find_pinned_file(self, name, expected):
        for folder in self.old_folders_in_preference_order():
            if not folder.is_dir():
                continue
            for relative in list_files(folder, skipped_top_level=(data_paths.RAW_FOLDER,)):
                if relative.name == name and file_sha256(folder / relative) == expected:
                    return folder, relative
        raise MigrationError(f"no old folder holds {name} with the pinned hash {expected}")

    def plan_workspace_from_version_two(self):
        if not self.version_two.is_dir():
            return
        for entry in sorted(self.version_two.iterdir()):
            if entry.name in KEPT_IN_PLACE:
                continue
            if entry.is_symlink():
                raise MigrationError(f"{entry} is a symlink; not expected in {VERSION_TWO_NAME}")
            self.add(MOVE, entry, self.workspace / entry.name, "data-v2 workspace")

    def plan_carried_files(self):
        """Files outside fonts/ and raw/ in the other three folders: duplicates go, new files are kept."""
        for folder in (self.next_copy, self.unbundle, self.old_root):
            if not folder.is_dir():
                continue
            for relative in list_files(folder, skipped_top_level=KEPT_IN_PLACE + NEW_FOLDERS):
                source = folder / relative
                if source in self.moved_to_published:
                    continue
                self.plan_carried_file(folder, relative)

    def plan_carried_file(self, folder, relative):
        source = folder / relative
        pinned_hash = self.pins.get(relative.name)
        if pinned_hash and file_sha256(source) == pinned_hash:
            self.add(DELETE, source, None, "same bytes as the pinned file in published/")
            return
        counterpart = self.version_two / relative
        if counterpart.is_file():
            if same_content(source, counterpart):
                self.add(DELETE, source, None, f"identical to the copy in {VERSION_TWO_NAME}")
            elif folder == self.old_root:
                self.add(DELETE, source, None, f"older build, differs from the copy in {VERSION_TWO_NAME}")
            else:
                raise MigrationError(f"{source} differs from {counterpart} and is not stale")
            return
        destination = self.workspace / relative
        earlier_source = self.planned_workspace_files.get(destination)
        if earlier_source is None:
            self.planned_workspace_files[destination] = source
            self.add(MOVE, source, destination, "only in this old folder, kept in next/")
        elif same_content(source, earlier_source):
            self.add(DELETE, source, None, "identical to the file already kept in next/")
        else:
            raise MigrationError(f"{source} and {earlier_source} would both become {destination}")

    def plan_fonts(self):
        version_two_fonts = self.version_two / data_paths.FONTS_PREFIX
        if not version_two_fonts.is_dir():
            return
        for relative in list_files(version_two_fonts):
            source = version_two_fonts / relative
            destination = self.fonts / relative
            if not destination.exists():
                self.add(MOVE, source, destination, f"font only in {VERSION_TWO_NAME}")
            elif same_content(source, destination):
                self.add(DELETE, source, None, "identical to the same font in awn-data/fonts")
            elif relative.name == FONTS_JSON:
                self.plan_fonts_json(source, destination)
            else:
                raise MigrationError(f"{source} and {destination} are both fonts with different bytes")

    def plan_fonts_json(self, source, destination):
        newer = json.loads(source.read_text(encoding="utf-8"))["scripts"]
        older = json.loads(destination.read_text(encoding="utf-8"))["scripts"]
        for script, entry in older.items():
            if newer.get(script) != entry:
                raise MigrationError(f"{source} does not repeat the {script} entry of {destination}")
        self.add(REPLACE, source, destination, "fonts.json that also lists the extra scripts")

    def plan_raw(self):
        version_two_raw = self.version_two / data_paths.RAW_FOLDER
        if not version_two_raw.is_dir():
            return
        for relative in list_files(version_two_raw):
            source = version_two_raw / relative
            destination = self.raw / relative
            if not destination.exists():
                self.add(MOVE, source, destination, f"source download only in {VERSION_TWO_NAME}")
            elif same_content(source, destination):
                self.add(DELETE, source, None, "identical to the same download in awn-data/raw")
            else:
                kept_name = f"{relative.stem}.from-{VERSION_TWO_NAME}{relative.suffix}"
                kept_destination = destination.with_name(kept_name)
                self.reports.append(f"name clash, both kept: raw/{relative} and raw/{relative.parent / kept_name}")
                self.add(MOVE, source, kept_destination, "name clash with different bytes, both kept")

    def plan_folder_removals(self):
        for folder in (self.version_two, self.unbundle, self.next_copy):
            if folder.is_dir():
                self.add(REMOVE_FOLDER, folder, None, "everything in it is placed or a duplicate", 0, 0)
        for entry in sorted(self.old_root.iterdir()):
            if entry.is_dir() and entry.name not in KEPT_IN_PLACE + NEW_FOLDERS:
                self.add(REMOVE_FOLDER, entry, None, "stale folder of the old awn-data", 0, 0)


def ensure_only_empty_directories(folder):
    for directory, _subdirectories, names in os.walk(folder):
        for name in names:
            path = Path(directory) / name
            if not path.is_symlink():
                raise MigrationError(f"{path} is still in {folder}; not removing the folder")


def verify_published(published, pins):
    for name, expected in pins.items():
        matches = [path for path in published.rglob(name) if path.is_file()]
        if not any(file_sha256(path) == expected for path in matches):
            raise MigrationError(f"published/ holds no {name} with the pinned hash")


def apply_operations(operations, published, pins):
    published_verified = False
    for operation in operations:
        if operation.kind in (DELETE, REMOVE_FOLDER) and not published_verified:
            verify_published(published, pins)
            published_verified = True
        if operation.kind == COPY:
            operation.destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(operation.source, operation.destination)
        elif operation.kind in (MOVE, REPLACE):
            if operation.kind == MOVE and operation.destination.exists():
                raise MigrationError(f"{operation.destination} already exists")
            operation.destination.parent.mkdir(parents=True, exist_ok=True)
            os.replace(operation.source, operation.destination)
        elif operation.kind == DELETE:
            operation.source.unlink()
        elif operation.kind == REMOVE_FOLDER:
            ensure_only_empty_directories(operation.source)
            shutil.rmtree(operation.source)


def megabytes(size_bytes):
    return f"{size_bytes / 1e6:,.1f} MB"


def freed_bytes(operations):
    """Disk space the deletes give back: a file with other hard links elsewhere frees nothing."""
    deleted_links = collections.Counter()
    sizes = {}
    link_counts = {}
    for operation in operations:
        if operation.kind != DELETE:
            continue
        status = operation.source.stat()
        key = (status.st_dev, status.st_ino)
        deleted_links[key] += 1
        sizes[key] = status.st_size
        link_counts[key] = status.st_nlink
    return sum(sizes[key] for key, count in deleted_links.items() if count >= link_counts[key])


def group_key(operation):
    """Files in one folder with one reason print as one line; a folder operation prints alone."""
    if operation.source.is_dir():
        return (operation.kind, operation.source, operation.destination, operation.note)
    destination_folder = operation.destination.parent if operation.destination else None
    return (operation.kind, operation.source.parent, destination_folder, operation.note)


def describe(operations, repository):
    groups = collections.OrderedDict()
    for operation in operations:
        groups.setdefault(group_key(operation), []).append(operation)
    lines = []
    for (kind, source, destination, note), members in groups.items():
        if len(members) == 1:
            source, destination = members[0].source, members[0].destination
        size_bytes = sum(member.size_bytes for member in members)
        file_count = sum(member.file_count for member in members)
        source_text = str(source.relative_to(repository))
        destination_text = f" -> {destination.relative_to(repository)}" if destination else ""
        if len(members) > 1:
            source_text += "/*"
            destination_text = destination_text and destination_text + "/"
        count_text = f"{file_count} files, " if file_count != 1 else ""
        lines.append(
            f"{kind.upper():13} {source_text}{destination_text}"
            f"  ({count_text}{megabytes(size_bytes)})  [{note}]"
        )
    return lines


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apply", action="store_true", help="perform the plan; without it nothing changes")
    parser.add_argument("--repository", type=Path, default=data_paths.REPOSITORY, help="checkout that holds the old folders")
    arguments = parser.parse_args()
    repository = arguments.repository.resolve()
    try:
        planner = Planner(repository)
        operations = planner.plan()
        for line in describe(operations, repository):
            print(line)
        for report in planner.reports:
            print("REPORT", report)
        print(f"deleting frees about {megabytes(freed_bytes(operations))} (hard-linked copies free nothing)")
        if not arguments.apply:
            print("dry run: nothing was changed; add --apply to perform the plan")
            return 0
        apply_operations(operations, planner.published, planner.pins)
        print("done: awn-data/ now holds published/, next/, fonts/ and raw/")
        return 0
    except MigrationError as error:
        print(f"ABORTED, nothing after this point was changed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
