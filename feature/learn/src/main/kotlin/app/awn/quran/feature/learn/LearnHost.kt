package app.awn.quran.feature.learn

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import app.awn.quran.core.data.AyahRef
import app.awn.quran.core.data.Chapter
import app.awn.quran.core.data.QuranTextRepository
import app.awn.quran.feature.learn.content.LearnLanguage
import app.awn.quran.feature.learn.content.Lesson
import app.awn.quran.feature.learn.content.Snippet
import app.awn.quran.feature.learn.content.SnippetRepository
import app.awn.quran.feature.learn.fsrs.FsrsRating
import app.awn.quran.feature.learn.progress.ProgressSnapshot
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface LearnRoute {
    data object Home : LearnRoute

    data object Progress : LearnRoute

    data object Lessons : LearnRoute

    data object Revision : LearnRoute

    data class OneLesson(val id: String) : LearnRoute
}

private val EMPTY_SNAPSHOT =
    ProgressSnapshot(
        streakDays = 0,
        pagesToday = 0,
        pagesThisWeek = 0,
        memorisedByAyah = emptySet(),
        memorisedBySurah = emptyMap(),
        memorisationThreshold = 0,
        dueToday = emptyList(),
        nextDue = null,
    )

/**
 * The Learn tab: owns the database and moves between Learn, Revision, Progress and the lessons.
 * [quranText] supplies ayah text and surah names; without it the screens show references only.
 *
 * When [onReviseAt] is set, "Revise" calls it with the first due ayah in reading order, and :app
 * opens the Quran screen there in recite mode with words hidden; the Revision screen of this tab is
 * then not used. Without it, revision runs inside this tab.
 */
@Composable
fun LearnScreen(
    modifier: Modifier = Modifier,
    quranText: QuranTextRepository? = null,
    onReviseAt: ((AyahRef) -> Unit)? = null,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val services = remember { LearnServices.get(context) }
    val locale = LocalConfiguration.current.locales[0]
    val language = LearnLanguage.of(locale)
    val scope = rememberCoroutineScope()

    var route by remember { mutableStateOf<LearnRoute>(LearnRoute.Home) }
    BackHandler(enabled = route != LearnRoute.Home) {
        route = if (route is LearnRoute.OneLesson) LearnRoute.Lessons else LearnRoute.Home
    }

    val snapshot by remember {
        services.progress.observeSnapshot()
    }
        .collectAsState(initial = EMPTY_SNAPSHOT)
    val lessons by
        produceState(emptyList<Lesson>()) {
            value = withContext(Dispatchers.IO) { services.lessons.all() }
        }
    val snippets by
        produceState(emptyList<Snippet>()) {
            value = withContext(Dispatchers.IO) { services.snippets.all() }
        }
    val chapters by
        produceState(emptyList<Chapter>(), quranText) { value = quranText?.chapters().orEmpty() }
    val surahName = { surah: Int ->
        surahNameOf(chapters, surah, language) { resources.getString(R.string.surah_number, surah) }
    }

    LearnRoutes(
        route = route,
        onRouteChange = { route = it },
        content =
            LearnRouteContent(
                snapshot = snapshot,
                lessons = lessons,
                snippets = snippets,
                locale = locale,
                quranText = quranText,
                surahName = surahName,
            ),
        rate = { ayah, rating -> scope.launch { services.progress.gradeAyah(ayah, rating) } },
        onReviseAt = onReviseAt,
        modifier = modifier,
    )
}

/** What the screens of the Learn tab read; loaded once by [LearnScreen]. */
private class LearnRouteContent(
    val snapshot: ProgressSnapshot,
    val lessons: List<Lesson>,
    val snippets: List<Snippet>,
    val locale: Locale,
    val quranText: QuranTextRepository?,
    val surahName: (Int) -> String,
) {
    val language: LearnLanguage
        get() = LearnLanguage.of(locale)
}

@Composable
private fun LearnRoutes(
    route: LearnRoute,
    onRouteChange: (LearnRoute) -> Unit,
    content: LearnRouteContent,
    rate: (AyahRef, FsrsRating) -> Unit,
    onReviseAt: ((AyahRef) -> Unit)?,
    modifier: Modifier,
) {
    val summaries = LearnUiMapper.lessonSummaries(content.lessons, content.language)
    val snapshot = content.snapshot
    val surahName = content.surahName
    when (val current = route) {
        LearnRoute.Home -> {
            val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
            val snippet = SnippetRepository.forDay(content.snippets, today)
            LearnScreen(
                state = LearnUiMapper.learnState(snapshot, summaries, snippet),
                onLessonClick = { onRouteChange(LearnRoute.OneLesson(it)) },
                onAllLessonsClick = { onRouteChange(LearnRoute.Lessons) },
                onProgressClick = { onRouteChange(LearnRoute.Progress) },
                onReviseClick = {
                    reviseOrOpenRevision(snapshot.dueToday, onReviseAt) {
                        onRouteChange(LearnRoute.Revision)
                    }
                },
                modifier = modifier,
            )
        }
        LearnRoute.Progress ->
            ProgressScreen(
                state =
                    LearnUiMapper.progressState(
                        snapshot,
                        surahName,
                        ZoneId.systemDefault(),
                        content.locale,
                    ),
                onBackClick = { onRouteChange(LearnRoute.Home) },
                modifier = modifier,
            )
        LearnRoute.Lessons ->
            LessonsListScreen(
                lessons = summaries,
                onLessonClick = { onRouteChange(LearnRoute.OneLesson(it)) },
                onBackClick = { onRouteChange(LearnRoute.Home) },
                modifier = modifier,
            )
        is LearnRoute.OneLesson ->
            LessonRoute(
                lesson = content.lessons.firstOrNull { it.id == current.id },
                content = content,
                onBackClick = { onRouteChange(LearnRoute.Lessons) },
                modifier = modifier,
            )
        LearnRoute.Revision ->
            RevisionHost(
                dueToday = snapshot.dueToday,
                quranText = content.quranText,
                surahName = surahName,
                rate = rate,
                onBackClick = { onRouteChange(LearnRoute.Home) },
                modifier = modifier,
            )
    }
}

// Hands the first due ayah in reading order to :app when it handles revision, else opens the
// Revision screen of this tab.
private fun reviseOrOpenRevision(
    dueToday: List<AyahRef>,
    onReviseAt: ((AyahRef) -> Unit)?,
    openRevision: () -> Unit,
) {
    val firstDue = dueToday.minOrNull()
    if (onReviseAt != null && firstDue != null) onReviseAt(firstDue) else openRevision()
}

@Composable
private fun LessonRoute(
    lesson: Lesson?,
    content: LearnRouteContent,
    onBackClick: () -> Unit,
    modifier: Modifier,
) {
    if (lesson == null) return
    val examples by exampleLines(lesson, content.language, content.quranText, content.surahName)
    LessonScreen(
        lesson = lesson,
        language = content.language,
        examples = examples,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

/** Walks through the ayahs that were due when the user pressed Revise, one at a time. */
@Composable
private fun RevisionHost(
    dueToday: List<AyahRef>,
    quranText: QuranTextRepository?,
    surahName: (Int) -> String,
    rate: (AyahRef, FsrsRating) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier,
) {
    // The queue is fixed on entry: rating a card removes it from the live due list, and a queue
    // that shrank under the user would skip the next ayah.
    val queue = remember { dueToday }
    var index by remember { mutableIntStateOf(0) }
    var isTextShown by remember { mutableStateOf(false) }
    val currentAyah = queue.getOrNull(index)
    val text by
        produceState<String?>(null, currentAyah, quranText) {
            value =
                if (currentAyah == null || quranText == null) {
                    null
                } else {
                    quranText
                        .wordsOfAyah(currentAyah)
                        .joinToString(" ") { it.textUthmani }
                        .ifEmpty { null }
                }
        }
    val card = currentAyah?.let { RevisionCard(it, "${surahName(it.surah)} $it", text) }
    RevisionScreen(
        state = RevisionUiState(card, queue.size - index, queue.size, isTextShown),
        onShowTextClick = { isTextShown = true },
        onRate = { rating ->
            currentAyah?.let { rate(it, rating) }
            index += 1
            isTextShown = false
        },
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@Composable
private fun exampleLines(
    lesson: Lesson,
    language: LearnLanguage,
    quranText: QuranTextRepository?,
    surahName: (Int) -> String,
): State<List<ExampleLine>> =
    produceState(emptyList(), lesson, language, quranText) {
        value =
            lesson.examples.map { example ->
                val words = quranText?.wordsOfAyah(AyahRef(example.surah, example.ayah)).orEmpty()
                LearnUiMapper.exampleLine(example, language, surahName(example.surah), words)
            }
    }

private fun surahNameOf(
    chapters: List<Chapter>,
    surah: Int,
    language: LearnLanguage,
    fallback: () -> String,
): String {
    val chapter = chapters.firstOrNull { it.number == surah } ?: return fallback()
    return if (language == LearnLanguage.Bangla) chapter.nameBangla ?: chapter.nameSimple
    else chapter.nameSimple
}
