package app.awn.quran

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Robolectric test that does not name its application boots AwnApplication, whose onCreate starts
 * a background download check through WorkManager. Under Robolectric that check throws after the
 * test has ended, and kotlinx-coroutines-test reports it in whichever test runs next
 * (UncaughtExceptionsBeforeTest), which made SettingsHubRenderTest fail at random on CI. Every
 * Robolectric test in :app therefore names a plain application in its @Config.
 */
class RobolectricApplicationGateTest {

    @Test
    fun everyRobolectricTestNamesItsApplication() {
        val testSources =
            File("src/test/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }

        val bootingTheRealApp =
            testSources
                .filter {
                    Regex("""@RunWith\(\w*RobolectricTestRunner""").containsMatchIn(it.readText())
                }
                .filterNot {
                    Regex("""@Config\([^)]*application\s*=""").containsMatchIn(it.readText())
                }
                .map { it.path }
                .toList()

        assertTrue(
            "These tests boot AwnApplication; add application = Application::class: " +
                bootingTheRealApp,
            bootingTheRealApp.isEmpty(),
        )
    }
}
