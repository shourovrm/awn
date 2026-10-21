package app.awn.quran.feature.read

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.feature.read.settings.DataStoreReadSettingsStore
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.feature.read.settings.ReadSettingsViewModel
import app.awn.quran.feature.read.settings.ScreenOrientation
import app.awn.quran.feature.read.settings.TextLayer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReadSettingsTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()
    @get:Rule val folder = TemporaryFolder()

    private fun openStore(file: File, scope: CoroutineScope) =
        DataStoreReadSettingsStore(
            PreferenceDataStoreFactory.create(scope = scope) { file },
        )

    @Test
    fun settingsSurviveReopeningTheStore() = runTest {
        val file = File(folder.root, "read_settings.preferences_pb")
        val firstScope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        val changed =
            ReadSettings(
                script = ScriptId.V2,
                pageView = PageView.LineByLine,
                showArabic = false,
                translations = listOf(TranslationId("en_saheeh"), TranslationId("bn_zakaria")),
                showTransliteration = false,
                showWordByWord = false,
                arabicSizeStep = 6,
                translationSizeStep = 1,
                transliterationSizeStep = 5,
                wordByWordSizeStep = 0,
                mushafSizeStep = 1,
                quranOrientation = ScreenOrientation.Landscape,
                appOrientation = ScreenOrientation.Portrait,
                highlightBookmarks = false,
                volumeKeysTurnPages = true,
            )
        openStore(file, firstScope).update { changed }
        firstScope.coroutineContext.job.cancelAndJoin()

        val secondScope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        val reloaded = openStore(file, secondScope).settings.first()
        secondScope.coroutineContext.job.cancelAndJoin()

        assertEquals(changed, reloaded)
    }

    @Test
    fun theOldThreeStepSizeAndViewNameAreMigrated() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        val dataStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(folder.root, "legacy.preferences_pb")
            }
        dataStore.edit {
            it[stringPreferencesKey("text_size")] = "Large"
            it[stringPreferencesKey("page_view")] = "AyahList"
        }

        val loaded = DataStoreReadSettingsStore(dataStore).settings.first()
        scope.coroutineContext.job.cancelAndJoin()

        assertEquals(4, loaded.arabicSizeStep)
        assertEquals(4, loaded.translationSizeStep)
        assertEquals(PageView.LineByLine, loaded.pageView)
        assertEquals(false, loaded.showWordByWord)
    }

    @Test
    fun theOldWordByWordViewBecomesLineByLineWithTheWordByWordLayerTicked() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        val dataStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(folder.root, "legacy_word_by_word.preferences_pb")
            }
        dataStore.edit {
            it[stringPreferencesKey("page_view")] = "WordByWord"
            it[booleanPreferencesKey("word_by_word")] = false
            it[intPreferencesKey("translation_size_step")] = 5
        }
        val store = DataStoreReadSettingsStore(dataStore)

        val migrated = store.settings.first()
        // Saving once writes the new form; reading it again must give the same settings.
        store.update { it }
        val resaved = store.settings.first()
        scope.coroutineContext.job.cancelAndJoin()

        assertEquals(PageView.LineByLine, migrated.pageView)
        assertEquals(true, migrated.showWordByWord)
        assertEquals(migrated, resaved)
        // The layers that had no size of their own followed the translation size.
        assertEquals(5, migrated.transliterationSizeStep)
        assertEquals(5, migrated.wordByWordSizeStep)
    }

    @Test
    fun aTickTheUserRemovedIsNotBroughtBackAfterTheMigration() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        val store = openStore(File(folder.root, "after.preferences_pb"), scope)

        store.update { it.copy(pageView = PageView.LineByLine, showWordByWord = false) }
        val loaded = store.settings.first()
        scope.coroutineContext.job.cancelAndJoin()

        assertEquals(false, loaded.showWordByWord)
    }

    @Test
    fun layersListsOnlyTheTickedOnesInThePopupOrder() {
        val everything =
            ReadSettings(
                translations = listOf(TranslationId("en_saheeh")),
                showTransliteration = true,
                showWordByWord = true,
            )
        assertEquals(
            listOf(
                TextLayer.Arabic,
                TextLayer.Translation,
                TextLayer.Transliteration,
                TextLayer.WordByWord,
            ),
            everything.visibleLayers,
        )

        val onlyMeanings =
            everything.copy(
                showArabic = false,
                translations = emptyList(),
                showTransliteration = false,
            )
        assertEquals(listOf(TextLayer.WordByWord), onlyMeanings.visibleLayers)
    }

    @Test
    fun eachLayerKeepsItsOwnSizeInsideTheRange() {
        val settings = ReadSettings(arabicSizeStep = 3, wordByWordSizeStep = 0)

        val changed =
            settings
                .withStepMoved(TextLayer.Arabic, 2)
                .withStepMoved(TextLayer.WordByWord, -1)
                .withStepMoved(TextLayer.Transliteration, 99)

        assertEquals(5, changed.arabicSizeStep)
        assertEquals(0, changed.wordByWordSizeStep)
        assertEquals(7, changed.transliterationSizeStep)
        assertEquals(settings.translationSizeStep, changed.translationSizeStep)
    }

    @Test
    fun thePrintedPageFitsTheWidthAtStepZeroAndGrowsFromThere() {
        assertEquals(1f, ReadSettings().mushafScale, 0.001f)
        assertEquals(1.15f, ReadSettings(mushafSizeStep = 1).mushafScale, 0.001f)
        // Steps past the second only added line spacing, so a stored 4 reads back as 1.
        assertEquals(1.15f, ReadSettings(mushafSizeStep = 4).mushafScale, 0.001f)
        assertEquals(1, ReadSettings().withMushafStepMoved(99).mushafSizeStep)
        assertEquals(0, ReadSettings().withMushafStepMoved(-1).mushafSizeStep)
    }

    @Test
    fun anEmptyStoreGivesTheDefaults() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        val loaded = openStore(File(folder.root, "empty.preferences_pb"), scope).settings.first()
        scope.coroutineContext.job.cancelAndJoin()

        assertEquals(ReadSettings(), loaded)
    }

    @Test
    fun viewModelWritesChangesToTheStore() = runTest {
        val fixture = ReadFixture()
        val viewModel = ReadSettingsViewModel(fixture.store)

        viewModel.changeTextSize(-1)
        viewModel.setShowWordByWord(false)
        viewModel.setScript(ScriptId.V1)

        val stored = fixture.store.settings.first()
        assertEquals(2, stored.arabicSizeStep)
        assertEquals(2, stored.translationSizeStep)
        assertEquals(false, stored.showWordByWord)
        assertEquals(ScriptId.V1, stored.script)
        assertEquals(stored, viewModel.settings.value)
    }

    @Test
    fun translationsCanBeEnabledDisabledAndReordered() = runTest {
        val fixture = ReadFixture()
        val viewModel = ReadSettingsViewModel(fixture.store)
        val english = TranslationId("en_saheeh")
        val bangla = TranslationId("bn_zakaria")
        val third = TranslationId("en_abdul_haleem")

        viewModel.setTranslationEnabled(bangla, true)
        viewModel.setTranslationEnabled(english, true)
        viewModel.setTranslationEnabled(third, true)
        assertEquals(listOf(bangla, english, third), viewModel.settings.value.translations)

        viewModel.moveTranslationUp(third)
        assertEquals(listOf(bangla, third, english), viewModel.settings.value.translations)

        viewModel.moveTranslationUp(bangla)
        assertEquals(listOf(bangla, third, english), viewModel.settings.value.translations)

        viewModel.setTranslationEnabled(bangla, false)
        assertEquals(listOf(third, english), viewModel.settings.value.translations)
    }

    @Test
    fun theWordByWordLanguageIsSavedAndClearedBackToTheAppLanguage() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        val store = openStore(File(folder.root, "word_language.preferences_pb"), scope)
        val fresh = store.settings.first()

        store.update { it.copy(wordByWordLanguage = "ur") }
        val chosen = store.settings.first()
        store.update { it.copy(wordByWordLanguage = null) }
        val cleared = store.settings.first()
        scope.coroutineContext.job.cancelAndJoin()

        assertEquals(null, fresh.wordByWordLanguage)
        assertEquals("ur", chosen.wordByWordLanguage)
        assertEquals(null, cleared.wordByWordLanguage)
    }

    @Test
    fun anInstallationFromBeforeTheLanguageChoiceKeepsItsLayerTickAndFollowsTheAppLanguage() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
            val dataStore =
                PreferenceDataStoreFactory.create(scope = scope) {
                    File(folder.root, "before_language.preferences_pb")
                }
            dataStore.edit { it[booleanPreferencesKey("word_by_word")] = false }

            val loaded = DataStoreReadSettingsStore(dataStore).settings.first()
            scope.coroutineContext.job.cancelAndJoin()

            assertEquals(false, loaded.showWordByWord)
            assertEquals(null, loaded.wordByWordLanguage)
        }

    @Test
    fun tickingALanguageShowsTheMeaningsButTheRowAndRemovalLeaveTheLayerAlone() = runTest {
        val fixture = ReadFixture(ReadSettings(showWordByWord = false))
        val viewModel = ReadSettingsViewModel(fixture.store)

        viewModel.setWordByWordLanguage("ur")
        assertEquals("ur", viewModel.settings.value.wordByWordLanguage)
        assertEquals(false, viewModel.settings.value.showWordByWord)

        viewModel.useWordByWordLanguage("bn")
        assertEquals("bn", viewModel.settings.value.wordByWordLanguage)
        assertEquals(true, viewModel.settings.value.showWordByWord)

        viewModel.forgetWordByWordLanguage("ur")
        assertEquals("bn", viewModel.settings.value.wordByWordLanguage)
        viewModel.forgetWordByWordLanguage("bn")
        assertEquals(null, viewModel.settings.value.wordByWordLanguage)
    }

    @Test
    fun aNewInstallationTicksOnlyTheArabicBecauseNothingElseShipsInTheApp() {
        val fresh = ReadSettings()

        assertEquals(listOf(TextLayer.Arabic), fresh.visibleLayers)
        assertEquals(emptyList<TranslationId>(), fresh.translations)
    }
}
