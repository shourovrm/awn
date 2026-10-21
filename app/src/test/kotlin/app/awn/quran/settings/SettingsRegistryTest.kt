package app.awn.quran.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRegistryTest {

    @Test
    fun everySearchableRowHasItsOwnKey() {
        val keys = registeredRowKeys()

        assertEquals("duplicate row keys", keys.size, keys.toSet().size)
    }
}
