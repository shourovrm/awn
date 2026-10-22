package app.awn.quran.feature.hifz

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A plain `session.stop()` makes the next microphone start clear every mark, so the user starts the
 * ayah over. The user found that after the microphone stop button (fixed in round 5). Two more
 * paths did the same: leaving the app while reciting (the bars' ON_STOP handling) and the Hifz
 * bar's Play. Both bars must stop the microphone with ReciteController.stopMicrophone(), which
 * keeps what was recited and lets the next start go on from the next word.
 */
class KeepRecitedProgressGateTest {

    private val repositoryRoot: File by lazy {
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null && !File(directory, "settings.gradle.kts").isFile) {
            directory = directory.parentFile
        }
        checkNotNull(directory) { "settings.gradle.kts not found" }
    }

    private val bars =
        listOf(
            "feature/recite/src/main/kotlin/app/awn/quran/feature/recite/ReciteBar.kt",
            "feature/hifz/src/main/kotlin/app/awn/quran/feature/hifz/HifzBar.kt",
        )

    @Test
    fun neitherBarStopsTheMicrophoneInAWayThatLosesProgress() {
        val offenders = bars.filter { path ->
            File(repositoryRoot, path).readText().contains("session.stop()")
        }

        assertTrue("These bars still call session.stop(): $offenders", offenders.isEmpty())
    }
}
