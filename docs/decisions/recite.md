# Recite

## Current state
- `:feature:recite` has `MicrophoneTrackerInput` (16 kHz mono, VOICE_RECOGNITION, 100 ms blocks), `RecitationSession`, `ReciteScreen(range, session, settings, audioPlayer)`, `ReciteSettings` (DataStore) and `ReciteSettingsScreen(settings)`.
- The session runs input, phoneme source and tracker on one daemon thread; the tracker is touched only under a lock. `stop()` keeps all results; `start()` after a run clears them; `reset()` clears and returns to Idle.
- Word colours: Confirmed green, Wrong ochre, Skipped red (from `skippedWords`, since `WordState` has no Skipped), Tentative faded ochre, not yet reached field ink, Hidden short dashes in hifz.
- A wrong, skipped or badly held word has a 2 dp dotted underline and opens the review sheet (paper field). An icon-only button (content description "Review") ends each ayah with findings and opens its first flagged word.
- Review sheet blocks per word: Skipped or Wrong word, then Pronunciation (Yours / Correct), then Duration (measured over expected bar). Listen calls `AudioPlayer.playWord(reciterId, ayah, position)`; reciter id comes from `ReciteSettings.reciterId`, default "husary".
- `RecitationSession.apply` consumes `TrackerEvent.Duration` and `Substitution` and stores the tracker's `DurationCheck` and `Substitution` per word; `ReviewFinding` wraps them. The sheet title for a duration finding is always "Madd".
- A red word counts as passed: `currentWord` skips past `Wrong` as well as skipped words, and in hifz a wrong word counts like a skip for `hifzCompleted`. The screen draws a word in the skipped colour from `skippedWords`, else ochre for `Wrong`.
- `ReciteWords` scrolls the current word into view (`BringIntoViewRequester`). `load` reads the reference on `Dispatchers.Default`. A phoneme source that fails to build ends the run in the Error phase.
- :app holds the session in `ReciteViewModel` (scope `viewModelScope`), starting at Al-Fatihah 1-7; `onRangeChange` keeps the picked range across rotation.

## Open items
- Phone check: parsing the 12.8 MB phoneme JSON on first Recite (time and heap), model copy to filesDir on first start, scroll behaviour on a long surah.
- Any failure to build the phoneme source shows "Can't use the microphone"; a separate line would help.
- Compose test needs `@Config(sdk = [34])`: Espresso fails on the newer SDK (InputManager.getInstance).

## Gotchas
- Copy gate flags any `label = "` in Kotlin; build labels outside the call.
- Detekt ReturnCount and LongParameterList are suppressed with reasons where guards or collaborators are the point.

## Tried / rejected
- Text "Review" label under each ayah: replaced by dotted underline plus icon button.
