#!/usr/bin/env python3
"""Check feature/learn/src/main/assets/snippets.json.

For every hadith the script fetches the sunnah.com URL and asserts HTTP 200 and that the page holds
the hadith number. sunnah.com sits behind a Cloudflare challenge and answers automated requests
with HTTP 403; such URLs are listed as unverified, never as passed. As a second, independent check
the script asserts that the Arabic text of every hadith is a passage of the report with the same
number in the fawazahmed0/hadith-api editions (ara-bukhari, ara-muslim). Ayah entries are compared
with the Quran database (awn-data/published/quran/quran.sqlite).

Exit code: 0 all checks passed, 1 a check failed, 2 no failure but some URL is unverified.
Run with `python3 -I tools/check_snippets.py`.
"""

import json
import os
import re
import sqlite3
import sys
import urllib.error
import urllib.request
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import data_paths

REPOSITORY = Path(__file__).resolve().parent.parent
SNIPPETS = REPOSITORY / "feature/learn/src/main/assets/snippets.json"
API = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/{edition}.json"
EDITIONS = {"bukhari": "ara-bukhari", "muslim": "ara-muslim"}
EXPECTED_COUNT = 60

DIACRITICS = re.compile("[ؐ-ًؚ-ٰٟۖ-ۭ࣓-ࣿ]")
LETTER_FORMS = {"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي"}
MARKERS = re.compile("[‏\"{}]")


def plain(text):
    text = MARKERS.sub("", text)
    return "".join(LETTER_FORMS.get(c, c) for c in text if not DIACRITICS.match(c)).replace(" ", "")


def api_number(sunnah_number):
    """804a -> 804.01, 2667b -> 2667.02, 5027 -> 5027."""
    match = re.fullmatch(r"(\d+)([a-z]?)", sunnah_number)
    if not match:
        raise ValueError(f"unexpected hadith number {sunnah_number}")
    base, letter = match.groups()
    return base if not letter else f"{base}.{ord(letter) - ord('a') + 1:02d}"


def fetch_sunnah(url, number):
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Awn snippet check)"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read().decode("utf-8", errors="replace")
            status = response.status
    except urllib.error.HTTPError as error:
        return "blocked" if error.code in (403, 429, 503) else "failed", f"HTTP {error.code}"
    except urllib.error.URLError as error:
        return "failed", str(error.reason)
    if status != 200:
        return "failed", f"HTTP {status}"
    if number not in body:
        return "failed", f"page does not contain {number}"
    return "verified", "HTTP 200"


def load_editions():
    editions = {}
    for collection, edition in EDITIONS.items():
        with urllib.request.urlopen(API.format(edition=edition), timeout=120) as response:
            reports = json.load(response)["hadiths"]
        editions[collection] = {str(item.get("arabicnumber", item["hadithnumber"])): item["text"] for item in reports}
    return editions


def main():
    document = json.loads(SNIPPETS.read_text(encoding="utf-8"))
    snippets = document["snippets"]
    failures, unverified = [], []
    if len(snippets) != EXPECTED_COUNT:
        failures.append(f"{len(snippets)} snippets, expected {EXPECTED_COUNT}")
    if len({s["id"] for s in snippets}) != len(snippets):
        failures.append("duplicate ids")

    data_directory = data_paths.published()
    database = sqlite3.connect(f"file:{data_directory / 'quran' / 'quran.sqlite'}?mode=ro", uri=True)
    editions = load_editions()
    counts = {"ayah": 0, "hadith": 0, "url verified": 0, "url unverified": 0, "text matched": 0}

    for snippet in snippets:
        for field in ("arabic", "text_en", "text_bn"):
            if not snippet.get(field, "").strip():
                failures.append(f"{snippet['id']}: empty {field}")
        if snippet["url"] is None:
            counts["ayah"] += 1
            source = snippet["source"]
            surah, ayah = source["surah"], source["ayah"]
            if snippet["id"] != f"quran-{surah}-{ayah}":
                failures.append(f"{snippet['id']}: id does not match source {surah}:{ayah}")
            row = database.execute("SELECT text_uthmani FROM ayah WHERE surah=? AND ayah=?", (surah, ayah)).fetchone()
            if row is None or row[0] != snippet["arabic"]:
                failures.append(f"{snippet['id']}: Arabic differs from the Quran database")
            continue

        counts["hadith"] += 1
        collection, number = snippet["id"].split("-", 1)
        if snippet["grade"] != "sahih":
            failures.append(f"{snippet['id']}: grade {snippet['grade']}")
        if snippet["url"] != f"https://sunnah.com/{collection}:{number}":
            failures.append(f"{snippet['id']}: url {snippet['url']} does not match the id")
        if snippet["source"] != {"type": "hadith", "collection": collection, "number": number}:
            failures.append(f"{snippet['id']}: source {snippet['source']} does not match the id")

        report = editions[collection].get(api_number(number))
        if report is None:
            failures.append(f"{snippet['id']}: no report {api_number(number)} in the hadith API")
        elif plain(snippet["arabic"]) in plain(report):
            counts["text matched"] += 1
        else:
            failures.append(f"{snippet['id']}: Arabic text is not part of report {number}")

        outcome, detail = fetch_sunnah(snippet["url"], number)
        print(f"{outcome:9} {snippet['url']} ({detail})")
        if outcome == "verified":
            counts["url verified"] += 1
        elif outcome == "blocked":
            counts["url unverified"] += 1
            unverified.append(snippet["url"])
        else:
            failures.append(f"{snippet['url']}: {detail}")

    print()
    print(counts)
    for failure in failures:
        print(f"FAILED: {failure}")
    if unverified:
        print(f"{len(unverified)} sunnah.com URLs are unverified (the site blocks automated requests)")
    print("RESULT:", "FAILED" if failures else ("UNVERIFIED URLS" if unverified else "PASSED"))
    return 1 if failures else (2 if unverified else 0)


if __name__ == "__main__":
    sys.exit(main())
