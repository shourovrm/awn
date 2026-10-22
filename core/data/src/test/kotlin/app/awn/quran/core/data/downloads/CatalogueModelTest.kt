package app.awn.quran.core.data.downloads

import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.fakes.FakePackRepository
import app.awn.quran.core.data.fakes.InMemoryWantedPackStore
import app.awn.quran.core.data.fakes.RecordingPackWorkScheduler
import app.awn.quran.core.data.fakes.fakeCatalogueItem
import app.awn.quran.core.data.packs.PackFailureReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogueModelTest {

    private val packs = FakePackRepository()
    private val scheduler = RecordingPackWorkScheduler()
    private val wanted = InMemoryWantedPackStore()
    private val downloads = PackDownloads(wanted, scheduler)

    private fun item(
        id: String,
        installed: Boolean = false,
        bundled: Boolean = false,
        downloadBytes: Long = 54_000_000L,
        installedBytes: Long? = if (installed) downloadBytes else null,
    ) =
        fakeCatalogueItem(
            id,
            downloadBytes = downloadBytes,
            installedBytes = installedBytes,
            isInstalled = installed,
            isBundled = bundled,
        )

    private fun TestScope.model() =
        CatalogueModel(packs, downloads, backgroundScope).also {
            // The unconfined collectors above run eagerly, so the state is ready at once.
        }

    @Test
    fun rowsJoinTheItemsWithTheirTransfers() =
        runTest(UnconfinedTestDispatcher()) {
            packs.catalogueItems += item("a")
            packs.catalogueItems += item("b")
            val model = model()
            scheduler.transferStates.value = mapOf("b" to PackTransfer.Running(0.5f))

            val state = model.state.value

            assertTrue(state.isLoaded)
            assertNull(state.rowFor("a")!!.transfer)
            assertEquals(PackTransfer.Running(0.5f), state.rowFor("b")!!.transfer)
            assertTrue(state.rowFor("b")!!.isBusy)
        }

    @Test
    fun installAsksForTheDownloadWhenThereIsRoom() =
        runTest(UnconfinedTestDispatcher()) {
            packs.catalogueItems += item("a")
            packs.freeBytesValue = 100_000_000L
            val model = model()

            model.install("a")

            assertEquals(listOf("a"), scheduler.enqueued)
            assertEquals(setOf("a"), wanted.ids())
        }

    @Test
    fun installNamesTheNumbersAndStartsNothingWhenTheSpaceIsShort() =
        runTest(UnconfinedTestDispatcher()) {
            packs.catalogueItems += item("a", downloadBytes = 54_000_000L)
            packs.freeBytesValue = 20_000_000L
            val model = model()

            model.install("a")

            assertTrue(scheduler.enqueued.isEmpty())
            assertTrue(wanted.ids().isEmpty())
            assertEquals(
                PackTransfer.Failed(PackFailureReason.NoSpace, 54_000_000L, 20_000_000L),
                model.state.value.rowFor("a")!!.transfer,
            )
        }

    @Test
    fun theSpaceProblemClearsOnceThereIsRoom() =
        runTest(UnconfinedTestDispatcher()) {
            packs.catalogueItems += item("a", downloadBytes = 54_000_000L)
            packs.freeBytesValue = 20_000_000L
            val model = model()
            model.install("a")

            packs.freeBytesValue = 90_000_000L
            model.install("a")

            assertEquals(listOf("a"), scheduler.enqueued)
            // The space problem is gone and the download is queued.
            assertEquals(PackTransfer.Waiting, model.state.value.rowFor("a")!!.transfer)
        }

    @Test
    fun removeDeletesAnInstalledItemAndStopsItsDownload() =
        runTest(UnconfinedTestDispatcher()) {
            packs.catalogueItems += item("a", installed = true)
            val model = model()

            model.remove("a")

            assertFalse(model.state.value.rowFor("a")!!.item.isInstalled)
            assertEquals(listOf("a"), scheduler.cancelled)
        }

    @Test
    fun removeLeavesABundledItemAlone() =
        runTest(UnconfinedTestDispatcher()) {
            packs.catalogueItems += item("a", installed = true, bundled = true)
            val model = model()

            model.remove("a")

            assertTrue(model.state.value.rowFor("a")!!.item.isInstalled)
            assertTrue(scheduler.cancelled.isEmpty())
        }

    @Test
    fun spaceUsedAddsTheInstalledSizes() =
        runTest(UnconfinedTestDispatcher()) {
            packs.catalogueItems += item("a", installed = true, installedBytes = 3_000_000L)
            packs.catalogueItems += item("b", installed = true, installedBytes = 2_000_000L)
            packs.catalogueItems += item("c")
            val model = model()

            assertEquals(5_000_000L, model.state.value.spaceUsedBytes)
        }

    /**
     * The Downloads page said "12 MB used" on a fresh install: the space counted the items that
     * ship with the app. The user reads the figure as what their own downloads take.
     */
    @Test
    fun spaceUsedLeavesOutItemsThatShipWithTheApp() =
        runTest(UnconfinedTestDispatcher()) {
            packs.catalogueItems +=
                item("bundled", installed = true, bundled = true, installedBytes = 12_000_000L)
            packs.catalogueItems += item("chosen", installed = true, installedBytes = 2_000_000L)
            val model = model()

            assertEquals(2_000_000L, model.state.value.spaceUsedBytes)
        }

    @Test
    fun spaceUsedIsZeroWhenOnlyItemsThatShipWithTheAppAreOnThePhone() =
        runTest(UnconfinedTestDispatcher()) {
            packs.catalogueItems +=
                item("bundled", installed = true, bundled = true, installedBytes = 12_000_000L)
            val model = model()

            assertEquals(0L, model.state.value.spaceUsedBytes)
        }

    @Test
    fun rowsInAGroupKeepTheManifestOrder() =
        runTest(UnconfinedTestDispatcher()) {
            packs.catalogueItems += fakeCatalogueItem("p", group = CatalogueGroup.PageType)
            packs.catalogueItems += item("t1")
            packs.catalogueItems += item("t2")
            val model = model()

            assertEquals(
                listOf("t1", "t2"),
                model.state.value.rowsIn(CatalogueGroup.Translation).map { it.item.id },
            )
        }

    @Test
    fun anItemNotOnThePhoneCanOnlyBeInstalled() {
        assertEquals(listOf(ItemAction.Install), actionsOf(CatalogueRow(item("a"), null)))
    }

    @Test
    fun aFailedDownloadCanBeTriedAgain() {
        val failed = PackTransfer.Failed(PackFailureReason.NoInternet)

        assertEquals(listOf(ItemAction.Install), actionsOf(CatalogueRow(item("a"), failed)))
    }

    @Test
    fun aQueuedOrRunningDownloadCanOnlyBeCancelled() {
        assertEquals(
            listOf(ItemAction.Cancel),
            actionsOf(CatalogueRow(item("a"), PackTransfer.Waiting)),
        )
        assertEquals(
            listOf(ItemAction.Cancel),
            actionsOf(CatalogueRow(item("a"), PackTransfer.Running(0.2f))),
        )
    }

    @Test
    fun anInstalledItemCanBeRemoved() {
        assertEquals(
            listOf(ItemAction.Remove),
            actionsOf(CatalogueRow(item("a", installed = true), null)),
        )
    }

    @Test
    fun anInstalledItemWithAnUpdateCanBeUpdatedAndRemoved() {
        val outdated = item("a", installed = true).copy(hasUpdate = true)

        assertEquals(
            listOf(ItemAction.Update, ItemAction.Remove),
            actionsOf(CatalogueRow(outdated, null)),
        )
    }

    @Test
    fun aBundledItemHasNoActionUnlessItHasAnUpdate() {
        val bundled = item("a", installed = true, bundled = true)

        assertEquals(emptyList<ItemAction>(), actionsOf(CatalogueRow(bundled, null)))
        assertEquals(
            listOf(ItemAction.Update),
            actionsOf(CatalogueRow(bundled.copy(hasUpdate = true), null)),
        )
    }
}
