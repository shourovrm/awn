import app.awn.build.FetchVerifiedFilesTask
import com.android.build.api.variant.UnitTest

plugins {
    id("awn.android-library")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android { namespace = "app.awn.quran.core.data" }

room { schemaDirectory("$projectDir/schemas") }

// Room's exported schema JSON files are what the migration test opens as "the old database".
androidComponents {
    onVariants { variant ->
        variant.nestedComponents.filterIsInstance<UnitTest>().forEach { unitTest ->
            unitTest.sources.assets?.addStaticSourceDirectory("$projectDir/schemas")
        }
    }
}

// Packs that ship inside the APK. A machine with a built `awn-data/` supplies them directly;
// every other machine downloads them from the data release. Either way the files must match
// bundled-packs.sha256, so a rebuilt pack needs its pin updated in the same commit.
val bundledPackNames =
    file("bundled-packs.sha256")
        .readLines()
        .filter { it.isNotBlank() }
        .map { it.trim().split(Regex("\\s+"))[1] }

val bundledPacksDirectory = layout.projectDirectory.dir("src/main/assets/packs")

val copyLocalPacks =
    tasks.register<Copy>("copyLocalPacks") {
        description = "Copies the bundled packs from awn-data/ when that folder exists."
        group = "awn"
        // A git worktree has no awn-data of its own; AWN_DATA_DIR points it at the main checkout.
        val localDataDirectory =
            providers
                .environmentVariable("AWN_DATA_DIR")
                .map { file(it) }
                .orElse(rootProject.file("awn-data"))
                .get()
        onlyIf { localDataDirectory.isDirectory }
        from(localDataDirectory) {
            include(bundledPackNames.map { "**/$it" })
            eachFile { path = name }
        }
        includeEmptyDirs = false
        into(bundledPacksDirectory)
    }

val fetchBundledPacks =
    tasks.register<FetchVerifiedFilesTask>("fetchBundledPacks") {
        description = "Makes sure assets/packs holds the bundled packs with the pinned hashes."
        group = "awn"
        checksumFile = layout.projectDirectory.file("bundled-packs.sha256")
        baseUrl = "https://github.com/shourovrm/awn/releases/download/data-v1"
        outputDirectory = bundledPacksDirectory
        dependsOn(copyLocalPacks)
    }

// Every task that reads the assets folder must run after the packs are in place.
tasks
    .matching { it.name.contains("Assets") || it.name.contains("lint", ignoreCase = true) }
    .configureEach { dependsOn(fetchBundledPacks) }

dependencies {
    // Flow appears in the public repository interfaces, so consumers need it on their classpath.
    api(libs.kotlinx.coroutinesCore)

    // Background installs: WorkManager types appear in the app's Configuration.Provider.
    api(libs.androidx.work)
    implementation(libs.androidx.coreKtx)

    implementation(libs.androidx.roomRuntime)
    implementation(libs.androidx.roomKtx)
    ksp(libs.androidx.roomCompiler)

    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.androidx.roomTesting)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.testExtJunit)
}
