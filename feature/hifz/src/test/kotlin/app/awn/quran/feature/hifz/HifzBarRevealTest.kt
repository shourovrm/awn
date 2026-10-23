package app.awn.quran.feature.hifz

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.Word
import app.awn.quran.core.data.WordKey
import app.awn.quran.core.data.fakes.FakeProgressEvents
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.hifz.data.HifzDatabase
import app.awn.quran.feature.hifz.data.HifzRepository
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.PageTableLoader
import app.awn.quran.feature.recite.PreparedSpeech
import app.awn.quran.feature.recite.ReciteController
import app.awn.quran.feature.recite.ReciteSettings
import app.awn.quran.feature.recite.SpeechPreparation
import app.awn.quran.feature.recite.SpeechState
import app.awn.quran.player.fakes.FakeAudioPlayer
import app.awn.quran.tracker.PhonemeSource
import app.awn.quran.tracker.ReferencePhonemes
import app.awn.quran.tracker.ReferenceWord
import app.awn.quran.tracker.WordKeyLike
import app.awn.quran.tracker.WordState
import app.awn.quran.tracker.fakes.FakeReferencePhonemes
import app.awn.quran.tracker.fakes.FakeTrackerFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TIMEOUT_MILLIS = 10_000L
private const val WORDS_PER_AYAH = 2

/**
 * The user found no way to show words one by one in the Hifz bar (2026-10-23). While words are
 * hidden the bar offers the recite bar's two reveal buttons; they drive the same session. The text
 * has ayahs 2:1 to 2:4 of two words each.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HifzBarRevealTest {

    @get:Rule val composeRule = createComposeRule()

    @get:Rule val folder = TemporaryFolder()

    private lateinit var database: HifzDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var settingsScope: CoroutineScope
    private lateinit var hifz: HifzController
    private lateinit var recite: ReciteController

    private fun wordKey(ayah: Int, position: Int) = WordKey(2, ayah, position)

    @Before
    fun open() {
        val text = textOfFourAyahs()
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    HifzDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        hifz =
            HifzController(
                HifzRepository(database),
                PageTableLoader(text),
                flowOf(ScriptId.QpcHafs),
                scope,
            )
        settingsScope = CoroutineScope(Dispatchers.IO)
        // The first tap on the reveal button would only show a letter; these tests count words.
        val settings =
            ReciteSettings(
                PreferenceDataStoreFactory.create(scope = settingsScope) {
                    File(folder.root, "recite.preferences_pb")
                },
            )
        runBlocking { settings.setHintEnabled(false) }
        recite = reciteControllerOn(text, settings)
    }

    @After
    fun close() {
        runBlocking {
            scope.coroutineContext.job.cancelAndJoin()
            settingsScope.coroutineContext.job.cancelAndJoin()
        }
        database.close()
    }

    private fun textOfFourAyahs() =
        FakeQuranTextRepository(
            wordsByAyah =
                (1..4)
                    .associate { ayahNumber ->
                        ayah(ayahNumber) to
                            (1..WORDS_PER_AYAH).map { position ->
                                Word(wordKey(ayahNumber, position), "كلمة", null, null)
                            }
                    }
                    .toMutableMap(),
            pageByAyah = mutableMapOf(ayah(1) to 1, ayah(2) to 1, ayah(3) to 2, ayah(4) to 2),
            totalPages = 2,
        )

    private fun reciteControllerOn(
        text: FakeQuranTextRepository,
        settings: ReciteSettings,
    ): ReciteController {
        val reference: ReferencePhonemes =
            FakeReferencePhonemes(
                (1..4)
                    .associate { ayahNumber ->
                        (2 to ayahNumber) to
                            (1..WORDS_PER_AYAH).map { position ->
                                ReferenceWord(
                                    WordKeyLike(2, ayahNumber, position),
                                    listOf("a"),
                                )
                            }
                    }
                    .toMutableMap(),
            )
        val speech =
            SpeechPreparation(executor = { work -> work.run() }) {
                object : PreparedSpeech {
                    override val referencePhonemes = reference

                    override fun newPhonemeSource(): PhonemeSource =
                        error("The microphone is not used here.")

                    override fun close() = Unit
                }
            }
        val dispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler())
        return ReciteController(
            quranText = text,
            progressEvents = FakeProgressEvents(),
            settings = settings,
            audioPlayer = FakeAudioPlayer(),
            speech = speech,
            inputFactory = { error("The microphone is not used here.") },
            scope = CoroutineScope(dispatcher + SupervisorJob()),
            trackerFactory = FakeTrackerFactory(),
            recognitionDispatcher = dispatcher,
        )
    }

    private fun startPracticeWithWordsHidden() {
        // The screen state only runs while something collects it, as the tab does.
        scope.launch { hifz.screenState.collect {} }
        hifz.chooseMethod(HifzMethodId.SabaqSabqiManzil)
        composeRule.waitUntil(TIMEOUT_MILLIS) { hifz.startNextPart() != null }
        val request = checkNotNull(hifz.practice.value)
        recite.practiceFrom(request.firstAyah, hideWords = true)
        composeRule.setContent {
            AwnTheme { HifzBar(hifz, recite, playRange = { _, _ -> }, onExit = {}) }
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            recite.session.words.value.isNotEmpty() && recite.speechState.value == SpeechState.Ready
        }
        composeRule.waitForIdle()
    }

    private fun hiddenWordCount(): Int =
        recite.session.wordStates.value.values.count { it == WordState.Hidden }

    @Test
    fun revealNextWordShowsOneMoreWord() {
        startPracticeWithWordsHidden()
        val hiddenBefore = hiddenWordCount()

        composeRule.onNodeWithTag(HIFZ_BAR_REVEAL_WORD_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(hiddenBefore - 1, hiddenWordCount())
    }

    @Test
    fun revealAyahShowsTheRestOfTheAyah() {
        startPracticeWithWordsHidden()
        val hiddenBefore = hiddenWordCount()
        composeRule.onNodeWithTag(HIFZ_BAR_REVEAL_WORD_TAG).performClick()

        composeRule.onNodeWithTag(HIFZ_BAR_REVEAL_AYAH_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(hiddenBefore - WORDS_PER_AYAH, hiddenWordCount())
    }

    @Test
    fun theRevealButtonsAreGoneWhileTheWordsAreShown() {
        startPracticeWithWordsHidden()

        recite.setHideWords(false)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HIFZ_BAR_REVEAL_WORD_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(HIFZ_BAR_REVEAL_AYAH_TAG).assertDoesNotExist()
    }
}
