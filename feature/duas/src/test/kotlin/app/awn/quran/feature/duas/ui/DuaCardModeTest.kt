package app.awn.quran.feature.duas.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DuaCardModeTest {

    @Test
    fun aSavedModeIsRestoredByName() {
        assertEquals(DuaCardMode.Recite, modeFromStoredName("Recite"))
        assertEquals(DuaCardMode.Listen, modeFromStoredName("Listen"))
    }

    @Test
    fun theRemovedWordByWordModeFallsBackToListen() {
        assertEquals(DuaCardMode.Listen, modeFromStoredName("WordByWord"))
    }

    @Test
    fun theBarHasListenAndReciteOnly() {
        assertEquals(listOf(DuaCardMode.Listen, DuaCardMode.Recite), DuaCardMode.entries)
    }
}
