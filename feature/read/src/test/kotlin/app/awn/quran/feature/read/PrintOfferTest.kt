package app.awn.quran.feature.read

import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.downloads.PackDownloads
import app.awn.quran.core.data.downloads.PackTransfer
import app.awn.quran.core.data.fakes.InMemoryWantedPackStore
import app.awn.quran.core.data.fakes.RecordingPackWorkScheduler
import app.awn.quran.core.data.fakes.fakeCatalogueItem
import app.awn.quran.core.data.packs.PackFailureReason
import app.awn.quran.feature.read.quran.PrintOfferStore
import app.awn.quran.feature.read.quran.QuranViewModel
import app.awn.quran.feature.read.quran.preselectedPrintFor
import app.awn.quran.feature.read.quran.printOfferDownloadBytes
import app.awn.quran.feature.read.quran.printOfferProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class MemoryPrintOfferStore(isPending: Boolean) : PrintOfferStore {
    val pending = MutableStateFlow(isPending)
    override val isPending = pending

    override suspend fun markHandled() {
        pending.value = false
    }
}

class PrintOfferTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()
    private val scheduler = RecordingPackWorkScheduler()

    private fun printItem(script: ScriptId, megabytes: Long, installed: Boolean = false) =
        fakeCatalogueItem(
            id = script.packId,
            group = CatalogueGroup.PageType,
            language = null,
            downloadBytes = megabytes * 1_000_000L,
            isInstalled = installed,
        )

    private fun starterItem(id: String, group: CatalogueGroup, megabytes: Long) =
        fakeCatalogueItem(
            id = id,
            group = group,
            language = null,
            downloadBytes = megabytes * 1_000_000L,
        )

    private fun viewModelWith(
        isPending: Boolean,
        installed: Set<ScriptId> = emptySet(),
        language: String = "ar",
    ): Pair<QuranViewModel, MemoryPrintOfferStore> {
        val store = MemoryPrintOfferStore(isPending)
        fixture.packs.catalogueItems +=
            listOf(
                printItem(ScriptId.V2, 137, ScriptId.V2 in installed),
                printItem(ScriptId.V1, 55, ScriptId.V1 in installed),
                printItem(ScriptId.V4Tajweed, 69, ScriptId.V4Tajweed in installed),
                printItem(ScriptId.IndoPak15, 6, ScriptId.IndoPak15 in installed),
                starterItem("en_saheeh", CatalogueGroup.Translation, 9),
                starterItem("wbw_en", CatalogueGroup.WordByWord, 3),
                starterItem("bn_zakaria", CatalogueGroup.Translation, 8),
                starterItem("wbw_bn", CatalogueGroup.WordByWord, 4),
            )
        val dependencies =
            fixture.dependencies.copy(
                downloads = PackDownloads(InMemoryWantedPackStore(), scheduler),
                printOffer = store,
            )
        return QuranViewModel(dependencies, wordLanguage = language) to store
    }

    @Test
    fun theCardOpensOnTheFirstQuranScreenOfAFreshInstallation() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)

        viewModel.open(null)

        assertTrue(viewModel.state.value.printOffer.isOpen)
        assertEquals(ScriptId.V2, viewModel.state.value.printOffer.selectedScript)
        assertEquals(137_000_000L, viewModel.state.value.printStatuses[ScriptId.V2]?.downloadBytes)
    }

    @Test
    fun theCardNeverOpensWhenTheOfferIsNotPending() = runTest {
        val (viewModel, _) = viewModelWith(isPending = false)

        viewModel.open(null)

        assertFalse(viewModel.state.value.printOffer.isOpen)
    }

    @Test
    fun theCardNeverOpensForAnUpgradeThatAlreadyHasAPrintInstalled() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true, installed = setOf(ScriptId.V1))
        fixture.packs.install(fixture.packs.catalogueItems.first { it.id == "v1" }.entry) {}

        viewModel.open(null)

        assertFalse(viewModel.state.value.printOffer.isOpen)
    }

    @Test
    fun notNowClosesTheCardAndTheOfferNeverReturns() = runTest {
        val (viewModel, store) = viewModelWith(isPending = true)
        viewModel.open(null)

        viewModel.dismissPrintOffer()

        assertFalse(viewModel.state.value.printOffer.isOpen)
        assertFalse(store.pending.value)
        assertTrue(scheduler.enqueued.isEmpty())
    }

    @Test
    fun backOnTheQuestionClosesTheCardAndRemembersIt() = runTest {
        val (viewModel, store) = viewModelWith(isPending = true)
        viewModel.open(null)

        assertTrue(viewModel.state.value.hasBackWork)
        assertTrue(viewModel.onBack())

        assertFalse(viewModel.state.value.printOffer.isOpen)
        assertFalse(store.pending.value)
    }

    @Test
    fun theCardOffersTheIndoPakPrintWithItsSize() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)

        viewModel.open(null)

        val indoPakStatus = viewModel.state.value.printStatuses.getValue(ScriptId.IndoPak15)
        assertEquals(6_000_000L, indoPakStatus.downloadBytes)
        assertFalse(indoPakStatus.isInstalled)
    }

    @Test
    fun anIndoPakPrintOnItsOwnIsUsedOnlyOnceItIsInstalled() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)
        viewModel.open(null)
        viewModel.choosePrint(ScriptId.IndoPak15)

        viewModel.downloadPrint()

        assertEquals(listOf("indopak-15"), scheduler.enqueued)
        // Its pages cannot be read before the layouts are on the phone.
        assertNotEquals(ScriptId.IndoPak15, fixture.store.settings.first().script)

        fixture.packs.install(fixture.packs.catalogueItems.first { it.id == "indopak-15" }.entry) {}

        assertEquals(ScriptId.IndoPak15, fixture.store.settings.first().script)
    }

    @Test
    fun theCardClosesOnlyWhenEverythingStartedIsInstalled() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true, language = "en")
        viewModel.open(null)
        viewModel.downloadPrint()

        fixture.packs.install(fixture.packs.catalogueItems.first { it.id == "v2" }.entry) {}

        assertTrue(viewModel.state.value.printOffer.isOpen)

        fixture.packs.install(fixture.packs.catalogueItems.first { it.id == "en_saheeh" }.entry) {}
        fixture.packs.install(fixture.packs.catalogueItems.first { it.id == "wbw_en" }.entry) {}

        assertFalse(viewModel.state.value.printOffer.isOpen)
        assertTrue(viewModel.state.value.printOffer.downloading.isEmpty())
    }

    @Test
    fun downloadSetsThePageTypeStartsTheInstallAndRemembersTheOffer() = runTest {
        val (viewModel, store) = viewModelWith(isPending = true)
        viewModel.open(null)
        viewModel.choosePrint(ScriptId.V4Tajweed)

        viewModel.downloadPrint()

        assertEquals(ScriptId.V4Tajweed, fixture.store.settings.first().script)
        assertEquals(listOf("v4-tajweed"), scheduler.enqueued)
        assertEquals(setOf(ScriptId.V4Tajweed), viewModel.state.value.printOffer.downloading)
        assertFalse(store.pending.value)
    }

    @Test
    fun keepReadingClosesTheCardWhileTheDownloadContinues() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)
        viewModel.open(null)
        viewModel.downloadPrint()

        viewModel.keepReadingDuringPrintDownload()

        assertFalse(viewModel.state.value.printOffer.isOpen)
        assertTrue(scheduler.cancelled.isEmpty())
        assertEquals(listOf("v2"), scheduler.enqueued)
    }

    @Test
    fun backDuringTheDownloadKeepsReading() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)
        viewModel.open(null)
        viewModel.downloadPrint()

        assertTrue(viewModel.onBack())

        assertFalse(viewModel.state.value.printOffer.isOpen)
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test
    fun cancelStopsTheDownloadAndRestoresThePageType() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)
        viewModel.open(null)
        viewModel.choosePrint(ScriptId.V1)
        viewModel.downloadPrint()

        viewModel.cancelPrintDownload()

        assertEquals(listOf("v1"), scheduler.cancelled)
        assertEquals(ScriptId.V2, fixture.store.settings.first().script)
        assertTrue(viewModel.state.value.printOffer.downloading.isEmpty())
        assertTrue(viewModel.state.value.printOffer.isOpen)
    }

    @Test
    fun aFailureWhileTheCardIsOpenReturnsToTheQuestionWithoutAMessage() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)
        viewModel.open(null)
        viewModel.downloadPrint()

        scheduler.transferStates.value =
            mapOf("v2" to PackTransfer.Failed(PackFailureReason.NoInternet))

        val offer = viewModel.state.value.printOffer
        assertTrue(offer.downloading.isEmpty())
        assertTrue(offer.isOpen)
        assertEquals(0, offer.closedFailureCount)
        assertEquals(
            PackFailureReason.NoInternet,
            viewModel.state.value.printStatuses[ScriptId.V2]?.failure?.reason,
        )
    }

    @Test
    fun aFailureAfterTheCardWasClosedCountsForTheShortMessage() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)
        viewModel.open(null)
        viewModel.downloadPrint()
        viewModel.keepReadingDuringPrintDownload()

        scheduler.transferStates.value =
            mapOf("v2" to PackTransfer.Failed(PackFailureReason.NoInternet))

        assertEquals(1, viewModel.state.value.printOffer.closedFailureCount)
    }

    @Test
    fun banglaPreselectsIndoPakAndEveryOtherLanguageTheMadinahPrint() = runTest {
        assertEquals(ScriptId.IndoPak15, preselectedPrintFor("bn"))
        assertEquals(ScriptId.V2, preselectedPrintFor("en"))
        assertEquals(ScriptId.V2, preselectedPrintFor("ar"))

        val (viewModel, _) = viewModelWith(isPending = true, language = "bn")
        viewModel.open(null)

        assertEquals(ScriptId.IndoPak15, viewModel.state.value.printOffer.selectedScript)
    }

    @Test
    fun choosingAnotherEditionReplacesTheSelectedOne() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)
        viewModel.open(null)

        viewModel.choosePrint(ScriptId.V4Tajweed)
        viewModel.choosePrint(ScriptId.V1)

        val state = viewModel.state.value
        assertEquals(ScriptId.V1, state.printOffer.selectedScript)
        assertEquals(55_000_000L, printOfferDownloadBytes(state.printOffer, state.printStatuses))
    }

    @Test
    fun theTotalIsTheEditionPlusTheTickedTranslationDownloads() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true, language = "en")
        viewModel.open(null)

        val ticked = viewModel.state.value
        assertTrue(ticked.printOffer.offersStarter)
        assertEquals(
            (137L + 9L + 3L) * 1_000_000L,
            printOfferDownloadBytes(ticked.printOffer, ticked.printStatuses),
        )

        viewModel.toggleStarterDownload()

        val unticked = viewModel.state.value
        assertEquals(
            137_000_000L,
            printOfferDownloadBytes(unticked.printOffer, unticked.printStatuses),
        )
    }

    @Test
    fun aLanguageWithoutStarterDownloadsOffersNoTickBox() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true, language = "ar")

        viewModel.open(null)

        assertFalse(viewModel.state.value.printOffer.offersStarter)
    }

    @Test
    fun downloadInstallsTheEditionAndTheTickedTranslationAndTurnsThemOn() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true, language = "en")
        viewModel.open(null)

        viewModel.downloadPrint()

        assertEquals(setOf("v2", "en_saheeh", "wbw_en"), scheduler.enqueued.toSet())
        val settings = fixture.store.settings.first()
        assertTrue(settings.translations.any { it.id == "en_saheeh" })
        assertTrue(settings.showWordByWord)
        assertEquals(149_000_000L, viewModel.state.value.printOffer.startedBytes)
    }

    @Test
    fun downloadWithTheTranslationUntickedInstallsOnlyTheEdition() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true, language = "en")
        viewModel.open(null)
        viewModel.toggleStarterDownload()

        viewModel.downloadPrint()

        assertEquals(listOf("v2"), scheduler.enqueued)
    }

    @Test
    fun cancelStopsTheEditionAndTheTranslationDownloads() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true, language = "en")
        viewModel.open(null)
        viewModel.downloadPrint()

        viewModel.cancelPrintDownload()

        assertEquals(setOf("v2", "en_saheeh", "wbw_en"), scheduler.cancelled.toSet())
        assertFalse(viewModel.state.value.printOffer.isDownloading)
        assertTrue(viewModel.state.value.printOffer.isOpen)
    }

    @Test
    fun progressIsWeightedBytesOverEverythingStarted() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true, language = "en")
        viewModel.open(null)
        viewModel.downloadPrint()

        scheduler.transferStates.value =
            mapOf(
                "v2" to PackTransfer.Running(0.5f),
                "en_saheeh" to PackTransfer.Running(0f),
                "wbw_en" to PackTransfer.Running(0f),
            )

        val state = viewModel.state.value
        assertEquals(
            68.5f / 149f,
            printOfferProgress(state.printOffer, state.printStatuses),
            0.001f,
        )
    }
}
