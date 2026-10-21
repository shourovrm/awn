package app.awn.quran.feature.recite

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReciteSettingsTest {

    @get:Rule val folder = TemporaryFolder()

    private fun newSettings(scope: CoroutineScope) =
        ReciteSettings(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(folder.root, "settings.preferences_pb")
            },
        )

    // DataStore frees its file only when the scope's job has completed, and cancel() returns before
    // that. Opening a second DataStore on the same file straight after cancel() therefore fails
    // now and then with "multiple DataStores active for the same file". Joining makes it certain.
    private suspend fun closeAndWait(scope: CoroutineScope) {
        scope.coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun defaultsAreMiddleSpeedHifzOffHintOnAndHusary() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO)
        val settings = newSettings(scope)

        assertEquals(0.5f, settings.speed.first())
        assertEquals(false, settings.hifzEnabled.first())
        assertTrue(settings.hintEnabled.first())
        assertEquals("husary", settings.reciterId.first())
        closeAndWait(scope)
    }

    @Test
    fun valuesSurviveANewDataStoreOnTheSameFile() = runBlocking {
        val firstScope = CoroutineScope(Dispatchers.IO)
        newSettings(firstScope).apply {
            setSpeed(0.8f)
            setHifzEnabled(true)
        }
        closeAndWait(firstScope)

        val secondScope = CoroutineScope(Dispatchers.IO)
        val reopened = newSettings(secondScope)

        assertEquals(0.8f, reopened.speed.first())
        assertTrue(reopened.hifzEnabled.first())
        closeAndWait(secondScope)
    }
}
