"""Shared access to the Quran.com API v4 for the pack builders in tools/.

Every response is cached on disk under awn-data/raw/quran.com/ (data_paths.raw()), so a second
run of any builder makes no network calls. Live requests are limited to four
per second and retried with exponential backoff on HTTP 429 and 5xx.

The API answers HTTP 403 to the default Python user agent, so every request
sends an explicit User-Agent.
"""

import hashlib
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

import data_paths

API_BASE = "https://api.quran.com/api/v4"
# The "qdc" API of the same service serves the IndoPak page layouts (mushaf=6 and 7); v4 ignores that parameter.
QDC_API_BASE = "https://api.quran.com/api/qdc"
USER_AGENT = "awn-pack-builder/1.0 (https://github.com/awn-quran; free Quran app)"
MINIMUM_SECONDS_BETWEEN_REQUESTS = 0.26  # a little over 1/4 s keeps us under 4 requests per second
MAXIMUM_ATTEMPTS = 6

DATA_ROOT = str(data_paths.workspace())
FONTS_ROOT = str(data_paths.fonts())
RAW_CACHE_DIRECTORY = str(data_paths.raw() / "quran.com")

CHAPTER_COUNT = 114
AYAH_COUNT = 6236
PAGE_COUNT = 604

# The text, translation and word-by-word builders all read the same verse
# responses, so the parameters are defined once and the cache is shared.
WORD_FIELDS = "text_uthmani,code_v1,code_v2,line_number,page_number,v1_page,v2_page,char_type_name,transliteration"
AYAH_TRANSLATION_IDS = (20, 85, 57, 213)  # 131 (The Clear Quran) is not served by the public API

_last_request_time = 0.0


def _cache_path(url):
    digest = hashlib.sha256(url.encode("utf-8")).hexdigest()[:16]
    readable = urllib.parse.urlparse(url).path.strip("/").replace("/", "_")
    return os.path.join(RAW_CACHE_DIRECTORY, f"{readable}-{digest}.json")


def _download(url):
    global _last_request_time
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    for attempt in range(MAXIMUM_ATTEMPTS):
        wait = MINIMUM_SECONDS_BETWEEN_REQUESTS - (time.monotonic() - _last_request_time)
        if wait > 0:
            time.sleep(wait)
        _last_request_time = time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            retryable = error.code == 429 or error.code >= 500
            if not retryable or attempt == MAXIMUM_ATTEMPTS - 1:
                raise RuntimeError(f"HTTP {error.code} for {url}") from error
        except (urllib.error.URLError, TimeoutError) as error:
            if attempt == MAXIMUM_ATTEMPTS - 1:
                raise RuntimeError(f"network failure for {url}: {error}") from error
        time.sleep(2 ** attempt)
    raise RuntimeError(f"unreachable retry loop for {url}")


def fetch(path, api_base=None, **query):
    """Return the parsed JSON for an API path, using the disk cache when present."""
    url = (api_base or API_BASE) + path
    if query:
        url += "?" + urllib.parse.urlencode(query, safe=",")
    cache_file = _cache_path(url)
    if os.path.exists(cache_file):
        with open(cache_file, encoding="utf-8") as handle:
            return json.load(handle)["response"]
    body = _download(url)
    response = json.loads(body)
    os.makedirs(RAW_CACHE_DIRECTORY, exist_ok=True)
    retrieved = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    temporary_file = cache_file + ".tmp"
    with open(temporary_file, "w", encoding="utf-8") as handle:
        json.dump({"url": url, "retrieved": retrieved, "response": response}, handle, ensure_ascii=False)
    os.replace(temporary_file, cache_file)
    return response


def retrieval_date(path, **query):
    """Date (YYYY-MM-DD) on which the cached response for this request was fetched."""
    url = API_BASE + path
    if query:
        url += "?" + urllib.parse.urlencode(query, safe=",")
    with open(_cache_path(url), encoding="utf-8") as handle:
        return json.load(handle)["retrieved"]


def fetch_chapter_verses(chapter_number, word_translation_language, extra_query=None):
    """All verses of one chapter, following pagination. Words carry fields from WORD_FIELDS."""
    verses = []
    page = 1
    while page:
        query = {
            "words": "true",
            "word_fields": WORD_FIELDS,
            "fields": "text_uthmani",
            "translations": ",".join(str(identifier) for identifier in AYAH_TRANSLATION_IDS),
            "word_translation_language": word_translation_language,
            "per_page": 50,
            "page": page,
        }
        if word_translation_language != "en":
            # word_translation_language alone is ignored by the API for Bangla and
            # the words come back in English; the language parameter selects them.
            query["language"] = word_translation_language
        if extra_query:
            query.update(extra_query)
        response = fetch(f"/verses/by_chapter/{chapter_number}", **query)
        verses.extend(response["verses"])
        page = response["pagination"]["next_page"]
    return verses


def all_chapter_verses(word_translation_language):
    """Verses of all 114 chapters as a flat list, in reading order."""
    verses = []
    for chapter_number in range(1, CHAPTER_COUNT + 1):
        verses.extend(fetch_chapter_verses(chapter_number, word_translation_language))
    return verses


def fetch_chapter_audio(chapter_number, recitation_id):
    """Verses of one chapter with verse["audio"] = {"url", "segments"} for one recitation."""
    verses = []
    page = 1
    while page:
        response = fetch(
            f"/verses/by_chapter/{chapter_number}", audio=recitation_id,
            fields="verse_key", per_page=50, page=page,
        )
        verses.extend(response["verses"])
        page = response["pagination"]["next_page"]
    return verses


def fetch_page_words(page_number):
    """Words printed on one page of the V1 (1405H) mushaf, as returned by /verses/by_page.

    Unlike /verses/by_chapter, whose line_number follows the V2 print, this
    endpoint paginates and numbers lines by the V1 print. A verse that crosses
    a page break is returned on both pages, so only words whose own
    page_number equals the requested page are kept.
    """
    words = []
    page = 1
    while page:
        response = fetch(
            f"/verses/by_page/{page_number}", words="true",
            word_fields="line_number,page_number,v1_page,v2_page,char_type_name",
            per_page=50, page=page,
        )
        for verse in response["verses"]:
            surah, ayah = split_verse_key(verse)
            for word in verse["words"]:
                if word["page_number"] == page_number:
                    words.append((surah, ayah, word))
        page = response["pagination"]["next_page"]
    return words


def split_verse_key(verse):
    surah, ayah = verse["verse_key"].split(":")
    return int(surah), int(ayah)


def sqlite_path(*parts):
    path = os.path.join(DATA_ROOT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path
