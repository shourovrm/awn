package app.awn.quran.tracker

import kotlin.system.measureTimeMillis
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Prints how long the 12.8 MB reference file takes to parse on the JVM. */
class ReferenceParseTimeTest {

    @Test
    fun printsParseTime() {
        assumeTrue(TestPaths.referenceFile.isFile)
        val timesMilliseconds =
            List(RUNS) {
                measureTimeMillis { JsonReferencePhonemes.fromFile(TestPaths.referenceFile) }
            }
        println("PARSE ${TestPaths.referenceFile.length()} bytes, ms per run: $timesMilliseconds")
    }

    private companion object {
        const val RUNS = 3
    }
}
