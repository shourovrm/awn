package app.awn.quran.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private fun entry(label: String, vararg otherTexts: String, rowKey: String? = label) =
        SettingsSearchEntry(
            route = "settings/audio",
            rowKey = rowKey,
            label = label,
            pageLabel = "Audio",
            searchTexts = listOf(label) + otherTexts,
        )

    private val wifi = entry("Wi-Fi only", "শুধু ওয়াই-ফাই")
    private val downloadBy = entry("Download by", "Page", "Surah", "Juz", "ডাউনলোডের একক")
    private val downloads = entry("Downloads", "ডাউনলোড")
    private val all = listOf(wifi, downloadBy, downloads)

    @Test
    fun aBlankQueryFindsNothing() {
        assertTrue(searchSettings(all, "   ").isEmpty())
    }

    @Test
    fun theLabelIsFoundIgnoringCase() {
        assertEquals(listOf(wifi), searchSettings(all, "WI-FI"))
    }

    @Test
    fun aValueNameFindsTheSettingThatOffersIt() {
        assertEquals(listOf(downloadBy), searchSettings(all, "surah"))
    }

    @Test
    fun aBanglaQueryFindsASettingLabelledInEnglish() {
        assertEquals(listOf(wifi), searchSettings(all, "ওয়াই-ফাই"))
    }

    @Test
    fun everyWordOfTheQueryMustMatch() {
        assertEquals(listOf(downloadBy), searchSettings(all, "download juz"))
        assertTrue(searchSettings(all, "download wifi").isEmpty())
    }

    @Test
    fun labelsThatStartWithTheQueryComeBeforeOtherMatches() {
        val reciterDownloads = entry("Reciter downloads")
        val valueOnly = entry("Download by", "Downloads folder")

        val result = searchSettings(listOf(valueOnly, reciterDownloads, downloads), "downloads")

        assertEquals(listOf(downloads, reciterDownloads, valueOnly), result)
    }

    @Test
    fun aQueryWithNoMatchGivesAnEmptyList() {
        assertTrue(searchSettings(all, "zzz").isEmpty())
    }
}
