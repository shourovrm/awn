package app.awn.quran.feature.recite

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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

    @Test
    fun defaultsAreMiddleSpeedHifzOffHintOnAndHusary() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO)
        val settings = newSettings(scope)

        assertEquals(0.5f, settings.speed.first())
        assertEquals(false, settings.hifzEnabled.first())
        assertTrue(settings.hintEnabled.first())
        assertEquals("husary", settings.reciterId.first())
        scope.cancel()
    }

    @Test
    fun valuesSurviveANewDataStoreOnTheSameFile() = runBlocking {
        val firstScope = CoroutineScope(Dispatchers.IO)
        newSettings(firstScope).apply {
            setSpeed(0.8f)
            setHifzEnabled(true)
        }
        firstScope.cancel()

        val secondScope = CoroutineScope(Dispatchers.IO)
        val reopened = newSettings(secondScope)

        assertEquals(0.8f, reopened.speed.first())
        assertTrue(reopened.hifzEnabled.first())
        secondScope.cancel()
    }
}
