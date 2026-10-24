package app.awn.quran.feature.learn.words

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.feature.learn.session.FakeFrequency
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoverageBarsTest {

    @Test
    fun theRealWordFormsGiveTheShareOfEachLevelEnd() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val real = WordForms.read(assets.open(WordFormsAsset.PATH))

        val shares = CoverageBars.milestones(real).map { (it.share * 1_000).roundToInt() / 10.0 }

        assertEquals(listOf(43.0, 51.5, 56.4, 62.6, 70.7, 78.6, 88.8), shares)
    }

    @Test
    fun theBarsAreTheSevenLevelEnds() {
        val milestones = CoverageBars.milestones(FakeFrequency(formCount = 6_000))

        assertEquals(
            listOf(100, 200, 300, 500, 1_000, 2_000, 5_000),
            milestones.map { it.wordCount },
        )
    }

    @Test
    fun aShortWordListCoversNoMoreThanItHas() {
        val milestones = CoverageBars.milestones(FakeFrequency(formCount = 150))

        assertEquals(1.0, milestones.last().share, 1e-9)
        assertEquals(100 * 3 / (150 * 3.0), milestones.first().share, 1e-9)
    }
}
