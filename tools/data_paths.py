"""Where the local data folders are. Every tool in tools/ asks this module; none spells a path.

One gitignored folder, awn-data/, sits in the repository root:

    awn-data/published/  the files the app pins today (core/data/bundled-packs.sha256 and test-packs.sha256)
    awn-data/next/       the workspace for the next data release: quran, translations, timings, manifests
    awn-data/fonts/      font prints, shared by the published and the next release
    awn-data/raw/        source downloads (quran.com API responses, tanzil texts)

AWN_DATA_ROOT replaces awn-data/ as a whole (a git worktree has no awn-data of its own, so point it at
the main checkout's); AWN_DATA_DIR, the name the Gradle build uses for the same folder, is read when
AWN_DATA_ROOT is not set, so one variable serves a worktree build and its Python checks. AWN_DATA names the workspace alone and wins over AWN_DATA_ROOT for it, so commands
written before the layout existed keep working.
"""

import os
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parent.parent

# Pack entries in a manifest name their file relative to the data root, and the font prints are
# listed there as "fonts/<name>" even though they live beside the workspace, not inside it.
FONTS_PREFIX = "fonts"
PUBLISHED_FOLDER = "published"
WORKSPACE_FOLDER = "next"
RAW_FOLDER = "raw"
DATA_FOLDER = "awn-data"


def data_root():
    configured = os.environ.get("AWN_DATA_ROOT") or os.environ.get("AWN_DATA_DIR")
    if configured:
        return Path(configured)
    return REPOSITORY / DATA_FOLDER


def published():
    return data_root() / PUBLISHED_FOLDER


def workspace():
    configured = os.environ.get("AWN_DATA")
    if configured:
        return Path(configured)
    return data_root() / WORKSPACE_FOLDER


def fonts():
    return data_root() / FONTS_PREFIX


def raw():
    return data_root() / RAW_FOLDER


def pack_file_path(manifest_file):
    """The path on disk of a manifest entry's "file" value."""
    parts = Path(manifest_file).parts
    if parts[0] == FONTS_PREFIX:
        return fonts().joinpath(*parts[1:])
    return workspace() / manifest_file


def manifest_file_name(path):
    """The "file" value a manifest entry uses for a pack at this path; the inverse of pack_file_path."""
    path = Path(path)
    if path.is_relative_to(fonts()):
        return str(Path(FONTS_PREFIX) / path.relative_to(fonts()))
    return str(path.relative_to(workspace()))
