package app.awn.quran.feature.listen

import app.awn.quran.core.ui.theme.AwnField
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenFieldTest {
    @Test
    fun ownsItsFieldColour() {
        assertEquals(AwnField.Green, ListenField)
    }
}
