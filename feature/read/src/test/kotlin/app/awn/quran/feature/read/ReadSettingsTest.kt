package app.awn.quran.feature.read

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.TranslationId
import app.awn.quran.feature.read.settings.DataStoreReadSettingsStore
import app.awn.quran.feature.read.settings.PageView
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.feature.read.settings.ReadSettingsViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
                pageView = PageView.WordByWord,
                translations = listOf(TranslationId("en_saheeh"), TranslationId("bn_zakaria")),
                showTransliteration = false,
                showWordByWord = false,
                arabicSizeStep = 6,
                translationSizeStep = 1,
                highlightBookmarks = false,
                volumeKeysTurnPages = true,
            )
        openStore(file, firstScope).update { changed }
        firstScope.cancel()

        val secondScope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        val reloaded = openStore(file, secondScope).settings.first()
        secondScope.cancel()

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
        scope.cancel()

        assertEquals(4, loaded.arabicSizeStep)
        assertEquals(4, loaded.translationSizeStep)
        assertEquals(PageView.Translation, loaded.pageView)
    }

    @Test
    fun anEmptyStoreGivesTheDefaults() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        val loaded = openStore(File(folder.root, "empty.preferences_pb"), scope).settings.first()
        scope.cancel()

        assertEquals(ReadSettings(), loaded)
    }

    @Test
    fun viewModelWritesChangesToTheStore() = runTest {
        val fixture = ReadFixture()
        val viewModel = ReadSettingsViewModel(fixture.store, fixture.translations)

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
        val viewModel = ReadSettingsViewModel(fixture.store, fixture.translations)
        val english = TranslationId("en_saheeh")
        val bangla = TranslationId("bn_zakaria")
        val third = TranslationId("en_abdul_haleem")

        viewModel.setTranslationEnabled(third, true)
        assertEquals(listOf(bangla, english, third), viewModel.settings.value.translations)

        viewModel.moveTranslationUp(third)
        assertEquals(listOf(bangla, third, english), viewModel.settings.value.translations)

        viewModel.moveTranslationUp(bangla)
        assertEquals(listOf(bangla, third, english), viewModel.settings.value.translations)

        viewModel.setTranslationEnabled(bangla, false)
        assertEquals(listOf(third, english), viewModel.settings.value.translations)
    }
}
