package app.awn.quran

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigation Compose cross-fades every screen change over 700 ms unless the NavHost is given
 * transitions, which made Back slow. The source of every NavHost in :app must pass all four.
 */
class NavTransitionsTest {

    private val requiredArguments =
        mapOf(
            "enterTransition" to "EnterTransition.None",
            "exitTransition" to "ExitTransition.None",
            "popEnterTransition" to "EnterTransition.None",
            "popExitTransition" to "ExitTransition.None",
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
    fun everyNavHostSetsAllFourTransitionsToNone() {
        val calls =
            appSources().flatMap { file ->
                val source = file.readText()
                navHostCall.findAll(source).map { callText(source, it.range.last) }.toList()
            }

        assertTrue("no NavHost call found", calls.isNotEmpty())
        for (call in calls) {
            for ((argument, expected) in requiredArguments) {
                val pattern = Regex("""$argument\s*=\s*\{\s*$expected\s*}""")
                assertTrue(
                    "NavHost lacks $argument = { $expected }:\n$call",
                    pattern.containsMatchIn(call),
                )
            }
        }
    }

    @Test
    fun theCheckSeesAMissingTransition() {
        val call = "NavHost(navController, start, enterTransition = { EnterTransition.None })"
        val pattern = Regex("""popExitTransition\s*=\s*\{\s*ExitTransition.None\s*}""")

        assertEquals(false, pattern.containsMatchIn(call))
    }
}
