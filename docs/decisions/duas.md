# Duas

## Current state
- `:feature:duas` holds the "Duas from the Quran" collection, opened from a Browse row on the Quran tab (placement B, approved 2026-10-24).
- Data: `src/main/assets/duas.json`, built by `tools/duas/build_duas.py` from `tools/duas/duas.tsv` and `definitions.json`: 89 duas in 17 groups, word ranges (start and end word with their Uthmani text), opening, prophet, ten starters of 15 words or fewer, and an `excluded` list with reasons. No translation text: cards show the user's ticked translation.
- Inclusion rule: words addressed to Allah that ask or seek refuge and a believer can say today; prayers for harm on a people and pleas of the people of the Fire are out; asking for a just ruling or help is in. Reliance (hasbunallah) and travel words are their own named groups.
- `BundledDuaCatalogueTest` checks every start and end word against the bundled quran.sqlite, word overlaps, group sizes and the starters.
- Screens: home (search, chips, starters, groups), card pager (Listen whole ayahs, Recite and check via `ReciteController.practiceAyahs`, Word by word), saved and learned (three clean recitations) in a DataStore.

- Ayahs by topic: `topics.json` from `tools/topics/topics_source.py` via `build_topics.py` (checks every range against quran.sqlite, at most 8 ayahs a card): 32 topics in 7 categories, 118 parts, 243 passages, reviewed by Opus against Sahih International for mid-thought starts. `TopicCatalogue.asCardCatalogue` turns parts into card groups so topics reuse the dua cards; a passage ends at word 9999 (whole ayahs). Quran tab Browse shows Duas and Topics tiles side by side.
- Cards: the Listen bar has play, the reciter (opens the app's reciter sheet) and repeat; no speed. Word by word offers the `en_transliteration_words` download when it is missing.

## Open items
- Listen and recite play and load whole ayahs; a dua that starts mid-ayah needs a phone check.
- Bangla group and screen text needs a native reader's check.

## Gotchas
- The hizb mark ۞ is stuck to word 1 of 7:156 and 12:101 in the text, so it shows in `start.text`.
