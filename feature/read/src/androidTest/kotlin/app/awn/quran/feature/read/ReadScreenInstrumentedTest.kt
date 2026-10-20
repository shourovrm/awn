package app.awn.quran.feature.read

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.awn.quran.core.data.fakes.FakeBookmarkRepository
import app.awn.quran.core.data.fakes.FakePackRepository
import app.awn.quran.core.data.fakes.FakeProgressEvents
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.data.fakes.FakeTranslationRepository
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.QuranScreen
import app.awn.quran.feature.read.quran.QuranScreenSlots
import app.awn.quran.feature.read.quran.QuranTarget
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.feature.read.settings.ReadSettingsStore
import app.awn.quran.player.fakes.FakeAudioPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/** Runs on a device: the full Mushaf screen on page 1 from the fakes. */
class ReadScreenInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    private class MemoryStore : ReadSettingsStore {
        private val state = MutableStateFlow(ReadSettings())
        override val settings = state

        override suspend fun update(transform: (ReadSettings) -> ReadSettings) {
            state.value = transform(state.value)
        }
    }

    @Test
    fun mushafScreenShowsTheWordsOfPageOne() {
        val text = FakeQuranTextRepository()
        text.chapterList = listOf(chapter())
        text.pages[1] = pageOne()
        val dependencies =
            ReadDependencies(
                text,
                FakeTranslationRepository(),
                FakeBookmarkRepository(),
                FakePackRepository(),
                FakeProgressEvents(),
                FakeAudioPlayer(),
                MemoryStore(),
            )

        composeRule.setContent {
            AwnTheme {
                QuranScreen(
                    dependencies = dependencies,
                    slots = QuranScreenSlots.Empty,
                    target = QuranTarget.Page(1),
                )
            }
        }

        composeRule.onNodeWithText("بِسْمِ").assertIsDisplayed()
    }

    private fun chapter() =
        app.awn.quran.core.data.Chapter(1, "الفاتحة", "Al-Fatihah", null, 7, "makkah", false)

    private fun pageOne() =
        app.awn.quran.core.data.PageLayout(
            1,
            app.awn.quran.core.data.ScriptId.QpcHafs,
            listOf(
                app.awn.quran.core.data.PageLine(
                    1,
                    listOf(
                        app.awn.quran.core.data.LineItem.WordItem(
                            app.awn.quran.core.data.Word(
                                app.awn.quran.core.data.WordKey(1, 1, 1),
                                "بِسْمِ",
                                null,
                                null,
                                null,
                            ),
                        ),
                    ),
                ),
            ),
        )
}
