# Module contracts

These interfaces are the only surface that feature modules use. Only the maintainer edits the files that declare them. Each interface has an in-memory fake in a `fakes` package of the same module (in `src/main`, so other modules can import it); feature tests use the fakes.

## :core:data (package `app.awn.quran.core.data`)
- `AyahRef` is a surah and ayah number, ordered by surah then ayah.
- `WordKey` is surah, ayah and word position, the key shared by every pack.
- `ScriptId` names a print of the text and carries its pack id.
- `Chapter` is one surah with its names, verse count, revelation place and bismillah flag.
- `Word` is one spoken word with its Uthmani text, page-font glyph codes and transliteration.
- `LineItem` is one thing on a mushaf line: `WordItem`, `AyahEnd`, `SurahHeader` or `Basmala`.
- `PageLine` is one numbered slot of a page with its items.
- `PageLayout` is the lines of one page in one script.
- `QuranTextRepository` returns chapters, words, page layouts and the ayah-to-page mapping.
- `TranslationId` is a translation pack file stem such as `en_saheeh`.
- `TranslationInfo` describes a translation and whether it is installed.
- `TranslationText` is one passage with the ayah range it covers.
- `TranslationRepository` returns translation passages (null if not installed) and word-by-word meanings (empty if not installed).
- `Reciter` is a reciter with URL patterns, word-timing flag and timing pack id.
- `WordTiming` is the start and end millisecond of one word in an ayah file.
- `AyahAudio` is the URL, duration and word timings of one ayah.
- `ReciterRepository` returns reciters and ayah audio (null if unknown).
- `Bookmark` is a bookmarked page with a creation time.
- `SavedAyah` is a saved ayah with an optional note.
- `RecentPage` is a page with the time it was last opened.
- `BookmarkRepository` exposes bookmarks, saved ayahs and recent pages as flows and exports or imports them as JSON.
- `InstalledPack` is an installed pack with its kind, format version and size.
- `PackEntry` (package `packs`) is one manifest entry.
- `PackRepository` lists, installs and removes packs, locates the font file for a page, and reports a pack's download and installed size (`sizeOf`, `PackSize`).
- `Quarters` is the 240 rub' al-hizb starts with their first ayah and page per print.
- `SearchRepository` finds ayahs by Arabic (marks and letter variants ignored) and by installed translations.
- `LineItem.AyahEnd` carries the printed end-sign glyph code for page-font prints.
- `ProgressEvents` receives learning events; :feature:learn implements it and :feature:read and :feature:recite call it.

## :tracker (package `app.awn.quran.tracker`, pure Kotlin)
- `PhonemeEvent`, `PhonemeSource`, `TrackerInput`, `SherpaPhonemeSource` are unchanged: model output and audio input.
- `WordState` is how a word is shown while reciting.
- `WordKeyLike` repeats `WordKey` because this module cannot depend on Android modules.
- `ReferenceWord` is a word key with the phonemes expected for it.
- `TrackerConfig` holds `speed`, 0 patient to 1 eager.
- `TrackerEvent` is a change the tracker reports: `WordChanged`, `Skipped`, `Inserted` or `Anchored`.
- `Tracker` aligns heard phonemes to the loaded reference words.
- `TrackerFactory` creates a tracker for a config.
- `ReferencePhonemes` returns the reference words of an ayah from `ordered_quran_phonemes.json`.

## :player (package `app.awn.quran.player`)
- `PlaybackState` is `Idle`, `Downloading`, `Playing`, `Paused` or `Error`.
- `AudioPlayer` plays ayah ranges or single words, moves to the next or previous ayah, and exposes its state and its `PlaybackOptions` (repeat each ayah, repeat the range, speed) as flows.
- `DownloadingAudioPlayer` wraps a player and first downloads missing audio by the download amount (page, surah, juz).
- `DownloadState` is the progress of one reciter's surah download.
- `AudioDownloads` downloads, measures and removes offline audio.
- The stub `Reciter` is deleted; use `app.awn.quran.core.data.Reciter`.

## Fakes
- `core.data.fakes`: `FakeQuranTextRepository`, `FakeTranslationRepository`, `FakeReciterRepository`, `FakeBookmarkRepository`, `FakePackRepository`, `FakeProgressEvents`.
- `tracker.fakes`: `FakeTracker`, `FakeTrackerFactory`, `FakeReferencePhonemes`.
- `player.fakes`: `FakeAudioPlayer`, `FakeAudioDownloads`.

## Ownership
The single-page redesign wave (docs/redesign.md) is merged. Feature modules still depend only on :core modules, :player and :tracker; :app composes them through `QuranScreenSlots` and the settings page slots.
