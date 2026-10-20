package app.awn.quran.feature.read

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.awn.quran.core.ui.theme.AwnTheme
import app.awn.quran.feature.read.quran.QuranScreen
import app.awn.quran.feature.read.quran.QuranScreenSlots
import app.awn.quran.feature.read.quran.QuranTarget

/**
 * Kept so :app compiles until it wires [QuranScreen] with real slots (audio bar, play panel, recite
 * bar, index, search, learn, settings). It opens the Quran screen on [startPage] with empty slots.
 * [onExpandPlayer] and [reciterName] are no longer used.
 */
@Composable
fun ReadHost(
    dependencies: ReadDependencies,
    @Suppress("UNUSED_PARAMETER") onExpandPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    startPage: Int? = null,
    @Suppress("UNUSED_PARAMETER") reciterName: (String) -> String = { it },
) {
    AwnTheme(field = ReadField) {
        QuranScreen(
            dependencies = dependencies,
            slots = QuranScreenSlots.Empty,
            modifier = modifier,
            target = startPage?.let { QuranTarget.Page(it) },
        )
    }
}
