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
import app.awn.quran.feature.read.quran.printOfferDownloadBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun viewModelWith(
        isPending: Boolean,
        installed: Set<ScriptId> = emptySet(),
    ): Pair<QuranViewModel, MemoryPrintOfferStore> {
        val store = MemoryPrintOfferStore(isPending)
        fixture.packs.catalogueItems +=
            listOf(
                printItem(ScriptId.V2, 137, ScriptId.V2 in installed),
                printItem(ScriptId.V1, 55, ScriptId.V1 in installed),
                printItem(ScriptId.V4Tajweed, 69, ScriptId.V4Tajweed in installed),
            )
        val dependencies =
            fixture.dependencies.copy(
                downloads = PackDownloads(InMemoryWantedPackStore(), scheduler),
                printOffer = store,
            )
        return QuranViewModel(dependencies, wordLanguage = "en") to store
    }

    @Test
    fun theCardOpensOnTheFirstQuranScreenOfAFreshInstallation() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)

        viewModel.open(null)

        assertTrue(viewModel.state.value.printOffer.isOpen)
        assertEquals(setOf(ScriptId.V2), viewModel.state.value.printOffer.choices)
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
    fun tickingAPrintAddsItToTheChoices() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)
        viewModel.open(null)

        viewModel.choosePrint(ScriptId.V4Tajweed)

        val state = viewModel.state.value
        assertEquals(setOf(ScriptId.V2, ScriptId.V4Tajweed), state.printOffer.choices)
        assertEquals(
            (137L + 69L) * 1_000_000L,
            printOfferDownloadBytes(state.printOffer, state.printStatuses),
        )
    }

    @Test
    fun tickingATickedPrintAgainTakesItOut() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)
        viewModel.open(null)

        viewModel.choosePrint(ScriptId.V2)
        viewModel.choosePrint(ScriptId.V1)

        val state = viewModel.state.value
        assertEquals(setOf(ScriptId.V1), state.printOffer.choices)
        assertEquals(55_000_000L, printOfferDownloadBytes(state.printOffer, state.printStatuses))
    }

    @Test
    fun withNothingTickedDownloadDoesNothing() = runTest {
        val (viewModel, store) = viewModelWith(isPending = true)
        viewModel.open(null)
        viewModel.choosePrint(ScriptId.V2)

        viewModel.downloadPrint()

        assertTrue(viewModel.state.value.printOffer.choices.isEmpty())
        assertTrue(scheduler.enqueued.isEmpty())
        assertTrue(viewModel.state.value.printOffer.isOpen)
        assertTrue(store.pending.value)
    }

    @Test
    fun downloadingSeveralPrintsInstallsEachAndShowsTheFirstInCardOrder() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)
        viewModel.open(null)
        viewModel.choosePrint(ScriptId.V4Tajweed)
        viewModel.choosePrint(ScriptId.V1)

        viewModel.downloadPrint()

        assertEquals(setOf("v2", "v1", "v4-tajweed"), scheduler.enqueued.toSet())
        assertEquals(3, scheduler.enqueued.size)
        // The card lists 1421 (V2) first, so the page shows it while the others download.
        assertEquals(ScriptId.V2, fixture.store.settings.first().script)
        assertEquals(
            setOf(ScriptId.V2, ScriptId.V1, ScriptId.V4Tajweed),
            viewModel.state.value.printOffer.downloading,
        )
    }

    @Test
    fun theCardClosesOnlyWhenEveryChosenPrintIsInstalled() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)
        viewModel.open(null)
        viewModel.choosePrint(ScriptId.V1)
        viewModel.downloadPrint()

        fixture.packs.install(fixture.packs.catalogueItems.first { it.id == "v2" }.entry) {}

        assertTrue(viewModel.state.value.printOffer.isOpen)

        fixture.packs.install(fixture.packs.catalogueItems.first { it.id == "v1" }.entry) {}

        assertFalse(viewModel.state.value.printOffer.isOpen)
        assertTrue(viewModel.state.value.printOffer.downloading.isEmpty())
    }

    @Test
    fun cancelStopsEveryChosenDownload() = runTest {
        val (viewModel, _) = viewModelWith(isPending = true)
        viewModel.open(null)
        viewModel.choosePrint(ScriptId.V1)
        viewModel.downloadPrint()

        viewModel.cancelPrintDownload()

        assertEquals(setOf("v2", "v1"), scheduler.cancelled.toSet())
        assertTrue(viewModel.state.value.printOffer.downloading.isEmpty())
        assertTrue(viewModel.state.value.printOffer.isOpen)
    }

    @Test
    fun downloadSetsThePageTypeStartsTheInstallAndRemembersTheOffer() = runTest {
        val (viewModel, store) = viewModelWith(isPending = true)
        viewModel.open(null)
        viewModel.choosePrint(ScriptId.V4Tajweed)
        viewModel.choosePrint(ScriptId.V2)

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
        viewModel.choosePrint(ScriptId.V2)
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
}
