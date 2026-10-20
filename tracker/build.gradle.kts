plugins {
    kotlin("jvm")
}

val fetchNativeLibs = rootProject.tasks.named("fetchNativeLibs")
val fetchModels = rootProject.tasks.named("fetchModels")
val nativeLibsDirectory = rootProject.layout.projectDirectory.dir("libs")
val modelsDirectory = rootProject.layout.projectDirectory.dir("awn-models")

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
    dependsOn(fetchNativeLibs, fetchModels)
    val nativeLibraryDirectory = nativeLibsDirectory.dir("linux-x64").asFile.absolutePath
    // libsherpa-onnx-jni.so is loaded by name and finds libonnxruntime.so next to itself.
    systemProperty("java.library.path", nativeLibraryDirectory)
    environment("LD_LIBRARY_PATH", nativeLibraryDirectory)
    systemProperty("awn.models.directory", modelsDirectory.asFile.absolutePath)
    // Gradle only reports a test as passed or skipped; show which it was.
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}
