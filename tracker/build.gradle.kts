import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

plugins {
    kotlin("jvm")
}

val fetchNativeLibs = rootProject.tasks.named("fetchNativeLibs")
val fetchModels = rootProject.tasks.named("fetchModels")
val nativeLibsDirectory = rootProject.layout.projectDirectory.dir("libs")
val modelsDirectory = rootProject.layout.projectDirectory.dir("awn-models")

// ---------------------------------------------------------------------------------------------
// Test audio. Husary ayah MP3s from EveryAyah are converted to 16 kHz mono WAV, then joined into
// the recitation, cut, spliced and time-stretched fixtures that TrackingFixtureTest feeds through
// the model. Everything lands in the gitignored tracker/fixtures/ folder; existing files are kept.
// ---------------------------------------------------------------------------------------------

/** A foreign word spliced into [surah] after [afterAyah], taken from the start of a donor ayah. */
data class Splice(val surah: Int, val afterAyah: Int, val donorSurah: Int, val donorAyah: Int)

abstract class FetchFixturesTask : DefaultTask() {

    @get:OutputDirectory abstract val fixturesDirectory: DirectoryProperty

    private val ayahCountBySurah = mapOf(1 to 7, 112 to 4, 113 to 5, 114 to 6, 2 to 20, 36 to 20)

    // A reciter breathes between ayahs, but the ayah files end tightly, so joins get a pause.
    private val pauseBetweenAyahsSeconds = "0.4"
    private val stretchFactors = listOf("1.2")

    // (surah, ayah) cut out of the recitation; ayah 3 is cut from every short surah.
    private val cutAyahs =
        listOf(
            1 to 3,
            112 to 3,
            113 to 3,
            114 to 3,
            1 to 5,
            112 to 2,
            113 to 4,
            114 to 5,
            2 to 10,
            36 to 10,
        )

    // Donor words come from another surah and never start the recipient surah's own words.
    private val splices =
        listOf(
            Splice(1, 2, 112, 1),
            Splice(1, 4, 114, 6),
            Splice(112, 1, 1, 2),
            Splice(112, 3, 1, 4),
            Splice(113, 2, 1, 5),
            Splice(113, 4, 1, 6),
            Splice(114, 2, 1, 4),
            Splice(114, 4, 1, 5),
            Splice(2, 10, 112, 1),
            Splice(36, 10, 113, 1),
        )

    @TaskAction
    fun fetch() {
        val root = fixturesDirectory.get().asFile
        val mp3Directory = root.resolve("mp3").also { it.mkdirs() }
        val ayahDirectory = root.resolve("ayah").also { it.mkdirs() }
        val pause = root.resolve("pause.wav")
        if (!pause.exists()) {
            run(
                "/usr/bin/ffmpeg",
                "-y",
                "-f",
                "lavfi",
                "-i",
                "anullsrc=r=16000:cl=mono",
                "-t",
                pauseBetweenAyahsSeconds,
                pause.path,
            )
        }
        for ((surah, ayahCount) in ayahCountBySurah) {
            for (ayah in 1..ayahCount) {
                downloadAyah(surah, ayah, mp3Directory, ayahDirectory)
            }
        }
        for ((surah, ayahCount) in ayahCountBySurah) {
            val ayahs = (1..ayahCount).toList()
            join(root.resolve("full_s$surah.wav"), surah, ayahs, emptyMap(), ayahDirectory, pause)
        }
        for ((surah, cutAyah) in cutAyahs) {
            val kept = (1..ayahCountBySurah.getValue(surah)).filter { it != cutAyah }
            val output = root.resolve("cut_s${surah}_a$cutAyah.wav")
            join(output, surah, kept, emptyMap(), ayahDirectory, pause)
        }
        for (splice in splices) {
            val word = firstWordOf(splice.donorSurah, splice.donorAyah, ayahDirectory, root)
            val ayahs = (1..ayahCountBySurah.getValue(splice.surah)).toList()
            val name =
                "splice_s${splice.surah}_after${splice.afterAyah}" +
                    "_from${splice.donorSurah}-${splice.donorAyah}.wav"
            join(
                root.resolve(name),
                splice.surah,
                ayahs,
                mapOf(splice.afterAyah to word),
                ayahDirectory,
                pause,
            )
        }
        for ((surah, ayahCount) in ayahCountBySurah) {
            if (ayahCount > SHORT_SURAH_AYAH_LIMIT) continue
            for (factor in stretchFactors) {
                stretch(
                    root.resolve("full_s$surah.wav"),
                    root.resolve("stretch_s${surah}_t$factor.wav"),
                    factor,
                )
            }
        }
        // The madd comparison in tools/spike_zipformer.md needs one ayah at three speeds.
        for (factor in listOf("0.8", "1.2")) {
            stretch(
                ayahDirectory.resolve("001001.wav"),
                root.resolve("stretch_ayah_001001_t$factor.wav"),
                factor,
            )
        }
    }

    private fun downloadAyah(surah: Int, ayah: Int, mp3Directory: File, ayahDirectory: File) {
        val stem = "%03d%03d".format(surah, ayah)
        val mp3 = mp3Directory.resolve("$stem.mp3")
        if (!mp3.exists()) {
            val url = URI("https://mirrors.quranicaudio.com/everyayah/Husary_128kbps/$stem.mp3")
            val request = HttpRequest.newBuilder(url).build()
            val handler = HttpResponse.BodyHandlers.ofFile(mp3.toPath())
            val response = HttpClient.newHttpClient().send(request, handler)
            check(response.statusCode() == 200) { "HTTP ${response.statusCode()} for $url" }
        }
        val wav = ayahDirectory.resolve("$stem.wav")
        if (!wav.exists()) {
            run(
                "/usr/bin/ffmpeg",
                "-y",
                "-i",
                mp3.path,
                "-ac",
                "1",
                "-ar",
                "16000",
                "-f",
                "wav",
                wav.path,
            )
        }
    }

    // Concatenates the ayahs in order; [wordsAfterAyah] maps an ayah number to a WAV spliced after
    // it.
    private fun join(
        output: File,
        surah: Int,
        ayahs: List<Int>,
        wordsAfterAyah: Map<Int, File>,
        ayahDirectory: File,
        pause: File,
    ) {
        if (output.exists()) return
        val parts = mutableListOf<File>()
        for (ayah in ayahs) {
            parts += ayahDirectory.resolve("%03d%03d.wav".format(surah, ayah))
            parts += pause
            wordsAfterAyah[ayah]?.let { parts += listOf(it, pause) }
        }
        val listFile = File.createTempFile("concat", ".txt")
        listFile.writeText(parts.joinToString("\n") { "file '${it.absolutePath}'" })
        run(
            "/usr/bin/ffmpeg",
            "-y",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            listFile.path,
            "-c",
            "copy",
            output.path,
        )
        listFile.delete()
    }

    private fun stretch(source: File, output: File, factor: String) {
        if (output.exists()) return
        run(
            "/usr/bin/ffmpeg",
            "-y",
            "-i",
            source.path,
            "-filter:a",
            "atempo=$factor",
            "-ac",
            "1",
            "-ar",
            "16000",
            output.path,
        )
    }

    // No word timings are available here: the foreign segment starts where the leading silence ends
    // and stops at the next short silence at least MINIMUM_WORD_SECONDS later (one to three words;
    // single words of 0.6 to 0.9 s made the model emit no tokens at all).
    private fun firstWordOf(surah: Int, ayah: Int, ayahDirectory: File, root: File): File {
        val output = root.resolve("word_%03d%03d.wav".format(surah, ayah))
        if (output.exists()) return output
        val source = ayahDirectory.resolve("%03d%03d.wav".format(surah, ayah))
        val log =
            run(
                "/usr/bin/ffmpeg",
                "-i",
                source.path,
                "-af",
                "silencedetect=noise=-25dB:d=0.02",
                "-f",
                "null",
                "-",
            )
        val speechStart =
            Regex("silence_end: ([0-9.]+)").find(log)?.groupValues?.get(1)?.toDouble() ?: 0.0
        val nextSilenceStart =
            Regex("silence_start: ([0-9.]+)")
                .findAll(log)
                .map { it.groupValues[1].toDouble() }
                .firstOrNull { it > speechStart + MINIMUM_WORD_SECONDS }
        val wordEnd =
            if (nextSilenceStart != null && nextSilenceStart < speechStart + MAXIMUM_WORD_SECONDS) {
                nextSilenceStart
            } else {
                speechStart + FALLBACK_WORD_SECONDS
            }
        run(
            "/usr/bin/ffmpeg",
            "-y",
            "-i",
            source.path,
            "-ss",
            speechStart.toString(),
            "-t",
            (wordEnd - speechStart).toString(),
            output.path,
        )
        return output
    }

    private fun run(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val outputText = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "${command.joinToString(" ")} failed:\n$outputText" }
        return outputText
    }

    private companion object {
        const val SHORT_SURAH_AYAH_LIMIT = 7
        const val MINIMUM_WORD_SECONDS = 1.0
        const val MAXIMUM_WORD_SECONDS = 2.0
        const val FALLBACK_WORD_SECONDS = 1.4
    }
}

val fetchFixtures =
    tasks.register<FetchFixturesTask>("fetchFixtures") {
        description = "Downloads Husary ayah audio and builds the tracking test fixtures."
        group = "awn"
        fixturesDirectory = layout.projectDirectory.dir("fixtures")
    }

// sherpa-onnx has no Maven artifact. Its Kotlin classes come out of the Android AAR (unpacked by
// fetchNativeLibs); the Android app gets the same classes from the AAR itself, hence compileOnly.
val sherpaOnnxClasses =
    files(nativeLibsDirectory.file("sherpa-onnx-classes.jar")).builtBy(fetchNativeLibs)

dependencies {
    compileOnly(sherpaOnnxClasses)
    // The sherpa-onnx classes mention android.content.res.AssetManager in a constructor, so the
    // compiler and the JVM test runtime need that one type to exist.
    compileOnly(libs.android.stubs)

    testImplementation(sherpaOnnxClasses)
    testImplementation(libs.android.stubs)
    testImplementation(libs.junit)
}

kotlin { jvmToolchain(21) }

tasks.test {
    dependsOn(fetchNativeLibs, fetchModels, fetchFixtures)
    val nativeLibraryDirectory = nativeLibsDirectory.dir("linux-x64").asFile.absolutePath
    // libsherpa-onnx-jni.so is loaded by name and finds libonnxruntime.so next to itself.
    systemProperty("java.library.path", nativeLibraryDirectory)
    environment("LD_LIBRARY_PATH", nativeLibraryDirectory)
    systemProperty("awn.models.directory", modelsDirectory.asFile.absolutePath)
    systemProperty(
        "awn.fixtures.directory",
        layout.projectDirectory.dir("fixtures").asFile.absolutePath,
    )
    // The fixture test needs about 1 GB for the model plus the audio.
    maxHeapSize = "1g"
    // Gradle only reports a test as passed or skipped; show which it was.
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
