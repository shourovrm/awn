# Tracker

## Current state
- `:tracker` is pure Kotlin. `JsonReferencePhonemes` reads `ordered_quran_phonemes.json` (schema 2) with `org.json` from the android-stubs jar; fields are listed in `tracker/README.md`.
- Alignment unit is one character of a model token, with runs of one repeated character merged (hearing and reference side, word by word). All 41 reference characters exist in `tokens.txt`.
- `AlignmentTracker` aligns the unsettled heard characters to the words from the first unsettled word to the end of the ayah two after it (semi-global Needleman-Wunsch, affine gaps: match 4, substitution -3, insertion open -5 extend -1, deletion open -5 extend 0).
- Judgement: green at coverage (aligned / word length) of 0.9 - 0.2 x speed with at most 1 substitution; red at 2 or more substitutions once the user is heard in a later word; yellow for the rest. A word is settled when a later word has 2 aligned phonemes and at least 50 % of the word matched; otherwise it waits.
- Skip: words with no aligned phoneme, flagged when a later word is green and matched phonemes after them reach `skipEvidencePhonemes` (8 - 5 x speed). Skipped words get `WordState.Wrong` plus `TrackerEvent.Skipped`.
- Insertion: a run of unmatched heard characters (extra or substituted) of `insertionPhonemes` (8 - 4 x speed, 6 at 0.5). Re-anchor: the last `reanchorPhonemes` (16 - 8 x speed, 12 at 0.5) have at most 75 % matches, then a local search of the loaded words with the last 24 characters; accepted at 60 % of a perfect match and 16 points above the current alignment.
- `currentIndex()` counts a red word as passed (the contract said "confirmed or skipped").
- Substitution events: one per substituted phoneme of a passed word, with the model symbols (the reference has no letter positions, so heard and expected are phoneme characters).
- Duration events: madd annotations come from pairing the `rules` ids 1 to 6 with the runs of two or more of ا ۥ ۦ in order; ayahs where counts or lengths disagree get none. One haraka = 0.35 x median gap of the last 40 short-vowel tokens; verdict outside expected +- 1 haraka.
- Contract additions to `Tracker.kt`: `MaddAnnotation`, `ReferenceWord.madds` (default empty), `DurationVerdict`, `DurationCheck`, `TrackerEvent.Duration`, `TrackerEvent.Substitution`.
- Measured on Husary (`TrackingFixtureTest`, model v3): green 87 of 87 on surahs 1, 112, 113, 114 (gate 83); 228 of 230 on 2:1-20 and 159 of 159 on 36:1-20; wrong-ayah anchors 0; false skips 0 on 10 unmodified fixtures (6 full, 4 at tempo 1.2); cut ayah flagged in 10 of 10 (gate 9); foreign segment flagged in 7 of 10 (gate 7).
- Madd duration: 0 of 16 original madds flagged; 5 of 10 vowels slowed 3 times flagged Overheld (gate 9, missed by 4); 4 of 10 at 2 times. Substitutions: 0 on clean short surahs, 2 on the two long sets (1 each), emitted near the splice in 3 of 10 spliced fixtures.
- Fixtures: 62 ayah WAVs, 6 full, 10 cut, 10 splice, 4 + 2 stretched; foreign segments are 1.3 to 1.8 s (one to three words), the start of another surah's ayah.

## Open items
- Phone check: word latency under 1 s and alignment cost per update on a mid-range phone (the DP is O(heard x window), fixtures run at 26 x real time on the desktop).
- Madd: the gap after a long vowel does not follow its length (see `tools/spike_zipformer.md`); try the span from the previous onset, or per-frame posteriors.
- Maintainer: `ReferencePhonemes` loads the 12.8 MB JSON whole through `org.json`; on the phone use a smaller per-surah pack.
- `ordered_quran_phonemes.json` leen madd (ids 7) and muqatta'at ayahs 19:1 and 42:2 carry no madd annotations.
- Other reciters and the user's own recordings are not yet in the fixtures; thresholds are tuned on Husary only.

## Gotchas
- `OnlineRecognizerResult.ysProbs` is empty for CTC greedy search; `SherpaPhonemeSource` reports probability -1.
- Feed about 1 s of silence after the last block (or call `finish()`): the last 0.48 s chunk is not decoded otherwise.
- Token onsets lie on a 0.04 s grid and about 0.12 s after the sound in the file.
- Foreign clips of 0.6 to 0.9 s produced zero or one tokens (splices flagged 4 of 10); clips need 1 s or more.
- Gradle daemons are shared: use `-Dorg.gradle.daemon.registry.base=/tmp/tracker-daemons` so another agent's `--stop` cannot kill a test run. `spotlessApply` and `detekt` exist only at the root project.

## Tried / rejected
- Deletion extend cost -1: a skipped 100-phoneme ayah cost 105 points, so the re-anchor search fired first and picked a repeated phrase in a later ayah (one wrong anchor, cut fixtures 8 of 10).
- Strictly unmatched tail for re-anchoring: chance matches in a 26-letter alphabet keep the run short; use the match share instead.
- Settling poorly matched words: let noise march the anchor forward through red words; they now wait.
- Uniform time stretching to test the madd check: scales the reference short vowels too, so the verdict cannot change.
