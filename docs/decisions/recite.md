# Recite

## Current state
- `:feature:recite` has `MicrophoneTrackerInput` (16 kHz mono, VOICE_RECOGNITION, 100 ms blocks), `RecitationSession`, `ReciteScreen(range, session, settings, audioPlayer)`, `ReciteSettings` (DataStore) and `ReciteSettingsScreen(settings)`.
- The session runs input, phoneme source and tracker on one daemon thread; the tracker is touched only under a lock. `stop()` keeps all results; `start()` after a run clears them; `reset()` clears and returns to Idle.
- Word colours: Confirmed green, Wrong ochre, Skipped red (from `skippedWords`, since `WordState` has no Skipped), Tentative faded ochre, not yet reached field ink, Hidden short dashes in hifz.
- A wrong, skipped or badly held word has a 2 dp dotted underline and opens the review sheet (paper field). An icon-only button (content description "Review") ends each ayah with findings and opens its first flagged word.
- Review sheet blocks per word: Skipped or Wrong word, then Pronunciation (Yours / Correct), then Duration (measured over expected bar). Listen calls `AudioPlayer.playWord(reciterId, ayah, position)`; reciter id comes from `ReciteSettings.reciterId`, default "husary".
- `DurationFinding`, `DurationVerdict` and `SubstitutionFinding` are local copies of the tracker's `DurationCheck`, `DurationVerdict` and `Substitution` shapes. The session fills them through `recordDuration` and `recordSubstitution`.

## Open items
- When `:tracker` gains `TrackerEvent.Duration` and `Substitution`, add one `when` branch each in `RecitationSession.apply` and `ayahOfIndex` that maps to the local types.
- `:app` must create the session (real `PhonemeSource`, `MicrophoneTrackerInput`) and hold it across rotation.
- The page does not scroll to the current word yet.
- Compose test needs `@Config(sdk = [34])`: Espresso fails on the newer SDK (InputManager.getInstance).

## Gotchas
- Copy gate flags any `label = "` in Kotlin; build labels outside the call.
- Detekt ReturnCount and LongParameterList are suppressed with reasons where guards or collaborators are the point.

## Tried / rejected
- Text "Review" label under each ayah: replaced by dotted underline plus icon button.
