# Ops

2026-10-20 | Create ~/.gradle/gradle.properties entry org.gradle.java.home=/home/rms/.gradle/jdks/temurin-21 | system JDK is corrupt
2026-10-20 | keytool -genkeypair -keystore ~/.gradle/awn-release.jks -storetype PKCS12 -alias awn -keyalg RSA -keysize 4096 -validity 10000 (random 32-char password) and the four AWN_* properties appended to ~/.gradle/gradle.properties | release signing key; maintainer must back it up offline
2026-10-20 | sdkmanager --sdk_root=$HOME/android-sdk "platforms;android-37.0" and accepted licences | AndroidX dependencies require compileSdk 37
2026-10-20 | Manual, not done: add GitHub secrets AWN_KEYSTORE_BASE64 (base64 -w0 of the jks), AWN_KEYSTORE_PASSWORD, AWN_KEY_ALIAS, AWN_KEY_PASSWORD | release.yml needs them
2026-10-20 | Manual, not done: gh release create models-v1 with the files in awn-models/ | fetchModels downloads from it when awn-models/ is empty
