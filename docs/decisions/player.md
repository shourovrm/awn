# Player and Listen

## Current state
- `:player` plays one file at a time. `PlaybackController` (pure Kotlin) drives a `PlaybackEngine`; `ExoPlaybackEngine` is the Media3 ExoPlayer one and tests use a fake engine.
- `ExoAudioPlayer` is created once by :app. Its first `play` binds a `MediaController` to `AudioPlaybackService`, which holds the media session and notification (previous, pause, next, stop). The notification title is "Al-Baqarah 2:6" and the artist is the reciter; the session player maps previous and next to `AudioPlayer.previous/next`.
- `AudioPlayer` contract: `state`, `options` (`PlaybackOptions`: `repeatEachAyah`, `repeatRange` as total plays or `INFINITE_REPEAT`, `speed` 0.75 to 1.5), `play`, `playWord`, `pause`, `resume`, `stop`, `next`, `previous`, `setOptions`; `setRepeat(n)` stays as "n more plays of each ayah".
- `PlaybackState` adds `Downloading(doneBytes, totalBytes)`. `next` after the last ayah ends or repeats the range as usual. `previous` restarts the ayah after 3 s, else goes back. A surah-file reciter (Raad al-Kurdi) steps by surah.
- `DownloadingAudioPlayer(player, downloads, quranText, downloadAmount, scope)` wraps the Exo player. `play` returns at once; if the audio at the start of the range is not on disk it downloads it (state `Downloading`), then plays. A refused (Wi-Fi only) or failed download streams instead; `stop` cancels the download.
- Download amount counts from the start ayah (`DownloadPlan`): Page is the range's ayahs on the start ayah's page (grouped by surah when it holds two), Surah is the whole start surah, Juz is the whole start juz (`JuzTable`). Later ayahs stream.
- `AudioDownloads` adds `download(reciterId, ayahs, onProgress)`, `usedBytes` (live flow), `status` (bytes and ayahs on disk), `estimateBytes` (HEAD of a few files; ayah files use bitrate times timed durations, surah files the mean) and `removeAyahs`.
- Word position is the last word started at the exact position and not ended more than 300 ms ago (`WordTimeline`); real alafasy pauses reach 4.7 s inside an ayah, so a gap lights nothing. Checked on all four timed packs: no overlap or backward step, no word ends after the file.
- Files are in `filesDir/audio/<reciterId>/<SSS><AAA>.mp3` (or `<SSS>.mp3`), written as `.part`, resumed with `Range`, size-checked, retried on `.quranicaudio.org`. `LocalFirstDataSource` reads them.
- `:feature:listen`: `AudioFeature` (holds the player, downloads, repositories, settings, application scope and the view model factory; `controller.playFrom(ayah)`), composables `AudioBar`, `PlayRangePanel`, `ReciterSheet`, `AudioSettingsPage` (with the downloads manager), `ListenSettings` (reciter, Wi-Fi only, download amount). Icons are `ic_listen_*` vector drawables.
- Still present for :app until it is rewired: `ListenPanel`, `ListenViewModel`, `ListenField`, `RecitersScreen`/`RecitersViewModel`, `DownloadsScreen`/`DownloadsViewModel`.

## Open items
- :app must wrap the player in `DownloadingAudioPlayer`, build `AudioFeature`, fill the three slots and link `AudioSettingsPage`, then delete the old Listen route and screens.
- Downloads stop when the app process dies; a foreground service or WorkManager job would continue them.
- Repeat and speed are not saved between launches.
- Gap between ayahs is the time ExoPlayer needs to open the next file; no preloading.
- The audio bar uses `Awn.field.buttonFill` and `onButton`; the shell agent's colour modes may want a dedicated bar colour.

## Gotchas
- Lint wants `androidx.annotation.OptIn` plus `@UnstableApi` on classes, not a Kotlin file-level opt-in.
- In `runTest`, `advanceUntilIdle()` skips tasks that only `backgroundScope` holds; call `runCurrent()` too.
- Gradle daemons are shared by all agents on this machine; `--stop` from another session kills a running build.

## Tried / rejected
- Kotlin `@file:OptIn(UnstableApi::class)`: lint ignores it.
- A percentile-based silence threshold for the timing check: recitations with no silence made every word fail.
- Rounding the position down to 50 ms for the word lookup: it delayed the highlight and missed 44 short Alafasy words; the 50 ms publish tick is the only step now.
- Listing a reciter in all three sheet groups: the same name twice; groups are exclusive.
