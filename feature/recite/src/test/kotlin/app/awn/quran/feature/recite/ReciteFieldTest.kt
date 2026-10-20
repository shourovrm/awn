package app.awn.quran.feature.recite

import app.awn.quran.core.ui.theme.AwnField
import org.junit.Assert.assertEquals
import org.junit.Test

class ReciteFieldTest {
    @Test
    fun ownsItsFieldColour() {
        assertEquals(AwnField.Night, ReciteField)
    }
}
