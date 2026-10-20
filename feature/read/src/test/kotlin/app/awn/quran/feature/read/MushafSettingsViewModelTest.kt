package app.awn.quran.feature.read

import app.awn.quran.core.data.PackSize
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.packs.Credit
import app.awn.quran.core.data.packs.PackEntry
import app.awn.quran.core.data.packs.PackKind
import app.awn.quran.feature.read.page.MushafSettingsViewModel
import app.awn.quran.feature.read.page.megabytesOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MushafSettingsViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()
    private val viewModel by lazy { MushafSettingsViewModel(fixture.dependencies) }

    private fun scriptPack(id: String) =
        PackEntry(
            id = id,
            kind = PackKind.Script,
            formatVersion = 1,
            sha256 = "0".repeat(64),
            downloadUrl = "https://example.org/$id.zip",
            credit = Credit("test", "https://example.org", "test"),
        )

    @Test
    fun everyPageTypeShowsItsSizeBeforeItIsInstalled() = runTest {
        fixture.packs.sizes["v2"] = PackSize(137_055_055, null)
        fixture.packs.sizes["v1"] = PackSize(54_470_805, null)

        val statuses = viewModel.statuses.value

        assertEquals(137_055_055L, statuses.getValue(ScriptId.V2).downloadBytes)
        assertEquals(54_470_805L, statuses.getValue(ScriptId.V1).downloadBytes)
        assertFalse(statuses.getValue(ScriptId.V2).isInstalled)
        assertNull(statuses.getValue(ScriptId.V2).installedBytes)
    }

    @Test
    fun installingAPageTypePutsItInUseWhenTheFontIsOnThePhone() = runTest {
        fixture.packs.manifests["scripts"] = listOf(scriptPack("v1"))

        viewModel.install(ScriptId.V1)

        assertTrue(viewModel.statuses.value.getValue(ScriptId.V1).isInstalled)
        assertEquals(ScriptId.V1, viewModel.settings.value.script)
    }

    @Test
    fun theInstalledSizeReplacesTheDownloadSizeAfterInstall() = runTest {
        fixture.packs.manifests["scripts"] = listOf(scriptPack("v2"))
        fixture.packs.sizes["v2"] = PackSize(137_055_055, 208_100_000)

        viewModel.install(ScriptId.V2)

        assertEquals(208_100_000L, viewModel.statuses.value.getValue(ScriptId.V2).installedBytes)
    }

    @Test
    fun theTypeInUseCannotBeRemovedButAnotherCanBe() = runTest {
        fixture.packs.manifests["scripts"] = listOf(scriptPack("v2"), scriptPack("v1"))
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

    @Test
    fun sizesAreWrittenInDecimalMegabytesWithOneDecimal() {
        assertEquals("137.1", megabytesOf(137_055_055))
        assertEquals("54.5", megabytesOf(54_470_805))
        assertEquals("0.2", megabytesOf(242_368))
    }
}
