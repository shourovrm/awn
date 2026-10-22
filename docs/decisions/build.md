# Build

## Current state
- Gradle 9.8.2 wrapper (checksum pinned), AGP 9.4.1 with built-in Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00, all versions in gradle/libs.versions.toml.
- Build logic is in buildSrc: awn.android-library, awn.compose, AwnVersion, FetchVerifiedFilesTask, FetchNativeLibsTask.
- compileSdk 37 (Compose 1.12, Navigation 2.10 and core-ktx 1.19 refuse 36), targetSdk 36, minSdk 26. SDK platform android-37.0 is installed in ~/android-sdk.
- Flavors github and play (dimension channel). Only release builds are used; library modules and the app set testBuildType release.
- Signing reads AWN_KEYSTORE_PATH, AWN_KEYSTORE_PASSWORD, AWN_KEY_ALIAS, AWN_KEY_PASSWORD from Gradle properties or environment. Packaging fails with a clear message when any is missing.
- Local keystore: ~/.gradle/awn-release.jks (PKCS12, RSA 4096, alias awn, 10000 days); properties are in ~/.gradle/gradle.properties. Certificate SHA-256 is in README.md. Backing it up is the maintainer's job; losing it blocks all updates.
- CI signs with a per-run throwaway key (fork PRs have no secrets). release.yml expects secrets AWN_KEYSTORE_BASE64, AWN_KEYSTORE_PASSWORD, AWN_KEY_ALIAS, AWN_KEY_PASSWORD.
- versionName and versionCode come from git: tag vMAJOR.MINOR.PATCH on HEAD gives M*10000+m*100+p; otherwise 0.0.0-<commit count> and the count. ABI offsets in the millions place: armeabi-v7a 1, arm64-v8a 2, x86_64 3.
- ABI splits arm64-v8a, armeabi-v7a, x86_64; no universal APK. R8 and resource shrinking on; the only keep rule is com.k2fsa.sherpa.onnx.** (native code reads fields by name).
- fetchNativeLibs verifies gradle/native-libs.sha256, then unpacks classes.jar from the AAR to libs/sherpa-onnx-classes.jar and the Linux .so files to libs/linux-x64/. fetchModels ensures awn-models/ matches gradle/models.sha256, downloading from https://github.com/shourovrm/awn/releases/download/models-v1 (owner assumed from the gh login).
- :tracker tests set java.library.path and LD_LIBRARY_PATH to libs/linux-x64 and depend on both fetch tasks.
- Third-party coordinates (okhttp, media3-datasource-okhttp, coroutines-android and -test, espresso, ui-test-manifest) are in the catalog; lint rule UseTomlInstead is on in every module.
- App assets: only the Learn lessons and snippets JSON. The model is no asset any more; `fetchModels` serves the `:tracker` tests only.
- `:core:data` asset and lint tasks depend on `fetchBundledPacks` (quran.sqlite, Hafs font, three manifests); its unit-test tasks also depend on `fetchTestPacks` (five translation and word packs into build/test-packs). A worktree's own `awn-data/published/` wins over `$AWN_DATA_DIR/published`.
- Gates: Spotless with ktfmt 0.65 kotlinlang style, detekt 1.23.8 (config/detekt/detekt.yml), Android Lint with warningsAsErrors. Disabled lint checks: GradleDependency, NewerVersionAvailable, AndroidGradlePluginVersion (network-dependent, Dependabot covers them); in :app also OldTargetApi (target 36 on purpose) and ObsoleteSdkInt (aapt2 does not resolve mipmap-anydpi without -v26).
- Robolectric tests need --add-exports java.base/jdk.internal.access=ALL-UNNAMED, set in the library convention.

## Open items
- No CI step yet for Quran-text checksums (no text exists).
- smoke.yml runs connectedGithubReleaseAndroidTest, but no androidTest exists yet.
- Play flavor has no distinct behaviour yet.

## Gotchas
- R8 on :app runs inside the Gradle daemon and needs about 1.5 GB: the user-level `-Xmx1g` makes `minifyGithubReleaseWithR8` fail with OutOfMemoryError. Pass `-Dorg.gradle.jvmargs="-Xmx2g -XX:+UseParallelGC"` or raise the user setting.
- Inside a task configuration block, `path` is the task path; capture the project path first.
- Room schema JSON reaches the Robolectric test through variant.nestedComponents UnitTest sources.assets.
- `by tasks.registering` is deprecated in Gradle 9.8; use tasks.register.
- Subclass @TaskAction runs before the superclass action; FetchNativeLibsTask uses an afterVerified hook instead.

## Tried / rejected
- compileSdk 36 with the current BOM: dependency metadata check fails.
- Linux jar as the source of sherpa Kotlin classes: it contains only .so files.
