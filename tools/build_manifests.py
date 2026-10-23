#!/usr/bin/env python3
"""Write awn-data/manifests/{scripts,translations,reciters}.json from the built packs.

Every pack entry carries format_version, sha256, bytes, credit and source_url.
sha256 and bytes describe the file named in "file", relative to the data root; font files read "fonts/..." although awn-data/fonts sits beside the workspace.

The manifests keep `format_version: 1`. The second catalogue adds only optional fields, which a reader of
the first catalogue ignores:
  min_sdk            (scripts) lowest Android API level that can show the print; 33 for v4-tajweed (COLRv1)
  requires           (scripts) ids of the packs that must be installed with this one
  download_url       (scripts) where the app downloads the file from when it is not the data release; the
                     sha256 and bytes still describe that file and the app checks both
  script_id          (scripts) the app's ScriptId name that this pack makes available
  layouts            (scripts) page counts and lines per page of a text pack with several layouts
  installed_bytes    (font zips) unpacked size, for the free-space check; a single file or SQLite pack installs at `bytes`
  language_name      (translations) the language in English, for grouping in the install list
  content            (translations) "translation", "transliteration", "word_translation" or "word_transliteration"
  rights_holder, terms_known, publishable
                     (translations) what is known about the copyright; docs/packs.md has the table
  style, riwaya      (reciters) "murattal", "mujawwad" or "muallim"; always "hafs"
  audio_bytes        (reciters) summed Content-Length of the 114 QuranicAudio surah files, measured by HEAD
  ayah_audio_bytes   (reciters) estimate for the EveryAyah ayah files; `estimated` is true when it is present
  ayah_base_url      (reciters) the host folder that serves the ayah files, when it is not the default mirror

Font scripts that have one file per page are packed into awn-data/fonts/<script>.zip (data_paths.fonts())
(deflate, fixed member timestamps, members in page order) so a script is one
download with one checksum. A Unicode font is a single file and is listed as is.

A reciter without a timing pack has timing_pack null and no sha256 or bytes: there is no pack to verify.

--publishable=yes,conditional keeps only translation entries whose publishable value is listed; use it to
write the manifests that go into a release. Without the flag every pack is listed, for testing.

--exclude=id,id leaves the named entries out of every manifest (a font script, a translation, a reciter).

--add-indopak-to=PATH only puts the IndoPak entries into the scripts.json at PATH (the published one, which
lists fewer packs than the workspace) and does nothing else.

--add-learn-audio=PATH only puts the Learn sounds entry (id learn-qaida-audio, kind learn-audio) into the
scripts.json at PATH, from the zip that tools/build_learn_audio_pack.py wrote to awn-data/next/learn/. The
entry has member_count (clips) and installed_bytes (unpacked size). It does nothing else.

Usage: python3 tools/build_manifests.py [--publishable=yes,conditional] [--exclude=indopak-15,indopak-16,indopak-layouts]
"""

import hashlib
import json
import os
import sqlite3
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import data_paths
import pack_catalogue
import quran_com
import reciter_catalogue

FORMAT_VERSION = 1
DATA_ROOT = quran_com.DATA_ROOT
FONTS_ROOT = quran_com.FONTS_ROOT
FIXED_ZIP_TIMESTAMP = (2026, 1, 1, 0, 0, 0)
QURAN_COM_ATTRIBUTION = "Quran.com / Quran Foundation, API v4"
QURAN_COM_URL = "https://api.quran.com/api/v4"
FONT_SOURCE_URL = ("https://github.com/quran/quran.com-frontend-next/tree/"
                   "aff1a035b09b66f28047b3216edcae4c5c949a49/public/fonts/quran/hafs")

FONT_CREDIT = ("King Fahd Quran Printing Complex (Madinah Mushaf fonts), "
               "as hosted by the Quran.com frontend repository (quran/quran.com-frontend-next)")
INDOPAK_FONT_CREDIT = ("AlQuran IndoPak by QuranWBW 4.2.1-WL, Ayman Siddiqui (quranwbw.com), "
                       "downloaded from the Quran.com frontend repository (quran/quran.com-frontend-next)")
# The font's name table forbids distribution without written notice from quranwbw.com, so Awn does not
# host it: the app downloads it from the Quran.com repository, pinned to the commit that build_fonts.py uses.
INDOPAK_FONT_DOWNLOAD_URL = (
    "https://raw.githubusercontent.com/quran/quran.com-frontend-next/aff1a035b09b66f28047b3216edcae4c5c949a49/"
    "public/fonts/quran/hafs/nastaleeq/indopak/indopak-nastaleeq-waqf-lazim-v4.2.1.ttf")
# pack id, name, lines per page, pages. Only the 15-line print is offered.
INDOPAK_PRINTS = [
    ("indopak-15", "IndoPak", 15, 610),
]
# The layouts the file holds, offered or not: id, pages, lines per page.
INDOPAK_FILE_LAYOUTS = [("15", 610, 15), ("16", 548, 16)]
# "indopak-16" is listed so that a manifest written by an older build loses its entry.
INDOPAK_PACK_IDS = {"indopak-layouts", "indopak-15", "indopak-16"}
INDOPAK_LAYOUT_CREDIT = (f"{QURAN_COM_ATTRIBUTION} (QDC API, mushaf 6 and 7): IndoPak 15-line layout "
                         "(Qudratullah, Lahore, 610 pages) and IndoPak 16-line layout (Taj Company, 548 pages)")
SURAH_URL_PATTERN = "https://download.quranicaudio.com/quran/{quranicaudio_path}{SSS}.mp3"

# Android 13 (API 33) is the first release that draws COLRv1 colour glyphs.
V4_TAJWEED_MIN_SDK = 33


def file_checksum_and_size(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest(), os.path.getsize(path)


def relative_path(path):
    return data_paths.manifest_file_name(path)


def pack_entry(path, **fields):
    sha256, size = file_checksum_and_size(path)
    entry = {"format_version": FORMAT_VERSION, "file": relative_path(path), "sha256": sha256, "bytes": size}
    entry.update(fields)
    return entry


def installed_font_bytes(script):
    """Unpacked size of a per-page font script: the sum of its member files."""
    directory = os.path.join(FONTS_ROOT, script)
    return sum(os.path.getsize(os.path.join(directory, f"p{page}.ttf")) for page in range(1, quran_com.PAGE_COUNT + 1))


def zip_font_script(script):
    directory = os.path.join(FONTS_ROOT, script)
    archive_path = directory + ".zip"
    temporary_path = archive_path + ".part"
    names = [f"p{page}.ttf" for page in range(1, quran_com.PAGE_COUNT + 1)]
    with zipfile.ZipFile(temporary_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in names:
            member = zipfile.ZipInfo(name, FIXED_ZIP_TIMESTAMP)
            member.compress_type = zipfile.ZIP_DEFLATED
            with open(os.path.join(directory, name), "rb") as handle:
                archive.writestr(member, handle.read(), compresslevel=9)
    # Replace by a new file: an older data folder may hold a hard link to the old archive.
    os.replace(temporary_path, archive_path)
    return archive_path


EXCLUDED_IDS = set()


def write_manifest(name, key, entries):
    entries = [entry for entry in entries if entry["id"] not in EXCLUDED_IDS]
    path = os.path.join(DATA_ROOT, "manifests", name)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    temporary_path = path + ".part"
    with open(temporary_path, "w", encoding="utf-8") as handle:
        json.dump({"format_version": FORMAT_VERSION, key: entries}, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    os.replace(temporary_path, path)
    print("wrote", path, f"({len(entries)} entries)")


def indopak_entries():
    """The IndoPak layout pack and the font entry of the 15-line print.

    The font entry requires the layout pack. The font is downloaded from `download_url`, never from
    our release.
    """
    entries = [pack_entry(
        os.path.join(DATA_ROOT, "quran", "indopak.sqlite"), id="indopak-layouts", kind="text",
        name="IndoPak layouts", credit=INDOPAK_LAYOUT_CREDIT,
        source_url=f"{QURAN_COM_URL.replace('/v4', '/qdc')}/verses/by_page",
        requires=["quran-text"],
        layouts=[{"id": layout_id, "pages": pages, "lines_per_page": lines}
                 for layout_id, pages, lines in INDOPAK_FILE_LAYOUTS])]
    font_path = os.path.join(FONTS_ROOT, "indopak", "indopak-nastaleeq-waqf-lazim-v4.2.1.ttf")
    for pack_id, name, lines, pages in INDOPAK_PRINTS:
        entries.append(pack_entry(
            font_path, id=pack_id, kind="font", name=name, glyph_font_per_page=False,
            script_id=pack_id, requires=["indopak-layouts"], lines_per_page=lines, pages=pages,
            credit=INDOPAK_FONT_CREDIT, source_url=FONT_SOURCE_URL + "/nastaleeq/indopak",
            download_url=INDOPAK_FONT_DOWNLOAD_URL))
    return entries


def add_indopak_to_manifest(path):
    """Puts the IndoPak entries into an existing scripts.json (the one the app bundles), leaving the rest as is."""
    with open(path, encoding="utf-8") as handle:
        manifest = json.load(handle)
    replaced_ids = INDOPAK_PACK_IDS | {"indopak"}
    kept = [entry for entry in manifest["packs"] if entry["id"] not in replaced_ids]
    manifest["packs"] = kept + indopak_entries()
    temporary_path = path + ".part"
    with open(temporary_path, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    os.replace(temporary_path, path)
    print("wrote", path, f"({len(manifest['packs'])} entries)")


LEARN_AUDIO_ID = "learn-qaida-audio"
LEARN_AUDIO_CREDIT = ("Islamic Home Schooling Academy of New Zealand, Noorani Qaida lessons (CC BY); "
                      "recordings are screen captures of the Al-Furqan Noorani Qaida program")
LEARN_AUDIO_SOURCE_URL = "https://www.youtube.com/channel/UCX766Oi2anGGIkIgMJXu9ng"


def learn_audio_entry(archive_path=None):
    """The manifest entry of the Learn sounds zip."""
    archive_path = archive_path or os.path.join(data_paths.workspace(), "learn", "qaida-audio.zip")
    with zipfile.ZipFile(archive_path) as archive:
        members = archive.infolist()
        unpacked_bytes = sum(member.file_size for member in members)
    return pack_entry(
        archive_path, id=LEARN_AUDIO_ID, kind="learn-audio", name="Lesson sounds",
        archive_format="zip", member_pattern="{unit}/{clip}.opus", member_count=len(members),
        installed_bytes=unpacked_bytes, credit=LEARN_AUDIO_CREDIT, source_url=LEARN_AUDIO_SOURCE_URL)


def add_learn_audio_to_manifest(path, archive_path=None):
    """Puts the Learn sounds entry into an existing scripts.json, replacing an older one."""
    with open(path, encoding="utf-8") as handle:
        manifest = json.load(handle)
    kept = [entry for entry in manifest["packs"] if entry["id"] != LEARN_AUDIO_ID]
    manifest["packs"] = kept + [learn_audio_entry(archive_path)]
    temporary_path = path + ".part"
    with open(temporary_path, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    os.replace(temporary_path, path)
    print("wrote", path, f"({len(manifest['packs'])} entries)")


def build_scripts_manifest():
    entries = [pack_entry(
        os.path.join(DATA_ROOT, "quran", "quran.sqlite"), id="quran-text", kind="text",
        name="Quran text, word table, page and line layout (V1 and V2)",
        credit=f"{QURAN_COM_ATTRIBUTION}; Uthmani text and King Fahd Quran Printing Complex page layouts",
        source_url=QURAN_COM_URL,
    )]
    unicode_font = os.path.join(FONTS_ROOT, "qpc-hafs", "UthmanicHafs1Ver18.ttf")
    entries.append(pack_entry(
        unicode_font, id="qpc-hafs", kind="font", name="QPC Hafs (Unicode)", glyph_font_per_page=False,
        credit=FONT_CREDIT, source_url=FONT_SOURCE_URL + "/uthmanic_hafs"))
    font_scripts = [
        ("v1", "QPC V1 (1405H print)", "v1/ttf", {}),
        ("v2", "QPC V2 (1421H print)", "v2/ttf", {}),
        ("v4-tajweed", "QPC V4 tajweed (COLRv1 colour glyphs)", "v4/colrv1/ttf", {"min_sdk": V4_TAJWEED_MIN_SDK}),
    ]
    for script, name, repository_directory, extra_fields in font_scripts:
        archive_path = zip_font_script(script)
        entries.append(pack_entry(
            archive_path, id=script, kind="font", name=name, glyph_font_per_page=True,
            archive_format="zip", member_pattern="p{page}.ttf", member_count=quran_com.PAGE_COUNT,
            installed_bytes=installed_font_bytes(script),
            credit=FONT_CREDIT, source_url=f"{FONT_SOURCE_URL}/{repository_directory}", **extra_fields))
    entries.extend(indopak_entries())
    entries.append(pack_entry(
        os.path.join(DATA_ROOT, "quran", "tajweed.sqlite"), id="tajweed-text", kind="text",
        name="Tajweed-coloured Uthmani text (inline rule markup)",
        credit=f"{QURAN_COM_ATTRIBUTION}, words[].text_uthmani_tajweed", source_url=QURAN_COM_URL,
        requires=["quran-text"]))
    write_manifest("scripts.json", "packs", entries)


def translation_entry(catalogue, pack):
    path = os.path.join(DATA_ROOT, "translations", pack["id"] + ".sqlite")
    resource = catalogue[pack["resource_id"]]
    is_transliteration = pack in pack_catalogue.TRANSLITERATION_PACKS
    return pack_entry(
        path, id=pack["id"], kind="transliteration" if is_transliteration else "ayah",
        language=pack["language"], language_name=pack["language_name"],
        content="transliteration" if is_transliteration else "translation", name=pack["name"],
        quran_com_resource_id=pack["resource_id"],
        credit=f"{resource['author_name']}, via {QURAN_COM_ATTRIBUTION} (resource {pack['resource_id']})",
        source_url=QURAN_COM_URL, terms_file=relative_path(path[: -len(".sqlite")] + ".TERMS.md"),
        rights_holder=pack["rights_holder"], terms_known=pack["terms_known"], publishable=pack["publishable"])


def transliteration_words_entry():
    """The per-word transliteration that used to be a column of quran.sqlite (build_transliteration_pack.py)."""
    path = os.path.join(DATA_ROOT, "translations", "en_transliteration_words.sqlite")
    return pack_entry(
        path, id="en_transliteration_words", kind="word_transliteration", language="en",
        language_name="English (Latin letters)", content="word_transliteration",
        name="Transliteration, word by word", quran_com_resource_id=None,
        credit=f"{QURAN_COM_ATTRIBUTION}, word transliteration", source_url=QURAN_COM_URL,
        terms_file=relative_path(path[: -len(".sqlite")] + ".TERMS.md"))


def build_translations_manifest(allowed_publishable):
    catalogue = {entry["id"]: entry for entry in quran_com.fetch("/resources/translations")["translations"]}
    # First, so that the list of transliterations offers the pack the Transliteration layer reads.
    entries = [transliteration_words_entry()]
    for pack in pack_catalogue.TRANSLATIONS_AND_TRANSLITERATIONS:
        if allowed_publishable is None or pack["publishable"] in allowed_publishable:
            entries.append(translation_entry(catalogue, pack))
    for word_pack in pack_catalogue.WORD_PACKS:
        path = os.path.join(DATA_ROOT, "translations", word_pack["id"] + ".sqlite")
        entries.append(pack_entry(
            path, id=word_pack["id"], kind="word", language=word_pack["language"],
            language_name=word_pack["language_name"], content="word_translation",
            name=f"Word by word, {word_pack['language_name']}", quran_com_resource_id=None,
            credit=f"{QURAN_COM_ATTRIBUTION}, word-by-word translation", source_url=QURAN_COM_URL,
            terms_file=relative_path(path[: -len(".sqlite")] + ".TERMS.md")))
    quantum_path = os.path.join(DATA_ROOT, "translations", "bn_quantum.sqlite")
    if os.path.exists(quantum_path):
        entries.append(pack_entry(
            quantum_path, id="bn_quantum", kind="ayah", language="bn", language_name="Bangla",
            content="translation", name="Quantum Foundation Bangla", quran_com_resource_id=None,
            credit="Quantum Foundation (alquran.org.bd); redistribution permission is handled by the maintainer",
            source_url="https://alquran.org.bd", terms_file=None))
    asad_path = os.path.join(DATA_ROOT, "translations", "en_asad.sqlite")
    if os.path.exists(asad_path):
        entries.append(pack_entry(
            asad_path, id="en_asad", kind="ayah", language="en", language_name="English",
            content="translation", name="Muhammad Asad, The Message of the Qur'an",
            quran_com_resource_id=None,
            credit="Muhammad Asad, via Al Quran Cloud (Islamic Network), edition en.asad; "
                   "rights holder The Book Foundation",
            source_url="https://api.alquran.cloud/v1/quran/en.asad", terms_file=None))
    write_manifest("translations.json", "packs", entries)


def read_timing_info(path):
    with sqlite3.connect(path) as connection:
        return dict(connection.execute("SELECT key, value FROM pack_info").fetchall())


def timing_audio_folder(path):
    with sqlite3.connect(path) as connection:
        url = connection.execute("SELECT url FROM ayah_audio WHERE surah = 1 AND ayah = 1").fetchone()[0]
    return url.rsplit("/", 1)[0]


def reciter_entry(reciter, probes):
    probe = probes.get(reciter["id"], {})
    ayah_base = probe.get("ayah_base", reciter_catalogue.AYAH_BASES[0])
    entry = {
        "id": reciter["id"], "name": reciter["name"],
        "quranicaudio_id": reciter["quranicaudio_id"],
        "quranicaudio_path": reciter["quranicaudio_path"],
        "surah_url_pattern": SURAH_URL_PATTERN.replace("{quranicaudio_path}", reciter["quranicaudio_path"]),
        "everyayah_folder": reciter["everyayah_folder"],
        "ayah_url_pattern": (f"{ayah_base}{reciter['everyayah_folder']}/{{SSS}}{{AAA}}.mp3"
                             if reciter["everyayah_folder"] else None),
        "fallback_domain_suffix": {"from": ".quranicaudio.com", "to": ".quranicaudio.org"},
        "word_by_word": reciter["quran_com_recitation_id"] is not None,
        "timing_pack": reciter["id"] if reciter["quran_com_recitation_id"] is not None or reciter["id"] == "basfar" else None,
        "style": reciter["style"], "riwaya": "hafs",
    }
    if "alternative_everyayah_folders" in reciter:
        entry["alternative_everyayah_folders"] = reciter["alternative_everyayah_folders"]
    if probe.get("surah_files_ok") == 114:
        entry["audio_bytes"] = probe["surah_bytes_total"]
    if "ayah_bytes_total_estimate" in probe:
        entry["ayah_audio_bytes"] = probe["ayah_bytes_total_estimate"]
        entry["estimated"] = True
    if ayah_base != reciter_catalogue.AYAH_BASES[0] and reciter["everyayah_folder"]:
        entry["ayah_base_url"] = ayah_base
    if entry["timing_pack"]:
        path = os.path.join(DATA_ROOT, "timings", entry["timing_pack"] + ".sqlite")
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
        credit = ("Audio from QuranicAudio (quranicaudio.com) and EveryAyah (everyayah.com); "
                  "no word timings, surah and ayah playback only" if reciter["everyayah_folder"]
                  else "Audio from QuranicAudio (quranicaudio.com); no timings exist, surah playback only")
        entry.update({"format_version": FORMAT_VERSION, "file": None, "sha256": None, "bytes": None,
                      "credit": credit, "source_url": "https://quranicaudio.com"})
    return entry


def build_reciters_manifest():
    probes = {}
    if os.path.exists(reciter_catalogue.PROBE_FILE):
        with open(reciter_catalogue.PROBE_FILE, encoding="utf-8") as handle:
            probes = json.load(handle)
    entries = [reciter_entry(reciter, probes) for reciter in reciter_catalogue.RECITERS]
    write_manifest("reciters.json", "reciters", entries)


def main():
    allowed_publishable = None
    for argument in sys.argv[1:]:
        if argument.startswith("--publishable="):
            allowed_publishable = set(argument.split("=", 1)[1].split(","))
        elif argument.startswith("--exclude="):
            EXCLUDED_IDS.update(argument.split("=", 1)[1].split(","))
        elif argument.startswith("--add-indopak-to="):
            add_indopak_to_manifest(argument.split("=", 1)[1])
            return
        elif argument.startswith("--add-learn-audio="):
            add_learn_audio_to_manifest(argument.split("=", 1)[1])
            return
        else:
            raise SystemExit(f"unknown argument {argument}")
    build_scripts_manifest()
    build_translations_manifest(allowed_publishable)
    build_reciters_manifest()


if __name__ == "__main__":
    main()
