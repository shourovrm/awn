package app.awn.quran.feature.read

import app.awn.quran.core.ui.theme.AwnField
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadFieldTest {
    @Test
    fun ownsItsFieldColour() {
        assertEquals(AwnField.Paper, ReadField)
    }
}
