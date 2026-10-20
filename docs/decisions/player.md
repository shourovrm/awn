# Player and Listen

## Current state
- `:player` plays one file at a time. `PlaybackController` (pure Kotlin logic) drives a `PlaybackEngine`; `ExoPlaybackEngine` is the Media3 ExoPlayer implementation and tests use a fake engine.
- `ExoAudioPlayer` is created once by :app with the application context. Its first `play` binds a `MediaController` to `AudioPlaybackService`, which holds the media session and the notification (play, pause, stop only). The service finds the player through `ExoAudioPlayer.active`.
- The player's manifest declares the service (not exported, `mediaPlayback`), `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. :app must still request `POST_NOTIFICATIONS` on Android 13+.
- The file to play is the `ayah_audio.url` of the timing pack (the folder the timings were made for), else the reciter's ayah pattern, else the surah file (Raad al-Kurdi). A surah file plays once and its ayahs are skipped; the state's ayah stays at the first one.
- `setRepeat(n)` repeats each ayah (or the single word) n more times, not the whole range.
- `playWord` starts at `start_ms` and the engine stops at `end_ms` with a player message (exact, not polled), cut at the file duration if known.
- Word position is the last word whose start is at or before the position rounded down to 50 ms; it stays on that word in gaps.
- Downloads go to `filesDir/audio/<reciterId>/<SSS><AAA>.mp3` (or `<SSS>.mp3`), written as `.part`, resumed with `Range`, size-checked against `Content-Length`, renamed when whole, retried once on `.quranicaudio.org`. `LocalFirstDataSource` reads such a file when the media item's cache key names it.
- `DownloadState.totalBytes` is an estimate while a surah downloads (bytes so far times files/present files); exact when complete.
- `UnmeteredOnlyPolicy` (Wi-Fi only) is passed to `OkHttpAudioDownloads`; a refused download throws `DownloadNotAllowedException`.
- `:feature:listen` holds `ListenPanel`, `RecitersScreen`, `DownloadsScreen`, three ViewModels and `ListenSettings` (DataStore: Wi-Fi only, selected reciter, default `alafasy`). Downloads run in a scope the app passes to `DownloadsViewModel`.
- `tools/verify_timings.py` cuts words by timing and counts cuts with speech-level sound for most of their length (alafasy, 20 ayahs, seed 1: 258 of 264 words).

## Open items
- The Alafasy timing pack lists `https://verses.quran.com/Alafasy/mp3/...`, not quranicaudio hosts, so the `.org` fallback does not apply to it; check that host stays up.
- Downloads stop when the app process dies; a foreground service or WorkManager job would continue them.
- Notification has no previous/next buttons; the panel has them.
- Gap between ayahs is the time ExoPlayer needs to open the next file; no preloading.
- The Zipformer check of word cuts (milestone 4) replaces the energy test in `verify_timings.py`.

## Gotchas
- Lint wants `androidx.annotation.OptIn` plus `@UnstableApi` on classes, not a Kotlin file-level opt-in.
- `lint { disable += "UseTomlInstead" }` is set in both modules because third-party coordinates are explicit.
- Gradle daemons are shared by all agents on this machine; `--stop` from another session kills a running build. A private registry avoids it: `-Dorg.gradle.daemon.registry.base=/tmp/<name>`.

## Tried / rejected
- Kotlin `@file:OptIn(UnstableApi::class)`: lint ignores it.
- A percentile-based silence threshold for the timing check: recitations with no silence made every word fail.
