#!/usr/bin/env python3
"""Write awn-data/manifests/{scripts,translations,reciters}.json from the built packs.

Every pack entry carries format_version, sha256, bytes, credit and source_url.
sha256 and bytes describe the file named in "file", relative to awn-data/.

Font scripts that have one file per page are packed into awn-data/fonts/<script>.zip
(deflate, fixed member timestamps, members in page order) so a script is one
download with one checksum. The Unicode font is a single file and is listed as is.

A reciter without a timing pack (Raad al-Kurdi) has timing_pack null and
no sha256 or bytes: there is no pack to verify.

Usage: python3 tools/build_manifests.py
"""

import hashlib
import json
import os
import sqlite3
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import quran_com

FORMAT_VERSION = 1
DATA_ROOT = quran_com.DATA_ROOT
FIXED_ZIP_TIMESTAMP = (2026, 1, 1, 0, 0, 0)
QURAN_COM_ATTRIBUTION = "Quran.com / Quran Foundation, API v4"
QURAN_COM_URL = "https://api.quran.com/api/v4"
FONT_SOURCE_URL = ("https://github.com/quran/quran.com-frontend-next/tree/"
                   "aff1a035b09b66f28047b3216edcae4c5c949a49/public/fonts/quran/hafs")

FONT_CREDIT = ("King Fahd Quran Printing Complex (Madinah Mushaf fonts), "
               "as hosted by the Quran.com frontend repository (quran/quran.com-frontend-next)")

# Reciter facts from docs/plan.md, Milestone 3. QuranicAudio surah files:
# https://download.quranicaudio.com/quran/<quranicaudio_path>/<SSS>.mp3
# EveryAyah ayah files: https://mirrors.quranicaudio.com/everyayah/<folder>/<SSS><AAA>.mp3
RECITERS = [
    {"id": "alafasy", "name": "Mishari Rashid al-Afasy", "quranicaudio_id": 5,
     "quranicaudio_path": "mishaari_raashid_al_3afaasee/", "everyayah_folder": "Alafasy_128kbps",
     "timing_pack": "alafasy", "word_by_word": True},
    {"id": "husary", "name": "Mahmoud Khalil Al-Husary (Murattal)", "quranicaudio_id": 16,
     "quranicaudio_path": "mahmood_khaleel_al-husaree/", "everyayah_folder": "Husary_128kbps",
     "timing_pack": "husary", "word_by_word": True},
    {"id": "husary_muallim", "name": "Mahmoud Khalil Al-Husary (Muallim)", "quranicaudio_id": 141,
     "quranicaudio_path": "husary_muallim/", "everyayah_folder": "Husary_Muallim_128kbps",
     "timing_pack": "husary_muallim", "word_by_word": True},
    {"id": "minshawi", "name": "Mohamed Siddiq al-Minshawi (Murattal)", "quranicaudio_id": 6,
     "quranicaudio_path": "muhammad_siddeeq_al-minshaawee/", "everyayah_folder": "Minshawy_Murattal_128kbps",
     "timing_pack": "minshawi", "word_by_word": True},
    {"id": "basfar", "name": "Abdullah Basfar", "quranicaudio_id": 2,
     "quranicaudio_path": "abdullaah_basfar/", "everyayah_folder": "Abdullah_Basfar_192kbps",
     "alternative_everyayah_folders": [{"folder": "Abdullah_Basfar_64kbps", "base_url": "https://everyayah.com/data"}],
     "timing_pack": "basfar", "word_by_word": False},
    {"id": "raad_al_kurdi", "name": "Raad Mohammad al-Kurdi", "quranicaudio_id": 168,
     "quranicaudio_path": "raad_mohammad_al_kurdi/mp3/", "everyayah_folder": None,
     "timing_pack": None, "word_by_word": False},
]
SURAH_URL_PATTERN = "https://download.quranicaudio.com/quran/{quranicaudio_path}{SSS}.mp3"
AYAH_URL_PATTERN = "https://mirrors.quranicaudio.com/everyayah/{everyayah_folder}/{SSS}{AAA}.mp3"

TRANSLATION_PACKS = [
    # file stem, kind, language, display name, Quran.com resource id, credit, source url
    ("en_saheeh", "ayah", "en", "Saheeh International", 20),
    ("en_abdul_haleem", "ayah", "en", "M.A.S. Abdel Haleem", 85),
    ("en_transliteration", "ayah", "en", "English transliteration", 57),
    ("bn_zakaria", "ayah", "bn", "Dr. Abu Bakr Muhammad Zakaria", 213),
    ("wbw_en", "word", "en", "Word by word, English", None),
    ("wbw_bn", "word", "bn", "Word by word, Bangla", None),
]


def file_checksum_and_size(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest(), os.path.getsize(path)


def relative_path(path):
    return os.path.relpath(path, DATA_ROOT)


def pack_entry(path, **fields):
    sha256, size = file_checksum_and_size(path)
    entry = {"format_version": FORMAT_VERSION, "file": relative_path(path), "sha256": sha256, "bytes": size}
    entry.update(fields)
    return entry


def zip_font_script(script):
    directory = os.path.join(DATA_ROOT, "fonts", script)
    archive_path = directory + ".zip"
    names = [f"p{page}.ttf" for page in range(1, quran_com.PAGE_COUNT + 1)]
    with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in names:
            member = zipfile.ZipInfo(name, FIXED_ZIP_TIMESTAMP)
            member.compress_type = zipfile.ZIP_DEFLATED
            with open(os.path.join(directory, name), "rb") as handle:
                archive.writestr(member, handle.read(), compresslevel=9)
    return archive_path


def write_manifest(name, key, entries):
    path = os.path.join(DATA_ROOT, "manifests", name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump({"format_version": FORMAT_VERSION, key: entries}, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print("wrote", path)


def build_scripts_manifest():
    quran_path = os.path.join(DATA_ROOT, "quran", "quran.sqlite")
    entries = [pack_entry(
        quran_path, id="quran-text", kind="text",
        name="Quran text, word table, page and line layout (V1 and V2)",
        credit=f"{QURAN_COM_ATTRIBUTION}; Uthmani text and King Fahd Quran Printing Complex page layouts",
        source_url=QURAN_COM_URL,
    )]
    unicode_font = os.path.join(DATA_ROOT, "fonts", "qpc-hafs", "UthmanicHafs1Ver18.ttf")
    entries.append(pack_entry(
        unicode_font, id="qpc-hafs", kind="font", name="QPC Hafs (Unicode)", glyph_font_per_page=False,
        credit=FONT_CREDIT, source_url=FONT_SOURCE_URL + "/uthmanic_hafs"))
    font_scripts = [
        ("v1", "QPC V1 (1405H print)", "v1/ttf"),
        ("v2", "QPC V2 (1421H print)", "v2/ttf"),
        ("v4-tajweed", "QPC V4 tajweed (COLRv1 colour glyphs)", "v4/colrv1/ttf"),
    ]
    for script, name, repository_directory in font_scripts:
        archive_path = zip_font_script(script)
        entries.append(pack_entry(
            archive_path, id=script, kind="font", name=name, glyph_font_per_page=True,
            archive_format="zip", member_pattern="p{page}.ttf", member_count=quran_com.PAGE_COUNT,
            credit=FONT_CREDIT, source_url=f"{FONT_SOURCE_URL}/{repository_directory}"))
    write_manifest("scripts.json", "packs", entries)


def build_translations_manifest():
    catalogue = {entry["id"]: entry for entry in quran_com.fetch("/resources/translations")["translations"]}
    entries = []
    for file_stem, kind, language, name, resource_id in TRANSLATION_PACKS:
        path = os.path.join(DATA_ROOT, "translations", file_stem + ".sqlite")
        if resource_id:
            author = catalogue[resource_id]["author_name"]
            credit = f"{author}, via {QURAN_COM_ATTRIBUTION} (resource {resource_id})"
        else:
            credit = f"{QURAN_COM_ATTRIBUTION}, word-by-word translation"
        entries.append(pack_entry(
            path, id=file_stem, kind=kind, language=language, name=name,
            quran_com_resource_id=resource_id, credit=credit, source_url=QURAN_COM_URL,
            terms_file=relative_path(path[: -len(".sqlite")] + ".TERMS.md")))
    quantum_path = os.path.join(DATA_ROOT, "translations", "bn_quantum.sqlite")
    if os.path.exists(quantum_path):
        entries.append(pack_entry(
            quantum_path, id="bn_quantum", kind="ayah", language="bn",
            name="Quantum Foundation Bangla", quran_com_resource_id=None,
            credit="Quantum Foundation (alquran.org.bd); redistribution permission is handled by the maintainer",
            source_url="https://alquran.org.bd", terms_file=None))
    write_manifest("translations.json", "packs", entries)


def read_timing_info(path):
    with sqlite3.connect(path) as connection:
        return dict(connection.execute("SELECT key, value FROM pack_info").fetchall())


def timing_audio_folder(path):
    with sqlite3.connect(path) as connection:
        url = connection.execute("SELECT url FROM ayah_audio WHERE surah = 1 AND ayah = 1").fetchone()[0]
    return url.rsplit("/", 1)[0]


def build_reciters_manifest():
    entries = []
    for reciter in RECITERS:
        entry = {
            "id": reciter["id"], "name": reciter["name"],
            "quranicaudio_id": reciter["quranicaudio_id"],
            "quranicaudio_path": reciter["quranicaudio_path"],
            "surah_url_pattern": SURAH_URL_PATTERN.replace("{quranicaudio_path}", reciter["quranicaudio_path"]),
            "everyayah_folder": reciter["everyayah_folder"],
            "ayah_url_pattern": (AYAH_URL_PATTERN.replace("{everyayah_folder}", reciter["everyayah_folder"])
                                 if reciter["everyayah_folder"] else None),
            "fallback_domain_suffix": {"from": ".quranicaudio.com", "to": ".quranicaudio.org"},
            "word_by_word": reciter["word_by_word"],
            "timing_pack": reciter["timing_pack"],
        }
        if "alternative_everyayah_folders" in reciter:
            entry["alternative_everyayah_folders"] = reciter["alternative_everyayah_folders"]
        if reciter["timing_pack"]:
            path = os.path.join(DATA_ROOT, "timings", reciter["timing_pack"] + ".sqlite")
            info = read_timing_info(path)
            entry.update(pack_entry(
                path, quran_com_recitation_id=(None if info["quran_com_recitation_id"] == "none"
                                               else int(info["quran_com_recitation_id"])),
                timing_granularity=info["granularity"],
                timing_audio_folder=timing_audio_folder(path),
                credit=(f"Segment timings from {QURAN_COM_ATTRIBUTION}, recitation {info['quran_com_recitation_id']}"
                        if info["granularity"] == "word" else "Ayah file list; audio from EveryAyah (everyayah.com)"),
                source_url=QURAN_COM_URL))
        else:
            entry.update({"format_version": FORMAT_VERSION, "file": None, "sha256": None, "bytes": None,
                          "credit": "Audio from QuranicAudio (quranicaudio.com); no timings exist, surah playback only",
                          "source_url": "https://quranicaudio.com"})
        entries.append(entry)
    write_manifest("reciters.json", "reciters", entries)


def main():
    build_scripts_manifest()
    build_translations_manifest()
    build_reciters_manifest()


if __name__ == "__main__":
    main()
