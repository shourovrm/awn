# Awn (عون)

Awn is a free Android app for reading the Quran, listening to reciters and following your own recitation word by word on the device. It has no advertisements, no accounts, no charges and no in-app purchases. The speech model runs on the phone, so recitation audio never leaves it.

The source is available under MIT plus the Awn additional conditions (see `LICENSE`). Because those conditions restrict selling, the project is source-available and is not OSI open source.

Status: milestone 1 (skeleton). The app starts, shows the notice, and has empty Read, Listen, Recite and Learn screens. Plan: `docs/plan.md`.

## Install

1. Download the APK for your phone from the latest release on the GitHub Releases page. Most phones need `arm64-v8a`; older 32-bit phones need `armeabi-v7a`; emulators on a PC need `x86_64`.
2. Check the file against `SHA256SUMS.txt` from the same release: `sha256sum -c SHA256SUMS.txt`.
3. Open the APK on the phone and allow installation from your browser or file manager when Android asks.

Android 8.0 (API 26) or newer is required.

## Verify the signature

Every release APK is signed with one key. Its SHA-256 certificate fingerprint is:

```
E7:AC:D7:83:3F:50:FA:B5:ED:90:D2:AB:33:CB:26:31:9F:E1:39:8B:25:3A:8F:21:3C:CD:70:67:CC:3D:DA:34
```

Check an APK with `apksigner verify --print-certs app-github-arm64-v8a-release.apk` from the Android SDK build-tools; the line `Signer #1 certificate SHA-256 digest` must equal the fingerprint above without the colons, in lower case. If it differs, do not install the file.

## Build and run

Requirements: Temurin JDK 21, the Android SDK with platform 37 and build-tools 36.0.0, and a signing key.

```
export JAVA_HOME=/path/to/temurin-21
export ANDROID_HOME=$HOME/android-sdk        # platform 37.0 and build-tools 36.0.0 installed
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

Every build is a signed release build. Put the signing key in `~/.gradle/gradle.properties` (never in the repository):

```
AWN_KEYSTORE_PATH=/path/to/your.jks
AWN_KEYSTORE_PASSWORD=...
AWN_KEY_ALIAS=...
AWN_KEY_PASSWORD=...
```

Make a test key with `keytool -genkeypair -keystore ~/my-awn.jks -alias awn -keyalg RSA -keysize 4096 -validity 10000`. An APK signed with your key cannot update the official one.

```
./gradlew assembleGithubRelease      # APKs in app/build/outputs/apk/github/release/
./gradlew installGithubRelease       # to a connected phone or running emulator
```

The first build downloads the sherpa-onnx libraries (`fetchNativeLibs`) and the pinned Quran text and manifests (`:core:data:fetchBundledPacks`) and checks their SHA-256 hashes. `./gradlew fetchModels` fetches the speech model for the tracker tests; the app itself downloads it on first use of recite.

## Test

```
./gradlew spotlessCheck detekt lintGithubRelease testGithubReleaseUnitTest
./gradlew connectedGithubReleaseAndroidTest      # needs an emulator or phone
```

`spotlessApply` formats the code. The tracker tests run on the PC against the real model and need no emulator.

## Notice

English: This app is an artificial-intelligence model that may make mistakes. It is intended for assistance only and must not be relied upon. It is not a replacement for a proficient, authorized teacher, because the Quran is only received through oral transmission (mushafahah).

বাংলা: এই অ্যাপ একটি কৃত্রিম বুদ্ধিমত্তা (এআই) মডেল, যা ভুল করতে পারে। এটি কেবল সহায়তার জন্য; এর ওপর নির্ভর করা উচিত নয়। এটি একজন দক্ষ ও অনুমোদিত শিক্ষকের বিকল্প নয়, কারণ কুরআন কেবল মৌখিক ধারায় (মুশাফাহা) গ্রহণ করা হয়।

العربية: هذا المعلّم القرآني نموذج ذكاء اصطناعي قد يخطئ؛ فهو للإعانة فقط ولا يُعتمد عليه، وليس بديلاً عن معلّمٍ مُتقِنٍ مُجاز؛ لأنّ القرآن لا يُؤخذ إلا بالمشافهة.

## Credits

- Quran-Lab, Zipformer P-Arabic v3 speech model, under the NPL-1.2 licence (`awn-models/NPL-1.2.txt` in the models release): https://huggingface.co/Quran-Lab/zipformer_p-arabic-v3
- k2-fsa, sherpa-onnx, runs the model on the device: https://github.com/k2-fsa/sherpa-onnx
- Microsoft, ONNX Runtime, inside sherpa-onnx: https://github.com/microsoft/onnxruntime
- Quran.com / Tarteel QUL, Quran text, layouts, fonts and word timings (used from milestone 2): https://qul.tarteel.ai
- QuranicAudio and EveryAyah, reciter audio (used from milestone 3).
- Google, Jetpack Compose, Room, DataStore, Navigation and Media3 (Apache-2.0).

### Translations

- Saheeh International, English, via Quran.com / Quran Foundation API: https://quran.com
- M.A.S. Abdel Haleem, English, via Quran.com / Quran Foundation API: https://quran.com
- Muhammad Asad, *The Message of the Qur'an*, English, translation only (no footnotes), via Al Quran Cloud (Islamic Network), edition en.asad: https://alquran.cloud
- Dr. Abu Bakr Muhammad Zakaria, Bangla, via Quran.com / Quran Foundation API: https://quran.com
- Quantum Foundation, Bangla (meaning-based): https://alquran.org.bd
- English transliteration and word-by-word meanings in English and Bangla, via Quran.com / Quran Foundation API: https://quran.com

Each data source is also credited in the app's About screen and in the pack manifests.

## Contributing

See `CONTRIBUTING.md`. Contributions are accepted under the repository licence.
