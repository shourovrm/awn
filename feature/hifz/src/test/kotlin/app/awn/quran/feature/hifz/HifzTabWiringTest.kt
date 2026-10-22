package app.awn.quran.feature.hifz

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.data.fakes.FakeQuranTextRepository
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.hifz.data.HifzDatabase
import app.awn.quran.feature.hifz.data.HifzRepository
import app.awn.quran.feature.hifz.method.HifzMethodId
import app.awn.quran.feature.hifz.method.PageTableLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TIMEOUT_MILLIS = 10_000L

/** The real tab on a real (in-memory) database: what :app puts into the tab host. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HifzTabWiringTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var database: HifzDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var controller: HifzController
    private val text =
        FakeQuranTextRepository(
            pageByAyah = mutableMapOf(ayah(1) to 1, ayah(2) to 1, ayah(3) to 2, ayah(4) to 2),
            totalPages = 2,
        )
    private var todayLine by mutableStateOf<HifzTodayLine?>(null)

    @Before
    fun open() {
        database =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    HifzDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        controller =
            HifzController(
                HifzRepository(database),
                PageTableLoader(text),
                flowOf(ScriptId.QpcHafs),
                scope,
            )
    }

    @After
    fun close() {
        // Joined so no controller coroutine outlives the test and fails a later one.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        database.close()
    }

    @Composable
    private fun CaptureTodayLine() {
        todayLine = rememberHifzTodayLine(controller)
    }

    private fun show() {
        composeRule.setContent {
            AwnTheme {
                CaptureTodayLine()
                HifzTab(controller, text, onStartPractice = {}, onOpenAyah = {})
            }
        }
    }

    private fun ComposeContentTestRule.hasLabel(label: String): Boolean =
        onAllNodes(hasText(label)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun withoutAPlanTheTabOffersMethodsAndTodayHasNoHifzLine() {
        show()

        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.hasLabel("Choose a method") }
        assertNull(todayLine)
    }

    @Test
    fun withAPlanTheTabShowsTodayAndTheHifzLineExists() {
        show()
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.hasLabel("Choose a method") }

        controller.chooseMethod(HifzMethodId.SabaqSabqiManzil)

        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.hasLabel("Today") }
        composeRule.onNodeWithText("Start Sabaq").assertExists()
        composeRule.waitUntil(TIMEOUT_MILLIS) { todayLine != null }
        assertNotNull(todayLine)
        assertEquals("Hifz", todayLine?.title)
        assertEquals("Sabaq page 1", todayLine?.detail)
    }
}
