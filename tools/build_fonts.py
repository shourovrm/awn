#!/usr/bin/env python3
"""Download the King Fahd Complex Quran fonts from the Quran.com frontend repository.

Source: github.com/quran/quran.com-frontend-next (MIT-licensed code), directory
public/fonts/quran/hafs/, pinned to one commit so the pack is reproducible.
The fonts themselves are the work of the King Fahd Quran Printing Complex; the
repository states no separate font licence.

Output, under awn-data/fonts/:
  qpc-hafs/UthmanicHafs1Ver18.ttf    one Unicode font (KFGQPC Uthmanic Hafs v18)
  v1/p1.ttf .. p604.ttf              QPC V1 glyph fonts, one per page (1405H print)
  v2/p1.ttf .. p604.ttf              QPC V2 glyph fonts, one per page (1421H print)
  v4-tajweed/p1.ttf .. p604.ttf      QPC V4 coloured tajweed, COLRv1 colour glyphs
  indopak/indopak-nastaleeq-waqf-lazim-v4.2.1.ttf
                                     one Unicode font for the IndoPak Nastaleeq text (see tools/build_indopak.py);
                                     "AlQuran IndoPak by QuranWBW" 4.2.1-WL by Ayman Siddiqui. Its name table
                                     says "not for sale, not for modification, not for distribution or not for
                                     development without written notice by quranwbw.com", so the pack is not
                                     publishable until the maintainer has that written notice.
  fonts.json                         file names and sizes per script

Only .ttf files are taken. Android's Typeface loader reads TrueType and OpenType
files and does not read WOFF2. The repository's ot-svg variants of V4 (OpenType
SVG, about 257 MB per theme) are not taken because Android does not render
OpenType SVG glyphs; COLRv1 renders on Android 13 and later only, which the app
must handle with a fallback.

Usage: python3 tools/build_fonts.py
"""

import concurrent.futures
import json
import os
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_com

REPOSITORY = "quran/quran.com-frontend-next"
COMMIT = "aff1a035b09b66f28047b3216edcae4c5c949a49"
RAW_BASE = f"https://raw.githubusercontent.com/{REPOSITORY}/{COMMIT}/"
TREE_URL = f"https://api.github.com/repos/{REPOSITORY}/git/trees/{COMMIT}?recursive=1"
FONT_ROOT = "public/fonts/quran/hafs/"
USER_AGENT = "awn-pack-builder/1.0"
PARALLEL_DOWNLOADS = 4

# script name -> (repository directory, description)
PAGE_SCRIPTS = {
    "v1": ("v1/ttf/", "QPC V1 (1405H print), one glyph font per page"),
    "v2": ("v2/ttf/", "QPC V2 (1421H print), one glyph font per page"),
    "v4-tajweed": ("v4/colrv1/ttf/", "QPC V4 tajweed (1441H print), COLRv1 colour glyphs, one font per page"),
}
UNICODE_FONT_PATH = "uthmanic_hafs/UthmanicHafs1Ver18.ttf"
INDOPAK_FONT_PATH = "nastaleeq/indopak/indopak-nastaleeq-waqf-lazim-v4.2.1.ttf"


def http_get(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def list_repository_files():
    """Map repository path to size in bytes for every blob in the pinned commit."""
    tree = json.loads(http_get(TREE_URL))
    if tree.get("truncated"):
        raise RuntimeError("GitHub truncated the tree listing")
    return {entry["path"]: entry["size"] for entry in tree["tree"] if entry["type"] == "blob"}


def download(repository_path, destination, expected_size):
    if os.path.exists(destination) and os.path.getsize(destination) == expected_size:
        return
    data = http_get(RAW_BASE + repository_path)
    if len(data) != expected_size:
        raise RuntimeError(f"{repository_path}: got {len(data)} bytes, tree says {expected_size}")
    os.makedirs(os.path.dirname(destination), exist_ok=True)
    temporary = destination + ".part"
    with open(temporary, "wb") as handle:
        handle.write(data)
    os.replace(temporary, destination)


def plan_downloads(repository_files):
    """Return {script: (description, [(repository_path, file_name, size)])}."""
    plan = {}
    unicode_path = FONT_ROOT + UNICODE_FONT_PATH
    plan["qpc-hafs"] = ("KFGQPC Uthmanic Hafs v18, one Unicode font",
                        [(unicode_path, os.path.basename(unicode_path), repository_files[unicode_path])])
    indopak_path = FONT_ROOT + INDOPAK_FONT_PATH
    plan["indopak"] = ("AlQuran IndoPak by QuranWBW 4.2.1-WL (Nastaleeq), one Unicode font",
                       [(indopak_path, os.path.basename(indopak_path), repository_files[indopak_path])])
    for script, (directory, description) in PAGE_SCRIPTS.items():
        entries = []
        for page in range(1, quran_com.PAGE_COUNT + 1):
            repository_path = f"{FONT_ROOT}{directory}p{page}.ttf"
            entries.append((repository_path, f"p{page}.ttf", repository_files[repository_path]))
        plan[script] = (description, entries)
    return plan


def main():
    fonts_root = os.path.join(quran_com.DATA_ROOT, "fonts")
    plan = plan_downloads(list_repository_files())
    manifest = {"source": f"https://github.com/{REPOSITORY}/tree/{COMMIT}/{FONT_ROOT}", "scripts": {}}
    with concurrent.futures.ThreadPoolExecutor(max_workers=PARALLEL_DOWNLOADS) as pool:
        for script, (description, entries) in plan.items():
            futures = [
                pool.submit(download, repository_path, os.path.join(fonts_root, script, file_name), size)
                for repository_path, file_name, size in entries
            ]
            for future in futures:
                future.result()  # re-raises any download failure
            files = [{"name": file_name, "bytes": size} for _, file_name, size in entries]
            manifest["scripts"][script] = {
                "description": description,
                "repository_directory": os.path.dirname(entries[0][0]) + "/",
                "file_count": len(files),
                "total_bytes": sum(entry["bytes"] for entry in files),
                "files": files,
            }
            print(f"{script}: {len(files)} files, {manifest['scripts'][script]['total_bytes']:,} bytes")
    fonts_json_path = os.path.join(fonts_root, "fonts.json")
    if os.path.exists(fonts_json_path):
        os.remove(fonts_json_path)  # a hard-linked copy of an older data folder must not be written through
    with open(fonts_json_path, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2)
    for script in plan:
        actual = sum(os.path.getsize(os.path.join(fonts_root, script, name))
                     for name in os.listdir(os.path.join(fonts_root, script)))
        print(f"on disk {script}: {actual:,} bytes")


if __name__ == "__main__":
    main()
