package app.awn.quran.feature.hifz

import android.content.Context
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.hifz.data.HifzDatabase
import app.awn.quran.feature.hifz.data.HifzRepository
import app.awn.quran.feature.hifz.method.PageTableLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Everything the Hifz tab and the practice bar need, created once per process by :app. [scripts] is
 * the print the reader shows, so the pages of the Hifz tab always follow it.
 */
class HifzServices(
    context: Context,
    quranText: QuranTextRepository,
    scripts: Flow<ScriptId>,
    scope: CoroutineScope,
) {
    val repository = HifzRepository(HifzDatabase.open(context.applicationContext))
    val controller = HifzController(repository, PageTableLoader(quranText), scripts, scope)
}
