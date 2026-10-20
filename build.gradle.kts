import app.awn.build.FetchNativeLibsTask
import app.awn.build.FetchVerifiedFilesTask

plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// ---------------------------------------------------------------------------------------------
// Inputs that are not on Maven: sherpa-onnx native libraries and the Zipformer model files.
// ---------------------------------------------------------------------------------------------

val sherpaOnnxVersion = libs.versions.sherpaOnnx.get()

val fetchNativeLibs =
    tasks.register<FetchNativeLibsTask>("fetchNativeLibs") {
        description = "Downloads and unpacks the sherpa-onnx AAR and Linux jar into libs/."
        group = "awn"
        checksumFile = layout.projectDirectory.file("gradle/native-libs.sha256")
        baseUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion"
        outputDirectory = layout.projectDirectory.dir("libs")
        androidArchiveName = "sherpa-onnx-static-link-onnxruntime-$sherpaOnnxVersion.aar"
        linuxJarName = "sherpa-onnx-native-lib-linux-x64-$sherpaOnnxVersion.jar"
    }

val fetchModels =
    tasks.register<FetchVerifiedFilesTask>("fetchModels") {
        description = "Makes sure awn-models/ holds the Zipformer files from the models-v1 release."
        group = "awn"
        checksumFile = layout.projectDirectory.file("gradle/models.sha256")
        baseUrl = "https://github.com/shourovrm/awn/releases/download/models-v1"
        outputDirectory = layout.projectDirectory.dir("awn-models")
    }

// ---------------------------------------------------------------------------------------------
// Quality gates.
// ---------------------------------------------------------------------------------------------

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(layout.projectDirectory.file("config/detekt/detekt.yml"))
    source.setFrom(
        fileTree(layout.projectDirectory) {
            include("**/src/**/*.kt")
            exclude("**/build/**")
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Whole-repository entry points. The flavored tasks exist only in :app, so these root tasks
// pull in the matching release tasks of every library module and the JVM-only :tracker.
// ---------------------------------------------------------------------------------------------

val lintGithubRelease =
    tasks.register("lintGithubRelease") {
        description = "Runs Android Lint on every library module (the app has its own task)."
        group = "verification"
    }

val testGithubReleaseUnitTest =
    tasks.register("testGithubReleaseUnitTest") {
        description = "Runs the unit tests of every library module and :tracker."
        group = "verification"
    }

subprojects {
    // Captured here because inside the task configuration blocks `path` means the task's path.
    val modulePath = path
    plugins.withId("com.android.library") {
        lintGithubRelease.configure { dependsOn("$modulePath:lintRelease") }
        testGithubReleaseUnitTest.configure { dependsOn("$modulePath:testReleaseUnitTest") }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        testGithubReleaseUnitTest.configure { dependsOn("$modulePath:test") }
    }
}
