package app.awn.quran.feature.learn.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.learn.content.LessonLanguageChoice
import app.awn.quran.feature.learn.progress.LearnSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LessonLanguageRowTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var storeScope: CoroutineScope
    private lateinit var settings: LearnSettings

    @Before
    fun setUp() {
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File.createTempFile("lesson_language", ".preferences_pb").also { it.delete() }
        settings = LearnSettings(PreferenceDataStoreFactory.create(scope = storeScope) { file })
    }

    @After
    fun tearDown() {
        runBlocking { storeScope.coroutineContext.job.cancelAndJoin() }
    }

    private fun storedChoice(): LessonLanguageChoice = runBlocking {
        settings.lessonLanguage.first()
    }

    @Test
    fun theRowStartsOnTheAppLanguageAndStoresTheChoice() {
        composeRule.setContent { AwnTheme { LessonLanguageRow(settings) } }
        assertEquals(LessonLanguageChoice.FollowApp, storedChoice())

        composeRule.onNodeWithText("Same as app").performClick()
        composeRule.onNodeWithText("বাংলা").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            storedChoice() == LessonLanguageChoice.Bangla
        }
    }
}
