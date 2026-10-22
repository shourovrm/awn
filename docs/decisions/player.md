# Player and Listen

## Current state
- `PlaybackController` (pure Kotlin) drives a `PlaybackEngine`; `ExoPlaybackEngine` is the Media3 one and tests use a fake. `ExoAudioPlayer` is created once by :app; its first `play` (or `warmUp`) binds a `MediaController` to `AudioPlaybackService` (media session, notification).
- `PlaySchedule` decides what plays and in which order as a pure function of a position and the options read at each boundary: plain order, repeat each ayah, repeat range, cumulative units (1, 2, 1+2, 3, 1+2+3 ...; `PlayStep` is the counter), and the Basmalah turn.
- Gapless: while a file plays the controller asks the schedule for the next turn and calls `engine.prepareNext(source)`; Exo keeps a two item playlist, moves on by itself (`onMediaItemTransition` AUTO is reported as `onEnded`) and the next `start` of the same file adopts it. Nothing is prepared before a learner pause, a sleep stop, the end of the range or a single word. An Exo error on the prepared file drops it and carries on.
- Basmalah (rule 7): the reciter's 1:1 file plays once per surah per session before ayah 1 of surahs 2 to 114 except 9, never between repeats or range rounds; a reciter with only surah files gets none (the file has it). `DownloadingAudioPlayer` fetches 1:1 with a range that starts a surah.
- `PlaybackOptions`: `repeatEachAyah`, `repeatRange` (total plays or `INFINITE_REPEAT`), `speed` 0.75 to 1.5, `pauseAfterAyah` (Off, 1x, 1.5x, 2x of the ayah's played length = duration / speed), `cumulative` (read when a range starts), `stopOnTaskRemoved` (default true). Changing them never restarts audio. A surah-file reciter ignores cumulative and the pause. `SleepTimer`: end of ayah, end of surah, 15, 30, 60 minutes (`setSleepTimer`).
- `AudioPlayer` also has `downloadPrompt`, `answerDownloadPrompt`, `warmUp(reciterId)`. `PlaybackState.Playing` and `Paused` carry `step`; `Error` carries an optional `DownloadProblem`.
- Downloads: `DownloadingAudioPlayer(player, downloads, quranText, downloadAmount, scope, runner, network, networkPolicy)`. Missing audio (amount from the start ayah: page, surah, juz) is fetched at once when under 20 MB and on an unmetered network; otherwise `downloadPrompt` shows the size and waits (Download or Stream). A refused or failed automatic download streams; a failure after the user chose Download, and having no internet, show `Error` with a `DownloadProblem` (`NotEnoughSpace(needed, free)`, `NoInternet`, `ServerNotReachable`, `WifiOnly`).
- `AudioDownloadRunner`: `DirectDownloadRunner` (tests, default) or `WorkManagerDownloadRunner` (`AudioDownloadWorker`, unique work `awn-audio-download`, foreground type dataSync, low importance channel, cancel action, partial files resume after the process dies). Both call `requireFreeSpace` (estimate minus bytes on disk against `FreeSpace`, `DirectoryFreeSpace` uses `StorageManager.getAllocatableBytes`). The job (reciter, ayahs) lives in `filesDir/audio-jobs`. :app must call `AudioDownloadWork.install` in `Application.onCreate`.
- Media session: `setHandleAudioBecomingNoisy(true)` pauses on unplug (also during the learner's silence); channel `audio_playback` is IMPORTANCE_LOW with public lock screen visibility; `onTaskRemoved` stops unless `stopOnTaskRemoved` is off; previous and next move by ayah (cumulative: by step); the title is "Al-Baqarah 2:6", the progress is the ayah file's.
- :app never asks for the notification permission when audio starts: the dialog stopped the first Listen, and Android 13+ shows media-session notifications without it. The manifest keeps `POST_NOTIFICATIONS`; the revise reminder switch in :feature:learn asks for it where a notification needs it. The download progress notification may stay hidden until the user grants it there.
- Files are in `filesDir/audio/<reciterId>/<SSS><AAA>.mp3` (or `<SSS>.mp3`), written as `.part`, resumed with `Range`, size-checked, retried on `.quranicaudio.org`. `LocalFirstDataSource` reads them. Word position is the last word started and not ended more than 300 ms ago (`WordTimeline`, 50 ms tick).
- `:feature:listen`: `AudioFeature` (player, downloads, repositories, `ListenSettings`, scope, `useScript(ScriptId)`, optional runner and network) restores `PlaybackOptions` from `ListenSettings.playbackOptions` at creation and saves every change. `AudioBar(feature, page, onExpand, onRecite)` (idle, playing with step counter, downloading, "Download 3.2 MB?" with Download and Stream), `PlayRangePanel` (range, repeats, speed, pause after ayah, cumulative repeat, sleep timer), `ReciterSheet`, `AudioSettingsPage` (adds "Stop when app closes"; downloads manager names failures).
- Combined bar (2026-10-22): the idle and playing bars end with an ochre microphone (`AudioBarActions.onRecite`, hidden when null) that calls `onRecite(AyahRef)` with `AudioBarViewModel.reciteStart()` (the playing or paused ayah, else the first ayah of the page); the playing bar has Stop, Previous, Pause, Next, Repeat, Reciter, Recite and no speed button (seven fill 360 dp; speed stays in the play options). The recite bar sits at the same place with the same end button.
- Still present for :app until it is rewired: `ListenPanel`, `ListenViewModel`, `ListenField`, `RecitersScreen`/`RecitersViewModel`, `DownloadsScreen`/`DownloadsViewModel`.

## Open items
- Audio settings page: Reciter link, stop-on-close switch, Download by choice, Wi-Fi switch, Downloads manager link. The manager shows a reciter list with a remove button (confirm dialog, space freed) and, per reciter, a Show by choice (Surah, Juz, Range, All) over pack-style rows (download, cancel, remove with confirm). The play panel uses choice rows for repeat counts, pause after ayah and sleep timer.
- :app wiring: see the audio-ux report (AudioDownloadWork.install, WorkManagerDownloadRunner, AndroidNetworkStatus, DirectoryFreeSpace, useScript).
- Phone checks: the gap between ayahs, the Basmalah files of each reciter (a first-ayah file that already holds it would play it twice), play to sound time, lock screen, unplug, task removal, Doze with a download.
- A surah-file reciter replays the Basmalah when the range repeats (no timing to skip it).
- The audio bar uses `Awn.field.buttonFill` and `onButton`; the shell agent's colour modes may want a dedicated bar colour.

## Gotchas
- Lint wants `androidx.annotation.OptIn` plus `@UnstableApi` on classes, not a Kotlin file-level opt-in.
- In `runTest`, `advanceUntilIdle()` skips tasks that only `backgroundScope` holds; call `runCurrent()` too.
- Gradle daemons are shared by all agents on this machine; `--stop` from another session kills a running build.
- A new `PlaybackState` subtype breaks exhaustive `when` in :feature:read; the download question is a separate flow for that reason.
- The player and controller must be touched on the main thread; the application scope is on Default, so `AudioFeature` launches on `Dispatchers.Main.immediate`.

## Tried / rejected
- Kotlin `@file:OptIn(UnstableApi::class)`: lint ignores it.
- A percentile-based silence threshold for the timing check: recitations with no silence made every word fail.
- Rounding the position down to 50 ms for the word lookup: it delayed the highlight and missed 44 short Alafasy words; the 50 ms publish tick is the only step now.
- Listing a reciter in all three sheet groups: the same name twice; groups are exclusive.
- Surah files plus a timing table for gapless: no surah level timing exists for the ayah-file reciters, whole surahs are far larger than a page download, and word highlight is already per ayah file. A preloaded playlist keeps per-ayah files and downloads.
- Checking free space inside `OkHttpAudioDownloads.download`: it would add size requests to every download; the runners do it.
