# Recite

## Current state
- Recite lives on the Quran screen. `ReciteController` (one per process, created in `AwnServices`) owns the `RecitationSession`, `SpeechPreparation` and `ReciteSettings`. API: `reciteFrom(AyahRef)` loads that ayah to the end of its page (script V2), `reviseAt(AyahRef)`, `grade(AyahGrade)`, `exit()`, `isActive`, `isRevising`, `revisionRemaining`, `speechState`, `setSpeed`, `setHideWords`. Optional constructor arguments: `gradeSink`, `revisionQueue` (`RevisionQueue.dueAyahs()`), `onOpenAyah`.
- Reciting continues past the page: when fewer than 15 words are left to recite, the session asks the controller for the ayahs of the next page that has any (`ayahsOnPageAfter`) and appends them to the tracker (`Tracker.append`) without a reset, across page and surah boundaries. An empty answer (end of the Quran) stops asking. The session loop runs the read itself (no dispatcher switch), so tests stay synchronous.
- `ReciteBar(controller, onExit)` fills the `reciteBar` slot. Row 1: close, speed popup, hide-words switch, hint, review (after stopping), microphone. Row 2, only while words are hidden: show next word, show ayah (silent self-testing, no microphone). Row 3, when an ayah is finished in hide-words mode: four grade buttons Easy, Good, Hard, Forgot, one tap each; the grade the recitation earned is filled (a 2 by 2 grid from font scale 1.3). Status line shows "Ayah N done. Pick a grade." or the revision count. Colours come from `ReciteBarColors.of(Awn.field)` (bar, text, main-button fill), so Awn, Light, Dark and Black all work; `ReciteBarLayoutTest` checks 360 dp at font scale 2.0 in every mode.
- Grades: `HifzRules.suggestedGrade(missedWords)` is Good for 0, Hard for 1, Forgot for 2 or more; Easy is only ever chosen by the user. A recited ayah is sent to `ProgressEvents.hifzCompleted` as before (learn rates it the same way) and the prompt carries the suggestion; revealing any word of the ayah sends nothing automatically and the prompt has no suggestion. `RecitationSession.grade` calls `gradeSink(ayah, grade)`; :app maps it to `LearnProgress.gradeAyah`, which replaces the automatic rating.
- Revision: `reviseAt(ayah)` reads the due ayahs from `RevisionQueue`, turns hide words on (also saved), loads from the ayah and sets `isRevising`. After each grade the run moves to the next due ayah after it (else the first before it): it calls `onOpenAyah(next)` and loads it, unless it is already loaded further on. When none is left `isRevising` turns false and recite mode stays.
- `rememberReciteWordStates(controller)` returns `Map<WordKey, WordState>` for the page and now covers appended words too; revealed words are Neutral (shown, no colour), unheard words Hidden in hide-words mode. `rememberReciteCurrentAyah(controller)` returns the ayah of the word the user is on, for the reader to turn the page.
- The session runs input, phoneme source and tracker on one daemon thread; the tracker is touched only under a lock. `load(List<AyahRef>)`, `unload()`, `stop()` keeps results, `start()` after a run clears them, `reset()` clears and returns to Idle. Ayah results are keyed by surah and ayah, since appended words can cross a surah.
- The speech model runs in the `:speech` process: `SpeechService` (app/services) holds one `SherpaRecognizer` (~144 MB native) and its streams; `RemoteSpeech` binds it, loads the model there while parsing the reference JSON here, and sends 100 ms blocks over synchronous binder calls (`SpeechProtocol`; ~1.5 ms per block on top of ~10 ms decoding). Unbinding ends the service, which kills its process, so everything the model used goes back. `AwnApplication` builds no services in that process. `SpeechPreparation` keeps state (Idle, Preparing, Ready, Failed) and the generation counter as before. Tap to Ready: 2.9 s (in-process it was 2.3 s).
- The review sheet takes its colours from the colour mode, so it is dark at night.
- Review sheet: one flat list of flagged words, Listen, Previous/Next, and "Experimental feedback on likely mistakes".
- Audio in the bar (`ReciteAudioState` from `PlaybackState`, `ReciteListening.kt`): Listen plays `ayahToListenTo()` (ayah of the next expected word, else the first loaded) once with the chosen reciter; while audio is not Idle it becomes Pause/Resume, a Stop button and a 2 dp progress line show, hint and review hide, and `pauseMicrophone()` stops the session (results stay). The microphone button is off until the audio ends and is never restarted by itself; `Start` after a stop clears results as before. `reciteFrom` and `reviseAt` stop the audio first. Review moved to a second row because six buttons fill 360 dp.
- `ReciteSettingsPage(settings)` is for the Settings hub. Old `ReciteScreen`, `ReciteRangePicker`, `ReciteWords` and `ReciteSettingsScreen` were removed with the Recite route.

- Speech model stays loaded 2 min after close (`SpeechPreparation.releaseAfterGrace`, cancelled by `prepare()`), released at once by `ReciteController.onLowMemory()` (AwnApplication.onTrimMemory >= RUNNING_LOW). The bar shows only "Preparing…" + progress + Close until `SpeechState.Ready` (`reciteBarMode`). `RecitationSession.wordProblems` feeds the word card (`WordProblem`).

## Open items
- Phone check: speech preparation time on every recite start and heap after exit; the page turning when recitation crosses a page (the reader must follow `rememberReciteCurrentAyah`); the reveal and grade icons and the bar height with three rows.
- A rotation removes the bar and so stops reciting (`onDispose` calls `exit()`), which now also unloads the model; the next recite waits ~5 s again.
- Any failure to build the phoneme source shows "Can't use the microphone"; a separate line would help.
- Revision does not start the microphone by itself (permission and intent); the user taps it once after each jump.
- Compose tests need `@Config(sdk = [34])`: Espresso fails on the newer SDK (InputManager.getInstance).

## Gotchas
- Copy gate flags any `label = "` in Kotlin; build labels outside the call.
- Detekt ReturnCount and LongParameterList are suppressed with reasons where guards or collaborators are the point.
- A `withContext` inside the session loop makes the unconfined test dispatcher return early: ten tests failed until the extension read ran inline.

## Tried / rejected
- Model in the app process plus Runtime.gc() after release: PSS stayed +54 MB after close (native +38, speech library code +17); hence `:speech`.
- sherpa provider "xnnpack" (as recite_quran uses): same ~10 ms decode per block, load 2.8 s instead of 2.0 s.
- Text "Review" label under each ayah, then per-ayah icon button: replaced by one review button in the bar.
- Voice-activity gate on the microphone: the model emitted 0 phonemes on 26 synthetic noises, so it would only risk cutting quiet recitation.
- Grades committed only when the user taps (no automatic rating): a silent session would lose every ayah the user forgot to grade; the automatic rating is kept and replaced on a tap.
