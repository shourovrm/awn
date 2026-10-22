package app.awn.quran.feature.read

import app.awn.quran.core.ui.theme.AwnField
import app.awn.quran.core.ui.theme.AwnReciteFills
import app.awn.quran.core.ui.theme.ColourMode
import app.awn.quran.core.ui.theme.awnFieldColors
import app.awn.quran.feature.read.page.reciteFillColor
import app.awn.quran.tracker.WordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Yellow now means "said, but mispronounced" (WordState.Mispronounced, published by the recite
 * session for a confirmed word with a finding). A word the tracker is still unsure about
 * (Tentative) gets no fill, so the page no longer flashes "unclear" while the user recites.
 */
class MispronouncedFillTest {

    @Test
    fun aMispronouncedWordIsYellowByDayAndByNight() {
        assertEquals(
            AwnReciteFills.dayTentative,
            reciteFillColor(
                WordState.Mispronounced,
                awnFieldColors(AwnField.Paper, ColourMode.Awn),
            ),
        )
        assertEquals(
            AwnReciteFills.nightTentative,
            reciteFillColor(
                WordState.Mispronounced,
                awnFieldColors(AwnField.Paper, ColourMode.Dark),
            ),
        )
    }

    @Test
    fun aWordStillBeingRecitedHasNoFill() {
        assertNull(
            reciteFillColor(WordState.Tentative, awnFieldColors(AwnField.Paper, ColourMode.Awn)),
        )
        assertNull(
            reciteFillColor(WordState.Tentative, awnFieldColors(AwnField.Paper, ColourMode.Black)),
        )
    }
}
