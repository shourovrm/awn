# Tracker

## Current state
- `:tracker` is pure Kotlin. `JsonReferencePhonemes` reads `ordered_quran_phonemes.json` (schema 2) with `org.json` from the android-stubs jar; fields are listed in `tracker/README.md`.
- Alignment unit is one character of a model token, runs of one repeated character merged (heard and reference, word by word). All 41 reference characters exist in `tokens.txt`. `PhonemeAligner` scores: match 4, substitution -3, insertion open -5 extend -1, deletion open -5 extend 0.
- `AlignmentTracker` is a cursor machine (2026-10-25 rewrite). The cursor is the word the user is expected to say next (`currentIndex()`; it moves back during a repeat). Each update fits only the heard phonemes no word has taken (`HeardBuffer`) against a few candidates, each with free heard audio before and after (`WordFitter`): the cursor word, the cursor word with the next, the next two words, and the words just passed (`CursorDecisions`). A said word's audio is dropped except its last phoneme (kept one step for wasl/idgham); old audio is never re-routed.
- Pass (`WordJudge`, `WordScore.cost` unchanged: near sounds 0.4-0.5, other swaps 1, missing 1, extra 0.5, divided by length): 1-3 phonemes cost <= 0.20 and every consonant heard; 4-7 <= 0.26 and 70 % heard as itself or a near sound; 8+ <= 0.30 and 70 %. A passing word whose last consonant is not heard as itself (a substitution does not count) is Held (shown Tentative) until its tail, the next word starting with two matched phonemes, a gap over 0.6 s between token onsets, or `Tracker.closePending()` (the session calls it when a microphone run ends). Only the held state is kept; the word is refitted on closing, as the heard audio is trimmed to 64 phonemes.
- Short words (1-3) pass only heard exactly at the front of the audio, or between a green word and a next word with 2 aligned phonemes.
- Skip (`SkipRules`): the cursor moves past a failing word only when word +1 or +2 (4+ phonemes, first two matched) passes at cost 0.20, or two words in a row from +1 or +2 pass; at most 2 words are jumped, the audio before the target may be at most the jumped words' length + 6, and a target may not reuse 2 matched sounds of a half-heard cursor word. Jumped words: said after all (green), said wrong (2+ matches and 30 %: red, substitutions reported) or left out (red + `Skipped`). A failing cursor word stays Tentative until then.
- Whole ayah left out (cursor at an ayah start, nothing of it heard, the next ayah's first word or two words heard within 6 phonemes): every word red + `Skipped` in one update; the only update that colours more than 2 words. Mutashabihat: when the words said so far equal the next ayah's opening word for word and the next ayah goes on, the cursor's ayah was left out (its green opening turns red, the next ayah's opening green), the one rule that turns green red.
- Repeat (`RepeatDetector`): one of the last 3 words or the first word of this or the last ayah, at cost 0.20, at the front of the audio, followed by 2 aligned phonemes of the next word: `TrackerEvent.Repeated(first, last)`, cursor back, every mark kept; a red word said right becomes green with `TrackerEvent.Corrected`. After a repeat the cursor may jump to the first unsaid word without marks.
- Lost (`LostSearch`): only when 30+ unexplained phonemes failed every candidate; the last 24 are searched in all loaded words (Smith-Waterman), accepted at 80 % of perfect and 12 points above the best other place (best place masked +-4); a tie stays put. Forward: words between become `WordState.Unread` (no fill, not a mistake), one `JumpedAhead(from, to)` plus `Anchored`. Backward: `Anchored`, marks kept. Within cursor+2: no jump.
- Strictness replaces speed: `TrackerConfig(Strictness)`, `TrackerConfig.fromSpeed` maps the stored speed (< 1/3 Lenient, > 2/3 Strict). Rows (cost 1-3/4-7/8+, coverage, skip cost, insertion, lost, entry): Lenient 0.24/0.30/0.34, 65 %, 0.22, 8, 36, 7; Normal 0.20/0.26/0.30, 70 %, 0.20, 6, 30, 6; Strict 0.17/0.22/0.26, 75 %, 0.17, 5, 26, 5. Lenient and Strict hold every fixture gate too.
- Entry check unchanged: nothing is judged until the heard phonemes match the start of the cursor's ayah and the two after with `entryMatches` matches, 70 % of the heard ones, at most 2 left out; before that only the latest 16 are kept.
- Insertion: 6+ unexplained phonemes before a said word (`Inserted(word before)`). Substitution events: one per substituted phoneme of a said or said-wrong word; a single short-vowel swap in a word of 8+ phonemes is not reported (yellow floor). `WordState.Mispronounced` stays display only (the session publishes it).
- Duration events (`MaddDurations`): madd annotations from the `rules` ids 1 to 6 paired with runs of 2+ ا ۥ ۦ; a word is measured when the word after it is said; the vowel ends at the next token that matched one of the next 2 expected phonemes; one haraka = 0.35 x median of the last 40 short-vowel gaps; verdict outside expected +- 1 haraka, Underheld also needs under 0.4 x expected.
- `Tracker.append(words)` extends the loaded words without a reset (states, cursor, heard audio and symbol numbers stay). Contract: `MaddAnnotation`, `ReferenceWord.madds`, `DurationVerdict`, `DurationCheck`, events `Duration`, `Substitution`, `Repeated`, `Corrected`, `JumpedAhead`; `WordState.Unread`.
- Measured on Husary, model v3, Normal (`TrackingFixtureTest`, `ScenarioFixtureTest`), before (window tracker) -> after:

| Gate | Before | After |
|---|---|---|
| Green, surahs 1, 112, 113, 114 (gate 83 of 87) | 87 | 87 |
| Green 2:1-20 / 36:1-20 | 229 of 230 / 159 of 159 | 230 / 159 |
| Wrong-ayah anchors; false skips on 10 unmodified (gate 0) | 0; 0 | 0; 0 |
| Cut ayah flagged (gate 9 of 10) | 10 | 9 (112:3 opens like 112:4) |
| Foreign segment flagged (gate 7 of 10) | 9 | 9 |
| Noise: 300 random runs per setting colouring a word (gate 0) | 0 | 0 |
| Original madds not Ok (gate 0); slowed vowels Overheld (gate 9) | 0 of 16; 8 | 0 of 18; 7 |
| Clean 563 words: red (gate <= 1); yellow sound; madd only; tentative at end | 1; 2; 14; 0 | 0; 2; 9; 0 |
| Updates colouring > 2 words red, 30 fixtures (new gate 0 beyond one left-out ayah) | 10 (7 cut, 3 splice) | 6 (all one left-out ayah) |
| Jumps on clean / cut fixtures (new gate 0) | 0 / 0 | 0 / 0 |
| Ayah said twice (1:2, 112:3): words ever red; jumps; Repeated | 3; 2; - | 0; 0; 2 |
| Ayah restarted at 40 % (1:2, 114:5): ever red; jumps | 1; 2 | 0; 0 |
| Back from 112:3 to 112:1: ever red; all green at end | 3 (2 skipped); yes | 0; yes |
| 1:1 then 1:4 (forward jump): ever red; Unread; JumpedAhead | 6; 0; - | 0; 7; 1 |
| Cough and clicks between ayahs of 1: ever red; jumps | 0; 0 | 0; 0 |
| Slip at the start of 2:12 (2:13 opens alike): ever red; skipped; jumps | 21; 16; 1 | 2; 0; 0 |

- The slowed-vowel miss added is 113:1 word 1: the harness stretch lands after the vowel's token (0.32 s measured before and after). Substitutions near a splice: 3 -> 0 of 10, as foreign audio is now unexplained (an insertion), not forced onto words.
- Fixtures: 62 ayah WAVs, 6 full, 10 cut, 10 splice, 4 + 2 stretched; foreign segments 1.3 to 1.8 s. Scenario WAVs are joined from the ayah WAVs by `ScenarioRecordings` into fixtures/scenario/.
- Model permission: the Quran-Lab authors approved the maintainer's request to use Zipformer P-Arabic v3 from Hugging Face (Quran-Lab/zipformer_p-arabic-v3, NPL-1.2), 2026-10-21. awn-models came from another copy: both .onnx files match Hugging Face in size (72,705,392 bytes); tokens.txt is the same text with CRLF endings; ordered_quran_phonemes.json is a separately generated file (quran-transcript, schema 2, 12.8 MB) and differs from the 5.1 MB file on Hugging Face.
- Voice search (`AyahFinder`, Search screen mic): local alignment of the heard phonemes against all 6,236 ayahs joined (652,401 phonemes, repeats collapsed), credited to the ayah where the match starts, +8 when it starts at an ayah's first phoneme. Each `AyahMatch` also carries `firstWord` and `lastWord` (0-based): the words of that ayah the best alignment covers, from a per-phoneme word table beside the ayah table and the start and end phoneme kept with each ayah's best score; a recitation that runs into the next ayah ends at the ayah's last word. Result rows (`recitedHit`) join the words after `normalizeFallbackText` per word and mark the span as one range; typed-search rows go through `normalizeFallbackTextWithMap` so the stored space before a pause mark no longer draws a dotted circle and the marked ranges follow. The reference and the text split 2:181, 8:6, 13:37 and 37:130 into different word counts, so a span past the text is clamped. Two columns of memory. Listening stops on Stop, at 15 s, or when the same ayah led three 1 s searches and 5 s were heard. Measured 2026-10-24 (AyahFinderAccuracyTest, JVM): at 5 s, right ayah first 90% (40 Alafasy/Minshawy ayahs from tools/fetch_search_fixtures.py) and 95% (62 Husary), in the top three 98% both; at 3 s 68%/55% first. Slowest search 81 ms at 5 s on the PC (unchanged by the word span); not yet timed on the phone. Misses: openings shared by many ayahs (4:135 "يا أيها الذين آمنوا") and ayahs under 8 phonemes (36:1).
- Voice search list (`AyahFinder.findAll`): every ayah scoring at least 85% of the best, ties in Quran order, at most 100 (`MAXIMUM_RESULTS`); partial ayahs are allowed, so a fragment ties with each ayah that shares it. Measured 2026-10-27 on 102 clips with `findAllListsAreMeasuredOnClipsAndFragments`: the right ayah is listed for 98% of clips at 5 s (mean 2.5 rows), 79% at 3 s and 88% for a 3 s fragment from mid-ayah. Lowering the share to 50% or the 40% floor to 15% left the hit rate unchanged and grew the mean list to 22 to 46 rows, so the misses are mishearings, not cutoffs; 85% stays.

## Open items
- Phone check: word latency under 1 s and the cost of the candidate fits and the lost search per update on a mid-range phone.
- Entry check: a user whose first word is garbled is never followed (a slip at 1:1's start: 0 of 29 green, old and new); let the entry match start anywhere in the window.
- Madd: 113:4 and 1:2 stay Ok when slowed; leen madd (id 7) and 19:1, 42:2 carry no annotations; per-frame posteriors are the next step.
- Maintainer: `ReferencePhonemes` loads the 12.8 MB JSON whole through `org.json`; on the phone use a smaller per-surah pack.
- Other reciters and the user's own recordings are not yet in the fixtures; thresholds are tuned on Husary only.

## Gotchas
- `OnlineRecognizerResult.ysProbs` is empty for CTC greedy search; `SherpaPhonemeSource` reports probability -1.
- Feed about 1 s of silence after the last block (or call `finish()`): the last 0.48 s chunk is not decoded otherwise.
- Token onsets lie on a 0.04 s grid and about 0.12 s after the sound in the file.
- Foreign clips of 0.6 to 0.9 s produced zero or one tokens (splices flagged 4 of 10); clips need 1 s or more.
- Gradle daemons are shared: use `-Dorg.gradle.daemon.registry.base=/tmp/tracker-daemons` so another agent's `--stop` cannot kill a test run. `spotlessApply` and `detekt` exist only at the root project.
- `fetchNativeLibs` rewrites `libs/linux-x64/*.so` on every run; a worktree whose `libs` links to the main checkout crashes a running test JVM (SIGBUS) when another worktree builds. Give each worktree its own copy of `libs`.

## Tried / rejected
- Madd from the gap before the vowel token, or the sum before + after: originals reach 7.4 haraka (1:7 word 5) like slowed vowels (5 to 9.7); no separating line.
- Madd from the second next token minus one short-vowel gap: false Overheld on original 1:7 word 5 (0.28 s against 0.21 s allowed); 113:4 stays out of reach because the stretched ayah inflates its own median gap.
- Overheld tolerance of 0.6 haraka instead of 1 (2026-10-21): 3 of 16 original madds flagged (gate 0) and slowed vowels flagged 7 of 8 candidates, because the set of Ok originals shrank; no gain, so the tolerance stays 1 haraka. Madd stays at 8 of 10.
- Voice-activity gate for noise: the model alone answers 26 synthetic noises with 0 phonemes (one for loud clicks); the cause was stray phonemes reaching the alignment.
- Deletion extend cost -1: a skipped 100-phoneme ayah cost 105 points, so the re-anchor search fired first and picked a repeated phrase in a later ayah (one wrong anchor, cut fixtures 8 of 10).
- Strictly unmatched tail for re-anchoring: chance matches in a 26-letter alphabet keep the run short; use the match share instead.
- Settling poorly matched words: let noise march the anchor forward through red words; they now wait.
- Uniform time stretching to test the madd check: scales the reference short vowels too, so the verdict cannot change.
- One window alignment re-routing all unsettled audio, with re-anchoring at 75 % matched over 12 phonemes (to 2026-10-25): a slip before a phrase found later jumped there and turned the words between red (2:12: 21 red).
- Skip target matching anywhere in the unexplained audio, or without its first two phonemes: a half-said cursor word matched the next word minus its first consonant (2:7, 2:14, 2:20 false skips).
- Repeat without the next word started: the start of the cursor word (رح) passed as a repeat of a similar word just said (رب).
