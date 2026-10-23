package app.awn.quran

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.awn.quran.core.data.ScriptId
import app.awn.quran.feature.hifz.HifzScreenState
import app.awn.quran.feature.hifz.HifzToday
import app.awn.quran.feature.learn.ui.LearnPageLinks
import app.awn.quran.feature.read.settings.ReadSettings
import app.awn.quran.services.AwnServices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What the Learn tab needs to know about the other tabs: the page the Quran tab continues from, the
 * page of today's Hifz lesson (null without a Hifz plan or when the lesson is done), the print the
 * user reads, and how to show a page in the Quran screen.
 */
@Composable
internal fun rememberLearnPageLinks(
    services: AwnServices,
    navigation: TabNavigation,
): LearnPageLinks {
    val lastReadPage by
        remember(services) { lastReadPageFlow(services) }.collectAsStateWithLifecycle(null)
    val hifzScreenState by services.hifz.controller.screenState.collectAsStateWithLifecycle()
    val script by
        remember(services) { readScriptFlow(services) }
            .collectAsStateWithLifecycle(ReadSettings().script)
    return LearnPageLinks(
        lastReadPage = lastReadPage,
        hifzTodayPage = hifzTodayPageOf(hifzScreenState),
        script = script,
        onOpenPage = { page -> navigation.openQuran(page = page, ayah = null) },
    )
}

private fun lastReadPageFlow(services: AwnServices): Flow<Int?> =
    services.coreData.bookmarks
        .recentPages()
        .map { recentPages -> recentPages.firstOrNull()?.page }
        .distinctUntilChanged()

private fun readScriptFlow(services: AwnServices): Flow<ScriptId> =
    services.readDependencies.settingsStore.settings
        .map { settings -> settings.script }
        .distinctUntilChanged()

private fun hifzTodayPageOf(screenState: HifzScreenState): Int? {
    val ready = screenState as? HifzScreenState.Ready ?: return null
    return HifzToday.summaryOf(ready.state)?.lessonPage
}
