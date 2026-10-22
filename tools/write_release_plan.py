#!/usr/bin/env python3
"""Write awn-data/RELEASE-PLAN.md: what to upload under the next data tag, how big it is, what is held back.

Reads the manifests written by build_manifests.py (all packs listed), so run that first without flags.
Nothing is uploaded: the file only contains the `gh release` commands for the maintainer.

A pack is held back when
  - it is a translation whose publishable value is "no", or bn_quantum (permission is handled by the maintainer);
  - it is in HOLD_REASONS below.
Everything else is "publish now". The held packs are not in the commands; the plan names the
build_manifests.py call that leaves them out of the manifests as well.

Usage: python3 tools/write_release_plan.py [tag]      (default tag: data-v2)
"""

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import data_paths
import quran_com

DATA_ROOT = quran_com.DATA_ROOT
REPOSITORY = "shourovrm/awn"
HOLD_REASONS = {
    "indopak": "font licence: its name table forbids distribution without written notice from quranwbw.com",
    "indopak-layouts": "useless without the IndoPak font; layouts come from the same Quran.com source",
}


def load(name, key):
    with open(os.path.join(DATA_ROOT, "manifests", name), encoding="utf-8") as handle:
        return json.load(handle)[key]


def path_from_workspace(manifest_file):
    return os.path.relpath(data_paths.pack_file_path(manifest_file), data_paths.workspace())


def megabytes(size):
    return f"{size / 1e6:,.1f} MB"


def hold_reason(entry):
    if entry["id"] in HOLD_REASONS:
        return HOLD_REASONS[entry["id"]]
    if entry["id"] == "bn_quantum":
        return "permission from Quantum Foundation is handled by the maintainer"
    if entry.get("publishable") == "no":
        return f"no public permission found ({entry.get('rights_holder')})"
    return None


def main():
    tag = sys.argv[1] if len(sys.argv) > 1 else "data-v2"
    packs = load("scripts.json", "packs") + load("translations.json", "packs")
    reciters = [entry for entry in load("reciters.json", "reciters") if entry["file"]]
    published, held = [], []
    for entry in packs + reciters:
        reason = hold_reason(entry)
        (held if reason else published).append((entry, reason))
    names = [entry["file"].rsplit("/", 1)[-1] for entry, _ in published + held]
    if len(set(names)) != len(names):
        raise SystemExit("two packs share a file name; release assets are flat")

    lines = [f"# Release plan for {tag}", "",
             f"Generated from awn-data/next/manifests. Nothing has been uploaded and no release exists. The app builds "
             f"download URLs as `https://github.com/{REPOSITORY}/releases/download/<tag>/<file name>` where the file name is the "
             "last part of the manifest `file` path (ManifestCatalog.DATA_RELEASE_URL, currently `data-v1`), so the "
             f"app must point at `{tag}` before the new packs can be installed.", ""]
    lines += ["## Publish now", "", "| asset | bytes | size | pack id | note |", "|---|---|---|---|---|"]
    total = 0
    for entry, _ in published:
        total += entry["bytes"]
        note = entry.get("publishable", "")
        note = f"publishable: {note}" if note else ""
        lines.append(f"| {entry['file'].rsplit('/', 1)[-1]} | {entry['bytes']:,} | {megabytes(entry['bytes'])} | {entry['id']} | {note} |")
    manifest_bytes = sum(os.path.getsize(os.path.join(DATA_ROOT, "manifests", n)) for n in ("scripts.json", "translations.json", "reciters.json"))
    lines += ["", f"Total of the packs: {megabytes(total)}, plus three manifests of {manifest_bytes:,} bytes.", ""]
    lines += ["## Held back", "", "| asset | size | pack id | reason |", "|---|---|---|---|"]
    for entry, reason in held:
        lines.append(f"| {entry['file'].rsplit('/', 1)[-1]} | {megabytes(entry['bytes'])} | {entry['id']} | {reason} |")
    excluded_ids = ",".join(entry["id"] for entry, _ in held)
    # The commands run inside the workspace, but the font zips sit in awn-data/fonts beside it.
    pack_paths = " ".join(path_from_workspace(entry["file"]) for entry, _ in published)
    terms_paths = " ".join(path_from_workspace(entry["terms_file"]) for entry, _ in published if entry.get("terms_file"))
    workspace = data_paths.workspace()
    repository = data_paths.REPOSITORY
    lines += ["", "## Commands", "",
              "The maintainer runs these; the first block writes manifests without the held packs, the second creates the release.",
              "", "```sh", f"cd {repository}",
              f"python3 -I tools/build_manifests.py --exclude={excluded_ids}",
              "python3 -I tools/verify_packs.py", "```", "",
              "```sh", f"cd {workspace}",
              f"gh release create {tag} --repo {REPOSITORY} --prerelease --title \"Data packs {tag}\" "
              f"--notes \"Quran text, fonts, translations and word timings. Checksums are in manifests/*.json.\" "
              "manifests/scripts.json manifests/translations.json manifests/reciters.json",
              f"gh release upload {tag} --repo {REPOSITORY} {pack_paths}",
              f"gh release upload {tag} --repo {REPOSITORY} {terms_paths}", "```", "",
              "A held pack is added later by rebuilding the manifests without it in `--exclude` and running "
              "`gh release upload` for its file and its TERMS.md. Keep the manifests and the uploaded assets in step: "
              "a manifest entry without an asset fails at install time.",
              "",
              "bn_zakaria and en_abdul_haleem are held here because no permission was found, but both are already "
              "bundled in the APK from the first release; whether to keep them bundled is the maintainer's decision.", ""]
    path = os.path.join(DATA_ROOT, "RELEASE-PLAN.md")
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines))
    print("wrote", path, megabytes(total), "to publish,", len(held), "held")


if __name__ == "__main__":
    main()
