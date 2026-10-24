package app.awn.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.awn.quran.core.data.ScriptId
import app.awn.quran.core.quranfont.LocalQuranPrint
import app.awn.quran.core.quranfont.QuranPrint
import app.awn.quran.core.quranfont.SharedPageFontCache
import app.awn.quran.feature.read.ReadDependencies
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold

/**
 * Makes the Mushaf print the user chose the print of every screen below, so that all Quran text
 * follows the setting as soon as it changes. The font cache is the one the Mushaf uses, and it is
 * made again after each pack change so a font installed later shows.
 */
@Composable
internal fun ProvideQuranPrint(dependencies: ReadDependencies, content: @Composable () -> Unit) {
    val script by
        remember(dependencies.settingsStore) {
                dependencies.settingsStore.settings.map { it.script }.distinctUntilChanged()
            }
            .collectAsState(initial = ScriptId.QpcHafs)
    val packRevision by
        remember(dependencies.packs) {
                dependencies.packs.installed().drop(1).runningFold(0) { count, _ -> count + 1 }
            }
            .collectAsState(initial = 0)
    val print =
        remember(script, packRevision, dependencies) {
            QuranPrint(
                script = script,
                fontCache = SharedPageFontCache.obtain(dependencies.packs, packRevision),
                quranText = dependencies.quranText,
            )
        }
    CompositionLocalProvider(LocalQuranPrint provides print, content = content)
}
