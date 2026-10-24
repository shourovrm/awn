#!/usr/bin/env python3
"""Ranks the written forms of the Quran's words and writes the file the Learn tab ships.

Run with `python3 -I tools/build_learn_words.py --quran quran.sqlite --out word_forms.bin`. The
Gradle task `generateLearnContent` of :feature:learn runs it at build time from the bundled Quran
text pack, so the output is not committed.

Why forms and not base words: no base-word (lemma) data with a usable licence is available. The
Quranic Arabic Corpus is GPL and forbids changes, and QUL's morphology states no licence. A form is
the written word with every vowel mark, stop sign and tatweel removed and the alef wasla written as
a plain alef. Before that, the one-letter words written joined to the front of a word (وَ "and",
فَ "so", بِ "with", لِ and لَ "for" or "indeed", كَ "like") are split off when what remains,
with its vowel marks, appears on its own somewhere in the Quran: وَقَالَ and فَقَالَ count as
قَالَ, لِلنَّاسِ as ٱلنَّاسِ. The vowelled check keeps words whose own first letter is و or ف,
such as وَلِىٌّ (a guardian), which is not وَ + لِى. Two cases are kept whole although the rest
stands alone: a doubled first letter (لَمَّا "when" is not لَ + مَّا) and a pronoun ending after
a preposition (لَهُمْ "for them" is not لَ + هُمْ "they"). Endings (رَبُّكَ) and verb forms stay
separate forms. Splitting cut the forms from 14,814 to 13,145 and raised the share of the Quran
that the 1,000 commonest forms cover from 66.3% to 70.7% (measured 2026-10-24). The Kotlin side
reads the result through one interface, so base-word data can replace this file later.

File format, version 1, all numbers big-endian:
    4 bytes   magic "AWLW"
    1 byte    format version (1)
    4 bytes   word count (77,429)
    2 bytes   form count
    114 x 2   ayah count of each surah
    A x 1     word count of each ayah, in reading order (A is the sum of the ayah counts)
    W x 2     form rank (1 is the most frequent) of each word, in reading order
    F x 4     representative word of each form, in rank order: its index among all words, from 0

The representative word of a form is the first occurrence of its commonest spelling; the app shows
it as the word and reads its meaning from the installed word-by-word pack. The word-by-word packs
are downloads, so this script does not read them.
"""

import argparse
import collections
import sqlite3
import struct
import sys
import unicodedata
from pathlib import Path

MAGIC = b"AWLW"
FORMAT_VERSION = 1
SURAH_COUNT = 114
MAXIMUM_FORMS = 0xFFFF
MAXIMUM_AYAH_WORDS = 0xFF

# Signs that are not letters and are not combining marks: tatweel, the quarter sign, the sajdah
# sign and the small high waw and yeh (modifier letters).
IGNORED_CODE_POINTS = {0x0640, 0x06DE, 0x06E9, 0x06E5, 0x06E6}
ALEF_WASLA = "ٱ"
ALEF = "ا"
IGNORED_CATEGORIES = {"Mn", "Me", "Cf"}

# The joined one-letter words, each with the vowel it is written with.
PREFIXES = ("وَ", "فَ", "بِ", "لِ", "لَ", "كَ")
# لِ before the article drops the article's alef: لِ + ٱلنَّاسِ is written لِلنَّاسِ.
LAM_BEFORE_ARTICLE = "لِل"
SHADDA = "\u0651"
# After لَ, لِ or بِ these are pronoun endings (لَهُمْ "for them"), not the words "they" or "them".
PRONOUN_ENDING_FORMS = {"هم", "هن", "هما"}
PREPOSITIONS_BEFORE_ENDINGS = ("لَ", "لِ", "بِ")


def normalise(text):
    """Returns the written form of [text]: letters only, alef wasla as alef."""
    letters = []
    for character in text:
        if unicodedata.category(character) in IGNORED_CATEGORIES:
            continue
        if ord(character) in IGNORED_CODE_POINTS or character.isspace():
            continue
        letters.append(ALEF if character == ALEF_WASLA else character)
    return "".join(letters)


def read_words(quran_database):
    """Returns (ayah_counts, words) in reading order.

    `ayah_counts[surah - 1]` is the number of ayahs of the surah and every word is a tuple
    (surah, ayah, position, text).
    """
    quran = sqlite3.connect(quran_database)
    words = list(
        quran.execute(
            "SELECT surah, ayah, position, text_uthmani FROM word ORDER BY surah, ayah, position"
        )
    )
    ayah_counts = [0] * SURAH_COUNT
    for surah, ayah, _, _ in words:
        ayah_counts[surah - 1] = max(ayah_counts[surah - 1], ayah)
    return ayah_counts, words


def base_spelling(text, spellings):
    """Returns [text] without its joined one-letter words, or [text] itself.

    A prefix is split off only when the rest, after any further split, is in [spellings] (every
    vowelled spelling that stands as a word in the Quran).
    """
    for rest in prefix_rests(text):
        base = base_spelling(rest, spellings)
        if base in spellings:
            return base
    return text


def prefix_rests(text):
    """The possible texts left after one joined one-letter word, longest prefix first."""
    rests = []
    if text.startswith(LAM_BEFORE_ARTICLE):
        rests.append(ALEF_WASLA + text[len("لِ"):])
    for prefix in PREFIXES:
        if text.startswith(prefix) and is_possible_rest(prefix, text[len(prefix):]):
            rests.append(text[len(prefix):])
    return rests


def marks_after_first_letter(text):
    """The combining marks written on the first letter of [text], in any order."""
    marks = []
    for character in text[1:]:
        if unicodedata.category(character) != "Mn":
            break
        marks.append(character)
    return marks


def is_possible_rest(prefix, rest):
    """False when [prefix] cannot be a joined word in front of [rest].

    A doubled first letter means the letter before belongs to the word (لَمَّا, كَلَّا), and a
    pronoun after a preposition is an ending (لَهُمْ).
    """
    if len(rest) < 2 or SHADDA in marks_after_first_letter(rest):
        return False
    is_ending = prefix in PREPOSITIONS_BEFORE_ENDINGS and normalise(rest) in PRONOUN_ENDING_FORMS
    return not is_ending


def base_spellings(words):
    """Returns the base spelling of each word (see base_spelling)."""
    spellings = {word[3] for word in words}
    return [base_spelling(word[3], spellings) for word in words]


def rank_forms(words, bases):
    """Returns (form_of_word, order): the form text of each word and the forms by rank.

    [bases] holds each word's base spelling. Equal counts are ordered by the first appearance in
    reading order, so the order is stable.
    """
    form_of_word = [normalise(base) for base in bases]
    counts = collections.Counter(form_of_word)
    first_index = {}
    for index, form in enumerate(form_of_word):
        first_index.setdefault(form, index)
    order = sorted(counts, key=lambda form: (-counts[form], first_index[form]))
    return form_of_word, order


def representative_indexes(words, bases, form_of_word, order):
    """Returns the representative word index of each form, in rank order.

    Only words written without a joined prefix can represent a form, so the app shows قَالَ, not
    وَقَالَ, for the form قال. Every form has one: a prefix is split off only when the base spelling
    stands on its own somewhere.
    """
    indexes_of_form = collections.defaultdict(list)
    for index, form in enumerate(form_of_word):
        if words[index][3] == bases[index]:
            indexes_of_form[form].append(index)
    representatives = []
    for form in order:
        indexes = indexes_of_form[form]
        spelling_counts = collections.Counter(words[index][3] for index in indexes)
        best_count = max(spelling_counts.values())
        # The earliest word of the commonest spelling decides a tie between spellings.
        representatives.append(
            next(index for index in indexes if spelling_counts[words[index][3]] == best_count)
        )
    return representatives


def encode(ayah_counts, words, form_of_word, order, representatives):
    """Returns the bytes of the file described in the module docstring."""
    if len(order) > MAXIMUM_FORMS:
        raise ValueError(f"{len(order)} forms do not fit in two bytes.")
    rank_of_form = {form: rank for rank, form in enumerate(order, start=1)}
    words_of_ayah = collections.Counter((word[0], word[1]) for word in words)
    ayah_keys = sorted(words_of_ayah)
    if sum(ayah_counts) != len(ayah_keys):
        raise ValueError("The ayah counts of the surahs do not match the ayahs of the words.")
    buffer = bytearray()
    buffer += MAGIC
    buffer += struct.pack(">BIH", FORMAT_VERSION, len(words), len(order))
    buffer += struct.pack(">114H", *ayah_counts)
    for key in ayah_keys:
        count = words_of_ayah[key]
        if count > MAXIMUM_AYAH_WORDS:
            raise ValueError(f"Ayah {key} has {count} words; the file allows {MAXIMUM_AYAH_WORDS}.")
        buffer += struct.pack(">B", count)
    for form in form_of_word:
        buffer += struct.pack(">H", rank_of_form[form])
    for index in representatives:
        buffer += struct.pack(">I", index)
    return bytes(buffer)


def decode(data):
    """Reads a file back: returns (ayah_counts, words_per_ayah, form_ranks, representatives)."""
    if data[:4] != MAGIC:
        raise ValueError("Not a word forms file.")
    version, word_count, form_count = struct.unpack_from(">BIH", data, 4)
    if version != FORMAT_VERSION:
        raise ValueError(f"Unsupported word forms version {version}.")
    offset = 4 + 7
    ayah_counts = struct.unpack_from(">114H", data, offset)
    offset += 2 * SURAH_COUNT
    ayah_total = sum(ayah_counts)
    words_per_ayah = list(data[offset : offset + ayah_total])
    offset += ayah_total
    form_ranks = struct.unpack_from(f">{word_count}H", data, offset)
    offset += 2 * word_count
    representatives = struct.unpack_from(f">{form_count}I", data, offset)
    offset += 4 * form_count
    if offset != len(data):
        raise ValueError("The word forms file has trailing bytes.")
    return list(ayah_counts), words_per_ayah, list(form_ranks), list(representatives)


def build(quran_database):
    ayah_counts, words = read_words(quran_database)
    bases = base_spellings(words)
    form_of_word, order = rank_forms(words, bases)
    representatives = representative_indexes(words, bases, form_of_word, order)
    return encode(ayah_counts, words, form_of_word, order, representatives)


def main(arguments):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--quran", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    options = parser.parse_args(arguments)
    data = build(options.quran)
    options.out.parent.mkdir(parents=True, exist_ok=True)
    options.out.write_bytes(data)
    print(f"Wrote {options.out} ({len(data)} bytes).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
