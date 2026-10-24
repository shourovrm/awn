# Duas source

`duas.tsv` lists each dua by surah, start and end word position, group, prophet and starter flag.
`definitions.json` holds the note, group names, prophet names and the excluded list.
Run `python3 -I tools/duas/build_duas.py` to write `feature/duas/src/main/assets/duas.json`.
The script reads the word texts from `core/data/src/main/assets/packs/quran.sqlite` (gitignored).
After a change, run the `:feature:duas` content tests, which check the file against the Quran text.
