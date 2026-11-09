"""Translate Learn lesson files into Bangla with an OpenRouter model.

    python3 -I tools/translate_lessons.py content/lessons/en/letters/05-three-short-vowels.md ...

Each English file is sent whole; the Bangla file with the same name is written next to the
other Bangla lessons (content/lessons/bn/<course>/<name>.md). The existing Bangla file, when
there is one, goes along as a terminology reference only. The reply is accepted only when every
structural line (headings, keys, Arabic, clip ids, answer numbers) matches the English line for
line; otherwise it is saved as <name>.rejected.md for a look.

The API key is read from ~/.config/jev/openrouter.env (OPENROUTER_API_KEY=...). Nothing else
is read from outside the repository.
"""

import json
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

MODEL = "openai/gpt-6-luna"
ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
KEY_FILE = Path.home() / ".config/jev/openrouter.env"
LESSONS = Path(__file__).resolve().parent.parent / "content/lessons"
TRANSLATION_KEYS = ("translated_from", "source_version", "translation_reviewed_by")
# Keys whose whole value must be the same in both languages.
FIXED_KEYS = {
    "id", "order", "reference", "licence", "examples", "reviewed_by", "version",
    "answer", "clip", "mouth", "highlight", "ayah",
}
TOKEN = re.compile(r"\[\[[^\]]*\]\]|\{\{[^}]*\}\}|qaida-\d+/\d+|[؀-ۿ]+")
KEY_LINE = re.compile(r"^([a-z_]+):(.*)$")
HEADING = re.compile(r"^(#{2,3}) ([A-Za-z]+)(?: (\d+))?(?::|$)")

FIXED_TERMS = """
হরফ (letter), নুকতা (dot), হরকত (vowel mark, harakah), ফাতহা, কাসরা, দাম্মা (never যবর, যের, পেশ),
সুকুন, তানবীন, তাশদীদ (shaddah), মাদ্দ (madd), লীন (leen), গুন্নাহ, কলকলা, মাখরাজ, হামযা,
মোটা (heavy letter or sound), চিকন (light letter or sound), হ্রস্ব স্বর (short vowel),
দীর্ঘ স্বর (long vowel), শব্দ (word), আয়াত (ayah), সূরা (surah), আভেরোস কায়দা (Averroes Qaida),
ট্যাপ করুন (tap, as the app's buttons say), পড়ার ইউনিট (Reading unit).
""".strip()

STYLE = """
Write for a Bangladeshi beginner, child or adult, who is learning to read the Quran from a Qaida.
Sound like a patient Qaida teacher speaking plain, everyday Bangla (চলিত ভাষা), addressing the
reader as আপনি. Say what the English means, not how it says it: reorder, split or join clauses
freely so each sentence is short and reads naturally when spoken aloud. Do not copy English
punctuation habits (semicolons, colon chains, "X: Y" asides); use the Bangla way of saying it.
Prefer common spoken words over bookish Sanskritised ones. Keep Arabic terms that Bangladeshi
Qaida learners already use (listed below) in Bangla script, and keep an English word only when
it is everyday Bangla; never transliterate an uncommon English word. Add nothing the English does
not say: no explanations, no encouragement, no filler, no exclamation marks. Numbers inside prose,
and in the `title:` and `appears_after:` values, are written in Bangla digits; numbers in headings,
in other keys and in option bullets stay as in the source.
Quran words and ayah references are never changed.
""".strip()

FORMAT_RULES = """
The output is the complete Bangla lesson file and nothing else: no code fence, no comment.
Keep exactly one output line for every input line, in the same order, blank lines included, so the
files match line for line. On every line keep these unchanged: the `---` rules; the heading
markers with their kind and number (`## Screen 3:`, `## Grid:`, `## Check:`, `## Answer:`,
`## Practice:`, `## Quiz:`, `### Pick` and the other quiz kinds); every key name and its colon;
every `[[...]]` and `{{...}}` token; every clip id such as `qaida-04/079`; every `answer:` value;
the count and order of the `- ` option lines; and the values of id, order, reference, licence,
examples, reviewed_by, version, mouth, highlight, ayah, clip. Translate the heading titles, the
`title:` and `appears_after:` values and all prose, question, option, explain, tip and label text.
In the front matter, add these three lines right after `order:`: `translated_from: en`,
`source_version: <the version value>`, `translation_reviewed_by: ""`.
Grid cells are `- <Arabic> | <transliteration> | <clip id>`: keep the Arabic and the clip id, and
write the transliteration as the syllable in Bangla script, spelling each letter as the letter-name
list below does (so ثَ is ছা, حَ is হ়া, صَ is স্বা, قَ is ক্বা); a label such as `fā · start`
becomes `ফা · শুরু`.
Letter names are spelled exactly as in the letter-name list below, in every sentence.
""".strip()


def api_key():
    for line in KEY_FILE.read_text().splitlines():
        name, _, value = line.partition("=")
        if name.strip() == "OPENROUTER_API_KEY":
            return value.strip().strip('"')
    raise SystemExit(f"OPENROUTER_API_KEY not found in {KEY_FILE}")


def letter_names():
    """`Arabic = Bangla name` for every single letter of the Bangla unit 1 grids."""
    names = {}
    for line in (LESSONS / "bn/letters/01-all-letters.md").read_text().splitlines():
        cells = [cell.strip() for cell in line.removeprefix("- ").split("|")]
        if line.startswith("- ") and len(cells) == 3 and len(cells[0]) == 1:
            names.setdefault(cells[0], cells[1])
    return "\n".join(f"{arabic} = {name}" for arabic, name in names.items())


def skeleton(text, drop_translation_keys=False):
    """The structural part of every non-blank line, for comparing two languages."""
    lines = []
    for line in text.splitlines():
        line = line.rstrip()
        if not line:
            continue
        heading = HEADING.match(line)
        key = KEY_LINE.match(line)
        if line == "---":
            lines.append(("rule",))
        elif heading:
            lines.append(("heading", heading.group(1), heading.group(2), heading.group(3)))
        elif key and key.group(1) in TRANSLATION_KEYS:
            if not drop_translation_keys:
                lines.append(("key", key.group(1)))
        elif key and key.group(1) in FIXED_KEYS:
            lines.append(("key", key.group(1), key.group(2).strip()))
        elif key:
            lines.append(("key", key.group(1), tokens(key.group(2))))
        elif line.startswith("- ") and line.count("|") == 2:
            arabic, _, clip = (cell.strip() for cell in line[2:].split("|"))
            lines.append(("cell", arabic, clip))
        elif line.startswith("- "):
            lines.append(("option", tokens(line)))
        else:
            lines.append(("prose", tokens(line)))
    return lines


def tokens(text):
    """The Arabic, word keys and clip ids of a line; Bangla word order may differ, so sorted."""
    return sorted(TOKEN.findall(text))


def structure_problems(english, bangla):
    """Lines where the Bangla structure departs from the English, as short messages."""
    source = skeleton(english)
    result = skeleton(bangla, drop_translation_keys=True)
    missing = [key for key in TRANSLATION_KEYS if f"\n{key}:" not in bangla]
    problems = [f"front matter lacks {key}" for key in missing]
    for index, (expected, actual) in enumerate(zip(source, result), start=1):
        if expected != actual:
            problems.append(f"line {index}: expected {expected}, got {actual}")
        if len(problems) >= 8:
            break
    if len(source) != len(result):
        problems.append(f"{len(source)} structural lines in English, {len(result)} in Bangla")
    return problems


def ask(key, system, user):
    body = {
        "model": MODEL,
        "temperature": 0.4,
        "max_tokens": 64000,
        "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
    }
    request = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(body).encode(),
        headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=900) as response:
        reply = json.load(response)
    text = reply["choices"][0]["message"]["content"].strip()
    if text.startswith("```"):
        text = text.split("\n", 1)[1].rsplit("```", 1)[0]
    return text.strip() + "\n", reply.get("usage", {})


def system_prompt(names):
    readme = (LESSONS / "README.md").read_text()
    parts = [
        "You translate one Learn lesson of the Awn Quran app from English into Bangla.",
        "## Style\n" + STYLE,
        "## Terms to use\n" + FIXED_TERMS,
        "## Letter names (use exactly these spellings)\n" + names,
        "## Format rules (binding)\n" + FORMAT_RULES,
        "## The project's own description of the lesson format\n" + readme,
    ]
    return "\n\n".join(parts)


def with_draft_grid_cells(bangla, draft):
    """The translation with every grid transliteration the earlier Bangla file already had.

    The grid spellings were reviewed by the maintainer (2026-11-08) and stay as they are."""
    kept = {}
    for line in draft.splitlines():
        if line.startswith("- ") and line.count("|") == 2:
            arabic, transliteration, clip = (cell.strip() for cell in line[2:].split("|"))
            kept[(arabic, clip)] = transliteration
    lines = []
    for line in bangla.splitlines():
        if line.startswith("- ") and line.count("|") == 2:
            arabic, transliteration, clip = (cell.strip() for cell in line[2:].split("|"))
            transliteration = kept.get((arabic, clip), transliteration)
            line = f"- {arabic} | {transliteration} | {clip}"
        lines.append(line)
    return "\n".join(lines) + "\n"


def translate_file(english_path, key, names):
    english_path = Path(english_path).resolve()
    bangla_path = LESSONS / "bn" / english_path.relative_to(LESSONS / "en")
    english = english_path.read_text()
    draft = bangla_path.read_text() if bangla_path.exists() else ""
    system = system_prompt(names)
    bangla, usage = ask(key, system, "Translate this lesson file.\n\n" + english)
    problems = structure_problems(english, bangla)
    if problems:
        retry = (
            "Translate this lesson file. Your previous reply broke the line structure:\n- "
            + "\n- ".join(problems)
            + "\nKeep one output line per input line and every structural part unchanged.\n\n"
            + english
        )
        bangla, usage = ask(key, system, retry)
        problems = structure_problems(english, bangla)
    bangla = with_draft_grid_cells(bangla, draft)
    if problems:
        rejected = bangla_path.with_suffix(".rejected.md")
        rejected.write_text(bangla)
        return f"{bangla_path.name}: REJECTED, see {rejected.name}\n  " + "\n  ".join(problems)
    bangla_path.write_text(bangla)
    return f"{bangla_path.name}: written ({usage.get('total_tokens', '?')} tokens)"


def main(paths):
    if not paths:
        raise SystemExit(__doc__)
    key = api_key()
    names = letter_names()
    with ThreadPoolExecutor(max_workers=4) as pool:
        reports = pool.map(lambda path: translate_file(path, key, names), paths)
        for report in reports:
            print(report, flush=True)


if __name__ == "__main__":
    main(sys.argv[1:])
