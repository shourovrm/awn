package app.awn.quran.core.quranfont

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeastRecentlyUsedCacheTest {

    @Test
    fun holdsNoMoreEntriesThanItsCapacity() {
        val cache = LeastRecentlyUsedCache<Int, String>(capacity = 3)

        (1..5).forEach { key -> cache.put(key, "value $key") }

        assertEquals(3, cache.size)
        assertEquals(setOf(3, 4, 5), cache.keys())
    }

    @Test
    fun evictsTheEntryThatWasPutFirstWhenNothingWasRead() {
        val cache = LeastRecentlyUsedCache<Int, String>(capacity = 2)

        cache.put(1, "one")
        cache.put(2, "two")
        cache.put(3, "three")

        assertNull(cache.get(1))
        assertEquals("two", cache.get(2))
    }

    @Test
    fun aReadEntrySurvivesEvictionAndTheUnreadOneGoes() {
        val cache = LeastRecentlyUsedCache<Int, String>(capacity = 2)
        cache.put(1, "one")
        cache.put(2, "two")

        assertEquals("one", cache.get(1))
        cache.put(3, "three")

        assertEquals("one", cache.get(1))
        assertNull(cache.get(2))
    }

    @Test
    fun puttingAKeyAgainReplacesItsValueWithoutGrowing() {
        val cache = LeastRecentlyUsedCache<Int, String>(capacity = 2)
        cache.put(1, "one")
        cache.put(2, "two")

        cache.put(1, "uno")
        cache.put(3, "three")

        assertEquals(2, cache.size)
        assertEquals("uno", cache.get(1))
        assertNull(cache.get(2))
    }

    @Test
    fun aMissingKeyLeavesTheOrderAsItWas() {
        val cache = LeastRecentlyUsedCache<Int, String>(capacity = 2)
        cache.put(1, "one")
        cache.put(2, "two")

        assertNull(cache.get(9))
        cache.put(3, "three")

        assertEquals(setOf(2, 3), cache.keys())
    }
}
