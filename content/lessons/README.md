# Awn lessons

These files are the lessons of the Learn tab. The app is built from them: `tools/build_learn_content.py` reads this folder and writes the lesson data that ships in the APK. English is the source text; other languages translate it.

## Licence

The lesson text in this folder is licensed under Creative Commons Attribution-ShareAlike 4.0 (CC BY-SA 4.0), https://creativecommons.org/licenses/by-sa/4.0/. Reuse needs a credit to Awn and the same licence for the result. The Quran words and ayahs that the lessons point to are not part of this licence; the app draws them from its own Quran text.

## Layout

```
content/lessons/<language>/<course>/NN-slug.md
```

- `<language>`: `en`, `bn`. A lesson has the same file name and the same `id` in every language.
- `<course>`: `basics` (four short lessons before the first word), `grammar` (a lesson appears after the set that brings its grammar point), `letters` (the Reading track: from the letters to a whole ayah, in Qaida order), `tajweed` (the Tajweed track: the rules of reciting, units 11 to 19).
- `NN`: the order in the course. A gap is allowed; the course runs in `order`.

## File format

A file starts with YAML front matter between `---` lines, then the body.

| Key | Meaning |
|---|---|
| `id` | the course, a hyphen and the file name without `.md` (`grammar-03-pronoun-endings`), the same in every language |
| `title` | the title on the path and above the first screen |
| `order` | the position in the course |
| `appears_after` | when the lesson comes, in words, for a reviewer |
| `introduced_by` | grammar lessons only: word keys; the lesson is due once the user has met the base form of any of them |
| `reference` | the source the lesson follows, with a link |
| `licence` | the licence of the source and of the text |
| `examples` | every word key (`surah:ayah:word`) and ayah key (`surah:ayah`) the body uses |
| `reviewed_by` | the reviewer's name once the file is checked, else empty |
| `version` | the date of the last change |
| `translated_from`, `source_version`, `translation_reviewed_by` | translations only |

The body is a list of screens. Each screen starts with a heading and holds paragraphs separated by blank lines.

- `## Screen N: Title` is a reading screen.
- `## Check: Title` is a question after the reading screens, and `## Answer: Title` is the explanation shown once the user has answered. A lesson may end with several Check and Answer pairs; nothing else may follow them. A check has `question:`, an optional `ayah:` (shown with the words around it), an optional `highlight:` (a word key marked in the ayah), a bullet list of options and `answer:` (the option number, from 1). Options appear in the order written.
- `## Grid: Title` is a teaching grid, read right to left, five cells to a row. Each line is one cell: `- Arabic | transliteration | clip id`. The Arabic is written as it is drawn (no `[[ ]]`), the transliteration is in the file's language and has no Arabic, and the clip id looks like `qaida-04/012`. A tap on a cell plays its sound; Play all plays the cells in order.
- `## Practice: Title` is one item of the unit's practice set; the app draws 10 at random from all the Practice items of the unit on each visit. It has the lines of a Check (`question:`, optional `ayah:` and `highlight:`, options, `answer:`) and a required `explain:` line, shown after the answer, and an optional `clip:` that plays after the answer. An option written as `[[بَ]]` alone shows as a large tile.
- Grid and Practice screens may come anywhere after the first reading screen, between the Check and Answer pairs; a Check is still followed at once by its Answer. Translations keep the same clip ids as English.
- `letters: [[ا]] alif; [[ب]] baa` is a grid of letters, five to a row from the right, each with its name under it.
- `- item` is a bullet; `| a | b |` rows are a table (header row, then `|---|`).
- `{{2:3:7}}` stands for the Quran word 2:3:7, drawn in the reader's script. `{{2:3}}` alone in a paragraph stands for the ayah, drawn with the reader's translation.
- `[[هُمْ]]` is a typed Arabic fragment that is not a Quran word: one letter, an ending, a root, a letter shape. Arabic outside `{{ }}` and `[[ ]]` is an error.

The build fails when a key does not exist in the Quran text, when `examples` and the keys used in the body differ, when a translation has other screens or keys than the English file, or when front matter is missing.

## Rules for the text

- A paragraph starts with one fact the reader can check, then a quantity, a worked example or the mechanism.
- Every term is defined in the sentence where it first appears.
- An example comes before the rule it shows.
- No sentence announces the text, no sentence claims something about all of Arabic, no list of three for rhythm.
- A heading names its subject. It is never "Example" or "Rule".
- The text follows a reference; it does not quote it, and no source line appears on a lesson screen.
- A hint line is written only when the label cannot be understood without it.

## References

| Course | Source | Licence | Used for |
|---|---|---|---|
| Grammar | William Wright, *A Grammar of the Arabic Language*, 1859 (translated from Caspari). Scan: https://commons.wikimedia.org/wiki/File:A_Grammar_of_the_Arabic_Language_(IA_agrammararabicl00wriggoog).pdf | Public domain. The Commons file page carries the tag `PD-old-100-expired`. | Order, facts and examples |
| Grammar, letters, basics | Wikibooks, *Arabic*. https://en.wikibooks.org/wiki/Arabic | CC BY-SA 4.0 | Order, facts and examples |
| Letters | Qari Noor Muhammad, *Al-Qaida An-Nooraniyya* (Noorani Qaida). Copy: https://archive.org/details/noorani-qaida_202011 | Used for non-commercial purposes (maintainer's decision, 2026-10-23) | Order of the units and practice items |
| Letters (audio) | Islamic Home Schooling Academy of New Zealand, Noorani Qaida lessons 1 to 17. https://www.youtube.com/channel/UCX766Oi2anGGIkIgMJXu9ng | CC BY (YouTube licence field, checked 2026-10-23) | Letter and practice recordings |

The references are credited in each file's front matter, here and on the app's Sources page, and nowhere on a lesson screen. A file whose wording stays close to a Wikibooks page stays under CC BY-SA 4.0, as this whole folder does.

Not used: Quranic Arabic Corpus (GPL; its data header forbids changes), QUL morphology (no licence stated), Madinah Arabic, Understand Quran Academy and Bayyinah (all rights reserved).

## Proposing a correction

Open an issue or a pull request that changes the file for the language concerned. Say which screen, what the text says now and what it should say. For an Arabic example, give the word key. A reviewer who has checked a file adds their name to `reviewed_by` (or `translation_reviewed_by` for a translation). Bangla files need a native reader before they are marked reviewed.
