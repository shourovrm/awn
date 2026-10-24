package app.awn.quran.feature.duas.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreDuaProgressStoreTest {

    @get:Rule val folder = TemporaryFolder()

    private val storeScope = CoroutineScope(Dispatchers.IO)

    @After fun closeStore() = storeScope.cancel()

    private fun newStore(): DataStoreDuaProgressStore =
        DataStoreDuaProgressStore(
            PreferenceDataStoreFactory.create(scope = storeScope) {
                File(folder.root, "duas.preferences_pb")
            },
        )

    @Test
    fun aNewStoreHasNothingSavedOrLearned() = runTest {
        val progress = newStore().progress.first()

        assertEquals(emptySet<String>(), progress.savedIds)
        assertFalse(progress.isLearned("d1"))
    }

    @Test
    fun savingTwiceTakesTheDuaOutAgain() = runTest {
        val store = newStore()

        store.toggleSaved("d1")
        store.toggleSaved("d2")
        assertEquals(setOf("d1", "d2"), store.progress.first().savedIds)

        store.toggleSaved("d1")
        assertEquals(setOf("d2"), store.progress.first().savedIds)
    }

    @Test
    fun threeCleanRecitationsMarkADuaLearnedAndOthersStayApart() = runTest {
        val store = newStore()

        repeat(CLEAN_RECITATIONS_TO_LEARN - 1) { store.recordCleanRecitation("d1") }
        assertFalse(store.progress.first().isLearned("d1"))

        store.recordCleanRecitation("d1")
        store.recordCleanRecitation("d2")
        val progress = store.progress.first()
        assertTrue(progress.isLearned("d1"))
        assertEquals(CLEAN_RECITATIONS_TO_LEARN, progress.cleanCount("d1"))
        assertEquals(1, progress.cleanCount("d2"))
        assertFalse(progress.isLearned("d2"))
    }

    @Test
    fun savedIdsAndCountsDoNotMixUp() = runTest {
        val store = newStore()

        store.toggleSaved("clean_d1")
        store.recordCleanRecitation("d1")

        val progress = store.progress.first()
        assertEquals(setOf("clean_d1"), progress.savedIds)
        assertEquals(mapOf("d1" to 1), progress.cleanRecitations)
    }
}
