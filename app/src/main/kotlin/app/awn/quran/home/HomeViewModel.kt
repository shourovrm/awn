package app.awn.quran.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.awn.quran.feature.learn.content.Snippet
import app.awn.quran.feature.learn.content.SnippetRepository
import app.awn.quran.services.AwnServices
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** What the Home screen shows; every field has a neutral value until its source has answered. */
data class HomeState(
    val lastPage: Int? = null,
    val reciterName: String? = null,
    val isHifzOn: Boolean = false,
    val ayahsDue: Int = 0,
    val streakDays: Int = 0,
    val pagesThisWeek: Int = 0,
    val snippet: Snippet? = null,
)

/** Gathers the figures of the Home bands, the two tiles and the day's snippet. */
class HomeViewModel(private val services: AwnServices) : ViewModel() {

    val state: StateFlow<HomeState> =
        combine(readingDetail(), listeningDetail(), learningDetail(), snippetOfTheDay()) {
                reading,
                listening,
                learning,
                snippet ->
                HomeState(
                    lastPage = reading,
                    reciterName = listening.first,
                    isHifzOn = listening.second,
                    ayahsDue = learning.dueToday.size,
                    streakDays = learning.streakDays,
                    pagesThisWeek = learning.pagesThisWeek,
                    snippet = snippet,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeState())

    private fun readingDetail(): Flow<Int?> =
        services.coreData.bookmarks.recentPages().map { pages -> pages.firstOrNull()?.page }

    /** The selected reciter's name, paired with whether hifz mode is on. */
    private fun listeningDetail(): Flow<Pair<String?, Boolean>> =
        combine(services.listenSettings.selectedReciterId, services.reciteSettings.hifzEnabled) {
            reciterId,
            isHifzOn ->
            reciterNameOf(reciterId) to isHifzOn
        }

    private suspend fun reciterNameOf(reciterId: String): String? =
        services.coreData.reciters.reciters().firstOrNull { it.id == reciterId }?.name

    private fun learningDetail() = services.learn.progress.observeSnapshot()

    private fun snippetOfTheDay(): Flow<Snippet?> = flow {
        val snippets = withContext(Dispatchers.IO) { services.learn.snippets.all() }
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        emit(SnippetRepository.forDay(snippets, today))
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
