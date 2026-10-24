package app.awn.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import app.awn.quran.services.AwnServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How many duas the bundled collection holds, or null until it has been read. The Quran tab shows
 * its Duas entry only once the count is known.
 */
@Composable
internal fun rememberDuaCount(services: AwnServices): Int? {
    val count by
        produceState<Int?>(initialValue = null, services) {
            value =
                withContext(Dispatchers.Default) {
                    services.duasDependencies.loadCatalogue().duas.size
                }
        }
    return count
}
