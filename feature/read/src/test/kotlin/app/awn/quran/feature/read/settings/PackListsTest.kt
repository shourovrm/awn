package app.awn.quran.feature.read.settings

import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.fakes.fakeCatalogueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackListsTest {

    private fun row(
        id: String,
        language: String? = "en",
        installed: Boolean = false,
        bundled: Boolean = false,
    ) =
        CatalogueRow(
            fakeCatalogueItem(
                id,
                language = language,
                isInstalled = installed,
                isBundled = bundled,
                installedBytes = if (installed) 4_200_000L else null,
            ),
            transfer = null,
        )

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
    fun aDownloadedEntryCanBeRemovedAndNamesTheSpaceItFrees() {
        val removal = removalOf(row("bn_quantum", installed = true), "Quantum Foundation")

        assertEquals("Quantum Foundation", removal?.name)
        assertEquals(4_200_000L, removal?.freedBytes)
    }

    @Test
    fun anEntryThatShipsInsideTheAppCannotBeRemoved() {
        assertNull(removalOf(row("bn_zakaria", installed = true, bundled = true), "Zakaria"))
    }

    @Test
    fun anEntryThatIsNotOnThePhoneCannotBeRemoved() {
        assertNull(removalOf(row("en_abdul_haleem"), "Abdel Haleem"))
    }

    @Test
    fun everyKindListsItsOwnGroup() {
        assertEquals(
            setOf(
                CatalogueGroup.PageType,
                CatalogueGroup.Translation,
                CatalogueGroup.Transliteration,
                CatalogueGroup.WordByWord,
                CatalogueGroup.WordAudio,
            ),
            PackListKind.entries.map { it.group }.toSet(),
        )
    }

    @Test
    fun theChosenWordLanguageIsShownWhenItsPackIsOnThePhone() {
        assertEquals("ur", wordLanguageShown("ur", appLanguage = "bn", setOf("en", "bn", "ur")))
    }

    @Test
    fun withNoChoiceTheWordLanguageIsTheAppLanguage() {
        assertEquals("bn", wordLanguageShown(null, appLanguage = "bn", setOf("en", "bn")))
    }

    @Test
    fun aWordLanguageWithoutAPackFallsBackToEnglish() {
        assertEquals("en", wordLanguageShown("ur", appLanguage = "bn", setOf("en", "bn")))
        assertEquals("en", wordLanguageShown(null, appLanguage = "ar", setOf("en", "bn")))
    }

    @Test
    fun installedWordLanguagesSkipsPacksThatAreNotOnThePhone() {
        val rows =
            listOf(
                row("wbw_en", "en", installed = true),
                row("wbw_bn", "bn"),
                row("wbw_ur", "ur", installed = true),
            )

        assertEquals(listOf("en", "ur"), installedWordLanguages(rows))
    }
}
