# Awn - Quran & Tajweed Helper (عون)

Free, ad-free, account-free native Android app (Kotlin, Jetpack Compose, Android only) for reading the Quran and following recitation word by word on the device with on-device ONNX models; for Muslims learning to recite and memorise. Licence: MIT plus Awn additional conditions (free to all end users, no ads, no charges or in-app purchases, AI-advisory notice, conditions pass through to forks). Application id `app.awn.quran`. The repository is source-available, not OSI open source; F-Droid is out of scope for that reason. Awn depends on no other Quran app's code; the plan is `docs/plan.md`.

## Commands
The Gradle project is not scaffolded yet; the commands below are the intended ones. Stack: Gradle wrapper 9.8.2, AGP 9.4, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk/targetSdk 36, minSdk 26, versions in `gradle/libs.versions.toml`. No global Gradle is installed (the wrapper handles it).
- JDK: the system `jdk21-openjdk` is corrupt (`java` fails with "libjli.so: invalid ELF header"; `pacman -Qkk jdk21-openjdk` reports 193 mismatches). Use the working Temurin 21 at `~/.gradle/jdks/temurin-21` on every Gradle call: `JAVA_HOME=~/.gradle/jdks/temurin-21 ./gradlew ...`, or set `org.gradle.java.home` in the user-level `~/.gradle/gradle.properties` (never in the repo). CI uses Temurin 21 via `actions/setup-java`, so local and CI match.
- Android SDK: `~/android-sdk` (platforms 33–36, build-tools 36.0.0). Shell: `ANDROID_HOME=$HOME/android-sdk`, `ANDROID_AVD_HOME=$HOME/.config/.android/avd`, and `unset ANDROID_SDK_ROOT` (the shell default points at a read-only `/opt` SDK with no images).
- Setup: `./gradlew help` (downloads the wrapper's Gradle); `sdkmanager --sdk_root=$HOME/android-sdk --licenses`.
- Build: `./gradlew assembleGithubRelease`. There are no debug builds: every build is a signed release build. Local signing reads the keystore path and passwords from `~/.gradle/gradle.properties` (never from the repo); CI reads GitHub Secrets. `android.testBuildType = "release"` so instrumented tests run on release builds.
- Native and model inputs: sherpa-onnx has no Maven artifact. `./gradlew fetchNativeLibs` downloads `sherpa-onnx-static-link-onnxruntime-1.13.8.aar` and the Linux JNI jar from the k2-fsa release and `./gradlew fetchModels` downloads the Zipformer files from this repo's `models-v1` data release; both verify SHA-256 and write into gitignored folders.
- Test: `./gradlew testGithubReleaseUnitTest` (includes `:tracker` JVM tests on the Linux sherpa-onnx jar); instrumented and Compose UI tests: `./gradlew connectedGithubReleaseAndroidTest` (needs the emulator or the phone).
- Lint / format: `./gradlew spotlessApply` to format (Spotless + ktfmt); `./gradlew spotlessCheck detekt lintGithubRelease` to check.
- Run locally: `~/android-sdk/emu.sh start` (headless, blocks until boot; ~3.2 GB host RAM, stop when idle), then `./gradlew installGithubRelease && adb shell am start -n app.awn.quran/.MainActivity`. Phone: `adb devices`, then the same install task with `ANDROID_SERIAL=<serial>`.
- Verify before any push, in order: 1 `./gradlew spotlessCheck`; 2 `./gradlew detekt lintGithubRelease`; 3 `./gradlew testGithubReleaseUnitTest`; 4 `./gradlew connectedGithubReleaseAndroidTest` on the headless emulator; 5 `./gradlew installGithubRelease` on the user's phone over USB or wireless ADB; 6 push. CI repeats 1 to 4.
- Release, in order: 1 confirm `main` is green and `DECISIONS.md` Next holds no shipped work; 2 `git tag v0.MINOR.PATCH` (every release is a GitHub pre-release; no 1.0 planned; a release may close several milestones); 3 `git push --tags`; 4 `release.yml` builds signed split-per-ABI APKs, writes SHA-256 checksums, attests provenance and runs `gh release create --prerelease --generate-notes`; 5 Google Play upload (later, `bundlePlayRelease`, same signing key). `versionCode` = MAJOR*10000 + MINOR*100 + PATCH plus a per-ABI offset of 1 to 3 in the millions place, computed in Gradle from the tag (untagged builds use 0.0.0 plus commit count), never edited by hand.
- Data releases: packs and models live under data tags (`data-v1`, `models-v1`) of this repo's GitHub Releases, separate from app tags, so old app versions keep working.
- Exclusive resources (one session at a time): the headless emulator (`emu.sh`), the ADB-connected phone.

## Memory — MANDATORY
`DECISIONS.md` is an index. Knowledge lives in `docs/decisions/`. Git history holds events.

Files and hard caps:
- `DECISIONS.md` — at most 40 lines, no line over 200 characters. Sections: **Topics** (one line per topic file), **Next** (in-flight handoff, at most 5 bullets), **Gotchas (cross-cutting)** (only what every session needs).
- `docs/decisions/<topic>.md` — one per area, at most 80 lines. Sections: **Current state** (a snapshot: what is true now), **Open items** (at most 5), **Gotchas**, **Tried / rejected** (one line: what + why dead; never re-attempt).
- `docs/decisions/ops.md` — data fixes, config changes, manual production steps: date, exact command or SQL, why. Never in the log.
- `docs/decisions/LOG.md` — append at the end, one line per decision under 160 characters: `YYYY-MM-DD | decision | why`. Read recent entries with `tail -20`; otherwise grep.

Rules:
- Current state is rewritten, not extended. When a fact changes, delete the old sentence. A dated paragraph is history and belongs in LOG.md, not here.
- Shipped work leaves Current state and Next in the same commit as the release. Releases, deploys and version bumps are never logged; the tag and the release page record them.
- If a log entry needs more than one line, the detail goes in the topic file and the log line names the topic.
- Before any work: read `DECISIONS.md`, then only the topic files the task touches. Before trying a new approach: `grep -ri <keyword> docs/decisions/`.
- Update in the same commit as the change it describes. New topic = new file + one index line.
- Pruning trigger: if any file is over its cap, pruning is the first task of the next session, before feature work. Check with:
  `wc -l DECISIONS.md docs/decisions/*.md; awk 'length > 200 {print FILENAME": "FNR}' DECISIONS.md`
- If the layout is missing, create `DECISIONS.md`, `docs/decisions/LOG.md` and `docs/decisions/ops.md`. If an old single-file `DECISIONS.md` exists, split it into topics first and cut every shipped/deployed line from the log.

## Local-only files
`AGENTS.md`, `CLAUDE.md`, `DECISIONS.md`, `docs/decisions/` and `docs/plan.md` are committed (public repo, future contributors). `sessions/` (exported chats) and `.claude/` are local-only and gitignored. Model weights, audio, fonts and data packs are never committed (see `.gitignore`); they are downloaded at build or run time.

## Project conventions
These apply to every contributor and agent; the maintainer's global rules are not available to outside contributors, so the essentials are repeated here.
- Commits: Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`, `ci:`), imperative subject under 50 characters, body says why. One logical change per commit. No AI co-author trailers.
- Branching: GitHub Flow. Feature and fix work goes on a branch named `<issue>-<slug>` (for example `42-add-bookmarks`), pull request to `main` with `Refs #n` or `Fixes #n`, squash merge, branch deleted after merge. Small direct commits to `main` are allowed for docs, typos and config tweaks. Agents commit on the branch after each completed change.
- Before saying "done": format, lint and tests run locally with real output shown; anything that could not be run is stated first.
- Secrets (keystore, passwords, `HF_TOKEN`) live only in GitHub Secrets and the maintainer's offline backup, never in git, logs or chat. Losing the signing key means users can never update.
- Change only what the task requires; say what was left alone and why. Code is read in six months by someone else: full names, obvious code over clever code, comments explain why.
- Independence: never copy code from Quran for Android (GPL-3.0), Natlu, `recite_quran` or azani-w-qiblati; only public data sources and published parameter values are reused. Those projects are a last-resort reference when truly stuck, not a dependency.
- Wording: never claim "makharij verification" or that the app replaces a teacher; the checker is "experimental feedback on likely mistakes". The AI-advisory notice stays in the first-launch screen, About, `NOTICE` and the README, in English, Bangla and Arabic. UI text is written from the user's point of view: short, conventional, no explanations, no filler.
- Data integrity: every bundled Quran text and translation pack has a recorded source checksum; CI fails if it changes. Content errors (Quran text, translation, word timing) are the top-priority issue type.

## CI/CD (target setup, GitHub Actions)
- `ci.yml` on every PR and push to `main`: `actions/setup-java` (distribution temurin, java-version 21), `gradle/actions/setup-gradle` for the Gradle cache, `fetchNativeLibs`, `fetchModels`, then `spotlessCheck`, `detekt`, `lintGithubRelease`, `testGithubReleaseUnitTest` (tracker tests run on the JVM against the real Zipformer from the `models-v1` data release, cached with `actions/cache` keyed by its SHA-256; no Hugging Face token is needed, so fork PRs run them too), the Quran-text integrity test (checksums of bundled text and each translation against recorded source checksums), and `assembleGithubRelease`. The 876 MB Muaalem model is never downloaded in CI; use the small fixtures.
- `smoke.yml` on PRs to `main` (move to nightly if slow): `reactivecircus/android-emulator-runner` runs `connectedGithubReleaseAndroidTest`, which launches the app and opens a mushaf page.
- `release.yml` on tags `v0.*`: signed split-per-ABI APKs from secrets, SHA-256 checksum file, `actions/attest-build-provenance`, `gh release create --prerelease --generate-notes`.
- `dependabot.yml` weekly for `gradle` and `github-actions`.
- Security: minimal `permissions:` per workflow (`contents: read`; release job `contents: write` plus `attestations: write`, `id-token: write`); actions pinned to commit SHAs; never `pull_request_target`; secrets never available to fork PRs; "require approval for first-time contributors" stays on.
- Branch protection on `main`: CI required; no required review while solo, 1 approval once outside contributors send PRs; squash merge only; auto-delete branches. No CODEOWNERS, no hotfix branches, no alpha/beta/rc ladder.
- Gradle product flavors: `github` (in-app update check via the GitHub Releases API) and `play` (no self-update). One signing key for both channels; later uploaded to Play App Signing.
- Repo files: issue templates (bug, feature, content error), PR template, `CONTRIBUTING.md` stating contributions are under the repo licence. GitHub milestones per `0.x` version; a Project board (Backlog → Doing → Done) is welcome.

## Parallelism
When tasks touch disjoint files, run cost-efficient subagents in parallel in the background. Serialize only true conflicts: same-file edits and the exclusive resources listed under Commands. Build-tool and git locks self-queue; agents retry once on lock errors. Subagents never run the build or claim a build passed; the main session builds and verifies.

## Agent model tiers
- cheapest: search, fetch, docs lookup, transcription
- mid: tests, implementation, verification (fresh-eyes review after every logic-heavy task)
- strong: complex judgement, adversarial review of risky modules (licence compliance, Quran-text and translation integrity, the tracker's alignment logic, release signing), final whole-branch review
- main session: orchestration, builds, and final review; delegate work that touches three or more files or needs research

## Token economy
Terse replies, no filler; YAGNI; stdlib before dependencies; shortest working diff. Keep the `caveman` skill active if it is available.
