package app.awn.build

import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.UntrackedTask

/**
 * Downloads the sherpa-onnx release assets, then unpacks what the JVM tracker tests need.
 *
 * The Linux jar holds only the native libraries; the Kotlin classes (`com.k2fsa.sherpa.onnx`)
 * are inside the Android AAR as `classes.jar`. Tracker tests therefore put the AAR's classes on
 * the classpath and the Linux `.so` files on `java.library.path`.
 */
@UntrackedTask(because = "Extends a download task that re-checks hashes on every run.")
abstract class FetchNativeLibsTask : FetchVerifiedFilesTask() {

    @get:Input abstract val androidArchiveName: Property<String>

    @get:Input abstract val linuxJarName: Property<String>

    override fun afterVerified() {
        val directory = outputDirectory.get().asFile
        extractEntries(
            archive = File(directory, androidArchiveName.get()),
            targetDirectory = directory,
        ) { entryName ->
            if (entryName == "classes.jar") CLASSES_JAR_NAME else null
        }
        extractEntries(
            archive = File(directory, linuxJarName.get()),
            targetDirectory = File(directory, LINUX_NATIVE_DIRECTORY),
        ) { entryName ->
            if (entryName.startsWith("sherpa-onnx/native/linux-x64/") && entryName.endsWith(".so")) {
                entryName.substringAfterLast('/')
            } else {
                null
            }
        }
    }

    private fun extractEntries(
        archive: File,
        targetDirectory: File,
        targetNameFor: (entryName: String) -> String?,
    ) {
        targetDirectory.mkdirs()
        var extractedCount = 0
        ZipFile(archive).use { zip ->
            for (entry in zip.entries()) {
                val targetName = targetNameFor(entry.name) ?: continue
                zip.getInputStream(entry).use { input ->
                    File(targetDirectory, targetName).outputStream().use { input.copyTo(it) }
                }
                extractedCount++
            }
        }
        if (extractedCount == 0) {
            throw GradleException("Found nothing to extract from ${archive.path}.")
        }
    }

    companion object {
        const val CLASSES_JAR_NAME = "sherpa-onnx-classes.jar"
        const val LINUX_NATIVE_DIRECTORY = "linux-x64"
    }
}
