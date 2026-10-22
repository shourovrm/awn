#!/usr/bin/env python3
"""Compare each translation pack with the Tanzil text it is said to come from.

Tanzil (https://tanzil.net/trans/) publishes its translations under stated terms (non-commercial use,
a link back). Those terms apply to a Quran.com pack only if the Quran.com text is the Tanzil text, so this
script measures how close the two are: for each pack in tools/pack_catalogue.py with a tanzil_id it downloads
https://tanzil.net/trans/<tanzil_id> (cached under awn-data/raw/tanzil/) and compares the 6,236 ayah texts.

Per pack it prints:
  identical    ayahs whose texts are equal after removing everything but letters and digits and lower-casing
  mean_ratio   the mean difflib similarity of the cleaned texts, 0 to 1
and writes awn-data/translations/PROVENANCE.tsv with the same numbers.

A pack with identical close to 6,236 is the Tanzil text with only footnote or punctuation changes. A low
identical count with a high mean_ratio is the same translation in another edition or another spelling; a
low mean_ratio is a different text. The result decides the terms_known column in docs/packs.md.

Usage: python3 tools/check_tanzil_provenance.py
"""

import difflib
import os
import re
import sqlite3
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import data_paths
import pack_catalogue
import quran_com

TANZIL_URL = "https://tanzil.net/trans/{tanzil_id}"
NOT_LETTERS_OR_DIGITS = re.compile(r"[\W_]+", re.UNICODE)


def download_tanzil(tanzil_id):
    path = os.path.join(str(data_paths.raw()), "tanzil", tanzil_id + ".txt")
    if not os.path.exists(path):
        request = urllib.request.Request(TANZIL_URL.format(tanzil_id=tanzil_id), headers={"User-Agent": quran_com.USER_AGENT})
        with urllib.request.urlopen(request, timeout=60) as response:
            body = response.read()
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as handle:
            handle.write(body)
    texts = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            parts = line.rstrip("\n").split("|", 2)
            if len(parts) == 3 and parts[0].isdigit() and parts[1].isdigit():
                texts[(int(parts[0]), int(parts[1]))] = parts[2]
    return texts


def cleaned(text):
    return NOT_LETTERS_OR_DIGITS.sub("", text.lower())


def main():
    rows = []
    for pack in pack_catalogue.TRANSLATIONS_AND_TRANSLITERATIONS:
        if not pack["tanzil_id"]:
            continue
        tanzil = download_tanzil(pack["tanzil_id"])
        path = os.path.join(quran_com.DATA_ROOT, "translations", pack["id"] + ".sqlite")
        with sqlite3.connect(path) as connection:
            ours = {(s, a): t for s, a, t in connection.execute("SELECT surah, ayah, text FROM translation")}
        identical, ratio_sum = 0, 0.0
        for key, text in ours.items():
            left, right = cleaned(text), cleaned(tanzil.get(key, ""))
            if left == right:
                identical += 1
                ratio_sum += 1.0
            else:
                ratio_sum += difflib.SequenceMatcher(None, left, right).quick_ratio()
        rows.append((pack["id"], pack["tanzil_id"], len(tanzil), identical, ratio_sum / len(ours)))
        print(f"{pack['id']:20} {pack['tanzil_id']:16} tanzil_ayahs={len(tanzil)} identical={identical} mean_ratio={ratio_sum / len(ours):.3f}")
    output = os.path.join(quran_com.DATA_ROOT, "translations", "PROVENANCE.tsv")
    with open(output, "w", encoding="utf-8") as handle:
        handle.write("pack\ttanzil_id\ttanzil_ayahs\tidentical\tmean_ratio\n")
        for row in rows:
            handle.write(f"{row[0]}\t{row[1]}\t{row[2]}\t{row[3]}\t{row[4]:.3f}\n")


if __name__ == "__main__":
    main()
