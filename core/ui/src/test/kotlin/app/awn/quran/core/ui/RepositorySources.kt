package app.awn.quran.core.ui

import java.io.File

/** Finds the files the gate tests inspect, wherever Gradle starts the test JVM. */
object RepositorySources {

    private val repositoryRoot: File by lazy {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").isFile) {
            directory = directory.parentFile
        }
        directory ?: error("settings.gradle.kts not found above ${System.getProperty("user.dir")}")
    }

    /** Every Kotlin file under a module's `src/main`, which is the code that ships. */
    fun shippedKotlinFiles(): List<File> =
        repositoryRoot
            .walkTopDown()
            .onEnter { it.name !in IGNORED_DIRECTORY_NAMES }
            .filter { it.isFile && it.extension == "kt" && "/src/main/" in it.invariantPath() }
            .toList()
            .sortedBy { it.path }

    /** Every string resource file of every module. */
    fun stringResourceFiles(): List<File> =
        repositoryRoot
            .walkTopDown()
            .onEnter { it.name !in IGNORED_DIRECTORY_NAMES }
            .filter {
                it.isFile && it.name == "strings.xml" && "/src/main/res/" in it.invariantPath()
            }
            .toList()
            .sortedBy { it.path }

    fun relativePath(file: File): String = file.relativeTo(repositoryRoot).invariantPath()

    fun isThemeFile(file: File): Boolean =
        "/core/ui/src/main/kotlin/app/awn/quran/core/ui/theme/" in file.invariantPath()

    private fun File.invariantPath(): String = path.replace(File.separatorChar, '/')

    // ".claude" holds agent worktrees: whole copies of the repository that are not this checkout.
    private val IGNORED_DIRECTORY_NAMES =
        setOf(
            "build",
            ".gradle",
            ".kotlin",
            ".git",
            ".claude",
            "buildSrc",
            "awn-models",
            "awn-data",
            "libs",
        )
}
