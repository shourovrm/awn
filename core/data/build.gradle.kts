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

// The only content that ships inside the APK: the Quran text and layout, the Hafs font and the
// three manifests. Everything else (translations, word packs, audio, the speech model) is
// downloaded after install. A machine with a built `awn-data/` supplies the files directly; every
// other machine downloads them from the data release. Either way the files must match
// bundled-packs.sha256, so a rebuilt pack needs its pin updated in the same commit.
fun packNamesIn(checksumFileName: String): List<String> =
    file(checksumFileName)
        .readLines()
        .filter { it.isNotBlank() }
        .map { it.trim().split(Regex("\\s+"))[1] }

// A git worktree has no awn-data of its own unless one is built there. Its own folder is searched
// first, then AWN_DATA_DIR (the main checkout's), so a pack rebuilt in the worktree wins.
val localDataDirectories =
    listOfNotNull(
            rootProject.file("awn-data"),
            providers.environmentVariable("AWN_DATA_DIR").map { file(it) }.orNull,
        )
        .filter { it.isDirectory }

fun registerCopyLocalPacks(taskName: String, names: List<String>, target: Directory) =
    tasks.register<Copy>(taskName) {
        description = "Copies $names from awn-data/ when that folder exists."
        group = "awn"
        val hasLocalData = localDataDirectories.isNotEmpty()
        onlyIf { hasLocalData }
        // The first folder that holds a file wins.
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        localDataDirectories.forEach { directory ->
            from(directory) {
                include(names.map { "**/$it" })
                eachFile { path = name }
            }
        }
        includeEmptyDirs = false
        into(target)
    }

val bundledPacksDirectory = layout.projectDirectory.dir("src/main/assets/packs")

val copyLocalPacks =
    registerCopyLocalPacks(
        "copyLocalPacks",
        packNamesIn("bundled-packs.sha256"),
        bundledPacksDirectory,
    )

val fetchBundledPacks =
    tasks.register<FetchVerifiedFilesTask>("fetchBundledPacks") {
        description = "Makes sure assets/packs holds the bundled packs with the pinned hashes."
        group = "awn"
        checksumFile = layout.projectDirectory.file("bundled-packs.sha256")
        baseUrl = "https://github.com/shourovrm/awn/releases/download/data-v1"
        outputDirectory = bundledPacksDirectory
        dependsOn(copyLocalPacks)
    }

// Unit tests read real translations and word packs that the APK no longer holds. They are fetched
// into a build folder, never into the assets, so they cannot reach the APK.
val downloadedTestPacksDirectory = layout.buildDirectory.dir("test-packs").get()

val copyLocalTestPacks =
    registerCopyLocalPacks(
        "copyLocalTestPacks",
        packNamesIn("test-packs.sha256"),
        downloadedTestPacksDirectory,
    )

val fetchTestPacks =
    tasks.register<FetchVerifiedFilesTask>("fetchTestPacks") {
        description = "Makes sure build/test-packs holds the packs the unit tests read."
        group = "awn"
        checksumFile = layout.projectDirectory.file("test-packs.sha256")
        baseUrl = "https://github.com/shourovrm/awn/releases/download/data-v1"
        outputDirectory = downloadedTestPacksDirectory
        dependsOn(copyLocalTestPacks)
    }

// Every task that reads the assets folder must run after the packs are in place.
tasks
    .matching { it.name.contains("Assets") || it.name.contains("lint", ignoreCase = true) }
    .configureEach { dependsOn(fetchBundledPacks) }

tasks
    .matching { it.name.startsWith("test") && it.name.endsWith("UnitTest") }
    .configureEach { dependsOn(fetchTestPacks) }

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
