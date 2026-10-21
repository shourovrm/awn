package app.awn.quran

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation Compose cross-fades every screen change over 700 ms unless the NavHost is given
 * transitions, which made Back slow. The source of every NavHost in :app must pass all four from
 * [ScreenTransitions], a short slide. The manifest must also opt out of the system's predictive
 * back animation, which shrinks the whole app towards the launcher icon.
 */
class NavTransitionsTest {

    private val requiredArguments =
        mapOf(
            "enterTransition" to "ScreenTransitions.enter",
            "exitTransition" to "ScreenTransitions.exit",
            "popEnterTransition" to "ScreenTransitions.popEnter",
            "popExitTransition" to "ScreenTransitions.popExit",
        )

    private val navHostCall = Regex("""\bNavHost\(""")

    private fun appSources(): List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** The text of the call that starts at [start]: everything up to its matching parenthesis. */
    private fun callText(source: String, start: Int): String {
        var depth = 0
        for (index in start until source.length) {
            when (source[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) return source.substring(start, index + 1)
                }
            }
        }
        error("Unbalanced parentheses after offset $start.")
    }

    @Test
    fun everyNavHostPassesTheFourScreenTransitions() {
        val calls =
            appSources().flatMap { file ->
                val source = file.readText()
                navHostCall.findAll(source).map { callText(source, it.range.last) }.toList()
            }

        assertTrue("no NavHost call found", calls.isNotEmpty())
        for (call in calls) {
            for ((argument, expected) in requiredArguments) {
                val pattern = Regex("""$argument\s*=\s*${Regex.escape(expected)}\b""")
                assertTrue(
                    "NavHost lacks $argument = $expected:\n$call",
                    pattern.containsMatchIn(call),
                )
            }
        }
    }

    @Test
    fun theSlideIsQuick() {
        assertTrue(
            "a screen slide must take 150 to 250 ms, was ${ScreenTransitions.SLIDE_MILLIS}",
            ScreenTransitions.SLIDE_MILLIS in 150..250,
        )
    }

    @Test
    fun theManifestOptsOutOfPredictiveBack() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(
            "android:enableOnBackInvokedCallback=\"false\" is missing on <application>",
            Regex("""android:enableOnBackInvokedCallback\s*=\s*"false"""")
                .containsMatchIn(manifest),
        )
    }

    @Test
    fun theCheckSeesAMissingTransition() {
        val call = "NavHost(navController, start, enterTransition = ScreenTransitions.enter)"
        val pattern = Regex("""popExitTransition\s*=\s*ScreenTransitions.popExit\b""")

        assertEquals(false, pattern.containsMatchIn(call))
    }
}
