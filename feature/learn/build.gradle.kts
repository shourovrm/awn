import com.android.build.api.variant.UnitTest
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    id("awn.android-library")
    id("awn.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "app.awn.quran.feature.learn"

    // The two test coordinates below are written out here on purpose, so that this module adds no
    // line to the shared version catalog while other modules are built in parallel.
}

room { schemaDirectory("$projectDir/schemas") }

// Room's exported schema JSON files are what the migration test opens as "the old database".
androidComponents {
    onVariants { variant ->
        variant.nestedComponents.filterIsInstance<UnitTest>().forEach { unitTest ->
            unitTest.sources.assets?.addStaticSourceDirectory("$projectDir/schemas")
        }
    }
}

// The lesson data and the word ranking are built from files in the repository and from the bundled
// Quran text pack by two scripts in tools/, so neither output is committed.
abstract class GenerateLearnContent @Inject constructor(private val execOperations: ExecOperations) :
    DefaultTask() {
    @get:InputDirectory abstract val lessonDirectory: DirectoryProperty

    @get:InputFile abstract val quranDatabase: RegularFileProperty

    @get:InputFile abstract val lessonScript: RegularFileProperty

    @get:InputFile abstract val wordScript: RegularFileProperty

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val learnFolder = outputDirectory.get().dir("learn").asFile
        learnFolder.mkdirs()
        execOperations.exec {
            commandLine(
                "python3",
                "-I",
                lessonScript.get().asFile.path,
                "--lessons",
                lessonDirectory.get().asFile.path,
                "--quran",
                quranDatabase.get().asFile.path,
                "--out",
                File(learnFolder, "lessons.json").path,
            )
        }
        execOperations.exec {
            commandLine(
                "python3",
                "-I",
                wordScript.get().asFile.path,
                "--quran",
                quranDatabase.get().asFile.path,
                "--out",
                File(learnFolder, "word_forms.bin").path,
            )
        }
    }
}

val bundledPackFolder = project(":core:data").layout.projectDirectory.dir("src/main/assets/packs")

val generateLearnContent =
    tasks.register<GenerateLearnContent>("generateLearnContent") {
        description = "Builds lessons.json and word_forms.bin from content/lessons and the packs."
        group = "awn"
        lessonDirectory = rootProject.layout.projectDirectory.dir("content/lessons")
        quranDatabase = bundledPackFolder.file("quran.sqlite")
        lessonScript = rootProject.layout.projectDirectory.file("tools/build_learn_content.py")
        wordScript = rootProject.layout.projectDirectory.file("tools/build_learn_words.py")
        outputDirectory = layout.buildDirectory.dir("generated/learnAssets")
        dependsOn(":core:data:fetchBundledPacks")
    }

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            generateLearnContent,
            GenerateLearnContent::outputDirectory,
        )
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))

    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.androidx.roomRuntime)
    implementation(libs.androidx.roomKtx)
    ksp(libs.androidx.roomCompiler)
    implementation(libs.androidx.datastorePreferences)
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.coreKtx)

    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.androidx.roomTesting)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.testExtJunit)
    testImplementation(libs.compose.uiTestJunit4)
    // Registers the activity that the Compose test rule launches under Robolectric.
    testImplementation(libs.compose.uiTestManifest)
}
