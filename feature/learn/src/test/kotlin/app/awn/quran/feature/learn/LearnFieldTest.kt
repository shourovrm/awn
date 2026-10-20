package app.awn.quran.feature.learn

import app.awn.quran.core.ui.theme.AwnField
import org.junit.Assert.assertEquals
import org.junit.Test

class LearnFieldTest {
    @Test
    fun ownsItsFieldColour() {
        assertEquals(AwnField.Ochre, LearnField)
    }
}
