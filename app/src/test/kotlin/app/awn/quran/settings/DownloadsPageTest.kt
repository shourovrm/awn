package app.awn.quran.settings

import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.fakes.fakeCatalogueItem
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsPageTest {

    private fun row(id: String, language: String?) =
        CatalogueRow(fakeCatalogueItem(id, language = language), transfer = null)

    @Test
    fun translationsAreGroupedByLanguageInManifestOrder() {
        val groups =
            groupByLanguage(
                listOf(
                    row("en_saheeh", "en"),
                    row("bn_zakaria", "bn"),
                    row("en_abdul_haleem", "en"),
                ),
            )

        assertEquals(listOf("en", "bn"), groups.map { it.language })
        assertEquals(
            listOf("en_saheeh", "en_abdul_haleem"),
            groups.first().rows.map { it.item.id },
        )
    }

    @Test
    fun anItemWithNoLanguageHasItsOwnGroup() {
        val groups = groupByLanguage(listOf(row("a", null), row("b", "en")))

        assertEquals(listOf(null, "en"), groups.map { it.language })
    }

    @Test
    fun noRowsGiveNoGroups() {
        assertEquals(emptyList<LanguageGroup>(), groupByLanguage(emptyList()))
    }
}
