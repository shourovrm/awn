package app.awn.quran.feature.read

import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.downloads.PackDownloads
import app.awn.quran.core.data.fakes.InMemoryWantedPackStore
import app.awn.quran.core.data.fakes.RecordingPackWorkScheduler
import app.awn.quran.core.data.fakes.fakeCatalogueItem
import app.awn.quran.feature.read.page.MushafSettingsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MushafSettingsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()
    private val viewModel by lazy { MushafSettingsViewModel(fixture.dependencies) }

    private fun pageType(id: String, downloadBytes: Long, installedBytes: Long? = null) =
        fakeCatalogueItem(
            id,
            group = CatalogueGroup.PageType,
            language = null,
            downloadBytes = downloadBytes,
            installedBytes = installedBytes,
        )

    @Test
    fun everyPageTypeShowsItsSizeBeforeItIsInstalled() = runTest {
        fixture.packs.catalogueItems += pageType("v2", 137_055_055)
        fixture.packs.catalogueItems += pageType("v1", 54_470_805)

        val statuses = viewModel.statuses.value

        assertEquals(137_055_055L, statuses.getValue(ScriptId.V2).downloadBytes)
        assertEquals(54_470_805L, statuses.getValue(ScriptId.V1).downloadBytes)
        assertFalse(statuses.getValue(ScriptId.V2).isInstalled)
        assertNull(statuses.getValue(ScriptId.V2).installedBytes)
    }

    @Test
    fun installingAPageTypePutsItInUseWhenTheFontIsOnThePhone() = runTest {
        fixture.packs.catalogueItems += pageType("v1", 54_470_805)

        viewModel.install(ScriptId.V1)

        assertTrue(viewModel.statuses.value.getValue(ScriptId.V1).isInstalled)
        assertEquals(ScriptId.V1, viewModel.settings.value.script)
    }

    @Test
    fun usingAnIndoPakPrintBeforeItIsInstalledDownloadsItInsteadOfSwitching() = runTest {
        fixture.packs.catalogueItems += pageType("indopak-15", 6_336_000)

        viewModel.use(ScriptId.IndoPak15)

        // The print becomes the one in use when the install ends, not before.
        assertTrue(viewModel.statuses.value.getValue(ScriptId.IndoPak15).isInstalled)
        assertEquals(ScriptId.IndoPak15, viewModel.settings.value.script)
    }

    @Test
    fun anIndoPakPrintThatIsNotInstalledIsNeverTheOneInUseWhileItDownloads() = runTest {
        fixture.packs.catalogueItems += pageType("indopak-15", 6_336_000)
        val scheduler = RecordingPackWorkScheduler()
        val dependencies =
            fixture.dependencies.copy(
                downloads = PackDownloads(InMemoryWantedPackStore(), scheduler),
            )
        val slowViewModel = MushafSettingsViewModel(dependencies)

        slowViewModel.use(ScriptId.IndoPak15)

        assertEquals(listOf("indopak-15"), scheduler.enqueued)
        assertNotEquals(ScriptId.IndoPak15, slowViewModel.settings.value.script)
    }

    @Test
    fun theInstalledSizeReplacesTheDownloadSizeAfterInstall() = runTest {
        fixture.packs.catalogueItems += pageType("v2", 137_055_055, installedBytes = 208_100_000)

        viewModel.install(ScriptId.V2)

        assertEquals(208_100_000L, viewModel.statuses.value.getValue(ScriptId.V2).installedBytes)
    }

    @Test
    fun theTypeInUseCannotBeRemovedButAnotherCanBe() = runTest {
        fixture.packs.catalogueItems += pageType("v2", 137_055_055)
        fixture.packs.catalogueItems += pageType("v1", 54_470_805)
        viewModel.install(ScriptId.V2)
        viewModel.install(ScriptId.V1)
        assertEquals(ScriptId.V1, viewModel.settings.value.script)

        viewModel.remove(ScriptId.V1)
        assertTrue(viewModel.statuses.value.getValue(ScriptId.V1).isInstalled)

        viewModel.remove(ScriptId.V2)
        assertFalse(viewModel.statuses.value.getValue(ScriptId.V2).isInstalled)
    }

    @Test
    fun showPageInfoIsStoredAndOnByDefault() = runTest {
        assertTrue(viewModel.settings.value.showPageInfo)

        viewModel.setShowPageInfo(false)

        assertFalse(viewModel.settings.value.showPageInfo)
    }
}
