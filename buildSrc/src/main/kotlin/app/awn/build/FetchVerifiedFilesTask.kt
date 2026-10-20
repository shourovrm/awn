package app.awn.build

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Makes sure every file listed in a `sha256sum`-style checksum file exists in [outputDirectory]
 * with exactly the recorded hash. A file with the right hash is left alone; any other file is
 * downloaded from [baseUrl] and checked before it replaces the old one.
 *
 * The task is untracked because Gradle cannot see changes to files on a remote server, so it
 * re-checks the hashes on every run. Hashing a few hundred megabytes takes under a second.
 */
@UntrackedTask(because = "Downloads from a remote server; the checksums are the source of truth.")
abstract class FetchVerifiedFilesTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val checksumFile: RegularFileProperty

    @get:Input abstract val baseUrl: Property<String>

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun fetchAndVerify() {
        val expectedHashes = parseChecksums(checksumFile.get().asFile)
        val directory = outputDirectory.get().asFile
        directory.mkdirs()

        for ((fileName, expectedHash) in expectedHashes) {
            val target = File(directory, fileName)
            if (target.isFile && sha256Of(target) == expectedHash) {
                logger.lifecycle("$fileName: present, checksum matches")
                continue
            }
            download(fileName, expectedHash, target)
        }
        afterVerified()
    }

    /** Hook for subclasses that need to post-process the verified files. */
    protected open fun afterVerified() = Unit

    private fun download(fileName: String, expectedHash: String, target: File) {
        val url = "${baseUrl.get().trimEnd('/')}/$fileName"
        logger.lifecycle("$fileName: downloading from $url")
        val partialFile = File(target.parentFile, "$fileName.part")
        try {
            val client =
                HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build()
            val request =
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(30)).build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofFile(partialFile.toPath()))
            if (response.statusCode() != 200) {
                throw GradleException("Download of $url failed with HTTP status ${response.statusCode()}.")
            }
        } catch (error: IOException) {
            throw GradleException("Download of $url failed: ${error.message}", error)
        }

        val actualHash = sha256Of(partialFile)
        if (actualHash != expectedHash) {
            partialFile.delete()
            throw GradleException(
                "Checksum mismatch for $fileName downloaded from $url.\n" +
                    "  expected $expectedHash\n  actual   $actualHash\n" +
                    "The download was discarded."
            )
        }
        Files.move(partialFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun parseChecksums(file: File): Map<String, String> {
        val hashesByName = linkedMapOf<String, String>()
        file.readLines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size != 2 || parts[0].length != 64) {
                throw GradleException("Malformed line in ${file.path}: '$line'")
            }
            hashesByName[parts[1].trimStart('*')] = parts[0].lowercase()
        }
        return hashesByName
    }

    companion object {
        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val readCount = input.read(buffer)
                    if (readCount < 0) break
                    digest.update(buffer, 0, readCount)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
