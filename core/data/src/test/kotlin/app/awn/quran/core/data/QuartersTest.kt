package app.awn.quran.core.data

import app.awn.quran.core.data.quran.QuarterData
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuartersTest {

    @Test
    fun hasTwoHundredFortyQuartersInReadingOrder() {
        val ayahs = Quarters.all.map { it.firstAyah }
        assertEquals(240, ayahs.size)
        assertEquals(ayahs.sorted(), ayahs)
        assertEquals(AyahRef(1, 1), ayahs.first())
    }

    @Test
    fun checksumMatchesTheRecordedSource() {
        val canonical =
            Quarters.all.joinToString("\n") { quarter ->
                listOf(
                        quarter.number,
                        quarter.firstAyah.surah,
                        quarter.firstAyah.ayah,
                        quarter.pageV1,
                        quarter.pageV2,
                    )
                    .joinToString(",")
            }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        assertEquals(QuarterData.SOURCE_SHA256, digest.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun knownQuartersMatchThePrint() {
        assertEquals(AyahRef(2, 26), Quarters.quarter(2).firstAyah)
        val quarterNine = Quarters.quarter(9)
        assertEquals(AyahRef(2, 142), quarterNine.firstAyah)
        assertEquals(22, quarterNine.pageV2)
        assertEquals(2, quarterNine.juz)
        assertEquals(0, quarterNine.quartersIntoHizb)
    }

    @Test
    fun juzAndHizbFollowFromTheNumber() {
        val quarterTen = Quarters.quarter(10)
        assertEquals(2, quarterTen.juz)
        assertEquals(3, quarterTen.hizb)
        assertEquals(1, quarterTen.quartersIntoHizb)
        assertEquals(8, Quarters.ofJuz(30).size)
        assertEquals(Quarters.quarter(233), Quarters.ofJuz(30).first())
    }

    @Test
    fun everyJuzStartsOnItsKnownPage() {
        val juzStartPages =
            listOf(1, 22, 42, 62, 82, 102, 121, 142, 162, 182, 201, 222, 242, 262, 282) +
                listOf(302, 322, 342, 362, 382, 402, 422, 442, 462, 482, 502, 522, 542, 562, 582)
        juzStartPages.forEachIndexed { index, page ->
            assertEquals("juz ${index + 1}", page, Quarters.ofJuz(index + 1).first().pageV2)
        }
    }

    @Test
    fun findsTheQuartersThatStartOnAPage() {
        assertEquals(listOf(2), Quarters.startingOnPage(5, ScriptId.V2).map { it.number })
        assertTrue(Quarters.startingOnPage(3, ScriptId.V2).isEmpty())
    }

    @Test
    fun findsTheQuarterOfAnAyah() {
        assertEquals(1, Quarters.containing(AyahRef(2, 25)).number)
        assertEquals(2, Quarters.containing(AyahRef(2, 26)).number)
        assertEquals(240, Quarters.containing(AyahRef(114, 6)).number)
    }
}
