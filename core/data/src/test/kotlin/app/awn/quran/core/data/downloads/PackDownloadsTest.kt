package app.awn.quran.core.data.downloads

import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.CatalogueItem
import app.awn.quran.core.data.fakes.FakePackRepository
import app.awn.quran.core.data.fakes.InMemoryWantedPackStore
import app.awn.quran.core.data.fakes.RecordingPackWorkScheduler
import app.awn.quran.core.data.packs.Credit
import app.awn.quran.core.data.packs.PackEntry
import app.awn.quran.core.data.packs.PackFailureReason
import app.awn.quran.core.data.packs.PackInstallException
import app.awn.quran.core.data.packs.PackKind
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackDownloadsTest {

    private val wanted = InMemoryWantedPackStore()
    private val scheduler = RecordingPackWorkScheduler()
    private val downloads = PackDownloads(wanted, scheduler)
    private val packs = FakePackRepository()

    private fun item(id: String, installed: Boolean = false, hasUpdate: Boolean = false) =
        CatalogueItem(
            id = id,
            name = "Name of $id",
            group = CatalogueGroup.Translation,
            language = "en",
            downloadBytes = 1_000,
            requiredFreeBytes = 1_000,
            isInstalled = installed,
            installedBytes = if (installed) 1_000 else null,
            isBundled = false,
            hasUpdate = hasUpdate,
            entry =
                PackEntry(
                    id = id,
                    kind = PackKind.Translation,
                    formatVersion = 1,
                    sha256 = "c".repeat(64),
                    downloadUrl = "https://example.org/$id.sqlite",
                    credit = Credit("Credit", "https://example.org", "Not stated"),
                ),
        )

    /** A rule whose two answers the test sets by hand. */
    private class FixedNetworkRule(var wifiOnly: Boolean, var metered: Boolean) :
        DownloadNetworkRule {
        override fun isWifiOnly() = wifiOnly

        override fun isMetered() = metered
    }

    @Test
    fun aRequestOnMobileDataWhileWifiOnlyIsNotScheduledAndShowsARetryableFailure() = runBlocking {
        val rule = FixedNetworkRule(wifiOnly = true, metered = true)
        val limited = PackDownloads(wanted, scheduler, rule)

        limited.request("v2")

        assertTrue(scheduler.enqueued.isEmpty())
        assertEquals(setOf("v2"), wanted.ids())
        assertEquals(
            PackTransfer.Failed(PackFailureReason.WifiOnly),
            limited.transfers.first()["v2"],
        )
    }

    @Test
    fun wifiOnlyAllowsARequestOnWifiAndMobileDataIsAllowedWithoutTheLimit() {
        PackDownloads(wanted, scheduler, FixedNetworkRule(wifiOnly = true, metered = false))
            .request("on_wifi")
        PackDownloads(wanted, scheduler, FixedNetworkRule(wifiOnly = false, metered = true))
            .request("on_data")

        assertEquals(listOf("on_wifi", "on_data"), scheduler.enqueued)
    }

    @Test
    fun cancelDropsARefusedRequest() = runBlocking {
        val limited =
            PackDownloads(wanted, scheduler, FixedNetworkRule(wifiOnly = true, metered = true))
        limited.request("v2")

        limited.cancel("v2")

        assertTrue(wanted.ids().isEmpty())
        assertTrue(limited.transfers.first().isEmpty())
    }

    @Test
    fun aRefusedRequestStartsOnTheNextLaunchAndItsFailureGoes() = runBlocking {
        val rule = FixedNetworkRule(wifiOnly = true, metered = true)
        val limited = PackDownloads(wanted, scheduler, rule)
        limited.request("v2")
        packs.catalogueItems += item("v2")

        rule.metered = false
        limited.resumeWanted(packs)

        assertEquals(listOf("v2"), scheduler.enqueued)
        assertTrue(limited.transfers.first().isEmpty())
    }

    @Test
    fun requestWritesTheWantedFlagAndSchedulesTheInstall() {
        downloads.request("en_abdul_haleem")

        assertEquals(setOf("en_abdul_haleem"), wanted.ids())
        assertEquals(listOf("en_abdul_haleem"), scheduler.enqueued)
    }

    @Test
    fun cancelClearsTheWantedFlagSoTheInstallDoesNotRestart() = runBlocking {
        downloads.request("en_abdul_haleem")

        downloads.cancel("en_abdul_haleem")
        packs.catalogueItems += item("en_abdul_haleem")
        downloads.resumeWanted(packs)

        assertTrue(wanted.ids().isEmpty())
        assertEquals(listOf("en_abdul_haleem"), scheduler.cancelled)
        assertEquals(listOf("en_abdul_haleem"), scheduler.enqueued)
    }

    @Test
    fun anInterruptedInstallStartsAgainOnTheNextLaunch() = runBlocking {
        // The process died with the install unfinished: the flag is still in the store.
        val storeAfterRestart = InMemoryWantedPackStore(setOf("v2"))
        val newScheduler = RecordingPackWorkScheduler()
        packs.catalogueItems += item("v2")

        PackDownloads(storeAfterRestart, newScheduler).resumeWanted(packs)

        assertEquals(listOf("v2"), newScheduler.enqueued)
        assertEquals(setOf("v2"), storeAfterRestart.ids())
        assertEquals(1, newScheduler.pruneCount)
    }

    @Test
    fun aWantedPackThatIsInstalledAndCurrentIsNotInstalledAgain() = runBlocking {
        wanted.add("v2")
        packs.catalogueItems += item("v2", installed = true)

        downloads.resumeWanted(packs)

        assertTrue(scheduler.enqueued.isEmpty())
        assertTrue(wanted.ids().isEmpty())
    }

    @Test
    fun aWantedUpdateIsInstalledAgain() = runBlocking {
        wanted.add("v2")
        packs.catalogueItems += item("v2", installed = true, hasUpdate = true)

        downloads.resumeWanted(packs)

        assertEquals(listOf("v2"), scheduler.enqueued)
    }

    @Test
    fun aWantedPackThatTheManifestsNoLongerListIsForgotten() = runBlocking {
        wanted.add("gone")

        downloads.resumeWanted(packs)

        assertTrue(scheduler.enqueued.isEmpty())
        assertTrue(wanted.ids().isEmpty())
    }

    @Test
    fun successClearsTheFlagAndReportsTheProgress() = runBlocking {
        packs.catalogueItems += item("v2")
        wanted.add("v2")
        val progress = mutableListOf<Float>()

        val outcome = PackInstallRunner(packs, wanted).run("v2") { progress += it }

        assertEquals(PackInstallOutcome.Succeeded, outcome)
        assertTrue(wanted.ids().isEmpty())
        assertEquals(1f, progress.last(), 0f)
    }

    @Test
    fun aDroppedConnectionKeepsTheFlagAndAsksForARetry() = runBlocking {
        packs.catalogueItems += item("v2")
        wanted.add("v2")
        packs.installFailure =
            PackInstallException("v2.zip", "dropped", reason = PackFailureReason.NoInternet)

        val outcome = PackInstallRunner(packs, wanted).run("v2") {}

        assertEquals(PackInstallOutcome.RetryLater, outcome)
        assertEquals(setOf("v2"), wanted.ids())
    }

    @Test
    fun aFailureARetryCannotFixClearsTheFlagAndKeepsTheNumbers() = runBlocking {
        packs.catalogueItems += item("v2")
        wanted.add("v2")
        packs.installFailure =
            PackInstallException(
                "v2.zip",
                "no space",
                reason = PackFailureReason.NoSpace,
                neededBytes = 54_000_000L,
                freeBytes = 20_000_000L,
            )

        val outcome = PackInstallRunner(packs, wanted).run("v2") {}

        assertEquals(
            PackInstallOutcome.Failed(PackFailureReason.NoSpace, 54_000_000L, 20_000_000L),
            outcome,
        )
        assertTrue(wanted.ids().isEmpty())
    }

    @Test
    fun aPlainIoErrorIsMappedToItsReason() = runBlocking {
        packs.catalogueItems += item("v2")
        packs.installFailure = IOException("write failed: No space left on device")

        val outcome = PackInstallRunner(packs, wanted).run("v2") {}

        assertEquals(PackInstallOutcome.Failed(PackFailureReason.NoSpace), outcome)
    }

    @Test
    fun anIdTheManifestsDoNotListFails() = runBlocking {
        wanted.add("gone")

        val outcome = PackInstallRunner(packs, wanted).run("gone") {}

        assertEquals(PackInstallOutcome.Failed(PackFailureReason.Other), outcome)
        assertTrue(wanted.ids().isEmpty())
    }
}
