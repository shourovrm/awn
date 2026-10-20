# Recite

## Current state
- Recite lives on the Quran screen. `ReciteController` (one per process, created in `AwnServices`) owns the `RecitationSession`, `SpeechPreparation` and `ReciteSettings`. API: `reciteFrom(AyahRef)` loads that ayah to the end of its page (script V2; a page with two surahs loads both), `exit()`, `prepareSpeech()`, `isActive`, `speechState`, `setSpeed`, `setHideWords`.
- `ReciteBar(controller, onExit)` fills the `reciteBar` slot: status line, then icon buttons (all 48 dp, content descriptions): close, speed popup (five labelled steps, fastest on top), hide-words switch, hint (on while words are hidden), review (only after stopping), microphone (start/stop/allow/open settings). Leaving the composition calls `controller.exit()`; ON_STOP stops the microphone.
- `rememberReciteWordStates(controller)` returns `Map<WordKey, WordState>` for the page: empty when not reciting, Hidden for unheard words in memorise mode, Wrong for skipped words. The hint letter shows in the bar status line, not on the page.
- The session runs input, phoneme source and tracker on one daemon thread; the tracker is touched only under a lock. `load(List<AyahRef>)` or `load(ReciteRange)`, `unload()`, `stop()` keeps results, `start()` after a run clears them, `reset()` clears and returns to Idle.
- `SpeechPreparation` copies the speech files, parses the 12.8 MB phoneme file and loads one `SherpaRecognizer` on a minimum-priority thread; `state` is Idle, Preparing, Ready or Failed (retry by `prepare()`). Later starts open a stream on the loaded recognizer instead of loading the model again.
- Noise: the tracker judges nothing until the heard phonemes match the start of the text (see tracker.md), so stray phonemes from room sound colour no word.
- Review sheet: one flat list of flagged words across the loaded ayahs, Listen (`AudioPlayer.playWord`, reciter from `ReciteSettings.reciterId`, default "husary"), Previous/Next, and the line "Experimental feedback on likely mistakes". It opens when nothing is flagged and then says "Nothing to review".
- `ReciteSettingsPage(settings)` (speed slider, hide words, hint) is for the Settings hub; title string `recite_settings_title`. "Memorised after" lives in learn.
- Old `ReciteScreen`, `ReciteRangePicker`, `ReciteWords` and `ReciteSettingsScreen` still compile for :app; remove them with the Recite route.

## Open items
- Phone check: time of `prepareSpeech()` (copy of 72 MB model, parse, model load) and heap with the model kept loaded; call `SpeechPreparation.release()` on low memory if needed.
- Recite stops at the end of the page; extending to the next page needs a tracker that can append reference words without a reset.
- A rotation removes the bar and so stops reciting (`onDispose` calls `exit()`).
- Any failure to build the phoneme source shows "Can't use the microphone"; a separate line would help.
- Compose test needs `@Config(sdk = [34])`: Espresso fails on the newer SDK (InputManager.getInstance).

## Gotchas
- Copy gate flags any `label = "` in Kotlin; build labels outside the call.
- Detekt ReturnCount and LongParameterList are suppressed with reasons where guards or collaborators are the point.

## Tried / rejected
- Text "Review" label under each ayah, then per-ayah icon button: replaced by one review button in the bar (the per-ayah button never existed for clean ayahs, so the sheet looked unopenable).
- Voice-activity gate on the microphone: the model emitted 0 phonemes on 26 synthetic noises, so it would only risk cutting quiet recitation.
