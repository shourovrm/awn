package app.awn.quran

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source checks for the :app side of the combined bar: the audio bar gets the recite action, which
 * starts reciting at the ayah AudioBarViewModel.reciteStart gives (the Quran screen follows recite
 * mode by itself), and the screen learns when audio is busy so the bar can shrink instead of
 * vanishing.
 */
class CombinedBarWiringTest {

    private val app = File("src/main/kotlin/app/awn/quran/AwnApp.kt").readText()

    @Test
    fun theAudioBarCanStartReciting() {
        assertTrue(Regex("""AudioBar\([^)]*onRecite\s*=""").containsMatchIn(app))
        assertTrue(app.contains("recite.reciteFrom"))
    }

    @Test
    fun theScreenKnowsWhenAudioIsBusy() {
        assertTrue(Regex("""isAudioActive\s*=""").containsMatchIn(app))
    }
}
