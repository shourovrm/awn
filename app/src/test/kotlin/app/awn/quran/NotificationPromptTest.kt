package app.awn.quran

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The notification permission dialog opened on the first Listen and stopped the audio. Android 13
 * and later show media-session notifications without that permission, so the app asks for it only
 * where a notification needs it: the revise reminder switch in Settings (:feature:learn).
 */
class NotificationPromptTest {

    @Test
    fun theAppDoesNotAskForNotificationsWhenAudioStarts() {
        val appSources =
            File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }

        val asking = appSources.filter { it.readText().contains("POST_NOTIFICATIONS") }.toList()

        assertFalse("These :app files still ask for notifications: $asking", asking.any())
    }
}
