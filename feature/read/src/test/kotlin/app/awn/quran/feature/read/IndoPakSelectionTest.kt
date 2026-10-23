package app.awn.quran.feature.read

import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.CatalogueGroup
import app.awn.quran.core.data.PageLayout
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.fakes.fakeCatalogueItem
import app.awn.quran.feature.read.page.PageLoad
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.quran.QuranViewModel
import app.awn.quran.feature.read.quran.readablePrintOf
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.ReadSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The IndoPak print saved while its layouts are not on the phone, which version 0.1.0 allowed: the
 * print was saved the moment it was ticked, before the download. The real repository throws
 * [UnsupportedOperationException] from every call that needs `indopak.sqlite`, and the line-by-line
 * view and an ayah link did not expect it, so the exception left a coroutine of the view model and
 * ended the app.
 */
class IndoPakSelectionTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val fixture = ReadFixture()

    /**
     * The fixture's text, except that the IndoPak print behaves as when the layouts are missing.
     */
    private val textWithoutIndoPakLayouts =
        object : QuranTextRepository by fixture.text {
            override suspend fun pageLayout(page: Int, script: ScriptId): PageLayout {
                failIfIndoPak(script)
                return fixture.text.pageLayout(page, script)
            }

            override suspend fun pageOf(ayah: AyahRef, script: ScriptId): Int {
                failIfIndoPak(script)
                return fixture.text.pageOf(ayah, script)
            }

            override suspend fun ayahsOnPage(page: Int, script: ScriptId): List<AyahRef> {
                failIfIndoPak(script)
                return fixture.text.ayahsOnPage(page, script)
            }

            private fun failIfIndoPak(script: ScriptId) {
                if (script == ScriptId.IndoPak15) {
                    throw UnsupportedOperationException("The IndoPak layouts are not installed.")
                }
            }
        }

    private fun viewModelFor(settings: ReadSettings): QuranViewModel {
        val dependencies =
            fixture.dependencies.copy(
                quranText = textWithoutIndoPakLayouts,
                settingsStore = InMemorySettingsStore(settings),
            )
        return QuranViewModel(dependencies, wordLanguage = "en")
    }

    @Test
    fun lineByLineShowsThePageTextInsteadOfEndingTheApp() = runTest {
        val viewModel =
            viewModelFor(ReadSettings(script = ScriptId.IndoPak15, pageView = PageView.LineByLine))

        viewModel.open(null)

        assertNotNull(viewModel.state.value.contents[1])
        assertEquals(ReadSettings().script, viewModel.state.value.settings.script)
    }

    @Test
    fun mushafShowsThePageInTheDefaultPrint() = runTest {
        val viewModel =
            viewModelFor(ReadSettings(script = ScriptId.IndoPak15, pageView = PageView.Mushaf))

        viewModel.open(null)

        assertTrue(viewModel.state.value.layouts[1] is PageLoad.Loaded)
    }

    @Test
    fun anAyahLinkOpensThePageInTheDefaultPrint() = runTest {
        val viewModel = viewModelFor(ReadSettings(script = ScriptId.IndoPak15))

        viewModel.open(QuranTarget.Ayah(AyahRef(1, 1)))

        assertEquals(1, viewModel.state.value.currentPage)
    }

    @Test
    fun theSavedPrintIsCorrectedSoNoOtherScreenReadsTheMissingLayouts() = runTest {
        val viewModel = viewModelFor(ReadSettings(script = ScriptId.IndoPak15))

        viewModel.open(null)

        val savedScript = viewModel.state.value.settings.script
        assertEquals(ReadSettings().script, savedScript)
    }

    @Test
    fun anInstalledIndoPakPrintStaysTheSavedPrint() = runTest {
        fixture.packs.catalogueItems +=
            fakeCatalogueItem(
                id = ScriptId.IndoPak15.packId,
                group = CatalogueGroup.PageType,
                language = null,
                downloadBytes = 6_336_000,
            )
        fixture.packs.install(fixture.packs.catalogueItems.single().entry) {}
        val settingsStore = InMemorySettingsStore(ReadSettings(script = ScriptId.IndoPak15))
        val viewModel =
            QuranViewModel(
                fixture.dependencies.copy(settingsStore = settingsStore),
                wordLanguage = "en",
            )

        viewModel.open(null)

        assertEquals(ScriptId.IndoPak15, settingsStore.settings.first().script)
    }

    @Test
    fun aMadaniPrintWithoutItsFontIsKeptBecauseThePageDrawsTheBundledText() {
        assertEquals(ScriptId.V1, readablePrintOf(ScriptId.V1, installedPackIds = emptySet()))
        assertEquals(ScriptId.IndoPak15, readablePrintOf(ScriptId.IndoPak15, setOf("indopak-15")))
        assertEquals(
            ScriptId.V2,
            readablePrintOf(ScriptId.IndoPak15, installedPackIds = emptySet()),
        )
    }
}
