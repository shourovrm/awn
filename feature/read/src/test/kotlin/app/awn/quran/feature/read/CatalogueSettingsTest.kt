package app.awn.quran.feature.read

import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.CatalogueItem
import app.awn.quran.core.data.TranslationId
import app.awn.quran.core.data.downloads.CatalogueRow
import app.awn.quran.core.data.downloads.CatalogueState
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.data.packs.Credit
import app.awn.quran.core.data.packs.PackEntry
import app.awn.quran.core.data.packs.PackKind
import app.awn.quran.feature.read.settings.missingTranslations
import app.awn.quran.feature.read.settings.orderedForDisplay
import app.awn.quran.feature.read.settings.rowToOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogueSettingsTest {

    private fun row(
        id: String,
        group: CatalogueGroup,
        language: String? = "en",
        installed: Boolean = false,
        transfer: PackTransfer? = null,
    ) =
        CatalogueRow(
            CatalogueItem(
                id = id,
                name = "Name of $id",
                group = group,
                language = language,
                downloadBytes = 1_000L,
                requiredFreeBytes = 1_000L,
                isInstalled = installed,
                installedBytes = if (installed) 1_000L else null,
                isBundled = false,
                hasUpdate = false,
                entry =
                    PackEntry(
                        id = id,
                        kind = PackKind.Translation,
                        formatVersion = 1,
                        sha256 = "e".repeat(64),
                        downloadUrl = "https://example.org/$id.sqlite",
                        credit = Credit("Credit", "https://example.org", "Not stated"),
                    ),
            ),
            transfer,
        )

    private fun stateOf(vararg rows: CatalogueRow) =
        CatalogueState(rows = rows.toList(), isLoaded = true)

    @Test
    fun wordByWordOffersTheDownloadOfTheAppLanguageWhenItIsMissing() {
        val state =
            stateOf(
                row("wbw_en", CatalogueGroup.WordByWord, "en", installed = true),
                row("wbw_bn", CatalogueGroup.WordByWord, "bn"),
            )

        assertEquals("wbw_bn", rowToOffer(state, CatalogueGroup.WordByWord, "bn")!!.item.id)
        assertNull(rowToOffer(state, CatalogueGroup.WordByWord, "en"))
    }

    @Test
    fun noOfferWhenTheManifestsListNothingForTheLanguage() {
        val state = stateOf(row("wbw_en", CatalogueGroup.WordByWord, "en"))

        assertNull(rowToOffer(state, CatalogueGroup.WordByWord, "bn"))
    }

    @Test
    fun transliterationOffersTheFirstItemWhenNoneIsInstalled() {
        val state =
            stateOf(
                row("tr_a", CatalogueGroup.Transliteration),
                row("tr_b", CatalogueGroup.Transliteration),
            )

        assertEquals("tr_a", rowToOffer(state, CatalogueGroup.Transliteration, null)!!.item.id)
    }

    @Test
    fun transliterationNeedsNoOfferOnceAnyItemIsInstalled() {
        val state =
            stateOf(
                row("tr_a", CatalogueGroup.Transliteration),
                row("tr_b", CatalogueGroup.Transliteration, installed = true),
            )

        assertNull(rowToOffer(state, CatalogueGroup.Transliteration, null))
    }

    @Test
    fun enabledTranslationsThatAreNotOnThePhoneAreMissing() {
        val state =
            stateOf(
                row("en_saheeh", CatalogueGroup.Translation, installed = true),
                row("en_abdul_haleem", CatalogueGroup.Translation),
                row("bn_quantum", CatalogueGroup.Translation, "bn"),
            )
        val enabled = listOf(TranslationId("en_saheeh"), TranslationId("en_abdul_haleem"))

        assertEquals(
            listOf("en_abdul_haleem"),
            missingTranslations(enabled, state).map { it.item.id },
        )
    }

    @Test
    fun noPromptWhenEveryEnabledTranslationIsInstalledOrUnknown() {
        val state = stateOf(row("en_saheeh", CatalogueGroup.Translation, installed = true))
        val enabled = listOf(TranslationId("en_saheeh"), TranslationId("not_in_manifest"))

        assertEquals(emptyList<CatalogueRow>(), missingTranslations(enabled, state))
    }

    @Test
    fun theMissingTranslationKeepsItsProgressForThePrompt() {
        val state =
            stateOf(
                row(
                    "en_abdul_haleem",
                    CatalogueGroup.Translation,
                    transfer = PackTransfer.Running(0.4f),
                ),
            )

        val missing = missingTranslations(listOf(TranslationId("en_abdul_haleem")), state)

        assertEquals(PackTransfer.Running(0.4f), missing.single().transfer)
    }

    @Test
    fun enabledTranslationsComeFirstInTheUsersOrder() {
        val rows =
            listOf(
                row("a", CatalogueGroup.Translation),
                row("b", CatalogueGroup.Translation),
                row("c", CatalogueGroup.Translation),
            )

        val ordered = orderedForDisplay(rows, listOf(TranslationId("c"), TranslationId("a")))

        assertEquals(listOf("c", "a", "b"), ordered.map { it.item.id })
    }
}
