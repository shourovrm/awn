# Awn - Quran & Tajweed Helper: project plan

Awn (Arabic عون, "help") is a free, ad-free, account-free native Android app written in Kotlin with Jetpack Compose that shows the Quran page by page, follows the user's recitation word by word on the device, and later checks tajweed with an optional on-device model. This document is the working plan as of 2026-10-20. It supersedes the structure of the earlier "build plan (app-first ordering)" recorded in `sessions/2026-10-20-research.md`, and it reuses that plan's verified numbers where the decisions below did not change them. Every time or size figure marked "(estimate)" is a guess by the author of this plan, not a measurement.

## Status

The goal is a published source repository and a series of GitHub pre-releases, each tagged `v0.MINOR.PATCH` under semantic versioning, that together add the ten features listed in the milestone table. No `1.0.0` is planned; the project stays at `0.x.y` while any milestone below 0.9 is incomplete.

The constraints are fixed by the licences of the two models the app uses. The Zipformer tracker (Quran-Lab No-Profit License 1.2, "NPL-1.2") forbids charging for the app or any feature it powers and requires a statement that automatic feedback can be wrong and does not replace a qualified teacher; the Muaalem checker (Quran Muaalem License 1.0, "QML-1.0", Apache-2.0 plus conditions) forbids advertisements and requires the same kind of notice. Awn therefore has no ads, no payments, no accounts, no analytics by default, and all audio stays on the device.

The following decisions are made and are not reopened in this plan:

- The stack is native Android: Kotlin, Jetpack Compose, Gradle wrapper, Android only. Awn depends on no other Quran app's code. The `recite_quran` Dart package and the Natlu app are not dependencies and are not read or ported; they are a last-resort reference only if a problem is truly stuck.
- The app is the critical path. The Muaalem checker is an optional in-app download behind a beta flag. The self-trained mini model is a parallel best-effort track that never blocks a release.
- The user has accepted the Hugging Face licences as published (the Quran-Lab gate under NPL-1.2 included) and will not email any rights holder. Compliance is handled by the checklist in "Compliance and attribution", not by permission requests.
- No release APK is tested on physical phones for now. Verification uses CI (format, lint, unit tests, a build), the headless Android emulator already installed at `~/android-sdk` (AVD `test`, API 34 `default` x86_64 image, no Google Play services, about 3.2 GB host RAM while running), and a debug install on the user's own phone over ADB before each push. Measured device testing on low-end and mid-range phones stays a later stage.
- The repository licence is MIT plus "Awn additional conditions" modelled on QML-1.0: free to all end users, no ads, no charges, the AI-advisory notice, and pass-through to forks. The repository is source-available, not OSI open source, and F-Droid inclusion is out of scope for that reason.
- Audio is downloaded from QuranicAudio, the host Quran for Android uses, and kept on the device. Cloudflare R2 (free tier: 10 GB storage, free egress) is a fallback host only.
- Scripts are rendered with fonts and word-level text, never page images, because live tracking colours individual words.
- Google Play is a later stage. The only Play facts kept are: new apps must target API 36 since 31 Aug 2026 ([developer.android.com](https://developer.android.com/google/play/requirements/target-sdk)), and a new personal developer account must run a closed test with 12 testers for 14 days before production.
- Store and README wording must not claim "makharij verification" until a measured precision figure supports it (see milestone 9).

## Toolchain and verification environment

The build uses the Gradle wrapper with these versions, all current stable on 2026-10-20: Android Gradle Plugin 9.4 (minimum Gradle 9.6.0, minimum JDK 17; [developer.android.com/build/releases/gradle-plugin](https://developer.android.com/build/releases/gradle-plugin); 9.4.1 is the newest on Google's Maven), Gradle 9.8.2 ([gradle.org/releases](https://gradle.org/releases/)), Kotlin 2.4.21 ([github.com/JetBrains/kotlin/releases](https://github.com/JetBrains/kotlin/releases/latest); 2.5.0 is still beta), Compose BOM 2026.09.00 with Compose 1.12.1 and Material 3 1.4.0 ([developer.android.com/jetpack/androidx/releases/compose](https://developer.android.com/jetpack/androidx/releases/compose)), `compileSdk` and `targetSdk` 36, `minSdk` 26. Versions live in `gradle/libs.versions.toml`. Formatting is Spotless 8.10.4 with ktfmt 0.64; linting is Android Lint plus detekt 1.23.8; unit tests use JUnit with Robolectric 4.17 where a Context is needed; UI and tracker tests are Compose UI tests and instrumented tests.

The development machine has a broken default JDK: `java` from `/usr/lib/jvm/java-21-openjdk` fails with "libjli.so: invalid ELF header" and `pacman -Qkk jdk21-openjdk` reports 193 checksum mismatches (broken since 2026-10-06). The build uses the working Temurin 21 JDK at `~/.gradle/jdks/temurin-21` (Temurin-21.0.12.1+1, verified with `java -version`), either as `JAVA_HOME=~/.gradle/jdks/temurin-21 ./gradlew ...` or via `org.gradle.java.home` in the user-level `~/.gradle/gradle.properties`, never in the repo's properties file. CI uses the same major version through `actions/setup-java` with `distribution: temurin` and `java-version: 21`, so local and CI builds match. The user should reinstall `jdk21-openjdk` when convenient. The Android SDK at `~/android-sdk` has platforms 33 to 36 and build-tools up to 36.0.0; no global Gradle is installed, which is fine because the wrapper downloads its own.

Every milestone's exit check runs in one of two places. "CI" means the GitHub Actions pipeline (section "CI/CD and repository workflow"): `./gradlew spotlessCheck detekt lintGithubDebug testGithubDebugUnitTest assembleGithubDebug` with zero violations and all tests green. "Emulator" means `~/android-sdk/emu.sh start`, then `./gradlew connectedGithubDebugAndroidTest` or `./gradlew installGithubDebug` followed by a scripted `adb` run. The emulator has no microphone, so recitation tests feed 16 kHz PCM decoded from reciter MP3 files straight into the tracker through a test seam instead of recording.

The sherpa-onnx 1.13.8 Android release ships native libraries for arm64-v8a, armeabi-v7a, x86 and x86_64 (the `sherpa-onnx-1.13.8.aar`, 50,129,134 bytes, and `sherpa-onnx-v1.13.8-android.tar.bz2` assets at [github.com/k2-fsa/sherpa-onnx/releases](https://github.com/k2-fsa/sherpa-onnx/releases/latest)), so the Zipformer tracker runs on the x86_64 emulator. The ONNX Runtime Android AAR `com.microsoft.onnxruntime:onnxruntime-android` 1.31.0 (Maven Central, 2026-10-09) is expected to include x86_64 as well; milestone 9 verifies this before relying on it.

The development machine has 15 GB of RAM (`free -g`), so an AVD with 4 GB of RAM for the milestone 9 model test fits beside the host; the home disk has about 11 GB free, which holds one 876 MB model but not a second system image.

Measurements of latency, RAM and battery on real phones are deferred. A debug build is installed on the user's own phone over ADB before each push, which catches crashes but produces no numbers. Each milestone below lists the phone check it postpones, so that nothing is forgotten when measured phone testing starts.

## Milestones

| Version range | Milestone | Scope | Path | Exit check (summary) |
|---|---|---|---|---|
| 0.0.x | 0 Toolchain and skeleton | Gradle project, repo files, licence, CI, empty app on emulator | Critical | CI green; app opens on emulator |
| 0.1.x | 1 Mushaf and live tracking | 604-page Mushaf in one script, own Kotlin tracker on Zipformer, hifz mode, madd check | Critical | 95 % of words tracked on 4 surahs of Husary audio; 9/10 skips flagged |
| 0.2.x | 2 Scripts | QPC V1, V2, V4 tajweed glyph fonts, IndoPak, with layouts | Critical | 604 pages render in each script with 0 missing glyphs |
| 0.3.x | 3 Reciters and word playback | Page and surah download from QuranicAudio, word seek via QUL segments | Critical | 95/100 sampled words play the right word for each shipped reciter |
| 0.4.x | 4 Translations | Full translations (English, Bangla first), word-by-word from QUL | Critical | Every ayah has text in each shipped translation; terms file per translation |
| 0.5.x | 5 Bookmarks, saved verses, recent | Local storage only | Critical | UI tests; data survives app restart on emulator |
| 0.6.x | 6 Progress and FSRS revision | Streaks, pages read, memorised ayahs, spaced repetition | Critical | FSRS schedule matches py-fsrs reference on 200 cases |
| 0.7.x | 7 Motivational snippets | Curated, referenced hadith and verses | Critical | 100 % of references resolve; all hadith graded sahih |
| 0.8.x | 8 Tajweed guide | Lessons written by the project, QUL tajweed examples | Critical | Teacher sign-off per lesson; unreviewed lessons labelled |
| 0.9.x | 9 Muaalem checker (beta) | 876 MB int8 download, Kotlin feature extractor, decode, feedback | Critical, flagged | Kotlin decode equals Python on 50/50 clips; precision gate per error type |
| 0.10.x and later | 10 Quranic Arabic; Play; phones | Morphology lessons, Play listing, device tests | Later | Separate plan |
| Parallel | M Mini model track | Own ~120 M parameter distilled model | Parallel, never blocking | Per-head F1 within 5 points of the teacher |

Time estimates assume one person at about 10 hours per week ("part-time"). Milestones 0 to 5 total about 26 to 34 weeks part-time (estimate), of which the independent tracker in milestone 1 is the largest item; milestones 6 to 9 add about 20 to 28 weeks (estimate). Milestones 2 to 8 are independent of each other once milestone 1 exists, so their order may change without affecting the plan.

## Milestone 0: toolchain and skeleton (0.0.x)

Inputs: Temurin JDK 21 at `~/.gradle/jdks/temurin-21`; Android SDK at `~/android-sdk` (platforms 33 to 36, build-tools 36.0.0); the `.gitignore` already in the repo.

Steps:

1. Generate the project with the Gradle wrapper at 9.8.2, AGP 9.4, Kotlin 2.4.21 and a version catalog in `gradle/libs.versions.toml`; document `JAVA_HOME=~/.gradle/jdks/temurin-21` in the README's local-setup section and set `kotlin { jvmToolchain(21) }` with `compileOptions` targeting Java 21 in the build. Keep the `ANDROID_SDK_ROOT` and `ANDROID_AVD_HOME` overrides noted in the emulator memory file.
2. Application id `org.awn.quran` (open question 1), `minSdk 26`, `compileSdk` and `targetSdk` 36. Product flavors `github` (in-app update check against the GitHub Releases API) and `play` (no self-update). R8 on for release; ABI splits for `arm64-v8a`, `armeabi-v7a` and `x86_64` (the emulator needs the last one; drop it from published builds later). `versionName` and `versionCode` are computed in Gradle from the git tag as `MAJOR*10000 + MINOR*100 + PATCH` plus a per-ABI offset, never typed by hand.
3. Write the licence: MIT text plus "Awn additional conditions" mirroring QML-1.0 sections 2.1 (notice) and 2.3a (no advertisements) and NPL-1.2's no-charge clause, with pass-through to forks. Name it `LICENSE` and note in the README that the repository is source-available rather than OSI open source.
4. Add README (install, run, test), `.editorconfig`, Spotless with ktfmt, detekt with a `detekt.yml` baseline of the default rules, and Android Lint with `warningsAsErrors = true`.
5. Add a first-launch notice screen with the QML-1.0 bilingual text and the NPL-1.2 statement, blocking until "I understand" is tapped, reachable again from About. The Arabic text controls per QML 2.1.
6. Tag `v0.0.1` as a GitHub pre-release with the unsigned debug APK attached.

Tools: Gradle wrapper, `sdkmanager`, `emu.sh`, GitHub Actions. Cost: $0. Time: 1 to 2 weeks (estimate).

Exit check: CI is green on `main`; `./gradlew installGithubDebug` and `adb shell am start` open the notice screen on the emulator with no crash in `adb logcat` over 60 seconds. Postponed phone check: none.

If it fails: the usual causes are the broken JDK 21 being picked up (check `./gradlew -version` prints Temurin 21) and SDK-path confusion (see `android-emulator-setup.md`); fix the environment before changing code.

## Milestone 1: Mushaf reading and live tracking (0.1.x)

Inputs:

- Text: QUL "KFGQPC Hafs script - Word by word" (JSON or SQLite, tagged Mushaf layout) and "KFGQPC V2 layout (1421H print)" from [qul.tarteel.ai/resources/quran-script](https://qul.tarteel.ai/resources/quran-script) and [qul.tarteel.ai/resources/mushaf-layout](https://qul.tarteel.ai/resources/mushaf-layout). The Madani Mushaf has 604 pages (Quran for Android's `MadaniDataSource.numberOfPages = 604`) and 15 lines per page.
- Font: QUL "QPC Hafs font" (Unicode, ttf) from [qul.tarteel.ai/resources/font](https://qul.tarteel.ai/resources/font).
- Model and reference data from [huggingface.co/Quran-Lab/zipformer_p-arabic-v3](https://huggingface.co/Quran-Lab/zipformer_p-arabic-v3) (gated, NPL-1.2 accepted): `zipformer_p_arabic_v3.int8.onnx` (72,705,392 bytes; 65.5 M parameters; a "standard sherpa-onnx streaming-CTC zipformer2 export"), `zipformer_p_arabic_v3.1.int8.onnx` (madd fine-tune, same interface), `tokens.txt` (251 symbols; the card warns that `phoneme_units.json` IDs are offset by one from the CTC output and that the blank is ID 250, not 0), `quran_text2phoneme.json` (3.88 MB, text to phoneme lookup), `ordered_quran_phonemes.json` (5.11 MB, the canonical phonemisation of all 6,236 ayat), and `quran_per_eval.py` (the reference streaming loop with cache tensors and fbank settings). Input is 16 kHz mono with 80-dim Kaldi fbank (Povey window); mel-spectrogram features roughly double the error.
- Runtime: sherpa-onnx 1.13.8 Android AAR (Apache-2.0) with its Kotlin API (`OnlineRecognizer`, `OnlineStream`, `OnlineRecognizerConfig`, `OnlineModelConfig` with a zipformer2 CTC model entry, `FeatureConfig(sampleRate = 16000, featureDim = 80)`; verify the exact names against `kotlin-api/OnlineRecognizer.kt` at tag v1.13.8).
- Test audio: EveryAyah per-ayah MP3s for Husary (`https://mirrors.quranicaudio.com/everyayah/Husary_128kbps/001001.mp3`, verified HTTP 200 on 2026-10-20), converted on the PC with ffmpeg to 16 kHz mono WAV for surahs 1, 112, 113 and 114 (33 ayahs), plus recordings of the user's own voice for the same ayahs once available.

Steps, each with unit tests on the WAV fixtures:

1. Data import: a Python script converts the QUL word table into a bundled SQLite file with columns `surah, ayah, word_position, page, line, text`, and converts `ordered_quran_phonemes.json` plus `quran_text2phoneme.json` into a per-word reference phoneme table keyed by the same `surah, ayah, word_position`. A test asserts every one of the 6,236 ayahs has a reference and that the word count per ayah matches between the two sources; where QUL counts the ayah-end marker as a word, the offset rule is recorded once.
2. Page view in Compose: one row per line, right to left, each word a tappable element with a colour state (neutral, green, yellow, red, hidden). Page navigation by swipe, surah list, juz list and page number.
3. Audio capture: `AudioRecord` at 16 kHz mono PCM 16-bit with `MediaRecorder.AudioSource.VOICE_RECOGNITION` (the source with hardware noise suppression and gain control disabled), delivered in 100 ms blocks to a `TrackerInput` interface. A WAV-file implementation of the same interface drives all tests.
4. Phoneme stream: sherpa-onnx `OnlineRecognizer` configured for zipformer2 CTC with the model and `tokens.txt`, greedy decoding, emitting (phoneme, frame index) pairs; frames are about 40 ms after the encoder's subsampling (verify from the model config). A test decodes Husary 1:1 and asserts the phoneme string equals the reference for that ayah within 5 % edit distance.
5. Alignment: a dynamic-programming (Needleman-Wunsch with affine gaps) alignment of the growing decoded phoneme sequence against the reference phonemes of a window of the current and next two ayahs, re-run on each new block and anchored to the last confirmed word; a word is confirmed (green) when at least 80 % of its phonemes align with at most one substitution, tentative (yellow) when partially covered, and wrong (red) when its aligned span has two or more substitutions. The window advances when the last word of an ayah is confirmed. Re-anchoring: when 12 consecutive phonemes fail to align, search the whole surah for the best-matching 12-phoneme span and move the anchor.
6. Skip and insert detection: an alignment gap covering a whole word on the reference side marks that word skipped (red outline); a run of 6 or more decoded phonemes on the hypothesis side with no reference match marks an insertion at the preceding word. Tests cut ayah 3 from each surah fixture and splice in a foreign word.
7. Hifz mode: hide words of the chosen range, reveal each word when confirmed, flag skipped words, and offer a "first letter" hint that reveals only the first letter of the next hidden word.
8. Madd duration check: for each madd letter in the reference (positions from the tajweed-marked QUL script or from the phoneme table's long-vowel symbols), count the CTC frames the aligned long vowel occupied; compare with the expected 2, 4 or 6 harakat using the reciter's own median haraka length measured over the current ayah; flag a deviation of more than 1 haraka. Tests use Husary fixtures (expected to pass) and time-stretched copies (expected to flag).
9. Model delivery (open question 6): the file is either bundled in the APK or downloaded on first use from a GitHub Release asset of this repository, with SHA-256 check and HTTP Range resume to app-private storage.

Tools: Kotlin, Compose, sherpa-onnx AAR, Room or plain SQLite, ffmpeg, Python for the import scripts. Cost: $0. Time: 10 to 14 weeks part-time (estimate), of which steps 4 to 8 are about 7 to 9 weeks; the earlier plan's 5 to 7 weeks assumed a ready-made tracker.

Exit check (emulator and CI): with Husary's 33 ayahs fed through the WAV seam at real-time pace, the tracker marks at least 95 % of the roughly 150 words green and never anchors to a wrong ayah; in hifz mode, with ayah 3 cut out of each of 10 surah passes, the skip detector flags the skip in 9 of 10 passes and produces no false skip on the 10 unmodified passes; madd check flags 9 of 10 stretched fixtures and 0 of 10 originals; Compose UI tests cover the first-letter hint and the colour mapping. Postponed phone check: word latency under 1.0 s median on a mid-range phone and model load under 3 s, from the earlier plan.

If it fails: on tracking, confirm the WAV seam delivers 16 kHz mono PCM (any resampling mismatch halves accuracy silently), try v3.1, and try chunk sizes 16 and 24 frames (the model was trained at 8, 16 and 24); on alignment, tune the thresholds in step 5 on the fixtures before changing the algorithm; on madd, fall back to flagging only deviations of 2 or more harakat.

## Milestone 2: scripts (0.2.x)

Inputs: QUL glyph fonts "QPC V1 Font" (604 page fonts, 1405H print), "QPC V2 Font" (1421H print), "QPC V4 Tajweed Font" (1441H print, colour tajweed), "Indopak Nastaleeq font" and "KFGQPC Nastaleeq" with the matching layouts "KFGQPC V1 layout", "KFGQPC V4 layout", "KFGQPC Nastaleeq 15 lines layout" and "Indopak 13/15/16 lines layout"; the word tables "QPC V1 Glyphs - Word by Word", "QPC V2 Glyph - Word by Word", "V4 Glyphs (With Tajweed) - Word by word", "Indopak Nastaleeq script - Word by Word" and "QPC Hafs Script - With Tajweed". QUL credits King Fahd Quran Printing Complex for the QPC fonts and Ayman Siddiqui for the IndoPak and tajweed fonts.

Steps:

1. Define a `Script` record: font family, per-page font file or single file, word table, layout table, line count. Make milestone 1's QPC Hafs Unicode script the first instance.
2. Glyph-based scripts use one font per page, loaded through Compose `FontFamily` from a file; load the page font lazily and cache 5 pages either side. Measure the total font pack size per script (estimate 40 to 70 MB for 604 files); ship the Unicode script in the APK and offer glyph scripts as downloadable packs hosted as GitHub Release assets of this repository (2 GB per asset limit).
3. Keep one canonical word key (`surah, ayah, word_position`) across scripts so bookmarks, tracking colours and translations stay valid when the user switches script.
4. Settings: script picker with a preview of page 1 and page 604.

Tools: Compose text and `Canvas`, Python to repackage QUL downloads. Cost: $0. Time: 3 to 4 weeks part-time (estimate).

Exit check (CI and emulator): a test renders all 604 pages in each shipped script to a bitmap with `Paint.hasGlyph` checks per word and counts missing glyphs; the count must be 0, and the page's word count must equal the word table's count on every page. Compose screenshot tests for pages 1, 2, 255 and 604 in each script guard regressions.

If it fails: a nonzero missing-glyph count usually means the word table and font version differ; re-download both from the same QUL resource pair rather than patching glyph codes.

## Milestone 3: reciters and word-by-word playback (0.3.x)

Inputs:

- Full-surah files: `https://download.quranicaudio.com/quran/<path>/<SSS>.mp3`, with `<path>` taken from the QuranicAudio qari API (`https://quranicaudio.com/api/qaris`, 177 entries). Verified paths: Husary `mahmood_khaleel_al-husaree/`, Minshawi `muhammad_siddeeq_al-minshaawee/`, Afasy `mishaari_raashid_al_3afaasee/`, Sudais `abdurrahmaan_as-sudays/`, Husary Muallim `husary_muallim/`. The path `husary/` returns 404.
- Per-ayah files: `https://mirrors.quranicaudio.com/everyayah/<folder>/<SSS><AAA>.mp3`, folders from `https://everyayah.com/data/recitations.js` (for example `Husary_128kbps`, `Alafasy_128kbps`, `Abdul_Basit_Murattal_64kbps`).
- Fallback host: replace `.quranicaudio.com` with `.quranicaudio.org` on failure, as Quran for Android's `UrlUtil.kt` does (verified 200 for the everyayah mirror on `.org`).
- Timings: QUL recitations with segments (59 of 133 listed), format `[word_position, start_ms, end_ms]` relative to the ayah file for ayah-by-ayah sets, and `timestamp_from`/`timestamp_to` keyed `surah:ayah` for surah-by-surah sets, from [qul.tarteel.ai/resources/recitation](https://qul.tarteel.ai/resources/recitation). Husary is QUL id 112.

Steps:

1. Ship a reciter table with both URL patterns, the QUL segment file id, and a `verified` flag. Start with Husary, Afasy, Abdul Basit Murattal, Minshawi and Sudais (open question 2).
2. Download behaviour: on play, download the current page by default (the ayahs on that page as per-ayah files, roughly 10 to 15 files of 50 to 150 KB each) or the whole surah on request (one gapless file, 1 to 60 MB), and keep files in app storage with a per-reciter size shown in Settings. Downloads use OkHttp with HTTP Range resume and verify `Content-Length`.
3. Playback with Media3 ExoPlayer: ayah-by-ayah with highlighting; word tap seeks to `start_ms` and stops at `end_ms`; a "repeat word/ayah N times" control for hifz.
4. Per-reciter timing verification script (PC): for each reciter, take 20 random ayahs, cut each word by the QUL segment from the EveryAyah file with ffmpeg, and have the Zipformer transcribe the cut; the transcription must match the reference phonemes of that word for at least 18 of 20 ayahs before the reciter's `verified` flag is set. A listening pass on 20 words confirms the automatic result.

Tools: Media3, OkHttp, ffmpeg, Python. Cost: $0 (QuranicAudio is a public host; R2 free tier if a mirror becomes necessary). Time: 4 to 5 weeks part-time (estimate).

Exit check (emulator and PC): for each shipped reciter, 100 sampled words seek and stop inside their segment with no neighbouring word audible by the automatic phoneme check (step 4) on at least 95 of 100; a download of page 1 completes and survives app restart; the fallback host is exercised by a test that blocks the `.com` host. Postponed phone check: playback start within 300 ms of tap.

If it fails for a reciter: QUL's timings were made for a specific recording; try the other QuranicAudio folder for that reciter (bitrate variants differ in encoder delay by up to 50 ms), else ship the reciter with ayah-level playback only and no word seek.

## Milestone 4: translations (0.4.x)

Inputs:

- QuranEnc API ([quranenc.com/en/home/api](https://quranenc.com/en/home/api), base `https://quranenc.com/api/v1/`): 76 translations as of 2026-10-20. Keys for the first languages: `english_saheeh` (v1.1.2), `english_rwwad` (v1.0.19), `urdu_junagarhi` (v1.1.3), `indonesian_affairs` (v1.0.1), `turkish_rwwad` (v1.0.4), `persian_ih` (v1.1.3), `french_montada` (v1.0.0). QuranEnc has no Bengali and no Malay translation: its language list contains neither `bn` nor `ms`.
- Bangla full translations: QUL "Dr. Abu Bakr Muhammad Zakaria", "Sheikh Mujibur Rahman", "Taisirul Quran" (ayah by ayah) from [qul.tarteel.ai/resources/translation](https://qul.tarteel.ai/resources/translation) (205 translations listed); Tanzil "Muhiuddin Khan" and "Zohurul Hoque" from [tanzil.net/trans](https://tanzil.net/trans/) (non-commercial use only; a link to tanzil.net/trans is required when more than three Tanzil translations are used; redistribution of the list is forbidden).
- Malay: Tanzil "Basmeih" or a QUL entry; verify terms per file.
- Word-by-word: QUL "English Word by Word Translation" and "Bengali wbw translation" (QUL credits QuranWBW.com); 22 word-by-word translations exist on QUL.

Steps:

1. Translation packs: one SQLite file per translation with `surah, ayah, text, version, source_url`, generated by a PC script from the QuranEnc sura endpoint (114 calls per translation) or from QUL/Tanzil downloads. Each pack ships with a `TERMS.md` that quotes the source's conditions verbatim and records the version number, which QuranEnc's terms require to be displayed, and a SHA-256 of the source download that CI re-checks.
2. Host packs as GitHub Release assets of this repository; the app lists packs from a `translations.json` manifest in the repo and downloads on demand. English Saheeh International is bundled in the APK as the default.
3. Word-by-word pack keyed by the canonical word key from milestone 2; shown under each word on long-press and as an optional interlinear line.
4. Update check: compare manifest versions monthly when online, since QuranEnc requires republished copies to track its latest version.

Tools: Python requests, Room, Compose bottom sheet UI. Cost: $0. Time: 3 to 4 weeks part-time (estimate).

Exit check (CI): a test opens every shipped pack and asserts 6,236 non-empty rows and the recorded checksum; a test asserts a `TERMS.md` and a credit entry in the About screen exist for every pack in the manifest. On the emulator, downloading the Bangla pack and switching translation survives restart.

If it fails: a pack with fewer than 6,236 rows is a source gap (QuranEnc returns 404 for missing ayahs); report it upstream through the site and ship the pack only when complete.

## Milestone 5: bookmarks, saved verses, recent pages (0.5.x)

Inputs: none external. Storage is a local Room database with tables `bookmark(page, created_at)`, `saved_ayah(surah, ayah, note, created_at)` and `recent(page, opened_at)` capped at 50 rows.

Steps: a bookmark toggle on the page, a saved-verse action on ayah long-press with an optional note, a Recent list ordered by `opened_at`, export and import of the database as a JSON file through the Storage Access Framework so users keep their data without an account.

Tools: Room, `ActivityResultContracts.CreateDocument`. Cost: $0. Time: 1 to 2 weeks part-time (estimate).

Exit check (CI and emulator): Compose UI tests for toggle, list and export; on the emulator, 10 bookmarks, 10 saved ayahs and 10 recent pages are present after `adb shell am force-stop` and relaunch, and an exported JSON re-imports to the same 30 rows.

If it fails: schema migrations are the usual culprit; every schema change ships with a forward-only Room migration and a test that opens the previous version's database file.

## Milestone 6: progress tracking and FSRS revision (0.6.x)

Inputs: the FSRS algorithm (Free Spaced Repetition Scheduler, the algorithm Anki uses) as implemented in py-fsrs by the open-spaced-repetition project; no maintained Kotlin package was verified, so the scheduler (about 200 lines: stability, difficulty, retrievability and interval formulas with the 21-weight default parameter set) is ported to Kotlin and tested against py-fsrs output. Local Room database.

Steps:

1. Record events: page opened, ayah tracked green in recitation mode, ayah completed in hifz mode without a skip. Derive daily streak, pages read per day and the set of memorised ayahs (an ayah counts as memorised after 3 hifz completions on 3 different days, a threshold the user can change).
2. FSRS schedules each memorised ayah as a card; a hifz completion without a skip is a "Good" review, one with skips is "Again". A Revision screen lists the cards due today.
3. Progress screen: streak, pages this week, memorised ayah count by surah, next revision due.

Tools: Kotlin, Room, py-fsrs on the PC for reference vectors. Cost: $0. Time: 3 to 4 weeks part-time (estimate).

Exit check (CI): a test feeds 200 review sequences generated by py-fsrs on the PC into the Kotlin scheduler and asserts identical next-due intervals (to the day) on all 200; streak tests cover the day boundary in the device time zone.

If it fails: an interval mismatch means a formula or parameter differs; pin the py-fsrs commit used for the reference vectors in the test file and diff formula by formula.

## Milestone 7: motivational snippets (0.7.x)

Inputs: Sahih al-Bukhari and Sahih Muslim via sunnah.com (API key is requested by opening an issue on [github.com/sunnah-com/api](https://github.com/sunnah-com/api); the API covers "a portion" of the data, and an offline dump is listed as not yet available); Quran ayahs from the app's own text. sunnah.com publishes no redistribution licence for its English translations.

Steps:

1. Curate 60 to 100 snippets by hand: Quran ayahs about recitation and remembrance, and hadith graded sahih in Bukhari or Muslim about reciting, memorising and teaching the Quran. Store them as `assets/snippets.json` with fields `type, arabic, translation, reference, grade, url`.
2. Use the Arabic hadith text (classical text with no copyright) and write the project's own short English and Bangla translations, or quote a translation with its translator named, so no sunnah.com translation is reproduced without attribution.
3. Show one snippet on the home screen per day, with the reference and a link.

Tools: a spreadsheet or JSON editor, a Python checker. Cost: $0. Time: 1 to 2 weeks part-time (estimate).

Exit check (CI): a test asserts every snippet has a reference and URL, that every hadith has `grade == "sahih"`, and a PC script confirms each URL returns HTTP 200 and the page's hadith number matches the reference on 100 % of entries.

If it fails: remove the snippet; no unverified text ships.

## Milestone 8: tajweed guide (0.8.x)

Inputs: QUL "QPC Hafs Script - With Tajweed" (word-level tajweed markup) and "QPC V4 Tajweed Font" from milestone 2; the project's own lesson text; a qualified teacher for review (open question 3).

Steps:

1. Write 12 to 16 lessons in English and Bangla (Arabic terms kept) covering makharij overview, sifat, noon and meem sakinah rules (idgham, ikhfa, iqlab, izhar), ghunnah, qalqalah, madd types and lengths, tafkheem and tarqeeq of ر and ل, and waqf signs. Each lesson has a definition, the rule, and three Quran examples pulled from the tajweed-marked text so the highlighted letters come from data, not hand-typing.
2. Each lesson has a `reviewed_by` and `reviewed_on` field; lessons without a reviewer show a "Draft, not yet reviewed by a teacher" banner.
3. Link each lesson to the matching feedback type in milestone 9 so a flagged word opens the rule.

Tools: Markdown lesson files rendered with a Compose Markdown library (licence checked before adding) or plain Compose text, Python to extract examples. Cost: $0 (a gift to the reviewer is outside scope). Time: 4 to 6 weeks part-time including review (estimate).

Exit check: every lesson's examples are confirmed by a script to carry the lesson's tajweed tag in the QUL data (100 %); a lesson leaves draft state only with a named reviewer. The guide ships in 0.8.0 with drafts labelled if review is pending.

If review is unavailable: ship labelled drafts and keep the banner; do not remove the banner on the author's own authority.

## Milestone 9: optional on-device Muaalem checker, beta (0.9.x)

Inputs:

- Model: `muaalem_tajweed_int8.onnx`, 876,466,434 bytes, SHA-256 `3ef7f9760b54161d876b5866fccd19cbcc7b793386753f515395f2d5012e9f96`, MIT tag, from [huggingface.co/Abddar/muaalem-v3-2-int8-android-onnx](https://huggingface.co/Abddar/muaalem-v3-2-int8-android-onnx) (download counts are not tracked for this repo). It is a re-embedding of a dynamically quantised export of [obadx/muaalem-model-v3_2](https://huggingface.co/obadx/muaalem-model-v3_2): 24 layers, hidden 1024, 11 CTC heads (phonemes vocab 43, two sifat heads vocab 4, eight sifat heads vocab 3), SeamlessM4TFeatureExtractor input (80 mel bins, stride 2, 160-dim frames, padding value 1).
- Benchmarks: the paper ([arXiv 2509.00094](https://arxiv.org/abs/2509.00094)) reports phoneme-level PER 0.543 % on held-out moshafs 19.0, 29.0 and 30.0 and 5.8 % on qdat_bench; "tajweed F1 75.8 %" is the mean of noon moshaddadah 0.869, ikhfaa 0.453 and qalqalah 0.953. qdat_bench ([HF](https://huggingface.co/datasets/obadx/qdat_bench)) has 159 clips of one ayah, Apache-2.0.
- Reference phonemes in the Muaalem 43-symbol alphabet: `quran-transcript` 0.6.4 (PyPI, MIT, with Tanzil CC BY 3.0 text notice) run once on the PC over all 6,236 ayahs with Husary's moshaf attributes; shipped as gzip JSON (10 to 20 MB, estimate). This is a different alphabet from the Zipformer's 251 symbols used in milestone 1.
- Runtime: `com.microsoft.onnxruntime:onnxruntime-android` 1.31.0 (MIT). A Dart port of the feature extractor exists in [azaniqiblati-spec/azani-w-qiblati](https://github.com/azaniqiblati-spec/azani-w-qiblati) but has no licence; it is read for parameter values only, never copied.

Steps:

1. PC evaluation (Kaggle or local CPU): run the PyTorch model and the int8 ONNX on all 159 qdat_bench clips and 300 clips sampled from the three held-out moshafs; record phoneme PER per model and greedy-decode string agreement per head. Confirm the ONNX Runtime Android AAR contains x86_64 for the emulator.
2. Kotlin feature extractor matching transformers 4.55.4 `SeamlessM4TFeatureExtractor`: frame 400, hop 160, FFT 512, Povey window, DC removal, pre-emphasis 0.97, power spectrum, 80 Kaldi-scale mel bins 20 Hz to 8 kHz, log with floor 1.192092955078125e-07, per-bin utterance normalisation, stride-2 stacking to 160 dims. Export the mel filterbank (80 x 257 float32) and window from Python as binary assets. Confirm the waveform x32768 scaling in the transformers source before implementing.
3. Test vectors: 50 clips (20 clean dataset clips, 20 of the user's own or volunteer phone recordings, 10 synthetic: silence, tone, clipped) with Python features and 11 logit arrays stored as `.npy`; JUnit tests load them in CI.
4. Chunking at most 15 s per call, 10 s core plus 2 s context, split at word boundaries from the tracker; greedy CTC decode per head, grouping as `quran_muaalem.decode`, Levenshtein alignment to the reference, typed errors following `quran-transcript`'s `explain_error`.
5. Download flow: Settings toggle "Tajweed checker (beta)" repeats the notice, downloads over Wi-Fi by default from Hugging Face (fallback: a GitHub Release asset of this repo, if the 2 GB asset limit allows, or R2) with Range resume and SHA-256 check, and hides the toggle when `ActivityManager.MemoryInfo.totalMem` is below 4 GB.
6. Feedback: after each ayah, per word a colour and a one-line reason in Arabic, English and Bangla; a confidence threshold per error type; ikhfaa is never shown while the teacher model's own ikhfaa F1 is 0.453.
7. Beta label and wording: the feature is called "experimental"; README and any listing say "points out likely tajweed mistakes" and nothing about verification.

Tools: Python (transformers 4.55.4 pinned, onnxruntime, quran-muaalem, quran-transcript), Kaggle (12 h sessions, 20 GB disk), ONNX Runtime Android. Cost: $0. Time: 10 to 14 weeks part-time (estimate).

Exit check, in three parts:

- PC: int8 vs fp32 phoneme decode agreement at least 97 % of clips and PER increase at most 0.3 points; qdat_bench phoneme PER within 1.0 point of 5.8 %.
- CI: Kotlin features within 1e-4 of Python after normalisation and identical greedy decodes on 50 of 50 clips for all 11 heads.
- Emulator: the model loads and returns 11 outputs for a 10 s clip pushed with `adb push`, with the AVD's RAM raised to 4 GB in `config.ini` (the host has 15 GB).

Precision gate for leaving beta: on a teacher-labelled learner set (at least 250 clips, 30 instances per error type; targets from the earlier plan: SKIP/ADD precision 0.95, SUB 0.90, MADD 0.85, SIFA 0.85), each shown error type meets its precision target. Until such a set exists (open question 3), the checker stays labelled beta and the README states that precision is unmeasured. Postponed phone check: load under 10 s, peak RAM under 1.6 GB, latency under 6 s per 10 s of audio on a 6 GB phone.

If the int8 check fails: re-export from [obadx/muaalem-v3_2-torchscript](https://huggingface.co/obadx/muaalem-v3_2-torchscript) with static int8 quantisation calibrated on 200 clips, or ship fp16 (about 1.2 GB). If the Kotlin decode check fails: fix the extractor; the feature does not ship with a mismatch.

## Later work

Quranic Arabic lessons (0.10.x) use QUL's root, stem and lemma tables ([qul.tarteel.ai/resources/morphology](https://qul.tarteel.ai/resources/morphology), six SQLite resources credited to Dr. Kais Dukes and Mustafa Jibaly, no licence stated) rather than the Quranic Arabic Corpus download, whose morphology file is under the GNU GPL ([corpus.quran.com/download](https://corpus.quran.com/download/)) and would conflict with the repository's additional conditions if bundled.

Google Play publication follows the earlier plan's phase A3: target API 36, Data safety form declaring on-device audio and no collection, internal track, then a 12-tester 14-day closed test if the account is a new personal account, then production, using the `play` flavor and the same signing key uploaded to Play App Signing.

Physical-phone testing follows the earlier plan's phases A2 and B4: a low-end (3 GB RAM) and a mid-range (6 GB RAM) device, measuring load time, RAM, word latency filmed at 60 fps, and battery over 20 minutes, with the thresholds quoted in each milestone's "postponed phone check".

## Mini model track (parallel, never blocking)

The track trains a model of about 120 M parameters that keeps all 11 Muaalem heads and runs in about 130 MB int8 (estimate), following phases C1 to C3 of the earlier plan: width-sliced w2v-BERT 2.0 (MIT) initialised from the teacher, all 24 layers kept because HifzGuide found depth cuts destroy the backbone; frame-level distillation from teacher logits produced in one RunPod RTX 4090 pass ($0.34/h, 3 to 6 GPU hours, about $1 to $2 plus storage) over [obadx/muaalem-annotated-v3](https://huggingface.co/datasets/obadx/muaalem-annotated-v3) (848 h, 97 GB, MIT tag, reciter audio rights not stated); training on Kaggle (12 h sessions, unpublished floating GPU quota) with checkpoint and resume; cash budget $0 to $20.

The gate is the one from phase C3: on the learner set, per-error-type F1 within 5 points of the teacher and the precision targets met for every shown type; on the three held-out moshafs, phoneme PER at most 1.5 %. A model that fails stays a research artifact published with its numbers.

If obadx releases weights for `muaalem-model-v3-mini` or the `w2v2-384`, `whisper-encoder-small` or `streaming-rnn-v0` experiments (config-only repos as of 2026-10-20), the released model is evaluated in milestone 9's harness before any further training is spent.

## Compliance and attribution

The About screen carries one credit entry per source below, each with the source name, what Awn uses from it, the licence or terms name, and a link. A CI test asserts that every data pack in the manifests and every model in the download list has a matching credit entry.

- Tanzil: Quran text basis of `quran-transcript`'s reference data (CC BY 3.0, verbatim, attribution and link to tanzil.net); Bangla and Malay translations if used (non-commercial, link to tanzil.net/trans).
- QuranicAudio and EveryAyah: all recitation audio, downloaded from their hosts and credited by name as QUL itself does.
- QUL (Quranic Universal Library, Tarteel): scripts, fonts, layouts, word segments, word-by-word translations, morphology; credit QUL and the upstream names from [qul.tarteel.ai/credits](https://qul.tarteel.ai/credits) (King Fahd Quran Printing Complex, Ayman Siddiqui, Dr. Amin Anane, Collin Fair, QuranWBW.com, Dr. Kais Dukes, Mustafa Jibaly).
- QuranEnc and each translation's publisher: translation text, version number shown, verbatim, "no inappropriate advertisements", updated to the latest version.
- Quran-Lab: Zipformer model, `tokens.txt` and the phoneme reference files under NPL-1.2, with the mandatory statement that automatic tajweed feedback can be wrong and does not replace a qualified teacher; the licence file ships with any redistributed copy.
- k2-fsa sherpa-onnx (Apache-2.0) and Microsoft ONNX Runtime (MIT): standard licence notices in the About screen's open-source list.
- obadx (Abdullah Abdelfattah) and Abddar: Muaalem model and its int8 export; QML-1.0 bilingual notice before first use and in About; `quran-transcript` and `quran-muaalem` packages.
- sunnah.com and the hadith translators named per snippet.

Code from Quran for Android ([quran/quran_android](https://github.com/quran/quran_android), GPL-3.0) is never copied; only its data sources (QuranicAudio URL patterns, the `.org` fallback, QuranEnc) are reused, and a line in the README says so. Code from Natlu, `recite_quran` and azani-w-qiblati is likewise never copied.

The repository `LICENSE` carries the no-ads, no-charge and AI-notice conditions so that any fork inherits them. The README's feature list describes the checker as "experimental feedback on likely mistakes".

## Data sources and licences

| Source | Provides | Licence or terms as known | Attribution required |
|---|---|---|---|
| Quran-Lab/zipformer_p-arabic-v3 (HF, gated, accepted) | 69 MB streaming phoneme CTC model, `tokens.txt`, phoneme reference JSON | NPL-1.2: no charging, feedback-can-be-wrong notice; redistribution only within free use | Yes, plus notice |
| k2-fsa sherpa-onnx 1.13.8 (AAR) | Streaming CTC inference, Kaldi fbank | Apache-2.0 | Notice |
| obadx/muaalem-model-v3_2 (HF) | Teacher model, 606 M parameters | MIT tag on HF; GitHub repo under QML-1.0 (Apache-2.0 + notice + no ads + Sharia clause, covers derivatives) | Yes, plus bilingual notice |
| Abddar/muaalem-v3-2-int8-android-onnx | 876 MB int8 ONNX | MIT tag | Yes |
| Microsoft onnxruntime-android 1.31.0 | On-device inference for the checker | MIT | Notice |
| obadx/quran-transcript 0.6.4 | Reference phonemes (Muaalem alphabet), error typing | MIT code; Tanzil CC BY 3.0 text notice | Tanzil link |
| obadx/muaalem-annotated-v3 | 848 h labelled recitation (mini track) | MIT tag; reciter audio rights not stated | Yes |
| obadx/qdat_bench | 159 learner clips, one ayah | Apache-2.0 | Yes |
| sysofwan/hifzguide-muaalem-mini | Not used (phonemes only) | AGPL-3.0, conflicts with no-profit conditions | Not used |
| facebook/w2v-bert-2.0 | Base for mini model | MIT | Yes |
| QuranicAudio / EveryAyah | Surah and ayah MP3s | No licence published; public hosts used by Quran for Android | Credit by name |
| QUL recitations with segments | Word timings for 59 recitations | No licence stated; QUL asks third parties to self-host audio, not use its CDN | Credit QUL and Collin Fair |
| QUL scripts, fonts, layouts | QPC V1/V2/V4, Hafs Unicode, IndoPak, 604-page layouts | No licence stated; credits name King Fahd Complex and font authors | Credit QUL and authors |
| QUL translations and word-by-word | Bangla full translations, 22 wbw sets | Per resource; some marked copyrighted (Clear Quran, German and Russian wbw) | Credit; check each |
| QuranEnc | 76 translations with API | Verbatim, credit publisher and QuranEnc, show version, keep updated, no inappropriate ads | Yes |
| Tanzil translations | Bangla (Muhiuddin Khan, Zohurul Hoque), Malay, others | Non-commercial; link back if more than three used; list not redistributable | Link to tanzil.net/trans |
| sunnah.com | Hadith text and references | No redistribution terms published; API key by GitHub issue | Credit and link |
| QUL morphology | Root, stem, lemma tables | No licence stated | Credit Dukes and Jibaly |
| Quranic Arabic Corpus download | Morphology v0.4 | GNU GPL | Not bundled |
| py-fsrs (open-spaced-repetition) | Reference vectors for the Kotlin FSRS port | MIT | Notice if formulas are copied |
| Cloudflare R2 | Fallback file host | Free tier 10 GB, free egress | None |

## CI/CD and repository workflow

The repository follows GitHub Flow: each feature or fix lives on a branch named `<issue>-<slug>` (for example `42-add-bookmarks`), reaches `main` through a pull request that says `Refs #n` or `Fixes #n`, is squash-merged, and the branch is deleted. Small direct commits to `main` are allowed for docs, typos and config. Branch protection requires CI to pass; a review is required once outside contributors start sending pull requests.

Before any push the author runs, in order: `./gradlew spotlessCheck detekt lintGithubDebug testGithubDebugUnitTest`; then `./gradlew connectedGithubDebugAndroidTest` on the headless emulator on this machine; then `./gradlew installGithubDebug` on the user's phone over USB or wireless ADB. CI repeats the Gradle steps and the emulator step.

Four GitHub Actions workflows are planned, each with minimal `permissions:` and actions pinned to commit SHAs, never using `pull_request_target`:

- `ci.yml` on every pull request and push to `main`: `actions/setup-java` with `distribution: temurin` and `java-version: 21`, `gradle/actions/setup-gradle` for the Gradle cache, then format check, detekt, Android Lint, unit tests, a Quran-text integrity test (SHA-256 of the bundled Quran text and of each translation pack against the checksums recorded from the source), and an unsigned debug build. The Zipformer int8 model (72,705,392 bytes) is downloaded from the Quran-Lab Hugging Face repo with a read-only fine-grained `HF_TOKEN` secret, verified by SHA-256 and cached with `actions/cache` keyed by that hash so tracker tests run on the real model; fork pull requests have no secret and skip those tests. The 876 MB Muaalem model is never downloaded in CI; its tests use the 50-clip fixtures.
- `smoke.yml` on pull requests to `main` (nightly if too slow): `reactivecircus/android-emulator-runner` boots an emulator and runs `connectedGithubDebugAndroidTest`, which launches the app and opens a Mushaf page.
- `release.yml` on tags `v0.*`: signed split-per-ABI APKs from the keystore in GitHub Secrets, a SHA-256 checksum file, `actions/attest-build-provenance`, and `gh release create --prerelease --generate-notes`. The Android `versionCode` is derived from the tag in Gradle as `MAJOR*10000 + MINOR*100 + PATCH` plus a per-ABI offset and is never edited by hand. One signing key serves GitHub Releases and, later, Google Play App Signing; losing it means users can never update, so it exists only in GitHub Secrets and an offline backup.
- `dependabot.yml` weekly for `gradle` and `github-actions`.

The app has two Gradle product flavors: `github` (checks GitHub Releases for updates in-app) and `play` (no self-update). Issue templates cover bug, feature and content error (Quran text, translation or word timing, top priority); a pull request template and `CONTRIBUTING.md` state that contributions fall under the repository licence. GitHub milestones map to the `0.x` version ranges above. The full agent-facing description of commands, verification order and exclusive resources is in `AGENTS.md`.

## Risks

- The independent tracker is new engineering with no published baseline: the alignment thresholds in milestone 1 step 5 are guesses until the fixtures and the user's own recordings are run. The fallback is to tune on fixtures first and to ship 0.1.0 with tracking marked "experimental" if the 95 % gate is missed by less than 5 points.
- A QUL segment file does not match the QuranicAudio recording of the same reciter, so word seeks land on the wrong word. Milestone 3 step 4 measures this per reciter before the reciter is enabled; a mismatch demotes the reciter to ayah-level playback.
- QuranicAudio changes paths or blocks app traffic. The `.org` fallback and the reciter table's URL fields allow a hotfix release; R2 mirroring (3 to 4.5 GB for five reciters at 64 kbps, estimate) is the fallback host.
- The `HF_TOKEN` is revoked or the Quran-Lab repo moves. CI then fails on the model download step; the cached copy keeps builds green until the cache expires (7 days unused), and the file is re-fetched by hand into the cache or a release asset.
- Licence conflict between the HF MIT tag and QML-1.0 on Muaalem derivatives. Awn complies with QML-1.0 (the stricter) regardless, so the conflict has no effect on the app.
- The emulator cannot load the 876 MB model. A 4 GB AVD fits the 15 GB host, but the x86_64 int8 kernels may be slow or fail; if so, milestone 9's model test runs on the PC with onnxruntime in Python and the emulator check is deferred to phone testing.
- Zipformer accuracy on the user's own voice is lower than the card's 3.65 % phone-audio PER (the card also reports 24 stray training clips in its studio benchmark and 9.1 % on an unseen reciter). Tracking stays usable at that level, but skip flags become noisy; thresholds are tuned on the user's own recordings in milestone 1.
- QUL has no stated licence for fonts, scripts and segments. QUL's own page says resources are "intended for you to download and package with your own project"; the plan relies on that statement plus full credits.
- Tanzil's non-commercial term and QuranEnc's "update to latest version" term impose ongoing duties; the monthly manifest check in milestone 4 and the no-profit licence cover them.
- No teacher-labelled learner set exists, so the checker's precision cannot be measured and it stays in beta indefinitely. This is open question 3.
- The broken JDK 21 on the development machine is silently picked by a tool that ignores `JAVA_HOME`; every build log must show Gradle running on Temurin 21.0.12.1 until `jdk21-openjdk` is reinstalled.
- Repository disk: fonts, translation packs and models must never be committed (the `.gitignore` already excludes model and audio formats; add `*.ttf`, `*.woff2` and `*.sqlite` packs outside `assets/`).

## Open questions for the user

1. Application id and package name: `org.awn.quran`, `app.awn.quran`, or another domain you control?
2. Reciters for 0.3.0: confirm Husary (Murattal), Afasy, Abdul Basit Murattal, Minshawi and Sudais, or substitute; and whether Husary Muallim (teaching recitation, QuranicAudio path `husary_muallim/`) should be included for hifz.
3. Do you know a qualified teacher (with ijazah) who would review the tajweed lessons (milestone 8) and, later, label learner recordings for the checker's precision gate (milestone 9)? Without one, lessons ship as labelled drafts and the checker stays beta.
4. Which phone will receive the pre-push debug installs, and over USB or wireless ADB?
5. Bangla full translation for 0.4.0: QUL's Dr. Abu Bakr Muhammad Zakaria (the King Fahd Complex Bangla translation) or Tanzil's Muhiuddin Khan (non-commercial term, link-back)?
6. Zipformer delivery: (a) bundle the 72.7 MB int8 file in the APK, or (b) download it on first launch from a GitHub Release asset of this repository. Both redistribute the file, which NPL-1.2 permits only within free, no-charge use, so the licence text travels with it either way. Recommendation: (a) bundle. The app then works offline from the first launch, CI already holds the verified file, there is no download or checksum code path to maintain in 0.1, and a per-ABI APK of about 90 to 100 MB (estimate) is far below Play's 500 MB base limit; switch to (b) only if APK size becomes a complaint.
7. Should the checker's beta toggle be visible to all users in 0.9.0, or hidden behind a developer gesture until the precision gate passes?
8. Which languages should the app's own interface support at 0.1.0: English and Bangla only, or Arabic too?
