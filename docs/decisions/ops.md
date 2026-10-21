# Ops

2026-10-20 | Create ~/.gradle/gradle.properties entry org.gradle.java.home=/home/rms/.gradle/jdks/temurin-21 | system JDK is corrupt
2026-10-20 | keytool -genkeypair -keystore ~/.gradle/awn-release.jks -storetype PKCS12 -alias awn -keyalg RSA -keysize 4096 -validity 10000 (random 32-char password) and the four AWN_* properties appended to ~/.gradle/gradle.properties | release signing key; maintainer must back it up offline
2026-10-20 | sdkmanager --sdk_root=$HOME/android-sdk "platforms;android-37.0" and accepted licences | AndroidX dependencies require compileSdk 37
2026-10-20 | Manual, not done: add GitHub secrets AWN_KEYSTORE_BASE64 (base64 -w0 of the jks), AWN_KEYSTORE_PASSWORD, AWN_KEY_ALIAS, AWN_KEY_PASSWORD | release.yml needs them
2026-10-20 | Manual, not done: gh release create models-v1 with the files in awn-models/ | fetchModels downloads from it when awn-models/ is empty
2026-10-21 | `cp -al awn-data awn-data-v2`, then `AWN_DATA=.../awn-data-v2 python3 -I tools/build_*.py` | second catalogue built beside the first; build_translations wrote 6 TERMS.md through hard links into awn-data (text only, packs unchanged), scripts now unlink first
2026-10-21 | `python3 -I tools/build_quantum_pack.py --add-pack-info awn-data/translations/bn_quantum.sqlite sessions/upload/bn_quantum.sqlite`, then a one-off call of `build_manifests.pack_entry` to refresh only the bn_quantum sha256 and bytes in manifests/translations.json, then the new hash in core/data/bundled-packs.sha256 | the first bn_quantum had no pack_info, so every install failed as "not a readable pack database"; the site was not re-fetched, rows are unchanged
2026-10-21 | `gh release upload data-v1 sessions/upload/bn_quantum.sqlite --clobber` and `gh release upload data-v1 awn-data/manifests/translations.json --clobber` (old copies kept in sessions/upload) | the app and CI read data-v1 and check each sha256
