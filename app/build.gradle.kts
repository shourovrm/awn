import app.awn.build.AwnVersion
import com.android.build.api.variant.FilterConfiguration

plugins {
    id("com.android.application")
    id("awn.compose")
}

val fetchNativeLibs = rootProject.tasks.named("fetchNativeLibs")

// ---------------------------------------------------------------------------------------------
// Version, derived from git so it is never edited by hand.
// ---------------------------------------------------------------------------------------------

fun gitOutput(vararg arguments: String): String? {
    val execution = providers.exec {
        commandLine("git", *arguments)
        workingDir = rootDir
        isIgnoreExitValue = true
    }
    val succeeded = execution.result.get().exitValue == 0
    return if (succeeded) execution.standardOutput.asText.get().trim() else null
}

val versionTag =
    gitOutput("tag", "--points-at", "HEAD")?.lines()?.firstOrNull { it.startsWith("v") }
val commitCount = gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 0
val awnVersion = AwnVersion.fromGit(exactTag = versionTag, commitCount = commitCount)

// ---------------------------------------------------------------------------------------------
// Signing. Every build is a signed release build; the key never lives in the repository.
// ---------------------------------------------------------------------------------------------

fun signingValue(name: String): String? =
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull

val keystorePath = signingValue("AWN_KEYSTORE_PATH")
val keystorePassword = signingValue("AWN_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("AWN_KEY_ALIAS")
val releaseKeyPassword = signingValue("AWN_KEY_PASSWORD")
val signingConfigured =
    listOf(keystorePath, keystorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

android {
    namespace = "app.awn.quran"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.awn.quran"
        minSdk = 26
        targetSdk = 36
        versionName = awnVersion.versionName
        versionCode = awnVersion.baseVersionCode.coerceAtLeast(1)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    flavorDimensions += "channel"
    productFlavors {
        // In-app update check against the GitHub Releases API.
        create("github") { dimension = "channel" }
        // Google Play updates the app; no self-update code.
        create("play") { dimension = "channel" }
    }

    signingConfigs {
        if (signingConfigured) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signingConfigured) signingConfig = signingConfigs.getByName("release")
        }
    }

    // No debug build is used anywhere, so instrumented tests run against the release build too.
    testBuildType = "release"

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // These checks query the network for newer versions; Dependabot reports them instead.
        // OldTargetApi: targetSdk stays at 36, the level Google Play requires, until the app
        // has been tested on 37. ObsoleteSdkInt: aapt2 cannot resolve the launcher icon from a
        // mipmap-anydpi folder without the -v26 suffix, even with minSdk 26.
        disable +=
            setOf(
                "GradleDependency",
                "NewerVersionAvailable",
                "AndroidGradlePluginVersion",
                "OldTargetApi",
                "ObsoleteSdkInt",
            )
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi =
                output.filters
                    .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                    ?.identifier
            val versionCode =
                if (abi == null) awnVersion.baseVersionCode.coerceAtLeast(1)
                else awnVersion.versionCodeForAbi(abi)
            output.versionCode.set(versionCode)
            output.versionName.set(awnVersion.versionName)
        }
    }
}

// Packaging without a key would produce an unsigned APK that cannot be installed or updated.
val requireSigningProperties =
    tasks.register("requireSigningProperties") {
        val isSigningConfigured = signingConfigured
        doLast {
            if (!isSigningConfigured) {
                throw GradleException(
                    "Release signing is not configured. Set AWN_KEYSTORE_PATH, AWN_KEYSTORE_PASSWORD, " +
                        "AWN_KEY_ALIAS and AWN_KEY_PASSWORD as Gradle properties (~/.gradle/gradle.properties) " +
                        "or environment variables.",
                )
            }
        }
    }

tasks
    .matching { it.name.startsWith("package") && it.name.endsWith("Release") }
    .configureEach {
        dependsOn(requireSigningProperties)
    }

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":player"))
    implementation(project(":feature:read"))
    implementation(project(":feature:listen"))
    implementation(project(":feature:recite"))
    implementation(project(":feature:learn"))
    implementation(project(":tracker"))

    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.navigationCompose)
    implementation(libs.androidx.lifecycleRuntimeCompose)
    implementation(libs.androidx.datastorePreferences)
    implementation(libs.androidx.coreKtx)

    // sherpa-onnx classes and native libraries; downloaded and checksum-verified by
    // fetchNativeLibs.
    implementation(
        files(
                rootProject.layout.projectDirectory.file(
                    "libs/sherpa-onnx-static-link-onnxruntime-${libs.versions.sherpaOnnx.get()}.aar",
                ),
            )
            .builtBy(fetchNativeLibs),
    )

    testImplementation(libs.junit)
}
