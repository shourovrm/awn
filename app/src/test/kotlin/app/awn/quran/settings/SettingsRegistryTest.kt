package app.awn.quran.settings

import app.awn.quran.audioManagerRoute
import app.awn.quran.core.ui.components.SettingRowKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRegistryTest {

    @Test
    fun everySearchableRowHasItsOwnKey() {
        val keys = registeredRowKeys()

        assertEquals("duplicate row keys", keys.size, keys.toSet().size)
    }

    @Test
    fun everyRowKeyOfEveryPageIsInTheSearchIndex() {
        val definedKeys =
            SettingRowKeys::class
                .java
                .declaredFields
                .filter { it.type == String::class.java }
                .map { it.get(null) as String }
        val indexedKeys = registeredRowKeys().toSet()

        val missing = definedKeys.filterNot { it in indexedKeys }

        assertTrue("row keys without a search entry: $missing", missing.isEmpty())
    }

    @Test
    fun everyPageOfTheHubIsInTheSearchIndexWithItsRoute() {
        val routes = registeredPageRoutes()

        assertEquals(SettingsSection.entries.map { it.route }, routes)
    }

    @Test
    fun theHubListsTheEightPagesInOrder() {
        assertEquals(
            listOf(
                "settings/quran",
                "settings/audio",
                "settings/recite",
                "settings/learn",
                "settings/colours",
                "settings/storage",
                "settings/backup",
                "settings/about",
            ),
            SettingsSection.entries.map { it.route },
        )
    }

    @Test
    fun storageRecitersOpenTheAudioPageOnItsDownloadsManager() {
        val route = audioManagerRoute()

        assertTrue(route, route.startsWith(SettingsSection.Audio.route))
        assertTrue(route, route.endsWith("manager=true"))
    }
}
